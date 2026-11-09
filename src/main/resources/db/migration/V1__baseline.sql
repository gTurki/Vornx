-- V1: baseline.
-- Flyway runs every file in this folder once, in version order (V1, V2, V3...),
-- and records it in the table flyway_schema_history.
-- RULE: never edit a migration that already ran. To change the schema, add a new file (V2__..., V3__...).

SELECT 1;
