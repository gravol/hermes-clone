#!/usr/bin/env bash
# Hermex smoothness benchmark — repeatable gesture script + gfxinfo capture.
# Usage: gesture_benchmark.sh <package> [serial]
# Prints the gfxinfo frame block; compare Janky % and p90/p95/p99 across builds.
# A/B reference (2026-11-09, Pixel 8 test phone, same session content):
#   official ai.nousresearch.hermes.mobile : jank 0.10%  p90 13 p95 15 p99 17
#   v0.2.21 (bubbleBounds state bug)       : jank 8.29%  p90 23 p95 48 p99 105
#   v0.2.22 static session                 : jank 0.00%  p90 13 p95 14 p99 15
#   v0.2.22 live-turn session              : jank 5.36%  p90 19 p95 27 p99 69
set -euo pipefail
PKG="${1:?package}"; SER="${2:-39151FDJH00HX4}"
A="adb -s $SER shell"
$A dumpsys gfxinfo "$PKG" reset >/dev/null
for i in 1 2 3 4 5 6; do $A input swipe 540 1900 540 300 120; sleep 0.35; done
for i in 1 2 3 4 5 6; do $A input swipe 540 300 540 1900 120; sleep 0.35; done
for i in 1 2 3 4; do $A input swipe 540 1800 540 800 600; sleep 0.3; done
for i in 1 2 3 4; do $A input swipe 540 800 540 1800 600; sleep 0.3; done
$A dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th percentile"
