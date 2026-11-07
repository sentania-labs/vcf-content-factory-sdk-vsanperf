package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.util.Map;

/**
 * Runs one cycle and reports it, SDK-free so the failure path is tested:
 * the self-metrics and {@code Collection|last_error} go to the sink first,
 * then a failed cycle throws its credential-free message with the original
 * cause attached, so the framework maps an unreachable vCenter to DOWN
 * ({@link Failures#isUnreachable} walks the cause chain).
 */
final class CycleReport {

    private CycleReport() {}

    /** Where the self-metrics go (the adapter instance resource). */
    interface Sink {
        void metric(String key, double value);

        void property(String key, String value);
    }

    static final String LAST_ERROR = "Collection|last_error";

    /**
     * @throws IOException the cycle failed (message = {@code Result.failure},
     *         cause = {@code Result.failureCause})
     */
    static CycleEngine.Result run(CycleEngine engine, long deadlineMs, Sink sink)
            throws IOException, InterruptedException {
        CycleEngine.Result res = engine.run(deadlineMs);
        if (sink != null) {
            for (Map.Entry<String, Double> e : res.metrics().entrySet()) {
                sink.metric(e.getKey(), e.getValue());
            }
            sink.property(LAST_ERROR, res.lastErrorProperty());
        }
        if (res.failure != null) throw new IOException(res.failure, res.failureCause);
        return res;
    }
}
