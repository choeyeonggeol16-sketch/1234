package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.network.core.sc.packet.SC_CloseList;
import l1j.server.server.model.character.transform.CTransform;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Npc;

final class NpcPlayerMenuHandler {
    private NpcPlayerMenuHandler() {}

    static void handle(L1PcInstance requester, L1PcInstance target, String command) {
        // The existing player-backed UI always emits its own object ID.
        // GM commands use the separate, already authorized local route.
        if (requester != target) return;
        if (isAsciiNumber(command, 0)) {
            summonMonster(target, command);
        } else if (target.isMagicItem()) {
            CTransform.TransformFromBook(target, command, 1200);
        } else {
            CTransform.handleCommands(target, command);
        }
    }

    // Matches [0-9]+ from the indicated offset, with no Pattern/Matcher allocation.
    static boolean isAsciiNumber(String command, int offset) {
        int length = command.length();
        if (offset < 0 || offset >= length) return false;
        for (int i = offset; i < length; i++) {
            char c = command.charAt(i);
            if (c < '0' || c > '9') return false;
        }
        return true;
    }

	private static final String[] summonstr_list = new String[] { "7", "263", "519", "8", "264", "520", "9", "265", "521", "10", "266", "522",
			"11", "267", "523", "12", "268", "524", "13", "269", "525", "14", "270", "526", "15", "271", "527",
			"16", "17", "18", "274" };
	
	private static final int[] summonid_list = new int[] { 810820, 810821, 810822, // 28
			810823, 810824, 810825, // 32
			810826, 810827, 810828, // 36
			810829, 810830, 810831, // 40
			810832, 810833, 810834, // 44
			810835, 810836, 810837, // 48
			810839, 810838, 810840, // 52
			810841, 810842, 810843, // 56
			810844, 810845, 810846, // 60
			810847, // 64
			810848, // 68
			810850, 810849 // 72 //-- 이부분 임.

	}; // 소환몹 정하는 구문....
	
	private static final int[] summonlvl_list = new int[] { 28, 28, 28, 32, 32, 32, 36, 36, 36, 40, 40, 40, 44, 44, 44, 48, 48, 48, 52, 52, 52,
			56, 56, 56, 60, 60, 60, 64, 68, 72, 72 };// 술자 레벨제한
	private static final int[] summoncha_list = new int[] { 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8, 8,
			14, 36, 36, 50 };// 카리
	
	private static void summonMonster(L1PcInstance pc, String s) {
		int summonid = 0;
		int levelrange = 0;
		int summoncost = 0;
		
		// 소비구문
		for (int loop = 0; loop < summonstr_list.length; loop++) {
			if (s.equalsIgnoreCase(summonstr_list[loop])) {
				summonid = summonid_list[loop];
				levelrange = summonlvl_list[loop];
				summoncost = summoncha_list[loop];
				break;
			}
		}
		if (summonid == 0 || summoncost <= 0) return;
		if (pc.getLevel() < levelrange) {
			SC_ServerMessage.send(pc, 743);
			return;
		}

		int petcost = 0;
		Object[] petlist = pc.getPetList().values().toArray();
		for (Object pet : petlist) {
			petcost += ((L1NpcInstance) pet).getPetcost();
		}
		if ((summonid == 810850 || summonid == 810849 || summonid == 810848) && petcost != 0) {
			SC_CloseList.send(pc, pc.getId());
			return;
		}
		int charisma = pc.GetTotalStatCha() + 6 - petcost;
		int summoncount = 0;
		if (levelrange <= 52) {
			summoncount = charisma / summoncost;
		} else if (levelrange == 56) {
			summoncount = charisma / (summoncost + 2);
		} else if (levelrange == 60) {
			summoncount = charisma / (summoncost + 4);
		} else if (levelrange == 64) {
			summoncount = charisma / (summoncost + 6);
		} else {
			summoncount = charisma / summoncost;
		}

		if (levelrange <= 52 && summoncount > 5) {
			summoncount = 5;
		} else if (levelrange == 56 && summoncount > 4) {
			summoncount = 4;
		} else if (levelrange == 60 && summoncount > 3) {
			summoncount = 3;
		} else if (levelrange == 64 && summoncount > 2) {
			summoncount = 2;
		}

        L1Npc npcTemp = CObjectDB.LoadNPC().getTemplate(summonid);
		if (npcTemp == null) return;
		L1SummonInstance summon = null;
		for (int cnt = 0; cnt < summoncount; cnt++) {
			summon = new L1SummonInstance(npcTemp, pc);
			if (summonid == 810850 || summonid == 810849 || summonid == 810848) {
				summon.setPetcost(pc.GetTotalStatCha() + 7);
			} else {
				if (levelrange <= 52) {
					summon.setPetcost(summoncost);
				} else if (levelrange == 56) {
					summon.setPetcost(summoncost + 2);
				} else if (levelrange == 60) {
					summon.setPetcost(summoncost + 4);
				} else if (levelrange == 64) {
					summon.setPetcost(summoncost + 6);
				} else {
					summoncount = charisma / summoncost;
				}
			}
		}
		SC_CloseList.send(pc, pc.getId());
	}

}
