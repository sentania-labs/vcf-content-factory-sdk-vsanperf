package com.vcfcf.adapters.vsanperf;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.GZIPOutputStream;

/**
 * T2: {@link VsanJsonClient} against a JDK {@code HttpServer} speaking the
 * vSphere JSON API shapes: session header on every call, re-login once on
 * NotAuthenticated, fault bodies read through the small cap, gzip, the
 * size cap, the per-call timeout, the release-id fallback, two-page
 * inventory paging, the ContainerView lifecycle, one transport retry, and
 * Logout. Perf and inventory bodies are the verbatim captures; Login,
 * ServiceContent, faults other than the captured one, paged results and
 * vimServiceVersions.xml are constructed in the recorded shapes.
 */
public final class ClientFakeServerTest {

    static final String PW = "Pw-not-for-logs-123";

    static final class Req {
        final String method, path, session, acceptEncoding, body;

        Req(String method, String path, String session, String acceptEncoding, String body) {
            this.method = method;
            this.path = path;
            this.session = session;
            this.acceptEncoding = acceptEncoding;
            this.body = body;
        }
    }

    static final class Server implements AutoCloseable {
        final HttpServer http;
        final java.util.concurrent.ExecutorService pool;
        final List<Req> reqs = new CopyOnWriteArrayList<>();
        final Set<String> valid = ConcurrentHashMap.newKeySet();
        final Set<String> deadViews = ConcurrentHashMap.newKeySet();
        final AtomicInteger logins = new AtomicInteger();
        final AtomicInteger views = new AtomicInteger();
        final AtomicInteger perfCalls = new AtomicInteger();
        volatile boolean rejectPinned;
        volatile boolean loginNotFound;
        volatile boolean paged;
        volatile String perfMode = "ok"; // ok, expireOnce, alwaysNotAuth, fault, huge, slow, resetOnce
        volatile String release = "8.0.3.0";
        /** A view fault whose faultstring names the view (real moId shape). */
        volatile boolean viewFaultNamesView;

