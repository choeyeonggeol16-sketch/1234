package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PetList;

import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1PetType;
import l1j.server.server.utils.L1SpawnUtil;

final class NpcPetCommands {
    private NpcPetCommands() {}

    static void petbuy(CIOClient client, int npcid, int paytype, int paycount) {
	        L1PcInstance pc = client.getActiveChar();
	        if (pc == null) return;
	        L1PcInventory inv = pc.getInventory();

	        // ── 카리스마 계산 ──
	        int charisma = pc.getAbility().getTotalCha();
	        int petcost = 0;
	        Object[] petlist = pc.getPetList().values().toArray();
	        for (Object pet : petlist) {
	            if (pet instanceof L1PetInstance) {
	                petcost += ((L1NpcInstance) pet).getPetcost();
	            }
	        }
	        if (pc.isCrown()) { charisma += 6; }        // CROWN
	        else if (pc.isElf()) { charisma += 12; }    // ELF
	        else if (pc.isWizard()) { charisma += 6; }  // WIZ
	        else if (pc.isDarkelf()) { charisma += 6; } // DE
	        else if (pc.isDragonknight()) { charisma += 6; } // 용기사
	        else if (pc.isBlackwizard()) { charisma += 6; }  // 환술사
	        charisma -= petcost;

	        if (charisma / 6 <= 0) {
	            SC_ServerMessage.send(pc, 489); // 애완동물이 너무 많습니다.
	            return;
	        }
	        if (!pc.getInventory().checkItem(paytype, paycount)) {
	            return;
	        }
	        if (!pc.getInventory().consumeItem(paytype, paycount)) return;

	        // 스폰하고 "방금 스폰된 객체"를 직접 받는다 — 주변 몹 스캔 불필요
	        L1NpcInstance spawned = L1SpawnUtil.spawn(pc, npcid, 0, 0, 1);
	        if (!(spawned instanceof L1MonsterInstance)) {
	            return;  
	        }
	        L1MonsterInstance targetpet = (L1MonsterInstance) spawned;

	        L1PetType petType = PetTypeTable.getInstance().get(targetpet.getNpcTemplate().get_npcId());
	        if (petType == null || targetpet.isDead()) {
	            return;
	        }
	        if (charisma >= 6 && inv.getSize() < 180) {
	            L1ItemInstance petamu = inv.storeItem(40314, 1); // 펫의 아뮬렛
	            if (petamu != null) {
	                new L1PetInstance(targetpet, pc, petamu.getId());
	                SC_ItemName.send(pc, petamu);
	            }
	        }
	    }

    static void withdrawPets(L1PcInstance pc, int objid) {
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            final int id = item.getItem().getItemId();
            if (id == 40314 || id == 40316) {
                SC_PetList.send(pc, objid);
                return;
            }
        }
        SC_SystemMessage.send(pc, "보유중인 펫이 없습니다.");
    }
}
