(ns surfer.component.h2
  (:require [com.stuartsierra.component :as component]))

(defrecord H2 [db config]
  component/Lifecycle

  (start [component]
    (let [default-config {:dbtype "h2"
                          :dbname "~/.surfer/h2/surfer"}

          user-config (-> (get-in config [:config :h2])
                          (select-keys [:dbtype :dbname]))]

      (assoc component :db (merge default-config user-config))))

  (stop [component]
    component))
