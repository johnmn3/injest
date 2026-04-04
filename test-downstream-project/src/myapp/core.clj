(ns myapp.core
  (:require [injest.path :refer [+> +>> x>> =>>]]))

(defn path-thread-demo
  "Demonstrates +> path threading with nested data lookups."
  []
  (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :res}]}])}]
    (+> m 1 2 "b" 2 :c name)))

(defn transducify-demo
  "Demonstrates x>> auto-transducification."
  []
  (x>> (range 1000)
       (map inc)
       (filter odd?)
       (mapcat #(do [% (dec %)]))
       (partition-by #(= 0 (mod % 5)))
       (map (partial apply +))
       (map (partial + 10))
       (map #(do {:temp-value %}))
       (map :temp-value)
       (filter even?)
       (apply +)))

(defn parallel-demo
  "Demonstrates =>> parallel transducification."
  []
  (=>> (range 1000)
       (map inc)
       (filter even?)
       (map #(* % %))
       (filter #(= 0 (mod % 4)))
       (apply +)))

(defn lambda-wrapping-demo
  "Demonstrates threading through anonymous functions."
  []
  (+> 10 range rest 2 #(- 10 % 1)))

(defn path-thread-last-demo
  "Demonstrates +>> path threading with lambda."
  []
  (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c :bob}]}])}]
    (with-out-str
      (+>> m 1 2 "b" 2 :c name #(println "hi" % "!")))))
