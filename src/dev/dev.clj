(ns dev
  (:require [surfer.store :as store]
            [surfer.config :as config]
            [surfer.system :as system]
            [starfish.core :as sf]
            [clojure.data.json :as data.json]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]))

(set-init (system/init-fn {:http-port 3030}))

(comment

  (defn get-time
    "Get current time"
    [param]
    {:time (data.json/json-str (str (java.util.Date.)))})

  (def aladdin
    (let [local-did config/DID
          local-ddo config/LOCAL-DDO
          local-ddo-string (sf/json-string-pprint local-ddo)]
      (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame")))

  (def get-time-operation
    (->> (sf/in-memory-operation (sf/invokable-metadata  #'get-time))
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

