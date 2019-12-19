(ns surfer.demo.invokable
  (:require [starfish.core :as sf]
            [starfish.alpha :as sfa]
            [clojure.tools.logging :as log]
            [surfer.storage :as storage]
            [surfer.env :as env]
            [surfer.app-context :as app-context]
            [clojure.data.json :as data.json]
            [clojure.java.io :as io]))

(defn ^{:params {} :results {"range" "json"}} make-range
  "Make range 0-10"
  [_ _]
  {:range (range 10)})

(defn ^{:params {"numbers" "json"} :results {"odds" "json"}} filter-odds
  "Filter odd numbers"
  [_ params]
  {:odds (filter odd? (:numbers params))})

(defn ^{:params {"coll1" "json" "coll2" "json"} :results {"coll" "json"}} concatenate
  "Concatenate collections"
  [_ params]
  {:coll (into (:coll1 params) (:coll2 params))})

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
    (with-open [input-stream (sf/content-stream asset)]
      (let [{:keys [n]} (data.json/read (io/reader input-stream) :key-fn keyword)]
        {:n n
         :is_odd (odd? n)}))))
