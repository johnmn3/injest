(ns injest.core
  (:require [cljs.analyzer.api :as api]
            #?(:clj [clojure.core.async :as a :refer [chan to-chan! pipeline <!!]]))
  #?(:cljs (:require-macros [injest.core])))

(def par-regs
  #{'cljs.core/dedupe
    'cljs.core/disj!
    'cljs.core/dissoc!
    'cljs.core/filter
    'cljs.core/keep
    'cljs.core/map
    'cljs.core/random-sample
    'cljs.core/remove
    'cljs.core/replace
    'cljs.core/take-while
    'cljs.core/halt-when
    'cljs.core/mapcat
    'cljs.core/cat

    'clojure.core/dedupe
    'clojure.core/disj!
    'clojure.core/dissoc!
    'clojure.core/filter
    'clojure.core/keep
    'clojure.core/map
    'clojure.core/random-sample
    'clojure.core/remove
    'clojure.core/replace
    'clojure.core/take-while
    'clojure.core/halt-when
    'clojure.core/mapcat
    'clojure.core/cat})

(def def-regs
  #{'cljs.core/mapcat
    'cljs.core/disj!
    'cljs.core/dissoc!
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
    'cljs.core/halt-when

    'clojure.core/take-nth
    'clojure.core/disj!
    'clojure.core/dissoc!
    'clojure.core/distinct
    'clojure.core/keep-indexed
    'clojure.core/random-sample
    'clojure.core/map-indexed
    #_; <- map is being added below, via registration
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
    'clojure.core/filter
    'clojure.core/halt-when})

(def transducables (atom #{}))

(def par-transducables (atom #{}))

(defn transducable? [form]
  (when (sequential? form)
    (->> form first (contains? @transducables))))

(defn par-transducable? [form]
  (when (sequential? form)
    (->> form first (contains? @par-transducables))))

(defn compose-transducer-group [xfs]
  (->> xfs
       (map #(apply (first %) (rest %)))
       (apply comp)))

(defn xfn [xf-group]
  (fn [args]
    (sequence
     (compose-transducer-group xf-group)
     args)))

#?(:clj
   (defn pxfn [xf-group]
     (fn [args]
       (let [concurrent (+ 2 (.. Runtime getRuntime availableProcessors))
             results (chan)]
         (pipeline concurrent
                   results
                   (compose-transducer-group xf-group)
                   (to-chan! args))
         (<!! (a/into [] results))))))

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

(defmacro reg-xf! [& xfs]
  `(swap! transducables into ~(->> xfs (mapv #(qualify-sym % &env)))))

(defn regxf! [& xfs]
  (swap! transducables into xfs))

(defmacro reg-pxf! [& xfs]
  `(swap! par-transducables into ~(->> xfs (mapv #(qualify-sym % &env)))))

(defn regpxf! [& xfs]
  (swap! par-transducables into xfs))

(apply regxf! def-regs)

(apply regpxf! par-regs)

(regxf! 'clojure.core/map) ; or (reg-xf! map) ; Must be called from Clojure

(defn qualify-thread [env thread]
  (mapv
   (fn w [x]
     (if (and (list? x) (symbol? (first x)))
       (-> x first (qualify-form env) (concat (rest x)))
       x))
   thread))

(defn pre-transducify-thread [env minimum-group-size t-fn t-pred thread]
  (->> thread
       (qualify-thread env)
       (partition-by #(t-pred %))
       (mapv #(if-not (and (t-pred (first %))
                           (not (< (count %) minimum-group-size)))
                %
                (list (list `(~t-fn ~(mapv vec %))))))
       (apply concat)))

(defmacro x>
  "Just like -> but first composes consecutive transducing fns into a function
  that sequences the second arguement through the transformers."
  [x & thread]
  `(-> ~x ~@(->> thread (pre-transducify-thread &env 2 `xfn transducable?))))

(defmacro x>>
  "Just like ->> but first composes consecutive transducing fns into a function
  that sequences the last arguement through the transformers."
  [x & thread]
  `(->> ~x ~@(->> thread (pre-transducify-thread &env 2 `xfn transducable?))))

#?(:clj
   (defmacro =>
     "Just like -> but first composes consecutive stateless transducing functions 
      into a function that parallel-pipeline's the values flowing through the thread.
      Remaining consecutive stateful transducers are composed just like x>."
     [x & thread]
     `(x> ~x ~@(->> thread (pre-transducify-thread &env 1 `pxfn par-transducable?)))))

#?(:clj
   (defmacro =>>
     "Just like -> but first composes consecutive stateless transducing functions 
      into a function that parallel-pipeline's the values flowing through the thread.
      Remaining consecutive stateful transducers are composed just like x>>."
     [x & thread]
     `(x>> ~x ~@(->> thread (pre-transducify-thread &env 1 `pxfn par-transducable?)))))

(comment

  (require '[clojure.edn :as edn])
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

  ;; work utilities
  (defn work-1000 [work-fn]
    (range (last (repeatedly 1000 work-fn))))

  (defn ->>work [input]
    (work-1000
     (fn []
       (->> input
            (map inc)
            (filter odd?)
            (mapcat #(do [% (dec %)]))
            (partition-by #(= 0 (mod % 5)))
            (map (partial apply +))
            (map (partial + 10))
            (map #(do {:temp-value %}))
            (map :temp-value)
            (filter even?)
            (apply +)
            str
            (take 3)
            (apply str)
            edn/read-string))))

  (defn x>>work [input]
    (work-1000
     (fn []
       (x>> input
            (map inc)
            (filter odd?)
            (mapcat #(do [% (dec %)]))
            (partition-by #(= 0 (mod % 5)))
            (map (partial apply +))
            (map (partial + 10))
            (map #(do {:temp-value %}))
            (map :temp-value)
            (filter even?)
            (apply +)
            str
            (take 3)
            (apply str)
            edn/read-string))))

  (->> (range 100)
       (repeat 10)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       last
       count
       time)
  ; "Elapsed time: 18309.397391 msecs"
  ; 234

  (x>> (range 100)
       (repeat 10)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       last
       count
       time)
  ; "Elapsed time: 6252.224178 msecs"
  ; 234

  (=>> (range 100)
       (repeat 10)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       (map ->>work)
       last
       count
       time)
  ; "Elapsed time: 8976.963402 msecs"
  ; 234

  (=>> (range 100)
       (repeat 10)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       (map x>>work)
       last
       count
       time)
  ; "Elapsed time: 2862.172838 msecs"
  ; 234

  :end)
