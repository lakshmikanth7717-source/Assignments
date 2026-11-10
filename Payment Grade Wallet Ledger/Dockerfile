# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-17 AS dependencies
WORKDIR /workspace
ARG LOCAL_CA_CACHE_KEY=none
RUN --mount=type=secret,id=local_ca \
    if [ -s /run/secrets/local_ca ]; then keytool -importcert -noprompt -cacerts \
    -storepass changeit -alias wallet-local-ca -file /run/secrets/local_ca; fi
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

FROM dependencies AS tests
COPY src ./src
CMD ["mvn", "-B", "-ntp", "verify"]

FROM tests AS build
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:17-jre-jammy AS runtime
RUN groupadd --system wallet && useradd --system --gid wallet --home-dir /app wallet
WORKDIR /app
COPY --from=build --chown=wallet:wallet /workspace/target/*.jar app.jar
USER wallet:wallet
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
