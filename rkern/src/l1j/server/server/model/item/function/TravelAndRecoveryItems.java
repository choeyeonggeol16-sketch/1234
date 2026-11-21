package l1j.server.server.model.item.function;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.world.instance.inn.InnHelper;

final class TravelAndRecoveryItems {
private TravelAndRecoveryItems() {}

    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
            
        switch (itemId) {
        case 3000147:
        case 202099:
        case 3000229:
        case 875640424:
        case 875640415:
        case 3000413:
        case 240100:
            IslandReturnScrollUse.use(client, pc, l1iteminstance, itemId);
            break;
        case 748:
        case 749:
            ShopTeleportAmuletUse.use(client, pc, l1iteminstance, itemId);
            break;
        case 40124:
        case 30086:
        case InnHelper.INN_KEYID:
        case 700023:
            ClanInnReturnScrollUse.use(client, pc, l1iteminstance, itemId);
            break;
        case 41260:
            DeployableEffectItems.use(client, pc, l1iteminstance, itemId);
            break;
        case 731:
        case 3000397:
        case 3000398:
        case 650009:
        case 3000128:
        case 4100040:
        case 60208:
        case 60209:
        case 60210:
            TravelBuffUseItems.use(client, pc, l1iteminstance, itemId);
            break;
        case 700021:
        case 60256:
            TravelUtilityExchangeItems.use(client, pc, l1iteminstance, itemId);
            break;
        case 700000:
        case 4100074:
        case 4100075:
        case 40033:
        case 40034:
        case 40035:
        case 40036:
        case 40037:
        case 40038:
            ExperienceAndElixirUseItems.use(client, pc, l1iteminstance, itemId);
            break;
        case 3000126:
        case 3000127:
        case 89136:
        case 875640417:
        case 875640450:
        case 123124:
        case 12302:
            CharacterPackageUseItems.use(client, pc, l1iteminstance, itemId);
            break;
        case 3000123:
            BossInvocationScrollItems.use(client, pc, l1iteminstance, itemId);
            break;
        case 210104:
            AccountPositionRecoveryItems.use(client, pc, l1iteminstance, itemId);
            break;
        }
    }
}
