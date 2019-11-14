(ns surfer.env
  (:require [starfish.core :as sf]))

(defn- select-config-key [config ks]
  (if (seq ks)
    (get-in config ks)
    config))

(defn web-server-config [env & [ks]]
  (let [config (get-in env [:config :web-server])]
    (select-config-key config ks)))

(defn agent-config [env & [ks]]
  (let [config (get-in env [:config :agent])]
    (select-config-key config ks)))

(defn h2-config [env & [ks]]
  (let [config (get-in env [:config :h2])]
    (select-config-key config ks)))

(defn storage-config [env & [ks]]
  (let [config (get-in env [:config :storage])]
    (select-config-key config ks)))

(defn agent-did [env]
  (sf/did
    (let [did (agent-config env [:did])]
      (cond
        (= :auto-generate did)
        (sf/random-did)

        (string? did)
        did

        :else
        (sf/random-did)))))

(defn local-ddo [env]
  (let [port (web-server-config env [:port])
        local-url (str "http://localhost:" port)]
    {:id (str (agent-did env))
     :service
     [{:type "Ocean.Invoke.v1"
       :serviceEndpoint (str local-url "/api/v1/invoke")}

      {:type "Ocean.Meta.v1"
       :serviceEndpoint (str local-url "/api/v1/meta")}

      {:type "Ocean.Auth.v1"
       :serviceEndpoint (str local-url "/api/v1/auth")}

      {:type "Ocean.Storage.v1"
       :serviceEndpoint (str local-url "/api/v1/assets")}]}))

(defn remote-ddo [env]
  (let [remote-url (agent-config env [:remote-url])]
    {(keyword "@context") "https://www.w3.org/2019/did/v1"
     :id (str (agent-did env))
     :service
     [{:type "Ocean.Invoke.v1"
       :serviceEndpoint (str remote-url "/api/v1/invoke")}

      {:type "Ocean.Meta.v1"
       :serviceEndpoint (str remote-url "/api/v1/meta")}

      {:type "Ocean.Auth.v1"
       :serviceEndpoint (str remote-url "/api/v1/auth")}

      {:type "Ocean.Storage.v1"
       :serviceEndpoint (str remote-url "/api/v1/assets")}]}))
