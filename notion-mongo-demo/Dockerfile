# syntax=docker/dockerfile:1
# Build stage
FROM maven:3.9 AS build
WORKDIR /app

# Copy root pom.xml
COPY pom.xml .

# Copy all subproject pom.xml files (automatically includes new projects)
COPY --parents */pom.xml ./

# Cached layer
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline -pl notion-mongo-demo -am

COPY notion-mongo-demo/src ./notion-mongo-demo/src

FROM build AS test
RUN --mount=type=cache,target=/root/.m2 \
    mvn verify

# Package stage
FROM build AS package
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -pl notion-mongo-demo -am -DskipTests

# --- FINAL RUNTIME STAGE ---
FROM eclipse-temurin:21-jre-jammy AS final

RUN addgroup --system spring && adduser --system --ingroup spring springuser

# Copy the entrypoint script from the project-a directory
COPY --chown=springuser:spring notion-mongo-demo/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

USER springuser
WORKDIR /app

# Copy the JAR from the project-a target directory
COPY --from=package --chown=springuser:spring /app/notion-mongo-demo/target/*.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s \
  CMD curl -f http://localhost:8080/hello || exit 1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]