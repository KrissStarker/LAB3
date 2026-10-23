-- От пользователя postgres в pgAdmin.

CREATE USER courier_app WITH PASSWORD 'courier_pass';
CREATE DATABASE courierdb OWNER postgres ENCODING 'UTF8';
GRANT CONNECT ON DATABASE courierdb TO courier_app;

-- Дальше Query Tool на базе courierdb:
-- 1) выполнить src/main/resources/schema.sql
-- 2) выдать права приложению:

GRANT USAGE ON SCHEMA public TO courier_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO courier_app;
GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO courier_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO courier_app;
ALTER DEFAULT PRIVILEGES FOR ROLE postgres IN SCHEMA public
    GRANT USAGE, SELECT ON SEQUENCES TO courier_app;
