FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY apps/backend/pom.xml apps/backend/
COPY packages/java/emf-compare-standalone/pom.xml packages/java/emf-compare-standalone/
COPY packages/java/mde-etl-runner/pom.xml packages/java/mde-etl-runner/
COPY packages/java/mde-evl-validator/pom.xml packages/java/mde-evl-validator/
COPY packages/java/mde-m2t-runner/pom.xml packages/java/mde-m2t-runner/
COPY packages/java/platform-artifact/pom.xml packages/java/platform-artifact/
COPY packages/java/platform-assistant/pom.xml packages/java/platform-assistant/
COPY packages/java/platform-export/pom.xml packages/java/platform-export/
COPY packages/java/platform-identity/pom.xml packages/java/platform-identity/
COPY packages/java/platform-kernel/pom.xml packages/java/platform-kernel/
COPY packages/java/platform-model/pom.xml packages/java/platform-model/
COPY packages/java/platform-modeling/pom.xml packages/java/platform-modeling/
COPY packages/java/platform-project/pom.xml packages/java/platform-project/
COPY packages/java/platform-storage-api/pom.xml packages/java/platform-storage-api/
COPY packages/java/platform-storage-postgres/pom.xml packages/java/platform-storage-postgres/
COPY packages/java/platform-transformation/pom.xml packages/java/platform-transformation/
COPY tools/mde-cli/pom.xml tools/mde-cli/
COPY tools/mde-etl-cli/pom.xml tools/mde-etl-cli/
COPY tools/mde-evl-cli/pom.xml tools/mde-evl-cli/
COPY tools/mde-m2t-cli/pom.xml tools/mde-m2t-cli/
# Warm runtime/build dependencies once per POM change; omit analysis and unused lifecycle tools.
RUN --mount=type=cache,id=modriss-maven,target=/root/.m2,sharing=locked \
    mvn -B -pl apps/backend -am org.apache.maven.plugins:maven-dependency-plugin:3.9.0:go-offline -DincludeScope=runtime \
    -DexcludeReactor=true -DexcludeArtifactIds=maven-checkstyle-plugin,maven-pmd-plugin,spotbugs-maven-plugin,spotless-maven-plugin,maven-site-plugin,maven-clean-plugin,maven-install-plugin,maven-deploy-plugin
COPY apps/backend/src apps/backend/src
COPY packages/java packages/java
COPY config config
COPY mde mde
RUN --mount=type=cache,id=modriss-maven,target=/root/.m2,sharing=locked \
    mvn -pl apps/backend -am package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre
WORKDIR /app
# hadolint ignore=DL3008
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN useradd --system --uid 10001 modriss
RUN mkdir -p /app/uploads /app/logs /app/storage \
    && chown -R modriss:modriss /app/uploads /app/logs /app/storage
COPY --from=build /workspace/apps/backend/target/backend-*.jar /app/backend.jar
COPY --from=build /workspace/mde /app/mde
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
