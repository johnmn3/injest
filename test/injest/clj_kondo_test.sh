#!/usr/bin/env bash
#
# Test that reproduces clj-kondo/clj-kondo#2798:
# The macroexpand hook for x>> maps to +>>, but if +>> is defined as defn
# instead of defmacro, clj-kondo's macroexpand convention passes &form and &env
# as the first two args (which defmacro absorbs implicitly but defn does not).
# This causes the original (x>> ...) call to leak through into the expanded
# output, leading to infinite re-expansion -> StackOverflowError.
#
# This test:
# 1. Verifies the bug: old config with defn +>/+>> causes clj-kondo to error
# 2. Verifies the fix: new config with defmacro +>/+>> lints cleanly
#
# Usage: bash test/injest/clj_kondo_test.sh [/path/to/clj-kondo]

set -euo pipefail

CLJ_KONDO="${1:-clj-kondo}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"

TMPDIR_ROOT="$(mktemp -d)"
trap 'rm -rf "$TMPDIR_ROOT"' EXIT

PASS=0
FAIL=0

pass() { echo "  PASS: $1"; PASS=$((PASS + 1)); }
fail() { echo "  FAIL: $1"; FAIL=$((FAIL + 1)); }

###############################################################################
# Create the sample source file that triggers the bug (from the issue report)
###############################################################################

SAMPLE_SRC="$TMPDIR_ROOT/sample_src/clj_kondo_debug/core.clj"
mkdir -p "$(dirname "$SAMPLE_SRC")"
cat > "$SAMPLE_SRC" << 'CLOJURE'
(ns clj-kondo-debug.core
  (:require
   [injest.path :as inj]))

(defn foo []
  (inj/x>> [1 2 3]
    (map inc)))

(defn bar []
  (inj/x> {:a 1 :b 2}
    :a))

(defn baz []
  (inj/+>> [1 2 3]
    (map inc)))

(defn quux []
  (inj/+> {:a 1 :b 2}
    :a))
CLOJURE

###############################################################################
# Helper: set up a .clj-kondo dir with given config and hook, then lint
# Returns clj-kondo's exit code (0 = clean, nonzero = errors)
###############################################################################

run_lint() {
  local label="$1"
  local hook_file="$2"
  local config_edn="$3"
  local workdir="$TMPDIR_ROOT/$label"

  mkdir -p "$workdir/.clj-kondo/imports/net.clojars.john/injest/injest"

  # Write the hook implementation
  cp "$hook_file" "$workdir/.clj-kondo/imports/net.clojars.john/injest/injest/path.clj"

  # Write the config
  cp "$config_edn" "$workdir/.clj-kondo/imports/net.clojars.john/injest/config.edn"

  # Point .clj-kondo to the imports
  cat > "$workdir/.clj-kondo/config.edn" << 'EOF'
{:config-paths ["imports/net.clojars.john/injest"]}
EOF

  # Run clj-kondo and capture output + exit code
  local output
  local rc=0
  output=$("$CLJ_KONDO" --lint "$SAMPLE_SRC" --config-dir "$workdir/.clj-kondo" 2>&1) || rc=$?

  echo "$output"
  return $rc
}

###############################################################################
# Create the BROKEN hook (defn instead of defmacro) - the old code
###############################################################################

BROKEN_HOOK="$TMPDIR_ROOT/broken_hook.clj"
cat > "$BROKEN_HOOK" << 'CLOJURE'
(ns injest.path)

(def protected-fns #{`fn 'fn 'fn* 'partial})

(defn get-or-nth [m-or-v aval]
  (if (associative? m-or-v)
    (get m-or-v aval)
    (nth m-or-v aval)))

(defn path-> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~x ~@(next form)) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list 'injest.path/get-or-nth x form)
        :else
        (list form x)))

(defn path->> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~@(next form) ~x) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list 'injest.path/get-or-nth x form)
        :else
        (list form x)))

(defn +>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path-> (first forms) x) (next forms))
      x)))

(defn +>>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path->> (first forms) x) (next forms))
      x)))
CLOJURE

###############################################################################
# Create the FIXED hook (defmacro instead of defn) - the new code
###############################################################################

FIXED_HOOK="$TMPDIR_ROOT/fixed_hook.clj"
cat > "$FIXED_HOOK" << 'CLOJURE'
(ns injest.path)

(def protected-fns #{`fn 'fn 'fn* 'partial})

(defn get-or-nth [m-or-v aval]
  (if (associative? m-or-v)
    (get m-or-v aval)
    (nth m-or-v aval)))

(defn path-> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~x ~@(next form)) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list 'injest.path/get-or-nth x form)
        :else
        (list form x)))

