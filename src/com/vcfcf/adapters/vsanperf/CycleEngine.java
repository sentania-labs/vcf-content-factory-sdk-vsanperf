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
 *   Ops cluster map, cluster tier on (every 12th cycle, cluster set change,
 *       a MoRef miss at most once per 12 cycles)
 *   Ops VM map, VM tier on, on cycles with a VM run (every 12th cycle,
 *       a VM read that changed a VM set, a MoRef miss at most once per 12)
 *   one task per in-scope cluster -> bounded pool (N workers)
 *       task: the types in scope, serially: core (host-domowner,
 *             host-domclient, vsan-pnic-net), cluster, host, disk tier,
 *             and the VM tier on the cluster's VM cycle; the disk map and
 *             VM read lazily before the first type that needs them; VM
 *             calls one at a time across the pool; parse from the stream,
 *             resolve, discard the body, queue compact rows
 *   caller drains the queue, dedups, batches per target kind and sample
 *   time (100 resources or ~1 MB), pushes; stops at the cycle deadline and
 *   pushes what completed
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

        /** The R2 read; with the disk tier on, hosts carry {@code configManager.vsanSystem}. */
        Inventory readInventory(long timeoutMs, boolean withVsanSystem)
                throws IOException, InterruptedException;

        /** Build 1 shape: the R2 read without the vsanSystem path. */
        default Inventory readInventory(long timeoutMs) throws IOException, InterruptedException {
            return readInventory(timeoutMs, false);
        }

        /** {@code QueryDisksForVsan} on one host: its non-empty vSAN disk uuids (disk tier). */
        List<String> queryDisksForVsan(String vsanSystemMoid, long timeoutMs)
                throws IOException, InterruptedException;

        /** The paged VM read over a cluster-rooted ContainerView (VM tier). */
        VmInventory readVms(String clusterMoid, boolean withDevices, long timeoutMs)
                throws IOException, InterruptedException;

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
        /** The types queried per cluster, in collection order. */
        final List<EntityType> types;
        /** VM tier cadence: a cluster runs its VM types every Nth cycle, staggered. */
        final int vmEveryN;

        /** Build 1 behaviour: the three core types only (all tiers off). */
        Settings(ClusterFilter filter, int workers, long callTimeoutMs, boolean perfLogging) {
            this(filter, workers, callTimeoutMs, perfLogging,
                    EntityType.ofTiers(java.util.EnumSet.of(EntityType.Tier.CORE)));
        }

        Settings(ClusterFilter filter, int workers, long callTimeoutMs, boolean perfLogging,
                List<EntityType> types) {
            this(filter, workers, callTimeoutMs, perfLogging, types,
                    VsanPerfConfig.DEFAULT_VM_EVERY_N);
        }

        Settings(ClusterFilter filter, int workers, long callTimeoutMs, boolean perfLogging,
                List<EntityType> types, int vmEveryN) {
            this.filter = filter;
            this.workers = workers;
            this.callTimeoutMs = callTimeoutMs;
            this.perfLogging = perfLogging;
            this.types = Collections.unmodifiableList(new ArrayList<>(types));
            this.vmEveryN = Math.max(1, vmEveryN);
        }

        /** VM-tier types in scope that need the device list ({@code virtual-disk}). */
        boolean needsDevices() {
            return types.contains(EntityType.VIRTUAL_DISK);
        }

        boolean has(EntityType.Tier tier) {
            for (EntityType t : types) if (t.tier == tier) return true;
            return false;
        }
    }

    /** The perf query window (design D3). */
    static final long WINDOW_MS = 7L * 60 * 1000;
    /** Ops host map refresh cadence in cycles (design call plan step 3). */
    static final int OPS_REFRESH_EVERY = 12;
    /** Landing-type WARN period per type (v2 "Runtime landing"): once a day. */
    static final long LANDING_WARN_MS = 24L * 60 * 60 * 1000;
    private static final int TIERS = EntityType.Tier.values().length;
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

    // Ops cluster map (cluster tier). Refreshed every 12th cycle, on a change
    // in the in-scope cluster set, and on a MoRef miss at most once per 12
    // cycles (review clarification 2026-11-06): a missing MoRef is otherwise
    // skipped until the next scheduled refresh.
    private OpsHostMap opsClusters;
    private long opsClustersCycle;
    private Set<String> opsClustersSet = Collections.emptySet();
    private boolean opsClustersRetry;
    private boolean opsClustersMissPending;
    private long opsClustersMissRefreshCycle = Long.MIN_VALUE / 2;
    private boolean clusterMissWarnedSinceRefresh;
    private final Set<String> unresolvedClusterWarned = new HashSet<>();
    /** Per landing type: when its unparsed WARN was last written. */
    private final Map<EntityType, Long> landingWarnedAt = new java.util.concurrent.ConcurrentHashMap<>();
    /** Review N5: per type, when its malformed-id WARN was last written. */
    private final Map<EntityType, Long> malformedWarnedAt = new java.util.concurrent.ConcurrentHashMap<>();
    /** Review W1: (cluster, type) optional failures warned since the last host map refresh. */
    private final Set<String> optionalWarned = new HashSet<>();

    /** Per-cluster identity state of the disk and VM tiers (caller thread only). */
    private static final class ClusterState {
        Map<String, String> disks;
        /** A disk map read was attempted (review W2: the null-map trigger fires once). */
        boolean diskAttempted;
        long diskReadCycle = Long.MIN_VALUE / 2;
        long diskMissReadCycle = Long.MIN_VALUE / 2;
        boolean diskMissPending;
        Set<String> diskHostSet = Collections.emptySet();
        boolean diskWarned;
        VmInventory vms;
        /** VM runs of this cluster since its last VM read attempt (review B1). */
        int vmRunsSinceRead;
        /**
         * Build 13: the last due VM read failed. virtual-disk stays off for the
         * cluster until a read succeeds, and the read is retried on its next
         * VM run (one call per cluster per VM run, bounded by the cadence).
         */
        boolean vmReadFailed;
        long vmMissReadCycle = Long.MIN_VALUE / 2;
        boolean vmMissPending;
        boolean vmReadWarned;
    }

    private final Map<String, ClusterState> clusterState = new HashMap<>();

    // Ops VirtualMachine map (VM tier): read when there is none, every 12th
    // cycle with a VM run, after a VM read changed a cluster's VM set, and on
    // a MoRef miss at most once per 12 cycles per vCenter.
    private OpsHostMap opsVms;
    private long opsVmsCycle;
    private boolean opsVmsRetry;
    private boolean opsVmsChange;
    private boolean opsVmsMissPending;
    private long opsVmsMissRefreshCycle = Long.MIN_VALUE / 2;
    private final Set<String> unresolvedVmWarned = new HashSet<>();
    private final Set<String> unresolvedDiskWarned = new HashSet<>();

    /** v2 Sizing requirement 4: one VM-type call in flight across the pool. */
    private final java.util.concurrent.Semaphore vmPermit = new java.util.concurrent.Semaphore(1, true);

    /** (cluster, type) pairs whose type-unavailable fault on an exempt type was logged. */
    private final Set<String> exemptFaultLogged = java.util.concurrent.ConcurrentHashMap.newKeySet();
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

    /** The seventeen self-metrics, and why the cycle failed ({@code null}: it did not). */
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
        /** Build 10: cluster uuids or cluster MoRefs with no ClusterComputeResource of this vCenter. */
        int clustersUnresolved;
        /** Build 10: entities skipped for an id of the wrong form (or a landing id neither cluster nor node). */
        int entitiesUnparsed;
        /** Type calls not made because the type is learned on that cluster. */
        int learnedSkips;
        /** Build 11: vSAN disk uuids with no host in the disk map (disk tier). */
        int disksUnresolved;
        /** Build 11: VM instanceUuids not in the VM read, or VMs with no Ops object. */
        int vmsUnresolved;
        /** Build 11: vdisk paths not in the device map, or on an unknown controller. */
        int vdisksUnresolved;
        /** Review W1: failed calls of optional (non-core) types; the cluster still succeeds. */
        int optionalCallsFailed;
        /** Max time a worker waited for the one VM-call permit (PERF pool). */
        long vmPermitWaitMaxMs;
        // Per tier (index = EntityType.Tier ordinal), for PERF tier and the summary.
        final int[] tierCalls = new int[TIERS];
        final long[] tierCallMs = new long[TIERS];
        final long[] tierWire = new long[TIERS];
        final long[] tierDecoded = new long[TIERS];
        final int[] tierEntities = new int[TIERS];
        final int[] tierSeriesKept = new int[TIERS];
        final int[] tierSeriesPushed = new int[TIERS];
        final int[] tierUnresolved = new int[TIERS];
        final int[] tierLearnedSkips = new int[TIERS];
        final int[] tierFailed = new int[TIERS];

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
            m.put("Collection|clusters_unresolved", (double) clustersUnresolved);
            m.put("Collection|entities_unparsed", (double) entitiesUnparsed);
            m.put("Collection|disks_unresolved", (double) disksUnresolved);
            m.put("Collection|vms_unresolved", (double) vmsUnresolved);
            m.put("Collection|vdisks_unresolved", (double) vdisksUnresolved);
            m.put("Collection|optional_calls_failed", (double) optionalCallsFailed);
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

    /** Rows of one entity, resolved to a resource id of one target kind. */
    private static final class EntityRows {
        /** {@link Watermarks#hash} of the {@code entityRefId}. */
        final long mark;
        /** The type's catalog size (the mark array length). */
        final int width;
        final String resourceId;
        /** {@link OpsHostMap#HOST_SYSTEM} or {@link OpsHostMap#CLUSTER}. */
        final String kind;
        final EntityType.Tier tier;
        final List<String> keys = new ArrayList<>();
        final List<Integer> positions = new ArrayList<>();
        final List<Long> times = new ArrayList<>();
        final List<Double> values = new ArrayList<>();

        EntityRows(long mark, int width, String resourceId, String kind, EntityType.Tier tier) {
            this.mark = mark;
            this.width = width;
            this.resourceId = resourceId;
            this.kind = kind;
            this.tier = tier;
        }
    }

    private abstract static class Msg {
        final Inventory.Cluster cluster;

        Msg(Inventory.Cluster cluster) {
            this.cluster = cluster;
        }
    }

    private static final class TypeDone extends Msg {
        final EntityType type;
        final List<EntityRows> rows;
        final Set<String> unresolvedNodes;
        final Set<String> missedMoids;
        final Set<String> unresolvedClusters = new HashSet<>();
        final Set<String> missedClusterMoids = new HashSet<>();
        /** Entities dropped for identity (any reason), for PERF tier. */
        int unresolvedEntities;
        /** Entities skipped for their id form. */
        int unparsed;
        /** A landing type's unclassifiable id: its part count, 0 when none. */
        int landingUnparsedParts;
        /** Part count of the first malformed id (review N5 WARN). */
        int malformedParts;
        /** Disk uuids not in the disk map. */
        final Set<String> unresolvedDisks = new HashSet<>();
        /** VM instanceUuids not in the VM read, and VM MoRefs not in Ops. */
        final Set<String> vmsUnresolved = new HashSet<>();
        int vdisksUnresolved;
        /** An instanceUuid or vdisk path missed: the cluster's VM read is re-run (rate-limited). */
        boolean vmMiss;
        /** A VM MoRef missed in Ops: the Ops VM map is re-read (rate-limited). */
        boolean opsVmMiss;
        long wireBytes;
        long decodedBytes;
        int entities;
        int seriesKept;

        TypeDone(Inventory.Cluster c, EntityType type, List<EntityRows> rows,
                Set<String> unresolvedNodes, Set<String> missedMoids) {
            super(c);
            this.type = type;
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
        /** Per tier: calls, call ms and learned skips of this cluster. */
        final int[] tierCalls;
        final long[] tierCallMs;
        final int[] tierSkips;
        final List<OptionalFailure> optional;
        final boolean vmRun;
        final long wallMs;
        final String warn;
        final String fatal;
        /** A VirtualMachineError or thread kill in the worker: rethrown on the caller thread. */
        final Error fatalError;

        ClusterDone(Inventory.Cluster c, Outcome outcome, int calls, long callMs, long wallMs,
                String warn, String fatal, Error fatalError, int[] tierCalls, long[] tierCallMs,
                int[] tierSkips, List<OptionalFailure> optional, boolean vmRun) {
            super(c);
            this.optional = optional;
            this.vmRun = vmRun;
            this.tierCalls = tierCalls;
            this.tierCallMs = tierCallMs;
            this.tierSkips = tierSkips;
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
                log.warn("the vCenter instance UUID changed; forgetting the host and cluster "
                        + "maps, dedup marks and learned types");
                opsMap = null;
                opsClusters = null;
                opsVms = null;
                clusterState.clear();
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
                inv = vc.readInventory(budget(deadlineMs), settings.has(EntityType.Tier.DISK));
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
                    optionalWarned.clear();
                    unresolvedDiskWarned.clear();
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

            // Step 2b: Ops ClusterComputeResource map (cluster tier).
            if (settings.has(EntityType.Tier.CLUSTER)) refreshClusterMap(id, uuid, scope, res);
            // Step 2c: Ops VirtualMachine map, on cycles where some cluster runs its VM types.
            if (settings.has(EntityType.Tier.VM)) {
                boolean anyVmRun = false;
                for (Inventory.Cluster c : scope) anyVmRun |= vmRun(c.moid, cycle);
                if (anyVmRun) refreshVmMap(id, uuid, res);
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
            for (EntityType.Tier t : EntityType.Tier.values()) {
                int i = t.ordinal();
                if (t != EntityType.Tier.CORE && !settings.has(t)) continue;
                perf("PERF tier cycle=" + id + " tier=" + t.label + " calls=" + res.tierCalls[i]
                        + " call_ms_sum=" + res.tierCallMs[i] + " wire_bytes=" + res.tierWire[i]
                        + " decoded_bytes=" + res.tierDecoded[i] + " entities="
                        + res.tierEntities[i] + " series_kept=" + res.tierSeriesKept[i]
                        + " series_pushed=" + res.tierSeriesPushed[i] + " unresolved="
                        + res.tierUnresolved[i] + " learned_skips=" + res.tierLearnedSkips[i]
                        + " failed=" + res.tierFailed[i]);
            }
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
                    + res.seriesPushed + " (" + byTier(res.tierSeriesPushed) + "; "
                    + res.duplicatesSkipped + " already pushed); unresolved hosts="
                    + res.hostsUnresolved + " clusters=" + res.clustersUnresolved
                    + (settings.has(EntityType.Tier.DISK) ? " disks=" + res.disksUnresolved : "")
                    + (settings.has(EntityType.Tier.VM) ? " vms=" + res.vmsUnresolved
                            + " vdisks=" + res.vdisksUnresolved : "")
                    + "; unparsed=" + res.entitiesUnparsed + "; learned skips=" + res.learnedSkips
                    + "; optional calls failed=" + res.optionalCallsFailed
                    + "; push batches ok="
                    + res.pushBatchesOk + " failed=" + res.pushBatchesFailed + "; "
                    + String.format(java.util.Locale.ROOT, "%.1f", res.cycleSeconds) + " s"
                    + (res.failure != null ? "; FAILED: " + res.failure : ""));
            perf("PERF cycle cycle=" + id + " clusters=" + res.clustersInScope + " perf_calls="
                    + res.perfCalls + " perf_phase_ms=" + res.perfPhaseMs
                    + " calls_per_s=" + String.format(java.util.Locale.ROOT, "%.2f",
                            res.perfCallsPerSecond)
                    + " call_ms_sum=" + res.perfCallMsTotal + " call_ms_avg="
                    + String.format(java.util.Locale.ROOT, "%.1f", res.perfCallMsAvg)
                    + callsByTier(res.tierCalls)
                    + " push_ms=" + pushMs[0] + " cycle_ms=" + Math.round(res.cycleSeconds * 1000)
                    + " marks_entities=" + marks.entities() + " marks_bytes_est="
                    + marks.bytesEstimate()
                    + " heap_before=" + heapBefore + " heap_after=" + usedHeap());
        }
    }

    /** {@code core 390 host 705 cluster 91}: the tiers this instance runs. */
    private String byTier(int[] counts) {
        StringBuilder b = new StringBuilder();
        for (EntityType.Tier t : EntityType.Tier.values()) {
            if (t != EntityType.Tier.CORE && !settings.has(t)) continue;
            if (b.length() > 0) b.append(' ');
            b.append(t.label).append(' ').append(counts[t.ordinal()]);
        }
        return b.toString();
    }

    /**
     * Step 2b: the Ops ClusterComputeResource map, keyed (MoRef, VCID) like
     * hosts. Read when there is none, every 12th cycle, when the in-scope
     * cluster set changes, after a failed read, and after a MoRef miss at
     * most once per 12 cycles. A failed read is never a cycle failure: the
     * cluster tier's rows are dropped (counted) and host data still goes.
     */
    private void refreshClusterMap(long id, String uuid, List<Inventory.Cluster> scope,
            Result res) throws InterruptedException {
        Set<String> clusterSet = new TreeSet<>();
        for (Inventory.Cluster c : scope) clusterSet.add(c.moid);
        String trigger = null;
        if (opsClusters == null || opsClustersRetry) trigger = "refresh";
        else if (cycle - opsClustersCycle >= OPS_REFRESH_EVERY) trigger = "refresh";
        else if (!clusterSet.equals(opsClustersSet)) trigger = "change";
        else if (opsClustersMissPending
                && cycle - opsClustersMissRefreshCycle >= OPS_REFRESH_EVERY) {
            trigger = "miss";
            opsClustersMissRefreshCycle = cycle;
        }
        if (trigger == null) return;
        long t0 = clock.getAsLong();
        try {
            OpsHostMap m = OpsHostMap.load(ops, uuid, OpsHostMap.CLUSTER);
            opsClusters = m;
            opsClustersCycle = cycle;
            opsClustersSet = clusterSet;
            opsClustersRetry = false;
            opsClustersMissPending = false;
            // Review N2: any read restarts the miss rate limit (no back-to-back reads).
            opsClustersMissRefreshCycle = cycle;
            clusterMissWarnedSinceRefresh = false;
            unresolvedClusterWarned.clear();
            log.info("Ops cluster map: " + m);
            perf("PERF identity cycle=" + id + " read=ops_clusters calls=1 ms="
                    + (clock.getAsLong() - t0) + " count=" + m.size() + " trigger=" + trigger);
        } catch (IOException e) {
            opsClustersRetry = true;
            log.warn("the VCF Operations ClusterComputeResource list could not be read ("
                    + e.getMessage() + "); " + (opsClusters == null
                            ? "cluster tier data is not pushed this cycle"
                            : "keeping the previous map this cycle"));
            res.error("ClusterComputeResource list read failed");
        }
    }

    /** {@code calls_core=.. calls_host=..}: the tiers this instance runs. */
    private String callsByTier(int[] calls) {
        StringBuilder b = new StringBuilder();
        for (EntityType.Tier t : EntityType.Tier.values()) {
            if (t != EntityType.Tier.CORE && !settings.has(t)) continue;
            b.append(" calls_").append(t.label).append('=').append(calls[t.ordinal()]);
        }
        return b.toString();
    }

    /**
     * The VM cadence (v2 Settings): cluster {@code moid} runs its VM types on
     * cycles where {@code (cycle + stableHash(moid)) mod N == 0}, so 1/N of
     * the clusters do VM calls each cycle.
     */
    boolean vmRun(String moid, long cycleNo) {
        int n = settings.vmEveryN;
        return n <= 1 || Math.floorMod(cycleNo + Math.floorMod(Watermarks.hash(moid), (long) n),
                (long) n) == 0;
    }

    /**
     * Step 2c: the Ops VirtualMachine map, keyed (MoRef, VCID) like hosts
     * (P2: both identifiers part of uniqueness, 45/45 matched), 500 per page.
     * A failed read is never a cycle failure: VM rows are skipped this cycle.
     */
    private void refreshVmMap(long id, String uuid, Result res) throws InterruptedException {
        String trigger = null;
        if (opsVms == null || opsVmsRetry) trigger = "refresh";
        else if (cycle - opsVmsCycle >= OPS_REFRESH_EVERY) trigger = "refresh";
        else if (opsVmsChange) trigger = "change";
        else if (opsVmsMissPending && cycle - opsVmsMissRefreshCycle >= OPS_REFRESH_EVERY) {
            trigger = "miss";
        }
        if (trigger == null) return;
        long t0 = clock.getAsLong();
        try {
            OpsHostMap m = OpsHostMap.load(ops, uuid, OpsHostMap.VIRTUAL_MACHINE);
            opsVms = m;
            opsVmsCycle = cycle;
            opsVmsRetry = false;
            opsVmsChange = false;
            opsVmsMissPending = false;
            opsVmsMissRefreshCycle = cycle;
            unresolvedVmWarned.clear();
            log.info("Ops VM map: " + m);
            perf("PERF identity cycle=" + id + " read=ops_vms pages=" + m.pages + " ms="
                    + (clock.getAsLong() - t0) + " count=" + m.size() + " trigger=" + trigger);
        } catch (IOException e) {
            opsVmsRetry = true;
            log.warn("the VCF Operations VirtualMachine list could not be read ("
                    + e.getMessage() + "); " + (opsVms == null
                            ? "VM tier data is not pushed this cycle"
                            : "keeping the previous map this cycle"));
            res.error("VirtualMachine list read failed");
        }
    }

    /** Per-cluster identity plan for this cycle (caller thread). */
    private Plan plan(Inventory.Cluster c) {
        ClusterState st = clusterState.computeIfAbsent(c.moid, k -> new ClusterState());
        Plan p = new Plan();
        if (settings.has(EntityType.Tier.DISK)) {
            Set<String> hostSet = new TreeSet<>();
            for (Inventory.Host h : c.hosts) hostSet.add(h.moid);
            if (st.disks == null && !st.diskAttempted) {
                p.diskTrigger = "refresh";
            } else if (cycle - st.diskReadCycle >= OPS_REFRESH_EVERY) {
                p.diskTrigger = "refresh";
            } else if (st.disks != null && !hostSet.equals(st.diskHostSet)) {
                p.diskTrigger = "change";
            } else if (st.diskMissPending && cycle - st.diskMissReadCycle >= OPS_REFRESH_EVERY) {
                p.diskTrigger = "miss";
            }
            if (p.diskTrigger != null) {
                // Rate limit from the attempt, success or not (a host that always
                // faults cannot drive a read every cycle; review W2: neither can
                // every host faulting, the map then being empty, not absent).
                st.diskAttempted = true;
                st.diskReadCycle = cycle;
                st.diskMissReadCycle = cycle;
                st.diskMissPending = false;
                st.diskHostSet = hostSet;
            }
            p.disks = st.disks;
        }
        if (settings.has(EntityType.Tier.VM)) {
            p.vmRun = vmRun(c.moid, cycle);
            p.vmDevices = settings.needsDevices();
            p.opsVms = opsVms;
            if (p.vmRun) {
                st.vmRunsSinceRead++;
                if (st.vms == null) {
                    p.vmTrigger = "refresh";
                } else if (p.vmDevices && !st.vms.withDevices) {
                    p.vmTrigger = "refresh";
                } else if (st.vmReadFailed) {
                    p.vmTrigger = "retry";
                } else if (st.vmRunsSinceRead >= OPS_REFRESH_EVERY) {
                    // Review B1 (v2 device-list caching (b)): every 12th VM run, so
                    // a disk moved between VMs or bus positions, which no miss
                    // reveals, is placed right again within 12 runs.
                    p.vmTrigger = "refresh";
                } else if (st.vmMissPending && cycle - st.vmMissReadCycle >= OPS_REFRESH_EVERY) {
                    p.vmTrigger = "miss";
                }
                if (p.vmTrigger != null) {
                    st.vmRunsSinceRead = 0;
                    st.vmMissReadCycle = cycle;
                    st.vmMissPending = false;
                }
            }
            p.vms = st.vms;
        }
        return p;
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
        final OpsHostMap clusterMap = opsClusters;
        final java.util.concurrent.atomic.AtomicLong vmWaitMax =
                new java.util.concurrent.atomic.AtomicLong();
        for (int idx = 0; idx < scope.size(); idx++) {
            final Inventory.Cluster c = scope.get(idx);
            final int position = idx;
            final Plan plan = plan(c);
            futures.put(c.moid, pool.submit(() -> {
                furthestStarted.accumulateAndGet(position, Math::max);
                int now = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(now, Math::max);
                try {
                    clusterTask(id, c, nodeToHost, map, clusterMap, plan, windowStart,
                            windowEnd, deadlineMs, queue, clock.getAsLong() - submitAt, span,
                            vmWaitMax);
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
        Ids clusterIds = new Ids();
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
                callMs += handle(id, m, buf, res, done, unresolvedNodes, missed, clusterIds, pushMs);
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
                    callMs += handle(id, m, buf, res, done, unresolvedNodes, missed, clusterIds, pushMs);
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
        res.vmPermitWaitMaxMs = vmWaitMax.get();
        perf("PERF pool cycle=" + id + " workers=" + workers + " max_in_flight="
                + maxInFlight.get() + " max_queue_depth=" + maxDepth
                + (settings.has(EntityType.Tier.VM) ? " vm_permit_wait_ms_max="
                        + res.vmPermitWaitMaxMs : ""));
        identityAfterCycle(unresolvedNodes, missed, res);
        clusterIdentityAfterCycle(clusterIds, res);
        diskVmIdentityAfterCycle(clusterIds, res);
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

    /** Cluster-side identity misses of one cycle. */
    private static final class Ids {
        final Set<String> unresolvedUuids = new TreeSet<>();
        final Set<String> missedMoids = new TreeSet<>();
        // Disk and VM tiers.
        final Set<String> disks = new TreeSet<>();
        final Set<String> vms = new TreeSet<>();
        int vdisks;
        boolean opsVmMiss;
    }

    /** Apply one worker message on the caller thread. @return its perf-call ms */
    private long handle(long id, Msg m, PushBuffer buf, Result res, Set<String> done,
            Set<String> unresolvedNodes, Set<String> missed, Ids clusterIds, long[] pushMs) {
        if (m instanceof TypeDone) {
            TypeDone t = (TypeDone) m;
            unresolvedNodes.addAll(t.unresolvedNodes);
            missed.addAll(t.missedMoids);
            clusterIds.unresolvedUuids.addAll(t.unresolvedClusters);
            clusterIds.missedMoids.addAll(t.missedClusterMoids);
            int ti = t.type.tier.ordinal();
            res.tierWire[ti] += t.wireBytes;
            res.tierDecoded[ti] += t.decodedBytes;
            res.tierEntities[ti] += t.entities;
            res.tierSeriesKept[ti] += t.seriesKept;
            res.tierUnresolved[ti] += t.unresolvedEntities;
            res.entitiesUnparsed += t.unparsed;
            if (t.landingUnparsedParts > 0) {
                landingWarn(t.type, t.landingUnparsedParts);
            } else if (t.unparsed > 0) {
                malformedWarn(t.type, t.unparsed, t.malformedParts);
            }
            clusterIds.disks.addAll(t.unresolvedDisks);
            clusterIds.vms.addAll(t.vmsUnresolved);
            clusterIds.vdisks += t.vdisksUnresolved;
            clusterIds.opsVmMiss |= t.opsVmMiss;
            ClusterState st = clusterState.get(t.cluster.moid);
            if (st != null) {
                if (!t.unresolvedDisks.isEmpty()) st.diskMissPending = true;
                if (t.vmMiss) st.vmMissPending = true;
            }
            for (EntityRows er : t.rows) {
                for (int i = 0; i < er.keys.size(); i++) {
                    // Per (entityRefId, label): one label's newer bucket never
                    // hides another label's value for that bucket.
                    int pos = er.positions.get(i);
                    long ts = er.times.get(i);
                    if (ts <= marks.last(er.mark, pos)) {
                        res.duplicatesSkipped++;
                        continue;
                    }
                    Long full = buf.add(er.kind, er.resourceId, er.keys.get(i), ts,
                            er.values.get(i), er.tier);
                    if (full != null) pushGroup(id, buf, er.kind, full, res, pushMs);
                    // Review N5: recorded only once the pushes are known clean (flush).
                    buf.pendingMarks.add(er.mark, er.width, pos, ts);
                }
            }
            return 0;
        }
        if (m instanceof IdentityDone) {
            identityDone(id, (IdentityDone) m, res);
            return 0;
        }
        ClusterDone d = (ClusterDone) m;
        if (!done.add(d.cluster.moid)) return 0;
        res.perfCalls += d.calls;
        for (OptionalFailure f : d.optional) {
            // Review W1: counted per tier, WARN once per (cluster, type) per
            // refresh, last_error; the cluster and the cycle still succeed.
            res.optionalCallsFailed++;
            res.tierFailed[f.type.tier.ordinal()]++;
            res.error("cluster " + d.cluster.moid + ": " + f.message);
            if (optionalWarned.add(d.cluster.moid + '\u0000' + f.type.id)) {
                log.warn("cluster " + d.cluster.moid + " (" + d.cluster.name + "): " + f.message
                        + " (an optional " + f.type.tier.label + "-tier type; the cluster's "
                        + "other data is still pushed; not repeated until the next refresh)");
            }
        }
        for (int i = 0; i < TIERS; i++) {
            res.tierCalls[i] += d.tierCalls[i];
            res.tierCallMs[i] += d.tierCallMs[i];
            res.tierLearnedSkips[i] += d.tierSkips[i];
            res.learnedSkips += d.tierSkips[i];
        }
        switch (d.outcome) {
            case OK: res.clustersOk++; break;
            case FAILED: res.clustersFailed++; break;
            default: res.clustersTimedOut++; break;
        }
        if (d.warn != null) {
            log.warn("cluster " + d.cluster.moid + " (" + d.cluster.name + "): " + d.warn);
            res.error("cluster " + d.cluster.moid + ": " + d.warn);
        }
        StringBuilder byTier = new StringBuilder();
        for (EntityType.Tier t : EntityType.Tier.values()) {
            if (t != EntityType.Tier.CORE && !settings.has(t)) continue;
            if (byTier.length() > 0) byTier.append(',');
            byTier.append(t.label).append(':').append(d.tierCalls[t.ordinal()]);
        }
        perf("PERF cluster cycle=" + id + " cluster=" + d.cluster.moid + " hosts="
                + d.cluster.hosts.size() + " calls=" + d.calls + " calls_by_tier=" + byTier
                + (settings.has(EntityType.Tier.VM) ? " vm_run=" + (d.vmRun ? "yes" : "no") : "")
                + " wall_ms=" + d.wallMs
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

    /**
     * After the drain, cluster side: count unresolved clusters, WARN once per
     * map refresh, and ask for a map refresh on a MoRef miss (rate-limited in
     * {@link #refreshClusterMap}).
     */
    private void clusterIdentityAfterCycle(Ids ids, Result res) {
        res.clustersUnresolved = ids.unresolvedUuids.size() + ids.missedMoids.size();
        Set<String> fresh = new TreeSet<>(ids.unresolvedUuids);
        fresh.removeAll(unresolvedClusterWarned);
        if (!fresh.isEmpty()) {
            unresolvedClusterWarned.addAll(fresh);
            log.warn(fresh.size() + " vSAN cluster uuid(s) in the performance data are not the "
                    + "queried cluster's; their data is dropped (first: "
                    + fresh.iterator().next() + ")");
        }
        if (ids.missedMoids.isEmpty()) return;
        opsClustersMissPending = true;
        if (!clusterMissWarnedSinceRefresh) {
            clusterMissWarnedSinceRefresh = true;
            if (opsClusters == null) {
                // Review N2: the list read failed; that is the reason, not the adapter.
                log.warn(ids.missedMoids.size() + " cluster(s) could not be matched because the "
                        + "VCF Operations ClusterComputeResource list could not be read; their "
                        + "cluster tier data is not pushed this cycle");
            } else {
                log.warn(ids.missedMoids.size() + " cluster(s) have no VMWARE "
                        + "ClusterComputeResource object of this vCenter in VCF Operations (is "
                        + "the vCenter adapter collecting them?); their cluster tier data is not "
                        + "pushed (first: " + ids.missedMoids.iterator().next() + ")");
            }
        }
    }

    /** Review N5: WARN once per type per day for ids of the wrong form (count and part count only). */
    private void malformedWarn(EntityType type, int count, int parts) {
        long now = clock.getAsLong();
        Long last = malformedWarnedAt.get(type);
        if (last != null && now - last < LANDING_WARN_MS) return;
        malformedWarnedAt.put(type, now);
        log.warn(type.id + ": " + count + " entity id(s) not in the expected form (first has "
                + parts + " part(s)); skipped and counted in entities_unparsed (not repeated "
                + "for this type today)");
    }

    /** Keep a worker's fresh disk map or VM inventory (caller thread). */
    private void identityDone(long id, IdentityDone m, Result res) {
        ClusterState st = clusterState.computeIfAbsent(m.cluster.moid, k -> new ClusterState());
        if (m.diskRead) {
            if (m.disks != null) {
                st.disks = m.disks;
            } else if (st.disks == null) {
                // Review W2: every host failed and there is no earlier map: keep an
                // empty one, so the retry waits for the 12-cycle refresh.
                st.disks = Collections.emptyMap();
            }
            if (m.diskHostsFailed > 0) {
                if (!st.diskWarned) {
                    st.diskWarned = true;
                    log.warn("cluster " + m.cluster.moid + ": QueryDisksForVsan failed on "
                            + m.diskHostsFailed + " host(s) (first: " + m.diskFault + "); their "
                            + "disks are unresolved until the next disk map read (at most once "
                            + "per 12 cycles)");
                }
                res.error("cluster " + m.cluster.moid + ": QueryDisksForVsan failed on "
                        + m.diskHostsFailed + " host(s) (" + m.diskFault + ")");
            } else {
                st.diskWarned = false;
            }
        }
        if (m.vms != null) {
            VmInventory before = st.vms;
            st.vms = m.vms;
            st.vmReadWarned = false;
            st.vmReadFailed = false;
            if (before == null || before.vms() != m.vms.vms()) opsVmsChange = true;
        } else if (m.vmReadError != null) {
            st.vmReadFailed = true;
            res.error("cluster " + m.cluster.moid + ": " + m.vmReadError);
            if (!st.vmReadWarned) {
                st.vmReadWarned = true;
                log.warn("cluster " + m.cluster.moid + ": " + m.vmReadError + "; virtual-disk "
                        + "is not collected for it until a VM read succeeds (retried on its "
                        + "next VM run)" + (st.vms == null ? ""
                                : "; virtual-machine and vscsi use the previous VM read"));
            }
        }
    }

    /**
     * After the drain, disk and VM side: count unresolved disks, VMs and
     * vdisks, WARN once per refresh for each new class, and ask for an Ops VM
     * map refresh on a MoRef miss (rate-limited in {@link #refreshVmMap}).
     */
    private void diskVmIdentityAfterCycle(Ids ids, Result res) {
        res.disksUnresolved = ids.disks.size();
        res.vmsUnresolved = ids.vms.size();
        res.vdisksUnresolved = ids.vdisks;
        Set<String> d = new TreeSet<>(ids.disks);
        d.removeAll(unresolvedDiskWarned);
        if (!d.isEmpty()) {
            unresolvedDiskWarned.addAll(d);
            log.warn(d.size() + " vSAN disk uuid(s) in the performance data are on no host of "
                    + "the disk map; their data is dropped until the cluster's disk map is "
                    + "re-read (at most once per 12 cycles; first: " + d.iterator().next() + ")");
        }
        Set<String> v = new TreeSet<>(ids.vms);
        v.removeAll(unresolvedVmWarned);
        if (!v.isEmpty()) {
            unresolvedVmWarned.addAll(v);
            log.warn(v.size() + " VM(s) in the performance data could not be placed (no VM of "
                    + "the cluster's VM read, or no VMWARE VirtualMachine object of this vCenter "
                    + "in VCF Operations); their data is dropped (first: " + v.iterator().next()
                    + ")");
        }
        if (ids.opsVmMiss) opsVmsMissPending = true;
    }

    /** WARN once per type per day: a landing type's id was neither the cluster nor a node. */
    private void landingWarn(EntityType type, int parts) {
        long now = clock.getAsLong();
        Long last = landingWarnedAt.get(type);
        if (last != null && now - last < LANDING_WARN_MS) return;
        landingWarnedAt.put(type, now);
        log.warn(type.id + ": an entity id (" + parts + " part(s)) is neither the cluster's vSAN "
                + "uuid nor one node uuid of that cluster; skipped and counted in "
                + "entities_unparsed (first observation of this form; please report it)");
    }

    // ------------------------------------------------------------------
    // Worker
    // ------------------------------------------------------------------

    /**
     * What the caller decided for one cluster before the pool starts (it owns
     * the per-cluster state): the VM cadence, and the disk map and VM
     * inventory snapshots with whether to re-read them this cycle.
     */
    static final class Plan {
        boolean vmRun;
        Map<String, String> disks;
        String diskTrigger;
        VmInventory vms;
        String vmTrigger;
        boolean vmDevices;
        OpsHostMap opsVms;
    }

    /** An optional-type call failure (W1): not a cluster failure. */
    private static final class OptionalFailure {
        final EntityType type;
        final String message;

        OptionalFailure(EntityType type, String message) {
            this.type = type;
            this.message = message;
        }
    }

    /** The worker's fresh disk map or VM inventory for the caller to keep. */
    private static final class IdentityDone extends Msg {
        /** A disk map read (as opposed to a VM read). */
        final boolean diskRead;
        /** Non-null: the new disk map (possibly partial); null: every host failed. */
        final Map<String, String> disks;
        final int diskHostsFailed;
        /** The first failing host's fault class (review N3); never server text. */
        final String diskFault;
        /** Non-null: the new VM inventory. */
        final VmInventory vms;
        /** A VM read that failed: its credential-free message. */
        final String vmReadError;

        IdentityDone(Inventory.Cluster c, boolean diskRead, Map<String, String> disks,
                int diskHostsFailed, String diskFault, VmInventory vms, String vmReadError) {
            super(c);
            this.diskRead = diskRead;
            this.disks = disks;
            this.diskHostsFailed = diskHostsFailed;
            this.diskFault = diskFault;
            this.vms = vms;
            this.vmReadError = vmReadError;
        }
    }

    private void clusterTask(long id, Inventory.Cluster c, Map<String, String> nodeToHost,
            OpsHostMap map, OpsHostMap clusterMap, Plan plan, long windowStart, long windowEnd,
            long deadlineMs, BlockingQueue<Msg> queue, long queueWaitMs, CallSpan span,
            java.util.concurrent.atomic.AtomicLong vmWaitMax) {
        long t0 = clock.getAsLong();
        int calls = 0;
        int failedCalls = 0;
        long callMs = 0;
        int[] tierCalls = new int[TIERS];
        long[] tierCallMs = new long[TIERS];
        int[] tierSkips = new int[TIERS];
        List<OptionalFailure> optional = new ArrayList<>();
        Outcome outcome = Outcome.OK;
        String warn = null;
        String fatal = null;
        Error fatalError = null;
        String worker = Thread.currentThread().getName();
        Map<String, String> disks = plan.disks;
        boolean diskReadDone = plan.diskTrigger == null;
        VmInventory vms = plan.vms;
        boolean vmReadDone = plan.vmTrigger == null;
        // Build 13 (Codex): a VM read that was due and failed leaves the device
        // map unproven for this run, so virtual-disk is not collected on it.
        boolean vmReadFailed = false;
        try {
            for (EntityType type : settings.types) {
                if (Thread.currentThread().isInterrupted()) {
                    outcome = Outcome.TIMED_OUT;
                    break;
                }
                if (type.tier == EntityType.Tier.VM && !plan.vmRun) continue;
                long now = clock.getAsLong();
                if (learned.skip(c.moid, type, now)) {
                    tierSkips[type.tier.ordinal()]++;
                    continue;
                }
                if (deadlineMs - now <= 0) {
                    outcome = Outcome.TIMED_OUT;
                    break;
                }
                // Identity reads, lazily before the first call that needs them.
                if (type.target == EntityType.Target.DISK && !diskReadDone) {
                    diskReadDone = true;
                    Map<String, String> fresh = readDisks(id, c, plan.diskTrigger, deadlineMs,
                            queue);
                    if (fresh != null) disks = fresh;
                }
                if (type.target == EntityType.Target.VM && !vmReadDone) {
                    vmReadDone = true;
                    VmInventory fresh = readVmInventory(id, c, plan, deadlineMs, queue, vmWaitMax);
                    if (fresh != null) {
                        vms = fresh;
                    } else {
                        vmReadFailed = true;
                    }
                }
                if (type == EntityType.VIRTUAL_DISK
                        && (vmReadFailed || vms == null || !vms.withDevices)) {
                    // v2 failure table: virtual-disk needs a current device map. When
                    // the due read failed, the previous map may place a moved disk on
                    // its old VM or bus position, so the type is dropped this run;
                    // virtual-machine and vscsi still use the previous read.
                    continue;
                }
                long left = deadlineMs - clock.getAsLong();
                if (left <= 0) {
                    outcome = Outcome.TIMED_OUT;
                    break;
                }
                // v2 Sizing requirement 4: one VM-type call in flight per instance.
                boolean permit = false;
                if (type.tier == EntityType.Tier.VM) {
                    long w0 = clock.getAsLong();
                    permit = vmPermit.tryAcquire(left, TimeUnit.MILLISECONDS);
                    vmWaitMax.accumulateAndGet(clock.getAsLong() - w0, Math::max);
                    if (!permit) {
                        outcome = Outcome.TIMED_OUT;
                        break;
                    }
                    left = deadlineMs - clock.getAsLong();
                }
                long budget = Math.max(1, Math.min(settings.callTimeoutMs, left));
                long tc = clock.getAsLong();
                span.start.accumulateAndGet(tc, Math::min);
                calls++;
                tierCalls[type.tier.ordinal()]++;
                PerfCall pc = null;
                String faultKey = "-";
                int status = 0;
                boolean stop = false;
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
                        stop = true;
                    } else if (f.isTypeUnavailable()) {
                        if (LearnedTypes.exempt(type)) {
                            // ioinsight: never learned, queried every cycle, best-effort.
                            if (exemptFaultLogged.add(c.moid + '\u0000' + type.id)) {
                                log.info("cluster " + c.moid + " does not offer " + type.id
                                        + " (" + f.faultClass + "); still queried every cycle");
                            }
                        } else if (learned.learn(c.moid, type, clock.getAsLong())) {
                            log.info("cluster " + c.moid + " does not offer " + type.id + " ("
                                    + f.faultClass + "); not queried there for about 24 h");
                        }
                    } else {
                        if (f.isBatchingFault()) {
                            log.error("cluster " + c.moid + " " + type.id + ": query batching "
                                    + "fault " + f.firstKey() + " (a defect in this pack)");
                        }
                        failedCalls++;
                        String msg = type.id + ": " + f.getMessage();
                        if (type.tier == EntityType.Tier.CORE
                                || f.is(VsanFault.MANAGED_OBJECT_NOT_FOUND)) {
                            outcome = Outcome.FAILED;
                            warn = first(warn, msg);
                            if (f.is(VsanFault.MANAGED_OBJECT_NOT_FOUND)) stop = true;
                        } else {
                            optional.add(new OptionalFailure(type, msg));
                        }
                    }
                } catch (HttpTimeoutException e) {
                    faultKey = "timeout";
                    outcome = Outcome.TIMED_OUT;
                    warn = first(warn, type.id + ": " + e.getMessage());
                    stop = true;
                } catch (InterruptedException e) {
                    outcome = Outcome.TIMED_OUT;
                    Thread.currentThread().interrupt();
                    stop = true;
                } catch (IOException | RuntimeException e) {
                    faultKey = e.getClass().getSimpleName();
                    failedCalls++;
                    String msg = type.id + ": " + e.getMessage();
                    if (type.tier == EntityType.Tier.CORE) {
                        outcome = Outcome.FAILED;
                        warn = first(warn, msg);
                    } else {
                        optional.add(new OptionalFailure(type, msg));
                    }
                } finally {
                    if (permit) vmPermit.release();
                    long tEnd = clock.getAsLong();
                    span.end.accumulateAndGet(tEnd, Math::max);
                    long ms = tEnd - tc;
                    callMs += ms;
                    tierCallMs[type.tier.ordinal()] += ms;
                    if (pc == null) learned.answered(c.moid, type);
                    perf("PERF call cycle=" + id + " cluster=" + c.moid + " type=" + type.id
                            + " tier=" + type.tier.label + " parse=stream worker=" + worker
                            + " queue_wait_ms=" + queueWaitMs + " wall_ms=" + ms + " status="
                            + status + " wire_bytes=" + (pc == null ? 0 : pc.wireBytes)
                            + " decoded_bytes=" + (pc == null ? 0 : pc.decodedBytes)
                            + " entities=" + (pc == null ? 0 : pc.result.entities.size())
                            + " series_kept=" + (pc == null ? 0 : pc.result.seriesKept)
                            + " fault=" + faultKey);
                }
                if (stop) break;
                if (pc != null) {
                    if (pc.result.isEmptyEcho()) {
                        if (learned.empty(c.moid, type, clock.getAsLong())) {
                            log.info("cluster " + c.moid + ": " + type.id + " answered empty "
                                    + LearnedTypes.EMPTY_CYCLES + (type.tier == EntityType.Tier.VM
                                            ? " VM runs" : " cycles") + " in a row; not "
                                    + "queried there for about 24 h");
                        }
                    } else {
                        learned.answered(c.moid, type);
                    }
                    TypeDone td = rows(c, type, pc.result, nodeToHost, map, clusterMap, disks, vms,
                            plan.opsVms);
                    td.wireBytes = pc.wireBytes;
                    td.decodedBytes = pc.decodedBytes;
                    if (!put(queue, td, deadlineMs)) {
                        outcome = Outcome.TIMED_OUT;
                        break;
                    }
                }
            }
            // Review W1: a cluster fails on a core failure, or when every call failed.
            if (outcome == Outcome.OK && calls > 0 && failedCalls == calls) {
                outcome = Outcome.FAILED;
                warn = first(warn, "every call failed; first: " + optional.get(0).message);
                optional.clear();
            }
        } catch (InterruptedException e) {
            // Cancelled at the deadline or by Stop Collecting while waiting for
            // the VM permit or in an identity read.
            outcome = Outcome.TIMED_OUT;
            Thread.currentThread().interrupt();
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
                    warn, fatal, fatalError, tierCalls, tierCallMs, tierSkips, optional,
                    plan.vmRun);
            if (!put(queue, d, deadlineMs) && fatalError != null) {
                queue.offer(d);
            }
        }
    }

    /**
     * The disk map read for one cluster (disk tier, v2 §HostSystem): one
     * {@code QueryDisksForVsan} per member host with a vsanSystem moId,
     * mapping each non-empty vSAN disk uuid to the host it was read from. A
     * host whose call fails contributes nothing this refresh (its disks are
     * unresolved, retried at the next one). A uuid on two hosts is dropped.
     *
     * @return the new map, or {@code null} when no host answered
     */
    private Map<String, String> readDisks(long id, Inventory.Cluster c, String trigger,
            long deadlineMs, BlockingQueue<Msg> queue) throws InterruptedException {
        long t0 = clock.getAsLong();
        Map<String, String> m = new HashMap<>();
        Set<String> dup = new HashSet<>();
        int calls = 0;
        int failed = 0;
        int answered = 0;
        String fault = null;
        for (Inventory.Host h : c.hosts) {
            if (h.vsanSystem == null) continue;
            long left = deadlineMs - clock.getAsLong();
            if (left <= 0) break;
            calls++;
            try {
                for (String u : vc.queryDisksForVsan(h.vsanSystem,
                        Math.min(settings.callTimeoutMs, left))) {
                    if (dup.contains(u)) continue;
                    String prev = m.put(u, h.moid);
                    if (prev != null && !prev.equals(h.moid)) {
                        m.remove(u);
                        dup.add(u);
                    }
                }
                answered++;
            } catch (IOException | RuntimeException e) {
                failed++;
                if (fault == null) {
                    fault = e instanceof VsanFault ? ((VsanFault) e).faultClass
                            : e.getClass().getSimpleName();
                }
            }
        }
        perf("PERF identity cycle=" + id + " read=disk_map cluster=" + c.moid + " calls=" + calls
                + " ms=" + (clock.getAsLong() - t0) + " count=" + m.size() + " failed=" + failed
                + " trigger=" + trigger);
        Map<String, String> out = answered == 0 && calls > 0 ? null
                : Collections.unmodifiableMap(m);
        put(queue, new IdentityDone(c, true, out, failed, fault, null, null), deadlineMs);
        return out;
    }

    /**
     * The VM read for one cluster (VM tier); {@code null} when it failed. It
     * holds the one VM permit (review W1, v2 Sizing requirement 5): one VM
     * read page, or one VM-type response, in memory across the pool.
     */
    private VmInventory readVmInventory(long id, Inventory.Cluster c, Plan plan, long deadlineMs,
            BlockingQueue<Msg> queue, java.util.concurrent.atomic.AtomicLong vmWaitMax)
            throws InterruptedException {
        long t0 = clock.getAsLong();
        VmInventory v = null;
        String err = null;
        long left = deadlineMs - clock.getAsLong();
        boolean permit = false;
        if (left > 0) {
            long w0 = clock.getAsLong();
            permit = vmPermit.tryAcquire(left, TimeUnit.MILLISECONDS);
            vmWaitMax.accumulateAndGet(clock.getAsLong() - w0, Math::max);
            left = deadlineMs - clock.getAsLong();
        }
        if (permit && left > 0) {
            try {
                v = vc.readVms(c.moid, plan.vmDevices, Math.min(settings.callTimeoutMs, left));
            } catch (IOException | RuntimeException e) {
                err = "VM read failed (" + e.getClass().getSimpleName() + "): " + e.getMessage();
            } finally {
                vmPermit.release();
            }
        } else {
            if (permit) vmPermit.release();
            err = "VM read skipped at the cycle deadline";
        }
        perf("PERF identity cycle=" + id + " read=vm_read cluster=" + c.moid + " pages="
                + (v == null ? 0 : v.pages) + " ms=" + (clock.getAsLong() - t0) + " wire_bytes="
                + (v == null ? 0 : v.wireBytes) + " decoded_bytes="
                + (v == null ? 0 : v.decodedBytes) + " count=" + (v == null ? 0 : v.vms())
                + " disks=" + (v == null ? 0 : v.disks()) + " devices=" + plan.vmDevices
                + " trigger=" + plan.vmTrigger + (err == null ? "" : " result=failed"));
        put(queue, new IdentityDone(c, false, null, 0, null, v, err), deadlineMs);
        return v;
    }

    private static String first(String a, String b) {
        return a != null ? a : b;
    }

    /**
     * Resolve one response to compact rows; the parsed response is dropped
     * after. Host types land by node uuid (Build 1); cluster types on the
     * queried cluster when the id is its vSAN uuid; a landing type
     * ({@link EntityType.Target#CLUSTER_OR_HOST}) on the cluster for its
     * uuid, on a host for one node uuid of that cluster, else it is skipped
     * and counted (v2 "Runtime landing"); disk types on the host the disk map
     * read the disk from; VM types on the VM the VM read names, the vdisk
     * instance being its bus position. Nothing is pushed under a guess.
     */
    private TypeDone rows(Inventory.Cluster c, EntityType type, PerfParser.Result r,
            Map<String, String> nodeToHost, OpsHostMap map, OpsHostMap clusterMap,
            Map<String, String> disks, VmInventory vms, OpsHostMap opsVms) {
        List<EntityRows> out = new ArrayList<>();
        TypeDone td = new TypeDone(c, type, out, new HashSet<>(), new HashSet<>());
        td.entities = r.entities.size();
        td.seriesKept = r.seriesKept;
        td.unparsed = r.malformed;
        td.malformedParts = r.malformedParts;
        if (type.target == EntityType.Target.CLUSTER_OR_HOST && r.malformed > 0) {
            td.landingUnparsedParts = r.malformedParts;
        }
        String clusterUuid = c.vsanUuid();
        for (PerfParser.Entity e : r.entities) {
            String rid;
            String kind;
            String instance = e.ref.instance;
            EntityType.Target target = type.target;
            if (target == EntityType.Target.CLUSTER_OR_HOST) {
                if (e.ref.uuid.equals(clusterUuid)) {
                    target = EntityType.Target.CLUSTER;
                } else if (isNodeOf(c, e.ref.uuid)) {
                    target = EntityType.Target.HOST;
                } else {
                    td.unparsed++;
                    if (td.landingUnparsedParts == 0) td.landingUnparsedParts = 1;
                    continue;
                }
            }
            if (target == EntityType.Target.CLUSTER) {
                kind = OpsHostMap.CLUSTER;
                if (!e.ref.uuid.equals(clusterUuid)) {
                    td.unresolvedClusters.add(e.ref.uuid);
                    td.unresolvedEntities++;
                    continue;
                }
                rid = clusterMap == null ? null : clusterMap.resourceId(c.moid);
                if (rid == null) {
                    td.missedClusterMoids.add(c.moid);
                    td.unresolvedEntities++;
                    continue;
                }
            } else if (target == EntityType.Target.VM) {
                kind = OpsHostMap.VIRTUAL_MACHINE;
                String vmMoid;
                if (type.form == EntityType.IdForm.PATH) {
                    VmInventory.Disk d = vms == null ? null : vms.disk(e.ref.uuid);
                    if (d == null || d == VmInventory.UNPLACEABLE) {
                        td.vdisksUnresolved++;
                        td.unresolvedEntities++;
                        // Review B1: an unplaceable path (seen on two VMs at read
                        // time, e.g. a HotAdd) also asks for the rate-limited re-read.
                        td.vmMiss = true;
                        continue;
                    }
                    vmMoid = d.vmMoid;
                    instance = d.busPosition;
                } else {
                    vmMoid = vms == null ? null : vms.vmMoid(e.ref.uuid);
                    if (vmMoid == null) {
                        td.vmsUnresolved.add(e.ref.uuid);
                        td.unresolvedEntities++;
                        td.vmMiss = true;
                        continue;
                    }
                }
                rid = opsVms == null ? null : opsVms.resourceId(vmMoid);
                if (rid == null) {
                    td.vmsUnresolved.add(vmMoid);
                    td.unresolvedEntities++;
                    td.opsVmMiss = true;
                    continue;
                }
            } else {
                kind = OpsHostMap.HOST_SYSTEM;
                String moid;
                if (target == EntityType.Target.DISK) {
                    moid = disks == null ? null : disks.get(e.ref.uuid);
                    if (moid == null) {
                        td.unresolvedDisks.add(e.ref.uuid);
                        td.unresolvedEntities++;
                        continue;
                    }
                } else {
                    moid = nodeToHost.get(e.ref.uuid);
                    if (moid == null) {
                        td.unresolvedNodes.add(e.ref.uuid);
                        td.unresolvedEntities++;
                        continue;
                    }
                }
                rid = map == null ? null : map.resourceId(moid);
                if (rid == null) {
                    td.missedMoids.add(moid);
                    td.unresolvedEntities++;
                    continue;
                }
            }
            EntityRows er = new EntityRows(Watermarks.hash(e.ref.raw), type.size(), rid, kind,
                    type.tier);
            for (PerfParser.Sample s : e.samples) {
                er.keys.add(type.statKey(instance, s.metric));
                er.positions.add(s.metric.position);
                er.times.add(s.timestamp);
                er.values.add(s.value);
            }
            out.add(er);
        }
        return td;
    }

    /** {@code uuid} is the node uuid of exactly one member host of {@code c}. */
    private static boolean isNodeOf(Inventory.Cluster c, String uuid) {
        int n = 0;
        for (Inventory.Host h : c.hosts) if (uuid.equals(h.nodeUuid)) n++;
        return n == 1;
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
     * Values to push, grouped by target kind (HostSystem,
     * ClusterComputeResource) and then by sample time: {@code pushStatsBatch}
     * stamps every value of a call with the call's one timestamp, and rows of
     * one cycle carry different sample times (every bucket in the window, a
     * lagging host). The caps (100 resources, about 1 MB) apply within each
     * group; a group that reaches them is pushed on its own, one
     * {@code pushStatsBatch} call per group.
     */
    private static final class PushBuffer {
        static final class Group {
            final Map<String, Map<String, Double>> byResource = new LinkedHashMap<>();
            final int[] byTier = new int[TIERS];
            long bytes;
            int values;
        }

        /** Kind, then sample time. */
        final Map<String, TreeMap<Long, Group>> byKind = new TreeMap<>();
        /** Dedup marks of every value buffered this cycle; committed at the end if all pushes were clean. */
        final Watermarks.Pending pendingMarks = new Watermarks.Pending();
        boolean allClean = true;

        /** @return the group's time when it reached a cap, else {@code null} */
        Long add(String kind, String rid, String key, long ts, double v, EntityType.Tier tier) {
            Group g = byKind.computeIfAbsent(kind, k -> new TreeMap<>())
                    .computeIfAbsent(ts, k -> new Group());
            g.byResource.computeIfAbsent(rid, k -> new LinkedHashMap<>()).put(key, v);
            g.bytes += key.length() + 48;
            g.values++;
            g.byTier[tier.ordinal()]++;
            return g.byResource.size() >= PUSH_RESOURCES || g.bytes >= PUSH_BYTES ? ts : null;
        }
    }

    private int pushNo;

    /** Push one (kind, time) group and drop it from the buffer. */
    private void pushGroup(long id, PushBuffer buf, String kind, long ts, Result res,
            long[] pushMs) {
        TreeMap<Long, PushBuffer.Group> times = buf.byKind.get(kind);
        PushBuffer.Group g = times == null ? null : times.remove(ts);
        if (g == null || g.values == 0) return;
        long t = clock.getAsLong();
        PushOutcome o = ops.pushStats(g.byResource, ts);
        long ms = clock.getAsLong() - t;
        pushMs[0] += ms;
        res.seriesPushed += g.values;
        for (int i = 0; i < TIERS; i++) res.tierSeriesPushed[i] += g.byTier[i];
        res.pushBatchesOk += o.batchesOk;
        res.pushBatchesFailed += o.batchesFailed;
        if (!o.clean) {
            buf.allClean = false;
            log.warn("push of " + g.values + " value(s) for " + g.byResource.size() + " "
                    + kind + " resource(s) was not clean: " + o.batchesFailed
                    + " batch(es) failed");
            res.error("Suite API push not clean (" + o.batchesFailed + " batch(es) failed)");
        }
        perf("PERF push cycle=" + id + " batch=" + (++pushNo) + " kind=" + kind
                + " sample_time=" + ts + " resources=" + g.byResource.size() + " stat_values="
                + g.values + " bytes~" + g.bytes + " ms=" + ms + " result="
                + (o.clean ? "ok" : "failed"));
    }

    /**
     * End of cycle: push every remaining group, then commit the dedup marks
     * (review N5) only if every push of the cycle was clean. Not clean: no
     * mark is recorded, so the next cycle re-sends these buckets while they
     * are still in the 7-minute window (a value that did land is re-sent with
     * the same time, which overwrites).
     */
    private void flush(long id, PushBuffer buf, Result res, long[] pushMs) {
        for (String kind : new ArrayList<>(buf.byKind.keySet())) {
            for (Long ts : new ArrayList<>(buf.byKind.get(kind).keySet())) {
                pushGroup(id, buf, kind, ts, res, pushMs);
            }
        }
        if (buf.allClean) buf.pendingMarks.commitTo(marks);
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
