#!/usr/bin/env bash
# Fails if a built variant's Hilt component is missing any @HiltViewModel from the sources.
# Usage: scripts/check-hilt-viewmodels.sh <variant>...   (e.g. debug release)
#
# Why: incremental KSP once dropped :feature:transactions' generated Hilt modules from the release
# build while Gradle still called the task up to date. The APK built fine and crashed on its
# first screen ("NoSuchMethodException: TransactionsViewModel.<init>"). docs/DECISIONS.md ADR-0029.
set -euo pipefail
cd "$(dirname "$0")/.."

[ "$#" -gt 0 ] || { echo "usage: $0 <variant>..." >&2; exit 2; }

mapfile -t view_models < <(
  grep -rlE '^@HiltViewModel' --include='*.kt' app/src/main core/*/src/main feature/*/src/main notifications/src/main widget/src/main ingest/src/main 2>/dev/null |
    xargs -r grep -hA2 -E '^@HiltViewModel' |
    sed -nE 's/^class ([A-Za-z0-9_]+).*/\1/p' | sort -u
)
[ "${#view_models[@]}" -gt 0 ] || { echo "error: found no @HiltViewModel classes" >&2; exit 1; }

status=0
for variant in "$@"; do
  component=$(find "app/build/generated/hilt/component_sources/$variant" -name '*_HiltComponents_SingletonC.java' 2>/dev/null | head -1)
  if [ -z "$component" ]; then
    echo "error: no Hilt component for variant '$variant' (build it first)" >&2
    status=1
    continue
  fi
  for vm in "${view_models[@]}"; do
    if ! grep -q "${vm}_HiltModules" "$component"; then
      echo "error: $variant Hilt component is missing $vm (stale KSP output?)" >&2
      status=1
    fi
  done
  [ "$status" -eq 0 ] && echo "ok: $variant has all ${#view_models[@]} view models"
done
exit "$status"
