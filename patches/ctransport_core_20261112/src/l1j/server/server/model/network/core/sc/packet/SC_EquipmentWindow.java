package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_EquipmentWindow {
    public static final byte EQUIPMENT_INDEX_HEML = 1; //투구
    public static final byte EQUIPMENT_INDEX_ARMOR = 2;//갑옷
    public static final byte EQUIPMENT_INDEX_T = 3;    //티
    public static final byte EQUIPMENT_INDEX_CLOAK = 4;//장갑
    public static final byte EQUIPMENT_INDEX_PAIR = 5;//각반
    public static final byte EQUIPMENT_INDEX_BOOTS = 6;//부츠
    public static final byte EQUIPMENT_INDEX_GLOVE = 7;//장갑
    public static final byte EQUIPMENT_INDEX_SHIELD = 8;//방패
    public static final byte EQUIPMENT_INDEX_WEAPON = 9;//무기
    public static final byte EQUIPMENT_INDEX_NECKLACE = 11;//목걸이
    public static final byte EQUIPMENT_INDEX_BELT = 12;//벨트
    public static final byte EQUIPMENT_INDEX_EARRING = 13;//귀걸이1
    public static final byte EQUIPMENT_INDEX_EARRING1 = 14;//귀걸이2
    public static final byte EQUIPMENT_INDEX_RING1 = 19;//반지1
    public static final byte EQUIPMENT_INDEX_RING2 = 20;//반지2
    public static final byte EQUIPMENT_INDEX_RING3 = 21;//반지3
    public static final byte EQUIPMENT_INDEX_RING4 = 22;//반지4
    public static final byte EQUIPMENT_INDEX_RUNE1 = 23;//룬
    public static final byte EQUIPMENT_INDEX_RUNE2 = 24;
    public static final byte EQUIPMENT_INDEX_RUNE3 = 25;
    public static final byte EQUIPMENT_INDEX_RUNE4 = 26;
    public static final byte EQUIPMENT_INDEX_RUNE5 = 27;
    public static final byte EQUIPMENT_INDEX_SHOULD = 0x1D;
    public static final byte EQUIPMENT_INDEX_BADGE = 0x1E;
    public static final byte EQUIPMENT_run2 = 28;
    private SC_EquipmentWindow() {}

    public static boolean send(L1PcInstance pc, int itemObjId, int index, boolean equipped) {
        return pc != null && send(pc.getNetConnection(), itemObjId, index, equipped);
    }

    public static boolean send(CIOClient client, int itemObjId, int index, boolean equipped) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(10);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_VOICE_CHAT);
            buffer.writeC(0x42);
            buffer.writeD(itemObjId);
            buffer.writeC(index);
            buffer.writeC(equipped ? 1 : 0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
