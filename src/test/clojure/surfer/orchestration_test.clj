(ns surfer.orchestration-test
  (:require [clojure.test :refer :all]
            [surfer.orchestration :as orchestration]
            [com.stuartsierra.dependency :as dep]
            [surfer.test.fixture :as fixture]
            [surfer.invokable :as invoke]
            [surfer.demo.invokable-demo :as demo.invokable]
            [surfer.system :as system]
            [surfer.env :as env]
            [starfish.core :as sf]
            [starfish.alpha :as sfa])
  (:import (sg.dex.starfish.impl.memory LocalResolverImpl)))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

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

(deftest execute-test
  (binding [sfa/*resolver* (LocalResolverImpl.)]

    (sfa/register! fixture/test-agent-did (env/self-ddo (system/env test-system)))

    (let [test-agent (fixture/test-agent)

          make-range (->> (invoke/invokable-metadata #'demo.invokable/make-range)
                          (invoke/register-invokable test-agent))

          filter-odds (->> (invoke/invokable-metadata #'demo.invokable/filter-odds)
                           (invoke/register-invokable test-agent))

          concatenate (->> (invoke/invokable-metadata #'demo.invokable/concatenate)
                           (invoke/register-invokable test-agent))]
      (testing "A very basic Orchestration example"
        (let [orchestration {:children
                             {"make-range" (sf/asset-id make-range)
                              "filter-odds" (sf/asset-id filter-odds)}

                             :edges
                             [{:source "make-range"
                               :target "filter-odds"
                               :ports [:range :numbers]}]}]
          (is (= {:topo '("make-range" "filter-odds"),
                  :process {"make-range" {:input {}, :output {:range [0 1 2 3 4 5 6 7 8 9]}},
                            "filter-odds" {:input {:numbers [0 1 2 3 4 5 6 7 8 9]}, :output {:odds [1 3 5 7 9]}}}}
                 (orchestration/execute (system/app-context test-system) orchestration)))))

      (testing "Nodes (Operations) with dependencies"
        (let [orchestration {:children
                             {"make-range1" (sf/asset-id make-range)
                              "make-range2" (sf/asset-id make-range)
                              "concatenate" (sf/asset-id concatenate)}

                             :edges
                             [{:source "make-range1"
                               :target "concatenate"
                               :ports [:range :coll1]}

                              {:source "make-range2"
                               :target "concatenate"
                               :ports [:range :coll2]}]}]
          (is (= {:topo '("make-range1" "make-range2" "concatenate"),
                  :process {"make-range1" {:input {}, :output {:range [0 1 2 3 4 5 6 7 8 9]}},
                            "make-range2" {:input {}, :output {:range [0 1 2 3 4 5 6 7 8 9]}},
                            "concatenate" {:input {:coll2 [0 1 2 3 4 5 6 7 8 9], :coll1 [0 1 2 3 4 5 6 7 8 9]},
                                           :output {:coll [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}}}}
                 (orchestration/execute (system/app-context test-system) orchestration))))))))

;; Add to Backlog - Think about the `additionalInfo` metadata

(deftest results-test
  (is (= {:status "succeeded"
          :results {:odds [1 3]}
          :children
          {"make-range"
           {:status "succeeded"
            :results {:range [0 1 2 3]}}
           "filter-odds"
           {:status "succeeded"
            :results {:odds [1 3]}}}}
         (orchestration/results {:topo '("make-range" "filter-odds")
                                 :process {"make-range" {:input {}
                                                         :output {:range [0 1 2 3]}}
                                           "filter-odds" {:input {:numbers [0 1 2 3]}
                                                          :output {:odds [1 3]}}}}))))
