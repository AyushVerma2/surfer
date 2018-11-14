(ns surfer.test-handler
  (:require 
    [surfer systems]
    [clj-http.client :as client]
    [surfer.systems :refer [base-system]]
    [system.repl :refer [set-init! go start reset stop]])
  (:require [clojure.test :refer :all]))

;; ensure server is running
(let [system #'base-system]
    (set-init! system)
    (try 
      (start)
      (catch Throwable t))) 

(def BASE_URL "http://localhost:8080/")

(deftest test-welcome
  (is (= 200 (:status (client/get (str BASE_URL "/"))))))