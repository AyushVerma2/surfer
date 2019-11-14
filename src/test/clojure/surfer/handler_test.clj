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
    [integration.fixture :as fixture]))

(set! *warn-on-reflection* true)
(set! *unchecked-math* :warn-on-boxed)

(def test-system
  nil)

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(def AUTH_HEADERS
  {:headers {"Authorization", "Basic QWxhZGRpbjpPcGVuU2VzYW1l"}})

(defn base-url []
  (str "http://localhost:" (env/web-server-config (system/env test-system) [:port]) "/"))

(deftest ^:integration test-welcome
  (is (= 200 (:status (client/get (base-url) AUTH_HEADERS)))))

(deftest ^:integration test-register-upload
  (let [adata (json/write-str {"name" "test asset 1"})
        r1 (client/post (str (base-url) "api/v1/meta/data")
                        (merge AUTH_HEADERS
                               {:body adata}))
        id (json/read-str (:body r1))]
    (is (= 200 (:status r1)))
    (is (utils/valid-asset-id? id))
    (is (= (utils/sha256 adata) id))

    ;; test we can get the asset metadata
    (let [r2 (client/get (str (base-url) "api/v1/meta/data/" id) AUTH_HEADERS)]
      (is (= 200 (:status r2))))

    ;; (println (str "Registered: " id))

    ;; test re-upload of identical metadata
    (let [r2a (client/put (str (base-url) "api/v1/meta/data/" id)
                          (merge AUTH_HEADERS
                                 {:body adata}))]
      (is (= 200 (:status r2a))))

    ;; check validation failure with erroneous metadata
    (is (try+
          (client/put (str (base-url) "api/v1/meta/data/" id)
                      (merge AUTH_HEADERS
                             {:body (str adata " some extra stuff")}))
          (catch [:status 400] {:keys [request-time headers body]}
            ;; OK, should expect validation failue here
            true)))

    ;; test upload
    (try+
      (let [content (io/input-stream (io/resource "testfile.txt"))
            r3 (client/post (str (base-url) "api/v1/assets/" id)
                            (merge AUTH_HEADERS
                                   {:multipart [{:name "file"
                                                 :content content}]}))]
        (is (= 201 (:status r3))))
      (catch [:status 400] ex
        (binding [*out* *err*] (println ex))
        (is false)))

    ;; test download
    (let [r4 (client/get (str (base-url) "api/v1/assets/" id) AUTH_HEADERS)]
      (is (= 200 (:status r4)))
      (is (= "This is a test file" (:body r4))))

    ;; test POST listing
    (let [ldata (json/write-str {:assetid id})
          r5 (client/post (str (base-url) "api/v1/market/listings")
                          (merge AUTH_HEADERS
                                 {:body ldata
                                  :info {:title "Blah blah"}}))])))

(deftest ^:integration test-get-purchases
  (let [r1 (client/get (str (base-url) "api/v1/market/purchases")
                       (merge AUTH_HEADERS
                              {:body nil}))
        result (json/read-str (:body r1))]
    (is (= 200 (:status r1)))))

(deftest ^:integration test-no-asset
  (is (try+
        (client/get (str (base-url) "api/v1/meta/data/"
                         "000011112222333344445556666777788889999aaaabbbbccccddddeeeeffff") AUTH_HEADERS)
        (catch [:status 404] {:keys [request-time headers body]}
          ;; OK, not found expected
          true)))

  (is (try+
        (client/get (str (base-url) "api/v1/meta/data/"
                         "0000") AUTH_HEADERS)
        (catch [:status 404] {:keys [request-time headers body]}
          ;; OK, not found expected
          true))))

