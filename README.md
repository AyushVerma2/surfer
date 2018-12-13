# Surfer

Surfer provides a standalone reference implementation of an Ocean Agent with the following capabilities:

- Meta API for serving Ocean Asset Metadata
- Market API for serving as an Ocean Marketplace backend
- Common Storage API for asset data
- CKAN Dataset Import

Surfer is a component of the Ocean Reference Marketplace, fulfilling the role of a Marketplace Agent.
Marketplace operators are free to use Surfer or another compatible implementation of the relevant APIs 
for their marketplace.

## Motivation

An Ocean marketplace requires the use of one or more Ocean Agents that perform off-chain functionality.
Surfer provides an implementation of this functionality as a reference implementation for the ecosystem,
and as a open source product that Marketplace operators can use.

Surfer is designed so that it can be relatively easily configured and/or customised to support specific 
requirements of a marketplace operator.

## Key functionality and use cases

### Meta API

The Meta API is an implementation of OEP 15, enabling access to asset metadata stored in surfer.

Authentication for Meta API access is handled by the marketplace. Access control rules can be 
configured by the marketplace operator, however default recommended behaviour is:
- metadata write access is restricted to registered marketplace users
- metadata read access is public

### CKAN Import

The Comprehensive Knowledge Archive Network (CKAN) is a web-based open source management system for the storage and distribution of open data. By crawling and indexing CKAN data, we are able to quickly open up a wealth of open data resources to the Ocean ecosystem.

Surfer provides a simple service that:
- Crawls CKAN repositories
- Converts CKAN metadata into Ocean metadata
- Hashes the metadata to obtain Asset IDs
- Offers a web-based interface for exploring the resulting Ocean assets
- Hosts a Swagger interface for exploring the Surfer APIs

## Docker
### Building the docker image
First run the following command, it takes a while ( ~7 minutes ) so relax and have a nice cup of tea..

```
docker build -t surfer .
```

After the building the docker container you can then run it by doing the following command

```
docker run surfer
```

## Example Usage

### Running Surfer in server mode

Surfer can be executed using Maven.

1. Clone / download the surfer repository from GitHub
2. In the root directory run `nohup mvn clean install exec:java &`
3. Browse to `http://localhost/8080` for the Welcome page

For production usage, the use of a reverse proxy such as nginx is recommended.

### Interactive REPL use

Surfer may be used interactively at a Clojure REPL. Open a REPL in the 'surfer.core' namespace.

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

