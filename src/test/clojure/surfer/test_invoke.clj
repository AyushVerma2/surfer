(ns surfer.test-invoke
  (:require [surfer test-handler])
  (:use [clojure.repl])
  (:require [clojure.test :refer :all])
  (:require [starfish.core :as sf]
            [surfer.config :as config]
            [surfer.systems :refer [base-system PORT]])
  )

(defn test-function
  "Sample function to invoke"
  [inputs]
  (let [_ (println (str "function received:" inputs))
        a (sf/asset (:input inputs))
        ^String c (sf/to-string a)
        C (.toUpperCase c)]
    {:output (sf/memory-asset {:name "Result of computation"} C)}))

(defn test-function-2
  "Sample function to invoke with JSON"
  [inputs]
  (let [_ (println (str "function received:" inputs))
        a (:input inputs)
        ^String c (sf/to-string a)
        C (.toUpperCase c)]
    {:output C}))

(def local-did
  config/DID)

(def local-ddo
  config/LOCAL-DDO)

(def op-meta-map
  {:name "Test operation"
   :type "operation"
   :operation {:params {:input {:type "json"}}
               :results {:output {:type "json"}}}
   :additionalInfo {:function "surfer.test-invoke/test-function-2"}})

(def op1 (sf/memory-asset op-meta-map ""))

(def op-meta (.getMetadataString op1))

(def local-ddo-string (sf/json-string-pprint local-ddo))

(def ag (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame"))

(def op1 (sf/register ag op1))

(defn demo []
  (def j1 (sf/invoke op1 {:input (sf/memory-asset "Foo")} ))
  
  )