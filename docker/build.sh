#!/bin/sh -ex

docker build -f src/Dockerfile.build -t nethsm_builder src
docker rm -f nethsm_tmp >/dev/null 2>&1 && true
docker run -t --name nethsm_tmp nethsm_builder
docker cp nethsm_tmp:/src/_build/main.native docker/adds/nethsm
docker rm nethsm_tmp
docker build -f docker/Dockerfile.run -t nethsm/nethsm docker
