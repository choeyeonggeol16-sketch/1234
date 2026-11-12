-- Apply once to the existing game database while the server is stopped.
-- No existing character HP/MP/stat values are recalculated or overwritten.
ALTER TABLE characters ADD COLUMN hp_mp_growth MEDIUMTEXT NULL;
