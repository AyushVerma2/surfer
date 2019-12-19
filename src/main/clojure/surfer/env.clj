(ns surfer.env
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [cemerick.friend.credentials :as creds]
            [clojure.tools.logging :as log]
            [aero.core :as aero]
            [starfish.core :as sf]))

(defrecord Env [config-path config user-config-path user-config profile]
  component/Lifecycle

  (start [component]
    (let [config-path (or (env :config-path) "surfer-config.edn")

          config-file (io/file config-path)

          ;; -- surfer-config.edn - create if it doesn't exist
          _ (when-not (.exists config-file)
              (with-open [sample (io/input-stream (io/resource "surfer-config-sample.edn"))]
                (io/copy sample config-file)))

          ;; Merge configs - config (disk), overrides
          config (merge (aero/read-config config-path {:profile profile}) config)

          web-server-port (get-in config [:web-server :port])

          config (update config :agent (fn [{:keys [remote-url] :as agent-config}]
                                         (let [remote-url (or remote-url (str "http://localhost:" web-server-port))]
                                           (assoc agent-config :remote-url remote-url))))

          user-config-path (get-in config [:security :user-config])

          ;; -- surfer-users.edn - create if it doesn't exist *only if* there's a path set
          _ (when user-config-path
              (when-not (.exists (io/file user-config-path))
                (let [config (edn/read-string (slurp (io/resource "surfer-users-sample.edn")))]
                  (spit user-config-path (with-out-str (pprint/pprint config))))))

          user-config (when user-config-path
                        (->> (edn/read-string (slurp user-config-path))
                             ;; We need to hash the passwords. Do this here to avoid potential leaks
                             ;; by storing plaintext passwords in memory.
                             (mapv (fn [{:keys [password] :as user}]
                                     (assoc user :password (creds/hash-bcrypt password))))))]

      (log/debug (str config-path "\n" (with-out-str (pprint/pprint config))))

      (assoc component :config-path config-path
                       :config config
                       :user-config-path user-config-path
                       :user-config user-config)))

  (stop [component]
    (assoc component :config-path nil
                     :config nil
                     :user-config-path nil
                     :user-config nil)))

(def ^:dynamic *agent-remote-url*
  nil)

(defn- select-config-key [config ks]
  (if (seq ks)
    (get-in config ks)
    config))

(defn config [env]
  (:config env))

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
  ;; FIXME: Database config can be a string.
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
