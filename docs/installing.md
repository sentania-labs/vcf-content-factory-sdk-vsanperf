# Installing & Configuring: VCF Content Factory vSAN Performance

## Prerequisites

- VCF Operations with the vCenter adapter collecting the vCenter (the
  HostSystem objects the keys land on).
- vCenter with the vSphere JSON API (8.0 Update 1 or later).
- The Python vsanhostmetrics pack may stay installed; this pack is a
  different adapter kind. See the README's migration section.

## Permissions Required

A read-only vCenter account, propagated from the root. Pushing onto
HostSystem uses the platform's own per-instance Suite API credential.

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

## Step-by-Step Installation

1. Install the `.pak` (Administration > Integrations > Repository).
2. Add an account: vCenter Server, credential, optionally Clusters to
   include. Pin it to its own collector group if you can.
3. Validate Connection: it logs in, reads the cluster inventory and checks
   the local Suite API (logged only).
4. After a cycle or two, check the instance's `Collection|*` metrics and a
   host's `vSAN Perf` keys.

Until the platform acceptance in the repo README ("Acceptance on each
platform") has passed on your VCF Operations version, treat two things as
unverified: that the `Collection|*` self-metrics appear on the instance,
and that an unreachable vCenter shows the account DOWN rather than ERROR
"Resource discovery failed and there was nothing to collect".

## Troubleshooting

- `Collection|last_error` holds the cycle's first problem.
- `Collection|hosts_unresolved` above 0: the vCenter adapter is not
  collecting some hosts, or a host's vSAN node uuid is not in the inventory.
  A host the vCenter adapter starts collecting later is picked up within
  about an hour (the HostSystem list is re-read every 12th cycle).
- Turn on Performance timing logging and grep the instance log for `PERF`.
