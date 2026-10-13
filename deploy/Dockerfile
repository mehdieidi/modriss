FROM maven:3.9.11-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY apps/backend/pom.xml apps/backend/
COPY packages/java/emf-compare-standalone/pom.xml packages/java/emf-compare-standalone/
COPY packages/java/epsilon-model-transformation/pom.xml packages/java/epsilon-model-transformation/
COPY packages/java/epsilon-model-validation/pom.xml packages/java/epsilon-model-validation/
COPY packages/java/epsilon-text-generation/pom.xml packages/java/epsilon-text-generation/
COPY packages/java/platform-artifact-management/pom.xml packages/java/platform-artifact-management/
COPY packages/java/platform-modeling-assistant/pom.xml packages/java/platform-modeling-assistant/
COPY packages/java/platform-project-export/pom.xml packages/java/platform-project-export/
COPY packages/java/platform-user-authentication/pom.xml packages/java/platform-user-authentication/
COPY packages/java/platform-shared-types/pom.xml packages/java/platform-shared-types/
COPY packages/java/platform-model-workspace/pom.xml packages/java/platform-model-workspace/
COPY packages/java/platform-modeling-infrastructure/pom.xml packages/java/platform-modeling-infrastructure/
COPY packages/java/platform-project-management/pom.xml packages/java/platform-project-management/
COPY packages/java/platform-storage-api/pom.xml packages/java/platform-storage-api/
COPY packages/java/platform-storage-postgres/pom.xml packages/java/platform-storage-postgres/
COPY packages/java/platform-mde-pipeline/pom.xml packages/java/platform-mde-pipeline/
COPY tools/emfatic-to-ecore-cli/pom.xml tools/emfatic-to-ecore-cli/
COPY tools/model-transformer-cli/pom.xml tools/model-transformer-cli/
COPY tools/model-validator-cli/pom.xml tools/model-validator-cli/
COPY tools/code-generator-cli/pom.xml tools/code-generator-cli/
# Warm runtime/build dependencies once per POM change; omit analysis and unused lifecycle tools.
RUN --mount=type=cache,id=modriss-maven,target=/root/.m2,sharing=locked \
    mvn -B -pl apps/backend -am org.apache.maven.plugins:maven-dependency-plugin:3.9.0:go-offline -DincludeScope=runtime \
    -DexcludeReactor=true -DexcludeArtifactIds=maven-checkstyle-plugin,maven-pmd-plugin,spotbugs-maven-plugin,spotless-maven-plugin,maven-site-plugin,maven-clean-plugin,maven-install-plugin,maven-deploy-plugin
COPY apps/backend/src apps/backend/src
COPY packages/java packages/java
COPY config config
COPY methodology methodology
COPY docs/api/openapi/openapi.yaml docs/api/openapi/
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
COPY --from=build /workspace/methodology /app/methodology
USER 10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/backend.jar"]
