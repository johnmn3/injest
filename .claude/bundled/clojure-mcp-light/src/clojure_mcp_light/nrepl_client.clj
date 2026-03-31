(ns clojure-mcp-light.nrepl-client
  "nREPL client library based on lazy sequences of messages"
  (:require [babashka.fs :as fs]
            [bencode.core :as b]
            [clojure.edn :as edn]
            [clojure-mcp-light.tmp :as tmp]))

;; ============================================================================
;; Message encoding/decoding
;; ============================================================================

(defmulti bytes->str
  "Recursively convert byte arrays to strings in nested structures"
  class)

(defmethod bytes->str :default
  [x]
  x)

(defmethod bytes->str (Class/forName "[B")
  [^bytes x]
  (String. x "UTF-8"))

(defmethod bytes->str clojure.lang.IPersistentVector
  [v]
  (mapv bytes->str v))

(defmethod bytes->str clojure.lang.IPersistentMap
  [m]
  (->> m
       (map (fn [[k v]] [(bytes->str k) (bytes->str v)]))
       (into {})))

(defn read-msg
  "Decode a raw bencode message map into a Clojure map with keyword keys.
  Recursively converts all byte arrays to strings."
  [msg]
  (let [decoded (bytes->str msg)]
    (zipmap (map keyword (keys decoded))
            (vals decoded))))

(defn coerce-long [x]
  (if (string? x) (Long/parseLong x) x))

(defn next-id []
  (str (java.util.UUID/randomUUID)))

(defn write-bencode-msg
  "Write bencode message to output stream and flush"
  [out msg]
  (b/write-bencode out msg)
  (.flush out))

;; ============================================================================
;; Lazy sequence API
;; ============================================================================

