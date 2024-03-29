(ns surfer.handler
  (:require
    [clojure.walk :refer [stringify-keys]]
    [compojure.route :as route]
    [compojure.api.sweet :refer :all]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [ring.swagger.upload :as upload]
    [ring.middleware.multipart-params :refer [wrap-multipart-params]]
    [ring.middleware.cors :refer [wrap-cors]]
    [ring.middleware.params :refer [wrap-params]]
    [ring.logger :refer [wrap-log-response]]
    [surfer.store :as store]
    [surfer.schema :as schema]
    [surfer.storage :as storage]
    [starfish.core :as sf]
    [surfer.utils :as utils]
    [surfer.env :as env]
    [surfer.invokable :as invokable]
    [surfer.app-context :as app-context]
    [surfer.database :as database]
    [surfer.asset :as asset]
    [surfer.job :as job]
    [schema.core :as s]
    [clojure.data.json :as json]
    [clojure.pprint :as pprint :refer [pprint]]
    [surfer.ckan :as ckan]
    [ring.util.response :as response]
    [ring.util.request :as request]
    [slingshot.slingshot :as slingshot]
    [clojure.java.io :as io]
    [cemerick.friend :as friend]
    [cemerick.friend [workflows :as workflows]
     [credentials :as creds]]
    [clojure.tools.logging :as log]
    [hiccup.core :as hiccup]
    [hiccup.page :as hiccup.page]
    [byte-streams]
    [clojure.string :as str]
    [surfer.migration :as migration]
    [surfer.orchestration :as orchestration]
    [clojure.walk :as walk]
    [clojure.edn :as edn])
  (:import [java.io InputStream StringWriter PrintWriter]
           (clojure.lang ExceptionInfo)
           (java.time LocalDateTime)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; =========================================
;; utility functions

(defn add-middleware [route middleware]
  (let [handler (or (:handler route) (throw (Error. "Expected a :handler in the route")))]
    (compojure.api.routes/map->Route
      (assoc route :handler (middleware handler)))))

(defn json-from-input-stream
  "Gets a JSON structure from an input stream"
  ([^InputStream input-stream]
   (let [_ (.reset input-stream)
         ^String body (slurp input-stream)]
     (json/read-str body :key-fn keyword))))

(defn get-current-userid
  "Gets the current user ID from a request, or nil if not registered / logged in"
  [app-context request]
  (let [db (database/db (app-context/database app-context))
        auth (friend/current-authentication request)
        username (:identity auth)
        userid (:id (store/get-user-by-name db username))]
    userid))

(defn get-current-token
  "Gets the current token from a request (if set)"
  [request]
  (-> request friend/current-authentication :token))

;; ==========================================
;; Status API

(defn status-api [app-context]
  (let [env (app-context/env app-context)]
    (context "/api" []
      :tags ["Status API"]
      (routes {:swagger {:data {:info {:title "Status API"}}}}
              (GET "/ddo" []
                :summary "Gets the ddo for this Agent"
                :return schema/DDO
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body (env/self-ddo env)})

              (GET "/status" []
                :summary "Gets the status for this Agent"
                :return s/Any
                (let [agent (env/agent-config env)]
                  {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body {:name (or (:name agent) "Unnamed Agent")
                          :description (or (:description agent) "No description")
                          :api-versions ["v1"]
                          :custom {:server-type "Surfer"}}}))))))


;; ==========================================
;; Meta API

(defn meta-api-v1 [app-context]
  (let [env (app-context/env app-context)
        db (database/db (app-context/database app-context))]
    (context "/api/v1/meta" []
      :tags ["Meta API v1"]
      (routes
        {:swagger
         {:data {:info {:title "Meta API"
                        :description "Meta API for Data Ecosystem Agents"}
                 :tags [{:name "Meta API", :description "Meta API for Ocean Marketplace"}]}}}

        (GET "/data/:id" [id]
          :summary "Gets metadata for a specified asset"
          :coercion nil
          :return schema/Asset
          (if-let [meta (store/get-metadata-str db id)]
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body meta}
            (response/not-found "Metadata for this Asset ID is not available.")))

        (GET "/data" request
          :summary "Gets a list of assets where metadata is available"
          :return [schema/AssetID]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body (store/all-keys db)})

        (POST "/data" request
          {:coercion nil
           :body [metadata schema/Asset]
           :return schema/AssetID
           :summary "Stores metadata, creating a new Asset ID"}
          (let [^InputStream body-stream (:body request)
                _ (.reset body-stream)
                ^String body (slurp body-stream)
                body-decoded (json/read-str body :key-fn keyword)]
            (if (str/blank? body)
              (response/bad-request "Missing body.")
              (try
                (when (env/enforce-content-hashes? env)
                  (let [;; If no type is specified, it is assumed to be "dataset".
                        dataset? (contains? #{"dataset" nil} (:type body-decoded))
                        missing-content-hash? (not (:contentHash body-decoded))]
                    (when (and dataset? missing-content-hash?)
                      (throw (ex-info "Missing content hash." body-decoded)))))

                (response/response
                  (str "\"" (store/register-asset db body) "\""))

                (catch ExceptionInfo ex
                  (response/bad-request (ex-message ex)))))))

        (PUT "/data/:id" {{:keys [id]} :params :as request}
          {:coercion nil
           :body [metadata schema/Asset]
           :summary "Stores metadata for the given asset ID"}
          (let [^InputStream body-stream (:body request)
                _ (.reset body-stream)
                ^String body (slurp body-stream)
                hash (utils/sha256 body)]
            (if (= id hash)
              (store/register-asset db id body)             ;; OK, write to store
              (response/bad-request (str "Invalid ID for metadata, expected: " hash " got " id)))))

        (GET "/index" _
          :summary "Gets a Metadata map indexed by Asset ID."
          :return schema/MetadataIndex
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body (store/metadata-index db)})))))

