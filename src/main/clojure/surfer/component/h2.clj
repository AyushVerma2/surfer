(ns surfer.component.h2
  "H2 Component namespace

   This namespace should only be required by `surfer.system`."
  (:require [com.stuartsierra.component :as component]
            [surfer.env :as env]))

(defrecord H2 [env db-spec]
  component/Lifecycle

  (start [component]
    (assoc component :db-spec (env/database-config env)))

  (stop [component]
    component))
