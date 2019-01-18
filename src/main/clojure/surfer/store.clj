(ns surfer.store
  "Namespace that, manages the data store for the Agent.

   Currenty implemented using JDBC with the H2 embedded database. Other database implementations
   may be future options."
  (:require [surfer.utils :as u]
            [surfer.config :refer [CONFIG USER-CONFIG]]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [ragtime.jdbc]
            [ragtime.repl]
            [ragtime.strategy]
            [cemerick.friend
                     [credentials :as creds]])
  (:require [clojure.tools.logging :as log])
  (:import [java.time Instant]
           [java.util Date]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; ====================================================
;; Database setup and management

(def db {:dbtype "h2"
         :dbname "~/surfertest"})

;; ====================================================
;; Database migration

(def ragtime-config
  {:datastore (ragtime.jdbc/sql-database db)
   :migrations (ragtime.jdbc/load-resources "migrations")
   :strategy ragtime.strategy/rebase})

(ragtime.repl/migrate ragtime-config)

(Class/forName "org.h2.Driver")

;; =========================================================
;; Bulk update admin functions

(defn truncate-db! 
  ([] (truncate-db! db))
  ([db]
    (jdbc/execute! db "truncate TABLE Metadata;")
    (jdbc/execute! db "truncate TABLE Listings;")
    (jdbc/execute! db "truncate TABLE Purchases;")
    (jdbc/execute! db "truncate TABLE Users;")
  ))



;; =========================================================
;; Asset management and metadata

(defn register-asset 
  "Registers asset metadata in the data store. Returns the Asset ID as a string."
  ([^String asset-metadata-str]
    (let [hash (u/hex-string (u/keccak256 asset-metadata-str))]
      (register-asset hash asset-metadata-str)))
  ([^String hash ^String asset-metadata-str]
    (let [rs (jdbc/query db ["select * from Metadata where id = ?" hash])]
      (if (empty? rs)
        (jdbc/insert! db "Metadata" 
                      {:id hash 
                       :metadata asset-metadata-str
                       :utime (Instant/now)}))
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
  "Utility function to clean database record returned from an asset listing
   into appropriate data structure format"
  ([listing]
    (let [info (when-let [info-str (:info listing)] 
                 (json/read-str info-str :key-fn keyword))
          listing (if info (assoc listing :info info) listing)
          ;; listing  (dissoc listing :utime :ctime) ;; todo figure out how to coerce these for JSON output
          listing (assoc listing :utime (u/to-instant (:utime listing)))
          listing (assoc listing :ctime (u/to-instant (:ctime listing)))
          ]
      listing)))

(defn get-listing 
  "Gets a listing map from the data store.
   Returns nil if not found"
  ([id]
    (let [rs (jdbc/query db ["select * from Listings where id = ?" id])]
      (if (empty? rs)
        nil ;; user not found
        (clean-listing (first rs))))))

(defn get-listings 
  "Gets a full list of listings from the marketplace"
  ([]
    (let [rs (jdbc/query db ["select * from Listings where status = 'published' order by ctime desc"])]
      (map clean-listing rs)))
  ([opts]
    (if-let [userid (:userid opts)]
      (let [rs (jdbc/query db ["select * from Listings where userid = ? order by ctime desc" userid])]
        (map clean-listing rs))
      (get-listings))))

(defn create-listing 
  "Creates a listing in the data store. Returns the new Listing."
  ([listing]
    ;; (println listing) 
    (let [id (or
               (if-let [givenid (:id listing)]
                 (and (u/valid-listing-id? givenid) (not (get-listing givenid)) givenid))
               (u/new-random-id))
          userid (:userid listing)
          info (:info listing)
          info (when info (json/write-str info))
          instant-now (Instant/now)
          insert-data {:id id
                       :userid userid
                       :assetid (:assetid listing)
                       :trust_level (int (or (:trust_level listing) 0))
                       :ctime instant-now
                       :utime instant-now
                       :status (or (:status listing) "unpublished") 
                       :info info 
                       :agreement (:agreement listing)
                       }]
      (jdbc/insert! db "Listings" insert-data)
      (clean-listing insert-data) ;; return the cleaned listing
      )))

(defn update-listing 
  "Updates a listing in the data store. Returns the new Listing.
   Uses the Listing ID provided in the listing record."
  ([listing]
    ;; (println listing) 
      (let [id (:id listing)
            userid (:userid listing)
            info (:info listing)
            info (when info (json/write-str info))
            update-data {;; :id                     ; id must already be correct!
                           :userid userid
                           :assetid (:assetid listing)
                           :status (:status listing) 
                           :info info 
                           :agreement (:agreement listing)
                           :trust_level (:trust_level listing)
                           ;; :ctime deliberately excluded
                           :utime (Instant/now) ;; utime = current time
                         }]
      (jdbc/update! db "Listings" update-data ["id = ?" id])
      (get-listing id) ;; return the updated listing
        )))

;; ===================================================
;; Purchase management

(defn clean-purchase 
  "Utility function to clean database record returned from an asset purchase
   into appropriate data structure format"
  ([purchase]
    (let [info (when-let [info-str (:info purchase)] 
                 (json/read-str info-str :key-fn keyword))
          purchase (if info (assoc purchase :info info) purchase) 
          purchase (assoc purchase :utime (u/to-instant (:utime purchase)))
          purchase (assoc purchase :ctime (u/to-instant (:ctime purchase)))]
       purchase)))

(defn get-purchase
  "Gets a purchase map from the data store.
   Returns nil if not found"
  ([id]
    (let [rs (jdbc/query db ["select * from Purchases where id = ?" id])]
      (if (empty? rs)
        nil ;; user not found
        (clean-purchase (first rs))))))

(defn get-purchases 
  "Gets a full list of purchases from the marketplace"
  ([]
    (let [rs (jdbc/query db ["select * from Purchases order by ctime desc"])]
      (map clean-purchase rs)))
  ([opts]
    (if-let [userid (:userid opts)]
      (let [rs (jdbc/query db ["select * from Purchases where userid = ? order by ctime desc" userid])]
        (map clean-purchase rs))
      (get-purchases))))

(defn create-purchase 
  "Creates a purchase in the data store. Returns the new Purchase."
  ([purchase]
    ;; (println listing) 
    (let [id (or
               (if-let [givenid (:id purchase)]
                 (and (u/valid-purchase-id? givenid) (not (get-purchase givenid)) givenid))
               (u/new-random-id))
          userid (:userid purchase)
          info (:info purchase)
          info (when info (json/write-str info))
          instant-now (Instant/now)
          insert-data (u/remove-nil-values {:id id
                                            :userid userid
                                            :listingid (:listingid purchase)
                                            :ctime instant-now
                                            :utime instant-now                     
                                            :status (or (:status purchase) "wishlist") 
                                            :info info 
                                            :agreement (:agreement purchase)
                                            })]
      (jdbc/insert! db "Purchases" insert-data)
      (clean-purchase insert-data) ;; return the cleaned purchase
      )))

(defn update-purchase 
  "Updates a purchase in the data store. Returns the new Purchase.
   Uses the Purchase ID provided in the purchase record."
  ([purchase]
    ;; (println listing) 
      (let [id (:id purchase)
            userid (:userid purchase)
            info (:info purchase)
            info (when info (json/write-str info))
            update-data {;; :id                     ; id must already be correct!
                           :userid userid
                           :listingid (:listingid purchase)
                           :status (:status purchase) 
                           :info info 
                           :agreement (:agreement purchase)
                           ;; :ctime deliberately excluded
                           :utime (Instant/now) ;; utime = current time
                         }]
      (jdbc/update! db "Purchases" update-data ["id = ?" id])
      (get-purchase id) ;; return the updated purchase
        )))

;; ===================================================
;; User management

(defn get-user
  "Gets a user map from the data store.
   Returns nil if not found"
  ([^String id]
    (let [rs (jdbc/query db ["select * from Users where id = ?" id])]
      (if (empty? rs)
        nil ;; user not found
        (first rs)))))

(defn get-user-by-name
  "Gets a user map from the data store.
   Returns nil if not found"
  ([^String username]
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

   Returns the new user ID, or nil if creation failed - most likely due to
   user alreday existing in  database"
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
                       :ctime (Instant/now)})
         id))))

