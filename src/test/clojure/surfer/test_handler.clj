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
  (let [adata (json/write-str {"name" "test asset 1"})
        r1 (client/post (str BASE_URL "api/v1/meta/data") 
                        (merge AUTH_HEADERS
                               {:body adata}))
        id (json/read-str (:body r1))]
    (is (= 200 (:status r1)))
    (is (utils/valid-asset-id? id))
    (is (= (utils/hex-string (utils/keccak256 adata)) id))
   
    ;; test we can get the asset metadata
    (let [r2 (client/get (str BASE_URL "api/v1/meta/data/" id) AUTH_HEADERS)]
      (is (= 200 (:status r2))))
   
    ;; (println (str "Registered: " id))
    
    ;; test re-upload of identical metadata
    (let [r2a (client/put (str BASE_URL "api/v1/meta/data/" id) 
                         (merge AUTH_HEADERS
                               {:body adata}))]
      (is (= 200 (:status r2a))))
    
    ;; check validation failure with erroneous metadata
    (try+ 
      (client/put (str BASE_URL "api/v1/meta/data/" id) 
                         (merge AUTH_HEADERS
                               {:body (str adata " some extra stuff")}))
      (catch [:status 400] {:keys [request-time headers body]}
        ;; OK, should expect validation failue here
      ))
    
    ;; test upload
    (let [r3 (client/put (str BASE_URL "api/v1/assets/" id) 
                         (merge AUTH_HEADERS
                               {:body "This is my test data"}))]
      (is (= 201 (:status r3))))
    
    ;; test download 
    (let [r4 (client/get (str BASE_URL "api/v1/assets/" id) AUTH_HEADERS)]
      (is (= 200 (:status r4)))
      (is (= "This is my test data" (:body r4))))
    
    ;; test listing
    (let [ldata (json/write-str {:assetid id})
          r5 (client/post (str BASE_URL "api/v1/market/listings") 
                          (merge AUTH_HEADERS
                               {:body ldata}))])
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


