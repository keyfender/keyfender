#!/bin/sh

# build nethsm
eval $(opam config env)
#MODE=xen NET=direct make configure depend build
MODE=unix NET=direct make configure depend build
MODE=unix NET=socket make configure depend build

# run nethsm on post 8080
DEBUG_PATH=1 make run 2>&1 | sed 's/^/[nethsm] /' &

# functional tests
cd tests/end-to-end/
sbt test
