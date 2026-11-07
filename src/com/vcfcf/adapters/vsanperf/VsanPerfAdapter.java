package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.VcfCfAdapter;
import com.vcfcf.adapter.http.ManagedHttpClient;
import com.vcfcf.adapter.spi.VcfCfCollector;
import com.vcfcf.adapter.spi.VcfCfTester;
import com.vcfcf.adapter.stitch.SuiteApiBatchResult;
import com.vcfcf.adapter.stitch.SuiteApiStitcher;

import com.integrien.alive.common.adapter3.AdapterBase;
import com.integrien.alive.common.adapter3.MetricData;
import com.integrien.alive.common.adapter3.ResourceStatus;
import com.integrien.alive.common.adapter3.TestParam;
import com.integrien.alive.common.adapter3.config.AdapterConfig;
import com.integrien.alive.common.adapter3.config.ResourceConfig;
import com.integrien.alive.common.util.CommonConstants.ResourceStatusEnum;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * VCF Content Factory vSAN Performance: vSAN Performance Service metrics
 * for DOM client, DOM owner and vSAN pNICs, read over the vSphere JSON API
 * and stitched onto the vCenter adapter's own VMWARE HostSystem objects
 * through the Suite API. The pak creates no object but its adapter
 * instance (design {@code knowledge/designs/vsanperf-mp-v1.md}).
 *
 * <p><b>Where the cycle runs (build 7).</b> In {@code collect()} for the
 * one object the pak discovers per instance ({@link #COLLECTOR_KIND}), as
 * compliance does for {@code ComplianceWorld}: the platform does not list
 * the adapter instance among the resources it collects, so build 5's cycle
 * in {@code rediscover} could only ever show ERROR "nothing to collect"
 * (devel 9.0.2). A failed cycle now throws from {@code collect()}, and an
 * unreachable vCenter maps to DOWN. The self-metrics ({@code Collection|*})
 * still go onto the adapter instance resource, which {@code rediscover}
 * hands over each cycle.
 *
 * <p>Everything else is SDK-free ({@link CycleEngine}, {@link VsanJsonClient})
 * and tested by {@code ci/run_java_tests.sh}.
 */
public final class VsanPerfAdapter extends VcfCfAdapter<VsanPerfConfig> {

    /** MUST match describe.xml and adapter.yaml. */
    private static final String ADAPTER_KIND = "vcfcf_vsanperf";

    /** One configuration's clients and engine, published with one volatile write. */
    private static final class Runtime {
        final VsanPerfConfig cfg;
        final VsanJsonClient client;
        final SuiteApiStitcher stitcher;
        final CycleEngine engine;

        Runtime(VsanPerfConfig cfg, VsanJsonClient client, SuiteApiStitcher stitcher,
                CycleEngine engine) {
            this.cfg = cfg;
            this.client = client;
            this.stitcher = stitcher;
            this.engine = engine;
        }
    }

    private volatile Runtime runtime;
    /** Why the last configure built nothing; every cycle fails with it. */
    private volatile String configError;

    public VsanPerfAdapter() {
        super(ADAPTER_KIND);
    }

    public VsanPerfAdapter(String adapterDir, Integer adapterInstanceId) {
        super(ADAPTER_KIND, adapterDir, adapterInstanceId);
    }

    // ------------------------------------------------------------------
    // Configure
    // ------------------------------------------------------------------

    @Override
    protected void configureAdapter(ResourceStatus status, ResourceConfig rc) {
        Runtime old = this.runtime;
        this.runtime = null;
        this.configError = null;
        try {
            VsanPerfConfig cfg = buildConfig(rc);
            this.config = cfg;
            VsanJsonClient client = buildClient(cfg);
            SuiteApiStitcher sas = null;
            try {
                sas = SuiteApiStitcher.create(this, componentLogger(SuiteApiStitcher.class));
            } catch (RuntimeException e) {
                logWarn("the local VCF Operations Suite API is unavailable to this instance ("
                        + e.getClass().getSimpleName() + "); nothing can be pushed onto "
                        + "HostSystem until it is");
            }
            CycleEngine engine = new CycleEngine(client, sas == null ? null : opsApi(sas),
                    new CycleEngine.Settings(cfg.filter(), cfg.workers,
                            cfg.callTimeoutSeconds * 1000L, cfg.perfTimingLogging),
                    log(componentLogger(CycleEngine.class)), System::currentTimeMillis);
            engine.setStopSignal(this::isAbortRequested);
            this.runtime = new Runtime(cfg, client, sas, engine);
            // Never the principal, password or token. // REDACT-SECRET
            logInfo("VsanPerfAdapter configured: " + cfg + " releaseId="
                    + VsanJsonClient.PINNED_RELEASE + " (server version on refusal) stitcher="
                    + (sas != null));
        } catch (IllegalArgumentException e) {
            this.configError = e.getMessage();
            logError("VsanPerfAdapter configuration is invalid: " + e.getMessage(), e);
        } catch (RuntimeException e) {
            this.configError = "the vCenter client could not be built ("
                    + e.getClass().getSimpleName() + "); see the configure ERROR in the log";
            logError("VsanPerfAdapter could not build its vCenter client", e);
        } finally {
            if (old != null) release(old);
        }
    }

    private VsanPerfConfig buildConfig(ResourceConfig rc) {
        return new VsanPerfConfig(getIdentifier(rc, "vcenter_host"),
                getCredentialField(rc, "username"),
                getCredentialField(rc, "password"), // REDACT-SECRET
                getIdentifier(rc, "cluster_include"), getIdentifier(rc, "cluster_exclude"),
                getIdentifier(rc, "max_parallel_clusters"),
                getIdentifier(rc, "call_timeout_seconds"),
                getIdentifier(rc, "cycle_deadline_seconds"),
                getIdentifier(rc, "perf_timing_logging"));
    }

    /** The JSON client on the platform trust store with the certificate name checked. */
    private VsanJsonClient buildClient(VsanPerfConfig cfg) {
        javax.net.ssl.SSLContext ssl = getPlatformSslContext(cfg.vcenterHost);
        return new VsanJsonClient(cfg.baseUrl(), cfg.username, cfg.password(), // REDACT-SECRET
                VsanJsonClient.httpClient(ssl, VsanPerfConfig.CONNECT_TIMEOUT_S * 1000L),
                cfg.callTimeoutSeconds * 1000L, log(componentLogger(VsanJsonClient.class)));
    }

    private void release(Runtime r) {
        try {
            r.client.close();
        } catch (RuntimeException ignored) {
            // best effort
        }
        if (r.stitcher != null) {
            try {
                r.stitcher.discard();
            } catch (RuntimeException ignored) {
                // best effort
            }
        }
    }

    @Override
    protected List<String> certificateCheckUrls(ResourceConfig rc) {
        String url = VsanPerfConfig.rootUrl(getIdentifier(rc, "vcenter_host"));
        return url == null ? Collections.emptyList() : Collections.singletonList(url);
    }

    // ------------------------------------------------------------------
    // Test Connection: self-contained (configure has not run)
    // ------------------------------------------------------------------

    @Override
    protected VcfCfTester<VsanPerfConfig> getTester() {
        return (cfg, http, param) -> {
            ResourceConfig rc = testResourceConfig(param);
            if (rc == null) {
                throw new Exception("Test Connection: no adapter instance configuration on "
                        + "the request");
            }
            VsanPerfConfig c = buildConfig(rc);
            VsanJsonClient client = buildClient(c);
            try {
                long t = c.callTimeoutSeconds * 1000L;
                String uuid = client.instanceUuid(t);
                if (uuid == null || uuid.trim().isEmpty()) {
                    throw new Exception("vCenter answered without an instance UUID "
                            + "(about.instanceUuid); hosts could not be matched");
                }
                Inventory inv = client.readInventory(t);
                ClusterFilter f = c.filter();
                int vsan = 0;
                int inScope = 0;
                for (Inventory.Cluster cl : inv.clusters) {
                    if (!cl.isVsan()) continue;
                    vsan++;
                    if (f.accepts(cl.moid, cl.name)) inScope++;
                }
                testSuiteApi();
                logInfo("Test OK: " + client.describeSession() + "; " + inv.clusters.size()
                        + " cluster(s), " + vsan + " vSAN-enabled, " + inScope + " in scope");
            } finally {
                client.close();
            }
        };
    }

    /** Whether the local Suite API answers; logged only (the test may run on another node). */
    private void testSuiteApi() {
        SuiteApiStitcher sas = null;
        try {
            sas = SuiteApiStitcher.create(this, componentLogger(SuiteApiStitcher.class));
            sas.get("/api/versions/current");
            logInfo("Test Connection: the local VCF Operations Suite API answered");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            logWarn("Test Connection: the local VCF Operations Suite API did not answer from "
                    + "where this test ran (" + e.getClass().getSimpleName() + "). Values are "
                    + "pushed through it; the test still passes, since it can run on another "
                    + "node than collection.");
        } finally {
            if (sas != null) {
                try {
                    sas.discard();
                } catch (RuntimeException ignored) {
                    // best effort
                }
            }
        }
    }

    private static ResourceConfig testResourceConfig(TestParam param) {
        if (param == null) return null;
        AdapterConfig ac = param.getAdapterConfig();
        return ac == null ? null : ac.getAdapterInstResource();
    }

    // ------------------------------------------------------------------
    // Collection
    // ------------------------------------------------------------------

    /**
     * The one object this pak discovers per adapter instance (build 7). The
     * platform does not list the adapter instance among the resources it
     * collects (devel 9.0.2, build 5: the account row read "Resource
     * discovery failed and there was nothing to collect"), so a cycle run
     * from {@code rediscover} could never report DOWN. The cycle runs in
     * {@code collect()} for this object instead, the way compliance runs
     * its cycle for {@code ComplianceWorld}, so an unreachable vCenter is a
     * normal collect failure mapped to DOWN.
     */
    static final String COLLECTOR_KIND = "VsanPerfCollector";
    static final String COLLECTOR_NAME = "VCF Content Factory vSAN Performance Collector";

    /**
     * The adapter instance resource of the current cycle, handed to
     * {@code rediscover} (the self-metrics go onto it, where build 5 proved
     * they read back on devel 9.0.2).
     */
    private volatile ResourceConfig cycleInstance;

    @Override
    protected void onCycleStart() {
        cycleInstance = null;
    }

    @Override
    protected boolean discoverOnCollect() {
        return true;
    }

    /**
     * The collector object, identified by the account's own identity
     * (vCenter Server and Clusters to include) exactly as the account stores
     * it, trimmed only (build 8: two accounts that differ in case or spacing
     * are distinct accounts, so they must not share one object key), known
     * from the configuration alone. Not by the vCenter instance UUID: that needs a login, and the
     * object must exist when vCenter is unreachable for the failure to show
     * as DOWN. An invalid configuration throws (the account shows ERROR with
     * its message).
     */
    @Override
    protected void enumerateResources(com.vcfcf.adapter.spi.ResourceSink sink) throws Exception {
        Runtime r = this.runtime;
        if (r == null) {
            throw new IllegalStateException(notConfigured());
        }
        sink.accept(collectorResource(r.cfg));
    }

    private static ResourceConfig collectorResource(VsanPerfConfig cfg) {
        com.integrien.alive.common.adapter3.ResourceKey key =
                new com.integrien.alive.common.adapter3.ResourceKey(COLLECTOR_NAME,
                        COLLECTOR_KIND, ADAPTER_KIND);
        key.addIdentifier(new com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig(
                "vcenter", cfg.vcenterHost, true));
        key.addIdentifier(new com.integrien.alive.common.adapter3.config.ResourceIdentifierConfig(
                "cluster_scope", cfg.clusterScope(), true));
        return new ResourceConfig(key);
    }

    private String notConfigured() {
        String invalid = configError;
        return invalid != null ? "the adapter instance configuration is invalid: " + invalid
                : "the adapter instance is not configured";
    }

    @Override
    protected VcfCfCollector<VsanPerfConfig> getCollector() {
        return new VcfCfCollector<VsanPerfConfig>() {

            /** Only to receive the adapter instance resource; no work, never throws. */
            @Override
            public boolean needsRediscovery(VsanPerfConfig cfg) {
                return true;
            }

            @Override
            public void rediscover(VsanPerfConfig cfg, ManagedHttpClient http,
                    ResourceConfig adapterInst, AdapterBase adapter) {
                cycleInstance = adapterInst;
            }

            @Override
            public void collect(VsanPerfConfig cfg, ManagedHttpClient http, ResourceConfig rc,
                    List<MetricData> out, AdapterBase adapter) throws Exception {
                if (!COLLECTOR_KIND.equals(rc.getResourceKind())) return;
                runCycle();
            }

            @Override
            public ResourceStatusEnum mapCollectException(Exception e) {
                return Failures.isUnreachable(e) ? ResourceStatusEnum.RESOURCE_STATUS_DOWN
                        : ResourceStatusEnum.RESOURCE_STATUS_ERROR;
            }
        };
    }

    /** One cycle; the self-metrics go onto the adapter instance, then a failure throws. */
    private void runCycle() throws Exception {
        if (isAbortRequested()) return;
        final ResourceConfig inst = cycleInstance != null ? cycleInstance : instanceResource();
        CycleReport.Sink sink = inst == null ? null : new CycleReport.Sink() {
            private final long now = System.currentTimeMillis();

            @Override public void metric(String key, double value) {
                pushMetric(inst, key, value, now);
            }

            @Override public void property(String key, String value) {
                pushStringProperty(inst, key, value);
            }
        };
        Runtime r = this.runtime;
        if (r == null) {
            String msg = notConfigured();
            if (sink != null) sink.property(CycleReport.LAST_ERROR, msg);
            throw new IllegalStateException(msg);
        }
        Integer interval = null;
        try {
            interval = inst == null ? getMonitoringIntervalInSec() : inst.getIntervalInSec();
        } catch (RuntimeException ignored) {
            // the default interval applies
        }
        long deadline = System.currentTimeMillis() + r.cfg.deadlineSeconds(interval) * 1000L;
        CycleReport.run(r.engine, deadline, sink);
    }

    /** {@code AdapterBase.getAdapterInstResource()}, or null. */
    private ResourceConfig instanceResource() {
        try {
            return getAdapterInstResource();
        } catch (RuntimeException e) {
            return null;
        }
    }

    @Override
    public void onDiscard() {
        Runtime r = this.runtime;
        this.runtime = null;
        if (r != null) release(r);
        super.onDiscard();
    }

    // ------------------------------------------------------------------
    // Adapters to the SDK-free ports
    // ------------------------------------------------------------------

    private static CycleEngine.OpsApi opsApi(final SuiteApiStitcher sas) {
        return new CycleEngine.OpsApi() {
            @Override
            public String get(String path) throws java.io.IOException, InterruptedException {
                return sas.get(path);
            }

            @Override
            public CycleEngine.PushOutcome pushStats(Map<String, Map<String, Double>> stats,
                    long ts) {
                SuiteApiBatchResult r = sas.pushStatsBatch(stats, ts);
                return new CycleEngine.PushOutcome(r.getSent(), r.getBatchesOk(),
                        r.getBatchesFailed(), r.isClean());
            }
        };
    }

    private static Log log(final com.integrien.alive.common.adapter3.Logger l) {
        if (l == null) return Log.NONE;
        return new Log() {
            @Override public void info(String m) { l.info(m); }
            @Override public void warn(String m) { l.warn(m); }
            @Override public void error(String m) { l.error(m); }
        };
    }
}
