package l1j.server.server.model.item.function;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_BARLOG;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_CURSE_YAHEE;
import static l1j.server.server.model.character.skill.L1SkillId.STATUS_FREEZE;
import static l1j.server.server.model.item.function.TravelUseTables.*;

import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.world.effect.L1EffectSpawn;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
final class DeployableEffectItems {
    private DeployableEffectItems() {}
    static void use(CUserSocket client, L1PcInstance pc, L1ItemInstance l1iteminstance, int itemId) {
        int[] loc = null;
        switch (itemId) {
		case 41260:// 장착
			for (L1Object object : L1World.getInstance().getVisibleObjects(pc, 3)) {
				if (object instanceof L1EffectInstance) {
					if (((L1NpcInstance) object).getNpcTemplate().get_npcId() == 81170) {
						SC_ServerMessage.send(pc, 1162);
						return;
					}
				}
			}
            ItemReceiptOperations.UseDurableConsume(pc, l1iteminstance, () -> {
                final int[] front = pc.getFrontLoc();
                L1EffectSpawn.getInstance().spawnEffect(81170, 300000,
                        front[0], front[1], pc.getMapId());
            });
			break;

        }
    }
}
