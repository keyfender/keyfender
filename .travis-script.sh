#!/bin/sh

export OPAMYES=1
export FLAGS="-vv"
export DEPEXT=
export MIRAGE_LOGS=debug

# build nethsm
eval $(opam config env)
MODE=xen NET=direct make configure depend build
MODE=unix NET=direct make configure depend build
MODE=unix NET=socket make configure depend build

# run nethsm on port 8080
make run 2>&1 &

# functional tests
cd tests/end-to-end/
sbt test
