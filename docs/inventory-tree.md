# Inventory Tree: VCF Content Factory vSAN Performance

> Generated from `describe.xml` v0.0.0.16. Do not edit: regenerated on every build.

## Traversal Tree



> \* = identifying (unique) key

## Resource Kinds Reference

| Kind | Display Label | Identifying Keys | Parent(s) |
|------|--------------|-----------------|-----------|
| `VsanPerfCollector` | vSAN Performance Collector | `vcenter` *, `cluster_scope` * | - |

## Metrics Pushed Onto

This pak pushes keys onto resource kinds owned by other management packs, through the Suite API at collection time. `describe.xml` cannot declare keys on another adapter's kinds, so the targets are declared in `adapter.yaml` (`stitch_targets`) and listed here.

### HostSystem (VMWARE)

- **Matched on:** VMEntityObjectID (host MoRef, from the vSAN node uuid; disk tier: from the per-host QueryDisksForVsan map) + VMEntityVCID (vCenter about.instanceUuid). vSAN Direct Cluster, File Service and IOInsight land on a host only when the entity id is one node uuid of the queried cluster (runtime landing); otherwise on the cluster
- **Instanced by:** \<vmnic\> = vSAN physical NIC; \<stack\> = TCP/IP stack; \<vmk\> = vSAN VMkernel NIC; \<disk uuid\> = vSAN disk uuid (disk tier)

| Key group |
|-----------|
| `vSAN Perf\|DOM Owner\|*` |
| `vSAN Perf\|DOM Client\|*` |
| `vSAN Perf\|pNIC:<vmnic>\|*` |
| `vSAN Perf\|CMMDS Network\|*` |
| `vSAN Perf\|CPU\|*` |
| `vSAN Perf\|vSAN Direct\|*` |
| `vSAN Perf\|DOM Comp Mgr\|*` |
| `vSAN Perf\|ESA Dedup DOM Client\|*` |
| `vSAN Perf\|Host Network\|*` |
| `vSAN Perf\|ZDOM Top Stats\|*` |
| `vSAN Perf\|Memory\|*` |
| `vSAN Perf\|RDT Latency\|*` |
| `vSAN Perf\|TCP/IP:<stack>\|*` |
| `vSAN Perf\|vNIC:<vmk>\|*` |
| `vSAN Perf\|vNIC RDT Latency:<vmk>\|*` |
| `vSAN Perf\|ZDOM VTX\|*` |
| `vSAN Perf\|Cache Disk:<disk uuid>\|*` |
| `vSAN Perf\|Capacity Disk:<disk uuid>\|*` |
| `vSAN Perf\|ESA Disk Layer:<disk uuid>\|*` |
| `vSAN Perf\|ESA Disk SCSI:<disk uuid>\|*` |
| `vSAN Perf\|vSAN Direct Cluster\|*` |
| `vSAN Perf\|File Service\|*` |
| `vSAN Perf\|IOInsight\|*` |

### ClusterComputeResource (VMWARE)

- **Matched on:** VMEntityObjectID (cluster MoRef; the entity id must be that cluster's vSAN uuid) + VMEntityVCID (vCenter about.instanceUuid)

| Key group |
|-----------|
| `vSAN Perf\|Capacity\|*` |
| `vSAN Perf\|DOM Client\|*` |
| `vSAN Perf\|DOM Comp Mgr\|*` |
| `vSAN Perf\|DOM Owner\|*` |
| `vSAN Perf\|ESA Dedup DOM Client\|*` |
| `vSAN Perf\|RDT Latency\|*` |
| `vSAN Perf\|ZDOM Top Stats\|*` |
| `vSAN Perf\|vSAN Direct Cluster\|*` |
| `vSAN Perf\|File Service\|*` |
| `vSAN Perf\|IOInsight\|*` |

### VirtualMachine (VMWARE)

- **Matched on:** VMEntityObjectID (VM MoRef, from config.instanceUuid in the cluster's VM read) + VMEntityVCID (vCenter about.instanceUuid)
- **Instanced by:** \<busN:M\> = the virtual disk's bus position from the VM device list (scsi0:0); \<scsiN:M\> = the virtual SCSI device

| Key group |
|-----------|
| `vSAN Perf\|VM\|*` |
| `vSAN Perf\|Virtual Disk:<busN:M>\|*` |
| `vSAN Perf\|vSCSI:<scsiN:M>\|*` |
