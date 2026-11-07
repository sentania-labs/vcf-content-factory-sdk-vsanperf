package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.util.List;

/** T1: fault parsing (verbatim samples) and the request builder rules. */
public final class FaultAndRequestTest {

    public static void main(String[] args) throws Exception {
        faults();
        perfBody();
        inventoryBody();
        versions();
        System.out.println("FaultAndRequestTest: all assertions passed");
    }

    static void faults() throws Exception {
        VsanFault f = VsanFault.parse(T.vendor("queryperf-fault-wildcardlimit-2026-11-06.json"), 500);
        T.eq("InvalidArgument", f.faultClass, "class");
        T.eq("com.vmware.vsan.perfsvc.fault.queryperf.wildcardlimit", f.firstKey(), "key");
        T.eq("A specified parameter was not correct: ", f.faultString, "empty-param faultstring");
        T.check(f.isBatchingFault(), "batching fault");
        T.check(!f.isTypeUnavailable(), "not a learned type (empty param)");
        T.check(f.getMessage().contains("wildcardlimit"), "message names the key");

        VsanFault p = VsanFault.parse(
                T.vendor("retrievepropertiesex-fault-invalidproperty-2026-11-06.json"), 500);
        T.eq("InvalidProperty", p.faultClass, "inventory fault class");
        T.eq(null, p.firstKey(), "no faultMessage");

        // Constructed after the api map's recorded faultstrings.
        T.check(VsanFault.parse("{\"_typeName\":\"InvalidArgument\",\"faultstring\":"
                + "\"A specified parameter was not correct: entityRefId\"}", 500).isTypeUnavailable(),
                "unknown type learned");
        T.check(VsanFault.parse("{\"_typeName\":\"NotSupported\",\"faultstring\":\"x\"}", 500)
                .isTypeUnavailable(), "NotSupported learned");
        T.eq(null, VsanFault.parse("<html>500</html>", 500), "no fault body");
        T.eq(null, VsanFault.parse("{\"faultstring\":\"x\"}", 500), "no _typeName");
    }

    static void perfBody() {
        long start = java.time.Instant.parse("2026-11-07T02:28:26.789Z").toEpochMilli();
        long end = start + CycleEngine.WINDOW_MS;
        for (EntityType t : EntityType.values()) {
            SimpleJson b = SimpleJson.parse(VsanJsonClient.perfBody(t, "domain-c6004", start, end));
            T.eq(1, b.get("querySpecs").size(), t.id + ": exactly one spec");
            SimpleJson spec = b.get("querySpecs").get(0);
            T.eq("VsanPerfQuerySpec", spec.get("_typeName").asString(), "spec _typeName");
            T.eq(t.id + ":*", spec.get("entityRefId").asString(), "one wildcard");
            T.eq("2026-11-07T02:28:26Z", spec.get("startTime").asString(), "UTC Z, seconds");
            T.eq("2026-11-07T02:35:26Z", spec.get("endTime").asString(), "7-minute window");
            T.check(spec.get("interval").isNull(), "no interval");
            List<SimpleJson> labels = spec.get("labels").asList();
            T.eq(t.labels().size(), labels.size(), "labels count");
            for (int i = 0; i < labels.size(); i++) {
                T.eq(t.labels().get(i), labels.get(i).asString(), "label " + i);
            }
            SimpleJson c = b.get("cluster");
            T.eq("ManagedObjectReference", c.get("_typeName").asString(), "cluster MoRef");
            T.eq("ClusterComputeResource", c.get("type").asString(), "cluster type");
            T.eq("domain-c6004", c.get("value").asString(), "cluster moid");
        }
    }

    static void inventoryBody() {
        SimpleJson b = SimpleJson.parse(Inventory.retrieveBody("session[x]y"));
        SimpleJson spec = b.get("specSet").get(0);
        T.eq("PropertyFilterSpec", spec.get("_typeName").asString(), "filter spec");
        T.eq("session[x]y", spec.get("objectSet").get(0).get("obj").get("value").asString(), "view");
        T.eq(4, spec.get("propSet").get(1).get("pathSet").size(), "host paths");
        T.check(!Inventory.retrieveBody("v").contains("configurationEx"),
                "no configurationEx sub-path (faults InvalidProperty)");
        T.eq("session%5Bx%5Dy", VsanJsonClient.pathSegment("session[x]y"), "path encoding");
    }

    static void versions() {
        // Constructed in the published vimServiceVersions.xml shape.
        String xml = "<namespaces version=\"1.0\"><namespace><name>urn:vsan</name><version>9.1.1.0"
                + "</version></namespace><namespace>\n <name>urn:vim25</name>\n <version>9.1.1.0"
                + "</version>\n <priorVersions><version>8.0.3.0</version></priorVersions></namespace>"
                + "</namespaces>";
        T.eq("9.1.1.0", VsanJsonClient.parseVim25Version(xml), "vim25 version");
        T.eq(null, VsanJsonClient.parseVim25Version("<x/>"), "none");
    }
}
