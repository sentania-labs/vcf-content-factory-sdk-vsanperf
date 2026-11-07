package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.net.http.HttpTimeoutException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

/**
 * One adapter instance's collection engine (design "Collector shape").
 * Plain JDK: the vCenter and the Suite API are interfaces, so the tests
 * run it against fakes. State that outlives a cycle (dedup marks, learned
 * types, the Ops host map, the fairness cursor) lives here; a reconfigure
 * builds a new engine.
 *
 * <pre>
 * run()
 *   session + about (instanceUuid)         caller thread
 *   inventory: clusters, hosts, node uuids  caller thread, one read
 *   Ops host map (every 12th cycle, host set change, MoRef miss)
 *   one task per in-scope cluster -> bounded pool (N workers)
 *       task: host-domowner, host-domclient, vsan-pnic-net, serially;
 *             parse, resolve, discard the body, queue compact rows
 *   caller drains the queue, dedups, batches (100 hosts or ~1 MB), pushes;
 *   stops at the cycle deadline and pushes what completed
 * </pre>
 *
 * Only the caller thread pushes. A worker never holds more than one
 * response body.
 */
final class CycleEngine {

    // ------------------------------------------------------------------
    // Ports
    // ------------------------------------------------------------------

    /** The vCenter side ({@link VsanJsonClient}). */
    interface VcenterApi {
        /** Logs in when needed; the vCenter {@code about.instanceUuid}. */
        String instanceUuid(long timeoutMs) throws IOException, InterruptedException;

        Inventory readInventory(long timeoutMs) throws IOException, InterruptedException;

        PerfCall queryPerf(String clusterMoid, EntityType type, long startMs, long endMs,
                long timeoutMs) throws IOException, InterruptedException;
    }

    /** The VCF Operations side (the framework {@code SuiteApiStitcher}). */
    interface OpsApi extends OpsHostMap.Getter {
        PushOutcome pushStats(Map<String, Map<String, Double>> stats, long timestamp);
    }

    /** The framework batch result, without the framework type. */
    static final class PushOutcome {
        final int sent;
        final int batchesOk;
        final int batchesFailed;
        final boolean clean;

        PushOutcome(int sent, int batchesOk, int batchesFailed, boolean clean) {
            this.sent = sent;
            this.batchesOk = batchesOk;
            this.batchesFailed = batchesFailed;
            this.clean = clean;
        }
    }

    /** One answered perf call. */
    static final class PerfCall {
        final PerfParser.Result result;
        final int status;
        final long wireBytes;
        final long decodedBytes;

        PerfCall(PerfParser.Result result, int status, long wireBytes, long decodedBytes) {
            this.result = result;
            this.status = status;
            this.wireBytes = wireBytes;
            this.decodedBytes = decodedBytes;
        }
    }

    /** Engine settings, from the instance configuration. */
    static final class Settings {
        final ClusterFilter filter;
        final int workers;
        final long callTimeoutMs;
        final boolean perfLogging;

        Settings(ClusterFilter filter, int workers, long callTimeoutMs, boolean perfLogging) {
            this.filter = filter;
            this.workers = workers;
            this.callTimeoutMs = callTimeoutMs;
            this.perfLogging = perfLogging;
        }
    }

    /** The perf query window (design D3). */
    static final long WINDOW_MS = 7L * 60 * 1000;
    /** Ops host map refresh cadence in cycles (design call plan step 3). */
    static final int OPS_REFRESH_EVERY = 12;
    /** Push when the buffer holds this many resources... */
    static final int PUSH_RESOURCES = 100;
    /** ...or about this many bytes of JSON. */
    static final long PUSH_BYTES = 1_000_000L;

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    private final VcenterApi vc;
    private final OpsApi ops;
    private final Settings settings;
    private final Log log;
    private final LongSupplier clock;
    /**
     * Performance timing logging, re-read every cycle by the adapter (build
     * 9); starts at the configured value.
     */
    private volatile boolean perfLogging;

    void setPerfLogging(boolean on) {
        if (on != perfLogging) {
            log.info("Performance timing logging " + (on ? "on" : "off")
                    + " (instance setting perf_timing_logging)");
        }
        perfLogging = on;
    }

    boolean perfLogging() {
        return perfLogging;
    }

    /** Stop Collecting: ends the drain like the deadline. */
    private volatile java.util.function.BooleanSupplier stop = () -> false;

    void setStopSignal(java.util.function.BooleanSupplier s) {
        this.stop = s == null ? () -> false : s;
    }

