(ns surfer.job
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :as pprint]
            [clojure.tools.logging :as log]
            [clojure.datafy :as datafy]
            [surfer.app-context :as app-context]
            [surfer.orchestration :as orchestration]
            [surfer.store :as store]
            [surfer.invokable :as invokable])
  (:import (java.time LocalDateTime)))

(defn new-job [db job]
  (let [m (first (jdbc/insert! db "JOBS" job))]
    (:id m)))

(defn update-job [db job]
  (jdbc/update! db "JOBS" job ["id = ?" (:id job)]))

(defn get-job [db id]
  (first (jdbc/query db ["SELECT * FROM JOBS WHERE ID = ?" id])))

(defn pretty-status [process]
  (let [status (->> process
                    (map
                      (fn [[nid invocation]]
                        [nid (name (:orchestration-invocation/status invocation))]))
                    (into {}))]
    (with-out-str (pprint/print-table [status]))))

(defn watch-job [db id]
  (fn [process]
    (log/debug (str "JOB-ID " id (pretty-status process)))

    (update-job db {:id id
                    :status (name (get-in process [orchestration/root-nid :orchestration-invocation/status]))
                    :results (str (orchestration/results {:orchestration-execution/process process}))
                    :updated_at (LocalDateTime/now)})))

(defn- run-operation* [invokable app-context params job-id]
  (try
    (let [results (invokable/invoke invokable app-context params)]
      (update-job (app-context/db app-context) {:id job-id
                                                :status (name :orchestration-invocation.status/succeeded)
                                                :results (str results)
                                                :updated_at (LocalDateTime/now)})

      results)
    (catch Exception e
      (update-job (app-context/db app-context) {:id job-id
                                                :status (name :orchestration-invocation.status/failed)
                                                :error (str (datafy/datafy e))
                                                :updated_at (LocalDateTime/now)})

      (throw e))))

(defn- run-job* [app-context oid params & [{:keys [async?] :or {async? false}}]]
  (let [db (app-context/db app-context)
        metadata (store/get-metadata db oid {:key-fn keyword})]
    (cond
      (nil? metadata)
      (throw (ex-info "Metadata not found." {:job/error :job.error/missing-metadata}))

      (not= "operation" (:type metadata))
      (throw (ex-info "Metadata type must be 'operation'." {:job/error :job.error/invalid-type})))

    (let [job-id (new-job db {:operation oid
                              :created_at (LocalDateTime/now)})]
      (cond
        (= "orchestration" (get-in metadata [:operation :class]))
        (try
          (let [orchestration (orchestration/get-orchestration app-context oid)
                watch (watch-job db job-id)]
            (if async?
              (do
                (orchestration/execute-async app-context orchestration params {:watch watch})
                {:jobid job-id})
              (orchestration/results (orchestration/execute app-context orchestration params {:watch watch}))))
          (catch Exception e
            (throw (ex-info (.getMessage e) {:job/error :job.error/orchestration-failed} e))))

        (= "operation" (:type metadata))
        (try
          (let [invokable (invokable/resolve-invokable metadata)
                f #(run-operation* invokable app-context params job-id)]
            (if async?
              (do
                (future (f))
                {:jobid job-id})
              (f)))
          (catch Exception e
            (throw (ex-info (.getMessage e) {:job/error :job.error/operation-failed} e))))))))

(defn run-job [app-context oid params]
  (run-job* app-context oid params {:async? false}))

(defn run-job-async [app-context oid params]
  (run-job* app-context oid params {:async? true}))

