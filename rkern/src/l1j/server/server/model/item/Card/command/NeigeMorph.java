package l1j.server.server.model.item.Card.command;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

final class NeigeMorph {
    private NeigeMorph() {}

    static void apply(L1PcInstance pc, int appearance) {
        L1PolyMorph.doPoly(pc, appearance, 14_400, L1PolyMorph.MORPH_BY_ITEMMAGIC, true);
        SC_SkillSound.send(pc, pc.getId(), 6082);
        SC_SkillSound.broadcast(pc, pc.getId(), 6082);
    }
}
