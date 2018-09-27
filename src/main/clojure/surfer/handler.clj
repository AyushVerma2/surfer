(ns surfer.handler
  (:require
    [compojure.route :as route]
    [compojure.core :refer [defroutes GET POST ANY]]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
    [surfer.store :as store]
    [clojure.data.json :as json]
    [surfer.ckan :as ckan]))

(defn json-response
  [data & [status]]
  (let [status (or status 200)]
    {:status status
     :headers {"Content-Type" "application/json"}
     :body data}))

(defroutes routes
  (GET "/" [] "Welcome.")
  
  (GET "/assets" []
       (str 
              "<body style=\"font-family: 'courier new', monospace;\">"
              (apply str 
                     (mapv 
                       (fn [id]
                         (let [j (json/read-str (store/lookup id))
                               title (j "title")]
                           (str "<a href=\"/metadata/" id "\">" id " - " title "<br/>\n")))
                       (store/all-keys)))
              "</body>"
              ))
  
  (GET "/metadata/:id" [id] 
       (if-let [js (store/lookup id)]
         (json-response js)
         (route/not-found "404")))
  
  (route/not-found "404"))

(def app
  (-> routes
      wrap-restful-format
      (wrap-defaults api-defaults)))