(defn invoke-api-v1 [app-context]
  (let [db (app-context/db app-context)

        params (fn [request]
                 (json/read-str (slurp (doto ^InputStream (:body request) (.reset))) :key-fn keyword))

        error-response (fn [{:job/keys [error]}]
                         (case error
                           :job.error/missing-metadata
                           (response/not-found {:error "Metadata not found."})

                           :job.error/invalid-type
                           (response/bad-request {:error "Asset must be an Operation."})

                           :job.error/orchestration-failed
                           {:status 500
                            :body {:error "Failure running Orchestration. Please try again."}}

                           :job.error/operation-failed
                           {:status 500
                            :body {:error "Failure running Operation. Please try again."}}))]
    (context "/api/v1/invoke" []
      :tags ["Invoke API v1"]
      (routes
        {:swagger
         {:data
          {:info
           {:title "Invoke API"
            :description "Invoke API for Remote Operations"}
           :tags [{:name "Invoke API" :description "Invoke API for Remote Operations"}]
           :produces ["application/json"]}}}

        (POST "/sync/:op-id" request
          :coercion nil
          :body [_ schema/InvokeRequest]
          (let [oid (get-in request [:params :op-id])
                params (params request)]

            (log/debug (str "Invoke Sync " oid " " params))

            (try
              {:status 200
               :body (job/run-job app-context oid params)}
              (catch ExceptionInfo e
                (log/error e "Job error.")

                (error-response (ex-data e)))
              (catch Exception e
                (log/error e "Unknown error.")

                {:status 500
                 :body "Failed to invoke Operation. Please try again."}))))

        (POST "/async/:op-id" request
          :coercion nil
          :body [_ schema/InvokeRequest]
          (let [oid (get-in request [:params :op-id])
                params (params request)]

            (log/debug (str "Invoke Async " oid " " params))

            (try
              {:status 200
               :body {:jobid (:job/id (job/run-job-async app-context oid params))}}
              (catch ExceptionInfo e
                (log/error e "Job error.")

                (error-response (ex-data e)))
              (catch Exception e
                (log/error e "Unknown error.")

                {:status 500
                 :body "Failed to invoke Operation. Please try again."}))))

        (GET "/jobs/:jobid" [jobid]
          (if-let [job (job/get-job db jobid)]
            (response/response (edn/read-string (:results job)))
            (response/not-found (str "Job not found: " jobid))))))))

