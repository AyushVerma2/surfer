(ns surfer.demo.invokable-demo
  (:require [surfer.demo.asset.content :as asset.content]
            [clojure.data.json :as data.json]
            [starfish.core :as sf]))

(defn make-range
  "Make range 0-10"
  {:params {}
   :results {:range "json"}}
  [_ _]
  {:range (vec (range 10))})

(defn make-range-asset
  "Make range 0-10"
  {:params {}
   :results {:range "asset"}
   :asset-results {:range {:asset-fn (comp sf/memory-asset data.json/write-str)}}}
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

(defn concatenate-asset
  "Concatenate collections"
  {:params
   {:coll1 "asset"
    :coll2 "asset"}
   :asset-params
   {:coll1 {:data-fn #(data.json/read % :key-fn keyword)}
    :coll2 {:data-fn #(data.json/read % :key-fn keyword)}}
   :results
   {:coll "asset"}
   :asset-results
   {:coll {:asset-fn (comp sf/memory-asset data.json/write-str)}}}
  [_ params]
  (let [coll1 (get-in params [:asset-params :coll1 :data])
        coll2 (get-in params [:asset-params :coll2 :data])]
    {:coll (into coll1 coll2)}))

(defn invokable-odd?
  {:params {:n "json"}}
  [_ params]
  (let [n (:n params)]
    {:n n
     :is_odd (odd? n)}))

(defn n-odd?
  {:params {:n "asset"}
   :asset-params {:n {:data-fn #(data.json/read % :key-fn keyword)}}
   :results {:is_odd "json"}}
  [_ params]
  (let [n (get-in params [:asset-params :n :data])]
    {:is_odd (odd? n)}))
