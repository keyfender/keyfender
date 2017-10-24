FROM keyfender_buildbase

ADD . /src
RUN set -ex ;\
  [ -d /htdocs ] && sudo cp -a /htdocs /src/ ;\
  sudo chown -R opam /src
