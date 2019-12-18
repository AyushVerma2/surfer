(ns surfer.orchestration
  (:require [com.stuartsierra.dependency :as dep]))

(defn dependency-graph [orchestration]
  (reduce
    (fn [graph {:keys [source target]}]
      (dep/depend graph target source))
    (dep/graph)
    (:edges orchestration)))
