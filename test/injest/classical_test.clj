(ns injest.classical-test
  "Functional tests for injest.classical — standard (non-path) threading with
   transducer composition, pipeline parallelism, and fold parallelism."
  (:require [clojure.test :refer :all]
            [injest.classical :refer :all]))

;; ===================================================================
;; x> — thread-first with transducer composition
;; ===================================================================

(deftest classical-x>-basic
  (testing "x> with no transducers acts like ->"
    (is (= 3 (x> 1 (+ 2))))
    (is (= 4 (x> 1 (+ 2) (+ 1)))))

  (testing "x> threads as first arg"
    (is (= [1 2 3] (x> [1 2] (conj 3))))
    (is (= {:a 1 :b 2} (x> {:a 1} (assoc :b 2))))))

(deftest classical-x>-with-transducers
  (testing "x> composes map transducer"
    (is (= [2 3 4] (vec (x> [1 2 3] (map inc))))))

  (testing "x> composes map and filter"
    (is (= [2 4] (vec (x> [1 2 3 4] (map inc) (filter even?))))))

  (testing "x> with transducer and vec"
    (is (= [2 4] (x> [1 2 3 4] (map inc) (filter even?) vec)))))

;; ===================================================================
;; x>> — thread-last with transducer composition
;; ===================================================================

(deftest classical-x>>-basic
  (testing "x>> with no transducers acts like ->>"
    (is (= 3 (x>> 1 (+ 2))))
    (is (= [1 2 3 99] (x>> 99 (conj [1 2 3])))))

  (testing "x>> threads as last arg"
    (is (= '(0 1 2) (x>> 3 (range))))))

(deftest classical-x>>-with-transducers
  (testing "x>> composes map transducer"
    (is (= [2 3 4] (vec (x>> [1 2 3] (map inc))))))

  (testing "x>> composes map and filter"
    (is (= [2 4] (vec (x>> [1 2 3 4] (map inc) (filter even?))))))

  (testing "x>> full pipeline"
    (is (= 1044
           (x>> (range 100)
                (map inc)
                (filter odd?)
                (mapcat #(do [% (dec %)]))
                (partition-by #(= 0 (mod % 5)))
                (map (partial apply +))
                (map (partial + 10))
                (map #(do {:temp-value %}))
                (map :temp-value)
                (filter even?)
                (apply +)))))

  (testing "x>> with transducers then apply"
    (is (= 9 (x>> [1 2 3] (map inc) (apply +)))))

  (testing "x>> with non-transducer then transducers"
    (is (= [1 2 3 4 5]
           (vec (x>> 5 (range) (map inc)))))))

;; ===================================================================
;; |> — pipeline parallel, thread-first
;; ===================================================================

(deftest classical-|>-basic
  (testing "|> with stateless transducers produces correct results"
    (is (= [2 3 4] (vec (|> [1 2 3] (map inc))))))

  (testing "|> with map and filter"
    (is (= [2 4] (vec (|> [1 2 3 4] (map inc) (filter even?))))))

  (testing "|> with no transducers acts like ->"
    (is (= 3 (|> 1 (+ 2))))))

;; ===================================================================
;; |>> — pipeline parallel, thread-last
;; ===================================================================

(deftest classical-|>>-basic
  (testing "|>> with stateless transducers produces correct results"
    (is (= [2 3 4] (vec (|>> [1 2 3] (map inc))))))

  (testing "|>> full pipeline"
    (is (= 1044
           (|>> (range 100)
                (map inc)
                (filter odd?)
                (mapcat #(do [% (dec %)]))
                (partition-by #(= 0 (mod % 5)))
                (map (partial apply +))
                (map (partial + 10))
                (map #(do {:temp-value %}))
                (map :temp-value)
                (filter even?)
                (apply +)))))

  (testing "|>> with apply"
    (is (= 9 (|>> [1 2 3] (map inc) (apply +))))))

;; ===================================================================
;; => — fold parallel, thread-first
;; ===================================================================

(deftest classical-=>-basic
  (testing "=> with stateless transducers produces correct results"
    (is (= [2 3 4] (vec (=> [1 2 3] (map inc))))))

  (testing "=> with map and filter"
    (is (= [2 4] (vec (=> [1 2 3 4] (map inc) (filter even?))))))

  (testing "=> with no transducers"
    (is (= 3 (=> 1 (+ 2))))))

;; ===================================================================
;; =>> — fold parallel, thread-last
;; ===================================================================

(deftest classical-=>>-basic
  (testing "=>> with stateless transducers produces correct results"
    (is (= [2 3 4] (vec (=>> [1 2 3] (map inc))))))

  (testing "=>> full pipeline"
    (is (= 1044
           (=>> (range 100)
                (map inc)
                (filter odd?)
                (mapcat #(do [% (dec %)]))
                (partition-by #(= 0 (mod % 5)))
                (map (partial apply +))
                (map (partial + 10))
                (map #(do {:temp-value %}))
                (map :temp-value)
                (filter even?)
                (apply +)))))

  (testing "=>> with apply"
    (is (= 9 (=>> [1 2 3] (map inc) (apply +))))))

;; ===================================================================
;; Thread-first vs thread-last semantics
;; ===================================================================

(deftest classical-threading-direction
  (testing "x> threads as first arg for multi-arg functions"
    (is (= [10 1 2 3] (x> 10 (vector 1 2 3)))))

  (testing "x>> threads as last arg for multi-arg functions"
    (is (= [1 2 3 10] (x>> 10 (vector 1 2 3)))))

  (testing "direction doesn't matter for single-arg functions"
    (is (= (x> 5 (inc) (dec) (str))
           (x>> 5 (inc) (dec) (str))))))
