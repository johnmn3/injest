(ns injest.report.path
  (:require
   [injest.impl :as i]
   [injest.path :as p]
   [injest.report :as r])
  #?(:cljs (:require-macros [injest.vary.path])))

;; non-transducer versions, with path navigation, for untransducifying a transducified path thread
(defmacro +>
  "Just like ->> but for ints will index into vectors and sequences and `get` 
   into maps, whereas for strings, booleans and nils, will be passed to the 
   thread-value as a lookup param. Also wraps lambdas.
   As in:
   (let [m {1 {\"b\" [0 1 {:c :res}]}}]
     (+> m 1 \"b\" 2 :c name #(str \"hi\" % \"!\"))) ;=> \"hi res!\""
  [x & forms]
  `(p/+> ~x ~@forms))

(defmacro +>>
  "Just like ->> but for ints will index into vectors and sequences and `get` 
   into maps, whereas for strings, booleans and nils, will be passed to the 
   thread-value as a lookup param. Also wraps lambdas.
   As in:
   (let [m {1 {\"b\" [0 1 {:c :res}]}}]
     (+>> m 1 \"b\" 2 :c name #(str \"hi\" % \"!\"))) ;=> \"hi res!\""
  [x & forms]
  `(p/+>> ~x ~@forms))

;; transducer version
(defmacro x>>
  "Just like +>> but first composes transducers into a function that sequences
   the thread values through the transducers."
  [x & thread]
  (if-not @r/report-live?
    `(injest.path/x>> ~x ~@thread)
    (let [variants (shuffle '(injest.path/x>> injest.path/+>>))
          applicator (first variants)
          k (r/flc &form)]
      (r/monitor k applicator (concat [x] thread)))))

;; parallel transducer version
#?(:cljs (defmacro =>> "Just like x>>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro =>>
           "Just like x>> but first composes stateless transducers into a function that 
            `r/fold`s in parallel the values flowing through the thread. Remaining
            stateful transducers are composed just like x>>."
           [x & thread]
           (if-not @r/report-live?
             `(injest.path/=>> ~x ~@thread)
             (let [variants (shuffle '(injest.path/=>> injest.path/x>> injest.path/+>>))
                   applicator (first variants)
                   k (r/flc &form)]
               (r/monitor k applicator (concat [x] thread))))))
