(ns ocean.schemas
  (:require
    [surfer.utils :as u]
    [schema.core :as s]
    [ring.swagger.json-schema :as rjs]
    [schema-generators.generators :as g]))

(s/defschema AssetID
  (rjs/field
    (s/constrained s/Str u/valid-asset-id? "Valid Asset ID")
    {:example "1a6889682e624ac54571dc2ab1b4c9a9ba16b2b3f70a035ce793d6704a04edb9"
     :description "A valid Asset ID (64 characters lowercase hex). This is the keccak256 has of asset metadata."}))

(s/defschema UserID
  (rjs/field
    (s/constrained s/Str u/valid-user-id? "Valid User ID")
    {:example "789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4ca6"
     :description "A valid User ID (64 characters lowercase hex)"}))

(s/defschema ListingID
  (rjs/field
    (s/constrained s/Str u/valid-listing-id? "Valid Listing ID")
    {:example "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"
     :description "A valid Listing ID (64 characters lowercase hex)"}))


(s/defschema PurchaseID
  (rjs/field
    (s/constrained s/Str u/valid-purchase-id? "Valid Purchase ID")
    {:example "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"
     :description "A valid Purchase ID (64 characters lowercase hex)"}))

(s/defschema Username
  (rjs/field
    s/Str
    {:example "test"
     :description "A valid username for a user registered on the marketplace"}))

;; =====================================================
;; Assets

(s/defschema AssetLinkType
  (s/enum "download",         ;; a direct download URL, intended for free/open assets
          "sample",           ;; a link to a sample dataset for this asset
          "previous-version", ;; a link to a previous version of this asset
          "source"            ;; a URL for the source, e.g. a website
          ))

(s/defschema AssetType
  (rjs/field 
    (s/enum "dataset", "algorithm", "bundle", "workflow", "other")
    {:example "dataset"
     :description "The fundamental class of asset being considered."}))

(s/defschema AssetLink
  {:name s/Str
   :type AssetLinkType
   (s/optional-key :url) s/Str
   (s/optional-key :assetid) AssetID})

(s/defschema Asset
  {:name s/Str
   :description s/Str
   :type AssetType
   :dateCreated s/Inst
   :links [AssetLink]})

;; ===========================================================
;; Listings

(s/defschema ListingStatus
  (s/enum "unpublished", "published", "suspended"))

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

;; ============================================================
;; Purchases

(s/defschema PurchaseStatus
  (s/enum "wishlist", "ordered", "delivered"))

(s/defschema Purchase
  {:id PurchaseID
   :listingid ListingID
   :userid UserID
   (s/optional-key :status) (s/maybe PurchaseStatus)
   (s/optional-key :info) (s/maybe ListingInfo)
   (s/optional-key :agreement) s/Any
   (s/optional-key :ctime) s/Inst
   (s/optional-key :utime) s/Inst})

