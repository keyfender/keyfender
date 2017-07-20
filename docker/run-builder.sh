#!/bin/bash -ex

CMD=$@
BUILDER_OUT=${BUILDER_OUT:-/tmp/nethsm_builder_out}

DIR=$(dirname "$(readlink -f "$0")")
. $DIR/env-setup.rc

docker rm -f nethsm_tmp >/dev/null 2>&1 && true
docker run --name nethsm_tmp \
  $BUILD_VOLUMES \
  nethsm_builder sh -c "$CMD"
rm -rf $BUILDER_OUT
docker cp nethsm_tmp:/src $BUILDER_OUT
docker rm nethsm_tmp
