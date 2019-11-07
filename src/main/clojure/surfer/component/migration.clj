(ns surfer.component.migration
  (:require [com.stuartsierra.component :as component]
            [clojure.java.jdbc :as jdbc]
            [clojure.tools.logging :as log]
            [surfer.store :as store]
            [ragtime.jdbc]
            [ragtime.repl]
            [ragtime.strategy]))

(defn migrate [db-spec]
  (ragtime.repl/migrate {:datastore (ragtime.jdbc/sql-database db-spec)
                         :migrations (ragtime.jdbc/load-resources "migrations")
                         :strategy ragtime.strategy/rebase}))

(defn truncate-tables
  [db-spec & tables]
  (doseq [table (or tables
                    ["Metadata"
                     "Listings"
                     "Purchases"
                     "Users"])]
    (jdbc/execute! db-spec (str "TRUNCATE TABLE " table ";"))))

(defrecord Migration [config db]
  component/Lifecycle

  (start [component]
    (migrate (:db-spec db))

    (log/info "Successfully migrated database!")

    (when-let [users (:user-config config)]
      (doseq [{:keys [id username] :as user} users]
        (try
          (cond
            (not username)
            (log/info "No :username provided in user-config!")

            (store/get-user-by-name username)
            (log/info (str "User already registered: " username))

            (and id (store/get-user id))
            (log/info (str "User ID already exists: " id))

            :else
            (do (store/register-user user)
                (log/info (str "Auto-registered default user:" username))))
          (catch Throwable t
            (log/error (str "Problem auto-registering default users: " t))))))

    component)

  (stop [component]
    (when (get-in config [:config :migration :truncate-on-stop?])
      (try
        (truncate-tables (:db-spec db))

        (log/info "Successfully truncated tables!")

        (catch Exception e
          (log/info e "Failed to truncate tables."))))

    component))
