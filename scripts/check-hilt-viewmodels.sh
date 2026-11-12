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

# Each @HiltViewModel (short or fully qualified) and the class it annotates: the first class
# declaration after it, allowing modifiers, KDoc and other annotations in between.
mapfile -t view_models < <(
  find app/src/main core/*/src/main feature/*/src/main notifications/src/main widget/src/main ingest/src/main \
    -name '*.kt' -print0 2>/dev/null |
    xargs -0 awk '
      FNR == 1 { if (pending) print "UNMATCHED:" file; pending = 0; file = FILENAME }
      /^[[:space:]]*@(dagger\.hilt\.android\.lifecycle\.)?HiltViewModel([[:space:](]|$)/ { pending++; next }
      pending && match($0, /^[[:space:]]*([a-z]+[[:space:]]+)*class[[:space:]]+[A-Za-z0-9_]+/) {
        decl = substr($0, RSTART, RLENGTH); sub(/.*class[[:space:]]+/, "", decl); print decl; pending--
      }
      END { if (pending) print "UNMATCHED:" file }
    ' | sort -u
)
if printf '%s\n' "${view_models[@]}" | grep -q '^UNMATCHED:'; then
  printf '%s\n' "${view_models[@]}" | sed -n 's/^UNMATCHED:/error: @HiltViewModel without a class in /p' >&2
  exit 1
fi
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
    # Whole word, so SortViewModel can't pass on QuickSortViewModel_HiltModules.
    if ! grep -qw "${vm}_HiltModules" "$component"; then
      echo "error: $variant Hilt component is missing $vm (stale KSP output?)" >&2
      status=1
    fi
  done
  [ "$status" -eq 0 ] && echo "ok: $variant has all ${#view_models[@]} view models"
done
exit "$status"
