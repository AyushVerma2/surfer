(ns surfer.core
  (:require 
    [surfer systems]
    [surfer.ckan :refer :all]
    [clj-http.client :as client]
    [surfer.systems :refer [base-system]]
    [system.repl :refer [set-init! go start reset stop]]
    [clojure.data.json :as json]))


(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'base-system)]
    (set-init! system)
    (start)))


(comment
  (-main) ;; launch the app
  (reset) ;; stop and restart the app

  
  )