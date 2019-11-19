(ns surfer.agent
  (:require [starfish.core :as sf]))

(defn parse-did [config-did]
  (cond
    (= :auto-generate config-did)
    (sf/random-did)

    (string? config-did)
    (sf/did config-did)))

(defn ddo [did remote-url]
  {"@context" "https://www.w3.org/2019/did/v1"
   :id (str did)
   :service
   [{:type "Ocean.Invoke.v1"
     :serviceEndpoint (str remote-url "/api/v1/invoke")}

    {:type "Ocean.Meta.v1"
     :serviceEndpoint (str remote-url "/api/v1/meta")}

    {:type "Ocean.Auth.v1"
     :serviceEndpoint (str remote-url "/api/v1/auth")}

    {:type "Ocean.Storage.v1"
     :serviceEndpoint (str remote-url "/api/v1/assets")}]})
