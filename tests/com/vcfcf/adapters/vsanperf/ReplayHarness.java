package com.vcfcf.adapters.vsanperf;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.LongSupplier;

/**
 * Design T3, the replay harness (pak issue #6; design v2 §Test plan T3, v1
 * T3). It runs the real {@link CycleEngine} collect path end to end against
 * synthetic clusters built from the verbatim captures in
 * {@code tests/fixtures/vsan-perfsvc-json/}:
 *
 * <ul>
 * <li><b>Replay vCenter</b>: serves each type's recorded response per
 *     synthetic cluster, the entity ids rewritten (cluster uuid, host node
 *     uuids, NIC names, VM instance uuids and vdisk paths) and the two
 *     buckets moved forward 5 minutes per cycle, so every cycle has one new
 *     bucket per series. The 5-host lab captures become 6 hosts by reusing
 *     the first lab host's rows for host 6. Empty types answer the wildcard
 *     echo. Responses go through the production streaming parser.</li>
 * <li><b>Inventory</b>: N clusters x H hosts x 2 pNICs; with the VM tier, V
 *     VMs per host with 4 or 5 PVSCSI disks each (average 4.93, the lab's
 *     148 / 30), synthetic device pages of 50 VMs.</li>
 * <li><b>Throttle models</b> (design D9): per vCenter, the server starts at
 *     most {@code ceiling} calls per second across all workers; per
 *     cluster, at most {@code ceiling} per second per cluster. Each call
 *     takes {@code latency} after it starts.</li>
 * <li><b>Recording stitcher</b>: counts pushed values per sample time and
 *     per resource kind; keeps no values.</li>
 * <li><b>Simulated time</b> ({@link VirtualClock}): the engine's injectable
 *     clock. Worker threads carry their own simulated time, advanced by the
 *     calls they make; the caller thread sees the slowest active worker's
 *     time, so it cuts at the deadline only when every worker has passed it.
 *     No call sleeps; a 70-cluster cycle runs in seconds of wall time.</li>
 * </ul>
 *
 * Standalone: {@code ci/replay.sh [--clusters 70] [--hosts 6] [--cycles 6]
 * [--workers 4] [--latency-ms 190] [--ceiling 7] [--model vcenter|cluster]
 * [--tiers default|all|core] [--vm] [--disk] [--vm-every 3] [--vms-per-host 40]
 * [--heap] [--pace 20] [--perf] [--push-fixed-ms 60] [--push-ms-per-resource 1.5]}. Design T4 ({@code ReplayBudgetTest}) drives it from the tests.
 */
final class ReplayHarness {

    // ------------------------------------------------------------------
    // Configuration and results
    // ------------------------------------------------------------------

    static final class Config {
        int clusters = 70;
        int hosts = 6;
        int cycles = 6;
        int workers = 4;
        long latencyMs = 190;
        double ceilingPerSecond = 7.0;
        boolean perVcenter = true;
        boolean tierHost = true;
        boolean tierCluster = true;
        boolean tierDisk = false;
        boolean tierVm = false;
        int vmEveryN = 3;
        int vmsPerHost = 40;
        long intervalMs = 300_000;
        long deadlineMs = 240_000;
        boolean heap = false;
        long heapSampleMs = 150;
        /** Print the engine's PERF pool and cycle lines (standalone --perf). */
        boolean perf = false;
        /**
         * Wall-clock pacing: each call also waits its simulated latency divided
         * by this factor (0: no waiting). Without it the workers outrun the
         * caller and fill the result queue (16 messages), which production
         * never does (a call takes at least 0.19 s there); the heap readings
         * use a factor of 20.
         */
        int pace = 0;
        /**
         * Simulated Suite API push latency, two terms: a fixed cost per push
         * call plus a cost per resource in the batch (build 18), from the live
         * {@code PERF push} lines of devel 9.0.2 (build 16, VM tier on, three
         * cycles): 1 ClusterComputeResource 54 to 69 ms, 5 HostSystems 47 to
         * 74 ms, 30 VirtualMachines 48 to 113 ms. The cost is dominated by the
         * call (~50 to 60 ms), with at most ~1 to 2 ms per resource. Simulated,
         * never waited in wall time (build 17): the push moves the caller's
         * clock on by its duration, so the drain loop's deadline check and
         * {@code PERF push} see it, and workers that would overfill the result
         * queue during it wait for its end ({@link VirtualClock#callerBusy}).
         * Approximation: the caller and the workers are not in lock step in
         * wall time, so calls that complete before the caller registers its
         * push are not held.
         */
        double pushFixedMs = 60.0;
        double pushMsPerResource = 1.5;

        List<EntityType> types() {
            return new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null,
                    String.valueOf(tierHost), String.valueOf(tierCluster), String.valueOf(tierDisk),
                    String.valueOf(tierVm), String.valueOf(vmEveryN), "").types;
        }

