#!/bin/sh

export APK_CACHE=$HOME/.apk-cache
export OPAM_DIR=$HOME/.opam
export BUILD_UI=1

# build keyfender container
docker/build-all-in-one.sh

# run keyfender on port 4433
docker run -i --rm --device=/dev/net/tun:/dev/net/tun --cap-add=NET_ADMIN -p4433:4433 keyfender/keyfender &

# functional tests
cd tests/end-to-end/
sbt test
