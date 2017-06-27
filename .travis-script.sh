#!/bin/sh

export OPAMYES=1
export FLAGS="-vv"
export DEPEXT=
export MIRAGE_LOGS=debug

# build nethsm
eval $(opam config env)
MODE=xen NET=direct make configure depend build
MODE=unix NET=direct make configure depend build
docker/build.sh

# run nethsm on port 4433
docker run -i --rm -p4433:4433 nethsm/nethsm &

# functional tests
cd tests/end-to-end/
sbt test
