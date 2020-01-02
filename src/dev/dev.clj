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
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]
            [clj-http.client :as http]
            [com.stuartsierra.dependency :as dep]
            [surfer.database :as database]))

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

  (def orchestration1
    (let [metadata (invokable/invokable-metadata #'demo.invokable/orchestration1)]
      (invokable/register-invokable aladdin metadata)))

  (def orchestration2
    (let [metadata (invokable/invokable-metadata #'demo.invokable/orchestration2)]
      (invokable/register-invokable aladdin metadata)))

  (def basic-orchestration
    (let [metadata {:name "Basic Orchestration"
                    :type "operation"
                    :dateCreated (str (java.util.Date.))
                    :operation {:modes ["sync"]
                                :class "orchestration"
                                :params {}
                                :results {:results "json"}}}

          metadata-str (data.json/write-str metadata)

          data {:children
                {"make-range" (sf/asset-id make-range)
                 "filter-odds" (sf/asset-id filter-odds)}

                :edges
                [{:source "make-range"
                  :target "filter-odds"
                  :ports [:range :numbers]}]}

          data-str (data.json/write-str data)

          asset (sf/register aladdin (sf/memory-asset metadata-str data-str))]

      ;; FIXME Figure out why it isn't uploading the data (is it because it's an Operation?)
      (storage/save (env/storage-path (env)) (sf/asset-id asset) (.getBytes data-str))

      asset))

  ;; A very basic Orchestration example
  (let [orchestration {:children
                       {"make-range" (sf/asset-id make-range)
                        "filter-odds" (sf/asset-id filter-odds)}

                       :edges
                       [{:source "make-range"
                         :target "filter-odds"
                         :ports [:range :numbers]}]}]
    (orchestration/execute (app-context) orchestration))

  ;; Nodes (Operations) with dependencies
  ;;     :a
  ;;    / |
  ;;  :b  |
  ;;    \ |
  ;;     :c
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
    (orchestration/execute (app-context) orchestration))

  ;; Re-using the same Operation n times to connect to a different port
  (let [orchestration {:children
                       {"make-range" (sf/asset-id make-range)
                        "concatenate" (sf/asset-id concatenate)}

                       :edges
                       [{:source "make-range"
                         :target "concatenate"
                         :ports [:range :coll1]}

                        {:source "make-range"
                         :target "concatenate"
                         :ports [:range :coll2]}]}]
    (orchestration/execute (app-context) orchestration))


  (def orchestration
    {:id "Root"
     :children
     [{:id "A"
       :did (sf/random-did-string)}

      {:id "B"
       :did (sf/random-did-string)}

      {:id "C"
       :did (sf/random-did-string)}]

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
       :target "Root"}]})

  (def g
    (orchestration/dependency-graph {:children
                                     {}

                                     :edges
                                     [{:source "make-range1"
                                       :target "concatenate"
                                       :ports [:range :coll1]}

                                      [:source "make-range2"
                                       :target "concatenate"
                                       :ports [:range :coll2]]]}))

  (dep/topo-sort g)

  )

