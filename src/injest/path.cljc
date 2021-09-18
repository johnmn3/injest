(ns injest.path
  (:require
   [injest.core :as c])
  #?(:cljs (:require-macros [injest.core])))

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

(def regxf! c/regxf!)

(defmacro reg-xf! [& args]
  `(c/reg-xf! ~@args))

(defmacro reg-pxf! [& xfs]
  `(c/reg-pxf! ~@xfs))

(def regpxf! c/regpxf!)

(defn path-thread-first [form x]
  (cond (and (seq? form) (not (#{'fn 'fn*} (first form))))
        (with-meta `(~(first form) ~x ~@(next form)) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list `get-or-nth x form)
        :else
        (list form x)))

(defn path-thread-last [form x]
  (cond (and (seq? form) (not (#{'fn 'fn*} (first form))))
        (with-meta `(~(first form) ~@(next form) ~x) (meta form))
        (or (string? form) (nil? form) (boolean? form))
        (list x form)
        (int? form)
        (list `get-or-nth x form)
        :else
        (list form x)))

;; non-transducer versions, with path navigation, for untransducifying a transducified path thread
(defmacro +>
  "Just like -> but for ints will index on vectors and sequences and will 
   call `get` on maps. All strings, boolans and nils will be passed to the thread value.
   
   As in:
   (let [m {1 {\"b\" [0 1 {:c :res}]}}]
     (+> m 1 \"b\" 2 :c name)) ;=> \"res\""
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path-thread-first (first forms) x) (next forms))
      x)))

(defmacro +>>
  "Just like ->> but for ints will index on vectors and sequences and will 
   call `get` on maps. All strings, boolans and nils will be passed to the thread value.
   
   As in:
   (let [m {1 {\"b\" [0 1 {:c :res}]}}]
     (->> m 1 \"b\" 2 :c name)) ;=> \"res\""
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (path-thread-last (first forms) x) (next forms))
      x)))

;; transducer version
(defmacro x>
  "Just like +> but first composes consecutive transducing fns into a function
  that sequences the last arguement through the transformers."
  [x & thread]
  `(+> ~x ~@(->> thread (c/pre-transducify-thread &env 2 `c/xfn c/transducable?))))

(defmacro x>>
  "Just like +>> but first composes consecutive transducing fns into a function
  that sequences the last arguement through the transformers."
  [x & thread]
  `(+>> ~x ~@(->> thread (c/pre-transducify-thread &env 2 `c/xfn c/transducable?))))

;; parallel transducer version
#?(:clj
   (defmacro =>
     "Just like +> but first composes consecutive stateless transducing functions 
     into a function that parallel-pipeline's the values flowing through the thread.
     Remaining consecutive stateful transducers are composed just like x>."
     [x & thread]
     `(x> ~x ~@(->> thread (c/pre-transducify-thread &env 1 `c/pxfn c/par-transducable?)))))

#?(:clj
   (defmacro =>>
     "Just like +>> but first composes consecutive stateless transducing functions 
     into a function that parallel-pipeline's the values flowing through the thread.
     Remaining consecutive stateful transducers are composed just like x>>."
     [x & thread]
     `(x>> ~x ~@(->> thread (c/pre-transducify-thread &env 1 `c/pxfn c/par-transducable?)))))

(comment

  (let [m {1 {"b" [0 1 {:c :res}]}}]
    (x> m 1 "b" 2 :c))

  (x> {0 :a 2 :b} 2) ;=> :b

  (x> [0 2 5] 2 #(- 10 % 1)) ;=> 4

  (x> [0 1 2 3 4] rest 2 #(- 10 % 1)) ;=> 6

  (x> 10 range rest 2 #(- 10 % 1)) ;=> 6

  (x> [:a :b :c] 2) ;=> :c

  (x> `(x y z) 2) ;=> injest.path/z

  (x> {0 :a nil 2} nil) ;=> 2

  (x> {0 :a false 2} false) ;=> 2

  (x>> {0 :a 2 :b} 2) ;=> :b

  (x>> [:a :b :c] 2) ;=> :c

  (x>> `(x y z) 2) ;=> injest.path/z

  (x>> {0 :a nil 2} nil) ;=> 2

  (x>> {0 :a false 2} false) ;=> 2

  ; non-transducer, with path navigation, for untransducifying a transducified path thread
  (+> {0 :a 2 :b} 2) ;=> :b

  (+> [:a :b :c] 2) ;=> :c

  (+> `(x y z) 2) ;=> injest.path/z

  (+> {0 :a nil 2} nil) ;=> 2

  (+> {0 :a false 2} false) ;=> 2

  (+>> {0 :a 2 :b} 2) ;=> :b

  (+>> [:a :b :c] 2) ;=> :c

  (+>> `(x y z) 2) ;=> injest.path/z

  (+>> {0 :a nil 2} nil) ;=> 2

  (+>> {0 :a false 2} false) ;=> 2

  (let [m {1 {"b" [0 1 {:c :res}]}}]
    (x> m 1 "b" 2 :c name)) ;=> "res"

  (let [m {1 {"b" [0 1 {:c :res}]}}]
    (x>> m 1 "b" 2 :c name)) ;=> "res"

  (let [m {1 {"b" [0 1 {:c :res}]}}]
    (+> m 1 "b" 2 :c name)) ;=> "res"

  (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :res}]}])}]
    (+>> m 1 2 "b" 2 :c name)) ;=> "res"
  
  (x>> (range 10000000)
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
       time)

  :end)