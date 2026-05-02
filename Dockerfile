# syntax=docker/dockerfile:1
# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Define module name
ARG MODULE_NAME

# Copy root pom.xml
COPY pom.xml .

# Copy all subproject pom.xml files (automatically includes new projects)
COPY --parents */pom.xml ./

# Cached layer
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B dependency:go-offline -pl ${MODULE_NAME} -am

COPY ${MODULE_NAME}/src ./${MODULE_NAME}/src

FROM build AS test
ARG MODULE_NAME
RUN --mount=type=cache,target=/root/.m2 \
    mvn verify -pl ${MODULE_NAME} -am

# Package stage
FROM build AS package
ARG MODULE_NAME
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B package -pl ${MODULE_NAME} -am -DskipTests

# --- FINAL RUNTIME STAGE ---
FROM eclipse-temurin:21-jre-jammy AS final
ARG MODULE_NAME
RUN addgroup --system spring && adduser --system --ingroup spring springuser

# Copy the entrypoint script from the project directory
COPY --chown=springuser:spring ${MODULE_NAME}/entrypoint.sh /usr/local/bin/entrypoint.sh
RUN chmod +x /usr/local/bin/entrypoint.sh

USER springuser
WORKDIR /app

# Copy the JAR from the project target directory
COPY --from=package --chown=springuser:spring /app/${MODULE_NAME}/target/*.jar app.jar  

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s \
  CMD curl -f http://localhost:8080/hello || exit 1

ENTRYPOINT ["/usr/local/bin/entrypoint.sh"]

CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]