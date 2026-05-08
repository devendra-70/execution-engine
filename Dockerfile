# Multi-stage build for minimal image size
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache wget maven

WORKDIR /app

COPY execution-engine-service/target/*.jar app.jar

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]
COPY . .

RUN mvn clean package -DskipTests

# ===============================
# = Runtime stage with security hardening
# EPMICMPCOD-349: Sandbox Runtime Isolation
# ===============================

FROM eclipse-temurin:21-jre-alpine

# === SUBTASK 453: Container Security Hardening ===

# Install minimum required packages
RUN apk add --no-cache dumb-init && \
    # Remove unnecessary packages and binaries
    apk del apk-tools && \
    rm -rf /var/cache/apk/* /usr/bin/wget /usr/bin/curl

WORKDIR /sandbox

# Create non-root sandbox user (UID 65534, GID 65534)
# UID 65534 is conventionally used for unprivileged operations
RUN addgroup -g 65534 sandbox-group && \
    adduser -u 65534 -G sandbox-group -h /sandbox -s /sbin/nologin -D sandbox-user

# Create sandbox execution directory
RUN mkdir -p /sandbox /tmp && \
    chown -R 65534:65534 /sandbox /tmp

# Remove setuid/setgid bits to prevent privilege escalation
RUN find / -perm /4000 -o -perm /2000 -delete 2>/dev/null || true

# Copy compiled artifact from builder
COPY --from=builder /app/execution-engine-service/target/*.jar app.jar

# Set file ownership
RUN chown 65534:65534 /sandbox/app.jar

# === SUBTASK 453: Read-only filesystem enforcement ===
# Docker will apply --read-only at runtime via docker run command

# === SUBTASK 456: Network isolation marker ===
# Docker will apply --net=none at runtime via docker run command

# === SUBTASK 451: JVM Flags Injection ===
# JVM_FLAGS environment variable injected at runtime
# Example: -Xms128m -Xmx256m -XX:TieredStopAtLevel=1 -XX:+UseEpsilonGC ...

# Non-root execution
USER 65534:65534

# Health check (basic process check)
HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=1 \
    CMD java -version 2>&1 | grep -q "openjdk version" || exit 1

# Entrypoint with graceful shutdown and JVM flags support
ENTRYPOINT ["/sbin/dumb-init", "--"]

# Execute with injected JVM flags
CMD ["sh", "-c", "exec java ${JVM_FLAGS:-} -jar app.jar"]