    private final Watermarks marks = new Watermarks();
    private final LearnedTypes learned = new LearnedTypes();
    private long cycle;
    private String vcUuid;
    private OpsHostMap opsMap;
    private long opsMapCycle;
    private Set<String> opsMapHostSet = Collections.emptySet();
    private boolean opsRefreshForced;
    /** MoRefs that missed right after the last refresh: no new refresh for them. */
    private Set<String> missesSinceRefresh = Collections.emptySet();
    private boolean missWarnedSinceRefresh;
    private Set<String> unresolvedNodeWarned = new HashSet<>();
    /**
     * Fairness: the furthest cluster, in last cycle's order, that a worker
     * started, whatever its outcome (Codex review: advancing only on success
     * let a cluster that always runs past the deadline be first every cycle
     * and starve the ones after it).
     */
    private String lastAttempted;
    private final Set<String> skipInfoLogged = new HashSet<>();
    private final Set<String> uuidWarned = new HashSet<>();

    CycleEngine(VcenterApi vc, OpsApi ops, Settings settings, Log log, LongSupplier clockMs) {
        this.vc = vc;
        this.ops = ops;
        this.settings = settings;
        this.log = log == null ? Log.NONE : log;
        this.clock = clockMs;
        this.perfLogging = settings.perfLogging;
    }

    // ------------------------------------------------------------------
    // Result
    // ------------------------------------------------------------------

    /** The eleven self-metrics, and why the cycle failed ({@code null}: it did not). */
    static final class Result {
        int clustersInScope;
        int clustersOk;
        int clustersFailed;
        int clustersTimedOut;
        /** Unfinished because of Stop Collecting (log only; not a self-metric). */
        int clustersStopped;
        int perfCalls;
        int seriesPushed;
        int hostsUnresolved;
        int pushBatchesFailed;
        int pushBatchesOk;
        double cycleSeconds;
        /** perf calls / elapsed seconds of the perf phase (first call start to last call end). */
        double perfCallsPerSecond;
        /** Summed per-call wall ms / perf calls: latency, not throughput. */
        double perfCallMsAvg;
        /** Elapsed ms of the perf phase across all workers. */
        long perfPhaseMs;
        /** Summed per-call wall ms. */
        long perfCallMsTotal;
        /** Credential-free; {@code null} when nothing went wrong. */
        String lastError;
        /** Non-null: the cycle failed (the account row shows it). */
        String failure;
        /** The exception behind {@link #failure}, when there is one (DOWN versus ERROR). */
        Throwable failureCause;
        int duplicatesSkipped;

        /** Self-metric values by key (describe.xml {@code Collection} group). */
        Map<String, Double> metrics() {
            Map<String, Double> m = new LinkedHashMap<>();
            m.put("Collection|clusters_in_scope", (double) clustersInScope);
            m.put("Collection|clusters_ok", (double) clustersOk);
            m.put("Collection|clusters_failed", (double) clustersFailed);
            m.put("Collection|clusters_timed_out", (double) clustersTimedOut);
            m.put("Collection|perf_calls", (double) perfCalls);
            m.put("Collection|series_pushed", (double) seriesPushed);
            m.put("Collection|hosts_unresolved", (double) hostsUnresolved);
            m.put("Collection|push_batches_failed", (double) pushBatchesFailed);
            m.put("Collection|cycle_seconds", cycleSeconds);
            m.put("Collection|perf_calls_per_second", perfCallsPerSecond);
            m.put("Collection|perf_call_ms_avg", perfCallMsAvg);
            return m;
        }

        String lastErrorProperty() {
            return lastError == null ? "none" : lastError;
        }

        void error(String e) {
            if (lastError == null) lastError = e;
        }
    }

    // ------------------------------------------------------------------
    // Worker messages
    // ------------------------------------------------------------------

    /** Rows of one entity, resolved to a resource id. */
    private static final class EntityRows {
        final String markKey;
        final String resourceId;
        final List<String> keys = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        final List<Double> values = new ArrayList<>();

        EntityRows(String markKey, String resourceId) {
            this.markKey = markKey;
            this.resourceId = resourceId;
        }
    }

    private abstract static class Msg {
        final Inventory.Cluster cluster;

        Msg(Inventory.Cluster cluster) {
            this.cluster = cluster;
        }
    }

    private static final class TypeDone extends Msg {
        final List<EntityRows> rows;
        final Set<String> unresolvedNodes;
        final Set<String> missedMoids;

        TypeDone(Inventory.Cluster c, List<EntityRows> rows, Set<String> unresolvedNodes,
                Set<String> missedMoids) {
            super(c);
            this.rows = rows;
            this.unresolvedNodes = unresolvedNodes;
            this.missedMoids = missedMoids;
        }
    }

    enum Outcome { OK, FAILED, TIMED_OUT }

    private static final class ClusterDone extends Msg {
        final Outcome outcome;
        final int calls;
        final long callMs;
        final long wallMs;
        final String warn;
        final String fatal;
        /** A VirtualMachineError or thread kill in the worker: rethrown on the caller thread. */
        final Error fatalError;

        ClusterDone(Inventory.Cluster c, Outcome outcome, int calls, long callMs, long wallMs,
                String warn, String fatal, Error fatalError) {
            super(c);
            this.fatalError = fatalError;
            this.outcome = outcome;
            this.calls = calls;
            this.callMs = callMs;
            this.wallMs = wallMs;
            this.warn = warn;
            this.fatal = fatal;
        }
    }

