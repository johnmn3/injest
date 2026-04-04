#!/usr/bin/env bash
# Test script that simulates a downstream user setting up a project with injest.
# Verifies:
# 1. deps resolve from Clojars
# 2. All injest macros (+> +>> x>> =>>) work correctly
# 3. clj-kondo configs auto-import WITHOUT manual :config-paths setup

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
cd "$SCRIPT_DIR"

echo "=== Step 1: Download dependencies from Clojars ==="
clojure -P
echo "PASS: Dependencies resolved"

echo ""
echo "=== Step 2: Run tests ==="
clojure -M:test
echo "PASS: All tests passed"

echo ""
echo "=== Step 3: Test clj-kondo auto-config (no manual :config-paths) ==="

if ! command -v clj-kondo &> /dev/null; then
    echo "SKIP: clj-kondo not installed, skipping lint check"
    exit 0
fi

# Start fresh - no .clj-kondo dir
rm -rf .clj-kondo
mkdir -p .clj-kondo

# Copy configs from classpath (simulates what IDEs do automatically)
CLASSPATH="$(clojure -Spath 2>/dev/null)"
clj-kondo --lint "$CLASSPATH" --dependencies --copy-configs --skip-lint

# Verify configs were auto-imported
if [ -d ".clj-kondo/imports/net.clojars.john/injest" ]; then
    echo "PASS: injest clj-kondo configs auto-imported"
else
    echo "FAIL: injest clj-kondo configs were NOT auto-imported"
    exit 1
fi

# Lint the source - should have 0 errors and 0 warnings
LINT_OUTPUT="$(clj-kondo --lint src test 2>&1)"
echo "$LINT_OUTPUT"

if echo "$LINT_OUTPUT" | grep -q "errors: 0, warnings: 0"; then
    echo "PASS: clj-kondo linting clean - no errors or warnings"
else
    echo "FAIL: clj-kondo reported errors or warnings"
    exit 1
fi

# Verify NO manual config.edn with :config-paths exists
if [ -f ".clj-kondo/config.edn" ]; then
    echo "FAIL: Manual config.edn exists - configs should load automatically"
    exit 1
else
    echo "PASS: No manual config.edn needed - auto-loading works"
fi

# Clean up generated files
rm -rf .clj-kondo

echo ""
echo "=== ALL TESTS PASSED ==="
echo "injest works correctly as a downstream dependency from Clojars."
echo "clj-kondo configs auto-import without manual :config-paths setup."
