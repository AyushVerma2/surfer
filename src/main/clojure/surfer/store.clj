(ns surfer.store
  "Namespace that, manages the data store for the Agent.

   Currenty implemented using JDBC with the H2 embedded database. Other database implementations
   may be future options."
  (:require [surfer.utils :as u]
            [clojure.data.json :as json]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [ragtime.jdbc]
            [ragtime.repl]
            [ragtime.strategy]
            [cemerick.friend.credentials :as creds])
  (:import [java.time Instant]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; ====================================================
;; Database migration

(defn migrate-db! [db]
  (ragtime.repl/migrate {:datastore (ragtime.jdbc/sql-database db)
                         :migrations (ragtime.jdbc/load-resources "migrations")
                         :strategy ragtime.strategy/rebase}))

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
  [db id]
  (let [rs (jdbc/query db ["select * from Listings where id = ?" id])]
    (if (empty? rs)
      nil                                                   ;; user not found
      (clean-listing (first rs)))))

(defn get-listings
  "Gets a full list of listings from the marketplace"
  ([db]
   (get-listings db {}))
  ([db {:keys [userid from size] :as opts}]
   (let [from (long (or from 0))
         size (long (or size 100))
         offset (* from size)]
     (map clean-listing
          (if userid
            (jdbc/query db ["select * from Listings where userid = ? order by ctime desc LIMIT ? OFFSET ?" userid size offset])
            (jdbc/query db ["select * from Listings where status = 'published' order by ctime desc LIMIT ? OFFSET ?" size offset]))))))

(defn create-listing
  "Creates a listing in the data store. Returns the new Listing."
  [db listing]
    ;; (println listing)
    (let [id (or
               (if-let [givenid (:id listing)]
                 (and (u/valid-listing-id? givenid) (not (get-listing db givenid)) givenid))
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
      ))

(defn update-listing
  "Updates a listing in the data store. Returns the new Listing.
   Uses the Listing ID provided in the listing record."
  [db listing]
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
      (get-listing db id) ;; return the updated listing
        ))

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
  [db id]
  (let [rs (jdbc/query db ["select * from Purchases where id = ?" id])]
    (if (empty? rs)
      nil                                                   ;; user not found
      (clean-purchase (first rs)))))

(defn get-purchases
  "Gets a full list of purchases from the marketplace"
  ([db]
   (let [rs (jdbc/query db ["select * from Purchases order by ctime desc"])]
     (map clean-purchase rs)))
  ([db opts]
   (if-let [userid (:userid opts)]
     (let [rs (jdbc/query db ["select * from Purchases where userid = ? order by ctime desc" userid])]
       (map clean-purchase rs))
     (get-purchases db))))

(defn create-purchase
  "Creates a purchase in the data store. Returns the new Purchase."
  [db purchase]
  (let [id (or
             (if-let [givenid (:id purchase)]
               (and (u/valid-purchase-id? givenid) (not (get-purchase db givenid)) givenid))
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
                                          :agreement (:agreement purchase)})]
    (jdbc/insert! db "Purchases" insert-data)
    (clean-purchase insert-data)))

(defn update-purchase
  "Updates a purchase in the data store. Returns the new Purchase.
   Uses the Purchase ID provided in the purchase record."
  [db purchase]
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
                     :utime (Instant/now)                   ;; utime = current time
                     }]
    (jdbc/update! db "Purchases" update-data ["id = ?" id])
    (get-purchase db id)))

;; ===================================================
;; User management