(defn hash-check! [file metadata]
  (let [{:keys [matches?] :as check} (storage/hash-check file (:contentHash metadata))]
    (cond
      (str/blank? (:contentHash metadata))
      (throw (ex-info "Metadata missing content hash." {:metadata metadata}))

      (not matches?)
      (throw (ex-info "Hashes don't match." (merge check {:metadata metadata}))))))

(defn storage-api-v1 [app-context]
  (let [database (app-context/database app-context)
        env (app-context/env app-context)
        db (database/db database)]
    (context "/api/v1/assets" []
      :tags ["Storage API v1"]
      (routes
        {:swagger
         {:data {:info {:title "Storage API"
                        :description "Storage API for Ocean Marketplace"}
                 :tags [{:name "Storage API", :description "Storage API for Ocean Marketplace"}]
                 ;; :consumes ["application/json"]
                 :produces ["application/json"]}}}

        (GET "/:id" [id]
          :summary "Gets data for a specified Asset ID"
          (if-let [meta (store/get-metadata db id)]         ;; NOTE meta is JSON (not EDN)!
            (if-let [body (storage/asset-input-stream (env/storage-path env) id)]

              (let [ctype (get meta "contentType" "application/octet-stream")
                    ext (utils/ext-for-content-type ctype)
                    return-filename (str "asset-" id ext)
                    headers {"Content-Type" ctype
                             "Content-Disposition"
                             (str "attachment; filename=\"" return-filename "\"")}]
                (log/debug "DOWNLOAD" return-filename "AS" ctype)
                (utils/remove-nil-values
                  {:status 200
                   :headers headers
                   :body body}))
              (response/not-found "Asset data not available."))
            (response/not-found "Asset metadata not available.")))

        (PUT "/:id" {{:keys [id]} :params body :body :as request}
          :coercion nil
          :summary "Stores data for a given Asset ID."
          (let [userid (get-current-userid app-context request)
                meta (store/get-metadata db id {:key-fn keyword})]

            (when body
              (.reset ^InputStream body))

            (cond
              (nil? userid)
              (response/status
                (response/response {:error
                                    {:message "User not authenticated."
                                     :data {:path-params id}}})
                401)

              (nil? meta)
              (response/not-found {:error
                                   {:message "Unregistered asset."
                                    :data {:path-params id}}})

              :else
              (if body
                (try
                  (when (env/enforce-content-hashes? env)
                    (hash-check! body meta))

                  (storage/save (env/storage-path env) id body)

                  (response/created (str "/api/v1/assets/" id))

                  (catch ExceptionInfo e
                    (response/bad-request {:error
                                           {:message (ex-message e)
                                            :data (ex-data e)}})))
                (response/bad-request {:error
                                       {:message "Missing body."
                                        :data {:path-params id
                                               :body body}}})))))

        (POST "/:id" request
          :summary "Stores data for a given Asset ID."
          :middleware [wrap-multipart-params]
          :path-params [id :- schema/AssetID]
          :multipart-params [file :- upload/TempFileUpload]
          :return s/Str
          (let [meta (store/get-metadata db id {:key-fn keyword})]
            (cond
              (nil? (get-current-userid app-context request))
              (response/status (response/response {:error
                                                   {:message "User not authenticated."
                                                    :data {:path-params id}}}) 401)

              (nil? meta)
              (response/not-found {:error
                                   {:message "Unregistered asset."
                                    :data {:path-params id}}})

              (not (map? file))
              (response/bad-request {:error
                                     {:message "Invalid multipart params; it should be an object."
                                      :data {:path-params id
                                             :multipart-params file}}})

              :else
              (if-let [tempfile (:tempfile file)]
                (try
                  (when (env/enforce-content-hashes? env)
                    (hash-check! tempfile meta))

                  (storage/save (env/storage-path env) id tempfile)

                  (response/created (str "/api/v1/assets/" id))

                  (catch ExceptionInfo e
                    (response/bad-request {:error
                                           {:message (ex-message e)
                                            :data (ex-data e)}})))
                (response/bad-request {:error
                                       {:message "Missing 'tempfile'."
                                        :data {:path-params id
                                               :multipart-params file}}})))))))))

