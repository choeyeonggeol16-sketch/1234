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

final class WorldQuestAndEventItems {
    private WorldQuestAndEventItems() {}
    static void use(CIOClient client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
case 40003: // 랜턴오일
            LanternOilReceipt.useDurableLanternOil(pc, l1iteminstance);
            break;
		
case 40566: { // 신비한 소라 껍데기

		    if (pc.isElf()
		            && pc.getX() >= 33971
		            && pc.getX() <= 33975
		            && pc.getY() >= 32324
		            && pc.getY() <= 32328
		            && pc.getMapId() == 4
		            && !pc.getInventory().checkItem(40548)) { // 망령의 봉투

		        final L1NpcInstance npc =
		                L1World.getInstance()
		                        .findNpcByNpcId(45300);

		        if (npc instanceof L1MonsterInstance) {

		            SC_ServerMessage.send(pc, 79); // \f1 아무것도

		        } else {

		            L1SpawnUtil.spawn(
		                    pc,
		                    45300,
		                    0,
		                    0); // 고대인의 망령
		        }

		    } else {

		        SC_ServerMessage.send(pc, 79); // \f1 아무것도
		    }

		    break;
		}		
case 40700: { // 실버 플룻

		    SC_Sound.send(pc, 10);
		    SC_Sound.broadcast(pc, 10);

		    if (pc.getX() >= 32619
		            && pc.getX() <= 32623
		            && pc.getY() >= 33120
		            && pc.getY() <= 33124
		            && pc.getMapId() == 440) {

		        final L1NpcInstance npc =
		                L1World.getInstance()
		                        .findNpcByNpcId(45875);

		        if (!(npc instanceof L1MonsterInstance)) {
		            L1SpawnUtil.spawn(
		                    pc,
		                    45875,
		                    0,
		                    0);
		        }
		    }

		    break;
		}
		
case 41121:// 카헬의 계약서
			if (pc.getQuest().get_step(L1Quest.QUEST_SHADOWS) == L1Quest.QUEST_END
			|| pc.getInventory().checkItem(41122, 1)) {
				SC_ServerMessage.send(pc, 79);
			} else {
				CreatureItemActions.createNewItem(pc, 41122, 1);
			}
			break;
		
case 41130:// 혈흔의 계약서
			if (pc.getQuest().get_step(L1Quest.QUEST_DESIRE) == L1Quest.QUEST_END
			|| pc.getInventory().checkItem(41131, 1)) {
				SC_ServerMessage.send(pc, 79);
			} else {
				CreatureItemActions.createNewItem(pc, 41131, 1);
			}
			break;
		
        }
    }
}
