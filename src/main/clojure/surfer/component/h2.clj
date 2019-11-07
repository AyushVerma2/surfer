(ns surfer.component.h2
  (:require [com.stuartsierra.component :as component]))

(defrecord H2 [db config]
  component/Lifecycle

  (start [component]
    (Class/forName "org.h2.Driver")

    (assoc component :db {:dbtype "h2"
                          :dbname "~/surfertest"}))

  (stop [component]
    component))
