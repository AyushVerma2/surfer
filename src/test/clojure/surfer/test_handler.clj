(ns surfer.test-handler
  (:require 
    [surfer systems]
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [surfer.utils :as utils]
    [slingshot.slingshot :refer [try+ throw+]]
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
  (let [r1 (client/post (str BASE_URL "api/v1/meta/data") AUTH_HEADERS)
        id (json/read-str (:body r1))]
    (is (= 200 (:status r1)))
    (is (utils/valid-asset-id? id))
    (let [r2 (client/get (str BASE_URL "api/v1/meta/data/" id) AUTH_HEADERS)]
      (is (= 200 (:status r1)))
      (println r2)
      
      )
    ))

(deftest test-no-asset 
  (try+ 
      (client/get (str BASE_URL "api/v1/meta/data/" 
                       "000011112222333344445556666777788889999aaaabbbbccccddddeeeeffff") AUTH_HEADERS)
      (catch [:status 404] {:keys [request-time headers body]}
        ;; OK, not found expected
        )
      )
  (try+ 
      (client/get (str BASE_URL "api/v1/meta/data/" 
                       "0000") AUTH_HEADERS)
      (catch [:status 404] {:keys [request-time headers body]}
        ;; OK, not found expected
        )
      ))


