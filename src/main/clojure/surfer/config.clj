(ns surfer.config
  (:require [starfish.core :as sf]))

(defn agent-did [config]
  (sf/did
    (let [did (get-in config [:config :agent :did])]
      (cond
        (= :auto-generate did)
        (sf/random-did)

        (string? did)
        did

        :else
        (sf/random-did)))))

(defn local-ddo [config]
  (let [port (get-in config [:config :http-port])
        local-url (str "http://localhost:" port)]
    {:id (str (agent-did config))
     :service
     [{:type "Ocean.Invoke.v1"
       :serviceEndpoint (str local-url "/api/v1/invoke")}

      {:type "Ocean.Meta.v1"
       :serviceEndpoint (str local-url "/api/v1/meta")}

      {:type "Ocean.Auth.v1"
       :serviceEndpoint (str local-url "/api/v1/auth")}

      {:type "Ocean.Storage.v1"
       :serviceEndpoint (str local-url "/api/v1/assets")}]}))

(defn remote-ddo [config]
  (let [remote-url (get-in config [:config :agent :remote-url])]
    {(keyword "@context") "https://www.w3.org/2019/did/v1"
     :id (str (agent-did config))
     :service
     [{:type "Ocean.Invoke.v1"
       :serviceEndpoint (str remote-url "/api/v1/invoke")}

      {:type "Ocean.Meta.v1"
       :serviceEndpoint (str remote-url "/api/v1/meta")}

      {:type "Ocean.Auth.v1"
       :serviceEndpoint (str remote-url "/api/v1/auth")}

      {:type "Ocean.Storage.v1"
       :serviceEndpoint (str remote-url "/api/v1/assets")}]}))
