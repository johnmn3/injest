(ns injest.impl
  (:require 
   #?(:clj [clojure.core.async :as a :refer [chan to-chan! pipeline <!!]])
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
  (fn [args]
    (sequence
     (compose-transducer-group xf-group)
     args)))

#?(:cljs (def pxfn xfn)
   :clj
   (defn pxfn [xf-group]
     (fn [args]
       (let [concurrent (+ 2 (.. Runtime getRuntime availableProcessors))
             results (chan)]
         (pipeline concurrent
                   results
                   (compose-transducer-group xf-group)
                   (to-chan! args))
         (<!! (a/into [] results))))))

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

(defn path-> [form x]
  (cond (and (seq? form) (not (#{'fn 'fn*} (first form))))
        (with-meta `(~(first form) ~x ~@(next form)) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list `get-or-nth x form)
        :else
        (list form x)))

(defn path->> [form x]
  (cond (and (seq? form) (not (#{'fn 'fn*} (first form))))
        (with-meta `(~(first form) ~@(next form) ~x) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list `get-or-nth x form)
        :else
        (list form x)))
