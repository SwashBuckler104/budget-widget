#!/usr/bin/env bash
# Fails if an APK requests a permission that is not in config/permissions-allowlist.txt,
# with a dedicated message for android.permission.INTERNET (hard constraint, SCOPE.md §2).
# Usage: scripts/check-permissions.sh <apk> [<apk>...]
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
allowlist="$root/config/permissions-allowlist.txt"

if [[ $# -eq 0 ]]; then
    echo "usage: $0 <apk> [<apk>...]" >&2
    exit 2
fi

sdk="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
if [[ -z "$sdk" && -f "$root/local.properties" ]]; then
    sdk="$(sed -n 's/^sdk\.dir=//p' "$root/local.properties")"
fi
build_tools="$(find "$sdk/build-tools" -mindepth 1 -maxdepth 1 -type d 2>/dev/null | sort -V | tail -n 1)"
aapt2="$build_tools/aapt2"
if [[ ! -x "$aapt2" ]]; then
    echo "error: aapt2 not found; set ANDROID_HOME (looked in '$sdk/build-tools')" >&2
    exit 2
fi

status=0
for apk in "$@"; do
    [[ -f "$apk" ]] || { echo "error: no such APK: $apk" >&2; exit 2; }
    package="$("$aapt2" dump packagename "$apk")"
    allowed="$(grep -vE '^[[:space:]]*(#|$)' "$allowlist" | sed "s/\${applicationId}/$package/g")"
    # Covers both <uses-permission> and <uses-permission-sdk-23>.
    requested="$("$aapt2" dump permissions "$apk" | sed -nE "s/^uses-permission(-sdk-23)?: name='([^']+)'.*/\2/p" | sort -u)"

    echo "$apk ($package):"
    if grep -qx 'android.permission.INTERNET' <<<"$requested"; then
        echo "  FAIL android.permission.INTERNET is requested. The app must never have network access." >&2
        echo "       Find the dependency with :app:processReleaseManifest's merge report" >&2
        echo "       (app/build/outputs/logs/manifest-merger-*-report.txt) and remove it with tools:node=\"remove\"." >&2
        status=1
    fi
    while IFS= read -r permission; do
        [[ -z "$permission" ]] && continue
        if grep -qxF "$permission" <<<"$allowed"; then
            echo "  ok   $permission"
        elif [[ "$permission" != "android.permission.INTERNET" ]]; then
            echo "  FAIL $permission is not in config/permissions-allowlist.txt" >&2
            status=1
        fi
    done <<<"$requested"
done
exit "$status"