(defn trust-api-v1 [app-context]
  (context "/api/v1/trust" []
    :tags ["Trust API v1"]
    (routes
      {:swagger
       {:data
        {:info
         {:title "Trust API"
          :description "Trust API for Ocean Marketplace"}
         :tags [{:name "Trust API", :description "Trust API for Ocean Marketplace"}]
         :produces ["application/json"]}}}

      (GET "/groups" []
        :summary "Gets the list of current groups"
        (throw (UnsupportedOperationException. "Not yet implemented!"))))))

(defn market-api-v1 [app-context]
  (let [database (app-context/database app-context)
        db (database/db database)]
    (context "/api/v1/market" []
      :tags ["Market API v1"]
      (routes
        {:swagger
         {:data {:info {:title "Market API"
                        :description "Market API for Ocean Marketplace"}
                 :tags [{:name "Market API", :description "Market API for Ocean Marketplace"}]
                 ;;:consumes ["application/json"]
                 :produces ["application/json"]}}}

        ;; ===========================================
        ;; User management

        (GET "/users" []
          :summary "Gets the list of current users"
          (or
            (seq (store/list-users db))
            {:status 200
             :body "No users available in database - please check your setup"}))

        (GET "/users/:id" [id]
          :summary "Gets data for a specified user"
          :path-params [id :- schema/UserID]
          :return s/Any
          (or
            (when-let [user (store/get-user db id)]
              ;; (println user)
              {:status 200
               :headers {"Content-Type" "application/json"}
               :body {:id (:id user)
                      :username (:username user)}
               })
            (response/not-found (str "Cannot find user with id: " id))))

        (POST "/users" request
          :query-params [username :- schema/Username,
                         password :- String]
          :return schema/UserID
          :summary "Attempts to register a new user"
          (let [crypt-pw (creds/hash-bcrypt password)
                user {:username username
                      :password crypt-pw}]
            (if-let [id (store/register-user db user)]
              {:status 200
               :headers {"Content-Type" "application/json"}
               :body id}
              (response/bad-request (str "User name already exists: " (:username user))))))

        ;; ===========================================
        ;; Asset listings

        (POST "/listings" request
          ;; :body-params [listing :- schemas/Listing]
          :body [listing-body (s/maybe schema/Listing)]
          :return schema/Listing
          :summary "Create a listing on the marketplace.
                       Marketplace will return a new listing record"
          ;; (println (:body request) )
          (let [listing (json-from-input-stream (:body request))
                userid (get-current-userid app-context request)]
            ;; (println listing)
            (if userid
              (if-let [asset (store/get-metadata-str db (:assetid listing))]
                (let [listing (assoc listing :userid userid)
                      ;; _ (println userid)
                      result (store/create-listing db listing)]
                  ;; (println result)
                  {:status 200
                   :headers {"Content-Type" "application/json"}
                   :body result
                   })
                {:status 400
                 :body "Invalid asset id - must register asset first"
                 })
              {:status 401
               :body (str "Must be logged in as a user to create a listing")})))


        (GET "/listings" request
          :query-params [{username :- schema/Username nil}
                         {userid :- schema/UserID nil}
                         {from :- schema/From 0}
                         {size :- schema/Size 100}]
          :summary "Gets all current listings from the marketplace"
          :return [schema/Listing]
          (let [userid (if (not (empty? username))
                         (:id (store/get-user-by-name db username))
                         userid)
                opts (assoc (if userid {:userid userid} {})
                       :from from
                       :size size)
                listings (store/get-listings db opts)]
            (log/debug "GET /listings username" username "userid" userid
                       "from" from "size" size)
            {:status 200
             :headers {"Content-Type" "application/json"
                       "X-Ocean-From" from
                       "X-Ocean-Size" size}
             :body listings}))

        (GET "/listings/:id" [id]
          :summary "Gets data for a specified listing"
          :path-params [id :- schema/ListingID]
          :return schema/Listing
          (if-let [listing (store/get-listing db id)]
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body listing
             }
            (response/not-found (str "No listing found for id: " id))))

        (PUT "/listings/:id" {{:keys [id]} :params :as request}
          :summary "Updates data for a specified listing"
          :path-params [id :- schema/ListingID]
          :body [listing-body (s/maybe schema/Listing)]
          :return schema/Listing
          (let [listing (json-from-input-stream (:body request))

                ;; check the exitsing listing
                old-listing (store/get-listing db id)
                _ (when (not old-listing) (throw (IllegalArgumentException. "Listing ID does not exist: ")))

                ownerid (:userid old-listing)
                userid (get-current-userid app-context request)

                listing (merge old-listing listing)         ;; merge changes. This allows single field edits etc.
                listing (assoc listing :id id)              ;; ensure ID is present.
                ]
            (if (= ownerid userid)                          ;; strong ownership enforcement!
              (let [new-listing (store/update-listing db listing)]
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body new-listing})
              {:status 403
               :body "Can't modify listing: only listing owner can do so"})))

        ;; ==================================================
        ;; Asset purchases

        (POST "/purchases" request
          :body [purchase-body (s/maybe schema/Purchase)]
          :return schema/Purchase
          :summary "Create a new purchase on the marketplace. Marketplace will return a new Purchase record"
          (let [purchase (json-from-input-stream (:body request))
                userid (get-current-userid app-context request)
                listingid (:listingid purchase)
                listing (store/get-listing db listingid)]
            (cond
              (not userid) (response/bad-request (str "Cannot create a purchase unless logged in"))
              (not listing) (response/bad-request (str "Invalid purchase request - listing does not exist: " listingid))
              :else (let [purchase (assoc purchase :userid userid)
                          result (store/create-purchase db purchase)]
                      {:status 200
                       :headers {"Content-Type" "application/json"}
                       :body result}))))


        (GET "/purchases" request
          :query-params [{username :- schema/Username nil}
                         {userid :- schema/UserID nil}]
          :summary "Gets all current purchases from the marketplace by user"
          :return [schema/Purchase]
          ;; TODO access control
          (let [userid (if (not (empty? username))
                         (:id (store/get-user-by-name db username))
                         userid)
                opts (if userid {:userid userid} nil)
                purchases (store/get-purchases db opts)]
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body purchases}))

        (GET "/purchases/:id" [id]
          :summary "Gets data for a specified purchase"
          :return schema/Purchase
          (let [purchase (store/get-purchase db id)]
            (cond
              purchase {:status 200
                        :headers {"Content-Type" "application/json"}
                        :body purchase
                        }
              :else (response/not-found (str "No purchase found for id: " id)))))

        (PUT "/purchases/:id" {{:keys [id]} :params :as request}
          :summary "Updates data for a specified Purchase"
          :body [purchase-body (s/maybe schema/Purchase)]
          :return schema/Purchase
          (let [purchase (json-from-input-stream (:body request))

                ;; check the exitsing purchase
                old-purchase (store/get-purchase db id)
                _ (when (not old-purchase) (throw (IllegalArgumentException. "Purchase ID does not exist: ")))

                ownerid (:userid old-purchase)
                userid (get-current-userid app-context request)

                purchase (merge old-purchase purchase)      ;; merge changes. This allows single field edits etc.
                purchase (assoc purchase :id id)            ;; ensure ID is present.
                ]
            (if (= ownerid userid)                          ;; strong ownership enforcement!
              (let [new-purchase (store/update-purchase db purchase)]
                {:status 200
                 :headers {"Content-Type" "application/json"}
                 :body new-purchase})
              {:status 403
               :body "Can't modify purchase: only purchase owner can do so"})))
        ))))

