(ns integration.agent-test
  (:require [clojure.test :refer :all]
            [surfer.env :as env]
            [surfer.system :as system]
            [starfish.core :as sf]
            [clojure.string :as str]
            [integration.fixture :as fixture]))

(def test-system
  nil)

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(defn upper-case-text
  "Convert text to upper case."
  [params]
  (update params :text str/upper-case))

(defn upper-case-text-new-asset
  "Convert text to upper case."
  [params]
  (update params :text (comp sf/memory-asset str/upper-case)))

(defn upper-case-asset-text
  "Convert asset's text content to upper case."
  [params]
  (update params :text str/upper-case))

(deftest ^:integration agent-integration
  (let [local-did (env/agent-did (system/env test-system))
        local-ddo (env/local-ddo (system/env test-system))
        local-ddo-string (sf/json-string-pprint local-ddo)

        username "Aladdin"
        password "OpenSesame"

        agent (sf/remote-agent local-did local-ddo-string username password)]

    (let [foo-memory-asset (sf/memory-asset "Foo")
          foo-remote-data-asset (sf/upload agent foo-memory-asset)]
      (testing "Asset Content"
        (is (= (sf/to-string (sf/content foo-memory-asset))
               (sf/to-string (sf/content foo-remote-data-asset)))))

      (testing "Asset Metadata"
        (is (= (sf/metadata-string foo-memory-asset)
               (sf/metadata-string foo-remote-data-asset)))))

    (let [invokable-metadata (sf/invokable-metadata #'upper-case-text)]
      (testing "Invokable Operation Metadata"
        (is (= {:name "Convert text to upper case.",
                :type "operation",
                :additionalInfo {:function "integration.agent-test/upper-case-text"},
                :operation {"modes" ["sync" "async"], "params" {"params" {"type" "json"}}}}
               (select-keys invokable-metadata [:name :type :additionalInfo :operation]))))

      (testing "Operation Registration"
        (is (sf/register agent (sf/in-memory-operation invokable-metadata))))

      (testing "Sync Invoke"
        (is (= {:text "HELLO"} (sf/invoke-sync (sf/in-memory-operation invokable-metadata) {:text "hello"}))))

      (testing "Sync Invoke Result"
        (is (= {:text "HELLO"} (sf/invoke-result (sf/in-memory-operation invokable-metadata) {:text "hello"}))))

      (testing "Sync Invoke - Make Asset"
        (let [{text-upper-case-asset :text} (-> (sf/invokable-metadata #'upper-case-text-new-asset)
                                                (sf/in-memory-operation)
                                                (sf/invoke-sync {:text "hello"}))]
          (is (= "HELLO" (sf/to-string text-upper-case-asset))))))))


(comment
  (= (sf/to-string (sf/memory-asset "Hello"))
     (sf/to-string (sf/asset (sf/memory-asset "Hello"))))

  (sf/memory-asset "Hello")

  (sf/asset (sf/memory-asset "Hello")))