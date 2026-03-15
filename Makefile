DOCKER_ENV_DIR = deploy
JVM_DIST_DIR = $(DOCKER_ENV_DIR)/jvm/dist
FAT_JAR = build/libs/backend-all.jar

.PHONY: local local-db local-db-stop deploy deploy-stop build clean

# Run app locally with postgres from docker-compose
local: local-db
	./gradlew run

# Start only postgres container
local-db:
	docker compose -f $(DOCKER_ENV_DIR)/docker-compose.yaml up -d postgres

# Stop postgres container
local-db-stop:
	docker compose -f $(DOCKER_ENV_DIR)/docker-compose.yaml down

# Build fat jar, copy to jvm/dist, and run in docker
deploy: build
	cp $(FAT_JAR) $(JVM_DIST_DIR)/app.jar
	docker compose -f $(DOCKER_ENV_DIR)/docker-compose.yaml up -d --build postgres jvm

# Stop all docker containers
deploy-stop:
	docker compose -f $(DOCKER_ENV_DIR)/docker-compose.yaml down

# Build the fat jar
build:
	./gradlew shadowJar

# Clean build artifacts
clean:
	./gradlew clean
	rm -f $(JVM_DIST_DIR)/app.jar
