#!/bin/bash
# Capture hook for debugging hook calls
# This script captures the JSON input from hooks and logs it

# Create logs directory if it doesn't exist
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
LOG_DIR="${SCRIPT_DIR}/hook-logs"
mkdir -p "$LOG_DIR"

# Generate timestamp-based log file
TIMESTAMP=$(date +"%Y%m%d_%H%M%S_%N")
LOG_FILE="${LOG_DIR}/hook-${TIMESTAMP}.json"

# Read JSON from stdin and save it
INPUT=$(cat)
echo "$INPUT" > "$LOG_FILE"

# Print the log file location to stderr (won't interfere with JSON output)
echo "Hook data captured to: $LOG_FILE" >&2

# Extract hook_event_name from input
HOOK_EVENT=$(echo "$INPUT" | grep -o '"hook_event_name":"[^"]*"' | cut -d'"' -f4)

# Output appropriate response based on hook event
if [ "$HOOK_EVENT" = "SessionEnd" ]; then
  # SessionEnd hooks don't use hookSpecificOutput, just exit successfully
  exit 0
else
  echo '{"hookSpecificOutput":{"hookEventName":"PreToolUse","permissionDecision":"allow"}}'
  exit 0
fi
