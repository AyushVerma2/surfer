(ns surfer.store
  (:require [surfer.utils :as u]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc])
  (:import [java.time LocalDateTime]))

(def db {:dbtype "h2"
         :dbname "~/surfertest"})

(Class/forName "org.h2.Driver")

(defn create-db! [db] 
  (jdbc/execute! db 
  "CREATE TABLE IF NOT EXISTS Metadata ( 
     id CHAR(64) NOT NULL PRIMARY KEY, 
     metadata varchar NOT NULL, 
     utime TIMESTAMP NOT NULL
   );"
  ))

(defn drop-db! [db]
  (jdbc/execute! db 
  "drop TABLE Metadata;"
  ))

(create-db! db)

(defonce database (atom {}))

(defn register [^String asset-metadata-str]
  (let [hash (u/hex-string (u/keccak256 asset-metadata-str))
        rs (jdbc/query db ["select * from Metadata where id = ?" hash])]
    (if (empty? rs)
      (jdbc/insert! db "Metadata" 
                    {:id hash 
                     :metadata asset-metadata-str
                     :utime (LocalDateTime/now)}))
    hash))

(defn lookup [^String id-str]
  (let [rs (jdbc/query db ["select * from Metadata where id = ?" id-str])]
    (if (empty? rs)
      nil
      (str (:metadata (first rs))))))

(defn all-keys []
  (keys @database))