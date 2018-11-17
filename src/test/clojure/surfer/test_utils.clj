(ns surfer.test-utils
  (:require [clojure.test :refer :all])
  (:require [surfer.utils :refer :all]))

(deftest test-keccak256
  (is (= "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
         (hex-string (keccak256 ""))))
  
  (is (= "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"
         (hex-string (keccak256 "abc")))))

(deftest test-asset-ids
  (is (valid-asset-id? "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"))
  (is (not (valid-asset-id? "0123")))
  (is (not (valid-asset-id? "zzz2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")))
  (is (not (valid-asset-id? "AAA2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")))
  (is (not (valid-asset-id? 10))))

(deftest test-random-ids
  (is (not (= (new-random-id) (new-random-id))))
  (is (= 10 (count (new-random-id 10))))
  (is (= 0 (count (new-random-id 0))))
  (is (= 100 (count (new-random-id 100)))))

