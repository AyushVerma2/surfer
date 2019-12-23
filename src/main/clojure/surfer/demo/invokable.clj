(ns surfer.demo.invokable
  (:require [starfish.core :as sf]
            [starfish.alpha :as sfa]
            [clojure.tools.logging :as log]
            [surfer.storage :as storage]
            [surfer.env :as env]
            [surfer.app-context :as app-context]
            [surfer.demo.asset.content :as asset.content]
            [clojure.data.json :as data.json]
            [clojure.java.io :as io]))

(defn ^{:params {} :results {:range "json"}} make-range
  "Make range 0-10"
  [_ _]
  {:range (vec (range 10))})

(defn ^{:params {} :results {:range "asset"}} make-range-asset
  "Make range 0-10"
  [_ _]
  {:range (vec (range 10))})

(defn ^{:params {"numbers" "json"} :results {"odds" "json"}} filter-odds
  "Filter odd numbers"
  [_ params]
  {:odds (vec (filter odd? (:numbers params)))})

(defn ^{:params {"coll1" "json" "coll2" "json"} :results {"coll" "json"}} concatenate
  "Concatenate collections"
  [_ params]
  {:coll (into (:coll1 params) (:coll2 params))})

(defn ^{:params {"n" "json"}} invokable-odd? [_ params]
  (let [n (:n params)]
    {:n n
     :is_odd (odd? n)}))

(defn ^{:params {"n" "asset"}} invokable-asset-odd? [_ {:keys [n]}]
  (let [{:keys [n]} (asset.content/json-reader n)]
    {:n n
     :is_odd (odd? n)}))
