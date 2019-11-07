(ns surfer.system
  (:require [com.stuartsierra.component :as component]
            [surfer.component.config :as component.config]
            [surfer.component.h2 :as component.h2]
            [surfer.component.migration :as component.migration]
            [surfer.component.http-kit :as component.http-kit]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn new-system [& [config]]
  (component/system-map
    :config (component.config/map->Config {:config config})

    :h2 (component/using
          (component.h2/map->H2 {}) [:config])

    :migration (component/using
                 (component.migration/map->Migration {}) [:config :h2])

    :web (component/using
           (component.http-kit/map->WebServer {}) [:config :h2])))

(defn init-fn [& [config]]
  (fn [system]
    (new-system config)))

