package l1j.server.server.model.item.Card.command;

import l1j.server.server.model.character.transform.TransformType;
import l1j.server.server.model.character.transform.CTransform;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

final class NeigeMorph {
    private NeigeMorph() {}

    static void apply(L1PcInstance pc, int appearance) {
        if (pc.Transform(appearance, 14_400, TransformType.ITEM_MAGIC, true)) sound(pc);
    }

    static void applyPaid(L1PcInstance pc, int appearance) {
        CTransform.TransformWithCost(pc, appearance, 14_400, TransformType.ITEM_MAGIC, true,
                875640379, 1, () -> sound(pc));
    }

    private static void sound(L1PcInstance pc) {
        SC_SkillSound.send(pc, pc.getId(), 6082);
        SC_SkillSound.broadcast(pc, pc.getId(), 6082);
    }
}
