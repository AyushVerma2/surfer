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
                         :migrations (#'ragtime.jdbc/load-all-files [(io/resource "migrations/001-users.edn")
                                                                     (io/resource "migrations/002-metadata.edn")
                                                                     (io/resource "migrations/003-listings.edn")
                                                                     (io/resource "migrations/004-purchases.edn")
                                                                     (io/resource "migrations/005-tokens.edn")])
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
