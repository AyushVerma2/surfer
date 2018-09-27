(ns surfer.handler
  (:require
    [compojure.route :as route]
    [compojure.core :refer [defroutes GET POST ANY]]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [surfer.store :as store]
    [surfer.ckan :as ckan]))

(defroutes routes
  (GET "/" [] "Welcome.")
  
  (GET "/assets" []
       (str 
              "<body style=\"font-family: 'courier new', monospace;\">"
              (apply str 
                     (mapv 
                       (fn [id]
                         (str "<a href=\"/metadata/" id "\">" id " <br/>\n"))
                       (store/all-keys)))
              "</body>"
              ))
  
  (GET "/metadata/:id" [id] 
       (or (store/lookup id)
           (route/not-found "404")))
  
  (route/not-found "404"))

(def app
  (-> routes
      wrap-restful-format
      (wrap-defaults api-defaults)))
