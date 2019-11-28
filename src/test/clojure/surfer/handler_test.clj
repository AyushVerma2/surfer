(ns surfer.handler-test
  (:require
    [clj-http.client :as client]
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [surfer.utils :as utils]
    [surfer.system :as system]
    [slingshot.slingshot :refer [try+ throw+]]
    [clojure.test :refer :all]
    [surfer.env :as env]
    [surfer.test.fixture :as fixture]
    [starfish.core :as sf]
    [byte-streams]
    [clojure.tools.logging :as log])
  (:import (clojure.lang ExceptionInfo)))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(def auth-headers
  {:headers {"Authorization", "Basic QWxhZGRpbjpPcGVuU2VzYW1l"}})

(defn base-url []
  (str "http://localhost:" (env/web-server-config (system/env test-system) [:port]) "/"))

(deftest ^:integration test-welcome
  (is (= 200 (:status (client/get (base-url) auth-headers)))))

(deftest ^:integration test-register-upload
  (let [metadata-str (json/write-str {"name" "test asset 1"
                                      "contentHash" (-> (io/resource "testfile.txt")
                                                        (io/input-stream)
                                                        (byte-streams/to-byte-array)
                                                        (sf/digest))})

        meta-data-response (client/post (str (base-url) "api/v1/meta/data")
                                        (merge auth-headers {:body metadata-str}))

        generated-id (json/read-str (:body meta-data-response))]

    (testing "Successful Asset Metadata Upload"
      (is (= 200 (:status meta-data-response))))

    (testing "Valid Generated Asset ID"
      (is (utils/valid-asset-id? generated-id)))

    (testing "SHA256 of Metadata = Generated Asset ID"
      (is (= (utils/sha256 metadata-str) generated-id)))

    (testing "Get Asset Metadata"
      (let [response (client/get (str (base-url) "api/v1/meta/data/" generated-id) auth-headers)]
        (is (= 200 (:status response)))))

    (testing "Re-upload Asset Metadata"
      (let [response (client/put (str (base-url) "api/v1/meta/data/" generated-id)
                                 (merge auth-headers {:body metadata-str}))]
        (is (= 200 (:status response)))))

    (testing "Bad Asset Metadata Request"
      (let [status-code (try
                          (client/put (str (base-url) "api/v1/meta/data/" generated-id)
                                      (merge auth-headers {:body (str metadata-str " some extra stuff")}))
                          (catch ExceptionInfo ex
                            (log/debug ex (ex-message ex) (ex-data ex))
                            (:status (ex-data ex))))]
        (is (= 400 status-code))))

    (testing "Upload Asset Data"
      (let [content (io/input-stream (io/resource "testfile.txt"))
            response (client/post (str (base-url) "api/v1/assets/" generated-id)
                                  (merge auth-headers
                                         {:multipart [{:name "file"
                                                       :content content}]}))]
        (is (= 201 (:status response)))))

    (testing "Get Asset Data"
      (let [response (client/get (str (base-url) "api/v1/assets/" generated-id) auth-headers)]
        (is (= 200 (:status response)))
        (is (= "This is a test file" (:body response)))))))

(deftest ^:integration test-get-purchases
  (let [r1 (client/get (str (base-url) "api/v1/market/purchases")
                       (merge auth-headers
                              {:body nil}))
        result (json/read-str (:body r1))]
    (is (= 200 (:status r1)))))

(deftest ^:integration test-no-asset
  (is (try+
        (client/get (str (base-url) "api/v1/meta/data/"
                         "000011112222333344445556666777788889999aaaabbbbccccddddeeeeffff") auth-headers)
        (catch [:status 404] {:keys [request-time headers body]}
          ;; OK, not found expected
          true)))

  (is (try+
        (client/get (str (base-url) "api/v1/meta/data/"
                         "0000") auth-headers)
        (catch [:status 404] {:keys [request-time headers body]}
          ;; OK, not found expected
          true))))

