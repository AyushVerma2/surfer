(ns dev
  (:require [surfer.demo.invokable-demo :as demo.invokable]
            [surfer.store :as store]
            [surfer.env :as env]
            [surfer.system :as system]
            [surfer.invokable :as invokable]
            [surfer.asset :as asset]
            [surfer.storage :as storage]
            [surfer.app-context :as app-context]
            [surfer.migration :as migration]
            [surfer.orchestration :as orchestration]
            [starfish.core :as sf]
            [starfish.alpha :as sfa]
            [clojure.data.json :as data.json]
            [clojure.java.jdbc :as jdbc]
            [clojure.repl :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [clojure.alpha.spec :as s]
            [clojure.alpha.spec.gen :as gen]
            [clojure.pprint]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]
            [clj-http.client :as http]
            [com.stuartsierra.dependency :as dep]
            [surfer.database :as database]
            [clojure.string :as str]
            [clojure.pprint :as pprint]
            [surfer.job :as job]))

(set-init (constantly (system/new-system :dev)))

;; ---

(defn env []
  (system/env system))

(defn app-context []
  (system/app-context system))

(defn db []
  (database/db (system/database system)))

(defn reset-db []
  (store/clear-db (db) (env/dbtype (env)))
  (migration/migrate (db) (env/user-config (env))))

(defn query [& sql-params]
  (jdbc/query (db) sql-params))

(defn print-pretty-status [process]
  (println (job/pretty-status process)))

(comment

  (reset-db)


  (def did
    (env/self-did (system/env system)))

  (def ddo
    (env/self-ddo (system/env system)))

  (def aladdin
    (sfa/did->agent did))


  ;; -- Invokables
  (def increment
    (let [metadata (invokable/invokable-metadata #'demo.invokable/increment)]
      (invokable/register-invokable aladdin metadata)))

  (def bad-increment
    (let [metadata (invokable/invokable-metadata #'demo.invokable/bad-increment)]
      (invokable/register-invokable aladdin metadata)))

  (def make-range
    (let [metadata (invokable/invokable-metadata #'demo.invokable/make-range)]
      (invokable/register-invokable aladdin metadata)))

  (def make-range-asset
    (let [metadata (invokable/invokable-metadata #'demo.invokable/make-range-asset)]
      (invokable/register-invokable aladdin metadata)))

  (def filter-odds
    (let [metadata (invokable/invokable-metadata #'demo.invokable/filter-odds)]
      (invokable/register-invokable aladdin metadata)))

  (def concatenate
    (let [metadata (invokable/invokable-metadata #'demo.invokable/concatenate)]
      (invokable/register-invokable aladdin metadata)))

  ;; -- Run Job
  (job/run-job (app-context) (sf/asset-id increment) {:n 1})
  (job/run-job (app-context) (sf/asset-id bad-increment) {})


  ;; -- Orchestration Demo
  ;; 1. Register this Operation
  ;; 2. Invoke the Operation to create an Orchestration
  ;; 3. Invoke the Orchestration (the previous step should return the ID of the Orchestration)
  (def make-orchestration-demo1
    (let [metadata (invokable/invokable-metadata #'demo.invokable/make-orchestration-demo1)]
      (invokable/register-invokable aladdin metadata)))

  ;; Step 2 - Invoke the Operation
  (as-> (job/run-job (app-context) (sf/asset-id make-orchestration-demo1) {:n 2}) orchestration
        ;; Step 3 - Invoke the Orchestration
        (job/run-job (app-context) (:id orchestration) {:n 0}))

  (def make-orchestration-demo2
    (let [metadata (invokable/invokable-metadata #'demo.invokable/make-orchestration-demo2)]
      (invokable/register-invokable aladdin metadata)))


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
                                                              :target-port :n}]}]
    (orchestration/execute-async (app-context) orchestration {:n 1} {:watch print-pretty-status}))

  ;; Re-using the same Operation n times to connect to a different port
  (let [orchestration (orchestration/dep13->orchestration {:id "Root"

                                                           :children
                                                           {"make-range" {:did (sf/asset-id make-range)}
                                                            "concatenate" {:did (sf/asset-id concatenate)}}

                                                           :edges
                                                           [{:source "make-range"
                                                             :sourcePort "range"
                                                             :target "concatenate"
                                                             :targetPort "coll1"}

                                                            {:source "make-range"
                                                             :sourcePort "range"
                                                             :target "concatenate"
                                                             :targetPort "coll1"}

                                                            {:source "concatenate"
                                                             :sourcePort "coll"
                                                             :target "Root"
                                                             :targetPort "coll"}]})]
    (orchestration/execute (app-context) orchestration {} {:watch print-pretty-status}))


  ;; -- Specs
  (s/valid? :orchestration-edge/source-root #:orchestration-edge{:source-port :a
                                                                 :target "A"
                                                                 :target-port :x})

  (s/valid? :orchestration-edge/node-to-node #:orchestration-edge{:source "A"
                                                                  :source-port :a
                                                                  :target "B"
                                                                  :target-port :b})

  (s/conform :orchestration-edge/edge #:orchestration-edge{:source "Root"
                                                           :source-port :a
                                                           :target-port :x})

  (gen/sample (s/gen :orchestration/orchestration) 1)

  )

