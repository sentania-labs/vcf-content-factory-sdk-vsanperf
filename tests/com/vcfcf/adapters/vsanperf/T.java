package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/** Assertion and fixture helpers for the plain-main tests (run from the repo root). */
final class T {

    private T() {}

    /**
     * Build 22: the simulated clock every engine test runs on. It starts at
     * the real time (so dedup-mark pruning and learned-type windows see
     * realistic times) and moves only when a fixture moves it (FakeVc
     * delays), so no cycle deadline or per-call budget depends on how fast
     * the runner is.
     */
    static final java.util.concurrent.atomic.AtomicLong CLOCK =
            new java.util.concurrent.atomic.AtomicLong(System.currentTimeMillis());

    /** A deadline {@code ms} after the simulated now. */
    static long in(long ms) {
        return CLOCK.get() + ms;
    }

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
