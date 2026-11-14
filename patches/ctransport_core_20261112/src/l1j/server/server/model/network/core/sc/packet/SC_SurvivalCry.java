package l1j.server.server.model.network.core.sc.packet;

import java.nio.charset.Charset;
import java.util.Collection;

import l1j.server.server.Opcodes;
import l1j.server.server.datatables.AdenShopTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.templates.L1AdenShopItem;

public final class SC_SurvivalCry {
    public static final int LIST = 0;
    public static final int EMAIL = 1;
    public static final int POINT = 2;
    public static final int OTP_SHOW = 4;
    public static final int OTP_CHECK_MSG = 5;
    public static final int 접속모름 = 15;

    private static final byte[] EMAIL_BODY = parseHex(
            "0c 00 26 00 6e 00 75 00 6c 00 6c 00 40 00 6e 00 75 00 6c 00 6c 00 2e 00 63 00 6f 00 6d 00 00 00 20 b8");
    private static final byte[] OTP_BODY = parseHex("02 00 00 f4 ff ff ff 00 00 00 00 00 00 99 17");
    private static final Charset MS949 = Charset.forName("MS949");

    private SC_SurvivalCry() {}

    public static boolean send(L1PcInstance pc, int value) {
        return pc != null && send(pc.getNetConnection(), pc, value);
    }

    public static boolean send(CIOClient client, L1PcInstance pc, int value) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int ncoin = pc != null ? pc.getNcoin() : 0;
        final Collection<L1AdenShopItem> collection = value == LIST
                ? AdenShopTable.getInstance().toArray() : null;
        final L1AdenShopItem[] list = collection == null ? null : collection.toArray(new L1AdenShopItem[0]);
        final int max = list == null ? 0 : list.length;
        final L1AdenShopItem[] selected = value == LIST ? new L1AdenShopItem[max] : null;
        final String[] names = value == LIST ? new String[max] : null;
        final int[] lengths = value == LIST ? new int[max << 1] : null;
        int count = 0;
        long size;
        switch (value) {
            case 0: size = 16L; break;
            case 1: size = 3L + EMAIL_BODY.length; break;
            case 2: size = 15L; break;
            case 3: size = 3L + OTP_BODY.length; break;
            case 4: size = 9L; break;
            case 5: size = 12L; break;
            default: return false;
        }
        if (value == 0) {
            for (int i = 0; i < max; ++i) {
                final L1AdenShopItem item = list[i];
                if (item == null || item.getItem() == null) continue;
                String name = item.getItem().getName();
                if (item.getPackCount() > 1) name += "(" + item.getPackCount() + ")";
                if (item.getItem().getMaxUseTime() > 0) name += " [" + item.getItem().getMaxUseTime() + "]";
                if (name == null) name = "";
                final String html = item.getHtml() == null ? "" : item.getHtml();
                final int nameLength = name.length() * 2 + 2;
                int htmlLength = 2;
                if (!html.isEmpty()) {
                    final byte[] bytes = html.getBytes(MS949);
                    for (int j = 0; j < bytes.length;) {
                        j += (bytes[j] & 0xff) >= 0x7f ? 2 : 1;
                        htmlLength += 2;
                    }
                }
                size += 28L + nameLength + htmlLength;
                if (size > Integer.MAX_VALUE || count >= 65535) return false;
                selected[count] = item;
                names[count] = name;
                lengths[count << 1] = nameLength;
                lengths[(count << 1) + 1] = htmlLength;
                count++;
            }
        }
        final CIOFrame frame = connection.beginDirectFrame((int) size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            switch (value) {
                case 0:
                    buffer.writeC(Opcodes.S_EXTENDED);
                    buffer.writeC(0x02);
                    buffer.writeH(0);
                    buffer.writeD(0);
                    buffer.writeH(count);
                    buffer.writeH(AdenShopTable.data_length);
                    buffer.writeH(AdenShopTable.data_length);
                    for (int i = 0; i < count; ++i) {
                        final L1AdenShopItem item = selected[i];
                        buffer.writeD(item.getItemId());
                        buffer.writeH(item.getItem().getGfxId());
                        buffer.writeH(0);
                        buffer.writeH(lengths[i << 1]);
                        buffer.writeSU16(names[i]);
                        buffer.writeH(lengths[(i << 1) + 1]);
                        buffer.writeSS(item.getHtml() == null ? "" : item.getHtml());
                        buffer.writeD(item.getPrice());
                        buffer.writeH(item.getType());
                        buffer.writeH(item.getStatus());
                        buffer.writeD(0x000C0DBF);
                        buffer.writeD(0x000063);
                    }
                    break;
                case 1:
                    buffer.writeC(Opcodes.S_EXTENDED);
                    buffer.writeByte(EMAIL_BODY);
                    break;
                case 2:
                    buffer.writeC(Opcodes.S_EXTENDED);
                    buffer.writeH(3);
                    buffer.writeH(1);
                    buffer.writeH(4);
                    buffer.writeD(ncoin);
                    buffer.writeH(0);
                    break;
                case 3:
                    buffer.writeC(Opcodes.S_EXTENDED);
                    buffer.writeByte(OTP_BODY);
                    break;
                case 4:
                    buffer.writeC(Opcodes.S_EXTENDED);
                    buffer.writeD(0x33);
                    buffer.writeH(0);
                    break;
                case 5:
                    buffer.writeC(Opcodes.S_EXTENDED);
                    buffer.writeH(5);
                    buffer.writeH(0);
                    buffer.writeD(0);
                    buffer.writeC(0);
                    break;
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static byte[] parseHex(String values) {
        final String[] parts = values.split(" ");
        final byte[] out = new byte[parts.length];
        for (int i = 0; i < out.length; ++i) out[i] = (byte) Integer.parseInt(parts[i], 16);
        return out;
    }
}
