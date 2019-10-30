(ns rr.invoke
  (:use [clojure.repl])
  (:require [clojure.test :refer :all])
  (:require [starfish.core :as sf]
            [surfer.config :as config]))

(def local-did
  config/DID)

(def local-ddo
  config/LOCAL-DDO)

(def local-ddo-string (sf/json-string-pprint local-ddo))

(def ag (sf/remote-agent local-did local-ddo-string "Aladdin" "OpenSesame"))

(defn test-function
  "Sample function to invoke with JSON"
  [inputs]
  (let [_ (println (str "function received:" inputs))
        a (:input inputs)
        ^String c (sf/to-string a)
        C (.toUpperCase c)]
    {:output C}))

(def op-meta-map
  {:name "Test RR operation"
   :type "operation"
   :operation {:modes ["sync", "async"]
               :params {:input {:type "json"}}
               :results {:output {:type "json"}}}
   :additionalInfo {:function "rr.invoke/test-function"}})

(defn setup-invoke 
  "Sets up a test operation"
  []
  (let [op (sf/register ag (sf/memory-asset op-meta-map ""))] 
    {:status "OK"
     :ops [(str op)]}))