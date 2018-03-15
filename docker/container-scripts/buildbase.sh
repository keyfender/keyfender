#!/bin/sh -ex

apk update
apk upgrade
#apk add alpine-sdk openssh bash nano ncurses-dev rsync xz opam aspcud
apk add gcc make autoconf patch gmp-dev m4 opam aspcud musl-dev sudo bash libressl-dev linux-headers zlib-dev git
adduser -D opam
chown -R opam:opam /home/opam # in case it's bind mounted
echo 'opam ALL=(ALL:ALL) NOPASSWD:ALL' > /etc/sudoers.d/opam
chmod 440 /etc/sudoers.d/opam
chown root:root /etc/sudoers.d/opam
sed -i.bak 's/^Defaults.*requiretty//g' /etc/sudoers
export OCAML_VERSION
su - opam -c "OCAML_VERSION=$OCAML_VERSION exec sh" <<'EOF'
set -ex
export OPAMYES=1

#install opam
opam init
eval $(opam config env)
# 4.05.0 doesnt work yet
#opam switch $(opam switch | grep Official.*release | tail -1 | awk '{ print $3; }')
opam switch $OCAML_VERSION
eval $(opam config env)

# bring cache up-to-date
opam update

# remove all pinnings from cache
opam pin -n remove $(opam pin list -s)

# webmachine 0.4.0 requires calendar which is not compatible with
# mirage
# https://github.com/inhabitedtype/ocaml-webmachine/issues/73
opam pin -n add webmachine https://github.com/ansiwen/ocaml-webmachine.git#remove-calendar
opam pin -n add irmin-http https://github.com/ansiwen/irmin.git#new-webmachine

# https://github.com/mirleft/ocaml-nocrypto/issues/143
opam pin -n add -k version ppx_sexp_conv v0.10.0

opam upgrade

# to use the builder image also as a irmin server runner for tests
opam install irmin-unix

# install mirage and it's system dependencies
opam depext -i mirage

# make sure we are in a stable state
opam upgrade
opam upgrade --fixup
EOF

if [ -n "$BUILD_UI" ] ; then
  apk add --no-cache --purge -U nodejs nodejs-npm git
  git clone --depth=1 https://github.com/keyfender/keyfender-ui
  cd keyfender-ui
  npm install
  npm run build
  mkdir /htdocs
  cp -a index.html dist /htdocs
  cd ..
  rm -rf /root/.npm
  rm -rf keyfender-ui
  apk del nodejs
fi
