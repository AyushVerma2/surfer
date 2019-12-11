(ns surfer.component.env
  "Env Component namespace

   This namespace should only be required by `surfer.system`."
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [cemerick.friend.credentials :as creds]
            [clojure.tools.logging :as log]
            [clojure.string :as str]
            [aero.core :as aero]))

(defrecord Env [config-path config user-config-path user-config]
  component/Lifecycle

  (start [component]
    (let [config-path (or (env :config-path) "surfer-config.edn")

          config-file (io/file config-path)
          config-sample-file (io/file (io/resource "surfer-config-sample.edn"))

          ;; -- surfer-config.edn - create if it doesn't exist
          _ (when-not (.exists config-file)
              (io/copy config-sample-file config-file))

          ;; Merge configs - config (disk), overrides
          config (merge (aero/read-config config-path) config)

          web-server-port (get-in config [:web-server :port])

          config (-> config
                     (update :agent (fn [{:keys [remote-url] :as agent-config}]
                                      (let [remote-url (or remote-url (str "http://localhost:" web-server-port))]
                                        (assoc agent-config :remote-url remote-url)))))

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

      (log/debug (str "Config\n" (with-out-str (pprint/pprint config))))

      (assoc component :config-path config-path
                       :config config
                       :user-config-path user-config-path
                       :user-config user-config)))

  (stop [component]
    (assoc component :config-path nil
                     :config nil
                     :user-config-path nil
                     :user-config nil)))
