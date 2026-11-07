package com.vcfcf.adapters.vsanperf;

import java.util.ArrayList;
import java.util.List;

/**
 * T1: identity. Host MoRef to Ops resource id under this vCenter's VCID
 * only; a MoRef present in two vCenters resolves only under the matching
 * VCID; ambiguous, VCID-less and id-less rows are never matched; paging.
 * Bodies constructed in the Suite API resource list shape.
 */
public final class OpsHostMapTest {

    static String row(String id, String moid, String vcid) {
        StringBuilder ids = new StringBuilder("{\"identifierType\":{\"name\":\"VMEntityName\"},\"value\":\"x\"}");
        if (moid != null) ids.append(",{\"identifierType\":{\"name\":\"VMEntityObjectID\"},\"value\":\"")
                .append(moid).append("\"}");
        if (vcid != null) ids.append(",{\"identifierType\":{\"name\":\"VMEntityVCID\"},\"value\":\"")
                .append(vcid).append("\"}");
        return "{" + (id == null ? "" : "\"identifier\":\"" + id + "\",")
                + "\"resourceKey\":{\"name\":\"x\",\"resourceIdentifiers\":[" + ids + "]}}";
    }

    static String page(long total, String... rows) {
        return "{\"pageInfo\":{\"totalCount\":" + total + "},\"resourceList\":["
                + String.join(",", rows) + "]}";
    }

    public static void main(String[] args) throws Exception {
        List<String> paths = new ArrayList<>();
        String[] pages = {
                page(7, row("r1", "host-10", "VC-A"), row("r2", "host-10", "VC-B"),
                        row("r3", "host-11", null), row("r4", "host-12", "VC-A")),
                page(7, row("r5", "host-12", "VC-A"), row(null, "host-13", "VC-A"),
                        row("r7", "host-14", "VC-A"))};
        OpsHostMap m = OpsHostMap.load(p -> {
            paths.add(p);
            return pages[paths.size() - 1];
        }, "VC-A");
        T.eq(2, paths.size(), "two pages read");
        T.check(paths.get(0).contains("adapterKind=VMWARE&resourceKind=HostSystem"), "path");
        T.check(paths.get(1).endsWith("&page=1"), "second page");
        T.eq("r1", m.resourceId("host-10"), "host-10 resolves under VC-A only");
        OpsHostMap b = OpsHostMap.load(p -> pages[p.endsWith("page=0") ? 0 : 1], "VC-B");
        T.eq("r2", b.resourceId("host-10"), "same MoRef under VC-B resolves to VC-B's row");
        T.eq(null, m.resourceId("host-11"), "no VCID: never matched");
        T.eq(null, m.resourceId("host-12"), "ambiguous MoRef: neither");
        T.eq(null, m.resourceId("host-13"), "no resource id");
        T.eq("r7", m.resourceId("host-14"), "page 2 row");
        T.eq(1, m.otherVcenter, "other vCenter counted");

        T.expect(java.io.IOException.class, () -> OpsHostMap.load(p -> p.endsWith("page=0")
                ? page(10, row("r", "h", "VC-A")) : page(10),
                "VC-A"), "a short list (no rows past page 0) is not complete");
        T.expect(java.io.IOException.class, () -> OpsHostMap.load(p -> "{}", "VC-A"),
                "no resourceList");
        T.eq(0, OpsHostMap.load(p -> "{\"pageInfo\":{\"totalCount\":0}}", "VC-A").size(),
                "an empty list with total 0 is complete");
        T.expect(java.io.IOException.class, () -> OpsHostMap.load(p -> page(0), ""),
                "no vCenter uuid: refused");
        System.out.println("OpsHostMapTest: all assertions passed");
    }
}
