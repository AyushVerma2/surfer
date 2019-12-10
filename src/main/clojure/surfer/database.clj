(ns surfer.database
  (:require [surfer.env :as env]
            [com.stuartsierra.component :as component]))

(defrecord Database [env db-spec]
  component/Lifecycle

  (start [component]
    (assoc component :db-spec (env/database-config env)))

  (stop [component]
    component))

(defn db
  "DB Spec"
  [database]
  (:db-spec database))