(defn unpack-user-metadata
  "Inflates status and roles slots in a user map from metadata"
  [user]
  (let [metadata-str (or (:metadata user) "{}")
        metadata (json/read-str metadata-str :key-fn keyword)
        {:keys [status roles]} metadata
        status (or status "Active")
        roles (set (map keyword (or roles #{:user})))
        user' (assoc user
                     :metadata (dissoc metadata :status :roles)
                     :status status
                     :roles roles)]
    user'))

(defn get-user
  "Gets a user map from the data store.
   Returns nil if not found"
  [db ^String id]
  (let [rs (jdbc/query db ["select * from Users where id = ?" id])]
    (if (empty? rs)
      nil                                                   ;; user not found
      (unpack-user-metadata (first rs)))))

(defn get-user-by-name
  "Gets a user map from the data store.
   Returns nil if not found"
  [db ^String username]
  (if-not (empty? username)
    (let [rs (jdbc/query db ["select * from Users where username = ?" username])]
      (if (empty? rs)
        nil                                                 ;; user not found
        (unpack-user-metadata (first rs))))))

(defn get-users
  "Lists all users of the marketplace.
   Returns a sequence of maps containing the db records for each user"
  [db]
  (jdbc/query db ["select * from Users;"]))

(defn list-users
  "Lists all users of the marketplace.
   Returns a sequence of maps containing :id and :username"
  [db]
  (map #(select-keys % [:id :username]) (get-users db)))

(defn register-user
  "Registers a user in the data store. Returns the New User ID as a string.

   Returns the new user ID, or nil if creation failed - most likely due to
   user alreday existing in  database"
  [db user-data]
  (let [{:keys [id username password metadata status roles]} user-data
        id (or id (u/new-random-id))
        rs (jdbc/query db ["select * from Users where id = ?" id])
        rs2 (jdbc/query db ["select * from Users where username = ?" username])
        status (or status "Active")
        roles (or roles #{:user})
        metadata (merge (or metadata {})
                        {:status status
                         :roles roles})]
    (when (and (empty? rs) (empty? rs2))
      (log/debug "register-user username:" username "id:" id)
      (jdbc/insert! db "Users"
                    {:id id
                     :username username
                     :password password
                     :status status
                     :metadata (json/write-str metadata)
                     :ctime (Instant/now)})
      id)))

(defn truncate [db & tables]
  (doseq [table (or tables ["Metadata"
                            "Listings"
                            "Purchases"
                            "Users"])]
    (jdbc/execute! db (str "TRUNCATE TABLE " table ";"))))

;; =========================================================
;; Asset management and metadata

(defn register-asset
  "Registers asset metadata in the data store. Returns the Asset ID as a string."
  ([db ^String asset-metadata-str]
    (let [hash (u/sha256 asset-metadata-str)]
      (register-asset db hash asset-metadata-str)))
  ([db ^String hash ^String asset-metadata-str]
    (let [rs (jdbc/query db ["select * from Metadata where id = ?" hash])]
      (if (empty? rs)
        (jdbc/insert! db "Metadata"
                      {:id hash
                       :metadata asset-metadata-str
                       :utime (Instant/now)}))
      hash)))

(defn lookup
  "Returns metadata as a JSON-encoded-string for the given Asset ID, or nil if not available."
  (^String [db ^String id]
   (let [rs (jdbc/query db ["select * from Metadata where id = ?" id])]
     (when (seq rs)
       (str (:metadata (first rs)))))))

(defn lookup-json
  "Gets the JSON data structure for the metadata of a given asset ID.
   Returns nil if the metadata is not available."
  [db ^String id-str & [{:keys [key-fn]}]]
  (when-let [meta (lookup db id-str)]
    (json/read-str meta :key-fn (or key-fn identity))))

(defn all-keys
  "Returns a list of all metadata asset IDs stored."
  [db]
  (let [rs (jdbc/query db ["select id from Metadata;"])]
    (map :id rs)))

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
  [db id]
  (let [rs (jdbc/query db ["select * from Purchases where id = ?" id])]
    (if (empty? rs)
      nil                                                   ;; user not found
      (clean-purchase (first rs)))))

(defn get-purchases
  "Gets a full list of purchases from the marketplace"
  ([db]
    (let [rs (jdbc/query db ["select * from Purchases order by ctime desc"])]
      (map clean-purchase rs)))
  ([db opts]
    (if-let [userid (:userid opts)]
      (let [rs (jdbc/query db ["select * from Purchases where userid = ? order by ctime desc" userid])]
        (map clean-purchase rs))
      (get-purchases db))))

(defn create-purchase
  "Creates a purchase in the data store. Returns the new Purchase."
  [db purchase]
  (let [id (or
             (if-let [givenid (:id purchase)]
               (and (u/valid-purchase-id? givenid) (not (get-purchase db givenid)) givenid))
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
    (clean-purchase insert-data)))

(defn update-purchase
  "Updates a purchase in the data store. Returns the new Purchase.
   Uses the Purchase ID provided in the purchase record."
  [db purchase]
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
      (get-purchase db id)))

;; =========================================================
;; Test data generation

(defn generate-test-data! [db]
  (register-user db {:id "789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4ca6"
                     :username "test"
                     :password (creds/hash-bcrypt "foobar")})
  ;; Authorization: Basic dGVzdDpmb29iYXI=

  (register-user db {:id "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
                     :username "Aladdin"
                     :password (creds/hash-bcrypt "OpenSesame")})
  ;; Authorization: Basic QWxhZGRpbjpPcGVuU2VzYW1l

  (let [assetid (register-asset db (json/write-str {:name "Test Asset"
                                                    :description "A sample asset for testing purposes"}))]
    (create-listing db {:id "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"
                        :userid "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
                        :assetid assetid
                        :info {:title "Ocean Test Asset"
                               :custom "Some custom information"}})

    (let [assetid (register-asset db (json/write-str {:name "Test Asset"
                                                      :description "A sample asset for testing purposes"}))]
      (create-listing db {:id "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"
                          :userid "9671e2c4dabf1b0ea4f4db909b9df3814ca481e3d110072e0e7d776774a68e0d"
                          :assetid assetid
                          :info {:title "Ocean Test Asset"
                                 :custom "Some custom information"}})

      (create-purchase db {:userid "789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4ca6"
                           :listingid "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"

                           :info nil
                           :status "wishlist"}))))

;; ===================================================
;; Authentication API

;; returns nilable schemas/UserID
(defn get-userid-by-token
  "Return userid for this token (else nil)."
  [db token]
  (let [sql "SELECT userid FROM tokens WHERE token = ?;"
        rs (jdbc/query db [sql token])
        userid (-> rs first :userid)]
    (log/debug "get-userid-by-token" token "USERID:" userid)
    userid))

;; returns [schemas/OAuth2Token]
(defn all-tokens
  "Returns a list of all tokens for this user."
  [db userid]
  (let [sql "SELECT tokens.token FROM tokens JOIN users ON tokens.userid = users.id WHERE users.id = ?;"
        rs (jdbc/query db [sql userid])
        tokens (mapv :token rs)]
    (log/debug "all-tokens" userid "TOKENS:" tokens)
    tokens))

;; returns schemas/OAuth2Token
(defn create-token
  "Create an OAuth2Token for this user."
  [db userid]
  (let [token (u/new-random-id)
        sql "INSERT INTO tokens (token, userid) VALUES (?, ?);"
       rs (jdbc/execute! db [sql token userid])] ;; expect '(1)
    (log/debug "create-token" userid "TOKEN:" token)
    token))

;; returns s/Bool
(defn delete-token
  "Deletes an OAuth2Token for this user."
  [db userid token]
  (let [sql "token = ?"
        rs (jdbc/delete! db :tokens [sql token])
        success (= (first rs) 1)]
    (log/debug "delete-token" userid "TOKEN:" token "RS:" rs)
    success))
