package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

public final class CS_Throw {

    private CS_Throw() {
    }

    public static void handle(
            CIOClient client) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        pc.setFishingTime(0);
        pc.setFishingReady(false);
        pc.setFishing(false);

        SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));

        SC_CharVisualUpdate.broadcastPacked(pc, SC_CharVisualUpdate.pack(pc));

        FishingTimeController
                .getInstance()
                .removeMember(pc);

        SC_InventoryIcon.icoEnd(pc, L1SkillId.Fishing_etc);
    }
}