(defn admin-api-v1 [app-context]
  (let [database (app-context/database app-context)
        env (app-context/env app-context)
        db (database/db database)]
    (context "/api/v1/admin" []
      :tags ["Admin API v1"]
      (routes {:swagger {:data {:info {:title "Admin API"}}}}
              (GET "/auth" request
                :summary "Gets the authentication map for the current user. Useful for debugging."
                (response/response (friend/current-authentication request)))

              (POST "/ckan-import" request
                :query-params [{userid :- schema/UserID nil},
                               repo :- String,
                               {count :- s/Int 10}]
                :summary "Imports assets from a CKAN repository."
                (friend/authorize #{:admin}
                                  (let [userid (or userid (get-current-userid app-context request) (throw (IllegalArgumentException. "No valid userid")))]
                                    (let [all-names (ckan/package-list repo)
                                          names (if count (take count (shuffle all-names)) all-names)]
                                      (binding [ckan/*import-userid* userid]
                                        (ckan/import-packages db repo names))))))

              (POST "/clear-db" []
                :summary "Clear database."
                (friend/authorize #{:admin}
                                  (store/clear-db db (env/dbtype env))
                                  (response/response {:message "Success"})))

              (POST "/migrate-db" []
                :summary "Run database migrations."
                (friend/authorize #{:admin}
                                  (migration/migrate db (env/user-config env))
                                  (response/response {:message "Success"})))

              (POST "/reset-db" []
                :summary "Clear database & run migrations."
                (friend/authorize #{:admin}
                                  (store/clear-db db (env/dbtype env))
                                  (migration/migrate db (env/user-config env))
                                  (response/response {:message "Success"})))

              (POST "/import-sample-datasets" []
                :summary "Import sample datasets - datasets.edn."
                (friend/authorize #{:admin}
                                  (let [storage-path (-> (app-context/env app-context)
                                                         (env/storage-path))]
                                    (response/response (asset/import-edn! db storage-path "datasets.edn")))))

              (GET "/print-config" []
                :summary "Print config."
                (friend/authorize #{:admin} (response/response (env/config env))))

              (POST "/config-agent-remote-url" []
                :summary "Config - Set `[:agent :remote-url]`."
                :coercion nil
                :query-params [remote-url :- s/Str]
                (friend/authorize #{:admin}
                                  (do
                                    (alter-var-root #'env/*agent-remote-url* (constantly remote-url))
                                    (response/response {:remote-url remote-url}))))))))

;; ==========================================
;; Authentication API

(defn response-json [body]
  {:status 200
   :headers {"Content-Type" "application/json"}
   :body body})

(defn get-auth-api-user
  "Based on the request return the a map with :user (if authorized), else
  :response 401 (if unauthenticated) or 403 (if unauthorized)"
  [app-context request]
  (let [database (app-context/database app-context)
        db (database/db database)
        userid (get-current-userid app-context request)
        user (if userid
               (dissoc (store/get-user db userid) :password :ctime))
        roles (or (:roles user) #{})
        rv (cond
             (nil? userid)
             {:response (response/status
                          (response/response "User not authenticated") 401)}

             (not (contains? roles :user))
             {:response (response/status
                          (response/response
                            "User not authorized for Ocean.Authentication.v1")
                          403)}

             :else
             {:user user})]
    (log/debug (str "GET-AUTH-API-USER user: \"" (:username user)
                    "\" for api: " (:uri request))
               "authorized:" (boolean (:user rv)))
    rv))

(defn auth-api-v1 [app-context]
  (let [database (app-context/database app-context)
        db (database/db database)]
    (context "/api/v1/auth" []
      :tags ["Authentication API v1"]
      (routes
        {:swagger
         {:data
          {:info
           {:title "Authentication API"
            :description "Authentication API for Ocean Marketplace"}
           :tags [{:name "Authentication API", :description "Authentication API for Ocean Marketplace"}]
           :produces ["application/json"]}}}

        (GET "/token" request
          :summary "Gets a list of OAuth2 tokens for the currently authenticated user"
          :coercion nil
          :return [schema/OAuth2Token]
          (let [{:keys [response user]} (get-auth-api-user app-context request)]
            (or response
                (response-json (store/all-tokens db (:id user))))))

        (POST "/token" request
          :summary "Creates a new OAuth2Token"
          :coercion nil
          :return schema/OAuth2Token
          (let [{:keys [response user]} (get-auth-api-user app-context request)]
            (or response
                (response-json (str "\"" (store/create-token db (:id user)) "\"")))))

        (DELETE "/revoke/:token" request
          :summary "Revokes one of the existing OAuth2 tokens for the authenticated user"
          :coercion nil
          :path-params [token :- schema/OAuth2Token]
          :return s/Bool
          (let [{:keys [response user]} (get-auth-api-user app-context request)]
            (or response
                (let [result (store/delete-token db (:id user) token)]
                  (if-not result
                    (response/not-found "Token not found.")
                    (response-json (str result)))))))

        ;; Synonym for DELETE for use in the web form (only) as forms only support
        ;; GET,POST methods: https://www.w3.org/TR/1999/REC-html401-19991224/interact/forms.html#adef-method
        (POST "/revoke/:token" request
          :summary "Revokes one of the existing OAuth2 tokens for the authenticated user (via web form) [DEVELOPMENT]"
          :path-params [token :- schema/OAuth2Token]
          :return s/Bool
          :coercion nil
          (let [{:keys [response user]} (get-auth-api-user app-context request)]
            (or response
                (let [result (store/delete-token db (:id user) token)]
                  (if-not result
                    (response/not-found "Token not found.")
                    (response-json (str result)))))))))))

(defn tokens-page
  "Display a simple web form for the authenticated user showing any
  OAuth2 tokens (with the option to revoke each one) as well as
  the abililty to create a new token"
  [app-context request]
  (let [userid (get-current-userid app-context request)
        token (get-current-token request)
        query-params (if token (str "?access_token=" token) "")
        tokens (if userid (store/all-tokens (database/db (app-context/database app-context)) userid) [])
        header (str "<html><head><style type=\"text/css\">"
                    "html {"
                    "  font-family: 'courier new', monospace;"
                    "}"
                    "table {"
                    "  border-style: none;"
                    "  border-spacing: 0px;"
                    "}"
                    "td {"
                    "  border-style: none;"
                    "  text-align: center;"
                    "  padding: 1em 1em 0em 1em;"
                    "}"
                    "td.token {"
                    "  padding: 0em 1em 0em 0em;"
                    "}"
                    "</style></head><body>\n")
        tokens-html (apply
                      str
                      (for [token tokens]
                        (str "<tr><td class=\"token\">" token
                             "</td><td><form action=\"/api/v1/auth/revoke/"
                             token query-params
                             "\" enctype=\"text/plain\" method=\"POST\">"
                             "<input type=\"submit\" value=\"delete\"></form>"
                             "</td></tr>\n")))
        add-button (str "<hr><form action=\"/api/v1/auth/token" query-params
                        "\" enctype=\"text/plain\" method=\"POST\">"
                        "<input type=\"submit\" value=\"add\"></form>")
        body (str header
                  "<table>\n"
                  tokens-html
                  "</table>\n"
                  add-button
                  "\n</body></html>")]
    (response/response body)))

(defn logout-page [request]
  (let [body (str "<html><body>\n"
                  "logged out\n"
                  "</body></html>\n")]
    (response/response body)))

(defn api-routes [app-context]
  (let [database (app-context/database app-context)
        db (database/db database)]
    (api
      {:api {:invalid-routes-fn nil}                        ;; supress warning on child routes
       :exceptions {:handlers
                    {:compojure.api.exception/default
                     (fn [^Throwable ex ex-data request]
                       ;; (.printStackTrace ^Throwable ex)
                       (log/error (str ex))
                       (response/status
                         (response/response
                           (let [sw (StringWriter.)
                                 pw (PrintWriter. sw)]
                             (.printStackTrace ex pw)
                             (str (class ex) "/n"
                                  (.toString sw))))
                         500))}}}
      (swagger-routes {:ui "/api-docs", :spec "/swagger.json"})

      (GET "/assets" []
        (str
          "<body style=\"font-family: 'courier new', monospace;\">"
          (apply str
                 (mapv
                   (fn [id]
                     (try
                       (let [j (json/read-str (store/get-metadata-str db id))
                             title (j "title")]
                         (str "<a href=\"api/v1/meta/data/" id "\">" id " - " title "<br/>\n"))
                       (catch Throwable t
                         (str "Fail in asset id:" id " - " t "<br/>\n"))))
                   (store/all-keys db)))
          "</body>"))

      (GET "/tokens" request
        (fn [request]
          (tokens-page app-context request)))

      (GET "/logout" [] logout-page)

      ;; "/api/v1/meta"
      (meta-api-v1 app-context)

      ;; "/api/v1/assets"
      (storage-api-v1 app-context)

      ;; "/api/v1/market"
      (market-api-v1 app-context)

      ;; "/api/v1/trust"
      (trust-api-v1 app-context)

      ;; "/api/v1/auth"
      (auth-api-v1 app-context)

      ;; "/api/v1/invoke"
      (invoke-api-v1 app-context)

      ;; "/api/v1/admin"
      (admin-api-v1 app-context)

      ;; "/api"
      (status-api app-context)

      (route/not-found "Not found."))))

(def web-routes
  (api
    (GET "/" []
      (let [link (fn [href content]
                   [:a.link.fw6.blue.dim.mv1 {:href href} content])]
        (hiccup.page/html5
          [:head (hiccup.page/include-css "https://unpkg.com/tachyons@4/css/tachyons.min.css")]
          [:body.sans-serif
           [:header.w-100.pa3.ph5-ns.bg-white
            [:span.dib.f5.f4-ns.fw6.black-70 "Surfer"]]
           [:article.pa3.ph5-ns
            [:div.flex.flex-column
             (link "/api-docs" "Swagger UI")
             (link "/assets" "Assets")
             (link "/echo" "Echo Test")
             (link "/tokens" "Manage Tokens")
             (link "/logout" "Logout")]]])))

    (GET "/echo" request (str request))))

;; ===========================================
;; Authentication

(def users (atom {"test" {:username "test"
                          :password (creds/hash-bcrypt "foobar")
                          :roles #{:user}}
                  "Aladdin" {:username "Aladdin"
                             :password (creds/hash-bcrypt "OpenSesame")
                             :roles #{:user :admin}}}))

(defn wrap-cache-buster
  "Prevents any and all HTTP caching by adding a Cache-Control header
  that marks contents as private and non-cacheable."
  [handler]
  (fn wrap-cache-buster-handler [response]
    (response/header (handler response)
                     "cache-control" "private, max-age=0, no-cache")))

(def AUTH_REALM "OceanRM")

(defn credential-fn
  "A Friend credential function.

   Accepts a Friend credential map as sole input.

   Returns an authentication map, including the :identity and :roles set"
  [db]
  (fn [credential]
    (let [{:keys [username password]} credential]
      (or (and (not (empty? username))
               (not (empty? password))
               (creds/bcrypt-credential-fn @users credential))
          (let [user (store/get-user-by-name db username)]
            (when (and user
                       (= "Active" (:status user))
                       (creds/bcrypt-verify password (:password user)))
              {:identity username
               :roles (:roles user)
               :userid (:id user)}))))))

(defn workflow-logout
  "Workflow to log out of basic authentication"
  [request]
  (let [{:keys [uri]} request]
    (if (= uri "/logout")
      (workflows/http-basic-deny AUTH_REALM request))))

(defn workflow-oauth2
  "Workflow to check for an OAuth2 token"
  [db]
  (fn [request]
    (let [{:keys [headers params]} request
          {:strs [authorization]} headers
          {:strs [access_token]} params
          match (and authorization (re-matches #"\s*token\s+(.+)" authorization))
          token (or (if match (second match)) access_token)
          userid (if token (store/get-userid-by-token db token))
          user (if userid (store/get-user db userid))]
      (when (and user (= "Active" (:status user)))
        (workflows/make-auth
          {:identity (:username user)
           :roles (:roles user)
           :userid (:id user)
           :token token}
          {::friend/workflow :oauth2
           ::friend/redirect-on-auth? false
           ::friend/ensure-session false})))))

(def workflow-http-basic
  (workflows/http-basic :realm AUTH_REALM))

(def http-basic-deny
  (partial workflows/http-basic-deny AUTH_REALM))

(defn auth-config [db]
  {:allow-anon? false
   :credential-fn (credential-fn db)
   :workflows [workflow-logout
               (workflow-oauth2 db)
               workflow-http-basic]
   :unauthenticated-handler http-basic-deny
   :unauthorized-handler http-basic-deny})

(defn wrap-auth
  "Middlware for API authentications"
  [config]
  (fn [handler]
    (-> handler
        (friend/wrap-authorize #{:user :admin})
        (friend/authenticate config))))

(defn make-handler [app-context]
  (let [config (auth-config (app-context/db app-context))

        wrap-auth (wrap-auth config)
        wrap-cors (fn [handler]
                    (wrap-cors handler
                               :access-control-allow-origin #".*"
                               :access-control-allow-credentials true
                               :access-control-allow-methods [:get :put :post :delete :options]))

        middleware (comp
                     wrap-cors
                     wrap-log-response
                     wrap-params
                     wrap-cache-buster
                     wrap-auth)

        api-routes (add-middleware (routes (api-routes app-context)) middleware)]
    (routes web-routes api-routes)))