        @Override
        public String toString() {
            return clusters + " clusters x " + hosts + " hosts x 2 pNICs, " + workers + " workers, "
                    + latencyMs + " ms per call, " + ceilingPerSecond + " calls/s "
                    + (perVcenter ? "per vCenter" : "per cluster") + ", tiers core"
                    + (tierHost ? "+host" : "") + (tierCluster ? "+cluster" : "")
                    + (tierDisk ? "+disk" : "") + (tierVm ? "+vm(N=" + vmEveryN + ", "
                            + vmsPerHost + " VMs/host)" : "");
        }
    }

    /** One cycle's figures. */
    static final class CycleResult {
        int cycle;
        CycleEngine.Result res;
        /** Values pushed under this cycle's newest bucket. */
        long newestBucketValues;
        long valuesPushed;
        Map<String, Long> byKind = new TreeMap<>();
        long wallMs;
        /** Peak heap delta during the cycle (post-GC samples), 0 unless measured. */
        long peakHeapDelta;
        /** Heap delta after the cycle (post-GC), 0 unless measured. */
        long retainedHeapDelta;
        /** Simulated Suite API push time of the cycle (the caller is blocked for it). */
        long pushSimMs;
        int pushCalls;

        @Override
        public String toString() {
            CycleEngine.Result r = res;
            return String.format(java.util.Locale.ROOT,
                    "cycle %d: calls=%d perf_phase=%.1f s clusters ok=%d timed_out=%d failed=%d "
                            + "values=%d newest_bucket=%d by_kind=%s learned_skips=%d "
                            + "cut=%s push=%d calls %.1f s cycle=%.1f s wall=%d ms%s",
                    cycle, r.perfCalls, r.perfPhaseMs / 1000.0, r.clustersOk, r.clustersTimedOut,
                    r.clustersFailed, valuesPushed, newestBucketValues, byKind, r.learnedSkips,
                    r.lastError != null && r.lastError.contains("cycle deadline"), pushCalls,
                    pushSimMs / 1000.0, r.cycleSeconds, wallMs,
                    peakHeapDelta > 0 ? String.format(java.util.Locale.ROOT,
                            " heap_peak=%.1f MB heap_retained=%.1f MB", peakHeapDelta / 1048576.0,
                            retainedHeapDelta / 1048576.0) : "");
        }
    }

    // ------------------------------------------------------------------
    // Simulated time
    // ------------------------------------------------------------------

    /**
     * The engine's clock in simulated ms. A worker thread of the current
     * cycle (named {@code vsanperf-c<cycle>-w<n>} by the engine) has its own
     * time, starting at the cycle base and moved by its calls. The caller
     * thread sees the minimum over this cycle's registered workers (never
     * going back), so the drain loop's deadline check fires only when every
     * worker is past it; until then each worker stops on its own deadline
     * check. Known distortion: in the last moments before the cut, a worker
     * already past the deadline may pick up an unstarted cluster and time it
     * out at once, which counts it as attempted for the rotation.
     */
    static final class VirtualClock implements LongSupplier {
        private volatile long base;
        private volatile String prefix = "";
        private final Map<Thread, long[]> workers = new ConcurrentHashMap<>();
        private final ThreadLocal<long[]> own = new ThreadLocal<>();
        private long caller;

        synchronized void startCycle(long baseMs, long engineCycle) {
            base = baseMs;
            prefix = "vsanperf-c" + engineCycle + "-";
            workers.clear();
            caller = baseMs;
            busyStart = Long.MIN_VALUE;
            busyEnd = Long.MIN_VALUE;
            busyCompletions = 0;
        }

        // Synchronous push (build 17): while the caller is in a push call it
        // drains nothing; workers fill the result queue and then block.
        /** The engine's result queue: max(8, 4 x workers) messages. */
        int queueCapacity = 16;
        private long busyStart = Long.MIN_VALUE;
        private long busyEnd = Long.MIN_VALUE;
        private int busyCompletions;

        /**
         * The caller thread spends {@code ms} of simulated time in a push call:
         * its clock moves on by that much (the engine's deadline check and
         * {@code PERF push} see it), and workers are held per
         * {@link #holdForCaller}.
         */
        synchronized void callerBusy(long ms) {
            long start = getAsLong();
            caller = start + ms;
            busyStart = start;
            busyEnd = caller;
            busyCompletions = 0;
        }

        /**
         * A worker about to start a call: if the caller is in a push and the
         * calls finished meanwhile already fill the result queue, the worker
         * cannot hand over another result until the push ends, so it starts
         * no earlier than that.
         */
        synchronized void holdForCaller() {
            long[] me = slot();
            if (me[0] >= busyStart && me[0] < busyEnd && busyCompletions >= queueCapacity) {
                me[0] = busyEnd;
            }
        }

        /** A call finished at the worker's time: counted against a push in progress. */
        synchronized void completed() {
            long t = slot()[0];
            if (t >= busyStart && t < busyEnd) busyCompletions++;
        }

        long base() {
            return base;
        }

        /** [0] simulated time, [1] wall nanos of the worker's last clock use. */
        private long[] slot() {
            Thread t = Thread.currentThread();
            long[] s = own.get();
            if (s == null) {
                s = new long[] {base, System.nanoTime()};
                own.set(s);
                if (t.getName().startsWith(prefix)) workers.put(t, s);
            }
            s[1] = System.nanoTime();
            return s;
        }

        /** A worker that has not used the clock for this long (wall) counts as idle (above the longest paced wait, ~80 ms). */
        static final long IDLE_NANOS = 200_000_000L;

        /**
         * Shared-server ordering: the calling worker waits (wall time) until no
         * other active worker of this cycle is behind it in simulated time, so
         * the per-vCenter server sees requests in simulated-time order rather
         * than in the order threads happen to run. A worker idle for
         * {@link #IDLE_NANOS} (done, or blocked on the result queue) does not
         * hold the others back.
         */
        void awaitTurn() throws InterruptedException {
            long[] me = slot();
            synchronized (this) {
                while (true) {
                    long now = System.nanoTime();
                    boolean behind = false;
                    for (long[] w : workers.values()) {
                        if (w != me && w[0] < me[0] && now - w[1] < IDLE_NANOS) {
                            behind = true;
                            break;
                        }
                    }
                    if (!behind) return;
                    me[1] = now;
                    wait(1);
                }
            }
        }

        synchronized void wake() {
            notifyAll();
        }

        static boolean isWorker() {
            return Thread.currentThread().getName().startsWith("vsanperf-c");
        }

        /** The calling worker's time. */
        long worker() {
            return slot()[0];
        }

        /** Move the calling worker to {@code t} (never back). */
        void advance(long t) {
            long[] s = slot();
            if (t > s[0]) s[0] = t;
        }

        @Override
        public long getAsLong() {
            if (isWorker()) return slot()[0];
            synchronized (this) {
                long m = Long.MAX_VALUE;
                for (long[] w : workers.values()) m = Math.min(m, w[0]);
                if (m != Long.MAX_VALUE && m > caller) caller = m;
                return caller;
            }
        }
    }

    // ------------------------------------------------------------------
    // Synthetic ids
    // ------------------------------------------------------------------

    static String clusterMoid(int c) {
        return "domain-c" + (10000 + c);
    }

    static String clusterUuid(int c) {
        return String.format("%08x-0000-4000-8000-%012x", 0x5c000000 + c, c);
    }

    static String nodeUuid(int c, int h) {
        return String.format("%08x-%04x-4000-8000-%012x", 0x6a000000 + c, h, (long) c * 64 + h);
    }

    static String hostMoid(int c, int h) {
        return "host-" + c + "-" + h;
    }

    static String vmMoid(int c, int v) {
        return "vm-" + c + "-" + v;
    }

    static String vmUuid(int c, int v) {
        return String.format("%08x-%04x-4000-8000-%012x", 0x50000000 + c, v & 0xffff, (long) c * 100000 + v);
    }

    /** Disks of VM v: 4 for every 15th VM, else 5 (average 4.93, the lab's 148 / 30). */
    static int disksOf(int v) {
        return v % 15 == 0 ? 4 : 5;
    }

    static String vdiskPath(int c, int v, int u) {
        return "ns-" + c + "/" + vmMoid(c, v) + "-" + u + ".vmdk";
    }

    static final String VCID = "replay-vc-uuid";

    // ------------------------------------------------------------------
    // Response templates from the verbatim captures
    // ------------------------------------------------------------------

    /** One recorded entity: its id suffix after the node uuid, and its series as JSON. */
    private static final class Row {
        final String suffix;
        final String seriesJson;

        Row(String suffix, String seriesJson) {
            this.suffix = suffix;
            this.seriesJson = seriesJson;
        }
    }

    /** Per type: rows by lab node index (host types) or the one row (cluster, VM types). */
    private final Map<EntityType, List<List<Row>>> templates = new LinkedHashMap<>();
    private final Config cfg;

    ReplayHarness(Config cfg) throws IOException {
        this.cfg = cfg;
        String[][] files = {
                {"host-domowner", "queryperf-host-domowner-2026-11-06.json"},
                {"host-domclient", "queryperf-host-domclient-2026-11-06.json"},
                {"vsan-pnic-net", "queryperf-vsan-pnic-net-2026-11-06.json"},
                {"vsan-cluster-capacity", "queryperf-vsan-cluster-capacity-2026-11-06.json"},
                {"cluster-domclient", "queryperf-cluster-domclient-labels-2026-11-06.json"},
                {"cluster-domcompmgr", "queryperf-cluster-domcompmgr-2026-11-06.json"},
                {"cluster-domowner", "queryperf-cluster-domowner-2026-11-06.json"},
                {"cluster-esa-dedup-domclient-io", "queryperf-cluster-esa-dedup-domclient-io-2026-11-06.json"},
                {"cluster-rdt-network-latency", "queryperf-cluster-rdt-network-latency-2026-11-06.json"},
                {"cluster-zdom-top-stats", "queryperf-cluster-zdom-top-stats-2026-11-06.json"},
                {"cmmds-net", "queryperf-cmmds-net-2026-11-06.json"},
                {"vsan-cpu", "queryperf-vsan-cpu-2026-11-06.json"},
                {"host-domcompmgr", "queryperf-host-domcompmgr-2026-11-06.json"},
                {"host-esa-dedup-domclient-io", "queryperf-host-esa-dedup-domclient-io-2026-11-06.json"},
                {"vsan-host-net", "queryperf-vsan-host-net-2026-11-06.json"},
                {"host-zdom-top-stats", "queryperf-host-zdom-top-stats-2026-11-06.json"},
                {"vsan-memory", "queryperf-vsan-memory-2026-11-06.json"},
                {"rdt-network-latency", "queryperf-rdt-network-latency-2026-11-06.json"},
                {"vsan-tcpip-stats", "queryperf-vsan-tcpip-stats-2026-11-06.json"},
                {"vsan-vnic-net", "queryperf-vsan-vnic-net-2026-11-06.json"},
                {"vnic-rdt-network-latency", "queryperf-vnic-rdt-network-latency-2026-11-06.json"},
                {"zdom-vtx", "queryperf-zdom-vtx-2026-11-06.json"},
                {"vsan-esa-disk-layer", "queryperf-vsan-esa-disk-layer-2026-11-06.json"},
                {"vsan-esa-disk-scsifw", "queryperf-vsan-esa-disk-scsifw-2026-11-06.json"},
                {"virtual-machine", "queryperf-virtual-machine-2026-11-06.json"},
                {"virtual-disk", "queryperf-virtual-disk-2026-11-06.json"},
                {"vscsi", "queryperf-vscsi-2026-11-06.json"}};
        for (String[] f : files) {
            EntityType t = EntityType.byId(f[0]);
            List<Object> entities = JsonStream.list(new JsonStream(new java.io.StringReader(
                    T.vendor(f[1]))).document());
            Map<String, List<Row>> byFirst = new TreeMap<>();
            for (Object e : entities) {
                String ref = JsonStream.str(JsonStream.get(e, "entityRefId"));
                String id = ref.substring(ref.indexOf(':') + 1);
                int bar = id.indexOf('|');
                String first = bar < 0 ? id : id.substring(0, bar);
                String suffix = bar < 0 ? "" : id.substring(bar);
                byFirst.computeIfAbsent(first, k -> new ArrayList<>()).add(new Row(suffix,
                        JsonWrite.write(JsonStream.get(e, "value"))));
            }
            templates.put(t, new ArrayList<>(byFirst.values()));
        }
    }

    // ------------------------------------------------------------------
    // Responses
    // ------------------------------------------------------------------

    private static final DateTimeFormatter TS = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    /** The two buckets of a cycle: the newest is the cycle's own. */
    static String sampleInfo(long newestMs) {
        return TS.format(Instant.ofEpochMilli(newestMs - 300_000)) + ","
                + TS.format(Instant.ofEpochMilli(newestMs));
    }

    private static String entity(String ref, String sampleInfo, String series) {
        return "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":" + Json.quote(ref)
                + ",\"sampleInfo\":" + Json.quote(sampleInfo) + ",\"value\":" + series + "}";
    }

    /** The body for one type on synthetic cluster {@code c}, newest bucket {@code newest}. */
    String body(EntityType t, int c, long newest) {
        StringBuilder b = new StringBuilder();
        java.util.Iterator<String> it = chunks(t, c, newest);
        while (it.hasNext()) b.append(it.next());
        return b.toString();
    }

    /**
     * The body as chunks, one entity each, generated as the parser reads, so
     * the harness never holds a whole reply (production holds only the gzip
     * wire bytes, ~0.2 MB for a 240-VM vscsi reply; the decoded reply would be
     * ~3 MB and would inflate the heap reading).
     */
    java.util.Iterator<String> chunks(EntityType t, int c, long newest) {
        List<List<Row>> rows = templates.get(t);
        List<java.util.function.Supplier<String>> parts = new ArrayList<>();
        if (rows == null || rows.isEmpty()) {
            parts.add(() -> "{\"_typeName\":\"VsanPerfEntityMetricCSV\",\"entityRefId\":\""
                    + t.id + ":*\",\"sampleInfo\":\"\"}");
        } else {
            String si = sampleInfo(newest);
            if (t.tier == EntityType.Tier.CLUSTER) {
                parts.add(() -> entity(t.id + ":" + clusterUuid(c), si, rows.get(0).get(0).seriesJson));
            } else if (t.tier == EntityType.Tier.VM) {
                String series = rows.get(0).get(0).seriesJson;
                for (int v = 0; v < cfg.hosts * cfg.vmsPerHost; v++) {
                    final int vm = v;
                    if (t == EntityType.VIRTUAL_MACHINE) {
                        parts.add(() -> entity(t.id + ":" + vmUuid(c, vm), si, series));
                    } else {
                        for (int u = 0; u < disksOf(v); u++) {
                            final int unit = u;
                            parts.add(() -> entity(t == EntityType.VSCSI
                                    ? t.id + ":" + vmUuid(c, vm) + "|scsi0:" + unit
                                    : t.id + ":" + vdiskPath(c, vm, unit), si, series));
                        }
                    }
                }
            } else if (t.tier == EntityType.Tier.DISK) {
                for (int h = 0; h < cfg.hosts; h++) {
                    final int host = h;
                    parts.add(() -> entity(t.id + ":" + diskUuid(c, host), si,
                            rows.get(host % rows.size()).get(0).seriesJson));
                }
            } else {
                for (int h = 0; h < cfg.hosts; h++) {
                    for (Row r : rows.get(h % rows.size())) {
                        final int host = h;
                        parts.add(() -> entity(t.id + ":" + nodeUuid(c, host) + r.suffix, si,
                                r.seriesJson));
                    }
                }
            }
        }
        return new java.util.Iterator<String>() {
            int i = -1;

            @Override public boolean hasNext() {
                return i <= parts.size();
            }

            @Override public String next() {
                i++;
                if (i == 0) return "[";
                if (i == parts.size() + 1) return "]";
                return (i > 1 ? "," : "") + parts.get(i - 1).get();
            }
        };
    }

    /** An InputStream over generated chunks (UTF-8; the replies are ASCII). */
    static final class ChunkStream extends java.io.InputStream {
        private final java.util.Iterator<String> it;
        private byte[] cur = new byte[0];
        private int pos;
        long count;

        ChunkStream(java.util.Iterator<String> it) {
            this.it = it;
        }

        private boolean fill() {
            while (pos >= cur.length) {
                if (!it.hasNext()) return false;
                cur = it.next().getBytes(StandardCharsets.UTF_8);
                pos = 0;
            }
            return true;
        }

        @Override public int read() {
            if (!fill()) return -1;
            count++;
            return cur[pos++] & 0xff;
        }

        @Override public int read(byte[] b, int off, int len) {
            if (len == 0) return 0;
            if (!fill()) return -1;
            int n = Math.min(len, cur.length - pos);
            System.arraycopy(cur, pos, b, off, n);
            pos += n;
            count += n;
            return n;
        }
    }

    static String diskUuid(int c, int h) {
        return String.format("52%06x-0000-4000-8000-%012x", c, h);
    }

    // ------------------------------------------------------------------
    // Fakes
    // ------------------------------------------------------------------

    /** The replay vCenter: inventory, perf replies with the throttle, disk map, VM pages. */
    final class Vcenter implements CycleEngine.VcenterApi {
        final VirtualClock clock;
        volatile long newest;
        private long nextFree = Long.MIN_VALUE;
        private final Map<String, long[]> clusterNextFree = new ConcurrentHashMap<>();
        final AtomicLong calls = new AtomicLong();
        final AtomicLong vmReads = new AtomicLong();
        private final Map<String, Integer> index = new ConcurrentHashMap<>();

        Vcenter(VirtualClock clock) {
            this.clock = clock;
            for (int c = 0; c < cfg.clusters; c++) index.put(clusterMoid(c), c);
        }

        @Override public String instanceUuid(long t) {
            return VCID;
        }

        @Override public Inventory readInventory(long t, boolean withVsanSystem) {
            List<Inventory.Cluster> cl = new ArrayList<>();
            for (int c = 0; c < cfg.clusters; c++) {
                List<Inventory.Host> hosts = new ArrayList<>();
                for (int h = 0; h < cfg.hosts; h++) {
                    hosts.add(new Inventory.Host(hostMoid(c, h), hostMoid(c, h) + ".replay",
                            nodeUuid(c, h), clusterUuid(c), true,
                            withVsanSystem ? "vsanSystem-" + c + "-" + h : null));
                }
                cl.add(new Inventory.Cluster(clusterMoid(c), "replay-" + c, hosts));
            }
            return new Inventory(cl);
        }

        /**
         * Simulated latency of one call. VM-type calls grow with their payload:
         * a linear fit of the lab's (design MAP) virtual-machine call (30
         * entities, 111 ms) and vscsi call (148 entities, 250 ms), about 76 ms
         * plus 1.18 ms per entity; never below the configured latency.
         */
        long latency(EntityType t) {
            if (t.tier != EntityType.Tier.VM) return cfg.latencyMs;
            long n = 0;
            for (int v = 0; v < cfg.hosts * cfg.vmsPerHost; v++) {
                n += t == EntityType.VIRTUAL_MACHINE ? 1 : disksOf(v);
            }
            return Math.max(cfg.latencyMs, Math.round(76 + 1.18 * n));
        }

        /** Simulated service: start no earlier than the throttle allows, end after the latency. */
        private void serve(String cluster, long latencyMs) throws IOException {
            long gap = Math.round(1000.0 / cfg.ceilingPerSecond);
            long begin;
            clock.holdForCaller();
            try {
                // Both models: requests in simulated-time order, so cluster
                // pickup and the shared server follow simulated time.
                clock.awaitTurn();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.InterruptedIOException("replay: interrupted");
            }
            if (cfg.perVcenter) {
                long start = clock.worker();
                synchronized (this) {
                    begin = Math.max(start, nextFree);
                    nextFree = begin + gap;
                }
                clock.advance(begin + latencyMs);
                clock.completed();
                clock.wake();
                pace(latencyMs);
                return;
            } else {
                long start = clock.worker();
                long[] nf = clusterNextFree.computeIfAbsent(cluster, k -> new long[] {Long.MIN_VALUE});
                synchronized (nf) {
                    begin = Math.max(start, nf[0]);
                    nf[0] = begin + gap;
                }
            }
            clock.advance(begin + latencyMs);
            clock.completed();
            clock.wake();
            pace(latencyMs);
        }

        private void pace(long latencyMs) throws IOException {
            if (cfg.pace <= 0) return;
            try {
                Thread.sleep(Math.max(1, latencyMs / cfg.pace));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.InterruptedIOException("replay: interrupted");
            }
        }

        @Override public CycleEngine.PerfCall queryPerf(String cluster, EntityType type, long s,
                long e, long timeoutMs) throws IOException {
            calls.incrementAndGet();
            serve(cluster, latency(type));
            ChunkStream in = new ChunkStream(chunks(type, index.get(cluster), newest));
            PerfParser.Result r = PerfParser.parse(in, type);
            // Wire bytes: the lab's gzip ratio is about 1:10 to 1:17.
            return new CycleEngine.PerfCall(r, 200, in.count / 12, in.count);
        }

        @Override public List<String> queryDisksForVsan(String vsanSystem, long t) {
            clock.advance(clock.worker() + 10);
            String[] p = vsanSystem.substring("vsanSystem-".length()).split("-");
            return List.of(diskUuid(Integer.parseInt(p[0]), Integer.parseInt(p[1])));
        }

        @Override public VmInventory readVms(String cluster, boolean withDevices, long t)
                throws IOException {
            vmReads.incrementAndGet();
            int c = index.get(cluster);
            VmInventory.Builder b = new VmInventory.Builder(withDevices);
            int vms = cfg.hosts * cfg.vmsPerHost;
            for (int from = 0; from < vms; from += VmInventory.PAGE) {
                // SYNTHETIC device pages in the P2 shape (not vendor captures).
                StringBuilder p = new StringBuilder("{\"_typeName\":\"RetrieveResult\",\"objects\":[");
                for (int v = from; v < Math.min(vms, from + VmInventory.PAGE); v++) {
                    if (v > from) p.append(',');
                    p.append("{\"_typeName\":\"ObjectContent\",\"obj\":{\"_typeName\":"
                            + "\"ManagedObjectReference\",\"value\":\"").append(vmMoid(c, v))
                            .append("\",\"type\":\"VirtualMachine\"},\"propSet\":[{\"_typeName\":"
                                    + "\"DynamicProperty\",\"name\":\"config.instanceUuid\",\"val\":"
                                    + "{\"_typeName\":\"string\",\"_value\":\"").append(vmUuid(c, v))
                            .append("\"}}");
                    if (withDevices) {
                        p.append(",{\"_typeName\":\"DynamicProperty\",\"name\":"
                                + "\"config.hardware.device\",\"val\":{\"_typeName\":"
                                + "\"ArrayOfVirtualDevice\",\"_value\":[{\"_typeName\":"
                                + "\"ParaVirtualSCSIController\",\"key\":1000,\"busNumber\":0}");
                        for (int u = 0; u < disksOf(v); u++) {
                            p.append(",{\"_typeName\":\"VirtualDisk\",\"key\":").append(2000 + u)
                                    .append(",\"controllerKey\":1000,\"unitNumber\":").append(u)
                                    .append(",\"backing\":{\"_typeName\":\"VirtualDiskFlatVer2BackingInfo\","
                                            + "\"fileName\":\"[vsanDatastore] ")
                                    .append(vdiskPath(c, v, u)).append("\"}}");
                        }
                        p.append("]}}");
                    }
                    p.append("]}");
                }
                b.addPage(p.append("]}").toString());
            }
            clock.advance(clock.worker() + 60);
            return b.build();
        }
    }

    /** The recording stitcher and the Ops resource lists. */
    final class Ops implements CycleEngine.OpsApi {
        /** Set by {@link #run}: the push holds the caller's simulated time (build 17). */
        VirtualClock clock;
        final Map<Long, AtomicLong> byTime = new ConcurrentHashMap<>();
        final Map<String, AtomicLong> byKind = new ConcurrentHashMap<>();
        final AtomicLong values = new AtomicLong();

        @Override public String get(String path) {
            int page = Integer.parseInt(path.substring(path.lastIndexOf('=') + 1));
            int size = Integer.parseInt(path.replaceAll(".*pageSize=(\\d+).*", "$1"));
            List<String> all = new ArrayList<>();
            if (path.contains("resourceKind=HostSystem")) {
                for (int c = 0; c < cfg.clusters; c++) {
                    for (int h = 0; h < cfg.hosts; h++) all.add(hostMoid(c, h));
                }
            } else if (path.contains("resourceKind=ClusterComputeResource")) {
                for (int c = 0; c < cfg.clusters; c++) all.add(clusterMoid(c));
            } else {
                for (int c = 0; c < cfg.clusters; c++) {
                    for (int v = 0; v < cfg.hosts * cfg.vmsPerHost; v++) all.add(vmMoid(c, v));
                }
            }
            List<String> rows = new ArrayList<>();
            for (int i = page * size; i < Math.min(all.size(), (page + 1) * size); i++) {
                rows.add(OpsHostMapTest.row("rid-" + all.get(i), all.get(i), VCID));
            }
            return OpsHostMapTest.page(all.size(), rows.toArray(new String[0]));
        }

        final AtomicLong pushSimMs = new AtomicLong();
        final AtomicLong pushCalls = new AtomicLong();
        final AtomicLong bodyBytes = new AtomicLong();

        /**
         * Builds the Suite API batch body as production does
         * ({@code resource-stat-content}, one {@code stat-contents} entry per
         * key), so its allocation is in the heap reading (review W4), then
         * drops it; models the call's latency (review N2).
         */
        @Override public CycleEngine.PushOutcome pushStats(Map<String, Map<String, Double>> stats,
                long ts) {
            long n = 0;
            StringBuilder body = new StringBuilder("{\"resource-stat-content\":[");
            boolean firstR = true;
            for (Map.Entry<String, Map<String, Double>> r : stats.entrySet()) {
                if (!firstR) body.append(',');
                firstR = false;
                body.append("{\"id\":").append(Json.quote(r.getKey())).append(",\"stat-contents\":[");
                boolean firstK = true;
                for (Map.Entry<String, Double> k : r.getValue().entrySet()) {
                    if (!firstK) body.append(',');
                    firstK = false;
                    body.append("{\"statKey\":").append(Json.quote(k.getKey()))
                            .append(",\"timestamps\":[").append(ts).append("],\"data\":[")
                            .append(k.getValue()).append("]}");
                    n++;
                }
                body.append("]}");
            }
            byte[] wire = body.append("]}").toString().getBytes(StandardCharsets.UTF_8);
            bodyBytes.addAndGet(wire.length);
            long ms = Math.round(cfg.pushFixedMs + cfg.pushMsPerResource * stats.size());
            if (clock != null) clock.callerBusy(ms);
            pushSimMs.addAndGet(ms);
            pushCalls.incrementAndGet();
            String kind = "HostSystem";
            String any = stats.keySet().iterator().next();
            if (any.startsWith("rid-domain-")) kind = "ClusterComputeResource";
            else if (any.startsWith("rid-vm-")) kind = "VirtualMachine";
            byTime.computeIfAbsent(ts, k -> new AtomicLong()).addAndGet(n);
            byKind.computeIfAbsent(kind, k -> new AtomicLong()).addAndGet(n);
            values.addAndGet(n);
            return new CycleEngine.PushOutcome((int) n, 1, 0, true);
        }
    }

    // ------------------------------------------------------------------
    // Run
    // ------------------------------------------------------------------

    /** 2026-11-08T00:00:00Z: the first cycle's newest bucket. */
    static final long T0 = 1_794_096_000_000L;

    /** Run {@code cfg.cycles} cycles; one result per cycle. */
    List<CycleResult> run(Log log) throws Exception {
        VirtualClock clock = new VirtualClock();
        Vcenter vc = new Vcenter(clock);
        Ops ops = new Ops();
        ops.clock = clock;
        clock.queueCapacity = Math.max(8, 4 * cfg.workers);
        long baseline = cfg.heap ? usedAfterGc() : 0;
        CycleEngine e = new CycleEngine(vc, ops, new CycleEngine.Settings(new ClusterFilter("", ""),
                cfg.workers, 30_000, log != null, cfg.types(), cfg.vmEveryN),
                log == null ? Log.NONE : log, clock);
        List<CycleResult> out = new ArrayList<>();
        for (int i = 1; i <= cfg.cycles; i++) {
            long base = T0 + (i - 1) * cfg.intervalMs + 60_000; // a minute after the bucket
            clock.startCycle(base, i);
            vc.newest = T0 + (i - 1) * cfg.intervalMs;
            long before = ops.values.get();
            long pushMsBefore = ops.pushSimMs.get();
            long pushCallsBefore = ops.pushCalls.get();
            Map<String, Long> kindBefore = snapshot(ops.byKind);
            HeapSampler sampler = cfg.heap ? new HeapSampler(cfg.heapSampleMs) : null;
            long w0 = System.nanoTime();
            CycleEngine.Result r = e.run(base + cfg.deadlineMs);
            long wall = (System.nanoTime() - w0) / 1_000_000;
            CycleResult cr = new CycleResult();
            if (sampler != null) {
                cr.peakHeapDelta = Math.max(0, sampler.stop() - baseline);
                cr.retainedHeapDelta = Math.max(0, usedAfterGc() - baseline);
            }
            cr.cycle = i;
            cr.res = r;
            cr.wallMs = wall;
            cr.valuesPushed = ops.values.get() - before;
            cr.pushSimMs = ops.pushSimMs.get() - pushMsBefore;
            cr.pushCalls = (int) (ops.pushCalls.get() - pushCallsBefore);
            AtomicLong nb = ops.byTime.get(vc.newest);
            cr.newestBucketValues = nb == null ? 0 : nb.get();
            for (Map.Entry<String, Long> k : snapshot(ops.byKind).entrySet()) {
                long d = k.getValue() - kindBefore.getOrDefault(k.getKey(), 0L);
                if (d > 0) cr.byKind.put(k.getKey(), d);
            }
            ops.byTime.keySet().removeIf(t -> t < vc.newest - 600_000);
            out.add(cr);
        }
        return out;
    }

    private static Map<String, Long> snapshot(Map<String, AtomicLong> m) {
        Map<String, Long> s = new TreeMap<>();
        for (Map.Entry<String, AtomicLong> e : m.entrySet()) s.put(e.getKey(), e.getValue().get());
        return s;
    }

    /** Expected values for a completed cycle's newest bucket (one per series). */
    long expectedSeriesPerCycle() {
        long perHost = 0;
        long perCluster = 0;
        for (EntityType t : cfg.types()) {
            if (t.tier == EntityType.Tier.VM) continue;
            List<List<Row>> rows = templates.get(t);
            if (rows == null || rows.isEmpty()) continue;
            if (t.tier == EntityType.Tier.CLUSTER) {
                perCluster += t.size();
            } else {
                perHost += (long) rows.get(0).size() * t.size();
            }
        }
        return (long) cfg.clusters * (perCluster + (long) cfg.hosts * perHost);
    }

    // ------------------------------------------------------------------
    // Heap measurement
    // ------------------------------------------------------------------

    /**
     * Heap in use after a full collection: {@code System.gc()} three times,
     * then {@code totalMemory - freeMemory}. Noise: the collection is a
     * request, and objects of other threads (harness generating a reply,
     * the JIT) are counted too; across runs the reading varies by about 1 MB
     * on this machine. The {@link HeapSampler} reading misses transients
     * shorter than its period: sampling every 10 ms instead of 150 ms read
     * ~2 to 5 MB higher on the VM-tier case (build 15 review W4).
     */
    static long usedAfterGc() {
        Runtime rt = Runtime.getRuntime();
        for (int i = 0; i < 3; i++) System.gc();
        return rt.totalMemory() - rt.freeMemory();
    }

    /**
     * Samples heap after a GC every {@code periodMs} of wall time while a
     * cycle runs, keeping the maximum: the peak of live data at those
     * moments. Simulated time does not move during a GC pause, so sampling
     * does not change the cycle's outcome. Between samples a short-lived
     * peak (one response being parsed) can be missed; the in-flight terms
     * are bounded separately (one decoded reply per worker).
     */
    static final class HeapSampler {
        private final Thread t;
        private volatile boolean stop;
        private volatile long max;

        HeapSampler(long periodMs) {
            t = new Thread(() -> {
                while (!stop) {
                    max = Math.max(max, usedAfterGc());
                    try {
                        Thread.sleep(periodMs);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            }, "replay-heap-sampler");
            t.setDaemon(true);
            t.start();
        }

        long stop() throws InterruptedException {
            stop = true;
            t.interrupt();
            t.join();
            return Math.max(max, usedAfterGc());
        }
    }

    // ------------------------------------------------------------------
    // Standalone
    // ------------------------------------------------------------------

    static Config parse(String[] args) {
        Config c = new Config();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--clusters": c.clusters = Integer.parseInt(args[++i]); break;
                case "--hosts": c.hosts = Integer.parseInt(args[++i]); break;
                case "--cycles": c.cycles = Integer.parseInt(args[++i]); break;
                case "--workers": c.workers = Integer.parseInt(args[++i]); break;
                case "--latency-ms": c.latencyMs = Long.parseLong(args[++i]); break;
                case "--ceiling": c.ceilingPerSecond = Double.parseDouble(args[++i]); break;
                case "--model": c.perVcenter = "vcenter".equals(args[++i]); break;
                case "--tiers":
                    String t = args[++i];
                    c.tierHost = !"core".equals(t);
                    c.tierCluster = !"core".equals(t);
                    if ("all".equals(t)) {
                        c.tierDisk = true;
                        c.tierVm = true;
                    }
                    break;
                case "--vm": c.tierVm = true; break;
                case "--disk": c.tierDisk = true; break;
                case "--vm-every": c.vmEveryN = Integer.parseInt(args[++i]); break;
                case "--vms-per-host": c.vmsPerHost = Integer.parseInt(args[++i]); break;
                case "--heap": c.heap = true; break;
                case "--perf": c.perf = true; break;
                case "--pace": c.pace = Integer.parseInt(args[++i]); break;
                case "--push-ms-per-resource": c.pushMsPerResource = Double.parseDouble(args[++i]); break;
                case "--push-fixed-ms": c.pushFixedMs = Double.parseDouble(args[++i]); break;
                default: throw new IllegalArgumentException("unknown argument " + a);
            }
        }
        return c;
    }

    public static void main(String[] args) throws Exception {
        Config c = parse(args);
        ReplayHarness h = new ReplayHarness(c);
        System.out.println("replay: " + c + "; expected series per completed cycle "
                + h.expectedSeriesPerCycle());
        Log log = !c.perf ? null : new Log() {
            @Override public void info(String m) {
                if (m.startsWith("PERF pool") || m.startsWith("PERF cycle")) System.out.println(m);
            }
            @Override public void warn(String m) {}
            @Override public void error(String m) {}
        };
        for (CycleResult r : h.run(log)) System.out.println(r);
    }

    // ------------------------------------------------------------------
    // JSON writer for the templates
    // ------------------------------------------------------------------

    /** Writes the trees {@link JsonStream} reads. */
    static final class JsonWrite {
        private JsonWrite() {}

        static String write(Object v) {
            StringBuilder b = new StringBuilder();
            write(b, v);
            return b.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder b, Object v) {
            if (v == null) {
                b.append("null");
            } else if (v instanceof String) {
                b.append(Json.quote((String) v));
            } else if (v instanceof Double) {
                double d = (Double) v;
                if (d == Math.rint(d) && Math.abs(d) < 1e15) b.append((long) d);
                else b.append(d);
            } else if (v instanceof Boolean) {
                b.append(v);
            } else if (v instanceof Map) {
                b.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) v).entrySet()) {
                    if (!first) b.append(',');
                    first = false;
                    b.append(Json.quote(e.getKey())).append(':');
                    write(b, e.getValue());
                }
                b.append('}');
            } else if (v instanceof List) {
                b.append('[');
                boolean first = true;
                for (Object o : (List<Object>) v) {
                    if (!first) b.append(',');
                    first = false;
                    write(b, o);
                }
                b.append(']');
            } else {
                throw new IllegalArgumentException(v.getClass().getName());
            }
        }
    }
}
