(ns dev
  (:require [surfer.demo.invokable :as demo.invokable]
            [surfer.store :as store]
            [surfer.env :as env]
            [surfer.system :as system]
            [surfer.invoke :as invoke]
            [surfer.agent :as agent]
            [surfer.asset :as asset]
            [starfish.core :as sf]
            [clojure.data.json :as data.json]
            [clojure.java.jdbc :as jdbc]
            [clojure.repl :refer :all]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]
            [clojure.tools.logging :as log]
            [surfer.app-context :as app-context]
            [clojure.java.io :as io]
            [clojure.edn :as edn]
            [surfer.storage :as storage]
            [starfish.alpha :as sfa])
  (:import (sg.dex.starfish.util Utils)
           (sg.dex.starfish.impl.remote RemoteAccount RemoteAgent)
           (java.util HashMap)))

(set-init (system/init-fn))

;; ---

(defn db []
  (:db-spec (system/h2 system)))

(defn reset-db []
  (store/clear-db (db))
  (store/migrate-db! (db)))

(defn query [& sql-params]
  (jdbc/query (db) sql-params))

(defn env []
  (system/env system))

(defn app-context []
  (app-context/new-context (system/env system)
                           (system/h2 system)
                           (system/starfish system)))

(comment

  (reset-db)

  ;; -- Import Datasets
  (let [database (system/h2 system)
        storage-path (storage/storage-path (env/storage-config (env)))]
    (asset/import-datasets! database storage-path "datasets.edn"))

  (def default-resolver
    (let [{:starfish/keys [resolvers]} (system/starfish system)]
      (first resolvers)))

  (def did
    (agent/did (env/agent-config (system/env system))))

  (def ddo
    (agent/ddo (env/agent-config (system/env system))))

  (sfa/register! default-resolver did ddo)

  (def aladdin
    (let [^java.util.Map credentials (doto (new HashMap)
                                       (.put "username" "Aladdin")
                                       (.put "password" "OpenSesame"))

          account (RemoteAccount/create (Utils/createRandomHexString 32) credentials)]

      (RemoteAgent/create default-resolver did account)))


  (def n-asset
    ;; Data must be a JSON-encoded string
    (sf/upload aladdin (sf/memory-asset (data.json/write-str {:n 2}))))

  (def n-asset-did
    (sf/did n-asset))

  (sf/asset-id n-asset-did)

  (-> (sf/asset n-asset)
      (sf/content)
      (sf/to-string)
      (sf/read-json-string))


  ;; -- Resolver API
  (.getDDOString default-resolver did)
  (.getDDO default-resolver did)


  ;; -- Agent API
  (.getDID aladdin)
  (.getDDO aladdin)
  (.getEndpoint aladdin "Ocean.Meta.v1")
  (.getMetaEndpoint aladdin)



  ;; -- Invoke

  (let [operation (demo.invokable/memory-operation (app-context) #'demo.invokable/invokable-odd?)
        params {"n" 1}]
    (sf/invoke-result operation params))

  (let [operation (demo.invokable/memory-operation (app-context) #'demo.invokable/invokable-asset-odd?)
        params {"n" {"did" (str n-asset-did)}}]
    (sf/invoke-result operation params))

  (let [operation (demo.invokable/memory-operation (app-context) #'demo.invokable/invokable-asset-odd?2)
        params {"n" {"did" (str n-asset-did)}}]
    (sf/invoke-result operation params))

  (def operation-odd?
    (let [operation (demo.invokable/memory-operation (app-context) #'demo.invokable/invokable-odd?)]
      (sf/register aladdin operation)))

  ;; Param keys *must be* a string when calling the Java API directly.
  (def job (.invoke (demo.invokable/memory-operation (app-context) #'demo.invokable/invokable-odd?) {"n" 1}))

  ;; Param keys can be a keyword because `starfish.core/invoke` uses `stringify-keys`.
  (def job (sf/invoke (demo.invokable/memory-operation (app-context) #'demo.invokable/invokable-odd?) {:n 1}))

  (sf/poll-result job)

  (sf/job-status job)

  )

