#!/bin/sh

host=localhost
port=8080
url="http://${host}:${port}/"
user="one"
password="11111"

# construct curl args
args=""
args="$args -s" # NOTE turning off silent mode is useful for debugging
args="$args -m 3"
args="$args -u ${user}:${password}"

if curl $args $url ; then
    exit 0
fi

exit 1
