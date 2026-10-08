# Cloud Run Jobs image for the CardDemo nightly batch stream.
# Build from the repository root:  docker build -f modern/carddemo-batch/Dockerfile -t carddemo-batch .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
COPY app/data/ASCII app/data/ASCII
COPY modern modern
RUN mvn -q -f modern/pom.xml -DskipTests -Djacoco.skip=true package

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 carddemo && mkdir -p /work/out && chown -R carddemo /work
WORKDIR /work
COPY --from=build /src/modern/carddemo-batch/target/carddemo-batch.jar /app/carddemo-batch.jar
USER carddemo
# All runtime configuration is environment-driven (see CardDemoBatchProperties / application.yml):
#   CARDDEMO_JOB_NAME, CARDDEMO_BUSINESS_TIMESTAMP, CARDDEMO_SEED_DIR, CARDDEMO_OUT_DIR,
#   SPRING_PROFILES_ACTIVE=postgres[,pubsub], CARDDEMO_DB_URL / _USER / _PASSWORD (from Secret Manager),
#   CARDDEMO_PUBSUB_PROJECT, CARDDEMO_PUBSUB_TOPIC.
# CARDDEMO_LOAD_SEEDS (replace masters from seed files) and CARDDEMO_SEED_DEFAULTS (fall back to bundled
# sample extracts) are off by default: leave them off for scheduled runs against a live database.
ENV CARDDEMO_OUT_DIR=/work/out \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
ENTRYPOINT ["java", "-jar", "/app/carddemo-batch.jar"]
