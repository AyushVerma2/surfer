(ns surfer.storage-test
  (:require [clojure.test :refer :all]
            [surfer.utils :as utils]
            [surfer.storage :as storage]
            [surfer.test.fixture :as fixture]
            [surfer.system :as system]
            [surfer.env :as env])
  (:import (clojure.lang ExceptionInfo)))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest ^:integration test-store
  (let [storage-path (env/storage-path (system/env test-system))
        id "a5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
        data (byte-array (range 10))]

    (storage/save storage-path id data)

    (testing "Asset Path"
      (is (thrown-with-msg? ExceptionInfo #"Can't get asset path. Nil or empty storage path." (storage/get-asset-path nil nil)))
      (is (thrown-with-msg? ExceptionInfo #"Can't get asset path. Nil or empty storage path." (storage/get-asset-path "" "")))
      (is (thrown-with-msg? ExceptionInfo #"Can't get asset path. Nil or empty Asset ID." (storage/get-asset-path "~/.surfer/storage" "")))
      (is (thrown-with-msg? ExceptionInfo #"Can't get asset path. Invalid Asset ID." (storage/get-asset-path "~/.surfer/storage" "1234")))
      (is (= (str "~/.surfer/storage/" id ".ocb") (storage/get-asset-path "~/.surfer/storage" id))))

    (is (storage/asset-input-stream storage-path id))
    (is (= (seq data) (seq (utils/bytes-from-stream (storage/asset-input-stream storage-path id)))))))