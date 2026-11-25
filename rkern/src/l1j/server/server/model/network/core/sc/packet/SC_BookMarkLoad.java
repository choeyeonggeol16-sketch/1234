package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.Assemble;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOBufferEx.CIOFrame;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.templates.BookmarkSlot;

public final class SC_BookMarkLoad {
    private static final Logger LOG = Logger.getLogger(SC_BookMarkLoad.class.getName());
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_BookMarkLoad() {}

    private static final class Entry {
        final int id, order, map, x, y;
        final byte[] name;
        Entry(BookmarkSlot mark) {
            id = mark.getNumId();
            order = mark.getTemp_id();
            String s = mark.getName();
            name = (s == null ? "" : s).getBytes(MS949);
            map = mark.getMapId();
            x = mark.getLocX();
            y = mark.getLocY();
        }
    }

    public static boolean send(L1PcInstance pc) {
        if (pc == null) return false;
        int booksize = 0, markCount = 0;
        int[] speed = new int[0];
        List<Entry> marks = new ArrayList<>();
        try {
            markCount = Math.clamp(pc.getMark_count(), 0, 100);
            booksize = markCount + 6;
            for (BookmarkSlot mark : pc.getBookMarkArray()) marks.add(new Entry(mark));
            final BookmarkSlot[] shortcuts = pc.getSpeedBookMarkArray();
            int last = -1;
            for (BookmarkSlot mark : shortcuts)
                if (mark.getSpeed_id() >= 0 && mark.getSpeed_id() < 5)
                    last = Math.max(last, mark.getSpeed_id());
            speed = new int[last + 1];
            java.util.Arrays.fill(speed, 0xFF);
            for (BookmarkSlot mark : shortcuts)
                if (mark.getSpeed_id() >= 0 && mark.getSpeed_id() < speed.length)
                    speed[mark.getSpeed_id()] = mark.getNumId();
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "SC_BookMarkLoad snapshot failed", ex);
            return false;
        }
        if (marks.size() > markCount) return false;
        // Display order is independent of the stable session wire IDs.
        marks.sort(java.util.Comparator.comparingInt((Entry e) -> e.order).thenComparingInt(e -> e.id));
        final int tempSize = Math.max(0, booksize - 1 - marks.size() - speed.length);
        int wireSize = 2 + 5 + marks.size() + speed.length + tempSize + 2 + 2;
        for (Entry mark : marks) {
            if (wireSize > Integer.MAX_VALUE - (11 + mark.name.length)) return false;
            wireSize += 11 + mark.name.length;
        }

        final CUserSocket client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client;
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final Assemble b = frame.buffer();
            b.writeC(Opcodes.S_VOICE_CHAT);
            b.writeC(42);
            b.writeC(booksize);
            b.writeC(0);
            b.writeC(2);
            for (Entry mark : marks) b.writeC(mark.id);
            for (int num : speed) b.writeC(num);
            for (int i = 0; i < tempSize; i++) b.writeC(0xFF);
            b.writeH(markCount);
            b.writeH(marks.size());
            for (Entry mark : marks) {
                b.writeD(mark.id);
                b.writeByte(mark.name);
                b.writeC(0);
                b.writeH(mark.map);
                b.writeH(mark.x);
                b.writeH(mark.y);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
}
