#!/usr/bin/env bb

(ns stats-summary
  "Analyze delimiter event statistics from stats log file"
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [clojure.java.io :as io]))

(def stats-file
  (let [home (System/getProperty "user.home")]
    (str home "/.clojure-mcp-light/stats.log")))

(defn read-stats
  "Read and parse all EDN entries from stats log file"
  [file-path]
  (if (.exists (io/file file-path))
    (try
      (->> (slurp file-path)
           (str/split-lines)
           (remove str/blank?)
           (map edn/read-string))
      (catch Exception e
        (binding [*out* *err*]
          (println "Error reading stats file:" (.getMessage e)))
        []))
    (do
      (binding [*out* *err*]
        (println "Stats file not found:" file-path))
      [])))

(defn count-by
  "Count entries grouped by a key"
  [k entries]
  (->> entries
       (group-by k)
       (map (fn [[k v]] [k (count v)]))
       (sort-by second >)
       (into {})))

(defn format-count
  "Format count with padding for alignment"
  [n width]
  (format (str "%" width "d") n))

(defn print-section
  "Print a section header"
  [title]
  (println)
  (println title)
  (println (str/join (repeat (count title) "="))))

(defn print-summary
  "Print summary statistics"
  [entries]
  (let [;; Separate delimiter and cljfmt events
        delimiter-events (filter :hook-event entries)
        cljfmt-events (filter #(str/starts-with? (name (:event-type %)) "cljfmt-") entries)
        parse-events (filter #(= :delimiter-parse-error (:event-type %)) entries)

        total (count entries)
        delimiter-total (count delimiter-events)
        cljfmt-total (count cljfmt-events)
        parse-total (count parse-events)

        by-event-type (count-by :event-type delimiter-events)
        by-hook-event (count-by :hook-event delimiter-events)
        by-file (->> delimiter-events
                     (group-by :file-path)
                     (map (fn [[k v]] [k (count v)]))
                     (sort-by second >)
                     (take 10))]

    ;; Calculate delimiter metrics
    (let [errors (get by-event-type :delimiter-error 0)
          fixed (get by-event-type :delimiter-fixed 0)
          failed (get by-event-type :delimiter-fix-failed 0)
          ok (get by-event-type :delimiter-ok 0)
          ;; Total unique operations: ok + errors (not ok + errors + fixed + failed)
          ;; because error operations generate BOTH error AND fixed/failed events
          total-operations (+ ok errors)
          fix-attempts (+ fixed failed)

          ;; Calculate cljfmt metrics
          cljfmt-by-type (count-by :event-type cljfmt-events)
          cljfmt-already-formatted (get cljfmt-by-type :cljfmt-already-formatted 0)
          cljfmt-needed-formatting (get cljfmt-by-type :cljfmt-needed-formatting 0)
          cljfmt-fix-succeeded (get cljfmt-by-type :cljfmt-fix-succeeded 0)
          cljfmt-fix-failed (get cljfmt-by-type :cljfmt-fix-failed 0)
          cljfmt-check-errors (get cljfmt-by-type :cljfmt-check-error 0)
          cljfmt-total-checked (+ cljfmt-already-formatted cljfmt-needed-formatting cljfmt-check-errors)
          cljfmt-total-fix-attempts (+ cljfmt-fix-succeeded cljfmt-fix-failed)]

      (println)
      (println "clojure-mcp-light Utility Validation")
      (println (str/join (repeat 60 "=")))

      ;; Delimiter Repair Metrics
      (print-section "Delimiter Repair Metrics")
      (println (format "  Total Writes/Edits:         %5d" total-operations))
      (println (format "  Clean Code (no errors):     %5d  (%5.1f%% of total)"
                       ok
                       (if (pos? total-operations)
                         (* 100.0 (/ ok total-operations))
                         0.0)))
      (println (format "  Errors Detected:            %5d  (%5.1f%% of total)"
                       errors
                       (if (pos? total-operations)
                         (* 100.0 (/ errors total-operations))
                         0.0)))
      (println (format "  Successfully Fixed:         %5d  (%5.1f%% of errors)"
                       fixed
                       (if (pos? errors)
                         (* 100.0 (/ fixed errors))
                         0.0)))
      (println (format "  Failed to Fix:              %5d  (%5.1f%% of errors)"
                       failed
                       (if (pos? errors)
                         (* 100.0 (/ failed errors))
                         0.0)))
      (println (format "  Parse Errors:               %5d  (%5.1f%% of fix attempts)"
                       parse-total
                       (if (pos? fix-attempts)
                         (* 100.0 (/ parse-total fix-attempts))
                         0.0)))

      ;; Cljfmt Metrics
      (print-section "Cljfmt Metrics")
      (println (format "  Total Files Checked:        %5d" cljfmt-total-checked))
      (println (format "  Already Formatted:          %5d  (%5.1f%% of total)"
                       cljfmt-already-formatted
                       (if (pos? cljfmt-total-checked)
                         (* 100.0 (/ cljfmt-already-formatted cljfmt-total-checked))
                         0.0)))
      (println (format "  Needed Formatting:          %5d  (%5.1f%% of total)"
                       cljfmt-needed-formatting
                       (if (pos? cljfmt-total-checked)
                         (* 100.0 (/ cljfmt-needed-formatting cljfmt-total-checked))
                         0.0)))
      (println (format "  Check Errors:               %5d  (%5.1f%% of total)"
                       cljfmt-check-errors
                       (if (pos? cljfmt-total-checked)
                         (* 100.0 (/ cljfmt-check-errors cljfmt-total-checked))
                         0.0)))
      (println (format "  Fix Success Rate:           %5d  (%5.1f%% of fix attempts)"
                       cljfmt-fix-succeeded
                       (if (pos? cljfmt-total-fix-attempts)
                         (* 100.0 (/ cljfmt-fix-succeeded cljfmt-total-fix-attempts))
                         0.0)))
      (println (format "  Fix Failures:               %5d  (%5.1f%% of fix attempts)"
                       cljfmt-fix-failed
                       (if (pos? cljfmt-total-fix-attempts)
                         (* 100.0 (/ cljfmt-fix-failed cljfmt-total-fix-attempts))
                         0.0)))

      ;; Category Breakdown
      (when (pos? total-operations)
        (print-section "Events by Type")
        (doseq [[event-type cnt] by-event-type]
          (let [pct (if (pos? total-operations)
                      (format "%.1f%%" (* 100.0 (/ cnt total-operations)))
                      "0.0%")]
            (println (format "  %-30s %5d  (%6s)" (name event-type) cnt pct)))))

      (println))))

(defn -main [& args]
  (let [file-path (or (first args) stats-file)
        entries (read-stats file-path)]
    (if (empty? entries)
      (do
        (println "No statistics found.")
        (println "Enable stats tracking with: clj-paren-repair-claude-hook --stats")
        (System/exit 1))
      (do
        (print-summary entries)
        (System/exit 0)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
