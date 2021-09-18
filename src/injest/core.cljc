(ns injest.core
  (:require [injest.impl :as i])
  #?(:cljs (:require-macros [injest.core])))

(defmacro x>
  "Just like -> but first composes consecutive transducing fns into a function
  that sequences the thread values through the transducers."
  [x & thread]
  `(-> ~x ~@(->> thread (i/pre-transducify-thread &env 2 `i/xfn i/transducable?))))

(defmacro x>>
  "Just like ->> but first composes consecutive transducing fns into a function
  that sequences the thread values through the transducers."
  [x & thread]
  `(->> ~x ~@(->> thread (i/pre-transducify-thread &env 2 `i/xfn i/transducable?))))

#?(:cljs (defmacro => "Just like x>, for now" [& args] `(x> ~@args))
   :clj  (defmacro =>
           "Just like x> but first composes consecutive stateless transducing functions
           into a function that pipelines in parallel the thread values flowing through the 
           thread. Remaining consecutive stateful transducers are composed just like x>."
           [x & thread]
           `(x> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/pxfn i/par-transducable?)))))

#?(:cljs (defmacro =>> "Just like x>>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro =>>
           "Just like x>> but first composes consecutive stateless transducing functions
           into a function that pipelines in parallel the thread values flowing through the 
           thread. Remaining consecutive stateful transducers are composed just like x>>."
           [x & thread]
           `(x>> ~x ~@(->> thread (i/pre-transducify-thread &env 1 `i/pxfn i/par-transducable?)))))
