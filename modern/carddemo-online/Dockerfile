# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q package -DskipTests -Djacoco.skip=true

FROM eclipse-temurin:21-jre-jammy
RUN useradd --system --uid 10001 carddemo
WORKDIR /app
COPY --from=build /src/target/carddemo-online.jar app.jar
USER 10001
# Cloud Run injects PORT; all other configuration (DB, JWT secret, Redis) comes from env / Secret Manager.
ENV PORT=8080 JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
