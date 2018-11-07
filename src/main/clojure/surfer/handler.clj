(ns surfer.handler
  (:require
    [compojure.route :as route]
    [compojure.core :refer [defroutes GET PUT POST ANY context]]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [surfer.store :as store]
    [surfer.utils :as u]
    [clojure.data.json :as json]
    [surfer.ckan :as ckan]
    [ring.util.response :as response]
    [ring.util.request :as request]))

(defn json-response
  [data & [status]]
  (let [status (or status 200)]
    {:status status
     :headers {"Content-Type" "application/json"}
     :body data}))

(defroutes routes
  (GET "/" [] "<body><h1>Welcome to surfer!</h1><a href='/assets'>Explore imported asset list</a></body>")
  
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
    (GET "/data/:id" [id] 
        (if-let [meta (store/lookup id)]
          meta
          (route/not-found "Metata for this Asset ID is not available.")))
    
    (PUT "/data/:id" request 
        (let [id (:id request)
              body (request/body-string request)
              hash (u/hex-string (u/keccak256 body))]
          (when-not 
            (= id hash) 
            (response/bad-request ("Invalid ID for metadata, expected: " hash)))
          (store/register id body))))
  
  (route/not-found "404"))

(def app
  (-> routes
      wrap-restful-format
      (wrap-defaults api-defaults)))
