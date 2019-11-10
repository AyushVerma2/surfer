(ns surfer.component.migration
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [surfer.store :as store]
            [ragtime.jdbc]
            [ragtime.repl]
            [ragtime.strategy]))

(defn migrate [db]
  (ragtime.repl/migrate {:datastore (ragtime.jdbc/sql-database db)
                         :migrations (ragtime.jdbc/load-resources "migrations")
                         :strategy ragtime.strategy/rebase}))

(defn truncate [db & tables]
  (doseq [table (or tables ["Metadata"
                            "Listings"
                            "Purchases"
                            "Users"])]
    (jdbc/execute! db (str "TRUNCATE TABLE " table ";"))))

(defrecord Migration [env database]
  component/Lifecycle

  (start [component]
    (migrate (:db-spec database))

    (log/info "Successfully migrated database!")

    (when-let [users (:user-config env)]
      (doseq [{:keys [id username] :as user} users]
        (try
          (cond
            (not username)
            (log/info "No :username provided in user-config!")

            (store/get-user-by-name (:db-spec database) username)
            (log/info (str "User already registered: " username))

            (and id (store/get-user (:db-spec database) id))
            (log/info (str "User ID already exists: " id))

            :else
            (do (store/register-user (:db-spec database) user)
                (log/info (str "Auto-registered default user:" username))))
          (catch Throwable t
            (log/error (str "Problem auto-registering default users: " t))))))

    component)

  (stop [component]
    component))
