# VCF Content Factory vSAN Performance: Reference

Build 0.1.0.6 (Build 1 of the design). Design: the factory's
`knowledge/designs/vsanperf-mp-v1.md`.

## Adapter

| Field | Value |
|---|---|
| Adapter kind | `vcfcf_vsanperf` |
| Tier | 2 (Java SDK) |
| Monitoring interval | 5 minutes (the Performance Service's native 300 s bucket) |
| Objects created | the adapter instance only |
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

`vcenter_host` and `cluster_include` are the account's identity, so two
instances can shard one vCenter's clusters. Changing either recreates the
account's identity.

## Adapter instance metrics (`Collection|*`)

| Key | Label | Meaning |
|---|---|---|
| `Collection\|clusters_in_scope` | Clusters in scope | vSAN-enabled clusters after include/exclude |
| `Collection\|clusters_ok` | Clusters collected | clusters whose calls all returned |
| `Collection\|clusters_failed` | Clusters failed | a call faulted or errored |
| `Collection\|clusters_timed_out` | Clusters timed out | per-call timeout or cycle deadline |
| `Collection\|perf_calls` | Performance Service calls | `VsanPerfQueryPerf` calls this cycle |
| `Collection\|series_pushed` | Series pushed | values handed to the Suite API batch push |
| `Collection\|hosts_unresolved` | Hosts unresolved | vSAN node uuids or host MoRefs with no HostSystem object of this vCenter. A host the vCenter adapter starts collecting later is picked up at the next refresh of the HostSystem list: every 12th cycle (up to an hour at 5 minutes), sooner if the cluster's host set changes |
| `Collection\|push_batches_failed` | Push batches failed | from the batch push result |
| `Collection\|cycle_seconds` | Cycle time (s) | wall time of the cycle |
| `Collection\|perf_calls_per_second` | Performance Service call rate | calls / elapsed seconds of the perf phase (first call start to last call end, all workers): aggregate throughput |
| `Collection\|perf_call_ms_avg` | Performance Service call latency (ms) | summed per-call ms / calls: latency, not throughput |
| `Collection\|last_error` | Last error (property) | first problem of the cycle, credential-free; `none` when clean |

## Keys on VMWARE HostSystem

Pushed through the Suite API onto the vCenter adapter's own HostSystem
(matched on host MoRef plus vCenter instance UUID, never by name). Pushed
keys show **no label and no unit** in VCF Operations: the unit is in the
key's leaf (`Us` microseconds, `BytesPerSec`, `Pct` = per mille divided by
10). Each value carries the Performance Service sample's own timestamp, the
newest non-empty 5-minute bucket. Key names are permanent once data exists.

`<vmnic>` is the NIC name (`vmnic1`); one instance per vSAN physical NIC the
service reports.

Each push call carries one timestamp, so values are grouped by sample time
first (a lagging host, or a series whose newest non-empty value is an older
bucket, lands under its own time), then sent in batches of up to 100 hosts
and about 1 MB per time. A bucket is marked as pushed only when every push
of the cycle came back clean; after a failed batch the next cycle sends the
same buckets again while they are still in the 7-minute window (a value that
did land is overwritten with itself).

### DOM Owner (`host-domowner`): 38 keys per host

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

Units come from the Python pack's labels (harvested from HCIBench), not from
the API; the microsecond and byte-rate readings agree with the captured
magnitudes but are not proven by the API.

## Logging

INFO every cycle: one summary line. WARN: a failed cluster (fault class and
key), an unclean push, unresolved hosts (once per Ops map refresh). With
**Performance timing logging** on, `key=value` lines prefixed `PERF call`,
`PERF cluster`, `PERF pool`, `PERF push`, `PERF inventory` and `PERF cycle`
(perf phase wall ms, calls/s from it, summed and average call ms, push ms,
heap before and after). No log line, exception message or property carries
the password or the session token. The ContainerView id (`session[...]...`,
it embeds the vCenter session key) is never logged by this pack, a fault on
the view is passed on with fixed text only, and every vSphere fault text is
scrubbed of view ids before it is used.

The framework logs a failed cycle once per cycle at WARN as `rediscovery
failed (non-fatal)` with the stack trace (the cycle runs in that hook; see
README "Acceptance on each platform").
