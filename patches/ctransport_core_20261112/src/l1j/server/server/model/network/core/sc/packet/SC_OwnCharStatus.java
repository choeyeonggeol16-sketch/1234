package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.L1GameTimeClock;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_OwnCharStatus {
    private static final int FRAME_SIZE = 52;

    private SC_OwnCharStatus() {}

    public static boolean send(L1PcInstance pc) {
        if (pc == null) return false;

        final int objId = pc.getId();
        final int rawLevel = pc.getLevel();
        final int level = rawLevel < 1 ? 1 : Math.min(rawLevel, 127);
        final int exp = pc.getExp();
        final int str = pc.getAbility() != null ? pc.getAbility().getTotalStr() : 0;
        final int intel = pc.getAbility() != null ? pc.getAbility().getTotalInt() : 0;
        final int wis = pc.getAbility() != null ? pc.getAbility().getTotalWis() : 0;
        final int dex = pc.getAbility() != null ? pc.getAbility().getTotalDex() : 0;
        final int con = pc.getAbility() != null ? pc.getAbility().getTotalCon() : 0;
        final int cha = pc.getAbility() != null ? pc.getAbility().getTotalCha() : 0;
        final int currentHp = Math.min(pc.getCurrentHp(), 32767);
        final int maxHp = Math.min(pc.getMaxHp(), 32767);
        final int currentMp = Math.min(pc.getCurrentMp(), 32767);
        final int maxMp = Math.min(pc.getMaxMp(), 32767);
        final int ac = pc.getAC() != null ? pc.getAC().getAc() : 0;
        final int rawTime = L1GameTimeClock.getInstance().getGameTime().getSeconds();
        final int time = rawTime - rawTime % 300;
        final int food = pc.get_food();
        final int weight = pc.getInventory() != null ? pc.getInventory().getWeight100() : 0;
        final int lawful = pc.getLawful();
        final int fire = pc.getResistance() != null ? pc.getResistance().getFire() : 0;
        final int water = pc.getResistance() != null ? pc.getResistance().getWater() : 0;
        final int wind = pc.getResistance() != null ? pc.getResistance().getWind() : 0;
        final int earth = pc.getResistance() != null ? pc.getResistance().getEarth() : 0;
        final int monsterKill = pc.getMonsterkill();

        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(FRAME_SIZE);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
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