;; ============================================================
;; database state update

(try
  (when-let [users USER-CONFIG]
    (log/info "Starting user auto-registration")
    (doseq [{:keys [username id password] :as user} users]
      (cond
        (not username) (log/info "No :username provided in user-config!")  
        (get-user-by-name username) (log/info (str "User already registered: " username))
        (and id (get-user id)) (log/info (str "User ID already exists: " id))
        :else (do (register-user user)
                (log/info (str "Auto-registered default user:" username)))))) 
  (catch Throwable t
    (log/error (str "Problem auto-registering default users: " t))))

;; FIXME move loading config to a deliberate "system" initialization
(try
  (when-let [users USER-CONFIG]
    (log/info "Starting user auto-registration")
    (doseq [{:keys [username id password] :as user} users]
      (cond
        (not username) (log/info "No :username provided in user-config!")
        (get-user-by-name username) (log/info (str "User already registered: " username))
        (and id (get-user id)) (log/info (str "User ID already exists: " id))
        :else (do (register-user user)
                (log/info (str "Auto-registered default user:" username))))))
  (catch Throwable t
    (log/error (str "Problem auto-registering default users: " t))))

(Class/forName "org.h2.Driver")

(defn truncate-db!
  ([] (truncate-db! db))
  ([db]
    (jdbc/execute! db "truncate TABLE Metadata;")
    (jdbc/execute! db "truncate TABLE Listings;")
    (jdbc/execute! db "truncate TABLE Purchases;")
    (jdbc/execute! db "truncate TABLE Users;")
  ))

