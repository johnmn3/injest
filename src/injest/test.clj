(ns injest.test
  (:require
   [injest.state :as i.s]
   [injest.report :as r]
   [injest.report.path :as injest :refer [+> +>> x>> =>>]]))

(comment

  (r/add-report-tap! println)
  (r/report! false)

  (dotimes [_ 10]
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
         time))

  (dotimes [_ 10]
    (x>> (range 1000000)
         (map inc)
         (filter odd?)
      ;;  (mapv dec)
         (map (partial + 10))
         (filter even?)
      ;;  (x/reduce +)
      ;;  first
         (apply +)
         time))

  (dotimes [_ 10]
    (=>> (range 1000000)
         (map inc)
         (filter odd?)
      ;;  (mapv dec)
         (map (partial + 10))
         (filter even?)
      ;;  (x/reduce +)
      ;;  first
         (apply +)
         time))
  
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
      ;;  (x/reduce +)
      ;;  first
       (apply +)
       time)

  :end
  )