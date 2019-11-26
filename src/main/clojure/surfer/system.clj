(ns surfer.system
  (:require [com.stuartsierra.component :as component]
            [surfer.component.env :as component.env]
            [surfer.component.h2 :as component.h2]
            [surfer.component.migration :as component.migration]
            [surfer.component.web-server :as component.web-server]
            [starfish.alpha :as sfa])
  (:import (sg.dex.starfish.impl.memory LocalResolverImpl)
           (sg.dex.starfish.impl.remote RemoteAgent RemoteAccount)
           (sg.dex.starfish.util Utils)
           (java.util Map HashMap)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(defn new-system [& [config]]
  (component/system-map
    :env (component.env/map->Env {:config config})

    :h2 (component/using
          (component.h2/map->H2 {}) [:env])

    :starfish #:starfish{:resolvers [(LocalResolverImpl.)]

                         :procurer (fn [_ resolver did]
                                     (let [^Map credentials (doto (new HashMap)
                                                              (.put "username" "Aladdin")
                                                              (.put "password" "OpenSesame"))
                                           account (RemoteAccount/create (Utils/createRandomHexString 32) credentials)]
                                       (RemoteAgent/create resolver did account)))}

    ;; -- DATABASE KEY
    ;; *Migration* and *WebServer* use a generic database key
    ;; so it's possible to replace the (relational) database implementation
    ;; without changing other parts of the system.

    :migration (component/using
                 (component.migration/map->Migration {}) {:env :env
                                                          :database :h2})

    :web-server (component/using
                  (component.web-server/map->WebServer {}) {:env :env
                                                            :database :h2
                                                            :starfish :starfish})))

(defn init-fn [& [config]]
  (fn [system]
    (new-system config)))

(defn env [system]
  (:env system))

(defn h2 [system]
  (:h2 system))

(defn starfish [system]
  (:starfish system))


(defmethod sfa/resolve-agent "1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c" [resolver did ddo]
  (let [^Map credentials (doto (HashMap.)
                           (.put "username" (get-in ddo ["credentials" "username"]))
                           (.put "password" (get-in ddo ["credentials" "password"])))
        account (RemoteAccount/create (Utils/createRandomHexString 32) credentials)]
    (RemoteAgent/create resolver did account)))