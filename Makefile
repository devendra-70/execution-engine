# CodEval Execution Engine — Local Dev Commands
.PHONY: build build-sandbox up down logs clean
## 1. Build both Maven modules
build:
./mvnw clean package -DskipTests
## 2. Build the sandbox-wrapper Docker image
build-sandbox: build
docker build -t codeval/sandbox-wrapper:latest -f sandbox-wrapper/Dockerfile sandbox-wrapper/
## 3. Start all infrastructure + execution engine
up: build build-sandbox
docker compose up --build -d
## 4. Stop everything
down:
docker compose down
## 5. Tail logs
logs:
docker compose logs -f execution-engine
## 6. Full clean
clean:
./mvnw clean
docker compose down -v --remove-orphans
## Start only infra (no engine)
infra:
docker compose up postgres redis zookeeper kafka -d
