-- Native PSS settings opener. Reusable; no item is consumed by the use handler.
-- Apply after backing up your DB, then restart/reload the existing ItemTable.
SET NAMES utf8mb4;

INSERT INTO etcitem
    (item_id, name, name_id, item_type, use_type, material, weight,
     invgfx, grdgfx, itemdesc_id, stackable, max_charge_count, hit, dmg,
     min_lvl, max_lvl, locx, locy, mapid, bless, trade, cant_delete,
     delay_id, delay_time, delay_effect, food_volume, save_at_once, isEndedTimeMessage)
SELECT
    900000901, 'PSS 설정서', 'PSS 설정서', 'other', 'normal', 'paper', 0,
    474, 22, 0, 0, 0, 0, 0,
    0, 0, 0, 0, 0, 1, 1, 0,
    0, 0, 0, 0, 1, 'false'
WHERE NOT EXISTS (SELECT 1 FROM etcitem WHERE item_id = 900000901)
  AND NOT EXISTS (SELECT 1 FROM weapon WHERE item_id = 900000901)
  AND NOT EXISTS (SELECT 1 FROM armor WHERE item_id = 900000901);

-- The existing row is never overwritten. The name must match the source constant.
SELECT item_id, name, use_type, invgfx FROM etcitem WHERE item_id = 900000901;
