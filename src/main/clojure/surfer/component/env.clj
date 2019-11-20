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
            [clojure.string :as str]))

(defrecord Env [config-path config user-config-path user-config]
  component/Lifecycle

  (start [component]
    (let [config-path (or (env :config-path) "surfer-config.edn")

          ;; -- surfer-config.edn - create if it doesn't exist
          _ (when-not (.exists (io/file config-path))
              (let [config (edn/read-string (slurp (io/resource "surfer-config-sample.edn")))]
                (spit config-path (with-out-str (pprint/pprint config)))))

          ;; Merge configs - config (disk), overrides
          config (merge (edn/read-string (slurp config-path)) config)
          ;; Resolve storage path; e.g ~/.surfer => /home/user/.surfer
          config (update config :storage (fn [{:keys [path] :as storage-config}]
                                           (if path
                                             (assoc storage-config :path (str/replace path #"^~" (System/getProperty "user.home")))
                                             storage-config)))

          web-server-port (get-in config [:web-server :port])

          ;; Update Agent's remote url to use web server's port
          config (update config :agent (fn [{:keys [remote-url] :as agent-config}]
                                         (assoc agent-config :remote-url (str remote-url ":" web-server-port))))

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
