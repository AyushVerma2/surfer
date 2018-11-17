(ns surfer.store
  (:require [surfer.utils :as u]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc])
  (:import [java.time LocalDateTime]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


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
  )
  
  (jdbc/execute! db 
  "CREATE TABLE IF NOT EXISTS Users ( 
     id CHAR(64) NOT NULL PRIMARY KEY, 
     username CHAR(64) NOT NULL, 
     password varchar NOT NULL,
     metadata varchar NOT NULL, 
     ctime TIMESTAMP NOT NULL
   );"
  )
  
    (jdbc/execute! db 
    "CREATE INDEX IF NOT EXISTS IX_USERNAME
     ON USERS(username) ;"
  )
)

(defn drop-db! [db]
  (jdbc/execute! db "drop TABLE Metadata;")
  (jdbc/execute! db "drop TABLE Users;")
  );

(defn truncate-db! [db]
  (jdbc/execute! db 
  "truncate TABLE Metadata;"
  ))

(defn register-asset 
  "Regiaters asset metadata in the data store. Returns the Asset ID as a string."
  ([^String asset-metadata-str]
    (let [hash (u/hex-string (u/keccak256 asset-metadata-str))]
      (register-asset hash asset-metadata-str)))
  ([^String hash ^String asset-metadata-str]
    (let [rs (jdbc/query db ["select * from Metadata where id = ?" hash])]
      (if (empty? rs)
        (jdbc/insert! db "Metadata" 
                      {:id hash 
                       :metadata asset-metadata-str
                       :utime (LocalDateTime/now)}))
      hash)))

(defn register-user 
  "Regiaters a user in the data store. Returns the New User ID as a string."
  ([user-data]
    (let [id (utils/new-random-id)
          rs (jdbc/query db ["select * from Users where id = ?" hash])]
      (if (empty? rs)
        (jdbc/insert! db "Users" 
                      {:id id
                       :username (:username user-data)
                       :password (:password user-data)
                       :metadata (json/write-str {}) 
                       :ctime (LocalDateTime/now)}))
      hash)))

(defn lookup 
  "Gets the metadata string for a given Asset ID, or nil if not available."
  ([^String id-str]
    (let [rs (jdbc/query db ["select * from Metadata where id = ?" id-str])]
    (if (empty? rs)
      nil
        (str (:metadata (first rs)))))))

(defn lookup-json 
  "Gets the JSON data structure for the metadata of a given asset ID.
   Returns nil if the metadata is not available."
  ([^String id-str]
    (if-let [meta (lookup id-str)]
      (json/read-str meta) 
      nil)))

(defn all-keys []
  (let [rs (jdbc/query db ["select id from Metadata;"])]
    (map :id rs)))

;; ===================================================
;; User management

(defn list-users []
  (let [rs (jdbc/query db ["select * from Users;"])]
    (map (fn [user] {:id (:id user)
                     :username (:username user)}) rs)))
