(ns surfer.starfish
  (:require [starfish.core :as sf]
            [surfer.env :as env]
            [com.stuartsierra.component :as component])
  (:import (sg.dex.starfish.impl.remote RemoteAgent)))

(defn procurer [resolver did ddo]
  (let [username (get-in ddo ["credentials" "username"])
        password (get-in ddo ["credentials" "password"])
        account (sf/remote-account username password)]
    (RemoteAgent/create resolver did account)))

(defrecord Starfish [env]
  component/Lifecycle

  (start [component]
    (let [did (env/agent-did env)
          ddo (env/agent-ddo env)]
      (sf/register! did ddo procurer))

    component)

  (stop [component]
    component))
