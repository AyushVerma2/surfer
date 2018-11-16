(ns surfer.test-handler
  (:require 
    [surfer systems]
    [clj-http.client :as client]
    [surfer.systems :refer [base-system]]
    [system.repl :refer [set-init! go start reset stop]]
    [cemerick.friend [workflows :as workflows]
                     [credentials :as creds]])
  (:require [clojure.test :refer :all]))

;; ensure server is running
(let [system #'base-system]
    (set-init! system)
    (try 
      (start)
      (catch Throwable t))) 

(def BASE_URL "http://localhost:8080/")
(def AUTH_HEADERS {:headers {"Authorization", "Basic QWxhZGRpbjpPcGVuU2VzYW1l"}})

(deftest test-welcome
  (is (= 200 (:status (client/get (str BASE_URL) AUTH_HEADERS)))))

(deftest test-register-upload
  (let [r1 (client/post (str BASE_URL "api/v1/meta/data") AUTH_HEADERS)]
    (is (= 200 (:status r1))))
  )


