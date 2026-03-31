#!/usr/bin/env bash
set -euo pipefail

# Only run in ccweb (remote) environments
if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/../.." && pwd)"

# Run the main setup script
"$PROJECT_DIR/scripts/ccweb-setup.sh"

# Export PATH and convenience functions into CLAUDE_ENV_FILE
# so every subsequent Bash call has them
if [ -n "${CLAUDE_ENV_FILE:-}" ]; then
  cat >> "$CLAUDE_ENV_FILE" <<'ENVEOF'
export NODE_USE_ENV_PROXY=1
export PATH="$HOME/.local/bin:$PATH"

# shadow-compile: bypass Pomegranate by computing classpath via clojure -Spath
shadow-compile() {
  local build="$1"
  local alias="${2:-:app}"
  local cp
  cp="$(clojure -M"$alias" -Spath)"
  java -cp "$cp" clojure.main -m shadow.cljs.devtools.cli compile "$build"
}
ENVEOF
fi
