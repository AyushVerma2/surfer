(ns rr.invoke
  (:use [clojure.repl])
  (:require [clojure.test :refer :all])
  (:require [starfish.core :as sf]))

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
   :operation {:params {:input {:type "json"}}
               :results {:output {:type "json"}}}
   :additionalInfo {:function "rr.invoke/test-function"}})

(defn setup-invoke 
  "Sets up a test operation"
  []
  (let [] 
    {:status "OK"}))