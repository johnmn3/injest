(ns injest.path-unit-test
  "Unit tests for the path navigation primitives: path->, path->>, get-or-nth."
  (:require [clojure.test :refer :all]
            [injest.impl :as i]))

;; ===================================================================
;; get-or-nth
;; ===================================================================

(deftest get-or-nth-on-maps
  (testing "integer key in map"
    (is (= :b (i/get-or-nth {0 :a 2 :b} 2))))

  (testing "nil key in map"
    (is (= 2 (i/get-or-nth {0 :a nil 2} nil))))

  (testing "false key in map"
    (is (= 2 (i/get-or-nth {0 :a false 2} false))))

  (testing "true key in map"
    (is (= :yes (i/get-or-nth {true :yes false :no} true))))

  (testing "string key in map"
    (is (= :val (i/get-or-nth {"key" :val} "key"))))

  (testing "missing key returns nil"
    (is (nil? (i/get-or-nth {0 :a 1 :b} 5)))))

(deftest get-or-nth-on-vectors
  (testing "valid index"
    (is (= :c (i/get-or-nth [:a :b :c] 2))))

  (testing "first element"
    (is (= :a (i/get-or-nth [:a :b :c] 0))))

  (testing "last element"
    (is (= :c (i/get-or-nth [:a :b :c] 2))))

  (testing "nested vector"
    (is (= [3 4] (i/get-or-nth [[1 2] [3 4]] 1)))))

(deftest get-or-nth-on-sequences
  (testing "list indexing"
    (is (= 3 (i/get-or-nth '(1 2 3) 2))))

  (testing "lazy seq indexing"
    (is (= 5 (i/get-or-nth (range 10) 5))))

  (testing "first of sequence"
    (is (= 0 (i/get-or-nth (range 10) 0)))))

;; ===================================================================
;; path-> (thread-first path primitive)
;; ===================================================================

(deftest path-thread-first-fn-call
  (testing "seq form threads x as first arg"
    (let [result (i/path-> '(f a b) 'x)]
      (is (= '(f x a b) result))))

  (testing "single-element seq form threads x as only arg"
    (let [result (i/path-> '(inc) 'x)]
      (is (= '(inc x) result)))))

(deftest path-thread-first-string
  (testing "string form becomes (x string)"
    (let [result (i/path-> "key" 'x)]
      (is (= '(x "key") result)))))

(deftest path-thread-first-nil
  (testing "nil form becomes (x nil)"
    (let [result (i/path-> nil 'x)]
      (is (= '(x nil) result)))))

(deftest path-thread-first-boolean
  (testing "true form becomes (x true)"
    (let [result (i/path-> true 'x)]
      (is (= '(x true) result))))

  (testing "false form becomes (x false)"
    (let [result (i/path-> false 'x)]
      (is (= '(x false) result)))))

(deftest path-thread-first-integer
  (testing "integer form becomes (get-or-nth x int)"
    (let [result (i/path-> 2 'x)]
      (is (= (list 'injest.impl/get-or-nth 'x 2) result)))))

(deftest path-thread-first-keyword
  (testing "keyword form becomes (keyword x)"
    (let [result (i/path-> :a 'x)]
      (is (= '(:a x) result)))))

(deftest path-thread-first-symbol
  (testing "bare symbol form becomes (symbol x)"
    (let [result (i/path-> 'inc 'x)]
      (is (= '(inc x) result)))))

(deftest path-thread-first-protected-fns
  (testing "fn form is NOT threaded into"
    (let [result (i/path-> '(fn [y] (inc y)) 'x)]
      ;; fn is protected, so goes to :else branch: (form x)
      (is (= (list '(fn [y] (inc y)) 'x) result))))

  (testing "partial form is NOT threaded into"
    (let [result (i/path-> '(partial inc) 'x)]
      (is (= (list '(partial inc) 'x) result)))))

;; ===================================================================
;; path->> (thread-last path primitive)
;; ===================================================================

(deftest path-thread-last-fn-call
  (testing "seq form threads x as last arg"
    (let [result (i/path->> '(f a b) 'x)]
      (is (= '(f a b x) result))))

  (testing "single-element seq form threads x as only arg"
    (let [result (i/path->> '(inc) 'x)]
      (is (= '(inc x) result)))))

(deftest path-thread-last-string
  (testing "string form becomes (x string) — same as thread-first"
    (let [result (i/path->> "key" 'x)]
      (is (= '(x "key") result)))))

(deftest path-thread-last-nil
  (testing "nil form becomes (x nil) — same as thread-first"
    (let [result (i/path->> nil 'x)]
      (is (= '(x nil) result)))))

(deftest path-thread-last-boolean
  (testing "boolean forms same as thread-first"
    (is (= '(x true) (i/path->> true 'x)))
    (is (= '(x false) (i/path->> false 'x)))))

(deftest path-thread-last-integer
  (testing "integer form becomes (get-or-nth x int) — same as thread-first"
    (let [result (i/path->> 2 'x)]
      (is (= (list 'injest.impl/get-or-nth 'x 2) result)))))

(deftest path-thread-last-keyword
  (testing "keyword form becomes (keyword x)"
    (let [result (i/path->> :a 'x)]
      (is (= '(:a x) result)))))

;; ===================================================================
;; path-> vs path->> difference
;; ===================================================================

(deftest path-thread-direction-difference
  (testing "multi-arg fn call: -> threads first, ->> threads last"
    (is (= '(f x a b) (i/path-> '(f a b) 'x)))
    (is (= '(f a b x) (i/path->> '(f a b) 'x))))

  (testing "for non-seq forms (string, nil, bool, int), both directions are identical"
    (is (= (i/path-> "key" 'x) (i/path->> "key" 'x)))
    (is (= (i/path-> nil 'x) (i/path->> nil 'x)))
    (is (= (i/path-> true 'x) (i/path->> true 'x)))
    (is (= (i/path-> 2 'x) (i/path->> 2 'x)))
    (is (= (i/path-> :a 'x) (i/path->> :a 'x)))))
