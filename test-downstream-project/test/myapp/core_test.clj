(ns myapp.core-test
  (:require [clojure.test :refer [deftest testing is]]
            [myapp.core :as core]))

(deftest path-thread-test
  (testing "+> navigates nested heterogeneous data structures"
    (is (= "res" (core/path-thread-demo)))))

(deftest transducify-test
  (testing "x>> produces correct results with auto-transducification"
    (let [result (core/transducify-demo)]
      (is (number? result))
      (is (pos? result))
      ;; Compare with equivalent ->> to ensure same result
      (is (= result
             (->> (range 1000)
                  (map inc)
                  (filter odd?)
                  (mapcat #(do [% (dec %)]))
                  (partition-by #(= 0 (mod % 5)))
                  (map (partial apply +))
                  (map (partial + 10))
                  (map #(do {:temp-value %}))
                  (map :temp-value)
                  (filter even?)
                  (apply +)))))))

(deftest parallel-test
  (testing "=>> produces correct results with parallel transducification"
    (let [result (core/parallel-demo)]
      (is (number? result))
      (is (pos? result))
      ;; Compare with equivalent ->> to ensure same result
      (is (= result
             (->> (range 1000)
                  (map inc)
                  (filter even?)
                  (map #(* % %))
                  (filter #(= 0 (mod % 4)))
                  (apply +)))))))

(deftest lambda-wrapping-test
  (testing "+> threads through anonymous functions"
    (is (= 6 (core/lambda-wrapping-demo)))))

(deftest path-thread-last-test
  (testing "+>> works with path navigation and lambdas"
    (is (= "hi bob !\n" (core/path-thread-last-demo)))))
