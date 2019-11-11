(ns surfer.component.web-server
  "WebServer Component namespace

   This namespace should only be required by `surfer.system`."
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]
            [surfer.handler :as handler]
            [surfer.env :as env]
            [surfer.app-context :as app-context]))

(defrecord WebServer [env database stop-server]
  component/Lifecycle

  (start [component]
    (let [app-context (app-context/new-context env database)

          handler (handler/make-handler app-context)

          http-port (env/web-server-config env [:port])

          stop-server (run-server handler {:port http-port})]
      (assoc component :stop-server stop-server)))

  (stop [component]
    (if stop-server
      (do
        (stop-server)
        (assoc component :stop-server nil))
      component)))
