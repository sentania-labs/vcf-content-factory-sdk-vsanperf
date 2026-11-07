package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A vSphere JSON API fault: HTTP 500 with a JSON body carrying
 * {@code _typeName} (the fault class), {@code faultstring} and, for vSAN
 * validation faults, {@code faultMessage[]} of {@code {key, message}}
 * (verbatim sample {@code queryperf-fault-wildcardlimit-2026-11-06.json}).
 *
 * <p>Faults are deterministic: the client never re-sends a call that
 * answered with one, except {@code NotAuthenticated} (log in again once).
 * The message carries the fault class, the first fault key and the
 * server's faultstring, never a request body or header.
 */
final class VsanFault extends IOException {

    private static final long serialVersionUID = 1L;

    static final String NOT_AUTHENTICATED = "NotAuthenticated";
    static final String INVALID_REQUEST = "InvalidRequest";
    static final String INVALID_ARGUMENT = "InvalidArgument";
    static final String NOT_SUPPORTED = "NotSupported";
    static final String MANAGED_OBJECT_NOT_FOUND = "ManagedObjectNotFound";
    static final String INVALID_LOGIN = "InvalidLogin";

    /** Batching fault keys: a programming error if ever seen (design T1). */
    static final String KEY_PREFIX = "com.vmware.vsan.perfsvc.fault.queryperf.";

    final String faultClass;
    final String faultString;
    final List<String> keys;
    final int httpStatus;

    /**
     * A ContainerView moId, {@code session[<uuid>]<uuid>}: it embeds the vpxd
     * session key and must never reach a log, a property or a message
     * (design Logging; review B2). Scrubbed from every fault text.
     */
    private static final java.util.regex.Pattern VIEW_ID =
            java.util.regex.Pattern.compile("session\\[[^\\]]*\\][^\\s\"']*");

    /** {@code text} with any ContainerView moId replaced by {@code session[...]}. */
    static String scrub(String text) {
        return text == null ? null : VIEW_ID.matcher(text).replaceAll("session[...]");
    }

    VsanFault(String faultClass, String faultString, List<String> keys, int httpStatus) {
        super(describe(faultClass, scrub(faultString), keys, httpStatus));
        this.faultClass = faultClass;
        this.faultString = faultString == null ? "" : scrub(faultString);
        this.keys = Collections.unmodifiableList(new ArrayList<>(keys));
        this.httpStatus = httpStatus;
    }

    /**
     * The fault in a non-200 body, or {@code null} when the body is not a
     * JSON object with a {@code _typeName} (then only the status is known).
     */
    static VsanFault parse(String body, int httpStatus) {
        if (body == null || body.isEmpty()) return null;
        SimpleJson root;
        try {
            root = SimpleJson.parse(body);
        } catch (RuntimeException e) {
            return null;
        }
        if (!root.isObject()) return null;
        String cls = root.get("_typeName").asString();
        if (cls == null || cls.isEmpty()) return null;
        List<String> keys = new ArrayList<>();
        for (SimpleJson m : root.get("faultMessage").asList()) {
            String k = m.get("key").asString();
            if (k != null && !k.isEmpty()) keys.add(k);
        }
        return new VsanFault(cls, root.get("faultstring").asString(""), keys, httpStatus);
    }

    boolean is(String cls) {
        return cls.equals(faultClass);
    }

    /** The first fault key, or {@code null}. */
    String firstKey() {
        return keys.isEmpty() ? null : keys.get(0);
    }

    /**
     * A type this cluster does not offer (design "Learned types"):
     * {@code NotSupported}, or {@code InvalidArgument} whose faultstring
     * ends in {@code entityRefId}.
     */
    boolean isTypeUnavailable() {
        if (is(NOT_SUPPORTED)) return true;
        return is(INVALID_ARGUMENT) && faultString.trim().endsWith("entityRefId");
    }

    /** One of the query batching faults (wildcardlimit and its siblings). */
    boolean isBatchingFault() {
        for (String k : keys) {
            if (k.endsWith(".wildcardlimit") || k.endsWith(".querySpecsSizeLimitOnlyRegularQuery")
                    || k.endsWith(".queryspectypelimit")) {
                return true;
            }
        }
        return false;
    }

    private static String describe(String cls, String fs, List<String> keys, int status) {
        StringBuilder b = new StringBuilder("vSphere fault ").append(cls);
        if (!keys.isEmpty()) b.append(" [").append(keys.get(0)).append(']');
        if (fs != null && !fs.trim().isEmpty()) b.append(": ").append(shorten(fs.trim()));
        return b.append(" (HTTP ").append(status).append(')').toString();
    }

    private static String shorten(String s) {
        return s.length() <= 200 ? s : s.substring(0, 200) + "...";
    }
}
