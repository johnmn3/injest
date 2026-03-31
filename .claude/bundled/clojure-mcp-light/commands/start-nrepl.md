---
description: Start an nREPL server in the background
---

When the user invokes this command, start an nREPL server in the background by following these steps:

## Step 1: Check CLAUDE.md or AGENTS.md for REPL Instructions

First, check if a `CLAUDE.md` or `AGENTS.md` file exists in the project root:

1. If `CLAUDE.md` or `AGENTS.md` exists, read it and look for a section about starting the REPL or nREPL
2. Look for keywords like "REPL", "nREPL", "start", "run", or similar
3. If specific instructions are found, follow those instructions instead of the default steps below

## Step 2: Check Environment

First, verify that nREPL is configured in the project:

1. Check if `deps.edn` exists and contains an `:nrepl` alias
2. Check if `project.clj` exists (Leiningen project)

If neither file exists or nREPL is not configured:
- Inform the user that nREPL is not configured
- Ask if they want you to add the nREPL configuration to `deps.edn`

## Step 3: Check for Existing nREPL Servers

Before starting a new server, check for existing servers in the current directory:

1. Run `clj-nrepl-eval --discover-ports` to find nREPL servers in current directory
2. If servers are found, inform the user and display the ports with their types (clj/bb/etc)
3. Ask if they want to start an additional server or use an existing one
4. Optionally also check `clj-nrepl-eval --connected-ports` to see previously connected sessions

## Step 4: Start nREPL Server

Start the nREPL server in the background WITHOUT specifying a port (let nREPL auto-assign an available port):

**For deps.edn projects:**
```bash
clojure -M:nrepl
```

**For Leiningen projects:**
```bash
lein repl :headless
```

Use the Bash tool with `run_in_background: true` to start the server.

## Step 5: Extract Port from Output

1. Wait 2-3 seconds for the server to start
2. Use the BashOutput tool to check the startup output
3. Parse the port number from output like: "nREPL server started on port 54321..."
4. Extract the numeric port value

## Step 6: Test Connection

Verify the connection by running a test evaluation:
```bash
clj-nrepl-eval -p PORT "(+ 1 2 3)"
```

## Step 7: Report to User

Display to the user:
- The port number the server is running on
- The connection URL (e.g., `nrepl://localhost:PORT`)
- The background process ID
- The command to use: `clj-nrepl-eval -p PORT "code"`
- Mention that they can use `--connected-ports` to see this connection later

## Example Output to User

```
nREPL server started successfully!

Port: 54321
URL: nrepl://localhost:54321
Background process: a12345

To evaluate code:
  clj-nrepl-eval -p 54321 "(+ 1 2 3)"

To see all connections:
  clj-nrepl-eval --connected-ports
```

## Error Handling

- If the server fails to start, display the error output
- If port cannot be parsed, show the raw output and ask user to check manually
- If `.nrepl-port` file cannot be created, inform the user
