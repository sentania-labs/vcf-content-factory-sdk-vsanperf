# VCF Content Factory vSAN Performance: Reference

Version 1.1.0, build 11 (Build 2 of the design: core, host, cluster, disk
and VM tiers, 33 entity types, 451 keys). Design: the factory's
`knowledge/designs/vsanperf-mp-v1.md` and, for everything it changes,
`knowledge/designs/vsanperf-mp-v2.md` with its P2 addendum.

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
| Entity types to exclude | `exclude_types` | empty | no (Advanced) | comma list of entity type ids (`ioinsight,vsan-file-service`), applied after the tier switches; may name core types; an id this build does not collect is a configure WARN and ignored; a list that leaves nothing in scope is a configuration error (Test Connection says so) |

An account saved before build 10 has no stored tier values and gets the
defaults: host and cluster tiers on, disk and VM tiers off. Set both to `false` for Build 1 behaviour. Tier
switches apply from the first cycle after the account is saved.

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
| `Collection\|clusters_unresolved` | Clusters unresolved | build 10: cluster-tier data that could not be placed: a vSAN cluster uuid that is not the queried cluster's, or a cluster with no ClusterComputeResource object of this vCenter |
| `Collection\|disks_unresolved` | Disks unresolved | build 11: vSAN disk uuids in the disk-tier data that the disk map places on no host (a host whose `QueryDisksForVsan` failed, or a disk added since the last map read) |
| `Collection\|vms_unresolved` | VMs unresolved | build 11: VM instanceUuids not in the cluster's VM read, and VMs with no VMWARE VirtualMachine object of this vCenter |
| `Collection\|vdisks_unresolved` | Virtual disks unresolved | build 11: vdisk paths not in the device map, or on an unknown controller type or a path two disks share (never guessed) |
| `Collection\|optional_calls_failed` | Optional calls failed | build 11 (review W1): failed calls of non-core types; each is named in `last_error` and logged once per cluster and type per refresh, and the cluster still counts as collected |
| `Collection\|entities_unparsed` | Entities unparsed | build 10: entities skipped for their id form (a wrong part count, or an id of the three runtime-landing types that is neither the cluster's uuid nor one of its node uuids) |

## Collection per cycle

One wildcard `VsanPerfQueryPerf` call per entity type in scope per vSAN
cluster, in this order: core (DOM owner, DOM client, pNIC), the cluster,
host, disk and VM tiers, so a deadline cut loses the tail first (the VM
types, which also run on a cadence). Calls per cycle on the lab (one 5-host
ESA cluster):

| Tiers | First 3 cycles | Steady (empty types learned) |
|---|---|---|
| default (core + host + cluster) | 26 | 23 |
| all, on a cycle without the cluster's VM run | 30 | 25 |
| all, on the cluster's VM run (every 3rd cycle) | 33 | 28 |

Responses are parsed from the gzip stream one entity at a time; only the
wire bytes are held, capped at 4 MB per call (16 MB for the VM types). VM
type calls run one at a time across all workers.

**Learned types.** A type a cluster does not offer (`NotSupported`, or an
`InvalidArgument` naming `entityRefId`) is not queried there for 24 hours.
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
- **Runtime landing** for `vsan-direct-cluster`, `vsan-file-service` and
  `ioinsight`, whose id form has not been observed: an id equal to the
  cluster's vSAN uuid lands on the cluster; an id equal to one node uuid of
  that cluster lands on that host under the same group name; anything else
  is skipped, counted in `entities_unparsed`, and logged once per type per
  day with its part count (not the id).

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

## Keys on VMWARE HostSystem: core (always on)

`<vmnic>` is the NIC name (`vmnic1`); one instance per vSAN physical NIC the
service reports.

### DOM Owner (`host-domowner`): 38 keys per host

The owner-side I/O for the vSAN objects this host owns: the DOM owner
coordinates each object's I/O, whichever host's VMs issued it. A busy DOM
Owner on a host means objects it owns are busy, not necessarily its own VMs.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|DOM Owner\|highOIODurationPercentWrite`|`highOIODurationPercentWrite`|High outstanding IO duration percent (write)|none stated|none|
|`vSAN Perf\|DOM Owner\|iops`|`iops`|IOPS|IO/s|none|
|`vSAN Perf\|DOM Owner\|iopsSegCleanerUnmap`|`iopsSegCleanerUnmap`|IOPS seg cleaner unmap|IO/s|none|
|`vSAN Perf\|DOM Owner\|iopsUnmap`|`iopsUnmap`|Unmap IOPS|IO/s|none|
|`vSAN Perf\|DOM Owner\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyAvgSegCleanerUnmapUs`|`latencyAvgSegCleanerUnmap`|Latency avg seg cleaner unmap|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyAvgUnmapUs`|`latencyAvgUnmap`|Unmap Latency|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyMaxReadUs`|`latencyMaxRead`|Latency (max, read)|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyMaxSegCleanerUnmapUs`|`latencyMaxSegCleanerUnmap`|Latency max seg cleaner unmap|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyMaxUnmapUs`|`latencyMaxUnmap`|Latency max unmap|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyMaxWriteUs`|`latencyMaxWrite`|Latency (max, write)|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyStddevSegCleanerUnmapUs`|`latencyStddevSegCleanerUnmap`|Latency standard deviation seg cleaner unmap|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyStddevWriteUs`|`latencyStddevWrite`|Latency standard deviation (write)|microseconds|none|
|`vSAN Perf\|DOM Owner\|numNetSchedControllerIopsLimit`|`numNetSchedControllerIopsLimit`|Num net sched controller IOPS limit|IO/s|none|
|`vSAN Perf\|DOM Owner\|numNetSchedGuestLTLatUs`|`numNetSchedGuestLTLatUs`|Num net sched guest lt latency us|microseconds|none|
|`vSAN Perf\|DOM Owner\|numNetSchedLowBand`|`numNetSchedLowBand`|Num net sched low band|none stated|none|
|`vSAN Perf\|DOM Owner\|numNetSchedLowBandSec`|`numNetSchedLowBandSec`|Num net sched low band sec|seconds|none|
|`vSAN Perf\|DOM Owner\|numNetSchedLowBandThreshUs`|`numNetSchedLowBandThreshUs`|Num net sched low band thresh us|microseconds|none|
|`vSAN Perf\|DOM Owner\|numNetSchedLowToMidBand`|`numNetSchedLowToMidBand`|Num net sched low to mid band|none stated|none|
|`vSAN Perf\|DOM Owner\|numNetSchedMidBand`|`numNetSchedMidBand`|Num net sched mid band|none stated|none|
|`vSAN Perf\|DOM Owner\|numNetSchedMidBandSec`|`numNetSchedMidBandSec`|Num net sched mid band sec|seconds|none|
|`vSAN Perf\|DOM Owner\|numNetSchedMidToLowBand`|`numNetSchedMidToLowBand`|Num net sched mid to low band|none stated|none|
|`vSAN Perf\|DOM Owner\|numNetSchedRollingAvgLatUs`|`numNetSchedRollingAvgLatUs`|Num net sched rolling avg latency us|microseconds|none|
|`vSAN Perf\|DOM Owner\|numNetSchedSeenNonResyncTpBps`|`numNetSchedSeenNonResyncTpBps`|Num net sched seen non resync tp bps|bytes/s|none|
|`vSAN Perf\|DOM Owner\|numNetSchedSeenTotalTpBps`|`numNetSchedSeenTotalTpBps`|Num net sched seen total tp bps|bytes/s|none|
|`vSAN Perf\|DOM Owner\|oio`|`oio`|Outstanding IO|none stated|none|
|`vSAN Perf\|DOM Owner\|owner2PCCommitLatencyAvgUs`|`owner2PCCommitLatencyAvgUs`|Owner2 pc commit latency avg us|microseconds|none|
|`vSAN Perf\|DOM Owner\|readLeafOwnerLatencyLocalUs`|`readLeafOwnerLatencyLocal`|Read leaf owner latency local|microseconds|none|
|`vSAN Perf\|DOM Owner\|readLeafOwnerLatencyRemoteUs`|`readLeafOwnerLatencyRemote`|Read leaf owner latency remote|microseconds|none|
|`vSAN Perf\|DOM Owner\|recoveryWriteCongestion`|`recoveryWriteCongestion`|Resync Read Congestions (sic, Python pack label)|none stated|none|
|`vSAN Perf\|DOM Owner\|segCleanerUnmapCongestion`|`segCleanerUnmapCongestion`|Seg cleaner unmap congestion|none stated|none|
|`vSAN Perf\|DOM Owner\|segCleanerUnmapLeafOwnerMaxLatencyAvgUs`|`segCleanerUnmapLeafOwnerMaxLatencyAvgUs`|Seg cleaner unmap leaf owner max latency avg us|microseconds|none|
|`vSAN Perf\|DOM Owner\|serverSwitchCount`|`serverSwitchCount`|Server switch count|none stated|none|
|`vSAN Perf\|DOM Owner\|throughputSegCleanerUnmapBytesPerSec`|`throughputSegCleanerUnmap`|Throughput seg cleaner unmap|bytes/s|none|
|`vSAN Perf\|DOM Owner\|unmapCount`|`unmapCount`|Unmap count|none stated|none|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerLatencyLocalUs`|`writeLeafOwnerLatencyLocal`|Write leaf owner latency local|microseconds|none|
|`vSAN Perf\|DOM Owner\|writeLeafOwnerLatencyRemoteUs`|`writeLeafOwnerLatencyRemote`|Write leaf owner latency remote|microseconds|none|

### DOM Client (`host-domclient`): 6 keys per host

The I/O this host's VMs issued, as seen from the client side, whichever
hosts own the objects. This is the closest group to "what my VMs on this
host experienced".

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|DOM Client\|congestion`|`congestion`|Congestions|none stated|none|
|`vSAN Perf\|DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|

### pNIC (`vsan-pnic-net`): 17 keys per vSAN physical NIC

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|pNIC:<vmnic>\|ioChainDrops`|`ioChainDrops`|IO chain drops|none stated|none|
|`vSAN Perf\|pNIC:<vmnic>\|ioChainRxdrops`|`ioChainRxdrops`|IO chain RX drops|none stated|none|
|`vSAN Perf\|pNIC:<vmnic>\|ioChainTxdrops`|`ioChainTxdrops`|IO chain TX drops|none stated|none|
|`vSAN Perf\|pNIC:<vmnic>\|pauseCountPct`|`pauseCount`|pNic 802.3x Pause Rate|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|pfcCountRaw`|`pfcCountRaw`|PFC count raw|none stated|none|
|`vSAN Perf\|pNIC:<vmnic>\|portRxDropsPct`|`portRxDrops`|Inbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|portTxDropsPct`|`portTxDrops`|Outbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|portTxpkts`|`portTxpkts`|Port TX packets|none stated|none|
|`vSAN Perf\|pNIC:<vmnic>\|rxCrcErrPct`|`rxCrcErr`|pNIC RX CRC Error|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|rxCrcErrRaw`|`rxCrcErrRaw`|RX CRC errors raw|none stated|none|
|`vSAN Perf\|pNIC:<vmnic>\|rxErrPct`|`rxErr`|pNIC RX Generic Error|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|rxFifoErrPct`|`rxFifoErr`|pNIC RX FIFO Error|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|rxMissErrPct`|`rxMissErr`|pNIC RX missed error (ring buffer full)|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|rxMissErrRaw`|`rxMissErrRaw`|pNIC RX missed error (ring buffer full) (raw)|none stated|none|
|`vSAN Perf\|pNIC:<vmnic>\|rxOvErrPct`|`rxOvErr`|pNIC RX Buffer Overflow Error|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|txCarErrPct`|`txCarErr`|pNIC TX Carrier Error|percent|per-mille / 10|
|`vSAN Perf\|pNIC:<vmnic>\|txErrPct`|`txErr`|pNIC TX Generic Error|percent|per-mille / 10|

## Keys on VMWARE HostSystem: host tier (`tier_host`, 149 keys)

Instances: `<stack>` is the TCP/IP stack name (`defaultTcpipStack`), one per
netstack; `<vmk>` is the vSAN VMkernel NIC (`vmk2`), one per vSAN vmknic.
`vsan-vnic-net` ids carry the stack too (`<node>|<stack>|<vmk>`); only the
vmk is in the key. On the lab: 141 series per host.

### CMMDS Network (`cmmds-net`): 10 keys per host

CMMDS (cluster membership directory) traffic on the host: group and RDT receive/transmit counts and throughput.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|CMMDS Network\|groupRx`|`groupRx`|Group Inbound|none stated|none|
|`vSAN Perf\|CMMDS Network\|groupRxThroughputBytesPerSec`|`groupRxThroughput`|Group Inbound Throughput|bytes/s|none|
|`vSAN Perf\|CMMDS Network\|groupTxMcast`|`groupTxMcast`|Group Outbound Multicast|none stated|none|
|`vSAN Perf\|CMMDS Network\|groupTxMcastThroughputBytesPerSec`|`groupTxMcastThroughput`|Group Outbound Multicast Throughput|bytes/s|none|
|`vSAN Perf\|CMMDS Network\|groupTxUcast`|`groupTxUcast`|Group Outbound Unicast|none stated|none|
|`vSAN Perf\|CMMDS Network\|groupTxUcastThroughputBytesPerSec`|`groupTxUcastThroughput`|Group Outbound Unicast Throughput|bytes/s|none|
|`vSAN Perf\|CMMDS Network\|rdtRx`|`rdtRx`|Read Inbound|none stated|none|
|`vSAN Perf\|CMMDS Network\|rdtRxThroughputBytesPerSec`|`rdtRxThroughput`|Read Inbound Throughput|bytes/s|none|
|`vSAN Perf\|CMMDS Network\|rdtTx`|`rdtTx`|Read Outbound|none stated|none|
|`vSAN Perf\|CMMDS Network\|rdtTxThroughputBytesPerSec`|`rdtTxThroughput`|Read Outbound Throughput|bytes/s|none|

### CPU (`vsan-cpu`): 7 keys per host

CPU used by vSAN worlds on the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 35 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|CPU\|avgRunPct`|`avgRunPct`|Run percent (average)|percent|none|
|`vSAN Perf\|CPU\|avgUsedPct`|`avgUsedPct`|Used percent (average)|percent|none|
|`vSAN Perf\|CPU\|maxRunPct`|`maxRunPct`|Run percent (max)|percent|none|
|`vSAN Perf\|CPU\|maxUsedPct`|`maxUsedPct`|Used percent (max)|percent|none|
|`vSAN Perf\|CPU\|readyPct`|`readyPct`|Percentage of Ready CPU Time|percent|none|
|`vSAN Perf\|CPU\|runPct`|`runPct`|Percentage of Running CPU Time|percent|none|
|`vSAN Perf\|CPU\|usedPct`|`usedPct`|Percentage of Used CPU Time|percent|none|

### vSAN Direct (`vsan-direct-host`): 8 keys per host

vSAN Direct on the host. Empty on the lab; entity id form unobserved (Python pack models it on `host_uuid`, provisional).

Lands on: HostSystem. Instance: none (if an observed id carries a second part, it is skipped and counted, counted in `Collection|entities_unparsed`). Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|vSAN Direct\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none|
|`vSAN Perf\|vSAN Direct\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none|
|`vSAN Perf\|vSAN Direct\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none|
|`vSAN Perf\|vSAN Direct\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none|
|`vSAN Perf\|vSAN Direct\|oioDevRead`|`oioDevRead`|Read Outstanding IO|none stated|none|
|`vSAN Perf\|vSAN Direct\|oioDevWrite`|`oioDevWrite`|Write Outstanding IO|none stated|none|
|`vSAN Perf\|vSAN Direct\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none|
|`vSAN Perf\|vSAN Direct\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none|

### DOM Comp Mgr (`host-domcompmgr`): 16 keys per host

DOM component manager role on the host: the layer that serves component I/O, including resync reads and recovery writes.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 80 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|DOM Comp Mgr\|componentCongestion`|`componentCongestion`|Component congestion|none stated|none|
|`vSAN Perf\|DOM Comp Mgr\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|DOM Comp Mgr\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyMaxReadUs`|`latencyMaxRead`|Latency (max, read)|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyMaxWriteUs`|`latencyMaxWrite`|Latency (max, write)|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyStddevWriteUs`|`latencyStddevWrite`|Latency standard deviation (write)|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|namespaceDispatchedCostWriteBytesPerSec`|`namespaceDispatchedCostWrite`|Namespace dispatched cost (write)|bytes/s|none|
|`vSAN Perf\|DOM Comp Mgr\|oio`|`oio`|Outstanding IO|none stated|none|
|`vSAN Perf\|DOM Comp Mgr\|resyncReadCongestion`|`resyncReadCongestion`|Resync read congestion|none stated|none|
|`vSAN Perf\|DOM Comp Mgr\|sharedCongestion`|`sharedCongestion`|Shared congestion|none stated|none|
|`vSAN Perf\|DOM Comp Mgr\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|
|`vSAN Perf\|DOM Comp Mgr\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|
|`vSAN Perf\|DOM Comp Mgr\|vmdiskDispatchedCostWriteBytesPerSec`|`vmdiskDispatchedCostWrite`|VM disk dispatched cost (write)|bytes/s|none|
|`vSAN Perf\|DOM Comp Mgr\|vmdiskQueueDepthWrite`|`vmdiskQueueDepthWrite`|VM disk queue depth (write)|none stated|none|

### ESA Dedup DOM Client (`host-esa-dedup-domclient-io`): 24 keys per host

ESA global deduplication I/O seen from this host's DOM client.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 120 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|

### Host Network (`vsan-host-net`): 8 keys per host

vSAN network traffic of the host as a whole (all vSAN vmknics).

Lands on: HostSystem. Instance: none. Lab: 5 entities, 40 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|Host Network\|portRxDropsPct`|`portRxDrops`|Inbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|
|`vSAN Perf\|Host Network\|portTxDropsPct`|`portTxDrops`|Outbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|
|`vSAN Perf\|Host Network\|rxPackets`|`rxPackets`|Inbound Packets Per Second|none stated|none|
|`vSAN Perf\|Host Network\|rxPacketsLossRatePct`|`rxPacketsLossRate`|Inbound Packet Discard Rate|percent|per-mille / 10|
|`vSAN Perf\|Host Network\|rxThroughputBytesPerSec`|`rxThroughput`|Throughput Inbound|bytes/s|none|
|`vSAN Perf\|Host Network\|txPackets`|`txPackets`|Outbound Packets Per Second|none stated|none|
|`vSAN Perf\|Host Network\|txPacketsLossRatePct`|`txPacketsLossRate`|Outbound Packet Discard Rate|percent|per-mille / 10|
|`vSAN Perf\|Host Network\|txThroughputBytesPerSec`|`txThroughput`|Throughput Outbound|bytes/s|none|

### ZDOM Top Stats (`host-zdom-top-stats`): 11 keys per host

ESA zDOM top-level I/O on the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 55 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|ZDOM Top Stats\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|ZDOM Top Stats\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|ZDOM Top Stats\|oio`|`oio`|Outstanding IO|none stated|none|
|`vSAN Perf\|ZDOM Top Stats\|oioWrite`|`oioWrite`|Outstanding IO (write)|none stated|none|
|`vSAN Perf\|ZDOM Top Stats\|readLatencyMaxUs`|`readLatencyMaxUs`|Read latency max us|microseconds|none|
|`vSAN Perf\|ZDOM Top Stats\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|
|`vSAN Perf\|ZDOM Top Stats\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|
|`vSAN Perf\|ZDOM Top Stats\|unmapLatencyMaxUs`|`unmapLatencyMaxUs`|Unmap latency max us|microseconds|none|
|`vSAN Perf\|ZDOM Top Stats\|writeLatencyMaxUs`|`writeLatencyMaxUs`|Write latency max us|microseconds|none|

### Memory (`vsan-memory`): 12 keys per host

Memory reserved and consumed by the vSAN kernel modules and daemons on the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 60 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|Memory\|clomdConsumedSizeBytes`|`clomdConsumedSize`|Clomd consumed size|bytes|none|
|`vSAN Perf\|Memory\|clomdReservedSizeBytes`|`clomdReservedSize`|Clomd reserved size|bytes|none|
|`vSAN Perf\|Memory\|cmmdsdConsumedSizeBytes`|`cmmdsdConsumedSize`|Cmmdsd consumed size|bytes|none|
|`vSAN Perf\|Memory\|cmmdsdReservedSizeBytes`|`cmmdsdReservedSize`|Cmmdsd reserved size|bytes|none|
|`vSAN Perf\|Memory\|epdConsumedSizeBytes`|`epdConsumedSize`|Epd consumed size|bytes|none|
|`vSAN Perf\|Memory\|kernelConsumedSize`|`kernelConsumedSize`|Kernel consumed size|none stated|none|
|`vSAN Perf\|Memory\|kernelReservedSizeBytes`|`kernelReservedSize`|Kernel Reserved Memory Size|bytes|none|
|`vSAN Perf\|Memory\|osfsdConsumedSizeBytes`|`osfsdConsumedSize`|Osfsd consumed size|bytes|none|
|`vSAN Perf\|Memory\|uwConsumedSizeBytes`|`uwConsumedSize`|Uw consumed size|bytes|none|
|`vSAN Perf\|Memory\|uwReservedSizeBytes`|`uwReservedSize`|User world Reserved Memory Size|bytes|none|
|`vSAN Perf\|Memory\|vsandevicemonitordConsumedSizeBytes`|`vsandevicemonitordConsumedSize`|Vsandevicemonitord consumed size|bytes|none|
|`vSAN Perf\|Memory\|vsanmgmtdConsumedSizeBytes`|`vsanmgmtdConsumedSize`|Vsanmgmtd consumed size|bytes|none|

### RDT Latency (`rdt-network-latency`): 11 keys per host

RDT latency and socket-buffer figures for the host.

Lands on: HostSystem. Instance: none. Lab: 5 entities, 55 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|RDT Latency\|avgLatencyUs`|`avgLatency`|RDT Network VNIC Average Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|kaReset`|`kaReset`|Keepalive reset|none stated|none|
|`vSAN Perf\|RDT Latency\|maxLatencyUs`|`maxLatency`|RDT Network Cluster Max Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|minLatencyUs`|`minLatency`|RDT Network Cluster Min Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|numReadyDelay`|`numReadyDelay`|Num ready delay|none stated|none|
|`vSAN Perf\|RDT Latency\|rxSbSpaceMaxBytes`|`rxSbSpaceMax`|RDT Network Socket Max Inbound Bytes|bytes|none|
|`vSAN Perf\|RDT Latency\|rxSbSpaceMinBytes`|`rxSbSpaceMin`|RDT Network Socket Min Inbound Bytes|bytes|none|
|`vSAN Perf\|RDT Latency\|txCtxQMaxBytes`|`txCtxQMax`|RDT Network Max Outbound Context Bytes|bytes|none|
|`vSAN Perf\|RDT Latency\|txQLatAvgUs`|`txQLatAvg`|RDT Network Average Outbound Queueing Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|txQLatMaxUs`|`txQLatMax`|RDT Network Max Outbound Queueing Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|txSbSpaceMinBytes`|`txSbSpaceMin`|RDT Network Socket Min Outbound Bytes|bytes|none|

### TCP/IP:<stack> (`vsan-tcpip-stats`): 12 keys per netstack

TCP/IP stack statistics for the stack carrying vSAN, one instance per stack.

Lands on: HostSystem. Instance: `<stack>` = the netstack name from the entity id (`defaultTcpipStack` on the lab). Lab: 5 entities, 60 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|TCP/IP:<stack>\|tcpErrsPct`|`tcpErrs`|TCP Errors|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpHalfopenDropRatePct`|`tcpHalfopenDropRate`|Half Open Drop Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRcvdupackRatePct`|`tcpRcvdupackRate`|Received Duplicate Acknowledge Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRcvduppackRatePct`|`tcpRcvduppackRate`|Received Duplicate Packets Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRcvoopackRatePct`|`tcpRcvoopackRate`|Received Out-of-order Packets Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpRxErrRate`|`tcpRxErrRate`|TCP RX errors (rate)|none stated|none|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSackRcvBlocksRatePct`|`tcpSackRcvBlocksRate`|Sack Received Blocks Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSackRexmitsRatePct`|`tcpSackRexmitsRate`|Sack Rexmits Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSackSendBlocksRatePct`|`tcpSackSendBlocksRate`|Sack Send Blocks Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpSndZeroWin`|`tcpSndZeroWin`|TCP Send Zero Window|none stated|none|
|`vSAN Perf\|TCP/IP:<stack>\|tcpTimeoutDropRatePct`|`tcpTimeoutDropRate`|Timeout Drop Rate|percent|per-mille / 10|
|`vSAN Perf\|TCP/IP:<stack>\|tcpTxRexmitRate`|`tcpTxRexmitRate`|TCP TX retransmit (rate)|none stated|none|

### vNIC:<vmk> (`vsan-vnic-net`): 10 keys per vSAN vmknic

vSAN traffic per vSAN vmknic.

Lands on: HostSystem. Instance: `<vmk>` = the vmknic name (`vmk2` on the lab); the stack part of the id is dropped from the key because a vmknic name is unique on its host (ESXi naming, inferred; the lab has one stack). Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|vNIC:<vmk>\|portRxDropsPct`|`portRxDrops`|Inbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|
|`vSAN Perf\|vNIC:<vmk>\|portRxpkts`|`portRxpkts`|Port RX packets|none stated|none|
|`vSAN Perf\|vNIC:<vmk>\|portTxDropsPct`|`portTxDrops`|Outbound Packet Drop Rate of vSwitch Port|percent|per-mille / 10|
|`vSAN Perf\|vNIC:<vmk>\|portTxpkts`|`portTxpkts`|Port TX packets|none stated|none|
|`vSAN Perf\|vNIC:<vmk>\|rxPackets`|`rxPackets`|Inbound Packets Per Second|none stated|none|
|`vSAN Perf\|vNIC:<vmk>\|rxPacketsLossRatePct`|`rxPacketsLossRate`|Inbound Packet Discard Rate|percent|per-mille / 10|
|`vSAN Perf\|vNIC:<vmk>\|rxThroughputBytesPerSec`|`rxThroughput`|Throughput Inbound|bytes/s|none|
|`vSAN Perf\|vNIC:<vmk>\|txPackets`|`txPackets`|Outbound Packets Per Second|none stated|none|
|`vSAN Perf\|vNIC:<vmk>\|txPacketsLossRatePct`|`txPacketsLossRate`|Outbound Packet Discard Rate|percent|per-mille / 10|
|`vSAN Perf\|vNIC:<vmk>\|txThroughputBytesPerSec`|`txThroughput`|Throughput Outbound|bytes/s|none|

### vNIC RDT Latency:<vmk> (`vnic-rdt-network-latency`): 10 keys per vSAN vmknic

RDT latency per vSAN vmknic.

Lands on: HostSystem. Instance: `<vmk>` = the vmknic name. Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|avgLatencyUs`|`avgLatency`|RDT Network VNIC Average Latency|microseconds|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|kaReset`|`kaReset`|Keepalive reset|none stated|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|maxLatencyUs`|`maxLatency`|RDT Network Cluster Max Latency|microseconds|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|minLatencyUs`|`minLatency`|RDT Network Cluster Min Latency|microseconds|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|numReadyDelay`|`numReadyDelay`|Num ready delay|none stated|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|rxSbSpaceMinBytes`|`rxSbSpaceMin`|RDT Network Socket Min Inbound Bytes|bytes|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txCtxQMaxBytes`|`txCtxQMax`|RDT Network Max Outbound Context Bytes|bytes|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txQLatAvgUs`|`txQLatAvg`|RDT Network Average Outbound Queueing Latency|microseconds|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txQLatMaxUs`|`txQLatMax`|RDT Network Max Outbound Queueing Latency|microseconds|none|
|`vSAN Perf\|vNIC RDT Latency:<vmk>\|txSbSpaceMinBytes`|`txSbSpaceMin`|RDT Network Socket Min Outbound Bytes|bytes|none|

### ZDOM VTX (`zdom-vtx`): 10 keys per host

ESA zDOM transaction layer (cache references, bank flushes, transaction latency).

Lands on: HostSystem. Instance: none. Lab: 5 entities, 50 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|ZDOM VTX\|checkpointWorkerWakeupMs`|`checkpointWorkerWakeupMs`|Checkpoint worker wakeup ms|milliseconds|none|
|`vSAN Perf\|ZDOM VTX\|latAvgTotalOpIOUs`|`latAvgTotalOpIO`|Latency avg total op IO|microseconds|none|
|`vSAN Perf\|ZDOM VTX\|latAvgTxnBankUs`|`latAvgTxnBank`|Latency avg transaction bank|microseconds|none|
|`vSAN Perf\|ZDOM VTX\|maxDiscUsedPct`|`maxDiscUsedPct`|Disc used percent (max)|percent|none|
|`vSAN Perf\|ZDOM VTX\|rateBankFlushTotalCacheRef`|`rateBankFlushTotalCacheRef`|Bank flush total cache ref (rate)|none stated|none|
|`vSAN Perf\|ZDOM VTX\|rateSegCleaningCtxDataTotalCacheRef`|`rateSegCleaningCtxDataTotalCacheRef`|Seg cleaning context data total cache ref (rate)|none stated|none|
|`vSAN Perf\|ZDOM VTX\|rateTotalCacheRef`|`rateTotalCacheRef`|Cache ref (rate, total)|none stated|none|
|`vSAN Perf\|ZDOM VTX\|rateTxnLookupCacheRef`|`rateTxnLookupCacheRef`|Transaction lookup cache ref (rate)|none stated|none|
|`vSAN Perf\|ZDOM VTX\|rateTxnPrefetchTotalCacheRef`|`rateTxnPrefetchTotalCacheRef`|Transaction prefetch total cache ref (rate)|none stated|none|
|`vSAN Perf\|ZDOM VTX\|rateTxnReadWrite`|`rateTxnReadWrite`|Transaction (read, write, rate)|none stated|none|

## Keys on VMWARE ClusterComputeResource: cluster tier (`tier_cluster`, 189 keys)

The cluster's own totals (DOM Owner and DOM Client here are the cluster-wide
totals of the host roles). On the lab: 91 series per cluster; vSAN Direct
Cluster, File Service and IOInsight were empty there (not configured).

### Capacity (`vsan-cluster-capacity`): 5 keys per cluster

vSAN datastore capacity and space efficiency for the cluster. `dedupRatio` is served as ratio x 100 (Python label "100 = 1.00x"; 145 on the lab) and pushed as served.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 5 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|Capacity\|dedupRatio`|`dedupRatio`|Space Efficiency Ratio (100 = 1.00x)|none stated|none; ratio x 100 (Python label: 100 = 1.00x); pushed as served|
|`vSAN Perf\|Capacity\|freeBytes`|`free`|Free Capacity|bytes|none|
|`vSAN Perf\|Capacity\|savedByDedupBytes`|`savedByDedup`|Saved Capacity|bytes|none|
|`vSAN Perf\|Capacity\|totalBytes`|`total`|Total Capacity|bytes|none|
|`vSAN Perf\|Capacity\|usedBytes`|`used`|Used Capacity|bytes|none|

### DOM Client (`cluster-domclient`): 11 keys per cluster

The cluster-wide total of the hosts' DOM Client role: the I/O the cluster's VMs issued, as the clients saw it (the host-level reading is under DOM Client on HostSystem).

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 11 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|DOM Client\|congestion`|`congestion`|Congestions|none stated|none|
|`vSAN Perf\|DOM Client\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|DOM Client\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|DOM Client\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|DOM Client\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|DOM Client\|oio`|`oio`|Outstanding IO|none stated|none|
|`vSAN Perf\|DOM Client\|readCongestion`|`readCongestion`|Read Congestions|none stated|none|
|`vSAN Perf\|DOM Client\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|
|`vSAN Perf\|DOM Client\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|
|`vSAN Perf\|DOM Client\|unmapCongestion`|`unmapCongestion`|Unmap congestion|none stated|none|
|`vSAN Perf\|DOM Client\|writeCongestion`|`writeCongestion`|Write Congestions|none stated|none|

### DOM Comp Mgr (`cluster-domcompmgr`): 12 keys per cluster

Cluster-level counterpart of the host DOM Comp Mgr group (component manager I/O, including recovery writes and resync reads per its labels).

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 12 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|DOM Comp Mgr\|congestion`|`congestion`|Congestions|none stated|none|
|`vSAN Perf\|DOM Comp Mgr\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|DOM Comp Mgr\|iopsRecWrite`|`iopsRecWrite`|Recovery Write IOPS|IO/s|none|
|`vSAN Perf\|DOM Comp Mgr\|iopsResyncRead`|`iopsResyncRead`|Resync Read IOPS|IO/s|none|
|`vSAN Perf\|DOM Comp Mgr\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|DOM Comp Mgr\|latAvgResyncReadUs`|`latAvgResyncRead`|Resync Read Latency|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgRecWriteUs`|`latencyAvgRecWrite`|Recovery Write Latency|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|DOM Comp Mgr\|oio`|`oio`|Outstanding IO|none stated|none|
|`vSAN Perf\|DOM Comp Mgr\|throughputRecWriteBytesPerSec`|`throughputRecWrite`|Recovery Write Throughput|bytes/s|none|
|`vSAN Perf\|DOM Comp Mgr\|tputResyncReadBytesPerSec`|`tputResyncRead`|Resync Read Throughput|bytes/s|none|

### DOM Owner (`cluster-domowner`): 23 keys per cluster

The cluster-wide total of the hosts' DOM Owner role: owner-side I/O for every object in the cluster, whichever host's VMs issued it (the host-level reading is under DOM Owner on HostSystem), plus the cluster's resync job counters.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 23 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|DOM Owner\|avgResyncParallelism`|`avgResyncParallelism`|Resync parallelism (average)|none stated|none|
|`vSAN Perf\|DOM Owner\|congestion`|`congestion`|Congestions|none stated|none|
|`vSAN Perf\|DOM Owner\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|DOM Owner\|iopsRecWrite`|`iopsRecWrite`|Recovery Write IOPS|IO/s|none|
|`vSAN Perf\|DOM Owner\|iopsResyncRead`|`iopsResyncRead`|Resync Read IOPS|IO/s|none|
|`vSAN Perf\|DOM Owner\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|DOM Owner\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|DOM Owner\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|DOM Owner\|numCompleteFullResyncOps`|`numCompleteFullResyncOps`|Num complete full resync ops|none stated|none|
|`vSAN Perf\|DOM Owner\|numCompleteResyncOps`|`numCompleteResyncOps`|Num complete resync ops|none stated|none|
|`vSAN Perf\|DOM Owner\|numInflightPriorityResyncJobs`|`numInflightPriorityResyncJobs`|Num inflight priority resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|numInflightSharedResyncJobs`|`numInflightSharedResyncJobs`|Num inflight shared resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|numPendingDecomResyncJobs`|`numPendingDecomResyncJobs`|Num pending decom resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|numPendingFullResyncJobs`|`numPendingFullResyncJobs`|Num pending full resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|numPendingResyncJobs`|`numPendingResyncJobs`|Num pending resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|numRunningFullResyncJobs`|`numRunningFullResyncJobs`|Num running full resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|numRunningResyncJobs`|`numRunningResyncJobs`|Num running resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|numSuspendedResyncJobs`|`numSuspendedResyncJobs`|Num suspended resync jobs|none stated|none|
|`vSAN Perf\|DOM Owner\|oio`|`oio`|Outstanding IO|none stated|none|
|`vSAN Perf\|DOM Owner\|readCongestion`|`readCongestion`|Read Congestions|none stated|none|
|`vSAN Perf\|DOM Owner\|tputReadBytesPerSec`|`tputRead`|Read Throughput|bytes/s|none|
|`vSAN Perf\|DOM Owner\|tputWriteBytesPerSec`|`tputWrite`|Write Throughput|bytes/s|none|
|`vSAN Perf\|DOM Owner\|writeCongestion`|`writeCongestion`|Write Congestions|none stated|none|

### ESA Dedup DOM Client (`cluster-esa-dedup-domclient-io`): 24 keys per cluster

ESA global deduplication I/O seen from the DOM client (chunk, engine and hash object reads and writes). Live on the lab's 9.1.1 ESA cluster, although the Python pack carries it only in its provisional `perfsvc_model_extra.py`.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 24 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkReadCount`|`dedupChunkReadCount`|Deduplication Chunk Object Read IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupChunkWriteCount`|`dedupChunkWriteCount`|Deduplication Chunk Object Write IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineReadCount`|`dedupEngineReadCount`|Deduplication Read IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupEngineWriteCount`|`dedupEngineWriteCount`|Deduplication Write IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashReadCount`|`dedupHashReadCount`|Deduplication Hash Object Read IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|dedupHashWriteCount`|`dedupHashWriteCount`|Deduplication Hash Object Write IO Count|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjRead`|`iopsDedupChunkObjRead`|Deduplication Chunk Object Read IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupChunkObjWrite`|`iopsDedupChunkObjWrite`|Deduplication Chunk Object Write IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineRead`|`iopsDedupEngineRead`|Deduplication Read IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupEngineWrite`|`iopsDedupEngineWrite`|Deduplication Write IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjRead`|`iopsDedupHashObjRead`|Deduplication Hash Object Read IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|iopsDedupHashObjWrite`|`iopsDedupHashObjWrite`|Deduplication Hash Object Write IOPS|IO/s|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjReadUs`|`latencyAvgDedupChunkObjRead`|Deduplication Chunk Object Read Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupChunkObjWriteUs`|`latencyAvgDedupChunkObjWrite`|Deduplication Chunk Object Write Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineReadUs`|`latencyAvgDedupEngineRead`|Deduplication Read Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupEngineWriteUs`|`latencyAvgDedupEngineWrite`|Deduplication Write Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjReadUs`|`latencyAvgDedupHashObjRead`|Deduplication Hash Object Read Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|latencyAvgDedupHashObjWriteUs`|`latencyAvgDedupHashObjWrite`|Deduplication Hash Object Write Latency|microseconds|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjRead`|`tputDedupChunkObjRead`|Deduplication Chunk Object Read Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupChunkObjWrite`|`tputDedupChunkObjWrite`|Deduplication Chunk Object Write Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineRead`|`tputDedupEngineRead`|Deduplication Read Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupEngineWrite`|`tputDedupEngineWrite`|Deduplication Write Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjRead`|`tputDedupHashObjRead`|Deduplication Hash Object Read Throughput|none stated|none|
|`vSAN Perf\|ESA Dedup DOM Client\|tputDedupHashObjWrite`|`tputDedupHashObjWrite`|Deduplication Hash Object Write Throughput|none stated|none|

### RDT Latency (`cluster-rdt-network-latency`): 9 keys per cluster

Reliable Datagram Transport latency and socket-buffer figures for the cluster.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 9 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|RDT Latency\|avgLatencyUs`|`avgLatency`|RDT Network VNIC Average Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|kaReset`|`kaReset`|Keepalive reset|none stated|none|
|`vSAN Perf\|RDT Latency\|maxLatencyUs`|`maxLatency`|RDT Network Cluster Max Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|minLatencyUs`|`minLatency`|RDT Network Cluster Min Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|numReadyDelay`|`numReadyDelay`|Num ready delay|none stated|none|
|`vSAN Perf\|RDT Latency\|rxSbSpaceMinBytes`|`rxSbSpaceMin`|RDT Network Socket Min Inbound Bytes|bytes|none|
|`vSAN Perf\|RDT Latency\|txCtxQMaxBytes`|`txCtxQMax`|RDT Network Max Outbound Context Bytes|bytes|none|
|`vSAN Perf\|RDT Latency\|txQLatAvgUs`|`txQLatAvg`|RDT Network Average Outbound Queueing Latency|microseconds|none|
|`vSAN Perf\|RDT Latency\|txSbSpaceMinBytes`|`txSbSpaceMin`|RDT Network Socket Min Outbound Bytes|bytes|none|

### ZDOM Top Stats (`cluster-zdom-top-stats`): 7 keys per cluster

ESA zDOM top-level I/O for the cluster.

Lands on: ClusterComputeResource. Instance: none. Lab: 1 entity, 7 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|ZDOM Top Stats\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|ZDOM Top Stats\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgReadUs`|`latencyAvgRead`|Read Latency|microseconds|none|
|`vSAN Perf\|ZDOM Top Stats\|latencyAvgWriteUs`|`latencyAvgWrite`|Write Latency|microseconds|none|
|`vSAN Perf\|ZDOM Top Stats\|oio`|`oio`|Outstanding IO|none stated|none|
|`vSAN Perf\|ZDOM Top Stats\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|
|`vSAN Perf\|ZDOM Top Stats\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|

### vSAN Direct Cluster (`vsan-direct-cluster`): 8 keys per cluster

vSAN Direct. Empty on the lab (not configured); entity id form unobserved.

Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time, see "Where keys land"). Instance: none. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|vSAN Direct Cluster\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none|
|`vSAN Perf\|vSAN Direct Cluster\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none|
|`vSAN Perf\|vSAN Direct Cluster\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none|
|`vSAN Perf\|vSAN Direct Cluster\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none|
|`vSAN Perf\|vSAN Direct Cluster\|oioDevRead`|`oioDevRead`|Read Outstanding IO|none stated|none|
|`vSAN Perf\|vSAN Direct Cluster\|oioDevWrite`|`oioDevWrite`|Write Outstanding IO|none stated|none|
|`vSAN Perf\|vSAN Direct Cluster\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none|
|`vSAN Perf\|vSAN Direct Cluster\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none|

### File Service (`vsan-file-service`): 8 keys per cluster

vSAN File Service. Empty on the lab (not enabled); entity id form unobserved.

Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time). Instance: none. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|File Service\|readLatencyUs`|`readLatency`|Read latency|microseconds|none|
|`vSAN Perf\|File Service\|readOpTotal`|`readOpTotal`|IOPS for read operations|IO/s|none|
|`vSAN Perf\|File Service\|readRequestedBytesPerSec`|`readRequested`|Requested bytes for read|bytes/s|none|
|`vSAN Perf\|File Service\|readTransferredBytesPerSec`|`readTransferred`|Actual transferred bytes for read|bytes/s|none|
|`vSAN Perf\|File Service\|writeLatencyUs`|`writeLatency`|Write latency|microseconds|none|
|`vSAN Perf\|File Service\|writeOpTotal`|`writeOpTotal`|IOPS for write operations|IO/s|none|
|`vSAN Perf\|File Service\|writeRequestedBytesPerSec`|`writeRequested`|Requested bytes for write|bytes/s|none|
|`vSAN Perf\|File Service\|writeTransferredBytesPerSec`|`writeTransferred`|Actual transferred bytes for write|bytes/s|none|

### IOInsight (`ioinsight`): 82 keys per cluster

IOInsight capture results. Empty on the lab (no capture running); entity id form unobserved. 82 of the cluster tier's 189 keys.

Lands on: ClusterComputeResource, or HostSystem (decided per entity at run time). Instance: none. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|IOInsight\|aligned4kRatio`|`aligned4kRatio`|4K Aligned IO|none stated|none|
|`vSAN Perf\|IOInsight\|aligned4kReadRatio`|`aligned4kReadRatio`|4K Aligned Read IO|none stated|none|
|`vSAN Perf\|IOInsight\|aligned4kWriteRatio`|`aligned4kWriteRatio`|4K Aligned Write IO|none stated|none|
|`vSAN Perf\|IOInsight\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|IOInsight\|iopsTotal`|`iopsTotal`|Total IOPS|IO/s|none|
|`vSAN Perf\|IOInsight\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|IOInsight\|randomRatio`|`randomRatio`|Random IO|none stated|none|
|`vSAN Perf\|IOInsight\|randomReadRatio`|`randomReadRatio`|Random Read IO|none stated|none|
|`vSAN Perf\|IOInsight\|randomWriteRatio`|`randomWriteRatio`|Random Write IO|none stated|none|
|`vSAN Perf\|IOInsight\|readRatio`|`readRatio`|Read IO|none stated|none|
|`vSAN Perf\|IOInsight\|riosz0_4k`|`riosz0_4k`|ReadSize<4K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz128k`|`riosz128k`|ReadSize=128K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz128k_256k`|`riosz128k_256k`|128K<ReadSize<256K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz16k`|`riosz16k`|ReadSize=16K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz16k_32k`|`riosz16k_32k`|16K<ReadSize<32K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz1_m`|`riosz1_m`|ReadSize>1M|none stated|none|
|`vSAN Perf\|IOInsight\|riosz1m`|`riosz1m`|ReadSize=1M|none stated|none|
|`vSAN Perf\|IOInsight\|riosz256k`|`riosz256k`|ReadSize=256K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz256k_512k`|`riosz256k_512k`|256K<ReadSize<512K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz32k`|`riosz32k`|ReadSize=32K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz32k_64k`|`riosz32k_64k`|32K<ReadSize<64K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz4k`|`riosz4k`|ReadSize=4K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz4k_8k`|`riosz4k_8k`|4K<ReadSize<8K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz512k`|`riosz512k`|ReadSize=512K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz512k_1m`|`riosz512k_1m`|512K<ReadSize<1M|none stated|none|
|`vSAN Perf\|IOInsight\|riosz64k`|`riosz64k`|ReadSize=64K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz64k_128k`|`riosz64k_128k`|64K<ReadSize<128K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz8k`|`riosz8k`|ReadSize=8K|none stated|none|
|`vSAN Perf\|IOInsight\|riosz8k_16k`|`riosz8k_16k`|8K<ReadSize<16K|none stated|none|
|`vSAN Perf\|IOInsight\|rlat0_1us`|`rlat0_1us`|0<ReadLatency<1(us)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat100_500us`|`rlat100_500us`|100<ReadLatency<500(us)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat100_ms`|`rlat100_ms`|ReadLatency>100(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat10_100us`|`rlat10_100us`|10<ReadLatency<100(us)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat10_25ms`|`rlat10_25ms`|10<ReadLatency<25(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat1_10us`|`rlat1_10us`|1<ReadLatency<10(us)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat1_5ms`|`rlat1_5ms`|1<ReadLatency<5(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat25_50ms`|`rlat25_50ms`|25<ReadLatency<50(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat500us_1ms`|`rlat500us_1ms`|500us<ReadLatency<1ms|none stated|none|
|`vSAN Perf\|IOInsight\|rlat50_100ms`|`rlat50_100ms`|50<ReadLatency<100(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|rlat5_10ms`|`rlat5_10ms`|5<ReadLatency<10(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|sequentialRatio`|`sequentialRatio`|Sequential IO|none stated|none|
|`vSAN Perf\|IOInsight\|sequentialReadRatio`|`sequentialReadRatio`|Sequential Read IO|none stated|none|
|`vSAN Perf\|IOInsight\|sequentialWriteRatio`|`sequentialWriteRatio`|Sequential Write IO|none stated|none|
|`vSAN Perf\|IOInsight\|throughputRandomBytesPerSec`|`throughputRandom`|Random Throughput|bytes/s|none|
|`vSAN Perf\|IOInsight\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|
|`vSAN Perf\|IOInsight\|throughputSequentialBytesPerSec`|`throughputSequential`|Sequential Throughput|bytes/s|none|
|`vSAN Perf\|IOInsight\|throughputTotalBytesPerSec`|`throughputTotal`|Total Throughput|bytes/s|none|
|`vSAN Perf\|IOInsight\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|
|`vSAN Perf\|IOInsight\|unaligned4kRatio`|`unaligned4kRatio`|4K Unaligned IO|none stated|none|
|`vSAN Perf\|IOInsight\|unaligned4kReadRatio`|`unaligned4kReadRatio`|4K Unaligned Read IO|none stated|none|
|`vSAN Perf\|IOInsight\|unaligned4kWriteRatio`|`unaligned4kWriteRatio`|4K Unaligned Write IO|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz0_4k`|`wiosz0_4k`|WriteSize<4K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz128k`|`wiosz128k`|WriteSize=128K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz128k_256k`|`wiosz128k_256k`|128K<WriteSize<256K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz16k`|`wiosz16k`|WriteSize=16K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz16k_32k`|`wiosz16k_32k`|16K<WriteSize<32K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz1_m`|`wiosz1_m`|WriteSize>1M|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz1m`|`wiosz1m`|WriteSize=1M|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz256k`|`wiosz256k`|WriteSize=256K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz256k_512k`|`wiosz256k_512k`|256K<WriteSize<512K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz32k`|`wiosz32k`|WriteSize=32K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz32k_64k`|`wiosz32k_64k`|32K<WriteSize<64K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz4k`|`wiosz4k`|WriteSize=4K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz4k_8k`|`wiosz4k_8k`|4K<WriteSize<8K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz512k`|`wiosz512k`|WriteSize=512K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz512k_1m`|`wiosz512k_1m`|512K<WriteSize<1M|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz64k`|`wiosz64k`|WriteSize=64K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz64k_128k`|`wiosz64k_128k`|64K<WriteSize<128K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz8k`|`wiosz8k`|WriteSize=8K|none stated|none|
|`vSAN Perf\|IOInsight\|wiosz8k_16k`|`wiosz8k_16k`|8K<WriteSize<16K|none stated|none|
|`vSAN Perf\|IOInsight\|wlat0_1us`|`wlat0_1us`|0<WriteLatency<1(us)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat100_500us`|`wlat100_500us`|100<WriteLatency<500(us)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat100_ms`|`wlat100_ms`|WriteLatency>100(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat10_100us`|`wlat10_100us`|10<WriteLatency<100(us)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat10_25ms`|`wlat10_25ms`|10<WriteLatency<25(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat1_10us`|`wlat1_10us`|1<WriteLatency<10(us)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat1_5ms`|`wlat1_5ms`|1<WriteLatency<5(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat25_50ms`|`wlat25_50ms`|25<WriteLatency<50(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat500us_1ms`|`wlat500us_1ms`|500us<WriteLatency<1ms|none stated|none|
|`vSAN Perf\|IOInsight\|wlat50_100ms`|`wlat50_100ms`|50<WriteLatency<100(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|wlat5_10ms`|`wlat5_10ms`|5<WriteLatency<10(ms)|none stated|none|
|`vSAN Perf\|IOInsight\|writeRatio`|`writeRatio`|Write IO|none stated|none|

## Keys on VMWARE HostSystem: disk tier (`tier_disk`, 31 keys, default off)

Instanced by the vSAN disk uuid (`ESA Disk Layer:52d171f6-...`), on the host
the disk map read the disk from. On ESA the two `vsan-esa-disk-*` types carry
data and `cache-disk` / `capacity-disk` answer empty (learned after 3
cycles); on OSA the reverse is expected (not observed). 24 of the 31 names
also exist as `vSAN|Performance|*` on the built-in vSAN adapter's disk
objects. On the lab: 10 series per disk.

### Cache Disk:<disk uuid> (`cache-disk`): 8 keys per disk

OSA cache-tier disk. Empty on ESA; learned-skipped after three empty cycles. 8 of 8 names match built-in OSA disk keys (name match only).

Lands on: HostSystem (the host the disk map read it from). Instance: `<disk uuid>` = the vSAN disk uuid from the entity id. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|Cache Disk:<disk uuid>\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevRead`|
|`vSAN Perf\|Cache Disk:<disk uuid>\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevWrite`|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevDAvgUs`|`latencyDevDAvg`|Device Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevDAvg`|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevGAvgUs`|`latencyDevGAvg`|Guest Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevGAvg`|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevRead`|
|`vSAN Perf\|Cache Disk:<disk uuid>\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevWrite`|
|`vSAN Perf\|Cache Disk:<disk uuid>\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevRead`|
|`vSAN Perf\|Cache Disk:<disk uuid>\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevWrite`|

### Capacity Disk:<disk uuid> (`capacity-disk`): 13 keys per disk

OSA capacity-tier disk. Empty on ESA; learned-skipped. 8 of 13 names match built-in keys .

Lands on: HostSystem. Instance: `<disk uuid>`. Lab: empty on the lab (wildcard echo).

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|deleteCongestion`|`deleteCongestion`|vSAN Layer Delete Congestion|bytes|none; Python unit says bytes; left unsuffixed|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsDevRead`|`iopsDevRead`|Physical-layer Read IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevRead`|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsDevWrite`|`iopsDevWrite`|Physical-layer Write IOPS|IO/s|none; name matches built-in `vSAN\|Performance\|iopsDevWrite`|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevDAvgUs`|`latencyDevDAvg`|Device Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevDAvg`|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevGAvgUs`|`latencyDevGAvg`|Guest Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevGAvg`|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevRead`|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevWrite`|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|throughputDevReadBytesPerSec`|`throughputDevRead`|Physical-layer Read Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevRead`|
|`vSAN Perf\|Capacity Disk:<disk uuid>\|throughputDevWriteBytesPerSec`|`throughputDevWrite`|Physical-layer Write Throughput|bytes/s|none; name matches built-in `vSAN\|Performance\|throughputDevWrite`|

### ESA Disk Layer:<disk uuid> (`vsan-esa-disk-layer`): 5 keys per disk

vSAN-layer I/O per ESA disk. 4 of 5 names match built-in `Vsan2Disk` `vSAN|Performance|*` keys .

Lands on: HostSystem. Instance: `<disk uuid>`. Lab: 5 entities, 25 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatReadCapacity`|`avgLatReadCapacity`|vSAN Layer Average Read Latency of vSAN ESA Disk|none stated|none; name matches built-in `vSAN\|Performance\|avgLatReadCapacity`|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatWriteCapacity`|`avgLatWriteCapacity`|vSAN Layer Average Write Latency of vSAN ESA Disk|none stated|none; name matches built-in `vSAN\|Performance\|avgLatWriteCapacity`|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|avgLatWritePerf`|`avgLatWritePerf`|Latency write perf (average)|none stated|none|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsReadCapacity`|`iopsReadCapacity`|vSAN Layer Read IOPS of vSAN ESA Disk|IO/s|none; name matches built-in `vSAN\|Performance\|iopsReadCapacity`|
|`vSAN Perf\|ESA Disk Layer:<disk uuid>\|iopsWriteCapacity`|`iopsWriteCapacity`|vSAN Layer Write IOPS of vSAN ESA Disk|IO/s|none; name matches built-in `vSAN\|Performance\|iopsWriteCapacity`|

### ESA Disk SCSI:<disk uuid> (`vsan-esa-disk-scsifw`): 5 keys per disk

Device (SCSI framework) latency per ESA disk. 4 of 5 names match built-in `Vsan2Disk` keys .

Lands on: HostSystem. Instance: `<disk uuid>`. Lab: 5 entities, 25 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevDAvgUs`|`latencyDevDAvg`|Device Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevDAvg`|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevGAvgUs`|`latencyDevGAvg`|Guest Average Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevGAvg`|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevKAvgUs`|`latencyDevKAvg`|Latency dev k (average)|microseconds|none|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevReadUs`|`latencyDevRead`|Physical-layer Read Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevRead`|
|`vSAN Perf\|ESA Disk SCSI:<disk uuid>\|latencyDevWriteUs`|`latencyDevWrite`|Physical-layer Write Latency|microseconds|none; name matches built-in `vSAN\|Performance\|latencyDevWrite`|


## Keys on VMWARE VirtualMachine: VM tier (`tier_vm`, 21 keys, default off)

Runs on every Nth cycle per cluster (`vm_tier_every_n_cycles`, default 3,
staggered so 1/N of the clusters run each cycle). `virtual-disk` and `vscsi`
are instanced by bus position (`scsi0:0`), so a VMDK's two groups share one
instance. On the lab: 80 series requested per VM, about 60 with a value.

### VM (`virtual-machine`): 6 keys per VM

vSAN I/O per VM, as the VM's DOM client saw it.

Lands on: VirtualMachine. Instance: none. Lab: 30 entities, 180 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|VM\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|VM\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|VM\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|
|`vSAN Perf\|VM\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|
|`vSAN Perf\|VM\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|
|`vSAN Perf\|VM\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|

### Virtual Disk:<busN:M> (`virtual-disk`): 7 keys per VMDK

Per-VMDK normalized IOPS and IOPS limit. The 4 `NIOPSRead*`/`NIOPSWrite*` labels were `None` for all 148 entities on the lab; they stay requested and documented, and push nothing while `None`.

Lands on: VirtualMachine. Instance: `<busN:M>` = the VMDK's bus position from the device read (`scsi0:0`, see "Where keys land"). Lab: 148 entities, 1036 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPS`|`NIOPS`|Normalized IOPS|IO/s|none|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSDelayed`|`NIOPSDelayed`|Delayed Normalized IOPS|IO/s|none|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSRead`|`NIOPSRead`|Niops (read)|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSReadDelayed`|`NIOPSReadDelayed`|Niops read delayed|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSWrite`|`NIOPSWrite`|Niops (write)|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|
|`vSAN Perf\|Virtual Disk:<busN:M>\|NIOPSWriteDelayed`|`NIOPSWriteDelayed`|Niops write delayed|IO/s|none; all `None` on the lab (148/148); requested, nothing pushed|
|`vSAN Perf\|Virtual Disk:<busN:M>\|iopsLimit`|`iopsLimit`|Normalized IOPS Limit|IO/s|none|

### vSCSI:<scsiN:M> (`vscsi`): 8 keys per virtual SCSI device

I/O per virtual SCSI device.

Lands on: VirtualMachine. Instance: `<scsiN:M>` = the device part of the entity id (`scsi0:0`), same shape as the vCenter adapter's own `virtualDisk:scsi0:0`. Lab: 148 entities, 1184 series.

|Ops stat key|vSAN label|Meaning (Python pack label)|Unit|Transform|
|---|---|---|---|---|
|`vSAN Perf\|vSCSI:<scsiN:M>\|iopsRead`|`iopsRead`|Read IOPS|IO/s|none|
|`vSAN Perf\|vSCSI:<scsiN:M>\|iopsWrite`|`iopsWrite`|Write IOPS|IO/s|none|
|`vSAN Perf\|vSCSI:<scsiN:M>\|latencyReadUs`|`latencyRead`|vSAN Layer Read Latency|microseconds|none|
|`vSAN Perf\|vSCSI:<scsiN:M>\|latencyWriteUs`|`latencyWrite`|vSAN Layer Write Latency|microseconds|none|
|`vSAN Perf\|vSCSI:<scsiN:M>\|readCount`|`readCount`|Read count|none stated|none|
|`vSAN Perf\|vSCSI:<scsiN:M>\|throughputReadBytesPerSec`|`throughputRead`|Read throughput|bytes/s|none|
|`vSAN Perf\|vSCSI:<scsiN:M>\|throughputWriteBytesPerSec`|`throughputWrite`|Write throughput|bytes/s|none|
|`vSAN Perf\|vSCSI:<scsiN:M>\|writeCount`|`writeCount`|Write count|none stated|none|


Units come from the Python pack's labels (harvested from HCIBench), not from
the API; the microsecond and byte-rate readings agree with the captured
magnitudes but are not proven by the API.

## Logging

Every pak line goes through the adapter's own logger, into
`VsanPerfAdapter_<instance id>.log` on the collector. INFO every cycle,
including a failed one: one summary line, `cycle <n> summary: clusters
ok=.. failed=.. timed_out=.. of ..; perf calls=..; series pushed=.. (core ..
host .. cluster .. disk .. vm ..; .. already pushed); unresolved hosts=..
clusters=.. disks=.. vms=.. vdisks=..; unparsed=..; learned skips=..;
optional calls failed=..; push batches ok=.. failed=..; <s> s` (the tiers
and unresolved classes listed are the ones switched on),
with `; FAILED: <reason>` on a failed cycle. WARN: a failed cluster (fault class and
key), an optional type's failure (once per cluster and type per refresh),
an unclean push, unresolved hosts, clusters, disks and VMs (once per
refresh), a failed `QueryDisksForVsan` or VM read (once until it succeeds),
an entity id of the wrong form or a runtime-landing id of an unknown form
(once per type per day, part count only), and entity types to exclude this
build does not know (configure). With
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
`series_kept`, `series_pushed`, `unresolved`, `learned_skips`, `failed`);
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
