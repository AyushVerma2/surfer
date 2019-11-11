(ns surfer.component.migration
  "Migration Component namespace

   This namespace should only be required by `surfer.system`."
  (:require [com.stuartsierra.component :as component]
            [clojure.tools.logging :as log]
            [surfer.store :as store]
            [surfer.database :as database]))

(defrecord Migration [env database]
  component/Lifecycle

  (start [component]
    (store/migrate-db! (database/db database))

    (log/info "Successfully migrated database!")

    (when-let [users (:user-config env)]
      (doseq [{:keys [id username] :as user} users]
        (try
          (cond
            (not username)
            (log/info "No :username provided in user-config!")

            (store/get-user-by-name (database/db database) username)
            (log/info (str "User already registered: " username))

            (and id (store/get-user (database/db database) id))
            (log/info (str "User ID already exists: " id))

            :else
            (do (store/register-user (database/db database) user)
                (log/info (str "Auto-registered default user:" username))))
          (catch Throwable t
            (log/error (str "Problem auto-registering default users: " t))))))

    component)

  (stop [component]
    component))
