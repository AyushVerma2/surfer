(ns surfer.system
  (:require [com.stuartsierra.component :as component]
            [surfer.component.env :as component.env]
            [surfer.component.h2 :as component.h2]
            [surfer.component.migration :as component.migration]
            [surfer.component.web-server :as component.web-server]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn new-system [& [config]]
  (component/system-map
    :env (component.env/map->Env {:config config})

    :h2 (component/using
          (component.h2/map->H2 {}) [:env])

    ;; *Migration* and *WebServer* use a generic database key
    ;; so it's possible to replace the (relational) database implementation
    ;; without changing other parts of the system.
    :migration (component/using
                 (component.migration/map->Migration {}) {:env :env
                                                          :database :h2})

    :web-server (component/using
                  (component.web-server/map->WebServer {}) {:env :env
                                                            :database :h2})))

(defn init-fn [& [config]]
  (fn [system]
    (new-system config)))

(defn env [system]
  (:env system))

(defn h2 [system]
  (:h2 system))