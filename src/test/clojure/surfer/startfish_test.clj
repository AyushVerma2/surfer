(ns surfer.startfish-test
  (:require [clojure.test :refer :all]
            [com.stuartsierra.component :as component]
            [surfer.systems :as systems]
            [surfer.config :as config]
            [starfish.core :as sf]))

(deftest ^:integration test-startfish
  (let [system (component/start-system (systems/base-system))

        local-did config/DID
        local-ddo config/LOCAL-DDO
        local-ddo-string (sf/json-string-pprint local-ddo)

        username "Aladdin"
        password "OpenSesame"

        remote-agent (sf/remote-agent local-did local-ddo-string username password)

        memory-asset (sf/memory-asset "Foo")
        remote-data-asset (sf/upload remote-agent memory-asset)]

    (is (= (sf/to-string (sf/content memory-asset))
           (sf/to-string (sf/content remote-data-asset))))

    (component/stop-system system)))
