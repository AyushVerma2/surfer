#!/bin/sh

host=localhost
port=8080
url="http://${host}:${port}/"

if curl -s -m 2 $url; then
    exit 0
fi

exit 1
