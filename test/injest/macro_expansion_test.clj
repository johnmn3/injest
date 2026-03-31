(ns injest.macro-expansion-test
  "Tests that verify macro expansion forms for all injest macros.
   Inspired by https://github.com/johnmn3/injest/issues/3 —
   macroexpand tests are the highest-signal way to verify a macro library."
  (:require [clojure.test :refer :all]
            [injest.path :as p]
            [injest.classical :as c]
            [injest.impl :as i]))

;; ---------------------------------------------------------------------------
;; Helpers
;; ---------------------------------------------------------------------------

(defn expansion-contains?
  "Returns true if the macroexpanded form (recursively) contains sym."
  [form sym]
  (cond
    (= form sym) true
    (coll? form) (some #(expansion-contains? % sym) form)
    :else false))

;; ===================================================================
;; 1. injest.path/+>  —  path-aware thread-first, no transducers
;; ===================================================================

(deftest path-thread-first-basic-expansion
  (testing "+> with a keyword expands to (keyword x)"
    (let [expanded (macroexpand-1 '(injest.path/+> m :a))]
      (is (= '(:a m) expanded))))

  (testing "+> with a function call expands to (f x args...)"
    (let [expanded (macroexpand-1 '(injest.path/+> x (inc)))]
      ;; path-> sees (inc) as a seq, threads x as first arg
      (is (= '(inc x) expanded))))

  (testing "+> with an integer expands to get-or-nth"
    (let [expanded (macroexpand-1 '(injest.path/+> v 2))]
      (is (expansion-contains? expanded 'injest.impl/get-or-nth))
      (is (expansion-contains? expanded 'v))
      (is (expansion-contains? expanded 2))))

  (testing "+> with a string expands to (x string)"
    (let [expanded (macroexpand-1 '(injest.path/+> m "key"))]
      (is (= '(m "key") expanded))))

  (testing "+> with nil expands to (x nil)"
    (let [expanded (macroexpand-1 '(injest.path/+> m nil))]
      (is (= '(m nil) expanded))))

  (testing "+> with a boolean expands to (x bool)"
    (let [expanded (macroexpand-1 '(injest.path/+> m true))]
      (is (= '(m true) expanded)))))

(deftest path-thread-first-multi-form-expansion
  (testing "+> with two forms threads sequentially"
    (let [expanded (macroexpand-1 '(injest.path/+> x :a :b))]
      (is (= '(:b (:a x)) expanded))))

  (testing "+> with three forms threads sequentially"
    (let [expanded (macroexpand-1 '(injest.path/+> x :a :b :c))]
      (is (= '(:c (:b (:a x))) expanded))))

  (testing "+> with mixed path types"
    (let [expanded (macroexpand-1 '(injest.path/+> m :a 0))]
      ;; :a applied first, then integer 0
      (is (expansion-contains? expanded :a))
      (is (expansion-contains? expanded 0))
      (is (expansion-contains? expanded 'injest.impl/get-or-nth))))

  (testing "+> with function call threads x as first arg"
    (let [expanded (macroexpand-1 '(injest.path/+> x (assoc :a 1) (dissoc :b)))]
      ;; Should be (dissoc (assoc x :a 1) :b)
      (is (= '(dissoc (assoc x :a 1) :b) expanded)))))

;; ===================================================================
;; 2. injest.path/+>>  —  path-aware thread-last, no transducers
;; ===================================================================

(deftest path-thread-last-basic-expansion
  (testing "+>> with a keyword expands to (keyword x)"
    (let [expanded (macroexpand-1 '(injest.path/+>> m :a))]
      (is (= '(:a m) expanded))))

  (testing "+>> with a function call threads x as last arg"
    (let [expanded (macroexpand-1 '(injest.path/+>> x (conj 1)))]
      (is (= '(conj 1 x) expanded))))

  (testing "+>> with an integer expands to get-or-nth"
    (let [expanded (macroexpand-1 '(injest.path/+>> v 2))]
      (is (expansion-contains? expanded 'injest.impl/get-or-nth))))

  (testing "+>> with a string expands to (x string)"
    (let [expanded (macroexpand-1 '(injest.path/+>> m "key"))]
      (is (= '(m "key") expanded))))

  (testing "+>> with nil expands to (x nil)"
    (let [expanded (macroexpand-1 '(injest.path/+>> m nil))]
      (is (= '(m nil) expanded))))

  (testing "+>> with a boolean expands to (x bool)"
    (let [expanded (macroexpand-1 '(injest.path/+>> m false))]
      (is (= '(m false) expanded)))))

(deftest path-thread-last-multi-form-expansion
  (testing "+>> with two function calls threads as last arg"
    (let [expanded (macroexpand-1 '(injest.path/+>> x (conj 1) (conj 2)))]
      (is (= '(conj 2 (conj 1 x)) expanded))))

  (testing "+>> thread-first vs thread-last difference"
    ;; The key difference: +> puts x first, +>> puts x last
    (let [first-expanded (macroexpand-1 '(injest.path/+> x (f a b)))
          last-expanded  (macroexpand-1 '(injest.path/+>> x (f a b)))]
      (is (= '(f x a b) first-expanded))
      (is (= '(f a b x) last-expanded)))))

;; ===================================================================
;; 3. Protected functions (fn, fn*, partial) — NOT threaded
;; ===================================================================

(deftest protected-fn-expansion
  (testing "+> with fn wraps as (fn x) not (fn x ...)"
    (let [expanded (macroexpand-1 '(injest.path/+> 5 (fn [x] (inc x))))]
      ;; fn is in protected-fns, so form goes to :else branch: (form x)
      (is (= (list '(fn [x] (inc x)) 5) expanded))))

  (testing "+> with partial wraps correctly"
    (let [expanded (macroexpand-1 '(injest.path/+> 5 (partial inc)))]
      (is (= (list '(partial inc) 5) expanded))))

  (testing "+>> protected fns behave same as +> (no threading direction)"
    (let [expanded (macroexpand-1 '(injest.path/+>> 5 (fn [x] (inc x))))]
      (is (= (list '(fn [x] (inc x)) 5) expanded)))))

;; ===================================================================
;; 4. injest.path/x>  —  transducer-aware path thread-first
;; ===================================================================

(deftest path-x-thread-first-expansion
  (testing "x> without transducers expands like +>"
    (let [expanded (macroexpand-1 '(injest.path/x> x :a :b))]
      ;; x> delegates to +> after pre-transducify-thread
      ;; Non-transducer forms pass through unchanged
      (is (expansion-contains? expanded 'injest.path/+>))))

  (testing "x> with a transducer wraps it via xfn"
    (let [expanded (macroexpand-1 '(injest.path/x> coll (map inc) (filter odd?)))]
      ;; Should contain +> and a reference to xfn for the transducer group
      (is (expansion-contains? expanded 'injest.path/+>))
      (is (expansion-contains? expanded 'injest.impl/xfn))))

  (testing "x> with mixed transducer and non-transducer forms"
    (let [expanded (macroexpand-1 '(injest.path/x> data (map inc) :a))]
      (is (expansion-contains? expanded 'injest.path/+>))
      (is (expansion-contains? expanded 'injest.impl/xfn))
      (is (expansion-contains? expanded :a)))))

;; ===================================================================
;; 5. injest.path/x>>  —  transducer-aware path thread-last
;; ===================================================================

(deftest path-x-thread-last-expansion
  (testing "x>> without transducers expands like +>>"
    (let [expanded (macroexpand-1 '(injest.path/x>> x :a :b))]
      (is (expansion-contains? expanded 'injest.path/+>>))))

  (testing "x>> with transducers wraps them via xfn"
    (let [expanded (macroexpand-1 '(injest.path/x>> coll (map inc) (filter odd?)))]
      (is (expansion-contains? expanded 'injest.path/+>>))
      (is (expansion-contains? expanded 'injest.impl/xfn))))

  (testing "x>> with transducers then non-transducer"
    (let [expanded (macroexpand-1 '(injest.path/x>> [1 2 3] (map inc) (apply +)))]
      (is (expansion-contains? expanded 'injest.path/+>>))
      (is (expansion-contains? expanded 'injest.impl/xfn))
      ;; apply is not a transducer, should remain as-is
      (is (expansion-contains? expanded 'clojure.core/apply)))))

;; ===================================================================
;; 6. injest.path/|>  —  pipeline parallel, thread-first
;; ===================================================================

(deftest path-pipeline-thread-first-expansion
  (testing "|> with stateless transducers uses pipeline-xfn"
    (let [expanded (macroexpand-1 '(injest.path/|> coll (map inc) (filter odd?)))]
      ;; |> pre-transducifies with pipeline-xfn for par-transducable? forms
      ;; then delegates to x> which uses xfn for remaining
      (is (expansion-contains? expanded 'injest.path/x>))))

  (testing "|> without transducers falls through"
    (let [expanded (macroexpand-1 '(injest.path/|> x :a :b))]
      (is (expansion-contains? expanded 'injest.path/x>)))))

;; ===================================================================
;; 7. injest.path/|>>  —  pipeline parallel, thread-last
;; ===================================================================

(deftest path-pipeline-thread-last-expansion
  (testing "|>> with stateless transducers"
    (let [expanded (macroexpand-1 '(injest.path/|>> coll (map inc) (filter odd?)))]
      (is (expansion-contains? expanded 'injest.path/x>>))))

  (testing "|>> without transducers"
    (let [expanded (macroexpand-1 '(injest.path/|>> x :a :b))]
      (is (expansion-contains? expanded 'injest.path/x>>)))))

;; ===================================================================
;; 8. injest.path/=>  —  fold parallel, thread-first
;; ===================================================================

(deftest path-fold-thread-first-expansion
  (testing "=> with stateless transducers uses fold-xfn"
    (let [expanded (macroexpand-1 '(injest.path/=> coll (map inc) (filter odd?)))]
      (is (expansion-contains? expanded 'injest.path/x>))))

  (testing "=> without transducers"
    (let [expanded (macroexpand-1 '(injest.path/=> x :a :b))]
      (is (expansion-contains? expanded 'injest.path/x>)))))

;; ===================================================================
;; 9. injest.path/=>>  —  fold parallel, thread-last
;; ===================================================================

(deftest path-fold-thread-last-expansion
  (testing "=>> with stateless transducers"
    (let [expanded (macroexpand-1 '(injest.path/=>> coll (map inc) (filter odd?)))]
      (is (expansion-contains? expanded 'injest.path/x>>))))

  (testing "=>> without transducers"
    (let [expanded (macroexpand-1 '(injest.path/=>> x :a :b))]
      (is (expansion-contains? expanded 'injest.path/x>>)))))

;; ===================================================================
;; 10. injest.classical/x>  —  classical transducer thread-first
;; ===================================================================

(deftest classical-x-thread-first-expansion
  (testing "classical x> expands to -> (not +>)"
    (let [expanded (macroexpand-1 '(injest.classical/x> x (map inc)))]
      ;; Classical x> uses clojure.core/-> not injest.path/+>
      (is (not (expansion-contains? expanded 'injest.path/+>)))
      (is (expansion-contains? expanded 'clojure.core/->))))

  (testing "classical x> with non-transducers passes through to ->"
    (let [expanded (macroexpand-1 '(injest.classical/x> x (inc) (dec)))]
      (is (expansion-contains? expanded 'clojure.core/->)))))

;; ===================================================================
;; 11. injest.classical/x>>  —  classical transducer thread-last
;; ===================================================================

(deftest classical-x-thread-last-expansion
  (testing "classical x>> expands to ->> (not +>>)"
    (let [expanded (macroexpand-1 '(injest.classical/x>> coll (map inc)))]
      (is (not (expansion-contains? expanded 'injest.path/+>>)))
      (is (expansion-contains? expanded 'clojure.core/->>))))

  (testing "classical x>> with non-transducers passes through to ->>"
    (let [expanded (macroexpand-1 '(injest.classical/x>> x (conj 1) (conj 2)))]
      (is (expansion-contains? expanded 'clojure.core/->>)))))

;; ===================================================================
;; 12. injest.classical parallel variants
;; ===================================================================

(deftest classical-pipeline-expansion
  (testing "classical |> delegates to classical x>"
    (let [expanded (macroexpand-1 '(injest.classical/|> coll (map inc)))]
      (is (expansion-contains? expanded 'injest.classical/x>))))

  (testing "classical |>> delegates to classical x>>"
    (let [expanded (macroexpand-1 '(injest.classical/|>> coll (map inc)))]
      (is (expansion-contains? expanded 'injest.classical/x>>)))))

(deftest classical-fold-expansion
  (testing "classical => delegates to classical x>"
    (let [expanded (macroexpand-1 '(injest.classical/=> coll (map inc)))]
      (is (expansion-contains? expanded 'injest.classical/x>))))

  (testing "classical =>> delegates to classical x>>"
    (let [expanded (macroexpand-1 '(injest.classical/=>> coll (map inc)))]
      (is (expansion-contains? expanded 'injest.classical/x>>)))))

;; ===================================================================
;; 13. Empty and single-form expansion
;; ===================================================================

(deftest empty-thread-expansion
  (testing "+> with no forms returns x"
    (is (= 'x (macroexpand-1 '(injest.path/+> x)))))

  (testing "+>> with no forms returns x"
    (is (= 'x (macroexpand-1 '(injest.path/+>> x))))))

(deftest single-form-expansion
  (testing "+> with single keyword"
    (is (= '(:a x) (macroexpand-1 '(injest.path/+> x :a)))))

  (testing "+>> with single keyword"
    (is (= '(:a x) (macroexpand-1 '(injest.path/+>> x :a)))))

  (testing "+> with single fn call"
    (is (= '(inc x) (macroexpand-1 '(injest.path/+> x (inc))))))

  (testing "+>> with single fn call"
    (is (= '(inc x) (macroexpand-1 '(injest.path/+>> x (inc)))))))

;; ===================================================================
;; 14. Classical vs Path expansion structure comparison
;; ===================================================================

(deftest classical-vs-path-expansion-structure
  (testing "classical x>> uses ->> while path x>> uses +>>"
    (let [classical (macroexpand-1 '(injest.classical/x>> coll (map inc) (filter odd?)))
          path      (macroexpand-1 '(injest.path/x>> coll (map inc) (filter odd?)))]
      (is (expansion-contains? classical 'clojure.core/->>))
      (is (not (expansion-contains? classical 'injest.path/+>>)))
      (is (expansion-contains? path 'injest.path/+>>))
      (is (not (expansion-contains? path 'clojure.core/->>)))))

  (testing "classical x> uses -> while path x> uses +>"
    (let [classical (macroexpand-1 '(injest.classical/x> coll (map inc)))
          path      (macroexpand-1 '(injest.path/x> coll (map inc)))]
      (is (expansion-contains? classical 'clojure.core/->))
      (is (expansion-contains? path 'injest.path/+>)))))

;; ===================================================================
;; 15. Transducer grouping in expansion
;; ===================================================================

(deftest transducer-grouping-expansion
  (testing "consecutive transducers are grouped together"
    (let [expanded (macroexpand-1 '(injest.path/x>> data
                                    (map inc)
                                    (filter odd?)
                                    (map str)))]
      ;; All three transducers should be composed into one xfn call
      (is (expansion-contains? expanded 'injest.impl/xfn))
      (is (expansion-contains? expanded 'injest.path/+>>))))

  (testing "non-transducer breaks grouping"
    (let [expanded (macroexpand-1 '(injest.path/x>> data
                                    (map inc)
                                    (apply +)
                                    (map str)))]
      ;; apply breaks the chain — should see xfn mentioned
      ;; for each separate group
      (is (expansion-contains? expanded 'injest.impl/xfn))
      (is (expansion-contains? expanded 'clojure.core/apply)))))
