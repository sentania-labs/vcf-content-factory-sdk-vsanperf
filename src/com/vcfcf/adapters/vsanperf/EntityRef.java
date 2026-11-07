package com.vcfcf.adapters.vsanperf;

/**
 * One parsed {@code entityRefId}: the type, the uuid the entity lands on (a
 * vSAN node uuid for host types, the vSAN cluster uuid for cluster types),
 * and the instance for an instanced type. Forms (api map "entityRefId
 * grammar per type", observed 2026-11-06):
 * <ul>
 * <li>{@code <type>:<uuid>}: the host and cluster types without instance;
 * <li>{@code vsan-pnic-net:<node>|vmnicN}, {@code vsan-tcpip-stats:<node>|<stack>},
 *     {@code vnic-rdt-network-latency:<node>|<vmk>}: instance = the second part;
 * <li>{@code vsan-vnic-net:<node>|<stack>|<vmk>}: instance = the vmk; the
 *     stack is dropped from the key (v2 HostSystem table).
 * <li>disk types {@code <type>:<disk uuid>}: the uuid is also the instance;
 * <li>{@code virtual-machine:<instanceUuid>}, {@code vscsi:<instanceUuid>|scsiN:M};
 * <li>{@code virtual-disk:<datastore path>}: the rest of the id whole, its
 *     leading {@code /} stripped (3 of 148 lab refs carry one); the
 *     instance (bus position) is resolved from the VM device read.
 * </ul>
 * Split on the first {@code :} for the type, then on {@code |}.
 */
final class EntityRef {

    final EntityType type;
    /**
     * The first id part: a node uuid, the cluster uuid, a disk uuid or a VM
     * instanceUuid; for {@code virtual-disk} the normalized datastore path.
     */
    final String uuid;
    /** The instance for an instanced type, {@code null} otherwise (and for {@code virtual-disk}). */
    final String instance;
    /** The {@code entityRefId} as served (the dedup mark key). */
    final String raw;

    private EntityRef(EntityType type, String uuid, String instance, String raw) {
        this.type = type;
        this.uuid = uuid;
        this.instance = instance;
        this.raw = raw;
    }

    /**
     * Parse {@code ref} as an entity of {@code expected}, or {@code null}
     * when it is not a usable one: another type, a wildcard echo
     * ({@code <type>:*}) or any {@code *}, an empty first part, a part count
     * other than the type's form, or an empty part. The caller counts a
     * {@code null}; nothing is ever pushed under a guessed instance.
     */
    static EntityRef parse(String ref, EntityType expected) {
        if (ref == null || ref.indexOf('*') >= 0) return null;
        int colon = ref.indexOf(':');
        if (colon <= 0) return null;
        if (!expected.id.equals(ref.substring(0, colon))) return null;
        String id = ref.substring(colon + 1);
        if (expected.form == EntityType.IdForm.PATH) {
            String path = id.startsWith("/") ? id.substring(1) : id;
            if (path.isEmpty() || path.indexOf('|') >= 0) return null;
            return new EntityRef(expected, path, null, ref);
        }
        String[] parts = id.split("\\|", -1);
        if (parts.length != expected.form.parts) return null;
        for (String p : parts) {
            if (p.isEmpty()) return null;
        }
        int ip = expected.form.instancePart;
        return new EntityRef(expected, parts[0], ip < 0 ? null : parts[ip], ref);
    }

    /** The number of {@code |} parts of an id that did not parse (for a WARN, never the id). */
    static int parts(String ref) {
        if (ref == null) return 0;
        int colon = ref.indexOf(':');
        String id = colon < 0 ? ref : ref.substring(colon + 1);
        return id.split("\\|", -1).length;
    }
}
