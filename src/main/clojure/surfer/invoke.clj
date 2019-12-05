(ns surfer.invoke
  (:require
    [surfer.storage :as storage]
    [surfer.store :as store]
    [surfer.env :as env]
    [starfish.core :as sf]
    [surfer.utils :as utils]
    [schema.core :as s]
    [clojure.data.json :as json]
    [clojure.tools.logging :as log]
    [surfer.app-context :as app-context])
  (:import [sg.dex.starfish.util DID]))

(defonce JOBS (atom {}))

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

(defn coerce-input-params 
  "Coerces the input request to a map of keywords to assets / objects"
  ([md req]
    (let [pspecs (:params (:operation md))]
      (reduce 
        (fn [m [k v]]
          (if-let [pspec (get pspecs (keyword k))]
            (let [type (:type pspec)]
              (if (= "asset" type)
                (assoc m (keyword k) (-> (sf/get-agent (:did v))
                                         (sf/get-asset (:did v))))
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
            md (sf/asset-metadata op)
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
    (let [agent-did (env/agent-did (app-context/env app-context))

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