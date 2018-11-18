(ns surfer.handler
  (:require
    [compojure.api.sweet :refer :all]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [surfer.store :as store]
    [ocean.schemas :as schemas]
    [surfer.storage :as storage]
    [surfer.utils :as u]
    [schema.core :as s]
    [clojure.data.json :as json]
    [surfer.ckan :as ckan]
    [ring.util.response :as response]
    [ring.util.request :as request]
    [cemerick.friend :as friend]
    [cemerick.friend [workflows :as workflows]
                     [credentials :as creds]])
  (:import [java.io InputStream])) 

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn add-middleware [route middleware]
  (let [handler (or (:handler route) (throw (Error. "Expected a :handler in the route")))]
    (compojure.api.routes/map->Route 
      (assoc route :handler (middleware handler)))))

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
        :summary "A list of assets where metadata is available"
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
              hash (u/hex-string (u/keccak256 body))]
          
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
              hash (u/hex-string (u/keccak256 body))]
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
            (let [ctype (meta "contentType")
                  headers (if ctype {"Content-Type" ctype} {})]
              {:status 200
               :headers headers
               :body body})
            (response/not-found "Asset data not available."))
          (response/not-found "Asset matadata not available.")))
    
    (PUT "/:id" {{:keys [id]} :params :as request} 
        ;; :coercion nil
        :body [metadata s/Any]
        :summary "Stores asset data for a given asset ID"
        ;; (println (:body request))
        (if-let [meta (store/lookup-json id)]
          (do 
            (if-let [^InputStream body (:body request)]
             (do ;; we have a body 
               (.reset body)
               (storage/save id body))
             (do ;; no body, but valid id
               (storage/save id (byte-array 0))
               ))
            (response/created (str "/api/v1/assets/" id)))
          (response/not-found (str "Attempting to store unregistered asset [" id "]")))
    )))

(def market-api 
  (routes     
    {:swagger
     {:data {:info {:title "Market API"
                    :description "Market API for Ocean Marketplace"}
             :tags [{:name "Market API", :description "Market API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]
           ;;:produces ["application/json"]
           }}}
    
    ;; ===========================================
    ;; User management
    
    (GET "/users" [] 
             :summary "Gets the list of current users"
             (store/list-users))
    
    (GET "/users/:id" [id] 
             :summary "Gets data for a specified user"
             (throw (UnsupportedOperationException.)))       
         
    (POST "/users" request 
         :query-params [username :- String, password :- String]
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
    
    (PUT "/listings" request 
             :body [metadata s/Any]
             :summary "Create a listing on the marketplace. Marketplace will return a new listing ID"
             (throw (UnsupportedOperationException.)))
    
    (GET "/listings/:id" [id] 
             :summary "Gets data for a specified listing"
             (throw (UnsupportedOperationException.)))
    
    (PUT "/listings/:id" [id] 
             :summary "Updates data for a specified listing"
             (throw (UnsupportedOperationException.)))
    ))

(def api-routes
  (api 
    {:api {:invalid-routes-fn nil}} ;; supress warning on child routes
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

(def users (atom {"test" {:username "test"
                          :password (creds/hash-bcrypt "foobar")
                          :roles #{:user}}
                  "Aladdin" {:username "Aladdin"
                          :password (creds/hash-bcrypt "OpenSesame")
                          :roles #{:user}}}))

(def all-routes 
   (routes 
     web-routes
     
     (add-middleware
       api-routes
       #(friend/wrap-authorize % #{:user}))
     )
   ) 

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
             :roles #{:user}})))))
   
(def app
  (-> all-routes
     (friend/authenticate {:credential-fn surfer-credential-function
                           :workflows [(workflows/http-basic
                                         ;; :credential-fn surfer-credential-function
                                         :realm "Friend demo")
                                         :unauthorized-handler #(workflows/http-basic-deny "Friend demo" %)
                                         :unauthenticated-handler #(workflows/http-basic-deny "Friend demo" %)
                                       ]
                           
                           })
      ;; wrap-restful-format
      ;;(wrap-defaults api-defaults)
      ))
