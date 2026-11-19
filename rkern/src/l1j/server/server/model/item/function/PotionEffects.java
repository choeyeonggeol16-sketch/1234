package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import static l1j.server.server.model.item.function.ItemReceiptOperations.UseDurableConsume;

/** Compatibility facade and zero-allocation potion item-domain dispatch. */
public final class PotionEffects {
    private PotionEffects() {}
    // true: legacy item handler stops before delay bookkeeping.
    public static boolean usePotionCase(L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        switch (itemId) {
        case 7007:
        case 40010:
        case 40011:
        case 40012:
        case 4100021:
        case 40019:
        case 40020:
        case 40021:
        case 40022:
        case 40023:
        case 40024:
        case 40026:
        case 40027:
        case 40028:
        case 40043:
        case 40058:
        case 40071:
        case 40506:
        case 40930:
        case 41141:
        case 41337:
        case 60029:
        case 60030:
        case 140010:
        case 140011:
        case 140012:
        case 140506:
        case 240010:
        case 41403:
        case 410000:
        case 410003:
        case 30062:
        case 30056:
        case 42658:
        case 40029:
        case 4100152:
        case 4100153:
        case 4100154:
            return useHealingPotion(pc, l1iteminstance, itemId);
        case 40858:
        case 708:
        case 3000456:
        case 30105:
        case 210094:
            return useExperiencePotion(pc, l1iteminstance, itemId);
        case 40030:
        case 40013:
        case 7006:
        case 40018:
        case 40039:
        case 40040:
        case 41338:
        case 41261:
        case 41262:
        case 41268:
        case 41269:
        case 41271:
        case 41272:
        case 41273:
        case 41342:
        case 30067:
        case 140013:
        case 140018:
        case 30158:
        case 875640293:
        case 40014:
        case 140014:
        case 41415:
        case 30073:
        case 40068:
        case 140068:
        case 210110:
        case 30076:
        case 712:
        case 40031:
        case 30075:
        case 713:
        case 210036:
        case 30077:
        case 40733:
            return useMovementPotion(pc, l1iteminstance, itemId);
        case 210115:
        case 40032:
        case 40041:
        case 41344:
        case 30083:
        case 40015:
        case 140015:
        case 40736:
        case 41142:
        case 210114:
        case 30089:
        case 40016:
        case 140016:
        case 210113:
        case 40025:
            return useManaPotion(pc, l1iteminstance, itemId);
        default:
            return false;
        }
    }

    // The caller's outer item-ID switch already selects this family.
    private static boolean useHealingPotion(L1PcInstance pc, L1ItemInstance item, int itemId) {
        if (itemId == 30062) {
            if (!(pc.getMapId() >= 1005 && pc.getMapId() <= 1022
                    || pc.getMapId() > 6000 && pc.getMapId() < 6999)) {
                SC_ServerMessage.send(pc, 1891);
                return true;
            }
        }
        if (itemId == 30056) {
            if (!(pc.getMapId() >= 2101 && pc.getMapId() <= 2151
                    || pc.getMapId() >= 12152 && pc.getMapId() <= 12200)) {
                SC_SystemMessage.send(pc, "특정지역에서만 사용이 가능합니다.");
                return true;
            }
        }
        L1HealingPotion healingPotion = L1HealingPotion.get(itemId);
        if (healingPotion == null) {
            SC_ServerMessage.send(pc, 79);
            return true;
        }
        healingPotion.use(pc, item);
        return false;
    }

    private static boolean useExperiencePotion(L1PcInstance pc, L1ItemInstance item, int itemId) {
        switch (itemId) {
        case 40858 -> UseDurableConsume(pc, item, () -> pc.setDrink(true));
        case 708 -> UseDurableConsume(pc, item, () -> UseExpPotion1(pc, itemId));
        case 3000456, 30105, 210094 ->
                UseDurableConsume(pc, item, () -> UseExpPotion(pc, itemId));
        default -> { return false; }
        }
        return false;
    }

