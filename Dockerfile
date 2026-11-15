FROM maven:3.9.13-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src/main ./src/main
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:25-jre
WORKDIR /app
RUN mkdir -p /logs && chown 10001:10001 /logs
COPY --from=build /app/target/app.jar /app.jar
USER 10001:10001
EXPOSE 8080 8081
ENTRYPOINT ["java", "-jar", "/app.jar"]
