#!/usr/bin/env bash
# Build the shared Rust core into core/ (not in the repository). The core is
# its own repository, expected beside this one.
set -euo pipefail
cd "$(dirname "$0")/.."
CORE="${MAGNETITE_MOBILE_CORE:-../magnetite-mobile-core}"
[ -d "$CORE" ] || { echo "The core is not at $CORE (set MAGNETITE_MOBILE_CORE)." >&2; exit 1; }
"$CORE/scripts/android.sh" "$PWD/core"
