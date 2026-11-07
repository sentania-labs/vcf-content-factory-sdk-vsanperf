package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

/**
 * VCF Operations resource ids of this vCenter's VMWARE HostSystem (or,
 * since build 10, ClusterComputeResource) objects, keyed by MoRef, from the
 * Suite API resource list (the compliance {@code ComplianceStitcher}
 * identity, build 102).
 *
 * <p><b>ClusterComputeResource (build 10) and VirtualMachine (build 11).</b>
 * Same identifiers as HostSystem, {@code VMEntityObjectID} +
 * {@code VMEntityVCID}, both part of uniqueness on both kinds (design P2
 * addendum: read on devel 9.0.2, (MoRef, VCID) matched 45 of 45 VMs, and a
 * test push onto a cluster and a VM read back). The live read-back by this
 * pak is still owed at T5. A row without them is never matched, so data is
 * dropped (counted), never misplaced. {@code VMEntityInstanceUUID} is not
 * used: it was empty on exactly the powered-on vSAN VMs on devel.
 *
 * <p><b>Identity.</b> A row is indexed only when it carries a resource id
 * ({@code identifier}), a {@code VMEntityObjectID} (MoRef) and a
 * {@code VMEntityVCID} equal to this vCenter's {@code about.instanceUuid}.
 * MoRefs repeat across vCenters (the MOID trap), so a row of another
 * vCenter or with no VCID is never matched. A MoRef carried by two rows of
 * this vCenter is ambiguous and matched to neither. No name fallback: a
 * wrong host is data corruption, a missing one is visible.
 *
 * <p><b>Why the vCenter filter is client side (review N6).</b> The design's
 * call plan asks for the list "filtered to this vCenter by VMEntityVCID".
 * {@code GET /api/resources} has no identifier filter in the vendor spec
 * (factory {@code reference/docs/operations-api*.json}, 8.18, 9.0 and 9.1:
 * {@code adapterKind}, {@code resourceKind}, {@code adapterInstanceId},
 * {@code propertyName}/{@code propertyValue}, ..., no identifiers), and
 * neither has {@code POST /api/resources/query}. Narrowing by the VMWARE
 * adapter instance's id would be a second lookup with no proof it returns
 * exactly this vCenter's hosts, so it is not guessed. Compliance reads the
 * same list the same way. Cost: one paged read of every VMWARE HostSystem
 * (5,000 per page) every 12th cycle or on a host-set change or new miss.
 */
final class OpsHostMap {

    static final int PAGE_SIZE = 5000;
    static final int MAX_PAGES = 40;
    static final String HOST_SYSTEM = "HostSystem";
    static final String CLUSTER = "ClusterComputeResource";
    static final String VIRTUAL_MACHINE = "VirtualMachine";
    /**
     * VirtualMachine page size (v2 §VirtualMachine): ~2.9 KB per VM and not
     * gzipped (P2), so 500 per page is ~1.45 MB through one parse.
     */
    static final int VM_PAGE_SIZE = 500;
    /** 40,000 VMs at 500 per page. */
    static final int VM_MAX_PAGES = 80;

    /** Pages read to build this map (PERF identity). */
    int pages;

    /** The VMWARE resource kind indexed. */
    final String kind;

    private final Map<String, String> byMoid;
    final int rows;
    final int otherVcenter;
    final int notIndexed;
    final Set<String> ambiguous;

    private OpsHostMap(String kind, Map<String, String> byMoid, int rows, int otherVcenter,
            int notIndexed, Set<String> ambiguous) {
        this.kind = kind;
        this.byMoid = Collections.unmodifiableMap(byMoid);
        this.rows = rows;
        this.otherVcenter = otherVcenter;
        this.notIndexed = notIndexed;
        this.ambiguous = Collections.unmodifiableSet(ambiguous);
    }

    /** The resource id for a host MoRef of this vCenter, or {@code null}. */
    String resourceId(String moid) {
        return moid == null ? null : byMoid.get(moid);
    }

    int size() {
        return byMoid.size();
    }

    /** The Suite API path for one page of HostSystem. */
    static String path(int page) {
        return path(HOST_SYSTEM, page);
    }

    /** The Suite API path for one page of {@code kind}. */
    static String path(String kind, int page) {
        return "/api/resources?adapterKind=VMWARE&resourceKind=" + kind + "&pageSize="
                + pageSize(kind) + "&page=" + page;
    }

    static int pageSize(String kind) {
        return VIRTUAL_MACHINE.equals(kind) ? VM_PAGE_SIZE : PAGE_SIZE;
    }

