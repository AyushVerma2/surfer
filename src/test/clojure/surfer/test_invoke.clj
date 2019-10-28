(ns surfer.test-invoke
  (:require [surfer test-handler])
  (:use [clojure.repl])
  (:require [clojure.test :refer :all])
  (:require [starfish.core :as sf]
            [surfer.config :as config]
            [surfer.systems :refer [base-system PORT]])
  )


(def local-did
  config/DID)

(def local-ddo
  config/LOCAL-DDO)

(def local-ddo-string (sf/json-string-pprint local-ddo))

(def ag (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame"))


(defn test-function-1
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


(def op-meta-map-1
  {:name "Test operation"
   :type "operation"
   :operation {:params {:input {:type "asset"}}
               :results {:output {:type "asset"}}}
   :additionalInfo {:function "surfer.test-invoke/test-function-1"}})

(def op-meta-map-2
  {:name "Test operation"
   :type "operation"
   :operation {:params {:input {:type "json"}}
               :results {:output {:type "json"}}}
   :additionalInfo {:function "surfer.test-invoke/test-function-2"}})

(def op1 (sf/memory-asset op-meta-map-1 ""))
(def op2 (sf/memory-asset op-meta-map-1 ""))
(def op1 (sf/register ag op1))
(def op2 (sf/register ag op2))

(def op-meta (.getMetadataString op1))

(def a1 (sf/memory-asset "Foo"))
(def a1 (sf/upload ag a1))


(defn demo []
  (def j1 (sf/invoke op1 {:input a1} ))
  (sf/job-status j1)
  )