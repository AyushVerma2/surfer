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
  {:n (inc n)})

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

(defn make-orchestration-demo1
  "Create Orchestration Demo 1"
  {:operation
   {:params {:n "json"}
    :results {:id "json"}}}
  [app-context {:keys [n]}]
  (let [db (app-context/db app-context)

        increment-metadata (merge (invokable/invokable-metadata #'increment) {:dateCreated "2020-01-01T00:00:00"})
        increment-metadata-str (json/write-str increment-metadata)
        increment-digest (sf/digest increment-metadata-str)
        increment-id (if (store/get-metadata db increment-digest)
                       increment-digest
                       (store/register-asset db increment-digest increment-metadata-str))

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
        edges (into edges [{:source "Root"
                            :target (child-key 0)
                            :ports [:n :n]}

                           {:source (child-key (dec n))
                            :target "Root"
                            :ports [:n :n]}])

        orchestration {:id "Root"
                       :children children
                       :edges edges}
        orchestration-str (json/write-str orchestration)
        orchestration-metadata {:name (str "Orchestration Demo 1 - n " n)
                                :type "operation"
                                :dateCreated "2020-01-01T00:00:00"
                                :operation {:modes ["sync"]
                                            :class "orchestration"
                                            :params {:n "json"}
                                            :results {:results "json"}}}
        orchestration-metadata-str (json/write-str orchestration-metadata)
        orchestration-digest (sf/digest orchestration-metadata-str)
        orchestration-id (if (store/get-metadata db orchestration-digest)
                           orchestration-digest
                           (do
                             (storage/save (env/storage-path (app-context/env app-context)) orchestration-digest orchestration-str)
                             (store/register-asset db orchestration-digest orchestration-metadata-str)))]
    {:id orchestration-id}))

(defn make-orchestration-demo2
  "Create Orchestration Demo 2"
  {:operation
   {:params {}
    :results {:id "json"}}}
  [app-context _]
  (let [db (app-context/db app-context)

        make-range-metadata (merge (invokable/invokable-metadata #'make-range) {:dateCreated "2020-01-01T00:00:00"})
        make-range-metadata-str (json/write-str make-range-metadata)
        make-range-digest (sf/digest make-range-metadata-str)
        make-range-id (if (store/get-metadata db make-range-digest)
                        make-range-digest
                        (store/register-asset db make-range-digest make-range-metadata-str))

        concatenate-metadata (merge (invokable/invokable-metadata #'concatenate) {:dateCreated "2020-01-01T00:00:00"})
        concatenate-metadata-str (json/write-str concatenate-metadata)
        concatenate-metadata-digest (sf/digest concatenate-metadata-str)
        concatenate-id (if (store/get-metadata db concatenate-metadata-digest)
                         concatenate-metadata-digest
                         (store/register-asset db concatenate-metadata-digest concatenate-metadata-str))

        orchestration {:id "Root"
                       :children
                       {"make-range1" make-range-id
                        "make-range2" make-range-id
                        "concatenate" concatenate-id}
                       :edges
                       [{:source "make-range1"
                         :target "concatenate"
                         :ports [:range :coll1]}

                        {:source "make-range2"
                         :target "concatenate"
                         :ports [:range :coll2]}

                        {:source "concatenate"
                         :target "Root"
                         :ports [:coll :coll]}]}
        orchestration-str (json/write-str orchestration)
        orchestration-metadata {:name "Orchestration Demo 2"
                                :type "operation"
                                :dateCreated "2020-01-01T00:00:00"
                                :operation {:modes ["sync"]
                                            :class "orchestration"
                                            :params {}
                                            :results {:results "json"}}}
        orchestration-metadata-str (json/write-str orchestration-metadata)
        orchestration-digest (sf/digest orchestration-metadata-str)
        orchestration-id (if (store/get-metadata db orchestration-digest)
                           orchestration-digest
                           (do
                             (storage/save (env/storage-path (app-context/env app-context)) orchestration-digest orchestration-str)
                             (store/register-asset db orchestration-digest orchestration-metadata-str)))]
    {:id orchestration-id}))
