(ns surfer.handler
  (:require
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
    [clojure.tools.logging :as log])
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
  ([request]
    (let [auth (friend/current-authentication request)
          username (:identity auth)
          userid (:id (store/get-user-by-name username))]
      userid)))

(defn get-current-token
  "Gets the current token from a request (if set)"
  [request]
  (-> request friend/current-authentication :token))

;; ==========================================
;; Meta API

(def meta-api
  (routes
    {:swagger
     {:data {:info {:title "Meta API"
                    :description "Meta API for Ocean Marketplace"}
             :tags [{:name "Meta API", :description "Meta API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]

             }}}

    (GET "/data/" request
        :summary "Gets a list of assets where metadata is available"
        :return [schemas/AssetID]
        {:status  200
         :headers {"Content-Type" "application/json"}
         :body    (store/all-keys)})

    (GET "/data/:id" [id]
        :summary "Gets metadata for a specified asset"
        :coercion nil
        :return schemas/Asset
        (if-let [meta (store/lookup id)]
          {:status  200
           :headers {"Content-Type" "application/json"}
           :body    meta}
          (response/not-found "Metadata for this Asset ID is not available.")))

    (POST "/data" request
        :coercion nil ;; prevents coercion so we get the original input stream
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
            (store/register-asset id body) ;; OK, write to store
            (response/bad-request (str "Invalid ID for metadata, expected: " hash " got " id)))))
    ))

(def invoke-api
  (routes
    {:swagger
     {:data {:info {:title "Invoke API"
                    :description "Invoke API for Remote Operations"}
             :tags [{:name "Invoke API", :description "Invoke API for Remote Operations"}]
             ;; :consumes ["application/json"]
             :produces ["application/json"]
           }}}
    
    (POST "/invoke/:op-id"
          {{:keys [op-id]} :params :as request}
          :coercion nil ;; prevents coercion so we get the original input stream
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
           (response/response (invoke/job-response jobid))
           (response/not-found (str "Job not found: " jobid)))
         )))


(def storage-api
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
        (if-let [meta (store/lookup-json id)] ;; NOTE meta is JSON (not EDN)!
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
        :body [uploaded nil]
        :summary "Stores asset data for a given asset ID"
        ;; (println (:body request))
  (let [userid (get-current-userid request)
              meta (store/lookup-json id)]
          (cond
            (nil? userid) (response/status
                            (response/response "User not authenticated")
                            401)
            (not (map? uploaded)) (response/bad-request
                                (str "Expected file upload, got body: " uploaded))
            (nil? meta) (response/not-found (str "Attempting to store unregistered asset [" id "]")))
            :else (let [file (:tempfile uploaded)] ;; we have a body
              ;; (println request)
              (storage/save id file)
                    (response/created (str "/api/v1/assets/" id)))
          ))

   (POST "/:id" request
         :multipart-params [file :- upload/TempFileUpload]
         :middleware [wrap-multipart-params]
         :path-params [id :- schemas/AssetID]
         :return s/Str
         :summary "upload an asset"
    (let [userid (get-current-userid request)
          meta (store/lookup-json id)]
      (cond
        (nil? userid) (response/status
                        (response/response "User not authenticated")
                        401)
        (nil? meta) (response/not-found (str "Attempting to store unregistered asset [" id "]")))
        (not (map? file)) (response/bad-request
                                (str "Expected file upload, got param: " file))
        :else (if-let [tempfile (:tempfile file)] ;; we have a body
          (do
                  ;; (binding [*out* *err*] (pprint/pprint request))
            (storage/save id tempfile)
                  (response/created (str "/api/v1/assets/" id)))
                (response/bad-request
                  (str "Expected map with :tempfile, got param: " file)))
      ))))


