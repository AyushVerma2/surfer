(ns surfer.system
  (:require [com.stuartsierra.component :as component]
            [surfer.database :as database]
            [surfer.component.env :as component.env]
            [surfer.component.starfish :as component.starfish]
            [surfer.component.migration :as component.migration]
            [surfer.component.web-server :as component.web-server]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn new-system [& [profile config]]
  (component/system-map
    :env (let [m {:config config}
               m (if profile
                   (merge m {:profile profile})
                   m)]
           (component.env/map->Env m))

    :database (component/using
                (database/map->Database {}) [:env])

    :starfish (component/using
                (component.starfish/map->Starfish {}) [:env])

    :migration (component/using
                 (component.migration/map->Migration {}) [:env :database])

    :web-server (component/using
                  (component.web-server/map->WebServer {}) [:env :database :starfish])))

(defn env [system]
  (:env system))

(defn database [system]
  (:database system))

(defn starfish [system]
  (:starfish system))