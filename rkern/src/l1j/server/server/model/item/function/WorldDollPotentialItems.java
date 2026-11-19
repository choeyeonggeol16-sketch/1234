package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.cs.packet.CS_Attr;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.L1NeigeItemBox;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;

final class WorldDollPotentialItems {
    private WorldDollPotentialItems() {}
    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
case 300001:
			DollPotentialItems.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		
case 700001:
			DollPotentialItems.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		
case 900001:

			if (!pc.getInventory().checkItem(900001, 100)) {
				SC_SystemMessage.send(pc, "잠재력 강화의 비약이 부족합니다.");
				SC_SystemMessage.send(pc, "100개 모을 시 [잠재력 강화]아이템으로 제작됩니다.");
				return;
			}
			ReceiptCraftingItems.UseDurableCraft(pc, 900001, 100, 300001,
					() -> SC_ChatPacket.sendDirect(pc, pc, "잠재력 강화 아이템을 제작하었습니다."));
			break;
		
case 400001:
			DollPotentialItems.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		
case 500001:
			DollPotentialItems.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		
case 600001:
			DollPotentialItems.apply(pc, l1iteminstance, l1iteminstance1);
			break;
		
        }
    }
}
