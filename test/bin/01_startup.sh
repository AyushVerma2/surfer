#!/bin/sh

host=localhost
port=8080
url="http://${host}:${port}/"
# construct curl args
args=""
args="$args -s" # NOTE turning off silent mode is useful for debugging
args="$args -m 3"
args="$args -u "

surferworking() {
    local user="$1"
    local password="$2"
    if curl $args "$user:$password" $url > /dev/null 2>&1 ; then
        return 0
    fi
    return 1
}

# NOTE: surfer may have been started by maven-surefire-plugin

i=0
n=10
while [ $i -le $n ]; do
    if surferworking test foobar ; then
        if [ $i -eq 0 ]; then
            echo "surfer is ALREADY up"
        fi
    else
        i=0
        echo "surfer stopped"
        sleep 3
        break
    fi
    printf "."
    sleep 1
    i=$((i + 1))
done

if [ $i -gt 0 ]; then
    echo "surfer is already running... tests aborted"
    exit 1
fi

pidfile="$RESULTS/surfer.pid"
outfile="$RESULTS/surfer.out"

rm -f "$pidfile"

jdkversion=$(java -version 2>&1 | awk -F\" '/version/ { print $2;}')
case "$jdkversion" in
    1.8*) true
          ;;
    1.9*) true
          ;;
    10.*) true
          ;;
    11.*) true
          ;;
    *) echo "error: only JDK 8 or later is supported, not $jdkversion"
       exit 1
       ;;
esac
echo "using java $jdkversion"

cd "$CODE"

CONFIG_PATH=test/resources/surfer-config.edn mvn exec:java -B > $outfile 2>&1 &
pid=$!
echo $pid > $pidfile
echo "started surfer as [$pid]"

i=1
n=60

while [ $i -le $n ]; do
    if surferworking "$USER" "$PASSWORD" ; then
        echo "surfer is up"
        exit 0
    fi
    printf "."
    sleep 1
    i=$((i + 1))
done

echo "surfer is NOT up"
exit 1
