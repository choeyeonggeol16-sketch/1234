package l1j.server.server.model.inventory;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public interface EquipFail {
    void apply(L1PcInstance pc);

    // ===== 공용 Fail ) =====
    EquipFail MSG_ALREADY_EQUIPPED_124 = pc -> SC_ServerMessage.send(pc, 124);
    EquipFail MSG_LEVEL_OVER_129       = pc -> SC_ServerMessage.send(pc, 129);
    EquipFail MSG_SHIELD_BLOCK_128     = pc -> SC_ServerMessage.send(pc, 128);
    EquipFail MSG_CANT_UNEQUIP_CURSE_150 = pc -> SC_ServerMessage.send(pc, 150);
    EquipFail MSG_SLOT_OCCUPIED_144    = pc -> pc.sendPackets(144);

    EquipFail MSG_DUP_ITEM_3278        = pc -> SC_ServerMessage.send(pc, 3278);
    EquipFail MSG_DUP_GRADE_3279       = pc -> SC_ServerMessage.send(pc, 3279);

    EquipFail MSG_SHOULDER_5077        = pc -> SC_ServerMessage.send(pc, 5077);
    EquipFail MSG_BADGE_5078           = pc -> SC_ServerMessage.send(pc, 5078);

    static EquipFail system(String text) {
        return pc -> SC_SystemMessage.send(pc, text);
    }

    static EquipFail text(String text) {
        return pc -> pc.sendPackets(text);
    }
}
