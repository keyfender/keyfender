#!/bin/sh -ex

IP=$(ip -o -f inet address show eth0 | awk '{print $4}')
ip address delete $IP dev eth0
tunctl -t tap0
brctl addbr br0
brctl addif br0 eth0
brctl addif br0 tap0
ifconfig br0 up
ifconfig tap0 up

export MIRAGE_LOGS=${MIRAGE_LOGS:-debug}

/nethsm --interface tap0 --ipv4 $IP
