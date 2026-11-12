package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

public final class SC_IdentifyDesc {
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_IdentifyDesc() {}

    public static boolean send(L1PcInstance pc, L1ItemInstance item) {
        return pc != null && send(pc.getNetConnection(), item);
    }

    public static boolean send(CIOClient client, L1ItemInstance item) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;

        final boolean valid = item != null && item.getItem() != null;
        final int desc = valid ? item.getItem().getItemDescId() : 0;
        final int bless = valid ? item.getBless() : 1;
        final String itemName = valid && item.getItem().getNameId() != null ? item.getItem().getNameId() : "";
        final int type2 = valid ? item.getItem().getType2() : 0;
        final int type = valid ? item.getItem().getType() : -1;
        final int itemId = valid ? item.getItem().getItemId() : 0;
        final int enchant = valid ? item.getEnchantLevel() : 0;
        final String prefix = bless == 0 ? "$227 " : bless == 2 ? "$228 " : "";
        String name = prefix + itemName;
        int messageId = 0;
        int argCount = 0;
        String arg2 = null;
        String arg3 = null;
        if (type2 == 1) {
            messageId = 134;
            argCount = 3;
            arg2 = item.getItem().getDmgSmall() + "+" + enchant;
            arg3 = item.getItem().getDmgLarge() + "+" + enchant;
        } else if (type2 == 2) {
            if (itemId == 20383) {
                messageId = 137;
                argCount = 3;
                arg2 = String.valueOf(item.getChargeCount());
            } else {
                messageId = 135;
                argCount = 2;
                arg2 = Math.abs(item.getItem().get_ac()) + "+" + enchant;
            }
        } else if (type2 == 0) {
            if (type == 1) {
                messageId = 137;
                argCount = 3;
                arg2 = String.valueOf(item.getChargeCount());
            } else if (type == 2) {
                messageId = 138;
                argCount = 2;
                name = name + ": $231 " + item.getRemainingTime();
            } else if (type == 7) {
                messageId = 136;
                argCount = 3;
                arg2 = String.valueOf(item.getItem().getFoodVolume());
            } else {
                messageId = 138;
                argCount = 2;
            }
            arg3 = String.valueOf(valid ? item.getWeight() : 0);
        }

        int size = 5;
        if (type2 >= 0 && type2 <= 2) {
            size += 3 + name.getBytes(MS949).length + 1;
            if (arg2 != null) size += arg2.getBytes(MS949).length + 1;
            if (arg3 != null) size += arg3.getBytes(MS949).length + 1;
        }
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_IDENTIFY_CODE);
            buffer.writeH(desc);
            if (type2 >= 0 && type2 <= 2) {
                buffer.writeH(messageId);
                buffer.writeC(argCount);
                buffer.writeS(name);
                if (arg2 != null) buffer.writeS(arg2);
                if (arg3 != null) buffer.writeS(arg3);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
