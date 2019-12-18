(ns surfer.orchestration-test
  (:require [clojure.test :refer :all]
            [surfer.orchestration :as orchestration]
            [starfish.core :as sf]
            [com.stuartsierra.dependency :as dep]))

(deftest dependency-graph-test
  (let [orchestration {:id "Root"
                       :edges
                       [{:source "Root"
                         :target "A"}

                        {:source "A"
                         :target "C"}

                        {:source "B"
                         :target "C"}

                        {:source "C"
                         :target "D"}

                        {:source "D"
                         :target "Root"}]}
        graph (orchestration/dependency-graph orchestration)]
    (is (= {"C" #{"B" "A"}, "D" #{"C"}} (:dependencies graph)))
    (is (= {"A" #{"C"}, "B" #{"C"}, "C" #{"D"}} (:dependents graph)))
    (is (= ["A" "B" "C" "D"] (dep/topo-sort graph)))))
