(babashka.deps/add-deps '{:deps {parinferish/parinferish {:mvn/version "0.8.0"}}})

(ns clojure-mcp-light.nrepl-eval
  "nREPL client implementation with automatic delimiter repair and timeout handling"
  (:require [babashka.fs :as fs]
            [clojure.string :as str]
            [clojure.tools.cli :refer [parse-opts]]
            [clojure-mcp-light.delimiter-repair :refer [fix-delimiters]]
            [clojure-mcp-light.nrepl-client :as nc]
            [clojure-mcp-light.tmp :as tmp]))

;; ============================================================================
;; High-level nREPL operations (using nrepl-client)
;; ============================================================================

(defn validate-stored-connection
  "Validate a stored nREPL connection by checking if server is reachable
   and session is still active.

   Parameters:
   - :host       - Host string
   - :port       - Port number
   - :session-id - Session ID to validate
   - :env-type   - Environment type (optional)

   Returns map with :status key:
   - {:status :active :host ... :port ... :session-id ... :env-type ...} if connection is valid
   - {:status :invalid :host ... :port ... :reason ...} if connection failed

   Does not clean up stale session files to preserve session persistence across
   server restarts. Use --reset-session to explicitly clean up sessions."
  [{:keys [host port session-id env-type]}]
  (try
    (if-let [active-sessions (nc/ls-sessions host port)]
      (if (some #{session-id} active-sessions)
        {:status :active
         :host host
         :port port
         :session-id session-id
         :env-type env-type}
        {:status :invalid
         :host host
         :port port
         :reason "Session expired"})
      {:status :invalid
       :host host
       :port port
       :reason "Server unreachable"})
    (catch Exception e
      {:status :invalid
       :host host
       :port port
       :reason (.getMessage e)})))

(defn list-connected-ports
  "List all active nREPL connections from stored session files.

   Returns a vector of maps with:
   - :host       - Host string
   - :port       - Port number
   - :session-id - Active session ID
   - :env-type   - Environment type (if available)
   - :status     - :active

   Only includes connections that are currently active and reachable.
   Automatically cleans up stale session files."
  []
  (let [ctx {}
        session-files (tmp/list-nrepl-session-files ctx)]
    (->> session-files
         (map validate-stored-connection)
         (filter #(= :active (:status %)))
         vec)))

;; Port discovery

(defn detect-nrepl-env-type
  "Detect nREPL environment type from describe response.
   Returns :clj, :bb, :basilisp, :scittle, :shadow, or :unknown."
  [describe-response]
  (when-let [versions (:versions describe-response)]
    (cond
      (or (get versions :clojure) (get versions "clojure")) :clj
      (or (get versions :babashka) (get versions "babashka")) :bb
      (or (get versions :basilisp) (get versions "basilisp")) :basilisp
      (or (get versions :sci-nrepl) (get versions "sci-nrepl")) :scittle
      :else :unknown)))

(defn detect-shadow-cljs?*
  "Check if nREPL server is running shadow-cljs using an existing connection.
   Returns true if evaluating code without a session results in :ns \"shadow.user\", false otherwise."
  [conn]
  (try
    (let [response (nc/eval-nrepl* conn "1")]
      (= "shadow.user" (:ns response)))
    (catch Exception _
      false)))

(defmulti fetch-project-directory-exp
  "Returns an expression (string) to evaluate for getting the project directory.
   Dispatches on nrepl-env-type."
  (fn [nrepl-env-type] nrepl-env-type))

(defmethod fetch-project-directory-exp :clj
  [_]
  "(System/getProperty \"user.dir\")")

(defmethod fetch-project-directory-exp :bb
  [_]
  "(System/getProperty \"user.dir\")")

(defmethod fetch-project-directory-exp :basilisp
  [_]
  "(import os)\n(os/getcwd)")

(defmethod fetch-project-directory-exp :shadow
  [_]
  "(System/getProperty \"user.dir\")")

(defmethod fetch-project-directory-exp :default
  [_]
  nil)

(defn read-nrepl-port-file
  "Read port number from .nrepl-port file in current directory.
   Returns nil if file doesn't exist or on error."
  []
  (try
    (when (fs/exists? ".nrepl-port")
      (parse-long (str/trim (slurp ".nrepl-port" :encoding "UTF-8"))))
    (catch Exception _
      nil)))

(defn parse-lsof-ports
  "Parse port numbers from lsof output.
   Matches patterns like: 'TCP *:7888 (LISTEN)' or 'TCP 127.0.0.1:7889 (LISTEN)'"
  [lsof-output]
  (when lsof-output
    (->> (str/split-lines lsof-output)
         (keep (fn [line]
                 (when-let [[_ port] (re-find #"TCP\s+(?:\*|[\d.]+):(\d+)\s+\(LISTEN\)" line)]
                   (parse-long port))))
         distinct
         vec)))

(defn get-listening-jvm-ports
  "Find ports where Java/Clojure/Babashka processes are listening.
   Returns vector of port numbers, empty vector on error."
  []
  (try
    (let [proc (.. (ProcessBuilder. ["sh" "-c" "lsof -nP -iTCP -sTCP:LISTEN 2>/dev/null | grep -Ei 'java|clojure|babashka|bb|nrepl'"])
                   (redirectErrorStream true)
                   start)
          _ (.waitFor proc 5 java.util.concurrent.TimeUnit/SECONDS)
          output (slurp (.getInputStream proc))]
      (or (parse-lsof-ports output) []))
    (catch Exception _
      [])))

(defn gather-port-info
  "Gather information about a nREPL port using a single connection.
   Returns map with port info or nil if connection fails."
  [host port source current-dir]
  (try
    (nc/with-socket host port 250
      (fn [socket out in]
        (let [conn (nc/make-connection socket out in host port)
              sessions-resp (nc/ls-sessions* conn)
              sessions (:sessions sessions-resp)]
          (if sessions
            ;; Valid nREPL - get env type and project dir
            (let [describe-resp (nc/describe-nrepl* conn)
                  base-env-type (detect-nrepl-env-type describe-resp)
                  ;; Check for shadow-cljs (overrides base env-type)
                  is-shadow? (detect-shadow-cljs?* conn)
                  env-type (if is-shadow? :shadow base-env-type)
                  dir-expr (fetch-project-directory-exp env-type)
                  project-dir (when dir-expr
                                (last (:value (nc/eval-nrepl* conn dir-expr))))
                  ;; Strip quotes if present (e.g., "\"/path\"" -> "/path")
                  project-dir (when project-dir
                                (str/replace project-dir #"^\"|\"$" ""))
                  matches-cwd (and project-dir
                                   (= project-dir current-dir))]
              {:host host
               :port port
               :source source
               :valid true
               :session-count (count sessions)
               :env-type env-type
               :project-dir project-dir
               :matches-cwd matches-cwd})
            ;; Invalid nREPL
            {:host host
             :port port
             :source source
             :valid false
             :session-count 0
             :env-type nil
             :project-dir nil
             :matches-cwd false}))))
    (catch Exception _
      ;; Connection failed
      {:host host
       :port port
       :source source
       :valid false
       :session-count 0
       :env-type nil
       :project-dir nil
       :matches-cwd false})))

(defn discover-nrepl-ports
  "Discover potential nREPL ports by:
   1. Checking .nrepl-port file in current directory
   2. Finding Java/Clojure/Babashka processes listening on TCP ports (lsof)
   3. Validating discovered ports by checking if they respond to ls-sessions
   4. Detecting environment type (clj, bb, basilisp, shadow, etc.)
   5. Getting project directory and checking if it matches current working directory

   Returns vector of maps with:
   - :host - Host string (always \"localhost\")
   - :port - Port number
   - :source - How port was discovered (:nrepl-port-file or :lsof)
   - :valid - Boolean indicating if port responds to nREPL ls-sessions op
   - :env-type - Environment type (:clj, :bb, :basilisp, :scittle, :shadow, :unknown, or nil if invalid)
   - :project-dir - Project directory path from nREPL server (or nil)
   - :matches-cwd - Boolean indicating if project-dir matches current working directory"
  []
  (let [;; Collect port candidates
        port-file-port (read-nrepl-port-file)
        lsof-ports (get-listening-jvm-ports)
        current-dir (System/getProperty "user.dir")

        ;; Combine and deduplicate
        all-ports (distinct (concat (when port-file-port [port-file-port])
                                    lsof-ports))

        ;; Validate each port and gather info in parallel using a single connection per port
        results (pmap (fn [port]
                        (let [source (if (= port port-file-port) :nrepl-port-file :lsof)]
                          (gather-port-info "localhost" port source current-dir)))
                      all-ports)]
    (vec results)))

(defn detect-cljs-mode
  "Detect if the nREPL session is in CLJS mode by evaluating *clojurescript-version*.
   Returns true if in CLJS mode, false if in CLJ mode.
   Must use the same session to get accurate mode detection."
  [conn]
  (try
    (let [response (nc/eval-nrepl* conn "cljs.user/*clojurescript-version*")]
      (seq (:value response)))
    (catch Exception _
      false)))

(defn format-divider
  "Format the output divider with namespace, env-type, and mode information.
   For shadow-cljs, always shows cljs-mode or clj-mode.
   For other env-types, omits mode indicator."
  [ns-str env-type]
  (str "*======== " ns-str " | " (name env-type) " ========*"))

(defn ensure-session
  "Ensure we have a valid session for the given connection.
  Returns session data map with :session-id and :env-type.
  Reuses existing session if valid, creates new one if needed.

  Takes a connection map with :input, :output, :host, :port."
  [conn]
  (let [{:keys [host port]} conn
        existing-data (nc/slurp-nrepl-session host port)
        existing-id (:session-id existing-data)
        ;; Validate existing session by checking if it's in the active sessions list
        sessions-resp (when existing-id (nc/ls-sessions* conn))
        session-valid? (and existing-id
                            sessions-resp
                            (some #{existing-id} (:sessions sessions-resp)))]
    ;; Validate existing session
    (if session-valid?
      ;; Session still valid, return existing data
      existing-data
      ;; Need new session
      (do
        ;; Clean up old session file
        (when existing-id
          (nc/delete-nrepl-session host port))
        ;; Create new session using connection API
        (let [clone-resp (nc/clone-session* conn)
              new-session (:new-session clone-resp)
              ;; Detect env-type if not already stored
              env-type (or (:env-type existing-data)
                           (let [describe-resp (nc/describe-nrepl* conn)
                                 base-env (detect-nrepl-env-type describe-resp)
                                 ;; Check for shadow-cljs
                                 eval-resp (nc/eval-nrepl* conn "1")
                                 is-shadow? (= "shadow.user" (:ns eval-resp))]
                             (if is-shadow? :shadow base-env)))
              session-data {:session-id new-session
                            :env-type env-type}]
          (nc/spit-nrepl-session session-data host port)
          session-data)))))

(defn output-messages
  "Process nREPL messages by printing output, errors, and values.
   Returns accumulated values from :value fields."
  [messages env-type cljs-mode?]
  (when (= env-type :shadow)
    (println
     (if cljs-mode?
       ";; shadow-cljs repl is in CLJS mode"
       ";; shadow-cljs repl is NOT in CLJS mode
;; use (shadow/active-builds) to list builds available
;; use (shadow/repl <build-id>) to jack into a CLJS repl")))
  (doseq [msg messages]
    (when-let [out-str (:out msg)]
      (print out-str)
      (flush))
    ;; Print errors
    (when-let [err-str (:err msg)]
      (binding [*out* *err*]
        (print err-str)
        (flush)))
    ;; Print values with divider
    (when-let [value (:value msg)]
      (println (str "=> " value))
      (println (format-divider (:ns msg) env-type))
      (flush))))

(defn eval-expr-with-timeout
  "Evaluate expression with timeout support and interrupt handling.
  If timeout-ms is exceeded, sends an interrupt to the nREPL server.

  Uses persistent sessions: reuses existing session ID from per-target session file
  or creates a new one if none exists. Session persists across invocations."
  [{:keys [host port expr timeout-ms] :or {timeout-ms 120000}}]
  (let [fixed-expr (or (fix-delimiters expr) expr)
        host (or host "localhost")]
    (nc/with-socket host (nc/coerce-long port) timeout-ms
      (fn [socket out in]
        (let [conn (nc/make-connection socket out in host port)
              session-data (ensure-session conn)
              session-id (:session-id session-data)
              env-type (or (:env-type session-data) :unknown)
              conn-with-session (assoc conn :session-id session-id)
              ;; Detect CLJS mode only for shadow-cljs environments
              cljs-mode? (when (= env-type :shadow)
                           (detect-cljs-mode conn-with-session))
              eval-id (nc/next-id)]
          (try
            ;; Evaluate expression using messages-for-id
            (let [messages (nc/messages-for-id
                            conn-with-session
                            {"op" "eval"
                             "code" fixed-expr
                             "id" eval-id})]
              (output-messages messages env-type cljs-mode?))
            (catch java.net.SocketTimeoutException _
              (println "\n⚠️  Timeout hit, sending nREPL :interrupt …")
              (nc/write-bencode-msg out {"op" "interrupt"
                                         "session" session-id
                                         "interrupt-id" eval-id})
              (println "✋ Evaluation interrupted."))))))))

;; ============================================================================
;; Command-line interface
;; ============================================================================

(def cli-options
  [["-p" "--port PORT" "nREPL port (required)"
    :parse-fn parse-long
    :validate [#(> % 0) "Must be a positive number"]]
   ["-H" "--host HOST" "nREPL host (default: 127.0.0.1)"]
   ["-t" "--timeout MILLISECONDS" "Timeout in milliseconds (default: 120000)"
    :default 120000
    :parse-fn parse-long
    :validate [#(> % 0) "Must be a positive number"]]
   ["-r" "--reset-session" "Reset the persistent nREPL session"]
   ["-c" "--connected-ports" "List all active nREPL connections"]
   ["-d" "--discover-ports" "Discover nREPL servers in current directory"]
   ["-h" "--help" "Show this help message"]])

(defn has-stdin-data?
  "Check if stdin has data available (not a TTY).
  Returns true if stdin is ready to be read (e.g., piped input or heredoc)."
  []
  (try
    (.ready *in*)
    (catch Exception _ false)))

(defn get-code
  "Get code from command-line arguments or stdin.
  Arguments take precedence over stdin.
  Returns nil if no code is available from either source."
  [arguments]
  (cond
    (seq arguments) (first arguments)
    (has-stdin-data?) (slurp *in*)
    :else nil))

(defn usage [options-summary]
  (str/join \newline
            ["clj-nrepl-eval - Evaluate Clojure code via nREPL"
             ""
             "Usage: clj-nrepl-eval --port PORT CODE"
             "       clj-nrepl-eval --port PORT --reset-session [CODE]"
             "       clj-nrepl-eval --connected-ports"
             "       clj-nrepl-eval --discover-ports"
             "       echo CODE | clj-nrepl-eval --port PORT"
             "       clj-nrepl-eval --port PORT <<'EOF' ... EOF"
             ""
             "Options:"
             options-summary
             ""
             "Session Persistence:"
             "  Sessions are persistent by default. Each host:port combination has its own"
             "  session file. State (vars, namespaces, loaded libraries) persists across"
             "  invocations until the nREPL server restarts. --reset-session only resets"
             "  the nREPL session (clearing *e, *1, etc.), not def'd vars or namespaces."
             ""
             "Input Methods:"
             "  Prefer heredoc via stdin (<<'EOF' ... EOF) to avoid shell escaping issues."
             "  Code can also be provided as a command-line argument."
             "  Arguments take precedence over stdin when both are provided."
             ""
             "Workflow:"
             "  1. Use --discover-ports to find nREPL servers in current directory"
             "  2. Use --connected-ports to see previously connected servers"
             "  3. Use --port to connect to a specific server"
             ""
             "Examples:"
             "  # Discover nREPL servers in current directory"
             "  # (scans .nrepl-port file and running JVM/Babashka processes)"
             "  clj-nrepl-eval --discover-ports"
             ""
             "  # List previously connected servers"
             "  clj-nrepl-eval --connected-ports"
             ""
             "  # Evaluate code (argument)"
             "  clj-nrepl-eval -p 7888 \"(+ 1 2 3)\""
             "  clj-nrepl-eval --port 7888 \"(println \\\"Hello\\\")\""
             ""
             "  # Evaluate code (stdin pipe)"
             "  echo \"(+ 1 2 3)\" | clj-nrepl-eval -p 7888"
             ""
             "  # Evaluate code (heredoc)"
             "  clj-nrepl-eval -p 7888 <<'EOF'"
             "  (def x 10)"
             "  (+ x 20)"
             "  EOF"
             ""
             "  # With timeout"
             "  clj-nrepl-eval -p 7888 --timeout 5000 \"(Thread/sleep 10000)\""
             ""
             "  # Reset session"
             "  clj-nrepl-eval -p 7888 --reset-session"
             "  clj-nrepl-eval -p 7888 --reset-session \"(def x 1)\""]))

(defn error-msg [errors]
  (str "Error parsing command line:\n\n"
       (str/join \newline errors)))

(defn get-host
  "Get host from options, defaulting to 127.0.0.1"
  [opts]
  (or (:host opts) "127.0.0.1"))

(defn handle-connected-ports
  "Handler for --connected-ports flag.
   Lists all previously connected nREPL servers from session files."
  []
  (let [connections (list-connected-ports)]
    (if (empty? connections)
      (println "No active nREPL connections found.")
      (do
        (println "Active nREPL connections:")
        (doseq [{:keys [host port session-id env-type]} connections]
          (println (format "  %s:%d (%s) (session: %s)"
                           host
                           port
                           (name (or env-type :unknown))
                           session-id)))
        (println)
        (println (format "Total: %d active connection%s"
                         (count connections)
                         (if (= 1 (count connections)) "" "s")))))))

(defn handle-discover-ports
  "Handler for --discover-ports flag.
   Discovers and lists all nREPL servers, grouped by directory."
  []
  (let [discovered (discover-nrepl-ports)
        valid-servers (filter :valid discovered)
        current-dir (System/getProperty "user.dir")
        current-dir-servers (filter :matches-cwd valid-servers)
        other-dir-servers (remove :matches-cwd valid-servers)]
    (if (empty? valid-servers)
      (println "No nREPL servers found.")
      (do
        (println "Discovered nREPL servers:")
        (println)

        ;; Show servers in current directory
        (when (seq current-dir-servers)
          (println (format "In current directory (%s):" current-dir))
          (doseq [{:keys [host port env-type]} current-dir-servers]
            (println (format "  %s:%d (%s)"
                             host
                             port
                             (name (or env-type :unknown)))))
          (println))

        ;; Show servers in other directories
        (when (seq other-dir-servers)
          (println "In other directories:")
          (doseq [{:keys [host port env-type project-dir]} other-dir-servers]
            (println (format "  %s:%d (%s) - %s"
                             host
                             port
                             (name (or env-type :unknown))
                             (or project-dir "unknown"))))
          (println))

        ;; Show summary
        (let [current-count (count current-dir-servers)
              other-count (count other-dir-servers)
              total-count (count valid-servers)]
          (println (format "Total: %d server%s%s"
                           total-count
                           (if (= 1 total-count) "" "s")
                           (if (and (pos? current-count) (pos? other-count))
                             (format " (%d in current directory, %d in other directories)"
                                     current-count
                                     other-count)
                             ""))))))))

(defn handle-reset-session
  "Handler for --reset-session flag.
   Resets the persistent nREPL session and optionally evaluates code."
  [options arguments]
  (if-let [port (:port options)]
    (let [host (get-host options)]
      (nc/delete-nrepl-session host port)
      (println (str "Session reset for " host ":" port))
      ;; If code is provided, continue to evaluate it with new session
      (when-let [expr (get-code arguments)]
        (eval-expr-with-timeout (cond-> {:host host
                                         :port port
                                         :expr expr}
                                  (:timeout options)
                                  (assoc :timeout-ms (:timeout options))))))
    (do
      (binding [*out* *err*]
        (println "Error: --port is required for --reset-session")
        (println "Use --connected-ports to see available connections"))
      (System/exit 1))))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]} (parse-opts args cli-options)]
    (cond
      (:help options)
      (println (usage summary))

      errors
      (do
        (binding [*out* *err*]
          (println (error-msg errors))
          (println)
          (println (usage summary)))
        (System/exit 1))

      ;; Handle --connected-ports flag
      (:connected-ports options)
      (handle-connected-ports)

      ;; Handle --discover-ports flag
      (:discover-ports options)
      (handle-discover-ports)

      ;; Handle --reset-session flag
      (:reset-session options)
      (handle-reset-session options arguments)

      :else
      (let [code (get-code arguments)]
        (if-not code
          (do
            (binding [*out* *err*]
              (println "Error: No code provided")
              (println "Provide code as an argument or via stdin (pipe/heredoc)")
              (println)
              (println (usage summary)))
            (System/exit 1))
          (if-let [port (:port options)]
            (eval-expr-with-timeout (cond-> {:host (get-host options)
                                             :port port
                                             :expr code}
                                      (:timeout options)
                                      (assoc :timeout-ms (:timeout options))))
            (do
              (binding [*out* *err*]
                (println "Error: --port is required")
                (println "Use --connected-ports to see available connections"))
              (System/exit 1))))))))
