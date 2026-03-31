#!/usr/bin/env bash
set -euo pipefail

# ccweb-setup.sh — Heavy lifter for ccweb + Clojure proxy support
#
# Parses $HTTPS_PROXY to generate ~/.m2/settings.xml, installs tools,
# downloads deps, and prepares the environment for Clojure development
# behind the ccweb egress proxy.

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

###############################################################################
# 1. Generate ~/.m2/settings.xml from $HTTPS_PROXY
###############################################################################

generate_maven_settings() {
  if [ -z "${HTTPS_PROXY:-}" ]; then
    echo "[ccweb-setup] No HTTPS_PROXY set, skipping Maven settings generation"
    return 0
  fi

  # Parse proxy URL: http://user:pass@host:port
  local proxy_url="$HTTPS_PROXY"
  # Strip protocol
  local no_proto="${proxy_url#http://}"
  no_proto="${no_proto#https://}"

  local userinfo=""
  local hostport=""

  if [[ "$no_proto" == *"@"* ]]; then
    userinfo="${no_proto%%@*}"
    hostport="${no_proto##*@}"
  else
    hostport="$no_proto"
  fi

  local host="${hostport%%:*}"
  local port="${hostport##*:}"
  # Strip trailing slash from port
  port="${port%%/*}"

  local username=""
  local password=""
  if [ -n "$userinfo" ]; then
    username="${userinfo%%:*}"
    password="${userinfo#*:}"
  fi

  mkdir -p ~/.m2

  cat > ~/.m2/settings.xml <<XMLEOF
<settings>
  <proxies>
    <proxy>
      <id>httpsProxy</id>
      <active>true</active>
      <protocol>https</protocol>
      <host>${host}</host>
      <port>${port}</port>
      <username>${username}</username>
      <password>${password}</password>
    </proxy>
    <proxy>
      <id>httpProxy</id>
      <active>true</active>
      <protocol>http</protocol>
      <host>${host}</host>
      <port>${port}</port>
      <username>${username}</username>
      <password>${password}</password>
    </proxy>
  </proxies>
</settings>
XMLEOF

  echo "[ccweb-setup] Generated ~/.m2/settings.xml (proxy: ${host}:${port})"
}

###############################################################################
# 2. Install Node.js (if not present)
###############################################################################

install_node() {
  if command -v node &>/dev/null; then
    echo "[ccweb-setup] Node.js already installed: $(node --version)"
    return 0
  fi

  echo "[ccweb-setup] Installing Node.js 24..."
  curl -fsSL https://deb.nodesource.com/setup_24.x | bash -
  apt-get install -y nodejs
  export NODE_USE_ENV_PROXY=1
  echo "[ccweb-setup] Node.js installed: $(node --version)"
}

###############################################################################
# 3. Install Babashka (for paren repair hook)
###############################################################################

install_babashka() {
  if command -v bb &>/dev/null; then
    echo "[ccweb-setup] Babashka already installed: $(bb --version)"
    return 0
  fi

  echo "[ccweb-setup] Installing Babashka..."
  curl -fsSL https://raw.githubusercontent.com/babashka/babashka/master/install | bash
  echo "[ccweb-setup] Babashka installed: $(bb --version)"
}

###############################################################################
# 4. Install Clojure CLI (if not present)
###############################################################################

install_clojure() {
  if command -v clojure &>/dev/null; then
    echo "[ccweb-setup] Clojure CLI already installed: $(clojure --version 2>&1 | head -1)"
    return 0
  fi

  echo "[ccweb-setup] Installing Clojure CLI..."
  curl -fsSL https://download.clojure.org/install/linux-install-1.12.0.1530.sh | bash
  echo "[ccweb-setup] Clojure CLI installed"
}

###############################################################################
# 5. Download deps for each alias
###############################################################################

download_deps() {
  echo "[ccweb-setup] Downloading project dependencies..."
  cd "$PROJECT_DIR"

  # Main deps
  clojure -P 2>/dev/null || true

  # Test deps
  clojure -P -M:test 2>/dev/null || true

  # CLJS test deps
  clojure -P -M:cljs-test 2>/dev/null || true

  # Build deps
  clojure -P -M:build 2>/dev/null || true

  echo "[ccweb-setup] Dependencies downloaded"
}

###############################################################################
# 6. npm install (if package.json exists)
###############################################################################

npm_install() {
  if [ -f "$PROJECT_DIR/package.json" ]; then
    echo "[ccweb-setup] Running npm install..."
    cd "$PROJECT_DIR"
    export NODE_USE_ENV_PROXY=1
    npm install
    echo "[ccweb-setup] npm install complete"
  fi
}

###############################################################################
# 7. Generate classpath.edn for shadow-cljs (if shadow-cljs.edn exists)
###############################################################################

generate_classpath() {
  if [ -f "$PROJECT_DIR/shadow-cljs.edn" ]; then
    echo "[ccweb-setup] Generating classpath.edn for shadow-cljs..."
    cd "$PROJECT_DIR"
    local cp
    cp="$(clojure -Spath 2>/dev/null)" || true
    if [ -n "$cp" ]; then
      echo "{:classpath \"$cp\"}" > "$PROJECT_DIR/classpath.edn"
      echo "[ccweb-setup] classpath.edn generated"
    fi
  fi
}

###############################################################################
# Main
###############################################################################

echo "[ccweb-setup] Starting ccweb + Clojure setup..."
generate_maven_settings
install_node
install_babashka
install_clojure
download_deps
npm_install
generate_classpath
echo "[ccweb-setup] Setup complete!"
