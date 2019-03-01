#!/bin/sh

pidfile="$RESULTS/surfer.pid"

if [ ! -e "$pidfile" ]; then
    echo "surfer is not running"
    exit 1
fi

pid=$(cat "$pidfile")

if kill -0 $pid > /dev/null 2>&1 ; then
    echo "stopping surfer as [$pid]"
else
    echo "cannot find the pid for surfer"
    exit 1
fi

kill $pid
rm -f "$pidfile"
