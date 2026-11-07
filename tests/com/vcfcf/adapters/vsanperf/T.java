package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Assertion and fixture helpers for the plain-main tests (run from the repo root). */
final class T {

    private T() {}

    static void check(boolean cond, String what) {
        if (!cond) throw new AssertionError(what);
    }

    static void eq(Object want, Object got, String what) {
        if (want == null ? got != null : !want.equals(got)) {
            throw new AssertionError(what + ": want <" + want + "> got <" + got + ">");
        }
    }

    static void near(double want, double got, String what) {
        if (Math.abs(want - got) > 1e-9) {
            throw new AssertionError(what + ": want <" + want + "> got <" + got + ">");
        }
    }

    static Path fixture(String name) {
        return Paths.get("tests", "fixtures", name);
    }

    static String read(String name) throws IOException {
        return new String(Files.readAllBytes(fixture(name)), StandardCharsets.UTF_8);
    }

    /** A verbatim vendor capture from tests/fixtures/vsan-perfsvc-json/. */
    static String vendor(String file) throws IOException {
        return read("vsan-perfsvc-json/" + file);
    }

    interface Body {
        void run() throws Exception;
    }

    /** Expect {@code body} to throw {@code type}; returns it. */
    static <E extends Throwable> E expect(Class<E> type, Body body, String what) {
        try {
            body.run();
        } catch (Throwable t) {
            if (type.isInstance(t)) return type.cast(t);
            throw new AssertionError(what + ": want " + type.getSimpleName() + " got " + t, t);
        }
        throw new AssertionError(what + ": want " + type.getSimpleName() + ", nothing thrown");
    }

    /** A recording {@link Log}. */
    static final class RecLog implements Log {
        final StringBuilder all = new StringBuilder();

        @Override public synchronized void info(String m) { all.append("INFO ").append(m).append('\n'); }
        @Override public synchronized void warn(String m) { all.append("WARN ").append(m).append('\n'); }
        @Override public synchronized void error(String m) { all.append("ERROR ").append(m).append('\n'); }

        synchronized String text() {
            return all.toString();
        }

        synchronized int count(String needle) {
            int n = 0;
            for (int i = all.indexOf(needle); i >= 0; i = all.indexOf(needle, i + 1)) n++;
            return n;
        }
    }
}