(defn message-seq
  "Create lazy sequence of raw bencode messages from input stream.
  Continues until EOF or error. Returns nils after stream ends."
  [in]
  (repeatedly
   #(b/read-bencode in)))

(defn decode-messages
  "Map raw bencode message sequence through decoder.
  Stops at first nil (EOF/error)."
  [msg-seq]
  (map read-msg (take-while some? msg-seq)))

(defn filter-id
  "Filter messages by message id."
  [id msg-seq]
  (filter #(= (:id %) id) msg-seq))

(defn filter-session
  "Filter messages by session id."
  [session-id msg-seq]
  (filter #(= (:session %) session-id) msg-seq))

(defn take-upto
  "Take elements from coll up to and including the first element
  where (pred element) is truthy."
  [pred coll]
  (lazy-seq
   (when-let [s (seq coll)]
     (let [x (first s)]
       (cons x (when-not (pred x)
                 (take-upto pred (rest s))))))))

(defn take-until-done
  "Take messages up to and including first with 'done' status."
  [msg-seq]
  (take-upto #(some #{"done"} (:status %)) msg-seq))

;; ============================================================================
;; Socket and connection management
;; ============================================================================

(defn create-socket
  "Create and connect a socket with timeout."
  [host port timeout-ms]
  (doto (java.net.Socket.)
    (.connect (java.net.InetSocketAddress. host (coerce-long port)) timeout-ms)
    (.setSoTimeout timeout-ms)))

(defn with-socket
  "Execute function f with connected socket and streams.
  f receives [socket out in] as arguments."
  [host port timeout-ms f]
  (with-open [s (create-socket host port timeout-ms)]
    (let [out (java.io.BufferedOutputStream. (.getOutputStream s))
          in (java.io.PushbackInputStream. (.getInputStream s))]
      (f s out in))))

;; ============================================================================
;; Connection map utilities
;; ============================================================================

(defn make-connection
  "Create a connection map from socket and streams.
  Connection map: {:input :output :host :port :session-id :nrepl-env :socket}"
  [socket out in host port & {:keys [session-id nrepl-env]}]
  {:socket socket
   :input in
   :output out
   :host host
   :port port
   :session-id session-id
   :nrepl-env nrepl-env})

;; ============================================================================
;; Helper functions for collecting messages
;; ============================================================================

(defn messages-for-id
  "Send operation and collect all messages for the given id.
  Returns realized vector of all messages up to 'done' status.

  conn: connection map with :input, :output, and optionally :session-id
  op-map: operation map (e.g., {'op' 'describe'})

  If op-map does not contain 'id', one will be generated.
  If op-map does not contain 'session' and conn has :session-id, it will be added."
  [conn op-map]
  (let [{:keys [input output session-id]} conn
        ;; Use provided id or generate new one
        id (get op-map "id" (next-id))
        session (get op-map "session" session-id)
        msg (cond-> (assoc op-map "id" id)
              session (assoc "session" session))
        _ (write-bencode-msg output msg)
        msgs (->> (message-seq input)
                  (decode-messages)
                  (filter-id id))]
    (take-until-done (cond->> msgs
                       session (filter-session session)))))

(defn merge-response
  "Combines the provided seq of response messages into a single response map.

   Certain message slots are combined in special ways:

     - only the last :ns is retained
     - :value is accumulated into an ordered collection
     - :status and :session are accumulated into a set
     - string values (associated with e.g. :out and :err) are concatenated"
  [responses]
  (reduce
   (fn [m [k v]]
     (case k
       (:id :ns) (assoc m k v)
       :value (update-in m [k] (fnil conj []) v)
       :status (update-in m [k] (fnil into #{}) v)
       :session (update-in m [k] (fnil conj #{}) v)
       (if (string? v)
         (update-in m [k] #(str % v))
         (assoc m k v))))
   {} (apply concat responses)))

(defn send-op
  "Send operation and return merged response.

  conn: connection map
  op-map: operation map

  Returns merged response map with all accumulated values."
  [conn op-map]
  (merge-response (messages-for-id conn op-map)))

;; ============================================================================
;; Session file I/O
;; ============================================================================

(defn slurp-nrepl-session
  "Read session data from nrepl session file for given host and port.
  Returns map with :session-id, :env-type, :host, and :port, or nil if file doesn't exist or on error."
  [host port]
  (try
    (let [ctx {}
          session-file (tmp/nrepl-target-file ctx {:host host :port port})]
      (when (fs/exists? session-file)
        (edn/read-string (slurp session-file :encoding "UTF-8"))))
    (catch Exception _
      nil)))

(defn spit-nrepl-session
  "Write session data to nrepl session file for given host and port.
  Takes a map with :session-id and optionally :env-type. Host and port are
  added to the session data for validation."
  [session-data host port]
  (let [ctx {}
        session-file (tmp/nrepl-target-file ctx {:host host :port port})
        full-data (assoc session-data :host host :port port)]
    ;; Ensure parent directories exist
    (when-let [parent (fs/parent session-file)]
      (fs/create-dirs parent))
    (spit session-file (str (pr-str full-data) "\n") :encoding "UTF-8")))

(defn delete-nrepl-session
  "Delete nrepl session file for given host and port if it exists."
  [host port]
  (let [ctx {}
        session-file (tmp/nrepl-target-file ctx {:host host :port port})]
    (fs/delete-if-exists session-file)))

;; ============================================================================
;; Basic nREPL operations
;; ============================================================================

;; Low-level operations that take a connection map

(defn describe-nrepl*
  "Get nREPL server description using an existing connection.
   Returns description map."
  [conn]
  (send-op conn {"op" "describe"}))

(defn eval-nrepl*
  "Evaluate code using an existing connection.
   Returns the full response map."
  [conn code]
  (send-op conn {"op" "eval" "code" code}))

(defn clone-session*
  "Clone a new session using an existing connection.
   Returns the response map with :new-session."
  [conn]
  (send-op conn {"op" "clone"}))

(defn ls-sessions*
  "List active sessions using an existing connection.
   Returns the response map with :sessions."
  [conn]
  (send-op conn {"op" "ls-sessions"}))

;; High-level convenience operations that create their own socket

(defn ls-sessions
  "Get list of active session IDs from nREPL server.
   Returns nil if unable to connect or on error.
   Uses a 500ms timeout for connection and read operations."
  [host port]
  (try
    (with-socket host port 500
      (fn [socket out in]
        (let [conn (make-connection socket out in host port)
              response (ls-sessions* conn)]
          (:sessions response))))
    (catch Exception _
      nil)))
