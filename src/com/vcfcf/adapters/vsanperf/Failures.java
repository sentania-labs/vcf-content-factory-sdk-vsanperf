package com.vcfcf.adapters.vsanperf;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Unreachable (DOWN) versus failed (ERROR), after the template's
 * {@code ExampleFailures} (issue #13): walks the cause chain, cycle safe.
 */
final class Failures {

    private Failures() {}

    /** A connect failure, timeout, unknown host or no route anywhere in the chain. */
    static boolean isUnreachable(Throwable t) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Throwable c = t; c != null && seen.add(c); c = c.getCause()) {
            if (c instanceof java.net.ConnectException
                    || c instanceof java.net.http.HttpTimeoutException
                    || c instanceof java.net.SocketTimeoutException
                    || c instanceof java.net.UnknownHostException
                    || c instanceof java.net.NoRouteToHostException) {
                return true;
            }
        }
        return false;
    }
}
