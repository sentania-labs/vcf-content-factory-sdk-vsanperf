# VCF Content Factory vSAN Performance

A Tier 2 (Java SDK) management pack for VCF Operations. It reads the vSAN
Performance Service over the vSphere JSON API and puts the numbers on the
hosts, clusters and VMs you already have: the vCenter adapter's own VMWARE
HostSystem, ClusterComputeResource and VirtualMachine objects, as keys under
`vSAN Perf`. Besides its adapter instance it creates
one object, the "VCF Content Factory vSAN Performance Collector" its cycle
runs for.

Version 1.1.0 (build 11) covers 33 entity types, 451 keys, in five tiers:
the always-on core (Build 1: DOM owner, DOM client, vSAN physical NICs, 61
keys), the host tier (13 types, 149 keys on HostSystem) and the cluster tier
(10 types, 189 keys on ClusterComputeResource), both on by default, and the
disk tier (4 types, 31 keys on HostSystem per vSAN disk) and the VM tier (3
types, 21 keys on VirtualMachine), both off by default. On a 5-host ESA
cluster the default tiers are 1,186 series per cycle (390 core, 705 host
tier, 91 cluster tier); the disk tier adds 50, the VM tier up to 2,400 per VM
run (30 VMs). Every key, unit and self-metric: [REFERENCE.md](REFERENCE.md).
Design: the factory's `knowledge/designs/vsanperf-mp-v1.md` and
`vsanperf-mp-v2.md` with its P2 addendum.

## Keys

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

**Tier settings** (Advanced): `tier_host` and `tier_cluster` (default
`true`), `tier_disk` and `tier_vm` (default `false`), `vm_tier_every_n_cycles`
(default 3), and `exclude_types` (comma list of entity type ids). An account
upgraded from 1.0.0.9 gets the host and cluster tiers on and the disk and VM
tiers off; set host and cluster to `false` for Build 1 behaviour.

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
  window, labels filter, gzip: 3 with the tiers off, 26 with the defaults
  on the first cycles and 23 on the lab once its empty types are learned;
  with all tiers 33 then 28 on the cluster's VM cycle (every 3rd), 30 then
  25 on the others. Responses are parsed from the gzip stream one entity at
  a time; VM-type calls run one at a time across the pool.
  Clusters run through a bounded pool (**Parallel clusters**, default 4);
  each cluster's calls run in order: core (DOM owner first), then the
  cluster, host, disk and VM tiers. Each cycle starts after the last cluster
  the previous cycle attempted, so a cluster that always runs past the
  deadline cannot starve the ones after it.
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
- The platform acceptance above (design T5).

## Migrating from the Python vsanhostmetrics pack

This pack is a different adapter kind (`vcfcf_vsanperf`, the Python pack is
`VsanHostMetrics`), so it is **not an upgrade**. Both can run side by side
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
use the factory's builder. The Broadcom SDK jar (`vrops-adapters-sdk-2.2.jar`) is
**not** shipped — supply it from your appliance:

```sh
# Obtain the SDK jar once from a VCF Ops appliance:
#   scp root@<appliance>:/usr/lib/vmware-vcops/common-lib/vrops-adapters-sdk-2.2.jar .

# Either pass it explicitly:
python3 -m vcfops_managementpacks build-sdk content/sdk-adapters/<name> \
  --sdk-jar /path/to/vrops-adapters-sdk-2.2.jar -o dist

# ...or set it once in the environment:
export VCFCF_SDK_JAR=/path/to/vrops-adapters-sdk-2.2.jar
python3 -m vcfops_managementpacks build-sdk content/sdk-adapters/<name> -o dist
```

Validate first (the cheap loop — exhaust this before building a pak):

```sh
python3 -m vcfops_managementpacks validate-sdk content/sdk-adapters/<name>
```

The framework jar (`vcfcf-adapter-base.jar`) is provided by the builder; you do
**not** commit it.

## Building from source

You don't need this repo's CI or the VCF Content Factory checkout to
build the `.pak` — the toolchain is a portable tarball. You need:

- **JDK 11+** (`javac` + `jar` on PATH)
- **python3** with `pyyaml` (`python3 -m pip install pyyaml`)
- **The GitHub CLI** (`gh`) — used to download the build toolchain
  below. The factory repo is public, so no `gh auth login` is needed
  for the download (authenticate only if you hit anonymous API rate
  limits). No `gh`? See the `curl` alternative under step 1.
- **The Broadcom adapter SDK jar** (`vrops-adapters-sdk-2.2.jar`).
  This is a Broadcom build artifact with no public redistribution
  channel — it is **never** bundled in the toolchain or this repo.
  Get it from your own VCF Operations appliance:

  ```
  scp root@<appliance>:/usr/lib/vmware-vcops/common-lib/vrops-adapters-sdk-2.2.jar .
  ```

  (Also present at
  `/usr/lib/vmware-vcops/suite-api/WEB-INF/lib/vrops-adapters-sdk.jar`.
  Partners can pull it from the Broadcom TAP / partner SDK portal
  instead.)

Then, from the root of this repo:

```bash
# 1. Fetch the build toolchain. Pin a full sdk-buildkit-vX.Y.Z tag (the
#    floating sdk-buildkit-v1 tag moves under you) and verify the published
#    checksum before unpacking.
BUILDKIT_VERSION=1.0.11
gh release download "sdk-buildkit-v$BUILDKIT_VERSION" \
  --repo sentania-labs/vcf-content-factory \
  --pattern "sdk-buildkit-$BUILDKIT_VERSION.tgz*"
# No gh? The assets are public; fetch both with curl instead:
#   base=https://github.com/sentania-labs/vcf-content-factory/releases/download/sdk-buildkit-v$BUILDKIT_VERSION
#   curl -fsSLO "$base/sdk-buildkit-$BUILDKIT_VERSION.tgz"
#   curl -fsSLO "$base/sdk-buildkit-$BUILDKIT_VERSION.tgz.sha256"
sha256sum -c "sdk-buildkit-$BUILDKIT_VERSION.tgz.sha256"   # macOS: shasum -a 256 -c
tar xzf "sdk-buildkit-$BUILDKIT_VERSION.tgz"

# 2. Point the kit at your SDK jar and build
export VCFCF_SDK_JAR=/path/to/vrops-adapters-sdk-2.2.jar
python3 -m sdk_buildkit validate-sdk .   # cheap loop: compile-check
python3 -m sdk_buildkit build-sdk .      # emits the .pak
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

## Bundled content (optional)

For an adapter that ships dashboards/views, add a `bundled_content:` block to
`adapter.yaml` and co-locate the YAML **in this repo** under `views/` and
`dashboards/` (paths resolve relative to `adapter.yaml`).

## License

MIT, see `LICENSE` in this repository (the same license as the VCF Content Factory).
