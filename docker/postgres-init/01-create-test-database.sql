-- Runs once, when the PostgreSQL container is created with an empty data volume.
-- Creates a separate database for automated tests, so tests never touch development data.
CREATE DATABASE iitsm_test OWNER iitsm;
