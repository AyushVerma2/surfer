(ns surfer.schema-test
  (:require [clojure.test :refer :all])
  (:require [surfer.utils :as u])
  (:require [schema.core :as s]
            [schema-generators.generators :as g])
  (:require [surfer.schema :as schema]))

(deftest test-IDs
  (is (s/validate schema/AssetID
                  "4e03657aea45a94fc7d47ba826c8d667c0d1e6e33a64a036ec44f58fa12d6c45"))

  (is (nil? (s/check schema/AssetID (u/new-random-id))))

  (is (s/check schema/AssetID "Bad ID String")))


