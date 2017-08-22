#!/bin/bash -ex

CMD=$@
BUILDER_OUT=${BUILDER_OUT:-/tmp/keyfender_builder_out}

DIR=$(dirname "$(readlink -f "$0")")
. $DIR/env-setup.rc

docker rm -f keyfender_tmp >/dev/null 2>&1 && true
docker run --name keyfender_tmp \
  $BUILD_VOLUMES \
  keyfender_builder sh -c "$CMD"
rm -rf $BUILDER_OUT
docker cp keyfender_tmp:/src $BUILDER_OUT
docker rm keyfender_tmp
