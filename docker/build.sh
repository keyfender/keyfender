#!/bin/sh -ex

docker build -f src/Dockerfile.build -t nethsm_builder src
docker rm -f nethsm_tmp && true
docker run -t --name nethsm_tmp nethsm_builder
docker cp nethsm_tmp:/src/_build/main.native docker/nethsm
docker rm nethsm_tmp
docker build -f docker/Dockerfile.run -t nethsm/nethsm docker
rm docker/nethsm
