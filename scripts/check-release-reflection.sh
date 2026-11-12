#!/usr/bin/env bash
# Fails if R8 removed a constructor that a library calls by reflection in the release APK.
# Usage: scripts/check-release-reflection.sh <apk>
#
# Why: Glance instantiates ActionCallback classes from their names; R8 dropped their no-arg
# constructors, and every Quick-Sort widget button silently failed in release builds while debug
# builds worked (docs/DECISIONS.md ADR-0031).
set -euo pipefail
cd "$(dirname "$0")/.."

apk=${1:?usage: $0 <apk>}
sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-$HOME/Android/Sdk}}
dexdump=$(find "$sdk/build-tools" -maxdepth 2 -name dexdump -type f | sort -V | tail -n 1)
[ -x "$dexdump" ] || { echo "error: dexdump not found under $sdk/build-tools" >&2; exit 2; }

# Classes Glance creates by reflection: every ActionCallback in our sources.
mapfile -t classes < <(
  grep -rlE ':[[:space:]]*ActionCallback([[:space:]]|\{|$)' --include='*.kt' widget/src/main app/src/main 2>/dev/null |
    while read -r file; do
      pkg=$(sed -nE 's/^package[[:space:]]+([A-Za-z0-9_.]+).*/\1/p' "$file" | head -n 1)
      sed -nE 's/^[[:space:]]*([a-z]+[[:space:]]+)*class[[:space:]]+([A-Za-z0-9_]+)[[:space:]]*:[[:space:]]*ActionCallback.*/\2/p' "$file" |
        while read -r name; do echo "L${pkg//.//}/$name;"; done
    done | sort -u
)
[ "${#classes[@]}" -gt 0 ] || { echo "error: found no ActionCallback classes" >&2; exit 1; }

work=$(mktemp -d)
trap 'rm -rf "$work"' EXIT
unzip -q -o "$apk" 'classes*.dex' -d "$work"
for dex in "$work"/classes*.dex; do "$dexdump" "$dex"; done |
  awk '/Class descriptor/ { cls = $4; gsub(/\x27/, "", cls) }
       /name[[:space:]]*:[[:space:]]*\x27<init>\x27/ { getline t; if (t ~ /\x27\(\)V\x27/) print cls }' |
  sort -u > "$work/inits"

status=0
for cls in "${classes[@]}"; do
  if grep -qxF "$cls" "$work/inits"; then
    echo "ok   $cls has a no-arg constructor"
  else
    echo "error: $cls lost its no-arg constructor in $apk (missing keep rule?)" >&2
    status=1
  fi
done
exit "$status"
