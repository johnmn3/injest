(ns injest.path)

(def protected-fns #{`fn 'fn 'fn* 'partial})

(defn path-> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~x ~@(next form)) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list 'clojure.core/get x form)
        (int? form)
        (list 'clojure.core/get x form)
        :else
        (list form x)))

(defn path->> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~@(next form) ~x) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list 'clojure.core/get x form)
        (int? form)
        (list 'clojure.core/get x form)
        :else
        (list form x)))

(defmacro +>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path-> (first forms) x) (next forms))
      x)))

(defmacro +>>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path->> (first forms) x) (next forms))
      x)))
