(ns surfer.component.web-server
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]
            [surfer.handler :as handler]))

(defrecord WebServer [config h2 kill]
  component/Lifecycle

  (start [component]
    (let [handler (handler/make-handler {:config config :h2 h2})
          http-port (get-in config [:config :http-port])
          kill-server (run-server handler {:port http-port})]
      (assoc component :kill kill-server)))

  (stop [component]
    (if kill
      (do
        (kill)
        (assoc component :kill nil))
      component)))
