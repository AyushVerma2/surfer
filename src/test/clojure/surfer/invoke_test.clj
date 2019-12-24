(ns surfer.invoke-test
  (:require [clojure.test :refer :all]
            [surfer.invoke :as invoke]
            [surfer.test.fixture :as fixture]
            [surfer.demo.invokable :as demo.invokable]
            [surfer.system :as system]
            [starfish.core :as sf]
            [surfer.env :as env]
            [starfish.alpha :as sfa])
  (:import (sg.dex.starfish.impl.memory LocalResolverImpl)))

(def test-system
  nil)

(use-fixtures :once (fixture/system-fixture #'test-system))

(deftest wrapped-params-test
  (is (= {:x 1} (#'invoke/wrapped-params {:operation {:params {:x "json"}}} {:x 1})))
  (is (= {:x 1} (#'invoke/wrapped-params {:operation {:params {:x "json"}}} {"x" 1})))
  (is (= {:x 1} (#'invoke/wrapped-params {"operation" {"params" {:x "json"}}} {:x 1})))
  (is (= {:x 1} (#'invoke/wrapped-params {"operation" {"params" {:x "json"}}} {"x" 1})))

  (testing "Resolve Asset (DID)"
    (binding [sfa/*resolver* (LocalResolverImpl.)]

      (sfa/register! (sf/did "did:dex:abc") (env/agent-ddo (system/env test-system)))

      (let [aladdin (sfa/did->agent (sf/did "did:dex:abc"))

            asset (sf/upload aladdin (sf/memory-asset ""))

            {:keys [x]} (#'invoke/wrapped-params {:operation {:params {:x "asset"}}} {:x {"did" (str (sf/did asset))}})]
        (is (= true (sf/asset? x)))))))

(deftest wrapped-results-test
  (is (= {:x 1} (#'invoke/wrapped-results {:operation {:results {:x "json"}}} {:x 1})))
  (is (= {:x 1} (#'invoke/wrapped-results {:operation {:results {:x "json"}}} {"x" 1})))
  (is (= {:x 1} (#'invoke/wrapped-results {"operation" {"results" {"x" "json"}}} {"x" 1})))
  (is (= {:x 1} (#'invoke/wrapped-results {} {:x 1}))))

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
      (is (= {:coll [1 2 3 4]} (invoke/invoke #'demo.invokable/concatenate (system/new-context test-system) {:coll1 [1 2] :coll2 [3 4]}))))))