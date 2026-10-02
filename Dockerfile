# syntax=docker/dockerfile:1@sha256:4edf897a3ffa55b89f906fc8cc78afdb3f1834cc9c7083565e611a8a7d5fe99e
# https://depot.dev/docs/container-builds/optimal-dockerfiles/java-gradle-dockerfile

# The natives are compiled by the natives-compile stage,
#   unless a directory of the context holding prebuilt ones is given (what CI does)
ARG PREBUILT_NATIVES
ARG NATIVES_STAGE=${PREBUILT_NATIVES:+prebuilt}

FROM docker.io/eclipse-temurin:26-jdk@sha256:72f06e2d7b40aaf9d237ff46611f2c3001e799f8d510c12170f4ceed847676db AS gradle

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
RUN mkdir nohorny-common nohorny-native nohorny-client nohorny-server

FROM gradle AS natives-compile

RUN apt-get update && apt-get install -y --no-install-recommends cmake ninja-build g++ \
    && rm -rf /var/lib/apt/lists/*

ENV CMAKE_GENERATOR=Ninja

COPY nohorny-native/CMakeLists.txt nohorny-native/
COPY nohorny-native/cmake/ nohorny-native/cmake/
COPY nohorny-native/src/ nohorny-native/src/

# The cmake directory is cached since OpenCV takes a while to compile
RUN --mount=type=cache,target=/cache/.gradle \
    --mount=type=cache,target=/app/nohorny-native/build/cmake \
    gradle :nohorny-native:cmakeBuild --no-daemon --stacktrace \
    && cp -r nohorny-native/build/generated/native /natives

FROM scratch AS natives-prebuilt

ARG PREBUILT_NATIVES
COPY ${PREBUILT_NATIVES}/ /natives/

# Only the selected stage is built
FROM natives-${NATIVES_STAGE:-compile} AS natives

FROM gradle AS build

ARG IS_RELEASE=false

ENV GRADLE_ARGS="-Pis_release=${IS_RELEASE} -Pprebuilt_natives=prebuilt-natives"

RUN --mount=type=cache,target=/cache/.gradle \
    gradle dependencies --no-daemon --stacktrace ${GRADLE_ARGS}

COPY nohorny-common/src/ nohorny-common/src/
COPY nohorny-server/src/ nohorny-server/src/
COPY --from=natives /natives prebuilt-natives/

RUN --mount=type=cache,target=/cache/.gradle \
    gradle build -x test --no-daemon --stacktrace --build-cache ${GRADLE_ARGS}

FROM docker.io/eclipse-temurin:26-jre@sha256:4a9c6bc048bbe4782482fe376bb5f753a3ac6d91381bc133cfd1ae367b33d8a9 AS runtime

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

USER appuser

EXPOSE 8080

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar nohorny-server.jar start"]
