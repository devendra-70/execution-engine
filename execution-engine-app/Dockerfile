FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
# Build context is project root
COPY execution-engine-app/target/execution-engine-app-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