    // ------------------------------------------------------------------
    // Cycle
    // ------------------------------------------------------------------

    /**
     * Run one cycle. Never throws for a collection failure: the
     * {@link Result#failure} says the cycle failed, so the adapter can
     * push the self-metrics first. An interrupt (stop) is rethrown.
     *
     * @param deadlineMs absolute clock time the cycle must end by
     */
    Result run(long deadlineMs) throws InterruptedException {
        long t0 = clock.getAsLong();
        long heapBefore = usedHeap();
        long id = ++cycle;
        Result res = new Result();
        long pushMs[] = {0};
        try {
            // Step 0: session and the vCenter's identity.
            String uuid;
            try {
                uuid = vc.instanceUuid(budget(deadlineMs));
            } catch (IOException e) {
                res.failure = "vCenter login or ServiceInstance read failed: " + e.getMessage();
                res.failureCause = e;
                return res;
            }
            if (uuid == null || uuid.trim().isEmpty()) {
                res.failure = "the vCenter instance UUID (about.instanceUuid) is empty or "
                        + "unreadable; nothing is pushed, since hosts are matched on MoRef "
                        + "plus vCenter UUID only";
                log.error(res.failure);
                return res;
            }
            if (vcUuid != null && !vcUuid.equals(uuid)) {
                log.warn("the vCenter instance UUID changed; forgetting the host map, "
                        + "dedup marks and learned types");
                opsMap = null;
                marks.clear();
                learned.clear();
            }
            vcUuid = uuid;
            if (ops == null) {
                res.failure = "the local VCF Operations Suite API is not available to this "
                        + "adapter instance; nothing can be pushed onto HostSystem";
                return res;
            }

            // Steps 1+2: inventory.
            long ti = clock.getAsLong();
            Inventory inv;
            try {
                inv = vc.readInventory(budget(deadlineMs));
            } catch (IOException e) {
                res.failure = "vSAN cluster inventory read failed: " + e.getMessage();
                res.failureCause = e;
                return res;
            }
            List<Inventory.Cluster> scope = inScope(inv);
            Map<String, String> nodeToHost = nodeMap(scope);
            int hosts = 0;
            for (Inventory.Cluster c : scope) hosts += c.hosts.size();
            perf("PERF inventory cycle=" + id + " read=clusters_hosts ms="
                    + (clock.getAsLong() - ti) + " clusters=" + inv.clusters.size()
                    + " in_scope=" + scope.size() + " hosts=" + hosts + " nodes="
                    + nodeToHost.size() + " pages=" + pagesOf(vc));
            res.clustersInScope = scope.size();

            // Step 3: Ops host map.
            Set<String> hostSet = new TreeSet<>(nodeToHost.values());
            if (opsMap == null || opsRefreshForced || !hostSet.equals(opsMapHostSet)
                    || cycle - opsMapCycle >= OPS_REFRESH_EVERY) {
                long to = clock.getAsLong();
                try {
                    OpsHostMap m = OpsHostMap.load(ops, uuid);
                    opsMap = m;
                    opsMapCycle = cycle;
                    opsMapHostSet = hostSet;
                    opsRefreshForced = false;
                    missWarnedSinceRefresh = false;
                    missesSinceRefresh = null;
                    unresolvedNodeWarned.clear();
                    log.info("Ops host map: " + m);
                    perf("PERF inventory cycle=" + id + " read=ops_resources ms="
                            + (clock.getAsLong() - to) + " count=" + m.size());
                } catch (IOException e) {
                    opsRefreshForced = true;
                    if (opsMap == null) {
                        res.failure = "the VCF Operations HostSystem list could not be read: "
                                + e.getMessage();
                        return res;
                    }
                    log.warn("the VCF Operations HostSystem list could not be read ("
                            + e.getMessage() + "); keeping the previous map this cycle");
                    res.error("HostSystem list read failed; previous map kept");
                }
            }
            if (scope.isEmpty()) {
                log.info("cycle " + id + ": no vSAN cluster in scope (" + settings.filter
                        + "); nothing to collect");
                return res;
            }

            // Steps 4 and 5.
            runPool(id, scope, nodeToHost, opsMap, deadlineMs, res, pushMs);
            if (res.perfCalls > 0) {
                if (res.perfPhaseMs > 0) {
                    res.perfCallsPerSecond = res.perfCalls * 1000.0 / res.perfPhaseMs;
                }
                res.perfCallMsAvg = (double) res.perfCallMsTotal / res.perfCalls;
            }
            if (res.failure == null && res.clustersFailed == res.clustersInScope) {
                res.failure = "every vSAN cluster in scope failed (" + res.clustersFailed
                        + "); first: " + res.lastError;
            }
            marks.prune(clock.getAsLong());
            return res;
        } catch (RuntimeException e) {
            // Review N8: a defect on the caller thread still fails the cycle
            // through Result, so the adapter pushes the self-metrics and
            // last_error first. Fixed text: the exception message may quote
            // server data.
            res.failure = "internal error in the collection cycle ("
                    + e.getClass().getSimpleName() + ")";
            res.failureCause = e;
            log.error(res.failure);
            return res;
        } finally {
            res.cycleSeconds = (clock.getAsLong() - t0) / 1000.0;
            if (res.failure != null) res.error(res.failure);
            log.info("cycle " + id + " summary: clusters ok=" + res.clustersOk + " failed="
                    + res.clustersFailed + " timed_out=" + res.clustersTimedOut
                    + (res.clustersStopped > 0 ? " stopped=" + res.clustersStopped : "") + " of "
                    + res.clustersInScope + "; perf calls=" + res.perfCalls + "; series pushed="
                    + res.seriesPushed + " (" + res.duplicatesSkipped + " already pushed); "
                    + "hosts unresolved=" + res.hostsUnresolved + "; push batches ok="
                    + res.pushBatchesOk + " failed=" + res.pushBatchesFailed + "; "
                    + String.format(java.util.Locale.ROOT, "%.1f", res.cycleSeconds) + " s"
                    + (res.failure != null ? "; FAILED: " + res.failure : ""));
            perf("PERF cycle cycle=" + id + " clusters=" + res.clustersInScope + " perf_calls="
                    + res.perfCalls + " perf_phase_ms=" + res.perfPhaseMs
                    + " calls_per_s=" + String.format(java.util.Locale.ROOT, "%.2f",
                            res.perfCallsPerSecond)
                    + " call_ms_sum=" + res.perfCallMsTotal + " call_ms_avg="
                    + String.format(java.util.Locale.ROOT, "%.1f", res.perfCallMsAvg)
                    + " push_ms=" + pushMs[0] + " cycle_ms=" + Math.round(res.cycleSeconds * 1000)
                    + " heap_before=" + heapBefore + " heap_after=" + usedHeap());
        }
    }

