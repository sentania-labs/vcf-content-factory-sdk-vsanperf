package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The vSphere JSON API client: vim25 session, ServiceContent, the cluster
 * inventory read, the vSAN {@code VsanPerfQueryPerf} call (parsed from the
 * gzip stream one entity at a time, build 11), the per-host
 * {@code QueryDisksForVsan} (disk tier) and the per-cluster VM read (VM
 * tier). Plain JDK
 * {@link HttpClient} (no vSAN or vim25 SDK jars), on the platform TLS
 * context the adapter passes in; no VCF Ops SDK type, so the fake-server
 * tests drive it directly.
 *
 * <p><b>Rules it enforces</b> (api map "Recommendation for the Java
 * port"): {@code _typeName} on every spec; UTC dates with {@code Z}; one
 * wildcard spec per call; the cluster always present; no
 * {@code interval}; the {@code labels} filter; {@code Accept-Encoding:
 * gzip}; one {@code vmware-api-session-id} session per adapter instance,
 * reused across cycles, logged in again once on {@code NotAuthenticated}.
 *
 * <p><b>Retries.</b> A fault body is deterministic and is never re-sent
 * (the framework retry policy is not used). Only a transport failure (no
 * response, not a timeout) is re-sent, once, if the call's time budget
 * allows. {@code NotAuthenticated} logs in again once and repeats the call
 * once; a second {@code NotAuthenticated} is thrown.
 *
 * <p><b>Secrets.</b> The password goes only into the Login body, the
 * token only into the request header. Neither appears in any log line or
 * exception message. // REDACT-SECRET
 */
final class VsanJsonClient implements CycleEngine.VcenterApi {

    /** Oldest release id the 9.1.1 server accepted (design Q5). */
    static final String PINNED_RELEASE = "8.0.3.0";
    static final String SESSION_HEADER = "vmware-api-session-id";

    private static final Pattern VIM25_VERSION = Pattern.compile(
            "<name>\\s*urn:vim25\\s*</name>\\s*<version>\\s*([0-9]+(?:\\.[0-9]+){1,3})\\s*</version>",
            Pattern.DOTALL);

    private final HttpClient http;
    private final String baseUrl;
    private final String username;
    private final String password; // REDACT-SECRET
    private final Log log;
    private final long callTimeoutMs;

    private final Object lock = new Object();
    private volatile String release = PINNED_RELEASE;
    private volatile Session session;
    private volatile boolean closed;

    /** One vim25 session and what was read with it. */
    static final class Session {
        final String token; // REDACT-SECRET
        final String instanceUuid;
        final String apiVersion;
        final String fullName;
        final String rootFolder;
        final String propertyCollector;
        final String viewManager;
        /** The ContainerView of clusters; session scoped, created on first use. */
        volatile String clusterView;
        /** Per cluster MoRef, a ContainerView of its VMs (VM tier); session scoped. */
        final java.util.concurrent.ConcurrentHashMap<String, String> vmViews =
                new java.util.concurrent.ConcurrentHashMap<>();

        Session(String token, String instanceUuid, String apiVersion, String fullName,
                String rootFolder, String propertyCollector, String viewManager) {
            this.token = token;
            this.instanceUuid = instanceUuid;
            this.apiVersion = apiVersion;
            this.fullName = fullName;
            this.rootFolder = rootFolder;
            this.propertyCollector = propertyCollector;
            this.viewManager = viewManager;
        }
    }

    /** One answered HTTP exchange. */
    static final class Reply {
        final int status;
        final String body;
        final String sessionId; // REDACT-SECRET
        final long wireBytes;
        final long decodedBytes;

        Reply(int status, String body, String sessionId, long wireBytes, long decodedBytes) {
            this.status = status;
            this.body = body;
            this.sessionId = sessionId;
            this.wireBytes = wireBytes;
            this.decodedBytes = decodedBytes;
        }
    }

    /** A body over a cap: a failed call, WARN with the cap. */
    static final class OverCapException extends IOException {
        private static final long serialVersionUID = 1L;

        OverCapException(String what, long cap) {
            super(what + ": the response is larger than the " + cap + "-byte cap; not read");
        }
    }

    /** A non-2xx answer without a fault body. */
    static final class StatusException extends IOException {
        private static final long serialVersionUID = 1L;
        final int status;

        StatusException(String what, int status) {
            super(what + " returned HTTP " + status);
            this.status = status;
        }
    }

    /**
     * @param baseUrl       {@code https://host[:port]}, no path
     * @param client        a JDK client on the platform TLS context
     * @param callTimeoutMs the per-call budget (send to last body byte)
     */
    VsanJsonClient(String baseUrl, String username, String password, HttpClient client,
            long callTimeoutMs, Log log) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password; // REDACT-SECRET
        this.http = client;
        this.callTimeoutMs = callTimeoutMs;
        this.log = log == null ? Log.NONE : log;
    }

    /** A JDK client with the given TLS context ({@code null}: JDK default trust). */
    static HttpClient httpClient(javax.net.ssl.SSLContext ssl, long connectTimeoutMs) {
        HttpClient.Builder b = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .followRedirects(HttpClient.Redirect.NEVER)
                .version(HttpClient.Version.HTTP_1_1);
        if (ssl != null) b.sslContext(ssl);
        return b.build();
    }

    String release() {
        return release;
    }

    // ------------------------------------------------------------------
    // Session
    // ------------------------------------------------------------------

    /** The current session, logging in (and reading ServiceContent) when there is none. */
    Session session(long deadline) throws IOException, InterruptedException {
        Session s = session;
        if (s != null) return s;
        synchronized (lock) {
            if (closed) throw new IOException("the vCenter client is closed");
            if (session == null) session = login(deadline);
            return session;
        }
    }

    /**
     * Replace {@code stale} with a fresh session, unless another thread
     * already did; then that one is returned.
     */
    private Session relogin(Session stale, long deadline)
            throws IOException, InterruptedException {
        synchronized (lock) {
            if (closed) throw new IOException("the vCenter client is closed");
            if (session != null && session != stale) return session;
            session = null;
            log.info("vCenter session expired (NotAuthenticated); logging in again");
            session = login(deadline);
            return session;
        }
    }

    private Session login(long deadline) throws IOException, InterruptedException {
        String token;
        try {
            token = loginOnce(deadline);
        } catch (VsanFault f) {
            if (!f.is(VsanFault.INVALID_REQUEST) || !PINNED_RELEASE.equals(release)) throw f;
            // Design Q5: the pinned id is unknown to this server; use its own.
            String own = readServerRelease(deadline);
            if (own == null || own.equals(release)) {
                throw new IOException("vCenter refused release id " + release
                        + " (" + f.faultClass + ") and its /sdk/vimServiceVersions.xml "
                        + "names no other vim25 version");
            }
            log.info("vCenter refused release id " + PINNED_RELEASE
                    + "; using the server's own vim25 version " + own);
            release = own;
            token = loginOnce(deadline);
        }
        return readContent(token, deadline);
    }

    private String loginOnce(long deadline) throws IOException, InterruptedException {
        String body = "{\"userName\":" + Json.quote(username)
                + ",\"password\":" + Json.quote(password) + "}"; // REDACT-SECRET
        Reply r;
        try {
            r = exchange("POST", vim("SessionManager/SessionManager/Login"), body, null,
                    deadline, "Login");
        } catch (StatusException e) {
            if (e.status == 404 || e.status == 405 || e.status == 400) {
                throw new IOException("vCenter did not accept the vSphere JSON API (Login "
                        + "returned HTTP " + e.status + " for release id " + release
                        + "). This pack needs the JSON API of vCenter 8.0 Update 1 or later.");
            }
            throw e;
        }
        String token = r.sessionId;
        if (token == null || token.isEmpty()) {
            throw new IOException("vCenter Login answered without a session id header");
        }
        return token;
    }

    private Session readContent(String token, long deadline)
            throws IOException, InterruptedException {
        Reply r = exchange("GET", vim("ServiceInstance/ServiceInstance/content"), null, token,
                deadline, "ServiceInstance content");
        SimpleJson c;
        try {
            c = SimpleJson.parse(r.body);
        } catch (RuntimeException e) {
            throw new Unreadable("ServiceInstance content is not valid JSON");
        }
        SimpleJson about = c.get("about");
        return new Session(token, about.get("instanceUuid").asString(),
                about.get("apiVersion").asString(), about.get("fullName").asString(),
                c.get("rootFolder").get("value").asString(),
                c.get("propertyCollector").get("value").asString(),
                c.get("viewManager").get("value").asString());
    }

    /** The server's vim25 version from {@code /sdk/vimServiceVersions.xml}, or {@code null}. */
    private String readServerRelease(long deadline) throws IOException, InterruptedException {
        Reply r = exchange("GET", "/sdk/vimServiceVersions.xml", null, null, deadline,
                "vimServiceVersions.xml");
        return parseVim25Version(r.body);
    }

    static String parseVim25Version(String xml) {
        if (xml == null) return null;
        Matcher m = VIM25_VERSION.matcher(xml);
        return m.find() ? m.group(1) : null;
    }

    /** Log out (best effort, short budget) and refuse further calls. */
    void close() {
        Session s;
        synchronized (lock) {
            closed = true;
            s = session;
            session = null;
        }
        if (s == null) return;
        try {
            exchange("POST", vim("SessionManager/SessionManager/Logout"), "{}", s.token,
                    deadlineIn(Math.min(callTimeoutMs, 5000)), "Logout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | RuntimeException e) {
            log.info("vCenter Logout failed (ignored): " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------
    // CycleEngine.VcenterApi
    // ------------------------------------------------------------------

    @Override
    public String instanceUuid(long timeoutMs) throws IOException, InterruptedException {
        return session(deadlineIn(timeoutMs)).instanceUuid;
    }

    /** Release, product and API version of the current session, for the configure log. */
    String describeSession() {
        Session s = session;
        return s == null ? "no session" : (s.fullName + ", apiVersion " + s.apiVersion
                + ", release id " + release);
    }

    /** Max continuation pages of one inventory read. */
    static final int MAX_INVENTORY_PAGES = 100;

    @Override
    public Inventory readInventory(long timeoutMs, boolean withVsanSystem)
            throws IOException, InterruptedException {
        long deadline = deadlineIn(timeoutMs);
        return authenticated(deadline, s -> readInventory(s, deadline, true, withVsanSystem));
    }

    /** Pages read by the last inventory read (for the PERF inventory line). */
    private volatile int lastInventoryPages;

    int lastInventoryPages() {
        return lastInventoryPages;
    }

    /**
     * One inventory read on the session-lived ContainerView (design
     * Addendum): created on first use, kept for the session (a re-login
     * makes a new session and so a new view). A fault on a read through the
     * view, other than {@code NotAuthenticated}, destroys it (failure
     * ignored), creates a new one and reads once more. The view moId embeds
     * the vCenter session key: it is never logged or put in a message.
     */
    private Inventory readInventory(Session s, long deadline, boolean mayRecreateView,
            boolean withVsanSystem) throws IOException, InterruptedException {
        if (s.rootFolder == null || s.propertyCollector == null || s.viewManager == null) {
            throw new IOException("ServiceInstance content names no rootFolder, "
                    + "propertyCollector or viewManager");
        }
        String view = s.clusterView;
        if (view == null) {
            view = createClusterView(s, deadline);
            s.clusterView = view;
        }
        try {
            return retrieveAll(s, view, deadline, withVsanSystem);
        } catch (VsanFault f) {
            if (f.is(VsanFault.NOT_AUTHENTICATED)) throw f;
            if (!mayRecreateView) {
                // Review B2: the server's faultstring may name the view, whose
                // moId embeds the session key; it is never passed on.
                throw new VsanFault(f.faultClass, "the inventory read on the cluster view "
                        + "failed after the view was recreated", f.keys, f.httpStatus);
            }
            log.warn("inventory read on the cluster view failed (" + f.faultClass
                    + "); recreating the view and reading once more");
            s.clusterView = null;
            destroyView(s, view, deadline);
            return readInventory(s, deadline, false, withVsanSystem);
        }
    }

    private Inventory retrieveAll(Session s, String view, long deadline, boolean withVsanSystem)
            throws IOException, InterruptedException {
        Inventory.Builder b = new Inventory.Builder();
        Reply r = exchange("POST", vim("PropertyCollector/" + s.propertyCollector
                + "/RetrievePropertiesEx"), Inventory.retrieveBody(view, withVsanSystem), s.token,
                deadline, "RetrievePropertiesEx");
        String token = b.addPage(r.body);
        int pages = 1;
        while (token != null) {
            if (++pages > MAX_INVENTORY_PAGES) {
                throw new IOException("inventory: more than " + MAX_INVENTORY_PAGES
                        + " RetrievePropertiesEx pages; read refused as incomplete");
            }
            r = exchange("POST", vim("PropertyCollector/" + s.propertyCollector
                    + "/ContinueRetrievePropertiesEx"), "{\"token\":" + Json.quote(token) + "}",
                    s.token, deadline, "ContinueRetrievePropertiesEx");
            token = b.addPage(r.body);
        }
        lastInventoryPages = pages;
        return b.build();
    }

    /** {@code DestroyView} on a view; every failure is ignored and nothing is logged. */
    private void destroyView(Session s, String view, long deadline) throws InterruptedException {
        try {
            exchange("POST", vim("ContainerView/" + pathSegment(view) + "/DestroyView"), "{}",
                    s.token, deadline - deadlineIn(5000) < 0 ? deadline : deadlineIn(5000), "DestroyView");
        } catch (IOException | RuntimeException ignored) {
            // destroy-ignore (design failure table); the moId is never logged
        }
    }

    /** Percent-encode a moId for a path segment (the view moId carries [ and ]). */
    static String pathSegment(String moid) {
        StringBuilder b = new StringBuilder();
        for (byte c : moid.getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            int ch = c & 0xff;
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                    || ch == '-' || ch == '.' || ch == '_' || ch == '~') {
                b.append((char) ch);
            } else {
                b.append('%').append(String.format("%02X", ch));
            }
        }
        return b.toString();
    }

    private String createClusterView(Session s, long deadline)
            throws IOException, InterruptedException {
        return createView(s, "Folder", s.rootFolder, "ClusterComputeResource", deadline);
    }

    /** {@code CreateContainerView} of {@code type} objects under a container; the id is never logged. */
    private String createView(Session s, String containerType, String container, String type,
            long deadline) throws IOException, InterruptedException {
        String body = "{\"container\":{\"_typeName\":\"ManagedObjectReference\",\"type\":"
                + Json.quote(containerType) + ",\"value\":" + Json.quote(container) + "},"
                + "\"type\":[" + Json.quote(type) + "],\"recursive\":true}";
        Reply r = exchange("POST", vim("ViewManager/" + s.viewManager + "/CreateContainerView"),
                body, s.token, deadline, "CreateContainerView");
        String v;
        try {
            v = SimpleJson.parse(r.body).get("value").asString();
        } catch (RuntimeException e) {
            v = null;
        }
        if (v == null || v.isEmpty()) {
            throw new Unreadable("CreateContainerView answered without a view id");
        }
        return v;
    }

    /** The perf query path (design Transport table). */
    String perfPath() {
        return "/sdk/vim25/" + release
                + "/vsan/VsanPerformanceManager/vsan-performance-manager/VsanPerfQueryPerf";
    }

    /**
     * The {@code VsanPerfQueryPerf} body: exactly one wildcard spec with
     * {@code _typeName}, UTC dates with {@code Z}, the type's labels, no
     * {@code interval}; the cluster MoRef always present.
     */
    static String perfBody(EntityType type, String clusterMoid, long startMs, long endMs) {
        StringBuilder labels = new StringBuilder("[");
        for (String l : type.labels()) {
            if (labels.length() > 1) labels.append(',');
            labels.append(Json.quote(l));
        }
        labels.append(']');
        return "{\"querySpecs\":[{\"_typeName\":\"VsanPerfQuerySpec\",\"entityRefId\":"
                + Json.quote(type.id + ":*") + ",\"startTime\":" + Json.quote(utc(startMs))
                + ",\"endTime\":" + Json.quote(utc(endMs)) + ",\"labels\":" + labels + "}],"
                + "\"cluster\":{\"_typeName\":\"ManagedObjectReference\","
                + "\"type\":\"ClusterComputeResource\",\"value\":" + Json.quote(clusterMoid) + "}}";
    }

    /** ISO-8601 UTC with {@code Z}, whole seconds. A non-zero offset is silently misread by the server. */
    static String utc(long epochMs) {
        return Instant.ofEpochMilli(epochMs).truncatedTo(ChronoUnit.SECONDS).toString();
    }

    @Override
    public CycleEngine.PerfCall queryPerf(String clusterMoid, EntityType type, long startMs,
            long endMs, long timeoutMs) throws IOException, InterruptedException {
        long deadline = deadlineIn(timeoutMs);
        String body = perfBody(type, clusterMoid, startMs, endMs);
        return authenticated(deadline, s -> {
            Raw raw = send("POST", perfPath(), body, s.token, deadline,
                    "VsanPerfQueryPerf " + type.id, type.wireCap());
            if (raw.status != 200) {
                failure(raw, "VsanPerfQueryPerf " + type.id);
            }
            if (raw.body == null || raw.body.bytes == null) {
                throw new Unreadable("VsanPerfQueryPerf " + type.id + " returned no body");
            }
            // v2 Sizing requirement 3: parse from the (gzip) stream one entity
            // at a time; only the wire bytes are held, under the type's cap.
            PerfParser.Result parsed;
            CountingInput counted = null;
            try {
                // Review N2 (build 11): the gzip header is read here, inside the
                // try, so a body that is not gzip or is too short is Unreadable
                // (never re-sent as a transport failure).
                counted = new CountingInput(raw.gzip
                        ? new java.util.zip.GZIPInputStream(
                                new java.io.ByteArrayInputStream(raw.body.bytes), 8192)
                        : new java.io.ByteArrayInputStream(raw.body.bytes));
                try (java.io.InputStream in = counted) {
                    parsed = PerfParser.parse(in, type);
                }
            } catch (Unreadable u) {
                throw u;
            } catch (IOException e) {
                throw new Unreadable("VsanPerfQueryPerf " + type.id + ": the gzip body could not "
                        + "be read (" + e.getClass().getSimpleName() + ")");
            }
            return new CycleEngine.PerfCall(parsed, raw.status, raw.body.bytes.length,
                    counted.count);
        });
    }

    /** Counts the decoded bytes read (PERF call {@code decoded_bytes}). */
    private static final class CountingInput extends java.io.FilterInputStream {
        long count;

        CountingInput(java.io.InputStream in) {
            super(in);
        }

        @Override public int read() throws IOException {
            int c = super.read();
            if (c >= 0) count++;
            return c;
        }

        @Override public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }
    }

    /** The {@code QueryDisksForVsan} path for a host's {@code configManager.vsanSystem}. */
    String disksPath(String vsanSystemMoid) {
        return vim("HostVsanSystem/" + pathSegment(vsanSystemMoid) + "/QueryDisksForVsan");
    }

    /**
     * {@code QueryDisksForVsan} on one host (P2: body {@code {}}, a bare
     * array of {@code VsanHostDiskResult}): the host's non-empty vSAN disk
     * uuids.
     */
    @Override
    public java.util.List<String> queryDisksForVsan(String vsanSystemMoid, long timeoutMs)
            throws IOException, InterruptedException {
        long deadline = deadlineIn(timeoutMs);
        return authenticated(deadline, s -> {
            Reply r = exchange("POST", disksPath(vsanSystemMoid), "{}", s.token, deadline,
                    "QueryDisksForVsan");
            if (r.body == null) throw new Unreadable("QueryDisksForVsan returned no body");
            return Inventory.vsanDiskUuids(r.body);
        });
    }

    /**
     * The VM read for one cluster (VM tier, P2): a session-lived
     * ContainerView rooted at the cluster, {@code RetrievePropertiesEx} in
     * pages of {@link VmInventory#PAGE}, each page reduced at once. A fault
     * on the read (other than {@code NotAuthenticated}) destroys the view,
     * creates a new one and reads once more, as the inventory read does.
     */
    @Override
    public VmInventory readVms(String clusterMoid, boolean withDevices, long timeoutMs)
            throws IOException, InterruptedException {
        long deadline = deadlineIn(timeoutMs);
        return authenticated(deadline, s -> readVms(s, clusterMoid, withDevices, deadline, true));
    }

    private VmInventory readVms(Session s, String clusterMoid, boolean withDevices, long deadline,
            boolean mayRecreateView) throws IOException, InterruptedException {
        if (s.propertyCollector == null || s.viewManager == null) {
            throw new IOException("ServiceInstance content names no propertyCollector or "
                    + "viewManager");
        }
        String view = s.vmViews.get(clusterMoid);
        if (view == null) {
            view = createView(s, "ClusterComputeResource", clusterMoid, "VirtualMachine",
                    deadline);
            s.vmViews.put(clusterMoid, view);
        }
        try {
            VmInventory.Builder b = new VmInventory.Builder(withDevices);
            Reply r = exchange("POST", vim("PropertyCollector/" + s.propertyCollector
                    + "/RetrievePropertiesEx"), VmInventory.retrieveBody(view, withDevices),
                    s.token, deadline, "RetrievePropertiesEx (VMs)");
            b.bytes(r.wireBytes, r.decodedBytes);
            String token = b.addPage(r.body);
            int pages = 1;
            while (token != null) {
                if (++pages > VmInventory.MAX_PAGES) {
                    throw new IOException("VM read: more than " + VmInventory.MAX_PAGES
                            + " pages; read refused as incomplete");
                }
                r = exchange("POST", vim("PropertyCollector/" + s.propertyCollector
                        + "/ContinueRetrievePropertiesEx"), "{\"token\":" + Json.quote(token)
                        + "}", s.token, deadline, "ContinueRetrievePropertiesEx (VMs)");
                b.bytes(r.wireBytes, r.decodedBytes);
                token = b.addPage(r.body);
            }
            return b.build();
        } catch (VsanFault f) {
            if (f.is(VsanFault.NOT_AUTHENTICATED)) throw f;
            if (!mayRecreateView) {
                throw new VsanFault(f.faultClass, "the VM read on the cluster's view failed "
                        + "after the view was recreated", f.keys, f.httpStatus);
            }
            log.warn("VM read on a cluster's view failed (" + f.faultClass
                    + "); recreating the view and reading once more");
            s.vmViews.remove(clusterMoid, view);
            destroyView(s, view, deadline);
            return readVms(s, clusterMoid, withDevices, deadline, false);
        }
    }

    // ------------------------------------------------------------------
    // Transport
    // ------------------------------------------------------------------

    @FunctionalInterface
    private interface WithSession<T> {
        T run(Session s) throws IOException, InterruptedException;
    }

    /**
     * Run {@code call} on the current session; on {@code NotAuthenticated}
     * log in again once and run it once more. A transport failure (no
     * answer, not a timeout) is re-sent once inside the same budget.
     */
    private <T> T authenticated(long deadline, WithSession<T> call)
            throws IOException, InterruptedException {
        Session s = session(deadline);
        try {
            return retryTransport(deadline, () -> call.run(s));
        } catch (VsanFault f) {
            if (!f.is(VsanFault.NOT_AUTHENTICATED)) throw f;
        }
        Session fresh = relogin(s, deadline);
        try {
            return retryTransport(deadline, () -> call.run(fresh));
        } catch (VsanFault f) {
            if (f.is(VsanFault.NOT_AUTHENTICATED)) {
                throw new VsanFault(VsanFault.NOT_AUTHENTICATED,
                        "vCenter refused the session again right after a fresh login",
                        f.keys, f.httpStatus);
            }
            throw f;
        }
    }

    @FunctionalInterface
    private interface Attempt<T> {
        T run() throws IOException, InterruptedException;
    }

    private static <T> T retryTransport(long deadlineNanos, Attempt<T> a)
            throws IOException, InterruptedException {
        try {
            return a.run();
        } catch (IOException e) {
            if (!isTransport(e) || System.nanoTime() >= deadlineNanos) throw e;
        }
        return a.run();
    }

    /**
     * No answer at all (connection refused or reset): not a timeout, fault,
     * status, cap, TLS failure or an unreadable answer (review N4).
     */
    static boolean isTransport(IOException e) {
        return !(e instanceof VsanFault) && !(e instanceof HttpTimeoutException)
                && !(e instanceof OverCapException) && !(e instanceof StatusException)
                && !(e instanceof Unreadable)
                && !(e instanceof javax.net.ssl.SSLException)
                && !(e.getCause() instanceof javax.net.ssl.SSLException);
    }

    /**
     * The absolute deadline ({@link System#nanoTime}) {@code timeoutMs} from
     * now. Each public call fixes it once; login, re-login, the transport
     * retry and every page then share it (Codex review: a retried call
     * reused the whole budget).
     */
    static long deadlineIn(long timeoutMs) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(1, timeoutMs));
    }

    /** Milliseconds left until {@code deadline}, at most 0 when spent. */
    static long remainingMs(long deadline) {
        return TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
    }

    private String vim(String tail) {
        return "/sdk/vim25/" + release + "/" + tail;
    }

    /**
     * One HTTP exchange, send to last body byte, bounded by the time left
     * until {@code deadline} (absolute {@link System#nanoTime}) and by the
     * per-call timeout, whichever is less; a spent deadline fails before
     * sending (HttpTimeoutException). 200 and 204 return; an error status with a fault
     * body throws {@link VsanFault}; anything else {@link StatusException}.
     */
    Reply exchange(String method, String path, String body, String token, long deadline,
            String what) throws IOException, InterruptedException {
        Raw raw = send(method, path, body, token, deadline, what, CappedBody.WIRE_CAP);
        int status = raw.status;
        long decodedCap = status == 200 ? CappedBody.DECODED_CAP : CappedBody.FAULT_CAP;
        String text = null;
        long wire = 0;
        if (raw.body != null && raw.body.bytes != null) {
            wire = raw.body.bytes.length;
            text = CappedBody.decode(raw.body.bytes, raw.gzip, decodedCap);
            if (text == null) {
                if (status == 200) throw new OverCapException(what + " (decoded)", decodedCap);
                throw new StatusException(what, status);
            }
        }
        long decoded = text == null ? 0 : text.length();
        if (status == 200 || status == 204) {
            return new Reply(status, text, raw.sessionId, wire, decoded);
        }
        VsanFault fault = VsanFault.parse(text, status);
        if (fault != null) throw fault;
        throw new StatusException(what, status);
    }

    /** A sent request's status and raw (still encoded) body. */
    private static final class Raw {
        final int status;
        final CappedBody.Body body;
        final boolean gzip;
        final String sessionId; // REDACT-SECRET

        Raw(int status, CappedBody.Body body, boolean gzip, String sessionId) {
            this.status = status;
            this.body = body;
            this.gzip = gzip;
            this.sessionId = sessionId;
        }
    }

    /** A non-200 answer to a streamed call: its fault, or the status. */
    private static void failure(Raw raw, String what) throws IOException {
        String text = raw.body == null || raw.body.bytes == null ? null
                : CappedBody.decode(raw.body.bytes, raw.gzip, CappedBody.FAULT_CAP);
        if (raw.status == 204) throw new Unreadable(what + " returned no body");
        VsanFault fault = VsanFault.parse(text, raw.status);
        if (fault != null) throw fault;
        throw new StatusException(what, raw.status);
    }

    /**
     * Send one request and read its body raw, a 200 body up to
     * {@code okCap} wire bytes (over it: {@link OverCapException}), an error
     * body up to the fault cap.
     */
    private Raw send(String method, String path, String body, String token, long deadline,
            String what, long okCap) throws IOException, InterruptedException {
        long left = remainingMs(deadline);
        if (left <= 0) {
            throw new HttpTimeoutException(what + ": the call's time budget is spent");
        }
        long budget = Math.min(left, callTimeoutMs);
        HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofMillis(budget))
                .header("Accept", "application/json")
                .header("Accept-Encoding", "gzip");
        if (token != null) rb.header(SESSION_HEADER, token); // REDACT-SECRET
        if ("POST".equals(method)) {
            rb.header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            rb.GET();
        }
        CompletableFuture<HttpResponse<CappedBody.Body>> f =
                http.sendAsync(rb.build(), new CappedBody(okCap, CappedBody.FAULT_CAP));
        HttpResponse<CappedBody.Body> resp;
        try {
            resp = f.get(budget, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            f.cancel(true);
            throw new HttpTimeoutException(what + ": no complete answer within " + budget + " ms");
        } catch (InterruptedException e) {
            f.cancel(true);
            throw e;
        } catch (ExecutionException e) {
            Throwable c = e.getCause();
            if (c instanceof HttpTimeoutException) {
                throw new HttpTimeoutException(what + ": no answer within " + budget + " ms");
            }
            if (c instanceof IOException) {
                throw new IOException(what + ": " + c.getClass().getSimpleName()
                        + (c.getMessage() == null ? "" : ": " + c.getMessage()), c);
            }
            throw new IOException(what + ": " + String.valueOf(c), c);
        }
        int status = resp.statusCode();
        CappedBody.Body raw = resp.body();
        boolean gzip = CappedBody.isGzip(resp.headers().firstValue("Content-Encoding").orElse(null));
        long cap = status == 200 ? okCap : CappedBody.FAULT_CAP;
        if (raw != null && raw.overCap) {
            if (status == 200) throw new OverCapException(what, cap);
            throw new StatusException(what, status);
        }
        return new Raw(status, raw, gzip, resp.headers().firstValue(SESSION_HEADER).orElse(null));
    }
}
