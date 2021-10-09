(ns injest.test
  (:require
   [injest.state :as i.s]
   [injest.path :as injest :refer [+> +>> x> x>> =>>]]))

(comment

  ;; these aren't workign in cljs
  (i.s/regxf! 'cljs.core/map)
  (i.s/reg-xf! map)

  (require '[clojure.edn :as edn])
  ;; (require '[net.cgrand.xforms :as x])

  ;; (reg-xf `x/reduce)

  ;; copied from test.clj, recorded times need to be updated for cljs
  (->> (range 1000000)
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

  (x>> (range 1000000)
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

;; path thread tests 

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

  (x>> (range 1000000)
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
  ;; "Elapsed time: 6735.604664 msecs"
  ;; 5000054999994

  :end)
