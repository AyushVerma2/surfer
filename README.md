# Surfer

[![Actions Status](https://github.com/DEX-Company/surfer/workflows/master/badge.svg)](https://github.com/DEX-Company/surfer/actions) [![Actions Status](https://github.com/DEX-Company/surfer/workflows/develop/badge.svg)](https://github.com/DEX-Company/surfer/actions)

Surfer provides a standalone reference implementation of an Data Ecosystem Agent with the following capabilities:

- Storage API for storing asset data according to DEP7
- Meta API for serving Asset Metadata according to DEP15
- Market API for serving as an Data Marketplace backend

Surfer also provides some example implementations of capabilities / services for an open data ecosystem, including
- CKAN Dataset Import


## Motivation

The decentralised data ecosystems of the future will incorporate a multitude of Agents which provide capabilities
to others in the data ecosystem. These capabilities may include:
 
 - Serving metadata about Assets in the ecosystem
 - Offering certain Data Assets for download
 - Allowing the remote invocation of compute services
 - Making payments for access rights to assets and servcies

To maintain ecosystem interoperability, Agents may implement the DEP Standards (https://github.com/DEX-Company/DEPs) 
that enable data ecosystem capabilities to be orchestrated into decentralised Data Supply Lines and operated with 
minimal custom coding, since they will all conform to a common abstract interface accessible via the public internet.

Surfer provides a reference implementation of Agent functionality for the ecosystem, as a open source product 
that Service Providers can use and customise. Surfer is designed so that it can be relatively easily configured 
and/or customised to support specific requirements of a service provider.

## Key functionality and use cases

### Meta API

The Meta API is an implementation of DEP 15, enabling access to asset metadata stored in surfer.

Authentication for Meta API access may be controlled by the service provider. Authorisation rules can also be
configured by the service provider, the default behaviour is:
- metadata write access is restricted to registered users
- metadata read access is public

### CKAN Import

The Comprehensive Knowledge Archive Network (CKAN) is a web-based open source management system for the storage and distribution of open data. By crawling and indexing CKAN data, we are able to quickly open up a wealth of open data resources to the Ocean ecosystem.

Surfer provides a simple service that:
- Crawls CKAN repositories
- Converts CKAN metadata into Ocean metadata

## Example Usage


### Running Surfer in server mode

1. Clone / download this repository
2. In the `surfer` directory run `nohup clojure -M:main &`
3. Browse to `http://localhost/3030` for the Welcome page

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
    proxy_pass http://localhost:3030;
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
    proxy_pass http://localhost:3030;
  }
}
```

### Interactive REPL use

Surfer may be used interactively at a Clojure REPL. Run `clj -A:dev:test` to launch Surfer with the extra `dev` and `test` aliases. Once the REPL is ready, start the system `(go)` - `(go)` will switch to the `dev` namespace and start the system.


### Running the CLI tests

#### Unit Tests

```
$ clojure -A:test:test-runner -e :integration
```

#### Integration Tests

```
$ clojure -A:test:test-runner -i :integration
```
