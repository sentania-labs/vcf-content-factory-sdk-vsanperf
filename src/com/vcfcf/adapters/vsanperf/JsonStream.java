package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A small pull reader for one JSON document on a {@link Reader}, for the
 * streaming {@code VsanPerfQueryPerf} parse (design v2 Sizing requirement
 * 3): the top-level array is walked one element at a time, and only that
 * element is held as a tree ({@link Map}, {@link List}, {@link String},
 * {@link Double}, {@link Boolean}, {@code null}). Plain JDK, no SDK.
 *
 * <p>Strict enough for vendor output (RFC 8259 tokens, escapes including
 * {@code \\uXXXX}); anything malformed, truncated or nested deeper than
 * {@link #MAX_DEPTH} throws {@link Unreadable}, so a cut gzip stream fails
 * the call cleanly and nothing of that response is used.
 */
final class JsonStream {

    static final int MAX_DEPTH = 64;

    private final Reader in;
    private int peeked = -2;

    JsonStream(Reader in) {
        this.in = in;
    }

    /** Called with each element of the top-level array, in order. */
    interface ElementSink {
        void accept(Object element) throws IOException;
    }

    /**
     * Read the document as a top-level array, handing each element to
     * {@code sink} as soon as it is complete.
     *
     * @throws Unreadable when the document is not a JSON array or is cut
     */
    void forEachInArray(ElementSink sink) throws IOException {
        skipWs();
        if (next() != '[') throw new Unreadable("the response is not a JSON array");
        skipWs();
        if (peek() == ']') {
            next();
        } else {
            while (true) {
                sink.accept(value(1));
                skipWs();
                int c = next();
                if (c == ']') break;
                if (c != ',') throw bad("',' or ']' expected in the array");
                skipWs();
            }
        }
        skipWs();
        if (peek() != -1) throw bad("content after the top-level array");
    }

    /** Parse one complete document into a tree (tests, small bodies). */
    Object document() throws IOException {
        skipWs();
        Object v = value(1);
        skipWs();
        if (peek() != -1) throw bad("content after the document");
        return v;
    }

    private Object value(int depth) throws IOException {
        if (depth > MAX_DEPTH) throw bad("nested deeper than " + MAX_DEPTH);
        int c = peek();
        switch (c) {
            case '{': return object(depth);
            case '[': return array(depth);
            case '"': next(); return string();
            case 't': literal("true"); return Boolean.TRUE;
            case 'f': literal("false"); return Boolean.FALSE;
            case 'n': literal("null"); return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) return number();
                throw bad(c == -1 ? "the document ends early" : "unexpected character");
        }
    }

    private Map<String, Object> object(int depth) throws IOException {
        next();
        Map<String, Object> m = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') {
            next();
            return m;
        }
        while (true) {
            skipWs();
            if (next() != '"') throw bad("a member name expected");
            String k = string();
            skipWs();
            if (next() != ':') throw bad("':' expected");
            skipWs();
            m.put(k, value(depth + 1));
            skipWs();
            int c = next();
            if (c == '}') return m;
            if (c != ',') throw bad("',' or '}' expected in an object");
        }
    }

    private List<Object> array(int depth) throws IOException {
        next();
        List<Object> l = new ArrayList<>();
        skipWs();
        if (peek() == ']') {
            next();
            return l;
        }
        while (true) {
            skipWs();
            l.add(value(depth + 1));
            skipWs();
            int c = next();
            if (c == ']') return l;
            if (c != ',') throw bad("',' or ']' expected in an array");
        }
    }

    private String string() throws IOException {
        StringBuilder b = new StringBuilder();
        while (true) {
            int c = next();
            if (c == -1) throw bad("the document ends inside a string");
            if (c == '"') return b.toString();
            if (c < 0x20) throw bad("a control character in a string");
            if (c != '\\') {
                b.append((char) c);
                continue;
            }
            int e = next();
            switch (e) {
                case '"': b.append('"'); break;
                case '\\': b.append('\\'); break;
                case '/': b.append('/'); break;
                case 'b': b.append('\b'); break;
                case 'f': b.append('\f'); break;
                case 'n': b.append('\n'); break;
                case 'r': b.append('\r'); break;
                case 't': b.append('\t'); break;
                case 'u':
                    int v = 0;
                    for (int i = 0; i < 4; i++) {
                        int h = Character.digit(next(), 16);
                        if (h < 0) throw bad("a bad \\u escape");
                        v = v * 16 + h;
                    }
                    b.append((char) v);
                    break;
                default: throw bad("a bad escape");
            }
        }
    }

    private Double number() throws IOException {
        StringBuilder b = new StringBuilder();
        while (true) {
            int c = peek();
            if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                b.append((char) next());
            } else {
                break;
            }
        }
        try {
            return Double.valueOf(b.toString());
        } catch (NumberFormatException e) {
            throw bad("a bad number");
        }
    }

    private void literal(String word) throws IOException {
        for (int i = 0; i < word.length(); i++) {
            if (next() != word.charAt(i)) throw bad("a bad literal");
        }
    }

    private void skipWs() throws IOException {
        int c;
        while ((c = peek()) == ' ' || c == '\n' || c == '\r' || c == '\t') next();
    }

    private int peek() throws IOException {
        if (peeked == -2) peeked = in.read();
        return peeked;
    }

    private int next() throws IOException {
        int c = peek();
        peeked = -2;
        return c;
    }

    private static Unreadable bad(String what) {
        return new Unreadable("the response is not valid JSON (" + what + ")");
    }

    // ------------------------------------------------------------------
    // Tree helpers (null-safe, like SimpleJson)
    // ------------------------------------------------------------------

    /** {@code m.get(key)} when {@code m} is an object, else {@code null}. */
    static Object get(Object m, String key) {
        return m instanceof Map ? ((Map<?, ?>) m).get(key) : null;
    }

    /** The string value, or {@code null} when absent or not a string. */
    static String str(Object v) {
        return v instanceof String ? (String) v : null;
    }

    /** The list value, or an empty list. */
    @SuppressWarnings("unchecked")
    static List<Object> list(Object v) {
        return v instanceof List ? (List<Object>) v : java.util.Collections.emptyList();
    }
}
