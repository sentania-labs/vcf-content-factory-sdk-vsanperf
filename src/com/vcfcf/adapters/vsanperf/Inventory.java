package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One cycle's vSAN cluster inventory: every ClusterComputeResource with its
 * member hosts, from one PropertyCollector read (api map "vim25 inventory
 * over JSON", shape R2). Plain data plus the parser; no SDK.
 *
 * <p>The cluster's own vSAN paths ({@code configurationEx.vsanConfigInfo.*})
 * fault {@code InvalidProperty} over JSON (sample
 * {@code retrievepropertiesex-fault-invalidproperty-2026-11-06.json}), so a
 * cluster's vSAN state comes from its hosts:
 * {@code config.vsanHostConfig.enabled}, {@code .clusterInfo.uuid} (equal to
 * the cluster uuid on every host, observed 5 of 5) and
 * {@code .clusterInfo.nodeUuid} (the perf entity id suffix).
 */
final class Inventory {

    /** One HostSystem. Unread fields are {@code null}, never a default. */
    static final class Host {
        final String moid;
        final String name;
        /** {@code null} when unreadable or empty (a non-vSAN host answers ""). */
        final String nodeUuid;
        final String vsanClusterUuid;
        /** {@code null} when unreadable. */
        final Boolean vsanEnabled;

        Host(String moid, String name, String nodeUuid, String vsanClusterUuid,
                Boolean vsanEnabled) {
            this.moid = moid;
            this.name = name;
            this.nodeUuid = blankToNull(nodeUuid);
            this.vsanClusterUuid = blankToNull(vsanClusterUuid);
            this.vsanEnabled = vsanEnabled;
        }

    }

    /** One ClusterComputeResource and its hosts in the cluster's order. */
    static final class Cluster {
        final String moid;
        final String name;
        final List<Host> hosts;

        Cluster(String moid, String name, List<Host> hosts) {
            this.moid = moid;
            this.name = name;
            this.hosts = Collections.unmodifiableList(hosts);
        }

        /**
         * vSAN-enabled (design Addendum): at least one member host reads
         * {@code enabled=true} with a non-empty {@code clusterInfo.uuid}.
         */
        boolean isVsan() {
            return vsanUuid() != null;
        }

        /**
         * The cluster's vSAN uuid: the {@code clusterInfo.uuid} of its
         * enabled members, the majority value when they disagree (ties go to
         * the value seen first in host order); {@code null} when no member
         * qualifies.
         */
        String vsanUuid() {
            Map<String, Integer> votes = votes();
            String best = null;
            int bestN = 0;
            for (Map.Entry<String, Integer> e : votes.entrySet()) {
                if (e.getValue() > bestN) {
                    best = e.getKey();
                    bestN = e.getValue();
                }
            }
            return best;
        }

        /** Members disagree on the vSAN uuid (WARN once). */
        boolean uuidDisagrees() {
            return votes().size() > 1;
        }

        private Map<String, Integer> votes() {
            Map<String, Integer> votes = new LinkedHashMap<>();
            for (Host h : hosts) {
                if (Boolean.TRUE.equals(h.vsanEnabled) && h.vsanClusterUuid != null) {
                    votes.merge(h.vsanClusterUuid, 1, Integer::sum);
                }
            }
            return votes;
        }

        /** Members with a node uuid: the ones the node map holds. */
        int vsanNodes() {
            int n = 0;
            for (Host h : hosts) if (h.nodeUuid != null) n++;
            return n;
        }
    }

    final List<Cluster> clusters;

    Inventory(List<Cluster> clusters) {
        this.clusters = Collections.unmodifiableList(clusters);
    }

    static final String P_NAME = "name";
    static final String P_HOST = "host";
    static final String P_ENABLED = "config.vsanHostConfig.enabled";
    static final String P_CLUSTER_UUID = "config.vsanHostConfig.clusterInfo.uuid";
    static final String P_NODE_UUID = "config.vsanHostConfig.clusterInfo.nodeUuid";

    /**
     * The R2 {@code RetrievePropertiesEx} body over a ContainerView of
     * ClusterComputeResource (api map, verbatim apart from the view moId).
     */
    static String retrieveBody(String viewMoid) {
        return "{\"specSet\":[{\"_typeName\":\"PropertyFilterSpec\","
                + "\"propSet\":[{\"_typeName\":\"PropertySpec\",\"type\":\"ClusterComputeResource\","
                + "\"pathSet\":[\"" + P_NAME + "\",\"" + P_HOST + "\"]},"
                + "{\"_typeName\":\"PropertySpec\",\"type\":\"HostSystem\",\"pathSet\":[\""
                + P_NAME + "\",\"" + P_ENABLED + "\",\"" + P_CLUSTER_UUID + "\",\""
                + P_NODE_UUID + "\"]}],"
                + "\"objectSet\":[{\"_typeName\":\"ObjectSpec\",\"obj\":{\"_typeName\":"
                + "\"ManagedObjectReference\",\"value\":" + Json.quote(viewMoid)
                + ",\"type\":\"ContainerView\"},\"skip\":true,"
                + "\"selectSet\":[{\"_typeName\":\"TraversalSpec\",\"name\":\"v\","
                + "\"type\":\"ContainerView\",\"path\":\"view\",\"skip\":false,"
                + "\"selectSet\":[{\"_typeName\":\"TraversalSpec\",\"name\":\"ch\","
                + "\"type\":\"ComputeResource\",\"path\":\"host\",\"skip\":false}]}]}]}],"
                + "\"options\":{\"_typeName\":\"RetrieveOptions\"}}";
    }

    /** Accumulates the pages of one retrieve (first page plus continuations). */
    static final class Builder {
        private final Map<String, Map<String, SimpleJson>> clusters = new LinkedHashMap<>();
        private final Map<String, Map<String, SimpleJson>> hosts = new LinkedHashMap<>();

        /**
         * Add one {@code RetrieveResult} page; returns its {@code token}
         * (more pages) or {@code null}.
         *
         * @throws IOException when the page is not a RetrieveResult
         */
        String addPage(String body) throws IOException {
            SimpleJson root;
            try {
                root = SimpleJson.parse(body);
            } catch (RuntimeException e) {
                throw new Unreadable("inventory: the RetrievePropertiesEx response is not valid JSON");
            }
            if (!root.isObject()) {
                // An empty result can come back as no body at all.
                if (root.isNull()) return null;
                throw new Unreadable("inventory: the RetrievePropertiesEx response "
                        + "is not a RetrieveResult");
            }
            for (SimpleJson oc : root.get("objects").asList()) {
                String type = oc.get("obj").get("type").asString();
                String moid = oc.get("obj").get("value").asString();
                if (moid == null || type == null) continue;
                Map<String, SimpleJson> props = new LinkedHashMap<>();
                for (SimpleJson dp : oc.get("propSet").asList()) {
                    String name = dp.get("name").asString();
                    if (name != null) props.put(name, unbox(dp.get("val")));
                }
                if ("ClusterComputeResource".equals(type)) {
                    clusters.put(moid, props);
                } else if ("HostSystem".equals(type)) {
                    hosts.put(moid, props);
                }
            }
            String token = root.get("token").asString();
            return token == null || token.isEmpty() ? null : token;
        }

        Inventory build() {
            List<Cluster> out = new ArrayList<>();
            for (Map.Entry<String, Map<String, SimpleJson>> c : clusters.entrySet()) {
                Map<String, SimpleJson> cp = c.getValue();
                List<Host> members = new ArrayList<>();
                SimpleJson hostRefs = cp.get(P_HOST);
                if (hostRefs != null) {
                    for (SimpleJson ref : hostRefs.asList()) {
                        String hm = ref.get("value").asString();
                        if (hm == null) continue;
                        Map<String, SimpleJson> hp = hosts.get(hm);
                        members.add(hp == null ? new Host(hm, null, null, null, null)
                                : new Host(hm, str(hp, P_NAME), str(hp, P_NODE_UUID),
                                        str(hp, P_CLUSTER_UUID), bool(hp, P_ENABLED)));
                    }
                }
                out.add(new Cluster(c.getKey(), str(cp, P_NAME), members));
            }
            return new Inventory(out);
        }
    }

    /** {@code {"_typeName":"string","_value":x}} to {@code x}; MoRefs and data objects as is. */
    static SimpleJson unbox(SimpleJson val) {
        if (val.isObject()) {
            SimpleJson inner = val.get("_value");
            if (!inner.isNull()) return inner;
        }
        return val;
    }

    private static String str(Map<String, SimpleJson> props, String key) {
        SimpleJson v = props.get(key);
        if (v == null || v.isNull() || v.isObject() || v.isList()) return null;
        return v.asString();
    }

    private static Boolean bool(Map<String, SimpleJson> props, String key) {
        String s = str(props, key);
        if ("true".equals(s)) return Boolean.TRUE;
        if ("false".equals(s)) return Boolean.FALSE;
        return null;
    }

    private static String blankToNull(String s) {
        return s == null || s.trim().isEmpty() ? null : s;
    }
}
