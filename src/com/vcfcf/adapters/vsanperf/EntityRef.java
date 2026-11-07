package com.vcfcf.adapters.vsanperf;

/**
 * One parsed {@code entityRefId} of a Build 1 type: the type, the vSAN
 * node uuid the entity lands on, and for pNIC the NIC name. Forms
 * (api map "Build 1 perf samples over JSON", observed):
 * {@code host-domclient:<node>}, {@code host-domowner:<node>},
 * {@code vsan-pnic-net:<node>|vmnicN}. Split on the first {@code :} for
 * the type, then on {@code |}.
 */
final class EntityRef {

    final EntityType type;
    final String node;
    /** The NIC name for pNIC, {@code null} for the host-scoped types. */
    final String instance;

    private EntityRef(EntityType type, String node, String instance) {
        this.type = type;
        this.node = node;
        this.instance = instance;
    }

    /**
     * Parse {@code ref} as an entity of {@code expected}, or {@code null}
     * when it is not a usable one: another type, a wildcard echo
     * ({@code <type>:*}) or any {@code *}, an empty node, a host-scoped id
     * with a {@code |} part, or a pNIC id that does not split into exactly
     * two non-empty parts. The caller counts a {@code null}; nothing is
     * ever pushed under a guessed instance.
     */
    static EntityRef parse(String ref, EntityType expected) {
        if (ref == null || ref.indexOf('*') >= 0) return null;
        int colon = ref.indexOf(':');
        if (colon <= 0) return null;
        if (!expected.id.equals(ref.substring(0, colon))) return null;
        String id = ref.substring(colon + 1);
        if (!expected.instanced) {
            if (id.isEmpty() || id.indexOf('|') >= 0) return null;
            return new EntityRef(expected, id, null);
        }
        String[] parts = id.split("\\|", -1);
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
            return null;
        }
        return new EntityRef(expected, parts[0], parts[1]);
    }
}
