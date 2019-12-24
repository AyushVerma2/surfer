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

(deftest wrapped-results-test
  (testing "Metadata and Results keys must be keywords"
    (testing "Valid Metadata & Results"
      (is (= {:x 1} (#'invoke/wrapped-results {:operation {:results {:x "json"}}} {:x 1}))))

    (testing "Invalid Results"
      (is (= {"x" 1} (#'invoke/wrapped-results {:operation {:results {:x "json"}}} {"x" 1}))))

    (testing "Missing Metadata"
      (is (= {:x 1} (#'invoke/wrapped-results {} {:x 1}))))))