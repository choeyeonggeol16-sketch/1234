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

final class WorldPetVoucherItems {
    private WorldPetVoucherItems() {}
    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
case 3000432:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 46043);
			break;
		
case 3000433:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 46045);
			break;
		
case 3000434:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 46046);
			break;
		
case 3000435:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45695);
			break;
		
case 3000436:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45693);
			break;
		
case 3000437:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45692);
			break;
		
case 3000438:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45694);
			break;
		
case 3000439:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45696);
			break;
		
case 3000440:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45697);
			break;
		
case 3000441:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45712);
			break;
		
case 3000442:
			PetVoucherTransactions.UsePetAmulet(client, l1iteminstance, 45710);
			break;
		
        }
    }
}
