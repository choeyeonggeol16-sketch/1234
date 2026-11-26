package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1ItemInstance;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_UNDERWATER_BREATH;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconBlessOfEva;

final class NpcBuffCommands {
    private NpcBuffCommands() {}
    private static final int[] CLAN_BUFF_SKILLS = { 14, 26, 42, 54, 48, 79, 160, 206, 211, 216, 158, 168 };
    private static final int[] BASIC_BUFF_SKILLS = { 26, 42, 48, 158 };

	static String birthday(L1PcInstance pc, String s, String htmlid, L1Object obj) {
				L1NpcInstance npc = (L1NpcInstance) obj;
				if (pc.isInvisble()) {
					SC_NpcChatPacket.sendNpc(pc, npc, "투명 상태에서 받을 수 없습니다.", 0);
					return htmlid;
				}
				if (s.equalsIgnoreCase("a")) {
					htmlid = "birthday6";
				}
				if (s.equalsIgnoreCase("b")) {
					if (pc.getInventory().DeleteItemInInventory(3000048, 1)) {
						L1SkillUse.cast(pc, L1SkillId.COMA_B, pc.getId(), pc.getX(), pc.getY(), null, 0,
								L1SkillUse.TYPE_SPELLSC);
						
						htmlid = "birthday4";
					} else {
						htmlid = "birthday6";
					}
				}
				/** 세실리아 */
		return htmlid;
	}

	static String lawfulBook(L1PcInstance pc, String s, String htmlid) {
				int lawful;
				byte count = 0;
				lawful = 0;
				switch (s) {
				case "0":
					count = 1;
					lawful = 3000;
					break;
				case "1":
					count = 3;
					lawful = 9000;
					break;
				case "2":
					count = 5;
					lawful = 15000;
					break;
				case "3":
					count = 10;
					lawful = 30000;
					break;
				}

				if (count == 0) return "yuris3";
				if (pc.getInventory().DeleteItemInInventory(3000155, count)) {
					pc.addLawful(lawful);
					SC_ServerMessage.send(pc, 674);
					SC_SkillSound.send(pc, pc.getId(), 8473);
					SC_SkillSound.broadcast(pc, pc.getId(), 8473);
					htmlid = "yuris2";
				} else {
					htmlid = "yuris3";
				}
		return htmlid;
	}

