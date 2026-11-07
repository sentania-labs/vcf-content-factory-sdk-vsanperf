#!/usr/bin/env bash
# Design T3 replay harness, standalone (pak issue #6): compiles the tests
# tree (no SDK jar needed) and runs ReplayHarness with the given arguments,
# for example:
#   ci/replay.sh --clusters 70 --hosts 6 --cycles 6 --workers 4 \
#                --latency-ms 190 --ceiling 7 --model vcenter --tiers default
#   ci/replay.sh --model cluster
#   ci/replay.sh --vm --vm-every 3 --heap
# Simulated time: no call sleeps; see ReplayHarness's class doc.
set -euo pipefail
cd "$(dirname "$0")/.."
out="$(mktemp -d)"
trap 'rm -rf "$out"' EXIT
mapfile -t sources < <(find tests -name '*.java' | sort)
javac -d "$out" -sourcepath src:tests "${sources[@]}"
java -cp "$out" com.vcfcf.adapters.vsanperf.ReplayHarness "$@"
