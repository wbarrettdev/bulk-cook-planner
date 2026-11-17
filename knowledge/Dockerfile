# syntax=docker/dockerfile:1
# Build from the repo root, because the module needs the parent pom:
#   docker build -f knowledge/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY knowledge/pom.xml knowledge/
COPY knowledge/src knowledge/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl knowledge -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /build/knowledge/target/knowledge-*.jar app.jar
USER app
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
