(ns surfer.demo.invokable
  (:require [surfer.demo.asset.content :as asset.content]))

(defn make-range
  "Make range 0-10"
  {:params {}
   :results {:range "json"}}
  [_ _]
  {:range (vec (range 10))})

(defn make-range-asset
  "Make range 0-10"
  {:params {}
   :results {:range "asset"}}
  [_ _]
  {:range (vec (range 10))})

(defn filter-odds
  "Filter odd numbers"
  {:params {:numbers "json"}
   :results {:odds "json"}}
  [_ params]
  {:odds (vec (filter odd? (:numbers params)))})

(defn concatenate
  "Concatenate collections"
  {:params {:coll1 "json"
            :coll2 "json"}
   :results {:coll "json"}}
  [_ params]
  {:coll (into (:coll1 params) (:coll2 params))})

(defn invokable-odd?
  {:params {:n "json"}}
  [_ params]
  (let [n (:n params)]
    {:n n
     :is_odd (odd? n)}))

(defn invokable-asset-odd?
  {:params {:n "asset"}}
  [_ {:keys [n]}]
  (let [{:keys [n]} (asset.content/json-reader n)]
    {:n n
     :is_odd (odd? n)}))
