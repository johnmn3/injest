(ns clojure-mcp-light.tmp
  "Unified temporary file management for Claude Code sessions.

  Provides consistent temp file paths for backups, nREPL sessions, and other
  temporary files with automatic cleanup support via SessionEnd hooks."
  (:require [babashka.fs :as fs]
            [clojure.edn :as edn]
            [clojure.string :as str]))

;; ============================================================================
;; Config & Helpers
;; ============================================================================

(defn runtime-base-dir
  "Get base directory for runtime temporary files.
  Prefers XDG_RUNTIME_DIR if present, otherwise falls back to java.io.tmpdir."
  []
  (or (System/getenv "XDG_RUNTIME_DIR")
      (System/getProperty "java.io.tmpdir")))

(defn sanitize
  "Sanitize a string for safe use in filesystem paths.
  Replaces non-alphanumeric characters (except ._-) with underscores,
  and collapses multiple underscores into one."
  [s]
  (-> s
      (str/replace #"[^\p{Alnum}._-]+" "_")
      (str/replace #"_{2,}" "_")))

(defn sha1
  "Compute SHA-1 hash of a string, returning hex digest."
  [^String s]
  (let [md (java.security.MessageDigest/getInstance "SHA-1")]
    (.update md (.getBytes s))
    (format "%040x" (BigInteger. 1 (.digest md)))))

(defn sha256
  "Compute SHA-256 hex digest of a string."
  ^String [^String s]
  (let [md (java.security.MessageDigest/getInstance "SHA-256")]
    (.update md (.getBytes s))
    (format "%064x" (BigInteger. 1 (.digest md)))))

(defn project-root-path
  "Get the project root path (current working directory).
  Returns absolute normalized path."
  []
  (-> (System/getProperty "user.dir")
      fs/absolutize
      fs/normalize
      str))

(defn gpid-session-id
  "Get session identifier based on grandparent process ID.

  Returns a string in the format 'gpid-{pid}-{startInstant}' or 'gpid-{pid}'
  if start time is unavailable. Returns nil if grandparent process handle cannot
  be obtained or on any exception.

  Uses grandparent (parent of parent) to get a stable process ID that persists
  across multiple command invocations in Claude Code sessions."
  []
  (try
    (when-let [gph (some-> (java.lang.ProcessHandle/current)
                           .parent (.orElse nil)
                           .parent (.orElse nil))]
      (let [pid (.pid gph)
            start (some-> (.info gph) .startInstant (.orElse nil) str)]
        (str "gpid-" pid (when start (str "-" start)))))
    (catch Exception _
      nil)))

(defn editor-scope-id
  "Get editor session scope identifier with fallback strategy.

  Tries in order:
  1. Grandparent process ID with start time (gpid-{pid}-{startInstant})
  2. Literal string 'global' as last resort

  The GPID approach provides a stable identifier for the Claude Code session
  lifetime."
  []
  (or (gpid-session-id)
      "global"))

(defn get-possible-session-ids
  "Get all possible session IDs for cleanup purposes.

  Parameters:
  - :session-id - Optional explicit session ID (e.g., from hook input)
  - :gpid       - Optional grandparent process ID for fallback calculation

  Returns a vector of unique session IDs that might have been used during
  this session. This ensures cleanup works regardless of which ID was actually
  used during file operations.

  Example: [{:session-id \"abc123\"}] might return [\"abc123\" \"gpid-1234-...\"]"
  [{:keys [session-id gpid]}]
  (let [gpid-id (if gpid
                  (str "gpid-" gpid)
                  (gpid-session-id))]
    (->> [session-id gpid-id]
         (filter some?)
         distinct
         vec)))

;; ============================================================================
;; Unified Session/Project Root
;; ============================================================================

(defn session-root
  "Returns the unified root directory for this Claude Code session + project.

  Structure:
    {runtime-dir}/claude-code/{user}/{hostname}/{session-id}-proj-{hash}/

  Parameters:
  - :project-root - Optional project root path (defaults to current directory)
  - :session-id   - Optional session ID (defaults to editor-scope-id)

  The project is identified by SHA-1 hash of its absolute path for stability
  across different session invocations."
  [{:keys [project-root session-id]}]
  (let [runtime (runtime-base-dir)
        sess    (or session-id (editor-scope-id))
        proj    (or project-root (project-root-path))
        proj-id (sha1 proj)]
    (str (fs/path runtime
                  "clojure-mcp-light"
                  (str (sanitize sess) "-proj-" proj-id)))))

;; ============================================================================
;; Convenience Subpaths
;; ============================================================================

(defn backups-dir
  "Get the backups directory for this session/project context.
  Creates directory if it doesn't exist."
  [ctx]
  (str (fs/create-dirs (fs/path (session-root ctx) "backups"))))

(defn nrepl-dir
  "Get the nREPL directory for this session/project context.
  Creates directory if it doesn't exist."
  [ctx]
  (str (fs/create-dirs (fs/path (session-root ctx) "nrepl"))))

;; ============================================================================
;; Specific File Paths
;; ============================================================================

(defn nrepl-session-file
  "Get path to nREPL session file.
  This file stores the persistent nREPL session ID."
  [ctx]
  (str (fs/path (nrepl-dir ctx) "session.edn")))

(defn nrepl-target-file
  "Get path to nREPL session file for a specific target (host:port combination).
  Each host:port gets its own session file for independent session management."
  [ctx {:keys [host port]}]
  (let [hid (sanitize (or host "127.0.0.1"))
        pid (str port)]
    (str (fs/path (nrepl-dir ctx) (format "target-%s-%s.edn" hid pid)))))

(defn backup-path
  "Deterministic backup path: {backups-dir}/{h0}{h1}/{h2}{h3}/{hash}--{filename}

  - Hash is SHA-256 of the absolute, normalized file path.
  - Keeps the original filename for readability.
  - Uses a 2-level shard to avoid directory overload."
  [ctx ^String absolute-file]
  (let [abs-path (-> absolute-file fs/absolutize fs/normalize)
        abs    (str abs-path)
        h      (sha256 abs)
        fname  (or (fs/file-name abs-path) "unnamed")
        shard1 (subs h 0 2)
        shard2 (subs h 2 4)
        out    (str h "--" (sanitize fname))]
    (str (fs/path (backups-dir ctx) shard1 shard2 out))))

(defn list-nrepl-session-files
  "List all stored nREPL session files for the current context.

  Scans the nREPL directory for target-*.edn files and reads session data
  containing host, port, session ID, and env-type.

  Returns a vector of maps with keys:
  - :host       - Host string from session data
  - :port       - Port number from session data
  - :file-path  - Absolute path to session file
  - :session-id - Session ID string (or nil if file is empty/invalid)
  - :env-type   - Environment type (or nil if not available)

  Returns empty vector if:
  - nREPL directory doesn't exist
  - No session files found
  - Permission errors reading directory"
  [ctx]
  (try
    (let [nrepl-path (nrepl-dir ctx)]
      (if (fs/exists? nrepl-path)
        (->> (fs/list-dir nrepl-path)
             (filter #(re-matches #"target-.*\.edn" (str (fs/file-name %))))
             (keep (fn [file-path]
                     (try
                       (when (fs/exists? file-path)
                         (let [content (slurp (str file-path) :encoding "UTF-8")
                               session-data (when content (edn/read-string content))]
                           (when session-data
                             (assoc session-data :file-path (str file-path)))))
                       (catch Exception _
                         nil))))
             vec)
        []))
    (catch Exception _
      [])))

;; ============================================================================
;; Session Cleanup
;; ============================================================================

(defn cleanup-session!
  "Clean up temporary files for this Claude Code session.

  Attempts to delete session directories for all possible session IDs
  (both env-based and GPID-based) to ensure cleanup works regardless
  of which ID was actually used during the session.

  Parameters:
  - :session-id - Optional explicit session ID (e.g., from SessionEnd hook)
  - :gpid       - Optional grandparent process ID

  Returns a cleanup report map:
  - :attempted - List of session IDs for which cleanup was attempted
  - :deleted   - List of successfully deleted directory paths
  - :errors    - List of {:path path :error error-msg} maps for failures
  - :skipped   - List of paths that didn't exist (skipped silently)"
  [{:keys [session-id gpid]}]
  (let [session-ids (get-possible-session-ids {:session-id session-id :gpid gpid})
        results (atom {:attempted session-ids
                       :deleted []
                       :errors []
                       :skipped []})]
    (doseq [sess-id session-ids]
      (let [sess-dir (session-root {:session-id sess-id})]
        (try
          (if (fs/exists? sess-dir)
            (do
              (fs/delete-tree sess-dir)
              (swap! results update :deleted conj sess-dir))
            (swap! results update :skipped conj sess-dir))
          (catch Exception e
            (swap! results update :errors conj
                   {:path sess-dir
                    :error (.getMessage e)})))))
    @results))
