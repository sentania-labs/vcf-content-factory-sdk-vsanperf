# Installing & Configuring: VCF Content Factory vSAN Performance

## Prerequisites

- VCF Operations with the vCenter adapter collecting the vCenter (the
  HostSystem, ClusterComputeResource and VirtualMachine objects the keys
  land on).
- vCenter with the vSphere JSON API (8.0 Update 1 or later).
- The Python vsanhostmetrics pack may stay installed; this pack is a
  different adapter kind. See the README's migration section.

## Permissions Required

A read-only vCenter account, propagated from the root. Pushing onto the
vCenter adapter's objects uses the platform's own per-instance Suite API
credential.

## Network Requirements

The collector (or Cloud Proxy) running the instance reaches vCenter on
HTTPS (443, or the port you enter) and its own local Suite API.

## TLS: certificate trust

The vCenter certificate is checked against the VCF Operations trust store
and must name the host as you enter it. An untrusted certificate brings up
the accept dialog on Validate Connection; accepting does not fix a name
mismatch, so enter vCenter by a name on its certificate.

## Configuration Fields

| Field | Default | Notes |
|---|---|---|
| vCenter Server | (required) | host or host:port; part of the account identity |
| Clusters to include | all vSAN clusters | names or MoRefs, comma separated; part of the account identity |
| Clusters to exclude | none | Advanced |
| Parallel clusters | 4 | Advanced, 1 to 16 |
| Call timeout (s) | 30 | Advanced |
| Cycle deadline (s) | 0 (interval minus 60 s) | Advanced |
| Performance timing logging | false | Advanced; `PERF` lines at INFO |
| Host tier (extra host groups) | true | Advanced; 13 more host types |
| Cluster tier | true | Advanced; 10 types onto ClusterComputeResource |
| Disk tier | false | Advanced; 4 types per vSAN disk onto HostSystem; adds one `QueryDisksForVsan` per host per refresh |
| VM tier | false | Advanced; 3 types onto VirtualMachine; adds a VM read per cluster and the Ops VM list |
| VM tier every N cycles | 3 | Advanced, 1 to 12; each cluster runs its VM types every Nth cycle, staggered |
| Entity types to exclude | (empty) | Advanced; comma list of type ids, for example `ioinsight,vsan-file-service`; an unknown id is a WARN; a list that leaves nothing is a configuration error |

An account upgraded from 1.0.0.9 gets the host and cluster tiers on and the
disk and VM tiers off. Set both host and cluster to `false` for Build 1
behaviour. Enable the VM tier on a large vCenter only after a day of the
default tiers with timing logging on, and on a cluster subset first.

## Step-by-Step Installation

1. Install the `.pak` (Administration > Integrations > Repository).
2. Add an account: vCenter Server, credential, optionally Clusters to
   include. Pin it to its own collector group if you can.
3. Validate Connection: it logs in, reads the cluster inventory and checks
   the local Suite API (logged only).
4. After a cycle or two, check the instance's `Collection|*` metrics and a
   host's `vSAN Perf` keys. Cluster keys can take about 6 minutes after a
   push to read back, VM keys about a minute.

A new instance shows its "VCF Content Factory vSAN Performance Collector"
object from its second cycle; the collection cycle runs for that object,
so a failure (an unreachable vCenter: DOWN) shows on the account row from
then on. Until the platform acceptance in the repo README has passed on
your VCF Operations version, treat that DOWN mapping as unverified.

## Troubleshooting

- `Collection|last_error` holds the cycle's first problem.
- `Collection|hosts_unresolved` above 0: the vCenter adapter is not
  collecting some hosts, or a host's vSAN node uuid is not in the inventory.
  A host the vCenter adapter starts collecting later is picked up within
  about an hour (the HostSystem list is re-read every 12th cycle).
- `Collection|clusters_unresolved`, `disks_unresolved`, `vms_unresolved` or
  `vdisks_unresolved` above 0: the object is not in VCF Operations, or the
  disk map or VM read does not (yet) name it; see REFERENCE.md "Where keys
  land" for the re-read cadence.
- `Collection|optional_calls_failed` above 0: an optional type faulted;
  `last_error` names it. The rest of the cluster's data is still pushed.
- Turn on Performance timing logging and grep the instance log for `PERF`.
