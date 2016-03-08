#!/bin/sh

export OPAMYES=1

export PATH=$HOME/gcc/bin:$PATH
export LIBRARY_PATH=$HOME/gcc/lib64:$LIBRARY_PATH
export LD_LIBRARY_PATH=$HOME/gcc/lib64:$LD_LIBRARY_PATH
export CPLUS_INCLUDE_PATH=$HOME/gcc/include/c++/4.8.2:$CPLUS_INCLUDE_PATH
export CPATH=$HOME/gcc/include:$HOME/gcc/lib/gcc/x86_64-unknown-linux-gnu/4.8.2/include:$CPATH

export PATH=$HOME/binutils/bin:$PATH
export LIBRARY_PATH=$HOME/binutils/lib:$LIBRARY_PATH
export LD_LIBRARY_PATH=$HOME/binutils/lib:$LD_LIBRARY_PATH
export CPATH=$HOME/binutils/include:$CPATH

gcc --version

# build nethsm
eval $(opam config env)
MODE=xen NET=direct make configure depend build
MODE=unix NET=direct make configure depend build
MODE=unix NET=socket make configure depend build

# run nethsm on port 8080
make run 2>&1 | sed 's/^/[nethsm] /' &

# functional tests
cd tests/end-to-end/
sbt test
