# Changelog

## 1.2.0.22 (Unreleased)

- fix(adapter): build 22 (CI on PR #11, JDK 25 leg). Stop Collecting
  while a worker was inside a call interrupted it, and its cluster's
  `TIMED_OUT` report, when it reached the drain after the stop, was counted
  in `clusters_timed_out` instead of as stopped (`stopIsNotTimeout` failed
  on a slower runner). A cluster that reports `TIMED_OUT` in the drain after
  a stop now counts as stopped; deadline cuts are unchanged.
- test(adapter): no test outcome depends on runner speed. Every engine test
  (EngineTest, TiersEngineTest, DiskVmEngineTest, DiskVmTest, ScopeTest,
  the engine case in ClientFakeServerTest) runs on a simulated clock
  (`T.CLOCK`): a fake call takes 1 ms of simulated time or its fixture
  delay, never a real sleep, so `deadlineCut`, `noStarvationBehindSlowCluster`
  and the restart rule decide their cuts from the fixture. The two
  client-level timeout tests drop their wall-clock upper bounds: the
  `HttpTimeoutException` alone proves the budget (the server answers with
  data if the budget is not enforced). New `stopDuringCallIsNotTimeout`
  stops the cycle while a worker is blocked in a call. Behaviour under test
  is unchanged.

## 1.2.0.21 (Unreleased)

- fix(adapter): build 21 (Codex on PR #11). `exclude_types` could not
  exclude `computeCluster-remotedomclient` or `computeHost-remotedomclient`
  under `metric_scope=all`: the setting was lower-cased and then matched
  case-sensitively against the mixed-case ids, so both stayed queried and
  were reported as unknown. The lookup is now case-insensitive
  (`EntityType.byIdIgnoreCase`; the 69 ids differ by more than case);
  requests keep the exact id. `ConfigTest` and
  `ScopeTest.mixedCaseExclusion` exclude both, in either case: not in
  scope, not reported unknown, never queried (54 calls).
- test(adapter): `EngineTest.cutClusterRestartsFirst` failed once on the
  JDK 25 CI leg (run 42385405904) because its cases raced wall-clock
  deadlines and a real per-call timeout. The restart rule is now two pure
  functions (`CycleEngine.settled`, `CycleEngine.restartChoice`, no change in
  behaviour) tested from injected outcomes, plus one engine run on a
  simulated clock with one worker, where a fixture call moves the clock past
  the deadline; nothing in the test depends on real time.

## 1.2.0.20 (Unreleased)

- fix(adapter): build 20, the build 19 review. B1: REFERENCE.md printed a
  dashboard key and label for the 44 `all` rows of `vsan-esa-disk-layer`
  and `vsan-esa-disk-scsifw` (a splice in the table generator); the rows are
  regenerated, and the new `ReferenceDocTest` checks every key table row
  against `EntityType` (stat key, label, leaf, instance template, transform,
  scope; every type's catalog documented once; 1,805 rows). W1: a cluster
  counts as cut only when the cycle deadline cut it (its task ended
  `TIMED_OUT` at or after the deadline, or never reported); one that timed
  out on its own call before the deadline no longer moves the next cycle's
  start; the restart-first rule is off with `max_parallel_clusters=1`, where
  the restarted cluster would hold the only worker. Its cost with two or
  more workers is documented (a cluster too slow to finish holds one worker
  for one cycle per rotation lap). `cutClusterRestartsFirst` now asserts
  the restart with two workers, no move for a per-call timeout, and no
  restart with one worker. N2: the `exclude_types` configure WARN wording
  is build 18's again ("this build collects"). N4: `stitch_targets`
  identity text names Resync on HostSystem and the remote rule on all three
  kinds. N5: a PerfParser javadoc back on its class.
- test(adapter): W2: T4 (d) asserts that every cluster completes at least
  once in any four consecutive cycles over all 12 (build 19 max gap 2;
  build 18's rotation 6 and build 19 without the restart rule 5 fail it);
  the two-cycle bound stays from cycle 10, after learning has settled. W3:
  T4 (e) gets its own ceilings, peak < 60 MB and retained < 45 MB, sized on
  its readings (retained 31.2 to 32.4 MB, peak up to 46.1 MB) plus a 9 MB
  sampler spike like the one seen once on (a); (a)'s margin (6.3 to 7.5 MB
  against 15 MB, one 16.3 MB spike) is recorded, its ceiling unchanged.
  N1: the class doc's variance figures are build 19's (cycle 4 1,662 to
  1,664 calls in 237 to 238 s). N3: `ScopeTest.remoteRuleVmTier` covers the
  remote rule on `remote-virtual-machine`, `remote-vscsi` and
  `remote-virtual-disk` (a VM of the read lands, a foreign VM, a foreign
  path and a non-path id count in `clusters_unresolved`, not as VM or vdisk
  misses). W4: the fixture provenance cites factory commit `478d67b` on
  main (PR #329); `cf40feb` was on a deleted branch.

## 1.2.0.19 (Unreleased)

- feat(adapter): build 19, design v3 (Build 3). `metric_scope` (Advanced,
  default `dashboard`, unchanged behaviour; an account saved before 1.2.0
  gets `dashboard`). `all` collects every metric of the 69 vSAN Performance
  Service entity types modelled by vsanhostmetrics 2.0.0: 1,595 keys
  (+1,144: 686 on 21 existing types, 458 on 36 new types onto HostSystem,
  ClusterComputeResource and VirtualMachine), plus the 210 ESA dedup labels
  the 9.1.1 service returns beyond that model (design addendum Q3, P3
  capture), 1,805 keys in all, no labels filter (labels outside the catalog,
  the 41 `Actual` twins on the lab, are dropped and counted in `PERF tier
  labels_dropped`, named once per type per day at INFO). Keys only added;
  leaves frozen by the v3 leaf rule (`EntityTypeTest` reproduces all 1,144
  Appendix A leaves from the Python units); `pctMemUsed` pushed as served
  (integer percent, P3). New id grammars: `host-cpu` 1 or 2 parts (the host
  aggregate is `vSAN Perf|pCPU|*`), world types `<world>-<id>`, iSCSI LUN
  `<target>/<lun>`. Remote (HCI Mesh) types land only on objects of the
  queried cluster, else `clusters_unresolved` with one WARN per refresh
  (addendum Q6). The nine types that fault on every query form on 9.1.1 are
  optional types: counted in `optional_calls_failed` and learned after three
  consecutive faults with the re-probe spread (addendum Q5). `cluster-pmem`
  never lands on a host (its `PMem` group is `host-pmem`'s there). Switching
  scope restarts learned types and dedup marks (a new engine; pinned in
  `ScopeTest`). Cost under `all`, lab: ~5.8x series per host, ~3.8x per
  cluster; steady calls per cluster 23 to 37 with the default tiers. Summary
  line gains `scope=`, `PERF tier` gains `scope` and `labels_dropped`.
- fix(adapter): cluster rotation under a sustained deadline cut (found by
  the new T4 (d)): a cluster a worker picked up after the deadline (timed
  out without a call) counted as attempted, so the next cycle started past
  the end of the list and the same clusters completed every cycle (51 of 70
  in two cycles). Now only a start before the deadline counts, and the
  earliest cluster the cut interrupted goes first next cycle, unless it was
  one the workers started with or the previous cycle cut it too (the Codex
  starvation case, `noStarvationBehindSlowCluster`, still holds). Affects
  `dashboard` too, only on cycles that hit the deadline (build 20 makes
  that exact: see 1.2.0.20).
- test(adapter): the 69 P3 captures (`all-<type>-2026-11-07.json`,
  verbatim) as fixtures; `ScopeTest` (request with and without the filter,
  parser over every capture under both scopes, new grammars, the engine
  under `all` on the lab: 56 / 54 / 54 / 37 calls, 13,516 values on cycle 1,
  the read-back keys, the scope switch, the remote rule); the replay harness
  takes `--scope all` (templates from the P3 captures, faults served as
  captured) and T4 gains (d), (d') and (e), all passing (README).

## 1.1.0.18 (Unreleased)

- test(adapter): build 18, the push model from live readings. The replay
  harness's push duration is now 60 ms per call plus 1.5 ms per resource
  (`--push-fixed-ms`, `--push-ms-per-resource`), from devel 9.0.2 `PERF
  push` lines (build 16, VM tier on: 1 cluster 54 to 69 ms, 5 hosts 47 to
  74 ms, 30 VMs 48 to 113 ms), quoted verbatim in README; build 17's 9 ms per
  resource was a linear extrapolation from one batch. The build 17
  synchronous-push simulation is kept. T4 with the defaults: every case
  passes, (b') included (all 70 clusters every cycle, 107 to 127 s, pushes
  50 to 93 s); default tiers push 1.6 s per cycle. README also notes that a
  settings change builds a new `CycleEngine`, so the learned-empty state and
  the dedup marks restart (design B7; build 10 review): on devel the first
  cycle after a reconfigure re-sent both window buckets (5,988 values =
  2 x 2,994) and re-probed the three lab-empty types (26 + 3 VM-tier
  calls = 29).

## 1.1.0.17 (Unreleased)

- test(adapter): build 17 (Codex on PR #10): the replay harness models the
  Suite API push as synchronous in simulated time. A push call moves the
  caller's simulated clock on by its modelled duration (the drain loop's
  deadline check and `PERF push` / `push_ms` see it: the Codex case now
  reports `push_ms=4000000`, `cycle_ms=4004940`), and workers that would
  overfill the 16-message result queue during a push start no earlier than
  its end. No wall-clock sleep. T4 with push time counted at 9 ms per
  resource: default tiers unchanged (pushes ~6 s per cycle; all assertions
  pass); all tiers with the VM tier per vCenter passes (pushes 144 to 214 s
  per cycle, 41 to 53 of 70 clusters done); **per cluster, (b') fails**:
  57 of 70 clusters complete on cycle 1 (pushes 216 to 239 s per cycle). The
  assertion is not loosened; the 9 ms per resource is an extrapolation that
  a live reading will replace. VM N = 1 (c): 16 to 17 of 70 clusters per
  cycle, heap 22.4 MB.

## 1.1.0.16 (Unreleased)

- fix(adapter): build 16, the build 15 review. W1: a failed push call's
  pending marks are kept until the end of the flush, then each affected
  (entity, metric) mark is lowered below the failed sample time (new
  `Watermarks.lower`), so a failed older bucket is sent again next cycle
  even when the newer bucket of the same metric was pushed clean (that newer
  bucket is sent again too and lands on itself). `EngineTest` fails each of
  the four push calls in turn: every failed value is re-sent, the only other
  re-sent values are the newer buckets of the failed metrics, and the cycle
  after sends nothing. W2: `stitch_targets` lists vSAN Direct Cluster, File
  Service and IOInsight on HostSystem too, with the runtime-landing rule in
  its identity text. W3: T4 (b) runs all tiers (disk on) and asserts the
  deadline path; (b') per-cluster asserts every cluster completes; (c) VM
  N = 1 heap only. W4: regression ceilings beside the 65 MB budget (default
  peak < 15 MB; VM cases peak < 50 MB, retained < 35 MB); the recording
  stitcher builds the real push body; the sampler's 2 to 5 MB blind spot is
  documented. N1: README states the run-to-run variance and the cycle-4
  margin. N2: the recording stitcher models push time (9 ms per resource,
  from the lab's 5-host batch), reported per cycle. T4 numbers: (a) 6.9 MB;
  (b) 32.7 MB; (b') 33.8 MB; (c) 37.5 MB.

## 1.1.0.15 (Unreleased)

- fix(adapter): build 15, the VM tier under the 65 MB heap budget (T4 (b),
  build 14 finding). The dedup marks of pushed values are now committed per
  push call (one target kind and sample time, at most 100 resources / ~1 MB)
  as soon as that call comes back clean, instead of being held for the whole
  cycle until every push was clean. A pending entry is 12 B (entity hash,
  catalog position and width packed; the group's one sample time held once)
  in arrays sized from the previous cycle's largest group. Semantics per
  (entity, label) are unchanged: a mark is set only on a clean push; a failed
  call leaves exactly its own values' marks unset, so only those buckets are
  sent again next cycle (was: every bucket of the cycle). At the 70-cluster
  example with the VM tier at N = 3 the peak heap delta fell from 76.6 to
  35.7 MB (per vCenter) and from 74.6 to 37.4 MB (per cluster), ~28 MB of it
  retained state; default tiers from 16.8 to 7.3 MB. T4 now asserts the
  65 MB budget for the VM variant. Tests: `EngineTest.failedMiddleBatchOnly`,
  `WatermarksTest` per-group commit.

## 1.1.0.14 (Unreleased)

- test(adapter): build 14, design T3 and T4 (pak issue #6). T3: a replay
  harness (`tests/.../ReplayHarness.java`, standalone `ci/replay.sh`) runs
  the real `CycleEngine` against N synthetic clusters built from the
  verbatim captures (cluster uuid, node uuids, NIC names, VM uuids and vdisk
  paths rewritten; buckets moved 5 minutes per cycle; replies generated as a
  stream through the production parser), with per-call latency, both
  ceiling models (per vCenter, per cluster), a recording stitcher and
  simulated time through the engine's existing injectable clock (no
  production change). T4: `ReplayBudgetTest` in `ci/run_java_tests.sh`. At
  70 clusters x 6 hosts x 2 pNICs, default tiers, 4 workers, 0.19 s per
  call, 7 calls/s per vCenter: cycles 1 to 3 take the deadline path (~1,664
  calls in 240 s), cycle 4 completes in 235 s, then 1,610 calls in 230 s;
  98,350 series per cycle; peak heap delta 16.8 MB (cold start), ~8 MB
  steady. Per-cluster model: 88.9 s, then 78.7 s. **Finding:** default
  tiers plus the VM tier at N = 3 peak at ~71 to 77 MB, over the 65 MB
  budget (~27 MB retained); the test guards it at 90 MB and prints it. VM
  tier stays off by default.
- docs(adapter): `stitch_targets` in adapter.yaml (factory #310, PR #321):
  the generated docs now list HostSystem, ClusterComputeResource and
  VirtualMachine as push targets with their key groups.

## 1.1.0.13 (Unreleased)

- fix(adapter): build 13 (Codex on PR #8): a VM read that fails after an
  earlier successful device read no longer lets `virtual-disk` run on the
  stale device map (a moved disk could land on its old VM or bus position).
  The failure is tracked per cluster: `virtual-disk` is not collected for
  the cluster until a VM read succeeds, the read is retried on the cluster's
  next VM run, and `virtual-machine` / `vscsi` keep using the previous read.
  The WARN and REFERENCE.md say exactly that. New test
  `DiskVmEngineTest.failedRefreshSuppressesVirtualDisk`.

## 1.1.0.12 (Unreleased)

- fix(adapter): build 12, every finding of the build 11 review. B1: the VM
  device list is re-read every 12th VM run of a cluster even with no miss
  (v2 device-list caching (b)), so a VMDK moved between two VMs or to
  another bus position is placed right again within 12 runs (it kept
  landing on the old VM); a path left unplaceable (seen on two VMs at read
  time) also asks for the rate-limited re-read. W1: the VM read holds the one
  VM permit, so one VM read page or one VM-type response is in memory across
  the pool (`vm_permit_wait_ms_max` covers both). W2: when every host's
  `QueryDisksForVsan` fails the cluster keeps an empty map and the retry
  waits for the 12-cycle limit (was: 5 calls per cluster every cycle). N1:
  `oneDeadlineAcrossRetries` budget 1,500 ms with 900 ms server sleeps, the
  repeat required only for `slowResetOnce` (it failed under load, on `main`
  too). N2: the gzip stream is opened inside the try, so a body that is not
  gzip is Unreadable and not re-sent. N3: the QueryDisksForVsan WARN names
  the first fault class. N4: `marks_bytes_est` counts the slot arrays as G1
  humongous objects (VM tier at the example: 21.2 MB estimated and
  measured; was 14.6 MB estimated). N5: `VsanPerfAdapter` class doc. N6: the
  same Ops row on two pages is not ambiguous.

## 1.1.0.11 (Unreleased)

- feat(adapter): build 11, Build 2 round 2 (factory design
  `knowledge/designs/vsanperf-mp-v2.md` with the P2 addendum): the disk tier
  (`tier_disk`, default off; 4 types, 31 keys onto HostSystem instanced by
  vSAN disk uuid; the disk-to-host map from one `QueryDisksForVsan` per host,
  keyed on non-empty `vsanUuid`, cached and re-read every 12th cycle, on a
  host-set change, or after a miss at most once per 12 cycles; R2 asks for
  `configManager.vsanSystem` only with the tier on) and the VM tier
  (`tier_vm`, default off; `vm_tier_every_n_cycles` default 3, staggered per
  cluster; 3 types, 21 keys onto VirtualMachine resolved on
  (`VMEntityObjectID`, `VMEntityVCID`) from the Ops VirtualMachine list at
  500 per page; VMs from a ContainerView rooted at the cluster, 50 per page,
  reduced page by page; `virtual-disk` and `vscsi` instanced by bus position
  from `controllerKey` to the controller's `busNumber`, prefix `scsi`,
  `nvme`, `sata` or `ide`, any other controller never guessed; the VM read is
  re-run only after an instanceUuid or path miss, at most once per 12 cycles
  per cluster; one VM-type call in flight per instance). Every perf response
  is now parsed from the gzip stream one entity at a time (`parse=stream`),
  with a 4 MB wire cap (16 MB for VM types) and no decoded cap; a stream cut
  mid-entity fails the call and pushes nothing of it. The Ops resource list
  read retries a page once (VCF Operations drops idle keep-alives after
  ~30 s). New self-metrics `disks_unresolved`, `vms_unresolved`,
  `vdisks_unresolved`, `optional_calls_failed`. 33 types, 451 keys.
- fix(adapter): build 10 review. B1: `docs/overview.md` rewritten for the
  tiers (it still described Build 1). W1: a cluster is FAILED only when a
  core type fails, its object vanished, or every call fails; an optional
  type's failure is counted per tier (`optional_calls_failed`, `PERF tier
  failed=`), named in `last_error` and WARNed once per cluster and type per
  refresh, and the cycle succeeds (a single faulting optional type on a
  one-cluster account no longer shows it DOWN). W2: `docs/installing.md`
  lists the tier settings. N1: fault-learned types re-probe with the same
  per-(cluster, type) offset. N2: any cluster-map read restarts the miss rate
  limit (no back-to-back reads), and a failed list read is named as the
  cause in the WARN. N3: ClusterComputeResource identifiers described as
  read in P2, live read-back still at T5. N4: describe comment and README
  file list. N5: the wrong-form id WARN is once per type per day.
- version: `1.1.0` (build 11). The scale release tag is to be `v1.1.0.11`
  after T3, T4 and the three-platform proof. Lab calls per cycle: default
  tiers 26 then 23; all tiers 33 then 28 on the cluster's VM cycle, 30 then
  25 on the others.

## 1.0.0.10 (Unreleased)

- feat(adapter): build 10, Build 2 round 1 (factory design
  `knowledge/designs/vsanperf-mp-v2.md`): the host tier (13 types, 149 keys
  onto VMWARE HostSystem, `TCP/IP:<stack>`, `vNIC:<vmk>` and
  `vNIC RDT Latency:<vmk>` instanced) and the cluster tier (10 types, 189
  keys onto VMWARE ClusterComputeResource, resolved by (MoRef, VCID) through
  a ClusterComputeResource list read like the HostSystem one; the
  identifiers were assumed at build 10; since read on devel in the design's
  P2 round, live read-back by this pak still at T5). `vsan-direct-cluster`,
  `vsan-file-service` and `ioinsight` land per entity: cluster uuid on the
  cluster, one node uuid of that cluster on the host, else skipped and
  counted. New Advanced settings `tier_host` and `tier_cluster` (default
  `true`, so an upgraded 1.0.0.9 account gets both) and `exclude_types`.
  Every Build 1 key, the account identity and the collector identity are
  unchanged. Behaviour changes for every tier: every non-empty bucket in the
  window newer than the per-(entity, metric) mark is pushed (Build 1 pushed
  only the newest), so a cold start pushes up to two buckets per series; an
  empty answer is learned after 3 consecutive cycles for 24 h plus a
  per-(cluster, type) offset under an hour, an empty re-probe re-learns at
  once, core types and `ioinsight` are never learned; dedup marks moved to a
  compact hashed table (~0.7 MB instead of ~13.6 MB at the 70-cluster
  example, `WatermarksTest`). New self-metrics `Collection|clusters_unresolved`
  and `Collection|entities_unparsed`; the cycle summary gains per-tier series
  and the new counts; PERF lines gain `tier=`, `calls_by_tier`, `kind=`,
  `PERF tier`, `PERF identity read=ops_clusters`, and marks size on
  `PERF cycle`. Lab with default tiers: 26 calls per cycle, 23 once the
  empty types are learned; 1,186 series per cycle. Tests: 19 new verbatim
  fixtures (PROVENANCE-2026-11-06-build2), the 338 keys row for row against
  a copy of the v2 tables, id forms, the cluster resolver with one MoRef in
  two vCenters, the learned-empty state machine, compact marks memory, a
  cluster-tier call and an empty echo over the fake server. Not in this
  build: the disk and VM tiers (blocked on design P2), streaming parse
  (`parse=tree` in PERF call).

## 1.0.0.9 (2026-11-06)

- release: first release line (RULE-014: `0.x` is the hand-built preview line, releases are `1.x`). Same source as dev preview 0.0.0.9 (build 9). Baseline for external testing: host tier only (DOM Owner, DOM Client, pNIC onto VMWARE HostSystem); key names may still change before the scale release if the key tree is aligned with the vsanhostmetrics `cptvsan|` grammar. T3/T4 moved to issue #6.

## 0.1.0.9 (Unreleased)

- fix(adapter): build 9, the cycle summary and the PERF timing lines reach
  the instance log. On devel 9.0.2 (build 7, factory
  `knowledge/context/investigations/2026-11-06-vsanperf-proxy-logs.md`)
  `VsanPerfAdapter_<id>.log` held only lines written through the adapter's
  own `logInfo`/`logWarn` (configure, framework, `onCollect` WARNs): no
  cycle summary and no `PERF ` line, also on an instance with timing on and
  on one failing every cycle. Cause: the engine and the JSON client logged
  through `componentLogger(CycleEngine.class)` /
  `componentLogger(VsanJsonClient.class)`, and those lines never reached
  the file. The summary line itself existed since build 5 (always INFO,
  once per cycle, failed cycles too); it was simply not delivered. Now every
  pak line goes through the adapter's `logInfo` / `logWarn` / `logError`,
  the route proven to land, at INFO (PERF lines included).
  `perf_timing_logging` is re-read from the instance's stored settings
  every cycle (it was read only at configure); a change is logged once. New
  test `EngineTest.summaryAndPerfLinesEveryCycle` captures the log: the
  summary line on a healthy and a failed cycle, no PERF line with the flag
  off, `PERF cycle` and `PERF call` lines at INFO once the flag is turned on
  between cycles, none after it is turned off. REFERENCE.md gains the
  owner-versus-client reading of the DOM Owner and DOM Client groups and the
  summary line's format.

## 0.1.0.8 (Unreleased)

- fix(adapter): build 8, the `VsanPerfCollector` identity is exactly what the
  account stores, trimmed only: `vcenter` = vCenter Server (was
  lower-cased), `cluster_scope` = Clusters to include, `*` when empty (was
  split, trimmed per item and de-duplicated). The account's identity is the
  raw strings, so two accounts differing only in case or spacing
  ("vc.lab" / "VC.lab", "cl01,cl02" / "cl01, cl02") were distinct accounts
  enumerating the same object key, and one instance got an object it did not
  own. Normalisation stays in the cluster filter only. ConfigTest covers
  both cases and that the filter still normalises; REFERENCE.md, the README
  identity note and the describe.xml comment say so. Not yet tagged, so no
  object exists under the build 7 key outside dev installs.

## 0.1.0.7 (Unreleased)

- fix(adapter): build 7, the cycle runs in `collect()` so an unreachable
  vCenter shows the account DOWN. Live on devel 9.0.2 (build 5) the stitch
  and all 12 self-metrics worked, but an unreachable host showed ERROR
  "Resource discovery failed and there was nothing to collect: IOException:
  vCenter login or ServiceInstance read failed: Login: ConnectException":
  the platform does not list the adapter instance among the resources it
  collects, so a cycle run from `rediscover` never reaches the collect
  failure path. **Behavior change:** each instance now discovers one object,
  "VCF Content Factory vSAN Performance Collector" (new kind
  `VsanPerfCollector` in describe.xml, nameKeys 40 to 42, identifiers
  `vcenter` and `cluster_scope`), and the whole cycle runs in `collect()` for
  it, as compliance does for `ComplianceWorld`; a failure is a normal collect
  failure, mapped to DOWN for an unreachable target with the same
  credential-free message. The object's identity is the account's own
  (vCenter Server lower-cased; Clusters to include as normalised items, `*`
  for all), not the vCenter instance UUID, which needs a login and so is
  unknown exactly when vCenter is unreachable. The self-metrics stay on the
  adapter instance (proven on devel 9.0.2): `rediscover` now only hands
  over the instance resource and never throws. A new instance shows the
  object, and so the DOWN mapping, from its second cycle. New SDK-free
  `CycleReport` (self-metrics and last_error to the sink, then throw with
  the cause). Test: EngineTest `loginFailsCollectThrows` (login fails: the
  collect path throws a ConnectException-caused failure that maps to DOWN,
  nothing is read from or pushed to VCF Operations, no perf call, the 11
  self-metrics and last_error still reported; a healthy cycle does not
  throw and reports `none`); ConfigTest covers `cluster_scope`. README
  "Acceptance on each platform" records the 9.0.2 results and the change;
  the pNIC Metrics-tab rendering check and the collector log scan stay open
  (they need a logged-in browser and a Cloud Proxy login, which the lab
  profile lacks).

## 0.1.0.6 (Unreleased)

- fix(adapter): build 6, two defects from the external (Codex) review of
  the Build 1 PR.
  - Scheduling starvation: the cycle's start cursor advanced only when a
    cluster finished OK, so a cluster whose calls always outlast the cycle
    budget was first every cycle and the clusters after it were never
    queried (one worker, two clusters: after three cycles the first queried
    three times, the second never). The cursor is now the furthest cluster
    a worker started, whatever the outcome (OK, failed, timed out, cut), and
    the next cycle starts after it.
  - Timeout not carried through retries: a call's repeat after
    NotAuthenticated or a transport failure got the whole original budget
    again (a 500 ms budget ended at 721 ms with two 350 ms attempts). Each
    public client call now fixes one absolute deadline; Login, the release
    fallback, ServiceInstance content, re-login, the transport retry, view
    create, every RetrievePropertiesEx page and DestroyView all spend it,
    each exchange gets only the time left (capped by the call timeout), and
    a spent deadline fails before sending. The engine already passes each
    caller-thread call the lesser of the call timeout and the time left to
    the cycle deadline, so inventory now ends inside the cycle budget too.
  - Tests: EngineTest `noStarvationBehindSlowCluster` (the repro: the
    second cluster is queried from cycle 2 on, the slow one still gets a turn
    every cycle); ClientFakeServerTest `oneDeadlineAcrossRetries` (two 350 ms
    attempts, after NotAuthenticated and after a reset, under a 500 ms
    budget fail by about 500 ms, asserted at or under 600 ms).

## 0.1.0.5 (Unreleased)

- fix(adapter): build 5, every finding of the build 4 review (factory
  `knowledge/context/reviews/vsanperf-build-4-review.md`, 2 BLOCKING,
  3 WARNING, 8 NIT) and two design corrections.
  - B1: a worker catches every throwable. A `VirtualMachineError` (or a
    thread kill) is carried to the caller thread in `ClusterDone`, the pool
    is shut down and the error is rethrown, ending the cycle; any other
    throwable fails the cluster with "internal error (<class>)". Was:
    swallowed by the `FutureTask`, cluster counted OK, `last_error=none`.
  - B2: a ContainerView fault that survives the view's recreation is
    rethrown with fixed text, never the server's faultstring, which can
    name the view (its moId embeds the vCenter session key). Every vSphere
    fault text is also scrubbed of `session[...]...` ids. REFERENCE.md's
    logging claim is corrected to what the code guarantees.
  - W1: README "Acceptance on each platform" (and docs) states the T5
    checks: self-metrics on the instance, and DOWN (not ERROR "nothing to
    collect") for an unreachable vCenter; and why the cycle runs in
    `rediscover` rather than compliance's `collect()`.
  - W2 (no stitched-key section in the generated docs) is a factory
    tooling gap, routed by the orchestrator; no pak change.
  - W3: design T3 (replay harness) and T4 (65 MB heap gate at 70 clusters)
    are **open and gate the scale release, issue #6** (README "Open before the scale release
    v* tag", docs/overview.md Known Limitations). Not built this round.
  - N1: Stop Collecting is logged and counted as stopped, not as the cycle
    deadline or `clusters_timed_out`. N2: the perf-phase span is a per-cycle
    object, so a straggler of a cut cycle cannot move the next cycle's call
    rate. N3: the newest sample is chosen by timestamp, not CSV position.
    N4: an answer that cannot be parsed (`Unreadable`) is never re-sent.
    N6: why the HostSystem list is filtered client side (the vendor spec
    has no identifier filter on `GET /api/resources`), in `OpsHostMap`.
    N7: a host collected later is picked up within about an hour,
    documented. N8: a `RuntimeException` on the caller thread becomes a
    failed cycle with fixed text, after the self-metrics and `last_error`
    are pushed.
  - Design correction (push timestamps): values are grouped by sample time
    first, one `pushStatsBatch` call per time, and the 100-host / ~1 MB caps
    apply within each time group (was: caps across all groups).
  - Design correction plus N5: the duplicate-suppression mark is per
    (entityRefId, label), not per entity, and is recorded only after every
    push of the cycle came back clean; a failed batch's buckets are re-sent
    next cycle while still in the window.
  - Tests: EngineTest gains an OutOfMemoryError probe, a RuntimeException
    after the call and from the call, a caller-thread defect, Stop
    Collecting, an unclean push re-sent, mixed sample times pushed each
    under its own time, the 100-host cap within one time, and a label that
    lags another within one entity; ClientFakeServerTest gains a view fault
    whose text carries a real-shaped `session[<uuid>]<uuid>` id (absent from
    the log, the thrown message and `last_error`) and an unreadable answer
    sent once; PerfParserTest gains newest-by-time.

## 0.1.0.4 (Unreleased)

- feat(adapter): build 4, the first vSAN Performance build (design Build 1,
  factory `knowledge/designs/vsanperf-mp-v1.md` with its 2026-11-06
  Addendum; Q1 to Q5 on the recommended answers). The template skeleton is
  renamed to adapter kind `vcfcf_vsanperf`, entry class
  `com.vcfcf.adapters.vsanperf.VsanPerfAdapter`, display name "VCF Content
  Factory vSAN Performance"; the example classes and tests are removed.
  describe.xml declares only the adapter instance: `vcenter_host` and
  `cluster_include` as identity, `cluster_exclude`, `max_parallel_clusters`
  (4), `call_timeout_seconds` (30), `cycle_deadline_seconds` (0 = interval
  minus 60 s) and `perf_timing_logging` (false) as Advanced settings, and
  twelve `Collection|*` self-metrics (including `perf_calls_per_second` over
  the perf phase's elapsed time and `perf_call_ms_avg`). New SDK-free
  classes: `VsanJsonClient` (vSphere JSON API: Login with the
  `vmware-api-session-id` header reused across cycles, re-login once on
  NotAuthenticated, release id 8.0.3.0 with fallback to the server's vim25
  version, gzip, 4 MB wire / 8 MB decoded caps, fault bodies read up to
  64 KB, per-call timeout, faults never re-sent, one transport retry; the
  session-lived ContainerView inventory read with ContinueRetrievePropertiesEx
  paging and destroy-recreate-retry on a view fault, its moId never logged),
  `Inventory` (boxed PropertyCollector values; a cluster is vSAN when a
  member host has enabled=true and a clusterInfo.uuid, majority uuid with
  one WARN; empty nodeUuid left out), `PerfParser` (newest non-None sample
  with its own timestamp, per-mille to percent), `EntityType` (the design's
  61 keys), `OpsHostMap` (HostSystem by MoRef plus VMEntityVCID, ambiguous
  and VCID-less rows never matched), `CycleEngine` (bounded pool across
  clusters, DOM owner first, cycle deadline, per-entityRefId duplicate
  suppression, learned types for 24 h, batched stats push per sample time,
  `PERF` lines), `VsanPerfConfig`, `ClusterFilter`, `CappedBody`,
  `VsanFault`, `Watermarks`, `LearnedTypes`, `Failures`. Tests: eight
  plain-main classes (T1 and T2 of the design) over verbatim captures copied
  to `tests/fixtures/vsan-perfsvc-json/` with provenance, plus a JDK
  `HttpServer` fake vCenter; a verbatim `SimpleJson` copy in `tests/stubs/`.
  README, REFERENCE.md and docs rewritten for the pak (migration from the
  Python pack, collector-group advice).

## 0.1.0.3 (Unreleased)

- ci: supply-chain and release hardening (#18). The release workflow splits
  into a read-only `build` job and a write-token `publish` job, pins the
  buildkit tag with a sha256 and the defect registry to
  `DEFECT_REGISTRY_COMMIT`, gates on `pak-compare`'s exit status, fails the
  defect gate closed, installs hash-pinned PyYAML, publishes `SHA256SUMS` and
  a provenance attestation, and never overwrites a published Release. The
  deploy key moves to the `sdk-release` Environment secret (configure its
  required reviewers and `v*` tag rulesets separately). New
  `sdk-validation.yml` runs `validate-sdk` and the gate tests on every push
  and PR.
- fix(adapter): the example navigation spec is rooted at the World kind
  (`rootResourceKind="World"`, path
  `example_adapter::World||example_adapter::ExampleResource::child`), the way
  VMware's vSphere adapter roots "vSphere Hosts and Clusters" at
  `VMWARE::vSphere World`. Build 2 rooted it at the adapter instance kind
  (`example_adapter::example_adapter||example_adapter::World::child||...`);
  the instance resource has no children, so the first hop never matches and
  the tree shows the instance with nothing under it (seen live on the UniFi
  and Synology paks built from this template, 2026-10-28). describe.xml's
  header and REFERENCE.md now teach the World root.

## 0.1.0.2 (2026-10-16)

- fix(adapter): `allowInsecure` now defaults to `false` in `describe.xml`, so
  a new instance validates the target certificate (#8). The parse stays
  strict (only `true`, in any letter case and with no surrounding spaces,
  turns validation off) and configure logs one WARN while
  it is off. **Upgrade note:** an existing instance keeps its stored value; an
  instance saved with the build 1 default of `true` still skips validation
  until it is edited and set to `false`.
- fix(adapter): `mapCollectException` walks the cause chain and maps
  `ConnectException`, `UnknownHostException`, `NoRouteToHostException`,
  `HttpConnectTimeoutException`, `HttpTimeoutException` and
  `SocketTimeoutException` to DOWN, wrapped or not (#13). New
  `ExampleFailures` helper, same shape as the UniFi and Synology ones.
- fix(adapter): `Status|value` is published only when the field is present
  and numeric (#10). New `metricIfPresent(out, key, SimpleJson)` helper skips
  a missing, null or non-numeric field instead of publishing the `0.0` that
  `SimpleJson.asDouble()` returns for it; a real zero is still published.
- fix(adapter): one immutable snapshot per collection cycle (#9). `rediscover`
  starts the cycle and reads `/api/items` once; every `collect` in the cycle
  reads the same snapshot through a `Map<id, item>` index (was one full-list
  GET per resource plus a linear scan). A failed read is remembered for the
  rest of its cycle, so a down target costs one read per cycle. New
  `CycleCache` helper; the children's `SnapshotCache` is referenced for what
  a production pak adds on top (failure back-off across cycles, one WARN per
  failed read). The maximum age is in the template too (entry below).
- fix(adapter): a cycle running while the instance is discarded stops with
  `InterruptedException("discarded")` instead of an NPE (the snapshot read
  copies the volatile `api` field to a local and checks it), and `rediscover`
  checks `isAbortRequested()` at entry (#14).
- fix(adapter): response bodies are read through a bounded handler (#12). New
  `BoundedBody` caps a 200 body at 8 MB (`DEFAULT_MAX_BYTES`, one constant)
  and stops reading past it; `ExampleApiClient.get()` then fails with
  `IOException` after the framework's retry loop, so an oversized body is
  fetched once, not once per retry. A non-200 body is not read at all (the
  transfer is cancelled at once, so a slow or endless error body cannot hold
  the collector thread; review W1). `get()` rejects a 200 with a non-JSON
  `Content-Type` (for example an HTML proxy page) before parsing.
- fix(adapter): `ExampleConfig` validates host and port (#16). The host must
  be a bare hostname, IPv4 address or bracketed IPv6 address (no `/ @ ? #`,
  port or whitespace) and the port a number from 1 to 65535; a bad value
  throws an `IllegalArgumentException` naming the field, which Test
  Connection shows. The base URL is built with `new URI(...)`. A configure
  with an invalid value builds no client and every read fails with that
  message (ERROR) until the instance is edited. `port` is now an `int`.
- fix(adapter): `ExampleConfig.password` is private, read only through a
  package-private `password()` used by `buildHttpClient`, and `toString()`
  prints host, port, username, allowInsecure and `password=***` (#15).
- docs: correction to the 0.1.0.1 entry, which says the workflow fetches the
  SDK jar "via `SDK_RUNTIME_TOKEN`": it uses the `SDK_RUNTIME_SSH_KEY` deploy
  key (a shallow clone of the private sdk-runtime repo); the PAT is retired
  (#17).
- docs: README says CI clones the sdk-runtime repo (not a release), pins the
  buildkit download to a full `sdk-buildkit-vX.Y.Z` tag with a `sha256sum -c`
  step, and says the Broadcom jar belongs in a private repo only, never a
  workflow artifact; `docs/installing.md` states the `false` TLS default
  (#17). README lists the new helper classes and tests.
- fix(adapter): no host or port validation message ever echoes the rejected
  value, not even an all-digit port (a PIN pasted there); each names the rule
  broken (for example "must not contain a user name or password ('@')"), so a
  credential pasted into Host or Port is not logged every cycle or shown in
  Test Connection
  (review B1, #16). A host whose last label is all digits ("999", "10.1") is
  rejected unless it is a full dotted IPv4 address (review N6).
- fix(adapter): the cycle snapshot is never served once it is half a
  collection interval (2.5 minutes, `COLLECTION_INTERVAL_MS / 2`) old, even if
  no new cycle began, so a skipped rediscover does not serve the previous
  cycle's data at the default interval (a safety net, not a guarantee: an
  operator can change the interval per instance). Age is measured on
  `System.nanoTime()`, so a backward wall-clock step cannot disable it. The
  `needsRediscovery` Javadoc and the `beginCycle` call say the snapshot
  cycle depends on `needsRediscovery()` returning true (review W2, #9).
- fix(adapter): `metricIfPresent` accepts plain decimal numbers only; Java-only
  forms such as `"42d"`, `"1f"` or `"0x1p3"` are skipped like any other
  non-number (review N1, #10).
- fix(adapter): `prop()` skips an absent (null) value instead of publishing
  `""`, so a missing item name is a gap, not an empty-string sentinel. Two
  comments corrected: the `rediscover` abort check only catches a stop in the
  moment after `onCollect` starts, and an item absent from the snapshot is
  still marked DATA_RECEIVING by the framework (review N3, N4).
- fix(adapter): `configureAdapter` drops the previous client before anything
  can fail, so a configure that throws never leaves reads going through the
  old, already-discarded HTTP client; a client that cannot be built fails
  every read ERROR with a reason, like an invalid host or port (review N5).
- docs: commit the generated dev-preview stamps (`0.0.0.2`) in
  `docs/README.md`, `docs/inventory-tree.md` and `REFERENCE.md`, as the
  children do (review N7). These generated stamps show the dev-preview build
  until the tag build rewrites them with the release version.
- fix(adapter): a snapshot whose `data` list is non-empty but has no item
  ids at all (schema drift, for example a renamed id field) now fails the
  read (resources go ERROR) instead of discovering only World and leaving
  every resource without data; an occasional id-less row is still skipped.
  New `ItemIndex` helper (peer review, #9).
- fix(adapter): Test Connection validates `/api/items` through the same
  `parseSnapshot` as the collection cycle, so it fails with the same
  message (no readable `data` list, or a non-empty list with no item ids)
  instead of reporting "Test OK" for a payload every cycle rejects (Codex
  P2 on PR #21, #9).

## 0.1.0.1 (2026-06-10)

- feat(framework): modernize the SDK adapter template to framework v2 + C2 pak
  shape + current CI (build 1). Replaces the stale onDiscover-only HelloWorld
  skeleton with a minimal framework-v2 ExampleAdapter:
  - extends `VcfCfAdapter` with keyed constructors `super(ADAPTER_KIND[, ...])`,
    framework default `onDescribe()` (no override), and the `com.vcfcf.adapter.spi`
    roles (`VcfCfTester` / `VcfCfDiscoverer` / `VcfCfCollector`);
  - **collect-path discovery** (`needsRediscovery()=true` + `rediscover()` →
    shared `enumerateResources(snapshot, sink)` used by both discovery and
    `registerNewResource`), so a fresh instance populates on its first collect on
    VCF Ops 9.0.2, which never calls `onDiscover()` for these collectors;
  - `componentLogger(Class)` for the helper logger, cooperative cancellation,
    loud-failure posture (unreadable payload throws — no 0.0 sentinels), and
    `// REDACT-SECRET` credential markers.
- feat(framework): byte-copy the canonical `build-pak-on-tag.yml` (private SDK-jar
  fetch via `SDK_RUNTIME_TOKEN` + `--sdk-jar`).
- feat(framework): C2 pak shape — no bundled jars; `vcfcf-adapter-base.jar` comes
  from the buildkit and `vrops-adapters-sdk-*.jar` is consumer-supplied. `.gitignore`
  ignores `*.generated.md`, dist/, build artifacts, and `lib/*.jar`.
- docs: README rewritten with instantiation steps, local dev build
  (`build-sdk --sdk-jar` / `VCFCF_SDK_JAR`), and the v*-tag CI release contract.
