(ns injest.state
  (:require 
   [injest.util :as u]
   [injest.data :as d])  
  #?(:cljs (:require-macros [injest.state])))

(def transducables (atom #{}))

(def par-transducables (atom #{}))

(defmacro reg-xf! [& xfs]
  `(swap! transducables into ~(->> xfs (mapv #(u/qualify-sym % &env)))))

(defn regxf! [& xfs]
  (swap! transducables into xfs))

(defmacro reg-pxf! [& xfs]
  `(swap! par-transducables into ~(->> xfs (mapv #(u/qualify-sym % &env)))))

(defn regpxf! [& xfs]
  (swap! par-transducables into xfs))

(apply regxf! d/def-regs)

(apply regpxf! d/par-regs)

; (regxf! 'clojure.core/map) 
; or (reg-xf! map) ; Must be called from Clojure
