package l1j.server.server.model;

import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.network.core.sc.packet.SC_Light;

final class CLight {
    private static final int LIGHT_SPELL_RADIUS = 14;

    private CLight() {
    }

    static int calculate(L1Character character) {
        final int npcRadius = character instanceof L1NpcInstance npc ? npc.getLightSize() : 0;
        final int radius = character.hasSkillEffect(L1SkillId.LIGHT) ? LIGHT_SPELL_RADIUS : npcRadius;
        final CInventory inventory = character.getInventory();
        return inventory == null ? radius : inventory.getLightRadius(radius);
    }

    static void send(L1Character character, int radius) {
        if (character instanceof L1PcInstance pc) {
            SC_Light.send(pc.getNetConnection(), pc.getId(), radius);
        }
        if (!character.isInvisble()) {
            SC_Light.broadcast(character, radius);
        }
    }
}
