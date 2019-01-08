(ns surfer.handler
  (:require
    [compojure.api.sweet :refer :all]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [ring.swagger.upload :as upload]
    [ring.middleware.multipart-params :refer [wrap-multipart-params]]
    [ring.middleware.cors :refer [wrap-cors]]
    [surfer.store :as store]
    [ocean.schemas :as schemas]
    [surfer.storage :as storage]
    [surfer.utils :as utils]
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
                     [credentials :as creds]])
  (:import [java.io InputStream])) 

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

;; ==========================================
;; Meta API

(def meta-api 
  (routes     
    {:swagger
     {:data {:info {:title "Meta API"
                    :description "Meta API for Ocean Marketplace"}
             :tags [{:name "Meta API", :description "Meta API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]
             :produces ["application/json"]
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
              hash (utils/hex-string (utils/keccak256 body))]
          
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
              hash (utils/hex-string (utils/keccak256 body))]
          (if (= id hash)
            (store/register-asset id body) ;; OK, write to store
            (response/bad-request (str "Invalid ID for metadata, expected: " hash " got " id)))))
    ))

(def storage-api 
  (routes     
    {:swagger
     {:data {:info {:title "Storage API"
                    :description "Storage API for Ocean Marketplace"}
             :tags [{:name "Storage API", :description "Storage API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]
           ;;:produces ["application/json"]
           }}}
    
    (GET "/:id" [id] 
        :summary "Gets data for a specified asset ID"
        (if-let [meta (store/lookup-json id)]
          (if-let [body (storage/load-stream id)]
            (let [ctype (or (:contentType meta) "application/octet-stream")
                  return-filename (str "asset-" id ".csv") ;; TODO: replace .csv hack
                  headers {"Content-Type" ctype
                           "Content-Disposition" (str "attachment; filename=\"" return-filename "\"")}]
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
             (store/list-users))
    
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
                            {userid :- schemas/UserID nil} ]
             :summary "Gets all current listings from the marketplace"
             :return [schemas/Listing] 
             (let [userid (if (not (empty? username)) 
                            (:id (store/get-user-by-name username))
                            userid)
                   opts (if userid {:userid userid} nil)
                   listings (store/get-listings opts)]
               {:status 200
                :headers {"Content-Type" "application/json"}
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

(def api-routes
  (api 
    {:api {:invalid-routes-fn nil} ;; supress warning on child routes
     :exceptions {:handlers {:compojure.api.exception/default 
                            (fn [ex ex-data request]
                              (.printStackTrace ^Throwable ex)
                              (slingshot/throw+ ex))
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
                           (let [j (json/read-str (store/lookup id))
                                 title (j "title")]
                             (str "<a href=\"api/v1/meta/data/" id "\">" id " - " title "<br/>\n")))
                         (store/all-keys)))
                "</body>"
                ))
  
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
  
   ;; (response/not-found "404")
    ))

(def web-routes
  (api 
    (GET "/" [] "<body>
                   <h1>Welcome to surfer!!!!</h1>
                   <p><a href='/assets'>Explore imported asset list</a></p>
                   <p><a href='/api-docs'>API Documentation</a></p>
                   <p><a href='/echo'>Echo request body</a></p>
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


(def AUTH_REALM "OceanRM")

(defn surfer-credential-function 
  "A friend credential function.

   Accepts a friend credential map as sole input.

   Returns an authentication map, including the :identity and :roles set"
  ([creds]
    (or (creds/bcrypt-credential-fn @users creds)
        (if-let [username (:username creds)]
          (let [password (:password creds)
                user (store/get-user-by-name username)]
            (when (and
                    user
                    (= password (:password user))
                    (= "Active" (:status user))))
            {:identity username
             :roles #{:user}
             :userid (:id user)})))))

(defn api-auth-middleware 
  "Middlware for API authentications"
  ([handler]
  (-> handler
    (friend/wrap-authorize #{:user :admin})
    (friend/authenticate {:credential-fn surfer-credential-function
                          :workflows [(workflows/http-basic
                                         ;; :credential-fn surfer-credential-function
                                        :realm AUTH_REALM)
                                        :unauthorized-handler #(workflows/http-basic-deny "Friend demo" %)
                                        :unauthenticated-handler #(workflows/http-basic-deny "Friend demo" %)
                                       ]}))))

;; =====================================================
;; Main routes

(def all-routes 
   (routes 
     web-routes
     
     (add-middleware
       api-routes
       (comp 
         #(wrap-cors % :access-control-allow-origin #".*"
                       :access-control-allow-credentials true
                       :access-control-allow-methods [:get :put :post :delete :options])
         api-auth-middleware 
         )
     )
   )) 

(def app
  (-> all-routes
     
      ;; wrap-restful-format
      ;;(wrap-defaults api-defaults)
      ))
