#!/bin/sh

export OPAMYES=1

pushd $HOME

#install opam
opam init
eval $(opam config env)
opam switch $(opam switch | grep Official.*release | tail -1 | awk '{ print $3; }')
eval $(opam config env)

# remove all pinnings from cache
opam pin -n remove $(opam pin list -s)

# webmachine 0.4.0 requires calendar which is not compatible with
# mirage
# https://github.com/inhabitedtype/ocaml-webmachine/issues/73
opam pin -n add webmachine 0.3.2

# install mirage
opam install mirage

# bring cache up-to-date
opam update

# make sure we are in a stable state
opam upgrade --fixup

# install sbt
mkdir $HOME/bin
curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > ~/bin/sbt && chmod 0755 ~/bin/sbt

sudo ip tuntap add tap100 mode tap
sudo ip addr add 10.0.0.1/24 dev tap100
sudo ip link set dev tap100 up

popd
