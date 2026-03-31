(ns injest.parallelism-test
  "Tests for parallel execution variants: |>, |>>, =>, =>>
   Verifies correctness, order preservation, and equivalence to sequential."
  (:require [clojure.test :refer :all]
            [injest.path :as p]
            [injest.classical :as c]))

;; ===================================================================
;; |>> — pipeline parallel, thread-last (path)
;; ===================================================================

(deftest path-pipeline-thread-last-correctness
  (testing "|>> with map on small input"
    (is (= [2 3 4 5 6] (vec (p/|>> [1 2 3 4 5] (map inc))))))

  (testing "|>> with filter"
    (is (= [2 4 6 8 10] (vec (p/|>> (range 1 11) (filter even?))))))

  (testing "|>> with map and filter composed"
    (is (= [3 5 7 9] (vec (p/|>> (range 1 10) (map inc) (filter odd?))))))

  (testing "|>> with mapcat"
    (is (= [1 1 2 2 3 3]
           (vec (p/|>> [1 2 3] (mapcat #(vector % %)))))))

  (testing "|>> with apply at the end"
    (is (= 15 (p/|>> [1 2 3 4 5] (map identity) (apply +))))))

;; ===================================================================
;; |> — pipeline parallel, thread-first (path)
;; ===================================================================

(deftest path-pipeline-thread-first-correctness
  (testing "|> with map"
    (is (= [2 3 4] (vec (p/|> [1 2 3] (map inc))))))

  (testing "|> with filter"
    (is (= [1 3 5] (vec (p/|> [1 2 3 4 5] (filter odd?))))))

  (testing "|> mixed transducers and path"
    (is (= [2 3 4] (vec (p/|> {:data [1 2 3]} :data (map inc)))))))

;; ===================================================================
;; =>> — fold parallel, thread-last (path)
;; ===================================================================

(deftest path-fold-thread-last-correctness
  (testing "=>> with map on small input"
    (is (= [2 3 4 5 6] (vec (p/=>> [1 2 3 4 5] (map inc))))))

  (testing "=>> with filter"
    (is (= [2 4 6 8 10] (vec (p/=>> (range 1 11) (filter even?))))))

  (testing "=>> with map and filter composed"
    (is (= [3 5 7 9] (vec (p/=>> (range 1 10) (map inc) (filter odd?))))))

  (testing "=>> with apply at the end"
    (is (= 15 (p/=>> [1 2 3 4 5] (map identity) (apply +))))))

;; ===================================================================
;; => — fold parallel, thread-first (path)
;; ===================================================================

(deftest path-fold-thread-first-correctness
  (testing "=> with map"
    (is (= [2 3 4] (vec (p/=> [1 2 3] (map inc))))))

  (testing "=> with filter"
    (is (= [1 3 5] (vec (p/=> [1 2 3 4 5] (filter odd?))))))

  (testing "=> mixed transducers and path"
    (is (= [2 3 4] (vec (p/=> {:data [1 2 3]} :data (map inc)))))))

;; ===================================================================
;; Order preservation
;; ===================================================================

(deftest order-preservation
  (testing "|>> preserves input order"
    (let [input (range 1000)
          result (vec (p/|>> input (map inc)))]
      (is (= (mapv inc input) result))))

  (testing "=>> preserves input order"
    (let [input (range 1000)
          result (vec (p/=>> input (map inc)))]
      (is (= (mapv inc input) result))))

  (testing "|> preserves input order"
    (let [input (vec (range 1000))
          result (vec (p/|> input (map inc)))]
      (is (= (mapv inc input) result))))

  (testing "=> preserves input order"
    (let [input (vec (range 1000))
          result (vec (p/=> input (map inc)))]
      (is (= (mapv inc input) result)))))

;; ===================================================================
;; Classical parallel variants
;; ===================================================================

(deftest classical-pipeline-correctness
  (testing "classical |>> with map"
    (is (= [2 3 4] (vec (c/|>> [1 2 3] (map inc))))))

  (testing "classical |>> with filter"
    (is (= [2 4] (vec (c/|>> [1 2 3 4 5] (filter even?))))))

  (testing "classical |> with map"
    (is (= [2 3 4] (vec (c/|> [1 2 3] (map inc)))))))

(deftest classical-fold-correctness
  (testing "classical =>> with map"
    (is (= [2 3 4] (vec (c/=>> [1 2 3] (map inc))))))

  (testing "classical =>> with filter"
    (is (= [2 4] (vec (c/=>> [1 2 3 4 5] (filter even?))))))

  (testing "classical => with map"
    (is (= [2 3 4] (vec (c/=> [1 2 3] (map inc)))))))

;; ===================================================================
;; Larger parallel workloads
;; ===================================================================

(deftest larger-parallel-workloads
  (testing "|>> handles 100k elements correctly"
    (let [expected (->> (range 100000) (map inc) (filter even?) vec)]
      (is (= expected (vec (p/|>> (range 100000) (map inc) (filter even?)))))))

  (testing "=>> handles 100k elements correctly"
    (let [expected (->> (range 100000) (map inc) (filter even?) vec)]
      (is (= expected (vec (p/=>> (range 100000) (map inc) (filter even?)))))))

  (testing "all parallel variants agree on large input"
    (let [input (range 10000)
          expected (vec (->> input (map inc) (filter odd?)))]
      (is (= expected (vec (p/x>>  input (map inc) (filter odd?)))))
      (is (= expected (vec (p/|>> input (map inc) (filter odd?)))))
      (is (= expected (vec (p/=>> input (map inc) (filter odd?))))))))

;; ===================================================================
;; Stateful transducers in parallel context
;; ===================================================================

(deftest stateful-transducers-in-parallel
  (testing "|>> with partition-all (stateful, cannot be pipelined separately)"
    (is (= [[1 2] [3 4] [5]]
           (vec (p/|>> [1 2 3 4 5] (partition-all 2))))))

  (testing "=>> with partition-all (stateful, cannot be folded separately)"
    (is (= [[1 2] [3 4] [5]]
           (vec (p/=>> [1 2 3 4 5] (partition-all 2))))))

  (testing "|>> with take (stateful)"
    (is (= [0 1 2] (vec (p/|>> (range 100) (take 3))))))

  (testing "=>> with take (stateful)"
    (is (= [0 1 2] (vec (p/=>> (range 100) (take 3))))))

  (testing "|>> with mixed stateless and stateful"
    (is (= [[2 3] [4 5] [6]]
           (vec (p/|>> [1 2 3 4 5] (map inc) (partition-all 2))))))

  (testing "=>> with mixed stateless and stateful"
    (is (= [[2 3] [4 5] [6]]
           (vec (p/=>> [1 2 3 4 5] (map inc) (partition-all 2)))))))
