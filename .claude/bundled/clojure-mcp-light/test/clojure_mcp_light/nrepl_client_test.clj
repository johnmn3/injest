(ns clojure-mcp-light.nrepl-client-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure-mcp-light.nrepl-client :as nrepl]))

;; ============================================================================
;; Message encoding/decoding tests
;; ============================================================================

(deftest bytes->str-test
  (testing "converts byte arrays to strings"
    (is (= "hello" (nrepl/bytes->str (.getBytes "hello"))))
    (is (= "test" (nrepl/bytes->str (.getBytes "test")))))

  (testing "passes through strings unchanged"
    (is (= "already-string" (nrepl/bytes->str "already-string"))))

  (testing "passes through other types unchanged"
    (is (= 42 (nrepl/bytes->str 42)))
    (is (= :keyword (nrepl/bytes->str :keyword))))

  (testing "recursively converts nested byte arrays in vectors"
    (let [result (nrepl/bytes->str [(.getBytes "a") (.getBytes "b") "c"])]
      (is (= ["a" "b" "c"] result))))

  (testing "recursively converts nested byte arrays in maps"
    (let [result (nrepl/bytes->str {"key" (.getBytes "value")
                                    (.getBytes "bkey") "string"})]
      (is (= {"key" "value" "bkey" "string"} result)))))

(deftest read-msg-test
  (testing "converts string keys to keywords"
    (let [msg {"op" "eval" "code" "(+ 1 2)"}
          result (nrepl/read-msg msg)]
      (is (= "eval" (:op result)))
      (is (= "(+ 1 2)" (:code result)))))

  (testing "converts byte values to strings"
    (let [msg {"value" (.getBytes "42")}
          result (nrepl/read-msg msg)]
      (is (= "42" (:value result)))))

  (testing "converts status byte vector to string vector"
    (let [msg {"status" [(.getBytes "done") (.getBytes "ok")]}
          result (nrepl/read-msg msg)]
      (is (= ["done" "ok"] (:status result)))))

  (testing "converts sessions byte vector to string vector"
    (let [msg {"sessions" [(.getBytes "session-1") (.getBytes "session-2")]}
          result (nrepl/read-msg msg)]
      (is (= ["session-1" "session-2"] (:sessions result)))))

  (testing "handles mixed byte and string values"
    (let [msg {"id" "abc123"
               "value" (.getBytes "result")
               "status" [(.getBytes "done")]}
          result (nrepl/read-msg msg)]
      (is (= "abc123" (:id result)))
      (is (= "result" (:value result)))
      (is (= ["done"] (:status result))))))

(deftest coerce-long-test
  (testing "parses string to long"
    (is (= 7888 (nrepl/coerce-long "7888")))
    (is (= 1234 (nrepl/coerce-long "1234"))))

  (testing "passes through long unchanged"
    (is (= 7888 (nrepl/coerce-long 7888)))
    (is (= 1234 (nrepl/coerce-long 1234)))))

