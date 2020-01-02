(ns surfer.demo.invokable-demo
  (:require [clojure.data.json :as data.json]
            [starfish.core :as sf]
            [surfer.orchestration :as orchestration]
            [surfer.invokable :as invoke]
            [surfer.asset :as asset]))

(defn make-range
  "Make range 0-10"
  {:operation
   {:params {}
    :results {:range "json"}}}
  [_ _]
  {:range (vec (range 10))})

(defn make-range-asset
  "Make range 0-10"
  {:operation
   {:params {}
    :results {:range "asset"}}}
  [_ _]
  {:range (sf/memory-asset (data.json/write-str (range 10)))})

(defn filter-odds
  "Filter odd numbers"
  {:operation
   {:params {:numbers "json"}
    :results {:odds "json"}}}
  [_ params]
  {:odds (vec (filter odd? (:numbers params)))})

(defn concatenate
  "Concatenate collections"
  {:operation
   {:params
    {:coll1 "json"
     :coll2 "json"}
    :results {:coll "json"}}}
  [_ params]
  {:coll (into (:coll1 params) (:coll2 params))})

(defn concatenate-asset
  "Concatenate collections"
  {:operation
   {:params
    {:coll1 "asset"
     :coll2 "asset"}
    :results
    {:coll "asset"}}}
  [_ params]
  (let [coll1 (get-in params [:asset-params :coll1 :data])
        coll2 (get-in params [:asset-params :coll2 :data])]
    {:coll (sf/memory-asset (data.json/write-str (into coll1 coll2)))}))

(defn invokable-odd?
  {:operation
   {:params {:n "json"}
    :results {}}}
  [_ params]
  (let [n (:n params)]
    {:n n
     :is_odd (odd? n)}))

(defn n-odd?
  {:operation
   {:params {:n "asset"}
    :results {:is_odd "json"}}}
  [_ params]
  (let [n (asset/read-json-content (:n params))]
    {:is_odd (odd? n)}))

(defn orchestration1
  {:operation
   {:params
    {:make-range-id "json"
     :filter-odds-id "json"}
    :results {:results "json"}}}
  [app-context params]
  (let [orchestration {:children
                       {"make-range" (:make-range-id params)
                        "filter-odds" (:filter-odds-id params)}

                       :edges
                       [{:source "make-range"
                         :target "filter-odds"
                         :ports [:range :numbers]}]}]
    {:results (orchestration/results (orchestration/execute app-context orchestration))}))

(defn orchestration2
  {:operation
   {:params
    {:make-range-id "json"
     :concatenate-id "json"}
    :results {:results "json"}}}
  [app-context params]
  (let [orchestration {:children
                       {"make-range1" (:make-range-id params)
                        "make-range2" (:make-range-id params)
                        "concatenate" (:concatenate-id params)}

                       :edges
                       [{:source "make-range1"
                         :target "concatenate"
                         :ports [:range :coll1]}

                        {:source "make-range2"
                         :target "concatenate"
                         :ports [:range :coll2]}]}]
    {:results (orchestration/results (orchestration/execute app-context orchestration))}))
