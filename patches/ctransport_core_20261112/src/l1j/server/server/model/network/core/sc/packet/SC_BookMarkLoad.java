package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.server.templates.L1BookMark;

public final class SC_BookMarkLoad {
    private static final Logger LOG = Logger.getLogger(SC_BookMarkLoad.class.getName());
    private static final Charset MS949 = Charset.forName("MS949");
    private SC_BookMarkLoad() {}

    private static final class Entry {
        final int id, map, x, y;
        final byte[] name;
        Entry(L1BookMark mark) {
            id = mark.getNumId();
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
            markCount = pc.getMark_count();
            booksize = markCount + 6;
            if (pc._bookmarks != null) {
                synchronized (pc._bookmarks) {
                    for (L1BookMark mark : pc._bookmarks)
                        if (mark != null) marks.add(new Entry(mark));
                }
            }
            if (pc._speedbookmarks != null) {
                synchronized (pc._speedbookmarks) {
                    speed = new int[pc._speedbookmarks.size()];
                    for (int i = 0; i < speed.length; i++) {
                        L1BookMark mark = pc._speedbookmarks.get(i);
                        speed[i] = mark == null ? 0 : mark.getNumId();
                    }
                }
            }
        } catch (Exception ex) {
            LOG.log(Level.WARNING, "SC_BookMarkLoad snapshot failed", ex);
            booksize = 0;
            markCount = 0;
            speed = new int[0];
            marks = new ArrayList<>();
        }
        final int tempSize = Math.max(0, booksize - 1 - marks.size() - speed.length);
        int wireSize = 2 + 5 + marks.size() + speed.length + tempSize + 2 + 2;
        for (Entry mark : marks) {
            if (wireSize > Integer.MAX_VALUE - (11 + mark.name.length)) return false;
            wireSize += 11 + mark.name.length;
        }

        final CIOClient client = pc.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(wireSize);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer b = frame.buffer();
            b.writeC(Opcodes.S_VOICE_CHAT);
            b.writeC(42);
            b.writeC(booksize);
            b.writeC(0);
            b.writeC(2);
            for (int i = 0, n = marks.size(); i < n; i++) b.writeC(i);
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
