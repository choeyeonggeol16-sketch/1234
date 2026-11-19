package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import static l1j.server.server.model.item.function.SpellBookItems.SpellBook2;

/** Phase 12: original SpellBookItems.java useElfSpellBook body, unchanged. */
final class ElfSpellbookConsumable {
    private ElfSpellbookConsumable() {}

	static void useElfSpellBook(L1PcInstance pc, L1ItemInstance item, int itemId) {
		int level = pc.getLevel();
		if ((pc.isElf() || pc.isGm())/* && isLearnElfMagic(pc) */) {
			if (itemId >= 40232 && itemId <= 40234 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40235 && itemId <= 40236 && level >= 1) {
				SpellBook2(pc, item);
			}
			if (itemId >= 40237 && itemId <= 40240 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40241 && itemId <= 40243 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40244 && itemId <= 40246 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40247 && itemId <= 40248 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40250 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40251 && itemId <= 40252 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40253 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40254 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40255 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40256 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40257 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40258 && itemId <= 40259 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40260 && itemId <= 40261 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40262 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId >= 40263 && itemId <= 40264 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 41150 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 41151 && level >= 1) {
				SpellBook2(pc, item);
			} else if ((itemId == 3000512 || itemId == 3000513) && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 3000511 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 3000091 && level >= 1) {
				SpellBook2(pc, item);
			} else if (itemId == 40249 || itemId == 41149 || itemId == 41149 || itemId == 41152 || itemId == 41153 || itemId == 4100295 && level >= 1) {
				SpellBook2(pc, item);
			}
		} else {
			SC_ServerMessage.send(pc, 79);
		}
	}
}
