SET NAMES utf8mb4;

-- Update only this settings item. Its use handler now matches the ID, not its name.
UPDATE etcitem
SET name = 'Play Supporter', name_id = 'Play Supporter', use_type = 'normal'
WHERE item_id = 900000901;

SELECT item_id, name, name_id, use_type FROM etcitem WHERE item_id = 900000901;
