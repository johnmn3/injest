(ns injest.report
  (:require [clojure.edn :as edn])
  #?(:cljs (:require-macros [injest.report])))

(def mon (atom {}))
(def report-live? (atom false))
(def report-taps (atom {}))

(defn flc [form]
  (let [f (namespace ::x)
        {:as m :keys [line column]} (meta form)
        k (str f "?line=" line "&col=" column)]
    k))

(defn now []
  #?(:clj (.toEpochMilli (java.time.Instant/now))
     :cljs (.now js/Date)))

(defmacro tv [& body]
  `(let [t1# (now)
         res# ~@body
         t2# (now)]
     {:res res# :time (- t2# t1#)}))

(defn add-time [times new-time]
  (let [the-times (take 99 (or times '()))]
    (vec (conj the-times new-time))))

(defn monitor [k applicator body]
  `(let [res# (tv (~applicator ~@body))
         t# (:time res#)
         result# (:res res#)]
     (swap! mon update-in [~k ~(str applicator)]
            #(do {:times (add-time (:times %) (:time res#))
                  :time (int (* 1.0 (/ (apply + (:times %)) (inc (count (:times %))))))
                  :res (:res res#)}))
     result#))

;; render report
(defn round [n]
  (float (/ (int (* 100 n)) 100)))

(defn unzero [n]
  (if (or (nil? n) (= 0 n) (= 0.0 n))
    1
    n))

(defn render-v [v]
  (let [t1 (some-> v (get "injest.path/+>>") :time)
        t2 (some-> v (get "injest.path/x>>") :time)
        t3 (some-> v (get "injest.path/=>>") :time)
        s-ts (->> [{:t (unzero t3) :s "=>>"}
                   {:t (unzero t2) :s "x>>"}
                   {:t (unzero t1) :s "+>>"}]
                  (sort-by :t)
                  reverse)
        max-ts (last s-ts)
        min-ts (first s-ts)
        mid-ts (second s-ts)

        diff1 (round (* 1.0 (/ (:t min-ts) (:t mid-ts))))
        diff2 (round (* 1.0 (/ (:t mid-ts) (:t max-ts))))]
    (if-not t3
      (if (= diff1 1.0)
        (str (:s min-ts) " and " (:s mid-ts) " are basically the same speed")
        (str (:s mid-ts) " is " diff1 " times faster than " (:s min-ts)))
      (str (if (= diff2 1.0)
             (str (:s max-ts) " and " (:s mid-ts) " are basically the same speed")
             (str (:s max-ts) " is " diff2 " times faster than " (:s mid-ts)))
           "\n and \n"
           (if (= diff1 1.0)
             (str (:s mid-ts) " and " (:s min-ts) " are basically the same speed")
             (str (:s mid-ts) " is " diff1 " times faster than " (:s min-ts)))))))

(defn report []
  (->> @mon
       (mapv (fn [[k v]] (str k "\n" (render-v v))))
       sort
       (reduce #(str %1 "\n\n" %2))))

(defn set-report-interval [callback ms]
  #?(:clj (future (while true (do (Thread/sleep ms) (when @report-live? (callback)))))
     :cljs identity #_(js/setInterval #(when @report-live? (callback)) ms)))

(defn report! [bool]
  (when (false? bool)
    (->> @report-taps vals (mapv #?(:clj future-cancel :cljs #(js/clearInterval %))))
    (reset! report-taps {}))
  (reset! report-live? (boolean bool)))

(defn add-report-tap! [handler & [seconds]]
  (let [f (set-report-interval #(handler (report)) (or (* 1000 seconds) 10000))]
    (swap! report-taps assoc handler f)))
