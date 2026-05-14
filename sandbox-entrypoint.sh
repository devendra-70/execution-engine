#!/bin/sh
# Sandbox container entrypoint (SRS §6.2)
# Decodes USER_CODE (base64 env var) and feeds it to SandboxWrapper via stdin JSON protocol.
# ContainerSpawner sets: USER_CODE=<base64-java-source>, JVM_FLAGS=<jvm-opts>

set -e

if [ -z "$USER_CODE" ]; then
    echo '{"type":"protocol_error","message":"USER_CODE env var is missing"}' >&2
    exit 2
fi

# Decode base64 user source code
SOURCE=$(printf '%s' "$USER_CODE" | base64 -d)

# JSON-encode the source: escape backslashes, double-quotes, tabs, and newlines
# using awk for portable line-by-line processing (no external tools needed)
ENCODED=$(printf '%s' "$SOURCE" | awk '{
    gsub(/\\/, "\\\\")
    gsub(/"/, "\\\"")
    gsub(/\t/, "\\t")
    gsub(/\r/, "\\r")
    if (NR > 1) printf "\\n"
    printf "%s", $0
}')

# Pipe three JSON frames to SandboxWrapper stdin:
#   1. source  – the Java class to compile
#   2. testcase – a single empty-stdin test case (RUN mode)
#   3. end     – signals no more test cases
{
    printf '{"type":"source","executionId":"exec-1","className":"Solution","sourceCode":"%s"}\n' "$ENCODED"
    printf '{"type":"testcase","id":"tc-0","stdin":"","timeoutMs":3000}\n'
    printf '{"type":"end","executionId":"exec-1"}\n'
} | java ${JVM_FLAGS:-} -jar /opt/sandbox/sandbox-wrapper.jar
