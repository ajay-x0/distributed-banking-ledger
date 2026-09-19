#!/bin/bash
set -euo pipefail
for service in gateway payment account ledger fraud notification; do
 psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" -v app_password="$DB_PASSWORD" -v app_user="bank_$service" -v app_db="bank_$service" <<'SQL'
CREATE USER :"app_user" WITH PASSWORD :'app_password';
CREATE DATABASE :"app_db" OWNER :"app_user";
REVOKE CONNECT ON DATABASE :"app_db" FROM PUBLIC;
GRANT CONNECT ON DATABASE :"app_db" TO :"app_user";
SQL
done
