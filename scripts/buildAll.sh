#!/bin/bash
scriptDir=$(dirname $0)

IMAGE_NAME=quay.io/ibmcase/item-aggregator
./mvnw clean package -Dui.deps -Dui.dev -DskipTests
docker build -f src/main/docker/Dockerfile.jvm -t ${IMAGE_NAME} .
docker push ${IMAGE_NAME}
