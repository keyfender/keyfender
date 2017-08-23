#!/bin/bash -ex

DIR=$(dirname "$(readlink -f "$0")")

# build the builder image
$DIR/build-builder.sh

mkdir -p $DIR/build

# run the builder
$DIR/run-builder.sh "mirage config --net=direct && make depend && make"
cp /tmp/keyfender_builder_out/_build/main.native $DIR/build/keyfender.direct

$DIR/run-builder.sh "mirage config --net=socket && make depend && make"
cp /tmp/keyfender_builder_out/_build/main.native $DIR/build/keyfender.socket

$DIR/run-builder.sh "mirage config -t ukvm --net=direct && make depend && make"
cp /tmp/keyfender_builder_out/keyfender.ukvm $DIR/build/
cp /tmp/keyfender_builder_out/ukvm-bin $DIR/build/

docker build -f $DIR/Dockerfile.run -t keyfender/keyfender $DIR
