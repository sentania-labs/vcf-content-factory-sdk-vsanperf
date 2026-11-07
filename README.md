# VCF Content Factory vSAN Performance

A Tier 2 (Java SDK) management pack for VCF Operations. It reads the vSAN
Performance Service over the vSphere JSON API and puts the numbers on the
hosts you already have: the vCenter adapter's own VMWARE HostSystem
objects, as keys under `vSAN Perf`. Besides its adapter instance it creates
one object, the "VCF Content Factory vSAN Performance Collector" its cycle
runs for.

Build 1 covers three entity types, 61 keys: DOM owner (38 per host), DOM
client (6 per host) and vSAN physical NICs (17 per NIC, instanced as
`vSAN Perf|pNIC:vmnic1|...`). On a 5-host cluster that is up to 390 series
per cycle. Every key, unit and self-metric: [REFERENCE.md](REFERENCE.md).
Design: the factory's `knowledge/designs/vsanperf-mp-v1.md`.

## How it collects

- One vCenter session per adapter instance, reused across cycles; logged
  in again once if vCenter drops it. Release id `8.0.3.0`, or the server's
  own vim25 version if vCenter refuses that one.
- Inventory: one PropertyCollector read per cycle over a session-lived view
  of clusters and their hosts (paged when vCenter pages it). A cluster
  counts as vSAN when a member host has vSAN enabled and a vSAN cluster
  uuid.
- Three Performance Service calls per vSAN cluster per cycle (one wildcard
  query per entity type, the API's floor), 7-minute window, labels filter,
  gzip. Clusters run through a bounded pool (**Parallel clusters**, default
  4); each cluster's calls run in order, DOM owner first. Each cycle starts
  after the last cluster the previous cycle attempted, so a cluster that
  always runs past the deadline cannot starve the ones after it.
- Values carry the sample's own 5-minute timestamp; a bucket already pushed
  is not pushed again (tracked per entity and metric, and only once the
  push came back clean).
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

## Acceptance on each platform (before the first `v*` tag)

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
cycle for `ComplianceWorld`. Its identity is the account's own (vCenter
Server and Clusters to include), known without a login, so it exists while
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

## Open before the first `v*` tag

- **Design T3, replay harness:** recorded per-type responses replayed per
  synthetic cluster with per-call latency and both call-ceiling models.
  Not built.
- **Design T4, heap budget gate:** 70 synthetic clusters x 6 hosts x 2 NICs
  must stay under the 65 MB per-instance budget (design D6) inside the
  cycle deadline. Not built; nothing yet verifies the scale claim.
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
  CycleEngine.java        the collector: inventory, pool, deadline, dedup, push
  VsanJsonClient.java     vSphere JSON API client (session, inventory, QueryPerf)
  Inventory.java          PropertyCollector result parser, cluster vSAN rule
  PerfParser.java         QueryPerf response parser, newest-sample selection
  EntityType.java         the 61-key catalog (design table)
  OpsHostMap.java         HostSystem MoRef + vCenter UUID to Ops resource id
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
