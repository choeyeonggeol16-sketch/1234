package l1j.server.server.model.item.function;

import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.TravelUseTables.*;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

/** Dedicated item-use handler extracted from a mixed domain. */
final class ShopTeleportAmuletUse {
    private ShopTeleportAmuletUse() {}

    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 748: // 잡화상점 이동부적
			pc.start_teleport(33453, 32820, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;

		case 749: // 장비상점 이동부적
			pc.start_teleport(33435, 32754, 4, pc.getHeading(), 169, true, pc.getAutoHunt());
			break;


        }
    }
}
