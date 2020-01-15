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
                                                           :children {:A {:did "<DID>"}
                                                                      :B {:did "<DID>"}}
                                                           :edges [{:sourcePort "a"
                                                                    :target "A"
                                                                    :targetPort "x"}

                                                                   {:source "A"
                                                                    :sourcePort "x"
                                                                    :target "B"
                                                                    :targetPort "y"}

                                                                   {:source "B"
                                                                    :sourcePort "y"
                                                                    :targetPort "b"}]})]
    (testing "Conversion"
      (is (= #:orchestration{:id "Root"

                             :children {"A" #:orchestration-child {:id "A"
                                                                   :did "<DID>"}

                                        "B" #:orchestration-child {:id "B"
                                                                   :did "<DID>"}}

                             :edges [#:orchestration-edge{:source-port :a
                                                          :target "A"
                                                          :target-port :x}

                                     #:orchestration-edge{:source "A"
                                                          :source-port :x
                                                          :target "B"
                                                          :target-port :y}

                                     #:orchestration-edge{:source "B"
                                                          :source-port :y
                                                          :target-port :b}]}

             orchestration)))

    (testing "Spec"
      (is (= true (s/valid? :orchestration-edge/edge #:orchestration-edge{:source "B"
                                                                          :source-port :y
                                                                          :target-port :b}))))))

