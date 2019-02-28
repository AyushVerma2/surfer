#!/bin/sh

pidfile="$RESULTS/surfer.pid"

if [ ! -e "$pidfile" ]; then
    echo "surfer is not running"
    # NOTE: assume surfer started by maven-surefire-plugin
    exit 0
    # exit 1
fi

pid=$(cat "$pidfile")

if kill -0 $pid > /dev/null 2>&1 ; then
    echo "stopping surfer as [$pid]"
else
    echo "cannot find the pid for surfer"
    # NOTE: assume surfer started by maven-surefire-plugin
    exit 0
    # exit 1
fi

kill $pid
rm -f "$pidfile"