    private static int maxPages(String kind) {
        return VIRTUAL_MACHINE.equals(kind) ? VM_MAX_PAGES : MAX_PAGES;
    }

    /**
     * One page; an {@link IOException} is retried once (P2: VCF Operations
     * closes an idle keep-alive connection after about 30 s, so the first
     * request of a cycle can meet a dropped connection).
     */
    private static String page(Getter get, String kind, int page)
            throws IOException, InterruptedException {
        try {
            return get.get(path(kind, page));
        } catch (IOException e) {
            return get.get(path(kind, page));
        }
    }

    /** Reads the paged list through {@code get}. */
    interface Getter {
        String get(String path) throws IOException, InterruptedException;
    }

    /**
     * Load every page and index this vCenter's hosts.
     *
     * @throws IOException on a failed or incomplete read (unparseable page,
     *         no {@code resourceList}, fewer rows than {@code totalCount});
     *         the caller keeps its previous map rather than treat a short
     *         list as complete
     */
    static OpsHostMap load(Getter get, String vcUuid) throws IOException, InterruptedException {
        return load(get, vcUuid, HOST_SYSTEM);
    }

    /** {@link #load(Getter, String)} for another VMWARE kind with the same identifiers. */
    static OpsHostMap load(Getter get, String vcUuid, String kind)
            throws IOException, InterruptedException {
        if (vcUuid == null || vcUuid.isEmpty()) {
            throw new IOException("the vCenter instance UUID is unknown; hosts are matched on "
                    + "MoRef plus vCenter UUID only");
        }
        Map<String, String> byMoid = new HashMap<>();
        Set<String> ambiguous = new TreeSet<>();
        int rows = 0;
        int other = 0;
        int notIndexed = 0;
        int pagesRead = 0;
        for (int page = 0; ; page++) {
            pagesRead++;
            if (page >= maxPages(kind)) {
                throw new IOException(kind + " list longer than " + maxPages(kind) + " pages");
            }
            SimpleJson root;
            try {
                root = SimpleJson.parse(page(get, kind, page));
            } catch (RuntimeException e) {
                throw new IOException(kind + " list page " + page + " is not valid JSON");
            }
            SimpleJson list = root.get("resourceList");
            long total = root.get("pageInfo").get("totalCount").asLong();
            if (!list.isList()) {
                if (!root.get("pageInfo").get("totalCount").isNull() && total == 0) break;
                throw new IOException(kind + " list page " + page + " has no resourceList");
            }
            int n = 0;
            for (SimpleJson r : list.asList()) {
                n++;
                rows++;
                String id = r.get("identifier").asString();
                String moid = null;
                String vcid = null;
                for (SimpleJson ri : r.get("resourceKey").get("resourceIdentifiers").asList()) {
                    String k = ri.get("identifierType").get("name").asString();
                    if ("VMEntityObjectID".equals(k)) moid = ri.get("value").asString();
                    else if ("VMEntityVCID".equals(k)) vcid = ri.get("value").asString();
                }
                if (vcid != null && !vcid.isEmpty() && !vcUuid.equals(vcid)) {
                    other++;
                    continue;
                }
                if (vcid == null || vcid.isEmpty() || id == null || id.isEmpty()
                        || moid == null || moid.isEmpty()) {
                    notIndexed++;
                    continue;
                }
                if (ambiguous.contains(moid)) continue;
                if (byMoid.containsKey(moid)) {
                    // Review N6 (build 11): offset paging over a list that changes
                    // mid-read can return a row twice; the same resource id again is
                    // not ambiguous, only a different id for the same MoRef is.
                    if (id.equals(byMoid.get(moid))) continue;
                    byMoid.remove(moid);
                    ambiguous.add(moid);
                    continue;
                }
                byMoid.put(moid, id);
            }
            boolean totalKnown = !root.get("pageInfo").get("totalCount").isNull();
            if (!totalKnown) break; // no pageInfo: one page is the whole list
            if (rows >= total) break;
            if (n == 0) {
                throw new IOException(kind + " list ended after " + rows + " of " + total
                        + " rows");
            }
        }
        OpsHostMap m = new OpsHostMap(kind, byMoid, rows, other, notIndexed, ambiguous);
        m.pages = pagesRead;
        return m;
    }

    @Override
    public String toString() {
        return size() + " " + kind + "(s) of this vCenter indexed (" + rows + " rows; "
                + otherVcenter + " other vCenters; " + notIndexed + " not indexable; "
                + ambiguous.size() + " ambiguous MoRef(s))";
    }
}
