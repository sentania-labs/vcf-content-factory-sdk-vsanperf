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
 * <li>Build 3 ({@code metric_scope=all}, P3 observed 2026-11-07 where marked):
 *     {@code host-cpu:<node>|cpu-<n>} (instance {@code cpu-<n>}) and the
 *     1-part host aggregate {@code host-cpu:<node>} (no instance, observed);
 *     {@code dom-world-cpu:<node>|<world>|<id>} (observed) and
 *     {@code lsom-world-cpu} (inferred): instance {@code <world>-<id>};
 *     {@code vsan-iscsi-lun:<node>|<target>|<lun>}: instance
 *     {@code <target>/<lun>} (inferred); {@code remote-virtual-disk}: a path
 *     like {@code virtual-disk}, kept whole even when it is not one.
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
    /** The instance for an instanced type, {@code null} otherwise (and for a path type and the {@code host-cpu} aggregate). */
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
        if (expected.form.isPath()) {
            String path = id.startsWith("/") ? id.substring(1) : id;
            if (path.isEmpty()) return null;
            if (path.indexOf('|') >= 0 && expected.form == EntityType.IdForm.PATH) return null;
            return new EntityRef(expected, path, null, ref);
        }
        String[] parts = id.split("\\|", -1);
        EntityType.IdForm form = expected.form;
        boolean aggregate = form == EntityType.IdForm.ONE_OR_TWO && parts.length == 1;
        if (parts.length != form.parts && !aggregate) return null;
        for (String p : parts) {
            if (p.isEmpty()) return null;
        }
        if (aggregate) return new EntityRef(expected, parts[0], null, ref);
        String instance;
        if (form == EntityType.IdForm.WORLD) {
            instance = parts[1] + "-" + parts[2];
        } else if (form == EntityType.IdForm.LUN) {
            instance = parts[1] + "/" + parts[2];
        } else {
            int ip = form.instancePart;
            instance = ip < 0 ? null : parts[ip];
        }
        return new EntityRef(expected, parts[0], instance, ref);
    }

    /** The number of {@code |} parts of an id that did not parse (for a WARN, never the id). */
    static int parts(String ref) {
        if (ref == null) return 0;
        int colon = ref.indexOf(':');
        String id = colon < 0 ? ref : ref.substring(colon + 1);
        return id.split("\\|", -1).length;
    }
}
