-- One-time setup for running the application locally (tests do not need this;
-- they use a Testcontainers MySQL instance). Run as a MySQL administrator, e.g.:
--   mysql -u root -p < db/local-setup.sql
--
-- utf8mb4_0900_ai_ci is case- and accent-insensitive, so employee name and email
-- search matches regardless of letter case without LOWER() on every row.

CREATE DATABASE IF NOT EXISTS acme_salary
    CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'acme'@'localhost' IDENTIFIED BY 'acme';

GRANT ALL PRIVILEGES ON acme_salary.* TO 'acme'@'localhost';
