# syntax=docker/dockerfile:1
# https://depot.dev/docs/container-builds/optimal-dockerfiles/java-gradle-dockerfile

FROM docker.io/eclipse-temurin:26-jdk AS build

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

COPY settings.gradle.kts ./
COPY build.gradle.kts ./
RUN mkdir nohorny-common nohorny-client nohorny-server

RUN --mount=type=cache,target=/cache/.gradle \
    gradle dependencies --no-daemon --stacktrace

COPY nohorny-common/src/ nohorny-common/src/
COPY nohorny-server/src/ nohorny-server/src/

RUN --mount=type=cache,target=/cache/.gradle \
    gradle build -x test --no-daemon --stacktrace --build-cache

FROM docker.io/eclipse-temurin:26-jre AS runtime

RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -m -d /app -s /bin/false appuser

WORKDIR /app

COPY --from=build --chown=appuser:appgroup /app/nohorny-server/build/libs/nohorny-server.jar nohorny-server.jar

ENV JAVA_OPTS="-server \
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -Djava.security.egd=file:/dev/./urandom"

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar nohorny-server.jar"]