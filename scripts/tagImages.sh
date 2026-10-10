#!/bin/bash
docker tag ${REPOSITORY_PREFIX}/housekeeping-config-server ${REPOSITORY_PREFIX}/housekeeping-config-server:${VERSION}
docker tag ${REPOSITORY_PREFIX}/housekeeping-discovery-server ${REPOSITORY_PREFIX}/housekeeping-discovery-server:${VERSION}
docker tag ${REPOSITORY_PREFIX}/housekeeping-api-gateway ${REPOSITORY_PREFIX}/housekeeping-api-gateway:${VERSION}
docker tag ${REPOSITORY_PREFIX}/housekeeping-visits-service ${REPOSITORY_PREFIX}/housekeeping-visits-service:${VERSION}
docker tag ${REPOSITORY_PREFIX}/housekeeping-vets-service ${REPOSITORY_PREFIX}/housekeeping-vets-service:${VERSION}
docker tag ${REPOSITORY_PREFIX}/housekeeping-customers-service ${REPOSITORY_PREFIX}/housekeeping-customers-service:${VERSION}
docker tag ${REPOSITORY_PREFIX}/housekeeping-admin-server ${REPOSITORY_PREFIX}/housekeeping-admin-server:${VERSION}
docker tag ${REPOSITORY_PREFIX}/housekeeping-genai-service ${REPOSITORY_PREFIX}/housekeeping-genai-service:${VERSION}
