# surfer
CKAN Dataset Exploration with Ocean

## Motivation

The Comprehensive Knowledge Archive Network (CKAN) is a web-based open source management system for the storage and distribution of open data. By crawling and indexing CKAN data, we are able to quickly open up a wealth of open data resources to the Ocean ecosystem.

Surfer is a simple service that:
- Crawls CKAN repositories
- Converts CKAN metadata into Ocean metadata
- Offers a web-based interface for exploring the resulting Ocean assets

## Usage

surfer is currently designed to be used interactively at a Clojure repl. Open a REPL in the 'surfer.core' namespace.

```clojure
(ns surfer.core)

;; first, launch the server
;; By default this runs on http://localhost:8080/
(-main)

;; Query a ckan repository (gets a list of all packages in the repo)
(def all (package-list "https://data.gov.uk"))

;; Randomly select 10 sample packages to import
;; We could use them all, but it would be a lot of requests, so keeping small for test purposes
(def packs (take 10 (shuffle all))

;; We get something like:
;; ("financial-transactions-data-darlington-primary-care-trust" "payment-recalls" 
;; "local-air-quality-monitoring-stations1" "organogram-nhs-greater-east-midlands-csu" 
;; "water-body-status-classification-south-west-awb" "bathymetric-survey-2002-07-31-liverpool-stages" 
;; "bathymetric-survey-2003-12-10-heysham-entrance" 
;; "distribution-of-ash-trees-in-woody-linear-features-in-great-britain" 
;; "bathymetric-survey-2001-03-30-eyemouth-to-berwick-upon-tweed" 
;; "2004-2007-university-of-exeter-cornwall-and-the-isles-of-scilly-grey-seal-survey1")

;; Import the metadata for the CKAN packages and convert to ocean format
(import-packages "https://data.gov.uk" packs)

;; Now go to http://localhost:8080/assets and explore your new Ocean assets!

```

