#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=app_password="$GENAI_DATABASE_PASSWORD" <<-'EOSQL'
CREATE EXTENSION IF NOT EXISTS vector;
SELECT format('CREATE ROLE genai_app LOGIN PASSWORD %L', :'app_password') \gexec
CREATE SCHEMA genai AUTHORIZATION genai_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOSQL
