package l1j.server.server.model.shop;

import l1j.server.Config;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.shared.GameRandom;
import l1j.server.server.templates.L1Item;

final class ShopDelivery {
    private ShopDelivery() {}

    static L1ItemInstance create(int npcId, ShopPolicy policy, L1ShopBuyOrder order) {
        int id = order.getItem().getItemId();
        if (ShopPolicy.isRace(npcId)) {
            var ticket = BugRaceController.getInstance().find_ticket_info(id);
            if (ticket != null) id = ticket.converter_itemid;
        }
        L1Item template = CObjectDB.GetItem(id);
        if (template == null) throw new IllegalStateException("Missing shop item template " + id);

        // The receipt allocates a world object ID only if a new row is needed.
        L1ItemInstance item = new L1ItemInstance(template, order.getCount());
        item.setBless(template.getBless());
        item.setIdentified(policy.delivery != ShopPolicy.Delivery.UNIDENTIFIED && npcId != 7310126);
        item.setPackage(policy.delivery == ShopPolicy.Delivery.PACKAGED);
        int enchant = switch (policy.delivery) {
            case CONFIGURED, PACKAGED -> order.getItem().getEnchant();
            default -> 0;
        };
        if (policy == ShopPolicy.FEATHER) {
            enchant = switch (npcId) {
                case 5073, 7310101, 7310113, 519, 224, 523 -> enchant;
                default -> 0;
            };
        }
        item.setEnchantLevel(resolveEnchant(npcId, enchant));
        return item;
    }

    private static int resolveEnchant(int npcId, int fallback) {
        if (npcId >= 810151 && npcId <= 810159) return configured(Config.방어구인첸트, fallback);
        return switch (npcId) {
            case 810150 -> configured(Config.무기고급인첸트, fallback);
            case 810160 -> configured(Config.악세사리, fallback);
            case 810161 -> configured(Config.룸티스, fallback);
            case 70068, 70020, 70056 -> commonRoll(GameRandom.next(1, 150));
            case 7310103, 7310199 -> luckyRoll(GameRandom.next(1, 100), fallback);
            case 900173 -> eventRoll(GameRandom.next(1, 170), fallback);
            case 7310125 -> equipmentRoll(GameRandom.next(1, 120), fallback);
            case 7310104 -> demonRoll(GameRandom.next(2, 101), fallback);
            case 7310126 -> craneRoll(GameRandom.next(1, 150), fallback);
            default -> fallback;
        };
    }

    private static int configured(int value, int fallback) {
        return value >= 1 && value <= 10 ? value : fallback;
    }

    private static int commonRoll(int roll) {
        if (roll <= 15) return -2;
        if (roll <= 30) return -1;
        if (roll <= 89) return 0;
        if (roll <= 141) return GameRandom.next(1, 2);
        if (roll <= 147) return GameRandom.next(3, 5);
        return roll <= 149 ? 6 : 7;
    }

    private static int luckyRoll(int roll, int fallback) {
        if (roll < 60) return 7;
        if (roll <= 70) return 8;
        if (roll >= 80 && roll <= 90) return 9;
        if (roll >= 95) return 10;
        return fallback;
    }

    private static int eventRoll(int roll, int fallback) {
        if (roll <= 20) return -2;
        if (roll >= 25 && roll <= 35) return -1;
        if (roll >= 40 && roll <= 55) return 0;
        if (roll >= 60 && roll <= 70) return 1;
        if (roll >= 100 && roll <= 120) return 2;
        if (roll >= 150) return 3;
        return fallback;
    }

    private static int equipmentRoll(int roll, int fallback) {
        if (roll < 20) return -2;
        if (roll <= 30) return -1;
        if (roll <= 50) return 0;
        if (roll <= 80) return (roll - 51) / 10 + 1;
        if (roll <= 90) return fallback;
        if (roll <= 110) return (roll - 91) / 10 + 4;
        return roll <= 115 ? 6 : 7;
    }

    private static int demonRoll(int roll, int fallback) {
        if (roll <= 15) return -3;
        if (roll <= 20) return 0;
        if (roll <= 30) return GameRandom.next(1, 2);
        if (roll <= 40) return -2;
        if (roll <= 50) return -1;
        if (roll <= 60) return GameRandom.next(3, 5);
        if (roll <= 90) return (roll - 61) / 10 + 2;
        return roll == 100 ? 5 : fallback;
    }

    private static int craneRoll(int roll, int fallback) {
        if (roll <= 15) return -2;
        if (roll >= 20 && roll <= 30) return -1;
        if (roll >= 31 && roll <= 40) return 0;
        if (roll >= 51 && roll <= 60) return 2;
        if (roll >= 70 && roll <= 80) return GameRandom.next(3, 4);
        if (roll >= 90 && roll <= 110) return 5;
        return roll == 148 ? 6 : fallback;
    }
}
