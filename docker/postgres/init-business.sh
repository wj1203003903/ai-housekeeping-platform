#!/bin/sh
set -eu

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" \
  --set=customers_password="$CUSTOMERS_DATABASE_PASSWORD" \
  --set=vets_password="$VETS_DATABASE_PASSWORD" \
  --set=visits_password="$VISITS_DATABASE_PASSWORD" <<-'EOSQL'
SELECT format('CREATE ROLE customers_app LOGIN PASSWORD %L', :'customers_password') \gexec
SELECT format('CREATE ROLE vets_app LOGIN PASSWORD %L', :'vets_password') \gexec
SELECT format('CREATE ROLE visits_app LOGIN PASSWORD %L', :'visits_password') \gexec
CREATE SCHEMA customers AUTHORIZATION customers_app;
CREATE SCHEMA vets AUTHORIZATION vets_app;
CREATE SCHEMA visits AUTHORIZATION visits_app;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOSQL
