package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.CGameTime;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;

public final class SC_OwnCharStatus {
    private static final int FRAME_SIZE = 52;

    private SC_OwnCharStatus() {}

    public static boolean send(L1PcInstance pc) {
        if (pc == null) return false;
        final CUserSocket connection = pc.getNetConnection();
        if (connection == null || !connection.isConnected() || !connection.isClientConnected()) return false;

        final int objId = pc.getId();
        final int rawLevel = pc.getLevel();
        final int level = rawLevel < 1 ? 1 : Math.min(rawLevel, 127);
        final int exp = pc.getExp();
        final int str = pc.GetTotalStatStr();
        final int intel = pc.GetTotalStatInt();
        final int wis = pc.GetTotalStatWis();
        final int dex = pc.GetTotalStatDex();
        final int con = pc.GetTotalStatCon();
        final int cha = pc.GetTotalStatCha();
        final int currentHp = Math.min(pc.getCurrentHp(), 32767);
        final int maxHp = Math.min(pc.getMaxHp(), 32767);
        final int currentMp = Math.min(pc.getCurrentMp(), 32767);
        final int maxMp = Math.min(pc.getMaxMp(), 32767);
        final int ac = pc.getAc();
        final int rawTime = CGameTime.getWireTime();
        final int time = rawTime - rawTime % 300;
        final int food = pc.get_food();
        final int weight = pc.getInventory() != null ? pc.getInventory().getWeight100() : 0;
        final int lawful = pc.getLawful();
        final int fire = pc.IsResistanceActive() ? pc.GetFireResist() : 0;
        final int water = pc.IsResistanceActive() ? pc.GetWaterResist() : 0;
        final int wind = pc.IsResistanceActive() ? pc.GetWindResist() : 0;
        final int earth = pc.IsResistanceActive() ? pc.GetEarthResist() : 0;
        final int monsterKill = pc.getMonsterkill();

        final CIOFrame frame = connection.beginDirectFrame(FRAME_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble buffer = frame.buffer();
            buffer.writeC(Opcodes.S_STATUS);
            buffer.writeD(objId);
            buffer.writeC(level);
            buffer.writeD(exp);
            buffer.writeC(str);
            buffer.writeC(intel);
            buffer.writeC(wis);
            buffer.writeC(dex);
            buffer.writeC(con);
            buffer.writeC(cha);
            buffer.writeH(currentHp);
            buffer.writeH(maxHp);
            buffer.writeH(currentMp);
            buffer.writeH(maxMp);
            buffer.writeD(ac);
            buffer.writeD(time);
            buffer.writeC(food);
            buffer.writeC(weight);
            buffer.writeH(lawful);
            buffer.writeH(fire);
            buffer.writeH(water);
            buffer.writeH(wind);
            buffer.writeH(earth);
            buffer.writeD(monsterKill);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