(deftest source-root-edge?-test
  (testing "Source root"
    (is (= true (orchestration/source-root-edge? nil #:orchestration-edge {})))
    (is (= true (orchestration/source-root-edge? {} #:orchestration-edge {})))
    (is (= true (orchestration/source-root-edge? {:orchestration/id "Root"} #:orchestration-edge {})))
    (is (= true (orchestration/source-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:source nil})))
    (is (= true (orchestration/source-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:source "Root"}))))

  (testing "Not source root"
    (is (= false (orchestration/source-root-edge? nil #:orchestration-edge {:source "A"})))
    (is (= false (orchestration/source-root-edge? {} #:orchestration-edge {:source "A"})))
    (is (= false (orchestration/source-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:source "A"})))
    (is (= false (orchestration/source-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:source "A" :target "B"})))))

(deftest target-root-edge?-test
  (testing "Target root"
    (is (= true (orchestration/target-root-edge? nil #:orchestration-edge {})))
    (is (= true (orchestration/target-root-edge? {} #:orchestration-edge {})))
    (is (= true (orchestration/target-root-edge? {:orchestration/id "Root"} #:orchestration-edge {})))
    (is (= true (orchestration/target-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:target nil})))
    (is (= true (orchestration/target-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:target "Root"}))))

  (testing "Not target root"
    (is (= false (orchestration/target-root-edge? nil #:orchestration-edge {:target "A"})))
    (is (= false (orchestration/target-root-edge? {} #:orchestration-edge {:target "A"})))
    (is (= false (orchestration/target-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:target "A"})))
    (is (= false (orchestration/target-root-edge? {:orchestration/id "Root"} #:orchestration-edge {:source "A" :target "B"})))))

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
  (testing "Redirect param"
    (testing "With explicit source and root"
      (let [orchestration {:orchestration/id "Root"
                           :orchestration/edges
                           [#:orchestration-edge {:source "Root"
                                                  :source-port :n
                                                  :target "Increment"
                                                  :target-port :n}

                            #:orchestration-edge {:source "Increment"
                                                  :source-port :n
                                                  :target "Root"
                                                  :target-port :n}]}

            process {"Root" {:orchestration-invocation/input {:n 1}}}

            params (orchestration/invokable-params orchestration {:n 1} process "Increment")]
        (is (= {:n 1} params))))

    (testing "Without explicit source and root"
      (let [orchestration {:orchestration/id "Root"
                           :orchestration/edges
                           [#:orchestration-edge {:source-port :n
                                                  :target "Increment"
                                                  :target-port :n}

                            #:orchestration-edge {:source "Increment"
                                                  :source-port :n
                                                  :target-port :n}]}

            process {"Root" {:orchestration-invocation/input {:n 1}}}

            params (orchestration/invokable-params orchestration {:n 1} process "Increment")]
        (is (= {:n 1} params)))))

  (testing "Single param"
    (let [orchestration {:orchestration/edges
                         [#:orchestration-edge {:source "make-range"
                                                :source-port :range
                                                :target "filter-odds"
                                                :target-port :coll}]}

          process {"make-range" {:orchestration-invocation/input {}
                                 :orchestration-invocation/output {:range [0 1 2]}}}

          params (orchestration/invokable-params orchestration {} process "filter-odds")]
      (is (= {:coll [0 1 2]} params))))

  (testing "Re-using source"
    (let [orchestration {:orchestration/edges
                         [#:orchestration-edge {:source "make-range"
                                                :source-port :range
                                                :target "concatenate"
                                                :target-port :coll1}

                          #:orchestration-edge {:source "make-range"
                                                :source-port :range
                                                :target "concatenate"
                                                :target-port :coll2}]}

          process {"make-range" {:orchestration-invocation/input {}
                                 :orchestration-invocation/output {:range [0 1 2]}}}

          params (orchestration/invokable-params orchestration {} process "concatenate")]
      (is (= {:coll1 [0 1 2] :coll2 [0 1 2]} params)))))

(deftest output-mapping-test
  (testing "Output less"
    (is (= {:n 1} (orchestration/output-mapping {:orchestration/id "Orchestration"
                                                 :orchestration/edges
                                                 [#:orchestration-edge {:source "Foo"
                                                                        :source-port :n1
                                                                        :target "Orchestration"
                                                                        :target-port :n}]}

                                                {"Foo" {:orchestration-invocation/output {:n1 1
                                                                                          :n2 2}}})))

    (is (= {:n 1} (orchestration/output-mapping {:orchestration/id "Orchestration"
                                                 :orchestration/edges
                                                 [#:orchestration-edge {:source "Foo"
                                                                        :source-port :n1
                                                                        :target-port :n}]}

                                                {"Foo" {:orchestration-invocation/output {:n1 1
                                                                                          :n2 2}}}))))

  (testing "Output is the same as Operation's output"
    (is (= {:n1 1 :n2 2} (orchestration/output-mapping {:orchestration/id "Orchestration"
                                                        :orchestration/edges
                                                        [#:orchestration-edge {:source "Foo"
                                                                               :source-port :n1
                                                                               :target-port :n1}

                                                         #:orchestration-edge {:source "Foo"
                                                                               :source-port :n2
                                                                               :target-port :n2}]}

                                                       {"Foo" {:orchestration-invocation/output {:n1 1
                                                                                                 :n2 2}}}))))

  (testing "Remap output"
    (is (= {:n1 1 :n2 1} (orchestration/output-mapping {:orchestration/id "Orchestration"
                                                        :orchestration/edges
                                                        [#:orchestration-edge {:source "Foo"
                                                                               :source-port :n1
                                                                               :target-port :n1}

                                                         #:orchestration-edge {:source "Foo"
                                                                               :source-port :n1
                                                                               :target-port :n2}]}

                                                       {"Foo" {:orchestration-invocation/output {:n1 1
                                                                                                 :n2 2}}})))))

(deftest execute-sync-test
  (binding [sfa/*resolver* (LocalResolverImpl.)]

    (sfa/register! fixture/test-agent-did (env/self-ddo (system/env test-system)))

    (let [test-agent (fixture/test-agent)

          make-range (->> (invoke/invokable-metadata #'demo.invokable/make-range)
                          (invoke/register-invokable test-agent))

          filter-odds (->> (invoke/invokable-metadata #'demo.invokable/filter-odds)
                           (invoke/register-invokable test-agent))

          concatenate (->> (invoke/invokable-metadata #'demo.invokable/concatenate)
                           (invoke/register-invokable test-agent))

          increment (->> (invoke/invokable-metadata #'demo.invokable/increment)
                         (invoke/register-invokable test-agent))

          bad-increment (->> (invoke/invokable-metadata #'demo.invokable/bad-increment)
                             (invoke/register-invokable test-agent))]
      (testing "A very basic Orchestration example"
        (let [orchestration {:orchestration/id "Root"

                             :orchestration/children
                             {"make-range" #:orchestration-child {:did (sf/asset-id make-range)}
                              "filter-odds" #:orchestration-child {:did (sf/asset-id filter-odds)}}

                             :orchestration/edges
                             [#:orchestration-edge {:source "make-range"
                                                    :source-port :range
                                                    :target "filter-odds"
                                                    :target-port :numbers}

                              #:orchestration-edge {:source "filter-odds"
                                                    :source-port :odds
                                                    :target-port :odds}]}]
          (is (= {:orchestration-execution/topo '("make-range" "filter-odds")
                  :orchestration-execution/process
                  {"Root" #:orchestration-invocation {:node "Root"
                                                      :input nil
                                                      :output {:odds [1 3 5 7 9]}
                                                      :status :orchestration-invocation.status/succeeded}

                   "make-range" #:orchestration-invocation {:node "make-range"
                                                            :input {}
                                                            :output {:range [0 1 2 3 4 5 6 7 8 9]}
                                                            :status :orchestration-invocation.status/succeeded}

                   "filter-odds" #:orchestration-invocation {:node "filter-odds"
                                                             :input {:numbers [0 1 2 3 4 5 6 7 8 9]}
                                                             :output {:odds [1 3 5 7 9]}
                                                             :status :orchestration-invocation.status/succeeded}}}

                 (orchestration/execute-sync (system/app-context test-system) orchestration)))))

      (testing "Nodes (Operations) with dependencies"
        (let [orchestration {:orchestration/id "Root"

                             :orchestration/children
                             {"make-range1" #:orchestration-child {:did (sf/asset-id make-range)}
                              "make-range2" #:orchestration-child {:did (sf/asset-id make-range)}
                              "concatenate" #:orchestration-child {:did (sf/asset-id concatenate)}}

                             :orchestration/edges
                             [#:orchestration-edge {:source "make-range1"
                                                    :source-port :range
                                                    :target "concatenate"
                                                    :target-port :coll1}

                              #:orchestration-edge {:source "make-range2"
                                                    :source-port :range
                                                    :target "concatenate"
                                                    :target-port :coll2}

                              #:orchestration-edge {:source "concatenate"
                                                    :source-port :coll
                                                    :target-port :coll}]}]
          (is (= {:orchestration-execution/topo '("make-range1" "make-range2" "concatenate"),
                  :orchestration-execution/process
                  {"Root" #:orchestration-invocation {:node "Root"
                                                      :input {}
                                                      :output {:coll [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}
                                                      :status :orchestration-invocation.status/succeeded}

                   "make-range1" #:orchestration-invocation {:node "make-range1"
                                                             :input {}
                                                             :output {:range [0 1 2 3 4 5 6 7 8 9]}
                                                             :status :orchestration-invocation.status/succeeded}

                   "make-range2" #:orchestration-invocation {:node "make-range2"
                                                             :input {}
                                                             :output {:range [0 1 2 3 4 5 6 7 8 9]}
                                                             :status :orchestration-invocation.status/succeeded}

                   "concatenate" #:orchestration-invocation {:node "concatenate"
                                                             :input {:coll2 [0 1 2 3 4 5 6 7 8 9] :coll1 [0 1 2 3 4 5 6 7 8 9]}
                                                             :output {:coll [0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9]}
                                                             :status :orchestration-invocation.status/succeeded}}}
                 (orchestration/execute-sync (system/app-context test-system) orchestration {})))))

      (testing "Intermediary Operation fails"
        (let [orchestration #:orchestration {:id "Root"

                                             :children
                                             {"Increment1" {:orchestration-child/did (sf/asset-id increment)}
                                              "BadIncrement" {:orchestration-child/did (sf/asset-id bad-increment)}
                                              "Increment2" {:orchestration-child/did (sf/asset-id increment)}}

                                             :edges
                                             [#:orchestration-edge {:source "Root"
                                                                    :source-port :n
                                                                    :target "Increment1"
                                                                    :target-port :n}

                                              #:orchestration-edge {:source "Increment1"
                                                                    :source-port :n
                                                                    :target "BadIncrement"
                                                                    :target-port :n}

                                              #:orchestration-edge {:source "BadIncrement"
                                                                    :source-port :n
                                                                    :target "Increment2"
                                                                    :target-port :n}

                                              #:orchestration-edge {:source "Increment2"
                                                                    :source-port :n
                                                                    :target "Root"
                                                                    :target-port :n}]}

              execution (orchestration/execute-sync (system/app-context test-system) orchestration {:n 1})]
          (is (= '("Increment1" "BadIncrement" "Increment2" (:orchestration-execution/topo execution))))
          (is (= {"Root" #:orchestration-invocation {:node "Root"
                                                     :input {:n 1}
                                                     :status :orchestration-invocation.status/failed}

                  "Increment1" #:orchestration-invocation {:node "Increment1"
                                                           :input {:n 1}
                                                           :output {:n 2}
                                                           :status :orchestration-invocation.status/succeeded}

                  "BadIncrement" #:orchestration-invocation {:node "BadIncrement"
                                                             :input {:n 2}
                                                             :status :orchestration-invocation.status/failed}

                  "Increment2" #:orchestration-invocation {:node "Increment2"
                                                           :status :orchestration-invocation.status/cancelled}}
                 (reduce-kv
                   (fn [process k v]
                     (assoc process k (dissoc v :orchestration-invocation/error)))
                   {}
                   (:orchestration-execution/process execution))))
          (let [root-error (get-in execution [:orchestration-execution/process "Root" :orchestration-invocation/error])
                bad-increment-error (get-in execution [:orchestration-execution/process "BadIncrement" :orchestration-invocation/error])

                [failed-spec-key] (s/conform :orchestration-invocation/error root-error)
                [exception-spec-key] (s/conform :orchestration-invocation/error bad-increment-error)]

            (is (= :failed failed-spec-key))
            (is (= :exception exception-spec-key))

            (is (= "java.lang.NullPointerException" (.getMessage bad-increment-error)))
            (is (= root-error (get-in execution [:orchestration-execution/process "BadIncrement"])))))))))

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

         (orchestration/results {:orchestration-execution/process
                                 {"Root" #:orchestration-invocation {:output {:odds [1 3]}
                                                                     :status :orchestration-invocation.status/succeeded}

                                  "make-range" #:orchestration-invocation {:output {:range [0 1 2 3]}
                                                                           :status :orchestration-invocation.status/succeeded}

                                  "filter-odds" #:orchestration-invocation {:output {:odds [1 3]}
                                                                            :status :orchestration-invocation.status/succeeded}}})))

  (let [increment1-invocation #:orchestration-invocation {:node "Increment1"
                                                          :status :orchestration-invocation.status/failed
                                                          :input {:n nil}
                                                          :error (NullPointerException. "Missing 'n'.")}

        increment2-invocation #:orchestration-invocation {:node "Increment2"
                                                          :status :orchestration-invocation.status/cancelled}

        root-invocation #:orchestration-invocation {:node "Root"
                                                    :status :orchestration-invocation.status/failed
                                                    :input {:n 1}
                                                    :error increment1-invocation}]
    (is (= {:status "failed"
            :error "Failed to execute Operation 'Increment1'."
            :children
            {"Increment1"
             {:status "failed"
              :error "Missing 'n'."}

             "Increment2"
             {:status "cancelled"}}}

           (orchestration/results {:orchestration-execution/process
                                   {"Root" root-invocation
                                    "Increment1" increment1-invocation
                                    "Increment2" increment2-invocation}})))))
