FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
ENV SSF_DATA_ROOT=/data
ENV SSF_DEMO_ENABLED=true
ENV SSF_DEMO_WORKSPACE_PATH=/demo/ssf-workspace
COPY --from=build /app/target/ssf-autodesign-0.1.0.jar app.jar
COPY demo /demo
RUN mkdir -p /data
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
