(ns surfer.agent
  (:require [starfish.core :as sf]))

(defn did [config]
  (let [x (:did config)]
    (cond
      (= :auto-generate x)
      (sf/random-did)

      (string? x)
      (sf/did x))))

(defn ddo [config]
  {"@context" "https://www.w3.org/2019/did/v1"
   :id (:did config)
   :service
   [{:type "Ocean.Invoke.v1"
     :serviceEndpoint (str (:remote-url config) "/api/v1/invoke")}

    {:type "Ocean.Meta.v1"
     :serviceEndpoint (str (:remote-url config) "/api/v1/meta")}

    {:type "Ocean.Auth.v1"
     :serviceEndpoint (str (:remote-url config) "/api/v1/auth")}

    {:type "Ocean.Storage.v1"
     :serviceEndpoint (str (:remote-url config) "/api/v1/assets")}]})
