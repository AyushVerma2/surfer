(ns surfer.invokable
  (:require
    [surfer.store :as store]
    [surfer.env :as env]
    [starfish.core :as sf]
    [clojure.tools.logging :as log]
    [surfer.app-context :as app-context]
    [clojure.data.json :as json]
    [starfish.alpha :as sfa]
    [clojure.walk :as walk])
  (:import (sg.dex.starfish.util DID)
           (sg.dex.starfish.impl.memory MemoryAgent ClojureOperation MemoryAsset)
           (java.time Instant)))

(defn- wrapped-params [metadata params]
  (let [params (walk/keywordize-keys params)]
    (reduce
      (fn [params [param-name param-type]]
        (if (= "asset" param-type)
          (let [did (sf/did (get-in params [param-name :did]))
                agent (sfa/did->agent did)
                asset (sf/get-asset agent did)]
            (assoc params param-name asset))
          params))
      params
      (get-in metadata [:operation :params]))))

(defn wrap-params [invokable metadata]
  (fn [app-context params]
    (->> (wrapped-params metadata params)
         (invokable app-context))))

(defn format-asset-result
  [^DID agent-did asset]
  {:did (str (.withPath agent-did (sf/asset-id asset)))})

;; TODO: More thinking
;; Don't need 'asset-fn' - it's up to the user.
;; When should the Asset be uploaded?
;; What are the possible return types and the "handling" for each type:
;;  - String DID
;;  - DID
;;  - MemoryAsset (upload)
;;  - RemoteAsset (don't upload)
(defn- wrapped-results [metadata results]
  (let [results (walk/keywordize-keys results)]
    (reduce
      (fn [new-results [result-name result-value]]
        (let [result-value (if (= "asset" (get-in metadata [:operation :results result-name]))
                             (let [;; TODO
                                   agent (sfa/did->agent (sf/did "did:dex:1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c"))
                                   asset (sf/upload agent result-value)]
                               {:did (str (sf/did asset))})
                             result-value)]
          (assoc new-results result-name result-value)))
      {}
      results)))

(defn wrap-results [invokable metadata]
  (fn [app-context params]
    (->> (invokable app-context params)
         (wrapped-results metadata))))

(defn resolve-invokable [metadata]
  (some-> (get-in metadata [:additionalInfo :function])
          (symbol)
          (resolve)))

(defn invokable-metadata
  "Returns an Operation Metadata map.

   `obj` *must* be a Var, and its value *must* be a function.

   Params are extracted from `obj` metadata, but you can pass an option map
   with `params` and `results` to be used instead.

   DEP 8 - Asset Metadata
   https://github.com/DEX-Company/DEPs/tree/master/8"
  [obj]
  (let [{:keys [doc operation]} (meta obj)]
    {:name (or doc "Unnamed Operation")
     :type "operation"
     :dateCreated (str (Instant/now))

     ;; TODO - Remove
     :additionalInfo {:function (-> obj symbol str)}

     :operation {:modes ["sync" "async"]
                 :params (or (:params operation) {})
                 :results (or (:results operation) {})}}))

(defn invokable-operation [app-context metadata]
  (let [invokable (resolve-invokable metadata)

        invokable (-> invokable
                      (wrap-params metadata)
                      (wrap-results metadata))

        metadata-str (json/write-str metadata)]
    (ClojureOperation/create metadata-str (MemoryAgent/create) (fn [params]
                                                                 (invokable app-context params)))))

(defn register-invokable [agent metadata]
  (sf/register agent (sf/memory-asset metadata "")))

(defn invoke [invokable context params]
  (let [metadata (invokable-metadata invokable)
        operation (invokable-operation context metadata)]
    (sf/invoke-result operation params)))

