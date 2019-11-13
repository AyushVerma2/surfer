(ns dev
  (:require [surfer.store :as store]
            [surfer.env :as env]
            [surfer.system :as system]
            [starfish.core :as sf]
            [clojure.data.json :as data.json]
            [clojure.java.jdbc :as jdbc]
            [clojure.repl :refer :all]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]))

(set-init (system/init-fn))

(defn db []
  (get-in system [:h2 :db-spec]))

(defn query [sql-params]
  (jdbc/query (db) sql-params ))

(comment

  ;; -- Migrations
  (query ["SELECT * FROM RAGTIME_MIGRATIONS"])

  ;; -- Tables
  (query ["SHOW TABLES"])

  ;; -- Metadata
  (query ["SELECT * FROM METADATA"])


  (defn get-time
    "Get current time"
    [param]
    {:time (data.json/json-str (str (java.util.Date.)))})

  (def aladdin
    (let [local-did (env/agent-did (system/env system))
          local-ddo (env/local-ddo (system/env system))
          local-ddo-string (sf/json-string-pprint local-ddo)]
      (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame")))

  (def get-time-operation
    (->> (sf/in-memory-operation (sf/invokable-metadata #'get-time))
         (sf/register aladdin)))

  ;; Param keys *must be* a string
  ;; when calling the Java API directly.
  (def job (.invoke get-time-operation {"param" "value"}))

  ;; Param keys can be a keyword because
  ;; `starfish.core/invoke` uses `stringify-keys`.
  (def job (sf/invoke get-time-operation {:param "value"}))

  (sf/poll-result job)

  (sf/job-status job)

  (sf/invoke-result get-time-operation {:param ""})

  )

