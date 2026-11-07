# Test fixtures

## `vsan-perfsvc-json/`: verbatim vendor responses

Byte-for-byte copies of live captures from the VCF Content Factory repo,
`reference/docs/extracted/vsan-perfsvc-json/`. Captured read-only from the
lab management vCenter (VMware vCenter Server 9.1.1.0100 build 25784172,
vSAN ESA cluster `domain-c6004`, 5 hosts), 2026-11-06. Provenance:

- `queryperf-cluster-domclient-2026-11-06.json`,
  `queryperf-fault-wildcardlimit-2026-11-06.json`: that folder's `README.md`.
- the six Build 1 files (`querynodeinformation`, `queryperf-host-domclient`,
  `queryperf-host-domowner`, `queryperf-vsan-pnic-net`, both
  `retrievepropertiesex-*`): that folder's `PROVENANCE-2026-11-06-evening.md`
  (requests, release id `8.0.3.0`, the 7-minute UTC window and the Build 1
  `labels` per type are recorded there).
- the nineteen build 10 files (the cluster-tier and host-tier types, added
  2026-11-06): that folder's `PROVENANCE-2026-11-06-build2.md` (same
  vCenter, cluster and release id; window 2026-11-07 05:01:41Z to 05:08:41Z;
  `labels` = the design's vSAN label column per type). The
  `cluster-domclient` capture of that round is saved as
  `queryperf-cluster-domclient-labels-2026-11-06.json` (the older,
  unfiltered `queryperf-cluster-domclient-2026-11-06.json` is a different
  capture). The five empty types of these tiers have no file; their answer
  is the wildcard echo quoted in that provenance file, which the tests build
  in code.
- the five build 11 perf files of the disk and VM tiers
  (`queryperf-vsan-esa-disk-layer`, `-scsifw`, `queryperf-virtual-machine`,
  `queryperf-virtual-disk`, `queryperf-vscsi`, same round): also
  `PROVENANCE-2026-11-06-build2.md`.
- the fourteen P2 files (VM pages over a cluster-rooted ContainerView, light
  chain pages 1 to 5 and the unpaged light read, device pages 1 and 2, the
  stale-token and device-subpath faults, `querydisksforvsan-host-15151`,
  `queryperf-vsan-esa-disk-layer-p2`, and the devel VCF Operations 9.0.2
  `ClusterComputeResource` and `VirtualMachine` resource lists): that
  folder's `PROVENANCE-2026-11-06-p2.md` (requests, maxObjects, tokens,
  sizes). The Ops bodies are as received (not gzipped).
- Digest: the factory's `knowledge/context/api-maps/vsan-performance-service.md`,
  sections "JSON API transport", "vim25 inventory over JSON" and "Build 2
  entity types (measured 2026-11-06)".

No credential or session id is in any file (scanned at capture). Never edit
them: a test that needs another shape builds it in code and says so.

