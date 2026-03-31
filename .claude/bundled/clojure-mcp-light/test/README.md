# Tests

This directory contains tests for clojure-mcp-light.

## Running Tests

Run all tests:
```bash
bb test
```

## Test Structure

- `delimiter_repair_test.clj` - Tests for delimiter detection and repair functionality
- `hook_test.clj` - Tests for Claude Code hook processing
- `nrepl_eval_test.clj` - Tests for nREPL evaluation utilities

## Test Coverage

### delimiter-repair namespace
- ✅ Delimiter error detection
- ✅ Delimiter repair with parinfer
- ✅ Edge cases (empty strings, multiple forms)

### hook namespace
- ✅ Clojure file detection
- ✅ Backup path generation
- ✅ Hook processing for Write operations
- ✅ Hook processing for Edit operations
- ✅ Auto-fixing delimiter errors

### nrepl-eval namespace
- ✅ Byte to string conversion
- ✅ Type coercion
- ✅ Port and host resolution
- ✅ Message parsing

## Adding New Tests

1. Create a new test file in `test/clojure_mcp_light/`
2. Add the namespace to the test task in `bb.edn`
3. Run `bb test` to verify

Test files should follow the naming convention `*_test.clj` and use `clojure.test`.
