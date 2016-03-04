#!/bin/sh

export OPAMYES=1

#install opam
opam init
opam update -u
eval $(opam config env)

# install mirage
opam install mirage

# install sbt
mkdir $HOME/bin
curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > ~/bin/sbt && chmod 0755 ~/bin/sbt
