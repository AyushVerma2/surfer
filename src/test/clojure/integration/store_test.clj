(ns integration.store-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [surfer.utils :as utils]
            [surfer.store :as store]
            [surfer.system :as system]
            [surfer.database :as database]))

(def test-system
  nil)

(defn system-fixture [f]
  (let [system (component/start
                 (system/new-system {:web-server
                                     {:port (utils/random-port)}

                                     :h2
                                     {:dbtype "h2:mem"
                                      :dbname "~/.surfer/surfer"}}))]

    (alter-var-root #'test-system (constantly system))

    (try
      (f)
      (finally
        (component/stop system)

        (alter-var-root #'test-system (constantly nil))))))

(use-fixtures :once system-fixture)

(deftest test-metadata-index
  (let [db (database/db (system/h2 test-system))]
    (is (= (into #{} (store/all-keys db))
           (into #{} (keys (store/metadata-index db)))))))
