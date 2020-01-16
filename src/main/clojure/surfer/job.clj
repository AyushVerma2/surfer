(ns surfer.job
  (:require [clojure.java.jdbc :as jdbc])
  (:import (java.time LocalDateTime)))

(defn new [db oid]
  (let [m (first (jdbc/insert! db "JOBS" {:operation oid :created_at (LocalDateTime/now)}))]
    (:id m)))

(defn set-results [db id results]
  (jdbc/update! db "JOBS" {:results results :updated_at (LocalDateTime/now)} ["id = ?" id]))
