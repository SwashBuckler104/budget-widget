#!/usr/bin/env bash
# Only :ingest may import the upstream parser (com.pennywiseai.*); everything else uses our
# SmsParser / ParsedSms types (SCOPE.md §5). Gradle enforces the dependency edge; this catches
# source-level imports, e.g. through a future test fixture.
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root"

violations="$(grep -rnE '^\s*import\s+com\.pennywiseai\.' --include='*.kt' --include='*.kts' . \
    --exclude-dir=third_party --exclude-dir=build --exclude-dir=.gradle \
    | grep -vE '^\./(ingest|parser-core)/' || true)"

if [[ -n "$violations" ]]; then
    echo "FAIL: com.pennywiseai.* imported outside :ingest:" >&2
    echo "$violations" >&2
    exit 1
fi
echo "ok: com.pennywiseai.* is only imported from :ingest"
