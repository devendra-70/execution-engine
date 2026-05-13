# ===============================
# Stage 1: Build sandbox-wrapper shaded JAR
# (SRS §6.1 — dependency-free Java application; SRS §13 — shaded JAR)
# ===============================
FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk add --no-cache maven

WORKDIR /build
COPY sandbox-wrapper/pom.xml pom.xml
COPY sandbox-wrapper/src src/

RUN mvn package -DskipTests -q

# ===============================
# Stage 2: Sandbox runtime image
# EPMICMPCOD-349: Sandbox Runtime Isolation
# ===============================
FROM eclipse-temurin:21-jdk-alpine

# === SUBTASK 453: Container Security Hardening ===
# JDK required: SandboxWrapper uses javax.tools.JavaCompiler (in-memory compilation)
RUN apk add --no-cache dumb-init

# Jar lives in /opt/sandbox — NOT under /sandbox (which is tmpfs-mounted at runtime)
WORKDIR /opt/sandbox

# Create non-root sandbox user (UID/GID 65534 = nobody in Alpine)
RUN adduser -u 65534 -G nobody -h /opt/sandbox -s /sbin/nologin -D sandbox-user 2>/dev/null || true

# Copy shaded sandbox-wrapper jar from builder stage
COPY --from=builder /build/target/sandbox-wrapper.jar /opt/sandbox/sandbox-wrapper.jar

# Copy entrypoint script
COPY sandbox-entrypoint.sh /opt/sandbox/run.sh
RUN chmod +x /opt/sandbox/run.sh

# Set ownership
RUN chown -R 65534:65534 /opt/sandbox

# Remove setuid/setgid bits (EPMICMPCOD-453: privilege escalation prevention)
RUN find / -xdev -perm /6000 -exec chmod a-s {} + 2>/dev/null || true

# ===============================
# Runtime configuration
# ===============================

# Non-root execution (EPMICMPCOD-453)
USER 65534:65534

# === SUBTASK 451: JVM Flags Injection ===
# JVM_FLAGS env var injected by ContainerSpawner at runtime
# USER_CODE env var injected by ContainerSpawner (base64 encoded Java source)

# Entrypoint: dumb-init for proper signal handling and zombie reaping
ENTRYPOINT ["/usr/bin/dumb-init", "--"]

# Default command: run the entrypoint script
CMD ["/bin/sh", "/opt/sandbox/run.sh"]
