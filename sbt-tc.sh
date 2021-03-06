#!/bin/bash

# look at the domain we are on and set up the proxy config in response
DOMAIN=`hostname -d`
if [ "$DOMAIN" = "gc2.dc1.gnm" ]; then
    PROXY_CONF=""
else
    PROXY_CONF="-Dhttp.proxyHost=devproxy.gul3.gnl -Dhttp.proxyPort=3128"
fi

echo "Domain is $DOMAIN: proxy conf is [$PROXY_CONF]"

cat /dev/null | java -XX:+CMSClassUnloadingEnabled -Xmx2g -XX:MaxPermSize=500m -XX:+UseCompressedOops \
    -Xloggc:gc.log -XX:+PrintGCDetails -XX:+PrintGCDateStamps \
    -Dsbt.log.noformat=true \
    $PROXY_CONF \
    -jar sbt-launch.jar "$@"
