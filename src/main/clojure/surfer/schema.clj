(ns surfer.schema
  (:require
    [surfer.utils :as u]
    [schema.core :as s]
    [ring.swagger.json-schema :as rjs]
    [schema-generators.generators :as g]))

;; hack to allow data.json to output dates
(extend-protocol clojure.data.json/JSONWriter
  java.time.Instant
  (-write [^java.time.Instant object out]
    (clojure.data.json/write (str object) out)))

;; =========================================================
;; Identifiers

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

(s/defschema OAuth2Token
  (rjs/field
    (s/constrained s/Str u/valid-oauth2-token? "Valid OAuth2 Token")
    {:example "56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f102"
     :description "A valid OAuth2 token (64 characters lowercase hex)"}))

(s/defschema Username
  (rjs/field
    s/Str
    {:example "test"
     :description "A valid username for a user registered on the marketplace"}))

;; =====================================================
;; Metadata

(s/defschema AssetMetadata
  {:id AssetID
   :metadata s/Str})

;; =====================================================
;; Common data types
(s/defschema Instant
  (rjs/field
    java.time.Instant
    {:example "2018-11-26T13:27:45.542Z"
     :description "A timestamp defining an instant in UTC time (ISO-8601)"}))

(s/defschema TokenValue
  (rjs/field
    (s/constrained s/Str u/valid-token-value? "Valid Token Value (decimal string)")
    {:example "1.0"
     :description "A quantity of Ocean tokens represented as a decimal string."}))

;; =====================================================
;; Users

;; =====================================================
;; Assets

(s/defschema AssetLinkType
  (s/enum "download",         ;; a direct download URL, intended for free/open assets
          "sample",           ;; a link to a sample dataset for this asset
          "previous-version", ;; a link to a previous version of this asset
          "format",           ;; a link to a resource that describes the format of this asset
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
   :dateCreated Instant
   (s/optional-key :tags) [s/Str]
   (s/optional-key :contentType) s/Str
   (s/optional-key :links) [AssetLink]
   s/Keyword s/Any})

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
   (s/optional-key :ctime) Instant
   (s/optional-key :utime) Instant
   (s/optional-key :trust_level) s/Int
   (s/optional-key :trust_access) (s/maybe s/Str)
   (s/optional-key :trust_visible) (s/maybe s/Str)})

(s/defschema DDO
  {:id String
   (keyword "@context") s/Str
   ;; TODO: more values
   s/Keyword s/Any})

(s/defschema Agreement
  {(s/optional-key :price) (s/maybe TokenValue)
   s/Keyword s/Any
   })

(s/defschema From
  (rjs/field
    s/Int
    {:example 0
     :description "Display results from this page (zero based, default 0)"}))

(s/defschema Size
  (rjs/field
    s/Int
    {:example 100
     :description "Display results with this page size (default 100)"}))

;; ============================================================
;; Invoke

(s/defschema InvokeRequest
  {s/Str s/Any})

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
   (s/optional-key :agreement) (s/maybe Agreement)
   (s/optional-key :ctime) (s/maybe Instant)
   (s/optional-key :utime) (s/maybe Instant)})
