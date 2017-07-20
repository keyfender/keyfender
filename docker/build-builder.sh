#!/bin/bash -ex

DIR=$(dirname "$(readlink -f "$0")")
source $DIR/env-setup.rc

OCAML_VERSION=${OCAML_VERSION:-4.04.2}

# Build the buildbase
# Since Dockerfile builds don't allow volume mounts, but we want to use cache
# directories for speedup, we run the build in a temporary container instead,
# and commit the result into an image.
docker rm -f nethsm_tmp >/dev/null 2>&1 && true
docker run --name nethsm_tmp \
  $BUILD_VOLUMES \
  -v $DIR/container-scripts/buildbase.sh:/buildbase.sh:ro \
  -e OCAML_VERSION=$OCAML_VERSION \
  alpine /buildbase.sh
docker commit \
  -c 'USER opam' \
  -c 'WORKDIR /src' \
  -c 'ENTRYPOINT [ "opam", "config", "exec", "--" ]' \
  -c 'ENV OPAMYES=1 FLAGS=-vv DEPEXT=' \
  -c 'CMD [ "sh" ]' \
  nethsm_tmp nethsm_buildbase
docker rm nethsm_tmp

# Build the builder image with the sources
docker build -f $DIR/../src/Dockerfile.build -t nethsm_builder $DIR/../src
