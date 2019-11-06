(ns surfer.component.http-kit
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]))

(defrecord WebServer [server handler options]
  component/Lifecycle

  (start [component]
    (assoc component :server (run-server handler options)))

  (stop [component]
    (if server
      (do
        (server)
        (assoc component :server nil))
      component)))
