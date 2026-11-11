# VCF Content Factory vSAN Performance

A Tier 2 (Java SDK) management pack for VCF Operations. It reads the vSAN
Performance Service over the vSphere JSON API and puts the numbers on the
hosts, clusters and VMs you already have: the vCenter adapter's own VMWARE
HostSystem, ClusterComputeResource and VirtualMachine objects, as keys under
`vSAN Perf`. Besides its adapter instance it creates
one object, the "VCF Content Factory vSAN Performance Collector" its cycle
runs for.

Version 1.2.0 (build 19) adds a **Metric scope** setting (Advanced):
`dashboard`, the default and the behaviour of every earlier version, or
`all`, which collects every metric of the 69 vSAN Performance Service entity
types the Python vsanhostmetrics pack models (1,805 keys: its 1,595, `Actual`
twins left out, plus 210 ESA dedup metrics the 9.1.1 service returns beyond
that model), in the same tiers. At dashboard scope the pack covers
33 entity types, 451 keys, in five tiers:
the always-on core (Build 1: DOM owner, DOM client, vSAN physical NICs, 61
keys), the host tier (13 types, 149 keys on HostSystem) and the cluster tier
(10 types, 189 keys on ClusterComputeResource), both on by default, and the
disk tier (4 types, 31 keys on HostSystem per vSAN disk) and the VM tier (3
types, 21 keys on VirtualMachine), both off by default. On a 5-host ESA
cluster the default tiers are 1,186 series per cycle (390 core, 705 host
tier, 91 cluster tier); the disk tier adds 50, the VM tier up to 2,400 per VM
run (30 VMs). Every key, unit and self-metric: [REFERENCE.md](REFERENCE.md).
Under `all` the same 5-host cluster pushes 6,758 values per bucket with the
default tiers (about 6x per host, 4x per cluster). Design: the factory's
`knowledge/designs/vsanperf-mp-v1.md`, `vsanperf-mp-v2.md` with its P2
addendum, and `vsanperf-mp-v3.md` with its decisions addendum.

