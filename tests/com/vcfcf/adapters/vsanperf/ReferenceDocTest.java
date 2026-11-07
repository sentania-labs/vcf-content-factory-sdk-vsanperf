package com.vcfcf.adapters.vsanperf;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Build 20 (build 19 review B1): every key table row of REFERENCE.md agrees
 * with {@link EntityType}, so the key contract an operator reads cannot drift
 * from what the pack pushes: per row the stat key (group, instance template,
 * leaf), the vSAN label, the per-mille transform and the scope; per type
 * exactly its catalog under {@code all}, no row twice; all 69 types and
 * 1,805 keys documented.
 */
public final class ReferenceDocTest {

    public static void main(String[] args) throws Exception {
        String doc = new String(Files.readAllBytes(Paths.get("REFERENCE.md")),
                StandardCharsets.UTF_8);
        Pattern heading = Pattern.compile("^### .*\\(`([A-Za-z0-9-]+)`\\)");
        EntityType cur = null;
        Map<EntityType, Set<String>> seen = new HashMap<>();
        int rows = 0;
        for (String line : doc.split("\n")) {
            Matcher h = heading.matcher(line);
            if (h.find()) {
                cur = EntityType.byId(h.group(1));
                continue;
            }
            if (!line.startsWith("|`vSAN Perf")) continue;
            T.check(cur != null, "key row outside a type section: " + line);
            EntityType t = cur;
            String[] c = line.replace("\\|", "\u0000").split("\\|", -1);
            // c[0] is empty (leading |); key, label, meaning, unit, transform, scope.
            T.eq(8, c.length, t.id + ": six columns: " + line);
            String key = strip(c[1]).replace('\u0000', '|');
            String label = strip(c[2]);
            String transform = c[5].trim();
            String scope = c[6].trim();
            EntityType.Metric m = t.metric(label, EntityType.Scope.ALL);
            T.check(m != null, t.id + ": REFERENCE.md documents label " + label
                    + " the catalog lacks");
            int last = key.lastIndexOf('|');
            T.check(key.startsWith(EntityType.ROOT + "|") && last > EntityType.ROOT.length(),
                    t.id + " key shape: " + key);
            String group = key.substring(EntityType.ROOT.length() + 1, last);
            String leaf = key.substring(last + 1);
            T.eq(m.leaf, leaf, t.id + " " + label + ": leaf");
            if (t.instanced) {
                T.check(group.startsWith(t.group + ":<") && group.endsWith(">"), t.id + " "
                        + label + ": instanced group " + group);
                String instance = group.substring(t.group.length() + 1);
                T.eq(key, t.statKey(instance, m), t.id + " " + label + ": stat key");
            } else {
                T.eq(t.group, group, t.id + " " + label + ": group");
                T.eq(key, t.statKey(null, m), t.id + " " + label + ": stat key");
            }
            T.eq(m.dashboard ? "dashboard" : "all", scope, t.id + " " + label + ": scope");
            T.eq(m.perMille, transform.startsWith("per-mille / 10"), t.id + " " + label
                    + ": transform");
            T.check(seen.computeIfAbsent(t, k -> new HashSet<>()).add(label), t.id + " " + label
                    + " documented twice");
            rows++;
        }
        for (EntityType t : EntityType.values()) {
            Set<String> s = seen.getOrDefault(t, Set.of());
            T.eq(t.size(EntityType.Scope.ALL), s.size(), t.id + ": every catalog key documented");
        }
        T.eq(1805, rows, "REFERENCE.md documents 1,805 keys");
        System.out.println("ReferenceDocTest: all assertions passed");
    }

    private static String strip(String cell) {
        String s = cell.trim();
        if (s.startsWith("`") && s.endsWith("`") && s.length() >= 2) s = s.substring(1, s.length() - 1);
        return s;
    }
}
