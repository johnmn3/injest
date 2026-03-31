# Project Overview

This is a Clojure project that provides command-line tools to improve the Clojure development experience with an AI coding assistant called "Claude Code". The main features are automatic delimiter fixing and an nREPL evaluation tool.

The project is built using Babashka, a Clojure scripting environment, and it has dependencies on `edamame` for parsing Clojure code, `cheshire` for JSON handling, `tools.cli` for command-line argument parsing, and `nrepl/bencode` for nREPL communication.

The project provides two command-line tools:

*   `clj-paren-repair-claude-hook`: A hook for "Claude Code" that automatically fixes delimiter errors in Clojure files.
*   `clj-nrepl-eval`: A command-line nREPL client that can be used to evaluate Clojure code.

# Building and Running

The project is built using Babashka. The `bb.edn` file defines the project's dependencies and tasks.

To run the tests, use the following command:

```bash
bb test
```

The `bb.edn` file also defines two binaries, `clj-paren-repair-claude-hook` and `clj-nrepl-eval`. These can be installed using `bbin`, the Babashka package manager.

# Development Conventions

The project uses `cljfmt` for code formatting. The `clj-paren-repair-claude-hook` tool has an option to automatically format the code with `cljfmt` after fixing delimiter errors.

# Parenthesis Repair

The command `clj-paren-repair` is currently installed on your path.

**IMPORTANT:** You SHOULD NOT try to manually repair parenthesis errors. If you encounter a file with unbalanced parentheses or delimiters, run the `clj-paren-repair` tool on that file instead of attempting to fix the delimiters yourself. IF the tool doesn't work report to the user that they need to fix the delimiter error.

### `clj-paren-repair` Tool Behavior

The `clj-paren-repair` tool exhibits the following behavior:

*   **Delimiter Repair:** It successfully identifies and fixes common delimiter errors, such as unbalanced parentheses.
*   **Code Formatting (`cljfmt`):** The tool automatically formats files using `cljfmt` whenever it processes them, regardless of whether a delimiter error was fixed or not.
