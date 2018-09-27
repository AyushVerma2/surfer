(ns surfer.core
  (:require 
    [surfer systems]
    [surfer.systems :refer [base-system]]
    [system.repl :refer [set-init! go start reset stop]]))


(defn -main
  "Start a production system, unless a system is passed as argument (as in the dev-run task)."
  [& args]
  (let [system (or (first args) #'base-system)]
    (set-init! system)
    (start)))
