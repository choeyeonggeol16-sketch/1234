-- Retire only the legacy pet database tables (RKern / L1J).
-- Stop the game server and CacheD, back up the database, deploy the matching
-- source revision and select the correct game database before executing.
-- MySQL DROP TABLE commits implicitly. Do not disable foreign-key checks.
-- Existing character items and companion records are intentionally untouched.
DROP TABLE IF EXISTS `pets`;
DROP TABLE IF EXISTS `petitem`;
DROP TABLE IF EXISTS `pettypes`;
