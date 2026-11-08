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
- the 69 P3 files `all-<type>-2026-11-07.json` (build 19, design v3 P3):
  that folder's `all-<type>.json`, renamed with the capture date, byte for
  byte; provenance `PROVENANCE-2026-11-07-all.md` (vCenter 9.1.1.0100 build
  25784172, cluster `domain-c6004`, window 2026-11-07 13:47:50Z to 13:54:50Z,
  **no `labels` key**, release id `8.0.3.0`). Faults (11, HTTP 500 bodies) and
  empty echoes (17) are saved like any other body. Factory commit `478d67b`.
- Digest: the factory's `knowledge/context/api-maps/vsan-performance-service.md`,
  sections "JSON API transport", "vim25 inventory over JSON" and "Build 2
  entity types (measured 2026-11-06)".

- 1.2.1 (build 23), the Performance Service state (design v3 "1.2.1"):
  `vsanclustergetconfig-enabled.json` and `vsanclustergetconfig-absent.json`
  are byte-for-byte copies of the factory's
  `reference/docs/extracted/vsan-osa-probe/01-esa-vsanclustergetconfig.json`
  (ESA `domain-c6004`, `perfsvcConfig: {"enabled": true}`) and
  `01-osa-vsanclustergetconfig.json` (OSA probe `domain-c39146`, no
  `perfsvcConfig`); provenance that folder's `PROVENANCE-2026-11-07.md`
  (vCenter 9.1.1.0100 build 25784172, release id `8.0.3.0`, 2026-11-07).
  **`vsanclustergetconfig-false.json` is SYNTHETIC**: the OSA capture with
  `"perfsvcConfig": {"_typeName": "VsanPerfsvcConfig", "enabled": false}`
  inserted in the server's formatting; whether a cluster that had the
  service on and then off reports this or omits the key is unverified.
