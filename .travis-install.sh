#!/bin/sh

export OPAMYES=1

pushd $HOME

# Get GCC 4.8
if [ ! -d $HOME/gcc/bin ]; then
    wget https://github.com/Viq111/travis-container-packets/releases/download/gcc-4.8.2/gcc.tar.bz2
    tar -xjf gcc.tar.bz2
    rm gcc.tar.bz2
fi

export PATH=$HOME/gcc/bin:$PATH
export LIBRARY_PATH=$HOME/gcc/lib64:$LIBRARY_PATH
export LD_LIBRARY_PATH=$HOME/gcc/lib64:$LD_LIBRARY_PATH
export CPLUS_INCLUDE_PATH=$HOME/gcc/include/c++/4.8.2:$CPLUS_INCLUDE_PATH
export CPATH=$HOME/gcc/include:$HOME/gcc/lib/gcc/x86_64-unknown-linux-gnu/4.8.2/include:$CPATH

# install binutils
if [ ! -d $HOME/binutils/bin ] ; then
    wget http://ftp.gnu.org/gnu/binutils/binutils-2.26.tar.bz2
    tar jxpf binutils-2.26.tar.bz2
    rm binutils-2.26.tar.bz2
    pushd binutils-2.26
    ./configure --prefix=$HOME/binutils
    make
    make install
    popd
    rm -rfv binutils/share
fi

export PATH=$HOME/binutils/bin:$PATH
export LIBRARY_PATH=$HOME/binutils/lib:$LIBRARY_PATH
export LD_LIBRARY_PATH=$HOME/binutils/lib:$LD_LIBRARY_PATH
export CPATH=$HOME/binutils/include:$CPATH

#install opam
opam init
opam update -u
eval $(opam config env)

# pin cohttp to 0.20.2 until
# https://github.com/inhabitedtype/ocaml-webmachine/issues/58
# is fixed
opam pin add cohttp 0.20.2

# install mirage
opam install mirage

# install sbt
mkdir $HOME/bin
curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > ~/bin/sbt && chmod 0755 ~/bin/sbt

popd
