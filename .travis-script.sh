#!/bin/sh

export APK_CACHE=$HOME/.apk-cache
export OPAM_DIR=$HOME/.opam

# build nethsm container
docker/build-all-in-one.sh

# run nethsm on port 4433
docker run -i --rm --device=/dev/net/tun:/dev/net/tun --cap-add=NET_ADMIN -p4433:4433 nethsm/nethsm &

# functional tests
cd tests/end-to-end/
sbt test
