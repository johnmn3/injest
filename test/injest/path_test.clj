(ns injest.path-test
  (:require [clojure.test :refer :all]
            [injest.path :refer :all]))

(deftest readme-example
  (testing "example from the readme"
    (is (= 5000054999994
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
                (apply +))))))

(deftest lookup-value-by-integer-key-in-map
  (testing "Get value from map by integer key"
    (is (= :b
           (+> {0 :a 2 :b}
               2)))
    (is (= :b
           (+>> {0 :a 2 :b}
                2)))
    (is (= :b
           (x> {0 :a 2 :b}
               2)))
    (is (= :b
           (x>> {0 :a 2 :b}
                2)))
    (is (= :b
           (=> {0 :a 2 :b}
               2)))
    (is (= :b
           (=>> {0 :a 2 :b}
                2)))))

(deftest index-into-vector
  (testing "Get value of index in vector"
    (is (= 5
           (+> [0 2 5]
               2)))
    (is (= 5
           (+>> [0 2 5]
                2)))
    (is (= 5
           (x> [0 2 5]
               2)))
    (is (= 5
           (x>> [0 2 5]
                2)))
    (is (= 5
           (=> [0 2 5]
               2)))
    (is (= 5
           (=>> [0 2 5]
                2)))))

(deftest index-into-sequence
  (testing "Get value of index in sequence"
    (is (= 5
           (+> '(0 2 5)
               2)))
    (is (= 5
           (+>> '(0 2 5)
                2)))
    (is (= 5
           (x> '(0 2 5)
               2)))
    (is (= 5
           (x>> '(0 2 5)
                2)))
    (is (= 5
           (=> '(0 2 5)
               2)))
    (is (= 5
           (=>> '(0 2 5)
                2)))))

(deftest lookup-key-by-string-in-map
  (testing "Get value of index in vector"
    (is (= 5
           (+> {0 :a "s" 5}
               "s")))
    (is (= 5
           (+>> {0 :a "s" 5}
                "s")))
    (is (= 5
           (x> {0 :a "s" 5}
               "s")))
    (is (= 5
           (x>> {0 :a "s" 5}
                "s")))
    (is (= 5
           (=> {0 :a "s" 5}
               "s")))
    (is (= 5
           (=>> {0 :a "s" 5}
                "s")))))

(deftest lookup-key-by-key-in-map
  (testing "Get value of index in vector"
    (is (= 5
           (+> {0 :a :k 5}
               :k)))
    (is (= 5
           (+>> {0 :a :k 5}
                :k)))
    (is (= 5
           (x> {0 :a :k 5}
               :k)))
    (is (= 5
           (x>> {0 :a :k 5}
                :k)))
    (is (= 5
           (=> {0 :a :k 5}
               :k)))
    (is (= 5
           (=>> {0 :a :k 5}
                :k)))))

(deftest lookup-key-by-nil-in-map
  (testing "Get value of index in vector"
    (is (= 5
           (+> {0 :a nil 5}
               nil)))
    (is (= 5
           (+>> {0 :a nil 5}
                nil)))
    (is (= 5
           (x> {0 :a nil 5}
               nil)))
    (is (= 5
           (x>> {0 :a nil 5}
                nil)))
    (is (= 5
           (=> {0 :a nil 5}
               nil)))
    (is (= 5
           (=>> {0 :a nil 5}
                nil)))))

(deftest lookup-key-by-boolean-in-map
  (testing "Get value of index in vector"
    (is (= 5
           (+> {0 :a true 5}
               true)))
    (is (= 5
           (+>> {0 :a true 5}
                true)))
    (is (= 5
           (x> {0 :a true 5}
               true)))
    (is (= 5
           (x>> {0 :a true 5}
                true)))
    (is (= 5
           (=> {0 :a true 5}
               true)))
    (is (= 5
           (=>> {0 :a true 5}
                true)))))

(deftest lamda-wrapping
  (testing "wrap lambdas"
    (is (= 8
           (+> 1
               #(- 10 (+ % 1)))))
    (is (= 8
           (+>> 1
                #(- 10 (+ % 1)))))
    (is (= 8
           (x> 1
               #(- 10 (+ % 1)))))
    (is (= 8
           (x>> 1
                #(- 10 (+ % 1)))))
    (is (= 8
           (=> 1
               #(- 10 (+ % 1)))))
    (is (= 8
           (=>> 1
                #(- 10 (+ % 1)))))))

(deftest all-thread-features
  (testing "test all the path features at once"
    (is (= "hi bob!"
           (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c {true {nil :bob}}}]}])}]
             (+> m 1 2 "b" 2 :c true nil name #(str "hi " % "!")))))
    (is (= "hi bob!"
           (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c {true {nil :bob}}}]}])}]
             (+>> m 1 2 "b" 2 :c true nil name #(str "hi " % "!")))))
    (is (= "hi bob!"
           (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c {true {nil :bob}}}]}])}]
             (x> m 1 2 "b" 2 :c true nil name #(str "hi " % "!")))))
    (is (= "hi bob!"
           (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c {true {nil :bob}}}]}])}]
             (x>> m 1 2 "b" 2 :c true nil name #(str "hi " % "!")))))
    (is (= "hi bob!"
           (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c {true {nil :bob}}}]}])}]
             (=> m 1 2 "b" 2 :c true nil name #(str "hi " % "!")))))
    (is (= "hi bob!"
           (let [m {1 (rest ['ignore0 0 1 {"b" [0 1 {:c {true {nil :bob}}}]}])}]
             (=>> m 1 2 "b" 2 :c true nil name #(str "hi " % "!")))))))

(deftest thread-last-transducers
  (testing "exercise thread-last macros"
    (is (= 1044
           (+>> (range 100)
                (map inc)
                (filter odd?)
                (mapcat #(do [% (dec %)]))
                (partition-by #(= 0 (mod % 5)))
                (map (partial apply +))
                (map (partial + 10))
                (map #(do {:temp-value %}))
                (map :temp-value)
                (filter even?)
                (apply +))))
    (is (= 1044
           (x>> (range 100)
                (map inc)
                (filter odd?)
                (mapcat #(do [% (dec %)]))
                (partition-by #(= 0 (mod % 5)))
                (map (partial apply +))
                (map (partial + 10))
                (map #(do {:temp-value %}))
                (map :temp-value)
                (filter even?)
                (apply +))))
    (is (= 1044
           (=>> (range 100)
                (map inc)
                (filter odd?)
                (mapcat #(do [% (dec %)]))
                (partition-by #(= 0 (mod % 5)))
                (map (partial apply +))
                (map (partial + 10))
                (map #(do {:temp-value %}))
                (map :temp-value)
                (filter even?)
                (apply +))))))
