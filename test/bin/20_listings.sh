#!/bin/sh

# for extremely verbose debugging output sent to target/cli-test/10_extensions.err
# set -x

# will exit on first error
set -e

host=localhost
port=8080
url="http://${host}:${port}"

downloads="$RESULTS/downloads"
mkdir -p "$downloads"

# construct curl args
args=""
args="$args -s" # NOTE turning off silent mode is useful for debugging
args="$args -m 3"
args="$args -u ${USER}:${PASSWORD}"
# The following get in trouble with quoting/evaluation...
# args="$args --header 'Content-Type: application/json'"
# args="$args --header 'Accept: application/json'"

# NOTE the userid is looked up based on authentication!
# useridnn="789e3f52da1020b56981e1cb3ee40c4df72103452f0986569711b64bdbdb4c"
# NOTE the assetid MUST have come from registering an asset
assetidnn="1a6889682e624ac54571dc2ab1b4c9a9ba16b2b3f70a035ce793d6704a04ed"
idnn="56f04c9b25576ef4a0c7491d47417009edefde8e75f788f05e1eab782fd0f1"

# this listing payload is taken directly from the swagger example
# and likely should be modified/adapted more
#
#  "userid": "$userid",
#
listing() {
    local nn="$1"
    local assetid="$2"
    #  local userid="${useridnn}${nn}"
    local id="${idnn}${nn}"
    cat<<EOF
{
  "trust_level": 0,
  "assetid": "$assetid",
  "agreement": {},
  "trust_visible": "string",
  "ctime": "2018-11-26T13:27:45.542Z",
  "status": "published",
  "id": "$id",
  "info": {
    "title": "string",
    "description": "string"
  },
  "utime": "2018-11-26T13:27:45.542Z",
  "trust_access": "string"
}
EOF
}

get_http_status() {
    local header="$1"
    awk '/^HTTP/ { print $2;}' "$header"
}

cd "$downloads"
assetidfiles="$(ls -1 | grep '\.assetid.json$')"

fail=0
i=1
for assetidfile in $assetidfiles; do
    nn=$(printf %02d $i)
    assetid=$(sed s/\"//g "$assetidfile")
    echo "LISTING $nn = $assetid"
    listingfile="${nn}.listing.json"
    listing2file="${nn}.listing2.json"
    resultfile="${nn}.listing-result.json"
    id="${idnn}${nn}"
    listing "$nn" "$assetid" > "$listingfile"
    if curl $args --header 'Content-Type: application/json' \
            --header 'Accept: application/json' \
            -D "$listingfile.header" \
            -o "$resultfile" \
            -d @"$listingfile" $url/api/v1/market/listings \
            && [ -e "$listingfile.header" ] \
            && [ "200" = $(get_http_status "$listingfile.header") ] ; then
        echo "  UPLOADED   LISTING $nn"
        if curl $args -D "$listing2file.header" -o "$listing2file" \
                $url/api/v1/market/listings/$id \
                && [ -e "$listing2file.header" ] \
                && [ "200" = $(get_http_status "$listing2file.header") ] ; then
            echo "  DOWNLOADED LISTING $nn"
        else
            echo "  FAILED DOWNLOAD LISTING"
            fail=$(($fail + 1))
        fi
    else
        echo "  FAILED to upload LISTING $nn"
        fail=$(($fail + 1))
    fi
    i=$(( $i + 1 ))
done

from=1
size=3
listingsfile="all.listings.json"
if curl $args --header 'Content-Type: application/json' \
        --header 'Accept: application/json' \
        -D "$listingsfile.header" \
            -o "$listingsfile" \
            $url/api/v1/market/listings\?from=$from\&size=$size \
            && [ -e "$listingsfile.header" ] \
            && [ "200" = $(get_http_status "$listingsfile.header") ] ; then
    echo "DOWNLOADED LISTINGS from $from size $size"
    resultsize=$(tr '"' '\n' < "$listingsfile" | fgrep assetid | wc -l)
    if [ "$resultsize" = "$size" ]; then
        echo "  EXPECTED RESULTS = $size"
    else
        echo "  UNEXPECTED RESULTS: $resultsize != $size"
        fail=$(($fail + 1))
    fi
else
    echo "FAILED to DOWNLOAD LISTINGS"
    fail=$(($fail + 1))
fi


if [ $fail -gt 0 ]; then
    exit 1
fi
