SET NAMES utf8mb4;
-- This item opens a read-only UI and is not consumed. Interaction 30 is
-- the existing spell_buff self-use path; the dedicated item handler opens UI.
-- The accompanying ItemTable source enforces the same interaction by item ID.
UPDATE etcitem SET use_type = 'spell_buff' WHERE item_id = 900000901;
SELECT item_id, name, use_type FROM etcitem WHERE item_id = 900000901;
