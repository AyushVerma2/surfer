(ns surfer.invoke-test
  (:require [clojure.test :refer :all]
            [surfer.invoke :as invoke]
            [surfer.test.fixture :as fixture]
            [surfer.demo.invokable-demo :as invokable-demo]
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

  (testing "Asset Params"
    (binding [sfa/*resolver* (LocalResolverImpl.)]

      (sfa/register! fixture/test-agent-did (env/self-ddo (system/env test-system)))

      (let [coll1 [1 2 3]
            coll2 [4 5 6]

            coll1-asset (sf/upload (fixture/test-agent) (sf/memory-asset (data.json/write-str coll1)))
            coll2-asset (sf/upload (fixture/test-agent) (sf/memory-asset (data.json/write-str coll2)))

            params {:coll1 {:did (str (sf/did coll1-asset))}
                    :coll2 {:did (str (sf/did coll2-asset))}}

            wrapped-params (#'invoke/wrapped-params (meta #'invokable-demo/concatenate-asset) params)]

        (testing "Params don't change"
          (is (= (:coll1 params) (:coll1 wrapped-params)))
          (is (= (:coll2 params) (:coll2 wrapped-params))))

        (testing "Asset (DID) param to Asset"
          (is (= true (sf/asset? (get-in wrapped-params [:asset-params :coll1 :asset]))))
          (is (= true (sf/asset? (get-in wrapped-params [:asset-params :coll2 :asset])))))

        (testing "Asset (DID) param to data"
          (is (= [1 2 3] (get-in wrapped-params [:asset-params :coll1 :data])))
          (is (= [4 5 6] (get-in wrapped-params [:asset-params :coll2 :data]))))))))

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
      (is (= {:range [0 1 2 3 4 5 6 7 8 9]} (invoke/invoke #'invokable-demo/make-range (system/app-context test-system) {}))))

    (testing "Make range 0-9, and return a new Asset (reference)"
      (let [results (invoke/invoke #'invokable-demo/make-range-asset (system/app-context test-system) {})
            did-str (get-in results [:range :did])]
        (is (= true (string? did-str)))
        (is (= true (sf/did? (sf/did did-str))))))

    (testing "Odd numbers"
      (is (= {:odds [1 3 5]} (invoke/invoke #'invokable-demo/filter-odds (system/app-context test-system) {:numbers [1 2 3 4 5]}))))

    (testing "Concatenate collections"
      (is (= {:coll [1 2 3 4]} (invoke/invoke #'invokable-demo/concatenate (system/app-context test-system) {:coll1 [1 2] :coll2 [3 4]}))))

    (testing "Number (Asset content) is odd"
      (binding [sfa/*resolver* (LocalResolverImpl.)]

        (sfa/register! fixture/test-agent-did (env/self-ddo (system/env test-system)))

        (testing "Function call"
          (let [agent (fixture/test-agent)
                asset (sf/upload agent (sf/memory-asset (data.json/write-str 1)))
                invokable (invoke/wrap-params #'invokable-demo/n-odd? (meta #'invokable-demo/n-odd?))]
            (is (= {:is_odd true} (invokable (system/app-context test-system) {:n {:did (str (sf/did asset))}})))))

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