package l1j.server.server.model.item.function;

import l1j.server.server.shared.GameRandom;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.TravelUseTables.*;

import l1j.server.server.model.character.combat.arena.stadium.StadiumManager;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.network.core.connection.CUserSocket;
final class BossInvocationScrollItems {
    private BossInvocationScrollItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 3000123: { // 보스소환 주문서
			if (pc.is_combat_field() || StadiumManager.getInstance().is_on_stadium(pc.getMapId())) {
				pc.sendPackets("해당 맵에서는 사용할 수 없습니다.");
				return;
			}

			int castle_id = L1CastleLocation.getCastleIdByArea(pc);
			if (castle_id != 0) {
				pc.sendPackets("공성지역은 소환이 불가능합니다.");
				return;
			}
			if (itemId == 3000123) {
				if (pc.getZoneType() != 0) {// 세이프존
					pc.sendPackets("노멀존(필드) 에서만 사용이 가능합니다.");
					return;
				}
                ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
                    BossSummonWandItems.UseMobEventSpawnWand(pc, l1iteminstance,
                            GameRandom.current());
                    pc.sendPackets("누군가가 소환되었다.");
                });
            } else {
                pc.sendPackets("노멀 존에서만 사용이 가능합니다.");
            }
		}
		break;

        }
    }
}
