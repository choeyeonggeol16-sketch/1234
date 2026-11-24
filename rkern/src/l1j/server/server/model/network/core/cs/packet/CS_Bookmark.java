package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;

public final class CS_Bookmark {
    private CS_Bookmark() {}
    public static void handle(CUserSocket client, String bookmarkName) {
        if (client == null) return;
        final L1PcInstance pc = client.getActiveChar();
        if (pc != null) pc.getUserBookmark().SaveSlot(pc, bookmarkName);
    }
}
