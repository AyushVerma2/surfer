(ns surfer.test-utils
  (:require [clojure.test :refer :all])
  (:require [surfer.utils :refer :all]))

(deftest test-keccak256
  (is (= "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"
         (hex-string (keccak256 "")))))