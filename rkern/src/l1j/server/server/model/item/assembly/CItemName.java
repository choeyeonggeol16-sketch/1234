package l1j.server.server.model.item.assembly;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.item.collection.CollectionObject;
import l1j.server.server.model.item.collection.loader.CollectionLoadManager;
import l1j.server.server.model.item.collection.loader.CollectionSystemLoader;
import l1j.server.server.model.item.pride.PrideInfo;
import l1j.server.server.model.item.pride.PrideLoadManager;
import l1j.server.server.model.world.instance.inn.InnHelper;
import l1j.server.server.templates.L1Item;

final class CItemName {
    private CItemName() {}

    static String view(L1ItemInstance item, int count) {
        if (PrideLoadManager.USE_PRIDE_SYSTEM && item.getItemId() == PrideLoadManager.PRIDE_ITEM_ID) {
            PrideInfo pInfo = PrideLoadManager.getInstance().get_pride(item.getId());
            if (pInfo != null)
                return pInfo.get_target_name() + "의 자존심";
        }

        StringBuilder name = new StringBuilder(64);

        if (item.isSpecialEnchantable()) {
            name.append("\\f3");
        }
        appendNumberedName(item, name, count);
        final L1Item template = item.getItem();
        final int itemType2 = template.getType2();
        final int itemType = template.getType();
        final int itemId = template.getItemId();

        if (itemType2 == 0 && itemType == 2) { // light
            if (item.isNowLighting()) {
                name.append(" ($10)");
            }
            if ((itemId == 40001 || itemId == 40002 || itemId == 7005) && item.getRemainingTime() <= 0) {
                name.append(" ($11)");
            }
        }

        if (itemId == InnHelper.INN_KEYID) {
            name.append(" (").append(item.getHotel_Town()).append(')');
        }

        if (item.isEquipped()) {
            if (itemType2 == 1) {
                name.append(" ($9)");
            } else if (itemType2 == 2) {
                name.append(" ($117)");
            } else if (itemType2 == 0 && itemType == 11) { // petitem
                name.append(" ($117)");
            }
        }

        if (itemType2 == 0 && itemType == 0) {
            final L1PcInstance owner = item._cha;
            if (owner != null && owner.getInventory().getArrowItemId() == item.getItemId()) {
                name.append(" ($117)");
            }
        }

        return name.toString();
    }

    static String numbered(L1ItemInstance item, int count) {
        StringBuilder name = new StringBuilder(48);
        appendNumberedName(item, name, count);
        return name.toString();
    }

    private static void appendNumberedName(L1ItemInstance item, StringBuilder name, int count) {
        final L1Item template = item.getItem();
        final int itemType2 = template.getType2();
        final int itemId = template.getItemId();
        final boolean identified = item.isIdentified();
        if (identified && (itemType2 == 1 || itemType2 == 2)) {
            name.append(L1ItemInstance.get_attribute_enchant_description(item.getAttrEnchantLevel()));
            final int enchant = item.getEnchantLevel();
            if (enchant >= 0) name.append('+');
            name.append(enchant).append(' ');
        }
        final String unidentifiedName = identified ? null : switch (itemId) {
            case 900075 -> "$25459";
            case 40008, 140008 -> "$27";
            case 40007 -> "$263";
            case 40006 -> "$28";
            case 40015, 140015 -> "$232";
            case 40012 -> "$238";
            case 40011 -> "$235";
            case 40010, 140010 -> "$237";
            default -> null;
        };
        if (unidentifiedName != null) {
            name.append(unidentifiedName);
        } else if (itemId == CollectionLoadManager.CTSYSTEM_LOAD_ID) {
            CollectionObject obj = CollectionSystemLoader.getInstance().get(item.getId());
            if (obj == null)
                name.append(template.getNameId());
            else
                name.append("[").append(obj.name).append("]봉인 구슬");
        } else {
            name.append(template.getNameId());
        }

        if (item.isSpecialEnchantable()) {
            for (int i = 1; i <= 3; ++i) {
                if (item.getSpecialEnchant(i) == 0) {
                    break;
                }
                switch (item.getSpecialEnchant(i)) {
                case L1ItemInstance.CHAOS_SPIRIT:
                    name.append("[혼돈] ");
                    break;
                case L1ItemInstance.CORRUPT_SPIRIT:
                    name.append("[타락] ");
                    break;
                case L1ItemInstance.BALLACAS_SPIRIT:
                    name.append("[발라카스] ");
                    break;
                case L1ItemInstance.ANTARAS_SPIRIT:
                    name.append("[안타라스] ");
                    break;
                case L1ItemInstance.LINDBIOR_SPIRIT:
                    name.append("[린드비오르] ");
                    break;
                case L1ItemInstance.PAPURION_SPIRIT:
                    name.append("[파푸리온] ");
                    break;
                case L1ItemInstance.DEATHKNIGHT_SPIRIT:
                    name.append("[데스나이트] ");
                    break;
                case L1ItemInstance.BAPPOMAT_SPIRIT:
                    name.append("[바포메트] ");
                    break;
                case L1ItemInstance.BALLOG_SPIRIT:
                    name.append("[발록] ");
                    break;
                case L1ItemInstance.ARES_SPIRIT:
                    name.append("[아레스] ");
                    break;
                }
            }
        }

        if (identified) {
            if (template.getMaxChargeCount() > 0) {
                name.append(" (").append(item.getChargeCount()).append(')');
            }
            if (itemId == 20383) {
                name.append(" (").append(item.getChargeCount()).append(')');
            }
            if (template.getMaxUseTime() > 0 && itemType2 != 0) {
                name.append(" [").append(item.getRemainingTime()).append(']');
            }
        }
        if (count > 1) {
            name.append(" (").append(count).append(')');
        }
    }
}
