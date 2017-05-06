#!/bin/sh

export OPAMYES=1

pushd $HOME

#install opam
opam init
eval $(opam config env)
opam switch $(opam switch | grep Official.*release | tail -1 | awk '{ print $3; }')
eval $(opam config env)
opam update -u

# install mirage
opam install mirage
opam upgrade mirage

# install sbt
mkdir $HOME/bin
curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > ~/bin/sbt && chmod 0755 ~/bin/sbt

popd
