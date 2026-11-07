# VCF Content Factory vSAN Performance: Reference

Version 1.2.0, build 19 (Build 3 of the design: `metric_scope`). 69 entity
types, 1,805 keys: 451 at dashboard scope (the default, 33 types, Builds 1
and 2 unchanged) and 1,354 more under `metric_scope=all` (1,144 from the
Python pack's model, the rest of its 1,595, and 210 ESA dedup labels the
9.1.1 service returns beyond that model, catalogued from the P3 capture).
Every key table has a **Scope** column: `dashboard` keys are collected under
either scope, `all` keys only under `all`. Design: the factory's
`knowledge/designs/vsanperf-mp-v1.md`, `vsanperf-mp-v2.md` with its P2
addendum, and `vsanperf-mp-v3.md` with its decisions addendum, each for
what it changes.

## Adapter

| Field | Value |
|---|---|
| Adapter kind | `vcfcf_vsanperf` |
| Tier | 2 (Java SDK) |
| Monitoring interval | 5 minutes (the Performance Service's native 300 s bucket) |
| Objects created | the adapter instance, and one "VCF Content Factory vSAN Performance Collector" (`VsanPerfCollector`) per instance, which the cycle runs for. Identifiers: `vcenter` = vCenter Server and `cluster_scope` = Clusters to include (`*` when empty), exactly as the account stores them, trimmed only; two accounts that differ only in case or spacing are two accounts and get two objects |
| License required | No |

### Credentials (`vcenter_credentials`)

| Field | Key | Type |
|---|---|---|
| User Name | `username` | string |
| Password | `password` | string (masked) |

A read-only vCenter account is enough for the Performance Service (the
Python pack's tested posture). Pushing to HostSystem uses the platform's
per-instance Suite API credential; there is no Ops credential field.

### Settings

| Label | Key | Default | Identity | Notes |
|---|---|---|---|---|
| vCenter Server | `vcenter_host` | (required) | yes | host or host:port, as the certificate names it |
| Clusters to include | `cluster_include` | empty = every vSAN cluster | yes | comma list of cluster names (exact, case-insensitive) or MoRefs (`domain-c6004`) |
| Clusters to exclude | `cluster_exclude` | empty | no (Advanced) | applied after include |
| Parallel clusters | `max_parallel_clusters` | 4 | no (Advanced) | 1 to 16 workers; parallelism is across clusters only |
| Call timeout (s) | `call_timeout_seconds` | 30 | no (Advanced) | per call, send to last body byte; a re-login and the one transport retry share the same budget, and no call outlives the cycle deadline |
| Cycle deadline (s) | `cycle_deadline_seconds` | 0 = interval minus 60 s | no (Advanced) | at the deadline completed clusters are pushed, the rest count as timed out |
| Performance timing logging | `perf_timing_logging` | false | no (Advanced) | `PERF` lines at INFO, below |
| Host tier (extra host groups) | `tier_host` | true | no (Advanced) | the 13 host-tier types beyond the always-on core (DOM Owner, DOM Client, pNIC) |
| Cluster tier | `tier_cluster` | true | no (Advanced) | the 10 cluster-tier types, onto ClusterComputeResource |
| Disk tier | `tier_disk` | false | no (Advanced) | the 4 disk types onto HostSystem, instanced by vSAN disk uuid; adds one `QueryDisksForVsan` per host per disk map refresh and `configManager.vsanSystem` to the inventory read |
| VM tier | `tier_vm` | false | no (Advanced) | the 3 VM types onto VirtualMachine; adds a VM read per cluster (cached) and the Ops VirtualMachine list |
| VM tier every N cycles | `vm_tier_every_n_cycles` | 3 | no (Advanced) | 1 to 12; cluster *c* runs its VM types on cycles where (cycle + a stable hash of its MoRef) mod N is 0, so 1/N of the clusters do VM calls each cycle |
| Metric scope | `metric_scope` | dashboard | no (Advanced) | build 19: `dashboard` = the 451 keys over 33 types, sent as the `labels` filter (unchanged behaviour); `all` = every catalog key of the 69 types in the tiers switched on (1,805), no `labels` filter. Composes with the tiers, never replaces them; see "Metric scope" below |
| Entity types to exclude | `exclude_types` | empty | no (Advanced) | comma list of entity type ids (`ioinsight,vsan-file-service`; any case, so `computeHost-remotedomclient` may be typed in lower case), applied after the tier switches and the scope; may name core types and any of the 69 ids under either scope (a type that is not in scope, such as `host-cpu` under `dashboard`, is silently nothing to remove); an id outside the 69 is a configure WARN and ignored; a list that leaves nothing in scope is a configuration error (Test Connection says so) |

An account saved before build 10 has no stored tier values and gets the
defaults: host and cluster tiers on, disk and VM tiers off. Set both to `false` for Build 1 behaviour. Tier
switches apply from the first cycle after the account is saved.

### Metric scope

Applied in this order: the tier switches pick tiers (core always on); the
scope picks the types (`dashboard`: the 33; `all`: the 69, each new type in
one tier) and the labels (`dashboard`: the dashboard labels as the filter;
`all`: no filter, the catalog labels kept, any other label dropped and
counted in `PERF tier labels_dropped`); `exclude_types` last. An account
saved before 1.2.0 has no stored value and gets `dashboard`: an in-place
upgrade changes nothing and renames no key.

A saved change of scope (like any settings change) builds a new collection
engine, so the learned types and the dedup marks start over: the first cycle
after a switch probes every type in scope again and pushes both buckets of
the 7-minute window (they land on themselves). Keys pushed under `all` stay
in VCF Operations after a switch back to `dashboard`; they stop receiving
data.

The cost of `all` (lab, design v3 "Ops storage impact"): about 6x the
series per host (219 to about 1,280 with a value on the lab, P3 labels
included; `host-cpu` and `dom-world-cpu` are 3 per physical CPU and 3 per DOM
world of that), about 4x per cluster (91 to 348), VMs barely move (+2 keys). Steady calls per
cluster go from 23 to 37 with the default tiers. `exclude_types=dom-world-cpu,host-cpu`
removes the two types that scale with cores and worlds, not storage.

`vcenter_host` and `cluster_include` are the account's identity, so two
instances can shard one vCenter's clusters. Changing either recreates the
account's identity.

## Adapter instance metrics (`Collection|*`)

| Key | Label | Meaning |
|---|---|---|
| `Collection\|clusters_in_scope` | Clusters in scope | vSAN-enabled clusters after include/exclude |
| `Collection\|clusters_ok` | Clusters collected | clusters whose calls all returned |
| `Collection\|clusters_failed` | Clusters failed | a core-type call (DOM owner, DOM client, pNIC) faulted or errored, the cluster object vanished, or every call of the cluster failed (build 11; before, any type's failure counted) |
| `Collection\|clusters_timed_out` | Clusters timed out | per-call timeout or cycle deadline |
| `Collection\|perf_calls` | Performance Service calls | `VsanPerfQueryPerf` calls this cycle |
| `Collection\|series_pushed` | Series pushed | values handed to the Suite API batch push |
| `Collection\|hosts_unresolved` | Hosts unresolved | vSAN node uuids or host MoRefs with no HostSystem object of this vCenter. A host the vCenter adapter starts collecting later is picked up at the next refresh of the HostSystem list: every 12th cycle (up to an hour at 5 minutes), sooner if the cluster's host set changes |
| `Collection\|push_batches_failed` | Push batches failed | from the batch push result |
| `Collection\|cycle_seconds` | Cycle time (s) | wall time of the cycle |
| `Collection\|perf_calls_per_second` | Performance Service call rate | calls / elapsed seconds of the perf phase (first call start to last call end, all workers): aggregate throughput |
| `Collection\|perf_call_ms_avg` | Performance Service call latency (ms) | summed per-call ms / calls: latency, not throughput |
| `Collection\|last_error` | Last error (property) | first problem of the cycle, credential-free; `none` when clean |
| `Collection\|clusters_unresolved` | Clusters unresolved | build 10: cluster-tier data that could not be placed: a vSAN cluster uuid that is not the queried cluster's, or a cluster with no ClusterComputeResource object of this vCenter. Build 19: also every id of a remote (HCI Mesh) type that is not an object of the queried cluster (a node of another cluster or vCenter, a VM or vdisk outside the cluster's VM read), per the remote rule |
| `Collection\|disks_unresolved` | Disks unresolved | build 11: vSAN disk uuids in the disk-tier data that the disk map places on no host (a host whose `QueryDisksForVsan` failed, or a disk added since the last map read) |
| `Collection\|vms_unresolved` | VMs unresolved | build 11: VM instanceUuids not in the cluster's VM read, and VMs with no VMWARE VirtualMachine object of this vCenter |
| `Collection\|vdisks_unresolved` | Virtual disks unresolved | build 11: vdisk paths not in the device map, or on an unknown controller type or a path two disks share (never guessed) |
| `Collection\|optional_calls_failed` | Optional calls failed | build 11 (review W1): failed calls of non-core types; each is named in `last_error` and logged once per cluster and type per refresh, and the cluster still counts as collected. Build 19: includes the faults of the nine `metric_scope=all` types that fault on every ref form on 9.1.1 (`clom-host`, `clom-disk`, `cmmds-workload`, `ddh-disk`, `heap-memory`, `slab-memory`, `remote-virtual-machine`, `remote-virtual-disk`, `remote-vscsi`) until they are learned, three faults in a row |
| `Collection\|entities_unparsed` | Entities unparsed | build 10: entities skipped for their id form (a wrong part count, or an id of a runtime-landing type that is neither the cluster's uuid nor one of its node uuids; build 19: `host-cpu` takes 1 or 2 parts, the world types and `vsan-iscsi-lun` 3, and a `cluster-pmem` node uuid is never landed) |

## Collection per cycle

One wildcard `VsanPerfQueryPerf` call per entity type in scope per vSAN
cluster, in this order: core (DOM owner, DOM client, pNIC), the cluster,
host, disk and VM tiers, so a deadline cut loses the tail first (the VM
types, which also run on a cadence). Calls per cycle on the lab (one 5-host
ESA cluster):

| Tiers | Scope | Cycle 1 | Cycles 2 and 3 | Steady (cycle 4 on) |
|---|---|---|---|---|
| default (core + host + cluster) | dashboard | 26 | 26 | 23 |
| default | all | 56 | 54 | 37 |
| all four, on a cycle without the cluster's VM run | dashboard | 30 | 30 | 25 |
| all four, without the VM run | all | 63 | 61 | 39 |
| all four, on the cluster's VM run (every 3rd cycle) | dashboard | 33 | 33 | 28 |
| all four, on the VM run | all | 69 | 67 | 42 |

Under `all` on the lab (P3 captures, 9.1.1 ESA): the two PMem types fault
`NotSupported` and are learned at once; the nine Q5 types fault on every
call and are learned after three (VM types: three VM runs), counted in
`optional_calls_failed` meanwhile; the empty types are learned after three
empty cycles (16 under all four tiers, among them the HCI Mesh, iSCSI,
resync and OSA-only types); `ioinsight` is queried every cycle. The default
tiers' steady 37 calls per cluster are measured by the T4 harness (row (d'),
2,590 calls for 70 clusters). Per cycle under `all`, default tiers, one
5-host lab cluster: ~218 KB wire / 3.6 MB decoded for all 69 types (P3);
the default tiers push 6,758 values per newest bucket (6,410 on the five
hosts, 2,060 of them core; 348 on the cluster), against 1,186 at dashboard
scope.

Responses are parsed from the gzip stream one entity at a time; only the
wire bytes are held, capped at 4 MB per call (16 MB for the VM types). VM
type calls run one at a time across all workers.

**Learned types.** A type a cluster does not offer (`NotSupported`, or an
`InvalidArgument` naming `entityRefId`) is not queried there for 24 hours.
Build 19 (design v3 Q5): the nine types that fault that way on every ref
form on 9.1.1 (listed under `optional_calls_failed`) are optional types
instead: each fault counts in `optional_calls_failed`, and the type is
learned after three consecutive faults (VM types: three VM runs), with the
same re-probe spread as an empty type; a re-probe that faults again
re-learns at once.
Since build 10 an empty answer (the wildcard echo, no entity) is learned
too: after 3 consecutive empty cycles (VM types: 3 consecutive VM runs) the
type is not queried on that cluster for 24 hours plus a fixed per-cluster,
per-type offset of up to an hour, so clusters learned together do not all
re-probe in one cycle (since build 11 the fault-learned types use the same
offset). A
re-probe that is still empty re-learns at once; one with data clears it.
The three core types are never learned from an empty answer, and
`ioinsight` is never learned at all (queried every cycle, best-effort: a
capture can start at any time). A feature turned on (vSAN Direct, File
Service) is picked up within a day.

**Value selection (build 10).** Every non-empty 5-minute bucket in the
7-minute window that is newer than the last bucket pushed for that entity
and metric is pushed, each with its own sample timestamp (Build 1 pushed
only the newest). The first cycle after an install or upgrade therefore
pushes up to two buckets per series; later cycles normally one. Marks are
per entity and metric, held in memory (a restart may push the newest
buckets again, which overwrites them with themselves).

## Where keys land

Pushed through the Suite API onto the vCenter adapter's own objects,
matched on MoRef plus vCenter instance UUID, never by name:

- **HostSystem**: the core and host-tier groups, by vSAN node uuid (the
  host's `config.vsanHostConfig.clusterInfo.nodeUuid`).
- **ClusterComputeResource** (build 10): the cluster-tier groups, when the
  entity id is the queried cluster's vSAN uuid. The Ops object is matched on
  its `VMEntityObjectID` + `VMEntityVCID` identifiers, the same as hosts
  (both part of uniqueness, read on devel 9.0.2 in the design's P2 round; a
  cluster with no such object is counted in `clusters_unresolved`, never
  pushed under a guess). The cluster list is re-read every 12th cycle and
  when the in-scope cluster set changes; a miss asks for a re-read at most
  once per 12 cycles counted from the last read, and the cluster is skipped
  until then. A pushed cluster key took about 6 minutes to read back on
  devel (hosts 1 to 4 minutes, VMs about 1 minute).
- **HostSystem, disk tier** (build 11): the disk id carries only the vSAN
  disk uuid. A `QueryDisksForVsan` on each member host
  (`HostVsanSystem/<configManager.vsanSystem>`, body `{}`) maps every
  non-empty `vsanUuid` to the host it was read from (`state` reads
  `ineligible` on ESA for in-use disks too, so it is not used). The map is
  read on the cluster's first disk-tier cycle, every 12th cycle, when its
  host set changes, and after an unmapped disk at most once per 12 cycles. A
  host whose call fails contributes nothing until the next read; when every
  host fails, the cluster keeps an empty (or its previous) map and the next
  read waits for the same 12-cycle limit (build 12).
- **VirtualMachine, VM tier** (build 11): a ContainerView rooted at the
  cluster, read with `name`, `config.instanceUuid`, `runtime.host` and (when
  `virtual-disk` is in scope) `config.hardware.device`, 50 VMs per page,
  reduced page by page to compact rows. `virtual-machine` and `vscsi` land
  by `config.instanceUuid`; `virtual-disk` by its backing path (datastore
  prefix and a leading `/` removed) to the VM and the disk's bus position:
  the controller found by the disk's `controllerKey`, its `busNumber`, the
  disk's `unitNumber`, prefixed `scsi` (PVSCSI, LSI Logic, LSI SAS,
  BusLogic), `nvme`, `sata` (AHCI) or `ide`; a disk on any other controller
  is never guessed. The VM read is re-run every 12th VM run of the cluster
  (build 12; so a disk moved between VMs or bus positions, which no miss
  reveals, is placed right again), and after an instanceUuid or path miss or
  an unplaceable path at most once per 12 cycles per cluster. A VM read
  holds the same one permit as the VM-type calls, so one VM read page or
  one VM response is in memory at a time. The VM's Ops object is
  matched on `VMEntityObjectID` + `VMEntityVCID` from the Suite API
  VirtualMachine list (500 per page; `VMEntityInstanceUUID` is not used: it
  was empty on the powered-on vSAN VMs on devel), re-read every 12th cycle
  with a VM run, after a VM read changed a cluster's VM count, and after a
  miss at most once per 12 cycles. If a due VM read fails (including a
  refresh after an earlier successful read), `virtual-disk` is not collected
  for that cluster until a VM read succeeds, since the previous device map
  could place a moved disk on its old VM or bus position; the read is retried
  on the cluster's next VM run, and `virtual-machine` / `vscsi` keep using
  the previous read meanwhile (build 13).
- **Runtime landing** for `vsan-direct-cluster`, `vsan-file-service`,
  `ioinsight` and (build 19) `cluster-resync` and `cluster-pmem`, whose id
  form has not been observed: an id equal to the cluster's vSAN uuid lands on
  the cluster; an id equal to one node uuid of that cluster lands on that
  host under the same group name, except `cluster-pmem`, whose `PMem` group
  `host-pmem` also writes on hosts, so its node uuids are never landed (two
  types never write one key); anything else is skipped, counted in
  `entities_unparsed`, and logged once per type per day with its part count
  (not the id).
- **Remote (HCI Mesh) types** (build 19, design v3 Q6):
  `cluster-remotedomclient` and `computeCluster-remotedomclient` land only
  when the id is the queried cluster's vSAN uuid; `host-remotedomclient` and
  `computeHost-remotedomclient` only when it is one node uuid of the queried
  cluster; `remote-virtual-machine` and `remote-vscsi` only when the VM uuid
  is in the queried cluster's VM read; `remote-virtual-disk` only when the
  path is in its device map. Anything else (a node or cluster of another
  in-scope cluster, another vCenter, a VM computing elsewhere) is dropped and
  counted in `clusters_unresolved`, with one WARN per Ops host map refresh;
  it is never landed on another in-scope cluster's object, even a known one,
  since two clusters' queries can return the same remote entity. Which
  cluster's uuid the remote ids carry is unobserved (no mesh in the lab).
- **`host-cpu`** (build 19): `host-cpu:<node>|cpu-<n>` lands as
  `vSAN Perf|pCPU:cpu-<n>|<leaf>`, and the 1-part `host-cpu:<node>`, the host
  aggregate, as `vSAN Perf|pCPU|<leaf>` (no instance). Both on the host.
- **Per-world instances** (build 19, design v3 Q8, accepted):
  `dom-world-cpu` and `lsom-world-cpu` instances are named
  `<world name>-<world id>`; the name carries a kernel address and the id
  changes when the host reboots, so new instances appear after a reboot and
  the old ones stop receiving data (they stay in VCF Operations; there is no
  per-key delete).

Pushed keys show **no label and no unit** in VCF Operations: the unit is in
the key's leaf (`Us` microseconds, `BytesPerSec`, `Bytes`, `Pct` = per mille
divided by 10, or a percent label that already ends in `Pct`). Key names are
permanent once data exists.

Each push call carries one timestamp, so values are grouped by target kind
and sample time first, then sent in batches of up to 100 resources and about
1 MB per time. A bucket is marked as pushed only when the push call that
carried it came back clean (since build 15 per call; before, only when every
push of the cycle was clean). After a failed call the next cycle sends that
call's buckets again while they are still in the 7-minute window. If a newer
bucket of the same entity and metric was pushed clean in the same cycle, it
is sent again too (since build 16; it lands on itself), because one mark per
entity and metric cannot otherwise remember the older failed bucket.

## Keys on VMWARE HostSystem: core (always on; 61 keys, 407 under `all`)

`<vmnic>` is the NIC name (`vmnic1`); one instance per vSAN physical NIC the
service reports.

### DOM Owner (`host-domowner`): 288 keys per host (38 at dashboard scope)

