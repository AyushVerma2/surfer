(ns surfer.startfish-test
  (:require [clojure.test :refer :all]
            [clojure.walk :refer [stringify-keys]]
            [com.stuartsierra.component :as component]
            [surfer.systems :as systems]
            [surfer.config :as config]
            [starfish.core :as sf]))

(defn test-function-1
  "Sample function to invoke"
  [inputs]
  (let [_ (println (str "function received:" inputs))
        a (sf/asset (:input inputs))
        ^String c (sf/to-string a)
        C (.toUpperCase c)]
    {:output (sf/memory-asset {:name "Result of computation"} C)}))

(deftest ^:integration test-startfish
  (let [system (component/start (systems/system))]
    (try
      (let [local-did config/DID
            local-ddo config/LOCAL-DDO
            local-ddo-string (sf/json-string-pprint local-ddo)

            username "Aladdin"
            password "OpenSesame"

            agent (sf/remote-agent local-did local-ddo-string username password)

            foo-memory-asset (sf/memory-asset "Foo")
            foo-remote-data-asset (sf/upload agent foo-memory-asset)]

        (is (= (sf/to-string (sf/content foo-memory-asset))
               (sf/to-string (sf/content foo-remote-data-asset))))

        (is (= (sf/metadata-string foo-memory-asset)
               (sf/metadata-string foo-remote-data-asset))))
      (finally
        (component/stop system)))))

(comment

  (def system (component/start (systems/system)))

  (component/stop system)

  (def local-agent-aladdin
    (let [local-did config/DID
          local-ddo config/LOCAL-DDO
          local-ddo-string (sf/json-string-pprint local-ddo)

          username "Aladdin"
          password "OpenSesame"]
      (sf/remote-agent local-did local-ddo-string username password)))

  (def asset
    (->> (sf/memory-asset "Foo")
         (sf/upload local-agent-aladdin)))

  (def operation
    (->> (sf/memory-asset {:name "Test operation"
                           :type "operation"
                           :operation {:modes ["sync" "async"]
                                       :params {:input {:type "asset"}}
                                       :results {:output {:type "asset"}}}
                           :additionalInfo {:function "surfer.startfish-test/test-function-1"}} "")
         (sf/register local-agent-aladdin)))

  (sf/invoke-sync operation (stringify-keys {:input asset})))