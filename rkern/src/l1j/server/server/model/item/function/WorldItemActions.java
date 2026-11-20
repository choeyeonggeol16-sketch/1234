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

final class WorldItemActions {

    private WorldItemActions() {}

    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId, L1ItemInstance l1iteminstance1) {
        switch (itemId) {
        case 3000106:
        case 3000354:
        case 3000394:
        case 3000396:
        case 3000395:
        case 40824:
        case 40825:
        case 43201:
        case 40826:
        case 40616:
        case 40782:
        case 40783:
        case 40692:
        case 41208:
        case 3000193:
            WorldTravelScrollItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 777000:
        case 777001:
        case 777002:
        case 777003:
        case 5100135:
        case 875640382:
        case 875640407:
        case 414100:
        case 414101:
        case 414102:
        case 40964:
        case 41036:
        case 3000194:
        case 41299:
        case 41300:
        case 41303:
        case 41304:
        case 887799:
        case 887700:
            WorldRewardAndCraftItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 70703:
        case 4100653:
        case 4100654:
        case 43321:
        case 32090:
        case 32005:
        case 32067:
        case 32063:
        case 32062:
        case 32087:
        case 32001:
        case 32002:
        case 32075:
        case 32058:
        case 32039:
        case 32038:
        case 32089:
        case 32064:
        case 32065:
        case 32029:
        case 32047:
        case 32060:
        case 32011:
        case 32061:
        case 32048:
        case 32066:
        case 32104:
        case 875640396:
        case 875640398:
        case 32105:
        case 32106:
        case 32107:
            WorldRevivalItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 40003:
        case 40566:
        case 40700:
        case 41121:
        case 41130:
            WorldQuestAndEventItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 40017:
        case 40507:
        case 510012:
        case 510013:
        case 510014:
        case 30084:
        case 3000209:
        case 3000213:
        case 3000214:
            WorldBuffAndCureItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 3000432:
        case 3000433:
        case 3000434:
        case 3000435:
        case 3000436:
        case 3000437:
        case 3000438:
        case 3000439:
        case 3000440:
        case 3000441:
        case 3000442:
            WorldPetVoucherItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        case 300001:
        case 700001:
        case 900001:
        case 400001:
        case 500001:
        case 600001:
            WorldDollPotentialItems.use(client, pc, l1iteminstance, itemId, l1iteminstance1);
            break;
        }
    }
}
