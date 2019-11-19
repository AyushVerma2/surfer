(ns surfer.demo.invokable
  (:require [starfish.core :as sf]
            [clojure.tools.logging :as log]))

(defn ^{:params {"n" "json"}} invokable-odd? [params]
  (let [n (:n params)]
    {:n n
     :odd? (odd? n)}))

(def invokable-odd?-metadata
  (sf/invokable-metadata #'invokable-odd? (meta #'invokable-odd?)))

(def operation-odd?
  (sf/in-memory-operation invokable-odd?-metadata))


(defn ^{:params {"n" "asset"}} asset-odd? [params]
  (let [asset (sf/asset (get-in params [:n "did"]))

        {:keys [n]} (-> (sf/content asset)
                        (sf/to-string)
                        (sf/read-json-string))]
    {:n n
     :odd? (odd? n)}))

(def invokable-asset-odd?-metadata
  (sf/invokable-metadata #'asset-odd? (meta #'asset-odd?)))

(def operation-asset-odd?
  (sf/in-memory-operation invokable-asset-odd?-metadata))


(defn invokable-asset2-odd? [params]
  ;; Asset -> Asset
  (let [n 1]
    {:n n
     :odd? (odd? n)}))

(defn ^{:params {"n" "json"}} invokable-inc [params]
  (update params :n inc))

(def invokable-inc-metadata
  (sf/invokable-metadata #'invokable-inc (meta #'invokable-inc)))

(def operation-inc
  (sf/in-memory-operation invokable-inc-metadata))