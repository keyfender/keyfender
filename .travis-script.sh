#!/bin/sh

export APK_CACHE=$HOME/.apk-cache
export OPAM_DIR=$HOME/.opam
export BUILD_UI=1

# build keyfender container
docker/build-all-in-one.sh

docker network create -d bridge --subnet 10.99.99.0/24 --gateway 10.99.99.1 \
  dockernet

docker run -i --rm --net dockernet --ip 10.99.99.3 --name irmin \
  -v $OPAM_DIR:/home/opam/.opam keyfender_buildbase irmin init \
  -a http://0.0.0.0:8081 -d --verbosity=info -s mem 2>&1 \
  | sed "s/^/<irmin> /" &

# run keyfender on port 4433
docker run -i --rm -p4433:4433 --privileged --net dockernet --ip 10.99.99.2 \
  keyfender/keyfender --masterkey 000102030405060708090A0B0C0D0E0F \
  --irmin http://10.99.99.3:8081 2>&1 | sed "s/^/<keyfender> /" &

# functional tests
cd tests/end-to-end/
sbt test 2>&1 | sed "s/^/<sbt> /"
