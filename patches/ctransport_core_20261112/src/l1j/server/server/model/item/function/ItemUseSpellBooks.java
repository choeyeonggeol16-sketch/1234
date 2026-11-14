package l1j.server.server.model.item.function;


import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;

import l1j.server.server.model.network.core.session.CIOClient;


import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Skills;




import static l1j.server.server.model.item.function.ItemUseEnchantments.*;
import static l1j.server.server.model.item.function.ItemUsePotions.*;
import static l1j.server.server.model.item.function.ItemUseTransformation.*;
import static l1j.server.server.model.item.function.ItemUseWorldHelpers.*;
import static l1j.server.server.model.item.function.ItemUseSpecialEnchant.*;
import static l1j.server.server.model.item.function.ItemUseMiscActions.*;
import static l1j.server.server.model.item.function.ItemUseEquipment.*;
import static l1j.server.server.model.item.function.ItemUseDurable.*;
import static l1j.server.server.model.item.function.ItemUseCombatAbilities.*;
import static l1j.server.server.model.item.function.ItemUseDollEffects.*;

import static l1j.server.server.model.item.function.ItemUseCreature.*;

public final class ItemUseSpellBooks {
    private ItemUseSpellBooks() {}
	public static void useSpellBook(L1PcInstance pc, L1ItemInstance item, int itemId) {
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

	public static void useElfSpellBook(L1PcInstance pc, L1ItemInstance item, int itemId) {
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
	

	public static void SpellBook(L1PcInstance pc, L1ItemInstance item, boolean isLawful) {
		String s = "";
		int i = 0;
		int level1 = 0;
		int level2 = 0;
		int l = 0;
		int i1 = 0;
		int j1 = 0;
		int k1 = 0;
		int l1 = 0;
		int i2 = 0;
		int j2 = 0;
		int k2 = 0;
		int l2 = 0;
		int i3 = 0;
		int j3 = 0;
		int k3 = 0;
		int l3 = 0;
		int i4 = 0;
		int j4 = 0;
		int k4 = 0;
		int l4 = 0;
		int i5 = 0;
		int j5 = 0;
		int k5 = 0;
		int l5 = 0;
		int i6 = 0;
		int dk3 = 0;
		int bw1 = 0;
		int bw2 = 0;
		int bw3 = 0;
		L1Skills l1skills = null;
		for (int skillId = 1; skillId < 81; skillId++) {
			l1skills = SkillsTable.getInstance().getTemplate(skillId);
			String s1 = "마법서 (" + l1skills.getName() + ")";
			if (item.getItem().getName().equalsIgnoreCase(s1)) {
				int skillLevel = l1skills.getSkillLevel();
				int i7 = l1skills.getId();
				s = l1skills.getName();
				i = l1skills.getSkillId();
				switch (skillLevel) {
				case 1:
					level1 = i7;
					break;
				case 2:
					level2 = i7;
					break;
				case 3:
					l = i7;
					break;
				case 4:
					i1 = i7;
					break;
				case 5:
					j1 = i7;
					break;
				case 6:
					k1 = i7;
					break;
				case 7:
					l1 = i7;
					break;
				case 8:
					i2 = i7;
					break;
				case 9:
					j2 = i7;
					break;
				case 10:
					k2 = i7;
					break;
				case 11:
					l2 = i7;
					break;
				case 12:
					i3 = i7;
					break;
				case 13:
					j3 = i7;
					break;
				case 14:
					k3 = i7;
					break;
				case 15:
					l3 = i7;
					break;
				case 16:
					i4 = i7;
					break;
				case 17:
					j4 = i7;
					break;
				case 18:
					k4 = i7;
					break;
				case 19:
					l4 = i7;
					break;
				case 20:
					i5 = i7;
					break;
				case 21:
					j5 = i7;
					break;
				case 22:
					k5 = i7;
					break;
				case 23:
					l5 = i7;
					break;
				case 24:
					i6 = i7;
					break;
				case 25:
					dk3 = i7;
					break;
				case 26:
					bw1 = i7;
					break;
				case 27:
					bw2 = i7;
					break;
				case 28:
					bw3 = i7;
					break;
				}
			}
		}
		if (pc.isSkillMastery(i)) {
			SC_SystemMessage.send(pc, "이미 배운 마법입니다.");
			return;
		}
		int objid = pc.getId();
		SC_AddSkill.send(pc, level1, level2, l, i1, j1, k1, l1, i2, j2, k2, l2, i3, j3, k3, l3, i4, j4, k4, l4,
				i5, j5, k5, l5, i6, dk3, bw1, bw2, bw3, 0, 0, pc.getElfAttr());
		long s_skillSound = SC_SkillSound.pack(objid, isLawful ? 224 : 231);
		SC_SkillSound.sendPacked(pc, s_skillSound);
		SC_SkillSound.broadcastPacked(pc, s_skillSound);
		SkillsTable.getInstance().spellMastery(objid, i, s, 0, 0);
		pc.getInventory().removeItem(item, 1);
	}

	public static void SpellBook1(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        ItemUseSkillBookLearning.learn(pc, l1iteminstance, ItemUseSkillBookLearning.Kind.DARK_ELF);
    }

	public static void SpellBook2(L1PcInstance pc, L1ItemInstance l1iteminstance) {
        ItemUseSkillBookLearning.learn(pc, l1iteminstance, ItemUseSkillBookLearning.Kind.ELF);
    }

	public static void SpellBook3(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        ItemUseSkillBookLearning.learn(pc, l1iteminstance, ItemUseSkillBookLearning.Kind.KNIGHT);
    }

	public static void SpellBook4(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        ItemUseSkillBookLearning.learn(pc, l1iteminstance, ItemUseSkillBookLearning.Kind.PRINCE);
    }

	public static void SpellBook5(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        ItemUseSkillBookLearning.learn(pc, l1iteminstance, ItemUseSkillBookLearning.Kind.DRAGON_KNIGHT);
    }

	public static void SpellBook6(L1PcInstance pc, L1ItemInstance l1iteminstance, CIOClient clientthread) {
        ItemUseSkillBookLearning.learn(pc, l1iteminstance, ItemUseSkillBookLearning.Kind.ILLUSIONIST);
    }

}