	static String dragonHunter(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().HaveItemByClassId(40308, 300000)
                            && pc.getInventory().DeleteItemInInventory(40308, 300000)) {
						L1SkillUse.cast(pc, L1SkillId.DRAGON_HUNTER_BLESS, pc.getId(), pc.getX(), pc.getY(), null, 0,
								L1SkillUse.TYPE_GMBUFF);
						SC_SkillSound.broadcast(pc, pc.getId(), 3944);
						SC_SkillSound.send(pc, pc.getId(), 3944);
						SC_SkillSound.broadcast(pc, pc.getId(), 9009);
						SC_SkillSound.send(pc, pc.getId(), 9009);
					} else {
						SC_SystemMessage.send(pc, "아데나 (300,000)원이 부족 합니다.");
						htmlid = "dragonbufff";
					}
				} else if (s.equalsIgnoreCase("b")) {
					SC_SystemMessage.send(pc, "\\fY현재 비활성화 되어있습니다.");
				}
		return htmlid;
	}

	static String miso(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {
					if (pc.getInventory().HaveItemByClassId(40308, 1000)
                            && pc.getInventory().DeleteItemInInventory(40308, 1000)) {

						final int targetId = pc.getId();
						final int targetX = pc.getX();
						final int targetY = pc.getY();

						pc.setBuffnoch(1);

						try {
							L1SkillUse.cast(
									pc,
									L1SkillId.PHYSICAL_ENCHANT_STR,
									targetId,
									targetX,
									targetY,
									null,
									0,
									L1SkillUse.TYPE_GMBUFF);

							L1SkillUse.cast(
									pc,
									L1SkillId.PHYSICAL_ENCHANT_DEX,
									targetId,
									targetX,
									targetY,
									null,
									0,
									L1SkillUse.TYPE_GMBUFF);

							L1SkillUse.cast(
									pc,
									L1SkillId.BLESS_WEAPON,
									targetId,
									targetX,
									targetY,
									null,
									0,
									L1SkillUse.TYPE_GMBUFF);

							L1SkillUse.cast(
									pc,
									L1SkillId.miso1,
									targetId,
									targetX,
									targetY,
									null,
									0,
									L1SkillUse.TYPE_GMBUFF);

							L1SkillUse.cast(
									pc,
									L1SkillId.miso2,
									targetId,
									targetX,
									targetY,
									null,
									0,
									L1SkillUse.TYPE_GMBUFF);

							L1SkillUse.cast(
									pc,
									L1SkillId.miso3,
									targetId,
									targetX,
									targetY,
									null,
									0,
									L1SkillUse.TYPE_GMBUFF);

						} finally {
							pc.setBuffnoch(0);
						}

					} else {
						SC_SystemMessage.send(pc, "아데나 1,000원이 부족 합니다.");
					}
				}
		return htmlid;
	}

	static String merisha(L1PcInstance pc, String s, String htmlid) {
				long time = System.currentTimeMillis();
				int useTime = 10 * 60 * 1000;

				if (pc.getBuffTime() + (useTime) > time) {
					long sec = ((pc.getBuffTime() + (useTime)) - time) / 1000;
					SC_SystemMessage.send(pc, sec + "초 후에 사용할 수 있습니다.");
					return htmlid;
				}
				if (pc.getLevel() < 80) {
					SC_SystemMessage.send(pc, "최소레벨 80 이상 부터 가능 합니다.");
					return htmlid;
				}
				if (s.equals("0")) { // 마법을 받는다.
					pc.setBuffnoch(1);
					
					L1SkillUse.cast(
							pc,
							4048,
							pc.getId(),
							pc.getX(),
							pc.getY(),
							null,
							0,
							L1SkillUse.TYPE_GMBUFF);
					
					htmlid = "merisha2";
					pc.curePoison();
					pc.setBuffTime(time);
				}
		return htmlid;
	}

	static String clanBuff(L1PcInstance pc, String s, String htmlid) {
				if ((pc.getClanRank() != L1Clan.수련) && (pc.getClanRank() != L1Clan.수호)
						&& (pc.getClanRank() != L1Clan.일반) && (pc.getClanRank() != L1Clan.군주)
						&& (pc.getClanRank() != L1Clan.정예)) {
					SC_SystemMessage.send(pc, "해당 성혈원만 받을수 있습니다. ");
					return htmlid;
				}
				/*
				 * if (s.equals("a")) { // 마법을 받는다. if (!pc.getClan().decWarPoint()) {
				 * SC_SystemMessage.send(pc, "혈맹 포인트가 모자랍니다."); return htmlid; }
				 */

				int[] allBuffSkill = CLAN_BUFF_SKILLS;
				// 덱,힘,블레스웨폰,네이쳐스
				pc.setBuffnoch(1);
				for (int i = 0; i < allBuffSkill.length; i++) {
					
					L1SkillUse.cast(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null, 0,
							L1SkillUse.TYPE_GMBUFF);
					
					SC_SkillSound.send(pc, pc.getId(), 830);
					pc.curePoison();
					// pc.setBuffTime(time);// 버프딜레이
				}
		return htmlid;
	}

	static String basicBuff(L1PcInstance pc, String s, String htmlid) {
				long time = System.currentTimeMillis();
				int useTime = 20 * 60 * 1000;// 20분뒤
				if (pc.getBuffTime() + (useTime) > time) {
					long sec = ((pc.getBuffTime() + (useTime)) - time) / 1000;
					SC_SystemMessage.send(pc, sec + "초 후에 사용할 수 있습니다.");
					return htmlid;
				}
				if (s.equals("a")) { // 마법을 받는다.
					int[] allBuffSkill = BASIC_BUFF_SKILLS;
					// 덱,힘,블레스웨폰,네이쳐스
					pc.setBuffnoch(1);
					for (int i = 0; i < allBuffSkill.length; i++) {
						
						L1SkillUse.cast(pc, allBuffSkill[i], pc.getId(), pc.getX(), pc.getY(), null, 0,
								L1SkillUse.TYPE_GMBUFF);
						
			
					}
					SC_SkillSound.send(pc, pc.getId(), 830);
					pc.curePoison();
					pc.setBuffTime(time);
				}
		return htmlid;
	}

	static String gunter(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {
					L1SkillUse.cast(pc, L1SkillId.BUFF_GUNTER, pc.getId(), pc.getX(), pc.getY(), null,
							0, L1SkillUse.TYPE_SPELLSC);
					
				}
				// 크레이
		return htmlid;
	}

	static String cray(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {
					L1SkillUse.cast(pc, L1SkillId.BUFF_CRAY, pc.getId(), pc.getX(), pc.getY(), null, 0,
							L1SkillUse.TYPE_SPELLSC);
					htmlid = "grayknight2";
				}
		return htmlid;
	}

	static String vala(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("a")) {
					L1SkillUse.cast(pc, L1SkillId.BUFF_Vala, pc.getId(), pc.getX(), pc.getY(), null, 0,
							L1SkillUse.TYPE_SPELLSC);
					htmlid = "vdeath2";
				}
				// 저주받은 무녀 사엘 (입구 npc)
		return htmlid;
	}

	static String sael(L1PcInstance pc, String s, String htmlid) {
				if (s.equals("a")) {
					
					L1SkillUse.cast(pc, L1SkillId.BUFF_SAEL, pc.getId(), pc.getX(), pc.getY(), null, 0,
							L1SkillUse.TYPE_SPELLSC);
					
					if (!pc.hasSkillEffect(STATUS_UNDERWATER_BREATH)) {
						pc.setSkillEffect(STATUS_UNDERWATER_BREATH, 1800 * 1000);
						SC_SkillIconBlessOfEva.send(pc, pc.getId(), pc.getEquipment().evaIconDuration(1800));
					}
				}
				// 쿠루 몽섬 집시촌이동
		return htmlid;
	}

	static String hunterBless(L1PcInstance pc, String s, String htmlid) {
				if (s.equalsIgnoreCase("giveto")) {
					if (pc.getInventory().HaveItemByClassId(40308, 200000)
                            && pc.getInventory().DeleteItemInInventory(40308, 200000)) {
					
						
						L1SkillUse.cast(pc, L1SkillId.HUNTER_BLESS, pc.getId(), pc.getX(), pc.getY(), null, 0,
								L1SkillUse.TYPE_GMBUFF);
						
						
						SC_SkillSound.broadcast(pc, pc.getId(), 9009);
						SC_SkillSound.send(pc, pc.getId(), 9009);
						htmlid = "";
					} else {
						SC_SystemMessage.send(pc, "기부하기전에..용사님 걱정부터 하셔야 할 것 같아요... 아데나가 부족 해요..");
						htmlid = "";
					}
				}
		return htmlid;
	}


    static void paidEquipmentBuff(L1PcInstance pc, boolean weapon) {
        final L1ItemInstance equipped = weapon ? pc.getWeapon() : pc.getInventory().getItemEquipped(2, 2);
        if (weapon && equipped == null) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        if (!pc.getInventory().HaveItemByClassId(40308, 100)) {
            SC_SystemMessage.send(pc, "아데나가 부족합니다.");
            return;
        }
        if (equipped == null) {
            SC_ServerMessage.send(pc, 79);
            return;
        }
        // Legacy weapon branch required the equipped object to be in the inventory.
        if (weapon && !pc.getInventory().getItems().contains(equipped)) return;
        if (!pc.getInventory().DeleteItemInInventory(40308, 100)) {
            SC_SystemMessage.send(pc, "아데나가 부족합니다.");
            return;
        }
        L1SkillUse.cast(pc, weapon ? L1SkillId.ENCHANT_WEAPON : L1SkillId.BLESSED_ARMOR,
                equipped.getId(), 0, 0, null, 0, L1SkillUse.TYPE_SPELLSC);
    }

    private static final int[] FEATHER_SKILLS = {L1SkillId.FEATHER_BUFF_A,L1SkillId.FEATHER_BUFF_B,
            L1SkillId.FEATHER_BUFF_C,L1SkillId.FEATHER_BUFF_D};

    private static final int[][] FEATHER_REMOVE_ORDER = {{1,2,3},{0,2,3},{1,0,3},{1,2,0}};

    static String featherBuff(L1PcInstance pc) {
        for (int i=0; i<FEATHER_SKILLS.length; i++) {
            final int token=410057+i;
            if (!pc.getInventory().HaveItemByClassId(token,1)) continue;
            if (!pc.getInventory().DeleteItemInInventory(token,1)) return "suschef4";
            for (int j : FEATHER_REMOVE_ORDER[i]) {
                if (pc.hasSkillEffect(FEATHER_SKILLS[j])) pc.removeSkillEffect(FEATHER_SKILLS[j]);
            }
            L1SkillUse.cast(pc,FEATHER_SKILLS[i],pc.getId(),pc.getX(),pc.getY(),null,0,L1SkillUse.TYPE_GMBUFF);
            SC_SkillSound.broadcast(pc,pc.getId(),7947+i);
            return "";
        }
        return "suschef4";
    }

}
