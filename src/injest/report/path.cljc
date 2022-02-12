(ns injest.report.path
  (:require
   [injest.path :as p]
   [injest.report :as r]
   #?(:cljs [cljs.analyzer :as ana]))
  #?(:cljs (:require-macros [injest.report.path])))

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

(defmacro get-namespace []
  (str *ns*))

;; transducer version
(defmacro x>>
  "Just like +>> but first composes transducers into a function that sequences
   the thread values through the transducers."
  [x & thread]
  `(if-not @r/report-live?
     (injest.path/x>> ~x ~@thread)
     (let [a?# (= 0 (rand-int 2))
           ans# (get-namespace)
           k# (r/flc ans# ~(meta &form))]
       (if a?#
         (r/monitor k# injest.path/x>> ~(concat [x] thread))
         (r/monitor k# injest.path/+>> ~(concat [x] thread))))))

;; parallel transducer version
#?(:cljs (defmacro =>> "Just like x>>, for now" [& args] `(x>> ~@args))
   :clj  (defmacro =>>
           "Just like x>> but first composes stateless transducers into a function that 
            `r/fold`s in parallel the values flowing through the thread. Remaining
            stateful transducers are composed just like x>>."
           [x & thread]
           `(if-not @r/report-live?
              (injest.path/x>> ~x ~@thread)
              (let [n# (rand-int 3)
                    ans# (get-namespace)
                    k# (r/flc ans# ~(meta &form))]
                (case n#
                  0 (r/monitor k# injest.path/=>> ~(concat [x] thread))
                  1 (r/monitor k# injest.path/x>> ~(concat [x] thread))
                  2 (r/monitor k# injest.path/+>> ~(concat [x] thread)))))))
