# Overview: VCF Content Factory vSAN Performance

## What's in the Pack

vSAN Performance Service metrics for DOM owner, DOM client and vSAN
physical NICs. The adapter reads them from vCenter over the vSphere JSON API
(HTTPS 443, one session per adapter instance) and pushes them through the
VCF Operations Suite API onto the vCenter adapter's VMWARE HostSystem
objects, under `vSAN Perf|...`. Key list: [REFERENCE.md](../REFERENCE.md).

### Resource kinds

| Kind | Notes |
|---|---|
| vSAN Performance Adapter Instance (`vcfcf_vsanperf`) | the only object the pack creates; carries the `Collection|*` self-metrics |

### Metrics scope

Build 1: 61 key definitions, 38 DOM owner and 6 DOM client keys per host,
17 keys per vSAN physical NIC. Up to 390 series per cycle on a 5-host
cluster with two vSAN NICs per host.

## Cross-Adapter Behavior

Keys land on VMWARE HostSystem, owned by the vCenter adapter. A host is
matched on its MoRef plus the vCenter instance UUID (`VMEntityObjectID` and
`VMEntityVCID`), never by name, so the same MoRef in two vCenters never
mixes. A host the vCenter adapter does not collect is counted in
`Collection|hosts_unresolved` and its data is dropped. Pushed keys show no
label or unit: the unit is in the key name.

## Notable Behaviors

- Values carry the Performance Service's own 5-minute sample time; the
  same bucket is never pushed twice.
- Clusters run in parallel (default 4); a slow or failed cluster does not
  hold up the others, and a cycle deadline pushes whatever finished.
- An entity type a cluster does not offer is skipped there for 24 hours.
- `Collection|perf_calls_per_second` is the aggregate call rate the vCenter
  sustained this cycle; `Collection|perf_call_ms_avg` is per-call latency.

## Known Limitations

- Build 1 covers hosts only: no cluster, disk or VM tier yet (Build 2).
- Units are borrowed from the Python pack's labels, not from the API.
- Whether the Metrics tab shows `pNIC:vmnic1` as an instance node is to be
  verified on each platform before the first release.
- The Performance Service's sustained call ceiling (per vCenter or per
  cluster) is not yet measured; the `PERF` log lines are how to read it.
- Not yet verified on a platform: self-metrics on the instance, and DOWN
  (not ERROR) on the account row for an unreachable vCenter (the cycle runs
  in the `rediscover` hook; README "Acceptance on each platform").
- Design T3 (replay harness) and T4 (65 MB heap gate at 70 clusters) are
  not built; the scale claim is unverified until they are.
