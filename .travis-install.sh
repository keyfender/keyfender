#!/bin/sh

# install sbt
mkdir -p $HOME/bin
curl -Ls https://git.io/sbt > ~/bin/sbt && chmod 0755 ~/bin/sbt