    /** vSAN clusters after the filter, rotated so the start follows last cycle's last completion. */
    private List<Inventory.Cluster> inScope(Inventory inv) {
        TreeMap<String, Inventory.Cluster> sorted = new TreeMap<>();
        for (Inventory.Cluster c : inv.clusters) {
            if (!settings.filter.accepts(c.moid, c.name)) continue;
            if (!c.isVsan()) {
                if (skipInfoLogged.add(c.moid)) {
                    log.info("cluster " + c.moid + " (" + c.name + ") is not vSAN-enabled "
                            + "(no member host with vSAN enabled and a vSAN cluster uuid); "
                            + "skipped");
                }
                continue;
            }
            skipInfoLogged.remove(c.moid);
            if (c.uuidDisagrees() && uuidWarned.add(c.moid)) {
                log.warn("cluster " + c.moid + ": member hosts report different vSAN cluster "
                        + "uuids; using the majority value " + c.vsanUuid());
            }
            sorted.put(c.moid, c);
        }
        List<Inventory.Cluster> order = new ArrayList<>(sorted.values());
        if (lastAttempted != null && order.size() > 1) {
            String start = sorted.higherKey(lastAttempted);
            if (start != null) {
                int i = new ArrayList<>(sorted.keySet()).indexOf(start);
                Collections.rotate(order, -i);
            }
        }
        return order;
    }

    /** vSAN node uuid to host MoRef; an empty node uuid is left out, a duplicate is dropped. */
    private Map<String, String> nodeMap(List<Inventory.Cluster> scope) {
        Map<String, String> m = new HashMap<>();
        Set<String> dup = new HashSet<>();
        for (Inventory.Cluster c : scope) {
            for (Inventory.Host h : c.hosts) {
                if (h.nodeUuid == null) continue;
                if (dup.contains(h.nodeUuid)) continue;
                String prev = m.put(h.nodeUuid, h.moid);
                if (prev != null && !prev.equals(h.moid)) {
                    m.remove(h.nodeUuid);
                    dup.add(h.nodeUuid);
                    log.warn("vSAN node uuid " + h.nodeUuid + " is reported by two hosts ("
                            + prev + ", " + h.moid + "); matched to neither");
                }
            }
        }
        return m;
    }

