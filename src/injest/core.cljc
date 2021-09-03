(ns injest.core)

(def transducables
  #{(symbol 'map)
    (symbol 'cat) 
    (symbol 'mapcat) 
    (symbol 'filter)
    (symbol 'remove) 
    (symbol 'take) 
    (symbol 'take-while) 
    (symbol 'take-nth) 
    (symbol 'drop) 
    (symbol 'drop-while) 
    (symbol 'replace) 
    (symbol 'partition-by) 
    (symbol 'partition-all) 
    (symbol 'keep) 
    (symbol 'keep-indexed) 
    (symbol 'map-indexed) 
    (symbol 'distinct) 
    (symbol 'interpose) 
    (symbol 'dedupe) 
    (symbol 'random-sample)})

(defn transducable? [form]
  (when (sequential? form)
    (contains? transducables (first form))))

(defn -comp
  ([] identity)
  ([f] f)
  ([f g] (fn [x] (f (g x))))
  ([f g h] (fn [x] (f (g (h x)))))
  #?@(:clj  [([f1 f2 f3 f4] (fn [x] (-> x f4 f3 f2 f1)))
             ([f1 f2 f3 f4 f5] (fn [x] (-> x f5 f4 f3 f2 f1)))
             ([f1 f2 f3 f4 f5 f6] (fn [x] (-> x f6 f5 f4 f3 f2 f1)))
             ([f1 f2 f3 f4 f5 f6 f7] (fn [x] (-> x f7 f6 f5 f4 f3 f2 f1)))
             ([f1 f2 f3 f4 f5 f6 f7 f8] (fn [x] (-> x f8 f7 f6 f5 f4 f3 f2 f1)))
             ([f1 f2 f3 f4 f5 f6 f7 f8 & fs]
              (-comp
               (apply -comp fs)
               (fn [x] (-> x f8 f7 f6 f5 f4 f3 f2 f1))))]
      :cljs [([f1 f2 f3 & fs]
              (-comp
               (apply -comp fs)
               (fn [x] (-> x f3 f2 f1))))]))

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