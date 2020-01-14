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
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]
            [clj-http.client :as http]
            [com.stuartsierra.dependency :as dep]
            [surfer.database :as database]
            [clojure.string :as str]))

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

(comment

  (reset-db)

  ;; -- Import Datasets
  (let [database (system/database system)
        storage-path (env/storage-path (env))]
    (asset/import-edn! (db) storage-path "datasets.edn"))

  (def did
    (env/self-did (system/env system)))

  (def ddo
    (env/self-ddo (system/env system)))

  (def aladdin
    (sfa/did->agent did))

  (def n-asset
    ;; Data must be a JSON-encoded string
    (sf/upload aladdin (sf/memory-asset (data.json/write-str {:n 2}))))

  (def n-asset-did
    (sf/did n-asset))


  ;; -- Invoke

  (invokable/invoke #'demo.invokable/n-odd? (app-context) {:n {:did (str n-asset-did)}})

  (invokable/invoke #'demo.invokable/make-range-asset (app-context) {})

  ;; Param keys *must be* a string when calling the Java API directly.
  (def job
    (let [metadata (invokable/invokable-metadata #'demo.invokable/invokable-odd?)
          operation (invokable/invokable-operation (app-context) metadata)]
      (.invoke operation {"n" 1})))

  ;; Param keys can be a keyword because `starfish.core/invoke` uses `stringify-keys`.
  (def job
    (let [metadata (invokable/invokable-metadata #'demo.invokable/invokable-odd?)
          operation (invokable/invokable-operation (app-context) metadata)]
      (sf/invoke operation {:n 1})))


  (sf/poll-result job)

  (sf/job-status job)

  (http/get "https://api.ipify.org")

  (dep/topo-sort (-> (dep/graph)
                     (dep/depend "B" "A")
                     (dep/depend "C" "B")))


  (invokable/invoke #'demo.invokable/bad-increment (app-context) {})

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


  ;; -- Orchestration Demo
  (def make-orchestration-demo1
    (let [metadata (invokable/invokable-metadata #'demo.invokable/make-orchestration-demo1)]
      (invokable/register-invokable aladdin metadata)))

  (def make-orchestration-demo2
    (let [metadata (invokable/invokable-metadata #'demo.invokable/make-orchestration-demo2)]
      (invokable/register-invokable aladdin metadata)))

  (demo.invokable/make-orchestration-demo1 (app-context) {:n 10})
  (demo.invokable/make-orchestration-demo2 (app-context) {})


  ;; A very basic Orchestration example
  (let [orchestration {:id "Root"

                       :children
                       {"make-range" (sf/asset-id make-range)
                        "filter-odds" (sf/asset-id filter-odds)}

                       :edges
                       [{:source "make-range"
                         :target "filter-odds"
                         :ports [:range :numbers]}

                        {:source "filter-odds"
                         :target "Root"
                         :ports [:odds :n]}]}]
    (orchestration/execute (app-context) orchestration {}))

  ;; Nodes (Operations) with dependencies
  ;;     :a
  ;;    / |
  ;;  :b  |
  ;;    \ |
  ;;     :c
  (let [orchestration {:id "Root"

                       :children
                       {"make-range1" (sf/asset-id make-range)
                        "make-range2" (sf/asset-id make-range)
                        "concatenate" (sf/asset-id concatenate)}

                       :edges
                       [{:source "make-range1"
                         :target "concatenate"
                         :ports [:range :coll1]}

                        {:source "make-range2"
                         :target "concatenate"
                         :ports [:range :coll2]}

                        {:source "concatenate"
                         :target "Root"
                         :ports [:coll :coll]}]}]
    (orchestration/execute (app-context) orchestration))


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

  (gen/sample (s/gen :orchestration-invocation/completed) 1)
  (gen/sample (s/gen :orchestration-invocation/running) 1)

  (gen/sample (s/gen :orchestration-execution/process) 1)


  ;; Re-using the same Operation n times to connect to a different port
  (let [orchestration {:id "Root"

                       :children
                       {"make-range" (sf/asset-id make-range)
                        "concatenate" (sf/asset-id concatenate)}

                       :edges
                       [{:source "make-range"
                         :target "concatenate"
                         :ports [:range :coll1]}

                        {:source "make-range"
                         :target "concatenate"
                         :ports [:range :coll2]}

                        {:source "concatenate"
                         :target "Root"
                         :ports [:coll :coll]}]}]
    (orchestration/execute (app-context) orchestration))

  ;; TODO
  (let [orchestration {:id "Root"

                       :children {"Inc-n1" (sf/asset-id increment)}

                       :edges
                       [{:source "Root"
                         :target "Inc"
                         :ports [:n :n]}

                        {:source "Inc"
                         :target "Root"
                         :ports [:n :n]}]}]
    (orchestration/execute (app-context) orchestration {:n 10}))

  (let [orchestration {:id "Orchestration"

                       :children
                       {"Inc-n1" (sf/asset-id increment)
                        "Inc-n2" (sf/asset-id increment)}

                       :edges
                       [{:source "Orchestration"
                         :target "Inc-n1"
                         :ports [:n :n]}

                        {:source "Inc-n1"
                         :target "Inc-n2"
                         :ports [:n :n]}

                        {:source "Inc-n2"
                         :target "Orchestration"
                         :ports [:n :n]}]}]
    (orchestration/execute (app-context) orchestration {:n 10}))

  )