(def trust-api
  (routes
    {:swagger
     {:data {:info {:title "Trust API"
                    :description "Trust API for Ocean Marketplace"}
             :tags [{:name "Trust API", :description "Trust API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]
             :produces ["application/json"]
           }}}

       (GET "/groups" []
             :summary "Gets the list of current groups"
             (throw (UnsupportedOperationException. "Not yet implemented!")))
 ))

(def market-api
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
               (seq (store/list-users))
               {:status 200
                :body "No users available in database - please check your setup"}))

    (GET "/users/:id" [id]
             :summary "Gets data for a specified user"
             :path-params [id :- schemas/UserID]
             :return s/Any
             (or
               (when-let [user (store/get-user id)]
                 ;; (println user)
                 {:status  200
                   :headers {"Content-Type" "application/json"}
                   :body    {:id (:id user)
                             :username (:username user)}
                   })
               (response/not-found "Cannot find user with id: " id)))

    (POST "/users" request
         :query-params [username :- schemas/Username,
                        password :- String]
         :return schemas/UserID
         :summary "Attempts to register a new user"
         (let [crypt-pw (creds/hash-bcrypt password)
               user {:username username
                     :password crypt-pw}]
           (if-let [id (store/register-user user)]
            {:status  200
             :headers {"Content-Type" "application/json"}
             :body    id}
            (response/bad-request (str "User name already exists: " (:username user))))))

    ;; ===========================================
    ;; Asset listings

    (POST "/listings" request
      ;; :body-params [listing :- schemas/Listing]
      :body [listing-body  (s/maybe schemas/Listing)]
      :return schemas/Listing
      :summary "Create a listing on the marketplace.
                       Marketplace will return a new listing record"
      ;; (println (:body request) )
      (let [listing (json-from-input-stream (:body request))
            userid (get-current-userid request)]
        ;; (println listing)
        (if userid
          (if-let [asset (store/lookup (:assetid listing))]
            (let [listing (assoc listing :userid userid)
                  ;; _ (println userid)
                  result (store/create-listing listing)]
              ;; (println result)
              {:status  200
               :headers {"Content-Type" "application/json"}
               :body    result
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
                     (:id (store/get-user-by-name username))
                     userid)
            opts (assoc (if userid {:userid userid} {})
                        :from from
                        :size size)
            listings (store/get-listings opts)]
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
      (if-let [listing (store/get-listing id)]
        {:status  200
         :headers {"Content-Type" "application/json"}
         :body    listing
         }
        (response/not-found (str "No listing found for id: " id))))

    (PUT "/listings/:id" {{:keys [id]} :params :as request}
      :summary "Updates data for a specified listing"
      :path-params [id :- schemas/ListingID]
      :body [listing-body  (s/maybe schemas/Listing)]
      :return schemas/Listing
      (let [listing (json-from-input-stream (:body request))

            ;; check the exitsing listing
            old-listing (store/get-listing id)
            _ (when (not old-listing) (throw (IllegalArgumentException. "Listing ID does not exist: ")))

            ownerid (:userid old-listing)
            userid (get-current-userid request)

            listing (merge old-listing listing) ;; merge changes. This allows single field edits etc.
            listing (assoc listing :id id) ;; ensure ID is present.
            ]
        (if (= ownerid userid) ;; strong ownership enforcement!
          (let [new-listing (store/update-listing listing)]
            {:status 200
             :headers {"Content-Type" "application/json"}
             :body    new-listing})
          {:status 403
           :body "Can't modify listing: only listing owner can do so"})))

    ;; ==================================================
    ;; Asset purchases

    (POST "/purchases" request
          :body [purchase-body  (s/maybe schemas/Purchase)]
          :return schemas/Purchase
          :summary "Create a new purchase on the marketplace.
                       Marketplace will return a new Purchase record"
             ;; (println (:body request) )
         (let [purchase (json-from-input-stream (:body request))
               userid (get-current-userid request)
               listingid (:listingid purchase)
               listing (store/get-listing listingid)]
           (cond
             (not userid) (response/bad-request (str "Cannot create a purchase unless logged in"))
             (not listing) (response/bad-request (str "Invalid purchase request - listing does not exist: " listingid))
             :else (let [purchase (assoc purchase :userid userid)
                         result (store/create-purchase purchase)]
                     ;; (println result)
                     {:status  200
                      :headers {"Content-Type" "application/json"}
                      :body    result
                      })
             )))


        (GET "/purchases" request
             :query-params [{username :- schemas/Username nil}
                            {userid :- schemas/UserID nil} ]
             :summary "Gets all current purchases from the marketplace by user"
             :return [schemas/Purchase]
             ;; TODO access control
             (let [userid (if (not (empty? username))
                            (:id (store/get-user-by-name username))
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
                 purchase {:status  200
                           :headers {"Content-Type" "application/json"}
                           :body    purchase
                           }
                 :else (response/not-found (str "No purchase found for id: " id)))))

    (PUT "/purchases/:id" {{:keys [id]} :params :as request}
             :summary "Updates data for a specified Purchase"
             :body [purchase-body  (s/maybe schemas/Purchase)]
             :return schemas/Purchase
             (let [purchase (json-from-input-stream (:body request))

                   ;; check the exitsing purchase
                   old-purchase (store/get-purchase id)
                   _ (when (not old-purchase) (throw (IllegalArgumentException. "Purchase ID does not exist: ")))

                   ownerid (:userid old-purchase)
                   userid (get-current-userid request)

                   purchase (merge old-purchase purchase) ;; merge changes. This allows single field edits etc.
                   purchase (assoc purchase :id id) ;; ensure ID is present.
                   ]
               (if (= ownerid userid) ;; strong ownership enforcement!
                 (let [new-purchase (store/update-purchase purchase)]
                   {:status 200
                    :headers {"Content-Type" "application/json"}
                    :body    new-purchase})
                 {:status 403
                  :body "Can't modify purchase: only purchase owner can do so"})))
    ))

(def admin-api
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
               (let [userid (or userid (get-current-userid request) (throw (IllegalArgumentException. "No valid userid")))]
               (let [all-names (ckan/package-list repo)
                     names (if count (take count (shuffle all-names)) all-names)]
                 (binding [ckan/*import-userid* userid]
                   (ckan/import-packages repo names))))
               ))

    ;; ===========================================
    ;; Marketplace database management

    (POST "/clear-db" []
             :summary "Clears the current database. DANGER."
             (friend/authorize #{:admin}
               (store/truncate-db!)
               (response/response "Successful")))

    (POST "/create-db-test-data" []
             :summary "Creates test data for the current database. DANGER."
             (friend/authorize #{:admin}
               (store/generate-test-data!)
               (response/response "Successful")))
    ))

;; ==========================================
;; Authentication API

(defn response-json [body]
  {:status  200
   :headers {"Content-Type" "application/json"}
   :body    body})

(defn get-auth-api-user
  "Based on the request return the a map with :user (if authorized), else
  :response 401 (if unauthenticated) or 403 (if unauthorized)"
  [request]
  (let [userid (get-current-userid request)
        user (if userid
               (dissoc (store/get-user userid) :password :ctime))
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

(def auth-api
  (routes
    {:swagger
     {:data {:info {:title "Authentication API"
                    :description "Authentication API for Ocean Marketplace"}
             :tags [{:name "Authentication API",
                     :description "Authentication API for Ocean Marketplace"}]
             :produces ["application/json"]}}}

    (GET "/token" request
        :summary "Gets a list of OAuth2 tokens for the currently authenticated user"
        :coercion nil
        :return [schemas/OAuth2Token]
        (let [{:keys [response user]} (get-auth-api-user request)]
          (or response
              (response-json (store/all-tokens (:id user))))))

    (POST "/token" request
        :summary "Creates a new OAuth2Token"
        :coercion nil
        :return schemas/OAuth2Token
        (let [{:keys [response user]} (get-auth-api-user request)]
          (or response
              (response-json (str "\"" (store/create-token (:id user)) "\"")))))

    (DELETE "/revoke/:token" request
        :summary "Revokes one of the existing OAuth2 tokens for the authenticated user"
        :coercion nil
        :path-params [token :- schemas/OAuth2Token]
        :return s/Bool
        (let [{:keys [response user]} (get-auth-api-user request)]
          (or response
              (let [result (store/delete-token (:id user) token)]
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
        (let [{:keys [response user]} (get-auth-api-user request)]
          (or response
              (let [result (store/delete-token (:id user) token)]
                (if-not result
                  (response/not-found "Token not found.")
                  (response-json (str result)))))))))

(defn tokens-page
  "Display a simple web form for the authenticated user showing any
  OAuth2 tokens (with the option to revoke each one) as well as
  the abililty to create a new token"
  [request]
  (let [userid (get-current-userid request)
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

(def api-routes
  (api
    {:api {:invalid-routes-fn nil} ;; supress warning on child routes
     :exceptions {:handlers {:compojure.api.exception/default
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
                                500
                                ))
                            }}
     }
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

    (GET "/tokens" request tokens-page)

    (GET "/logout" [] logout-page)

    (context "/api/v1/meta" []
      :tags ["Meta API"]
      meta-api)

    (context "/api/v1/assets" []
      :tags ["Storage API"]
      storage-api)

    (context "/api/v1/market" []
      :tags ["Market API"]
      market-api)
    

     (context "/api/v1/trust" []
      :tags ["Trust API"]
      trust-api)

    (context "/api/v1/market-admin" []
      :tags ["Market Admin API"]
      admin-api)

    (context "/api/v1/auth" []
      :tags ["Authentication API"]
      auth-api)
    
    (context "/api/v1" []
      :tags ["Invoke API"]
      invoke-api)

   ;; (response/not-found "404")
    ))

(def web-routes
  (api
    (GET "/" [] "<body>
                   <h1>Welcome to surfer!!!!</h1>
                   <p><a href='/assets'>Explore imported asset list</a></p>
                   <p><a href='/api-docs'>API Documentation</a></p>
                   <p><a href='/echo'>Echo request body</a></p>
                   <p><a href='/tokens'>Manage Tokens</a></p>
                   <p><a href='/logout'>Logout</a> (click SignIn with no username/password, then Cancel)</p>
                 </body>")

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

(defn surfer-credential-function
  "A friend credential function.

   Accepts a friend credential map as sole input.

   Returns an authentication map, including the :identity and :roles set"
  [creds]
  (let [{:keys [username password]} creds]
    (or (and (not (empty? username))
             (not (empty? password))
             (creds/bcrypt-credential-fn @users creds))
        (let [user (store/get-user-by-name username)]
          (when (and user
                     (= "Active" (:status user))
                     (creds/bcrypt-verify password (:password user)))
            {:identity username
             :roles (:roles user)
             :userid (:id user)})))))

(defn workflow-logout
  "Workflow to log out of basic authentication"
  [request]
  (let [{:keys [uri]} request]
    (if (= uri "/logout")
      (workflows/http-basic-deny AUTH_REALM request))))

(defn workflow-oauth2
  "Workflow to check for an OAuth2 token"
  [request]
  (let [{:keys [headers params]} request
        {:strs [authorization]} headers
        {:strs [access_token]} params
        match (and authorization (re-matches #"\s*token\s+(.+)" authorization))
        token (or (if match (second match)) access_token)
        userid (if token (store/get-userid-by-token token))
        user (if userid (store/get-user userid))]
    (when (and user (= "Active" (:status user)))
      (workflows/make-auth
       {:identity (:username user)
        :roles (:roles user)
        :userid (:id user)
        :token token}
       {::friend/workflow :oauth2
        ::friend/redirect-on-auth? false
        ::friend/ensure-session false}))))

(def workflow-http-basic
  (workflows/http-basic :realm AUTH_REALM))

(def http-basic-deny
  (partial workflows/http-basic-deny AUTH_REALM))

(def auth-config
  {:allow-anon?             false
   :credential-fn           surfer-credential-function
   :workflows               [workflow-logout
                             workflow-oauth2
                             workflow-http-basic]
   :unauthenticated-handler http-basic-deny
   :unauthorized-handler    http-basic-deny})

(defn api-auth-middleware
  "Middlware for API authentications"
  ([handler]
   (-> handler
       (friend/wrap-authorize #{:user :admin})
       (friend/authenticate auth-config))))

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

(def all-routes
  (routes
   web-routes
   (add-middleware
    (routes api-routes)
    (comp
     #(wrap-cors % :access-control-allow-origin #".*"
                 :access-control-allow-credentials true
                 :access-control-allow-methods
                 [:get :put :post :delete :options])
     wrap-params
     wrap-cache-buster
     api-auth-middleware
     ;; #(debug-handler % :api-routes)
     ))))

(def app
  (-> all-routes

      ;; wrap-restful-format
      ;;(wrap-defaults api-defaults)
      ))
