(ns surfer.system
  (:require [com.stuartsierra.component :as component]
            [surfer.handler :as handler]
            [surfer.config :refer [CONFIG]]
            [surfer.component.http-kit :as component.http-kit]
            [surfer.component.config :as component.config]
            [surfer.store :as store]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; TODO: Extract into a config component
(def PORT (Integer/parseInt (str (or (CONFIG :http-port) 3030))))

(defn new-system [& _]
  (component/system-map
    :config (component.config/map->Config {})
    :web (component.http-kit/map->WebServer {:handler handler/app
                                             :options {:port PORT}})))