The owner-side I/O for the vSAN objects this host owns: the DOM owner
coordinates each object's I/O, whichever host's VMs issued it. A busy DOM
Owner on a host means objects it owns are busy, not necessarily its own VMs.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|DOM Owner\|highOIODurationPercentWrite`|`highOIODurationPercentWrite`|High outstanding IO duration percent (write)|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|iops`|`iops`|IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|iopsSegCleanerUnmap`|`iopsSegCleanerUnmap`|IOPS seg cleaner unmap|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyAvgSegCleanerUnmapUs`|`latencyAvgSegCleanerUnmap`|Latency avg seg cleaner unmap|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyMaxReadUs`|`latencyMaxRead`|Latency (max, read)|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyMaxSegCleanerUnmapUs`|`latencyMaxSegCleanerUnmap`|Latency max seg cleaner unmap|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyMaxUnmapUs`|`latencyMaxUnmap`|Latency max unmap|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyMaxWriteUs`|`latencyMaxWrite`|Latency (max, write)|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyStddevSegCleanerUnmapUs`|`latencyStddevSegCleanerUnmap`|Latency standard deviation seg cleaner unmap|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyStddevWriteUs`|`latencyStddevWrite`|Latency standard deviation (write)|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedControllerIopsLimit`|`numNetSchedControllerIopsLimit`|Num net sched controller IOPS limit|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedGuestLTLatUs`|`numNetSchedGuestLTLatUs`|Num net sched guest lt latency us|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedLowBand`|`numNetSchedLowBand`|Num net sched low band|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedLowBandSec`|`numNetSchedLowBandSec`|Num net sched low band sec|seconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedLowBandThreshUs`|`numNetSchedLowBandThreshUs`|Num net sched low band thresh us|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedLowToMidBand`|`numNetSchedLowToMidBand`|Num net sched low to mid band|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedMidBand`|`numNetSchedMidBand`|Num net sched mid band|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedMidBandSec`|`numNetSchedMidBandSec`|Num net sched mid band sec|seconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedMidToLowBand`|`numNetSchedMidToLowBand`|Num net sched mid to low band|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedRollingAvgLatUs`|`numNetSchedRollingAvgLatUs`|Num net sched rolling avg latency us|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedSeenNonResyncTpBps`|`numNetSchedSeenNonResyncTpBps`|Num net sched seen non resync tp bps|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|numNetSchedSeenTotalTpBps`|`numNetSchedSeenTotalTpBps`|Num net sched seen total tp bps|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|owner2PCCommitLatencyAvgUs`|`owner2PCCommitLatencyAvgUs`|Owner2 pc commit latency avg us|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|readLeafOwnerLatencyLocalUs`|`readLeafOwnerLatencyLocal`|Read leaf owner latency local|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|readLeafOwnerLatencyRemoteUs`|`readLeafOwnerLatencyRemote`|Read leaf owner latency remote|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|recoveryWriteCongestion`|`recoveryWriteCongestion`|Resync Read Congestions (sic, Python pack label)|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|segCleanerUnmapCongestion`|`segCleanerUnmapCongestion`|Seg cleaner unmap congestion|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|segCleanerUnmapLeafOwnerMaxLatencyAvgUs`|`segCleanerUnmapLeafOwnerMaxLatencyAvgUs`|Seg cleaner unmap leaf owner max latency avg us|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|serverSwitchCount`|`serverSwitchCount`|Server switch count|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|throughputSegCleanerUnmapBytesPerSec`|`throughputSegCleanerUnmap`|Throughput seg cleaner unmap|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|unmapCount`|`unmapCount`|Unmap count|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerLatencyLocalUs`|`writeLeafOwnerLatencyLocal`|Write leaf owner latency local|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerLatencyRemoteUs`|`writeLeafOwnerLatencyRemote`|Write leaf owner latency remote|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|avgDeltaCreationDomActiveLatencySec`|`avgDeltaCreationDomActiveLatency`|Delta creation DOM active latency (average)|seconds|none|all|
|`vSAN Perf\|DOM Owner\|avgDeltaCreationDomCreationLatencySec`|`avgDeltaCreationDomCreationLatency`|Delta creation DOM creation latency (average)|seconds|none|all|
|`vSAN Perf\|DOM Owner\|avgResyncParallelism`|`avgResyncParallelism`|Resync parallelism (average)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|congestion`|`congestion`|Congestions|none stated|none|all|
|`vSAN Perf\|DOM Owner\|deltaLeafGTEs`|`deltaLeafGTEs`|Delta leaf gt es|none stated|none|all|
|`vSAN Perf\|DOM Owner\|deltaSourceChanges`|`deltaSourceChanges`|Delta source changes|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ecCachedWriteCount`|`ecCachedWriteCount`|Ec cached write count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ecCachedWriteHits`|`ecCachedWriteHits`|Ec cached write hits|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ecFswCount`|`ecFswCount`|Ec fsw count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ecStridedWriteCount`|`ecStridedWriteCount`|Ec strided write count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ecStridedWriteHits`|`ecStridedWriteHits`|Ec strided write hits|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ecWriteCount`|`ecWriteCount`|Ec write count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgDurationMs`|`highOIOAvgDurationMs`|High outstanding IO avg duration ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgDurationMsRead`|`highOIOAvgDurationMsRead`|High outstanding IO avg duration ms (read)|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgDurationMsWrite`|`highOIOAvgDurationMsWrite`|High outstanding IO avg duration ms (write)|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgIOCount`|`highOIOAvgIOCount`|High outstanding IO avg IO count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgIOCountRead`|`highOIOAvgIOCountRead`|High outstanding IO avg IO count (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgIOCountWrite`|`highOIOAvgIOCountWrite`|High outstanding IO avg IO count (write)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgOIO`|`highOIOAvgOIO`|High outstanding IO avg outstanding IO|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgOIORead`|`highOIOAvgOIORead`|High outstanding IO avg outstanding IO (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOAvgOIOWrite`|`highOIOAvgOIOWrite`|High outstanding IO avg outstanding IO (write)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOBytes`|`highOIOBytes`|High outstanding IO bytes|bytes|none|all|
|`vSAN Perf\|DOM Owner\|highOIOBytesRead`|`highOIOBytesRead`|High outstanding IO bytes (read)|bytes|none|all|
|`vSAN Perf\|DOM Owner\|highOIOBytesWrite`|`highOIOBytesWrite`|High outstanding IO bytes (write)|bytes|none|all|
|`vSAN Perf\|DOM Owner\|highOIOCount`|`highOIOCount`|High outstanding IO count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOCountPercent`|`highOIOCountPercent`|High outstanding IO count percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|highOIOCountPercentRead`|`highOIOCountPercentRead`|High outstanding IO count percent (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOCountPercentWrite`|`highOIOCountPercentWrite`|High outstanding IO count percent (write)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOCountRead`|`highOIOCountRead`|High outstanding IO count (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOCountWrite`|`highOIOCountWrite`|High outstanding IO count (write)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIODurationMs`|`highOIODurationMs`|High outstanding IO duration ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|highOIODurationMsRead`|`highOIODurationMsRead`|High outstanding IO duration ms (read)|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|highOIODurationMsWrite`|`highOIODurationMsWrite`|High outstanding IO duration ms (write)|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|highOIODurationPercent`|`highOIODurationPercent`|High outstanding IO duration percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|highOIODurationPercentRead`|`highOIODurationPercentRead`|High outstanding IO duration percent (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIONumTimes`|`highOIONumTimes`|High outstanding IO num times|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIONumTimesRead`|`highOIONumTimesRead`|High outstanding IO num times (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIONumTimesWrite`|`highOIONumTimesWrite`|High outstanding IO num times (write)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|highOIOThroughputBytesPerSec`|`highOIOThroughput`|High outstanding IO throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|highOIOThroughputReadBytesPerSec`|`highOIOThroughputRead`|High outstanding IO throughput (read)|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|highOIOThroughputWriteBytesPerSec`|`highOIOThroughputWrite`|High outstanding IO throughput (write)|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|ioCount`|`ioCount`|IO count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|iopsRecUnmap`|`iopsRecUnmap`|Recovery Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|iopsRecWrite`|`iopsRecWrite`|Recovery Write IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|iopsResyncRead`|`iopsResyncRead`|Resync Read IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgUs`|`latencyAvg`|Latency (average)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgRecUnmapUs`|`latencyAvgRecUnmap`|Recovery Unmap Latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgRecWriteUs`|`latencyAvgRecWrite`|Recovery Write Latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgResyncReadUs`|`latencyAvgResyncRead`|Resync Read Latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgServiceVMReadUs`|`latencyAvgServiceVMRead`|Latency avg service VM (read)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgServiceVMWriteUs`|`latencyAvgServiceVMWrite`|Latency avg service VM (write)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyStddevUs`|`latencyStddev`|Latency Std Dev|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyStddevReadUs`|`latencyStddevRead`|Latency standard deviation (read)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyStddevRecoveryWriteUs`|`latencyStddevRecoveryWrite`|Latency standard deviation recovery (write)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyStddevResyncReadUs`|`latencyStddevResyncRead`|Latency standard deviation resync (read)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyStddevUnmapUs`|`latencyStddevUnmap`|Unmap Latency Std Dev|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|leafGTEs`|`leafGTEs`|Leaf gt es|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedAvgCompRecoveryWriteDelayMs`|`netSchedAvgCompRecoveryWriteDelayMs`|Net sched avg comp recovery write delay ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|netSchedAvgCompResyncReadDelayMs`|`netSchedAvgCompResyncReadDelayMs`|Net sched avg comp resync read delay ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|netSchedAvgOwnerRecoveryWriteDelayMs`|`netSchedAvgOwnerRecoveryWriteDelayMs`|Net sched avg owner recovery write delay ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|netSchedAvgOwnerResyncReadDelayMs`|`netSchedAvgOwnerResyncReadDelayMs`|Net sched avg owner resync read delay ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoveredPerDiscoverySessionBytesPerSec`|`netSchedBWDiscoveredPerDiscoverySession`|Net sched bw discovered per discovery session|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoveryActions`|`netSchedBWDiscoveryActions`|Net sched bw discovery actions|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoveryAvgIntervalsUs`|`netSchedBWDiscoveryAvgIntervalsUs`|Net sched bw discovery avg intervals us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoverySessions`|`netSchedBWDiscoverySessions`|Net sched bw discovery sessions|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoveryStopsDueToGainsFromGuestPct`|`netSchedBWDiscoveryStopsDueToGainsFromGuestPct`|Net sched bw discovery stops due to gains from guest percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoveryStopsDueToHighLatencyPct`|`netSchedBWDiscoveryStopsDueToHighLatencyPct`|Net sched bw discovery stops due to high latency percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoveryStopsDueToLowUtilizationPct`|`netSchedBWDiscoveryStopsDueToLowUtilizationPct`|Net sched bw discovery stops due to low utilization percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWDiscoveryUtilizationPct`|`netSchedBWDiscoveryUtilizationPct`|Net sched bw discovery utilization percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocatedPerReallocationSessionBytesPerSec`|`netSchedBWReallocatedPerReallocationSession`|Net sched bw reallocated per reallocation session|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationActions`|`netSchedBWReallocationActions`|Net sched bw reallocation actions|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationAvgIntervalsUs`|`netSchedBWReallocationAvgIntervalsUs`|Net sched bw reallocation avg intervals us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationSessions`|`netSchedBWReallocationSessions`|Net sched bw reallocation sessions|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationStopsDueToHighSharePct`|`netSchedBWReallocationStopsDueToHighSharePct`|Net sched bw reallocation stops due to high share percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationStopsDueToLowUtilizationPct`|`netSchedBWReallocationStopsDueToLowUtilizationPct`|Net sched bw reallocation stops due to low utilization percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationUtilizationRatioPct`|`netSchedBWReallocationUtilizationRatio`|Net sched bw reallocation utilization ratio|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationUtilizationRatioAbove`|`netSchedBWReallocationUtilizationRatioAbove`|Net sched bw reallocation utilization ratio above|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBWReallocationUtilizationRatioBelow`|`netSchedBWReallocationUtilizationRatioBelow`|Net sched bw reallocation utilization ratio below|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedBandwidthDiscoveryBps`|`netSchedBandwidthDiscoveryBps`|Net sched bandwidth discovery bps|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedGuestIopsDecreasePerDiscoverySession`|`netSchedGuestIopsDecreasePerDiscoverySession`|Net sched guest IOPS decrease per discovery session|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|netSchedGuestIopsIncreasePerReallocationSession`|`netSchedGuestIopsIncreasePerReallocationSession`|Net sched guest IOPS increase per reallocation session|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|netSchedLatencyIncreaseFromResyncPerDiscoverySessionPct`|`netSchedLatencyIncreaseFromResyncPerDiscoverySessionPct`|Net sched latency increase from resync per discovery session percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|netSchedResyncGainsFromGuestCount`|`netSchedResyncGainsFromGuestCount`|Net sched resync gains from guest count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedResyncIopsDecreasePerReallocationSession`|`netSchedResyncIopsDecreasePerReallocationSession`|Net sched resync IOPS decrease per reallocation session|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|netSchedResyncIopsIncreasePerDiscoverySession`|`netSchedResyncIopsIncreasePerDiscoverySession`|Net sched resync IOPS increase per discovery session|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|netSchedUnderUtilCount`|`netSchedUnderUtilCount`|Net sched under utilization count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedUnderUtilGuestBps`|`netSchedUnderUtilGuestBps`|Net sched under utilization guest bps|none stated|none|all|
|`vSAN Perf\|DOM Owner\|netSchedUnderUtilTtlBps`|`netSchedUnderUtilTtlBps`|Net sched under utilization ttl bps|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numBlockedDeltaToBaseResyncJobs`|`numBlockedDeltaToBaseResyncJobs`|Num blocked delta to base resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numBlockedFullResyncJobs`|`numBlockedFullResyncJobs`|Num blocked full resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numBlockedPriorityResyncJobs`|`numBlockedPriorityResyncJobs`|Num blocked priority resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numBlockedResyncJobs`|`numBlockedResyncJobs`|Num blocked resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numBlockedSharedResyncJobs`|`numBlockedSharedResyncJobs`|Num blocked shared resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numBlockedSiblingToDeltaResyncJobs`|`numBlockedSiblingToDeltaResyncJobs`|Num blocked sibling to delta resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteDeltaToBaseResyncOps`|`numCompleteDeltaToBaseResyncOps`|Num complete delta to base resync ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteFullResyncOps`|`numCompleteFullResyncOps`|Num complete full resync ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteResyncOps`|`numCompleteResyncOps`|Num complete resync ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteResyncRwrBatches`|`numCompleteResyncRwrBatches`|Num complete resync rwr batches|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteRwrOps`|`numCompleteRwrOps`|Num complete rwr ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteSiblingToDeltaResyncOps`|`numCompleteSiblingToDeltaResyncOps`|Num complete sibling to delta resync ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numInflightDedupResyncJobs`|`numInflightDedupResyncJobs`|Num inflight dedup resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numInflightPriorityResyncJobs`|`numInflightPriorityResyncJobs`|Num inflight priority resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numInflightSharedResyncJobs`|`numInflightSharedResyncJobs`|Num inflight shared resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompIops`|`numNetSchedCompIops`|Num net sched comp IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompIopsLimit`|`numNetSchedCompIopsLimit`|Num net sched comp IOPS limit|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompRecoveryWriteIops`|`numNetSchedCompRecoveryWriteIops`|Num net sched comp recovery write IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompRecoveryWriteIopsLimit`|`numNetSchedCompRecoveryWriteIopsLimit`|Num net sched comp recovery write IOPS limit|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompRecoveryWriteTpThrottledBytesPerSec`|`numNetSchedCompRecoveryWriteTpThrottled`|Num net sched comp recovery write tp throttled|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompResyncReadIops`|`numNetSchedCompResyncReadIops`|Num net sched comp resync read IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompResyncReadIopsLimit`|`numNetSchedCompResyncReadIopsLimit`|Num net sched comp resync read IOPS limit|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompResyncReadTpThrottledBytesPerSec`|`numNetSchedCompResyncReadTpThrottled`|Num net sched comp resync read tp throttled|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedCompTpThrottledBytesPerSec`|`numNetSchedCompTpThrottled`|Num net sched comp tp throttled|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedControllerActivations`|`numNetSchedControllerActivations`|Num net sched controller activations|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedHighBand`|`numNetSchedHighBand`|Num net sched high band|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedHighBandHighShare`|`numNetSchedHighBandHighShare`|Num net sched high band high share|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedHighBandLowLimit`|`numNetSchedHighBandLowLimit`|Num net sched high band low limit|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedHighBandSec`|`numNetSchedHighBandSec`|Num net sched high band sec|seconds|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedHighToLowBand`|`numNetSchedHighToLowBand`|Num net sched high to low band|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedHighToMidBand`|`numNetSchedHighToMidBand`|Num net sched high to mid band|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedLowBandHighLimit`|`numNetSchedLowBandHighLimit`|Num net sched low band high limit|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedLowToHighBand`|`numNetSchedLowToHighBand`|Num net sched low to high band|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedMidBandHighLimit`|`numNetSchedMidBandHighLimit`|Num net sched mid band high limit|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedMidBandLowShare`|`numNetSchedMidBandLowShare`|Num net sched mid band low share|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedMidToHighBand`|`numNetSchedMidToHighBand`|Num net sched mid to high band|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerIops`|`numNetSchedOwnerIops`|Num net sched owner IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerIopsLimit`|`numNetSchedOwnerIopsLimit`|Num net sched owner IOPS limit|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerRecoveryWriteIops`|`numNetSchedOwnerRecoveryWriteIops`|Num net sched owner recovery write IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerRecoveryWriteIopsLimit`|`numNetSchedOwnerRecoveryWriteIopsLimit`|Num net sched owner recovery write IOPS limit|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerRecoveryWriteTpThrottledBytesPerSec`|`numNetSchedOwnerRecoveryWriteTpThrottled`|Num net sched owner recovery write tp throttled|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerResyncReadIops`|`numNetSchedOwnerResyncReadIops`|Num net sched owner resync read IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerResyncReadIopsLimit`|`numNetSchedOwnerResyncReadIopsLimit`|Num net sched owner resync read IOPS limit|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerResyncReadTpThrottledBytesPerSec`|`numNetSchedOwnerResyncReadTpThrottled`|Num net sched owner resync read tp throttled|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedOwnerTpThrottledBytesPerSec`|`numNetSchedOwnerTpThrottled`|Num net sched owner tp throttled|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedRecoveryWriteTpThrottled`|`numNetSchedRecoveryWriteTpThrottled`|Num net sched recovery write tp throttled|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedResyncReadTpThrottled`|`numNetSchedResyncReadTpThrottled`|Num net sched resync read tp throttled|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedResyncTotalSharePercent`|`numNetSchedResyncTotalSharePercent`|Num net sched resync total share percent|percent|none|all|
|`vSAN Perf\|DOM Owner\|numNetSchedSeenResyncTpBps`|`numNetSchedSeenResyncTpBps`|Num net sched seen resync tp bps|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|numOio`|`numOio`|Num outstanding IO|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingDecomResyncJobs`|`numPendingDecomResyncJobs`|Num pending decom resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingDedupResyncJobs`|`numPendingDedupResyncJobs`|Num pending dedup resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingDeltaToBaseResyncJobs`|`numPendingDeltaToBaseResyncJobs`|Num pending delta to base resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingFullResyncJobs`|`numPendingFullResyncJobs`|Num pending full resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingResyncJobs`|`numPendingResyncJobs`|Num pending resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingSiblingToDeltaResyncJobs`|`numPendingSiblingToDeltaResyncJobs`|Num pending sibling to delta resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numRunningDeltaToBaseResyncJobs`|`numRunningDeltaToBaseResyncJobs`|Num running delta to base resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numRunningFullResyncJobs`|`numRunningFullResyncJobs`|Num running full resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numRunningResyncJobs`|`numRunningResyncJobs`|Num running resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numRunningSiblingToDeltaResyncJobs`|`numRunningSiblingToDeltaResyncJobs`|Num running sibling to delta resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedDedupResyncJobs`|`numSuspendedDedupResyncJobs`|Num suspended dedup resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedDeltaToBaseResyncJobs`|`numSuspendedDeltaToBaseResyncJobs`|Num suspended delta to base resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedFullResyncJobs`|`numSuspendedFullResyncJobs`|Num suspended full resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedPriorityResyncJobs`|`numSuspendedPriorityResyncJobs`|Num suspended priority resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedResyncChangemaps`|`numSuspendedResyncChangemaps`|Num suspended resync changemaps|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedResyncJobs`|`numSuspendedResyncJobs`|Num suspended resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedSharedResyncJobs`|`numSuspendedSharedResyncJobs`|Num suspended shared resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedSiblingToDeltaResyncJobs`|`numSuspendedSiblingToDeltaResyncJobs`|Num suspended sibling to delta resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|oioRead`|`oioRead`|Outstanding IO (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|oioRecoveryWrite`|`oioRecoveryWrite`|Outstanding IO recovery (write)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|oioResyncRead`|`oioResyncRead`|Outstanding IO resync (read)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|oioWrite`|`oioWrite`|Outstanding IO (write)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ownerECCacheHitMissRatioPct`|`ownerECCacheHitMissRatio`|Owner ec cache hit miss ratio|percent|none|all|
|`vSAN Perf\|DOM Owner\|ownerECCacheNumCachedObjects`|`ownerECCacheNumCachedObjects`|Owner ec cache num cached objects|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ownerECCacheNumFailedAllocs`|`ownerECCacheNumFailedAllocs`|Owner ec cache num failed allocs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|ownerECCacheNumSuccessAllocs`|`ownerECCacheNumSuccessAllocs`|Owner ec cache num success allocs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOtherOwnerOpsFailure`|`rateOtherOwnerOpsFailure`|Other owner ops failure (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsBadParam`|`rateOwnerOpsBadParam`|Owner ops bad param (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsChecksumMismatch`|`rateOwnerOpsChecksumMismatch`|Owner ops checksum mismatch (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsIoError`|`rateOwnerOpsIoError`|Owner ops IO error (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsLimitExceed`|`rateOwnerOpsLimitExceed`|Owner ops limit exceed (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsNoConnect`|`rateOwnerOpsNoConnect`|Owner ops no connect (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsNoMem`|`rateOwnerOpsNoMem`|Owner ops no mem (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsNoSpace`|`rateOwnerOpsNoSpace`|Owner ops no space (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsNotSupported`|`rateOwnerOpsNotSupported`|Owner ops not supported (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|rateOwnerOpsRetry`|`rateOwnerOpsRetry`|Owner ops retry (rate)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|readCongestion`|`readCongestion`|Read Congestions|none stated|none|all|
|`vSAN Perf\|DOM Owner\|readCount`|`readCount`|Read count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|readECLeafOwnerMaxLatencyAvgUs`|`readECLeafOwnerMaxLatencyAvgUs`|Read ec leaf owner max latency avg us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|readECLeafOwnerMaxLatencyUs`|`readECLeafOwnerMaxLatencyUs`|Read ec leaf owner max latency us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|readLeafOwnerIops`|`readLeafOwnerIops`|Read leaf owner IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|readLeafOwnerLatencyUs`|`readLeafOwnerLatency`|Read leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|readLeafOwnerMaxLatencyAvgUs`|`readLeafOwnerMaxLatencyAvgUs`|Read leaf owner max latency avg us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|readLeafOwnerRetriesPerOp`|`readLeafOwnerRetriesPerOp`|Read leaf owner retries per op|none stated|none|all|
|`vSAN Perf\|DOM Owner\|readLeafOwnerThroughputBytesPerSec`|`readLeafOwnerThroughput`|Read leaf owner throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|readOwnerRetriesPerOp`|`readOwnerRetriesPerOp`|Read owner retries per op|none stated|none|all|
|`vSAN Perf\|DOM Owner\|recUnmapCount`|`recUnmapCount`|Recovery unmap count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|recWriteCount`|`recWriteCount`|Recovery write count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|recoveryUnmapCongestion`|`recoveryUnmapCongestion`|Recovery unmap congestion|none stated|none|all|
|`vSAN Perf\|DOM Owner\|recoveryUnmapLeafOwnerLatencyUs`|`recoveryUnmapLeafOwnerLatency`|Recovery unmap leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|recoveryWriteLeafOwnerLatencyUs`|`recoveryWriteLeafOwnerLatency`|Recovery write leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|recoveryWriteLeafOwnerLatencyLocalUs`|`recoveryWriteLeafOwnerLatencyLocal`|Recovery write leaf owner latency local|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|recoveryWriteLeafOwnerLatencyRemoteUs`|`recoveryWriteLeafOwnerLatencyRemote`|Recovery write leaf owner latency remote|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|resyncReadCount`|`resyncReadCount`|Resync read count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|resyncReadLeafOwnerLatencyUs`|`resyncReadLeafOwnerLatency`|Resync read leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|resyncReadLeafOwnerLatencyLocalUs`|`resyncReadLeafOwnerLatencyLocal`|Resync read leaf owner latency local|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|resyncReadLeafOwnerLatencyRemoteUs`|`resyncReadLeafOwnerLatencyRemote`|Resync read leaf owner latency remote|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|resyncsEndedAsDeltaToBase`|`resyncsEndedAsDeltaToBase`|Resyncs ended as delta to base|none stated|none|all|
|`vSAN Perf\|DOM Owner\|resyncsStartedAsDeltaToBase`|`resyncsStartedAsDeltaToBase`|Resyncs started as delta to base|none stated|none|all|
|`vSAN Perf\|DOM Owner\|segCleanerUnmapLeafOwnerLatencyUs`|`segCleanerUnmapLeafOwnerLatency`|Seg cleaner unmap leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|serverSwitchAvglatencyUs`|`serverSwitchAvglatency`|Server switch average latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|serverSwitchLatencyMaxUs`|`serverSwitchLatencyMaxUs`|Server switch latency max us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|serverSwitchlatencyStddevUs`|`serverSwitchlatencyStddev`|Server switchlatency standard deviation|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|throughputBytesPerSec`|`throughput`|Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|totalDeltaCreationDomActiveLatencyUs`|`totalDeltaCreationDomActiveLatency`|Delta creation DOM active latency (total)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|totalDeltaCreationDomCreationLatencyUs`|`totalDeltaCreationDomCreationLatency`|Delta creation DOM creation latency (total)|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|totalDeltasCreatedCount`|`totalDeltasCreatedCount`|Deltas created count (total)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|tputReadBytesPerSec`|`tputRead`|Read Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputRecUnmapBytesPerSec`|`tputRecUnmap`|Throughput recovery unmap|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputRecWriteBytesPerSec`|`tputRecWrite`|Recovery Write Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputResyncReadBytesPerSec`|`tputResyncRead`|Resync Read Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputServiceVMReadBytesPerSec`|`tputServiceVMRead`|Throughput service VM (read)|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputServiceVMWriteBytesPerSec`|`tputServiceVMWrite`|Throughput service VM (write)|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputUnmapBytesPerSec`|`tputUnmap`|Throughput unmap|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputWriteBytesPerSec`|`tputWrite`|Write Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitBlockedByGuestIOPS`|`twoPCBatchCommitBlockedByGuestIOPS`|Two pc batch commit blocked by guest IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitBlockedByOtherIOPS`|`twoPCBatchCommitBlockedByOtherIOPS`|Two pc batch commit blocked by other IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitBlockedByResyncIOPS`|`twoPCBatchCommitBlockedByResyncIOPS`|Two pc batch commit blocked by resync IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitBlockedBySyncIOPS`|`twoPCBatchCommitBlockedBySyncIOPS`|Two pc batch commit blocked by sync IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitGuestIOPS`|`twoPCBatchCommitGuestIOPS`|Two pc batch commit guest IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitOtherIOPS`|`twoPCBatchCommitOtherIOPS`|Two pc batch commit other IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitResyncIOPS`|`twoPCBatchCommitResyncIOPS`|Two pc batch commit resync IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCBatchCommitSyncIOPS`|`twoPCBatchCommitSyncIOPS`|Two pc batch commit sync IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCCommitCount`|`twoPCCommitCount`|Two pc commit count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|twoPCCommitSize`|`twoPCCommitSize`|Two pc commit size|none stated|none|all|
|`vSAN Perf\|DOM Owner\|twoPCPrepareLimitRetryGuestAvgTimeMs`|`twoPCPrepareLimitRetryGuestAvgTimeMs`|Two pc prepare limit retry guest avg time ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|twoPCPrepareLimitRetryGuestIOPS`|`twoPCPrepareLimitRetryGuestIOPS`|Two pc prepare limit retry guest IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCPrepareLimitRetryOtherAvgTimeMs`|`twoPCPrepareLimitRetryOtherAvgTimeMs`|Two pc prepare limit retry other avg time ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|twoPCPrepareLimitRetryOtherIOPS`|`twoPCPrepareLimitRetryOtherIOPS`|Two pc prepare limit retry other IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCPrepareLimitRetryResyncAvgTimeMs`|`twoPCPrepareLimitRetryResyncAvgTimeMs`|Two pc prepare limit retry resync avg time ms|milliseconds|none|all|
|`vSAN Perf\|DOM Owner\|twoPCPrepareLimitRetryResyncIOPS`|`twoPCPrepareLimitRetryResyncIOPS`|Two pc prepare limit retry resync IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|twoPCPrepareRetryCount`|`twoPCPrepareRetryCount`|Two pc prepare retry count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|twoPCReservedLSNExhaustedCount`|`twoPCReservedLSNExhaustedCount`|Two pc reserved lsn exhausted count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|twoPCReservedLSNUnusedCount`|`twoPCReservedLSNUnusedCount`|Two pc reserved lsn unused count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|twoPCReservedLSNUsedCount`|`twoPCReservedLSNUsedCount`|Two pc reserved lsn used count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|unmapCongestion`|`unmapCongestion`|Unmap congestion|none stated|none|all|
|`vSAN Perf\|DOM Owner\|unmapECLeafOwnerMaxLatencyAvgUs`|`unmapECLeafOwnerMaxLatencyAvgUs`|Unmap ec leaf owner max latency avg us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|unmapECLeafOwnerMaxLatencyUs`|`unmapECLeafOwnerMaxLatencyUs`|Unmap ec leaf owner max latency us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|unmapLeafOwnerLatencyUs`|`unmapLeafOwnerLatency`|Unmap leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|unmapLeafOwnerMaxLatencyAvgUs`|`unmapLeafOwnerMaxLatencyAvgUs`|Unmap leaf owner max latency avg us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|writeCongestion`|`writeCongestion`|Write Congestions|none stated|none|all|
|`vSAN Perf\|DOM Owner\|writeCount`|`writeCount`|Write count|none stated|none|all|
|`vSAN Perf\|DOM Owner\|writeECLeafOwnerMaxLatencyAvgUs`|`writeECLeafOwnerMaxLatencyAvgUs`|Write ec leaf owner max latency avg us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|writeECLeafOwnerMaxLatencyUs`|`writeECLeafOwnerMaxLatencyUs`|Write ec leaf owner max latency us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerIops`|`writeLeafOwnerIops`|Write leaf owner IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerLatencyUs`|`writeLeafOwnerLatency`|Write leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerMaxLatencyAvgUs`|`writeLeafOwnerMaxLatencyAvgUs`|Write leaf owner max latency avg us|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerRetriesPerOp`|`writeLeafOwnerRetriesPerOp`|Write leaf owner retries per op|none stated|none|all|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerThroughputBytesPerSec`|`writeLeafOwnerThroughput`|Write leaf owner throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|writeOwnerRetriesPerOp`|`writeOwnerRetriesPerOp`|Write owner retries per op|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 1440 catalog series.

### DOM Client (`host-domclient`): 78 keys per host (6 at dashboard scope)

The I/O this host's VMs issued, as seen from the client side, whichever
hosts own the objects. This is the closest group to "what my VMs on this
host experienced".

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|DOM Client\|congestion`|`congestion`|Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|DOM Client\|IOPSWithinBursts`|`IOPSWithinBursts`|IOPS within bursts|IO/s|none|all|
|`vSAN Perf\|DOM Client\|IOPSWithinBurstsExclFirstObj`|`IOPSWithinBurstsExclFirstObj`|IOPS within bursts excl first obj|IO/s|none|all|
|`vSAN Perf\|DOM Client\|IOPSWithinBurstsMultiObj`|`IOPSWithinBurstsMultiObj`|IOPS within bursts multi obj|IO/s|none|all|
|`vSAN Perf\|DOM Client\|avgBurstArrivalTimeUs`|`avgBurstArrivalTime`|Burst arrival time (average)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|avgBurstArrivalTimeMultiObj`|`avgBurstArrivalTimeMultiObj`|Burst arrival time multi obj (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgBurstCompletionTimeUs`|`avgBurstCompletionTime`|Burst completion time (average)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|avgBurstCompletionTimeMultiObj`|`avgBurstCompletionTimeMultiObj`|Burst completion time multi obj (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgBurstPeakOIO`|`avgBurstPeakOIO`|Burst peak outstanding IO (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgBurstPeakOIOMultiObj`|`avgBurstPeakOIOMultiObj`|Burst peak outstanding IO multi obj (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgBurstPeakOutstandingKb`|`avgBurstPeakOutstandingKb`|Burst peak outstanding kb (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgBurstPeakOutstandingKbMultiObj`|`avgBurstPeakOutstandingKbMultiObj`|Burst peak outstanding kb multi obj (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgBurstTotalTimeUs`|`avgBurstTotalTime`|Burst total time (average)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|avgBurstTotalTimeMultiObj`|`avgBurstTotalTimeMultiObj`|Burst total time multi obj (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgPctTimeToPeak`|`avgPctTimeToPeak`|Percent time to peak (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|avgPctTimeToPeakMultiObj`|`avgPctTimeToPeakMultiObj`|Percent time to peak multi obj (average)|none stated|none|all|
|`vSAN Perf\|DOM Client\|bandwidthWithinBursts`|`bandwidthWithinBursts`|Bandwidth within bursts|none stated|none|all|
|`vSAN Perf\|DOM Client\|bandwidthWithinBurstsExclFirstObj`|`bandwidthWithinBurstsExclFirstObj`|Bandwidth within bursts excl first obj|none stated|none|all|
|`vSAN Perf\|DOM Client\|bandwidthWithinBurstsMultiObj`|`bandwidthWithinBurstsMultiObj`|Bandwidth within bursts multi obj|none stated|none|all|
|`vSAN Perf\|DOM Client\|burstsPerSecond`|`burstsPerSecond`|Bursts per second|none stated|none|all|
|`vSAN Perf\|DOM Client\|burstsPerSecondMultiObj`|`burstsPerSecondMultiObj`|Bursts per second multi obj|none stated|none|all|
|`vSAN Perf\|DOM Client\|clientCacheHitRate`|`clientCacheHitRate`|Local Client Cache Hit Rate|none stated|none|all|
|`vSAN Perf\|DOM Client\|clientCacheHits`|`clientCacheHits`|Local Client Cache Hit IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Client\|ioCount`|`ioCount`|IO count|none stated|none|all|
|`vSAN Perf\|DOM Client\|iops`|`iops`|IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Client\|iopsSegCleanerUnmap`|`iopsSegCleanerUnmap`|IOPS seg cleaner unmap|IO/s|none|all|
|`vSAN Perf\|DOM Client\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Client\|latencyAvgUs`|`latencyAvg`|Latency (average)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyAvgSegCleanerUnmapUs`|`latencyAvgSegCleanerUnmap`|Latency avg seg cleaner unmap|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyMaxReadUs`|`latencyMaxRead`|Latency (max, read)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyMaxSegCleanerUnmapUs`|`latencyMaxSegCleanerUnmap`|Latency max seg cleaner unmap|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyMaxUnmapUs`|`latencyMaxUnmap`|Latency max unmap|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyMaxWriteUs`|`latencyMaxWrite`|Latency (max, write)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyStddevUs`|`latencyStddev`|Latency Std Dev|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyStddevReadUs`|`latencyStddevRead`|Latency standard deviation (read)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyStddevSegCleanerUnmapUs`|`latencyStddevSegCleanerUnmap`|Latency standard deviation seg cleaner unmap|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyStddevUnmapUs`|`latencyStddevUnmap`|Unmap Latency Std Dev|microseconds|none|all|
|`vSAN Perf\|DOM Client\|latencyStddevWriteUs`|`latencyStddevWrite`|Latency standard deviation (write)|microseconds|none|all|
|`vSAN Perf\|DOM Client\|lessSkewedMultiBurstsPerSecond`|`lessSkewedMultiBurstsPerSecond`|Less skewed multi bursts per second|none stated|none|all|
|`vSAN Perf\|DOM Client\|numBurstStatsSkipped`|`numBurstStatsSkipped`|Num burst stats skipped|none stated|none|all|
|`vSAN Perf\|DOM Client\|numOio`|`numOio`|Num outstanding IO|none stated|none|all|
|`vSAN Perf\|DOM Client\|oioRead`|`oioRead`|Outstanding IO (read)|none stated|none|all|
|`vSAN Perf\|DOM Client\|oioRecoveryWrite`|`oioRecoveryWrite`|Outstanding IO recovery (write)|none stated|none|all|
|`vSAN Perf\|DOM Client\|oioResyncRead`|`oioResyncRead`|Outstanding IO resync (read)|none stated|none|all|
|`vSAN Perf\|DOM Client\|oioWrite`|`oioWrite`|Outstanding IO (write)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsBadParam`|`rateClientOpsBadParam`|Client ops bad param (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsChecksumMismatch`|`rateClientOpsChecksumMismatch`|Client ops checksum mismatch (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsIoError`|`rateClientOpsIoError`|Client ops IO error (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsLimitExceed`|`rateClientOpsLimitExceed`|Client ops limit exceed (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsNoConnect`|`rateClientOpsNoConnect`|Client ops no connect (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsNoMem`|`rateClientOpsNoMem`|Client ops no mem (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsNoSpace`|`rateClientOpsNoSpace`|Client ops no space (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsNotSupported`|`rateClientOpsNotSupported`|Client ops not supported (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateClientOpsRetry`|`rateClientOpsRetry`|Client ops retry (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|rateOtherClientOpsFailure`|`rateOtherClientOpsFailure`|Other client ops failure (rate)|none stated|none|all|
|`vSAN Perf\|DOM Client\|readCongestion`|`readCongestion`|Read Congestions|none stated|none|all|
|`vSAN Perf\|DOM Client\|readCount`|`readCount`|Read count|none stated|none|all|
|`vSAN Perf\|DOM Client\|segCleanerUnmapCongestion`|`segCleanerUnmapCongestion`|Seg cleaner unmap congestion|none stated|none|all|
|`vSAN Perf\|DOM Client\|segCleanerUnmapLeafOwnerLatencyUs`|`segCleanerUnmapLeafOwnerLatency`|Seg cleaner unmap leaf owner latency|microseconds|none|all|
|`vSAN Perf\|DOM Client\|segCleanerUnmapLeafOwnerMaxLatencyAvgUs`|`segCleanerUnmapLeafOwnerMaxLatencyAvgUs`|Seg cleaner unmap leaf owner max latency avg us|microseconds|none|all|
|`vSAN Perf\|DOM Client\|serverSwitchAvglatencyUs`|`serverSwitchAvglatency`|Server switch average latency|microseconds|none|all|
|`vSAN Perf\|DOM Client\|serverSwitchCount`|`serverSwitchCount`|Server switch count|none stated|none|all|
|`vSAN Perf\|DOM Client\|serverSwitchLatencyMaxUs`|`serverSwitchLatencyMaxUs`|Server switch latency max us|microseconds|none|all|
|`vSAN Perf\|DOM Client\|throughputBytesPerSec`|`throughput`|Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Client\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Client\|throughputSegCleanerUnmapBytesPerSec`|`throughputSegCleanerUnmap`|Throughput seg cleaner unmap|bytes/s|none|all|
|`vSAN Perf\|DOM Client\|throughputUnmapBytesPerSec`|`throughputUnmap`|Unmap Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Client\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Client\|unmapCongestion`|`unmapCongestion`|Unmap congestion|none stated|none|all|
|`vSAN Perf\|DOM Client\|unmapCount`|`unmapCount`|Unmap count|none stated|none|all|
|`vSAN Perf\|DOM Client\|writeCongestion`|`writeCongestion`|Write Congestions|none stated|none|all|
|`vSAN Perf\|DOM Client\|writeCount`|`writeCount`|Write count|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 390 catalog series.

### pNIC (`vsan-pnic-net`): 41 keys per vSAN physical NIC (17 at dashboard scope)

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|pNIC:<vmnic>\|ioChainDrops`|`ioChainDrops`|IO chain drops|none stated|none|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|ioChainRxdrops`|`ioChainRxdrops`|IO chain RX drops|none stated|none|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|ioChainTxdrops`|`ioChainTxdrops`|IO chain TX drops|none stated|none|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|pauseCountPct`|`pauseCount`|pNic 802.3x Pause Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|pfcCountRaw`|`pfcCountRaw`|PFC count raw|none stated|none|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|portRxDropsPct`|`portRxDrops`|Inbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|portTxDropsPct`|`portTxDrops`|Outbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|portTxpkts`|`portTxpkts`|Port TX packets|none stated|none|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|rxCrcErrPct`|`rxCrcErr`|pNIC RX CRC Error|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|rxCrcErrRaw`|`rxCrcErrRaw`|RX CRC errors raw|none stated|none|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|rxErrPct`|`rxErr`|pNIC RX Generic Error|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|rxFifoErrPct`|`rxFifoErr`|pNIC RX FIFO Error|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|rxMissErrPct`|`rxMissErr`|pNIC RX missed error (ring buffer full)|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|rxMissErrRaw`|`rxMissErrRaw`|pNIC RX missed error (ring buffer full) (raw)|none stated|none|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|rxOvErrPct`|`rxOvErr`|pNIC RX Buffer Overflow Error|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|txCarErrPct`|`txCarErr`|pNIC TX Carrier Error|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|txErrPct`|`txErr`|pNIC TX Generic Error|percent|per-mille / 10|dashboard|
|`vSAN Perf\|pNIC:<vmnic>\|pauseCountRaw`|`pauseCountRaw`|Pause count raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|pfcCount`|`pfcCount`|PFC count|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|portRxpkts`|`portRxpkts`|Port RX packets|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxDrp`|`rxDrp`|RX dropped|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxErrRaw`|`rxErrRaw`|RX errors raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxFifoErrRaw`|`rxFifoErrRaw`|RX FIFO errors raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxFrmErr`|`rxFrmErr`|RX frame alignment errors|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxLgtErr`|`rxLgtErr`|RX length errors|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxOvErrRaw`|`rxOvErrRaw`|RX overrun errors raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxPackets`|`rxPackets`|Inbound Packets Per Second|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxPacketsLossRatePct`|`rxPacketsLossRate`|Inbound Packet Discard Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxPktRaw`|`rxPktRaw`|RX packet raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|rxThroughputBytesPerSec`|`rxThroughput`|Throughput Inbound|bytes/s|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txAbortErr`|`txAbortErr`|TX aborted errors|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txCarErrRaw`|`txCarErrRaw`|TX carrier errors raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txDrp`|`txDrp`|TX dropped|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txErrRaw`|`txErrRaw`|TX errors raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txFifoErr`|`txFifoErr`|TX FIFO errors|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txHeartErr`|`txHeartErr`|TX heartbeat errors|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txPackets`|`txPackets`|Outbound Packets Per Second|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txPacketsLossRatePct`|`txPacketsLossRate`|Outbound Packet Discard Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|pNIC:<vmnic>\|txPktRaw`|`txPktRaw`|TX packet raw|none stated|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txThroughputBytesPerSec`|`txThroughput`|Throughput Outbound|bytes/s|none|all|
|`vSAN Perf\|pNIC:<vmnic>\|txWinErr`|`txWinErr`|TX window errors|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 10 entities, 410 catalog series.

## Keys on VMWARE HostSystem: host tier (`tier_host`, 149 keys; 739 under `all`)

Instances: `<stack>` is the TCP/IP stack name (`defaultTcpipStack`), one per
netstack; `<vmk>` is the vSAN VMkernel NIC (`vmk2`), one per vSAN vmknic.
`vsan-vnic-net` ids carry the stack too (`<node>|<stack>|<vmk>`); only the
vmk is in the key. On the lab: 141 series per host.

### CMMDS Network (`cmmds-net`): 10 keys per host

CMMDS (cluster membership directory) traffic on the host: group and RDT receive/transmit counts and throughput.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|CMMDS Network\|groupRx`|`groupRx`|Group Inbound|none stated|none|dashboard|
|`vSAN Perf\|CMMDS Network\|groupRxThroughputBytesPerSec`|`groupRxThroughput`|Group Inbound Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|CMMDS Network\|groupTxMcast`|`groupTxMcast`|Group Outbound Multicast|none stated|none|dashboard|
|`vSAN Perf\|CMMDS Network\|groupTxMcastThroughputBytesPerSec`|`groupTxMcastThroughput`|Group Outbound Multicast Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|CMMDS Network\|groupTxUcast`|`groupTxUcast`|Group Outbound Unicast|none stated|none|dashboard|
|`vSAN Perf\|CMMDS Network\|groupTxUcastThroughputBytesPerSec`|`groupTxUcastThroughput`|Group Outbound Unicast Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|CMMDS Network\|rdtRx`|`rdtRx`|Read Inbound|none stated|none|dashboard|
|`vSAN Perf\|CMMDS Network\|rdtRxThroughputBytesPerSec`|`rdtRxThroughput`|Read Inbound Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|CMMDS Network\|rdtTx`|`rdtTx`|Read Outbound|none stated|none|dashboard|
|`vSAN Perf\|CMMDS Network\|rdtTxThroughputBytesPerSec`|`rdtTxThroughput`|Read Outbound Throughput|bytes/s|none|dashboard|

### CPU (`vsan-cpu`): 7 keys per host

CPU used by vSAN worlds on the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 35 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|CPU\|avgRunPct`|`avgRunPct`|Run percent (average)|percent|none|dashboard|
|`vSAN Perf\|CPU\|avgUsedPct`|`avgUsedPct`|Used percent (average)|percent|none|dashboard|
|`vSAN Perf\|CPU\|maxRunPct`|`maxRunPct`|Run percent (max)|percent|none|dashboard|
|`vSAN Perf\|CPU\|maxUsedPct`|`maxUsedPct`|Used percent (max)|percent|none|dashboard|
|`vSAN Perf\|CPU\|readyPct`|`readyPct`|Percentage of Ready CPU Time|percent|none|dashboard|
|`vSAN Perf\|CPU\|runPct`|`runPct`|Percentage of Running CPU Time|percent|none|dashboard|
|`vSAN Perf\|CPU\|usedPct`|`usedPct`|Percentage of Used CPU Time|percent|none|dashboard|

### vSAN Direct (`vsan-direct-host`): 8 keys per host

vSAN Direct on the host. Empty on the lab; entity id form unobserved (Python pack models it on `host_uuid`, provisional).

Lands on: HostSystem. Instance: none (if an observed id carries a second part, it is skipped and counted, counted in `Collection|entities_unparsed`). Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|vSAN Direct\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|vSAN Direct\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|vSAN Direct\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|vSAN Direct\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|vSAN Direct\|oioDevRead`|`oioDevRead`|Read Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|vSAN Direct\|oioDevWrite`|`oioDevWrite`|Write Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|vSAN Direct\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|vSAN Direct\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none|dashboard|

### DOM Comp Mgr (`host-domcompmgr`): 82 keys per host (16 at dashboard scope)

DOM component manager role on the host: the layer that serves component I/O, including resync reads and recovery writes.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 80 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|DOM Comp Mgr\|componentCongestion`|`componentCongestion`|Component congestion|none stated|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyMaxReadUs`|`latencyMaxRead`|Latency (max, read)|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyMaxWriteUs`|`latencyMaxWrite`|Latency (max, write)|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyStddevWriteUs`|`latencyStddevWrite`|Latency standard deviation (write)|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|namespaceDispatchedCostWriteBytesPerSec`|`namespaceDispatchedCostWrite`|Namespace dispatched cost (write)|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|resyncReadCongestion`|`resyncReadCongestion`|Resync read congestion|none stated|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|sharedCongestion`|`sharedCongestion`|Shared congestion|none stated|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|vmdiskDispatchedCostWriteBytesPerSec`|`vmdiskDispatchedCostWrite`|VM disk dispatched cost (write)|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|vmdiskQueueDepthWrite`|`vmdiskQueueDepthWrite`|VM disk queue depth (write)|none stated|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|congestion`|`congestion`|Congestions|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|ioCount`|`ioCount`|IO count|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|iops`|`iops`|IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|iopsRecUnmap`|`iopsRecUnmap`|Recovery Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|iopsRecWrite`|`iopsRecWrite`|Recovery Write IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|iopsResyncRead`|`iopsResyncRead`|Resync Read IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latAvgResyncReadUs`|`latAvgResyncRead`|Resync Read Latency|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgUs`|`latencyAvg`|Latency (average)|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgRecUnmapUs`|`latencyAvgRecUnmap`|Recovery Unmap Latency|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgRecWriteUs`|`latencyAvgRecWrite`|Recovery Write Latency|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgServiceVMReadUs`|`latencyAvgServiceVMRead`|Latency avg service VM (read)|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgServiceVMWriteUs`|`latencyAvgServiceVMWrite`|Latency avg service VM (write)|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyMaxUnmapUs`|`latencyMaxUnmap`|Latency max unmap|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyStddevUs`|`latencyStddev`|Latency Std Dev|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyStddevReadUs`|`latencyStddevRead`|Latency standard deviation (read)|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|latencyStddevUnmapUs`|`latencyStddevUnmap`|Unmap Latency Std Dev|microseconds|none|all|
|`vSAN Perf\|DOM Comp Mgr\|metadataDispatchedCostReadBytesPerSec`|`metadataDispatchedCostRead`|Metadata dispatched cost (read)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|metadataDispatchedCostWriteBytesPerSec`|`metadataDispatchedCostWrite`|Metadata dispatched cost (write)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|metadataQueueDepthRead`|`metadataQueueDepthRead`|Metadata queue depth (read)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|metadataQueueDepthWrite`|`metadataQueueDepthWrite`|Metadata queue depth (write)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|namespaceDispatchedCostReadBytesPerSec`|`namespaceDispatchedCostRead`|Namespace dispatched cost (read)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|namespaceQueueDepthRead`|`namespaceQueueDepthRead`|Namespace queue depth (read)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|namespaceQueueDepthWrite`|`namespaceQueueDepthWrite`|Namespace queue depth (write)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|numOio`|`numOio`|Num outstanding IO|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|oioRead`|`oioRead`|Outstanding IO (read)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|oioRecoveryWrite`|`oioRecoveryWrite`|Outstanding IO recovery (write)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|oioResyncRead`|`oioResyncRead`|Outstanding IO resync (read)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|oioWrite`|`oioWrite`|Outstanding IO (write)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsBadParam`|`rateCompmgrOpsBadParam`|Compmgr ops bad param (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsChecksumMismatch`|`rateCompmgrOpsChecksumMismatch`|Compmgr ops checksum mismatch (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsIoError`|`rateCompmgrOpsIoError`|Compmgr ops IO error (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsLimitExceed`|`rateCompmgrOpsLimitExceed`|Compmgr ops limit exceed (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsNoConnect`|`rateCompmgrOpsNoConnect`|Compmgr ops no connect (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsNoMem`|`rateCompmgrOpsNoMem`|Compmgr ops no mem (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsNoSpace`|`rateCompmgrOpsNoSpace`|Compmgr ops no space (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsNotSupported`|`rateCompmgrOpsNotSupported`|Compmgr ops not supported (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateCompmgrOpsRetry`|`rateCompmgrOpsRetry`|Compmgr ops retry (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|rateOtherCompmgrOpsFailure`|`rateOtherCompmgrOpsFailure`|Other compmgr ops failure (rate)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|readCongestion`|`readCongestion`|Read Congestions|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|readCount`|`readCount`|Read count|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|recUnmapCongestion`|`recUnmapCongestion`|Recovery unmap congestion|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|recUnmapCount`|`recUnmapCount`|Recovery unmap count|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|recWriteCongestion`|`recWriteCongestion`|Recovery write congestion|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|recWriteCount`|`recWriteCount`|Recovery write count|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|regulatorIopsRead`|`regulatorIopsRead`|Regulator IOPS (read)|IO/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|regulatorIopsWrite`|`regulatorIopsWrite`|Regulator IOPS (write)|IO/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|resyncDispatchedCostReadBytesPerSec`|`resyncDispatchedCostRead`|Resync dispatched cost (read)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|resyncDispatchedCostWriteBytesPerSec`|`resyncDispatchedCostWrite`|Resync dispatched cost (write)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|resyncQueueDepthRead`|`resyncQueueDepthRead`|Resync queue depth (read)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|resyncQueueDepthWrite`|`resyncQueueDepthWrite`|Resync queue depth (write)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|resyncReadCount`|`resyncReadCount`|Resync read count|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|throughputBytesPerSec`|`throughput`|Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|throughputRecUnmapBytesPerSec`|`throughputRecUnmap`|Recovery Unmap Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|throughputRecWriteBytesPerSec`|`throughputRecWrite`|Recovery Write Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|throughputUnmapBytesPerSec`|`throughputUnmap`|Unmap Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|tputResyncReadBytesPerSec`|`tputResyncRead`|Resync Read Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|tputServiceVMReadBytesPerSec`|`tputServiceVMRead`|Throughput service VM (read)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|tputServiceVMWriteBytesPerSec`|`tputServiceVMWrite`|Throughput service VM (write)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|unmapCongestion`|`unmapCongestion`|Unmap congestion|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|unmapCount`|`unmapCount`|Unmap count|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|vmdiskDispatchedCostReadBytesPerSec`|`vmdiskDispatchedCostRead`|VM disk dispatched cost (read)|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|vmdiskQueueDepthRead`|`vmdiskQueueDepthRead`|VM disk queue depth (read)|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|writeCongestion`|`writeCongestion`|Write Congestions|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|writeCount`|`writeCount`|Write count|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 410 catalog series.

### ESA Dedup DOM Client (`host-esa-dedup-domclient-io`): 42 keys per host (24 at dashboard scope)

ESA global deduplication I/O seen from this host's DOM client.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 120 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCongestion`|`dedupChunkReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkRecoveryUnmapCongestion`|`dedupChunkRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkRecoveryWriteCongestion`|`dedupChunkRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkResyncReadCongestion`|`dedupChunkResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkUnmapCongestion`|`dedupChunkUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkUnmapCount`|`dedupChunkUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCongestion`|`dedupChunkWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCongestion`|`dedupEngineReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCongestion`|`dedupEngineWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCongestion`|`dedupHashReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashRecoveryUnmapCongestion`|`dedupHashRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashRecoveryWriteCongestion`|`dedupHashRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashResyncReadCongestion`|`dedupHashResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashUnmapCongestion`|`dedupHashUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashUnmapCount`|`dedupHashUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCongestion`|`dedupHashWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjUnmap`|`iopsDedupChunkObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjUnmap`|`iopsDedupHashObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 210 catalog series.

### Host Network (`vsan-host-net`): 16 keys per host (8 at dashboard scope)

vSAN network traffic of the host as a whole (all vSAN vmknics).

Lands on: HostSystem. Instance: none. Lab: 5 entities, 40 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Host Network\|portRxDropsPct`|`portRxDrops`|Inbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|dashboard|
|`vSAN Perf\|Host Network\|portTxDropsPct`|`portTxDrops`|Outbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|dashboard|
|`vSAN Perf\|Host Network\|rxPackets`|`rxPackets`|Inbound Packets Per Second|none stated|none|dashboard|
|`vSAN Perf\|Host Network\|rxPacketsLossRatePct`|`rxPacketsLossRate`|Inbound Packet Discard Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|Host Network\|rxThroughputBytesPerSec`|`rxThroughput`|Throughput Inbound|bytes/s|none|dashboard|
|`vSAN Perf\|Host Network\|txPackets`|`txPackets`|Outbound Packets Per Second|none stated|none|dashboard|
|`vSAN Perf\|Host Network\|txPacketsLossRatePct`|`txPacketsLossRate`|Outbound Packet Discard Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|Host Network\|txThroughputBytesPerSec`|`txThroughput`|Throughput Outbound|bytes/s|none|dashboard|
|`vSAN Perf\|Host Network\|ioChainRxdrops`|`ioChainRxdrops`|IO chain RX drops|none stated|none|all|
|`vSAN Perf\|Host Network\|ioChainTxdrops`|`ioChainTxdrops`|IO chain TX drops|none stated|none|all|
|`vSAN Perf\|Host Network\|portRxpkts`|`portRxpkts`|Port RX packets|none stated|none|all|
|`vSAN Perf\|Host Network\|portTxpkts`|`portTxpkts`|Port TX packets|none stated|none|all|
|`vSAN Perf\|Host Network\|tcpRxErrRate`|`tcpRxErrRate`|TCP RX errors (rate)|none stated|none|all|
|`vSAN Perf\|Host Network\|tcpRxPackets`|`tcpRxPackets`|TCP Inbound Packets|none stated|none|all|
|`vSAN Perf\|Host Network\|tcpTxPackets`|`tcpTxPackets`|TCP Outbound Packets|none stated|none|all|
|`vSAN Perf\|Host Network\|tcpTxRexmitRate`|`tcpTxRexmitRate`|TCP TX retransmit (rate)|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 80 catalog series.

### ZDOM Top Stats (`host-zdom-top-stats`): 19 keys per host (11 at dashboard scope)

ESA zDOM top-level I/O on the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 55 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ZDOM Top Stats\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|oioWrite`|`oioWrite`|Outstanding IO (write)|none stated|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|readLatencyMaxUs`|`readLatencyMaxUs`|Read latency max us|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|unmapLatencyMaxUs`|`unmapLatencyMaxUs`|Unmap latency max us|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|writeLatencyMaxUs`|`writeLatencyMaxUs`|Write latency max us|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|ioCount`|`ioCount`|IO count|none stated|none|all|
|`vSAN Perf\|ZDOM Top Stats\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|all|
|`vSAN Perf\|ZDOM Top Stats\|oioRead`|`oioRead`|Outstanding IO (read)|none stated|none|all|
|`vSAN Perf\|ZDOM Top Stats\|readCount`|`readCount`|Read count|none stated|none|all|
|`vSAN Perf\|ZDOM Top Stats\|throughputUnmapBytesPerSec`|`throughputUnmap`|Unmap Throughput|bytes/s|none|all|
|`vSAN Perf\|ZDOM Top Stats\|unmapCount`|`unmapCount`|Unmap count|none stated|none|all|
|`vSAN Perf\|ZDOM Top Stats\|writeCount`|`writeCount`|Write count|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 95 catalog series.

### Memory (`vsan-memory`): 50 keys per host (12 at dashboard scope)

Memory reserved and consumed by the vSAN kernel modules and daemons on the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 60 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Memory\|clomdConsumedSizeBytes`|`clomdConsumedSize`|Clomd consumed size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|clomdReservedSizeBytes`|`clomdReservedSize`|Clomd reserved size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|cmmdsdConsumedSizeBytes`|`cmmdsdConsumedSize`|Cmmdsd consumed size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|cmmdsdReservedSizeBytes`|`cmmdsdReservedSize`|Cmmdsd reserved size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|epdConsumedSizeBytes`|`epdConsumedSize`|Epd consumed size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|kernelConsumedSize`|`kernelConsumedSize`|Kernel consumed size|none stated|none|dashboard|
|`vSAN Perf\|Memory\|kernelReservedSizeBytes`|`kernelReservedSize`|Kernel Reserved Memory Size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|osfsdConsumedSizeBytes`|`osfsdConsumedSize`|Osfsd consumed size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|uwConsumedSizeBytes`|`uwConsumedSize`|Uw consumed size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|uwReservedSizeBytes`|`uwReservedSize`|User world Reserved Memory Size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|vsandevicemonitordConsumedSizeBytes`|`vsandevicemonitordConsumedSize`|Vsandevicemonitord consumed size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|vsanmgmtdConsumedSizeBytes`|`vsanmgmtdConsumedSize`|Vsanmgmtd consumed size|bytes|none|dashboard|
|`vSAN Perf\|Memory\|clomdMemAllocMax`|`clomdMemAllocMax`|Clomd mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|clomdMemAllocMin`|`clomdMemAllocMin`|Clomd mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|cmmdsTimeMachineConsumedSizeBytes`|`cmmdsTimeMachineConsumedSize`|CMMDS time machine consumed size|bytes|none|all|
|`vSAN Perf\|Memory\|cmmdsTimeMachineMemAllocMax`|`cmmdsTimeMachineMemAllocMax`|CMMDS time machine mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|cmmdsTimeMachineMemAllocMin`|`cmmdsTimeMachineMemAllocMin`|CMMDS time machine mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|cmmdsTimeMachineReservedSizeBytes`|`cmmdsTimeMachineReservedSize`|CMMDS time machine reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|cmmdsdMemAllocMax`|`cmmdsdMemAllocMax`|Cmmdsd mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|cmmdsdMemAllocMin`|`cmmdsdMemAllocMin`|Cmmdsd mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|epdMemAllocMax`|`epdMemAllocMax`|Epd mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|epdMemAllocMin`|`epdMemAllocMin`|Epd mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|epdReservedSizeBytes`|`epdReservedSize`|Epd reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|osfsdMemAllocMax`|`osfsdMemAllocMax`|Osfsd mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|osfsdMemAllocMin`|`osfsdMemAllocMin`|Osfsd mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|osfsdReservedSizeBytes`|`osfsdReservedSize`|Osfsd reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|uwMemAllocMax`|`uwMemAllocMax`|Uw mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|uwMemAllocMin`|`uwMemAllocMin`|Uw mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|vitdConsumedSizeBytes`|`vitdConsumedSize`|Vitd consumed size|bytes|none|all|
|`vSAN Perf\|Memory\|vitdMemAllocMax`|`vitdMemAllocMax`|Vitd mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|vitdMemAllocMin`|`vitdMemAllocMin`|Vitd mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|vitdReservedSizeBytes`|`vitdReservedSize`|Vitd reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|vsandevicemonitordMemAllocMax`|`vsandevicemonitordMemAllocMax`|Vsandevicemonitord mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|vsandevicemonitordMemAllocMin`|`vsandevicemonitordMemAllocMin`|Vsandevicemonitord mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|vsandevicemonitordReservedSizeBytes`|`vsandevicemonitordReservedSize`|Vsandevicemonitord reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|vsanmgmtdMemAllocMax`|`vsanmgmtdMemAllocMax`|Vsanmgmtd mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|vsanmgmtdMemAllocMin`|`vsanmgmtdMemAllocMin`|Vsanmgmtd mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|vsanmgmtdReservedSizeBytes`|`vsanmgmtdReservedSize`|Vsanmgmtd reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|vsanmgmtdWatchdogConsumedSizeBytes`|`vsanmgmtdWatchdogConsumedSize`|Vsanmgmtd watchdog consumed size|bytes|none|all|
|`vSAN Perf\|Memory\|vsanmgmtdWatchdogMemAllocMax`|`vsanmgmtdWatchdogMemAllocMax`|Vsanmgmtd watchdog mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|vsanmgmtdWatchdogMemAllocMin`|`vsanmgmtdWatchdogMemAllocMin`|Vsanmgmtd watchdog mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|vsanmgmtdWatchdogReservedSizeBytes`|`vsanmgmtdWatchdogReservedSize`|Vsanmgmtd watchdog reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|vsanobserverConsumedSizeBytes`|`vsanobserverConsumedSize`|Vsanobserver consumed size|bytes|none|all|
|`vSAN Perf\|Memory\|vsanobserverMemAllocMax`|`vsanobserverMemAllocMax`|Vsanobserver mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|vsanobserverMemAllocMin`|`vsanobserverMemAllocMin`|Vsanobserver mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|vsanobserverReservedSizeBytes`|`vsanobserverReservedSize`|Vsanobserver reserved size|bytes|none|all|
|`vSAN Perf\|Memory\|vsantracedConsumedSizeBytes`|`vsantracedConsumedSize`|Vsantraced consumed size|bytes|none|all|
|`vSAN Perf\|Memory\|vsantracedMemAllocMax`|`vsantracedMemAllocMax`|Vsantraced mem alloc (max)|none stated|none|all|
|`vSAN Perf\|Memory\|vsantracedMemAllocMin`|`vsantracedMemAllocMin`|Vsantraced mem alloc (min)|none stated|none|all|
|`vSAN Perf\|Memory\|vsantracedReservedSizeBytes`|`vsantracedReservedSize`|Vsantraced reserved size|bytes|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 250 catalog series.

### RDT Latency (`rdt-network-latency`): 17 keys per host (11 at dashboard scope)

RDT latency and socket-buffer figures for the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 55 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|RDT Latency\|avgLatencyUs`|`avgLatency`|RDT Network VNIC Average Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|kaReset`|`kaReset`|Keepalive reset|none stated|none|dashboard|
|`vSAN Perf\|RDT Latency\|maxLatencyUs`|`maxLatency`|RDT Network Cluster Max Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|minLatencyUs`|`minLatency`|RDT Network Cluster Min Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|numReadyDelay`|`numReadyDelay`|Num ready delay|none stated|none|dashboard|
|`vSAN Perf\|RDT Latency\|rxSbSpaceMaxBytes`|`rxSbSpaceMax`|RDT Network Socket Max Inbound Bytes|bytes|none|dashboard|
|`vSAN Perf\|RDT Latency\|rxSbSpaceMinBytes`|`rxSbSpaceMin`|RDT Network Socket Min Inbound Bytes|bytes|none|dashboard|
|`vSAN Perf\|RDT Latency\|txCtxQMaxBytes`|`txCtxQMax`|RDT Network Max Outbound Context Bytes|bytes|none|dashboard|
|`vSAN Perf\|RDT Latency\|txQLatAvgUs`|`txQLatAvg`|RDT Network Average Outbound Queueing Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|txQLatMaxUs`|`txQLatMax`|RDT Network Max Outbound Queueing Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|txSbSpaceMinBytes`|`txSbSpaceMin`|RDT Network Socket Min Outbound Bytes|bytes|none|dashboard|
|`vSAN Perf\|RDT Latency\|rxSbSpaceAvgBytes`|`rxSbSpaceAvg`|RDT Network Socket Average Inbound Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txCtxQAvgBytes`|`txCtxQAvg`|RDT Network Average Outbound Context Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txCtxQMinBytes`|`txCtxQMin`|RDT Network Min Outbound Context Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txQLatMinUs`|`txQLatMin`|RDT Network Min Outbound Queueing Latency|microseconds|none|all|
|`vSAN Perf\|RDT Latency\|txSbSpaceAvgBytes`|`txSbSpaceAvg`|RDT Network Socket Average Outbound Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txSbSpaceMaxBytes`|`txSbSpaceMax`|RDT Network Socket Max Outbound Bytes|bytes|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 85 catalog series.

### TCP/IP:<stack> (`vsan-tcpip-stats`): 22 keys per netstack (12 at dashboard scope)

TCP/IP stack statistics for the stack carrying vSAN, one instance per stack.

Lands on: HostSystem. Instance: `<stack>` = the netstack name from the entity id (`defaultTcpipStack` on the lab). Lab: 5 entities, 60 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|TCP/IP:<stack>\|tcpErrsPct`|`tcpErrs`|TCP Errors|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpHalfopenDropRatePct`|`tcpHalfopenDropRate`|Half Open Drop Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRcvdupackRatePct`|`tcpRcvdupackRate`|Received Duplicate Acknowledge Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRcvduppackRatePct`|`tcpRcvduppackRate`|Received Duplicate Packets Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRcvoopackRatePct`|`tcpRcvoopackRate`|Received Out-of-order Packets Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRxErrRate`|`tcpRxErrRate`|TCP RX errors (rate)|none stated|none|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSackRcvBlocksRatePct`|`tcpSackRcvBlocksRate`|Sack Received Blocks Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSackRexmitsRatePct`|`tcpSackRexmitsRate`|Sack Rexmits Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSackSendBlocksRatePct`|`tcpSackSendBlocksRate`|Sack Send Blocks Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSndZeroWin`|`tcpSndZeroWin`|TCP Send Zero Window|none stated|none|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpTimeoutDropRatePct`|`tcpTimeoutDropRate`|Timeout Drop Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|tcpTxRexmitRate`|`tcpTxRexmitRate`|TCP TX retransmit (rate)|none stated|none|dashboard|
|`vSAN Perf\|TCP/IP:<stack>\|arpDropRate`|`arpDropRate`|ARP drop (rate)|none stated|none|all|
|`vSAN Perf\|TCP/IP:<stack>\|ip6ErrsPct`|`ip6Errs`|IPv6 Errors|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|TCP/IP:<stack>\|ip6Total`|`ip6Total`|IP6 (total)|none stated|none|all|
|`vSAN Perf\|TCP/IP:<stack>\|ipErrsPct`|`ipErrs`|IPv4 Errors|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|TCP/IP:<stack>\|ipTotal`|`ipTotal`|IP (total)|none stated|none|all|
|`vSAN Perf\|TCP/IP:<stack>\|tcpEcnCe`|`tcpEcnCe`|TCP ECN ce|none stated|none|all|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRxPackets`|`tcpRxPackets`|TCP Inbound Packets|none stated|none|all|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRxThroughputBytesPerSec`|`tcpRxThroughput`|TCP Inbound Throughout|bytes/s|none|all|
|`vSAN Perf\|TCP/IP:<stack>\|tcpTxPackets`|`tcpTxPackets`|TCP Outbound Packets|none stated|none|all|
|`vSAN Perf\|TCP/IP:<stack>\|tcpTxThroughputBytesPerSec`|`tcpTxThroughput`|TCP Outbound Throughout|bytes/s|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 110 catalog series.

### vNIC:<vmk> (`vsan-vnic-net`): 57 keys per vSAN vmknic (10 at dashboard scope)

vSAN traffic per vSAN vmknic.

Lands on: HostSystem. Instance: `<vmk>` = the vmknic name (`vmk2` on the lab); the stack part of the id is dropped from the key because a vmknic name is unique on its host (ESXi naming, inferred; the lab has one stack). Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|vNIC:<vmk>\|portRxDropsPct`|`portRxDrops`|Inbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|portRxpkts`|`portRxpkts`|Port RX packets|none stated|none|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|portTxDropsPct`|`portTxDrops`|Outbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|portTxpkts`|`portTxpkts`|Port TX packets|none stated|none|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|rxPackets`|`rxPackets`|Inbound Packets Per Second|none stated|none|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|rxPacketsLossRatePct`|`rxPacketsLossRate`|Inbound Packet Discard Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|rxThroughputBytesPerSec`|`rxThroughput`|Throughput Inbound|bytes/s|none|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|txPackets`|`txPackets`|Outbound Packets Per Second|none stated|none|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|txPacketsLossRatePct`|`txPacketsLossRate`|Outbound Packet Discard Rate|percent|per-mille / 10|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|txThroughputBytesPerSec`|`txThroughput`|Throughput Outbound|bytes/s|none|dashboard|
|`vSAN Perf\|vNIC:<vmk>\|arpDropRate`|`arpDropRate`|ARP drop (rate)|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|ioChainDrops`|`ioChainDrops`|IO chain drops|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|ioChainRxdrops`|`ioChainRxdrops`|IO chain RX drops|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|ioChainTxdrops`|`ioChainTxdrops`|IO chain TX drops|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|ip6ErrsPct`|`ip6Errs`|IPv6 Errors|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|ip6Total`|`ip6Total`|IP6 (total)|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|ipErrsPct`|`ipErrs`|IPv4 Errors|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|ipTotal`|`ipTotal`|IP (total)|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpBadrst`|`tcpBadrst`|TCP badrst|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpBadsyn`|`tcpBadsyn`|TCP badsyn|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpConndrops`|`tcpConndrops`|TCP conndrops|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpConnects`|`tcpConnects`|TCP connects|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpDrops`|`tcpDrops`|TCP drops|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpEcnCe`|`tcpEcnCe`|TCP ECN ce|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpErrsPct`|`tcpErrs`|TCP Errors|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpHalfopenDropRatePct`|`tcpHalfopenDropRate`|Half Open Drop Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpKeepdrops`|`tcpKeepdrops`|TCP keepdrops|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpKeeptimeo`|`tcpKeeptimeo`|TCP keeptimeo|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpPersisttimeo`|`tcpPersisttimeo`|TCP persisttimeo|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvackpack`|`tcpRcvackpack`|TCP received ackpack|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvbadoff`|`tcpRcvbadoff`|TCP received badoff|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvbadsum`|`tcpRcvbadsum`|TCP received badsum|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvdupack`|`tcpRcvdupack`|TCP received duplicate ACKs|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvdupackRatePct`|`tcpRcvdupackRate`|Received Duplicate Acknowledge Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvduppack`|`tcpRcvduppack`|TCP received duplicate packets|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvduppackRatePct`|`tcpRcvduppackRate`|Received Duplicate Packets Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvmemdrop`|`tcpRcvmemdrop`|TCP received memdrop|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvoopack`|`tcpRcvoopack`|TCP received out-of-order packets|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvoopackRatePct`|`tcpRcvoopackRate`|Received Out-of-order Packets Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRcvshort`|`tcpRcvshort`|TCP received short|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRexmttimeo`|`tcpRexmttimeo`|TCP rexmttimeo|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRxErrRate`|`tcpRxErrRate`|TCP RX errors (rate)|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRxPackets`|`tcpRxPackets`|TCP Inbound Packets|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpRxThroughputBytesPerSec`|`tcpRxThroughput`|TCP Inbound Throughout|bytes/s|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSackRcvBlocks`|`tcpSackRcvBlocks`|TCP SACK received blocks|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSackRcvBlocksRatePct`|`tcpSackRcvBlocksRate`|Sack Received Blocks Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSackRexmits`|`tcpSackRexmits`|TCP SACK retransmits|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSackRexmitsRatePct`|`tcpSackRexmitsRate`|Sack Rexmits Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSackSendBlocks`|`tcpSackSendBlocks`|TCP SACK send blocks|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSackSendBlocksRatePct`|`tcpSackSendBlocksRate`|Sack Send Blocks Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSndZeroWin`|`tcpSndZeroWin`|TCP Send Zero Window|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpSndacks`|`tcpSndacks`|TCP sent ACKS|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpTimeoutDropRatePct`|`tcpTimeoutDropRate`|Timeout Drop Rate|percent (served per mille)|per-mille / 10|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpTimeoutdrop`|`tcpTimeoutdrop`|TCP timeoutdrop|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpTxPackets`|`tcpTxPackets`|TCP Outbound Packets|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpTxRexmitRate`|`tcpTxRexmitRate`|TCP TX retransmit (rate)|none stated|none|all|
|`vSAN Perf\|vNIC:<vmk>\|tcpTxThroughputBytesPerSec`|`tcpTxThroughput`|TCP Outbound Throughout|bytes/s|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 285 catalog series.

### vNIC RDT Latency:<vmk> (`vnic-rdt-network-latency`): 17 keys per vSAN vmknic (10 at dashboard scope)

RDT latency per vSAN vmknic.

Lands on: HostSystem. Instance: `<vmk>` = the vmknic name. Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|avgLatencyUs`|`avgLatency`|RDT Network VNIC Average Latency|microseconds|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|kaReset`|`kaReset`|Keepalive reset|none stated|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|maxLatencyUs`|`maxLatency`|RDT Network Cluster Max Latency|microseconds|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|minLatencyUs`|`minLatency`|RDT Network Cluster Min Latency|microseconds|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|numReadyDelay`|`numReadyDelay`|Num ready delay|none stated|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|rxSbSpaceMinBytes`|`rxSbSpaceMin`|RDT Network Socket Min Inbound Bytes|bytes|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txCtxQMaxBytes`|`txCtxQMax`|RDT Network Max Outbound Context Bytes|bytes|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txQLatAvgUs`|`txQLatAvg`|RDT Network Average Outbound Queueing Latency|microseconds|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txQLatMaxUs`|`txQLatMax`|RDT Network Max Outbound Queueing Latency|microseconds|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txSbSpaceMinBytes`|`txSbSpaceMin`|RDT Network Socket Min Outbound Bytes|bytes|none|dashboard|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|rxSbSpaceAvgBytes`|`rxSbSpaceAvg`|RDT Network Socket Average Inbound Bytes|bytes|none|all|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|rxSbSpaceMaxBytes`|`rxSbSpaceMax`|RDT Network Socket Max Inbound Bytes|bytes|none|all|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txCtxQAvgBytes`|`txCtxQAvg`|RDT Network Average Outbound Context Bytes|bytes|none|all|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txCtxQMinBytes`|`txCtxQMin`|RDT Network Min Outbound Context Bytes|bytes|none|all|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txQLatMinUs`|`txQLatMin`|RDT Network Min Outbound Queueing Latency|microseconds|none|all|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txSbSpaceAvgBytes`|`txSbSpaceAvg`|RDT Network Socket Average Outbound Bytes|bytes|none|all|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txSbSpaceMaxBytes`|`txSbSpaceMax`|RDT Network Socket Max Outbound Bytes|bytes|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 85 catalog series.

### ZDOM VTX (`zdom-vtx`): 71 keys per host (10 at dashboard scope)

ESA zDOM transaction layer (cache references, bank flushes, transaction latency).

Lands on: HostSystem. Instance: none. Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ZDOM VTX\|checkpointWorkerWakeupMs`|`checkpointWorkerWakeupMs`|Checkpoint worker wakeup ms|milliseconds|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|latAvgTotalOpIOUs`|`latAvgTotalOpIO`|Latency avg total op IO|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|latAvgTxnBankUs`|`latAvgTxnBank`|Latency avg transaction bank|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|maxDiscUsedPct`|`maxDiscUsedPct`|Disc used percent (max)|percent|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|rateBankFlushTotalCacheRef`|`rateBankFlushTotalCacheRef`|Bank flush total cache ref (rate)|none stated|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|rateSegCleaningCtxDataTotalCacheRef`|`rateSegCleaningCtxDataTotalCacheRef`|Seg cleaning context data total cache ref (rate)|none stated|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|rateTotalCacheRef`|`rateTotalCacheRef`|Cache ref (rate, total)|none stated|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|rateTxnLookupCacheRef`|`rateTxnLookupCacheRef`|Transaction lookup cache ref (rate)|none stated|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchTotalCacheRef`|`rateTxnPrefetchTotalCacheRef`|Transaction prefetch total cache ref (rate)|none stated|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|rateTxnReadWrite`|`rateTxnReadWrite`|Transaction (read, write, rate)|none stated|none|dashboard|
|`vSAN Perf\|ZDOM VTX\|allOpCacheMaxGetLatUSec`|`allOpCacheMaxGetLatUSec`|All op cache max get latency u sec|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|cacheMissPerLookupTxn`|`cacheMissPerLookupTxn`|Cache miss per lookup transaction|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|cacheMissPerPrefetchTxn`|`cacheMissPerPrefetchTxn`|Cache miss per prefetch transaction|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|cacheMissPerSegCleaningCtxDataTxn`|`cacheMissPerSegCleaningCtxDataTxn`|Cache miss per seg cleaning context data transaction|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|iopsCacheMissRatePct`|`iopsCacheMissRate`|Cache Miss Per IOPS|percent|none|all|
|`vSAN Perf\|ZDOM VTX\|latAvgCacheGetUs`|`latAvgCacheGet`|Latency avg cache get|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|latAvgTxnBankTotalIOUs`|`latAvgTxnBankTotalIO`|Latency avg transaction bank total IO|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|latAvgTxnUnmapUs`|`latAvgTxnUnmap`|Latency avg transaction unmap|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|latAvgTxnUnmapTotalIOUs`|`latAvgTxnUnmapTotalIO`|Latency avg transaction unmap total IO|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|missesPerIO`|`missesPerIO`|Misses per IO|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|prefetchCacheMaxGetLatUs`|`prefetchCacheMaxGetLatUs`|Prefetch cache max get latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTotDirtyCachePage`|`rateTotDirtyCachePage`|Tot dirty cache page (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTotalBitmapCacheMiss`|`rateTotalBitmapCacheMiss`|Bitmap cache miss (rate, total)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTotalCacheMiss`|`rateTotalCacheMiss`|Cache miss (rate, total)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTotalLogicalTreeCacheMiss`|`rateTotalLogicalTreeCacheMiss`|Logical tree cache miss (rate, total)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTotalMiddleTreeCacheMiss`|`rateTotalMiddleTreeCacheMiss`|Middle tree cache miss (rate, total)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTotalSnapTreeCacheMiss`|`rateTotalSnapTreeCacheMiss`|Snap tree cache miss (rate, total)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTotalSutCacheMiss`|`rateTotalSutCacheMiss`|Sut cache miss (rate, total)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankLogicalTreeCacheMiss`|`rateTxnBankLogicalTreeCacheMiss`|Transaction bank logical tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankLogicalTreeCacheRef`|`rateTxnBankLogicalTreeCacheRef`|Transaction bank logical tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankMiddleTreeCacheMiss`|`rateTxnBankMiddleTreeCacheMiss`|Transaction bank middle tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankMiddleTreeCacheRef`|`rateTxnBankMiddleTreeCacheRef`|Transaction bank middle tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankSutCacheMiss`|`rateTxnBankSutCacheMiss`|Transaction bank sut cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankSutCacheRef`|`rateTxnBankSutCacheRef`|Transaction bank sut cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankTotalCacheMiss`|`rateTxnBankTotalCacheMiss`|Transaction bank total cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnBankTotalCacheRef`|`rateTxnBankTotalCacheRef`|Transaction bank total cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnLookupCacheMiss`|`rateTxnLookupCacheMiss`|Transaction lookup cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnLookupLogicalTreeCacheMiss`|`rateTxnLookupLogicalTreeCacheMiss`|Transaction lookup logical tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnLookupLogicalTreeCacheRef`|`rateTxnLookupLogicalTreeCacheRef`|Transaction lookup logical tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnLookupMiddleTreeCacheMiss`|`rateTxnLookupMiddleTreeCacheMiss`|Transaction lookup middle tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnLookupMiddleTreeCacheRef`|`rateTxnLookupMiddleTreeCacheRef`|Transaction lookup middle tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchLogicalTreeCacheMiss`|`rateTxnPrefetchLogicalTreeCacheMiss`|Transaction prefetch logical tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchLogicalTreeCacheRef`|`rateTxnPrefetchLogicalTreeCacheRef`|Transaction prefetch logical tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchMiddleTreeCacheMiss`|`rateTxnPrefetchMiddleTreeCacheMiss`|Transaction prefetch middle tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchMiddleTreeCacheRef`|`rateTxnPrefetchMiddleTreeCacheRef`|Transaction prefetch middle tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchSutCacheMiss`|`rateTxnPrefetchSutCacheMiss`|Transaction prefetch sut cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchSutCacheRef`|`rateTxnPrefetchSutCacheRef`|Transaction prefetch sut cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchTotalCacheMiss`|`rateTxnPrefetchTotalCacheMiss`|Transaction prefetch total cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataLogicalTreeCacheMiss`|`rateTxnSegCleaningCtxDataLogicalTreeCacheMiss`|Transaction seg cleaning context data logical tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataLogicalTreeCacheRef`|`rateTxnSegCleaningCtxDataLogicalTreeCacheRef`|Transaction seg cleaning context data logical tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataMiddleTreeCacheMiss`|`rateTxnSegCleaningCtxDataMiddleTreeCacheMiss`|Transaction seg cleaning context data middle tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataMiddleTreeCacheRef`|`rateTxnSegCleaningCtxDataMiddleTreeCacheRef`|Transaction seg cleaning context data middle tree cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataSutCacheMiss`|`rateTxnSegCleaningCtxDataSutCacheMiss`|Transaction seg cleaning context data sut cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataSutCacheRef`|`rateTxnSegCleaningCtxDataSutCacheRef`|Transaction seg cleaning context data sut cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataTotalCacheMiss`|`rateTxnSegCleaningCtxDataTotalCacheMiss`|Transaction seg cleaning context data total cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnSegCleaningCtxDataTotalCacheRef`|`rateTxnSegCleaningCtxDataTotalCacheRef`|Transaction seg cleaning context data total cache ref (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnUnmapLogicalTreeCacheMiss`|`rateTxnUnmapLogicalTreeCacheMiss`|Transaction unmap logical tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnUnmapMiddleTreeCacheMiss`|`rateTxnUnmapMiddleTreeCacheMiss`|Transaction unmap middle tree cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnUnmapSutCacheMiss`|`rateTxnUnmapSutCacheMiss`|Transaction unmap sut cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|rateTxnUnmapTotalCacheMiss`|`rateTxnUnmapTotalCacheMiss`|Transaction unmap total cache miss (rate)|none stated|none|all|
|`vSAN Perf\|ZDOM VTX\|tputCacheMissRatePct`|`tputCacheMissRate`|Cache Miss Per Throughput|percent|none|all|
|`vSAN Perf\|ZDOM VTX\|txnBankMaxLatUs`|`txnBankMaxLatUs`|Transaction bank max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnCacheFlushMaxLatUs`|`txnCacheFlushMaxLatUs`|Transaction cache flush max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnDLogMaxLatUs`|`txnDLogMaxLatUs`|Transaction d log max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnDelExtMiddleTreeMaxLatUs`|`txnDelExtMiddleTreeMaxLatUs`|Transaction del ext middle tree max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnLookupMaxLatUs`|`txnLookupMaxLatUs`|Transaction lookup max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnPrefetchMaxLatUs`|`txnPrefetchMaxLatUs`|Transaction prefetch max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnSegCtxDataMaxLatUs`|`txnSegCtxDataMaxLatUs`|Transaction seg context data max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnSegCtxLLPMaxLatUs`|`txnSegCtxLLPMaxLatUs`|Transaction seg context llp max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnUnmapMaxLatUs`|`txnUnmapMaxLatUs`|Transaction unmap max latency us|microseconds|none|all|
|`vSAN Perf\|ZDOM VTX\|txnVatWriteSegMaxLatUs`|`txnVatWriteSegMaxLatUs`|Transaction vat write seg max latency us|microseconds|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 355 catalog series.

Build 19, `metric_scope=all` only: 21 more host-tier types (`host-cpu` and `dom-world-cpu` run last in the tier, so a deadline cut loses them first).

### ESA Dedup DOM Comp Mgr (`host-esa-dedup-domcompmgr-io`): 42 keys per host, `metric_scope=all` only

ESA deduplication I/O at the DOM component manager, per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): 5 entities, 210 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkReadCongestion`|`dedupChunkReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkRecoveryUnmapCongestion`|`dedupChunkRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkRecoveryWriteCongestion`|`dedupChunkRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkResyncReadCongestion`|`dedupChunkResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkUnmapCongestion`|`dedupChunkUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkUnmapCount`|`dedupChunkUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkWriteCongestion`|`dedupChunkWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineReadCongestion`|`dedupEngineReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineWriteCongestion`|`dedupEngineWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashReadCongestion`|`dedupHashReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashRecoveryUnmapCongestion`|`dedupHashRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashRecoveryWriteCongestion`|`dedupHashRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashResyncReadCongestion`|`dedupHashResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashUnmapCongestion`|`dedupHashUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashUnmapCount`|`dedupHashUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashWriteCongestion`|`dedupHashWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupChunkObjUnmap`|`iopsDedupChunkObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupHashObjUnmap`|`iopsDedupHashObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### ESA Dedup DOM Owner (`host-esa-dedup-domowner-io`): 82 keys per host, `metric_scope=all` only

ESA deduplication I/O at the DOM owner, per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): 5 entities, 410 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadCongestion`|`dedupChunkReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkRecoveryUnmapCongestion`|`dedupChunkRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkRecoveryWriteCongestion`|`dedupChunkRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkResyncReadCongestion`|`dedupChunkResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkUnmapCongestion`|`dedupChunkUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkUnmapCount`|`dedupChunkUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteCongestion`|`dedupChunkWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineReadCongestion`|`dedupEngineReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineWriteCongestion`|`dedupEngineWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadCongestion`|`dedupHashReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashRecoveryUnmapCongestion`|`dedupHashRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashRecoveryWriteCongestion`|`dedupHashRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashResyncReadCongestion`|`dedupHashResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashUnmapCongestion`|`dedupHashUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashUnmapCount`|`dedupHashUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteCongestion`|`dedupHashWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkObjUnmap`|`iopsDedupChunkObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashObjUnmap`|`iopsDedupHashObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadLeafOwnerCountLocal`|`dedupChunkReadLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadLeafOwnerCountRemote`|`dedupChunkReadLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteLeafOwnerCountLocal`|`dedupChunkWriteLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteLeafOwnerCountRemote`|`dedupChunkWriteLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadLeafOwnerCountLocal`|`dedupHashReadLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadLeafOwnerCountRemote`|`dedupHashReadLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteLeafOwnerCountLocal`|`dedupHashWriteLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteLeafOwnerCountRemote`|`dedupHashWriteLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkReadLeafOwnerLocal`|`iopsDedupChunkReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkReadLeafOwnerRemote`|`iopsDedupChunkReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkWriteLeafOwnerLocal`|`iopsDedupChunkWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkWriteLeafOwnerRemote`|`iopsDedupChunkWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashReadLeafOwnerLocal`|`iopsDedupHashReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashReadLeafOwnerRemote`|`iopsDedupHashReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashWriteLeafOwnerLocal`|`iopsDedupHashWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashWriteLeafOwnerRemote`|`iopsDedupHashWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkReadLeafOwnerLocal`|`latencyAvgDedupChunkReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkReadLeafOwnerRemote`|`latencyAvgDedupChunkReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkWriteLeafOwnerLocal`|`latencyAvgDedupChunkWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkWriteLeafOwnerRemote`|`latencyAvgDedupChunkWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashReadLeafOwnerLocal`|`latencyAvgDedupHashReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashReadLeafOwnerRemote`|`latencyAvgDedupHashReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashWriteLeafOwnerLocal`|`latencyAvgDedupHashWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashWriteLeafOwnerRemote`|`latencyAvgDedupHashWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafCompLocal`|`tputDedupChunkReadLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafCompRemote`|`tputDedupChunkReadLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafOwnerLocal`|`tputDedupChunkReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafOwnerRemote`|`tputDedupChunkReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafCompLocal`|`tputDedupChunkWriteLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafCompRemote`|`tputDedupChunkWriteLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafOwnerLocal`|`tputDedupChunkWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafOwnerRemote`|`tputDedupChunkWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafCompLocal`|`tputDedupHashReadLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafCompRemote`|`tputDedupHashReadLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafOwnerLocal`|`tputDedupHashReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafOwnerRemote`|`tputDedupHashReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafCompLocal`|`tputDedupHashWriteLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafCompRemote`|`tputDedupHashWriteLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafOwnerLocal`|`tputDedupHashWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafOwnerRemote`|`tputDedupHashWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### ESA Dedup Chunk Service (`host-esa-dedup-store-chunksvc`): 33 keys per host, `metric_scope=all` only

ESA deduplication chunk store service, per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): 5 entities, 165 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyChangeRefCountUs`|`latencyChangeRefCount`|Change Ref Count Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyDeleteChunkExtentUs`|`latencyDeleteChunkExtent`|Delete Chunk Extent Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyDeletedObjCleanupUs`|`latencyDeletedObjCleanup`|Deleted Object Clean Up Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyGetChunkEntriesUs`|`latencyGetChunkEntries`|Get Chunk Entries Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyReserveMbaUs`|`latencyReserveMba`|Reserve MBA Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numChangeRefCount`|`numChangeRefCount`|Change Ref Count Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numDeleteChunkExtent`|`numDeleteChunkExtent`|Delete Chunk Extent Request Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numDeletedObjCleanup`|`numDeletedObjCleanup`|Deleted Object Clean Up Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numGetChunkEntries`|`numGetChunkEntries`|Get Chunk Entries Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numObjsReported`|`numObjsReported`|Reported Chunk Objects|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numReserveMba`|`numReserveMba`|Reserve MBA Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateBlocksInChunkDeleted`|`rateBlocksInChunkDeleted`|Blocks in Deleted Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateBlocksInChunkWritten`|`rateBlocksInChunkWritten`|Blocks in Written Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChangeRefCount`|`rateChangeRefCount`|Change Ref Count Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunkRefEntAdded`|`rateChunkRefEntAdded`|Added Chunk Ref Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunkRefEntRemoved`|`rateChunkRefEntRemoved`|Removed Chunk Ref Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksDeleted`|`rateChunksDeleted`|Deleted Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksInChangeRefCount`|`rateChunksInChangeRefCount`|Chunks in Change Ref Count Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksInDeleteChunkExtent`|`rateChunksInDeleteChunkExtent`|Chunks in Delete Chunk Extent Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksInGetChunkEntries`|`rateChunksInGetChunkEntries`|Chunks in Get Chunk Entries Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksWritten`|`rateChunksWritten`|Write Chunk Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateDeleteChunkExtent`|`rateDeleteChunkExtent`|Delete Chunk Extent Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateDeletedObjCleanup`|`rateDeletedObjCleanup`|Deleted Object Clean Up Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateGetChunkEntries`|`rateGetChunkEntries`|Get Chunk Entries Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateReserveMba`|`rateReserveMba`|Reserve MBA Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalNumBlks`|`totalNumBlks`|Total Number of Blocks of All Chunks|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalNumRefCnt`|`totalNumRefCnt`|Total Ref Count of All Chunks|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateAsyncTaskBatches`|`rateAsyncTaskBatches`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksOpportunisticDelete`|`rateChunksOpportunisticDelete`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksRetained`|`rateChunksRetained`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksToDelete`|`rateChunksToDelete`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalNumBytesRetained`|`totalNumBytesRetained`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalTputRetained`|`totalTputRetained`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### ESA Dedup Hash Service (`host-esa-dedup-store-hashsvc`): 39 keys per host, `metric_scope=all` only

ESA deduplication hash store service, per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): 5 entities, 195 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup Hash Service\|currentGenNum`|`currentGenNum`|Gen Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|currentStaleGenNum`|`currentStaleGenNum`|Current Stale Gen Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyDeleteChunksReqUs`|`latencyDeleteChunksReq`|Delete Chunk Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyNewDupReqUs`|`latencyNewDupReq`|New Dup Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyReadLockWaitUs`|`latencyReadLockWait`|Read Lock Wait Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyUniqueHashReqUs`|`latencyUniqueHashReq`|Unique Hash Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyWriteLockWaitUs`|`latencyWriteLockWait`|Write Lock Wait Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numDeleteChunksReq`|`numDeleteChunksReq`|Delete Chunk Request Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numNewDupReq`|`numNewDupReq`|New Dup Request Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numObjsReported`|`numObjsReported`|Reported Chunk Objects|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numReadLockWait`|`numReadLockWait`|Read Lock Wait Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numUniqHashReq`|`numUniqHashReq`|Unique Hash Request Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numWriteLockWait`|`numWriteLockWait`|Write Lock Wait Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateChunkInDeleteChunksReq`|`rateChunkInDeleteChunksReq`|Chunks in Delete Chunks Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateChunkInNewDupReq`|`rateChunkInNewDupReq`|Chunks in New Dup Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateChunkInUniqHashReq`|`rateChunkInUniqHashReq`|Chunks in Unique Hash Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDedupHashEntryAdded`|`rateDedupHashEntryAdded`|Added Dedup Hash Map Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDedupHashEntryRemoved`|`rateDedupHashEntryRemoved`|Removed Dedup Hash Map Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDedupHashEntryUpdated`|`rateDedupHashEntryUpdated`|Updated Dedup Hash Map Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDeleteChunksReq`|`rateDeleteChunksReq`|Delete Chunk Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDeletedChunks`|`rateDeletedChunks`|Deleted Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateHashKeyRemovalEntriesPerGen`|`rateHashKeyRemovalEntriesPerGen`|Hash Key Removal Entries per Gen Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateHashKeyRemovalEntriesToScanInStep`|`rateHashKeyRemovalEntriesToScanInStep`|Hash Key Removal Entries to Scan in Latest Step Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateHashKeyRemovalStepsDone`|`rateHashKeyRemovalStepsDone`|Steps Done in Current Scan Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateNewDupChunks`|`rateNewDupChunks`|Processed New Dup Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateNewDupReq`|`rateNewDupReq`|New Dup Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateOldDupChunks`|`rateOldDupChunks`|Processed Old Dup Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateReadLockWait`|`rateReadLockWait`|Read Lock Wait Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateRetryChunks`|`rateRetryChunks`|Processed Transient Retry Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateSentriesAdded`|`rateSentriesAdded`|Added Sentries Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateSentriesRemoved`|`rateSentriesRemoved`|Removed Sentries Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateUniqChunks`|`rateUniqChunks`|Processed Unique Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateUniqHashReq`|`rateUniqHashReq`|Unique Hash Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateWriteLockWait`|`rateWriteLockWait`|Write Lock Wait Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|capBytesPerUnitPerHashObj`|`capBytesPerUnitPerHashObj`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDupEntries`|`rateDupEntries`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateLiveUniqEntries`|`rateLiveUniqEntries`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateStaleUniqEntries`|`rateStaleUniqEntries`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateUniqEntriesPerUnit`|`rateUniqEntriesPerUnit`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### vSAN Sparse (`host-vsansparse`): 21 keys per host, `metric_scope=all` only

vSAN sparse (snapshot) layer I/O and cache.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): 5 entities, 105 catalog series. Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|vSAN Sparse\|cacheAllocFails`|`cacheAllocFails`|Cache alloc fails|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|cacheEntries`|`cacheEntries`|Cache entries|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|cacheEvictAttempts`|`cacheEvictAttempts`|Cache evict attempts|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|cacheHitRate`|`cacheHitRate`|Cache Hit Rate|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|cacheInserts`|`cacheInserts`|Cache inserts|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|cacheLockContentions`|`cacheLockContentions`|Cache lock contentions|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|cacheRemoves`|`cacheRemoves`|Cache removes|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|cacheUpdateLatencyUs`|`cacheUpdateLatency`|Cache update latency|microseconds|none|all|
|`vSAN Perf\|vSAN Sparse\|iopsGwe`|`iopsGwe`|GWE IOPS|IO/s|none|all|
|`vSAN Perf\|vSAN Sparse\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|vSAN Sparse\|iopsRmw`|`iopsRmw`|RMW IOPS|IO/s|none|all|
|`vSAN Perf\|vSAN Sparse\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|vSAN Sparse\|iopsWriteConflicts`|`iopsWriteConflicts`|Write Conficts IOPS|IO/s|none|all|
|`vSAN Perf\|vSAN Sparse\|latencyCacheLookupUs`|`latencyCacheLookup`|Cache Lookup Latency|microseconds|none|all|
|`vSAN Perf\|vSAN Sparse\|latencyGweUs`|`latencyGwe`|GWE Latency|microseconds|none|all|
|`vSAN Perf\|vSAN Sparse\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|all|
|`vSAN Perf\|vSAN Sparse\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|all|
|`vSAN Perf\|vSAN Sparse\|lruLockContentions`|`lruLockContentions`|Lru lock contentions|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|readsToLayer`|`readsToLayer`|Reads to layer|none stated|none|all|
|`vSAN Perf\|vSAN Sparse\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|vSAN Sparse\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|

### RDT Network (`rdt-net`): 1 keys per host, `metric_scope=all` only

RDT transport checksum mismatches.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): 5 entities, 5 catalog series. Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|RDT Network\|checksumMismatchCount`|`checksumMismatchCount`|RDT Net Checksum Mismatch Count|none stated|none|all|

### System Memory (`system-mem`): 3 keys per host, `metric_scope=all` only

Host physical memory use as the Performance Service reports it. `totalMbMemUsed` is MiB and `pctMemUsed` an integer percent of host memory, truncated (P3: exactly floor(100 x used / `hardware.memorySize` MiB) on 10 of 10 samples); both pushed as served.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): 5 entities, 15 catalog series. Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|System Memory\|overcommitRatioPct`|`overcommitRatio`|Over Commit Ratio|percent|none|all|
|`vSAN Perf\|System Memory\|pctMemUsed`|`pctMemUsed`|Used Memory Percentage|percent, integer (P3: floor(100 x totalMbMemUsed / host MiB); the Python pack treats it as per mille)|none|all|
|`vSAN Perf\|System Memory\|totalMbMemUsed`|`totalMbMemUsed`|Total Memory Used|MiB (P3)|none|all|

### LSOM World:<world>-<id> (`lsom-world-cpu`): 2 keys per LSOM world, `metric_scope=all` only

CPU of the LSOM worlds (OSA). Empty on ESA. Instances churn on a host reboot (see below).

Lands on: HostSystem by node uuid. Instance: `<world>-<id>` = the world name and world id from the entity id (inferred form `<node>\|<world>\|<id>`). Lab (P3, 2026-11-07): empty (wildcard echo). Feature: OSA.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|LSOM World:<world>-<id>\|readyPct`|`readyPct`|Percentage of Ready CPU Time|percent|none|all|
|`vSAN Perf\|LSOM World:<world>-<id>\|usedPct`|`usedPct`|Percentage of Used CPU Time|percent|none|all|

### Heap:<heap> (`heap-memory`): 1 keys per heap, `metric_scope=all` only

vSAN heap utilisation.

Lands on: HostSystem by node uuid. Instance: `<heap>` = the heap name (inferred form `<node>\|<heap>`). Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Heap:<heap>\|heapUtilPct`|`heapUtil`|Heap Utilization|percent|none|all|

### Slab:<slab> (`slab-memory`): 1 keys per slab, `metric_scope=all` only

vSAN slab utilisation.

Lands on: HostSystem by node uuid. Instance: `<slab>` = the slab name (inferred form `<node>\|<slab>`). Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Slab:<slab>\|slabUtilPct`|`slabUtil`|Slab Utilization|percent|none|all|

### CLOM (`clom-host`): 2 keys per host, `metric_scope=all` only

CLOM component count ratios per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|CLOM\|nodeCompCntRatio`|`nodeCompCntRatio`|Node Component Count Ratio|none stated|none|all|
|`vSAN Perf\|CLOM\|nodeDataCompCntRatio`|`nodeDataCompCntRatio`|Node Data Component Count Ratio|none stated|none|all|

### CMMDS Workload (`cmmds-workload`): 23 keys per host, `metric_scope=all` only

CMMDS message and batch counts.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|CMMDS Workload\|agentBatchesSent`|`agentBatchesSent`|Agent Batches Sent|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|checkPointBatchesSent`|`checkPointBatchesSent`|Check Point Batches Sent|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|masterBatchesRecved`|`masterBatchesRecved`|Primary Batches Received|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|rxAccept`|`rxAccept`|Inbound Accept|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|rxAgentUpdateRequest`|`rxAgentUpdateRequest`|Inbound Agent Update Request|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|rxHeartbeatRequest`|`rxHeartbeatRequest`|Inbound Heartbeat Request|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|rxMasterUpdate`|`rxMasterUpdate`|Inbound Primary Update|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|rxMisc`|`rxMisc`|Inbound Misc|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|rxRetransmitRequest`|`rxRetransmitRequest`|Inbound Retransmit Request|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|rxSnapshot`|`rxSnapshot`|Inbound Snapshot|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|totalUpdInBatchCkpt`|`totalUpdInBatchCkpt`|Total Updated In Batch Check Point|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|totalUpdInMasterBatches`|`totalUpdInMasterBatches`|Total Updated In Primary Batches|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|totalUpdSentInAgentBatch`|`totalUpdSentInAgentBatch`|Total Updated In Agent Batch|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txAgentUpdateRequest`|`txAgentUpdateRequest`|Outbound Agent Update Request|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txHeartbeatRequest`|`txHeartbeatRequest`|Outbound Heartbeat Request|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txMasterCheckpoint`|`txMasterCheckpoint`|Outbound Primary Check Point|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txMasterCkptTried`|`txMasterCkptTried`|Outbound Primary Check Point Tried|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txMasterUpdate`|`txMasterUpdate`|Outbound Primary Update|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txMasterUpdateTried`|`txMasterUpdateTried`|Outbound Primary Update Tried|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txRetransmitRequest`|`txRetransmitRequest`|Outbound Retransmit Request|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txSnapshot`|`txSnapshot`|Outbound Snapshot|none stated|none|all|
|`vSAN Perf\|CMMDS Workload\|txSnapshotBytes`|`txSnapshotBytes`|Outbound Snapshot Bytes|bytes|none|all|
|`vSAN Perf\|CMMDS Workload\|txSnapshotTried`|`txSnapshotTried`|Outbound Snapshot Tried|none stated|none|all|

### Distribution (`vsan-distribution`): 5 keys per host, `metric_scope=all` only

Component and DOM object distribution counts per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): empty (wildcard echo). Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Distribution\|components`|`components`|Components|none stated|none|all|
|`vSAN Perf\|Distribution\|domClients`|`domClients`|DOM Clients|none stated|none|all|
|`vSAN Perf\|Distribution\|domColocated`|`domColocated`|DOM Colocated|none stated|none|all|
|`vSAN Perf\|Distribution\|domOwners`|`domOwners`|DOM Owners|none stated|none|all|
|`vSAN Perf\|Distribution\|ioComponents`|`ioComponents`|IO Components|none stated|none|all|

### PMem (`host-pmem`): 8 keys per host, `metric_scope=all` only

Persistent memory I/O per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): fault `NotSupported` `pmemisunavailable` (learned at the first fault). Feature: PMem storage.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|PMem\|bandwidthReadBytesPerSec`|`bandwidthRead`|Read Bandwidth|bytes/s|none|all|
|`vSAN Perf\|PMem\|bandwidthTotalBytesPerSec`|`bandwidthTotal`|Total Bandwidth|bytes/s|none|all|
|`vSAN Perf\|PMem\|bandwidthWriteBytesPerSec`|`bandwidthWrite`|Write Bandwidth|bytes/s|none|all|
|`vSAN Perf\|PMem\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|PMem\|iopsTotal`|`iopsTotal`|Total IOPS|IO/s|none|all|
|`vSAN Perf\|PMem\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|PMem\|latencyRead`|`latencyRead`|vSAN Layer Read Latency|disputed: Python microseconds, schema nanoseconds; pushed verbatim|none|all|
|`vSAN Perf\|PMem\|latencyWrite`|`latencyWrite`|vSAN Layer Write Latency|disputed: Python microseconds, schema nanoseconds; pushed verbatim|none|all|

### Remote DOM Client (`host-remotedomclient`): 11 keys per host, `metric_scope=all` only

HCI Mesh: this host's remote client I/O.

Lands on: HostSystem, only when the id is one node uuid of the queried cluster (remote rule). Instance: none. Lab (P3, 2026-11-07): empty (wildcard echo). Feature: HCI Mesh.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Remote DOM Client\|congestion`|`congestion`|Congestions|none stated|none|all|
|`vSAN Perf\|Remote DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|Remote DOM Client\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|Remote DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|Remote DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|all|
|`vSAN Perf\|Remote DOM Client\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|all|
|`vSAN Perf\|Remote DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|all|
|`vSAN Perf\|Remote DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|all|
|`vSAN Perf\|Remote DOM Client\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|Remote DOM Client\|throughputUnmapBytesPerSec`|`throughputUnmap`|Unmap Throughput|bytes/s|none|all|
|`vSAN Perf\|Remote DOM Client\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|

### Compute Remote DOM Client (`computeHost-remotedomclient`): 11 keys per host, `metric_scope=all` only

HCI Mesh: compute-only host side of remote I/O.

Lands on: HostSystem, only when the id is one node uuid of the queried cluster (remote rule). Instance: none. Lab (P3, 2026-11-07): empty (wildcard echo). Feature: HCI Mesh.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Compute Remote DOM Client\|congestion`|`congestion`|Congestions|none stated|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|throughputUnmapBytesPerSec`|`throughputUnmap`|Unmap Throughput|bytes/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|

### iSCSI (`vsan-iscsi-host`): 10 keys per host, `metric_scope=all` only

vSAN iSCSI target service I/O per host.

Lands on: HostSystem by node uuid. Instance: none. Lab (P3, 2026-11-07): empty (wildcard echo). Feature: vSAN iSCSI target service.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|iSCSI\|bandwidthReadBytesPerSec`|`bandwidthRead`|Read Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI\|bandwidthTotalBytesPerSec`|`bandwidthTotal`|Total Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI\|bandwidthWriteBytesPerSec`|`bandwidthWrite`|Write Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI\|iopsTotal`|`iopsTotal`|Total IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI\|latencyTotalUs`|`latencyTotal`|Total Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI\|queueDepth`|`queueDepth`|Outstanding IO|none stated|none|all|

### iSCSI Target:<target> (`vsan-iscsi-target`): 10 keys per iSCSI target, `metric_scope=all` only

I/O per iSCSI target.

Lands on: HostSystem by node uuid. Instance: `<target>` = the target IQN (`:` kept; inferred form `<node>\|<target>`). Lab (P3, 2026-11-07): empty (wildcard echo). Feature: vSAN iSCSI target service.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|iSCSI Target:<target>\|bandwidthReadBytesPerSec`|`bandwidthRead`|Read Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|bandwidthTotalBytesPerSec`|`bandwidthTotal`|Total Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|bandwidthWriteBytesPerSec`|`bandwidthWrite`|Write Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|iopsTotal`|`iopsTotal`|Total IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|latencyTotalUs`|`latencyTotal`|Total Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI Target:<target>\|queueDepth`|`queueDepth`|Outstanding IO|none stated|none|all|

### iSCSI LUN:<target>/<lun> (`vsan-iscsi-lun`): 10 keys per iSCSI LUN, `metric_scope=all` only

I/O per iSCSI LUN.

Lands on: HostSystem by node uuid. Instance: `<target>/<lun>` (inferred form `<node>\|<target>\|<lun>`). Lab (P3, 2026-11-07): empty (wildcard echo). Feature: vSAN iSCSI target service.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|bandwidthReadBytesPerSec`|`bandwidthRead`|Read Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|bandwidthTotalBytesPerSec`|`bandwidthTotal`|Total Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|bandwidthWriteBytesPerSec`|`bandwidthWrite`|Write Bandwidth|bytes/s|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|iopsTotal`|`iopsTotal`|Total IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|latencyTotalUs`|`latencyTotal`|Total Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|all|
|`vSAN Perf\|iSCSI LUN:<target>/<lun>\|queueDepth`|`queueDepth`|Outstanding IO|none stated|none|all|

### pCPU and pCPU:<cpu> (`host-cpu`): 3 keys per host CPU, `metric_scope=all` only

Physical CPU use per CPU and for the host. Scales with core count: 3 keys per CPU (`exclude_types=host-cpu` drops it).

Lands on: HostSystem by node uuid. Instance: `<cpu>` = `cpu-<n>`; the 1-part id `host-cpu:<node>` is the host aggregate and has no instance (`vSAN Perf\|pCPU\|<leaf>`). Lab (P3, 2026-11-07): 165 entities, 495 catalog series. Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|pCPU:<cpu>\|coreUtilPct`|`coreUtilPct`|Percentage of CPU core uitilization|percent|none|all|
|`vSAN Perf\|pCPU:<cpu>\|pcpuUsedPct`|`pcpuUsedPct`|Percentage of Used Physical CPU Time|percent|none|all|
|`vSAN Perf\|pCPU:<cpu>\|pcpuUtilPct`|`pcpuUtilPct`|Percentage of Physical CPU Utilization|percent|none|all|

### DOM World:<world>-<id> (`dom-world-cpu`): 3 keys per DOM world, `metric_scope=all` only

CPU of the DOM worlds (CompServer, Owner, OwnerHlpr0, Client). 66 per host on the lab. **Instances change on a host reboot** (the name carries a kernel address and the world id): new instances appear, the old keys stop receiving data and stay in Ops (design Q8, accepted). `exclude_types=dom-world-cpu` drops it.

Lands on: HostSystem by node uuid. Instance: `<world>-<id>` from `<node>\|<world>\|<id>`, e.g. `VSAN_0x432abcf22c40_CompServer-2099225`. Lab (P3, 2026-11-07): 330 entities, 990 catalog series. Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|DOM World:<world>-<id>\|readyPct`|`readyPct`|Percentage of Ready CPU Time|percent|none|all|
|`vSAN Perf\|DOM World:<world>-<id>\|runPct`|`runPct`|Percentage of Running CPU Time|percent|none|all|
|`vSAN Perf\|DOM World:<world>-<id>\|usedPct`|`usedPct`|Percentage of Used CPU Time|percent|none|all|

## Keys on VMWARE ClusterComputeResource: cluster tier (`tier_cluster`, 189 keys; 495 under `all`)

The cluster's own totals (DOM Owner and DOM Client here are the cluster-wide
totals of the host roles). On the lab: 91 series per cluster; vSAN Direct
Cluster, File Service and IOInsight were empty there (not configured).

### Capacity (`vsan-cluster-capacity`): 6 keys per cluster (5 at dashboard scope)

vSAN datastore capacity and space efficiency for the cluster. `dedupRatio` is served as ratio x 100 (Python label "100 = 1.00x"; 145 on the lab) and pushed as served.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 5 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Capacity\|dedupRatio`|`dedupRatio`|Space Efficiency Ratio (100 = 1.00x)|none stated|none; ratio x 100 (Python label: 100 = 1.00x); pushed as served|dashboard|
|`vSAN Perf\|Capacity\|freeBytes`|`free`|Free Capacity|bytes|none|dashboard|
|`vSAN Perf\|Capacity\|savedByDedupBytes`|`savedByDedup`|Saved Capacity|bytes|none|dashboard|
|`vSAN Perf\|Capacity\|totalBytes`|`total`|Total Capacity|bytes|none|dashboard|
|`vSAN Perf\|Capacity\|usedBytes`|`used`|Used Capacity|bytes|none|dashboard|
|`vSAN Perf\|Capacity\|totalDpOverheadBytes`|`totalDpOverhead`|DP overhead (total)|bytes|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 1 entities, 6 catalog series.

### DOM Client (`cluster-domclient`): 14 keys per cluster (11 at dashboard scope)

The cluster-wide total of the hosts' DOM Client role: the I/O the cluster's VMs issued, as the clients saw it (the host-level reading is under DOM Client on HostSystem).

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 11 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|DOM Client\|congestion`|`congestion`|Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|DOM Client\|readCongestion`|`readCongestion`|Read Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Client\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Client\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Client\|unmapCongestion`|`unmapCongestion`|Unmap congestion|none stated|none|dashboard|
|`vSAN Perf\|DOM Client\|writeCongestion`|`writeCongestion`|Write Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Client\|numOio`|`numOio`|Num outstanding IO|none stated|none|all|
|`vSAN Perf\|DOM Client\|readCount`|`readCount`|Read count|none stated|none|all|
|`vSAN Perf\|DOM Client\|writeCount`|`writeCount`|Write count|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 1 entities, 14 catalog series.

### DOM Comp Mgr (`cluster-domcompmgr`): 15 keys per cluster (12 at dashboard scope)

Cluster-level counterpart of the host DOM Comp Mgr group (component manager I/O, including recovery writes and resync reads per its labels).

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 12 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|DOM Comp Mgr\|congestion`|`congestion`|Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|iopsRecWrite`|`iopsRecWrite`|Recovery Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|iopsResyncRead`|`iopsResyncRead`|Resync Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latAvgResyncReadUs`|`latAvgResyncRead`|Resync Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgRecWriteUs`|`latencyAvgRecWrite`|Recovery Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|throughputRecWriteBytesPerSec`|`throughputRecWrite`|Recovery Write Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|tputResyncReadBytesPerSec`|`tputResyncRead`|Resync Read Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Comp Mgr\|numOio`|`numOio`|Num outstanding IO|none stated|none|all|
|`vSAN Perf\|DOM Comp Mgr\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Comp Mgr\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 1 entities, 15 catalog series.

### DOM Owner (`cluster-domowner`): 46 keys per cluster (23 at dashboard scope)

The cluster-wide total of the hosts' DOM Owner role: owner-side I/O for every object in the cluster, whichever host's VMs issued it (the host-level reading is under DOM Owner on HostSystem), plus the cluster's resync job counters.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 23 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|DOM Owner\|avgResyncParallelism`|`avgResyncParallelism`|Resync parallelism (average)|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|congestion`|`congestion`|Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|iopsRecWrite`|`iopsRecWrite`|Recovery Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|iopsResyncRead`|`iopsResyncRead`|Resync Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|DOM Owner\|numCompleteFullResyncOps`|`numCompleteFullResyncOps`|Num complete full resync ops|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numCompleteResyncOps`|`numCompleteResyncOps`|Num complete resync ops|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numInflightPriorityResyncJobs`|`numInflightPriorityResyncJobs`|Num inflight priority resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numInflightSharedResyncJobs`|`numInflightSharedResyncJobs`|Num inflight shared resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numPendingDecomResyncJobs`|`numPendingDecomResyncJobs`|Num pending decom resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numPendingFullResyncJobs`|`numPendingFullResyncJobs`|Num pending full resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numPendingResyncJobs`|`numPendingResyncJobs`|Num pending resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numRunningFullResyncJobs`|`numRunningFullResyncJobs`|Num running full resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numRunningResyncJobs`|`numRunningResyncJobs`|Num running resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|numSuspendedResyncJobs`|`numSuspendedResyncJobs`|Num suspended resync jobs|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|readCongestion`|`readCongestion`|Read Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|tputReadBytesPerSec`|`tputRead`|Read Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|tputWriteBytesPerSec`|`tputWrite`|Write Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|DOM Owner\|writeCongestion`|`writeCongestion`|Write Congestions|none stated|none|dashboard|
|`vSAN Perf\|DOM Owner\|avgDeltaCreationDomActiveLatencySec`|`avgDeltaCreationDomActiveLatency`|Delta creation DOM active latency (average)|seconds|none|all|
|`vSAN Perf\|DOM Owner\|avgDeltaCreationDomCreationLatencySec`|`avgDeltaCreationDomCreationLatency`|Delta creation DOM creation latency (average)|seconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgRecWriteUs`|`latencyAvgRecWrite`|Recovery Write Latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|latencyAvgResyncReadUs`|`latencyAvgResyncRead`|Resync Read Latency|microseconds|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteDeltaToBaseResyncOps`|`numCompleteDeltaToBaseResyncOps`|Num complete delta to base resync ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteResyncRwrBatches`|`numCompleteResyncRwrBatches`|Num complete resync rwr batches|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteRwrOps`|`numCompleteRwrOps`|Num complete rwr ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numCompleteSiblingToDeltaResyncOps`|`numCompleteSiblingToDeltaResyncOps`|Num complete sibling to delta resync ops|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numOio`|`numOio`|Num outstanding IO|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingDeltaToBaseResyncJobs`|`numPendingDeltaToBaseResyncJobs`|Num pending delta to base resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numPendingSiblingToDeltaResyncJobs`|`numPendingSiblingToDeltaResyncJobs`|Num pending sibling to delta resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numRunningDeltaToBaseResyncJobs`|`numRunningDeltaToBaseResyncJobs`|Num running delta to base resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numRunningSiblingToDeltaResyncJobs`|`numRunningSiblingToDeltaResyncJobs`|Num running sibling to delta resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedDeltaToBaseResyncJobs`|`numSuspendedDeltaToBaseResyncJobs`|Num suspended delta to base resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedFullResyncJobs`|`numSuspendedFullResyncJobs`|Num suspended full resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedPriorityResyncJobs`|`numSuspendedPriorityResyncJobs`|Num suspended priority resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedResyncChangemaps`|`numSuspendedResyncChangemaps`|Num suspended resync changemaps|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedSharedResyncJobs`|`numSuspendedSharedResyncJobs`|Num suspended shared resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|numSuspendedSiblingToDeltaResyncJobs`|`numSuspendedSiblingToDeltaResyncJobs`|Num suspended sibling to delta resync jobs|none stated|none|all|
|`vSAN Perf\|DOM Owner\|totalDeltasCreatedCount`|`totalDeltasCreatedCount`|Deltas created count (total)|none stated|none|all|
|`vSAN Perf\|DOM Owner\|tputRecWriteBytesPerSec`|`tputRecWrite`|Recovery Write Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|tputResyncReadBytesPerSec`|`tputResyncRead`|Resync Read Throughput|bytes/s|none|all|
|`vSAN Perf\|DOM Owner\|unmapCongestion`|`unmapCongestion`|Unmap congestion|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 1 entities, 46 catalog series.

### ESA Dedup DOM Client (`cluster-esa-dedup-domclient-io`): 42 keys per cluster (24 at dashboard scope)

ESA global deduplication I/O seen from the DOM client (chunk, engine and hash object reads and writes). Live on the lab's 9.1.1 ESA cluster, although the Python pack carries it only in its provisional `perfsvc_model_extra.py`.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 24 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|dashboard|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCongestion`|`dedupChunkReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkRecoveryUnmapCongestion`|`dedupChunkRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkRecoveryWriteCongestion`|`dedupChunkRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkResyncReadCongestion`|`dedupChunkResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkUnmapCongestion`|`dedupChunkUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkUnmapCount`|`dedupChunkUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCongestion`|`dedupChunkWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCongestion`|`dedupEngineReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCongestion`|`dedupEngineWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCongestion`|`dedupHashReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashRecoveryUnmapCongestion`|`dedupHashRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashRecoveryWriteCongestion`|`dedupHashRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashResyncReadCongestion`|`dedupHashResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashUnmapCongestion`|`dedupHashUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashUnmapCount`|`dedupHashUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCongestion`|`dedupHashWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjUnmap`|`iopsDedupChunkObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjUnmap`|`iopsDedupHashObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 1 entities, 42 catalog series.

### RDT Latency (`cluster-rdt-network-latency`): 17 keys per cluster (9 at dashboard scope)

Reliable Datagram Transport latency and socket-buffer figures for the cluster.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 9 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|RDT Latency\|avgLatencyUs`|`avgLatency`|RDT Network VNIC Average Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|kaReset`|`kaReset`|Keepalive reset|none stated|none|dashboard|
|`vSAN Perf\|RDT Latency\|maxLatencyUs`|`maxLatency`|RDT Network Cluster Max Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|minLatencyUs`|`minLatency`|RDT Network Cluster Min Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|numReadyDelay`|`numReadyDelay`|Num ready delay|none stated|none|dashboard|
|`vSAN Perf\|RDT Latency\|rxSbSpaceMinBytes`|`rxSbSpaceMin`|RDT Network Socket Min Inbound Bytes|bytes|none|dashboard|
|`vSAN Perf\|RDT Latency\|txCtxQMaxBytes`|`txCtxQMax`|RDT Network Max Outbound Context Bytes|bytes|none|dashboard|
|`vSAN Perf\|RDT Latency\|txQLatAvgUs`|`txQLatAvg`|RDT Network Average Outbound Queueing Latency|microseconds|none|dashboard|
|`vSAN Perf\|RDT Latency\|txSbSpaceMinBytes`|`txSbSpaceMin`|RDT Network Socket Min Outbound Bytes|bytes|none|dashboard|
|`vSAN Perf\|RDT Latency\|rxSbSpaceAvgBytes`|`rxSbSpaceAvg`|RDT Network Socket Average Inbound Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|rxSbSpaceMaxBytes`|`rxSbSpaceMax`|RDT Network Socket Max Inbound Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txCtxQAvgBytes`|`txCtxQAvg`|RDT Network Average Outbound Context Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txCtxQMinBytes`|`txCtxQMin`|RDT Network Min Outbound Context Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txQLatMaxUs`|`txQLatMax`|RDT Network Max Outbound Queueing Latency|microseconds|none|all|
|`vSAN Perf\|RDT Latency\|txQLatMinUs`|`txQLatMin`|RDT Network Min Outbound Queueing Latency|microseconds|none|all|
|`vSAN Perf\|RDT Latency\|txSbSpaceAvgBytes`|`txSbSpaceAvg`|RDT Network Socket Average Outbound Bytes|bytes|none|all|
|`vSAN Perf\|RDT Latency\|txSbSpaceMaxBytes`|`txSbSpaceMax`|RDT Network Socket Max Outbound Bytes|bytes|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 1 entities, 17 catalog series.

### ZDOM Top Stats (`cluster-zdom-top-stats`): 12 keys per cluster (7 at dashboard scope)

ESA zDOM top-level I/O for the cluster.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 7 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ZDOM Top Stats\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|oio`|`oio`|Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|dashboard|
|`vSAN Perf\|ZDOM Top Stats\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|all|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|all|
|`vSAN Perf\|ZDOM Top Stats\|oioRead`|`oioRead`|Outstanding IO (read)|none stated|none|all|
|`vSAN Perf\|ZDOM Top Stats\|oioWrite`|`oioWrite`|Outstanding IO (write)|none stated|none|all|
|`vSAN Perf\|ZDOM Top Stats\|throughputUnmapBytesPerSec`|`throughputUnmap`|Unmap Throughput|bytes/s|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 1 entities, 12 catalog series.

### vSAN Direct Cluster (`vsan-direct-cluster`): 8 keys per cluster

vSAN Direct. Empty on the lab (not configured); entity id form unobserved.

Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time, see "Where keys land"). Instance: none. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|vSAN Direct Cluster\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|vSAN Direct Cluster\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|vSAN Direct Cluster\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|vSAN Direct Cluster\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|vSAN Direct Cluster\|oioDevRead`|`oioDevRead`|Read Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|vSAN Direct Cluster\|oioDevWrite`|`oioDevWrite`|Write Outstanding IO|none stated|none|dashboard|
|`vSAN Perf\|vSAN Direct Cluster\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|vSAN Direct Cluster\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none|dashboard|

### File Service (`vsan-file-service`): 8 keys per cluster

vSAN File Service. Empty on the lab (not enabled); entity id form unobserved.

Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time). Instance: none. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|File Service\|readLatencyUs`|`readLatency`|Read latency|microseconds|none|dashboard|
|`vSAN Perf\|File Service\|readOpTotal`|`readOpTotal`|IOPS for read operations|IO/s|none|dashboard|
|`vSAN Perf\|File Service\|readRequestedBytesPerSec`|`readRequested`|Requested bytes for read|bytes/s|none|dashboard|
|`vSAN Perf\|File Service\|readTransferredBytesPerSec`|`readTransferred`|Actual transferred bytes for read|bytes/s|none|dashboard|
|`vSAN Perf\|File Service\|writeLatencyUs`|`writeLatency`|Write latency|microseconds|none|dashboard|
|`vSAN Perf\|File Service\|writeOpTotal`|`writeOpTotal`|IOPS for write operations|IO/s|none|dashboard|
|`vSAN Perf\|File Service\|writeRequestedBytesPerSec`|`writeRequested`|Requested bytes for write|bytes/s|none|dashboard|
|`vSAN Perf\|File Service\|writeTransferredBytesPerSec`|`writeTransferred`|Actual transferred bytes for write|bytes/s|none|dashboard|

### IOInsight (`ioinsight`): 82 keys per cluster

IOInsight capture results. Empty on the lab (no capture running); entity id form unobserved. 82 of the cluster tier's 189 keys.

Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time). Instance: none. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|IOInsight\|aligned4kRatio`|`aligned4kRatio`|4K Aligned IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|aligned4kReadRatio`|`aligned4kReadRatio`|4K Aligned Read IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|aligned4kWriteRatio`|`aligned4kWriteRatio`|4K Aligned Write IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|IOInsight\|iopsTotal`|`iopsTotal`|Total IOPS|IO/s|none|dashboard|
|`vSAN Perf\|IOInsight\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|IOInsight\|randomRatio`|`randomRatio`|Random IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|randomReadRatio`|`randomReadRatio`|Random Read IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|randomWriteRatio`|`randomWriteRatio`|Random Write IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|readRatio`|`readRatio`|Read IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz0_4k`|`riosz0_4k`|ReadSize<4K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz128k`|`riosz128k`|ReadSize=128K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz128k_256k`|`riosz128k_256k`|128K<ReadSize<256K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz16k`|`riosz16k`|ReadSize=16K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz16k_32k`|`riosz16k_32k`|16K<ReadSize<32K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz1_m`|`riosz1_m`|ReadSize>1M|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz1m`|`riosz1m`|ReadSize=1M|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz256k`|`riosz256k`|ReadSize=256K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz256k_512k`|`riosz256k_512k`|256K<ReadSize<512K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz32k`|`riosz32k`|ReadSize=32K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz32k_64k`|`riosz32k_64k`|32K<ReadSize<64K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz4k`|`riosz4k`|ReadSize=4K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz4k_8k`|`riosz4k_8k`|4K<ReadSize<8K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz512k`|`riosz512k`|ReadSize=512K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz512k_1m`|`riosz512k_1m`|512K<ReadSize<1M|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz64k`|`riosz64k`|ReadSize=64K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz64k_128k`|`riosz64k_128k`|64K<ReadSize<128K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz8k`|`riosz8k`|ReadSize=8K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|riosz8k_16k`|`riosz8k_16k`|8K<ReadSize<16K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat0_1us`|`rlat0_1us`|0<ReadLatency<1(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat100_500us`|`rlat100_500us`|100<ReadLatency<500(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat100_ms`|`rlat100_ms`|ReadLatency>100(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat10_100us`|`rlat10_100us`|10<ReadLatency<100(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat10_25ms`|`rlat10_25ms`|10<ReadLatency<25(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat1_10us`|`rlat1_10us`|1<ReadLatency<10(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat1_5ms`|`rlat1_5ms`|1<ReadLatency<5(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat25_50ms`|`rlat25_50ms`|25<ReadLatency<50(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat500us_1ms`|`rlat500us_1ms`|500us<ReadLatency<1ms|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat50_100ms`|`rlat50_100ms`|50<ReadLatency<100(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|rlat5_10ms`|`rlat5_10ms`|5<ReadLatency<10(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|sequentialRatio`|`sequentialRatio`|Sequential IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|sequentialReadRatio`|`sequentialReadRatio`|Sequential Read IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|sequentialWriteRatio`|`sequentialWriteRatio`|Sequential Write IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|throughputRandomBytesPerSec`|`throughputRandom`|Random Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|IOInsight\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|dashboard|
|`vSAN Perf\|IOInsight\|throughputSequentialBytesPerSec`|`throughputSequential`|Sequential Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|IOInsight\|throughputTotalBytesPerSec`|`throughputTotal`|Total Throughput|bytes/s|none|dashboard|
|`vSAN Perf\|IOInsight\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|dashboard|
|`vSAN Perf\|IOInsight\|unaligned4kRatio`|`unaligned4kRatio`|4K Unaligned IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|unaligned4kReadRatio`|`unaligned4kReadRatio`|4K Unaligned Read IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|unaligned4kWriteRatio`|`unaligned4kWriteRatio`|4K Unaligned Write IO|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz0_4k`|`wiosz0_4k`|WriteSize<4K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz128k`|`wiosz128k`|WriteSize=128K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz128k_256k`|`wiosz128k_256k`|128K<WriteSize<256K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz16k`|`wiosz16k`|WriteSize=16K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz16k_32k`|`wiosz16k_32k`|16K<WriteSize<32K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz1_m`|`wiosz1_m`|WriteSize>1M|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz1m`|`wiosz1m`|WriteSize=1M|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz256k`|`wiosz256k`|WriteSize=256K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz256k_512k`|`wiosz256k_512k`|256K<WriteSize<512K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz32k`|`wiosz32k`|WriteSize=32K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz32k_64k`|`wiosz32k_64k`|32K<WriteSize<64K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz4k`|`wiosz4k`|WriteSize=4K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz4k_8k`|`wiosz4k_8k`|4K<WriteSize<8K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz512k`|`wiosz512k`|WriteSize=512K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz512k_1m`|`wiosz512k_1m`|512K<WriteSize<1M|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz64k`|`wiosz64k`|WriteSize=64K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz64k_128k`|`wiosz64k_128k`|64K<WriteSize<128K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz8k`|`wiosz8k`|WriteSize=8K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wiosz8k_16k`|`wiosz8k_16k`|8K<WriteSize<16K|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat0_1us`|`wlat0_1us`|0<WriteLatency<1(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat100_500us`|`wlat100_500us`|100<WriteLatency<500(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat100_ms`|`wlat100_ms`|WriteLatency>100(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat10_100us`|`wlat10_100us`|10<WriteLatency<100(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat10_25ms`|`wlat10_25ms`|10<WriteLatency<25(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat1_10us`|`wlat1_10us`|1<WriteLatency<10(us)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat1_5ms`|`wlat1_5ms`|1<WriteLatency<5(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat25_50ms`|`wlat25_50ms`|25<WriteLatency<50(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat500us_1ms`|`wlat500us_1ms`|500us<WriteLatency<1ms|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat50_100ms`|`wlat50_100ms`|50<WriteLatency<100(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|wlat5_10ms`|`wlat5_10ms`|5<WriteLatency<10(ms)|none stated|none|dashboard|
|`vSAN Perf\|IOInsight\|writeRatio`|`writeRatio`|Write IO|none stated|none|dashboard|

Build 19, `metric_scope=all` only: 9 more cluster-tier types.

### ESA Dedup DOM Comp Mgr (`cluster-esa-dedup-domcompmgr-io`): 42 keys per cluster, `metric_scope=all` only

ESA deduplication I/O at the DOM component manager, cluster total.

Lands on: ClusterComputeResource (the entity id is the queried cluster's vSAN uuid). Instance: none. Lab (P3, 2026-11-07): 1 entities, 42 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkReadCongestion`|`dedupChunkReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkRecoveryUnmapCongestion`|`dedupChunkRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkRecoveryWriteCongestion`|`dedupChunkRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkResyncReadCongestion`|`dedupChunkResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkUnmapCongestion`|`dedupChunkUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkUnmapCount`|`dedupChunkUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupChunkWriteCongestion`|`dedupChunkWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineReadCongestion`|`dedupEngineReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupEngineWriteCongestion`|`dedupEngineWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashReadCongestion`|`dedupHashReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashRecoveryUnmapCongestion`|`dedupHashRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashRecoveryWriteCongestion`|`dedupHashRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashResyncReadCongestion`|`dedupHashResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashUnmapCongestion`|`dedupHashUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashUnmapCount`|`dedupHashUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|dedupHashWriteCongestion`|`dedupHashWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupChunkObjUnmap`|`iopsDedupChunkObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Comp Mgr\|iopsDedupHashObjUnmap`|`iopsDedupHashObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### ESA Dedup DOM Owner (`cluster-esa-dedup-domowner-io`): 82 keys per cluster, `metric_scope=all` only

ESA deduplication I/O at the DOM owner, cluster total.

Lands on: ClusterComputeResource (the queried cluster's vSAN uuid). Instance: none. Lab (P3, 2026-11-07): 1 entities, 82 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadCongestion`|`dedupChunkReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkRecoveryUnmapCongestion`|`dedupChunkRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkRecoveryWriteCongestion`|`dedupChunkRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkResyncReadCongestion`|`dedupChunkResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkUnmapCongestion`|`dedupChunkUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkUnmapCount`|`dedupChunkUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteCongestion`|`dedupChunkWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineReadCongestion`|`dedupEngineReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupEngineWriteCongestion`|`dedupEngineWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadCongestion`|`dedupHashReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashRecoveryUnmapCongestion`|`dedupHashRecoveryUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashRecoveryWriteCongestion`|`dedupHashRecoveryWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashResyncReadCongestion`|`dedupHashResyncReadCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashUnmapCongestion`|`dedupHashUnmapCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashUnmapCount`|`dedupHashUnmapCount`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteCongestion`|`dedupHashWriteCongestion`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkObjUnmap`|`iopsDedupChunkObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashObjUnmap`|`iopsDedupHashObjUnmap`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadLeafOwnerCountLocal`|`dedupChunkReadLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkReadLeafOwnerCountRemote`|`dedupChunkReadLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteLeafOwnerCountLocal`|`dedupChunkWriteLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupChunkWriteLeafOwnerCountRemote`|`dedupChunkWriteLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadLeafOwnerCountLocal`|`dedupHashReadLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashReadLeafOwnerCountRemote`|`dedupHashReadLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteLeafOwnerCountLocal`|`dedupHashWriteLeafOwnerCountLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|dedupHashWriteLeafOwnerCountRemote`|`dedupHashWriteLeafOwnerCountRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkReadLeafOwnerLocal`|`iopsDedupChunkReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkReadLeafOwnerRemote`|`iopsDedupChunkReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkWriteLeafOwnerLocal`|`iopsDedupChunkWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupChunkWriteLeafOwnerRemote`|`iopsDedupChunkWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashReadLeafOwnerLocal`|`iopsDedupHashReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashReadLeafOwnerRemote`|`iopsDedupHashReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashWriteLeafOwnerLocal`|`iopsDedupHashWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|iopsDedupHashWriteLeafOwnerRemote`|`iopsDedupHashWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkReadLeafOwnerLocal`|`latencyAvgDedupChunkReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkReadLeafOwnerRemote`|`latencyAvgDedupChunkReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkWriteLeafOwnerLocal`|`latencyAvgDedupChunkWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupChunkWriteLeafOwnerRemote`|`latencyAvgDedupChunkWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashReadLeafOwnerLocal`|`latencyAvgDedupHashReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashReadLeafOwnerRemote`|`latencyAvgDedupHashReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashWriteLeafOwnerLocal`|`latencyAvgDedupHashWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|latencyAvgDedupHashWriteLeafOwnerRemote`|`latencyAvgDedupHashWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafCompLocal`|`tputDedupChunkReadLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafCompRemote`|`tputDedupChunkReadLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafOwnerLocal`|`tputDedupChunkReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkReadLeafOwnerRemote`|`tputDedupChunkReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafCompLocal`|`tputDedupChunkWriteLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafCompRemote`|`tputDedupChunkWriteLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafOwnerLocal`|`tputDedupChunkWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupChunkWriteLeafOwnerRemote`|`tputDedupChunkWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafCompLocal`|`tputDedupHashReadLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafCompRemote`|`tputDedupHashReadLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafOwnerLocal`|`tputDedupHashReadLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashReadLeafOwnerRemote`|`tputDedupHashReadLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafCompLocal`|`tputDedupHashWriteLeafCompLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafCompRemote`|`tputDedupHashWriteLeafCompRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafOwnerLocal`|`tputDedupHashWriteLeafOwnerLocal`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup DOM Owner\|tputDedupHashWriteLeafOwnerRemote`|`tputDedupHashWriteLeafOwnerRemote`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### ESA Dedup Chunk Service (`cluster-esa-dedup-store-chunksvc`): 33 keys per cluster, `metric_scope=all` only

ESA deduplication chunk store service, cluster total.

Lands on: ClusterComputeResource (the queried cluster's vSAN uuid). Instance: none. Lab (P3, 2026-11-07): 1 entities, 33 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyChangeRefCountUs`|`latencyChangeRefCount`|Change Ref Count Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyDeleteChunkExtentUs`|`latencyDeleteChunkExtent`|Delete Chunk Extent Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyDeletedObjCleanupUs`|`latencyDeletedObjCleanup`|Deleted Object Clean Up Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyGetChunkEntriesUs`|`latencyGetChunkEntries`|Get Chunk Entries Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|latencyReserveMbaUs`|`latencyReserveMba`|Reserve MBA Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numChangeRefCount`|`numChangeRefCount`|Change Ref Count Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numDeleteChunkExtent`|`numDeleteChunkExtent`|Delete Chunk Extent Request Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numDeletedObjCleanup`|`numDeletedObjCleanup`|Deleted Object Clean Up Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numGetChunkEntries`|`numGetChunkEntries`|Get Chunk Entries Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numObjsReported`|`numObjsReported`|Reported Chunk Objects|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|numReserveMba`|`numReserveMba`|Reserve MBA Request|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateBlocksInChunkDeleted`|`rateBlocksInChunkDeleted`|Blocks in Deleted Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateBlocksInChunkWritten`|`rateBlocksInChunkWritten`|Blocks in Written Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChangeRefCount`|`rateChangeRefCount`|Change Ref Count Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunkRefEntAdded`|`rateChunkRefEntAdded`|Added Chunk Ref Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunkRefEntRemoved`|`rateChunkRefEntRemoved`|Removed Chunk Ref Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksDeleted`|`rateChunksDeleted`|Deleted Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksInChangeRefCount`|`rateChunksInChangeRefCount`|Chunks in Change Ref Count Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksInDeleteChunkExtent`|`rateChunksInDeleteChunkExtent`|Chunks in Delete Chunk Extent Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksInGetChunkEntries`|`rateChunksInGetChunkEntries`|Chunks in Get Chunk Entries Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksWritten`|`rateChunksWritten`|Write Chunk Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateDeleteChunkExtent`|`rateDeleteChunkExtent`|Delete Chunk Extent Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateDeletedObjCleanup`|`rateDeletedObjCleanup`|Deleted Object Clean Up Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateGetChunkEntries`|`rateGetChunkEntries`|Get Chunk Entries Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateReserveMba`|`rateReserveMba`|Reserve MBA Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalNumBlks`|`totalNumBlks`|Total Number of Blocks of All Chunks|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalNumRefCnt`|`totalNumRefCnt`|Total Ref Count of All Chunks|none stated|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateAsyncTaskBatches`|`rateAsyncTaskBatches`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksOpportunisticDelete`|`rateChunksOpportunisticDelete`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksRetained`|`rateChunksRetained`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|rateChunksToDelete`|`rateChunksToDelete`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalNumBytesRetained`|`totalNumBytesRetained`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Chunk Service\|totalTputRetained`|`totalTputRetained`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### ESA Dedup Hash Service (`cluster-esa-dedup-store-hashsvc`): 39 keys per cluster, `metric_scope=all` only

ESA deduplication hash store service, cluster total.

Lands on: ClusterComputeResource (the queried cluster's vSAN uuid). Instance: none. Lab (P3, 2026-11-07): 1 entities, 39 catalog series. Feature: ESA with deduplication.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup Hash Service\|currentGenNum`|`currentGenNum`|Gen Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|currentStaleGenNum`|`currentStaleGenNum`|Current Stale Gen Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyDeleteChunksReqUs`|`latencyDeleteChunksReq`|Delete Chunk Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyNewDupReqUs`|`latencyNewDupReq`|New Dup Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyReadLockWaitUs`|`latencyReadLockWait`|Read Lock Wait Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyUniqueHashReqUs`|`latencyUniqueHashReq`|Unique Hash Request Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|latencyWriteLockWaitUs`|`latencyWriteLockWait`|Write Lock Wait Latency|microseconds|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numDeleteChunksReq`|`numDeleteChunksReq`|Delete Chunk Request Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numNewDupReq`|`numNewDupReq`|New Dup Request Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numObjsReported`|`numObjsReported`|Reported Chunk Objects|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numReadLockWait`|`numReadLockWait`|Read Lock Wait Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numUniqHashReq`|`numUniqHashReq`|Unique Hash Request Count|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|numWriteLockWait`|`numWriteLockWait`|Write Lock Wait Number|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateChunkInDeleteChunksReq`|`rateChunkInDeleteChunksReq`|Chunks in Delete Chunks Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateChunkInNewDupReq`|`rateChunkInNewDupReq`|Chunks in New Dup Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateChunkInUniqHashReq`|`rateChunkInUniqHashReq`|Chunks in Unique Hash Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDedupHashEntryAdded`|`rateDedupHashEntryAdded`|Added Dedup Hash Map Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDedupHashEntryRemoved`|`rateDedupHashEntryRemoved`|Removed Dedup Hash Map Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDedupHashEntryUpdated`|`rateDedupHashEntryUpdated`|Updated Dedup Hash Map Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDeleteChunksReq`|`rateDeleteChunksReq`|Delete Chunk Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDeletedChunks`|`rateDeletedChunks`|Deleted Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateHashKeyRemovalEntriesPerGen`|`rateHashKeyRemovalEntriesPerGen`|Hash Key Removal Entries per Gen Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateHashKeyRemovalEntriesToScanInStep`|`rateHashKeyRemovalEntriesToScanInStep`|Hash Key Removal Entries to Scan in Latest Step Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateHashKeyRemovalStepsDone`|`rateHashKeyRemovalStepsDone`|Steps Done in Current Scan Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateNewDupChunks`|`rateNewDupChunks`|Processed New Dup Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateNewDupReq`|`rateNewDupReq`|New Dup Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateOldDupChunks`|`rateOldDupChunks`|Processed Old Dup Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateReadLockWait`|`rateReadLockWait`|Read Lock Wait Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateRetryChunks`|`rateRetryChunks`|Processed Transient Retry Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateSentriesAdded`|`rateSentriesAdded`|Added Sentries Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateSentriesRemoved`|`rateSentriesRemoved`|Removed Sentries Entries Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateUniqChunks`|`rateUniqChunks`|Processed Unique Chunks Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateUniqHashReq`|`rateUniqHashReq`|Unique Hash Requests Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateWriteLockWait`|`rateWriteLockWait`|Write Lock Wait Per Second|none stated|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|capBytesPerUnitPerHashObj`|`capBytesPerUnitPerHashObj`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateDupEntries`|`rateDupEntries`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateLiveUniqEntries`|`rateLiveUniqEntries`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateStaleUniqEntries`|`rateStaleUniqEntries`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|
|`vSAN Perf\|ESA Dedup Hash Service\|rateUniqEntriesPerUnit`|`rateUniqEntriesPerUnit`|not in the Python model; served on 9.1.1 (P3)|unknown|none|all|

### Data Protection (`vsan-dp-historical-stats`): 1 keys per cluster, `metric_scope=all` only

vSAN data protection (snapshot) capacity history.

Lands on: ClusterComputeResource (the queried cluster's vSAN uuid). Instance: none. Lab (P3, 2026-11-07): 1 entities, 1 catalog series. Feature: none listed.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Data Protection\|totalSnapshotCapacity`|`totalSnapshotCapacity`|Used By Snapshots|none stated|none|all|

### Resync (`cluster-resync`): 24 keys per cluster, `metric_scope=all` only

Resync I/O by reason (decommission, fix compliance, policy change, rebalance). Id form not observed (empty on the lab).

Lands on: runtime landing: the cluster's vSAN uuid lands on the cluster, one node uuid of that cluster on that host (same group), anything else is unparsed. Instance: none. Lab (P3, 2026-11-07): empty (wildcard echo). Feature: expected empty except during a resync.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Resync\|iopsResyncReadDecom`|`iopsResyncReadDecom`|Evacuation Read IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|iopsResyncReadFixComp`|`iopsResyncReadFixComp`|Repair Read IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|iopsResyncReadPolicy`|`iopsResyncReadPolicy`|Policy Change Read IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|iopsResyncReadRebalance`|`iopsResyncReadRebalance`|Rebalance Read IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|iopsResyncWriteDecom`|`iopsResyncWriteDecom`|Evacuation Write IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|iopsResyncWriteFixComp`|`iopsResyncWriteFixComp`|Repair Write IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|iopsResyncWritePolicy`|`iopsResyncWritePolicy`|Policy Change Write IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|iopsResyncWriteRebalance`|`iopsResyncWriteRebalance`|Rebalance Write IOPS|IO/s|none|all|
|`vSAN Perf\|Resync\|latResyncReadDecomUs`|`latResyncReadDecom`|Evacuation Read Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|latResyncReadFixCompUs`|`latResyncReadFixComp`|Repair Read Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|latResyncReadPolicyUs`|`latResyncReadPolicy`|Policy Change Read Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|latResyncReadRebalanceUs`|`latResyncReadRebalance`|Rebalance Read Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|latResyncWriteDecomUs`|`latResyncWriteDecom`|Evacuation Write Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|latResyncWriteFixCompUs`|`latResyncWriteFixComp`|Repair Write Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|latResyncWritePolicyUs`|`latResyncWritePolicy`|Policy Change Write Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|latResyncWriteRebalanceUs`|`latResyncWriteRebalance`|Rebalance Write Latency|microseconds|none|all|
|`vSAN Perf\|Resync\|tputResyncReadDecomBytesPerSec`|`tputResyncReadDecom`|Evacuation Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Resync\|tputResyncReadFixCompBytesPerSec`|`tputResyncReadFixComp`|Repair Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Resync\|tputResyncReadPolicyBytesPerSec`|`tputResyncReadPolicy`|Policy Change Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Resync\|tputResyncReadRebalanceBytesPerSec`|`tputResyncReadRebalance`|Rebalance Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Resync\|tputResyncWriteDecomBytesPerSec`|`tputResyncWriteDecom`|Evacuation Write Throughput|bytes/s|none|all|
|`vSAN Perf\|Resync\|tputResyncWriteFixCompBytesPerSec`|`tputResyncWriteFixComp`|Repair Write Throughput|bytes/s|none|all|
|`vSAN Perf\|Resync\|tputResyncWritePolicyBytesPerSec`|`tputResyncWritePolicy`|Policy Change Write Throughput|bytes/s|none|all|
|`vSAN Perf\|Resync\|tputResyncWriteRebalanceBytesPerSec`|`tputResyncWriteRebalance`|Rebalance Write Throughput|bytes/s|none|all|

### PMem (`cluster-pmem`): 8 keys per cluster, `metric_scope=all` only

Persistent memory I/O, cluster total.

Lands on: the cluster only: its vSAN uuid lands on the cluster; a node uuid is not landed on a host, because `host-pmem` writes the same `PMem` keys there (counted in `entities_unparsed`). Instance: none. Lab (P3, 2026-11-07): fault `NotSupported` `pmemisunavailable` (learned at the first fault). Feature: PMem storage.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|PMem\|bandwidthReadBytesPerSec`|`bandwidthRead`|Read Bandwidth|bytes/s|none|all|
|`vSAN Perf\|PMem\|bandwidthTotalBytesPerSec`|`bandwidthTotal`|Total Bandwidth|bytes/s|none|all|
|`vSAN Perf\|PMem\|bandwidthWriteBytesPerSec`|`bandwidthWrite`|Write Bandwidth|bytes/s|none|all|
|`vSAN Perf\|PMem\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|PMem\|iopsTotal`|`iopsTotal`|Total IOPS|IO/s|none|all|
|`vSAN Perf\|PMem\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|PMem\|latencyRead`|`latencyRead`|vSAN Layer Read Latency|disputed: Python microseconds, schema nanoseconds; pushed verbatim|none|all|
|`vSAN Perf\|PMem\|latencyWrite`|`latencyWrite`|vSAN Layer Write Latency|disputed: Python microseconds, schema nanoseconds; pushed verbatim|none|all|

### Remote DOM Client (`cluster-remotedomclient`): 8 keys per cluster, `metric_scope=all` only

HCI Mesh: I/O of remote clients against this cluster's datastore (which cluster the id names is unobserved).

Lands on: ClusterComputeResource, only when the id is the queried cluster's vSAN uuid (remote rule, below). Instance: none. Lab (P3, 2026-11-07): empty (wildcard echo). Feature: HCI Mesh.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Remote DOM Client\|congestion`|`congestion`|Congestions|none stated|none|all|
|`vSAN Perf\|Remote DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|Remote DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|Remote DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|all|
|`vSAN Perf\|Remote DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|all|
|`vSAN Perf\|Remote DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|all|
|`vSAN Perf\|Remote DOM Client\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|Remote DOM Client\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|

### Compute Remote DOM Client (`computeCluster-remotedomclient`): 8 keys per cluster, `metric_scope=all` only

HCI Mesh: compute-only cluster side of remote I/O (client or server side unobserved).

Lands on: ClusterComputeResource, only when the id is the queried cluster's vSAN uuid (remote rule). Instance: none. Lab (P3, 2026-11-07): empty (wildcard echo). Feature: HCI Mesh.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Compute Remote DOM Client\|congestion`|`congestion`|Congestions|none stated|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|Compute Remote DOM Client\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|

## Keys on VMWARE HostSystem: disk tier (`tier_disk`, 31 keys, 126 under `all`, default off)

Instanced by the vSAN disk uuid (`ESA Disk Layer:52d171f6-...`), on the host
the disk map read the disk from. On ESA the two `vsan-esa-disk-*` types carry
data and `cache-disk` / `capacity-disk` answer empty (learned after 3
cycles); on OSA the reverse is expected (not observed). 24 of the 31 names
also exist as `vSAN|Performance|*` on the built-in vSAN adapter's disk
objects. On the lab: 10 series per disk.

### Cache Disk:<disk uuid> (`cache-disk`): 8 keys per disk

OSA cache-tier disk. Empty on ESA; learned-skipped after three empty cycles. 8 of 8 names match built-in OSA disk keys (name match only).

Lands on: HostSystem (the host the disk map read it from). Instance: `<disk uuid>` = the vSAN disk uuid from the entity id. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Cache Disk:<disk uuid>\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevRead`|dashboard|
|`vSAN Perf\|Cache Disk:<disk uuid>\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevWrite`|dashboard|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevDAvgUs`|`latencyDevDAvg`|Device Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevDAvg`|dashboard|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevGAvgUs`|`latencyDevGAvg`|Guest Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevGAvg`|dashboard|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevRead`|dashboard|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevWrite`|dashboard|
|`vSAN Perf\|Cache Disk:<disk uuid>\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevRead`|dashboard|
|`vSAN Perf\|Cache Disk:<disk uuid>\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevWrite`|dashboard|

### Capacity Disk:<disk uuid> (`capacity-disk`): 13 keys per disk

OSA capacity-tier disk. Empty on ESA; learned-skipped. 8 of 13 names match built-in keys .

Lands on: HostSystem. Instance: `<disk uuid>`. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|deleteCongestion`|`deleteCongestion`|vSAN Layer Delete Congestion|bytes|none; Python unit says bytes; left unsuffixed|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevRead`|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevWrite`|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevDAvgUs`|`latencyDevDAvg`|Device Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevDAvg`|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevGAvgUs`|`latencyDevGAvg`|Guest Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevGAvg`|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevRead`|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevWrite`|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevRead`|dashboard|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevWrite`|dashboard|

### ESA Disk Layer:<disk uuid> (`vsan-esa-disk-layer`): 38 keys per disk (5 at dashboard scope)

vSAN-layer I/O per ESA disk. 4 of 5 names match built-in `Vsan2Disk` `vSAN|Performance|*` keys .

Lands on: HostSystem. Instance: `<disk uuid>`. Lab: 5 entities, 25 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatReadCapacity`|`avgLatReadCapacity`|vSAN Layer Average Read Latency of vSAN ESA Disk|none stated|none; name matches built-in `vSAN\|Performance\|avgLatReadCapacity`|dashboard|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatWriteCapacity`|`avgLatWriteCapacity`|vSAN Layer Average Write Latency of vSAN ESA Disk|none stated|none; name matches built-in `vSAN\|Performance\|avgLatWriteCapacity`|dashboard|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatWritePerf`|`avgLatWritePerf`|Latency write perf (average)|none stated|none|dashboard|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsReadCapacity`|`iopsReadCapacity`|vSAN Layer Read IOPS of vSAN ESA Disk|IO/s|none; name matches built-in `vSAN\|Performance\|iopsReadCapacity`|dashboard|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsWriteCapacity`|`iopsWriteCapacity`|vSAN Layer Write IOPS of vSAN ESA Disk|IO/s|none; name matches built-in `vSAN\|Performance\|iopsWriteCapacity`|dashboard|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatReadPerf`|`avgLatReadPerf`|Latency read perf (average)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatUnmapCapacity`|`avgLatUnmapCapacity`|Latency unmap capacity (average)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatUnmapPerf`|`avgLatUnmapPerf`|Latency unmap perf (average)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsReadPerf`|`iopsReadPerf`|IOPS read perf|IO/s|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsUnmapCapacity`|`iopsUnmapCapacity`|IOPS unmap capacity|IO/s|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsUnmapPerf`|`iopsUnmapPerf`|IOPS unmap perf|IO/s|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsWritePerf`|`iopsWritePerf`|IOPS write perf|IO/s|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|maxReadTimeCapacity`|`maxReadTimeCapacity`|Read time capacity (max)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|maxReadTimePerf`|`maxReadTimePerf`|Read time perf (max)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|maxUnmapTimeCapacity`|`maxUnmapTimeCapacity`|Unmap time capacity (max)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|maxUnmapTimePerf`|`maxUnmapTimePerf`|Unmap time perf (max)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|maxWriteTimeCapacity`|`maxWriteTimeCapacity`|Write time capacity (max)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|maxWriteTimePerf`|`maxWriteTimePerf`|Write time perf (max)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|minReadTimeCapacity`|`minReadTimeCapacity`|Read time capacity (min)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|minReadTimePerf`|`minReadTimePerf`|Read time perf (min)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|minUnmapTimeCapacity`|`minUnmapTimeCapacity`|Unmap time capacity (min)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|minUnmapTimePerf`|`minUnmapTimePerf`|Unmap time perf (min)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|minWriteTimeCapacity`|`minWriteTimeCapacity`|Write time capacity (min)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|minWriteTimePerf`|`minWriteTimePerf`|Write time perf (min)|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|readCountCapacity`|`readCountCapacity`|Read count capacity|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|readCountPerf`|`readCountPerf`|Read count perf|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputReadCapacity`|`tputReadCapacity`|vSAN Layer Read Throughput of vSAN ESA Disk|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputReadDiskCapacity`|`tputReadDiskCapacity`|Throughput read disk capacity|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputReadDiskPerf`|`tputReadDiskPerf`|Throughput read disk perf|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputReadPerf`|`tputReadPerf`|Throughput read perf|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputUnmapCapacity`|`tputUnmapCapacity`|Throughput unmap capacity|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputUnmapPerf`|`tputUnmapPerf`|Throughput unmap perf|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputWriteCapacity`|`tputWriteCapacity`|vSAN Layer Write Throughput of vSAN ESA Disk|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|tputWritePerf`|`tputWritePerf`|Throughput write perf|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|unmapCountCapacity`|`unmapCountCapacity`|Unmap count capacity|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|unmapCountPerf`|`unmapCountPerf`|Unmap count perf|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|writeCountCapacity`|`writeCountCapacity`|Write count capacity|none stated|none|all|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|writeCountPerf`|`writeCountPerf`|Write count perf|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 190 catalog series.

### ESA Disk SCSI:<disk uuid> (`vsan-esa-disk-scsifw`): 16 keys per disk (5 at dashboard scope)

Device (SCSI framework) latency per ESA disk. 4 of 5 names match built-in `Vsan2Disk` keys .

Lands on: HostSystem. Instance: `<disk uuid>`. Lab: 5 entities, 25 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevDAvgUs`|`latencyDevDAvg`|Device Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevDAvg`|dashboard|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevGAvgUs`|`latencyDevGAvg`|Guest Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevGAvg`|dashboard|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevKAvgUs`|`latencyDevKAvg`|Latency dev k (average)|microseconds|none|dashboard|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevRead`|dashboard|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevWrite`|dashboard|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|ioCountDevRead`|`ioCountDevRead`|IO count dev (read)|none stated|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|ioCountDevUnmap`|`ioCountDevUnmap`|IO count dev unmap|none stated|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|ioCountDevWrite`|`ioCountDevWrite`|IO count dev (write)|none stated|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|iopsDevUnmap`|`iopsDevUnmap`|IOPS dev unmap|IO/s|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevUnmapUs`|`latencyDevUnmap`|Latency dev unmap|microseconds|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|outstandingCmdCount`|`outstandingCmdCount`|Outstanding cmd count|none stated|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|throughputDevUnmapBytesPerSec`|`throughputDevUnmap`|Throughput dev unmap|bytes/s|none|all|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 5 entities, 80 catalog series.


Build 19, `metric_scope=all` only: 3 more disk-tier types.

### Disk Group:<disk uuid> (`disk-group`): 38 keys per disk group, `metric_scope=all` only

OSA disk group scheduler, congestion and resync. Empty on ESA.

Lands on: HostSystem, through the disk map (OSA, unverified). Instance: `<disk uuid>` from the entity id (which disk names the group is unobserved). Lab (P3, 2026-11-07): empty (wildcard echo). Feature: OSA.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Disk Group:<disk uuid>\|componentCongestionReadSched`|`componentCongestionReadSched`|Read Component|none stated|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|componentCongestionWriteSched`|`componentCongestionWriteSched`|Write Component|none stated|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|diskgroupCongestionReadSched`|`diskgroupCongestionReadSched`|Read Disk Group|none stated|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|diskgroupCongestionWriteSched`|`diskgroupCongestionWriteSched`|Write Disk Group|none stated|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsDelayPctSched`|`iopsDelayPctSched`|IO Delay Percentage|percent|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsDirectSched`|`iopsDirectSched`|Direct Scheduled IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncRead`|`iopsResyncRead`|Resync Read IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncReadDecom`|`iopsResyncReadDecom`|Evacuation Read IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncReadFixComp`|`iopsResyncReadFixComp`|Repair Read IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncReadPolicy`|`iopsResyncReadPolicy`|Policy Change Read IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncReadRebalance`|`iopsResyncReadRebalance`|Rebalance Read IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncWrite`|`iopsResyncWrite`|Resync Write IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncWriteDecom`|`iopsResyncWriteDecom`|Evacuation Write IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncWriteFixComp`|`iopsResyncWriteFixComp`|Repair Write IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncWritePolicy`|`iopsResyncWritePolicy`|Policy Change Write IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|iopsResyncWriteRebalance`|`iopsResyncWriteRebalance`|Rebalance Write IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncReadUs`|`latResyncRead`|Resync Read Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncReadDecomUs`|`latResyncReadDecom`|Evacuation Read Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncReadFixCompUs`|`latResyncReadFixComp`|Repair Read Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncReadPolicyUs`|`latResyncReadPolicy`|Policy Change Read Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncReadRebalanceUs`|`latResyncReadRebalance`|Rebalance Read Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncWriteUs`|`latResyncWrite`|Resync Write Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncWriteDecomUs`|`latResyncWriteDecom`|Evacuation Write Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncWriteFixCompUs`|`latResyncWriteFixComp`|Repair Write Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncWritePolicyUs`|`latResyncWritePolicy`|Policy Change Write Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latResyncWriteRebalanceUs`|`latResyncWriteRebalance`|Rebalance Write Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|latencySchedUs`|`latencySched`|Scheduled Latency|microseconds|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|outstandingBytesSched`|`outstandingBytesSched`|Outstanding Bytes|disputed: bytes or bytes/s; pushed verbatim|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncReadBytesPerSec`|`tputResyncRead`|Resync Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncReadDecomBytesPerSec`|`tputResyncReadDecom`|Evacuation Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncReadFixCompBytesPerSec`|`tputResyncReadFixComp`|Repair Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncReadPolicyBytesPerSec`|`tputResyncReadPolicy`|Policy Change Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncReadRebalanceBytesPerSec`|`tputResyncReadRebalance`|Rebalance Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncWriteBytesPerSec`|`tputResyncWrite`|Resync Write Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncWriteDecomBytesPerSec`|`tputResyncWriteDecom`|Evacuation Write Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncWriteFixCompBytesPerSec`|`tputResyncWriteFixComp`|Repair Write Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncWritePolicyBytesPerSec`|`tputResyncWritePolicy`|Policy Change Write Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Group:<disk uuid>\|tputResyncWriteRebalanceBytesPerSec`|`tputResyncWriteRebalance`|Rebalance Write Throughput|bytes/s|none|all|

### CLOM Disk:<disk uuid> (`clom-disk`): 3 keys per disk, `metric_scope=all` only

CLOM component count ratios per disk.

Lands on: HostSystem, through the disk map. Instance: `<disk uuid>`. Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|CLOM Disk:<disk uuid>\|diskCompCntRatio`|`diskCompCntRatio`|Disk Component Count Ratio|none stated|none|all|
|`vSAN Perf\|CLOM Disk:<disk uuid>\|diskDataCompCntRatio`|`diskDataCompCntRatio`|Disk Data Component Count Ratio|none stated|none|all|
|`vSAN Perf\|CLOM Disk:<disk uuid>\|diskgrpDataCompCntRatio`|`diskgrpDataCompCntRatio`|Diskgroup data Component Count Ratio|none stated|none|all|

### Disk Health:<disk uuid> (`ddh-disk`): 10 keys per disk, `metric_scope=all` only

Device health I/O averages per disk.

Lands on: HostSystem, through the disk map. Instance: `<disk uuid>`. Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: not gated.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgReadIOPS`|`avgReadIOPS`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgReadLatency`|`avgReadLatency`|Read Latency|disputed: Python nanoseconds, schema milliseconds; pushed verbatim|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgReadTPutBytesPerSec`|`avgReadTPut`|Read Throughput|bytes/s|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgUnmapIOPS`|`avgUnmapIOPS`|Unmapped IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgUnmapLatencyUs`|`avgUnmapLatency`|Unmapped Latency|microseconds|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgUnmapTPut`|`avgUnmapTPut`|Unmapped Throughput|none stated|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgWriteIOPS`|`avgWriteIOPS`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgWriteLatency`|`avgWriteLatency`|Write Latency|disputed: Python nanoseconds, schema milliseconds; pushed verbatim|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|avgWriteTPut`|`avgWriteTPut`|Write Throughput|none stated|none|all|
|`vSAN Perf\|Disk Health:<disk uuid>\|logCongestion`|`logCongestion`|Log Congestion|none stated|none|all|

## Keys on VMWARE VirtualMachine: VM tier (`tier_vm`, 21 keys, 38 under `all`, default off)

Runs on every Nth cycle per cluster (`vm_tier_every_n_cycles`, default 3,
staggered so 1/N of the clusters run each cycle). `virtual-disk` and `vscsi`
are instanced by bus position (`scsi0:0`), so a VMDK's two groups share one
instance. On the lab: 80 series requested per VM, about 60 with a value.

### VM (`virtual-machine`): 8 keys per VM (6 at dashboard scope)

vSAN I/O per VM, as the VM's DOM client saw it.

Lands on: VirtualMachine. Instance: none. Lab: 30 entities, 180 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|VM\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|VM\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|VM\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|VM\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|VM\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|dashboard|
|`vSAN Perf\|VM\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|dashboard|
|`vSAN Perf\|VM\|readCount`|`readCount`|Read count|none stated|none|all|
|`vSAN Perf\|VM\|writeCount`|`writeCount`|Write count|none stated|none|all|

Under `metric_scope=all` (P3, 2026-11-07, no labels filter): data, 30 entities, 240 catalog series.

### Virtual Disk:<busN:M> (`virtual-disk`): 7 keys per VMDK

Per-VMDK normalized IOPS and IOPS limit. The 4 `NIOPSRead*`/`NIOPSWrite*` labels were `None` for all 148 entities on the lab; they stay requested and documented, and push nothing while `None`.

Lands on: VirtualMachine. Instance: `<busN:M>` = the VMDK's bus position from the device read (`scsi0:0`, see "Where keys land"). Lab: 148 entities, 1036 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPS`|`NIOPS`|Normalized IOPS|IO/s|none|dashboard|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSDelayed`|`NIOPSDelayed`|Delayed Normalized IOPS|IO/s|none|dashboard|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSRead`|`NIOPSRead`|Niops (read)|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|dashboard|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSReadDelayed`|`NIOPSReadDelayed`|Niops read delayed|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|dashboard|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSWrite`|`NIOPSWrite`|Niops (write)|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|dashboard|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSWriteDelayed`|`NIOPSWriteDelayed`|Niops write delayed|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|dashboard|
|`vSAN Perf\|Virtual Disk:<busN:M>\|iopsLimit`|`iopsLimit`|Normalized IOPS Limit|IO/s|none|dashboard|

### vSCSI:<scsiN:M> (`vscsi`): 8 keys per virtual SCSI device

I/O per virtual SCSI device.

Lands on: VirtualMachine. Instance: `<scsiN:M>` = the device part of the entity id (`scsi0:0`), same shape as the vCenter adapter's own `virtualDisk:scsi0:0`. Lab: 148 entities, 1184 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|vSCSI:<scsiN:M>\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|dashboard|
|`vSAN Perf\|vSCSI:<scsiN:M>\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|dashboard|
|`vSAN Perf\|vSCSI:<scsiN:M>\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|dashboard|
|`vSAN Perf\|vSCSI:<scsiN:M>\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|dashboard|
|`vSAN Perf\|vSCSI:<scsiN:M>\|readCount`|`readCount`|Read count|none stated|none|dashboard|
|`vSAN Perf\|vSCSI:<scsiN:M>\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|dashboard|
|`vSAN Perf\|vSCSI:<scsiN:M>\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|dashboard|
|`vSAN Perf\|vSCSI:<scsiN:M>\|writeCount`|`writeCount`|Write count|none stated|none|dashboard|

Build 19, `metric_scope=all` only: 3 more VM-tier types, on the same cadence.

### Remote VM (`remote-virtual-machine`): 6 keys per VM, `metric_scope=all` only

HCI Mesh: VM I/O against a remote datastore.

Lands on: VirtualMachine, only when the VM uuid is in the queried cluster's VM read (remote rule). Instance: none. Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: HCI Mesh.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Remote VM\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|Remote VM\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|Remote VM\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|all|
|`vSAN Perf\|Remote VM\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|all|
|`vSAN Perf\|Remote VM\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|Remote VM\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|

### Remote Virtual Disk:<busN:M> (`remote-virtual-disk`): 3 keys per VMDK, `metric_scope=all` only

HCI Mesh: per-VMDK normalized IOPS on a remote datastore.

Lands on: VirtualMachine, through the queried cluster's device map like `virtual-disk` (remote rule). Instance: `<busN:M>` from the device read; an id that is not a path is never placed. Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: HCI Mesh.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Remote Virtual Disk:<busN:M>\|NIOPS`|`NIOPS`|Normalized IOPS|IO/s|none|all|
|`vSAN Perf\|Remote Virtual Disk:<busN:M>\|NIOPSDelayed`|`NIOPSDelayed`|Delayed Normalized IOPS|IO/s|none|all|
|`vSAN Perf\|Remote Virtual Disk:<busN:M>\|iopsLimit`|`iopsLimit`|Normalized IOPS Limit|IO/s|none|all|

### Remote vSCSI:<scsiN:M> (`remote-vscsi`): 6 keys per virtual SCSI device, `metric_scope=all` only

HCI Mesh: vSCSI I/O against a remote datastore.

Lands on: VirtualMachine, only when the VM uuid is in the queried cluster's VM read (remote rule). Instance: `<scsiN:M>`. Lab (P3, 2026-11-07): fault `InvalidArgument`; faults on every ref form tried, wildcard and explicit (P3), so it is an optional type learned after three consecutive faults (design Q5). Feature: HCI Mesh.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|Scope|
|---|---|---|---|---|---|
|`vSAN Perf\|Remote vSCSI:<scsiN:M>\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|all|
|`vSAN Perf\|Remote vSCSI:<scsiN:M>\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|all|
|`vSAN Perf\|Remote vSCSI:<scsiN:M>\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|all|
|`vSAN Perf\|Remote vSCSI:<scsiN:M>\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|all|
|`vSAN Perf\|Remote vSCSI:<scsiN:M>\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|all|
|`vSAN Perf\|Remote vSCSI:<scsiN:M>\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|all|


Units come from the Python pack's labels (harvested from HCIBench), not from
the API; the microsecond and byte-rate readings agree with the captured
magnitudes but are not proven by the API.

**Leaves never change.** A key's leaf (and its unit suffix) is fixed when
the key is first released; a unit settled later changes the Unit column here,
never the key. The `all`-scope leaves follow the design v3 leaf rule, frozen
from the Python pack 2.0.0-rc.2 units: a label already released on any type
reuses that leaf; per mille gets `Pct` (divided by 10); a label with no unit,
an unknown unit or IOPS gets no suffix; `Us`, `BytesPerSec`, `Bytes`, `Ms`,
`Sec`, `Ns`, `Pct` are appended unless the label already has the token as a
word. Eight leaves stay verbatim because their unit is disputed:
`cluster-pmem` and `host-pmem` `latencyRead` / `latencyWrite` (Python
microseconds, schema nanoseconds), `ddh-disk` `avgReadLatency` /
`avgWriteLatency` (Python nanoseconds, schema milliseconds), `disk-group`
`outstandingBytesSched` (bytes or a rate), and `system-mem` `pctMemUsed`
(which P3 showed to be an integer percent: pushed as served, no transform).
The 210 P3 labels have no known unit and no suffix. The 41 `Actual` twins
the service returns under `all` (identical values) are not keys.

## Logging

Every pak line goes through the adapter's own logger, into
`VsanPerfAdapter_<instance id>.log` on the collector. INFO every cycle,
including a failed one: one summary line, `cycle <n> summary: clusters
ok=.. failed=.. timed_out=.. of ..; perf calls=..; series pushed=.. (core ..
host .. cluster .. disk .. vm ..; .. already pushed); unresolved hosts=..
clusters=.. disks=.. vms=.. vdisks=..; unparsed=..; learned skips=..;
optional calls failed=..; push batches ok=.. failed=..; scope=dashboard|all; <s> s` (the tiers
and unresolved classes listed are the ones switched on),
with `; FAILED: <reason>` on a failed cycle. WARN: a failed cluster (fault class and
key), an optional type's failure (once per cluster and type per refresh),
an unclean push, unresolved hosts, clusters, disks and VMs (once per
refresh), a failed `QueryDisksForVsan` or VM read (once until it succeeds),
an entity id of the wrong form or a runtime-landing id of an unknown form
(once per type per day, part count only), and entity types to exclude this
build does not know (configure), and (build 19) remote (HCI Mesh) ids that
are not objects of the queried cluster (once per Ops host map refresh). INFO
since build 19: once per type per day, the names of the labels the service
returned that the scope's catalog lacks (names only, never an id); a Q5
type learned after three faults. With
**Performance timing logging** on (re-read every cycle, so a change takes
effect on the next cycle without a restart), `key=value` lines prefixed `PERF call`,
`PERF cluster`, `PERF pool`, `PERF push`, `PERF inventory`, `PERF tier`,
`PERF identity` and `PERF cycle` (perf phase wall ms, calls/s from it,
summed and average call ms, push ms, heap before and after). Since builds 10
and 11: `tier=` and `parse=stream` on `PERF call`;
`calls_by_tier=core:..,host:..` (the tiers switched on) and, with the VM
tier, `vm_run=yes|no` on `PERF cluster`; `vm_permit_wait_ms_max` on
`PERF pool` with the VM tier; `kind=HostSystem|ClusterComputeResource|VirtualMachine`
on `PERF push`; `calls_<tier>=` per tier switched on, `marks_entities=` and
`marks_bytes_est=` on `PERF cycle`; one `PERF tier` line per tier per cycle
(`calls`, `call_ms_sum`, `wire_bytes`, `decoded_bytes`, `entities`,
`series_kept`, `series_pushed`, `unresolved`, `learned_skips`, `failed`,
and since build 19 `scope` and `labels_dropped`, the series whose label is
not in the scope's catalog);
and `PERF identity` for each identity read: `read=ops_clusters|ops_vms`
(`pages`, `ms`, `count`, `trigger=refresh|change|miss`), `read=disk_map`
(`cluster`, `calls`, `ms`, `count`, `failed`, `trigger`) and `read=vm_read`
(`cluster`, `pages`, `ms`, `wire_bytes`, `decoded_bytes`, `count`, `disks`,
`devices`, `trigger`). Never the ContainerView id. No log line, exception message or property carries
the password or the session token. The ContainerView id (`session[...]...`,
it embeds the vCenter session key) is never logged by this pack, a fault on
the view is passed on with fixed text only, and every vSphere fault text is
scrubbed of view ids before it is used.

A failed cycle is a failed collect of the collector object: the framework
logs it at WARN (stack trace on the first failure of a streak, one line
after), the object shows NO_DATA_RECEIVING and the account row DOWN (vCenter
unreachable) or ERROR, with the same credential-free message.
