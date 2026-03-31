(ns injest.edge-cases-test
  "Edge case tests: empty threads, nil inputs, single forms, nesting,
   large collections, deeply nested paths, identity, duplicates."
  (:require [clojure.test :refer :all]
            [injest.path :as p]
            [injest.classical :as c]))

;; ===================================================================
;; Empty threads — (macro x) should return x unchanged
;; ===================================================================

(deftest empty-thread-returns-identity
  (testing "+> with no forms returns x"
    (is (= 42 (p/+> 42)))
    (is (= nil (p/+> nil)))
    (is (= {:a 1} (p/+> {:a 1}))))

  (testing "+>> with no forms returns x"
    (is (= 42 (p/+>> 42)))
    (is (= "hello" (p/+>> "hello"))))

  (testing "x> with no forms returns x"
    (is (= [1 2 3] (p/x> [1 2 3]))))

  (testing "x>> with no forms returns x"
    (is (= [1 2 3] (p/x>> [1 2 3]))))

  (testing "|> with no forms returns x"
    (is (= :foo (p/|> :foo))))

  (testing "|>> with no forms returns x"
    (is (= :foo (p/|>> :foo))))

  (testing "=> with no forms returns x"
    (is (= 99 (p/=> 99))))

  (testing "=>> with no forms returns x"
    (is (= 99 (p/=>> 99))))

  (testing "classical x> with no forms returns x"
    (is (= 42 (c/x> 42))))

  (testing "classical x>> with no forms returns x"
    (is (= 42 (c/x>> 42)))))

;; ===================================================================
;; Single-form threads
;; ===================================================================

(deftest single-form-thread
  (testing "+> with single keyword lookup"
    (is (= 1 (p/+> {:a 1} :a))))

  (testing "+>> with single keyword lookup"
    (is (= 1 (p/+>> {:a 1} :a))))

  (testing "+> with single integer index"
    (is (= :b (p/+> [:a :b :c] 1))))

  (testing "+>> with single integer index"
    (is (= :b (p/+>> [:a :b :c] 1))))

  (testing "+> with single string lookup"
    (is (= 42 (p/+> {"k" 42} "k"))))

  (testing "+> with single fn"
    (is (= 2 (p/+> 1 inc))))

  (testing "x>> with single transducer"
    (is (= [2 3 4] (vec (p/x>> [1 2 3] (map inc))))))

  (testing "classical x>> with single transducer"
    (is (= [2 3 4] (vec (c/x>> [1 2 3] (map inc)))))))

;; ===================================================================
;; Nil as initial value
;; ===================================================================

(deftest nil-initial-value
  (testing "+> with nil and keyword returns nil (nil doesn't have keys)"
    (is (nil? (p/+> nil :a))))

  (testing "+>> with nil and keyword returns nil"
    (is (nil? (p/+>> nil :a))))

  (testing "x>> with nil and map passes nil through"
    (is (= [nil nil nil] (vec (p/x>> [nil nil nil] (map identity)))))))

;; ===================================================================
;; Empty collection inputs
;; ===================================================================

