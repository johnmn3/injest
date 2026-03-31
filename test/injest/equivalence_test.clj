(ns injest.equivalence-test
  "Tests proving equivalence relationships between injest macro variants.
   These tests ensure the macro family is internally consistent."
  (:require [clojure.test :refer :all]
            [injest.path :as p]
            [injest.classical :as c]))

;; ===================================================================
;; x>> == +>> when no transducers present (path)
;; ===================================================================

(deftest path-x>>-equals-+>>-no-transducers
  (testing "x>> with keywords equals +>> with keywords"
    (is (= (p/+>> {:a {:b 1}} :a :b)
           (p/x>> {:a {:b 1}} :a :b))))

  (testing "x>> with integers equals +>> with integers"
    (is (= (p/+>> [0 [1 2 3]] 1 2)
           (p/x>> [0 [1 2 3]] 1 2))))

  (testing "x>> with strings equals +>> with strings"
    (is (= (p/+>> {"a" {"b" :val}} "a" "b")
           (p/x>> {"a" {"b" :val}} "a" "b"))))

  (testing "x>> with fn calls equals +>> with fn calls"
    (is (= (p/+>> 1 (+ 2) (+ 3))
           (p/x>> 1 (+ 2) (+ 3)))))

  (testing "x>> with mixed path types equals +>>"
    (is (= (p/+>> {1 {"b" [0 1 {:c :res}]}} 1 "b" 2 :c)
           (p/x>> {1 {"b" [0 1 {:c :res}]}} 1 "b" 2 :c)))))

;; ===================================================================
;; x> == +> when no transducers present (path)
;; ===================================================================

(deftest path-x>-equals-+>-no-transducers
  (testing "x> with keywords equals +> with keywords"
    (is (= (p/+> {:a {:b 1}} :a :b)
           (p/x> {:a {:b 1}} :a :b))))

  (testing "x> with integers equals +> with integers"
    (is (= (p/+> [0 [1 2 3]] 1 2)
           (p/x> [0 [1 2 3]] 1 2))))

  (testing "x> with fn calls equals +> with fn calls"
    (is (= (p/+> 1 (+ 2) (+ 3))
           (p/x> 1 (+ 2) (+ 3))))))

;; ===================================================================
;; |>> == x>> for results (parallel produces same values)
;; ===================================================================

(deftest pipeline-equals-sequential-results
  (testing "|>> produces same results as x>> for stateless transducers"
    (is (= (vec (p/x>> (range 1000) (map inc) (filter even?)))
           (vec (p/|>> (range 1000) (map inc) (filter even?))))))

  (testing "|> produces same results as x>"
    (is (= (vec (p/x> [1 2 3 4 5] (map inc) (filter odd?)))
           (vec (p/|> [1 2 3 4 5] (map inc) (filter odd?))))))

  (testing "|>> with full pipeline matches x>>"
    (is (= (p/x>> (range 100)
                   (map inc)
                   (filter odd?)
                   (mapcat #(do [% (dec %)]))
                   (map (partial + 10))
                   (filter even?)
                   (apply +))
           (p/|>> (range 100)
                   (map inc)
                   (filter odd?)
                   (mapcat #(do [% (dec %)]))
                   (map (partial + 10))
                   (filter even?)
                   (apply +))))))

;; ===================================================================
;; =>> == x>> for results (fold produces same values)
;; ===================================================================

(deftest fold-equals-sequential-results
  (testing "=>> produces same results as x>> for map"
    (is (= (vec (p/x>> (range 1000) (map inc)))
           (vec (p/=>> (range 1000) (map inc))))))

  (testing "=>> produces same results as x>> for filter"
    (is (= (vec (p/x>> (range 1000) (filter even?)))
           (vec (p/=>> (range 1000) (filter even?))))))

  (testing "=> produces same results as x>"
    (is (= (vec (p/x> [1 2 3 4 5] (map inc) (filter odd?)))
           (vec (p/=> [1 2 3 4 5] (map inc) (filter odd?))))))

  (testing "=>> with full pipeline matches x>>"
    (is (= (p/x>> (range 100)
                   (map inc)
                   (filter odd?)
                   (mapcat #(do [% (dec %)]))
                   (map (partial + 10))
                   (filter even?)
                   (apply +))
           (p/=>> (range 100)
                   (map inc)
                   (filter odd?)
                   (mapcat #(do [% (dec %)]))
                   (map (partial + 10))
                   (filter even?)
                   (apply +))))))

;; ===================================================================
;; |>> == x>> for stateful-only transducers (can't parallelize)
;; ===================================================================

(deftest parallel-fallback-for-stateful
  (testing "|>> with partition-by (stateful) matches x>>"
    (is (= (vec (p/x>> (range 20)
                        (partition-by #(< % 10))))
           (vec (p/|>> (range 20)
                        (partition-by #(< % 10)))))))

  (testing "=>> with partition-all (stateful) matches x>>"
    (is (= (vec (p/x>> (range 20)
                        (partition-all 5)))
           (vec (p/=>> (range 20)
                        (partition-all 5)))))))

;; ===================================================================
;; All 8 path macros produce same result for simple case
;; ===================================================================

(deftest all-path-macros-equivalent-simple
  (let [data {:a [10 20 30]}
        expected 20]
    (testing "all 8 macros produce same result on simple path navigation"
      (is (= expected (p/+>  data :a 1)))
      (is (= expected (p/+>> data :a 1)))
      (is (= expected (p/x>  data :a 1)))
      (is (= expected (p/x>> data :a 1)))
      (is (= expected (p/|>  data :a 1)))
      (is (= expected (p/|>> data :a 1)))
      (is (= expected (p/=>  data :a 1)))
      (is (= expected (p/=>> data :a 1))))))

;; ===================================================================
;; Classical x>> and path x>> agree on non-path data
;; ===================================================================

(deftest classical-vs-path-on-flat-data
  (testing "classical x>> and path x>> agree when no path nav is used"
    (is (= (c/x>> [1 2 3] (map inc) (filter even?) (apply +))
           (p/x>> [1 2 3] (map inc) (filter even?) (apply +)))))

  (testing "classical x>> and path x>> agree for standard fn calls"
    (is (= (c/x>> 5 (inc) (dec) (str))
           (p/x>> 5 (inc) (dec) (str)))))

  (testing "classical x> and path x> agree for fn calls"
    (is (= (c/x> 1 (+ 2) (+ 3))
           (p/x> 1 (+ 2) (+ 3)))))

  (testing "classical =>> and path =>> agree on flat transducer chains"
    (is (= (c/=>> (range 50) (map inc) (filter odd?) (apply +))
           (p/=>> (range 50) (map inc) (filter odd?) (apply +)))))

  (testing "classical |>> and path |>> agree on flat transducer chains"
    (is (= (c/|>> (range 50) (map inc) (filter odd?) (apply +))
           (p/|>> (range 50) (map inc) (filter odd?) (apply +))))))

;; ===================================================================
;; Thread-first vs thread-last for single-arg functions
;; ===================================================================

(deftest direction-irrelevant-for-single-arg
  (testing "+> and +>> agree for single-arg functions"
    (is (= (p/+> 5 inc dec str)
           (p/+>> 5 inc dec str))))

  (testing "x> and x>> agree for single-arg functions"
    (is (= (p/x> 5 inc dec str)
           (p/x>> 5 inc dec str))))

  (testing "classical x> and x>> agree for single-arg functions"
    (is (= (c/x> 5 (inc) (dec) (str))
           (c/x>> 5 (inc) (dec) (str))))))
