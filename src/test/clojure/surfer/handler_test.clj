(ns surfer.handler-test
  (:require
    [clojure.data.json :as json]
    [clojure.java.io :as io]
    [clojure.test :refer :all]
    [clj-http.client :as http]
    [surfer.utils :as utils]
    [surfer.system :as system]
    [surfer.env :as env]
    [surfer.test.fixture :as fixture]
    [surfer.demo.invokable-demo :as invokable-demo]
    [surfer.invokable :as invokable]
    [starfish.core :as sf]
    [slingshot.slingshot :refer [try+ throw+]]
    [byte-streams])
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
  (is (= 200 (:status (http/get (base-url) auth-headers)))))

(deftest invoke-errors-test
  (testing "Metadata not found"
    (try
      (http/post (str (base-url) "api/v1/invoke/sync/foo")
                 (merge auth-headers {:body (json/write-str {:n 1})}))
      (catch ExceptionInfo e
        (let [{:keys [status body]} (ex-data e)]
          (is (= 404 status))
          (is (= {:error "Metadata not found."} (json/read-str body :key-fn keyword)))))))


  (testing "Invalid type"
    (let [metadata-str (json/write-str {"name" "Foo"
                                        "contentHash" (-> (io/resource "testfile.txt")
                                                          (io/input-stream)
                                                          (byte-streams/to-byte-array)
                                                          (sf/digest))})

          meta-data-response (http/post (str (base-url) "api/v1/meta/data")
                                        (merge auth-headers {:body metadata-str}))

          generated-id (json/read-str (:body meta-data-response))]
      (try
        (http/post (str (base-url) "api/v1/invoke/sync/" generated-id)
                   (merge auth-headers {:body (json/write-str {:n 1})}))
        (catch ExceptionInfo e
          (let [{:keys [status body]} (ex-data e)]
            (is (= 400 status))
            (is (= {:error "Asset must be an Operation."} (json/read-str body :key-fn keyword)))))))))

