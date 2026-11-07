# Test fixtures

## `vsan-perfsvc-json/`: verbatim vendor responses

Byte-for-byte copies of live captures from the VCF Content Factory repo,
`reference/docs/extracted/vsan-perfsvc-json/`. Captured read-only from the
lab management vCenter (VMware vCenter Server 9.1.1.0100 build 25784172,
vSAN ESA cluster `domain-c6004`, 5 hosts), 2026-11-06. Provenance:

- `queryperf-cluster-domclient-2026-11-06.json`,
  `queryperf-fault-wildcardlimit-2026-11-06.json`: that folder's `README.md`.
- the other six files: that folder's `PROVENANCE-2026-11-06-evening.md`
  (requests, release id `8.0.3.0`, the 7-minute UTC window and the Build 1
  `labels` per type are recorded there).
- Digest: the factory's `knowledge/context/api-maps/vsan-performance-service.md`,
  sections "JSON API transport" and "vim25 inventory over JSON".

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

## `design-build1-metrics.md`: the design's metric table

A copy of the "Build 1 metric list" tables of the factory's
`knowledge/designs/vsanperf-mp-v1.md` (2026-11-06), the source of truth for
the 61 stat keys. `EntityTypeTest` checks `EntityType` against it row for
row; change both together, and only before the first `v*` tag (pushed key
names are permanent once data exists).

## Constructed responses

Bodies the fake-server and engine tests build in code (Login replies,
ServiceContent, `NotAuthenticated` and `InvalidRequest` faults, paged
`RetrieveResult`s, `vimServiceVersions.xml`, Suite API resource lists,
lagging-host perf samples) follow the shapes recorded in the api map and in
the files above. They are constructed, not captures, and the tests say so.
