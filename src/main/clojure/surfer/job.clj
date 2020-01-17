(ns surfer.job
  (:require [clojure.java.jdbc :as jdbc]
            [clojure.pprint :as pprint]
            [surfer.app-context :as app-context]
            [clojure.tools.logging :as log]
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

(defn- watch [db job-id]
  (fn [process]
    (log/debug (str "JOB-ID " job-id (pretty-status process)))

    (update-job db {:id job-id
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
                                                :updated_at (LocalDateTime/now)})

      (throw e))))

(defn run-job [app-context oid params]
  (let [db (app-context/db app-context)

        job-id (new-job db {:operation oid
                            :created_at (LocalDateTime/now)})

        metadata (store/get-metadata db oid {:key-fn keyword})]
    (cond
      (nil? metadata)
      (throw (ex-info "Can't find metadata." {:error :missing-metadata}))

      (= "orchestration" (get-in metadata [:operation :class]))
      (try
        (orchestration/results (as-> (orchestration/get-orchestration app-context oid) orchestration
                                     (orchestration/execute app-context orchestration params {:watch (watch db job-id)})))
        (catch Exception e
          (throw (ex-info (.getMessage e) {:error :orchestration-failed} e))))

      (= "operation" (:type metadata))
      (try
        (run-operation* (invokable/resolve-invokable metadata) app-context params job-id)
        (catch Exception e
          (throw (ex-info (.getMessage e) {:error :operation-failed} e)))))))

