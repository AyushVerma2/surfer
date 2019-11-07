(ns surfer.system
  (:require [com.stuartsierra.component :as component]
            [surfer.handler :as handler]
            [surfer.config :refer [CONFIG]]
            [surfer.component.http-kit :as component.http-kit]
            [surfer.component.config :as component.config]
            [surfer.store :as store]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn new-system [& [config]]
  (component/system-map
    :config (component.config/map->Config {:config config})
    :web (component/using
           (component.http-kit/map->WebServer {:handler handler/app}) [:config])))

(defn init-fn [& [config]]
  (fn [system]
    (new-system config)))

