(ns surfer.starfish
  (:require [starfish.core :as sf]
            [surfer.env :as env]
            [com.stuartsierra.component :as component]))

(defrecord Starfish [env]
  component/Lifecycle

  (start [component]
    (let [did (env/agent-did env)
          ddo (env/agent-ddo env)
          make (fn [resolver did ddo]
                 (let [username (get-in ddo ["credentials" "username"])
                       password (get-in ddo ["credentials" "password"])
                       account (sf/remote-account username password)]
                   (sf/remote-agent resolver did account)))]
      (sf/install did ddo make))

    component)

  (stop [component]
    component))
