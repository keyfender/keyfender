#!/bin/sh -e

ARGS=$*

echo "##### Keyfender container setup #####"

[ -n "$KEYFENDER_HTTP_PORT" ] && ARGS="$ARGS --http=$KEYFENDER_HTTP_PORT"
[ -n "$KEYFENDER_HTTPS_PORT" ] && ARGS="$ARGS --https=$KEYFENDER_HTTPS_PORT"
[ -n "$KEYFENDER_PASSWORD" ] && ARGS="$ARGS --password=$KEYFENDER_PASSWORD"
[ -n "$KEYFENDER_IRMIN_URL" ] && ARGS="$ARGS --irmin-url=$KEYFENDER_IRMIN_URL"

NS=${KEYFENDER_NS:-$(grep ^nameserver /etc/resolv.conf | head -1 | awk '{print $2}')}
echo "Nameserver: $NS"

KEYFENDER_MEM=${KEYFENDER_MEM:-32}

export MIRAGE_LOGS=${MIRAGE_LOGS:-debug}
echo "Log level: $MIRAGE_LOG"

if [ -e /dev/net/tun ] ; then
  echo "Setting up tap0 device"
  IP=$(ip -o -f inet address show eth0 | awk '{print $4}')
  echo "Detected IP: $IP"
  GW=$(ip -o -f inet route list | grep "^default via" | head -1 | awk '{print $3}')
  echo "Detected gateway: $GW"
  ip address delete $IP dev eth0
  tunctl -t tap0
  brctl addbr br0
  brctl addif br0 eth0
  brctl addif br0 tap0
  ifconfig br0 up
  ifconfig tap0 up

  if [ -e /dev/kvm ] ; then
    echo "##### Starting keyfender as kvm instance #####"
    /ukvm-bin --net=tap0 --mem=$KEYFENDER_MEM /keyfender.ukvm --ipv4=$IP --ipv4-gateway=$GW --nameserver=$NS --logs=$MIRAGE_LOGS $ARGS
  else
    echo "##### Starting keyfender with direct network #####"
    /keyfender.direct --interface tap0 --ipv4 $IP --ipv4-gateway=$GW --nameserver=$NS $ARGS
  fi
else
  echo "##### Starting keyfender on unix socket #####"
  /keyfender.socket --nameserver=$NS $ARGS
fi
