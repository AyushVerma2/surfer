(ns surfer.component.h2
  (:require [com.stuartsierra.component :as component]))

(defrecord H2 [db config]
  component/Lifecycle

  (start [component]
    (let [h2-config (get-in config [:config :h2])]
      (assoc component :db (merge {:dbtype "h2"
                                   :dbname "~/.surfer/surfer"}
                                  (select-keys h2-config [:dbname])))))

  (stop [component]
    component))
