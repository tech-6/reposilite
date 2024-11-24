# Build stage
FROM amazoncorretto:21-al2023-jdk AS build
RUN \
    yum install -y findutils
COPY . /home/reposilite-build
WORKDIR /home/reposilite-build

RUN \
  export GRADLE_OPTS="-Djdk.lang.Process.launchMechanism=vfork" && \
  chmod +x gradlew && \
  bash gradlew :reposilite-backend:shadowJar --no-daemon --stacktrace

# Build-time metadata stage
ARG BUILD_DATE
ARG VCS_REF
ARG VERSION
LABEL org.label-schema.build-date=$BUILD_DATE \
      org.label-schema.name="Reposilite" \
      org.label-schema.description="Lightweight repository management software dedicated for the Maven artifacts" \
      org.label-schema.url="https://reposilite.com" \
      org.label-schema.vcs-ref=$VCS_REF \
      org.label-schema.vcs-url="https://github.com/dzikoysk/reposilite" \
      org.label-schema.vendor="dzikoysk" \
      org.label-schema.version=$VERSION \
      org.label-schema.schema-version="1.0"

FROM amazoncorretto:21-al2023-headless AS correto-al2023
# Configure UID and GID
ARG UID="999"
ARG GID="999"

RUN yum install -y shadow-utils && yum clean all &&\
    mkdir -p /app/data &&  \
    mkdir -p /var/log/reposilite && \
    groupadd --gid "$GID" reposilite && \
    useradd \
    --system \
    --home-dir /app \
    --no-create-home \
    --no-user-group \
    --shell "/usr/sbin/nologin" \
    --gid "$GID" \
    --uid "$UID" reposilite

WORKDIR /app
COPY --from=build /home/reposilite-build/reposilite-backend/build/libs/reposilite-3*.jar reposilite.jar
COPY --from=build /home/reposilite-build/entrypoint.sh entrypoint.sh
RUN chmod +x /app/entrypoint.sh && \
    chown --recursive "$UID:$GID" /app
USER reposilite
VOLUME /app/data

HEALTHCHECK --interval=30s --timeout=30s --start-period=15s \
    --retries=3 CMD [ "sh", "-c", "URL=$(cat /app/data/.local/reposilite.address); echo -n \"curl $URL... \"; \
    (\
        curl -sf $URL > /dev/null\
    ) && echo OK || (\
        echo Fail && exit 2\
    )"]
ENTRYPOINT ["/app/entrypoint.sh"]
CMD []
EXPOSE 8080
