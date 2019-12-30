(ns surfer.test.fixture
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [surfer.utils :as utils]
            [surfer.system :as system]
            [starfish.core :as sf]
            [starfish.alpha :as sfa])
  (:import (sg.dex.starfish.impl.remote RemoteAgent)))

(defn system-fixture [system-var & [config]]
  (fn [f]
    (let [system (component/start
                   (system/new-system :test (or config {:web-server {:port (utils/random-port)}})))]

      (alter-var-root system-var (constantly system))

      (try
        (f)
        (finally
          (component/stop system)

          (alter-var-root system-var (constantly nil)))))))

(defmethod sfa/resolve-agent "abc" [resolver did ddo]
  (let [username (get-in ddo ["credentials" "username"])
        password (get-in ddo ["credentials" "password"])
        account (sf/remote-account username password)]
    (RemoteAgent/create resolver did account)))

(def test-agent-did
  (sf/did "did:dex:abc"))

(defn test-agent []
  (sfa/did->agent test-agent-did))
