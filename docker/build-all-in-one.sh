#!/bin/bash -ex

DIR=$(dirname "$(readlink -f "$0")")

# build the builder image
$DIR/build-builder.sh

mkdir -p $DIR/build

# run the builder
$DIR/run-builder.sh "mirage config --net=direct && make depend && make"
cp /tmp/nethsm_builder_out/_build/main.native $DIR/build/nethsm.direct

$DIR/run-builder.sh "mirage config --net=socket && make depend && make"
cp /tmp/nethsm_builder_out/_build/main.native $DIR/build/nethsm.socket

$DIR/run-builder.sh "mirage config -t ukvm --net=direct && make depend && make"
cp /tmp/nethsm_builder_out/nethsm.ukvm $DIR/build/
cp /tmp/nethsm_builder_out/ukvm-bin $DIR/build/

docker build -f $DIR/Dockerfile.run -t nethsm/nethsm $DIR
