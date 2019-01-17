#!/bin/sh

host=localhost
port=8080
url="http://${host}:${port}/"
pidfile="$RESULTS/surfer.pid"
outfile="$RESULTS/surfer.out"

rm -f "$pidfile"

jdkversion=$(java -version 2>&1 | head -1 | cut -d\" -f2)
case "$jdkversion" in
    1.8*) true
          ;;
    *) echo "error: only JDK 8 is supported, not $jdkversion"
       exit 1
       ;;
esac
echo "using java $jdkversion"

cd "$CODE"
CONFIG_PATH=test/resources/surfer-config.edn mvn install exec:java 2>&1 > $outfile &
pid=$!
echo $pid > $pidfile
echo "started surfer as [$pid]"

i=1
n=30
while [ $i -le $n ]; do
    if curl -s -m 2 $url > /dev/null; then
        echo "surfer is up"
        exit 0
    fi
    printf "."
    sleep 1
    i=$((i + 1))
done

echo "surfer is NOT up"
exit 1
