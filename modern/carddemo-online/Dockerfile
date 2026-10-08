# syntax=docker/dockerfile:1
# Build from the repository root so the image can carry the legacy seed datasets:
#   docker build -f modern/carddemo-online/Dockerfile -t carddemo-online .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY modern/carddemo-online/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY modern/carddemo-online/src ./src
RUN mvn -B -q package -DskipTests -Djacoco.skip=true

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 carddemo
WORKDIR /app
COPY --from=build /src/target/carddemo-online.jar app.jar
# Same fixed-width seed files the legacy region loads (used when the database is empty).
COPY app/data/ASCII /app/data/ASCII
COPY app/data/EBCDIC /app/data/EBCDIC
USER 10001
# Cloud Run injects PORT; all other configuration (DB, JWT secret, Redis) comes from env / Secret Manager.
ENV PORT=8080 CARDDEMO_APP_DATA=/app/data JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
