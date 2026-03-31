(babashka.deps/add-deps '{:deps {dev.weavejester/cljfmt {:mvn/version "0.15.5"}
                                 parinferish/parinferish {:mvn/version "0.8.0"}}})

(ns clojure-mcp-light.hook
  "Claude Code hook for delimiter error detection and repair"
  (:require [babashka.fs :as fs]
            [cheshire.core :as json]
            [cljfmt.core :as cljfmt]
            [cljfmt.main]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure-mcp-light.delimiter-repair
             :refer [delimiter-error? fix-delimiters actual-delimiter-error?]]
            [clojure-mcp-light.stats :as stats]
            [clojure-mcp-light.tmp :as tmp]
            [taoensso.timbre :as timbre]))

;; ============================================================================
;; Configuration
;; ============================================================================

(def ^:dynamic *enable-cljfmt* false)
(def ^:dynamic *enable-revert* true)

;; ============================================================================
;; CLI Options
;; ============================================================================

(def cli-options
  [[nil "--cljfmt" "Enable cljfmt formatting on files after edit/write"]
   [nil "--no-revert" "Disable automatic file revert on unfixable delimiter errors"
    :id :no-revert
    :default false]
   [nil "--stats" "Enable statistics tracking for delimiter events (default: ~/.clojure-mcp-light/stats.log)"
    :id :stats
    :default false]
   [nil "--stats-file PATH" "Path to stats file (only used when --stats is enabled)"
    :id :stats-file
    :default (str (fs/path (fs/home) ".clojure-mcp-light" "stats.log"))]
   [nil "--log-level LEVEL" "Set log level for file logging"
    :id :log-level
    :parse-fn keyword
    :validate [#{:trace :debug :info :warn :error :fatal :report}
               "Must be one of: trace, debug, info, warn, error, fatal, report"]]
   [nil "--log-file PATH" "Path to log file"
    :id :log-file
    :default "./.clojure-mcp-light-hooks.log"]
   ["-h" "--help" "Show help message"]])

(defn usage []
  (str "clj-paren-repair-claude-hook - Claude Code hook for Clojure delimiter repair\n"
       "\n"
       "Usage: clj-paren-repair-claude-hook [OPTIONS]\n"
       "\n"
       "Options:\n"
       "      --cljfmt              Enable cljfmt formatting on files after edit/write\n"
       "      --no-revert           Disable automatic file revert on unfixable delimiter errors\n"
       "      --stats               Enable statistics tracking for delimiter events\n"
       "                            (default: ~/.clojure-mcp-light/stats.log)\n"
       "      --stats-file PATH     Path to stats file (only used when --stats is enabled)\n"
       "      --log-level LEVEL     Set log level for file logging\n"
       "                            Levels: trace, debug, info, warn, error, fatal, report\n"
       "      --log-file PATH       Path to log file (default: ./.clojure-mcp-light-hooks.log)\n"
       "  -h, --help                Show this help message"))

(defn error-msg [errors]
  (str "The following errors occurred while parsing command:\n\n"
       (string/join \newline errors)))

(defn handle-cli-args
  "Parse CLI arguments and handle help/errors. Returns options map or exits."
  [args]
  (let [actual-args (if (seq args) args *command-line-args*)
        {:keys [options errors]} (parse-opts actual-args cli-options)]
    (cond
      (:help options)
      (do
        (println (usage))
        (System/exit 0))

      errors
      (do
        (binding [*out* *err*]
          (println (error-msg errors))
          (println)
          (println (usage)))
        (System/exit 1))

      :else
      options)))

;; ============================================================================
;; Claude Code Hook Functions
;; ============================================================================

