#!/bin/sh

# for extremely verbose debugging output sent to target/cli-test/10_extensions.err
# set -x

# will exit on first error
set -e

host=localhost
port=8080
url="http://${host}:${port}"

downloads="$RESULTS/downloads"
if [ -d "$RESULTS/downloads" ]; then
    echo "downloads directly already exists: $RESULTS/downloads"
    exit 1
fi

mkdir -p "$downloads"

uploads="$CODE/test/data"

cd "$uploads"
assets="$(ls -1 | grep -v '\.type$')"
defaulttype="application/octet-stream"

# construct curl args
args=""
args="$args -s" # NOTE turning off silent mode is useful for debugging
args="$args -m 5"
args="$args -u ${USER}:${PASSWORD}"
# The following get in trouble with quoting/evaluation...
# args="$args --header 'Content-Type: application/json'"
# args="$args --header 'Accept: application/json'"

# this metadata payload is taken directly from the swagger example
# and likely should be modified/adapted more
metadata() {
    local asset="$1"
    local contenttype="$2"
    cat<<EOF
{
  "name": "$asset",
  "description": "string",
  "type": "dataset",
  "dateCreated": "2018-11-26T13:27:45.542Z",
  "tags": [
    "string"
  ],
  "contentType": "$contenttype",
  "links": [
    {
      "name": "string",
      "type": "download",
      "url": "string",
      "assetid": "1a6889682e624ac54571dc2ab1b4c9a9ba16b2b3f70a035ce793d6704a04edb9"
    }
  ]
}
EOF
}

fail=0
for asset in $assets; do
    assettype="$asset.type"
    if [ -e "$assettype" ]; then
        contenttype=$(cat "$assettype")
    else
        contenttype="$defaulttype"
    fi
    echo "ASSET $asset $contenttype"
    metadatafile="$downloads/${asset}.metadata.json"
    metadata2file="$downloads/${asset}.metadata2.json"
    assetidfile="$downloads/${asset}.assetid.json"
    asset2="$downloads/${asset}"
    metadata "$asset" "$contenttype" > $metadatafile
    if curl $args --header 'Content-Type: application/json' \
            --header 'Accept: application/json' \
            -o "$assetidfile" -d @"$metadatafile" $url/api/v1/meta/data \
            && [ -e "$assetidfile" ]; then
        # NOTE: remove quotes from the assetid
        assetid=$(sed s/\"//g "$assetidfile")
        echo "  UPLOADED METADATA $assetid"
        if curl $args --header 'Accept: application/json' \
                --form file=@"$asset" $url/api/v1/assets/$assetid ; then
            echo "  UPLOADED DATA"
            if curl $args --header 'Accept: application/json' \
                    -o "$metadata2file" $url/api/v1/meta/data/$assetid \
                    && [ -e "$metadata2file" ]; then
                echo "  DOWNLOAD METADATA"
                # NOTE: JSON either should be pretty printed or stripped
                # of whitespace before comparison (and still may not be in order)
                # i.e. this test is too naive
                # if diff -u "$metadatafile" "$metadata2file" ; then
                #     echo "  DOWNLOAD METADATA MATCHED"
                # else
                #     echo "  DOWNLOAD METADATA DID NOT MATCH"
                #     fail=$(($fail + 1))
                # fi
                if ( cd "$downloads" &&
                         curl $args \
                              -D "$asset2.header" -J -O \
                              $url/api/v1/assets/$assetid ) ; then
                    datafile=$(awk -F\" '/filename=/ { print $2; }' "$asset2.header")
                    echo "  DOWNLOAD DATA $datafile"
                    ext=$(echo "$asset" | awk -F. '{print $NF}')
                    ext2=$(echo "$datafile" | awk -F. '{print $NF}')
                    if [ "$ext" = "$ext2" ] ; then
                        echo "  EXTENSION MATCHED $ext"
                    else
                        echo "  EXTENSION $ext2 DID NOT MATCH $ext"
                        fail=$(($fail + 1))
                    fi
                else
                    echo "  FAILED DOWNLOAD DATA"
                    fail=$(($fail + 1))
                fi
            else
                echo "  FAILED DOWNLOAD METADATA"
                fail=$(($fail + 1))
            fi
        else
            echo "  FAILED DATA"
            fail=$(($fail + 1))
        fi
    else
        echo "  FAILED METADATA"
        fail=$(($fail + 1))
    fi
done

if [ $fail -gt 0 ]; then
    exit 1
fi
