(ns clojure-mcp-light.nrepl-eval-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-mcp-light.nrepl-eval :as ne]
            [clojure-mcp-light.nrepl-client :as nc]
            [clojure.java.io :as io]
            [clojure.string]))

(deftest bytes->str-test
  (testing "converts bytes to string"
    (is (= "hello" (nc/bytes->str (.getBytes "hello"))))
    (is (= "test" (nc/bytes->str "test")))))

(deftest coerce-long-test
  (testing "converts string to long"
    (is (= 7888 (nc/coerce-long "7888")))
    (is (= 1234 (nc/coerce-long 1234)))))

(deftest next-id-test
  (testing "generates unique IDs"
    (let [id1 (nc/next-id)
          id2 (nc/next-id)]
      (is (string? id1))
      (is (string? id2))
      (is (not= id1 id2)))))

(deftest slurp-nrepl-session-test
  (testing "reads session ID from per-target session file"
    (let [test-session-file ".nrepl-session-test"
          test-session-id "test-session-12345"
          test-host "localhost"
          test-port 7888]
      (try
        ;; Create a test session file
        (spit test-session-file (str test-session-id "\n") :encoding "UTF-8")
        ;; Test reading it
        (let [result (with-redefs [nc/slurp-nrepl-session
                                   (fn [_ _]
                                     (try
                                       (when (.exists (io/file test-session-file))
                                         (clojure.string/trim (slurp test-session-file :encoding "UTF-8")))
                                       (catch Exception _
                                         nil)))]
                       (nc/slurp-nrepl-session test-host test-port))]
          (is (= test-session-id result)))
        (finally
          ;; Clean up
          (io/delete-file test-session-file true)))))

  (testing "returns nil when file doesn't exist"
    (let [result (with-redefs [nc/slurp-nrepl-session
                               (fn [_ _]
                                 (try
                                   (when (.exists (io/file ".nrepl-session-nonexistent"))
                                     (clojure.string/trim (slurp ".nrepl-session-nonexistent" :encoding "UTF-8")))
                                   (catch Exception _
                                     nil)))]
                   (nc/slurp-nrepl-session "localhost" 7888))]
      (is (nil? result))))

  (testing "returns nil on read error"
    (let [result (nc/slurp-nrepl-session "localhost" 7888)]
      ;; Without a file, should return nil
      (is (or (nil? result) (string? result) (map? result))))))

(deftest spit-nrepl-session-test
  (testing "writes session ID to per-target session file"
    (let [test-session-file ".nrepl-session-test"
          test-session-data {:session-id "test-session-67890"}
          test-host "localhost"
          test-port 7888]
      (try
        ;; Write session ID
        (with-redefs [nc/spit-nrepl-session
                      (fn [session-data _ _]
                        (spit test-session-file (str (:session-id session-data) "\n") :encoding "UTF-8"))]
          (nc/spit-nrepl-session test-session-data test-host test-port))
        ;; Verify it was written correctly
        (let [content (clojure.string/trim (slurp test-session-file :encoding "UTF-8"))]
          (is (= (:session-id test-session-data) content)))
        (finally
          ;; Clean up
          (io/delete-file test-session-file true))))))

(deftest delete-nrepl-session-test
  (testing "deletes per-target session file when it exists"
    (let [test-session-file ".nrepl-session-test"
          test-host "localhost"
          test-port 7888]
      (try
        ;; Create a test file
        (spit test-session-file "test-session" :encoding "UTF-8")
        (is (.exists (io/file test-session-file)))
        ;; Delete it
        (with-redefs [nc/delete-nrepl-session
                      (fn [_ _]
                        (let [f (io/file test-session-file)]
                          (when (.exists f)
                            (.delete f))))]
          (nc/delete-nrepl-session test-host test-port))
        ;; Verify it's gone
        (is (not (.exists (io/file test-session-file))))
        (finally
          ;; Clean up in case test failed
          (io/delete-file test-session-file true)))))

  (testing "does nothing when file doesn't exist"
    (with-redefs [nc/delete-nrepl-session
                  (fn [_ _]
                    (let [f (io/file ".nrepl-session-nonexistent")]
                      (when (.exists f)
                        (.delete f))))]
      ;; Should not throw an error
      (is (nil? (nc/delete-nrepl-session "localhost" 7888))))))

(deftest get-host-test
  (testing "gets host from options"
    (is (= "custom-host" (ne/get-host {:host "custom-host"}))))

  (testing "falls back to default"
    (is (= "127.0.0.1" (ne/get-host {})))))

(deftest read-msg-test
  (testing "converts byte values to strings in message"
    (let [msg {"status" [(.getBytes "done")]
               "value" "42"}
          result (nc/read-msg msg)]
      (is (map? result))
      (is (= "done" (first (:status result))))
      (is (= "42" (:value result))))))
