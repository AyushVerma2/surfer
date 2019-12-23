(ns surfer.invoke-test
  (:require [clojure.test :refer :all]
            [surfer.invoke :as invoke]))

(deftest wrapped-params-test
  (is (= {:x 1} (#'invoke/wrapped-params {:operation {:params {:x "json"}}} {:x 1}))))