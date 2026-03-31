(ns injest.transducer-detection-test
  "Unit tests for transducer detection, registration, and thread grouping."
  (:require [clojure.test :refer :all]
            [injest.impl :as i]
            [injest.state :as s]
            [injest.data :as d]))

;; ===================================================================
;; transducable? predicate
;; ===================================================================

(deftest transducable?-positive-cases
  (testing "standard transducers are detected"
    (is (i/transducable? '(clojure.core/map inc)))
    (is (i/transducable? '(clojure.core/filter odd?)))
    (is (i/transducable? '(clojure.core/mapcat identity)))
    (is (i/transducable? '(clojure.core/keep identity)))
    (is (i/transducable? '(clojure.core/remove nil?))))

  (testing "stateful transducers are also transducable"
    (is (i/transducable? '(clojure.core/partition-by identity)))
    (is (i/transducable? '(clojure.core/partition-all 3)))
    (is (i/transducable? '(clojure.core/take 5)))
    (is (i/transducable? '(clojure.core/drop 2)))
    (is (i/transducable? '(clojure.core/distinct)))
    (is (i/transducable? '(clojure.core/dedupe))))

  (testing "cat is transducable (special case)"
    (is (i/transducable? cat))))

(deftest transducable?-negative-cases
  (testing "non-transducer functions are not detected"
    (is (not (i/transducable? '(clojure.core/apply +))))
    (is (not (i/transducable? '(clojure.core/reduce +))))
    (is (not (i/transducable? '(clojure.core/into [])))))

  (testing "non-sequential forms are not transducable"
    (is (not (i/transducable? :keyword)))
    (is (not (i/transducable? 42)))
    (is (not (i/transducable? "string")))
    (is (not (i/transducable? 'symbol))))

  (testing "nil is not transducable"
    (is (not (i/transducable? nil)))))

;; ===================================================================
;; par-transducable? predicate
;; ===================================================================

(deftest par-transducable?-positive-cases
  (testing "stateless transducers are par-transducable"
    (is (i/par-transducable? '(clojure.core/map inc)))
    (is (i/par-transducable? '(clojure.core/filter odd?)))
    (is (i/par-transducable? '(clojure.core/mapcat identity)))
    (is (i/par-transducable? '(clojure.core/keep identity)))
    (is (i/par-transducable? '(clojure.core/remove nil?)))
    (is (i/par-transducable? '(clojure.core/dedupe))))

  (testing "cat is par-transducable (special case)"
    (is (i/par-transducable? cat))))

(deftest par-transducable?-negative-cases
  (testing "stateful transducers are NOT par-transducable"
    (is (not (i/par-transducable? '(clojure.core/partition-by identity))))
    (is (not (i/par-transducable? '(clojure.core/partition-all 3))))
    (is (not (i/par-transducable? '(clojure.core/take 5))))
    (is (not (i/par-transducable? '(clojure.core/drop 2))))
    (is (not (i/par-transducable? '(clojure.core/take-nth 3))))
    (is (not (i/par-transducable? '(clojure.core/drop-while pos?)))))

  (testing "non-transducer functions are not par-transducable"
    (is (not (i/par-transducable? '(clojure.core/apply +))))
    (is (not (i/par-transducable? '(clojure.core/reduce +))))))

;; ===================================================================
;; Registration sets
;; ===================================================================

(deftest default-registrations
  (testing "def-regs is a superset of par-regs"
    (is (every? (fn [sym] (contains? d/def-regs sym)) d/par-regs)))

  (testing "def-regs contains stateful transducers not in par-regs"
    (is (contains? d/def-regs 'clojure.core/partition-by))
    (is (contains? d/def-regs 'clojure.core/partition-all))
    (is (contains? d/def-regs 'clojure.core/take))
    (is (contains? d/def-regs 'clojure.core/drop))
    (is (not (contains? d/par-regs 'clojure.core/partition-by)))
    (is (not (contains? d/par-regs 'clojure.core/partition-all)))
    (is (not (contains? d/par-regs 'clojure.core/take)))
    (is (not (contains? d/par-regs 'clojure.core/drop))))

  (testing "atoms are populated from data"
    (is (contains? @s/transducables 'clojure.core/map))
    (is (contains? @s/transducables 'clojure.core/filter))
    (is (contains? @s/transducables 'clojure.core/partition-by))
    (is (contains? @s/par-transducables 'clojure.core/map))
    (is (contains? @s/par-transducables 'clojure.core/filter))
    (is (not (contains? @s/par-transducables 'clojure.core/partition-by)))))

(deftest custom-registration
  (testing "regxf! adds to transducables atom"
    (let [before (contains? @s/transducables 'my.ns/custom-xf)]
      (is (not before))
      (s/regxf! 'my.ns/custom-xf)
      (is (contains? @s/transducables 'my.ns/custom-xf))
      ;; cleanup
      (swap! s/transducables disj 'my.ns/custom-xf)))

  (testing "regpxf! adds to par-transducables atom"
    (let [before (contains? @s/par-transducables 'my.ns/custom-pxf)]
      (is (not before))
      (s/regpxf! 'my.ns/custom-pxf)
      (is (contains? @s/par-transducables 'my.ns/custom-pxf))
      ;; cleanup
      (swap! s/par-transducables disj 'my.ns/custom-pxf))))

;; ===================================================================
;; compose-transducer-group
;; ===================================================================

(deftest compose-transducer-group-tests
  (testing "single transducer without args"
    (let [xf (i/compose-transducer-group [[map inc]])]
      (is (= [2 3 4] (into [] xf [1 2 3])))))

  (testing "single transducer with args"
    (let [xf (i/compose-transducer-group [[filter odd?]])]
      (is (= [1 3 5] (into [] xf [1 2 3 4 5])))))

  (testing "multiple transducers composed"
    (let [xf (i/compose-transducer-group [[map inc] [filter even?]])]
      (is (= [2 4 6] (into [] xf [1 2 3 4 5])))))

  (testing "transducer with multiple args"
    (let [xf (i/compose-transducer-group [[partition-all 2]])]
      (is (= [[1 2] [3 4] [5]] (into [] xf [1 2 3 4 5]))))))

;; ===================================================================
;; xfn — sequential transducer executor
;; ===================================================================

(deftest xfn-tests
  (testing "xfn with map"
    (let [f (i/xfn [[map inc]])]
      (is (= [2 3 4] (vec (f [1 2 3]))))))

  (testing "xfn with map and filter composed"
    (let [f (i/xfn [[map inc] [filter even?]])]
      (is (= [2 4] (vec (f [1 2 3 4]))))))

  (testing "xfn preserves order"
    (let [f (i/xfn [[map inc]])]
      (is (= (range 2 102) (vec (f (range 1 101))))))))

;; ===================================================================
;; fold-xfn — parallel fold executor
;; ===================================================================

(deftest fold-xfn-tests
  (testing "fold-xfn produces same results as xfn for map"
    (let [seq-f  (i/xfn [[map inc]])
          fold-f (i/fold-xfn [[map inc]])]
      (is (= (vec (seq-f (range 100)))
             (vec (fold-f (range 100)))))))

  (testing "fold-xfn produces same results as xfn for filter"
    (let [seq-f  (i/xfn [[filter even?]])
          fold-f (i/fold-xfn [[filter even?]])]
      (is (= (vec (seq-f (range 100)))
             (vec (fold-f (range 100)))))))

  (testing "fold-xfn with composed transducers"
    (let [seq-f  (i/xfn [[map inc] [filter odd?]])
          fold-f (i/fold-xfn [[map inc] [filter odd?]])]
      (is (= (vec (seq-f (range 100)))
             (vec (fold-f (range 100))))))))

;; ===================================================================
;; pipeline-xfn — async pipeline executor
;; ===================================================================

(deftest pipeline-xfn-tests
  (testing "pipeline-xfn produces same results as xfn for map"
    (let [seq-f      (i/xfn [[map inc]])
          pipeline-f (i/pipeline-xfn [[map inc]])]
      (is (= (vec (seq-f (range 100)))
             (vec (pipeline-f (range 100)))))))

  (testing "pipeline-xfn produces same results for filter"
    (let [seq-f      (i/xfn [[filter even?]])
          pipeline-f (i/pipeline-xfn [[filter even?]])]
      (is (= (vec (seq-f (range 100)))
             (vec (pipeline-f (range 100)))))))

  (testing "pipeline-xfn preserves order"
    (let [f (i/pipeline-xfn [[map inc]])]
      (is (= (range 1 101) (vec (f (range 100))))))))
