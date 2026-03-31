(babashka.deps/add-deps '{:deps {dev.weavejester/cljfmt {:mvn/version "0.15.5"}
                                 parinferish/parinferish {:mvn/version "0.8.0"}}})

(ns clojure-mcp-light.paren-repair
  "Standalone CLI tool for fixing delimiter errors and formatting Clojure files"
  (:require [babashka.fs :as fs]
            [cljfmt.core :as cljfmt]
            [clojure.string :as string]
            [clojure-mcp-light.delimiter-repair :refer [fix-delimiters]]
            [clojure-mcp-light.hook :as hook :refer [clojure-file? fix-and-format-file!]]
            [taoensso.timbre :as timbre]))

;; ============================================================================
;; Stdin Detection
;; ============================================================================

(defn has-stdin-data?
  "Check if stdin has data available (not a TTY).
  Returns true if stdin is ready to be read (e.g., piped input or heredoc)."
  []
  (try
    (.ready *in*)
    (catch Exception _ false)))

;; ============================================================================
;; Stdin Processing
;; ============================================================================

(defn process-stdin
  "Process code from stdin: fix delimiters and format.
   Outputs result to stdout.
   Returns a map with:
   - :success - boolean indicating overall success
   - :changed - boolean indicating if any changes were made
   - :delimiter-fixed - boolean indicating if delimiter was fixed
   - :formatted - boolean indicating if code was formatted"
  []
  (let [input (slurp *in*)
        {:keys [text method]} (fix-delimiters input)
        delimiter-fixed? (some? method)]
    (if text
      ;; fix-delimiters succeeded (or no errors)
      (let [formatted-text (try
                             (cljfmt/reformat-string text)
                             (catch Exception _
                               text))
            formatted? (not= text formatted-text)
            changed? (or delimiter-fixed? formatted?)]
        (print formatted-text)
        (flush)
        {:success true
         :changed changed?
         :delimiter-fixed delimiter-fixed?
         :formatted formatted?})
      ;; fix-delimiters returned nil (unfixable)
      (do
        (binding [*out* *err*]
          (println "Error: Could not fix delimiter errors"))
        {:success false
         :changed false
         :delimiter-fixed false
         :formatted false}))))

;; ============================================================================
;; File Processing
;; ============================================================================

(defn process-file
  "Process a single file: fix delimiters and format.
   Returns a map with:
   - :success - boolean indicating overall success
   - :file-path - the processed file path
   - :message - human-readable message about what happened
   - :delimiter-fixed - boolean indicating if delimiter was fixed
   - :formatted - boolean indicating if file was formatted"
  [file-path]
  (cond
    (not (fs/exists? file-path))
    {:success false
     :file-path file-path
     :message "File does not exist"
     :delimiter-fixed false
     :formatted false}

    (not (clojure-file? file-path))
    {:success false
     :file-path file-path
     :message "Not a Clojure file (skipping)"
     :delimiter-fixed false
     :formatted false}

    :else
    ;; Use shared fix-and-format-file! from hook.clj
    (assoc (fix-and-format-file! file-path true "paren-repair")
           :file-path file-path)))

;; ============================================================================
;; Main Entry Point
;; ============================================================================

(defn show-help []
  (println "Usage: clj-paren-repair [FILE ...]")
  (println "       echo CODE | clj-paren-repair")
  (println "       clj-paren-repair <<'EOF' ... EOF")
  (println)
  (println "Fix delimiter errors and format Clojure code.")
  (println)
  (println "When no files are provided, reads from stdin and writes to stdout.")
  (println "If no changes are needed, echoes the input unchanged.")
  (println)
  (println "Options:")
  (println "  -h, --help    Show this help message"))

(defn -main [& args]
  (let [show-help? (some #{"--help" "-h"} args)
        file-args (remove #{"--help" "-h"} args)]

    (cond
      ;; Help requested
      show-help?
      (do
        (show-help)
        (System/exit 0))

      ;; No file args - check for stdin
      (empty? file-args)
      (if (has-stdin-data?)
        ;; Stdin mode: read, process, output to stdout
        (do
          (timbre/set-config! {:appenders {}})
          (let [result (process-stdin)]
            (System/exit (if (:success result) 0 1))))
        ;; No stdin and no files - show help
        (do
          (show-help)
          (System/exit 1)))

      ;; File mode
      :else
      (do
        (timbre/set-config! {:appenders {}})

        (binding [hook/*enable-cljfmt* true]
          (try
            (let [results (doall (map process-file file-args))
                  successes (filter :success results)
                  failures (filter (complement :success) results)
                  success-count (count successes)
                  failure-count (count failures)]

              ;; Print results
              (println)
              (println "clj-paren-repair Results")
              (println "========================")
              (println)

              (doseq [{:keys [file-path message delimiter-fixed formatted]} results]
                (let [tags (when (or delimiter-fixed formatted)
                             (str " ["
                                  (string/join ", "
                                               (filter some?
                                                       [(when delimiter-fixed "delimiter-fixed")
                                                        (when formatted "formatted")]))
                                  "]"))]
                  (println (str "  " file-path ": " message tags))))

              (println)
              (println "Summary:")
              (println "  Success:" success-count)
              (println "  Failed: " failure-count)
              (println)

              (if (zero? failure-count)
                (System/exit 0)
                (System/exit 1)))
            (catch Exception e
              (binding [*out* *err*]
                (println "Fatal error:" (.getMessage e)))
              (System/exit 1))))))))
