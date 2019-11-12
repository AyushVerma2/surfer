(ns surfer.utils-test
  (:require [clojure.test :refer :all]
            [surfer.utils :refer :all]))

(deftest test-sha256
  (is (= "a7ffc6f8bf1ed76651c14756a061d662f580ff4de43b49fa82d80a4b80f8434a"
         (sha256 ""))))

(deftest test-asset-ids
  (is (valid-asset-id? "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470"))
  (is (not (valid-asset-id? "0123")))
  (is (not (valid-asset-id? nil)))
  (is (not (valid-asset-id? "zzz2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")))
  (is (not (valid-asset-id? "AAA2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470")))
  (is (not (valid-asset-id? 10))))

(deftest test-random-ids
  (is (not (= (new-random-id) (new-random-id))))
  (is (= 10 (count (new-random-id 10))))
  (is (= 0 (count (new-random-id 0))))
  (is (= 100 (count (new-random-id 100)))))

(deftest test-remove-nils
  (is (= {:a :b} (remove-nil-values {:a :b, :c nil})))
  (is (= {:a :b nil :c} (remove-nil-values {:a :b, nil :c}))))

