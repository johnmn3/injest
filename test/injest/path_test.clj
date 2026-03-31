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

;; ===================================================================
;; Thread-first transducer tests (gap: previously only thread-last)
;; ===================================================================

(deftest thread-first-transducers
  (testing "x> with map transducer"
    (is (= [2 3 4] (vec (x> [1 2 3] (map inc))))))

  (testing "x> with map and filter"
    (is (= [2 4] (vec (x> [1 2 3 4] (map inc) (filter even?))))))

  (testing "x> with transducer and vec"
    (is (= [2 4] (x> [1 2 3 4] (map inc) (filter even?) vec))))
    ;; NOTE: (apply +) and (reduce +) require thread-last semantics.

  (testing "+> thread-first semantics with multi-arg fn"
    (is (= {:a 1 :b 2} (+> {:a 1} (assoc :b 2))))
    (is (= {:a 1 :b 2 :c 3} (+> {:a 1} (assoc :b 2) (assoc :c 3)))))

  (testing "+> with conj threads as first arg"
    (is (= [1 2 3] (+> [1 2] (conj 3))))))

;; ===================================================================
;; Thread-last semantics tests
;; ===================================================================

(deftest thread-last-semantics
  (testing "+>> threads as last arg"
    (is (= [1 2 3 99] (+>> 99 (conj [1 2 3])))))

  (testing "+>> with range"
    (is (= '(0 1 2) (+>> 3 (range)))))

  (testing "+>> with str"
    (is (= "hello world" (+>> " world" (str "hello"))))))

;; ===================================================================
;; Pipeline |> and |>> tests
;; ===================================================================

(deftest pipeline-parallel-macros
  (testing "|>> with stateless transducers"
    (is (= [2 3 4] (vec (|>> [1 2 3] (map inc))))))

  (testing "|>> with map and filter"
    (is (= [2 4] (vec (|>> [1 2 3 4] (map inc) (filter even?))))))

  (testing "|>> full pipeline matches x>>"
    (is (= 1044
           (|>> (range 100)
                (map inc)
                (filter odd?)
                (mapcat #(do [% (dec %)]))
                (partition-by #(= 0 (mod % 5)))
                (map (partial apply +))
                (map (partial + 10))
                (map #(do {:temp-value %}))
                (map :temp-value)
                (filter even?)
                (apply +)))))

  (testing "|> with map"
    (is (= [2 3 4] (vec (|> [1 2 3] (map inc)))))))

;; ===================================================================
;; Real-world data patterns
;; ===================================================================

(deftest json-like-data-navigation
  (testing "navigating JSON-like nested data"
    (let [api-response {:status 200
                        :body {:users [{:name "Alice" :age 30}
                                       {:name "Bob" :age 25}
                                       {:name "Carol" :age 35}]}}]
      (is (= "Bob"
             (+> api-response :body :users 1 :name)))
      (is (= "Bob"
             (+>> api-response :body :users 1 :name)))
      (is (= 35
             (+> api-response :body :users 2 :age))))))

(deftest config-tree-navigation
  (testing "navigating config-like nested data"
    (let [config {:db {:host "localhost"
                       :port 5432
                       :pools {"main" {:size 10}
                               "read" {:size 20}}}}]
      (is (= 5432
             (+> config :db :port)))
      (is (= 10
             (+> config :db :pools "main" :size)))
      (is (= 20
             (+>> config :db :pools "read" :size))))))

(deftest transducer-pipeline-on-data
  (testing "extract and transform from nested data"
    (let [data {:items [{:price 10 :qty 2}
                        {:price 20 :qty 1}
                        {:price 5  :qty 4}]}]
      (is (= 60
             (+> data :items
                 (x>> (map #(* (:price %) (:qty %)))
                      (apply +)))))))

  (testing "filter and aggregate"
    (is (= [2 4 6 8 10]
           (vec (x>> (range 1 11) (filter even?))))))

  (testing "multi-step transducer composition"
    (is (= ["2" "4" "6"]
           (vec (x>> [1 2 3 4 5 6]
                     (filter even?)
                     (map str)))))))
