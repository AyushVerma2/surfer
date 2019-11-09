(ns surfer.handler
  (:require
    [clojure.walk :refer [stringify-keys]]
    [compojure.api.sweet :refer :all]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [ring.swagger.upload :as upload]
    [ring.middleware.multipart-params :refer [wrap-multipart-params]]
    [ring.middleware.cors :refer [wrap-cors]]
    [ring.middleware.params :refer [wrap-params]]
    [surfer.store :as store]
    [ocean.schemas :as schemas]
    [surfer.storage :as storage]
    [starfish.core :as sf]
    [surfer.utils :as utils]
    [surfer.config :as config]
    [surfer.invoke :as invoke]
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
    [hiccup.page :as hiccup.page])
  (:import [java.io InputStream StringWriter PrintWriter]
           [org.apache.commons.codec.binary Base64]))

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
  (let [db (get-in app-context [:h2 :db])
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

(defn status-api [{:keys [config] :as app-context}]
  (routes
    {:swagger
     {:data
      {:info
       {:title "Status API"
        :description "Status API for DEP Agents"}
       :tags [{:name "Status API",
               :description "Status API for DEP Agents"}]}}}

    (GET "/ddo" request
      :summary "Gets the ddo for this Agent"
      :return schemas/DDO
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (config/remote-ddo config)})

    (GET "/status" request
      :summary "Gets the status for this Agent"
      :return s/Any
      (let [agent (get-in config [:config :agent])]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body {:name (or (:name agent) "Unnamed Agent")
                :description (or (:description agent) "No description")
                :api-versions ["v1"]
                :custom {:server-type "Surfer"}}}))))


;; ==========================================
;; Meta API

(defn meta-api [app-context]
  (routes
    {:swagger
     {:data {:info {:title "Meta API"
                    :description "Meta API for Data Ecosystem Agents"}
             :tags [{:name "Meta API", :description "Meta API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]

             }}}

    (GET "/data/" request
      :summary "Gets a list of assets where metadata is available"
      :return [schemas/AssetID]
      {:status 200
       :headers {"Content-Type" "application/json"}
       :body (store/all-keys)})

    (GET "/data/:id" [id]
      :summary "Gets metadata for a specified asset"
      :coercion nil
      :return schemas/Asset
      (if-let [meta (store/lookup id)]
        {:status 200
         :headers {"Content-Type" "application/json"}
         :body meta}
        (response/not-found "Metadata for this Asset ID is not available.")))

    (POST "/data" request
      :coercion nil                                         ;; prevents coercion so we get the original input stream
      :body [metadata schemas/Asset]
      :return schemas/AssetID
      :summary "Stores metadata, creating a new Asset ID"
      (let [^InputStream body-stream (:body request)
            _ (.reset body-stream)
            ^String body (slurp body-stream)
            hash (utils/sha256 body)]

        ;; (println (str (class body) ":" body ))
        ;; (println (str (class metadata) ":" metadata ))
        (if (empty? body)
          (response/bad-request "No metadata body!")
          (let [id (store/register-asset body)]
            ;; (println "Created: " id)
            (response/response
              ;; (str "/api/v1/meta/data/" id)
              (str "\"" id "\"")
              )))))

    (PUT "/data/:id" {{:keys [id]} :params :as request}
      {:coercion nil
       :body [metadata schemas/Asset]
       :summary "Stores metadata for the given asset ID"}
      (let [^InputStream body-stream (:body request)
            _ (.reset body-stream)
            ^String body (slurp body-stream)
            hash (utils/sha256 body)]
        (if (= id hash)
          (store/register-asset id body)                    ;; OK, write to store
          (response/bad-request (str "Invalid ID for metadata, expected: " hash " got " id)))))
    ))

(defn invoke-api [app-context]
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
      :body [body schemas/InvokeRequest]
      (let [op-id (get-in request [:params :op-id])
            op-meta (some-> op-id (store/lookup-json {:key-fn keyword}))]
        (cond
          (nil? op-meta)
          (response/not-found (str "Operation (" op-id ") metadata not found."))

          (not= "operation" (:type op-meta))
          (response/bad-request (str "Operation ( " op-id ") metadata type value is not 'operation': " op-meta))

          :else
          (try
            (let [^InputStream body-stream (:body request)
                  _ (.reset body-stream)

                  operation (sf/in-memory-operation op-meta)

                  params (-> (slurp body-stream)
                             (json/read-str :key-fn str))]
              {:status 200
               :body (sf/invoke-result operation params)})
            (catch Exception e
              (log/error e "Failed to invoke operation." op-meta)

              {:status 500
               :body "Failed to invoke operation. Please try again."})))))

    (POST "/async/:op-id"
      {{:keys [op-id]} :params :as request}
      :coercion nil                                         ;; prevents coercion so we get the original input stream
      :body [body schemas/InvokeRequest]
      ;; (println (:body request))
      (if-let [op-meta (store/lookup op-id)]
        (let [md (sf/read-json-string op-meta)
              ^InputStream body-stream (:body request)
              _ (.reset body-stream)
              ^String body-string (slurp body-stream)
              invoke-req (sf/read-json-string body-string)]
          (log/debug (str "POST INVOKE on operation [" op-id "] body=" invoke-req))
          (cond
            (not (= "operation" (:type md))) (response/bad-request (str "Not a valid operation: " op-id))
            :else (if-let [jobid (invoke/launch-job op-id invoke-req)]
                    {:status 201
                     :body (str "{\"jobid\" : \"" jobid "\" , "
                                "\"status\" : \"scheduled\""
                                "}")}
                    (response/not-found "Operation not invokable."))))
        (response/not-found "Operation metadata not available.")))

    (GET "/jobs/:jobid"
         [jobid]
      (log/debug (str "GET JOB on job [" jobid "]"))
      (if-let [job (invoke/get-job jobid)]
        (response/response (invoke/job-response app-context jobid))
        (response/not-found (str "Job not found: " jobid))))))