| File | sha256 (first 16) |
|---|---|
| `querynodeinformation-2026-11-06.json` | `9a829b245e7fbea0` |
| `queryperf-cluster-domclient-2026-11-06.json` | `239df6573c3e8953` |
| `queryperf-fault-wildcardlimit-2026-11-06.json` | `e38820abaf38b8ae` |
| `queryperf-host-domclient-2026-11-06.json` | `fde5588bbba91c19` |
| `queryperf-host-domowner-2026-11-06.json` | `e9c8c4ce9ec10d10` |
| `queryperf-vsan-pnic-net-2026-11-06.json` | `dc7a43d1f51a84da` |
| `retrievepropertiesex-cluster-hosts-2026-11-06.json` | `ae54b5a69f7a461c` |
| `retrievepropertiesex-fault-invalidproperty-2026-11-06.json` | `a9c65f836aad46b8` |
| `queryperf-vsan-cluster-capacity-2026-11-06.json` | `6136968bb398356f` |
| `queryperf-cluster-domclient-labels-2026-11-06.json` | `7f30221252fd16b9` |
| `queryperf-cluster-domcompmgr-2026-11-06.json` | `1ee422a8fa7d4a02` |
| `queryperf-cluster-domowner-2026-11-06.json` | `1fc90d04e1f429aa` |
| `queryperf-cluster-esa-dedup-domclient-io-2026-11-06.json` | `02e6d70171873be7` |
| `queryperf-cluster-rdt-network-latency-2026-11-06.json` | `df374c2a70a202d8` |
| `queryperf-cluster-zdom-top-stats-2026-11-06.json` | `c11070684ca67037` |
| `queryperf-cmmds-net-2026-11-06.json` | `1c35b7332cc5cf09` |
| `queryperf-vsan-cpu-2026-11-06.json` | `537e8243c0e8e446` |
| `queryperf-host-domcompmgr-2026-11-06.json` | `dea77f925e06c06b` |
| `queryperf-host-esa-dedup-domclient-io-2026-11-06.json` | `21733c25ad4d37ff` |
| `queryperf-vsan-host-net-2026-11-06.json` | `4bf41be50aceff6c` |
| `queryperf-host-zdom-top-stats-2026-11-06.json` | `61a78d9db6997e13` |
| `queryperf-vsan-memory-2026-11-06.json` | `2074d8a916af5875` |
| `queryperf-rdt-network-latency-2026-11-06.json` | `7eb226a438ccfee1` |
| `queryperf-vsan-tcpip-stats-2026-11-06.json` | `4bd2208f2b056931` |
| `queryperf-vsan-vnic-net-2026-11-06.json` | `660662a66352bf5c` |
| `queryperf-vnic-rdt-network-latency-2026-11-06.json` | `0590d740e6c28863` |
| `queryperf-zdom-vtx-2026-11-06.json` | `5641265d4202ea65` |
| `queryperf-vsan-esa-disk-layer-2026-11-06.json` | `6ab564dc20f8dde0` |
| `queryperf-vsan-esa-disk-scsifw-2026-11-06.json` | `397b5dc2d0d18768` |
| `queryperf-virtual-machine-2026-11-06.json` | `230a98eaeac775a8` |
| `queryperf-virtual-disk-2026-11-06.json` | `a60d5a64602021db` |
| `queryperf-vscsi-2026-11-06.json` | `e003ec18adfe1041` |
| `queryperf-vsan-esa-disk-layer-p2-2026-11-06.json` | `ac932cb5fbbbc12a` |
| `querydisksforvsan-host-15151-2026-11-06.json` | `abfda93c85e64c57` |
| `retrievepropertiesex-vm-cluster-light-2026-11-06.json` | `deebc6ade3eecdfd` |
| `retrievepropertiesex-vm-cluster-light-max10-page1-2026-11-06.json` | `8bd5771024c5930e` |
| `retrievepropertiesex-vm-cluster-light-max10-page2-2026-11-06.json` | `055ea06b7edb3e07` |
| `retrievepropertiesex-vm-cluster-light-max10-page3-2026-11-06.json` | `a62eda5abff45ebe` |
| `retrievepropertiesex-vm-cluster-light-max10-page4-2026-11-06.json` | `2abe1633b0a6e9d4` |
| `retrievepropertiesex-vm-cluster-light-max10-page5-2026-11-06.json` | `d2ed7a0c6ba4f1c9` |
| `retrievepropertiesex-vm-cluster-devices-max10-page1-2026-11-06.json` | `a3acfb36ded4c33f` |
| `retrievepropertiesex-vm-cluster-devices-max10-page2-2026-11-06.json` | `98b6011108d5cba1` |
| `retrievepropertiesex-fault-stale-token-2026-11-06.json` | `bbf83541e5f65c89` |
| `retrievepropertiesex-fault-device-subpath-2026-11-06.json` | `3e2aeb519e068b65` |
| `ops-devel-resources-clustercomputeresource-2026-11-06.json` | `534dd99b1a0fd26d` |
| `ops-devel-resources-virtualmachine-2026-11-06.json` | `839ba9d4989576a8` |

## `design-build1-metrics.md`: the design's metric table

A copy of the "Build 1 metric list" tables of the factory's
`knowledge/designs/vsanperf-mp-v1.md` (2026-11-06), the source of truth for
the 61 stat keys. `EntityTypeTest` checks `EntityType` against it row for
row; change both together, and only before the first `v*` tag (pushed key
names are permanent once data exists).

## `design-build2-metrics.md`: the v2 design's host and cluster tables

A copy of the "Cluster tier" and "Host tier" tables of the factory's
`knowledge/designs/vsanperf-mp-v2.md` (2026-11-06), the source of truth for
the 338 build 10 stat keys. `EntityTypeTest` checks `EntityType` against it
row for row, the same way as the Build 1 copy.

Since 2026-11-07 the design copy also holds the "Disk tier" and "VM tier"
tables (390 rows in all); the first 338 rows are unchanged.

## Constructed responses

Bodies the fake-server and engine tests build in code (Login replies,
ServiceContent, `NotAuthenticated` and `InvalidRequest` faults, paged
`RetrieveResult`s, `vimServiceVersions.xml`, Suite API resource lists,
lagging-host perf samples) follow the shapes recorded in the api map and in
the files above. They are constructed, not captures, and the tests say so.
Build 11 adds: the hosts' `configManager.vsanSystem` moIds and four of the
five hosts' `QueryDisksForVsan` answers (`DiskVmEngineTest`, from the api
map's P2 uuid-to-host table; only host-15151's body was saved), and a
device array with NVMe, SATA, IDE and unknown controllers (`DiskVmTest`,
**synthesized**: the lab has PVSCSI and LSI Logic disks only).