    private static boolean useMovementPotion(L1PcInstance pc, L1ItemInstance item, int itemId) {
        // A blocked potion is neither debited nor charged delay bookkeeping.
        if (pc.hasSkillEffect(L1SkillId.DECAY_POTION)) {
            SC_ServerMessage.send(pc, 698);
            return true;
        }
        switch (itemId) {
        case 40030, 40013, 7006, 40018, 40039, 40040, 41338,
             41261, 41262, 41268, 41269, 41271, 41272, 41273,
             41342, 30067, 140013, 140018, 30158, 875640293:
            UseDurableConsume(pc, item, () -> { PotionEffects.useGreenPotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 40014, 140014, 41415, 30073:
            if (!(pc.isKnight() || pc.isWarrior() || pc.isCrown())) return denied(pc);
            break;
        case 40068, 140068, 210110, 30076:
            if (!pc.isElf()) return denied(pc);
            break;
        case 712, 40031, 30075:
            if (!pc.isCrown()) return denied(pc);
            break;
        case 40733:
            break;
        case 713, 210036, 30077:
            if (!pc.isBlackwizard()) return denied(pc);
            UseDurableConsume(pc, item, () -> { PotionEffects.useFruit(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        default:
            return false;
        }
        UseDurableConsume(pc, item, () -> { PotionEffects.useBravePotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
        return true;
    }

    private static boolean useManaPotion(L1PcInstance pc, L1ItemInstance item, int itemId) {
        if (pc.hasSkillEffect(L1SkillId.DECAY_POTION)) {
            SC_ServerMessage.send(pc, 698);
            return true;
        }
        switch (itemId) {
        case 210115, 40032, 40041, 41344:
            UseDurableConsume(pc, item, () -> { PotionEffects.useBlessOfEva(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 30083, 40015, 140015, 40736, 41142, 210114:
            UseDurableConsume(pc, item, () -> { PotionEffects.useBluePotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 30089, 40016, 140016, 210113:
            if (!(pc.isWizard() || pc.isBlackwizard())) {
                SC_ServerMessage.send(pc, 79);
                return true;
            }
            UseDurableConsume(pc, item, () -> { PotionEffects.useWisdomPotion(pc, itemId); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        case 40025:
            UseDurableConsume(pc, item, () -> { PotionEffects.useBlindPotion(pc); ItemActionCompletion.finishCommittedConsumable(pc, item); });
            return true;
        default:
            return false;
        }
    }

    private static boolean denied(L1PcInstance pc) {
        SC_ServerMessage.send(pc, 79);
        return true;
    }

	public static void UseExpPotion1(L1PcInstance pc, int item_id) {
		ExperiencePotionEffects.UseExpPotion1(pc, item_id);
	}

	public static void UseExpPotion(L1PcInstance pc, int item_id) {
		ExperiencePotionEffects.UseExpPotion(pc, item_id);
	}

	public static void useGreenPotion(L1PcInstance pc, int itemId) {
		MovementPotionEffects.useGreenPotion(pc, itemId);
	}

	public static void useBravePotion(L1PcInstance pc, int item_id) {
		MovementPotionEffects.useBravePotion(pc, item_id);
	}


	public static void useFruit(L1PcInstance pc, int item_id) {
		MovementPotionEffects.useFruit(pc, item_id);
	}
	public static void useBluePotion(L1PcInstance pc, int itemId) {
		ManaPotionEffects.useBluePotion(pc, itemId);
	}

	public static void useWisdomPotion(L1PcInstance pc, int item_id) {
		ManaPotionEffects.useWisdomPotion(pc, item_id);
	}

	public static void useBlessOfEva(L1PcInstance pc, int item_id) {
		ProtectivePotionEffects.useBlessOfEva(pc, item_id);
	}

	public static void useBlindPotion(L1PcInstance pc) {
		ProtectivePotionEffects.useBlindPotion(pc);
	}

	public static void useCashScroll(L1PcInstance pc, int item_id) {
		CashScrollPotionEffects.useCashScroll(pc, item_id);
	}

}