        Server() throws IOException {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            pool = Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "fake-vcenter");
                t.setDaemon(true);
                return t;
            });
            http.setExecutor(pool);
            http.createContext("/", this::handle);
            http.start();
        }

        String base() {
            return "http://127.0.0.1:" + http.getAddress().getPort();
        }

        int count(String pathPart) {
            int n = 0;
            for (Req r : reqs) if (r.path.contains(pathPart)) n++;
            return n;
        }

        @Override public void close() {
            http.stop(0);
            pool.shutdownNow();
        }

        void handle(HttpExchange ex) throws IOException {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getRawPath();
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String sid = ex.getRequestHeaders().getFirst("vmware-api-session-id");
            String ae = ex.getRequestHeaders().getFirst("Accept-Encoding");
            reqs.add(new Req(method, path, sid, ae, body));
            try {
                route(ex, method, path, body, sid);
            } catch (Exception e) {
                send(ex, 599, "{}", false);
            }
        }

        void route(HttpExchange ex, String method, String path, String body, String sid)
                throws Exception {
            if (path.equals("/sdk/vimServiceVersions.xml")) {
                send(ex, 200, "<namespaces><namespace><name>urn:vim25</name><version>" + "9.1.1.0"
                        + "</version></namespace></namespaces>", false);
                return;
            }
            String rel = path.startsWith("/sdk/vim25/") ? path.split("/")[3] : "";
            if (path.endsWith("/SessionManager/SessionManager/Login")) {
                if (loginNotFound) {
                    send(ex, 404, "", false);
                    return;
                }
                if (rejectPinned && rel.equals("8.0.3.0")) {
                    fault(ex, "InvalidRequest", "Unknown release ID.");
                    return;
                }
                release = rel;
                String tok = "tok-" + logins.incrementAndGet();
                valid.add(tok);
                ex.getResponseHeaders().add("vmware-api-session-id", tok);
                send(ex, 200, "{\"_typeName\":\"UserSession\",\"userName\":\"svc\"}", false);
                return;
            }
            if (sid == null || !valid.contains(sid)) {
                fault(ex, "NotAuthenticated", "The session is not authenticated.");
                return;
            }
            if (path.endsWith("/SessionManager/SessionManager/Logout")) {
                valid.remove(sid);
                send(ex, 204, null, false);
            } else if (path.endsWith("/ServiceInstance/ServiceInstance/content")) {
                send(ex, 200, "{\"_typeName\":\"ServiceContent\",\"rootFolder\":{\"_typeName\":"
                        + "\"ManagedObjectReference\",\"value\":\"group-d1\",\"type\":\"Folder\"},"
                        + "\"propertyCollector\":{\"_typeName\":\"ManagedObjectReference\",\"value\":"
                        + "\"propertyCollector\",\"type\":\"PropertyCollector\"},\"viewManager\":{"
                        + "\"_typeName\":\"ManagedObjectReference\",\"value\":\"ViewManager\",\"type\":"
                        + "\"ViewManager\"},\"about\":{\"_typeName\":\"AboutInfo\",\"fullName\":"
                        + "\"VMware vCenter Server 9.1.1\",\"apiVersion\":\"9.1.1.0\",\"instanceUuid\":"
                        + "\"vc-uuid-1\"}}", true);
            } else if (path.endsWith("/ViewManager/ViewManager/CreateContainerView")) {
                send(ex, 200, "{\"_typeName\":\"ManagedObjectReference\",\"value\":"
                        + "\"session[52b0c3a1-1111-2222-3333-4444555" + sid.substring(4) + "]"
                        + "52f1d2e3-aaaa-bbbb-cccc-ddddeeeeffff-view-" + views.incrementAndGet()
                        + "\",\"type\":\"ContainerView\"}", false);
            } else if (path.contains("/ContainerView/") && path.endsWith("/DestroyView")) {
                send(ex, 204, null, false);
            } else if (path.endsWith("/RetrievePropertiesEx")
                    && !path.endsWith("/ContinueRetrievePropertiesEx")) {
                if (viewFaultNamesView) {
                    String view = body.replaceAll("(?s).*\"value\":\"(session\\[[^\"]*)\",\"type\":"
                            + "\"ContainerView\".*", "$1");
                    fault(ex, "ManagedObjectNotFound", "The object 'vim.view.ContainerView:"
                            + view + "' has already been deleted or has not been completely created");
                    return;
                }
                for (String v : deadViews) {
                    if (body.contains(v)) {
                        fault(ex, "ManagedObjectNotFound", "The object has already been deleted");
                        return;
                    }
                }
                if (paged) {
                    send(ex, 200, InventoryTest.result("tok-page-2",
                            InventoryTest.cluster("domain-c6004", "h1", "h2"),
                            InventoryTest.host("h1", true, "U", "n1")), true);
                } else {
                    send(ex, 200, T.vendor("retrievepropertiesex-cluster-hosts-2026-11-06.json"), true);
                }
            } else if (path.endsWith("/ContinueRetrievePropertiesEx")) {
                T.check(body.contains("tok-page-2"), "continuation carries the token");
                send(ex, 200, InventoryTest.result(null, InventoryTest.host("h2", true, "U", "n2")),
                        true);
            } else if (path.endsWith("/vsan/VsanPerformanceManager/vsan-performance-manager/"
                    + "VsanPerfQueryPerf")) {
                int n = perfCalls.incrementAndGet();
                switch (perfMode) {
                    case "expireOnce":
                        if (n == 1) {
                            valid.remove(sid);
                            fault(ex, "NotAuthenticated", "The session is not authenticated.");
                            return;
                        }
                        break;
                    case "alwaysNotAuth":
                        fault(ex, "NotAuthenticated", "The session is not authenticated.");
                        return;
                    case "fault":
                        send(ex, 500, T.vendor("queryperf-fault-wildcardlimit-2026-11-06.json"), true);
                        return;
                    case "huge":
                        StringBuilder b = new StringBuilder("[");
                        while (b.length() < 5 * 1024 * 1024) b.append("{\"x\":\"0123456789\"},");
                        send(ex, 200, b.append("{}]").toString(), false);
                        return;
                    case "junk":
                        send(ex, 200, "<html>maintenance</html>", false);
                        return;
                    case "slow":
                        Thread.sleep(3_000);
                        break;
                    case "resetOnce":
                        if (n == 1) {
                            ex.close(); // no status line: a transport failure
                            return;
                        }
                        break;
                    default:
                        break;
                }
                send(ex, 200, T.vendor("queryperf-host-domowner-2026-11-06.json"), true);
            } else {
                send(ex, 404, "", false);
            }
        }

        void fault(HttpExchange ex, String cls, String fs) throws IOException {
            send(ex, 500, "{\"_typeName\":\"" + cls + "\",\"faultstring\":\"" + fs + "\"}", false);
        }

        static void send(HttpExchange ex, int status, String body, boolean gzip) throws IOException {
            if (body == null) {
                ex.sendResponseHeaders(status, -1);
                ex.close();
                return;
            }
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            if (gzip) {
                ByteArrayOutputStream bo = new ByteArrayOutputStream();
                try (GZIPOutputStream g = new GZIPOutputStream(bo)) {
                    g.write(b);
                }
                b = bo.toByteArray();
                ex.getResponseHeaders().add("Content-Encoding", "gzip");
            }
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
            if (b.length > 0) {
                try (OutputStream o = ex.getResponseBody()) {
                    o.write(b);
                }
            }
            ex.close();
        }
    }

    static VsanJsonClient client(Server s, long timeoutMs, T.RecLog log) {
        return new VsanJsonClient(s.base(), "svc@vsphere.local", PW,
                VsanJsonClient.httpClient(null, 2_000), timeoutMs, log);
    }

    static CycleEngine.PerfCall perf(VsanJsonClient c) throws Exception {
        long now = System.currentTimeMillis();
        return c.queryPerf("domain-c6004", EntityType.HOST_DOMOWNER, now - CycleEngine.WINDOW_MS,
                now, 10_000);
    }

    public static void main(String[] args) throws Exception {
        sessionGzipAndReuse();
        reloginOnce();
        notAuthenticatedTwice();
        faultBody();
        overCap();
        callTimeout();
        releaseFallback();
        loginNotFound();
        inventoryPagingAndView();
        transportRetry();
        viewIdNeverLeaks();
        unreadableNotRetried();
        System.out.println("ClientFakeServerTest: all assertions passed");
    }

    static void sessionGzipAndReuse() throws Exception {
        try (Server s = new Server()) {
            T.RecLog log = new T.RecLog();
            VsanJsonClient c = client(s, 10_000, log);
            T.eq("vc-uuid-1", c.instanceUuid(10_000), "instanceUuid from ServiceContent");
            CycleEngine.PerfCall p = perf(c);
            perf(c);
            T.eq(5, p.result.entities.size(), "gzip body decoded and parsed");
            T.eq(190, p.result.seriesKept, "all DOM owner series");
            T.check(p.wireBytes < p.decodedBytes, "wire bytes are the gzip bytes");
            T.eq(1, s.logins.get(), "one session reused");
            for (Req r : s.reqs) {
                T.eq("gzip", r.acceptEncoding, r.path + " asks for gzip");
                if (!r.path.endsWith("/Login")) T.eq("tok-1", r.session, r.path + " carries the session");
                if (!r.path.endsWith("/Login")) T.check(!r.body.contains(PW), "password only in Login");
            }
            c.close();
            T.eq(1, s.count("/Logout"), "Logout on close");
            T.expect(IOException.class, () -> perf(c), "closed client refuses calls");
            T.check(!log.text().contains(PW) && !log.text().contains("tok-"), "no secret logged");
        }
    }

    static void reloginOnce() throws Exception {
        try (Server s = new Server()) {
            s.perfMode = "expireOnce";
            VsanJsonClient c = client(s, 10_000, new T.RecLog());
            CycleEngine.PerfCall p = perf(c);
            T.eq(5, p.result.entities.size(), "retried call answered");
            T.eq(2, s.logins.get(), "logged in again once");
            T.eq(2, s.perfCalls.get(), "call repeated once");
            T.eq("tok-2", s.reqs.get(s.reqs.size() - 1).session, "retry on the new session");
        }
    }

    static void notAuthenticatedTwice() throws Exception {
        try (Server s = new Server()) {
            s.perfMode = "alwaysNotAuth";
            VsanJsonClient c = client(s, 10_000, new T.RecLog());
            VsanFault f = T.expect(VsanFault.class, () -> perf(c), "second NotAuthenticated");
            T.check(f.is(VsanFault.NOT_AUTHENTICATED), "thrown as NotAuthenticated");
            T.eq(2, s.logins.get(), "no login loop");
            T.eq(2, s.perfCalls.get(), "no retry loop");
        }
    }

    static void faultBody() throws Exception {
        try (Server s = new Server()) {
            s.perfMode = "fault";
            VsanJsonClient c = client(s, 10_000, new T.RecLog());
            VsanFault f = T.expect(VsanFault.class, () -> perf(c), "HTTP 500 fault");
            T.eq("com.vmware.vsan.perfsvc.fault.queryperf.wildcardlimit", f.firstKey(),
                    "gzip fault body read and dispatched");
            T.eq(1, s.perfCalls.get(), "a fault is never re-sent");
        }
    }

    static void overCap() throws Exception {
        try (Server s = new Server()) {
            s.perfMode = "huge";
            VsanJsonClient c = client(s, 10_000, new T.RecLog());
            T.expect(VsanJsonClient.OverCapException.class, () -> perf(c), "over the 4 MB cap");
            T.eq(1, s.perfCalls.get(), "fetched once");
        }
    }

    static void callTimeout() throws Exception {
        try (Server s = new Server()) {
            s.perfMode = "slow";
            VsanJsonClient c = client(s, 500, new T.RecLog());
            c.instanceUuid(10_000);
            long t = System.currentTimeMillis();
            T.expect(HttpTimeoutException.class, () -> perf(c), "per-call timeout");
            T.check(System.currentTimeMillis() - t < 2_000, "fires at the budget, not the server");
            T.eq(1, s.perfCalls.get(), "a timeout is not retried");
        }
    }

    static void releaseFallback() throws Exception {
        try (Server s = new Server()) {
            s.rejectPinned = true;
            T.RecLog log = new T.RecLog();
            VsanJsonClient c = client(s, 10_000, log);
            c.instanceUuid(10_000);
            T.eq("9.1.1.0", c.release(), "server's own vim25 version");
            perf(c);
            T.check(s.reqs.get(s.reqs.size() - 1).path.startsWith("/sdk/vim25/9.1.1.0/vsan/"),
                    "perf path uses it");
            T.eq(1, log.count("refused release id 8.0.3.0"), "logged once");
        }
    }

    static void loginNotFound() throws Exception {
        try (Server s = new Server()) {
            s.loginNotFound = true;
            IOException e = T.expect(IOException.class, () -> client(s, 10_000, new T.RecLog())
                    .instanceUuid(10_000), "no JSON API");
            T.check(e.getMessage().contains("8.0 Update 1"), "says what is needed: " + e.getMessage());
            T.check(!e.getMessage().contains(PW), "no password");
        }
    }

    static void inventoryPagingAndView() throws Exception {
        try (Server s = new Server()) {
            T.RecLog log = new T.RecLog();
            VsanJsonClient c = client(s, 10_000, log);
            Inventory inv = c.readInventory(10_000);
            T.eq(5, inv.clusters.get(0).hosts.size(), "verbatim inventory through the client");
            c.readInventory(10_000);
            T.eq(1, s.views.get(), "view created once and kept for the session");

            s.paged = true;
            Inventory two = c.readInventory(10_000);
            T.eq(1, s.count("/ContinueRetrievePropertiesEx"), "second page fetched");
            T.eq(2, c.lastInventoryPages(), "two pages");
            T.eq(2, two.clusters.get(0).vsanNodes(), "page-2 host joined");

            // The view faults: destroy (ignored), recreate, read once more.
            s.paged = false;
            s.deadViews.add("view-1");
            Inventory again = c.readInventory(10_000);
            T.eq(1, again.clusters.size(), "read after recreating the view");
            T.eq(2, s.views.get(), "view recreated");
            T.eq(1, s.count("/DestroyView"), "old view destroyed");
            T.check(s.reqs.stream().anyMatch(r -> r.path.contains("session%5B")), "moId encoded");
            T.check(!log.text().contains("session[") && !log.text().contains("view-"),
                    "the view moId is never logged");

            // A re-login makes a new session and so a new view.
            s.valid.clear();
            c.readInventory(10_000);
            T.eq(3, s.views.get(), "new view after re-login");
        }
    }

    /**
     * Review B2: a view fault whose server text names the view (moId
     * {@code session[<uuid>]<uuid>}, the vpxd session key), twice. The id
     * reaches neither the log, the thrown message, nor last_error.
     */
    static void viewIdNeverLeaks() throws Exception {
        try (Server s = new Server()) {
            s.viewFaultNamesView = true;
            T.RecLog log = new T.RecLog();
            VsanJsonClient c = client(s, 10_000, log);
            VsanFault f = T.expect(VsanFault.class, () -> c.readInventory(10_000),
                    "second view fault");
            T.check(!f.getMessage().contains("session["), "thrown message: " + f.getMessage());
            T.check(!f.faultString.contains("session["), "fault text");
            T.check(f.getMessage().contains("failed after the view was recreated"),
                    "the fixed text replaces the server's");
            T.eq(2, s.views.get(), "recreated once, then failed");
            T.check(s.count("/DestroyView") == 1, "first view destroyed");
            T.check(!log.text().contains("session["), "log: " + log.text());
            // The same through the engine: failure, last_error and the summary line.
            T.RecLog elog = new T.RecLog();
            CycleEngine e = new CycleEngine(c, EngineTest.labOps(EngineTest.labInventory()),
                    new CycleEngine.Settings(new ClusterFilter("", ""), 4, 10_000, true), elog,
                    System::currentTimeMillis);
            CycleEngine.Result r = e.run(System.currentTimeMillis() + 30_000);
            T.check(r.failure != null && !r.failure.contains("session["), "failure: " + r.failure);
            T.check(!r.lastErrorProperty().contains("session["), "last_error");
            T.check(!elog.text().contains("session[") && !log.text().contains("session["),
                    "no log line");
        }
        // Defence in depth: any fault text is scrubbed.
        VsanFault direct = new VsanFault("ManagedObjectNotFound",
                "obj vim.view.ContainerView:session[52b0c3a1-1111]52f1d2e3-aaaa' gone", List.of(), 500);
        T.check(!direct.getMessage().contains("52b0c3a1") && !direct.getMessage().contains("52f1d2e3"),
                "scrubbed: " + direct.getMessage());
        T.eq("a session[...]' b", VsanFault.scrub("a session[x-1]y-2' b"), "scrub stops at a quote");
    }

    /** Review N4: an answer that is not usable is not re-sent. */
    static void unreadableNotRetried() throws Exception {
        try (Server s = new Server()) {
            s.perfMode = "junk";
            VsanJsonClient c = client(s, 10_000, new T.RecLog());
            T.expect(Unreadable.class, () -> perf(c), "junk 200 body");
            T.eq(1, s.perfCalls.get(), "sent once");
        }
    }

    static void transportRetry() throws Exception {
        try (Server s = new Server()) {
            s.perfMode = "resetOnce";
            VsanJsonClient c = client(s, 10_000, new T.RecLog());
            CycleEngine.PerfCall p = perf(c);
            T.eq(5, p.result.entities.size(), "answered after one transport retry");
            T.eq(2, s.perfCalls.get(), "sent twice");
        }
    }
}
