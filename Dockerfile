# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e
# https://depot.dev/docs/container-builds/optimal-dockerfiles/java-gradle-dockerfile

FROM docker.io/eclipse-temurin:26-jdk@sha256:a45ef8c9f6304700665c5a5ac7250122e951831593bfa95859c295aea67f7d16 AS gradle

ENV GRADLE_HOME=/opt/gradle \
    GRADLE_USER_HOME=/cache/.gradle \
    GRADLE_OPTS="-Dorg.gradle.daemon=false -Dorg.gradle.parallel=true -Dorg.gradle.caching=true -Xmx2g"

COPY gradle/wrapper/gradle-wrapper.properties .

RUN apt-get update && apt-get install -y --no-install-recommends unzip wget \
    && GRADLE_VERSION=$(sed -nE 's/^distributionUrl=.*gradle-([0-9.]+)-(bin|all)\.zip/\1/p' gradle-wrapper.properties) \
    && echo "Using Gradle version: $GRADLE_VERSION" \
    && wget -q "https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip" \
    && unzip "gradle-$GRADLE_VERSION-bin.zip" -d /opt \
    && ln -s "/opt/gradle-$GRADLE_VERSION" /opt/gradle \
    && rm "gradle-$GRADLE_VERSION-bin.zip" \
    && apt-get remove -y unzip wget \
    && rm -rf /var/lib/apt/lists/*

ENV PATH="${GRADLE_HOME}/bin:${PATH}"

WORKDIR /app

COPY settings.gradle.kts build.gradle.kts ./
COPY gradle/libs.versions.toml gradle/
COPY build-logic/ build-logic/
COPY nohorny-common/build.gradle.kts nohorny-common/
COPY nohorny-plugin/build.gradle.kts nohorny-plugin/
COPY nohorny-frontend/build.gradle.kts nohorny-frontend/
COPY nohorny-server/build.gradle.kts nohorny-server/


FROM ghcr.io/pnpm/pnpm:12.16.1@sha256:33081d3d2c96ba582d316cfd630f0f43961d9c60ff919b50edd1835a1462976b AS frontend

RUN pnpm runtime set node 24 -g

WORKDIR /app

COPY nohorny-frontend/package.json nohorny-frontend/pnpm-lock.yaml nohorny-frontend/pnpm-workspace.yaml ./

# The store cache lives outside /pnpm. A mount over /pnpm/store would hide the Node.js that pnpm installed
RUN --mount=type=cache,id=pnpm,target=/var/cache/pnpm \
    pnpm install --store-dir /var/cache/pnpm --frozen-lockfile

COPY nohorny-frontend/ ./

# The same layout as the bundleFrontend task, the static/ resources of the server
RUN pnpm run build \
    && mkdir /frontend \
    && cp -r dist/client /frontend/static \
    && rm -rf /frontend/static/.vite


FROM gradle AS build

ARG IS_RELEASE=false

ENV GRADLE_ARGS="-Pis_release=${IS_RELEASE} -Pprebuilt_frontend=prebuilt-frontend"

RUN --mount=type=cache,target=/cache/.gradle \
    gradle dependencies --no-daemon --stacktrace ${GRADLE_ARGS}

COPY nohorny-common/src/ nohorny-common/src/
COPY nohorny-server/src/ nohorny-server/src/
COPY --from=frontend /frontend prebuilt-frontend/

RUN --mount=type=cache,target=/cache/.gradle \
    gradle :nohorny-server:build -x test --no-daemon --stacktrace --build-cache ${GRADLE_ARGS}


FROM docker.io/eclipse-temurin:26-jre@sha256:0dbf52b3861a84e2a19c503245504e5a71a5f071ccb9c48997efd4a3454a52b7 AS runtime

RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m -d /app -s /bin/false appuser && \
    install -d -o appuser -g appgroup -m 0755 /data

WORKDIR /app

COPY --from=build --chown=appuser:appgroup /app/nohorny-server/build/libs/nohorny-server.jar nohorny-server.jar

ENV JAVA_OPTS="-server \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    --enable-native-access=ALL-UNNAMED \
    -Djava.security.egd=file:/dev/./urandom"

ENV NOHORNY_STORAGE_DATABASE=/data/database.sqlite \
    NOHORNY_STORAGE_IMAGES=/data/images

VOLUME ["/data"]

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar nohorny-server.jar"]
