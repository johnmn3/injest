(ns clojure-mcp-light.delimiter-repair
  "Delimiter error detection and repair functions using edamame and parinfer-rust"
  (:require [edamame.core :as e]
            [clojure.java.shell :as shell]
            [cheshire.core :as json]
            [clojure-mcp-light.stats :as stats]
            [parinferish.core :as parinferish]))

(def ^:dynamic *signal-on-bad-parse* true)

(defn delimiter-error?
  "Returns true if the string has a delimiter error specifically.
   Checks both that it's an :edamame/error and has delimiter info.
   Uses :all true to enable all standard Clojure reader features:
   function literals, regex, quotes, syntax-quote, deref, var, etc.
   Also enables :read-cond :allow to support reader conditionals.
   Handles unknown data readers gracefully with a default reader fn."
  [s]
  (try
    (e/parse-string-all s {:all true
                           ;; TODO this is when we think bb has been updated
                           ;; :features (constantly true)
                           ;; :read-cond second
                           :features #{:bb :clj :cljs :cljr :default} #_(constantly true)
                           :read-cond :allow
                           :readers (fn [_tag] (fn [data] data))
                           :auto-resolve name})
    false ; No error = no delimiter error
    (catch clojure.lang.ExceptionInfo ex
      (let [data (ex-data ex)
            result (and (= :edamame/error (:type data))
                        (contains? data :edamame/opened-delimiter))]
        (when-not result
          (when *signal-on-bad-parse*
            (stats/log-stats! :delimiter-parse-error
                              {:ex-message (ex-message ex)
                               :ex-data (ex-data ex)})))
        result))
    (catch Exception e
      (when *signal-on-bad-parse*
        (stats/log-stats! :delimiter-parse-error
                          {:ex-message (ex-message e)}))

      ;; Experimentally going to return true in this case to
      ;; communication a parse failure we will run parinfer if this is
      ;; true just in case there is a delimiter error as well in the
      ;; file

      ;; running parinfer is a benign action most of the time

      *signal-on-bad-parse*)))

(defn actual-delimiter-error? [s]
  (binding [*signal-on-bad-parse* false]
    (delimiter-error? s)))

(defn parinferish-repair
  "Attempts to repair delimiter errors using parinferish (pure Clojure).
   Returns a map with:
   - :success - boolean indicating if repair was successful
   - :text - the repaired code (if successful)
   - :error - error message (if unsuccessful)"
  [s]
  (try
    (let [repaired (parinferish/flatten
                    (parinferish/parse s {:mode :indent}))]
      {:success true
       :text repaired
       :error nil})
    (catch Exception e
      {:success false
       :error (.getMessage e)})))

(def parinfer-rust-available?
  "Check if parinfer-rust binary is available on PATH.
   Result is memoized to avoid repeated shell calls."
  (memoize
   (fn []
     (try
       (let [result (shell/sh "which" "parinfer-rust")]
         (zero? (:exit result)))
       (catch Exception _
         false)))))

(defn parinfer-repair
  "Attempts to repair delimiter errors using parinfer-rust.
   Returns a map with:
   - :success - boolean indicating if repair was successful
   - :repaired-text - the repaired code (if successful)
   - :error - error message (if unsuccessful)"
  [s]
  (let [result (shell/sh "parinfer-rust"
                         "--mode" "indent"
                         "--language" "clojure"
                         "--output-format" "json"
                         :in s)
        exit-code (:exit result)]
    (if (zero? exit-code)
      (try
        (json/parse-string (:out result) true)
        (catch Exception _
          {:success false}))
      {:success false})))

(defn repair-delimiters
  "Unified delimiter repair function that automatically selects the best available backend.
   Prefers parinfer-rust (external tool) when available, falls back to parinferish (pure Clojure).
   Returns a map with:
   - :success - boolean indicating if repair was successful
   - :text - the repaired code (if successful)
   - :error - error message (if unsuccessful)"
  [s]
  (if (parinfer-rust-available?)
    (parinfer-repair s)
    (parinferish-repair s)))

(defn fix-delimiters
  "Takes a Clojure string and attempts to fix delimiter errors.
   Returns the repaired string if successful, false otherwise.
   If no delimiter errors exist, returns the original string."
  [s]
  (if (delimiter-error? s)
    (let [{:keys [text success]} (repair-delimiters s)]
      (when (and success text (not (delimiter-error? text)))
        text))
    s))
