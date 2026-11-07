# Changelog

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
    are **open and gate the first `v*` tag** (README "Open before the first
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
