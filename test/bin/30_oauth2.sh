#!/bin/sh

# will exit on first error
set -e

host=localhost
port=8080
url="http://${host}:${port}"

downloads="$RESULTS/downloads"
mkdir -p "$downloads"

# construct curl args
timeout=3
args=""
args="$args -s" # NOTE turning off silent mode is useful for debugging
args="$args -f" # FAIL on bad authentication
args="$args -m $timeout"

fail=0
ok="HTTP/1.1 200 OK"

token1file="$downloads/token1.json"
token2file="$downloads/token2.json"
tokensfile="$downloads/tokens.json"
userfile="$downloads/user.json"
deletefile="$downloads/delete.json"
assetsfile="$downloads/assets.json"
token1=""
token2=""
tokens=""

if curl $args -u "${USER}:${PASSWORD}" \
        -H 'Accept: application/json' -o "$token1file" \
        -X POST $url/api/v1/auth/token \
        && [ -e "$token1file" ]; then
    token1=$(sed s/\"//g "$token1file")
    echo "Created token1: $token1"
else
    echo "unable to create token1"
    fail=$(($fail + 1))
fi

echo " "
if curl $args -u "${USER}:${PASSWORD}" \
        -H 'Accept: application/json' -o "$token2file" \
        -X POST $url/api/v1/auth/token \
        && [ -e "$token2file" ]; then
    token2=$(sed s/\"//g "$token2file")
    echo "Created token2: $token2"
else
    echo "unable to create token2"
    fail=$(($fail + 1))
fi

echo " "
if curl $args -u "${USER}:${PASSWORD}" \
        -H 'Accept: application/json' -o "$tokensfile" \
        -X GET $url/api/v1/auth/token \
        && [ -e "$tokensfile" ]; then
    echo "Got tokens:"
    tokens=$(tr , '\n' < "$tokensfile"  | cut -d\" -f2)
    f1=""
    f2=""
    for token in $tokens; do
        if [ "$token" = "$token1" ]; then
            found="token1"
            f1=$found
        else
            if [ "$token" = "$token2" ]; then
                found="token2"
                f2=$found
            else
                found=""
            fi
        fi
        echo "  $token $found"
    done
else
    echo "unable to get tokens"
    fail=$(($fail + 1))
fi

if [ -z "$f1" ]; then
    echo "did NOT find token1"
    fail=$(($fail + 1))
fi

if [ -z "$f2" ]; then
    echo "did NOT find token2"
    fail=$(($fail + 1))
fi

echo " "
if curl $args \
        -H 'Accept: application/json' -o "$userfile" \
        -X GET $url/api/v1/auth/user?access_token=$token1 \
        && [ -e "$userfile" ]; then
    echo "Got user via access_token:"
    cat "$userfile"
    echo " "
else
    echo "unable to get user via access_token"
    fail=$(($fail + 1))
fi

echo " "
if curl $args \
        -H "Authorization: token $token1" \
        -H 'Accept: application/json' -o "$deletefile" \
        -X DELETE $url/api/v1/auth/token/$token2 \
        && [ -e "$deletefile" ]; then
    echo "Deleted token via OAuth2 header: " $(cat "$deletefile")
else
    echo "unable to delete token via OAuth2 header"
    fail=$(($fail + 1))
fi

echo " "
if curl $args -u ":"  -i \
        -H 'Accept: application/json' -o "$assetsfile" \
        -X GET $url/assets ; then
    echo "ERROR: empty username/password worked to get assets"
    fail=$(($fail + 1))
else
    echo "Empty username/password denied"
fi

if [ $fail -gt 0 ]; then
    exit 1
fi
