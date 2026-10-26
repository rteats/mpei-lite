#!/usr/bin/env bash
# Measure force-stopped Activity launch times. This is a TTID proxy, not TTFD.
set -euo pipefail

PACKAGE="${1:-com.rteats.mpeineo.dev}"
RUNS="${2:-10}"
if ! [[ "$RUNS" =~ ^[0-9]+$ ]] || (( RUNS < 2 )); then
  echo "Usage: $0 [package] [runs >= 2]" >&2
  exit 2
fi
if ! adb shell pm path "$PACKAGE" | grep -q '^package:'; then
  echo "App $PACKAGE is not installed" >&2
  exit 1
fi

COMPONENT="$PACKAGE/com.rteats.mpeineo.MainActivity"
TIMES=()
for ((i=0; i<=RUNS; ++i)); do
  RESULT="$(adb shell am start -S -W -n "$COMPONENT")"
  DURATION="$(printf '%s\n' "$RESULT" | awk '/^TotalTime:/ {print $2}')"
  if ! [[ "$DURATION" =~ ^[0-9]+$ ]]; then
    echo "$RESULT" >&2
    echo "Missing TotalTime" >&2
    exit 1
  fi
  if (( i == 0 )); then
    printf 'Warmup (discarded): %s ms\n' "$DURATION"
  else
    TIMES+=("$DURATION")
    printf 'Run %d: %s ms\n' "$i" "$DURATION"
  fi
done

printf '%s\n' "${TIMES[@]}" | sort -n | awk '
  { t[NR]=$1; sum+=$1 }
  END {
    n=NR
    if (n % 2) median=(t[n/2]+t[n/2+1])/2
    else median=t[(n+1)/2]
    p90=t[int((n*9+9)/10)]
    printf "n=%d min=%d ms median=%.1f ms p90=%d ms max=%d ms\n", n,t[1],median,p90,t[n]
  }'