    private void runPool(long id, List<Inventory.Cluster> scope, Map<String, String> nodeToHost,
            OpsHostMap map, long deadlineMs, Result res, long[] pushMs)
            throws InterruptedException {
        int workers = Math.max(1, Math.min(settings.workers, scope.size()));
        BlockingQueue<Msg> queue = new ArrayBlockingQueue<>(Math.max(8, 4 * workers));
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        CallSpan span = new CallSpan(); // this cycle's only; stragglers of a cut cycle keep theirs
        int maxDepth = 0;
        long windowEnd = clock.getAsLong();
        long windowStart = windowEnd - WINDOW_MS;
        ExecutorService pool = Executors.newFixedThreadPool(workers, threads(id));
        Map<String, Future<?>> futures = new LinkedHashMap<>();
        long submitAt = clock.getAsLong();
        AtomicInteger furthestStarted = new AtomicInteger(-1);
        for (int idx = 0; idx < scope.size(); idx++) {
            final Inventory.Cluster c = scope.get(idx);
            final int position = idx;
            futures.put(c.moid, pool.submit(() -> {
                furthestStarted.accumulateAndGet(position, Math::max);
                int now = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(now, Math::max);
                try {
                    clusterTask(id, c, nodeToHost, map, windowStart, windowEnd, deadlineMs,
                            queue, clock.getAsLong() - submitAt, span);
                } finally {
                    inFlight.decrementAndGet();
                }
            }));
        }
        pool.shutdown();

        PushBuffer buf = new PushBuffer();
        Set<String> done = new HashSet<>();
        Set<String> unresolvedNodes = new TreeSet<>();
        Set<String> missed = new TreeSet<>();
        long callMs = 0;
        String fatal = null;
        Error fatalError = null;
        boolean cut = false;
        boolean stopped = false;
        try {
            while (done.size() < scope.size()) {
                long left = deadlineMs - clock.getAsLong();
                if (stop.getAsBoolean()) {
                    cut = true;
                    stopped = true;
                    break;
                }
                if (left <= 0) {
                    cut = true;
                    break;
                }
                Msg m = queue.poll(Math.min(left, 1000), TimeUnit.MILLISECONDS);
                maxDepth = Math.max(maxDepth, queue.size() + (m == null ? 0 : 1));
                if (m == null) continue;
                callMs += handle(id, m, buf, res, done, unresolvedNodes, missed, pushMs);
                if (m instanceof ClusterDone && ((ClusterDone) m).fatalError != null) {
                    fatalError = ((ClusterDone) m).fatalError;
                    break;
                }
                if (m instanceof ClusterDone && ((ClusterDone) m).fatal != null) {
                    fatal = ((ClusterDone) m).fatal;
                    break;
                }
            }
        } finally {
            if (cut || fatal != null || fatalError != null || done.size() < scope.size()) {
                for (Future<?> f : futures.values()) f.cancel(true);
                pool.shutdownNow();
                // What completed before the cut is still pushed.
                Msg m;
                while ((m = queue.poll()) != null) {
                    callMs += handle(id, m, buf, res, done, unresolvedNodes, missed, pushMs);
                }
            }
        }
        // The next cycle starts after the furthest cluster attempted here,
        // completed, failed, timed out or cut.
        int furthest = furthestStarted.get();
        if (furthest >= 0) lastAttempted = scope.get(furthest).moid;
        if (fatalError != null) {
            // Review B1: the pool is shut down above; the error ends the cycle.
            log.error("cycle " + id + ": " + fatalError.getClass().getSimpleName()
                    + " in a collection worker; the cycle ends");
            throw fatalError;
        }
        if (cut) {
            int unfinished = 0;
            for (Inventory.Cluster c : scope) {
                if (!done.contains(c.moid)) {
                    unfinished++;
                    perf("PERF cluster cycle=" + id + " cluster=" + c.moid + " hosts="
                            + c.hosts.size() + " result=" + (stopped ? "stopped" : "timeout")
                            + " reason=" + (stopped ? "stop_collecting" : "cycle_deadline"));
                }
            }
            if (stopped) {
                // Review N1: a stop is not a timeout.
                res.clustersStopped += unfinished;
                log.warn("cycle " + id + ": stopped (Stop Collecting) with " + unfinished
                        + " cluster(s) unfinished; completed results are pushed");
                res.error("stopped by Stop Collecting; " + unfinished + " cluster(s) unfinished");
            } else {
                res.clustersTimedOut += unfinished;
                log.warn("cycle " + id + ": cycle deadline reached with " + unfinished
                        + " cluster(s) unfinished; completed results are pushed");
                res.error("cycle deadline reached; " + unfinished + " cluster(s) unfinished");
            }
        }
        if (fatal != null) {
            int rest = Math.max(0, scope.size() - done.size());
            res.clustersFailed += rest;
            res.failure = fatal;
        }
        flush(id, buf, res, pushMs);
        perf("PERF pool cycle=" + id + " workers=" + workers + " max_in_flight="
                + maxInFlight.get() + " max_queue_depth=" + maxDepth);
        identityAfterCycle(unresolvedNodes, missed, res);
        res.perfCallMsTotal = callMs;
        long first = span.start.get();
        long last = span.end.get();
        res.perfPhaseMs = first == Long.MAX_VALUE || last < first ? 0 : last - first;
    }

