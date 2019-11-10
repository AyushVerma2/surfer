(ns surfer.component.h2
  (:require [com.stuartsierra.component :as component]))

(defrecord H2 [env db-spec]
  component/Lifecycle

  (start [component]
    (assoc component :db-spec (get-in env [:config :h2])))

  (stop [component]
    component))
