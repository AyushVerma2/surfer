(ns surfer.demo.invokable
  (:require [starfish.core :as sf]
            [starfish.alpha :as sfa]
            [clojure.tools.logging :as log]
            [surfer.storage :as storage]
            [surfer.env :as env]
            [surfer.app-context :as app-context]
            [clojure.data.json :as data.json]
            [clojure.java.io :as io])
  (:import (sg.dex.starfish.impl.memory MemoryAgent ClojureOperation)))

(def app-context->storage-config
  (comp env/storage-config app-context/env))

(defn resolve-invokable [metadata]
  (some-> (get-in metadata [:additionalInfo :function])
          (symbol)
          (resolve)))

(defn invokable-metadata [invokable]
  (let [params-results (select-keys (meta invokable) [:params :results])]
    (sf/invokable-metadata invokable params-results)))

(defn invokable-operation [app-context metadata]
  (let [invokable (resolve-invokable metadata)

        metadata-str (data.json/write-str metadata)

        closure (fn [params]
                  (invokable app-context params))]
    (ClojureOperation/create metadata-str (MemoryAgent/create) closure)))

(defn ^{:params {"n" "json"}} invokable-odd? [_ params]
  (let [n (:n params)]
    {:n n
     :is_odd (odd? n)}))

(defn ^{:params {"n" "asset"}} invokable-asset-odd? [app-context params]
  (let [storage-path (env/storage-path (app-context/env app-context))
        asset-id (sf/asset-id (get-in params [:n "did"]))]
    (with-open [input-stream (storage/load-stream storage-path asset-id)]
      (let [{:keys [n]} (data.json/read (io/reader input-stream) :key-fn keyword)]
        {:n n
         :is_odd (odd? n)}))))

(defn ^{:params {"n" "asset"}} invokable-asset-odd?2 [app-context params]
  (let [did (sf/did (get-in params [:n "did"]))
        agent (sfa/did->agent did)
        asset (sf/get-asset agent did)]
    (with-open [input-stream (sf/asset-content-stream asset)]
      (let [{:keys [n]} (data.json/read (io/reader input-stream) :key-fn keyword)]
        {:n n
         :is_odd (odd? n)}))))
