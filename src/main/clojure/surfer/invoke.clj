(ns surfer.invoke
  (:require 
    [surfer.storage :as storage]
    [surfer.store :as store]
    [surfer.config :as config]
    [starfish.core :as sf]
    [surfer.utils :as utils]
    [schema.core :as s]
    [clojure.data.json :as json])
  (:import [sg.dex.starfish.util DID]))

(def JOBS (atom {}))

(defn get-operation 
  "Gets an in-memory operation for the given operation id"
  [op-id]
  (let [op-meta (store/lookup op-id)
        md (sf/read-json-string op-meta)
        op-function (:function (:additionalInfo md))
        params (keys (:params (:operation md)))
        op-sym (symbol op-function)
        f (resolve op-sym)]
    (when f (sf/create-operation params f))))

(defn launch-job 
  "Launch a job using the given function. 

   Returns jobid if successful, null if operation cannot be found."
  [op-id invoke-req]
  (let [op (get-operation op-id)]
    (when op
      (let [jobid (sf/random-hex-string 64)
            job (sf/invoke op )] 
        (swap! JOBS assoc jobid job)
        jobid))))

(defn get-job 
  "Gets the job for a given Job ID"
  [jobid]
  (@JOBS jobid))

(defn format-asset-result 
  [a]
  (let [id (sf/asset-id a)
        ^DID d config/DID
        d (.withPath d id)]
    {:did (str d)}))

(defn format-results
  "Formats results ready for output in a job status :result value."
  [rs]
  (into {}
        (map (fn [[k v]]
               (if (sf/asset? v)
                 [(keyword k) (format-asset-result v)]
                 [(keyword k) v]))
             rs)))

(defn job-response 
  "Gets the appropriate response map for a job result, or null if the job does not exist."
  [jobid]
  (when-let [job (get-job jobid)]
    (let [status (sf/job-status job)
          resp {:status status}
          resp (if (= status "succeeded")
                 (assoc resp :result (format-results (sf/get-result job)))
                 resp)]
      resp)))