(deftest empty-collection-input
  (testing "x>> with empty vector"
    (is (= [] (vec (p/x>> [] (map inc))))))

  (testing "x>> with empty list"
    (is (= [] (vec (p/x>> '() (map inc))))))

  (testing "|>> with empty vector"
    (is (= [] (vec (p/|>> [] (map inc))))))

  (testing "=>> with empty vector"
    (is (= [] (vec (p/=>> [] (map inc))))))

  (testing "classical x>> with empty vector"
    (is (= [] (vec (c/x>> [] (map inc)))))))

;; ===================================================================
;; Large collection correctness
;; ===================================================================

(deftest large-collection
  (testing "x>> with 100k elements"
    (is (= (apply + (map inc (range 100000)))
           (p/x>> (range 100000) (map inc) (apply +)))))

  (testing "|>> with 100k elements"
    (is (= (apply + (map inc (range 100000)))
           (p/|>> (range 100000) (map inc) (apply +)))))

  (testing "=>> with 100k elements"
    (is (= (apply + (map inc (range 100000)))
           (p/=>> (range 100000) (map inc) (apply +))))))

;; ===================================================================
;; Deeply nested path navigation
;; ===================================================================

(deftest deeply-nested-paths
  (testing "+> navigating 5 levels deep"
    (is (= :found
           (p/+> {:a {:b {:c {:d {:e :found}}}}}
                 :a :b :c :d :e))))

  (testing "+>> navigating 5 levels deep"
    (is (= :found
           (p/+>> {:a {:b {:c {:d {:e :found}}}}}
                  :a :b :c :d :e))))

  (testing "+> mixed path types deep"
    (is (= :val
           (p/+> {0 [nil {:a {"key" [0 :val]}}]}
                 0 1 :a "key" 1))))

  (testing "+>> mixed path types deep"
    (is (= :val
           (p/+>> {0 [nil {:a {"key" [0 :val]}}]}
                  0 1 :a "key" 1)))))

;; ===================================================================
;; Mixed path types in one thread
;; ===================================================================

(deftest mixed-path-types
  (testing "integer, string, keyword, nil in one thread"
    (let [data {0 {"a" {:b {nil :result}}}}]
      (is (= :result (p/+> data 0 "a" :b nil)))
      (is (= :result (p/+>> data 0 "a" :b nil)))))

  (testing "integer, boolean, keyword in one thread"
    (let [data {1 {true {:k :val}}}]
      (is (= :val (p/+> data 1 true :k)))
      (is (= :val (p/+>> data 1 true :k)))))

  (testing "path navigation then function call"
    (is (= "1" (p/+> {:a 1} :a str)))
    (is (= "1" (p/+>> {:a 1} :a str)))))

;; ===================================================================
;; Identity passthrough
;; ===================================================================

(deftest identity-passthrough
  (testing "+> with identity"
    (is (= 42 (p/+> 42 identity))))

  (testing "+>> with identity"
    (is (= 42 (p/+>> 42 identity))))

  (testing "x>> with (map identity) preserves collection"
    (is (= [1 2 3] (vec (p/x>> [1 2 3] (map identity)))))))

;; ===================================================================
;; Duplicate transducers
;; ===================================================================

(deftest duplicate-transducers
  (testing "x>> with two (map inc) applies both"
    (is (= [3 4 5] (vec (p/x>> [1 2 3] (map inc) (map inc))))))

  (testing "x>> with (filter even?) twice"
    (is (= [2 4] (vec (p/x>> [1 2 3 4 5] (filter even?) (filter even?))))))

  (testing "|>> with duplicate transducers"
    (is (= [3 4 5] (vec (p/|>> [1 2 3] (map inc) (map inc)))))))

;; ===================================================================
;; Nested macro calls
;; ===================================================================

(deftest nested-macro-calls
  (testing "+> inside +>"
    (is (= 2 (p/+> {:a {:b 2}}
                    :a
                    (p/+> :b)))))

  (testing "x>> as argument in x>>"
    ;; (map inc) on [1 2 3] = (2 3 4)
    ;; inner x>>: (map inc) on [0 1 2] = (1 2 3), (apply + (1 2 3)) = 6
    ;; (map #(+ % 6)) on (2 3 4) = (8 9 10)
    ;; (apply + (8 9 10)) = 27
    (is (= 27
           (p/x>> [1 2 3]
                  (map inc)
                  (map #(+ % (p/x>> [0 1 2] (map inc) (apply +))))
                  (apply +))))))

;; ===================================================================
;; Transducer with non-transducer interleaved
;; ===================================================================

(deftest interleaved-transducers-and-fns
  (testing "x>> with transducers then apply"
    ;; (map inc) (filter odd?) compose as transducers on [1 2 3 4]:
    ;; [1 2 3 4] -> inc -> [2 3 4 5] -> filter odd? -> [3 5]
    ;; (apply + [3 5]) = 8
    (is (= 8 (p/x>> [1 2 3 4] (map inc) (filter odd?) (apply +)))))

  (testing "x>> with non-xf, xfs, non-xf"
    ;; (range 10) = (0..9), (map inc) (filter even?) compose:
    ;; (0..9) -> inc -> (1..10) -> filter even? -> (2 4 6 8 10)
    ;; (apply max (2 4 6 8 10)) = 10
    (is (= 10
           (p/x>> 10
                  (range)
                  (map inc)
                  (filter even?)
                  (apply max)))))

  (testing "classical x>> with transducers then apply"
    (is (= 8 (c/x>> [1 2 3 4] (map inc) (filter odd?) (apply +))))))

;; ===================================================================
;; Boolean and nil as path keys (regression)
;; ===================================================================

(deftest boolean-nil-path-keys
  (testing "+> with false key"
    (is (= :no (p/+> {true :yes false :no} false))))

  (testing "+> with nil key"
    (is (= :none (p/+> {nil :none 0 :zero} nil))))

  (testing "+>> with false key"
    (is (= :no (p/+>> {true :yes false :no} false))))

  (testing "+>> with nil key"
    (is (= :none (p/+>> {nil :none 0 :zero} nil))))

  (testing "chained boolean and nil lookups"
    (let [data {false {nil :deep}}]
      (is (= :deep (p/+> data false nil)))
      (is (= :deep (p/+>> data false nil))))))

;; ===================================================================
;; Sequence types (vectors, lists, lazy seqs)
;; ===================================================================

(deftest various-sequence-types
  (testing "x>> works on vectors"
    (is (= [2 3 4] (vec (p/x>> [1 2 3] (map inc))))))

  (testing "x>> works on lists"
    (is (= [2 3 4] (vec (p/x>> '(1 2 3) (map inc))))))

  (testing "x>> works on lazy seqs"
    (is (= [1 2 3 4 5] (vec (p/x>> (range 1 6) (map identity))))))

  (testing "x>> works on sets (unordered)"
    (is (= #{2 3 4} (set (p/x>> #{1 2 3} (map inc)))))))
