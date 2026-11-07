package com.vcfcf.adapters.vsanperf;

/**
 * T1: the inventory parser against the verbatim RetrievePropertiesEx
 * capture, and the design Addendum's cluster vSAN rule on constructed
 * results (majority uuid, empty node uuid, no qualifying host).
 */
public final class InventoryTest {

    public static void main(String[] args) throws Exception {
        verbatim();
        rules();
        paging();
        System.out.println("InventoryTest: all assertions passed");
    }

    static void verbatim() throws Exception {
        Inventory.Builder b = new Inventory.Builder();
        T.eq(null, b.addPage(T.vendor("retrievepropertiesex-cluster-hosts-2026-11-06.json")),
                "no token");
        Inventory inv = b.build();
        T.eq(1, inv.clusters.size(), "one cluster");
        Inventory.Cluster c = inv.clusters.get(0);
        T.eq("domain-c6004", c.moid, "moid");
        T.eq("vcf-lab-mgmt-cl01", c.name, "name (boxed string)");
        T.eq(5, c.hosts.size(), "hosts from the host array");
        T.eq("host-6011", c.hosts.get(0).moid, "cluster host order");
        T.check(c.isVsan(), "vSAN enabled from members");
        T.check(!c.uuidDisagrees(), "members agree");
        T.eq("90d067b8-0077-11f1-af60-000c297e270a", c.vsanUuid(), "cluster uuid from members");
        T.eq(5, c.vsanNodes(), "node uuids");
        for (Inventory.Host h : c.hosts) {
            T.eq(Boolean.TRUE, h.vsanEnabled, h.moid + " enabled (boxed boolean)");
            T.check(h.nodeUuid != null && h.name != null, h.moid + " read");
        }
        Inventory.Host esx01 = null;
        for (Inventory.Host h : c.hosts) if (h.moid.equals("host-6017")) esx01 = h;
        T.eq("69805090-5bba-3949-88f7-38052534dfcc", esx01.nodeUuid, "esx01 node uuid");
        T.eq("vcf-lab-mgmt-esx01.int.sentania.net", esx01.name, "esx01 name");
    }

    static String host(String moid, Boolean enabled, String uuid, String node) {
        StringBuilder b = new StringBuilder("{\"_typeName\":\"ObjectContent\",\"obj\":{\"_typeName\":"
                + "\"ManagedObjectReference\",\"value\":\"" + moid + "\",\"type\":\"HostSystem\"},"
                + "\"propSet\":[");
        b.append(prop("name", "\"" + moid + ".lab\"", "string"));
        if (enabled != null) b.append(',').append(prop(Inventory.P_ENABLED, enabled.toString(), "boolean"));
        if (uuid != null) b.append(',').append(prop(Inventory.P_CLUSTER_UUID, "\"" + uuid + "\"", "string"));
        if (node != null) b.append(',').append(prop(Inventory.P_NODE_UUID, "\"" + node + "\"", "string"));
        return b.append("]}").toString();
    }

    static String prop(String name, String value, String type) {
        return "{\"_typeName\":\"DynamicProperty\",\"name\":\"" + name + "\",\"val\":{\"_typeName\":\""
                + type + "\",\"_value\":" + value + "}}";
    }

    static String cluster(String moid, String... hosts) {
        StringBuilder refs = new StringBuilder();
        for (String h : hosts) {
            if (refs.length() > 0) refs.append(',');
            refs.append("{\"_typeName\":\"ManagedObjectReference\",\"value\":\"").append(h)
                    .append("\",\"type\":\"HostSystem\"}");
        }
        return "{\"_typeName\":\"ObjectContent\",\"obj\":{\"_typeName\":\"ManagedObjectReference\","
                + "\"value\":\"" + moid + "\",\"type\":\"ClusterComputeResource\"},\"propSet\":["
                + "{\"_typeName\":\"DynamicProperty\",\"name\":\"host\",\"val\":{\"_typeName\":"
                + "\"ArrayOfManagedObjectReference\",\"_value\":[" + refs + "]}},"
                + prop("name", "\"" + moid + "-name\"", "string") + "]}";
    }

    static String result(String token, String... objects) {
        return "{\"_typeName\":\"RetrieveResult\"," + (token == null ? "" : "\"token\":\"" + token + "\",")
                + "\"objects\":[" + String.join(",", objects) + "]}";
    }

    /** Constructed in the captured shapes. */
    static void rules() throws Exception {
        Inventory.Builder b = new Inventory.Builder();
        b.addPage(result(null,
                cluster("c-major", "h1", "h2", "h3"),
                host("h1", true, "U1", "n1"), host("h2", true, "U1", "n2"), host("h3", true, "U2", "n3"),
                cluster("c-none", "h4", "h5"),
                host("h4", false, "", ""), host("h5", true, "", "n5"),
                cluster("c-empty"),
                cluster("c-mixed", "h6", "h7", "h8"),
                host("h6", true, "U3", "n6"), host("h7", false, "", ""), host("h8", null, null, null)));
        Inventory inv = b.build();
        Inventory.Cluster major = inv.clusters.get(0);
        T.check(major.isVsan(), "majority cluster is vSAN");
        T.check(major.uuidDisagrees(), "members disagree");
        T.eq("U1", major.vsanUuid(), "majority uuid");
        Inventory.Cluster none = inv.clusters.get(1);
        T.check(!none.isVsan(), "no host with enabled and a uuid: skipped");
        T.eq(null, none.hosts.get(0).nodeUuid, "empty nodeUuid is no node");
        T.check(!inv.clusters.get(2).isVsan(), "empty cluster skipped");
        Inventory.Cluster mixed = inv.clusters.get(3);
        T.check(mixed.isVsan(), "one qualifying host is enough");
        T.eq(1, mixed.vsanNodes(), "non-vSAN and unreadable hosts are not nodes");
        T.eq(null, mixed.hosts.get(2).vsanEnabled, "unread stays null");
    }

    static void paging() throws Exception {
        Inventory.Builder b = new Inventory.Builder();
        T.eq("tok-1", b.addPage(result("tok-1", cluster("c1", "h1", "h2"), host("h1", true, "U", "n1"))),
                "token returned");
        T.eq(null, b.addPage(result(null, host("h2", true, "U", "n2"))), "last page");
        Inventory inv = b.build();
        T.eq(2, inv.clusters.get(0).vsanNodes(), "a host on page 2 joins its cluster");
        T.expect(java.io.IOException.class, () -> new Inventory.Builder().addPage("[1]"),
                "not a RetrieveResult");
    }
}
