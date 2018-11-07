(ns surfer.store
  (:require [surfer.utils :as u]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]))

(def db {:dbtype "h2"
         :dbname "~/surfertest"})

(Class/forName "org.h2.Driver")

(defn create-db! [db] 
  (jdbc/execute! db 
  "CREATE TABLE IF NOT EXISTS Metadata ( 
     id CHAR(64) NOT NULL PRIMARY KEY, 
     metadata CLOB NOT NULL, 
     utime TIMESTAMP NOT NULL
   );"
  ))

(defonce database (atom {}))

(defn register [asset-metadata-str]
  (let [hash (u/hex-string (u/sha256 asset-metadata-str))]
    (swap! database assoc hash asset-metadata-str)
    hash))

(defn lookup [id-str]
  (get @database id-str))

(defn all-keys []
  (keys @database))