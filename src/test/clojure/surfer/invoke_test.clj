(ns surfer.invoke-test
  (:require [clojure.test :refer :all]
            [surfer.invoke :as invoke]
            [surfer.test.fixture :as fixture]))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest wrapped-params-test
  (testing "Metadata and params keys must be keywords"
    (testing "Valid metadata & params"
      (is (= {:x 1} (#'invoke/wrapped-params {:operation {:params {:x "json"}}} {:x 1}))))

    (testing "Invalid params"
      (is (= {:x nil} (#'invoke/wrapped-params {:operation {:params {:x "json"}}} {"x" 1}))))

    (testing "Invalid metadata"
      (is (= {} (#'invoke/wrapped-params {"operation" {"params" {:x "json"}}} {:x 1}))))

    (testing "Invalid metadata & params"
      (is (= {} (#'invoke/wrapped-params {"operation" {"params" {:x "json"}}} {"x" 1}))))))