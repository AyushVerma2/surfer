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
