#!/bin/sh -e

export MIRAGE_LOGS=${MIRAGE_LOGS:-debug}

if [ -e /dev/net/tun ] ; then
  IP=$(ip -o -f inet address show eth0 | awk '{print $4}')
  ip address delete $IP dev eth0
  tunctl -t tap0
  brctl addbr br0
  brctl addif br0 eth0
  brctl addif br0 tap0
  ifconfig br0 up
  ifconfig tap0 up

  if [ -e /dev/kvm ] ; then
    echo "##### Starting keyfender as kvm instance #####"
    /ukvm-bin --net=tap0 /keyfender.ukvm --ipv4 $IP
  else
    echo "##### Starting keyfender with direct network #####"
    /keyfender.direct --interface tap0 --ipv4 $IP
  fi
else
  echo "##### Starting keyfender on unix socket #####"
  /keyfender.socket
fi
