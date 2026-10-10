#!/usr/bin/env bash

set -o errexit
set -o errtrace
set -o nounset
set -o pipefail

pkill -9 -f housekeeping || echo "Failed to kill any apps"

docker compose kill || echo "No docker containers are running"

echo "Running infra"
docker compose up -d grafana-server prometheus-server tracing-server

echo "Running apps"
mkdir -p target
nohup java -jar housekeeping-config-server/target/*.jar --server.port=8888 --spring.profiles.active=chaos-monkey > target/config-server.log 2>&1 &
echo "Waiting for config server to start"
sleep 20
nohup java -jar housekeeping-discovery-server/target/*.jar --server.port=8761 --spring.profiles.active=chaos-monkey > target/discovery-server.log 2>&1 &
echo "Waiting for discovery server to start"
sleep 20
nohup java -jar housekeeping-customers-service/target/*.jar --server.port=8081 --spring.profiles.active=chaos-monkey > target/customers-service.log 2>&1 &
nohup java -jar housekeeping-visits-service/target/*.jar --server.port=8082 --spring.profiles.active=chaos-monkey > target/visits-service.log 2>&1 &
nohup java -jar housekeeping-vets-service/target/*.jar --server.port=8083 --spring.profiles.active=chaos-monkey > target/vets-service.log 2>&1 &
nohup java -jar housekeeping-genai-service/target/*.jar --server.port=8084 --spring.profiles.active=chaos-monkey > target/genai-service.log 2>&1 &
nohup java -jar housekeeping-api-gateway/target/*.jar --server.port=8080 --spring.profiles.active=chaos-monkey > target/gateway-service.log 2>&1 &
nohup java -jar housekeeping-admin-server/target/*.jar --server.port=9090 --spring.profiles.active=chaos-monkey > target/admin-server.log 2>&1 &
echo "Waiting for apps to start"
sleep 60
