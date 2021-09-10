(ns injest.core
  (:require [cljs.analyzer.api :as api])
  #?(:cljs (:require-macros [injest.core])))

(def def-regs
  #{'cljs.core/mapcat
    'cljs.core/keep
    'cljs.core/filter
    'cljs.core/take-while
    'cljs.core/drop-while
    'cljs.core/keep-indexed
    'cljs.core/take
    'cljs.core/partition-all
    'cljs.core/distinct
    'cljs.core/dedupe
    'cljs.core/take-nth
    ;; #_ ; <- uncomment here to try to add map from cljs
    'cljs.core/map
    'cljs.core/partition-by
    'cljs.core/remove
    'cljs.core/cat
    'cljs.core/replace
    'cljs.core/random-sample
    'cljs.core/interpose
    'cljs.core/map-indexed
    'cljs.core/drop

    'clojure.core/take-nth
    'clojure.core/distinct
    'clojure.core/keep-indexed
    'clojure.core/random-sample
    'clojure.core/map-indexed
    #_ ; <- map is being added below, via registration
    'clojure.core/map
    'clojure.core/replace
    'clojure.core/drop
    'clojure.core/remove
    'clojure.core/cat
    'clojure.core/partition-all
    'clojure.core/interpose
    'clojure.core/mapcat
    'clojure.core/dedupe
    'clojure.core/drop-while
    'clojure.core/partition-by
    'clojure.core/take-while
    'clojure.core/take
    'clojure.core/keep
    'clojure.core/filter})

(def transducables (atom #{}))

(defn transducable? [form]
  (when (sequential? form)
    (->> form first (contains? @transducables))))

(defn compose-transducer-group [xfs]
  (->> xfs
       (map #(apply (first %) (rest %)))
       (apply comp)))

(defn xfn [xf-group]
  (fn [args]
    (sequence
     (compose-transducer-group xf-group)
     args)))

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
  (if-not env
    (list (symbol (safe-resolve x)))
    (list
     (some-> x
             ((partial cljs.analyzer.api/resolve env))
             :name
             str
             symbol))))

(defmacro reg-xf! [& xfs]
  `(swap! transducables into ~(->> xfs (mapv #(qualify-sym % &env)))))

(defn regxf! [& xfs]
  (swap! transducables into xfs))

(apply regxf! def-regs)

(regxf! 'clojure.core/map) ; or (reg-xf! map) ; Must be called from Clojure

(defn qualify-thread [env thread]
  (mapv
   (fn w [x]
     (if (and (list? x) (symbol? (first x)))
       (-> x first (qualify-form env) (concat (rest x)))
       x))
   thread))

(defmacro x>>
  "Just like ->> but first composes consecutive transducing fns into a function
     that sequences the last arguement through the transformers.
   
   So:
     (x>> [1 2 3] 
          (map inc) 
          (map (partial + 2)))

   Becomes:
     ((xfn [[map inc] [map (partial + 2)]]) 
      [1 2 3])"
  [x & threads]
  (let [forms (->> threads
                   (qualify-thread &env)
                   (partition-by #(transducable? %))
                   (mapv #(if-not (and (transducable? (first %))
                                       (second %))
                            %
                            (list (list `(xfn ~(mapv vec %))))))
                   (apply concat))]
    (loop [x x, forms forms]
      (if forms
        (let [form (first forms)
              threaded (cond (seq? form)
                             (with-meta `(~(first form) ~@(next form) ~x) (meta form))
                             :else
                             (list form x))]
          (recur threaded (next forms)))
        x))))

(defmacro x>
  "Just like -> but first composes consecutive transducing fns into a function
   that sequences the second arguement through the transformers.

   So:
   (x> [1 2 3]
       (conj 4)
       (map inc)
       (map (partial + 2))
       2)

   Becomes:
   (nth
    ((xfn [[map inc] [map (partial + 2)]]) 
     (conj [1 2 3] 
           4)) 
    2)"
  [x & threads]
  (let [forms (->> threads
                   (partition-by #(transducable? %))
                   (mapv #(if-not (and (transducable? (first %))
                                       (second %))
                            %
                            (list (list `(xfn ~(mapv vec %))))))
                   (apply concat))]
    (loop [x x, forms forms]
      (if forms
        (let [form (first forms)
              threaded (cond (seq? form)
                             (with-meta `(~(first form) ~x ~@(next form)) (meta form))
                             :else
                             (list form x))]
          (recur threaded (next forms)))
        x))))

(comment

  ;; (require '[net.cgrand.xforms :as x])

  ;; (reg-xf `x/reduce)

  (->> (range 10000000)
       (map inc)
       (filter odd?)
       (mapcat #(do [% (dec %)]))
       (partition-by #(= 0 (mod % 5)))
       (map (partial apply +))
      ;;  (mapv dec)
       (map (partial + 10))
       (map #(do {:temp-value %}))
       (map :temp-value)
       (filter even?)
       (apply +)
       time)

  (x>> (range 10000000)
       (map inc)
       (filter odd?)
       (mapcat #(do [% (dec %)]))
       (partition-by #(= 0 (mod % 5)))
       (map (partial apply +))
      ;;  (mapv dec)
       (map (partial + 10))
       (map #(do {:temp-value %}))
       (map :temp-value)
       (filter even?)
      ;;  (x/reduce +)
      ;;  first
       (apply +)
       time)

  :end)