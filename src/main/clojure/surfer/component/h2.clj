(ns surfer.component.h2
  (:require [com.stuartsierra.component :as component]
            [surfer.env :as env]))

(defrecord H2 [env db-spec]
  component/Lifecycle

  (start [component]
    (assoc component :db-spec (env/h2-config env)))

  (stop [component]
    component))
