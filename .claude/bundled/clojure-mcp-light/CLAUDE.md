# CLAUDE.md

Instructions for Claude Code when working with this repository.

## Project Overview

clojure-mcp-light provides CLI tooling for Clojure development in Claude Code:
- **clj-paren-repair-claude-hook** - Auto-fixes delimiter errors in Clojure files via hooks
- **clj-nrepl-eval** - nREPL evaluation with automatic delimiter repair

## Essential Commands

```bash
# Run tests
bb test

# Lint
clj-kondo --lint src --lint test --lint scripts

# Install locally
bbin install .
bbin install . --as clj-nrepl-eval --main-opts '["-m" "clojure-mcp-light.nrepl-eval"]'

# Test hook manually
echo '{"hook_event_name":"PreToolUse","tool_name":"Write","tool_input":{"file_path":"test.clj","content":"(def x 1)"}}' | bb -m clojure-mcp-light.hook
echo '{"hook_event_name":"PreToolUse","tool_name":"Write","tool_input":{"file_path":"test.clj","content":"(def x 1)"}}' | bb -m clojure-mcp-light.hook -- --cljfmt --stats

# Show help
bb -m clojure-mcp-light.hook -- --help

# Quick eval/testing with bb and heredoc
bb <<'EOF'
(require '[edamame.core :as e])
(println (e/parse-string "{1 2 #?@(:cljs [3 4])}" {:all true :features #{:cljs} :read-cond :allow}))
EOF
```

## Core Modules

**delimiter_repair.clj** - Detects and repairs delimiter errors using edamame parser. Uses parinfer-rust when available, falls back to parinferish (pure Clojure)

**hook.clj** - Intercepts Write/Edit operations to auto-fix delimiter errors. For Write: fixes before writing. For Edit: creates backup, fixes after edit, restores if unfixable. Optional `--cljfmt` flag for formatting. Supports `--stats` for tracking delimiter events.

**nrepl_eval.clj** - nREPL client with timeout handling, persistent sessions, and delimiter repair. Use `--connected-ports` to discover connections, `--port` to specify target.

**tmp.clj** - Session-scoped temp file management with automatic cleanup via SessionEnd hook.

## Key Details

- Hook processes Clojure files by extension: `.clj`, `.cljs`, `.cljc`, `.bb`, `.edn`, `.lpy` (case-insensitive)
- Hook also detects Babashka scripts via shebang (`#!/usr/bin/env bb` or `#!/usr/bin/bb`)
- `delimiter-error?` only detects delimiter errors, not general syntax errors
- Logging enabled via `CML_ENABLE_LOGGING=true`, writes to `.clojure-mcp-light-hooks.log`
- Stats tracked in `~/.clojure-mcp-light/stats.log` when using `--stats` flag
- nREPL sessions persist per target in session-scoped temp dirs

## Dependencies

External tools:
- **parinfer-rust** (optional, recommended) - Faster delimiter repair when on PATH; falls back to parinferish if not available
- **cljfmt** (optional) - For `--cljfmt` flag
- **babashka** - For running scripts
- **bbin** - For installation

Clojure deps (bb.edn): edamame, cheshire, tools.cli, nrepl/bencode, parinferish

## Heredoc for Bash Tool Evaluation

Prefer heredocs with a single-quoted delimiter when evaluating code to avoid shell escaping issues:

```bash
clj-nrepl-eval -p <PORT> <<'EOF'
(swap! my-atom inc)
EOF

bb <<'EOF'
(println "hello!")
EOF
```