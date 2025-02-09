FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app

COPY pom.xml ./
RUN mvn dependency:go-offline

COPY src ./src
RUN mvn package -DskipTests

# FROM openjdk:17-jdk-slim
FROM registry.access.redhat.com/ubi8/openjdk-17:1.20

ENV LANGUAGE='en_US:en'
WORKDIR /app

COPY --from=build /app/target/quarkus-app/lib/ /app/lib/
COPY --from=build /app/target/quarkus-app/*.jar /app/
COPY --from=build /app/target/quarkus-app/app/ /app/app/
COPY --from=build /app/target/quarkus-app/quarkus/ /app/quarkus/

EXPOSE 8080
USER 185
ENV JAVA_OPTS="-Dquarkus.swagger-ui.always-include=true -Dquarkus.swagger-ui.path=/swagger-ui -Dquarkus.http.host=0.0.0.0"
# CMD ["java", "-jar", "/app/quarkus-run.jar"]
ENV JAVA_APP_JAR="/app/quarkus-run.jar"
ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]

# FROM maven:3.8.6-openjdk-18-slim AS build
# WORKDIR /app

# COPY pom.xml ./
# RUN mvn dependency:go-offline

# COPY src ./src
# RUN mvn package -DskipTests

# CMD ["mvn", "quarkus:dev -DskipTests -XX:-UseContainerSupport"]
