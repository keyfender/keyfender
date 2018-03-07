#!/bin/sh

export APK_CACHE=$HOME/.apk-cache
export OPAM_DIR=$HOME/.opam
export BUILD_UI=1

# build keyfender container
docker/build-all-in-one.sh

# run keyfender on port 4433
docker run -i --rm --device=/dev/net/tun:/dev/net/tun --cap-add=NET_ADMIN -p4433:4433 keyfender/keyfender --irmin http://irmin:8081 &

docker run -i --name irmin-build -v $OPAM_DIR:/home/opam/.opam keyfender_buildbase /bin/sh -c "opam install irmin-unix"
docker commit irmin-build irmin-img
docker run -i --rm --name irmin irmin-img irmin init -a http://0.0.0.0:8081 -d --verbosity=debug -s mem &

# functional tests
cd tests/end-to-end/
sbt test
