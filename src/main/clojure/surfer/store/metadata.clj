(ns surfer.store.metadata
  (:require [clojure.java.jdbc :as jdbc]))

(defn index [db]
  (jdbc/query db ["SELECT ID, METADATA FROM METADATA"]))