;; =========================================================
;; Asset management and metadata

(defn register-asset
  "Registers asset metadata in the data store. Returns the Asset ID as a string."
  ([^String asset-metadata-str]
    (let [hash (u/hex-string (u/keccak256 asset-metadata-str))]
      (register-asset hash asset-metadata-str)))
  ([^String hash ^String asset-metadata-str]
    (let [rs (jdbc/query db ["select * from Metadata where id = ?" hash])]
      (if (empty? rs)
        (jdbc/insert! db "Metadata"
                      {:id hash
                       :metadata asset-metadata-str
                       :utime (Instant/now)}))
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
  "Utility function to clean database record returned from an asset listing
   into appropriate data structure format"
  ([listing]
    (let [info (when-let [info-str (:info listing)]
                 (json/read-str info-str :key-fn keyword))
          listing (if info (assoc listing :info info) listing)
          ;; listing  (dissoc listing :utime :ctime) ;; todo figure out how to coerce these for JSON output
          listing (assoc listing :utime (u/to-instant (:utime listing)))
          listing (assoc listing :ctime (u/to-instant (:ctime listing)))
          ]
      listing)))

(defn get-listing
  "Gets a listing map from the data store.
   Returns nil if not found"
  ([id]
    (let [rs (jdbc/query db ["select * from Listings where id = ?" id])]
      (if (empty? rs)
        nil ;; user not found
        (clean-listing (first rs))))))

(defn get-listings
  "Gets a full list of listings from the marketplace"
  ([]
    (let [rs (jdbc/query db ["select * from Listings where status = 'published' order by ctime desc"])]
      (map clean-listing rs)))
  ([opts]
    (if-let [userid (:userid opts)]
      (let [rs (jdbc/query db ["select * from Listings where userid = ? order by ctime desc" userid])]
        (map clean-listing rs))
      (get-listings))))

(defn create-listing
  "Creates a listing in the data store. Returns the new Listing."
  ([listing]
    ;; (println listing)
    (let [id (or
               (if-let [givenid (:id listing)]
                 (and (u/valid-listing-id? givenid) (not (get-listing givenid)) givenid))
               (u/new-random-id))
          userid (:userid listing)
          info (:info listing)
          info (when info (json/write-str info))
          instant-now (Instant/now)
          insert-data {:id id
                       :userid userid
                       :assetid (:assetid listing)
                       :trust_level (int (or (:trust_level listing) 0))
                       :ctime instant-now
                       :utime instant-now
                       :status (or (:status listing) "unpublished")
                       :info info
                       :agreement (:agreement listing)
                       }]
      (jdbc/insert! db "Listings" insert-data)
      (clean-listing insert-data) ;; return the cleaned listing
      )))

(defn update-listing
  "Updates a listing in the data store. Returns the new Listing.
   Uses the Listing ID provided in the listing record."
  ([listing]
    ;; (println listing)
      (let [id (:id listing)
            userid (:userid listing)
            info (:info listing)
            info (when info (json/write-str info))
            update-data {;; :id                     ; id must already be correct!
                           :userid userid
                           :assetid (:assetid listing)
                           :status (:status listing)
                           :info info
                           :agreement (:agreement listing)
                           :trust_level (:trust_level listing)
                           ;; :ctime deliberately excluded
                           :utime (Instant/now) ;; utime = current time
                         }]
      (jdbc/update! db "Listings" update-data ["id = ?" id])
      (get-listing id) ;; return the updated listing
        )))

;; ===================================================
;; Purchase management

(defn clean-purchase
  "Utility function to clean database record returned from an asset purchase
   into appropriate data structure format"
  ([purchase]
    (let [info (when-let [info-str (:info purchase)]
                 (json/read-str info-str :key-fn keyword))
          purchase (if info (assoc purchase :info info) purchase)
          purchase (assoc purchase :utime (u/to-instant (:utime purchase)))
          purchase (assoc purchase :ctime (u/to-instant (:ctime purchase)))]
       purchase)))

(defn get-purchase
  "Gets a purchase map from the data store.
   Returns nil if not found"
  ([id]
    (let [rs (jdbc/query db ["select * from Purchases where id = ?" id])]
      (if (empty? rs)
        nil ;; user not found
        (clean-purchase (first rs))))))

(defn get-purchases
  "Gets a full list of purchases from the marketplace"
  ([]
    (let [rs (jdbc/query db ["select * from Purchases order by ctime desc"])]
      (map clean-purchase rs)))
  ([opts]
    (if-let [userid (:userid opts)]
      (let [rs (jdbc/query db ["select * from Purchases where userid = ? order by ctime desc" userid])]
        (map clean-purchase rs))
      (get-purchases))))

(defn create-purchase
  "Creates a purchase in the data store. Returns the new Purchase."
  ([purchase]
    ;; (println listing)
    (let [id (or
               (if-let [givenid (:id purchase)]
                 (and (u/valid-purchase-id? givenid) (not (get-purchase givenid)) givenid))
               (u/new-random-id))
          userid (:userid purchase)
          info (:info purchase)
          info (when info (json/write-str info))
          instant-now (Instant/now)
          insert-data (u/remove-nil-values {:id id
                                            :userid userid
                                            :listingid (:listingid purchase)
                                            :ctime instant-now
                                            :utime instant-now
                                            :status (or (:status purchase) "wishlist")
                                            :info info
                                            :agreement (:agreement purchase)
                                            })]
      (jdbc/insert! db "Purchases" insert-data)
      (clean-purchase insert-data) ;; return the cleaned purchase
      )))

(defn update-purchase
  "Updates a purchase in the data store. Returns the new Purchase.
   Uses the Purchase ID provided in the purchase record."
  ([purchase]
    ;; (println listing)
      (let [id (:id purchase)
            userid (:userid purchase)
            info (:info purchase)
            info (when info (json/write-str info))
            update-data {;; :id                     ; id must already be correct!
                           :userid userid
                           :listingid (:listingid purchase)
                           :status (:status purchase)
                           :info info
                           :agreement (:agreement purchase)
                           ;; :ctime deliberately excluded
                           :utime (Instant/now) ;; utime = current time
                         }]
      (jdbc/update! db "Purchases" update-data ["id = ?" id])
      (get-purchase id) ;; return the updated purchase
        )))

;; =========================================================
;; Test data generation

(defn generate-test-data!
  ([] (generate-test-data! db))
  ([db]
    (register-user {:id "789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4ca6"
                    :username "test"
                    :password (creds/hash-bcrypt "foobar")} )
    ;; Authorization: Basic dGVzdDpmb29iYXI=

    (register-user {:id "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
                    :username "Aladdin"
                    :password (creds/hash-bcrypt "OpenSesame")})
    ;; Authorization: Basic QWxhZGRpbjpPcGVuU2VzYW1l

    (let [assetid (register-asset (json/write-str {:name "Test Asset"
                                                   :description "A sample asset for testing purposes"}))]
      (create-listing {:id "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"
                       :userid "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
                       :assetid assetid
                       :info {:title "Ocean Test Asset"
                              :custom "Some custom information"}})

      (let [assetid (register-asset (json/write-str {:name "Test Asset"
                                                   :description "A sample asset for testing purposes"}))]
        (create-listing {:id "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"
                         :userid "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
                         :assetid assetid
                         :info {:title "Ocean Test Asset"
                                :custom "Some custom information"}})

        (create-purchase {:userid "789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4ca6"
                      :listingid "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"

                        :info nil
                        :status "wishlist"})
      )
)))
