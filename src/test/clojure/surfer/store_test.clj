(ns surfer.store-test
  (:require [clojure.test :refer :all]
            [surfer.store :as store]
            [surfer.system :as system]
            [surfer.database :as database]
            [surfer.test.fixture :as fixture]
            [starfish.core :as sf]))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest test-metadata-index
  (testing "Metadata Index Keys"
    (let [db (database/db (system/database test-system))]
      (is (= (into #{} (store/all-keys db))
             (into #{} (keys (store/metadata-index db))))))))

(deftest test-content-hash
  (testing "Memory Asset Content Hash"
    (let [data "Hello"
          sha (sf/digest data)
          asset (sf/memory-asset {:contentHash sha} data)]
      (is (= sha (sf/digest asset))))))