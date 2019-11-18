(ns dev
  (:require [surfer.demo.invokable :as demo.invokable]
            [surfer.store :as store]
            [surfer.env :as env]
            [surfer.system :as system]
            [surfer.invoke :as invoke]
            [starfish.core :as sf]
            [clojure.data.json :as data.json]
            [clojure.java.jdbc :as jdbc]
            [clojure.repl :refer :all]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]
            [clojure.tools.logging :as log]))

(set-init (system/init-fn))

(defn db []
  (get-in system [:h2 :db-spec]))

(defn query [sql-params]
  (jdbc/query (db) sql-params))

(comment

  (store/truncate (db))

  (def aladdin
    (let [local-did (env/agent-did (system/env system))
          local-ddo (env/local-ddo (system/env system))
          local-ddo-string (sf/json-string-pprint local-ddo)]
      (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame")))

  demo.invokable/invokable-odd?-metadata

  (def operation-odd?
    (sf/register aladdin demo.invokable/operation-odd?))

  (sf/asset-id operation-odd?)


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

