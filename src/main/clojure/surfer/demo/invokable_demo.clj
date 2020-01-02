(ns surfer.demo.invokable-demo
  (:require [clojure.data.json :as json]
            [starfish.core :as sf]
            [surfer.orchestration :as orchestration]
            [surfer.asset :as asset]
            [surfer.invokable :as invokable]
            [surfer.store :as store]
            [surfer.app-context :as app-context]
            [surfer.storage :as storage]
            [surfer.env :as env])
  (:import (java.time Instant)))

(defn increment
  "Increment number by one"
  {:operation
   {:params {:n "json"}
    :results {:n "json"}}}
  [_ {:keys [n]}]
  {:n (inc (or n 0))})

(defn make-orchestration-demo1
  "Register & upload an Orchestration that creates n increment nodes."
  {:operation
   {:params {:n "json"}
    :results {:id "json"}}}
  [app-context {:keys [n]}]
  (let [db (app-context/db app-context)

        increment-metadata (invokable/invokable-metadata #'increment)
        increment-metadata-str (json/write-str increment-metadata)
        increment-digest (sf/digest increment-metadata-str)
        increment-id (store/register-asset db increment-digest increment-metadata-str)

        child-key (fn [n]
                    (str "increment-" n))

        children (reduce
                   (fn [children n]
                     ;; n nodes (children), but same Operation
                     (assoc children (child-key n) increment-id))
                   {}
                   (range n))

        edges (map
                (fn [n]
                  {:source (child-key n)
                   :target (child-key (inc n))
                   :ports [:n :n]})
                (range (dec n)))

        orchestration {:children children :edges edges}
        orchestration-str (json/write-str orchestration)
        orchestration-metadata {:name "Orchestration - Demo 1"
                                :type "operation"
                                :dateCreated (str (Instant/now))
                                :operation {:modes ["sync"]
                                            :class "orchestration"
                                            :params {}
                                            :results {:results "json"}}}
        orchestration-metadata-str (json/write-str orchestration-metadata)
        orchestration-digest (sf/digest orchestration-metadata-str)
        orchestration-id (store/register-asset db orchestration-digest orchestration-metadata-str)
        _ (storage/save (env/storage-path (app-context/env app-context)) orchestration-id orchestration-str)]
    {:id orchestration-id}))

(defn make-range
  "Make range 0-9"
  {:operation
   {:params {}
    :results {:range "json"}}}
  [_ _]
  {:range (vec (range 10))})

(defn make-range-asset
  "Make range 0-9"
  {:operation
   {:params {}
    :results {:range "asset"}}}
  [_ _]
  {:range (sf/memory-asset (json/write-str (range 10)))})

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
    {:coll (sf/memory-asset (json/write-str (into coll1 coll2)))}))

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
