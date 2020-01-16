(ns surfer.migration
  (:require [com.stuartsierra.component :as component]
            [surfer.store :as store]
            [surfer.database :as database]
            [surfer.env :as env]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.tools.logging :as log]
            [ragtime.jdbc]
            [ragtime.repl]
            [ragtime.strategy]))

(defn migrate [db & [users]]
  (ragtime.repl/migrate {:datastore (ragtime.jdbc/sql-database db)
                         :migrations (ragtime.jdbc/load-resources "migrations")
                         :strategy ragtime.strategy/rebase})

  (doseq [user users]
    (try
      (store/register-user db user)
      (catch Exception ex
        (log/error ex "Failed to register user.")))))

(defrecord Migration [env database]
  component/Lifecycle

  (start [component]
    (migrate (database/db database) (env/user-config env))

    component)

  (stop [component]
    component))
