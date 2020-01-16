(ns surfer.job
  (:require [clojure.java.jdbc :as jdbc]))

(defn new-job [db job]
  (let [m (first (jdbc/insert! db "JOBS" job))]
    (:id m)))

(defn update-job [db job]
  (jdbc/update! db "JOBS" job ["id = ?" (:id job)]))
