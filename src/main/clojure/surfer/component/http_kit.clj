(ns surfer.component.http-kit
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]
            [surfer.handler :as handler]))

(defrecord WebServer [server config h2]
  component/Lifecycle

  (start [component]
    (let [handler (handler/make-handler {:config config
                                         :h2 h2})

          port (get-in config [:config :http-port])]
      (assoc component :server (run-server handler {:port port}))))

  (stop [component]
    (if server
      (do
        (server)
        (assoc component :server nil))
      component)))
