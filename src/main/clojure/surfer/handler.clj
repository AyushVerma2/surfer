(ns surfer.handler
  (:require
    [compojure.route :as route]
    [compojure.core :refer [defroutes GET POST ANY]]
    [ring.middleware.format :refer [wrap-restful-format]]
    [ring.middleware.defaults :refer [wrap-defaults api-defaults]]))

(defroutes routes
  (GET "/" [] "Welcome. fevfef rf rbrggrgtg grbrbrgb f dvfd")
  (route/not-found "404"))

(def app
  (-> routes
      wrap-restful-format
      (wrap-defaults api-defaults)))
