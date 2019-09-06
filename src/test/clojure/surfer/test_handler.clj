(ns surfer.test-handler
  (:require
    [surfer systems]
    [clj-http.client :as client]
    [clojure.tools.logging :as log]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [surfer.utils :as utils :refer [port-available?]]
    [surfer.ckan :as ckan]
    [slingshot.slingshot :refer [try+ throw+]]
    [surfer.systems :refer [base-system PORT]]
    [system.repl :refer [set-init! go start reset stop]]
    [cemerick.friend [workflows :as workflows]
                     [credentials :as creds]]
    [clojure.test :refer :all])
  (:import [java.lang Throwable RuntimeException]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

;; ensure server is running
(let [system #'base-system]
  (set-init! system)
  (if-not
      (try
        (if (port-available? PORT)
          (do
            (start)
            true)
          (do
            (log/error "Unable to start surfer, port unavailable:" PORT)
            false)) ;; unable to start surfer
        (catch Throwable t
          (.printStackTrace t)
          false)) ;; unable to start surfer
    (throw (RuntimeException.
            (str "Unable to start surfer, port unavailable: " PORT)))))

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
    (is (= (utils/sha256 adata) id))

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
    (try+
      (let [content (io/input-stream (io/resource "testfile.txt"))
            r3 (client/post (str BASE_URL "api/v1/assets/" id)
                           (merge AUTH_HEADERS
                               {:multipart [{:name "file"
                                             :content content}]}))]
        (is (= 201 (:status r3))))
      (catch [:status 400] ex
        (binding [*out* *err*] (println ex))
        (is false)))

    ;; test download
    (let [r4 (client/get (str BASE_URL "api/v1/assets/" id) AUTH_HEADERS)]
      (is (= 200 (:status r4)))
      (is (= "This is a test file" (:body r4))))

    ;; test POST listing
    (let [ldata (json/write-str {:assetid id})
          r5 (client/post (str BASE_URL "api/v1/market/listings")
                          (merge AUTH_HEADERS
                               {:body ldata
                                :info {:title "Blah blah"}}))])
    )

    (try+
      (let [ldata (json/write-str {:assetid "this is not a valid asset id"})
            r5 (client/post (str BASE_URL "api/v1/market/listings")
                          (merge AUTH_HEADERS
                               {:body ldata}))])
      (catch [:status 400] ex
        ;; OK, should not work
        ))
  )

(deftest test-get-purchases
  (let [r1 (client/get (str BASE_URL "api/v1/market/purchases")
                        (merge AUTH_HEADERS
                               {:body nil}))
        result (json/read-str (:body r1))]
    (is (= 200 (:status r1)))))

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
