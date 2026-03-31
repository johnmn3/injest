---
description: Info on how to evaluate Clojure code via nREPL using clj-nrepl-eval
---

When you need to evaluate Clojure code you can use the
`clj-nrepl-eval` command (if installed via bbin) to evaluate code
against an nREPL server.  This means the state of the REPL session
will persist between evaluations.

You can require or load a file in one evaluation of the command and
when you call the command again the namespace will still be available.

## Example uses

You can evaluate clojure code to check if a file you just edited still compiles and loads.

Whenever you require a namespace always use the `:reload` key.

## How to Use

The following evaluates Clojure code via an nREPL connection.

**Discover available nREPL servers:**
```bash
clj-nrepl-eval --discover-ports
```

**Evaluate code (requires --port) — prefer heredoc via stdin:**
```bash
clj-nrepl-eval -p <PORT> <<'EOF'
(swap! my-atom inc)
EOF
```

The single-quoted heredoc delimiter (`<<'EOF'`) passes all characters literally, avoiding the Bash tool's known bug of escaping `!` to `\!` (which breaks `swap!`, `reset!`, etc.).

**Simple expressions without `!` can use command-line arguments:**
```bash
clj-nrepl-eval --port <port> "(+ 1 2 3)"
```

## Options

- `-p, --port PORT` - nREPL port (required)
- `-H, --host HOST` - nREPL host (default: 127.0.0.1)
- `-t, --timeout MILLISECONDS` - Timeout in milliseconds (default: 120000)
- `-r, --reset-session` - Reset the persistent nREPL session
- `-c, --connected-ports` - List previously connected nREPL sessions
- `-d, --discover-ports` - Discover nREPL servers in current directory
- `-h, --help` - Show help message

## Workflow

**1. Discover nREPL servers in current directory:**
```bash
clj-nrepl-eval --discover-ports
# Discovered nREPL servers:
#
# In current directory (/path/to/project):
#   localhost:7888 (clj)
#   localhost:7889 (bb)
#
# Total: 2 servers
```

**2. Check previously connected sessions (optional):**
```bash
clj-nrepl-eval --connected-ports
# Active nREPL connections:
#   127.0.0.1:7888 (clj) (session: abc123...)
#
# Total: 1 active connection
```

**3. Evaluate code:**
```bash
clj-nrepl-eval -p 7888 "(+ 1 2 3)"
```

## Examples

**Discover servers:**
```bash
clj-nrepl-eval --discover-ports
```

**Basic evaluation:**
```bash
clj-nrepl-eval -p 7888 "(+ 1 2 3)"
```

**With timeout:**
```bash
clj-nrepl-eval -p 7888 --timeout 5000 "(Thread/sleep 10000)"
```

**Multiple expressions:**
```bash
clj-nrepl-eval -p 7888 "(def x 10) (* x 2) (+ x 5)"
```

**Reset session:**
```bash
clj-nrepl-eval -p 7888 --reset-session
```

## Features

- **Server discovery** - Use --discover-ports to find all nREPL servers (Clojure, Babashka, shadow-cljs, etc.) in current directory
- **Session tracking** - Use --connected-ports to see previously connected sessions
- **Automatic delimiter repair** - fixes missing/mismatched parens before evaluation
- **Timeout handling** - interrupts long-running evaluations
- **Persistent sessions** - State persists across invocations
