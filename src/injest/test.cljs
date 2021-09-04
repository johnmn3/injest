(ns injest.test
  (:require
   [injest.core :as injest :refer [x> x>>]]))


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

