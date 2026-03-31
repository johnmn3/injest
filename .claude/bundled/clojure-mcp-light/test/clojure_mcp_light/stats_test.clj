(ns clojure-mcp-light.stats-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [clojure-mcp-light.stats :as stats]
            [babashka.fs :as fs]))

;; ============================================================================
;; Path Normalization Tests
;; ============================================================================

(deftest normalize-stats-path-test
  (testing "expands tilde to home directory"
    (let [result (stats/normalize-stats-path "~/my-stats.log")
          home (System/getProperty "user.home")]
      (is (str/starts-with? result home))
      (is (str/ends-with? result "my-stats.log"))
      (is (not (str/includes? result "~")))))

  (testing "converts relative paths to absolute"
    (let [result (stats/normalize-stats-path "../../test.log")]
      (is (str/starts-with? result "/"))
      (is (not (str/includes? result "..")))))

  (testing "normalizes absolute paths"
    (let [result (stats/normalize-stats-path "/tmp/stats.log")]
      (is (= "/tmp/stats.log" result))))

  (testing "resolves dot-relative paths"
    (let [result (stats/normalize-stats-path "./my-stats.log")
          cwd (str (fs/cwd))]
      (is (str/starts-with? result cwd))
      (is (str/ends-with? result "my-stats.log"))))

  (testing "handles complex relative paths"
    (let [result (stats/normalize-stats-path "../../../stats/../test.log")]
      (is (str/starts-with? result "/"))
      (is (not (str/includes? result "..")))
      (is (not (str/includes? result "/stats/")))))

  (testing "returns a string"
    (is (string? (stats/normalize-stats-path "~/test.log")))
    (is (string? (stats/normalize-stats-path "/tmp/test.log")))
    (is (string? (stats/normalize-stats-path "./test.log")))))
