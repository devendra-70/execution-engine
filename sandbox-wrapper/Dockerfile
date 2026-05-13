FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/sandbox-wrapper.jar sandbox-wrapper.jar

# Create non-root user for security
RUN addgroup -S sandbox && adduser -S sandbox -G sandbox
USER sandbox

EXPOSE 5000

# Run as a persistent TCP socket server
ENTRYPOINT ["java", "-jar", "/app/sandbox-wrapper.jar"]
