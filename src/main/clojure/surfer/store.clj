(ns surfer.store
  (:require [surfer.utils :as u]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [cemerick.friend 
                     [credentials :as creds]])
  (:import [java.time LocalDateTime]
           [java.util Date]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(declare register-user generate-test-data)

;; ====================================================
;; Database setup and management

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
       trust_visible CHAR(64), 
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
       ON USERS(username) ;")
    
    (generate-test-data db)
  )
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

;; =========================================================
;; Test data generation

(defn generate-test-data [db]
  (register-user {:id "789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4ca6"
                  :username "test" 
                  :password (creds/hash-bcrypt "foobar")} )
  (register-user {:id "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
                  :username "Aladdin" 
                  :password (creds/hash-bcrypt "OpenSesame")} )
  )

;; =========================================================
;; Asset management and metadata

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
;; Listing management

(defn clean-listing 
  "Utility function to clean data from an asset listing"
  ([listing]
    ;; (println listing)
    (dissoc listing :utime :ctime)))

(defn get-listing 
  "Gets a listing map from the data store.
   Returns nil if not found"
  ([id]
    (let [rs (jdbc/query db ["select * from Listings where id = ?" id])]
      (if (empty? rs)
        nil ;; user not found
        (clean-listing (first rs))))))

(defn get-listings 
  "Gets a full list of listings from thge marketplace"
  ([]
    (let [rs (jdbc/query db ["select * from Listings"])]
      (map clean-listing rs))))

(defn create-listing 
  "Creates in the data store. Returns the new Listing."
  ([listing]
    ;; (println listing) 
    (let [id (u/new-random-id)
          userid (:userid listing)
          insert-data {:id id
                       :userid userid
                       :assetid (:assetid listing)
                       :info (:info listing) 
                       :agreement (:agreement listing)
                       :trust_level (int (or (:trust_level listing) 0))
                       :ctime (LocalDateTime/now)
                       :utime (LocalDateTime/now)
                       }]
      (jdbc/insert! db "Listings" insert-data)
      (clean-listing insert-data) ;; return the cleaned listing
      )))

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
    (let [id (or (:id user-data) (u/new-random-id))
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
