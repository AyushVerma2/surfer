(ns surfer.storage-test
  (:require 
    [surfer.storage :as storage])
  (:require [clojure.test :refer :all]
            [surfer.utils :as utils]))


(deftest ^:integration test-store
  (let [id "a5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
        bs (byte-array (range 10))]
    (storage/save id bs)
    (is (storage/load-stream id))
    (is (= (seq bs) (seq (utils/bytes-from-stream (storage/load-stream id)) )))))