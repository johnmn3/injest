(ns injest.impl
  (:require
   #?(:clj [clojure.core.async :as a :refer [chan to-chan! pipeline <!!]])
   #?(:clj [clojure.core.reducers :as r])
   [injest.state :as s]
   [injest.util  :as u]))

(defn transducable? [form]
  (when (sequential? form)
    (->> form first (contains? @s/transducables))))

(defn par-transducable? [form]
  (when (sequential? form)
    (->> form first (contains? @s/par-transducables))))

(defn compose-transducer-group [xfs]
  (->> xfs
       (map #(apply (first %) (rest %)))
       (apply comp)))

(defn xfn [xf-group]
  (let [ts (compose-transducer-group xf-group)]
    (fn [args]
      (sequence ts args))))

#?(:cljs (def fold-xfn xfn)
   :clj
   (defn fold-xfn [xf-group]
     (let [ts (compose-transducer-group xf-group)
           p (+ 2 (.. Runtime getRuntime availableProcessors))]
       (fn [args]
         (let [c (int (/ (count args) p))
               p-size (if (< c 32) 1 c)]
           (r/fold p-size (r/monoid into conj) (ts conj) (vec args)))))))

#?(:cljs (def pipeline-xfn xfn)
   :clj
   (defn pipeline-xfn [xf-group]
     (let [p (+ 2 (.. Runtime getRuntime availableProcessors))
           ts (compose-transducer-group xf-group)]
       (fn [args]
         (let [results (chan)]
           (pipeline p results ts (to-chan! args))
           (<!! (a/into [] results)))))))

(defn pre-transducify-thread [env minimum-group-size t-fn t-pred thread]
  (->> thread
       (u/qualify-thread env)
       (partition-by #(t-pred %))
       (mapv #(if-not (and (t-pred (first %))
                           (not (< (count %) minimum-group-size)))
                %
                (list (list `(~t-fn ~(mapv vec %))))))
       (apply concat)))

(defn get-or-nth [m-or-v aval]
  (if (associative? m-or-v)
    (get m-or-v aval)
    (nth m-or-v aval)))

(comment
  (get-or-nth {0 :a 2 :b} 2) ;=> :b
  (get-or-nth [:a :b :c] 2) ;=> :c
  (get-or-nth `(x y z) 2) ;=> injest.path/z
  (get-or-nth {0 :a nil 2} nil) ;=> 2
  (get-or-nth {0 :a false 2} false) ;=> 2

  :end)

(def protected-fns #{'fn 'fn* 'partial})

(defn path-> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~x ~@(next form)) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list `get-or-nth x form)
        :else
        (list form x)))

(defn path->> [form x]
  (cond (and (seq? form) (not (protected-fns (first form))))
        (with-meta `(~(first form) ~@(next form) ~x) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list `get-or-nth x form)
        :else
        (list form x)))
