# Built from Alpine package (pinned) – avoids unmaintained third-party pgbouncer images.
FROM alpine:3.22
RUN apk add --no-cache pgbouncer postgresql17-client \
 && adduser -S -D -H pgbouncer \
 && mkdir -p /etc/pgbouncer /var/run/pgbouncer && chown -R pgbouncer /etc/pgbouncer /var/run/pgbouncer
COPY pgbouncer.ini /etc/pgbouncer/pgbouncer.ini
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh
USER pgbouncer
EXPOSE 6432
ENTRYPOINT ["/entrypoint.sh"]
