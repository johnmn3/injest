(ns injest.path)

(def protected-fns #{`fn 'fn 'fn* 'partial})

(defn get-or-nth [m-or-v aval]
  (if (associative? m-or-v)
    (get m-or-v aval)
    (nth m-or-v aval)))

(defn path-> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~x ~@(next form)) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list 'injest.path/get-or-nth x form)
        :else
        (list form x)))

(defn path->> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~@(next form) ~x) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list 'injest.path/get-or-nth x form)
        :else
        (list form x)))

(defn +>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path-> (first forms) x) (next forms))
      x)))

(defn +>>
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path->> (first forms) x) (next forms))
      x)))