(deftest invoke-sync-test
  (testing "Invoke Sync API"
    (testing "Operation - Increment n"
      (let [invokable-metadata (invokable/invokable-metadata #'invokable-demo/increment)

            meta-response (http/post (str (base-url) "api/v1/meta/data")
                                     (merge auth-headers {:body (json/write-str invokable-metadata)}))

            generated-id (json/read-str (:body meta-response))

            invoke-response (http/post (str (base-url) "api/v1/invoke/sync/" generated-id)
                                       (merge auth-headers {:body (json/write-str {:n 1})}))

            results (json/read-str (:body invoke-response) :key-fn keyword)]
        (is (= {:n 2} results))))

    (testing "Operation - Concatenate colls"
      (let [invokable-metadata (invokable/invokable-metadata #'invokable-demo/concatenate)

            meta-response (http/post (str (base-url) "api/v1/meta/data")
                                     (merge auth-headers {:body (json/write-str invokable-metadata)}))

            generated-id (json/read-str (:body meta-response))

            invoke-response (http/post (str (base-url) "api/v1/invoke/sync/" generated-id)
                                       (merge auth-headers {:body (json/write-str {:coll1 [1 2]
                                                                                   :coll2 [3 4]})}))

            results (json/read-str (:body invoke-response) :key-fn keyword)]
        (is (= {:coll [1 2 3 4]} results))))

    (testing "Orchestration - Demo 1"
      (let [invokable-metadata (invokable/invokable-metadata #'invokable-demo/make-orchestration-demo1)

            meta-response (http/post (str (base-url) "api/v1/meta/data")
                                     (merge auth-headers {:body (json/write-str invokable-metadata)}))

            generated-id (json/read-str (:body meta-response))

            invoke-response (http/post (str (base-url) "api/v1/invoke/sync/" generated-id)
                                       (merge auth-headers {:body (json/write-str {:n 3})}))

            results (json/read-str (:body invoke-response) :key-fn keyword)

            invoke-orchestration-response (http/post (str (base-url) "api/v1/invoke/sync/" (:id results))
                                                     (merge auth-headers {:body (json/write-str {:n 10})}))

            {:keys [status results children]} (json/read-str (:body invoke-orchestration-response) :key-fn keyword)]
        (is (= "succeeded" status))
        (is (= {:n 13} results))
        (is (= {:increment-0 {:status "succeeded" :results {:n 11}}
                :increment-1 {:status "succeeded" :results {:n 12}}
                :increment-2 {:status "succeeded" :results {:n 13}}}
               children))))))

(deftest ^:integration test-register-upload
  (let [metadata-str (json/write-str {"name" "test asset 1"
                                      "contentHash" (-> (io/resource "testfile.txt")
                                                        (io/input-stream)
                                                        (byte-streams/to-byte-array)
                                                        (sf/digest))})

        meta-data-response (http/post (str (base-url) "api/v1/meta/data")
                                      (merge auth-headers {:body metadata-str}))

        generated-id (json/read-str (:body meta-data-response))]

    (testing "Successful Asset Metadata Upload"
      (is (= 200 (:status meta-data-response))))

    (testing "Bad Asset Metadata Upload - Missing Content Hash"
      (let [ex (try
                 (http/post (str (base-url) "api/v1/meta/data")
                            (merge auth-headers {:body (json/write-str {:name "Foo"})}))
                 (catch ExceptionInfo ex
                   ex))]
        (is (= 400 (-> ex ex-data :status)))
        (is (= "Missing content hash." (-> ex ex-data :body)))))

    (testing "Valid Generated Asset ID"
      (is (utils/valid-asset-id? generated-id)))

    (testing "SHA256 of Metadata = Generated Asset ID"
      (is (= (utils/sha256 metadata-str) generated-id)))

    (testing "Get Asset Metadata"
      (let [response (http/get (str (base-url) "api/v1/meta/data/" generated-id) auth-headers)]
        (is (= 200 (:status response)))))

    (testing "Re-upload Asset Metadata"
      (let [response (http/put (str (base-url) "api/v1/meta/data/" generated-id)
                               (merge auth-headers {:body metadata-str}))]
        (is (= 200 (:status response)))))

    (testing "Bad Asset Metadata Request"
      (let [status-code (try
                          (http/put (str (base-url) "api/v1/meta/data/" generated-id)
                                    (merge auth-headers {:body (str metadata-str " some extra stuff")}))
                          (catch ExceptionInfo ex
                            (:status (ex-data ex))))]
        (is (= 400 status-code))))

    (testing "Good Upload: Metadata with Content Hash"
      (let [content (io/input-stream (io/resource "testfile.txt"))
            response (http/post (str (base-url) "api/v1/assets/" generated-id)
                                (merge auth-headers
                                       {:multipart [{:name "file"
                                                     :content content}]}))]
        (is (= 201 (:status response)))))

    (testing "Get Asset Data"
      (let [response (http/get (str (base-url) "api/v1/assets/" generated-id) auth-headers)]
        (is (= 200 (:status response)))
        (is (= "This is a test file" (:body response)))))))

(deftest ^:integration test-get-purchases
  (let [r1 (http/get (str (base-url) "api/v1/market/purchases")
                     (merge auth-headers
                            {:body nil}))
        result (json/read-str (:body r1))]
    (is (= 200 (:status r1)))))

(deftest ^:integration test-no-asset
  (is (try+
        (http/get (str (base-url) "api/v1/meta/data/"
                       "000011112222333344445556666777788889999aaaabbbbccccddddeeeeffff") auth-headers)
        (catch [:status 404] {:keys [request-time headers body]}
          ;; OK, not found expected
          true)))

  (is (try+
        (http/get (str (base-url) "api/v1/meta/data/"
                       "0000") auth-headers)
        (catch [:status 404] {:keys [request-time headers body]}
          ;; OK, not found expected
          true))))

