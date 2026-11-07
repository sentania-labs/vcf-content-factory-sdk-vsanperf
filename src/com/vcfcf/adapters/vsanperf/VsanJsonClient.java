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
 * inventory read and the vSAN {@code VsanPerfQueryPerf} call. Plain JDK
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
    Session session(long timeoutMs) throws IOException, InterruptedException {
        Session s = session;
        if (s != null) return s;
        synchronized (lock) {
            if (closed) throw new IOException("the vCenter client is closed");
            if (session == null) session = login(timeoutMs);
            return session;
        }
    }

    /**
     * Replace {@code stale} with a fresh session, unless another thread
     * already did; then that one is returned.
     */
    private Session relogin(Session stale, long timeoutMs)
            throws IOException, InterruptedException {
        synchronized (lock) {
            if (closed) throw new IOException("the vCenter client is closed");
            if (session != null && session != stale) return session;
            session = null;
            log.info("vCenter session expired (NotAuthenticated); logging in again");
            session = login(timeoutMs);
            return session;
        }
    }

    private Session login(long timeoutMs) throws IOException, InterruptedException {
        String token;
        try {
            token = loginOnce(timeoutMs);
        } catch (VsanFault f) {
            if (!f.is(VsanFault.INVALID_REQUEST) || !PINNED_RELEASE.equals(release)) throw f;
            // Design Q5: the pinned id is unknown to this server; use its own.
            String own = readServerRelease(timeoutMs);
            if (own == null || own.equals(release)) {
                throw new IOException("vCenter refused release id " + release
                        + " (" + f.faultClass + ") and its /sdk/vimServiceVersions.xml "
                        + "names no other vim25 version");
            }
            log.info("vCenter refused release id " + PINNED_RELEASE
                    + "; using the server's own vim25 version " + own);
            release = own;
            token = loginOnce(timeoutMs);
        }
        return readContent(token, timeoutMs);
    }

    private String loginOnce(long timeoutMs) throws IOException, InterruptedException {
        String body = "{\"userName\":" + Json.quote(username)
                + ",\"password\":" + Json.quote(password) + "}"; // REDACT-SECRET
        Reply r;
        try {
            r = exchange("POST", vim("SessionManager/SessionManager/Login"), body, null,
                    timeoutMs, "Login");
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

    private Session readContent(String token, long timeoutMs)
            throws IOException, InterruptedException {
        Reply r = exchange("GET", vim("ServiceInstance/ServiceInstance/content"), null, token,
                timeoutMs, "ServiceInstance content");
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
    private String readServerRelease(long timeoutMs) throws IOException, InterruptedException {
        Reply r = exchange("GET", "/sdk/vimServiceVersions.xml", null, null, timeoutMs,
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
                    Math.min(callTimeoutMs, 5000), "Logout");
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
        return session(timeoutMs).instanceUuid;
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
    public Inventory readInventory(long timeoutMs) throws IOException, InterruptedException {
        return authenticated(timeoutMs, s -> readInventory(s, timeoutMs, true));
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
    private Inventory readInventory(Session s, long timeoutMs, boolean mayRecreateView)
            throws IOException, InterruptedException {
        if (s.rootFolder == null || s.propertyCollector == null || s.viewManager == null) {
            throw new IOException("ServiceInstance content names no rootFolder, "
                    + "propertyCollector or viewManager");
        }
        String view = s.clusterView;
        if (view == null) {
            view = createClusterView(s, timeoutMs);
            s.clusterView = view;
        }
        try {
            return retrieveAll(s, view, timeoutMs);
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
            destroyView(s, view);
            return readInventory(s, timeoutMs, false);
        }
    }

    private Inventory retrieveAll(Session s, String view, long timeoutMs)
            throws IOException, InterruptedException {
        Inventory.Builder b = new Inventory.Builder();
        Reply r = exchange("POST", vim("PropertyCollector/" + s.propertyCollector
                + "/RetrievePropertiesEx"), Inventory.retrieveBody(view), s.token,
                timeoutMs, "RetrievePropertiesEx");
        String token = b.addPage(r.body);
        int pages = 1;
        while (token != null) {
            if (++pages > MAX_INVENTORY_PAGES) {
                throw new IOException("inventory: more than " + MAX_INVENTORY_PAGES
                        + " RetrievePropertiesEx pages; read refused as incomplete");
            }
            r = exchange("POST", vim("PropertyCollector/" + s.propertyCollector
                    + "/ContinueRetrievePropertiesEx"), "{\"token\":" + Json.quote(token) + "}",
                    s.token, timeoutMs, "ContinueRetrievePropertiesEx");
            token = b.addPage(r.body);
        }
        lastInventoryPages = pages;
        return b.build();
    }

    /** {@code DestroyView} on a view; every failure is ignored and nothing is logged. */
    private void destroyView(Session s, String view) throws InterruptedException {
        try {
            exchange("POST", vim("ContainerView/" + pathSegment(view) + "/DestroyView"), "{}",
                    s.token, Math.min(callTimeoutMs, 5000), "DestroyView");
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

    private String createClusterView(Session s, long timeoutMs)
            throws IOException, InterruptedException {
        String body = "{\"container\":{\"_typeName\":\"ManagedObjectReference\",\"type\":\"Folder\","
                + "\"value\":" + Json.quote(s.rootFolder) + "},\"type\":[\"ClusterComputeResource\"],"
                + "\"recursive\":true}";
        Reply r = exchange("POST", vim("ViewManager/" + s.viewManager + "/CreateContainerView"),
                body, s.token, timeoutMs, "CreateContainerView");
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
        String body = perfBody(type, clusterMoid, startMs, endMs);
        return authenticated(timeoutMs, s -> {
            Reply r = exchange("POST", perfPath(), body, s.token, timeoutMs,
                    "VsanPerfQueryPerf " + type.id);
            if (r.body == null) {
                throw new Unreadable("VsanPerfQueryPerf " + type.id + " returned no body");
            }
            PerfParser.Result parsed = PerfParser.parse(r.body, type);
            return new CycleEngine.PerfCall(parsed, r.status, r.wireBytes, r.decodedBytes);
        });
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
    private <T> T authenticated(long timeoutMs, WithSession<T> call)
            throws IOException, InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        Session s = session(timeoutMs);
        try {
            return retryTransport(deadline, () -> call.run(s));
        } catch (VsanFault f) {
            if (!f.is(VsanFault.NOT_AUTHENTICATED)) throw f;
        }
        Session fresh = relogin(s, remaining(deadline, timeoutMs));
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

    private static long remaining(long deadlineNanos, long floorMs) {
        long ms = TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
        return ms > 0 ? ms : Math.min(floorMs, 1000);
    }

    private String vim(String tail) {
        return "/sdk/vim25/" + release + "/" + tail;
    }

    /**
     * One HTTP exchange bounded by {@code timeoutMs} from send to the
     * last body byte. 200 and 204 return; an error status with a fault
     * body throws {@link VsanFault}; anything else {@link StatusException}.
     */
    Reply exchange(String method, String path, String body, String token, long timeoutMs,
            String what) throws IOException, InterruptedException {
        long budget = Math.max(1, Math.min(timeoutMs, callTimeoutMs));
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
                http.sendAsync(rb.build(), new CappedBody());
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
        long cap = status == 200 ? CappedBody.WIRE_CAP : CappedBody.FAULT_CAP;
        if (raw != null && raw.overCap) {
            if (status == 200) throw new OverCapException(what, cap);
            throw new StatusException(what, status);
        }
        long decodedCap = status == 200 ? CappedBody.DECODED_CAP : CappedBody.FAULT_CAP;
        String text = null;
        long wire = 0;
        if (raw != null && raw.bytes != null) {
            wire = raw.bytes.length;
            text = CappedBody.decode(raw.bytes, gzip, decodedCap);
            if (text == null) {
                if (status == 200) throw new OverCapException(what + " (decoded)", decodedCap);
                throw new StatusException(what, status);
            }
        }
        long decoded = text == null ? 0 : text.length();
        if (status == 200 || status == 204) {
            String sid = resp.headers().firstValue(SESSION_HEADER).orElse(null);
            return new Reply(status, text, sid, wire, decoded);
        }
        VsanFault fault = VsanFault.parse(text, status);
        if (fault != null) throw fault;
        throw new StatusException(what, status);
    }
}
