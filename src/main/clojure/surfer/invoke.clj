(ns surfer.invoke
  (:require
    [surfer.storage :as storage]
    [surfer.store :as store]
    [surfer.env :as env]
    [starfish.core :as sf]
    [surfer.utils :as utils]
    [schema.core :as s]
    [clojure.tools.logging :as log]
    [surfer.app-context :as app-context]
    [clojure.data.json :as data.json]
    [starfish.alpha :as sfa]
    [clojure.walk :as walk]
    [clojure.java.io :as io])
  (:import (sg.dex.starfish.util DID)
           (sg.dex.starfish.impl.memory MemoryAgent ClojureOperation)))

(defn- wrapped-params [imeta params]
  (let [params (walk/keywordize-keys params)]
    (reduce
      (fn [params [param-name param-type]]
        (if (= "asset" param-type)
          (let [did (sf/did (get-in params [param-name :did]))
                agent (sfa/did->agent did)
                asset (sf/get-asset agent did)
                data-fn (get-in imeta [:asset-params param-name :data-fn])
                data (with-open [input-stream (sf/content-stream asset)]
                       (data-fn (io/reader input-stream)))]
            (-> params
                (assoc-in [:asset-params param-name :asset] asset)
                (assoc-in [:asset-params param-name :data] data)))
          params))
      params
      (:params imeta))))

(defn wrap-params [invokable imeta]
  (fn [context params]
    (->> (wrapped-params imeta params)
         (invokable context))))

(defn- wrapped-results [imeta results]
  (let [results (walk/keywordize-keys results)]
    (reduce
      (fn [new-results [result-name result-value]]
        (let [result-value (if (= "asset" (get-in imeta [:results result-name]))
                             (let [asset-fn (get-in imeta [:asset-results result-name :asset-fn])
                                   asset (asset-fn result-value)

                                   ;; FIXME
                                   agent (sfa/did->agent (sf/did "did:dex:1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c"))

                                   remote-asset (sf/upload agent asset)]
                               {:did (str (sf/did remote-asset))})
                             result-value)]
          (assoc new-results result-name result-value)))
      {}
      results)))

(defn wrap-results [invokable imeta]
  (fn [context params]
    (->> (invokable context params)
         (wrapped-results imeta))))

(defonce JOBS (atom {}))

(defn resolve-invokable [metadata]
  (some-> (get-in metadata [:additionalInfo :function])
          (symbol)
          (resolve)))

(defn invokable-metadata [invokable]
  (let [params-results (select-keys (meta invokable) [:params :results])]
    (walk/keywordize-keys (sf/invokable-metadata invokable params-results))))

(defn invokable-operation [context metadata]
  (let [invokable (resolve-invokable metadata)

        imeta (meta invokable)

        invokable (-> invokable
                      (wrap-params imeta)
                      (wrap-results imeta))

        metadata-str (data.json/write-str metadata)]
    (ClojureOperation/create metadata-str (MemoryAgent/create) (fn [params]
                                                                 (invokable context params)))))

(defn register-invokable [agent metadata]
  (sf/register agent (sf/memory-asset metadata "")))

(defn invoke [invokable context params]
  (let [metadata (invokable-metadata invokable)
        operation (invokable-operation context metadata)]
    (sf/invoke-result operation params)))

(defn get-operation
  "Gets an in-memory operation for the given operation id"
  [db op-id]
  (let [op-meta (store/get-metadata-str db op-id)
        md (sf/read-json-string op-meta)
        op-function (:function (:additionalInfo md))
        params (keys (:params (:operation md)))
        op-sym (symbol op-function)
        f (resolve op-sym)]
    (when f (sf/create-operation params f))))

(defn get-asset
  "Gets an asset in the context of this surfer instance."
  ([did]
   (sf/asset did)))

(defn coerce-input-params
  "Coerces the input request to a map of keywords to assets / objects"
  ([md req]
   (let [pspecs (:params (:operation md))]
     (reduce
       (fn [m [k v]]
         (if-let [pspec (get pspecs (keyword k))]
           (let [type (:type pspec)]
             (if (= "asset" type)
               (assoc m (keyword k) (get-asset (:did v)))
               (assoc m (keyword k) v)))
           m))
       req
       req))))

(defn launch-job
  "Launch a job using the given function. 

   Returns jobid if successful, null if operation cannot be found."
  [db op-id invoke-req]
  (let [op (get-operation db op-id)]
    (when op
      (let [jobid (sf/random-hex-string 32)
            md (sf/metadata op)
            job (sf/invoke op (coerce-input-params md invoke-req))]
        (swap! JOBS assoc jobid job)
        (log/debug (str "Job started with ID [" jobid "]"))
        jobid))))

(defn get-job
  "Gets the job for a given Job ID"
  [jobid]
  (@JOBS jobid))

(defn format-asset-result
  [^DID agent-did asset]
  {:did (str (.withPath agent-did (sf/asset-id asset)))})

(defn format-results
  "Formats results ready for output in a job status :result value."
  [^DID agent-did rs]
  (into {}
        (map (fn [[k v]]
               (if (sf/asset? v)
                 [(keyword k) (format-asset-result agent-did v)]
                 [(keyword k) v]))
             rs)))

(defn job-response
  "Gets the appropriate response map for a job result, or null if the job does not exist."
  [app-context jobid]
  (when-let [job (get-job jobid)]
    (let [agent-did (env/self-did (app-context/env app-context))

          _ (try
              (sf/poll-result job)
              (catch Throwable _))

          status (sf/job-status job)
          resp {:status status}
          resp (if (= status :succeeded)
                 (assoc resp :results (format-results agent-did (sf/get-result job)))
                 resp)
          resp (if (= status :failed)
                 (assoc resp :message (try (sf/get-result job)
                                           (catch Throwable t (.getMessage t))))
                 resp)]
      resp)))