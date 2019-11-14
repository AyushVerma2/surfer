(ns surfer.test.fixture
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [surfer.utils :as utils]
            [surfer.system :as system]))

(defn system-fixture [system-var & [config]]
  (fn [f]
    (let [system (component/start
                   (system/new-system (or config {:web-server
                                                  {:port (utils/random-port)}

                                                  :h2
                                                  {:dbtype "h2:mem"
                                                   :dbname "~/.surfer/surfer"}})))]

      (alter-var-root system-var (constantly system))

      (try
        (f)
        (finally
          (component/stop system)

          (alter-var-root system-var (constantly nil)))))))
