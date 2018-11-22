(ns ocean.schemas
  (:require
    [surfer.utils :as u]
    [schema.core :as s]
    [ring.swagger.json-schema :as rjs]
    [schema-generators.generators :as g]))

(s/defschema AssetID
  (rjs/field
    (s/constrained s/Str u/valid-asset-id? "Valid Asset ID")
    {:example "1a6889682e624ac54571dc2ab1b4c9a9ba16b2b3f70a035ce793d6704a04edb9"}))

(s/defschema UserID
  (s/constrained s/Str u/valid-user-id? "Valid User ID"))

(s/defschema ListingID
  (s/constrained s/Str u/valid-listing-id? "Valid Listing ID"))

(s/defschema AssetType
  (s/enum "dataset", "algorithm", "container", "workflow", "other"))

(s/defschema ListingStatus
  (s/enum "published", "unpublished", "suspended"))


(s/defschema AssetLink
  {:name s/Str
   :type s/Str
   :url s/Str})

(s/defschema Asset
  {:name s/Str
   :description s/Str
   :type AssetType
   :dateCreated s/Inst
   :links [AssetLink]})

(s/defschema ListingInfo
  {(s/optional-key :title) s/Str
   (s/optional-key :description) s/Str
   s/Keyword s/Any
   })

(s/defschema Listing
  {:id ListingID
   :assetid AssetID
   :userid UserID
   (s/optional-key :info) (s/maybe ListingInfo)
   (s/optional-key :status) (s/maybe ListingStatus)
   (s/optional-key :agreement) s/Any
   (s/optional-key :ctime) s/Inst
   (s/optional-key :utime) s/Inst
   (s/optional-key :trust_level) s/Int
   (s/optional-key :trust_access) (s/maybe s/Str)
   (s/optional-key :trust_visible) (s/maybe s/Str)})
