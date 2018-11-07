(ns surfer.handler
  (:require
    [compojure.api.sweet :refer :all]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [surfer.store :as store]
    [surfer.utils :as u]
    [schema.core :as s]
    [clojure.data.json :as json]
    [surfer.ckan :as ckan]
    [ring.util.response :as response]
    [ring.util.request :as request]))

(def meta-api 
  (api     
    {:swagger
     {:data {:info {:title "Meta API"
                    :description "Meta API for Ocean Marketplace"}
             :tags [{:name "Meta API", :description "Meta API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]
             ;;:produces ["application/json"]
             }}}
    
    (GET "/data/:id" [id] 
        :summary "Gets metadata for a specified asset"
        (if-let [meta (store/lookup id)]
          meta
          (response/not-found "Metadata for this Asset ID is not available.")))
    
    (PUT "/data" request 
        :body [metadata s/Any]
        :summary "Stores metadata, creating a new Asset ID"
        (let [body (request/body-string request)
              hash (u/hex-string (u/keccak256 body))]
          (store/register body)))
    
    (PUT "/data/:id" request 
        {:body [metadata s/Any] 
         :summary "Stores metadata for the given asset ID"}
        (let [id (:id request)
              body (request/body-string request)
              hash (u/hex-string (u/keccak256 body))]
          (if (= id hash)
            (store/register id body) ;; OK, write to store
            (response/bad-request (str "Invalid ID for metadata, expected: " hash)))))
    
    ))

(def storage-api 
  (api     
    {:swagger
     {:data {:info {:title "Storage API"
                    :description "Storage API for Ocean Marketplace"}
             :tags [{:name "Storage API", :description "Storage API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]
             ;;:produces ["application/json"]
             }}}
    
    (GET "/:id" [id] 
        :summary "Gets data for a specified asset ID"
        (let [meta (store/lookup id)]
          (throw (UnsupportedOperationException.))))
    
    (PUT "/:id" request 
        :body [metadata s/Any]
        :summary "Stores asset data for a given asset ID"
        (throw (UnsupportedOperationException.))
    )))

(def market-api 
  (api     
    {:swagger
     {:data {:info {:title "Market API"
                    :description "Market API for Ocean Marketplace"}
             :tags [{:name "Market API", :description "Market API for Ocean Marketplace"}]
             ;;:consumes ["application/json"]
             ;;:produces ["application/json"]
             }}}
    
    (GET "/listings/:id" [id] 
        :summary "Gets data for a specified listing"
        (throw (UnsupportedOperationException.)))
    
    (PUT "/listings" request 
        :body [metadata s/Any]
        :summary "Create a listing on the marketplace. Marketplace will return a new listing ID"
        (throw (UnsupportedOperationException.))
    )))

(def all-routes
  (api 
    (swagger-routes
      {:ui "/api-docs", :spec "/swagger.json"})
    
    (GET "/" [] "<body>
                   <h1>Welcome to surfer!</h1>
                   <p><a href='/assets'>Explore imported asset list</a></p>
                   <p><a href='/api-docs'>API Documentation</a></p>
                 </body>")
  
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

(def app
  (-> all-routes
      ;; wrap-restful-format
      ;;(wrap-defaults api-defaults)
      ))
