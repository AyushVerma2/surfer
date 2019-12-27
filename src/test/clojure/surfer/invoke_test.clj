(ns surfer.invoke-test
  (:require [clojure.test :refer :all]
            [surfer.invoke :as invoke]
            [surfer.test.fixture :as fixture]
            [surfer.demo.invokable :as demo.invokable]
            [surfer.system :as system]
            [starfish.core :as sf]
            [surfer.env :as env]
            [starfish.alpha :as sfa]
            [clojure.data.json :as data.json])
  (:import (sg.dex.starfish.impl.memory LocalResolverImpl)))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest wrapped-params-test
  (testing "Keywordize keys"
    (is (= {:x 1} (#'invoke/wrapped-params {:params {:x "json"}} {:x 1})))
    (is (= {:x 1} (#'invoke/wrapped-params {:params {:x "json"}} {"x" 1})))
    (is (= {:x 1} (#'invoke/wrapped-params {"params" {:x "json"}} {:x 1})))
    (is (= {:x 1} (#'invoke/wrapped-params {"params" {:x "json"}} {"x" 1}))))

  (testing "Asset Param"
    (binding [sfa/*resolver* (LocalResolverImpl.)]

      (sfa/register! (sf/did "did:dex:abc") (env/agent-ddo (system/env test-system)))

      (let [aladdin (sfa/did->agent (sf/did "did:dex:abc"))

            x 1

            asset (sf/upload aladdin (sf/memory-asset (data.json/write-str x)))

            pmeta {:params {:x "asset"}
                   :asset-params {:x {:reader #(data.json/read % :key-fn keyword)}}}

            params {:x {:did (str (sf/did asset))}}
            wrapped-params (#'invoke/wrapped-params pmeta params)]
        (is (= (:x params) (:x wrapped-params)))
        (is (= true (sf/asset? (get-in wrapped-params [:asset-params :x :asset]))))
        (is (= x (get-in wrapped-params [:asset-params :x :data])))))))

(deftest wrapped-results-test
  (testing "Keywordize keys"
    (is (= {:x 1} (#'invoke/wrapped-results {:results {:x "json"}} {:x 1})))
    (is (= {:x 1} (#'invoke/wrapped-results {:results {:x "json"}} {"x" 1})))
    (is (= {:x 1} (#'invoke/wrapped-results {"results" {"x" "json"}} {"x" 1})))
    (is (= {:x 1} (#'invoke/wrapped-results {} {:x 1}))))

  (testing "Generate Asset"
    (let [results (#'invoke/wrapped-results
                    {:results {:x "asset"}
                     :asset-results {:x {:asset-fn (comp sf/memory-asset data.json/write-str)}}}
                    {:x 1})]
      (is (= true (string? (get-in results [:x :did]))))
      (is (= true (sf/did? (sf/did (get-in results [:x :did]))))))))

(deftest invoke-test
  (testing "Invoke"
    (testing "Make range 0-9"
      (is (= {:range [0 1 2 3 4 5 6 7 8 9]} (invoke/invoke #'demo.invokable/make-range (system/new-context test-system) {}))))

    (testing "Make range 0-9, and return a new Asset (reference)"
      (let [results (invoke/invoke #'demo.invokable/make-range-asset (system/new-context test-system) {})
            did-str (get-in results [:range :did])]
        (is (= true (string? did-str)))
        (is (= true (sf/did? (sf/did did-str))))))

    (testing "Odd numbers"
      (is (= {:odds [1 3 5]} (invoke/invoke #'demo.invokable/filter-odds (system/new-context test-system) {:numbers [1 2 3 4 5]}))))

    (testing "Concatenate collections"
      (is (= {:coll [1 2 3 4]} (invoke/invoke #'demo.invokable/concatenate (system/new-context test-system) {:coll1 [1 2] :coll2 [3 4]}))))

    (testing "Number (Asset content) is odd"
      (binding [sfa/*resolver* (LocalResolverImpl.)]

        (sfa/register! fixture/agent-did (env/agent-ddo (system/env test-system)))

        (testing "Function call"
          (let [agent (fixture/agent)
                asset (sf/upload agent (sf/memory-asset (data.json/write-str 1)))
                invokable (invoke/wrap-params #'demo.invokable/n-odd? (meta #'demo.invokable/n-odd?))]
            (is (= {:is_odd true} (invokable (system/new-context test-system) {:n {:did (str (sf/did asset))}})))))

        ;;(testing "Invokable call"
        ;;  (let [aladdin (fixture/agent)
        ;;        asset (sf/upload aladdin (sf/memory-asset (data.json/write-str 1)))
        ;;        metadata (invoke/invokable-metadata #'demo.invokable/n-odd?)
        ;;        operation (invoke/invokable-operation (system/new-context test-system) metadata)]
        ;;    (try
        ;;      (is (= {:is_odd true} (sf/invoke-result operation {:n {:did (str (sf/did asset))}})))
        ;;      (catch ExecutionException ex
        ;;        (prn ex)))))

        ))))