(ns surfer.test-invoke
  (:require [surfer test-handler])
  (:require [clojure.test :refer :all])
  (:require [starfish.core :as sf]
            [surfer.config :as config]
            [surfer.systems :refer [base-system PORT]])
  )

(def local-did
  config/DID)

(def base-url
  (str "http://localhost:" PORT))

(def local-ddo
  {:service 
    [{:type "Ocean.Invoke.v1"
      :serviceEndpoint (str base-url "/api/v1")}
     {:type "Ocean.Meta.v1"
      :serviceEndpoint (str base-url "/api/v1/meta")}
     {:type "Ocean.Auth.v1"
      :serviceEndpoint (str base-url "/api/v1/auth")}
     {:type "Ocean.Storage.v1"
      :serviceEndpoint (str base-url "/api/v1/assets")}]})

(def op-meta-map
  {:name "Test operation"
   :type "operation"
   :operation {:params {:input {:type "asset"}}
               :results {:ouput {:type "asset"}}}})

(def op1 (sf/memory-asset op-meta-map ""))

(def op-meta (.getMetadataString op1))

(def local-ddo-string (sf/json-string-pprint local-ddo))

(def ag (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame"))

(def op1 (sf/register ag op1))

