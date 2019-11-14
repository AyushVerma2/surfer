(ns integration.store-test
  (:require [clojure.test :refer :all]
            [integration.fixture :as fixture]
            [surfer.store :as store]
            [surfer.system :as system]
            [surfer.database :as database]))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest test-metadata-index
  (let [db (database/db (system/h2 test-system))]
    (is (= (into #{} (store/all-keys db))
           (into #{} (keys (store/metadata-index db)))))))
