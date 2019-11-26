(ns surfer.component.starfish
  (:require [starfish.alpha :as sfa]
            [surfer.env :as env]
            [com.stuartsierra.component :as component]
            [surfer.agent :as agent])
  (:import (sg.dex.starfish.impl.remote RemoteAgent RemoteAccount)
           (sg.dex.starfish.util Utils)
           (java.util HashMap Map)))

(defrecord Starfish [env]
  component/Lifecycle

  (start [component]
    (let [did (agent/did (env/agent-config env))
          ddo (agent/ddo (env/agent-config env))]
      (sfa/register! did ddo))

    component)

  (stop [component]
    component))

(defmethod sfa/resolve-agent "1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c" [resolver did ddo]
  (let [^Map credentials (doto (HashMap.)
                           (.put "username" (get-in ddo ["credentials" "username"]))
                           (.put "password" (get-in ddo ["credentials" "password"])))
        account (RemoteAccount/create (Utils/createRandomHexString 32) credentials)]
    (RemoteAgent/create resolver did account)))