    /**
     * First perf call start and last perf call end of ONE cycle, across its
     * workers (review N2: a per-cycle object, so a straggler worker of a cut
     * cycle cannot move the next cycle's call rate).
     */
    private static final class CallSpan {
        final java.util.concurrent.atomic.AtomicLong start =
                new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);
        final java.util.concurrent.atomic.AtomicLong end =
                new java.util.concurrent.atomic.AtomicLong(Long.MIN_VALUE);
    }

    /** Apply one worker message on the caller thread. @return its perf-call ms */
    private long handle(long id, Msg m, PushBuffer buf, Result res, Set<String> done,
            Set<String> unresolvedNodes, Set<String> missed, long[] pushMs) {
        if (m instanceof TypeDone) {
            TypeDone t = (TypeDone) m;
            unresolvedNodes.addAll(t.unresolvedNodes);
            missed.addAll(t.missedMoids);
            for (EntityRows er : t.rows) {
                for (int i = 0; i < er.keys.size(); i++) {
                    // Per (entityRefId, label): one label's newer bucket never
                    // hides another label's value for that bucket.
                    String markKey = Watermarks.key(er.markKey, er.labels.get(i));
                    long ts = er.times.get(i);
                    if (ts <= marks.last(markKey)) {
                        res.duplicatesSkipped++;
                        continue;
                    }
                    Long full = buf.add(er.resourceId, er.keys.get(i), ts, er.values.get(i));
                    if (full != null) pushGroup(id, buf, full, res, pushMs);
                    // Review N5: recorded only once the pushes are known clean (flush).
                    buf.pendingMarks.merge(markKey, ts, Math::max);
                }
            }
            return 0;
        }
        ClusterDone d = (ClusterDone) m;
        if (!done.add(d.cluster.moid)) return 0;
        res.perfCalls += d.calls;
        switch (d.outcome) {
            case OK: res.clustersOk++; break;
            case FAILED: res.clustersFailed++; break;
            default: res.clustersTimedOut++; break;
        }
        if (d.warn != null) {
            log.warn("cluster " + d.cluster.moid + " (" + d.cluster.name + "): " + d.warn);
            res.error("cluster " + d.cluster.moid + ": " + d.warn);
        }
        perf("PERF cluster cycle=" + id + " cluster=" + d.cluster.moid + " hosts="
                + d.cluster.hosts.size() + " calls=" + d.calls + " wall_ms=" + d.wallMs
                + " result=" + d.outcome.name().toLowerCase(java.util.Locale.ROOT));
        return d.callMs;
    }

    /**
     * After the drain: count unresolved hosts, WARN once per refresh, and
     * force an Ops map refresh on a new (MoRef, VCID) miss.
     */
    private void identityAfterCycle(Set<String> unresolvedNodes, Set<String> missed, Result res) {
        res.hostsUnresolved = unresolvedNodes.size() + missed.size();
        Set<String> newNodes = new TreeSet<>(unresolvedNodes);
        newNodes.removeAll(unresolvedNodeWarned);
        if (!newNodes.isEmpty()) {
            unresolvedNodeWarned.addAll(newNodes);
            log.warn(newNodes.size() + " vSAN node uuid(s) in the performance data match no "
                    + "host of the inventory; their data is dropped (first: "
                    + newNodes.iterator().next() + ")");
        }
        if (missed.isEmpty()) return;
        if (missesSinceRefresh == null) {
            // First cycle after a refresh: these misses are the map's answer.
            missesSinceRefresh = new TreeSet<>(missed);
            if (!missWarnedSinceRefresh) {
                missWarnedSinceRefresh = true;
                log.warn(missed.size() + " host(s) have no VMWARE HostSystem object of this "
                        + "vCenter in VCF Operations (is the vCenter adapter collecting "
                        + "them?); their data is not pushed (first: " + missed.iterator().next()
                        + ")");
            }
            if (opsMapCycle != cycle) opsRefreshForced = true;
            return;
        }
        if (!missesSinceRefresh.containsAll(missed)) {
            opsRefreshForced = true;
        }
    }

    // ------------------------------------------------------------------
    // Worker
    // ------------------------------------------------------------------

    private void clusterTask(long id, Inventory.Cluster c, Map<String, String> nodeToHost,
            OpsHostMap map, long windowStart, long windowEnd, long deadlineMs,
            BlockingQueue<Msg> queue, long queueWaitMs, CallSpan span) {
        long t0 = clock.getAsLong();
        int calls = 0;
        long callMs = 0;
        Outcome outcome = Outcome.OK;
        String warn = null;
        String fatal = null;
        Error fatalError = null;
        String worker = Thread.currentThread().getName();
        try {
            for (EntityType type : EntityType.values()) {
                if (Thread.currentThread().isInterrupted()) {
                    outcome = Outcome.TIMED_OUT;
                    break;
                }
                long now = clock.getAsLong();
                if (learned.skip(c.moid, type, now)) continue;
                long left = deadlineMs - now;
                if (left <= 0) {
                    outcome = Outcome.TIMED_OUT;
                    break;
                }
                long budget = Math.min(settings.callTimeoutMs, left);
                long tc = clock.getAsLong();
                span.start.accumulateAndGet(tc, Math::min);
                calls++;
                PerfCall pc = null;
                String faultKey = "-";
                int status = 0;
                try {
                    pc = vc.queryPerf(c.moid, type, windowStart, windowEnd, budget);
                    status = pc.status;
                } catch (VsanFault f) {
                    faultKey = f.firstKey() != null ? f.firstKey() : f.faultClass;
                    status = f.httpStatus;
                    if (f.is(VsanFault.NOT_AUTHENTICATED)) {
                        fatal = "vCenter refused the session after a fresh login "
                                + "(NotAuthenticated twice)";
                        outcome = Outcome.FAILED;
                        break;
                    } else if (f.isTypeUnavailable()) {
                        if (learned.learn(c.moid, type, clock.getAsLong())) {
                            log.info("cluster " + c.moid + " does not offer " + type.id + " ("
                                    + f.faultClass + "); not queried there for 24 h");
                        }
                    } else if (f.isBatchingFault()) {
                        log.error("cluster " + c.moid + " " + type.id + ": query batching fault "
                                + f.firstKey() + " (a defect in this pack)");
                        outcome = Outcome.FAILED;
                        warn = first(warn, type.id + ": " + f.getMessage());
                    } else {
                        outcome = Outcome.FAILED;
                        warn = first(warn, type.id + ": " + f.getMessage());
                        if (f.is(VsanFault.MANAGED_OBJECT_NOT_FOUND)) break;
                    }
                } catch (HttpTimeoutException e) {
                    faultKey = "timeout";
                    outcome = Outcome.TIMED_OUT;
                    warn = first(warn, type.id + ": " + e.getMessage());
                    break;
                } catch (InterruptedException e) {
                    outcome = Outcome.TIMED_OUT;
                    Thread.currentThread().interrupt();
                    break;
                } catch (IOException | RuntimeException e) {
                    faultKey = e.getClass().getSimpleName();
                    if (outcome == Outcome.OK) outcome = Outcome.FAILED;
                    warn = first(warn, type.id + ": " + e.getMessage());
                } finally {
                    long tEnd = clock.getAsLong();
                    span.end.accumulateAndGet(tEnd, Math::max);
                    long ms = tEnd - tc;
                    callMs += ms;
                    perf("PERF call cycle=" + id + " cluster=" + c.moid + " type=" + type.id
                            + " worker=" + worker + " queue_wait_ms=" + queueWaitMs + " wall_ms="
                            + ms + " status=" + status + " wire_bytes="
                            + (pc == null ? 0 : pc.wireBytes) + " decoded_bytes="
                            + (pc == null ? 0 : pc.decodedBytes) + " entities="
                            + (pc == null ? 0 : pc.result.entities.size()) + " series_kept="
                            + (pc == null ? 0 : pc.result.seriesKept) + " fault=" + faultKey);
                }
                if (pc != null) {
                    if (pc.result.malformed > 0) {
                        log.warn("cluster " + c.moid + " " + type.id + ": "
                                + pc.result.malformed + " entity id(s) not in the expected form; "
                                + "skipped");
                    }
                    if (!put(queue, rows(c, type, pc.result, nodeToHost, map), deadlineMs)) {
                        outcome = Outcome.TIMED_OUT;
                        break;
                    }
                }
            }
        } catch (VirtualMachineError e) {
            // Review B1: never swallowed. Carried to the caller thread, which
            // shuts the pool down and rethrows it.
            outcome = Outcome.FAILED;
            fatalError = e;
        } catch (Throwable t) {
            if (t instanceof Error && "java.lang.ThreadDeath".equals(t.getClass().getName())) {
                outcome = Outcome.FAILED;
                fatalError = (Error) t; // a thread kill, same as above
                return;
            }
            // Review B1: anything else outside the per-call handling (the
            // post-call path: rows, queueing) fails the cluster, never OK.
            outcome = Outcome.FAILED;
            warn = first(warn, "internal error (" + t.getClass().getSimpleName() + ")");
        } finally {
            ClusterDone d = new ClusterDone(c, outcome, calls, callMs, clock.getAsLong() - t0,
                    warn, fatal, fatalError);
            if (!put(queue, d, deadlineMs) && fatalError != null) {
                queue.offer(d);
            }
        }
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    /** Resolve one response to compact rows; the parsed response is dropped after. */
    private TypeDone rows(Inventory.Cluster c, EntityType type, PerfParser.Result r,
            Map<String, String> nodeToHost, OpsHostMap map) {
        List<EntityRows> out = new ArrayList<>();
        Set<String> unresolved = new HashSet<>();
        Set<String> missed = new HashSet<>();
        for (PerfParser.Entity e : r.entities) {
            String moid = nodeToHost.get(e.ref.node);
            if (moid == null) {
                unresolved.add(e.ref.node);
                continue;
            }
            String rid = map == null ? null : map.resourceId(moid);
            if (rid == null) {
                missed.add(moid);
                continue;
            }
            EntityRows er = new EntityRows(Watermarks.key(e.ref), rid);
            for (PerfParser.Sample s : e.samples) {
                er.keys.add(type.statKey(e.ref.instance, s.metric));
                er.labels.add(s.metric.label);
                er.times.add(s.timestamp);
                er.values.add(s.value);
            }
            out.add(er);
        }
        return new TypeDone(c, out, unresolved, missed);
    }

    /** Queue a message, waiting no later than the deadline. */
    private boolean put(BlockingQueue<Msg> q, Msg m, long deadlineMs) {
        try {
            long left = deadlineMs - clock.getAsLong();
            return q.offer(m, Math.max(left, 1), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return q.offer(m);
        }
    }

    // ------------------------------------------------------------------
    // Push
    // ------------------------------------------------------------------

    /**
     * Values to push, grouped by sample time first: {@code pushStatsBatch}
     * stamps every value of a call with the call's one timestamp, and rows
     * of one cycle carry different sample times (a lagging host, a series
     * whose newest non-None value is an older bucket). The caps (100 hosts,
     * about 1 MB) apply within each group; a group that reaches them is
     * pushed on its own, one {@code pushStatsBatch} call per group.
     */
    private static final class PushBuffer {
        static final class Group {
            final Map<String, Map<String, Double>> byResource = new LinkedHashMap<>();
            long bytes;
            int values;
        }

        final Map<Long, Group> byTime = new TreeMap<>();
        /** Dedup marks of every value buffered this cycle; committed at the end if all pushes were clean. */
        final Map<String, Long> pendingMarks = new HashMap<>();
        boolean allClean = true;

        /** @return the group's time when it reached a cap, else {@code null} */
        Long add(String rid, String key, long ts, double v) {
            Group g = byTime.computeIfAbsent(ts, k -> new Group());
            g.byResource.computeIfAbsent(rid, k -> new LinkedHashMap<>()).put(key, v);
            g.bytes += key.length() + 48;
            g.values++;
            return g.byResource.size() >= PUSH_RESOURCES || g.bytes >= PUSH_BYTES ? ts : null;
        }
    }

    private int pushNo;

    /** Push one time group and drop it from the buffer. */
    private void pushGroup(long id, PushBuffer buf, long ts, Result res, long[] pushMs) {
        PushBuffer.Group g = buf.byTime.remove(ts);
        if (g == null || g.values == 0) return;
        long t = clock.getAsLong();
        PushOutcome o = ops.pushStats(g.byResource, ts);
        long ms = clock.getAsLong() - t;
        pushMs[0] += ms;
        res.seriesPushed += g.values;
        res.pushBatchesOk += o.batchesOk;
        res.pushBatchesFailed += o.batchesFailed;
        if (!o.clean) {
            buf.allClean = false;
            log.warn("push of " + g.values + " value(s) for " + g.byResource.size()
                    + " host(s) was not clean: " + o.batchesFailed + " batch(es) failed");
            res.error("Suite API push not clean (" + o.batchesFailed + " batch(es) failed)");
        }
        perf("PERF push cycle=" + id + " batch=" + (++pushNo) + " sample_time=" + ts
                + " resources=" + g.byResource.size() + " stat_values=" + g.values + " bytes~"
                + g.bytes + " ms=" + ms + " result=" + (o.clean ? "ok" : "failed"));
    }

    /**
     * End of cycle: push every remaining group, then commit the dedup marks
     * (review N5) only if every push of the cycle was clean. Not clean: no
     * mark is recorded, so the next cycle re-sends these buckets while they
     * are still in the 7-minute window (a value that did land is re-sent with
     * the same time, which overwrites).
     */
    private void flush(long id, PushBuffer buf, Result res, long[] pushMs) {
        for (Long ts : new ArrayList<>(buf.byTime.keySet())) {
            pushGroup(id, buf, ts, res, pushMs);
        }
        if (buf.allClean) {
            for (Map.Entry<String, Long> m : buf.pendingMarks.entrySet()) {
                marks.record(m.getKey(), m.getValue());
            }
        }
        buf.pendingMarks.clear();
        buf.allClean = true;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private long budget(long deadlineMs) {
        long left = deadlineMs - clock.getAsLong();
        return Math.max(1, Math.min(settings.callTimeoutMs, left));
    }

    private void perf(String line) {
        if (perfLogging) log.info(line);
    }

    private static int pagesOf(VcenterApi vc) {
        return vc instanceof VsanJsonClient ? ((VsanJsonClient) vc).lastInventoryPages() : 1;
    }

    private static long usedHeap() {
        Runtime rt = Runtime.getRuntime();
        return rt.totalMemory() - rt.freeMemory();
    }

    private static ThreadFactory threads(long cycleId) {
        AtomicInteger n = new AtomicInteger();
        return r -> {
            Thread t = new Thread(r, "vsanperf-c" + cycleId + "-w" + n.incrementAndGet());
            t.setDaemon(true);
            return t;
        };
    }

    // For tests.
    int marksSize() {
        return marks.size();
    }

    boolean opsRefreshPending() {
        return opsRefreshForced;
    }
}
