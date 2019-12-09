(ns surfer.env
  (:require [starfish.core :as sf]))

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

(defn h2-config [env & [ks]]
  (let [config (get-in env [:config :h2])]
    (select-config-key config ks)))

(defn storage-config [env & [ks]]
  (let [config (get-in env [:config :storage])]
    (select-config-key config ks)))

(defn storage-path [env]
  (storage-config env [:path]))

(defn enforce-content-hashes? [env]
  (storage-config env [:enforce-content-hashes?]))

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
  {(keyword "@context") "https://www.w3.org/2019/did/v1"
   :id (agent-config env [:did])
   :credentials
   {:username "Aladdin"
    :password "OpenSesame"}
   :service
   [{:type "Ocean.Invoke.v1"
     :serviceEndpoint "/api/v1/invoke"}

    {:type "Ocean.Meta.v1"
     :serviceEndpoint "/api/v1/meta"}

    {:type "Ocean.Auth.v1"
     :serviceEndpoint "/api/v1/auth"}

    {:type "Ocean.Storage.v1"
     :serviceEndpoint "/api/v1/assets"}]})
