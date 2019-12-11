(ns dev
  (:require [surfer.demo.invokable :as demo.invokable]
            [surfer.store :as store]
            [surfer.env :as env]
            [surfer.system :as system]
            [surfer.invoke :as invoke]
            [surfer.asset :as asset]
            [surfer.storage :as storage]
            [surfer.app-context :as app-context]
            [starfish.core :as sf]
            [starfish.alpha :as sfa]
            [clojure.data.json :as data.json]
            [clojure.java.jdbc :as jdbc]
            [clojure.repl :refer :all]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]
            [clj-http.client :as http])
  (:import (sg.dex.starfish.impl.memory LocalResolverImpl)))

(set-init (constantly (system/new-system :dev)))

;; ---

(defn env []
  (system/env system))

(defn app-context []
  (app-context/new-context (system/env system)
                           (system/database system)
                           (system/starfish system)))

(defn db []
  (:db-spec (system/database system)))

(defn reset-db []
  (store/clear-db (db) (env/dbtype (env)))
  (store/migrate-db! (db) (env/user-config (env))))

(defn query [& sql-params]
  (jdbc/query (db) sql-params))

(comment

  (reset-db)

  ;; -- Import Datasets
  (let [database (system/database system)
        storage-path (env/storage-path (env))]
    (asset/import-edn! (db) storage-path "datasets.edn"))

  (def did
    (env/agent-did (system/env system)))

  (def ddo
    (env/agent-ddo (system/env system)))

  ;; -- Resolver API
  (.getDDOString sfa/*resolver* did)
  (.getDDO sfa/*resolver* did)

  (def aladdin
    (sfa/did->agent did))


  ;; -- Dynamic *resolver*

  (binding [sfa/*resolver* (LocalResolverImpl.)]
    (.getDDOString sfa/*resolver* did))

  (binding [sfa/*resolver* (LocalResolverImpl.)]
    (sfa/did->agent did))

  ;; ---


  ;; -- Agent API
  (.getDID aladdin)
  (.getDDO aladdin)
  (.getEndpoint aladdin "Ocean.Meta.v1")
  (.getMetaEndpoint aladdin)

  (def n-asset
    ;; Data must be a JSON-encoded string
    (sf/upload aladdin (sf/memory-asset (data.json/write-str {:n 2}))))

  (def n-asset-did
    (sf/did n-asset))

  (sf/asset-id n-asset-did)


  ;; -- Invoke

  (let [metadata (demo.invokable/invokable-metadata #'demo.invokable/invokable-odd?)
        operation (demo.invokable/invokable-operation (app-context) metadata)
        params {"n" 1}]
    (sf/invoke-result operation params))

  (let [metadata (demo.invokable/invokable-metadata #'demo.invokable/invokable-odd?)
        operation (demo.invokable/invokable-operation (app-context) metadata)
        params {"n" 1}]
    (sf/invoke-sync operation params))

  (let [metadata (demo.invokable/invokable-metadata #'demo.invokable/invokable-asset-odd?)
        operation (demo.invokable/invokable-operation (app-context) metadata)
        params {"n" {"did" (str n-asset-did)}}]
    (sf/invoke-result operation params))

  (let [metadata (demo.invokable/invokable-metadata #'demo.invokable/invokable-asset-odd?2)
        operation (demo.invokable/invokable-operation (app-context) metadata)
        params {"n" {"did" (str n-asset-did)}}]
    (sf/invoke-result operation params))

  (def operation-odd?
    (let [metadata (demo.invokable/invokable-metadata #'demo.invokable/invokable-asset-odd?)
          operation (demo.invokable/invokable-operation (app-context) metadata)]
      (sf/register aladdin operation)))

  ;; Param keys *must be* a string when calling the Java API directly.
  (def job
    (let [metadata (demo.invokable/invokable-metadata #'demo.invokable/invokable-odd?)
          operation (demo.invokable/invokable-operation (app-context) metadata)]
      (.invoke operation {"n" 1})))

  ;; Param keys can be a keyword because `starfish.core/invoke` uses `stringify-keys`.
  (def job
    (let [metadata (demo.invokable/invokable-metadata #'demo.invokable/invokable-odd?)
          operation (demo.invokable/invokable-operation (app-context) metadata)]
      (sf/invoke operation {:n 1})))

  (sf/poll-result job)

  (sf/job-status job)

  (http/get "https://api.ipify.org")

  )

