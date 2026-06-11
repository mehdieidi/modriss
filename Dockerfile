FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY . .
RUN --mount=type=cache,id=modless-maven,target=/root/.m2,sharing=locked \
    mvn -pl apps/backend -am package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 modless
COPY --from=build /workspace/apps/backend/target/backend-*.jar /app/backend.jar
COPY --from=build /workspace/mde /app/mde
USER modless
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
