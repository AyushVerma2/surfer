(ns surfer.component.web-server
  (:require [com.stuartsierra.component :as component]
            [org.httpkit.server :refer [run-server]]
            [surfer.handler :as handler]))

(defrecord WebServer [config h2 kill]
  component/Lifecycle

  (start [component]
    (let [handler (handler/make-handler {:config config
                                         :h2 h2})

          port (get-in config [:config :http-port])]
      (assoc component :kill (run-server handler {:port port}))))

  (stop [component]
    (if kill
      (do
        (kill)
        (assoc component :kill nil))
      component)))
