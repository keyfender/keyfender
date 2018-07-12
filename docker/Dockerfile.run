FROM alpine

EXPOSE 4433 8080
RUN set -ex ;\
    apk add --no-cache --purge -U gmp ;\
    adduser -D keyfender ;\
    rm -rf /var/cache/apk/*
ADD container-scripts/start.sh /
ADD build/. /
CMD []
ENTRYPOINT ["/start.sh"]
