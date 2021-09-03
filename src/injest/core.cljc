(ns injest.core
  #?(:cljs (:require-macros [injest.core])))

(def default-transducables
  #{`map
    `cat 
    `mapcat 
    `filter
    `remove 
    `take 
    `take-while 
    `take-nth 
    `drop 
    `drop-while 
    `replace 
    `partition-by 
    `partition-all 
    `keep 
    `keep-indexed 
    `map-indexed 
    `distinct 
    `interpose 
    `dedupe 
    `random-sample})

(def transducables (atom #{}))

(defn reg-xf [& xf-args]
  (swap! transducables into (mapv resolve xf-args)))

(apply reg-xf default-transducables)

(defn transducable? [form]
  (when (sequential? form)
    (contains? @transducables (resolve (first form)))))

(defn compose-transducer-group [xfs]
  (->> xfs
       (map #(apply (first %) (rest %)))
       (apply comp)))

(defn xfn [xf-group]
  (fn [args]
    (sequence
     (compose-transducer-group xf-group)
     args)))

(defmacro x>>
  "Just like ->> but first composes consecutive transducing fns into a function
   that sequences the last arguement through the transformers. Also, calls nth
   for ints. So:
   
   (x>> [1 2 3] 
        (map inc) 
        (map (partial + 2)))
   Becomes:
   
   ((xfn [[map inc] 
          [map (partial + 2)]]) 
    [1 2 3])"
  [x & threads]
  (let [forms (->> threads
                   (partition-by transducable?)
                   (mapv #(if-not (and (transducable? (first %))
                                       (second %))
                              %
                              (list
                               (list
                                `(xfn ~(mapv vec %))))))
                   (apply concat))]
    (loop [x x, forms forms]
      (if forms
        (let [form (first forms)
              threaded (cond (seq? form)
                             (with-meta `(~(first form) ~@(next form) ~x) (meta form))
                             (int? form)
                             (list `nth x form)
                             :else
                             (list form x))]
          (recur threaded (next forms)))
        x))))

(defmacro x>
  "Just like -> but first composes consecutive transducing fns into a function
   that sequences the second arguement through the transformers. Also, calls nth
   for ints. So:
   
   (x> [1 2 3]
       (conj 4)
       (map inc)
       (map (partial + 2))
       2)
   Becomes like:
   
   (nth
    ((xfn [[map inc] [map (partial + 2)]]) 
     (conj [1 2 3] 
           4)) 
    2)"
  [x & threads]
  (let [forms (->> threads
                   (partition-by transducable?)
                   (mapv #(if-not (and (transducable? (first %))
                                       (second %))
                            %
                            (list
                             (list
                              `(xfn ~(mapv vec %))))))
                   (apply concat))]
    (loop [x x, forms forms]
      (if forms
        (let [form (first forms)
              threaded (cond (seq? form)
                             (with-meta `(~(first form) ~x ~@(next form)) (meta form))
                             (int? form)
                             (list `nth x form)
                             :else
                             (list form x))]
          (recur threaded (next forms)))
        x))))

(comment 

  (require '[net.cgrand.xforms :as x])

  (reg-xf `x/reduce)

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
       (x/reduce +)
       first
       time)

  (let [m {:a {:b [0 1 {:c :res}]}}]
    (x> m :a :b 2 :c))
  
  :end)
