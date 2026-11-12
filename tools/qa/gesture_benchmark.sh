#!/usr/bin/env bash
# Hermex smoothness benchmark — repeatable gesture script + gfxinfo capture.
# Usage: gesture_benchmark.sh <package> [serial]
# Prints the gfxinfo frame block; compare Janky % and p90/p95/p99 across builds.
# Historical runs (different content/state; NOT a direct A/B):
#   earlier official 0.10%; v0.2.21 8.29%; v0.2.22 static 0.00%, live 5.36%.
# Matched completed countdown session, Pixel 8 at 60 Hz (2026-11-08):
#   official: 0.34%, 0.23% jank (p99 13, 14 ms)
#   Hermex with sampled viewport diagnostics: 1.73%, 1.25% (p99 21 ms)
#   Disabling the entire viewport diagnostic collector: 1.50%, 1.48% (p99 20, 19 ms).
# This script drives the same gestures but does NOT verify matched content or foreground;
# check both manually before/after, and reject zero-frame runs.
#
# Deeper evidence (2026-11-08): `dumpsys gfxinfo <pkg> framestats` right after the
# swipes gives per-frame buckets even on this user build (no root, no atrace):
#   HandleInputStart - IntendedVsync      input dispatch lateness (synthetic-injection noise)
#   PerformTraversalsStart - AnimationStart   composition
#   SyncQueued - PerformTraversalsStart   app CPU (measure/layout/draw-record)
#   GpuCompleted - SwapBuffers            GPU
# Same completed chat, 4+4 swipes: official 0 janky/120 in 3 runs, app CPU p50 0.15 ms;
# Hermex 2.5% jank, app CPU p50 1.25 ms. Discount frames with input dispatch >4 ms late.
# The official transcript is recycled platform Views + a Spanned cache, NOT Compose,
# so jank differences here are architectural — see the hermes-clone-parity skill.
set -euo pipefail
PKG="${1:?package}"; SER="${2:-39151FDJH00HX4}"
A="adb -s $SER shell"
$A dumpsys gfxinfo "$PKG" reset >/dev/null
for i in 1 2 3 4 5 6; do $A input swipe 540 1900 540 300 120; sleep 0.35; done
for i in 1 2 3 4 5 6; do $A input swipe 540 300 540 1900 120; sleep 0.35; done
for i in 1 2 3 4; do $A input swipe 540 1800 540 800 600; sleep 0.3; done
for i in 1 2 3 4; do $A input swipe 540 800 540 1800 600; sleep 0.3; done
$A dumpsys gfxinfo "$PKG" | grep -E "Total frames rendered|Janky frames|50th|90th|95th|99th percentile"
