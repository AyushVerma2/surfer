(ns surfer.component.web-server
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]
            [surfer.handler :as handler]))

(defrecord WebServer [env database stop-server]
  component/Lifecycle

  (start [component]
    (let [app-context {:env env
                       :db (:db-spec database)}

          handler (handler/make-handler app-context)

          http-port (get-in env [:config :web-server :port])

          stop-server (run-server handler {:port http-port})]
      (assoc component :stop-server stop-server)))

  (stop [component]
    (if stop-server
      (do
        (stop-server)
        (assoc component :stop-server nil))
      component)))
