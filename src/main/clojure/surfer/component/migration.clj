(ns surfer.component.migration
  "Migration Component namespace

   This namespace should only be required by `surfer.system`."
  (:require [com.stuartsierra.component :as component]
            [surfer.store :as store]
            [surfer.database :as database]
            [surfer.env :as env]))

(defrecord Migration [env database]
  component/Lifecycle

  (start [component]
    (store/migrate-db! (database/db database) (env/user-config env))

    component)

  (stop [component]
    component))
