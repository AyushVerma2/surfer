(ns surfer.component.config
  (:require [com.stuartsierra.component :as component]
            [environ.core :refer [env]]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.pprint :as pprint]
            [cemerick.friend.credentials :as creds]
            [clojure.tools.logging :as log]))

(defrecord Config [config-path config user-config-path user-config]
  component/Lifecycle

  (start [component]
    (let [config-path (or (env :config-path) "surfer-config.edn")

          ;; -- surfer-config.edn - create if it doesn't exist
          _ (when-not (.exists (io/file config-path))
              (let [config (edn/read-string (slurp (io/resource "surfer-config-sample.edn")))]
                (spit config-path (with-out-str (pprint/pprint config)))))

          default-config {:web-server {:port 3030}
                          :h2 {:dbname "~/.surfer/surfer"}}

          ;; Merge configs - defaults, config (disk), overrides
          config (merge default-config (edn/read-string (slurp config-path)) config)

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

      (log/debug (str "-- CONFIG\n" (with-out-str (pprint/pprint config))))
      (log/debug (str "-- USER CONFIG\n" (with-out-str (pprint/pprint user-config))))

      (assoc component :config-path config-path
                       :config config
                       :user-config-path user-config-path
                       :user-config user-config)))

  (stop [component]
    (assoc component :config-path nil
                     :config nil
                     :user-config-path nil
                     :user-config nil)))
