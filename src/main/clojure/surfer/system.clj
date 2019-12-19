(ns surfer.system
  (:require [com.stuartsierra.component :as component]
            [surfer.database :as database]
            [surfer.env :as env]
            [surfer.starfish :as starfish]
            [surfer.migration :as migration]
            [surfer.web-server :as web-server]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn new-system [profile & [config]]
  (component/system-map
    :env (env/map->Env {:config config
                        :profile profile})

    :database (component/using
                (database/map->Database {}) [:env])

    :starfish (component/using
                (starfish/map->Starfish {}) [:env])

    :migration (component/using
                 (migration/map->Migration {}) [:env :database])

    :web-server (component/using
                  (web-server/map->WebServer {}) [:env :database :starfish])))

(defn env [system]
  (:env system))

(defn database [system]
  (:database system))

(defn starfish [system]
  (:starfish system))

(def context->db
  (comp database/db :app-context/database))