package com.vcfcf.adapters.vsanperf;

import com.vcfcf.adapter.json.SimpleJson;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * One cluster's VMs for the VM tier (design v2 §VirtualMachine and the P2
 * addendum), reduced page by page to compact rows so no page outlives its
 * parse: {@code config.instanceUuid} to VM MoRef, and, when the device list
 * was read, each {@code VirtualDisk}'s normalized backing path (hashed to
 * 64 bits) to (VM, bus position). Plain data plus the parser; no SDK.
 *
 * <p><b>The read</b> (P2, measured): a ContainerView rooted at the cluster,
 * {@code RetrievePropertiesEx} with pathSet {@code name},
 * {@code config.instanceUuid}, {@code runtime.host} and, for
 * {@code virtual-disk}, {@code config.hardware.device};
 * {@code RetrieveOptions.maxObjects} {@link #PAGE}, continued with the
 * page's {@code token} (each token used once, in order).
 *
 * <p><b>Bus position</b> (the {@code virtual-disk} instance, B5): the
 * disk's {@code controllerKey} is looked up among the same VM's controller
 * devices by {@code key}; the controller's {@code busNumber} and the disk's
 * {@code unitNumber} give {@code <prefix><bus>:<unit>}, the prefix by the
 * controller's {@code _typeName} ({@link #busPrefix}). Observed on the lab:
 * {@code ParaVirtualSCSIController} and {@code VirtualLsiLogicController}
 * ({@code scsi}), reproducing 148 of 148 {@code vscsi} ids. A disk on an
 * unknown controller type, or whose controller is missing, is kept as
 * unresolvable (counted, never guessed); so is a path two disks share.
 */
final class VmInventory {

    /** VMs per page (design: about 50, ~1.6 MB decoded at the lab's ~31 KB per VM worst page). */
    static final int PAGE = 50;
    /** Max continuation pages of one read (10,000 VMs at 50). */
    static final int MAX_PAGES = 200;

    static final String P_NAME = "name";
    static final String P_INSTANCE_UUID = "config.instanceUuid";
    static final String P_HOST = "runtime.host";
    static final String P_DEVICE = "config.hardware.device";

    private static final int CODE_UNKNOWN = -1;
    private static final int CODE_AMBIGUOUS = -2;
    private static final String[] PREFIX = {"scsi", "nvme", "sata", "ide"};

    final boolean withDevices;
    private final String[] moids;
    private final Map<String, Integer> byInstanceUuid;
    private final long[] pathHash;
    private final int[] pathVm;
    private final int[] pathBus;
    /** Disks kept as unresolvable (unknown controller, shared path). */
    final int unresolvableDisks;
    final int pages;
    final long wireBytes;
    final long decodedBytes;

    private VmInventory(boolean withDevices, String[] moids, Map<String, Integer> byInstanceUuid,
            long[] pathHash, int[] pathVm, int[] pathBus, int unresolvableDisks, int pages,
            long wireBytes, long decodedBytes) {
        this.withDevices = withDevices;
        this.moids = moids;
        this.byInstanceUuid = byInstanceUuid;
        this.pathHash = pathHash;
        this.pathVm = pathVm;
        this.pathBus = pathBus;
        this.unresolvableDisks = unresolvableDisks;
        this.pages = pages;
        this.wireBytes = wireBytes;
        this.decodedBytes = decodedBytes;
    }

    /** VMs held. */
    int vms() {
        return moids.length;
    }

    /** Virtual disks held (resolvable or not). */
    int disks() {
        return pathHash.length;
    }

    /** The VM MoRef for a {@code config.instanceUuid}, or {@code null}. */
    String vmMoid(String instanceUuid) {
        Integer i = instanceUuid == null ? null : byInstanceUuid.get(instanceUuid);
        return i == null ? null : moids[i];
    }

    /** A resolved virtual disk: its VM and its bus position. */
    static final class Disk {
        final String vmMoid;
        final String busPosition;

        Disk(String vmMoid, String busPosition) {
            this.vmMoid = vmMoid;
            this.busPosition = busPosition;
        }
    }

    /** Sentinel: the path is known but cannot be placed (unknown controller, shared path). */
    static final Disk UNPLACEABLE = new Disk(null, null);

    /**
     * The disk for a normalized perf path ({@link EntityRef#uuid} of a
     * {@code virtual-disk} entity), {@link #UNPLACEABLE}, or {@code null}
     * when the path is not in the map (a re-read trigger).
     */
    Disk disk(String normalizedPath) {
        if (normalizedPath == null || !withDevices) return null;
        int i = Arrays.binarySearch(pathHash, Watermarks.hash(normalizedPath));
        if (i < 0) return null;
        int bus = pathBus[i];
        if (bus < 0) return UNPLACEABLE;
        return new Disk(moids[pathVm[i]], busPosition(bus));
    }

    /** Estimated bytes held (for PERF identity). */
    long bytesEstimate() {
        long b = 16 + 4L * moids.length + 16 + 20L * pathHash.length;
        for (String m : moids) b += 40 + m.length();
        return b + 48L * byInstanceUuid.size();
    }

    // ------------------------------------------------------------------
    // Bus position
    // ------------------------------------------------------------------

    /**
     * The bus prefix for a controller {@code _typeName} (v2 §VirtualMachine):
     * the SCSI controller types {@code scsi}, {@code VirtualNVMEController}
     * {@code nvme}, {@code VirtualAHCIController} {@code sata},
     * {@code VirtualIDEController} {@code ide}; {@code null} for any other.
     */
    static String busPrefix(String controllerType) {
        int c = prefixCode(controllerType);
        return c < 0 ? null : PREFIX[c];
    }

    private static int prefixCode(String t) {
        if (t == null) return CODE_UNKNOWN;
        switch (t) {
            case "ParaVirtualSCSIController":
            case "VirtualLsiLogicController":
            case "VirtualLsiLogicSASController":
            case "VirtualBusLogicController":
                return 0;
            case "VirtualNVMEController":
                return 1;
            case "VirtualAHCIController":
                return 2;
            case "VirtualIDEController":
                return 3;
            default:
                return CODE_UNKNOWN;
        }
    }

    /** prefix (2 bits) | bus (12 bits) | unit (16 bits), or {@code -1} out of range. */
    static int pack(int prefixCode, int bus, int unit) {
        if (prefixCode < 0 || bus < 0 || bus > 4095 || unit < 0 || unit > 65535) return CODE_UNKNOWN;
        return (prefixCode << 28) | (bus << 16) | unit;
    }

    static String busPosition(int packed) {
        return PREFIX[(packed >>> 28) & 3] + ((packed >>> 16) & 0xfff) + ":" + (packed & 0xffff);
    }

    /**
     * A {@code backing.fileName} ({@code [datastore] ns/file.vmdk}) in the
     * perf id's form: the {@code [datastore] } prefix removed, a leading
     * {@code /} removed; {@code null} when empty.
     */
    static String normalizeBackingPath(String fileName) {
        if (fileName == null) return null;
        String p = fileName.trim();
        if (p.startsWith("[")) {
            int close = p.indexOf(']');
            if (close < 0) return null;
            p = p.substring(close + 1).trim();
        }
        if (p.startsWith("/")) p = p.substring(1);
        return p.isEmpty() ? null : p;
    }

    // ------------------------------------------------------------------
    // RetrievePropertiesEx body and page parser
    // ------------------------------------------------------------------

    /** The VM read over a cluster-rooted ContainerView (P2 provenance, verbatim shape). */
    static String retrieveBody(String viewMoid, boolean withDevices) {
        return "{\"specSet\":[{\"_typeName\":\"PropertyFilterSpec\","
                + "\"propSet\":[{\"_typeName\":\"PropertySpec\",\"type\":\"VirtualMachine\","
                + "\"pathSet\":[\"" + P_NAME + "\",\"" + P_INSTANCE_UUID + "\",\"" + P_HOST + "\""
                + (withDevices ? ",\"" + P_DEVICE + "\"" : "") + "]}],"
                + "\"objectSet\":[{\"_typeName\":\"ObjectSpec\",\"obj\":{\"_typeName\":"
                + "\"ManagedObjectReference\",\"type\":\"ContainerView\",\"value\":"
                + Json.quote(viewMoid) + "},\"skip\":true,"
                + "\"selectSet\":[{\"_typeName\":\"TraversalSpec\",\"name\":\"v\","
                + "\"type\":\"ContainerView\",\"path\":\"view\",\"skip\":false}]}]}],"
                + "\"options\":{\"_typeName\":\"RetrieveOptions\",\"maxObjects\":" + PAGE + "}}";
    }

    /** Builds one read from its pages; each page is reduced and dropped at once. */
    static final class Builder {
        private final boolean withDevices;
        private final List<String> moids = new ArrayList<>();
        private final Map<String, Integer> byInstanceUuid = new HashMap<>();
        private long[] hash = new long[64];
        private int[] vm = new int[64];
        private int[] bus = new int[64];
        private int n;
        private int pages;
        private long wire;
        private long decoded;

        Builder(boolean withDevices) {
            this.withDevices = withDevices;
        }

        /** Account the transfer of one page (PERF identity). */
        void bytes(long wireBytes, long decodedBytes) {
            wire += wireBytes;
            decoded += decodedBytes;
        }

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
                throw new Unreadable("VM read: the RetrievePropertiesEx response is not valid JSON");
            }
            pages++;
            if (!root.isObject()) {
                if (root.isNull()) return null;
                throw new Unreadable("VM read: the response is not a RetrieveResult");
            }
            for (SimpleJson oc : root.get("objects").asList()) {
                if (!"VirtualMachine".equals(oc.get("obj").get("type").asString())) continue;
                String moid = oc.get("obj").get("value").asString();
                if (moid == null || moid.isEmpty()) continue;
                String iu = null;
                SimpleJson devices = null;
                for (SimpleJson dp : oc.get("propSet").asList()) {
                    String name = dp.get("name").asString();
                    if (P_INSTANCE_UUID.equals(name)) {
                        iu = Inventory.unbox(dp.get("val")).asString();
                    } else if (P_DEVICE.equals(name)) {
                        devices = Inventory.unbox(dp.get("val"));
                    }
                }
                int idx = moids.size();
                moids.add(moid);
                if (iu != null && !iu.isEmpty()) {
                    // An instanceUuid on two VMs is matched to neither (never guessed).
                    Integer prev = byInstanceUuid.putIfAbsent(iu, idx);
                    if (prev != null && prev >= 0) byInstanceUuid.put(iu, -1);
                }
                if (withDevices && devices != null) addDisks(idx, devices);
            }
            String token = root.get("token").asString();
            return token == null || token.isEmpty() ? null : token;
        }

        private void addDisks(int vmIdx, SimpleJson devices) {
            Map<Long, int[]> controllers = new HashMap<>();
            for (SimpleJson d : devices.asList()) {
                int code = prefixCode(d.get("_typeName").asString());
                SimpleJson key = d.get("key");
                SimpleJson busNo = d.get("busNumber");
                if (!key.isNull() && !busNo.isNull()) {
                    controllers.put(key.asLong(), new int[] {code, (int) busNo.asLong()});
                }
            }
            for (SimpleJson d : devices.asList()) {
                if (!"VirtualDisk".equals(d.get("_typeName").asString())) continue;
                String path = normalizeBackingPath(d.get("backing").get("fileName").asString());
                if (path == null) continue;
                int packed = CODE_UNKNOWN;
                SimpleJson ck = d.get("controllerKey");
                SimpleJson unit = d.get("unitNumber");
                int[] ctl = ck.isNull() ? null : controllers.get(ck.asLong());
                if (ctl != null && !unit.isNull()) packed = pack(ctl[0], ctl[1], (int) unit.asLong());
                add(Watermarks.hash(path), vmIdx, packed);
            }
        }

        private void add(long h, int vmIdx, int packed) {
            if (n == hash.length) {
                hash = Arrays.copyOf(hash, n * 2);
                vm = Arrays.copyOf(vm, n * 2);
                bus = Arrays.copyOf(bus, n * 2);
            }
            hash[n] = h;
            vm[n] = vmIdx;
            bus[n] = packed;
            n++;
        }

        VmInventory build() {
            Integer[] order = new Integer[n];
            for (int i = 0; i < n; i++) order[i] = i;
            Arrays.sort(order, (a, b) -> Long.compare(hash[a], hash[b]));
            long[] h = new long[n];
            int[] v = new int[n];
            int[] b = new int[n];
            int m = 0;
            for (int k = 0; k < n; k++) {
                int i = order[k];
                if (m > 0 && h[m - 1] == hash[i]) {
                    b[m - 1] = CODE_AMBIGUOUS; // one path, two disks: never guessed
                    continue;
                }
                h[m] = hash[i];
                v[m] = vm[i];
                b[m] = bus[i];
                m++;
            }
            int unresolvable = 0;
            for (int k = 0; k < m; k++) if (b[k] < 0) unresolvable++;
            Map<String, Integer> byIu = new HashMap<>();
            for (Map.Entry<String, Integer> e : byInstanceUuid.entrySet()) {
                if (e.getValue() >= 0) byIu.put(e.getKey(), e.getValue());
            }
            return new VmInventory(withDevices, moids.toArray(new String[0]), byIu,
                    Arrays.copyOf(h, m), Arrays.copyOf(v, m), Arrays.copyOf(b, m), unresolvable,
                    pages, wire, decoded);
        }
    }
}
