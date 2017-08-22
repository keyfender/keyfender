# Container builds

This directory contains scripts to easily build keyfender into very small containers. To achieve that the build is done in several steps:

1. A builder base image is built, that contains all the software necessary to
build keyfender. (`docker/container-scripts/buildbase.sh`)

2. Based on the base image the main builder image is built by adding the source
code. (`src/Dockerfile.build`)

3. Then this image can be run to build the actual keyfender application.
(`docker/run-builder.sh`)

4. The results of the builds (by default under /tmp/keyfender_builder_out/) can then be used to build small runner images. (`docker/Dockerfile.run`)

Steps 1-2 are done by the `docker/build-builder.sh` script.

To increase the build time significantly, the environment variables `APK_CACHE`
and `OPAM_DIR` can be set, that point to cache directories on the building host
(docker will create them, if they don't exist already). The environment variable
`OCAML_VERSION` can be used to set the desired opam switch.

The script `build-all-in-one.sh` creates a runner container that includes three
variants of the unikernel (ukvm and unix with direct and socket network). The
container will select automatically the correct one based on the privileges. The
result of this build is available as `keyfender/keyfender` in the docker registry.