(defn path->> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~@(next form) ~x) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list 'injest.path/get-or-nth x form)
        :else
        (list form x)))

(defmacro +>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path-> (first forms) x) (next forms))
      x)))

(defmacro +>>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path->> (first forms) x) (next forms))
      x)))
CLOJURE

###############################################################################
# Config EDN (same for both, matches the project's config.edn)
###############################################################################

CONFIG_EDN="$TMPDIR_ROOT/config.edn"
cat > "$CONFIG_EDN" << 'CLOJURE'
{:lint-as {injest.core/x>  clojure.core/->
           injest.core/x>> clojure.core/->>
           injest.core/=>  clojure.core/->
           injest.core/=>> clojure.core/->>
           injest.core/|>  clojure.core/->
           injest.core/|>> clojure.core/->>

           injest.path/+>  clojure.core/->
           injest.path/+>> clojure.core/->>
           injest.path/x>  clojure.core/->
           injest.path/x>> clojure.core/->>
           injest.path/=>  clojure.core/->
           injest.path/=>> clojure.core/->>
           injest.path/|>  clojure.core/->
           injest.path/|>> clojure.core/->>}

 :hooks {:macroexpand {injest.path/+>  injest.path/+>
                       injest.path/+>> injest.path/+>>
                       injest.path/x>  injest.path/+>
                       injest.path/x>> injest.path/+>>
                       injest.path/=>  injest.path/+>
                       injest.path/=>> injest.path/+>>
                       injest.path/|>  injest.path/+>
                       injest.path/|>> injest.path/+>>}}

 :linters {:injest.path/+> {:level :error}
           :injest.path/+>> {:level :error}
           :unused-binding {:level :off}}}
CLOJURE

###############################################################################
# TEST 1: Broken hook (defn) should cause clj-kondo to error
###############################################################################

echo ""
echo "=== Test 1: Broken hook (defn +>/+>>) should cause clj-kondo error ==="
echo ""

BROKEN_OUTPUT=$(run_lint "broken" "$BROKEN_HOOK" "$CONFIG_EDN" 2>&1) && BROKEN_RC=0 || BROKEN_RC=$?

echo "$BROKEN_OUTPUT"
echo ""
echo "  Exit code: $BROKEN_RC"

if [ "$BROKEN_RC" -ne 0 ]; then
  pass "Broken hook (defn) causes clj-kondo to report errors (confirms the bug)"
else
  fail "Broken hook (defn) should have caused errors but didn't"
fi

###############################################################################
# TEST 2: Fixed hook (defmacro) should lint cleanly
###############################################################################

echo ""
echo "=== Test 2: Fixed hook (defmacro +>/+>>) should lint cleanly ==="
echo ""

FIXED_OUTPUT=$(run_lint "fixed" "$FIXED_HOOK" "$CONFIG_EDN" 2>&1) && FIXED_RC=0 || FIXED_RC=$?

echo "$FIXED_OUTPUT"
echo ""
echo "  Exit code: $FIXED_RC"

if [ "$FIXED_RC" -eq 0 ]; then
  pass "Fixed hook (defmacro) lints cleanly (confirms the fix)"
else
  fail "Fixed hook (defmacro) should have linted cleanly but got errors"
fi

###############################################################################
# TEST 3: Verify the actual project config from resources/ works
###############################################################################

echo ""
echo "=== Test 3: Project's resources/ config should lint cleanly ==="
echo ""

PROJECT_HOOK="$PROJECT_ROOT/resources/clj-kondo.exports/net.clojars.john/injest/injest/path.clj"
PROJECT_CONFIG="$PROJECT_ROOT/resources/clj-kondo.exports/net.clojars.john/injest/config.edn"

if [ -f "$PROJECT_HOOK" ] && [ -f "$PROJECT_CONFIG" ]; then
  PROJECT_OUTPUT=$(run_lint "project" "$PROJECT_HOOK" "$PROJECT_CONFIG" 2>&1) && PROJECT_RC=0 || PROJECT_RC=$?

  echo "$PROJECT_OUTPUT"
  echo ""
  echo "  Exit code: $PROJECT_RC"

  if [ "$PROJECT_RC" -eq 0 ]; then
    pass "Project config from resources/ lints cleanly"
  else
    fail "Project config from resources/ should lint cleanly but got errors"
  fi
else
  fail "Project config files not found at expected paths under resources/"
fi

###############################################################################
# Summary
###############################################################################

echo ""
echo "============================================"
echo "  Results: $PASS passed, $FAIL failed"
echo "============================================"
echo ""

if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
