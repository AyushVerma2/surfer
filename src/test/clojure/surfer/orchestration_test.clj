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

(deftest dependency-edges-test
  (let [orchestration {:edges
                       [{:source "a"
                         :target "b"}

                        {:source "b"
                         :target "c"}

                        {:source "make-range"
                         :target "concatenate"}

                        {:source "make-range"
                         :target "concatenate"}]}

        edges (orchestration/dependency-edges orchestration "concatenate" "make-range")]
    (is (= 2 (count edges)))

    (is (= #{"make-range"} (->> (map :source edges)
                                (into #{}))))

    (is (= #{"concatenate"} (->> (map :target edges)
                                 (into #{}))))))

(deftest params-test
  (testing "Single param"
    (let [orchestration {:edges
                         [{:source "make-range"
                           :target "filter-odds"
                           :ports [:range :coll]}]}

          process {"make-range" {:input {}
                                 :output {:range [0 1 2]}}}

          params (orchestration/params orchestration process "filter-odds")]
      (is (= {:coll [0 1 2]} params))))

  (testing "Re-using source"
    (let [orchestration {:edges
                         [{:source "make-range"
                           :target "concatenate"
                           :ports [:range :coll1]}

                          {:source "make-range"
                           :target "concatenate"
                           :ports [:range :coll2]}]}

          process {"make-range" {:input {}
                                 :output {:range [0 1 2]}}}

          params (orchestration/params orchestration process "concatenate")]
      (is (= {:coll1 [0 1 2] :coll2 [0 1 2]} params)))))
