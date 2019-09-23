(ns surfer.test-invoke
  (:require [surfer test-handler])
  (:require [clojure.test :refer :all])
  (:require [starfish.core :as sf]
            [surfer.config :as config]
            [surfer.systems :refer [base-system PORT]])
  )

(def local-did
  config/DID)

(def local-ddo
  config/LOCAL-DDO)

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

(sf/invoke-result op1 {:input (sf/memory-asset "Foo")} )