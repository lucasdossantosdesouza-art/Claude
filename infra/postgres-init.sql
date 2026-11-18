-- Banco separado para o Keycloak no mesmo servidor PostgreSQL
CREATE USER keycloak WITH PASSWORD 'keycloak';
CREATE DATABASE keycloak OWNER keycloak;
