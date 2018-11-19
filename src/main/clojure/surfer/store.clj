(ns surfer.store
  (:require [surfer.utils :as u]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc])
  (:import [java.time LocalDateTime]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)


(def db {:dbtype "h2"
         :dbname "~/surfertest"})

(defn current-db []
  db)

(Class/forName "org.h2.Driver")

(defn create-db! 
  ([] (create-db! (current-db)))
  ([db] 
  ;; Asset metadata
    (jdbc/execute! db 
    "CREATE TABLE IF NOT EXISTS Metadata ( 
       id CHAR(64) NOT NULL PRIMARY KEY, 
       metadata varchar NOT NULL, 
       utime TIMESTAMP NOT NULL
     );"
    )
  
  ;; Listings
    (jdbc/execute! db 
    "CREATE TABLE IF NOT EXISTS Listings ( 
       id CHAR(64) NOT NULL PRIMARY KEY, 
       userid CHAR(64) NOT NULL,
       assetid CHAR(64) NOT NULL, 
       info VARCHAR,
       agreement VARCHAR,
       trust_level INT, 
       trust_visbile CHAR(64), 
       trust_access CHAR(64), 
       ctime TIMESTAMP NOT NULL,
       utime TIMESTAMP
     );"
    )
  
    ;; Users
    (jdbc/execute! db 
    "CREATE TABLE IF NOT EXISTS Users ( 
       id CHAR(64) NOT NULL PRIMARY KEY, 
       username VARCHAR(64) NOT NULL, 
       password varchar NOT NULL,
       metadata varchar NOT NULL, 
       status varchar(10) NOT NULL,
       ctime TIMESTAMP NOT NULL
     );"
    )
  
    (jdbc/execute! db 
      "CREATE UNIQUE INDEX IF NOT EXISTS IX_USERNAME
       ON USERS(username) ;"
  ))
)

(defn drop-db! 
  ([] (drop-db! (current-db)))
  ([db]
    (jdbc/execute! db "drop TABLE IF EXISTS Metadata;")
    (jdbc/execute! db "drop TABLE IF EXISTS Listings;")
    (jdbc/execute! db "drop TABLE IF EXISTS Users;")
    (jdbc/execute! db "drop INDEX IF EXISTS IX_USERNAME;")));

(defn truncate-db! 
  ([] (truncate-db! (current-db)))
  ([db]
  (jdbc/execute! db "truncate TABLE Metadata;")
  (jdbc/execute! db "truncate TABLE Listings;")
  (jdbc/execute! db "truncate TABLE Users;")
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

(defn all-keys 
  "Returns a list of all metadata asset IDs stored."
  ([]
    (let [rs (jdbc/query db ["select id from Metadata;"])]
      (map :id rs))))

;; ===================================================
;; User management

(defn get-user 
  "Gets a user map from the data store.
   Returns nil if not found"
  ([id]
    (let [rs (jdbc/query db ["select * from Users where id = ?" id])]
      (if (empty? rs)
        nil ;; user not found
        (first rs)))))

(defn get-user-by-name
  "Gets a user map from the data store.
   Returns nil if not found"
  ([username]
    (let [rs (jdbc/query db ["select * from Users where username = ?" username])]
      (if (empty? rs)
        nil ;; user not found
        (first rs)))))

(defn get-users 
  "Lists all users of the marketplace.
   Returns a sequence of maps containing the db records for each user"
  ([]
    (let [rs (jdbc/query db ["select * from Users;"])]
      rs)))

(defn list-users 
  "Lists all users of the marketplace.
   Returns a sequence of maps containing :id and :username"
  ([]
    (let [rs (get-users)]
      (map (fn [user] {:id (:id user)
                       :username (:username user)}) rs))))

(defn register-user 
  "Registers a user in the data store. Returns the New User ID as a string.

   Returns the new user ID, or nil if creation failed."
  ([user-data]
    (let [id (u/new-random-id)
          username (:username user-data)
          rs (jdbc/query db ["select * from Users where id = ?" id])
          rs2 (jdbc/query db ["select * from Users where username = ?" username])]
      (when (and (empty? rs) (empty? rs2))
        (jdbc/insert! db "Users" 
                      {:id id
                       :username username
                       :password (:password user-data)
                       :status "Active" 
                       :metadata (json/write-str (or (:metadata user-data) {})) 
                       :ctime (LocalDateTime/now)})
         id))))
