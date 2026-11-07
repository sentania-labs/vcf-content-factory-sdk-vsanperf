package com.vcfcf.adapters.vsanperf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The cluster include and exclude settings: comma lists whose items are a
 * cluster name (exact, case-insensitive) or a MoRef such as
 * {@code domain-c6004} (exact). An empty include means every vSAN cluster;
 * the exclude applies after it. Used to shard one vCenter's clusters
 * across adapter instances.
 */
final class ClusterFilter {

    private final List<String> include;
    private final List<String> exclude;

    ClusterFilter(String include, String exclude) {
        this.include = items(include);
        this.exclude = items(exclude);
    }

    static List<String> items(String raw) {
        List<String> out = new ArrayList<>();
        if (raw == null) return out;
        for (String p : raw.split(",")) {
            String t = p.trim();
            if (!t.isEmpty() && !out.contains(t)) out.add(t);
        }
        return Collections.unmodifiableList(out);
    }

    boolean accepts(String moid, String name) {
        if (!include.isEmpty() && !matchesAny(include, moid, name)) return false;
        return !matchesAny(exclude, moid, name);
    }

    private static boolean matchesAny(List<String> items, String moid, String name) {
        for (String i : items) {
            if (i.equals(moid)) return true;
            if (name != null && i.toLowerCase(Locale.ROOT).equals(name.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    List<String> include() {
        return include;
    }

    List<String> exclude() {
        return exclude;
    }

    @Override
    public String toString() {
        return "include=" + (include.isEmpty() ? "(all)" : String.join(",", include))
                + " exclude=" + (exclude.isEmpty() ? "(none)" : String.join(",", exclude));
    }
}
