FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

COPY pom.xml .
COPY src ./src
RUN mvn -B clean verify

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system studyhub \
    && useradd --system --gid studyhub studyhub \
    && mkdir -p /app/uploads/documents \
    && chown -R studyhub:studyhub /app/uploads

COPY --from=build --chown=studyhub:studyhub \
    /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

USER studyhub
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
