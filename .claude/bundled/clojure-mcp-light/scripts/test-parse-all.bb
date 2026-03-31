#!/usr/bin/env bb

(ns test-parse-all
  "Test edamame parsing configuration against all Clojure files in a directory"
  (:require [edamame.core :as e]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(defn clojure-file? [^java.io.File f]
  (and (.isFile f)
       (let [name (.getName f)]
         (or (.endsWith name ".clj")
             (.endsWith name ".cljs")
             (.endsWith name ".cljc")
             (.endsWith name ".cljr")
             (.endsWith name ".bb")))))

(defn find-clojure-files [dir]
  (let [dir-file (io/file dir)]
    (if (.exists dir-file)
      (->> (file-seq dir-file)
           (filter clojure-file?)
           (sort-by #(.getPath ^java.io.File %)))
      [])))

(defn parse-file [file]
  "Attempts to parse a file with the same config as delimiter-repair.
   Returns a map with :status (:success, :delimiter-error, :other-error)
   and :error if applicable."
  (let [path (.getPath file)
        content (slurp file)]
    (try
      (e/parse-string-all content {:all true
                                   :features #{:bb :clj :cljs :cljr :default} #_(constantly true)
                                   :read-cond :allow
                                   ;; TODO this is when we think bb has been updated
                                   ;; :features (constantly true)
                                   ;; :read-cond second
                                   :readers (fn [_tag] (fn [data] data))
                                   :auto-resolve name})
      {:status :success :file path}
      (catch clojure.lang.ExceptionInfo ex
        (let [data (ex-data ex)]
          (if (and (= :edamame/error (:type data))
                   (contains? data :edamame/opened-delimiter))
            {:status :delimiter-error :file path :error (ex-message ex)}
            {:status :other-error :file path :error ex :data data})))
      (catch Exception ex
        ;; Unknown reader tags and other non-delimiter errors
        (let [msg (ex-message ex)]
          (if (and msg (str/includes? msg "No reader function for tag"))
            ;; Extract the tag name from error message
            (let [tag-name (second (re-find #"No reader function for tag (\S+)" msg))]
              {:status :unknown-tag :file path :error ex :tag tag-name})
            {:status :other-error :file path :error ex}))))))

(defn print-report [result]
  (case (:status result)
    :success
    (println "✓" (:file result))

    :delimiter-error
    (println "⚠" (:file result) "- delimiter error (expected):" (:error result))

    :unknown-tag
    (do
      (println "\n❌ UNKNOWN TAG")
      (println "File:" (:file result))
      (println "Tag:" (:tag result))
      (println "\nAdd this to the :readers map:")
      (println (format "  '%s (fn [x] x)" (:tag result))))

    :other-error
    (do
      (println "\n❌ UNEXPECTED ERROR")
      (println "File:" (:file result))
      (println "Error:" (ex-message (:error result)))
      (when-let [data (:data result)]
        (println "Ex-data:" data))
      (println "\nStacktrace:")
      (.printStackTrace (:error result)))))

(defn test-directory [dir]
  (let [files (find-clojure-files dir)]
    (println (format "Found %d Clojure files in %s\n" (count files) dir))
    (loop [files files
           stats {:success 0 :delimiter-error 0 :unknown-tag 0 :other-error 0}]
      (if-let [file (first files)]
        (let [result (parse-file file)]
          (print-report result)
          (if (or (= :other-error (:status result))
                  (= :unknown-tag (:status result)))
            ;; Stop on first error
            (do
              (println "\n" (str "Stopped after processing " (inc (:success stats)) " successful files"))
              stats)
            ;; Continue
            (recur (rest files)
                   (update stats (:status result) inc))))
        ;; All done
        (do
          (println "\n=== RESULTS ===")
          (println (format "✓ Success: %d files" (:success stats)))
          (println (format "⚠ Delimiter errors: %d files" (:delimiter-error stats)))
          (println (format "❌ Unknown tags: %d files" (:unknown-tag stats)))
          (println (format "❌ Other errors: %d files" (:other-error stats)))
          stats)))))

(defn -main [& args]
  (let [dir (or (first args) ".")]
    (if (.exists (io/file dir))
      (test-directory dir)
      (do
        (println "Error: Directory does not exist:" dir)
        (System/exit 1)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))
