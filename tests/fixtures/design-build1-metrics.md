<!-- Copied verbatim from the factory knowledge/designs/vsanperf-mp-v1.md, section "Build 1 metric list", 2026-11-06. See tests/fixtures/README.md. -->

### `host-domclient` (Python kind VsanHostDomclient), 6 keys per host

|vSAN label|Ops stat key|Label (Python pack)|Unit|Transform|
|---|---|---|---|---|
| `congestion` | `vSAN Perf\|DOM Client\|congestion` | Congestions | none stated | none |
| `iopsRead` | `vSAN Perf\|DOM Client\|iopsRead` | Read IOPS | IO/s | none |
| `iopsWrite` | `vSAN Perf\|DOM Client\|iopsWrite` | Write IOPS | IO/s | none |
| `latencyAvgRead` | `vSAN Perf\|DOM Client\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|DOM Client\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `oio` | `vSAN Perf\|DOM Client\|oio` | Outstanding IO | none stated | none |

### `host-domowner` (Python kind VsanHostDomowner), 38 keys per host

The API returns 288 labels for this type unfiltered (MAP test 2); the
`labels` filter is what keeps this type cheap.

|vSAN label|Ops stat key|Label (Python pack)|Unit|Transform|
|---|---|---|---|---|
| `highOIODurationPercentWrite` | `vSAN Perf\|DOM Owner\|highOIODurationPercentWrite` | High outstanding IO duration percent (write) | none stated | none |
| `iops` | `vSAN Perf\|DOM Owner\|iops` | IOPS | IO/s | none |
| `iopsSegCleanerUnmap` | `vSAN Perf\|DOM Owner\|iopsSegCleanerUnmap` | IOPS seg cleaner unmap | IO/s | none |
| `iopsUnmap` | `vSAN Perf\|DOM Owner\|iopsUnmap` | Unmap IOPS | IO/s | none |
| `latencyAvgRead` | `vSAN Perf\|DOM Owner\|latencyAvgReadUs` | Read Latency | microseconds | none |
| `latencyAvgSegCleanerUnmap` | `vSAN Perf\|DOM Owner\|latencyAvgSegCleanerUnmapUs` | Latency avg seg cleaner unmap | microseconds | none |
| `latencyAvgUnmap` | `vSAN Perf\|DOM Owner\|latencyAvgUnmapUs` | Unmap Latency | microseconds | none |
| `latencyAvgWrite` | `vSAN Perf\|DOM Owner\|latencyAvgWriteUs` | Write Latency | microseconds | none |
| `latencyMaxRead` | `vSAN Perf\|DOM Owner\|latencyMaxReadUs` | Latency (max, read) | microseconds | none |
| `latencyMaxSegCleanerUnmap` | `vSAN Perf\|DOM Owner\|latencyMaxSegCleanerUnmapUs` | Latency max seg cleaner unmap | microseconds | none |
| `latencyMaxUnmap` | `vSAN Perf\|DOM Owner\|latencyMaxUnmapUs` | Latency max unmap | microseconds | none |
| `latencyMaxWrite` | `vSAN Perf\|DOM Owner\|latencyMaxWriteUs` | Latency (max, write) | microseconds | none |
| `latencyStddevSegCleanerUnmap` | `vSAN Perf\|DOM Owner\|latencyStddevSegCleanerUnmapUs` | Latency standard deviation seg cleaner unmap | microseconds | none |
| `latencyStddevWrite` | `vSAN Perf\|DOM Owner\|latencyStddevWriteUs` | Latency standard deviation (write) | microseconds | none |
| `numNetSchedControllerIopsLimit` | `vSAN Perf\|DOM Owner\|numNetSchedControllerIopsLimit` | Num net sched controller IOPS limit | IO/s | none |
| `numNetSchedGuestLTLatUs` | `vSAN Perf\|DOM Owner\|numNetSchedGuestLTLatUs` | Num net sched guest lt latency us | microseconds | none |
| `numNetSchedLowBand` | `vSAN Perf\|DOM Owner\|numNetSchedLowBand` | Num net sched low band | none stated | none |
| `numNetSchedLowBandSec` | `vSAN Perf\|DOM Owner\|numNetSchedLowBandSec` | Num net sched low band sec | seconds | none |
| `numNetSchedLowBandThreshUs` | `vSAN Perf\|DOM Owner\|numNetSchedLowBandThreshUs` | Num net sched low band thresh us | microseconds | none |
| `numNetSchedLowToMidBand` | `vSAN Perf\|DOM Owner\|numNetSchedLowToMidBand` | Num net sched low to mid band | none stated | none |
| `numNetSchedMidBand` | `vSAN Perf\|DOM Owner\|numNetSchedMidBand` | Num net sched mid band | none stated | none |
| `numNetSchedMidBandSec` | `vSAN Perf\|DOM Owner\|numNetSchedMidBandSec` | Num net sched mid band sec | seconds | none |
| `numNetSchedMidToLowBand` | `vSAN Perf\|DOM Owner\|numNetSchedMidToLowBand` | Num net sched mid to low band | none stated | none |
| `numNetSchedRollingAvgLatUs` | `vSAN Perf\|DOM Owner\|numNetSchedRollingAvgLatUs` | Num net sched rolling avg latency us | microseconds | none |
| `numNetSchedSeenNonResyncTpBps` | `vSAN Perf\|DOM Owner\|numNetSchedSeenNonResyncTpBps` | Num net sched seen non resync tp bps | bytes/s | none |
| `numNetSchedSeenTotalTpBps` | `vSAN Perf\|DOM Owner\|numNetSchedSeenTotalTpBps` | Num net sched seen total tp bps | bytes/s | none |
| `oio` | `vSAN Perf\|DOM Owner\|oio` | Outstanding IO | none stated | none |
| `owner2PCCommitLatencyAvgUs` | `vSAN Perf\|DOM Owner\|owner2PCCommitLatencyAvgUs` | Owner2 pc commit latency avg us | microseconds | none |
| `readLeafOwnerLatencyLocal` | `vSAN Perf\|DOM Owner\|readLeafOwnerLatencyLocalUs` | Read leaf owner latency local | microseconds | none |
| `readLeafOwnerLatencyRemote` | `vSAN Perf\|DOM Owner\|readLeafOwnerLatencyRemoteUs` | Read leaf owner latency remote | microseconds | none |
| `recoveryWriteCongestion` | `vSAN Perf\|DOM Owner\|recoveryWriteCongestion` | Resync Read Congestions (sic, Python pack label) | none stated | none |
| `segCleanerUnmapCongestion` | `vSAN Perf\|DOM Owner\|segCleanerUnmapCongestion` | Seg cleaner unmap congestion | none stated | none |
| `segCleanerUnmapLeafOwnerMaxLatencyAvgUs` | `vSAN Perf\|DOM Owner\|segCleanerUnmapLeafOwnerMaxLatencyAvgUs` | Seg cleaner unmap leaf owner max latency avg us | microseconds | none |
| `serverSwitchCount` | `vSAN Perf\|DOM Owner\|serverSwitchCount` | Server switch count | none stated | none |
| `throughputSegCleanerUnmap` | `vSAN Perf\|DOM Owner\|throughputSegCleanerUnmapBytesPerSec` | Throughput seg cleaner unmap | bytes/s | none |
| `unmapCount` | `vSAN Perf\|DOM Owner\|unmapCount` | Unmap count | none stated | none |
| `writeLeafOwnerLatencyLocal` | `vSAN Perf\|DOM Owner\|writeLeafOwnerLatencyLocalUs` | Write leaf owner latency local | microseconds | none |
| `writeLeafOwnerLatencyRemote` | `vSAN Perf\|DOM Owner\|writeLeafOwnerLatencyRemoteUs` | Write leaf owner latency remote | microseconds | none |

### `vsan-pnic-net` (Python kind VsanPnic), 17 keys per vSAN pNIC

|vSAN label|Ops stat key|Label (Python pack)|Unit|Transform|
|---|---|---|---|---|
| `ioChainDrops` | `vSAN Perf\|pNIC:<vmnic>\|ioChainDrops` | IO chain drops | none stated | none |
| `ioChainRxdrops` | `vSAN Perf\|pNIC:<vmnic>\|ioChainRxdrops` | IO chain RX drops | none stated | none |
| `ioChainTxdrops` | `vSAN Perf\|pNIC:<vmnic>\|ioChainTxdrops` | IO chain TX drops | none stated | none |
| `pauseCount` | `vSAN Perf\|pNIC:<vmnic>\|pauseCountPct` | pNic 802.3x Pause Rate | percent | per-mille / 10 |
| `pfcCountRaw` | `vSAN Perf\|pNIC:<vmnic>\|pfcCountRaw` | PFC count raw | none stated | none |
| `portRxDrops` | `vSAN Perf\|pNIC:<vmnic>\|portRxDropsPct` | Inbound Packet Drop Rate of vSwitch Port | percent | per-mille / 10 |
| `portTxDrops` | `vSAN Perf\|pNIC:<vmnic>\|portTxDropsPct` | Outbound Packet Drop Rate of vSwitch Port | percent | per-mille / 10 |
| `portTxpkts` | `vSAN Perf\|pNIC:<vmnic>\|portTxpkts` | Port TX packets | none stated | none |
| `rxCrcErr` | `vSAN Perf\|pNIC:<vmnic>\|rxCrcErrPct` | pNIC RX CRC Error | percent | per-mille / 10 |
| `rxCrcErrRaw` | `vSAN Perf\|pNIC:<vmnic>\|rxCrcErrRaw` | RX CRC errors raw | none stated | none |
| `rxErr` | `vSAN Perf\|pNIC:<vmnic>\|rxErrPct` | pNIC RX Generic Error | percent | per-mille / 10 |
| `rxFifoErr` | `vSAN Perf\|pNIC:<vmnic>\|rxFifoErrPct` | pNIC RX FIFO Error | percent | per-mille / 10 |
| `rxMissErr` | `vSAN Perf\|pNIC:<vmnic>\|rxMissErrPct` | pNIC RX missed error (ring buffer full) | percent | per-mille / 10 |
| `rxMissErrRaw` | `vSAN Perf\|pNIC:<vmnic>\|rxMissErrRaw` | pNIC RX missed error (ring buffer full) (raw) | none stated | none |
| `rxOvErr` | `vSAN Perf\|pNIC:<vmnic>\|rxOvErrPct` | pNIC RX Buffer Overflow Error | percent | per-mille / 10 |
| `txCarErr` | `vSAN Perf\|pNIC:<vmnic>\|txCarErrPct` | pNIC TX Carrier Error | percent | per-mille / 10 |
| `txErr` | `vSAN Perf\|pNIC:<vmnic>\|txErrPct` | pNIC TX Generic Error | percent | per-mille / 10 |

**Totals.** 61 key definitions (6 + 38 + 17). On the lab cluster (5 hosts,
