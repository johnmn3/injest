# clj-nrepl-eval Examples

## Discovery

```bash
clj-nrepl-eval --connected-ports
```

## Heredoc for Multiline Code

```bash
clj-nrepl-eval -p 7888 <<'EOF'
(defn greet [name]
  (str "Hello, " name "!"))

(greet "Claude")
EOF
```

### Heredoc Simplifies String Escaping

Heredoc avoids shell escaping issues with quotes, backslashes, and special characters:

```bash
# With heredoc - no escaping needed
clj-nrepl-eval -p 7888 <<'EOF'
(def regex #"\\d{3}-\\d{4}")
(def message "She said \"Hello!\" and waved")
(def path "C:\\Users\\name\\file.txt")
(println message)
EOF

# Without heredoc - requires complex escaping
clj-nrepl-eval -p 7888 "(def message \"She said \\\"Hello!\\\" and waved\")"
```

## Working with Project Namespaces

```bash
# Test a function after requiring
clj-nrepl-eval -p 7888 <<'EOF'
(require '[clojure-mcp-light.delimiter-repair :as dr] :reload)
(dr/delimiter-error? "(defn foo [x]")
EOF
```

## Verify Compilation After Edit

```bash
# If this returns nil, the file compiled successfully
clj-nrepl-eval -p 7888 "(require 'clojure-mcp-light.hook :reload)"
```

## Session Management

```bash
# Reset session if state becomes corrupted
clj-nrepl-eval -p 7888 --reset-session
```

## Common Workflow Patterns

### Load, Test, Iterate

```bash
# After editing a file, reload and test in one command
clj-nrepl-eval -p 7888 <<'EOF'
(require '[my.namespace :as ns] :reload)
(ns/my-function test-data)
EOF
```

### Run Tests After Changes

```bash
clj-nrepl-eval -p 7888 <<'EOF'
(require '[my.project.core :as core] :reload)
(require '[my.project.core-test :as test] :reload)
(clojure.test/run-tests 'my.project.core-test)
EOF
```
