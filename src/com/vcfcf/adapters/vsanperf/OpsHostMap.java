package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeSet;
import java.util.Set;

/**
 * VCF Operations resource ids of this vCenter's VMWARE HostSystem
 * objects, keyed by host MoRef, from the Suite API resource list (the
 * compliance {@code ComplianceStitcher} identity, build 102).
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

    private final Map<String, String> byMoid;
    final int rows;
    final int otherVcenter;
    final int notIndexed;
    final Set<String> ambiguous;

    private OpsHostMap(Map<String, String> byMoid, int rows, int otherVcenter, int notIndexed,
            Set<String> ambiguous) {
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

    /** The Suite API path for one page. */
    static String path(int page) {
        return "/api/resources?adapterKind=VMWARE&resourceKind=HostSystem&pageSize="
                + PAGE_SIZE + "&page=" + page;
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
        if (vcUuid == null || vcUuid.isEmpty()) {
            throw new IOException("the vCenter instance UUID is unknown; hosts are matched on "
                    + "MoRef plus vCenter UUID only");
        }
        Map<String, String> byMoid = new HashMap<>();
        Set<String> ambiguous = new TreeSet<>();
        int rows = 0;
        int other = 0;
        int notIndexed = 0;
        for (int page = 0; ; page++) {
            if (page >= MAX_PAGES) {
                throw new IOException("HostSystem list longer than " + MAX_PAGES + " pages");
            }
            SimpleJson root;
            try {
                root = SimpleJson.parse(get.get(path(page)));
            } catch (RuntimeException e) {
                throw new IOException("HostSystem list page " + page + " is not valid JSON");
            }
            SimpleJson list = root.get("resourceList");
            long total = root.get("pageInfo").get("totalCount").asLong();
            if (!list.isList()) {
                if (!root.get("pageInfo").get("totalCount").isNull() && total == 0) break;
                throw new IOException("HostSystem list page " + page + " has no resourceList");
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
                throw new IOException("HostSystem list ended after " + rows + " of " + total
                        + " rows");
            }
        }
        return new OpsHostMap(byMoid, rows, other, notIndexed, ambiguous);
    }

    @Override
    public String toString() {
        return size() + " HostSystem(s) of this vCenter indexed (" + rows + " rows; "
                + otherVcenter + " other vCenters; " + notIndexed + " not indexable; "
                + ambiguous.size() + " ambiguous MoRef(s))";
    }
}
