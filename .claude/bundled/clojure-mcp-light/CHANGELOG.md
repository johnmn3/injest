# Changelog

All notable changes to this project will be documented in this file.

## [0.2.2] - 2026-03-14

This release fixes the PreToolUse Write hook so delimiter repairs are actually applied, adds ClojureDart (.cljd) file support, and updates documentation to recommend heredoc for code evaluation.

### Added
- **ClojureDart (.cljd) file extension support** - Hook now processes .cljd files (PR #14)
- **morph-mcp edit_file hook support** - Hook intercepts mcp__morph-mcp__edit_file tool
- **Stdin/stdout support for clj-paren-repair** - Pipe content directly for repair

### Fixed
- **PreToolUse Write hook now works** - Added `permissionDecision: "allow"` to hook response so Claude Code applies delimiter fixes on Write operations (PR #13, fixes #9)
- **Correct --reset-session docs** - Clarified that --reset-session only clears nREPL session vars (*e, *1), not def'd vars or namespaces (fixes #17)

### Changed
- **Prefer heredoc for code evaluation** - Docs now recommend heredoc (`<<'EOF'`) over quoted strings to avoid shell escaping issues
- **Documentation updates** - Quick install section, navigation links, Babashka version requirement

## [0.2.1] - 2025-11-27

This release adds a new standalone `clj-paren-repair` tool for LLM clients without hook support (Gemini CLI, Codex CLI), significantly improves nREPL port discovery performance, and includes comprehensive documentation updates that reorganize the README around the three CLI tools.

### Added
- **clj-paren-repair standalone tool** - New command for LLMs without hook support
  - Works with Gemini CLI, Codex CLI, and any LLM with shell access
  - Provides escape route from "Paren Edit Death Loop"
  - Automatically formats with cljfmt when processing files
  - Shared delimiter repair logic with hook tool

- **Help flag for clj-paren-repair** - Added `-h`/`--help` support

- **AGENTS.md support** - Now includes AGENTS.md in prompts for Codex CLI compatibility

### Changed
- **Parallelized port discovery** - nREPL port discovery now runs in parallel with reduced timeout (250ms)
  - Much faster `--discover-ports` execution
  - Better responsiveness when scanning multiple ports

- **Documentation reorganization** - README now structured around three CLI tools
  - Clearer quick reference table
  - Expanded rationale for clj-paren-repair approach
  - Better installation and configuration instructions
  - Updated GEMINI.md with project overview

### Fixed
- **cljfmt integration** - Fixed clj-paren-repair cljfmt integration issues

## [0.2.0] - 2025-11-17

### Summary

This release makes parinfer-rust optional by adding parinferish as a pure Clojure fallback, eliminating external dependencies while maintaining full delimiter repair functionality. The release also includes significant nREPL improvements with better port discovery, enhanced session management, and comprehensive refactoring for cleaner architecture.

### Added
- **Parinferish fallback** - Pure Clojure delimiter repair when parinfer-rust unavailable
  - Automatic backend selection: prefers parinfer-rust, falls back to parinferish
  - New `parinferish-repair` function for pure Clojure delimiter fixing
  - New `parinfer-rust-available?` function to detect parinfer-rust on PATH
  - Unified `repair-delimiters` function handles backend selection automatically
  - No external dependencies required for basic delimiter repair
  - Comprehensive test coverage for new repair functions

- **Enhanced nREPL port discovery** - Better workflow for finding and connecting to REPL servers
  - `--discover-ports` now shows servers grouped by directory
  - Shadow-cljs detection using `:ns` field from eval
  - Cross-platform UTF8 encoding protection for reliable output
  - Improved directory-based server organization

- **nREPL namespace differentiation** - Better LLM understanding of evaluation context
  - Namespace information included in output dividers
  - Helps Claude understand which namespace code was evaluated in
  - Improved context for multi-namespace projects

### Changed
- **Bundled cljfmt** - Now uses cljfmt from Babashka instead of external binary
  - Eliminates another external dependency
  - Consistent behavior across platforms
  - Simpler installation process

- **Improved nREPL architecture** - Major refactoring for better maintainability
  - Extracted nrepl-client library with lazy sequence API
  - Connection-based API with `*` suffix pattern for stateful operations
  - Consolidated session validation following `*` suffix pattern
  - Simplified `eval-expr-with-timeout` using connection-based API
  - Optimized `discover-nrepl-ports` to use single connection per port
  - Better separation of concerns between client and evaluation logic

- **Enhanced file detection** - Broader Clojure file support
  - Added `.lpy` support for Lispy Python files
  - Case-insensitive extension matching (`.CLJ`, `.Clj`, etc.)
  - Babashka shebang detection (`#!/usr/bin/env bb`, `#!/usr/bin/bb`)

- **Documentation improvements**
  - Updated README and CLAUDE.md to reflect optional parinfer-rust
  - Added GEMINI.md for project overview and instructions
  - Consolidated hook examples using global settings
  - Better installation instructions with fewer requirements

### Removed
- **Unused nREPL functions** - Cleaned up high-level convenience functions
  - Removed `->uuid` function (unused)
  - Removed edit validator and validation tracking (overly complex)
  - Simplified codebase by removing unnecessary abstractions
  - Better focus on core functionality

### Fixed
- **nREPL session management** - More robust session handling
  - Better session data management with improved validation
  - Cleaner filesystem usage with session-scoped temp files
  - Proper cleanup of session directories

## [0.1.1] - 2025-11-11

### Summary

This release improves the developer experience with Claude Code through enhanced documentation and better nREPL evaluation workflow. The most significant improvements are the new clojure-eval skill that provides streamlined REPL interactions and improved documentation with heredoc examples for better code evaluation patterns.

### Added
- **Claude Code skill for clojure-eval** - New skill provides streamlined nREPL evaluation workflow in Claude Code with automatic port discovery and session management
- **Stdin support for clj-nrepl-eval** - Evaluate code directly from stdin for easier piping and scripting workflows
- **Delimiter repair test for edge cases** - Added test coverage for unusual delimiter patterns

### Changed
- **Improved skill documentation** - Enhanced clojure-eval skill instructions with heredoc examples showing best practices for code evaluation
- **Timeout handling for nREPL** - All nREPL evaluations now use consistent timeout and interrupt handling for better reliability
- **Documentation cleanup** - Cleaned up CLAUDE.md file for better clarity

## [0.1.0] - 2025-11-10

### Summary

This release introduces edit validation metrics, enhanced nREPL connection discovery, and improved statistics tracking. The most significant improvements are in understanding how well the delimiter repair and cljfmt formatting are working through comprehensive validation metrics and success rate tracking.

### Added
- **Edit validation metrics** - Track validation of completed edits to understand delimiter repair effectiveness
  - New event types: `:edit-validated-ok`, `:edit-validated-error`, `:edit-validated-fixed`, `:edit-validated-fix-failed`
  - Tracks whether PostToolUse hook successfully validates and fixes edited files
  - Enables measurement of end-to-end edit quality and repair success rate
  - Stats summary includes edit validation breakdown and success metrics

- **Connection discovery for nREPL** - `--connected-ports` flag lists active nREPL connections
  - Displays all available nREPL servers with session information
  - Helps users discover which ports they've previously connected to
  - Makes `--port` requirement clearer by providing easy discovery mechanism
  - Scans nREPL session files in session-scoped temp directory

- **nREPL testing utilities** - Added `deps.edn` for connection testing
  - Provides reproducible nREPL server setup for development and testing
  - Documents port conventions (7890, 7891, 7892, etc.)
  - Simplifies manual testing of nREPL evaluation features

### Changed
- **Required --port flag** - Made `--port` explicit and required for all nREPL operations
  - Removed fallback to NREPL_PORT env var and .nrepl-port file
  - Prevents confusion about which server is being used
  - Clearer, more predictable behavior
  - Use `--connected-ports` to discover available servers

- **Enhanced stats tracking** - Improved cljfmt metrics with success/failure distinction
  - Track `:cljfmt-fixed` (formatting applied successfully) separately from `:cljfmt-check-error` (formatting failed)
  - More accurate success rates for formatting operations
  - Stats summary shows cljfmt fix success rate

- **Improved stats summary** - Refactored to focus on delimiter repair validation utility
  - Clearer breakdown of Pre vs Post hook validation events
  - Edit validation metrics highlighted as key quality indicators
  - Better organization with validation-focused sections
  - More actionable insights about delimiter repair effectiveness

- **CLI option parsing** - Fixed `--stats` flag handling to properly support custom file paths
  - Parse CLI options before processing hook input
  - `--stats-file` now correctly accepts custom paths

### Fixed
- **Parser generalization** - Removed `file-path` parameter from `validate-stored-connection`
  - Parser no longer requires file path context
  - Cleaner API for connection validation
  - Fixes unnecessary coupling between validation and file operations

- **clj-kondo linting** - Fixed all linting warnings
  - Cleaner codebase with zero linting issues
  - Improved code quality and maintainability

- **Test fixes** - Corrected test suite for recent API changes
  - All tests passing
  - Better test coverage for new features

[0.2.2]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.2.2
[0.2.1]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.2.1
[0.2.0]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.2.0
[0.1.1]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.1.1
[0.1.0]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.1.0

## [0.0.4-alpha] - 2025-11-09

### Summary

This release simplifies session identification and improves the stats tracking system.

### Changed
- **Session identification simplified** - Removed Bash hook and CML_CLAUDE_CODE_SESSION_ID functionality
  - Now relies exclusively on GPID (grandparent process ID) approach
  - Removed PreToolUse Bash hook that prepended session ID to commands
  - Removed all CML_CLAUDE_CODE_SESSION_ID environment variable references
  - Updated tmp.clj to use only GPID for session identification
  - Deleted scripts/echo-session-id.sh utility
  - Updated configuration examples to remove Bash from matchers
  - GPID provides stable session identification using Claude Code process hierarchy

- **Improved GPID-based session identification**
  - Use grandparent PID instead of parent PID for stable session IDs
  - GPID remains constant across multiple bb invocations within same Claude session
  - Fixes nREPL session persistence by ensuring consistent session file paths
  - Benefits: persistent namespaces, variables, and REPL state across evaluations

- **Backup path refactoring**
  - Replace path-preserving backups with hash-based structure
  - Use SHA-256 for stronger collision resistance
  - Implement 2-level directory sharding (h0h1/h2h3/) to prevent filesystem issues
  - Format: {backups-dir}/{shard1}/{shard2}/{hash}--{sanitized-filename}
  - Simplify session-root path structure to clojure-mcp-light/{session}-proj-{hash}

- **Stats script improvements**
  - Fixed misleading "Hook-level events" terminology
  - Now correctly shows "Delimiter events" (events with :hook-event field)
  - Added separate "Cljfmt events" count
  - More accurate breakdown of event types

- **Documentation updates**
  - Updated CLAUDE.md with current log file path (.clojure-mcp-light-hooks.log)
  - Note that log file is configurable via --log-file flag

[0.0.4-alpha]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.0.4-alpha

## [0.0.3-alpha] - 2025-11-09

This version represents a major improvement in robustness and developer experience.

### Summary

* **Fixed hook response protocol** - Hooks now return `nil` for normal operations instead of explicit `permissionDecision: allow` responses. The previous approach was bypassing Claude Code's normal permission dialogs, causing the UI to not properly prompt users for confirmation.

* **Robust nREPL session persistence** - Session management now properly handles multiple concurrent Claude Code sessions running in the same directory using session-scoped temporary files with fallback strategies (env var → PPID-based → global).

* **Automatic cleanup via SessionEnd hook** - Session persistence requires temporary file storage that must be cleaned up. The new SessionEnd hook automatically removes session directories when Claude Code sessions terminate, preventing accumulation of stale temporary files.

* **cljfmt support** - The `--cljfmt` CLI option enables automatic code formatting. Claude frequently indents code incorrectly by one space, and cljfmt quickly fixes these issues. Well-formatted code is essential for parinfer's indent mode to work correctly, making this option highly recommended.

* **Debugging support** - The `--log-level` and `--log-file` CLI options provide configurable logging. Without proper logging, developing and troubleshooting clojure-mcp-light is extremely difficult.

* **Statistics tracking** - The `--stats` flag enables global tracking of delimiter events. The `scripts/stats-summary.bb` tool provides comprehensive analysis of fix rates, error patterns, and code quality metrics.

### Added
- **Statistics tracking system** - Track delimiter events to analyze LLM code quality
  - `--stats` CLI flag enables event logging to `~/.clojure-mcp-light/stats.log`
  - Event types: `:delimiter-error`, `:delimiter-fixed`, `:delimiter-fix-failed`, `:delimiter-ok`
  - Stats include timestamps, hook events, and file paths
  - `scripts/stats-summary.bb` - Comprehensive analysis tool for stats logs
  - Low-level parse error tracking and false positive filtering
  - Cljfmt efficiency tracking (already-formatted vs needed-formatting vs check-errors)

- **Unified tmp namespace** - Session-scoped temporary file management
  - Centralized temporary file paths with automatic cleanup
  - Editor session detection with fallback strategies (env var → PPID-based → global)
  - Deterministic paths based on user, hostname, session ID, and project SHA
  - Per-project isolation prevents conflicts across multiple projects
  - Functions: `session-root`, `editor-scope-id`, `cleanup-session!`, `get-possible-session-ids`

- **SessionEnd cleanup hook** - Automatic temp file cleanup
  - Removes session directories when Claude Code sessions terminate
  - Attempts cleanup for all possible session IDs (env-based and PPID-based)
  - Recursive deletion with detailed logging (attempted, deleted, errors, skipped)
  - Never blocks SessionEnd events, even on errors

- **Enhanced CLI options**
  - `--log-level LEVEL` - Explicit log level control (trace, debug, info, warn, error, fatal, report)
  - `--log-file PATH` - Custom log file path (default: `./.clojure-mcp-light-hooks.log`)
  - `--cljfmt` - Enable automatic code formatting with cljfmt after write/edit operations

- **Comprehensive testing documentation** in CLAUDE.md
  - Manual hook testing instructions
  - Claude Code integration testing guide
  - Troubleshooting section for common issues

### Changed
- **Logging system** - Replaced custom logging with Timbre
  - Structured logging with timestamps, namespaces, and line numbers
  - Configurable appenders and log levels
  - Conditional ns-filter for targeted logging
  - Disabled by default to avoid breaking hook protocol

- **Hook system improvements**
  - Refactored hook response format to minimize unnecessary output
  - Updated hook tests to match new response format
  - Extracted PPID session ID logic into dedicated function
  - Flattened tmp directory structure to single session-project level

- **CLI handling** - Refactored into dedicated `handle-cli-args` function
  - Cleaner separation of concerns
  - Better error handling and help messages
  - Uses `tools.cli` for argument parsing

- **File organization** - Migrated to unified tmp namespace
  - `hook.clj` now uses tmp namespace for backups
  - `nrepl_eval.clj` now uses tmp namespace for per-target sessions
  - Consistent session-scoped file management across all components

### Removed
- **-c short flag** for `--cljfmt` option (prevented conflicts with potential future flags)

[0.0.3-alpha]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.0.3-alpha

## [0.0.2-alpha] - 2025-11-08

### Added
- **Enhanced ClojureScript support** - Learning to use edamame to detect delimiter errors across the widest possible set of Clojure/ClojureScript files
  - Added `:features #{:clj :cljs :cljr :default}` to enable platform-specific reader features
  - Explicit readers for common ClojureScript/EDN tagged literals:
    - `#js` - JavaScript object literals
    - `#jsx` - JSX literals
    - `#queue` - Queue data structures
    - `#date` - Date literals
  - Changed `:auto-resolve` to use `name` function for better compatibility

- **scripts/test-parse-all.bb** - Testing utility for delimiter detection
  - Recursively finds and parses all Clojure files in a directory
  - Reports unknown tags with suggestions for adding readers
  - Helps validate edamame configuration across real codebases
  - Stops on first error with detailed reporting

- **Dynamic var for error handling** - `*signal-on-bad-parse*` (defaults to `true`)
  - Triggers parinfer on unknown tag errors as a safety net
  - Allows users to opt out via binding if needed
  - More defensive approach: better to attempt repair than skip

- **Expanded test coverage**
  - 30 tests (up from 27) with 165 assertions (up from 129)
  - New test suites for ClojureScript features:
    - `clojurescript-tagged-literals-test` - All supported tagged literals
    - `clojurescript-features-test` - Namespaced keywords and `::keys` destructuring
    - `mixed-clj-cljs-features-test` - Cross-platform code with reader conditionals
  - Tests validate both delimiter detection and proper parsing

### Changed
- Updated `bb.edn` to use cognitect test-runner instead of manual test loading
  - Cleaner test execution
  - Better output formatting
  - Standard Clojure tooling approach

### Removed
- **Legacy standalone .bb scripts** - Removed `clj-paren-repair-hook.bb` and `clojure-nrepl-eval.bb`
  - Now use `bb -m clojure-mcp-light.hook` and `bb -m clojure-mcp-light.nrepl-eval` instead
  - bbin installation uses namespace entrypoints from `bb.edn`
  - Eliminates 597 lines of duplicate code
  - Simpler maintenance with single source of truth

[0.0.2-alpha]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.0.2-alpha

## [0.0.1-alpha] - 2025-11-08

### Added
- **clj-paren-repair-claude-hook** - Claude Code hook for automatic Clojure delimiter fixing
  - Detects delimiter errors using edamame parser
  - Auto-fixes with parinfer-rust
  - PreToolUse hooks for Write/Edit/Bash operations
  - PostToolUse hooks for Edit operations with backup/restore
  - Cross-platform backup path handling
  - Session-specific backup isolation

- **clj-nrepl-eval** - nREPL evaluation tool
  - Direct bencode protocol implementation for nREPL communication
  - Automatic delimiter repair before evaluation
  - Timeout and interrupt handling for long-running evaluations
  - Persistent session support with Claude Code session-id based tmp-file with `./.nrepl-session` file as fallback
  - `--reset-session` flag for session management
  - Port detection: CLI flag > NREPL_PORT env > .nrepl-port file
  - Formatted output with dividers

- **Slash commands** for Claude Code
  - `/start-nrepl` - Start nREPL server in background
  - `/clojure-nrepl` - Information about REPL evaluation

- **Installation support**
  - bbin package manager integration
  - Proper namespace structure (clojure-mcp-light.*)

- **Documentation**
  - Comprehensive README.md
  - CLAUDE.md project documentation for Claude Code
  - Example settings and configuration files
  - EPL-2.0 license

### Changed
- Logging disabled by default for hook operations
- Error handling: applies parinfer on all errors for maximum robustness

[0.0.1-alpha]: https://github.com/bhauman/clojure-mcp-light/releases/tag/v0.0.1-alpha
