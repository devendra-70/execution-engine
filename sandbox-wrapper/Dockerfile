FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY target/sandbox-wrapper.jar sandbox-wrapper.jar

# Create non-root user for security
RUN addgroup -S sandbox && adduser -S sandbox -G sandbox
USER sandbox

# The sandbox listens on stdin, no port needed
ENTRYPOINT ["sh", "-c", "while true; do sleep 3600; done"]

