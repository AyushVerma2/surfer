(ns surfer.env
  (:require [starfish.core :as sf]))

(def ^:dynamic *agent-remote-url*
  nil)

(defn- select-config-key [config ks]
  (if (seq ks)
    (get-in config ks)
    config))

(defn user-config [env & [ks]]
  (let [config (get-in env [:user-config])]
    (select-config-key config ks)))

(defn web-server-config [env & [ks]]
  (let [config (get-in env [:config :web-server])]
    (select-config-key config ks)))

(defn agent-config [env & [ks]]
  (let [config (get-in env [:config :agent])]
    (select-config-key config ks)))

(defn database-config [env & [ks]]
  (let [config (get-in env [:config :database])]
    (select-config-key config ks)))

(defn storage-config [env & [ks]]
  (let [config (get-in env [:config :storage])]
    (select-config-key config ks)))

(defn storage-path [env]
  (storage-config env [:path]))

(defn enforce-content-hashes? [env]
  (storage-config env [:enforce-content-hashes?]))

(defn web-server-port [env]
  (web-server-config env [:port]))

(defn dbtype
  "Database type. e.g., postgresql, h2, h2:mem."
  [env]
  (database-config env [:dbtype]))

(defn agent-remote-url [env]
  (or *agent-remote-url* (agent-config env [:remote-url])))

(defn agent-did
  "Surfer's DID."
  [env]
  (let [x (agent-config env [:did])]
    (cond
      (= :auto-generate x)
      (sf/random-did)

      (string? x)
      (sf/did x))))

(defn agent-ddo
  "Surfer's DDO."
  [env]
  (let [endpoint #(str (agent-remote-url env) %)]
    {(keyword "@context") "https://www.w3.org/2019/did/v1"
     :id (agent-config env [:did])
     :credentials
     {:username "Aladdin"
      :password "OpenSesame"}
     :service
     [{:type "Ocean.Invoke.v1"
       :serviceEndpoint (endpoint "/api/v1/invoke")}

      {:type "Ocean.Meta.v1"
       :serviceEndpoint (endpoint "/api/v1/meta")}

      {:type "Ocean.Auth.v1"
       :serviceEndpoint (endpoint "/api/v1/auth")}

      {:type "Ocean.Storage.v1"
       :serviceEndpoint (endpoint "/api/v1/assets")}]}))