(defn- babashka-shebang?
  "Checks if a file starts with a Babashka shebang.
   Returns true if the first line matches a Babashka shebang pattern."
  [file-path]
  (when (fs/exists? file-path)
    (try
      (with-open [r (io/reader file-path)]
        (let [line (-> r line-seq first)]
          (and line
               (re-matches #"^#!/[^\s]+/(bb|env\s{1,3}bb)(\s.*)?$" line))))
      (catch Exception _ false))))

(defn clojure-file?
  "Checks if a file path has a Clojure-related extension or Babashka shebang.

   Supported extensions:
   - .clj (Clojure)
   - .cljs (ClojureScript)
   - .cljc (Clojure/ClojureScript shared)
   - .cljd (ClojureDart)
   - .bb (Babashka)
   - .edn (Extensible Data Notation)
   - .lpy (Basilisp)

   Also detects files starting with a Babashka shebang (`bb`)."
  [file-path]
  (when file-path
    (let [lower-path (string/lower-case file-path)]
      (or (string/ends-with? lower-path ".clj")
          (string/ends-with? lower-path ".cljs")
          (string/ends-with? lower-path ".cljc")
          (string/ends-with? lower-path ".cljd")
          (string/ends-with? lower-path ".bb")
          (string/ends-with? lower-path ".lpy")
          (string/ends-with? lower-path ".edn")
          (babashka-shebang? file-path)))))

(defn run-cljfmt
  "Check if file needs formatting using cljfmt.core, then format with cljfmt.main.
  This avoids shell spawn for check while respecting user's cljfmt config for formatting.
  Returns true if file was formatted, false otherwise."
  [file-path]
  (when *enable-cljfmt*
    (stats/log-stats! :cljfmt-run {:file-path file-path})
    (try
      (let [original (slurp file-path :encoding "UTF-8")
            formatted (cljfmt/reformat-string original)]
        (if (not= original formatted)
          (do
            (stats/log-stats! :cljfmt-needed-formatting {:file-path file-path})
            (timbre/debug "Running cljfmt fix on:" file-path)
            ;; Use cljfmt.main to respect user's environment config
            (cljfmt.main/-main "fix" file-path)
            (stats/log-stats! :cljfmt-fix-succeeded {:file-path file-path})
            (timbre/debug "  cljfmt succeeded")
            true)
          (do
            (stats/log-stats! :cljfmt-already-formatted {:file-path file-path})
            (timbre/debug "  No formatting needed")
            false)))
      (catch Exception e
        (stats/log-stats! :cljfmt-fix-failed {:file-path file-path
                                              :ex-message (ex-message e)})
        (timbre/debug "  cljfmt error:" (.getMessage e))
        false))))

(defn backup-file
  "Backup file to temp location, returns backup path"
  [file-path session-id]
  (let [ctx {:session-id session-id}
        backup (tmp/backup-path ctx file-path)
        content (slurp file-path :encoding "UTF-8")]
    ;; Ensure parent directories exist
    (when-let [parent (fs/parent backup)]
      (fs/create-dirs parent))
    (spit backup content :encoding "UTF-8")
    backup))

(defn restore-file
  "Restore file from backup and delete backup"
  [file-path backup-path]
  (when (fs/exists? backup-path)
    (try
      (let [backup-content (slurp backup-path :encoding "UTF-8")]
        (spit file-path backup-content :encoding "UTF-8")
        true)
      (finally
        (io/delete-file backup-path)))))

(defn delete-backup
  "Delete backup file if it exists"
  [backup-path]
  (fs/delete-if-exists backup-path))

(defn fix-and-format-file!
  "Core logic for fixing delimiters and formatting a Clojure file in-place.
   This is the shared implementation used by both the hook and standalone tools.

   Parameters:
   - file-path: path to the file to process
   - enable-cljfmt: boolean to enable cljfmt formatting after delimiter fix
   - stats-event-prefix: string prefix for stats events (e.g., 'PostToolUse:Edit' or 'paren-repair')

   Returns map with:
   - :success - true if file was processed successfully (no unfixable errors)
   - :delimiter-fixed - true if a delimiter error was detected and fixed
   - :formatted - true if file was formatted with cljfmt
   - :message - human-readable message describing what happened"
  [file-path enable-cljfmt stats-event-prefix]
  (try
    (let [file-content (slurp file-path :encoding "UTF-8")
          has-delimiter-error? (delimiter-error? file-content)
          actual-error? (when has-delimiter-error?
                          (actual-delimiter-error? file-content))]

      (when (and has-delimiter-error? actual-error?)
        (stats/log-event! :delimiter-error stats-event-prefix file-path))

      (timbre/debug "  Delimiter error:" has-delimiter-error?)

      (if has-delimiter-error?
        ;; Has delimiter error - try to fix
        (do
          (timbre/debug "  Delimiter error detected, attempting fix")
          (if-let [fixed-content (fix-delimiters file-content)]
            (do
              (when actual-error?
                (stats/log-event! :delimiter-fixed stats-event-prefix file-path))
              (timbre/debug "  Fix successful, applying fix")
              (spit file-path fixed-content :encoding "UTF-8")
              (let [formatted? (when enable-cljfmt
                                 (run-cljfmt file-path))]
                {:success true
                 :delimiter-fixed true
                 :formatted (boolean formatted?)
                 :message "Delimiter errors fixed and formatted"}))
            (do
              (when actual-error?
                (stats/log-event! :delimiter-fix-failed stats-event-prefix file-path))
              (timbre/error "  Delimiter fix failed")
              {:success false
               :delimiter-fixed false
               :formatted false
               :message "Could not fix delimiter errors"})))
        ;; No delimiter error - just format if enabled
        (do
          (stats/log-event! :delimiter-ok stats-event-prefix file-path)
          (timbre/debug "  No delimiter errors")
          (let [formatted? (when enable-cljfmt
                             (run-cljfmt file-path))]
            {:success true
             :delimiter-fixed false
             :formatted (boolean formatted?)
             :message (if formatted? "Formatted" "No changes needed")}))))
    (catch Exception e
      (timbre/error "  Unexpected error processing file:" (.getMessage e))
      {:success false
       :delimiter-fixed false
       :formatted false
       :message (str "Error: " (.getMessage e))})))

(defn process-pre-write
  "Process content before write operation.
  Returns fixed content if Clojure file has delimiter errors, nil otherwise."
  [file-path content]
  (when (and (clojure-file? file-path) (delimiter-error? content))
    (fix-delimiters content)))

(defn process-pre-edit
  "Process file before edit operation.
  Creates a backup of Clojure files, returns backup path if created."
  [file-path session-id]
  (when (clojure-file? file-path)
    (backup-file file-path session-id)))

(defn process-post-edit
  "Process file after edit operation.
  Compares edited file with backup, fixes delimiters if content changed,
  and cleans up backup file."
  [file-path session-id]
  (when (clojure-file? file-path)
    (let [ctx {:session-id session-id}
          backup-file (tmp/backup-path ctx file-path)]
      (try
        (let [backup-content (try (slurp backup-file :encoding "UTF-8") (catch Exception _ nil))
              file-content (slurp file-path :encoding "UTF-8")]
          (when (not= backup-content file-content)
            (process-pre-write file-path file-content)))
        (finally
          (delete-backup backup-file))))))

(defmulti process-hook
  (fn [hook-input]
    [(:hook_event_name hook-input) (:tool_name hook-input)]))

(defmethod process-hook :default [_] nil)

(defmethod process-hook ["PreToolUse" "Write"]
  [{:keys [tool_input]}]
  (let [{:keys [file_path content]} tool_input]
    (when (clojure-file? file_path)
      (timbre/debug "PreWrite: clojure" file_path)
      (if (delimiter-error? content)
        (let [actual-error? (actual-delimiter-error? content)]
          (when actual-error?
            (stats/log-event! :delimiter-error "PreToolUse:Write" file_path))
          (timbre/debug "  Delimiter error detected, attempting fix")
          (if-let [fixed-content (fix-delimiters content)]
            (do
              (when actual-error?
                (stats/log-event! :delimiter-fixed "PreToolUse:Write" file_path))
              (timbre/debug "  Fix successful, allowing write with updated content")
              {:hookSpecificOutput
               {:hookEventName "PreToolUse"
                :permissionDecision "allow"
                :updatedInput {:file_path file_path
                               :content fixed-content}}})
            (do
              (when actual-error?
                (stats/log-event! :delimiter-fix-failed "PreToolUse:Write" file_path))
              (timbre/debug "  Fix failed, denying write")
              {:hookSpecificOutput
               {:hookEventName "PreToolUse"
                :permissionDecision "deny"
                :permissionDecisionReason "Delimiter errors found and could not be auto-fixed"}})))
        (do
          (stats/log-event! :delimiter-ok "PreToolUse:Write" file_path)
          (timbre/debug "  No delimiter errors, allowing write")
          nil)))))

(defmethod process-hook ["PreToolUse" "Edit"]
  [{:keys [tool_input session_id]}]
  (let [{:keys [file_path]} tool_input]
    (when (clojure-file? file_path)
      (timbre/debug "PreEdit: clojure" file_path)

      ;; Only create backup if revert is enabled
      (when *enable-revert*
        (try
          (let [backup (backup-file file_path session_id)]
            (timbre/debug "  Created backup:" backup)
            nil)
          (catch Exception e
            (timbre/debug "  Edit processing failed:" (.getMessage e))
            nil))))))

(defmethod process-hook ["PostToolUse" "Write"]
  [{:keys [tool_input tool_response]}]
  (let [{:keys [file_path]} tool_input]
    (when (and (clojure-file? file_path) tool_response *enable-cljfmt*)
      (timbre/debug "PostWrite: clojure cljfmt" file_path)
      (run-cljfmt file_path)
      nil)))

(defmethod process-hook ["PostToolUse" "Edit"]
  [{:keys [tool_input tool_response session_id]}]
  (let [{:keys [file_path]} tool_input]
    (when (and (clojure-file? file_path) tool_response)
      (timbre/debug "PostEdit: clojure" file_path)
      (let [backup (tmp/backup-path {:session-id session_id} file_path)
            backup-exists? (fs/exists? backup)
            result (fix-and-format-file! file_path *enable-cljfmt* "PostToolUse:Edit")]

        (try
          (if (:success result)
            ;; Success - delete backup and return nil
            (do
              (timbre/debug "  Processing successful, deleting backup")
              nil)
            ;; Failure - handle backup restore based on revert setting
            (if (and *enable-revert* backup-exists?)
              (do
                (timbre/debug "  Fix failed, restoring from backup:" backup)
                (restore-file file_path backup)
                {:decision "block"
                 :reason (str "Delimiter errors could not be auto-fixed. File was restored from backup to previous state: " file_path)
                 :hookSpecificOutput
                 {:hookEventName "PostToolUse"
                  :additionalContext "There are delimiter errors in the file. So we restored from backup."}})
              (do
                (timbre/debug "  Fix failed, revert disabled - blocking without restore")
                {:decision "block"
                 :reason (str "Delimiter errors could not be auto-fixed in file: " file_path)
                 :hookSpecificOutput
                 {:hookEventName "PostToolUse"
                  :additionalContext "There are delimiter errors in the file. Revert is disabled, so the file was not restored."}})))
          (finally
            (when backup-exists?
              (delete-backup backup))))))))

(defmethod process-hook ["PreToolUse" "mcp__morph-mcp__edit_file"]
  [input]
  (let [path (get-in input [:tool_input :path])]
    (process-hook (-> input
                      (assoc :tool_name "Edit")
                      (assoc-in [:tool_input :file_path] path)))))

(defmethod process-hook ["PostToolUse" "mcp__morph-mcp__edit_file"]
  [input]
  (let [path (get-in input [:tool_input :path])]
    (process-hook (-> input
                      (assoc :tool_name "Edit")
                      (assoc-in [:tool_input :file_path] path)))))

(defmethod process-hook ["SessionEnd" nil]
  [{:keys [session_id]}]
  (timbre/info "SessionEnd: cleaning up session" session_id)
  (try
    (let [report (tmp/cleanup-session! {:session-id session_id})]
      (timbre/info "  Cleanup attempted for session IDs:" (:attempted report))
      (timbre/info "  Deleted directories:" (:deleted report))
      (timbre/info "  Skipped (non-existent):" (:skipped report))
      (when (seq (:errors report))
        (timbre/warn "  Errors during cleanup:")
        (doseq [{:keys [path error]} (:errors report)]
          (timbre/warn "    " path "-" error)))
      nil)
    (catch Exception e
      (timbre/error "  Unexpected error during cleanup:" (.getMessage e))
      nil)))

(defn -main [& args]
  (let [options (handle-cli-args args)
        log-level (:log-level options)
        log-file (:log-file options)
        enable-logging? (some? log-level)
        enable-stats? (:stats options)
        stats-path (stats/normalize-stats-path (:stats-file options))]

    (timbre/set-config!
     {:appenders {:spit (assoc
                         (timbre/spit-appender {:fname log-file})
                         :enabled? enable-logging?
                         :min-level (or log-level :report)
                         :ns-filter (if enable-logging?
                                      {:allow "clojure-mcp-light.*"}
                                      {:deny "*"}))}})

    ;; Set cljfmt, revert, and stats flags from CLI options
    (binding [*enable-cljfmt* (:cljfmt options)
              *enable-revert* (not (:no-revert options))
              stats/*enable-stats* enable-stats?
              stats/*stats-file-path* stats-path]
      (try
        (let [input-json (slurp *in*)
              _ (timbre/debug "INPUT:" input-json)
              _ (when *enable-cljfmt*
                  (timbre/debug "cljfmt formatting is ENABLED"))
              _ (when stats/*enable-stats*
                  (timbre/debug "stats tracking is ENABLED, writing to:" stats/*stats-file-path*))
              hook-input (json/parse-string input-json true)
              response (process-hook hook-input)
              _ (timbre/debug "OUTPUT:" (json/generate-string response))]
          (when response
            (println (json/generate-string response)))
          (System/exit 0))
        (catch Exception e
          (timbre/error "Hook error:" (.getMessage e))
          (timbre/error "Stack trace:" (with-out-str (.printStackTrace e)))
          (binding [*out* *err*]
            (println "Hook error:" (.getMessage e))
            (println "Stack trace:" (with-out-str (.printStackTrace e))))
          (System/exit 2))))))
