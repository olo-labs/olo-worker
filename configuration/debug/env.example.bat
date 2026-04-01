@REM Copyright (c) 2026 Olo Labs. All rights reserved.
@REM

@REM Environment variables for Olo worker bootstrap (Windows).
@REM Run before tests: call env.example.bat
@REM See: docs/architecture/bootstrap/how-to-debug.md

set OLO_REGION=default

set OLO_REDIS_HOST=localhost
set OLO_REDIS_PORT=6379

set OLO_DB_HOST=localhost
set OLO_DB_PORT=5432
set OLO_DB_NAME=olo
set OLO_DB_USERNAME=olo
set OLO_DB_PASSWORD=pgpass
