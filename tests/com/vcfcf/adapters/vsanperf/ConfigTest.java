package com.vcfcf.adapters.vsanperf;

/** T1: settings parsing, the cluster filter, credential masking. */
public final class ConfigTest {

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
