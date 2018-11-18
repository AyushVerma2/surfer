(ns ocean.schemas
  (:require
    [surfer.utils :as u]
    [schema.core :as s]
    [schema-generators.generators :as g]))

(s/defschema AssetID
  (s/constrained s/Str u/valid-asset-id? "Valid Asset ID"))

(s/defschema UserID
  (s/constrained s/Str u/valid-user-id? "Valid User ID"))

(s/defschema AssetType
  (s/enum "dataset", "algorithm", "container", "workflow", "other"))

(s/defschema Asset
  {:name s/Str
   :type AssetType})