- 1.2.1 (build 23), the server faults of design D8:
  `queryperf-fault-systemerror-pfccountraw.json` (core `vsan-pnic-net`) and
  `queryperf-fault-systemerror-kareset.json` (the three RDT types) are
  **constructed**. The fault class `SystemError` and the faultstring are
  verbatim from the devel 9.0.2 adapter log of build 22 (2026-11-08,
  cluster `domain-c39146`, lines "vSphere fault SystemError: A general
  system error occurred: RuntimeError('no such column: pfcCountRaw. DB error
  code: 1, please check log file for details') (HTTP 500)" and the same
  with `kaReset`): the log carries the fault text, not the raw body. They
  are wrapped in the HTTP 500 JSON envelope of the factory's
  `reference/docs/extracted/vsan-osa-probe/05-osa-queryperf-disk-group.json`
  (`_typeName`, `faultstring`, server indentation), without a
  `faultMessage` array, since the logged message shows no fault key. A raw
  capture may replace them (RULE-016).
- Build 24 (review W1): `queryperf-fault-masternotfound-osa-2026-11-07.json`
  is a byte-for-byte copy of the factory's
  `reference/docs/extracted/vsan-osa-probe/05-osa-queryperf-disk-group.json`
  (OSA probe `domain-c39146`, Performance Service off, HTTP 500 `NotFound`
  with `faultMessage[0].key`
  `com.vmware.vsan.perfsvc.fault.masternotfound`); provenance that folder's
  `PROVENANCE-2026-11-07.md`.

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
| `all-cache-disk-2026-11-07.json` | `fc6c2cfeb24290c8` |
| `all-capacity-disk-2026-11-07.json` | `6d70f1f5fd2b9ac5` |
| `all-clom-disk-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-clom-host-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-cluster-domclient-2026-11-07.json` | `e11f411c4ccc2896` |
| `all-cluster-domcompmgr-2026-11-07.json` | `037b355d2df42b97` |
| `all-cluster-domowner-2026-11-07.json` | `b0876d75603ce827` |
| `all-cluster-esa-dedup-domclient-io-2026-11-07.json` | `2a19ee033e1d7a13` |
| `all-cluster-esa-dedup-domcompmgr-io-2026-11-07.json` | `2fbadcc4d41d3110` |
| `all-cluster-esa-dedup-domowner-io-2026-11-07.json` | `392d36fc9f31d3fc` |
| `all-cluster-esa-dedup-store-chunksvc-2026-11-07.json` | `f3ffe3f6746cc115` |
| `all-cluster-esa-dedup-store-hashsvc-2026-11-07.json` | `d1ac4dd538d0b7c8` |
| `all-cluster-pmem-2026-11-07.json` | `3a4abb4356419f0e` |
| `all-cluster-rdt-network-latency-2026-11-07.json` | `5e3f8be7c64f0710` |
| `all-cluster-remotedomclient-2026-11-07.json` | `9e666414d1980ab9` |
| `all-cluster-resync-2026-11-07.json` | `3502ea257eb6fea4` |
| `all-cluster-zdom-top-stats-2026-11-07.json` | `0e1498641051d141` |
| `all-cmmds-net-2026-11-07.json` | `6f04f9ca8d720eb4` |
| `all-cmmds-workload-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-computeCluster-remotedomclient-2026-11-07.json` | `6cf2a8bfaba32afd` |
| `all-computeHost-remotedomclient-2026-11-07.json` | `69e830551eae7160` |
| `all-ddh-disk-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-disk-group-2026-11-07.json` | `e2eca6ef279ce76c` |
| `all-dom-world-cpu-2026-11-07.json` | `5dd13bfb454dc30d` |
| `all-heap-memory-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-host-cpu-2026-11-07.json` | `11ea82f16cd9f557` |
| `all-host-domclient-2026-11-07.json` | `61ade3b8ec51d13c` |
| `all-host-domcompmgr-2026-11-07.json` | `b7c98e6fe354946a` |
| `all-host-domowner-2026-11-07.json` | `5010926aef39c972` |
| `all-host-esa-dedup-domclient-io-2026-11-07.json` | `e3ddb26242644fc3` |
| `all-host-esa-dedup-domcompmgr-io-2026-11-07.json` | `4595d7b39e9106a7` |
| `all-host-esa-dedup-domowner-io-2026-11-07.json` | `6efa272fcbf0ae17` |
| `all-host-esa-dedup-store-chunksvc-2026-11-07.json` | `b6129865bf7d540d` |
| `all-host-esa-dedup-store-hashsvc-2026-11-07.json` | `1ef95a4a674f5d12` |
| `all-host-pmem-2026-11-07.json` | `3a4abb4356419f0e` |
| `all-host-remotedomclient-2026-11-07.json` | `55e96e2a250596ed` |
| `all-host-vsansparse-2026-11-07.json` | `3e7c59053733d366` |
| `all-host-zdom-top-stats-2026-11-07.json` | `45c1bd06622dc318` |
| `all-ioinsight-2026-11-07.json` | `925db70ac522a860` |
| `all-lsom-world-cpu-2026-11-07.json` | `9bde3c0e9604586f` |
| `all-rdt-net-2026-11-07.json` | `c4ce98d4adef59e7` |
| `all-rdt-network-latency-2026-11-07.json` | `5b002fa375afd962` |
| `all-remote-virtual-disk-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-remote-virtual-machine-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-remote-vscsi-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-slab-memory-2026-11-07.json` | `92b031b5e93fbe96` |
| `all-system-mem-2026-11-07.json` | `24bf01126ff4f68a` |
| `all-virtual-disk-2026-11-07.json` | `f7dc316c1493f138` |
| `all-virtual-machine-2026-11-07.json` | `1839bddf1c1a9768` |
| `all-vnic-rdt-network-latency-2026-11-07.json` | `ea086f9ae6127bfe` |
| `all-vsan-cluster-capacity-2026-11-07.json` | `f3bff93c40fd3240` |
| `all-vsan-cpu-2026-11-07.json` | `c432586d98f02627` |
| `all-vsan-direct-cluster-2026-11-07.json` | `3d77b410e7b5ca7c` |
| `all-vsan-direct-host-2026-11-07.json` | `f35907a1b03fa685` |
| `all-vsan-distribution-2026-11-07.json` | `f31d5d86f4351d4c` |
| `all-vsan-dp-historical-stats-2026-11-07.json` | `49e75c1e4ea6e5fa` |
| `all-vsan-esa-disk-layer-2026-11-07.json` | `fe3105844b904a1d` |
| `all-vsan-esa-disk-scsifw-2026-11-07.json` | `f2b3d72dbc192a4a` |
| `all-vsan-file-service-2026-11-07.json` | `4a79869e4d88275b` |
| `all-vsan-host-net-2026-11-07.json` | `5525c39312419a69` |
| `all-vsan-iscsi-host-2026-11-07.json` | `cf4078997f7e3582` |
| `all-vsan-iscsi-lun-2026-11-07.json` | `2080a1b5f014c58d` |
| `all-vsan-iscsi-target-2026-11-07.json` | `624e4c7ce3bd4aca` |
| `all-vsan-memory-2026-11-07.json` | `15c379a1dad03377` |
| `all-vsan-pnic-net-2026-11-07.json` | `ae1c726ebd15aa02` |
| `all-vsan-tcpip-stats-2026-11-07.json` | `ac21e78e138d6658` |
| `all-vsan-vnic-net-2026-11-07.json` | `a8b87a76cbf7b64d` |
| `all-vscsi-2026-11-07.json` | `1d46557f3be662be` |
| `all-zdom-vtx-2026-11-07.json` | `db6e8abcd0ed6f60` |
| `vsanclustergetconfig-enabled.json` | `f1799f5d37eff460` |
| `vsanclustergetconfig-absent.json` | `91f104e5b68603d7` |
| `vsanclustergetconfig-false.json` (synthetic) | `fe5874c2562dee80` |
| `queryperf-fault-systemerror-pfccountraw.json` (constructed) | `7286d4b5ffa0ad8c` |
| `queryperf-fault-systemerror-kareset.json` (constructed) | `a85046b84785ff25` |
| `queryperf-fault-masternotfound-osa-2026-11-07.json` | `4137d1f3c6aa5ed4` |

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

## `design-build3-metrics.md`: the v3 catalog delta (build 19)

A copy of "Appendix A: catalog delta (1,144 keys)" of the factory's
`knowledge/designs/vsanperf-mp-v3.md` and of the "Labels beyond the Python
model (210, ESA dedup types only)" list in the P3 section of
`knowledge/context/api-maps/vsan-performance-service.md` (both factory commit
`478d67b`, 2026-11-07; the design's decisions addendum puts the 210 in
scope, Q3). `EntityTypeTest` checks every `metric_scope=all` row of
`EntityType` against it: nothing more, nothing less.

## `design-build3-units.tsv`: the Python pack units the v3 leaf rule reads

For each of the 762 distinct labels of Appendix A: the Python pack's unit
(`app/metric_labels.py` `UNITS`, `none` when it has no entry) and whether it
is in `app/perfsvc.py` `PERMILLE`, from the fork
`content/sdk-adapters/vsanhostmetrics` at tag `v2.0.0-rc.2`, generated
2026-11-07. `EntityTypeTest` applies the design's rules 1 to 7 to it and
must reproduce every Appendix A leaf.