(defn storage-api [app-context]
  (routes
    {:swagger
     {:data {:info {:title "Storage API"
                    :description "Storage API for Ocean Marketplace"}
             :tags [{:name "Storage API", :description "Storage API for Ocean Marketplace"}]
             ;; :consumes ["application/json"]
             :produces ["application/json"]
             }}}

    (GET "/:id" [id]
      :summary "Gets data for a specified asset ID"
      (if-let [meta (store/lookup-json id)]                 ;; NOTE meta is JSON (not EDN)!
        (if-let [body (storage/load-stream id)]

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

    (PUT "/:id" {{:keys [id]} :params :as request}
      :coercion nil
      ;:body [uploaded nil]
      :summary "Stores asset data for a given asset ID"
      ;; (println (:body request))
      (let [^InputStream body (:body request)
            _ (.reset body)
            userid (get-current-userid app-context request)
            meta (store/lookup-json id)]
        (cond
          (nil? userid) (response/status
                          (response/response "User not authenticated")
                          401)
          ;;(not (map? uploaded)) (response/bad-request
          ;;                        (str "Expected file upload, got body: " uploaded))
          (nil? meta) (response/not-found (str "Attempting to store unregistered asset [" id "]")))
        :else (if-let [file body]                           ;; we have a body
                ;; (println request)
                (do
                  (storage/save id file)
                  (response/created (str "/api/v1/assets/" id)))
                (response/bad-request
                  (str "No uploaded data?: " body)))
        ))

    (POST "/:id" request
      :multipart-params [file :- upload/TempFileUpload]
      :middleware [wrap-multipart-params]
      :path-params [id :- schemas/AssetID]
      :return s/Str
      :summary "upload an asset"
      (let [userid (get-current-userid app-context request)
            meta (store/lookup-json id)]
        (cond
          (nil? userid) (response/status
                          (response/response "User not authenticated")
                          401)
          (nil? meta) (response/not-found (str "Attempting to store unregistered asset [" id "]")))
        (not (map? file)) (response/bad-request
                            (str "Expected file upload, got param: " file))
        :else (if-let [tempfile (:tempfile file)]           ;; we have a body
                (do
                  ;; (binding [*out* *err*] (pprint/pprint request))
                  (storage/save id tempfile)
                  (response/created (str "/api/v1/assets/" id)))
                (response/bad-request
                  (str "Expected map with :tempfile, got param: " file)))
        ))))

(defn trust-api [app-context]
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
      (throw (UnsupportedOperationException. "Not yet implemented!")))))

(defn market-api [app-context]
  (let [db (get-in app-context [:h2 :db])]
    (routes
      {:swagger
       {:data {:info {:title "Market API"
                      :description "Market API for Ocean Marketplace"}
               :tags [{:name "Market API", :description "Market API for Ocean Marketplace"}]
               ;;:consumes ["application/json"]
               :produces ["application/json"]
               }}}

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
        :path-params [id :- schemas/UserID]
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
        :query-params [username :- schemas/Username,
                       password :- String]
        :return schemas/UserID
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
        :body [listing-body (s/maybe schemas/Listing)]
        :return schemas/Listing
        :summary "Create a listing on the marketplace.
                       Marketplace will return a new listing record"
        ;; (println (:body request) )
        (let [listing (json-from-input-stream (:body request))
              userid (get-current-userid app-context request)]
          ;; (println listing)
          (if userid
            (if-let [asset (store/lookup (:assetid listing))]
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
        :query-params [{username :- schemas/Username nil}
                       {userid :- schemas/UserID nil}
                       {from :- schemas/From 0}
                       {size :- schemas/Size 100}]
        :summary "Gets all current listings from the marketplace"
        :return [schemas/Listing]
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
        :path-params [id :- schemas/ListingID]
        :return schemas/Listing
        (if-let [listing (store/get-listing db id)]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body listing
           }
          (response/not-found (str "No listing found for id: " id))))

      (PUT "/listings/:id" {{:keys [id]} :params :as request}
        :summary "Updates data for a specified listing"
        :path-params [id :- schemas/ListingID]
        :body [listing-body (s/maybe schemas/Listing)]
        :return schemas/Listing
        (let [listing (json-from-input-stream (:body request))

              ;; check the exitsing listing
              old-listing (store/get-listing db id)
              _ (when (not old-listing) (throw (IllegalArgumentException. "Listing ID does not exist: ")))

              ownerid (:userid old-listing)
              userid (get-current-userid app-context request)

              listing (merge old-listing listing)           ;; merge changes. This allows single field edits etc.
              listing (assoc listing :id id)                ;; ensure ID is present.
              ]
          (if (= ownerid userid)                            ;; strong ownership enforcement!
            (let [new-listing (store/update-listing db listing)]
              {:status 200
               :headers {"Content-Type" "application/json"}
               :body new-listing})
            {:status 403
             :body "Can't modify listing: only listing owner can do so"})))

      ;; ==================================================
      ;; Asset purchases

      (POST "/purchases" request
        :body [purchase-body (s/maybe schemas/Purchase)]
        :return schemas/Purchase
        :summary "Create a new purchase on the marketplace.
                       Marketplace will return a new Purchase record"
        ;; (println (:body request) )
        (let [purchase (json-from-input-stream (:body request))
              userid (get-current-userid app-context request)
              listingid (:listingid purchase)
              listing (store/get-listing db listingid)]
          (cond
            (not userid) (response/bad-request (str "Cannot create a purchase unless logged in"))
            (not listing) (response/bad-request (str "Invalid purchase request - listing does not exist: " listingid))
            :else (let [purchase (assoc purchase :userid userid)
                        result (store/create-purchase purchase)]
                    ;; (println result)
                    {:status 200
                     :headers {"Content-Type" "application/json"}
                     :body result
                     })
            )))


      (GET "/purchases" request
        :query-params [{username :- schemas/Username nil}
                       {userid :- schemas/UserID nil}]
        :summary "Gets all current purchases from the marketplace by user"
        :return [schemas/Purchase]
        ;; TODO access control
        (let [userid (if (not (empty? username))
                       (:id (store/get-user-by-name db username))
                       userid)
              opts (if userid {:userid userid} nil)
              purchases (store/get-purchases opts)]
          {:status 200
           :headers {"Content-Type" "application/json"}
           :body purchases}))

      (GET "/purchases/:id" [id]
        :summary "Gets data for a specified purchase"
        :return schemas/Purchase
        (let [purchase (store/get-purchase id)]
          (cond
            purchase {:status 200
                      :headers {"Content-Type" "application/json"}
                      :body purchase
                      }
            :else (response/not-found (str "No purchase found for id: " id)))))

      (PUT "/purchases/:id" {{:keys [id]} :params :as request}
        :summary "Updates data for a specified Purchase"
        :body [purchase-body (s/maybe schemas/Purchase)]
        :return schemas/Purchase
        (let [purchase (json-from-input-stream (:body request))

              ;; check the exitsing purchase
              old-purchase (store/get-purchase id)
              _ (when (not old-purchase) (throw (IllegalArgumentException. "Purchase ID does not exist: ")))

              ownerid (:userid old-purchase)
              userid (get-current-userid app-context request)

              purchase (merge old-purchase purchase)        ;; merge changes. This allows single field edits etc.
              purchase (assoc purchase :id id)              ;; ensure ID is present.
              ]
          (if (= ownerid userid)                            ;; strong ownership enforcement!
            (let [new-purchase (store/update-purchase purchase)]
              {:status 200
               :headers {"Content-Type" "application/json"}
               :body new-purchase})
            {:status 403
             :body "Can't modify purchase: only purchase owner can do so"})))
      )))

(defn admin-api [app-context]
  (let [db (get-in app-context [:h2 :db])]
    (routes
      {:swagger
       {:data {:info {:title "Market Admin API"
                      :description "Administration API for Ocean Marketplace"}
               :tags [{:name "Admin API", :description "Administration API for Ocean Marketplace"}]
               ;;:consumes ["application/json"]
               ;;:produces ["application/json"]
               }}}

      ;; ===========================================
      ;; Admin tools

      (GET "/auth" request
        :summary "Gets the authentication map for the current user. Useful for debugging."
        (response/response (friend/current-authentication request)))

      ;; ===========================================
      ;; CKAN Functionality

      (POST "/ckan-import" request
        :query-params [{userid :- schemas/UserID nil},
                       repo :- String,
                       {count :- s/Int 10}]
        :summary "Imports assets from a CKAN repository"
        (friend/authorize #{:admin}
                          (let [userid (or userid (get-current-userid app-context request) (throw (IllegalArgumentException. "No valid userid")))]
                            (let [all-names (ckan/package-list repo)
                                  names (if count (take count (shuffle all-names)) all-names)]
                              (binding [ckan/*import-userid* userid]
                                (ckan/import-packages db repo names))))))

      ;; ===========================================
      ;; Marketplace database management

      (POST "/clear-db" []
        :summary "Clears the current database. DANGER."
        (friend/authorize #{:admin}
                          (store/truncate-db!)
                          (response/response "Successful")))

      (POST "/migrate-db" []
        :summary "Performs database migration. DANGER."
        (friend/authorize #{:admin}
                          (let [r (store/migrate-db!)]
                            (response/response (str "Successful: " r)))))

      (POST "/create-db-test-data" []
        :summary "Creates test data for the current database. DANGER."
        (friend/authorize #{:admin}
                          (store/generate-test-data! db)
                          (response/response "Successful"))))))

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
  (let [db (get-in app-context [:h2 :db])
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

(defn auth-api [app-context]
  (let [db (get-in app-context [:h2 :db])]
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
        :return [schemas/OAuth2Token]
        (let [{:keys [response user]} (get-auth-api-user app-context request)]
          (or response
              (response-json (store/all-tokens db (:id user))))))

      (POST "/token" request
        :summary "Creates a new OAuth2Token"
        :coercion nil
        :return schemas/OAuth2Token
        (let [{:keys [response user]} (get-auth-api-user app-context request)]
          (or response
              (response-json (str "\"" (store/create-token db (:id user)) "\"")))))

      (DELETE "/revoke/:token" request
        :summary "Revokes one of the existing OAuth2 tokens for the authenticated user"
        :coercion nil
        :path-params [token :- schemas/OAuth2Token]
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
        :path-params [token :- schemas/OAuth2Token]
        :return s/Bool
        :coercion nil
        (let [{:keys [response user]} (get-auth-api-user app-context request)]
          (or response
              (let [result (store/delete-token db (:id user) token)]
                (if-not result
                  (response/not-found "Token not found.")
                  (response-json (str result))))))))))

(defn tokens-page
  "Display a simple web form for the authenticated user showing any
  OAuth2 tokens (with the option to revoke each one) as well as
  the abililty to create a new token"
  [app-context request]
  (let [userid (get-current-userid app-context request)
        token (get-current-token request)
        query-params (if token (str "?access_token=" token) "")
        tokens (if userid (store/all-tokens userid) [])
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
  (api
    {:api {:invalid-routes-fn nil}                          ;; supress warning on child routes
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
    (swagger-routes
      {:ui "/api-docs", :spec "/swagger.json"})

    (GET "/assets" []
      (str
        "<body style=\"font-family: 'courier new', monospace;\">"
        (apply str
               (mapv
                 (fn [id]
                   (try
                     (let [j (json/read-str (store/lookup id))
                           title (j "title")]
                       (str "<a href=\"api/v1/meta/data/" id "\">" id " - " title "<br/>\n"))
                     (catch Throwable t
                       (str "Fail in asset id:" id " - " t "<br/>\n"))))
                 (store/all-keys)))
        "</body>"
        ))

    (GET "/tokens" request
      (fn [request]
        (tokens-page app-context request)))

    (GET "/logout" [] logout-page)

    (context "/api/v1/meta" []
      :tags ["Meta API"]
      (meta-api app-context))

    (context "/api/v1/assets" []
      :tags ["Storage API"]
      (storage-api app-context))

    (context "/api/v1/market" []
      :tags ["Market API"]
      (market-api app-context))

    (context "/api/v1/trust" []
      :tags ["Trust API"]
      (trust-api app-context))

    (context "/api/v1/market-admin" []
      :tags ["Market Admin API"]
      (admin-api app-context))

    (context "/api/v1/auth" []
      :tags ["Authentication API"]
      (auth-api app-context))

    (context "/api/v1/invoke" []
      :tags ["Invoke API"]
      (invoke-api app-context))

    (context "/api" []
      :tags ["Status API"]
      (status-api app-context))))

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
             (link "/api-docs" "API Documentation")
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
  "A friend credential function.

   Accepts a friend credential map as sole input.

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

;; =====================================================
;; Main routes

(defn pp-debug [tag m]
  (log/debug tag \newline (with-out-str (clojure.pprint/pprint m)))
  m)

(defn debug-handler [handler step]
  (fn [req]
    (log/debug "DEBUG HANDLER BEFORE" step)
    (pp-debug :req req)
    (try
      (let [rv (handler req)]
        (log/debug "DEBUG HANDLER AFTER" step)
        (pp-debug :req req)
        rv)
      (catch IllegalArgumentException e
        (log/debug "BROWSER CLOSED")
        {}))))

(defn make-handler [app-context]
  (let [db (get-in app-context [:h2 :db])
        config (auth-config db)
        wrap-auth (wrap-auth config)]
    (routes
      web-routes
      (add-middleware
        (routes (api-routes app-context))
        (comp
          (fn [handler]
            (wrap-cors handler
                       :access-control-allow-origin #".*"
                       :access-control-allow-credentials true
                       :access-control-allow-methods [:get :put :post :delete :options]))
          wrap-params
          wrap-cache-buster
          wrap-auth)))))
