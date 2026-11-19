package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook;

/** Phase 12: original SpellBookItems.java useSpellBook body, unchanged. */
final class GeneralSpellbookConsumable {
    private GeneralSpellbookConsumable() {}

	static void useSpellBook(L1PcInstance pc, L1ItemInstance item, int itemId) {
		int itemAttr = 1;
		int locAttr = 1; // 0:other 1:law 2:chaos
		boolean isLawful = true;
		// int pcX = pc.getX();
		// int pcY = pc.getY();
		// int mapId = pc.getMapId();
		int level = pc.getLevel();
		if (itemId == 45000 || itemId == 45008 || itemId == 45018 || itemId == 45021 || itemId == 40171
				|| itemId == 40179 || itemId == 40180 || itemId == 40182 || itemId == 40194 || itemId == 40197
				|| itemId == 40202 || itemId == 40206 || itemId == 40213 || itemId == 40220 || itemId == 40222) {
			itemAttr = 1;
		}
		if (itemId == 45009 || itemId == 45010 || itemId == 45019 || itemId == 40172 || itemId == 40173
				|| itemId == 40178 || itemId == 40185 || itemId == 40186 || itemId == 40192 || itemId == 40196
				|| itemId == 40201 || itemId == 40204 || itemId == 40211 || itemId == 40221 || itemId == 40225) {
			itemAttr = 1;
		}
		/** 마을에서 마법 배우기 **/
	
		if (pc.isGm()) {
			SpellBook(pc, item, isLawful);
		} else if ((itemAttr == locAttr || itemAttr == 0) && locAttr != 0) {
			if (pc.isKnight() || pc.isWarrior()) {
				if (itemId >= 45000 && itemId <= 45007 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45000 && itemId <= 45007) {
					SC_ServerMessage.send(pc, 312);
				} else {
					SC_ServerMessage.send(pc, 79);
				}
			} else if (pc.isCrown() || pc.isDarkelf()) {
				if (itemId >= 45000 && itemId <= 45007 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45008 && itemId <= 45015 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45008 && itemId <= 45015 || itemId >= 45000 && itemId <= 45007) {
					SC_ServerMessage.send(pc, 312);
				} else {
					SC_ServerMessage.send(pc, 79);
				}
			} else if (pc.isElf()) {
				if (itemId >= 45000 && itemId <= 45007 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45008 && itemId <= 45015 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45016 && itemId <= 45022 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40170 && itemId <= 40177 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40178 && itemId <= 40185 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40186 && itemId <= 40193 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45000 && itemId <= 45022 || itemId >= 40170 && itemId <= 40193) {
					SC_ServerMessage.send(pc, 312);
				} else {
					SC_ServerMessage.send(pc, 79);
				}
			} else if (pc.isWizard()) {
				if (itemId >= 45000 && itemId <= 45007 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45008 && itemId <= 45015 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 45016 && itemId <= 45022 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40170 && itemId <= 40177 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40178 && itemId <= 40185 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40186 && itemId <= 40193 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40194 && itemId <= 40201 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40202 && itemId <= 40209 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40210 && itemId <= 40217 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId >= 40218 && itemId <= 40225 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else if (itemId == 3000095 && level >= 1) {
					SpellBook(pc, item, isLawful);
				} else {
					SC_ServerMessage.send(pc, 312);
				}
			}
		} else if (itemAttr != locAttr && itemAttr != 0 && locAttr != 0) {
			SC_ServerMessage.send(pc, 79);
			long effect = SC_SkillSound.pack(pc.getId(), 10);
			SC_SkillSound.sendPacked(pc, effect);
			SC_SkillSound.broadcastPacked(pc, effect);
			pc.setCurrentHp(Math.max(pc.getCurrentHp() - 45, 0));
			if (pc.getCurrentHp() <= 0) {
				pc.death(null, true);
			}
			pc.getInventory().removeItem(item, 1);
		} else {
			SC_ServerMessage.send(pc, 79);
		}
	}
}
