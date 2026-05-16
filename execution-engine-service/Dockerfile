FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY execution-engine-service/target/execution-engine-service-*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=builder /build/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

