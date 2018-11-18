(ns ocean.schemas
  (:require
    [surfer.utils :as u]
    [schema.core :as s]
    [schema-generators.generators :as g]))

(s/defschema AssetID
  (s/constrained s/Str u/valid-asset-id? "Valid Asset ID"))
