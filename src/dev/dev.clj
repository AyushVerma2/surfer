(ns dev
  (:require [surfer.demo.invokable :as demo.invokable]
            [surfer.store :as store]
            [surfer.env :as env]
            [surfer.system :as system]
            [surfer.invoke :as invoke]
            [surfer.agent :as agent]
            [starfish.core :as sf]
            [clojure.data.json :as data.json]
            [clojure.java.jdbc :as jdbc]
            [clojure.repl :refer :all]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]
            [clojure.tools.logging :as log])
  (:import (sg.dex.starfish.util Utils)
           (sg.dex.starfish.impl.remote RemoteAccount RemoteAgent)))

(set-init (system/init-fn))

(defn db []
  (get-in system [:h2 :db-spec]))

(defn query [sql-params]
  (jdbc/query (db) sql-params))

(comment

  (store/truncate (db))

  (def aladdin
    (let [env (system/env system)

          did (agent/parse-did (env/agent-config env [:did]))
          ddo (agent/ddo did (env/agent-config env [:remote-url]))

          account (RemoteAccount/create (Utils/createRandomHexString 32)
                                        (doto (new java.util.HashMap)
                                          (.put "username" "Aladdin")
                                          (.put "password" "OpenSesame")))]

      (.registerDID sf/*resolver* did (data.json/write-str ddo))

      (RemoteAgent/create sf/*resolver* did account)))


  (def n-asset
    ;; Data must be a JSON-encoded string
    (sf/upload aladdin (sf/memory-asset (data.json/write-str {:n 1}))))

  (def n-asset-did
    (sf/did n-asset))

  (-> (sf/asset n-asset)
      (sf/content)
      (sf/to-string)
      (sf/read-json-string))


  ;; -- Resolver API
  (.getDDOString sf/*resolver* n-asset-did)
  (.getDDO sf/*resolver* n-asset-did)


  ;; -- Agent API
  (.getDID aladdin)
  (.getDDO aladdin)
  (.getEndpoint aladdin "Ocean.Meta.v1")
  (.getMetaEndpoint aladdin)



  ;; -- Invoke

  (def operation-odd?
    (sf/register aladdin demo.invokable/operation-asset-odd?))

  (sf/invoke-result demo.invokable/operation-asset-odd? {"n" {"did" (str n-asset-did)}})


  ;; Param keys *must be* a string
  ;; when calling the Java API directly.
  (def job (.invoke demo.invokable/operation-odd? {"n" 1}))

  ;; Param keys can be a keyword because
  ;; `starfish.core/invoke` uses `stringify-keys`.
  (def job (sf/invoke demo.invokable/operation-odd? {"n" 1}))

  (sf/poll-result job)

  (sf/job-status job)

  (sf/invoke-result demo.invokable/operation-odd? {"n" 1})
  (sf/invoke-result demo.invokable/operation-inc {"n" 1})

  )

