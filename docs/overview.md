# Overview: VCF Content Factory vSAN Performance

## What's in the Pack

vSAN Performance Service metrics in five tiers. The adapter reads them from
vCenter over the vSphere JSON API (HTTPS 443, one session per adapter
instance) and pushes them through the VCF Operations Suite API onto the
vCenter adapter's own objects, under `vSAN Perf|...`:

- **HostSystem**: the core groups (DOM Owner, DOM Client, vSAN physical
  NICs), the host tier (CMMDS, CPU, memory, host network, TCP/IP stack,
  vmknics, RDT, DOM component manager, ESA dedup, ZDOM) and the disk tier
  (per vSAN disk).
- **ClusterComputeResource**: the cluster tier (capacity, cluster DOM
  client, owner and component manager totals, ESA dedup, RDT, ZDOM, and
  vSAN Direct, File Service and IOInsight when configured).
- **VirtualMachine**: the VM tier (per VM, per virtual disk, per vSCSI
  device).

Key list: [REFERENCE.md](../REFERENCE.md).

**Performance Service state (1.2.1).** Every cycle the pack reads each
cluster's vSAN Performance Service configuration (`VsanClusterGetConfig`).
A cluster with the service disabled is skipped (no performance call, not
counted as failed, `Collection|clusters_perfsvc_disabled`) and gets
`vSAN Perf|Performance Service|enabled` = 0 on its ClusterComputeResource
(1 when enabled), whatever the tier settings.

### Bundled content

- Symptom **"[VCF Content Factory] vSAN Performance Service Disabled"**
  (VMWARE ClusterComputeResource, metric static
  `vSAN Perf|Performance Service|enabled` `< 1`, WARNING, wait 2 cycles,
  cancel 1).
- Alert **"[VCF Content Factory] vSAN Performance Service disabled on
  cluster (vSAN Perf collection paused)"** (ClusterComputeResource,
  WARNING, impact risk, type Storage / Configuration, wait 1, cancel 1).
- Recommendation **"[VCF Content Factory] vSAN Enable Performance
  Service"** (vSphere Client: cluster > Configure > vSAN > Services >
  Performance Service > Edit / Turn On).

### Resource kinds

| Kind | Notes |
|---|---|
| vSAN Performance Adapter Instance (`vcfcf_vsanperf`) | carries the `Collection|*` self-metrics |
| VCF Content Factory vSAN Performance Collector (`VsanPerfCollector`) | one per instance (identity: vCenter Server, Clusters to include); the cycle runs for it, so a failed cycle reaches the account row (DOWN when vCenter is unreachable); no metrics of its own |

### Metrics scope

33 entity types, 451 key definitions:

| Tier | Setting (default) | Types | Keys | Lands on | Lab series per cycle (5-host ESA cluster) |
|---|---|---|---|---|---|
| core | always on | 3 | 61 | HostSystem | 390 |
| host | `tier_host` (on) | 13 | 149 | HostSystem | 705 |
| cluster | `tier_cluster` (on) | 10 | 189 | ClusterComputeResource | 91 |
| disk | `tier_disk` (off) | 4 | 31 | HostSystem, instanced by disk uuid | 50 |
| VM | `tier_vm` (off; every 3rd cycle per cluster) | 3 | 21 | VirtualMachine | up to 2,400 (1,808 with a value) |

With the default tiers that is 399 keys and 1,186 series per cycle on the
lab cluster.

## Cross-Adapter Behavior

All keys land on objects the vCenter adapter owns; this pack creates none
there. Every target is matched on its MoRef plus the vCenter instance UUID
(`VMEntityObjectID` and `VMEntityVCID`), never by name, so the same MoRef
in two vCenters never mixes:

- **HostSystem**: by vSAN node uuid (from the host's vSAN configuration) to
  host MoRef. A host the vCenter adapter does not collect is counted in
  `Collection|hosts_unresolved`.
- **ClusterComputeResource**: cluster-tier data lands on the queried
  cluster when the entity id is that cluster's vSAN uuid. A cluster with no
  Ops object is counted in `Collection|clusters_unresolved`; the cluster
  list is re-read every 12th cycle, when the in-scope cluster set changes,
  and after a miss at most once per 12 cycles.
- **vSAN Direct Cluster, File Service, IOInsight** (id form not yet
  observed): decided per entity: the cluster's uuid lands on the cluster,
  one node uuid of that cluster on that host, anything else is skipped and
  counted in `Collection|entities_unparsed`.
- **Disk tier**: the disk id carries no host, so a per-host
  `QueryDisksForVsan` maps each vSAN disk uuid to its host (cached, re-read
  every 12th cycle or after a miss, at most once per 12 cycles per cluster).
  Unmapped disks are counted in `Collection|disks_unresolved`.
- **VM tier**: VMs by `config.instanceUuid` from a VM read scoped to the
  cluster; virtual disks by backing path to their bus position
  (`Virtual Disk:scsi0:0`, the same instance as `vSCSI:scsi0:0`). Misses are
  counted in `Collection|vms_unresolved` / `vdisks_unresolved`.

Data that cannot be placed is dropped, never guessed. Pushed keys show no
label or unit: the unit is in the key name.

## Notable Behaviors

- Every non-empty 5-minute bucket in the 7-minute window that is newer than
  the last one pushed for that entity and metric is pushed, each with its
  own sample time; the same bucket is never pushed twice (after a failed
  push, or a restart, it may be pushed again, which overwrites it with
  itself).
- Clusters run in parallel (default 4); a slow or failed cluster does not
  hold up the others, and a cycle deadline pushes whatever finished.
- A failing optional (non-core) type is counted in
  `Collection|optional_calls_failed` and named in `last_error`; the cluster
  and the cycle still succeed. A failing core type fails the cluster.
- An entity type a cluster does not offer is skipped there for about a day.
  A type that answers empty 3 cycles in a row (VM types: 3 VM runs) is
  skipped there for about a day too, re-probed with a per-cluster spread;
  the three core types are never skipped for an empty answer, and IOInsight
  is never skipped at all.
- `Collection|perf_calls_per_second` is the aggregate call rate the vCenter
  sustained this cycle; `Collection|perf_call_ms_avg` is per-call latency.

## Known Limitations

- Units are borrowed from the Python pack's labels, not from the API.
- The disk tier's names overlap the built-in vSAN adapter's disk keys (24
  of 31); it is off by default.
- On the lab, the 4 `NIOPSRead*` / `NIOPSWrite*` virtual-disk labels were
  always empty (perhaps only filled under an IOPS-limit policy); they are
  requested and push nothing while empty.
- Non-SCSI virtual disk buses (NVMe, SATA, IDE) and OSA disk ids are not
  observed on the lab; the mapping follows the vim25 types and is
  unverified there.
- Pushed cluster keys took about 6 minutes to read back on devel, VM keys
  about a minute.
- The Performance Service's sustained call ceiling (per vCenter or per
  cluster) is not yet measured; the `PERF` log lines are how to read it.
- Design T3 (replay harness) and T4 (65 MB heap gate at 70 clusters) are
  not built; the scale claim is unverified until they are, along with the
  three-platform acceptance in the repo README.
