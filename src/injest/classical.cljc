(ns injest.classical
  (:require [injest.impl :as i])
  #?(:cljs (:require-macros [injest.classical])))

(defmacro x>
  "Just like -> but first composes transducers into a function
  that sequences the thread values through the transducers."
  [x & thread]
  `(-> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/xfn i/transducable?))))

(defmacro x>>
  "Just like ->> but first composes transducers into a function
  that sequences the thread values through the transducers."
  [x & thread]
  `(->> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/xfn i/transducable?))))

#?(:cljs (defmacro |> "Just like x>, for now" [& args] `(x> ~@args))
   :clj  (defmacro |>
           "Just like x> but first composes stateless transducers into a function that 
            pipelines in parallel the values flowing through the  thread. Remaining 
            stateful transducers are composed just like x>."
           [x & thread]
           `(x> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/pipeline-xfn i/par-transducable?)))))

#?(:cljs (defmacro |>> "Just like x>>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro |>>
           "Just like x>> but first composes stateless transducers into a function that
            pipelines in parallel the values flowing through the thread. Remaining 
            stateful transducers are composed just like x>>."
           [x & thread]
           `(x>> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/pipeline-xfn i/par-transducable?)))))

#?(:cljs (defmacro => "Just like x>, for now" [& args] `(x> ~@args))
   :clj  (defmacro =>
           "Just like x> but first composes stateless transducers into a function that
            `r/fold`s in parallel the values flowing through the thread. Remaining 
            stateful transducers are composed just like x>."
           [x & thread]
           `(x> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/fold-xfn i/par-transducable?)))))

#?(:cljs (defmacro =>> "Just like x>>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro =>>
           "Just like x>> but first composes stateless transducers into a function that
            `r/fold`s in parallel the values flowing through the thread. Remaining
            stateful transducers are composed just like x>>."
           [x & thread]
           `(x>> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/fold-xfn i/par-transducable?)))))
