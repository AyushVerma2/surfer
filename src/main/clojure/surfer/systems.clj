(ns surfer.systems
  (:require [com.stuartsierra.component :as component]
            [system.core :refer [defsystem]]
            [system.components.http-kit :refer [new-http-kit]]
            [surfer.handler :refer [app]]
            [surfer.config :refer [CONFIG]]
            [surfer.store :as store]
            [clojure.tools.logging :as log]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def PORT (Integer/parseInt (str (or (CONFIG :http-port) 8080))))

(log/debug "Server configured for port:" PORT)

(defn system []
  (component/system-map
    :web (new-http-kit :port PORT
                       :handler (fn [request]
                                  (surfer.handler/app request)))))

