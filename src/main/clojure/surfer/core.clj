(ns surfer.core
  (:require
    [surfer.system :as system]
    [com.stuartsierra.component :as component]))

(defn -main
  "Start Surfer"
  [& _]
  (component/start (system/new-system :prod)))
