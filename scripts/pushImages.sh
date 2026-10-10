#!/bin/bash
docker push ${REPOSITORY_PREFIX}/housekeeping-config-server:${VERSION}
docker push ${REPOSITORY_PREFIX}/housekeeping-discovery-server:${VERSION}
docker push ${REPOSITORY_PREFIX}/housekeeping-api-gateway:${VERSION}
docker push ${REPOSITORY_PREFIX}/housekeeping-visits-service:${VERSION}
docker push ${REPOSITORY_PREFIX}/housekeeping-vets-service:${VERSION}
docker push ${REPOSITORY_PREFIX}/housekeeping-customers-service:${VERSION}
docker push ${REPOSITORY_PREFIX}/housekeeping-admin-server:${VERSION}
docker push ${REPOSITORY_PREFIX}/housekeeping-genai-service:${VERSION}
