# Surfer

[![Build Status](https://dev.azure.com/dex-devops/miketest/_apis/build/status/DEX-Company.surfer?branchName=develop)](https://dev.azure.com/dex-devops/miketest/_build/latest?definitionId=1?branchName=develop)

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

## Example Usage

### Running Surfer as a docker image

1. Clone / download the surfer repository from GitHub.
2. Build the docker container, it takes a while ( a lot of downloading for ~7 minutes ) so relax and have a nice cup of teh/kopi..

```
$ docker build -t surfer .
```

3. After the building the docker container you can then run it by doing the following command:

```
$ docker run -i -p 8080:8080 surfer
```

4. Browse to `http://localhost/8080` for the Welcome page.

5. To stop the docker container, *control-c* does not seem to work, so you will need to call `docker stop` by doing the following:
```
$ docker ps

CONTAINER ID        IMAGE               COMMAND                  CREATED              STATUS              PORTS                    NAMES
449fe0ec6cda        surfer              "/bin/sh -c 'mvn ins…"   About a minute ago   Up About a minute   0.0.0.0:8080->8080/tcp   wonderful_ardinghelli

$ docker stop 449fe0ec6cda
```

### Running Surfer in server mode

Surfer can be executed from the source tree using Maven.

1. Clone / download the surfer repository from GitHub (`git clone https://github.com/oceanprotocol/surfer` should work)
2. In the surfer directory run `nohup mvn clean install exec:java &`
3. Browse to `http://localhost/8080` for the Welcome page

To update the server if already running:

1. Pull the latest source in the surfer directory with `git pull`
2. Find the currently running surfer process with `ps -ax`. It should be a process with a resonably high CPU usage and maven in the file path.
3. Kill the currently running process e.g. `kill 18851`
4. Run the latest version with `nohup mvn clean install exec:java &` as before

For production usage, the use of a reverse proxy such as nginx for TLS is recommended.

Setup:
```
sud apt-get install nginx
sudo openssl req -x509 -nodes -days 365 -newkey rsa:2048 -keyout /etc/nginx/conf.d/selfsigned.key -out /etc/nginx/conf.d/selfsigned.crt
```

`/etc/nginx/conf.d/proxy.conf` file as below:

```
server {
  listen 80;
  server_name localhost;

  location / {
    proxy_pass http://localhost:8080;
  }
}

server {
  listen 443 ssl;
  server_name localhost;
  ssl_certificate /etc/nginx/conf.d/selfsigned.crt;
  ssl_certificate_key /etc/nginx/conf.d/selfsigned.key;
  ssl_protocols TLSv1 TLSv1.1 TLSv1.2;
  ssl_ciphers HIGH:!aNULL:!MD5;

  location / {
    proxy_pass http://localhost:8080;
  }
}
```

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

### Unit Tests

```
$ mvn test
```

### Integration Tests

This target will run Unit tests and Integration tests.
_NOTE:_ the command line tests will be elided on Windows.


```
$ mvn integration-test
```

### Running the CLI tests

Here are the steps required to run the command line tests on
an Ubuntu 18.04 LTS system:

```
$ mkdir -p ~/.ocean
$ touch ~/.ocean/.timestamp   # ensure ~/.ocean is writable
$ sudo apt-get update
$ sudo apt-get upgrade
$ sudo apt-get install git git-core openjdk-8-jdk maven
$ sudo update-java-alternatives --set java-1.8.0-openjdk-amd64
$ mkdir -p src/github/DEX-Company
$ cd src/github/DEX-Company
$ git clone https://github.com/DEX-Company/surfer.git
$ cd surfer
$ mvn clean
$ mvn dependency:list  # download artifacts the first time
$ mvn install # compile
$ ./test/bin/cli-test
```

CLI test results can be found in target/cli-test/

See also recent log entries in log/surfer.log
