(ns injest.util
  (:require [cljs.analyzer.api :as api]))

(def safe-resolve
  #?(:clj resolve :cljs identity))

(defn qualify-sym [x env]
  (if-not env
    `(quote
      ~(symbol (safe-resolve x)))
    `(symbol
      (quote
       ~(some-> x
                ((partial cljs.analyzer.api/resolve env))
                :name
                symbol)))))

(defn qualify-form [x env]
  (if-not (:ns env)
    (list (symbol (safe-resolve x)))
    (list
     (some-> x
             ((partial cljs.analyzer.api/resolve env))
             :name
             str
             symbol))))

(defn qualify-thread [env thread]
  (mapv
   (fn w [x]
     (if (and (list? x) (symbol? (first x)))
       (-> x first (qualify-form env) (concat (rest x)))
       x))
   thread))
