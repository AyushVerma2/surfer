(ns surfer.component.web-server
  "WebServer Component namespace

   This namespace should only be required by `surfer.system`."
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]
            [surfer.handler :as handler]
            [surfer.env :as env]
            [surfer.app-context :as app-context]))

(defrecord WebServer [env database starfish stop-server]
  component/Lifecycle

  (start [component]
    (let [app-context (app-context/new-context env database starfish)

          ;; Surfer is a web application, and handler functions are essentially
          ;; the entry point of the system.
          ;; Web handler functions have access to a subset of System's components so
          ;; it can pass such components to other functions or the the app context itself.
          handler (handler/make-handler app-context)

          http-port (env/web-server-config env [:port])

          stop-server (run-server handler {:port (or (some-> (System/getenv "PORT") (Integer/parseInt))
                                                     http-port)})]
      (assoc component :stop-server stop-server)))

  (stop [component]
    (if stop-server
      (do
        (stop-server)
        (assoc component :stop-server nil))
      component)))
