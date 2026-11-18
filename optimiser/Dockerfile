# syntax=docker/dockerfile:1
# Build from the repo root, because the module needs the parent pom:
#   docker build -f optimiser/Dockerfile .

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY knowledge/pom.xml knowledge/
COPY optimiser/pom.xml optimiser/
COPY optimiser/src optimiser/src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -q -pl optimiser -am -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd --system --no-create-home app
WORKDIR /app
COPY --from=build /build/optimiser/target/optimiser-*.jar app.jar
USER app
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
