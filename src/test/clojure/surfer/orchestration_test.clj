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
            [starfish.alpha :as sfa]
            [clojure.alpha.spec :as s])
  (:import (sg.dex.starfish.impl.memory LocalResolverImpl)))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest dep13->orchestration-test
  (let [orchestration (orchestration/dep13->orchestration {:id "Root"
                                                           :children {:A "<DID>"
                                                                      :B "<DID>"}
                                                           :edges [{:source "A"
                                                                    :sourcePort "x"
                                                                    :target "B"
                                                                    :targetPort "y"}]})]
    (testing "Conversion"
      (is (= #:orchestration{:id "Root"
                             :children {"A" "<DID>"
                                        "B" "<DID>"}
                             :edges [#:orchestration-edge{:source "A"
                                                          :target "B"
                                                          :ports [:x :y]}]}

             orchestration)))

    (testing "Spec"
      (is (= true (s/valid? :orchestration/orchestration orchestration))))))

(deftest dependency-graph-test
  (let [orchestration #:orchestration{:id "Root"
                                      :edges
                                      [#:orchestration-edge{:source "Root"
                                                            :target "A"}

                                       #:orchestration-edge{:source "A"
                                                            :target "C"}

                                       #:orchestration-edge{:source "B"
                                                            :target "C"}

                                       #:orchestration-edge{:source "C"
                                                            :target "D"}

                                       #:orchestration-edge{:source "D"
                                                            :target "Root"}]}
        graph (orchestration/dependency-graph orchestration)]
    (is (= {"C" #{"B" "A"}, "D" #{"C"}} (:dependencies graph)))
    (is (= {"A" #{"C"}, "B" #{"C"}, "C" #{"D"}} (:dependents graph)))
    (is (= ["A" "B" "C" "D"] (dep/topo-sort graph)))))

(deftest edges=-test
  (let [orchestration {:orchestration/edges
                       [#:orchestration-edge{:source "a"
                                             :target "b"}

                        #:orchestration-edge{:source "b"
                                             :target "c"}

                        #:orchestration-edge{:source "make-range"
                                             :target "concatenate"}

                        #:orchestration-edge{:source "make-range"
                                             :target "concatenate"}]}]

    (is (= 1 (count (orchestration/edges= orchestration #:orchestration-edge{:source "a"
                                                                             :target "b"}))))

    (is (= 2 (count (orchestration/edges= orchestration #:orchestration-edge{:source "make-range"
                                                                             :target "concatenate"}))))

    (is (= 2 (count (orchestration/edges= orchestration #:orchestration-edge{:source "make-range"}))))

    (is (= 2 (count (orchestration/edges= orchestration #:orchestration-edge{:target "concatenate"}))))))

(deftest invokable-params-test
  (testing "Single param"
    (let [orchestration {:orchestration/edges
                         [#:orchestration-edge {:source "make-range"
                                                :target "filter-odds"
                                                :ports [:range :coll]}]}

          process {"make-range" {:orchestration-invocation/input {}
                                 :orchestration-invocation/output {:range [0 1 2]}}}

          params (orchestration/invokable-params orchestration {} process "filter-odds")]
      (is (= {:coll [0 1 2]} params))))

  (testing "Re-using source"
    (let [orchestration {:orchestration/edges
                         [#:orchestration-edge {:source "make-range"
                                                :target "concatenate"
                                                :ports [:range :coll1]}

                          #:orchestration-edge {:source "make-range"
                                                :target "concatenate"
                                                :ports [:range :coll2]}]}

          process {"make-range" {:orchestration-invocation/input {}
                                 :orchestration-invocation/output {:range [0 1 2]}}}

          params (orchestration/invokable-params orchestration {} process "concatenate")]
      (is (= {:coll1 [0 1 2] :coll2 [0 1 2]} params)))))

(deftest output-mapping-test
  (testing "Output less"
    (is (= {:n 1} (orchestration/output-mapping {:orchestration/id "Orchestration"
                                                 :orchestration/edges
                                                 [#:orchestration-edge {:source "Foo"
                                                                        :target "Orchestration"
                                                                        :ports [:n1 :n]}]}

                                                {"Foo" {:orchestration-invocation/output {:n1 1
                                                                                          :n2 2}}}))))

  (testing "Output is the same as Operation's output"
    (is (= {:n1 1 :n2 2} (orchestration/output-mapping {:orchestration/id "Orchestration"
                                                        :orchestration/edges
                                                        [#:orchestration-edge {:source "Foo"
                                                                               :target "Orchestration"
                                                                               :ports [:n1 :n1]}

                                                         #:orchestration-edge {:source "Foo"
                                                                               :target "Orchestration"
                                                                               :ports [:n2 :n2]}]}

                                                       {"Foo" {:orchestration-invocation/output {:n1 1
                                                                                                 :n2 2}}}))))

  (testing "Remap output"
    (is (= {:n1 1 :n2 1} (orchestration/output-mapping {:orchestration/id "Orchestration"
                                                        :orchestration/edges
                                                        [#:orchestration-edge {:source "Foo"
                                                                               :target "Orchestration"
                                                                               :ports [:n1 :n1]}

                                                         #:orchestration-edge {:source "Foo"
                                                                               :target "Orchestration"
                                                                               :ports [:n1 :n2]}]}

                                                       {"Foo" {:orchestration-invocation/output {:n1 1
                                                                                                 :n2 2}}})))))

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
        (let [orchestration {:orchestration/id "Root"

                             :orchestration/children
                             {"make-range" (sf/asset-id make-range)
                              "filter-odds" (sf/asset-id filter-odds)}

                             :orchestration/edges
                             [#:orchestration-edge {:source "make-range"
                                                    :target "filter-odds"
                                                    :ports [:range :numbers]}

                              #:orchestration-edge {:source "filter-odds"
                                                    :target "Root"
                                                    :ports [:odds :odds]}]}]
          (is (= {:orchestration-execution/topo '("make-range" "filter-odds")
                  :orchestration-execution/process
                  {"Root" {:orchestration-invocation/input nil
                           :orchestration-invocation/output {:odds [1 3 5 7 9]}}

                   "make-range" {:orchestration-invocation/input {}
                                 :orchestration-invocation/output {:range [0 1 2 3 4 5 6 7 8 9]}}

                   "filter-odds" {:orchestration-invocation/input {:numbers [0 1 2 3 4 5 6 7 8 9]}
                                  :orchestration-invocation/output {:odds [1 3 5 7 9]}}}}

                 (orchestration/execute (system/app-context test-system) orchestration)))))

      (testing "Nodes (Operations) with dependencies"
        (let [orchestration {:orchestration/id "Root"

                             :orchestration/children
                             {"make-range1" (sf/asset-id make-range)
                              "make-range2" (sf/asset-id make-range)
                              "concatenate" (sf/asset-id concatenate)}

                             :orchestration/edges
                             [#:orchestration-edge {:source "make-range1"
                                                    :target "concatenate"
                                                    :ports [:range :coll1]}

                              #:orchestration-edge {:source "make-range2"
                                                    :target "concatenate"
                                                    :ports [:range :coll2]}

                              #:orchestration-edge {:source "concatenate"
                                                    :target "Root"
                                                    :ports [:coll :coll]}]}]
          (is (= {:orchestration-execution/topo '("make-range1" "make-range2" "concatenate"),
                  :orchestration-execution/process
                  {"Root" {:orchestration-invocation/input {}
                           :orchestration-invocation/output {:coll [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}}

                   "make-range1" {:orchestration-invocation/input {}
                                  :orchestration-invocation/output {:range [0 1 2 3 4 5 6 7 8 9]}}

                   "make-range2" {:orchestration-invocation/input {}
                                  :orchestration-invocation/output {:range [0 1 2 3 4 5 6 7 8 9]}}

                   "concatenate" {:orchestration-invocation/input {:coll2 [0 1 2 3 4 5 6 7 8 9] :coll1 [0 1 2 3 4 5 6 7 8 9]}
                                  :orchestration-invocation/output {:coll [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}}}}
                 (orchestration/execute (system/app-context test-system) orchestration {}))))))))

;; Add to Backlog - Think about the `additionalInfo` metadata

(deftest results-test
  (is (= {:status "succeeded"
          :results {:odds [1 3]}
          :children
          {"Root"
           {:status "succeeded"
            :results {:odds [1 3]}}

           "make-range"
           {:status "succeeded"
            :results {:range [0 1 2 3]}}

           "filter-odds"
           {:status "succeeded"
            :results {:odds [1 3]}}}}

         (orchestration/results {:process {"Root" {:output {:odds [1 3]}}

                                           "make-range" {:input {}
                                                         :output {:range [0 1 2 3]}}

                                           "filter-odds" {:input {:numbers [0 1 2 3]}
                                                          :output {:odds [1 3]}}}}))))
