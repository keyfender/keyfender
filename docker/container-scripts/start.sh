#!/bin/sh -e

ARGS=$*

echo "##### Keyfender container setup #####"

[ -n "$KEYFENDER_HTTP_PORT" ] && ARGS="$ARGS --http=$KEYFENDER_HTTP_PORT"
[ -n "$KEYFENDER_HTTPS_PORT" ] && ARGS="$ARGS --https=$KEYFENDER_HTTPS_PORT"
[ -n "$KEYFENDER_PASSWORD" ] && ARGS="$ARGS --password=$KEYFENDER_PASSWORD"
[ -n "$KEYFENDER_IRMIN_URL" ] && ARGS="$ARGS --irmin-url=$KEYFENDER_IRMIN_URL"
[ -n "$KEYFENDER_MASTERKEY" ] && ARGS="$ARGS --masterkey=$KEYFENDER_MASTERKEY"

NS=${KEYFENDER_NS:-$(grep ^nameserver /etc/resolv.conf | head -1 | awk '{print $2}')}
echo "Nameserver: $NS"
ARGS="$ARGS --nameserver=$NS"

KEYFENDER_MEM=${KEYFENDER_MEM:-32}

export MIRAGE_LOGS=${MIRAGE_LOGS:-debug}
echo "Log level: $MIRAGE_LOGS"
ARGS="$ARGS --logs=$MIRAGE_LOGS"

if [ -e /dev/net/tun ] ; then
  echo "Setting up tap0 device"
  IP=$(ip -o -f inet address show eth0 | awk '{print $4}')
  echo "Detected IP: $IP"
  GW=$(ip -o -f inet route list | grep "^default via" | head -1 | awk '{print $3}')
  echo "Detected gateway: $GW"
  ip address delete $IP dev eth0
  tunctl -t tap0 -u keyfender
  brctl addbr br0
  brctl addif br0 eth0
  brctl addif br0 tap0
  ifconfig br0 up
  ifconfig tap0 up

  ARGS="$ARGS --ipv4=$IP --ipv4-gateway=$GW"

  if [ -e /dev/kvm ] ; then
    echo "##### Starting keyfender as kvm instance #####"
    exec su -l keyfender -c "/ukvm-bin --net=tap0 --mem=$KEYFENDER_MEM /keyfender.ukvm $ARGS"
  else
    echo "##### Starting keyfender with direct network #####"
    exec su -l keyfender -c "/keyfender.direct --interface tap0 $ARGS"
  fi
else
  echo "##### Starting keyfender on unix socket #####"
  exec su -l keyfender -c "/keyfender.socket $ARGS"
fi
