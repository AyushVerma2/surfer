(ns surfer.component.http-kit
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]))

(defrecord WebServer [server handler config]
  component/Lifecycle

  (start [component]
    (assoc component :server (run-server handler {:port (get-in config [:config :http-port])})))

  (stop [component]
    (if server
      (do
        (server)
        (assoc component :server nil))
      component)))
