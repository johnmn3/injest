(ns injest.path
  (:require
   [injest.impl :as i])
  #?(:cljs (:require-macros [injest.path])))

;; non-transducer versions, with path navigation, for untransducifying a transducified path thread
(defmacro +>
  "Just like -> but for ints will index into vectors and sequences and `get` 
   into maps, whereas for strings, booleans and nils, will be passed to the 
   thread-value as a lookup param. Also wraps lambdas.
   As in:
   (let [m {1 {\"b\" [0 1 {:c :res}]}}]
     (+> m 1 \"b\" 2 :c name #(str \"hi\" % \"!\"))) ;=> \"hi res!\""
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (i/path-> (first forms) x) (next forms))
      x)))

(defmacro +>>
  "Just like ->> but for ints will index into vectors and sequences and `get` 
   into maps, whereas for strings, booleans and nils, will be passed to the 
   thread-value as a lookup param. Also wraps lambdas.
   As in:
   (let [m {1 {\"b\" [0 1 {:c :res}]}}]
     (+>> m 1 \"b\" 2 :c name #(str \"hi\" % \"!\"))) ;=> \"hi res!\""
  [x & forms]
  (loop [x x, forms forms]
    (if forms
      (recur (i/path->> (first forms) x) (next forms))
      x)))

;; transducer version
(defmacro x>
  "Just like +> but first composes transducers into a function that sequences
   the thread values through the transducers."
  [x & thread]
  `(+> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/xfn i/transducable?))))

(defmacro x>>
  "Just like +>> but first composes transducers into a function that sequences
   the thread values through the transducers."
  [x & thread]
  `(+>> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/xfn i/transducable?))))

;; parallel transducer version
#?(:cljs (defmacro |> "Just like x>, for now" [& args] `(x> ~@args))
   :clj  (defmacro |>
           "Just like x> but first composes stateless transducers functions into a function
            that pipelines in parallel the thread values flowing through the thread. 
            Remaining stateful transducers are composed just like x>."
           [x & thread]
           `(x> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/pipeline-xfn i/par-transducable?)))))

#?(:cljs (defmacro |>> "Just like x>>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro |>>
           "Just like x>> but first composes stateless transducers functions into a function 
            that pipelines in parallel the thread values flowing through the thread. 
            Remaining stateful transducers are composed just like x>>."
           [x & thread]
           `(x>> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/pipeline-xfn i/par-transducable?)))))

#?(:cljs (defmacro => "Just like x>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro =>
           "Just like x> but first composes stateless transducers into a function that 
            `r/fold`s in parallel the values flowing through the thread. Remaining 
            stateful transducers are composed just like x>>."
           [x & thread]
           `(x> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/fold-xfn i/par-transducable?)))))

#?(:cljs (defmacro =>> "Just like x>>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro =>>
           "Just like x>> but first composes stateless transducers into a function that 
            `r/fold`s in parallel the values flowing through the thread. Remaining
            stateful transducers are composed just like x>>."
           [x & thread]
           `(x>> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/fold-xfn i/par-transducable?)))))
