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
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;

final class WorldRewardAndCraftItems {
    private WorldRewardAndCraftItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
case 777000:
			ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, 555000);
			break;
		
case 777001:
			ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, 555001);
			break;
		
case 777002:
			ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, 555002);
			break;
		
case 777003:
			ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, 555003);
			break;
		
case 5100135:
			TowerCharmCraftingItems.UseDurableTowerCharm(pc, l1iteminstance);
			break;
		
case 875640382:
			L1NeigeItemBox.lowbox(pc, l1iteminstance);
			break;
		
case 875640407:
			L1NeigeItemBox.herobox(pc, l1iteminstance);
			break;
		
case 414100:
            CS_Attr.resetKillDeath(pc, l1iteminstance, true, false);
            break;
        
case 414101:
            CS_Attr.resetKillDeath(pc, l1iteminstance, false, true);
            break;
        
case 414102:
            CS_Attr.resetKillDeath(pc, l1iteminstance, true, true);
            break;

		
case 40964:
		case 41036:
			TargetedPurificationReceipt.useTargetedPurification(pc, l1iteminstance, l1iteminstance1);
			break;
		
case 3000194:
		case 41299:
		case 41300:
		case 41303:
		case 41304:
			MoneyBoxTransactions.useRandomMoneyBox(pc, l1iteminstance);
			break;

		
case 887799:
			ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, pc.getType() == 1 ? 41148 : pc.getType() == 3 ? 40222 : pc.getType() == 4 ? 5559 : pc.getType() == 7 ? 210125 : 41148);
			break;
		
case 887700:
			ItemReceiptOperations.UseDurableExchange(pc, l1iteminstance, pc.getType() == 1 ? 4100102 : pc.getType() == 4 ? 4100105 : pc.getType() == 7 ? 4100112 : 4100102);
			break;


        }
    }
}
