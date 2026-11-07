package com.vcfcf.adapters.vsanperf;

/** T1: settings parsing, the tier settings, the cluster filter, credential masking. */
public final class ConfigTest {

    static VsanPerfConfig tiers(String host, String cluster, String exclude) {
        return new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null, host, cluster,
                exclude);
    }

    /** Build 10 (design v2 "Settings"). */
    static void tiers() {
        VsanPerfConfig d = cfg("vc", null, null, null, null);
        T.check(d.tierHost && d.tierCluster, "an account saved before build 10: both tiers on");
        T.eq(26, d.types.size(), "core + host + cluster types");
        T.eq(EntityType.HOST_DOMOWNER, d.types.get(0), "collection order starts with core");
        T.eq(EntityType.VSAN_CLUSTER_CAPACITY, d.types.get(3), "then the cluster tier");
        T.eq(EntityType.CMMDS_NET, d.types.get(13), "then the host tier");
        T.eq(16, tiers("true", "false", "").types.size(), "cluster tier off");
        T.eq(13, tiers("FALSE", " true ", "").types.size(), "host tier off, any case");
        T.eq(3, tiers("false", "false", null).types.size(), "both off: Build 1");
        VsanPerfConfig ex = tiers(null, null, " ioinsight,VSAN-PNIC-NET , cache-dsk,bogus id");
        T.eq(24, ex.types.size(), "two known types excluded, core included");
        T.check(!ex.types.contains(EntityType.IOINSIGHT), "ioinsight out");
        T.eq(java.util.List.of("cache-dsk", "bogus id"), ex.unknownExcludes,
                "not in this build: a WARN, not an error");
        String warn = ex.unknownExcludesWarning();
        T.check(warn.contains("cache-dsk") && !warn.contains("bogus id"),
                "the WARN echoes only id-shaped entries: " + warn);
        T.expect(IllegalArgumentException.class, () -> tiers("false", "false",
                "host-domowner,host-domclient,vsan-pnic-net"), "nothing left in scope");
        T.expect(IllegalArgumentException.class, () -> tiers("yes", null, null), "strict flag");
        T.check(d.toString().contains("tierHost=true tierCluster=true tierDisk=false tierVm=false "
                + "vmEveryN=3 scope=dashboard types=26"), "toString");
        // Build 11: the disk and VM tiers, default off; the VM cadence.
        T.check(!d.tierDisk && !d.tierVm, "disk and VM tiers off by default");
        T.eq(3, d.vmEveryN, "VM every 3rd cycle by default");
        VsanPerfConfig all = new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null,
                null, null, "true", "true", "1", "");
        T.eq(33, all.types.size(), "all tiers: 33 types");
        T.eq(1, all.vmEveryN, "N = 1");
        T.eq(EntityType.CACHE_DISK, all.types.get(26), "disk tier after the host tier");
        T.eq(EntityType.VSCSI, all.types.get(32), "VM tier last");
        T.expect(IllegalArgumentException.class, () -> new VsanPerfConfig("vc", "u", "p", "", "",
                null, null, null, null, null, null, null, "true", "13", ""), "N over 12");
        T.expect(IllegalArgumentException.class, () -> new VsanPerfConfig("vc", "u", "p", "", "",
                null, null, null, null, null, null, null, "true", "0", ""), "N 0");
        T.eq(31, new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null, null, null,
                "true", "true", null, "virtual-disk,cache-disk").types.size(),
                "disk and VM types can be excluded");
    }

    static VsanPerfConfig scoped(String tierDisk, String tierVm, String exclude, String scope) {
        return new VsanPerfConfig("vc", "u", "p", "", "", null, null, null, null, null, null,
                tierDisk, tierVm, null, exclude, scope);
    }

    /** Build 19 (design v3 "Setting: metric_scope", composition steps 1 to 4). */
    static void metricScope() {
        T.eq(EntityType.Scope.DASHBOARD, cfg("vc", null, null, null, null).scope,
                "an account saved before 1.2.0: dashboard");
        T.eq(EntityType.Scope.DASHBOARD, scoped(null, null, "", " ").scope, "blank: dashboard");
        T.eq(EntityType.Scope.ALL, scoped(null, null, "", " ALL ").scope, "any case, trimmed");
        IllegalArgumentException e = T.expect(IllegalArgumentException.class,
                () -> scoped(null, null, "", "everything"), "strict enum");
        T.check(!e.getMessage().contains("everything"), "never echoes the value");
        // Types per tier under all (v3): core 3, cluster 19, host 34, disk 7, VM 6.
        T.eq(26, scoped(null, null, "", "dashboard").types.size(), "dashboard default tiers");
        VsanPerfConfig all = scoped(null, null, "", "all");
        T.eq(3 + 19 + 34, all.types.size(), "all, default tiers: core 3 + cluster 19 + host 34");
        T.eq(69, scoped("true", "true", "", "all").types.size(), "all four tiers: 69 types");
        T.eq(33, scoped("true", "true", "", "dashboard").types.size(), "dashboard: 33 types");
        VsanPerfConfig coreOnly = new VsanPerfConfig("vc", "u", "p", "", "", null, null, null,
                null, "false", "false", "false", "false", null, "", "all");
        T.eq(3, coreOnly.types.size(), "all, every tier off: the three core types only");
        // C8: within each tier dashboard types first, host-cpu and dom-world-cpu last.
        int hostCpu = all.types.indexOf(EntityType.HOST_CPU);
        int world = all.types.indexOf(EntityType.DOM_WORLD_CPU);
        T.eq(all.types.size() - 2, hostCpu, "host-cpu second to last of the default tiers");
        T.eq(all.types.size() - 1, world, "dom-world-cpu last");
        boolean seenNew = false;
        EntityType.Tier tier = null;
        for (EntityType t : scoped("true", "true", "", "all").types) {
            if (t.tier != tier) {
                tier = t.tier;
                seenNew = false;
            }
            if (t.allOnly) seenNew = true;
            else T.check(!seenNew, t.id + ": a dashboard type after a new one in its tier");
        }
        // exclude_types: any of the 69 under either scope; a new-type id under
        // dashboard is silent (known, not in scope).
        VsanPerfConfig ex = scoped(null, null, "dom-world-cpu,host-cpu", "all");
        T.eq(all.types.size() - 2, ex.types.size(), "the v3 example exclusion");
        T.check(ex.unknownExcludes.isEmpty(), "known ids");
        VsanPerfConfig dashEx = scoped(null, null, "dom-world-cpu,host-cpu", "dashboard");
        T.eq(26, dashEx.types.size(), "nothing to remove under dashboard");
        T.check(dashEx.unknownExcludes.isEmpty(), "silent under dashboard");
        T.eq(java.util.List.of("no-such-type"), scoped(null, null, "no-such-type", "all")
                .unknownExcludes, "an id outside the 69 stays a WARN");
        T.check(all.toString().contains("scope=all types=56"), "toString: " + all);
        // Build 21 (Codex on PR #11): the two mixed-case ids can be excluded,
        // in their own case or any other; neither stays in scope nor is
        // reported unknown; the exact id is what the request carries.
        for (String ex2 : new String[] {"computeCluster-remotedomclient,computeHost-remotedomclient",
                "COMPUTECLUSTER-REMOTEDOMCLIENT, computehost-remotedomclient"}) {
            VsanPerfConfig mixed = scoped(null, null, ex2, "all");
            T.check(!mixed.types.contains(EntityType.COMPUTECLUSTER_REMOTEDOMCLIENT)
                    && !mixed.types.contains(EntityType.COMPUTEHOST_REMOTEDOMCLIENT),
                    "both mixed-case types excluded: " + ex2);
            T.eq(all.types.size() - 2, mixed.types.size(), "exactly two removed: " + ex2);
            T.check(mixed.unknownExcludes.isEmpty(), "not reported unknown: " + mixed.unknownExcludes);
        }
        T.check(VsanJsonClient.perfBody(EntityType.COMPUTEHOST_REMOTEDOMCLIENT, EntityType.Scope.ALL,
                "domain-c1", 0, CycleEngine.WINDOW_MS).contains("\"computeHost-remotedomclient:*\""),
                "the request keeps the exact id");
        for (EntityType t : EntityType.values()) {
            T.eq(t, EntityType.byIdIgnoreCase(t.id.toUpperCase(java.util.Locale.ROOT)),
                    t.id + ": unambiguous ignoring case");
        }
    }

    static VsanPerfConfig cfg(String host, String workers, String timeout, String deadline,
            String perf) {
        return new VsanPerfConfig(host, "svc@vsphere.local", "s3cret-pw", "", "", workers, timeout,
                deadline, perf);
    }

    public static void main(String[] args) {
        VsanPerfConfig d = cfg("vc.lab.example", null, null, null, null);
        T.eq(4, d.workers, "default workers");
        T.eq(30, d.callTimeoutSeconds, "default call timeout");
        T.eq(240L, d.deadlineSeconds(300), "deadline = interval - 60");
        T.eq(240L, d.deadlineSeconds(null), "unknown interval = 5 min");
        T.eq(30L, d.deadlineSeconds(60), "deadline floor");
        T.eq(100L, cfg("vc", null, null, "100", null).deadlineSeconds(300), "explicit deadline");
        T.check(!d.perfTimingLogging, "perf timing off by default");
        T.check(cfg("vc", null, null, null, "true").perfTimingLogging, "on");
        T.check(!cfg("vc", null, null, null, "yes").perfTimingLogging, "strict parse");
        T.eq("https://vc.lab.example", d.baseUrl(), "base url");
        T.eq("https://vc.lab.example:8443", cfg("vc.lab.example:8443", null, null, null, null)
                .baseUrl(), "host:port");
        T.eq("https://[fd00::10]:443", cfg("[fd00::10]:443", null, null, null, null).baseUrl(), "v6");
        T.eq("https://vc.lab.example:443/", VsanPerfConfig.rootUrl("vc.lab.example"), "check url");
        T.eq("https://vc:9443/", VsanPerfConfig.rootUrl("vc:9443"), "check url port");
        T.eq(null, VsanPerfConfig.rootUrl("https://x/"), "invalid check url");
        T.check(!d.toString().contains("s3cret"), "toString has no password");
        T.eq("*", d.clusterScope(), "collector scope: every cluster");
        // Build 8: the collector identity is what the account stores, trimmed only.
        T.eq("Prod , domain-c9,Prod", new VsanPerfConfig("vc", "u", "p", " Prod , domain-c9,Prod ",
                "", null, null, null, null).clusterScope(), "collector scope: trimmed only");
        T.check(!new VsanPerfConfig("vc", "u", "p", "cl01,cl02", "", null, null, null, null)
                .clusterScope().equals(new VsanPerfConfig("vc", "u", "p", "cl01, cl02", "", null,
                        null, null, null).clusterScope()), "accounts differing in spacing: two objects");
        T.eq("VC.lab", cfg(" VC.lab ", null, null, null, null).vcenterHost,
                "vcenter identity keeps case, trimmed");
        T.check(!cfg("vc.lab", null, null, null, null).vcenterHost
                .equals(cfg("VC.lab", null, null, null, null).vcenterHost),
                "accounts differing in case: two objects");
        T.check(new VsanPerfConfig("vc", "u", "p", "cl01, cl02", "", null, null, null, null).filter()
                .accepts("domain-c1", "CL02"), "the filter still normalises");
        T.check(!d.toString().contains("svc@"), "toString has no principal");

        for (String bad : new String[] {"", "https://vc", "u:pw@vc", "vc/path", "vc:0", "vc:x",
                "fd00::10", "vc lab", "999", "10.0.0.300"}) {
            IllegalArgumentException e = T.expect(IllegalArgumentException.class,
                    () -> cfg(bad, null, null, null, null), "rejects <" + bad + ">");
            T.check(bad.length() < 5 || !e.getMessage().contains(bad), "message never quotes <" + bad + ">: " + e.getMessage());
        }
        T.expect(IllegalArgumentException.class, () -> cfg("vc", "0", null, null, null), "workers 0");
        T.expect(IllegalArgumentException.class, () -> cfg("vc", "17", null, null, null), "workers 17");
        T.expect(IllegalArgumentException.class, () -> cfg("vc", "four", null, null, null), "nan");
        T.expect(IllegalArgumentException.class, () -> cfg("vc", null, "0", null, null), "timeout");
        T.expect(IllegalArgumentException.class, () -> new VsanPerfConfig("vc", "u", "", null, null,
                null, null, null, null), "empty password");

        tiers();
        metricScope();

        ClusterFilter all = new ClusterFilter("", "");
        T.check(all.accepts("domain-c1", "Prod"), "empty include = all");
        ClusterFilter inc = new ClusterFilter(" prod , domain-c9 ", "domain-c9");
        T.check(inc.accepts("domain-c1", "PROD"), "name, case-insensitive");
        T.check(!inc.accepts("domain-c9", "Other"), "exclude after include");
        T.check(!inc.accepts("domain-c2", "Test"), "not included");
        T.check(!inc.accepts("DOMAIN-C9", "x"), "MoRef exact");
        T.check(!new ClusterFilter("", "Lab").accepts("d", "lab"), "exclude by name");
        System.out.println("ConfigTest: all assertions passed");
    }
}
