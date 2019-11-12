(ns surfer.schemas-test
  (:require [clojure.test :refer :all])
  (:require [surfer.utils :as u])
  (:require [schema.core :as s]
            [schema-generators.generators :as g])
  (:require [ocean.schemas :as schemas]))

(deftest test-IDs
  (is (s/validate schemas/AssetID 
                  "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"))
  
  (is (nil? (s/check schemas/AssetID (u/new-random-id))))
  
  (is (s/check schemas/AssetID "Bad ID String")))


