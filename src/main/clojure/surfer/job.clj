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

(defn pretty-status [process]
  (let [status (->> process
                    (map
                      (fn [[nid invocation]]
                        [nid (name (:orchestration-invocation/status invocation))]))
                    (into {}))]
    (with-out-str (pprint/print-table [status]))))

(defn new-job [db job]
  (let [m (first (jdbc/insert! db "JOBS" job))]
    (:id m)))

(defn update-job [db job]
  (jdbc/update! db "JOBS" job ["id = ?" (:id job)]))

(defn get-job [db id]
  (first (jdbc/query db ["SELECT * FROM JOBS WHERE ID = ?" id])))

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

        job-id (new-job db {:operation oid
                            :created_at (LocalDateTime/now)})

        metadata (store/get-metadata db oid {:key-fn keyword})]
    (cond
      (nil? metadata)
      (throw (ex-info "Can't find metadata." {:error :missing-metadata}))

      (= "orchestration" (get-in metadata [:operation :class]))
      (try
        (let [orchestration (orchestration/get-orchestration app-context oid)

              watch (fn [process]
                      (log/debug (str "JOB-ID " job-id (pretty-status process)))

                      (update-job db {:id job-id
                                      :status (name (get-in process [orchestration/root-nid :orchestration-invocation/status]))
                                      :results (str (orchestration/results {:orchestration-execution/process process}))
                                      :updated_at (LocalDateTime/now)}))]
          (if async?
            (do
              (orchestration/execute-async app-context orchestration params {:watch watch})
              {:jobid job-id})
            (orchestration/results (orchestration/execute app-context orchestration params {:watch watch}))))
        (catch Exception e
          (throw (ex-info (.getMessage e) {:error :orchestration-failed} e))))

      (= "operation" (:type metadata))
      (try
        (let [f #(run-operation* (invokable/resolve-invokable metadata) app-context params job-id)]
          (if async?
            (do
              (future (f))
              {:jobid job-id})
            (f)))
        (catch Exception e
          (throw (ex-info (.getMessage e) {:error :operation-failed} e)))))))

(defn run-job [app-context oid params]
  (run-job* app-context oid params {:async? false}))

(defn run-job-async [app-context oid params]
  (run-job* app-context oid params {:async? true}))