(deftest next-id-test
  (testing "generates unique IDs"
    (let [id1 (nrepl/next-id)
          id2 (nrepl/next-id)]
      (is (string? id1))
      (is (string? id2))
      (is (not= id1 id2))))

  (testing "generates UUID format"
    (let [id (nrepl/next-id)]
      (is (re-matches #"[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}" id)))))

;; ============================================================================
;; Lazy sequence function tests
;; ============================================================================

(deftest decode-messages-test
  (testing "decodes raw bencode messages"
    (let [raw-msgs [{"op" "eval"} {"value" (.getBytes "42")} nil]
          decoded (nrepl/decode-messages raw-msgs)]
      (is (= 2 (count decoded)))
      (is (= "eval" (:op (first decoded))))
      (is (= "42" (:value (second decoded))))))

  (testing "stops at first nil"
    (let [raw-msgs [{"op" "eval"} nil {"value" "should-not-appear"}]
          decoded (nrepl/decode-messages raw-msgs)]
      (is (= 1 (count decoded)))
      (is (= "eval" (:op (first decoded)))))))

(deftest filter-id-test
  (testing "filters messages by id"
    (let [msgs [{:id "abc" :value "1"}
                {:id "def" :value "2"}
                {:id "abc" :status ["done"]}]
          filtered (nrepl/filter-id "abc" msgs)]
      (is (= 2 (count filtered)))
      (is (every? #(= "abc" (:id %)) filtered))))

  (testing "returns empty when no matches"
    (let [msgs [{:id "abc" :value "1"}]
          filtered (nrepl/filter-id "xyz" msgs)]
      (is (empty? filtered)))))

(deftest filter-session-test
  (testing "filters messages by session"
    (let [msgs [{:session "s1" :value "1"}
                {:session "s2" :value "2"}
                {:session "s1" :status ["done"]}]
          filtered (nrepl/filter-session "s1" msgs)]
      (is (= 2 (count filtered)))
      (is (every? #(= "s1" (:session %)) filtered))))

  (testing "returns empty when no matches"
    (let [msgs [{:session "s1" :value "1"}]
          filtered (nrepl/filter-session "s2" msgs)]
      (is (empty? filtered)))))

(deftest take-upto-test
  (testing "takes up to and including first match"
    (let [coll [1 2 3 4 5 6]
          result (nrepl/take-upto #(> % 3) coll)]
      (is (= [1 2 3 4] result))))

  (testing "takes all when no match"
    (let [coll [1 2 3]
          result (nrepl/take-upto #(> % 10) coll)]
      (is (= [1 2 3] result))))

  (testing "takes only first element when first matches"
    (let [coll [5 6 7]
          result (nrepl/take-upto #(> % 3) coll)]
      (is (= [5] result))))

  (testing "works with predicates on maps"
    (let [coll [{:done false} {:done false} {:done true} {:done false}]
          result (nrepl/take-upto :done coll)]
      (is (= 3 (count result)))
      (is (= true (:done (last result))))))

  (testing "is lazy"
    (let [counter (atom 0)
          coll (map (fn [x] (swap! counter inc) x) [1 2 3 4 5])
          result (nrepl/take-upto #(> % 2) coll)]
      ;; Before realization, counter should be 0
      (is (= 0 @counter))
      ;; Force realization
      (doall result)
      ;; Should have processed at least up to the matching element
      ;; Implementation may process a few extra elements due to chunking
      (is (<= 3 @counter 5)))))

(deftest take-until-done-test
  (testing "takes messages until done status"
    (let [msgs [{:id "1" :value "42"}
                {:id "1" :out "output"}
                {:id "1" :status ["done"]}
                {:id "1" :value "should-not-appear"}]
          result (nrepl/take-until-done msgs)]
      (is (= 3 (count result)))
      (is (= ["done"] (:status (last result))))))

  (testing "takes all when no done status"
    (let [msgs [{:id "1" :value "42"}
                {:id "1" :out "output"}]
          result (nrepl/take-until-done msgs)]
      (is (= 2 (count result)))))

  (testing "handles done with other statuses"
    (let [msgs [{:id "1" :value "42"}
                {:id "1" :status ["done" "ok"]}]
          result (nrepl/take-until-done msgs)]
      (is (= 2 (count result)))
      (is (some #{"done"} (:status (last result)))))))

;; ============================================================================
;; merge-response tests
;; ============================================================================

(deftest merge-response-test
  (testing "merges single value message"
    (let [msgs [{:id "1" :value "42" :ns "user"}]
          result (nrepl/merge-response msgs)]
      (is (= ["42"] (:value result)))
      (is (= "user" (:ns result)))))

  (testing "preserves custom fields from describe response"
    (let [msgs [{:id "1" :versions {:clojure {:major 1 :minor 12} :nrepl {:major 1 :minor 3}}
                 :ops {:eval {} :load-file {}}
                 :aux {:some-data "value"}
                 :status ["done"]}]
          result (nrepl/merge-response msgs)]
      (is (= {:clojure {:major 1 :minor 12} :nrepl {:major 1 :minor 3}} (:versions result)))
      (is (= {:eval {} :load-file {}} (:ops result)))
      (is (= {:some-data "value"} (:aux result)))
      (is (contains? (:status result) "done"))))

  (testing "merges multiple value messages"
    (let [msgs [{:id "1" :value "first"}
                {:id "1" :value "second"}
                {:id "1" :value "third"}]
          result (nrepl/merge-response msgs)]
      (is (= ["first" "second" "third"] (:value result)))))

  (testing "concatenates output streams"
    (let [msgs [{:id "1" :out "line1\n"}
                {:id "1" :out "line2\n"}
                {:id "1" :err "error\n"}]
          result (nrepl/merge-response msgs)]
      (is (= "line1\nline2\n" (:out result)))
      (is (= "error\n" (:err result)))))

  (testing "merges status sets"
    (let [msgs [{:id "1" :status ["evaluating"]}
                {:id "1" :status ["done"]}
                {:id "1" :status ["ok"]}]
          result (nrepl/merge-response msgs)]
      (is (= #{"evaluating" "done" "ok"} (:status result)))))

  (testing "preserves last namespace"
    (let [msgs [{:id "1" :ns "user" :value "1"}
                {:id "1" :ns "foo.bar" :value "2"}
                {:id "1" :ns "baz.qux" :value "3"}]
          result (nrepl/merge-response msgs)]
      (is (= "baz.qux" (:ns result)))
      (is (= ["1" "2" "3"] (:value result)))))

  (testing "handles exception fields"
    (let [msgs [{:id "1" :ex "NPE" :root-ex "RootException"}]
          result (nrepl/merge-response msgs)]
      (is (= "NPE" (:ex result)))
      (is (= "RootException" (:root-ex result)))))

  (testing "merges complete eval sequence"
    (let [msgs [{:id "123" :out "printing...\n"}
                {:id "123" :ns "user" :value "nil"}
                {:id "123" :ns "user" :value "42"}
                {:id "123" :status ["done"]}]
          result (nrepl/merge-response msgs)]
      (is (= ["nil" "42"] (:value result)))
      (is (= "printing...\n" (:out result)))
      (is (= "user" (:ns result)))
      (is (contains? (:status result) "done"))))

  (testing "handles empty message sequence"
    (let [result (nrepl/merge-response [])]
      (is (map? result))
      (is (nil? (:value result))))))

;; ============================================================================
;; Connection map tests
;; ============================================================================

(deftest make-connection-test
  (testing "creates connection map with required fields"
    (let [socket (java.net.Socket.)
          out (java.io.ByteArrayOutputStream.)
          in (java.io.ByteArrayInputStream. (.getBytes "test"))
          conn (nrepl/make-connection socket out in "localhost" 7888)]
      (is (= socket (:socket conn)))
      (is (= out (:output conn)))
      (is (= in (:input conn)))
      (is (= "localhost" (:host conn)))
      (is (= 7888 (:port conn)))))

  (testing "creates connection map with optional fields"
    (let [socket (java.net.Socket.)
          out (java.io.ByteArrayOutputStream.)
          in (java.io.ByteArrayInputStream. (.getBytes "test"))
          conn (nrepl/make-connection socket out in "localhost" 7888
                                      :session-id "session-123"
                                      :nrepl-env :clj)]
      (is (= "session-123" (:session-id conn)))
      (is (= :clj (:nrepl-env conn))))))

;; ============================================================================
;; Lazy sequence pipeline integration tests
;; ============================================================================

(deftest lazy-pipeline-integration-test
  (testing "full lazy pipeline from raw messages to filtered results"
    (let [;; Simulate raw bencode messages
          raw-msgs [{"id" "abc" "value" (.getBytes "1")}
                    {"id" "def" "value" (.getBytes "2")}
                    {"id" "abc" "status" [(.getBytes "done")]}
                    nil]
          ;; Build lazy pipeline
          result (->> raw-msgs
                      (nrepl/decode-messages)
                      (nrepl/filter-id "abc")
                      (nrepl/take-until-done)
                      (doall))]
      (is (= 2 (count result)))
      (is (= "1" (:value (first result))))
      (is (= ["done"] (:status (second result))))))

  (testing "pipeline with session and id filtering"
    (let [raw-msgs [{"id" "1" "session" "s1" "value" (.getBytes "a")}
                    {"id" "2" "session" "s1" "value" (.getBytes "b")}
                    {"id" "1" "session" "s1" "status" [(.getBytes "done")]}
                    {"id" "1" "session" "s2" "value" (.getBytes "c")}
                    nil]
          result (->> raw-msgs
                      (nrepl/decode-messages)
                      (nrepl/filter-session "s1")
                      (nrepl/filter-id "1")
                      (nrepl/take-until-done)
                      (doall))]
      (is (= 2 (count result)))
      (is (= "a" (:value (first result))))
      (is (= ["done"] (:status (second result))))))

  (testing "pipeline with merge-response"
    (let [raw-msgs [{"id" "x" "out" "line1\n"}
                    {"id" "x" "value" (.getBytes "42")}
                    {"id" "x" "status" [(.getBytes "done")]}
                    nil]
          result (->> raw-msgs
                      (nrepl/decode-messages)
                      (nrepl/filter-id "x")
                      (nrepl/take-until-done)
                      (nrepl/merge-response))]
      (is (= ["42"] (:value result)))
      (is (= "line1\n" (:out result)))
      (is (contains? (:status result) "done")))))

;; ============================================================================
;; Connection-based API tests (with * suffix)
;; ============================================================================

(deftest connection-based-api-test
  (testing "describe-nrepl* with connection"
    (let [raw-msgs [{"id" "1" "versions" {:clojure {:major 1}} "ops" {:eval {}} "status" [(.getBytes "done")]}]
          msgs (nrepl/decode-messages raw-msgs)
          conn {:input nil :output nil :host "localhost" :port 7888}
          result (with-redefs [nrepl/messages-for-id (fn [_ _] msgs)]
                   (nrepl/describe-nrepl* conn))]
      (is (= {:clojure {:major 1}} (:versions result)))
      (is (= {:eval {}} (:ops result)))))

  (testing "eval-nrepl* with connection"
    (let [raw-msgs [{"id" "1" "value" "42" "ns" "user" "status" [(.getBytes "done")]}]
          msgs (nrepl/decode-messages raw-msgs)
          conn {:input nil :output nil :host "localhost" :port 7888}
          result (with-redefs [nrepl/messages-for-id (fn [_ _] msgs)]
                   (nrepl/eval-nrepl* conn "(+ 1 2)"))]
      (is (= ["42"] (:value result)))
      (is (= "user" (:ns result)))))

  (testing "clone-session* with connection"
    (let [raw-msgs [{"id" "1" "new-session" "session-123" "status" [(.getBytes "done")]}]
          msgs (nrepl/decode-messages raw-msgs)
          conn {:input nil :output nil :host "localhost" :port 7888}
          result (with-redefs [nrepl/messages-for-id (fn [_ _] msgs)]
                   (nrepl/clone-session* conn))]
      (is (= "session-123" (:new-session result)))))

  (testing "ls-sessions* with connection"
    (let [raw-msgs [{"id" "1" "sessions" [(.getBytes "s1") (.getBytes "s2")] "status" [(.getBytes "done")]}]
          msgs (nrepl/decode-messages raw-msgs)
          conn {:input nil :output nil :host "localhost" :port 7888}
          result (with-redefs [nrepl/messages-for-id (fn [_ _] msgs)]
                   (nrepl/ls-sessions* conn))]
      (is (= ["s1" "s2"] (:sessions result))))))

;; ============================================================================
;; Edge cases and error handling
;; ============================================================================

(deftest edge-cases-test
  (testing "handles nil in message sequence gracefully"
    (let [msgs [nil {:id "1"} nil]
          decoded (nrepl/decode-messages msgs)]
      (is (= 0 (count decoded)))))

  (testing "handles empty message sequence"
    (let [msgs []
          result (->> msgs
                      (nrepl/decode-messages)
                      (nrepl/filter-id "x")
                      (nrepl/take-until-done)
                      (doall))]
      (is (empty? result))))

  (testing "handles messages without expected fields"
    (let [msgs [{:id "1"}  ; no value
                {:value "42"}  ; no id
                {:id "1" :status ["done"]}]
          result (->> msgs
                      (nrepl/filter-id "1")
                      (nrepl/take-until-done)
                      (nrepl/merge-response))]
      (is (map? result))
      (is (nil? (:value result)))
      (is (contains? (:status result) "done"))))

  (testing "handles multiple done statuses"
    (let [msgs [{:id "1" :value "1" :status ["done"]}
                {:id "1" :value "2" :status ["done"]}]
          result (nrepl/take-until-done msgs)]
      ;; Should stop at first done
      (is (= 1 (count result)))
      (is (= "1" (:value (first result))))))

  (testing "preserves laziness through pipeline"
    (let [counter (atom 0)
          msgs (map (fn [m] (swap! counter inc) m)
                    [{:id "1" :value "a"}
                     {:id "1" :value "b"}
                     {:id "1" :status ["done"]}
                     {:id "1" :value "should-not-process"}])
          ;; Build lazy pipeline without forcing
          pipeline (nrepl/take-until-done msgs)]
      ;; Before realization
      (is (= 0 @counter))
      ;; Force realization
      (doall pipeline)
      ;; Should have processed only up to done (may process 1 extra due to chunking)
      (is (<= 3 @counter 4)))))
