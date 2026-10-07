FROM node:24.19.0-bookworm-slim@sha256:a9f5f7c91a432850b2a8a7797adf5eadb6c733ceed61167806cee7ea7fbc29df AS ui
WORKDIR /build
COPY frontend/package*.json ./
RUN --mount=type=secret,id=build_ca,target=/tmp/build-ca.pem \
    if [ -f /tmp/build-ca.pem ]; then NODE_EXTRA_CA_CERTS=/tmp/build-ca.pem npm ci --no-audit --no-fund; else npm ci --no-audit --no-fund; fi
COPY frontend/ ./
RUN npm run build

FROM maven:3.9.11-eclipse-temurin-21@sha256:6fdc855a6ed81d288ca7ca37ac6ff5e9308b612485c0801d70b25a858c83d237 AS backend
WORKDIR /build
COPY backend/pom.xml ./
COPY backend/src ./src
COPY --from=ui /build/dist/browser/ ./src/main/resources/static/
RUN --mount=type=secret,id=maven_settings,target=/tmp/settings.xml \
    --mount=type=secret,id=java_truststore,target=/tmp/cacerts \
    if [ -f /tmp/cacerts ]; then export MAVEN_OPTS="-Djavax.net.ssl.trustStore=/tmp/cacerts"; fi; \
    if [ -f /tmp/settings.xml ]; then mvn -s /tmp/settings.xml -B package -DskipTests; else mvn -B package -DskipTests; fi

FROM eclipse-temurin:21-jre-jammy@sha256:f04fb34e053148344e83317976114ec3f37e4b830ec8bdab5a2fe3cecd7d010b
WORKDIR /app
COPY --from=backend /build/target/service-proof-1.0.0.jar app.jar
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java","-XX:MaxRAMPercentage=70","-jar","/app/app.jar"]
