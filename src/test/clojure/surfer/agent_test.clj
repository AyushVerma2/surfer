(ns surfer.agent-test
  (:require [clojure.test :refer :all]
            [surfer.env :as env]
            [surfer.system :as system]
            [starfish.core :as sf]
            [clojure.string :as str]
            [surfer.test.fixture :as fixture]))

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

(deftest ^:integration agent-integration
  (let [agent-did (env/agent-did (system/env test-system))
        account (sf/remote-account "Aladdin" "OpenSesame")
        agent (sf/remote-agent agent-did account)]

    (let [foo-memory-asset (sf/memory-asset "Foo")
          foo-remote-data-asset (sf/upload agent foo-memory-asset)]
      (testing "Asset Content"
        (is (= (sf/to-string (sf/asset-content foo-memory-asset))
               (sf/to-string (sf/asset-content foo-remote-data-asset)))))

      (testing "Asset Metadata"
        (is (= (sf/asset-metadata-string foo-memory-asset)
               (sf/asset-metadata-string foo-remote-data-asset)))))

    (let [invokable-metadata (sf/invokable-metadata #'upper-case-text)]
      (testing "Invokable Operation Metadata"
        (is (= {:name "Convert text to upper case.",
                :type "operation",
                :additionalInfo {:function "surfer.agent-test/upper-case-text"},
                :operation {"modes" ["sync" "async"], "params" {"params" {"type" "json"}}}}
               (select-keys invokable-metadata [:name :type :additionalInfo :operation]))))

      (testing "Operation Registration"
        (is (sf/register agent (sf/memory-operation invokable-metadata))))

      (testing "Sync Invoke"
        (is (= {:text "HELLO"} (sf/invoke-sync (sf/memory-operation invokable-metadata) {:text "hello"}))))

      (testing "Sync Invoke Result"
        (is (= {:text "HELLO"} (sf/invoke-result (sf/memory-operation invokable-metadata) {:text "hello"}))))

      (testing "Sync Invoke - Make Asset"
        (let [{text-upper-case-asset :text} (-> (sf/invokable-metadata #'upper-case-text-new-asset)
                                                (sf/memory-operation)
                                                (sf/invoke-sync {:text "hello"}))]
          (is (= "HELLO" (sf/to-string text-upper-case-asset))))))))


(comment
  (= (sf/to-string (sf/memory-asset "Hello"))
     (sf/to-string (sf/asset (sf/memory-asset "Hello"))))

  (sf/memory-asset "Hello")

  (sf/asset (sf/memory-asset "Hello")))