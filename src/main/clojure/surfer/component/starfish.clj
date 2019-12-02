(ns surfer.component.starfish
  (:require [starfish.alpha :as sfa]
            [surfer.env :as env]
            [com.stuartsierra.component :as component])
  (:import (sg.dex.starfish.impl.remote RemoteAgent)))

(defrecord Starfish [env]
  component/Lifecycle

  (start [component]
    (let [did (env/agent-did env)
          ddo (env/agent-ddo env)]
      (sfa/register! did ddo))

    component)

  (stop [component]
    component))

(defmethod sfa/resolve-agent "1acd41655b2d8ea3f3513cc847965e72c31bbc9bfc38e7e7ec901852bd3c457c" [resolver did ddo]
  (let [username (get-in ddo ["credentials" "username"])
        password (get-in ddo ["credentials" "password"])
        account (sfa/remote-account username password)]
    (RemoteAgent/create resolver did account)))
