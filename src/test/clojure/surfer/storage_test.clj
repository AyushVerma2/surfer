(ns surfer.storage-test
  (:require [clojure.test :refer :all]
            [surfer.utils :as utils]
            [surfer.storage :as storage]
            [surfer.test.fixture :as fixture]
            [surfer.system :as system]))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest ^:integration test-store
  (let [storage-path (storage/storage-path (system/env test-system))
        id "a5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
        data (byte-array (range 10))]

    (storage/save storage-path id data)

    (is (storage/load-stream storage-path id))
    (is (= (seq data) (seq (utils/bytes-from-stream (storage/load-stream storage-path id)))))))