Version 1.2.1 reads each cluster's vSAN **Performance Service** state first
(`VsanClusterGetConfig`, not counted in `perf_calls`). A cluster with the
service disabled is skipped (no perf call, not counted failed) and flagged on
its ClusterComputeResource as `vSAN Perf|Performance Service|enabled` = 0
(1 when on), which the bundled symptom and alert act on (see "Bundled
content"). A vSphere fault on one entity type stays with that type: it is
counted (`required_calls_failed` for the core types,
`optional_calls_failed` otherwise) and, after three faulting cycles in a row
on a cluster that otherwise collected, skipped there for 24 hours. An HTTP
error with no vSphere fault body is not a type fault and keeps the 1.2.0
handling. `masternotfound` (no stats primary) fails the cluster on any type,
core or not, since it is cluster-wide, and ends that cluster's calls for the
cycle.

## Keys

At dashboard scope (the default; under `all` most of these groups carry more
keys, REFERENCE.md has every key with its scope):


| Tier | Group (`vSAN Perf\|<group>\|...`) | Entity type | Lands on | Instance | Keys |
|---|---|---|---|---|---|
| core | `DOM Owner` | `host-domowner` | HostSystem | | 38 |
| core | `DOM Client` | `host-domclient` | HostSystem | | 6 |
| core | `pNIC:<vmnic>` | `vsan-pnic-net` | HostSystem | vSAN physical NIC | 17 |
| host | `CMMDS Network` | `cmmds-net` | HostSystem | | 10 |
| host | `CPU` | `vsan-cpu` | HostSystem | | 7 |
| host | `vSAN Direct` | `vsan-direct-host` | HostSystem | | 8 |
| host | `DOM Comp Mgr` | `host-domcompmgr` | HostSystem | | 16 |
| host | `ESA Dedup DOM Client` | `host-esa-dedup-domclient-io` | HostSystem | | 24 |
| host | `Host Network` | `vsan-host-net` | HostSystem | | 8 |
| host | `ZDOM Top Stats` | `host-zdom-top-stats` | HostSystem | | 11 |
| host | `Memory` | `vsan-memory` | HostSystem | | 12 |
| host | `RDT Latency` | `rdt-network-latency` | HostSystem | | 11 |
| host | `TCP/IP:<stack>` | `vsan-tcpip-stats` | HostSystem | TCP/IP stack | 12 |
| host | `vNIC:<vmk>` | `vsan-vnic-net` | HostSystem | vSAN vmknic | 10 |
| host | `vNIC RDT Latency:<vmk>` | `vnic-rdt-network-latency` | HostSystem | vSAN vmknic | 10 |
| host | `ZDOM VTX` | `zdom-vtx` | HostSystem | | 10 |
| cluster | `Capacity` | `vsan-cluster-capacity` | ClusterComputeResource | | 5 |
| cluster | `DOM Client` | `cluster-domclient` | ClusterComputeResource | | 11 |
| cluster | `DOM Comp Mgr` | `cluster-domcompmgr` | ClusterComputeResource | | 12 |
| cluster | `DOM Owner` | `cluster-domowner` | ClusterComputeResource | | 23 |
| cluster | `ESA Dedup DOM Client` | `cluster-esa-dedup-domclient-io` | ClusterComputeResource | | 24 |
| cluster | `RDT Latency` | `cluster-rdt-network-latency` | ClusterComputeResource | | 9 |
| cluster | `ZDOM Top Stats` | `cluster-zdom-top-stats` | ClusterComputeResource | | 7 |
| cluster | `vSAN Direct Cluster` | `vsan-direct-cluster` | cluster or host, decided per entity | | 8 |
| cluster | `File Service` | `vsan-file-service` | cluster or host, decided per entity | | 8 |
| cluster | `IOInsight` | `ioinsight` | cluster or host, decided per entity | | 82 |
| disk | `Cache Disk:<disk uuid>` | `cache-disk` | HostSystem (OSA) | vSAN disk | 8 |
| disk | `Capacity Disk:<disk uuid>` | `capacity-disk` | HostSystem (OSA) | vSAN disk | 13 |
| disk | `ESA Disk Layer:<disk uuid>` | `vsan-esa-disk-layer` | HostSystem | vSAN disk | 5 |
| disk | `ESA Disk SCSI:<disk uuid>` | `vsan-esa-disk-scsifw` | HostSystem | vSAN disk | 5 |
| vm | `VM` | `virtual-machine` | VirtualMachine | | 6 |
| vm | `Virtual Disk:<busN:M>` | `virtual-disk` | VirtualMachine | bus position (`scsi0:0`) | 7 |
| vm | `vSCSI:<scsiN:M>` | `vscsi` | VirtualMachine | virtual SCSI device | 8 |

Under `metric_scope=all` only (36 more types, in the same tiers; keys
per object):

| Tier | Groups (entity types) | Lands on | Keys |
|---|---|---|---|
| cluster | `ESA Dedup DOM Comp Mgr` 42, `ESA Dedup DOM Owner` 82, `ESA Dedup Chunk Service` 33, `ESA Dedup Hash Service` 39, `Data Protection` 1, `Resync` 24, `PMem` 8, `Remote DOM Client` 8, `Compute Remote DOM Client` 8 | ClusterComputeResource (`Resync` and `PMem` by runtime landing; the remote types only for the queried cluster) | 245 |
| host | the four ESA Dedup groups (per host), `vSAN Sparse` 21, `RDT Network` 1, `System Memory` 3, `LSOM World:<world>-<id>` 2, `Heap:<heap>` 1, `Slab:<slab>` 1, `CLOM` 2, `CMMDS Workload` 23, `Distribution` 5, `PMem` 8, `Remote DOM Client` 11, `Compute Remote DOM Client` 11, `iSCSI` 10, `iSCSI Target:<target>` 10, `iSCSI LUN:<target>/<lun>` 10, `pCPU` and `pCPU:<cpu>` 3, `DOM World:<world>-<id>` 3 | HostSystem | 321 |
| disk | `Disk Group:<disk uuid>` 38, `CLOM Disk:<disk uuid>` 3, `Disk Health:<disk uuid>` 10 | HostSystem | 51 |
| vm | `Remote VM` 6, `Remote Virtual Disk:<busN:M>` 3, `Remote vSCSI:<scsiN:M>` 6 | VirtualMachine | 15 |

`DOM World` (and `LSOM World`) instances are named after the vSAN world and
its id, which change when a host reboots: new instances appear and the old
ones stop receiving data (accepted, design Q8; `exclude_types=dom-world-cpu`
leaves them out). On 9.1.1 nine of the new types (`clom-host`, `clom-disk`,
`cmmds-workload`, `ddh-disk`, `heap-memory`, `slab-memory` and the three
remote VM types) fault on every query form; they count as optional failures
until learned after three faults, then cost one call per cluster per day.

**Tier settings** (Advanced): `tier_host` and `tier_cluster` (default
`true`), `tier_disk` and `tier_vm` (default `false`), `vm_tier_every_n_cycles`
(default 3), and `exclude_types` (comma list of entity type ids). An account
upgraded from 1.0.0.9 gets the host and cluster tiers on and the disk and VM
tiers off; set host and cluster to `false` for Build 1 behaviour.
`metric_scope` (default `dashboard`; an account upgraded from 1.1.0 keeps
`dashboard`, nothing changes) applies after the tiers and before
`exclude_types`; a saved change restarts learned types and dedup marks.

## How it collects

- One vCenter session per adapter instance, reused across cycles; logged
  in again once if vCenter drops it. Release id `8.0.3.0`, or the server's
  own vim25 version if vCenter refuses that one.
- Inventory: one PropertyCollector read per cycle over a session-lived view
  of clusters and their hosts (paged when vCenter pages it). A cluster
  counts as vSAN when a member host has vSAN enabled and a vSAN cluster
  uuid.
- One Performance Service call per entity type in scope per vSAN cluster
  per cycle (one wildcard query per type, the API's floor), 7-minute
  window, labels filter (none under `metric_scope=all`), gzip: 3 with the tiers off, 26 with the defaults
  on the first cycles and 23 on the lab once its empty types are learned;
  with all tiers 33 then 28 on the cluster's VM cycle (every 3rd), 30 then
  25 on the others; under `all` 56, 54, then 37 with the defaults on the lab
  (REFERENCE.md "Collection per cycle"). Responses are parsed from the gzip stream one entity at
  a time; VM-type calls run one at a time across the pool.
  Clusters run through a bounded pool (**Parallel clusters**, default 4);
  each cluster's calls run in order: core (DOM owner first), then the
  cluster, host, disk and VM tiers. Each cycle starts after the last cluster
  the previous cycle started before its deadline, so a cluster that always
  runs past the deadline cannot starve the ones after it; since build 19,
  with two or more workers, a cluster the cycle deadline cut after it
  started late in the cycle (not one the workers started with, not one cut
  the cycle before too, and not one that timed out on its own call before
  the deadline) goes first next cycle instead of waiting for the rotation to
  come round. Cost: a cluster too slow to finish even then holds one worker
  for that next cycle, once per rotation lap; with **Parallel clusters** 1
  the rule is off and the rotation alone moves on.
- A type that answers empty 3 cycles in a row on a cluster is not queried
  there for about a day (core types and `ioinsight` never); see
  REFERENCE.md "Learned types". A settings change builds a new `CycleEngine`,
  so the learned-empty state and the dedup marks restart (design B7; build
  10 review). On devel the first cycle after a reconfigure re-sent both
  window buckets (5,988 values = 2 x 2,994) and re-probed the three
  lab-empty types (26 + 3 VM-tier calls = 29). A failing optional type is counted and
  logged; only a core-type failure, or every call failing, fails a cluster.
- Disk tier: a `QueryDisksForVsan` per host maps each vSAN disk to its host
  (cached; re-read every 12th cycle, or after a miss at most once per 12
  cycles; when every host fails, the retry waits for the same limit). VM
  tier: a VM read scoped to the cluster (50 VMs per page, one read or VM
  call in memory at a time; cached, re-read every 12th VM run of the
  cluster, or after a miss at most once per 12 cycles) places VMs by
  instance UUID and virtual disks by backing path at their bus position.
- Values carry the sample's own 5-minute timestamp; every bucket in the
  window newer than the last one pushed is pushed (since build 10), and a
  bucket already pushed is not pushed again (tracked per entity and metric
  in a compact in-memory table, and only once the push came back clean).
- Clusters (cluster tier) and VMs (VM tier) are matched the same way, on
  MoRef plus vCenter instance UUID; one with no VCF Operations object of
  this vCenter is counted in `Collection|clusters_unresolved` /
  `vms_unresolved`.
- Hosts are matched on host MoRef plus vCenter instance UUID, never by
  name. A host with no HostSystem object of this vCenter in VCF Operations
  is counted in `Collection|hosts_unresolved` and its data is dropped. The
  HostSystem list is re-read every 12th cycle (or when a cluster's host set
  changes), so a host the vCenter adapter starts collecting later is picked
  up within about an hour.
- Pushes go through the Suite API, one call per sample time, in batches of
  up to 100 hosts and about 1 MB within each time.
- A cycle deadline (default: interval minus 60 s) stops the cycle; clusters
  that finished are still pushed.

Pushed keys carry no label or unit in the UI (VCF Operations takes
HostSystem metadata from the vCenter adapter), so the unit is in the key
name: `Us` microseconds, `BytesPerSec`, `Pct` for per-mille values converted
to percent.

## Requirements

- vCenter with the vSphere JSON API (vCenter 8.0 Update 1 or later; release
  id 8.0.3.0 was proven on 9.1.1). An older vCenter fails Test Connection
  with a message saying so.
- A read-only vCenter account.
- The vCenter adapter collecting the same vCenter in VCF Operations (the
  HostSystem objects the keys land on).

## Deployment

- **Pin each instance to its own collector group** when you can, so its
  pool and heap do not compete with other adapters on the collector.
- One instance can carry many clusters. To shard a large vCenter, create
  several instances with different **Clusters to include**; that field is
  part of the account's identity, so the instances are distinct accounts.
- Turn **Performance timing logging** on for a cycle or two to read call
  times, the aggregate call rate and push times from the instance log
  (`PERF` lines).

## Acceptance on each platform (before each release)

Install on VCF Operations 8.18, 9.0 and 9.1 (design T5) and check, on each:

1. **Self-metrics reach the instance.** `Collection|*` and
   `Collection|last_error` read back on the vSAN Performance adapter
   instance resource (`GET /suite-api/api/resources/{id}/stats` and
   `.../properties`).
2. **An unreachable vCenter shows the account DOWN**, not ERROR with
   "Resource discovery failed and there was nothing to collect". Check the
   account row's status and text with vCenter unreachable, and again with
   wrong credentials (that one should be ERROR, with a credential-free
   message).
3. One key per group read back on each host, against the same cycle's
   `PERF` log; the Metrics tab shows `vSAN Perf > pNIC > vmnic1` (or note how
   it renders).
4. The collector's own log has no stack-trace storms or unexpected WARN
   lines over a few cycles.
5. **Builds 10 and 11, cluster and VM tiers (design v2 T5, first step).**
   The identifiers were read on devel in the design's P2 round
   (`VMEntityObjectID` + `VMEntityVCID` on ClusterComputeResource and
   VirtualMachine, both part of uniqueness); confirm on each platform that
   this pak's pushes resolve (`clusters_unresolved` and `vms_unresolved` 0)
   and read back one key per new group on a host, on the cluster and on one
   VM. **Wait at least 6 minutes after the push before reading a cluster
   key** (devel: ~343 s); VM keys read back after about a minute, host keys
   after 1 to 4. Turn the disk and VM tiers on for the run, and note how
   `TCP/IP:defaultTcpipStack`, `vNIC:vmk2`, `ESA Disk Layer:<uuid>`,
   `Virtual Disk:scsi0:0` and `vSCSI:scsi0:0` render in the Metrics tab.
6. **Build 10, in-place upgrade from 1.0.0.9:** the account and collector
   object survive, the new settings show their defaults (both tiers on),
   and the Build 1 keys continue without a gap.

7. **Build 19, `metric_scope` (design v3 T5; devel 9.0.2 against
   `vcf-lab-mgmt-cl01` first, all four tiers on, N = 3, timing logging on,
   then 8.18 and 9.1).**
   1. Upgrade in place from 1.1.0.18: the account keeps `dashboard`
      (Metric scope shows its default); `perf_calls` 25 / 28 and the series
      as before.
   2. Set `metric_scope=all` and save. Cycle 1: `perf_calls` 63 (69 on the
      VM run); cycles 2 and 3: 61 (67); from cycle 4: **39 (42)**. The
      design's 55 / 58 for cycles 2 and 3 assumed the nine Q5 types learned
      at the first fault; the decisions addendum makes them optional types
      learned after three faults, so they are still called on cycles 2 and 3
      (VM types: on the first three VM runs).
   3. Learned (lab): `cluster-pmem` and `host-pmem` at cycle 1
      (`NotSupported`, "does not offer"); the nine Q5 types after three
      faults ("faulted (InvalidArgument) 3 cycles in a row"; meanwhile
      `optional_calls_failed` counts them and `last_error` names one);
      the 16 empty types after three empty cycles ("answered empty"); and
      `ioinsight` every cycle.
   4. `series_pushed`: the first cycle about twice the steady value (both
      buckets, marks reset); `PERF tier ... scope=all labels_dropped=` shows
      the `Actual` twins only (pNIC 24, TCP/IP 10, vNIC 7 per entity); one
      INFO per type per day names the dropped labels; `entities_unparsed`
      and the `*_unresolved` counters 0.
   5. Read back, waiting at least 6 minutes for the cluster key: host
      `vSAN Perf|System Memory|totalMbMemUsed` and `pctMemUsed` (integer
      percent, as in the cycle's `PERF` log), cluster
      `vSAN Perf|Data Protection|totalSnapshotCapacity`, host
      `vSAN Perf|pCPU:cpu-0|pcpuUsedPct` and the aggregate
      `vSAN Perf|pCPU|pcpuUsedPct`; each equal to a same-window JSON query.
   6. Browser pass: how `pCPU` beside `pCPU:cpu-0`, and
      `DOM World:VSAN_0x..._CompServer-<id>`, render in the Metrics tab.
   7. Set back to `dashboard`: the next cycle makes 30 calls (33 on the VM
      run), steady 25 / 28 from cycle 4; both buckets re-sent once, then
      about 2,994 values; the read-back keys above get no newer sample;
      "answered empty" lines reappear at cycle 3 for the dashboard empties
      (learned state reset).
   8. After a host reboot: `DOM World` instances of that host change (new
      world ids); the old ones stop receiving data (accepted, design Q8).

8. **1.2.1, Performance Service state and type fault isolation (design v3
   "1.2.1", T5; devel 9.0.2 first: `metric_scope=dashboard`, host and
   cluster tiers on, disk and VM tiers off, N = 3, timing logging on;
   scope `vcf-lab-mgmt-cl01` (service on) and `vcf-lab-osa-probe-cl01`).**
   1. Upgrade in place from 1.2.0.22. With the probe's service **on**
      (as of 2026-11-08), build 22 shows 43 calls a cycle (probe 20, cl01
      23) and `clusters_failed` 1 (the core `vsan-pnic-net` faults
      `SystemError` "no such column: pfcCountRaw" there). On 1.2.1, cycles
      1 to 3 once the learned empties settle: `clusters_failed` 0,
      `required_calls_failed` 1 (`vsan-pnic-net`), `optional_calls_failed`
      3 (`cluster-rdt-network-latency`, `rdt-network-latency`,
      `vnic-rdt-network-latency`, "no such column: kaReset"), `last_error`
      names one of them, one WARN per type. From cycle 4: both counters 0,
      **39 calls** steady (probe 16, cl01 23), learned skips up by 4, four
      INFO "faulted (SystemError) 3 cycles in a row" lines, `last_error`
      `none`. The probe's hosts lose the pNIC core keys while the type is
      learned (accepted: it never answers there). A type that faults on the
      server is counted, named in `last_error` for three cycles, then
      skipped for 24 h; the cluster is never counted failed for it.
   2. With the probe's service **off** (`VsanClusterGetConfig` without
      `perfsvcConfig`, as captured 2026-11-07): `perf_calls` 23 (cl01 only;
      cl01 26 on cycles 1 to 3), `clusters_perfsvc_disabled` 1,
      `clusters_failed` 0, `last_error` `none`, no probe `masternotfound`
      WARN, one INFO "Performance Service disabled on domain-c39146
      (vcf-lab-osa-probe-cl01); perf collection skipped" and none after.
   3. Read back, at least 6 minutes after the push:
      `vSAN Perf|Performance Service|enabled` 0 on the probe cluster, 1 on
      cl01 (also with `tier_cluster` off).
   4. The bundled alert "[VCF Content Factory] vSAN Performance Service
      disabled on cluster (vSAN Perf collection paused)" (symptom "[VCF
      Content Factory] vSAN Performance Service Disabled", `< 1`, wait 2,
      cancel 1; recommendation "[VCF Content Factory] vSAN Enable
      Performance Service") is active on the probe cluster within 2 Ops
      cycles of the first 0, absent on cl01, and cancels within a cycle of
      the first 1.
   5. Scott enables (or disables) the service on the probe (a vCenter
      write, his call): the next cycle logs the transition INFO and pushes
      the new value; after enabling, the probe re-probes every type
      (`perf_calls` 49 = 23 + 26 on its first cycles). Optional: capture
      the `VsanClusterGetConfig` body after an enable-then-disable to
      settle absent versus `enabled: false` (api-cartographer).
   6. 8.18 and 9.1: `clusters_perfsvc_disabled` 0, the value 1 on every
      cluster, no alert.

**Results so far (devel 9.0.2, build 5).** Check 1 passed: all 12 values
read back on the instance; 390 series pushed, keys on all 5 hosts at the
newest bucket, cycle 1.0 s, 5.69 calls/s. Check 2 **failed**: an
unreachable host showed ERROR "Resource discovery failed and there was
nothing to collect: IOException: vCenter login or ServiceInstance read
failed: Login: ConnectException". The platform does not list the adapter
instance among the resources it collects, so a cycle run from the
`rediscover` hook (build 5) could never report DOWN.

**Build 7 change.** The cycle now runs in `collect()` for one object the
pack discovers per instance, "VCF Content Factory vSAN Performance
Collector" (kind `VsanPerfCollector`), the way the compliance pack runs its
cycle for `ComplianceWorld`. Its identity is the account's own: vCenter
Server and Clusters to include exactly as entered, trimmed only (`*` when
Clusters to include is empty), so two accounts that differ only in case or
spacing get two objects (build 8). It is known without a login, so it exists while
vCenter is unreachable and the failure takes the framework's normal collect
path (DOWN for an unreachable target, same credential-free message). The
self-metrics stay on the adapter instance, where check 1 proved them. The
collector object appears from the second cycle of a new instance (an object
discovered in a cycle is collected from the next one). Checks 1 and 2 must
be re-run on build 7, on all three platforms.

**Still open, not runnable from the lab profile:** check 3's Metrics-tab
rendering of `pNIC:vmnic1` (needs a logged-in browser) and check 4's
collector log scan (needs a Cloud Proxy login). Neither credential is in
the lab profile.

## Scale verification (issue #6)

**Build 19 (design v3, `metric_scope=all`).** `--scope all` builds every
type's reply from its P3 capture (69 types, no labels filter, faults and
empty echoes as served on the lab), so the `all` rows below run on what the
9.1.1 service actually returns: 8,040 newest values per 6-host cluster
(7,692 on the hosts, 348 on the cluster; series whose newest token is
`None` push nothing). Under the per-vCenter ceiling `all` never fits 240 s
at 70 clusters (the design's R21): about 44 clusters complete per cycle once
learned, and every cluster completes at least once in any two consecutive
cycles, and over all 12 cycles in any four. That needed the rotation fix of
build 19 (CHANGELOG 1.2.0.19, refined in 1.2.0.20):
before it, 51 of 70 clusters completed in two cycles and 19 never did.
Before the learned state builds up (cycles 1 to 8, longer than the design's
estimate because the nine Q5 types take three faults to learn and a cluster
learns only when it is reached) only 28 to 40 complete per cycle. Under the
per-cluster ceiling `all` fits with 4 workers, as the design's model says
(186 s, then 127 s).

**Delivered (builds 14 to 18): design T3 and T4**, in simulated time against
the verbatim captures (`tests/.../ReplayHarness.java`, gate
`ReplayBudgetTest`, run by `ci/run_java_tests.sh`; standalone
`ci/replay.sh` takes the arguments listed in the harness's class doc, for
example `ci/replay.sh --clusters 70 --model vcenter --cycles 7`). 70 clusters
x 6 hosts x 2 pNICs, 4 workers, 0.19 s per call.

**Push model.** The Suite API push is synchronous in simulated time, as in
production (build 17): a push call moves the caller's clock on by its
duration, the caller drains nothing meanwhile, and workers block once the
16-message result queue is full. Its duration is two terms (build 18):
**60 ms per call plus 1.5 ms per resource** (`--push-fixed-ms`,
`--push-ms-per-resource`), from these `PERF push` readings on devel 9.0.2
(build 16, VM tier on, three cycles), verbatim:

```
ClusterComputeResource resources=1 stat_values=91 bytes~8106 ms=55/69/56/54
HostSystem resources=5 stat_values=1095 bytes~97160 ms=74/51/61/47
VirtualMachine resources=30 stat_values=1808 bytes~155226 ms=70/80/113/48
```

The cost is dominated by the call (~50 to 60 ms), with at most ~1 to 2 ms
per resource. Build 17 assumed 9 ms per resource from one 5-host batch,
which made the VM tier's pushes take most of each cycle.

Every row is asserted in CI:

| Case (assertions) | Cycles 1 to 3 | Steady | Push per cycle | Peak heap delta (retained) | Status |
|---|---|---|---|---|---|
| (a) default tiers, 7 calls/s per vCenter (deadline path on cycles 1 to 3; every cluster done and perf phase < 240 s from cycle 4; 98,350 series; 1,610 calls at cycle 7; heap < 65 MB and < 15 MB) | 1,664 to 1,681 calls in 240 s, 61 to 64 of 70 done (varies run to run) | cycle 4: 1,628 to 1,640 calls, 232.9 to 234.6 s (about 5 s under the assertion); cycle 7: 1,610 calls, 230.3 s | 1.6 s (2.9 s cold) | 6.3 MB (1.8 MB) | passes |
| (a') default tiers, per cluster (all done; 88.9 s then 78.7 s within 2 s) | 1,820 calls, 88.9 s | 1,610 calls, 78.7 s | 1.6 s | not measured | passes |
| (b) all tiers, VM N = 3, per vCenter (deadline path taken; heap < 65 / 50 MB, retained < 35 MB) | 1,602 to 1,679 calls in 240 s, 51 to 53 of 70 done | 1,630 to 1,644 calls, 60 of 70 done | 38 to 76 s | 33.7 MB (26.6 MB) | passes |
| (b') all tiers, VM N = 3, per cluster (every cluster done every cycle; same heap limits) | 2,148 to 2,190 calls, 116 to 127 s, all done | 1,822 calls, 107 s, all done | 50 to 93 s | 34.0 MB (27.9 MB) | passes |
| (c) all tiers, VM N = 1, per vCenter (heap only, same limits) | 1,551 to 1,598 calls, 47 to 48 of 70 done | same | 74 to 150 s | 34.3 MB (27.2 MB) | passes |
| (d) `metric_scope=all`, default tiers, per vCenter (deadline path every cycle; every cluster done at least once in any four consecutive cycles over all 12; from cycle 10, once learning has settled, in any two; the completed clusters' 8,040 values each in the newest bucket; heap < 65 / 50 MB, retained < 35 MB) | 1,512 to 1,681 calls in 240 s, 28 of 70 done (56 calls each, nothing learned yet) | from cycle 9: 1,628 to 1,681 calls, 44 of 70 done, 70 of 70 over any two cycles; cycles 6 to 8 are the warm-up (30 to 40 done) | 4.7 to 6.5 s | 21.7 MB (5.7 MB) | passes |
| (d') `metric_scope=all`, default tiers, per cluster (all done every cycle; perf phase <= 200 s on cycles 1 to 3, <= 135 s from cycle 4; 3,920 / 3,780 / 2,590 calls; 562,800 values) | 3,920 calls, 186.4 s; cycles 2 and 3: 3,780 calls, 181.3 s | 2,590 calls, 126.5 s | 5.5 s (11.1 s cold) | not measured | passes |
| (e) `metric_scope=all`, all tiers, VM N = 3, per cluster (all done every cycle, perf phase < 240 s; heap < 65 MB, own ceilings since build 20: peak < 60 MB, retained < 45 MB) | 4,366 to 4,554 calls, 216.6 to 230.3 s, all done | 2,874 calls, 155.2 s, all done | 55 to 98 s | 38.5 to 46.1 MB (31.2 to 32.4 MB) | passes |

(e)'s ceilings (build 20) are sized on its readings so a ~9 MB sampler
spike on top of the highest peak (55 MB) does not fail it; (a) keeps 15 MB
against readings of 6.3 to 7.5 MB because that is what catches build 14's
regression, and it once read 16.3 MB on such a spike (a run started as a
CPU-load run ended): re-run a red (a) before reading it as a regression.
Before build 15 the VM cases peaked at 70.8 to 76.6 MB. Heap: used heap
after `System.gc()`, sampled every 150 ms of wall time during each cycle,
minus a baseline taken before the engine exists; about 1 MB of noise, and a
10 ms sampler reads 2 to 5 MB higher on the VM cases. The 15 / 50 / 35 MB
ceilings sit at about twice the readings so a regression fails on its own.
Cycles run paced (each call also waits its simulated latency / 20, VM
cases / 40, of wall time) so the result queue behaves as in production.
Figures vary from run to run because thread scheduling decides which
worker takes which cluster. With the VM tier on, pushing still takes 38 to
150 s of a cycle on the draining thread; under the per-vCenter ceiling the
VM cases are bounded by the calls (all tiers do not fit 240 s at 70
clusters, as the design's call plan says), not by the push.

**Still open:**
- **The vSAN Performance Service's call ceiling** (per vCenter or per
  cluster, design D9). The harness models both; which one a real vCenter
  has decides whether the default tiers fit a 5-minute cycle at 70 clusters
  (230 s under the per-vCenter model, 79 s under the per-cluster one). Read
  it from `PERF cycle` on a multi-cluster vCenter with timing logging on.
- The platform acceptance above (design T5), including check 7 for
  `metric_scope`.
- Under `all`: the Suite API's ingest of ~44 MB per cycle at 70 x 6 and the
  Ops-side cost of ~5.6x the series (design Q9, Q10), and whether the nine
  Q5 types answer on an 8.0 U3 or 9.0 vCenter (P3 found no 9.0 or 8.x vSAN
  vCenter in the lab).

## Migrating from the Python vsanhostmetrics pack

This pack is a different adapter kind (`vcfcf_vsanperf`, the Python pack is
`VsanHostMetrics`), so it is **not an upgrade**. The Python pack 2.0.0 collects
"everything" by default; this pack's `metric_scope=all` is the like-for-like
comparison, without the `Actual` twins (identical values) and the Python
pack's 2 computed metrics, and with 210 ESA dedup metrics the Python model
lacks. Note one unit difference: the Python pack divides `pctMemUsed` by 10
(per mille); the service serves an integer percent (P3), which this pack
pushes as served. Both can run side by side
while you compare. To move over: install this pack, create its account and
credentials, compare, then uninstall the Python pack. **No data carries
over:** the Python pack's per-entity objects and their history are removed
with it, and this pack's history starts on HostSystem from its first cycle.

## Repository layout

```
adapter.yaml, describe.xml, resources/, icons/   pak metadata and descriptor
src/com/vcfcf/adapters/vsanperf/
  VsanPerfAdapter.java    SDK glue: configure, Test Connection, the collector object
  CycleReport.java        one cycle: self-metrics and last_error first, then the failure
  CycleEngine.java        the collector: inventory, pool, deadline, tiers, dedup, push
  VsanJsonClient.java     vSphere JSON API client (session, inventory, QueryPerf)
  Inventory.java          PropertyCollector result parser, cluster vSAN rule
  PerfParser.java         QueryPerf response parser (streamed), every-bucket selection
  JsonStream.java         pull JSON reader for the streamed parse
  VmInventory.java        VM read pages to compact rows, bus position
  EntityType.java         the 399-key catalog (design tables), tiers, id forms
  OpsHostMap.java         HostSystem / ClusterComputeResource MoRef + vCenter UUID to Ops id
  Watermarks.java         compact per-(entity, metric) dedup marks
  LearnedTypes.java       types skipped per cluster (faults, empty answers)
  ...                     config, faults, caps, dedup marks, learned types
tests/                    plain-main tests (ci/run_java_tests.sh), verbatim
                          vendor fixtures with provenance, a SimpleJson stub
```

## Tests

`ci/run_java_tests.sh` runs every `tests/**/*Test.java` (no SDK jar needed).

**Tests on every push and PR.** `sdk-validation.yml` verifies the release workflow’s pinned buildkit, runs `validate-sdk` without the private SDK jar, and tests the defect gate and real pak-compare exit statuses. Full framework/adapter compilation, deploy-key access, checksum handover, provenance and release upload are proved only by a tag build. The defect registry is pinned by `DEFECT_REGISTRY_COMMIT` in the release workflow; review and update that pin before releasing when the factory registry changes.

A new pak inherits the test workflow by keeping `.github/workflows/tests.yml` as created: it calls this template's `pak-tests.yml@main`, which runs `ci/run_java_tests.sh` (every `tests/**/*Test.java`) on JDK 17 and 25 (pass `test-script:`, `pre-check:` or `java-versions:` under `with:` to customize).
A pak instantiated from the template also carries a copy of `pak-tests.yml` that it never runs (only the template calls its local copy); the pak may delete it.

## Local dev build (preview)

The official `.pak` is built by CI on a tag (next section). For a local preview,
use the factory's builder. The Broadcom SDK jar for VCF Operations 9.0.2 is **not** shipped —
supply it from a 9.0.2 appliance (JDK 17 or newer is needed to read it):

```sh
# Obtain the SDK jar once from a VCF Ops appliance:
#   scp root@<appliance>:/usr/lib/vmware-vcops/common/lib/vrops-adapters-sdk.jar ~/vcf-ops-sdk/
#   (check its sha256 and rename it as described in the build instructions below)

# Either pass it explicitly:
python3 -m vcfcf_managementpacks build-sdk content/sdk-adapters/<name> \
  --sdk-jar /path/to/vrops-adapters-sdk-9.0.2.0.25137845.jar -o dist

# ...or set it once in the environment:
export VCFCF_SDK_JAR=/path/to/vrops-adapters-sdk-9.0.2.0.25137845.jar
python3 -m vcfcf_managementpacks build-sdk content/sdk-adapters/<name> -o dist
```

Validate first (the cheap loop — exhaust this before building a pak):

```sh
python3 -m vcfcf_managementpacks validate-sdk content/sdk-adapters/<name>
```

The framework jar (`vcfcf-adapter-base.jar`) is provided by the builder; you do
**not** commit it.

## Building from source

You don't need this repo's CI or the VCF Content Factory checkout to
build the `.pak` — the toolchain is a portable tarball. You need:

- **JDK 17+** (`javac` + `jar` on PATH; the 9.0.2 SDK jar's class files are Java 17)
- **python3** with `pyyaml` (`python3 -m pip install pyyaml`)
- **The GitHub CLI** (`gh`) — used to download the build toolchain
  below. The factory repo is public, so no `gh auth login` is needed
  for the download (authenticate only if you hit anonymous API rate
  limits). No `gh`? See the `curl` alternative under step 1.
- **The Broadcom adapter SDK jar for VCF Operations 9.0.2**, and
  **JDK 17 or newer** to read it. This is a Broadcom build artifact
  with no public redistribution channel — it is **never** bundled in
  the toolchain or this repo. The jar is different on every platform
  version; the build compiles against the 9.0.2 one. Get it from a VCF
  Operations 9.0.2 appliance, where it is
  `/usr/lib/vmware-vcops/common/lib/vrops-adapters-sdk.jar`. Keep it
  **outside this repo's working tree**, so it can never be committed
  by accident:

  ```
  mkdir -p ~/vcf-ops-sdk
  scp root@<appliance>:/usr/lib/vmware-vcops/common/lib/vrops-adapters-sdk.jar ~/vcf-ops-sdk/
  sha256sum ~/vcf-ops-sdk/vrops-adapters-sdk.jar
  ```

  Look the hash up in the table under "Consumer contract" in the
  buildkit release notes. It lists the builds the kit recognises and the
  file name it uses for each, `vrops-adapters-sdk-<version>.<build>.jar`
  (for example `vrops-adapters-sdk-9.0.2.0.25137845.jar`); rename your
  copy to match. The kit identifies the jar by its hash, so a jar from a
  patched appliance is a different build with a different name. If the
  hash is not in the table, the appliance is at a build the kit does not
  know yet: the jar still compiles, the build reports it as an unknown
  jar, and you can name it `vrops-adapters-sdk-<version>.<build>.jar`
  from the appliance's own version and build. The same
  section covers the optional 8.18.7 and 9.1.1 jars for the
  compatibility checks. Partners can pull the jar from the Broadcom TAP
  / partner SDK portal instead.

Then, from the root of this repo:

```bash
# Run as one block. The subshell stops at the first failure, so a checksum
# mismatch never reaches the unpack or the build, and your own shell stays open.
(
set -e
# 1. Fetch the build toolchain. Pin a full sdk-buildkit-vX.Y.Z tag (the
#    floating sdk-buildkit-v1 tag moves under you) and verify the published
#    checksum before unpacking. Use the version this repo's release
#    workflow pins (BUILDKIT_TAG in .github/workflows/build-pak-on-tag.yml);
#    1.0.27 at the time of writing.
BUILDKIT_VERSION=1.0.27
gh release download "sdk-buildkit-v$BUILDKIT_VERSION" \
  --repo sentania-labs/vcf-content-factory \
  --pattern "sdk-buildkit-$BUILDKIT_VERSION.tgz*"
# No gh? The assets are public; fetch both with curl instead:
#   base=https://github.com/sentania-labs/vcf-content-factory/releases/download/sdk-buildkit-v$BUILDKIT_VERSION
#   curl -fsSLO "$base/sdk-buildkit-$BUILDKIT_VERSION.tgz"
#   curl -fsSLO "$base/sdk-buildkit-$BUILDKIT_VERSION.tgz.sha256"
# Verify before unpacking. The strong check is against the hash this repo
# commits for its release builds (BUILDKIT_SHA256 in the same workflow),
# which a replaced release asset cannot match:
echo "$(awk '/^  BUILDKIT_SHA256:/{print $2; exit}' .github/workflows/build-pak-on-tag.yml)  sdk-buildkit-$BUILDKIT_VERSION.tgz" | sha256sum -c -   # macOS: shasum -a 256 -c -
# For a version other than the pinned one, the release's own sidecar only
# proves the download is intact:
#   sha256sum -c "sdk-buildkit-$BUILDKIT_VERSION.tgz.sha256"
tar xzf "sdk-buildkit-$BUILDKIT_VERSION.tgz"

# 2. Point the kit at your SDK jar and build
export VCFCF_SDK_JAR=~/vcf-ops-sdk/vrops-adapters-sdk-9.0.2.0.25137845.jar
python3 -m sdk_buildkit validate-sdk .   # cheap loop: compile-check
python3 -m sdk_buildkit build-sdk .      # emits the .pak
)
```

The kit carries everything else it needs (including the
`vcfcf-adapter-base.jar` framework runtime that ends up in the pak's
`lib/`). `validate-sdk` is the fast iteration loop; exhaust it before
building paks.

**Dev builds vs releases.** Anything you build this way is a *dev
build*. The **official** artifact for this repo is the one its own CI
builds and attaches to a GitHub Release when a `v*` tag is pushed —
deterministic, no developer machine in the path.

**If you fork this repo**, the CI workflow
(`.github/workflows/build-pak-on-tag.yml`) needs one adjustment
before your own `v*` tags will build (it already runs on GitHub-hosted
`ubuntu-latest`, so no runner change is needed).

**SDK jar sourcing**: the upstream workflow fetches the Broadcom
   jar from a private repo via an `SDK_RUNTIME_SSH_KEY` deploy-key
   secret you won't have. Replace that step with your own source:
   store the appliance-extracted jar in **a private repo only**, and
   fetch it from there. Never upload the jar as a workflow artifact:
   on a public repo anyone can download workflow artifacts, which
   would amount to redistributing it. Then **also update the
   `--sdk-jar` argument** on the `build-sdk` line of the workflow to
   point at wherever your replacement step puts the jar. The explicit
   `--sdk-jar` flag overrides `VCFCF_SDK_JAR`, so setting the env var
   alone is not enough — if you leave `--sdk-jar _sdk_runtime/...` in
   place the build will look for the upstream path and fail. Do **not**
   commit the jar to a public repo (no redistribution).

## CI release contract (the official artifact)

This repo is the **single source of truth** for its `.pak`. The shippable artifact
is built by CI, never on a laptop:

1. Author + commit + push to `main`.
2. **Push a `vX.Y.Z` tag.** The `build-pak-on-tag` workflow:
   - pulls the `sdk-buildkit` tarball from the factory's Releases at the pinned
     `BUILDKIT_TAG` and verifies it against `BUILDKIT_SHA256` before extracting,
   - gates on the factory defect registry, fetched at `DEFECT_REGISTRY_COMMIT`,
   - fetches the private Broadcom SDK jar from
     `sentania-labs/vcf-content-factory-sdk-runtime` (the jar is committed at
     that repo's root; no release asset is used), authenticated by the
     `SDK_RUNTIME_SSH_KEY` deploy key, at the pinned `SDK_RUNTIME_COMMIT`
     (never that repo's HEAD), and checks the jar against `SDK_JAR_SHA256`
     with `sha256sum -c` before anything compiles against it,
   - builds the `.pak` deterministically (no agent, no factory checkout),
   - gates on `pak-compare` (zero BLOCKING required), and
   - attaches the `.pak` and a `SHA256SUMS` file to the tag's GitHub Release
     with a build-provenance attestation. A published release is never
     overwritten: re-running a tag whose Release already has assets
     fails.

That Release asset **is** the release. A factory `/publish` that references this
pak emits only a **pointer** to the latest Release — it never rebuilds or mirrors
the binary.

### Required release environment secret

`SDK_RUNTIME_SSH_KEY` — a read-only ed25519 **deploy key** for
`sentania-labs/vcf-content-factory-sdk-runtime`. The Broadcom SDK jar is
committed at the root of that private repo and fetched by the workflow via a
shallow git clone; it is never bundled in this repo or the buildkit (C2
redistribution constraint). Setup: generate an ed25519 keypair, add the
PUBLIC half as a read-only deploy key on the sdk-runtime repo, and store the
PRIVATE half as an Environment secret named `SDK_RUNTIME_SSH_KEY` in
`sdk-release` **before** pushing a release tag. See the workflow header for the
runner/JDK/buildkit knobs. Configure required reviewers on `sdk-release` and
protect `v*` tags with repository rulesets before releasing. Those GitHub
settings are managed separately from this template.

## C2 pak shape — no bundled jars

This pak **never** carries `vrops-adapters-sdk` or any Broadcom jar:

- `vcfcf-adapter-base.jar` (the VCF-CF framework) is provided by the buildkit and
  copied into the pak's `lib/` at build time.
- `vrops-adapters-sdk-*.jar` is on the appliance's shared classpath at runtime and
  is supplied to the *compiler* by the consumer (`--sdk-jar` / `VCFCF_SDK_JAR`);
  it is never placed in the pak.

`.gitignore` ignores `lib/*.jar` so a stray jar can't be committed.

## Bundled content

Since 1.2.1 the pak installs three content items with the adapter
(`bundled_content:` in `adapter.yaml`, files under `symptoms/`, `alerts/`
and `recommendations/`; `content_naming: factory_prefixed`;
`content_foreign_adapter_kinds: [VMWARE]` because they target the vCenter
adapter's ClusterComputeResource, where the pak stitches its key):

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

## License

MIT, see `LICENSE` in this repository (the same license as the VCF Content Factory).
