(ns surfer.component.db
  (:require [com.stuartsierra.component :as component]))

(defrecord DB [db-spec config]
  component/Lifecycle

  (start [component]
    (Class/forName "org.h2.Driver")

    (assoc component :db-spec {:dbtype "h2"
                               :dbname "~/surfertest"}))

  (stop [component]
    component))
