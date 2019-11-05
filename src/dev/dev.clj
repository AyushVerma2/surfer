(ns dev
  (:require [surfer.store :as store]
            [surfer.config :as config]
            [surfer.system :as system]
            [com.stuartsierra.component.repl :refer [set-init reset start stop system]]))

(set-init! #'systems/system)

(comment

  (defn get-time [_]
    {:time (data.json/json-str (str (Date.)))})

  (def aladdin
    (let [local-did config/DID
          local-ddo config/LOCAL-DDO
          local-ddo-string (sf/json-string-pprint local-ddo)]
      (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame")))

  (def get-time-operation
    (->> (sf/create-operation [] `get-time {:name "Get Time"
                                            :type "operation"
                                            :operation
                                            {:modes ["sync" "async"]
                                             :params {}
                                             :results {:time {:type "json"}}}
                                            :additionalInfo {:function "dev/get-time"}})
         (sf/register aladdin)))

  ;; Param keys *must be* a string
  ;; when calling the Java API directly.
  (def job (.invoke get-time-operation {"key" "value"}))

  ;; Param keys can be a keyword because
  ;; `starfish.core/invoke` uses `stringify-keys`.
  (def job (sf/invoke get-time-operation {:key "value"}))

  (sf/poll-result job)

  (sf/job-status job)

  (sf/invoke-result get-time-operation {})

  )

