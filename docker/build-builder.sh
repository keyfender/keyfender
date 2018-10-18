#!/bin/bash -ex

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
source $DIR/env-setup.rc

OCAML_VERSION=${OCAML_VERSION:-4.07.0}

# Build the buildbase
# Since Dockerfile builds don't allow volume mounts, but we want to use cache
# directories for speedup, we run the build in a temporary container instead,
# and commit the result into an image.
docker rm -f keyfender_tmp >/dev/null 2>&1 && true
docker run --name keyfender_tmp \
  $BUILD_VOLUMES \
  -v $DIR/container-scripts/buildbase.sh:/buildbase.sh:ro \
  -e OCAML_VERSION=$OCAML_VERSION \
  -e BUILD_UI=$BUILD_UI \
  alpine /buildbase.sh
docker commit \
  -c 'USER opam' \
  -c 'WORKDIR /src' \
  -c 'ENTRYPOINT [ "opam", "config", "exec", "--", "sh", "-c"]' \
  -c 'ENV OPAMYES=1 FLAGS=-vv DEPEXT=' \
  -c 'CMD [ "sh" ]' \
  keyfender_tmp keyfender_buildbase
docker rm keyfender_tmp

# Build the builder image with the sources
docker build -f $DIR/../src/Dockerfile.build -t keyfender_builder $DIR/../src
