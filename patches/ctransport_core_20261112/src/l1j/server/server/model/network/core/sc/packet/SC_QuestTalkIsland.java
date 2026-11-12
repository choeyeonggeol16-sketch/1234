package l1j.server.server.model.network.core.sc.packet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.QuestInfoTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1QuestInfo;
import l1j.server.server.templates.L1QuestView;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.utils.BinaryOutputStream;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

public final class SC_QuestTalkIsland {
    private SC_QuestTalkIsland() {}
	// 퀘스트 서브 아이템 스냅샷 DTO
	private static class QuestSubItemInfo {
		private final int id;
		private final int currentVal;
		private final int maxVal;

		public QuestSubItemInfo(int id, int currentVal, int maxVal) {
			this.id = id;
			this.currentVal = currentVal;
			this.maxVal = maxVal;
		}
	}

	// 퀘스트 개별 정보 불변 스냅샷 DTO
	private static class QuestInfoSnapshot {
		private final int questId;
		private final long startTime;
		private final long endTime;
		private final boolean isEnd;
		private final List<QuestSubItemInfo> subItems;

		public QuestInfoSnapshot(int questId, L1QuestInfo info) {
			this.questId = questId;
			this.startTime = info.st_time;
			this.endTime = info.end_time;
			this.isEnd = (info.end_time != 0);

			L1QuestView view = QuestInfoTable.getInstance().getQuestView(questId);
			List<QuestSubItemInfo> items = new ArrayList<>();

			if (view == null) {
				int current = (info.ck != null && info.ck.length > 0) ? info.ck[0] : 0;
				items.add(new QuestSubItemInfo(1, current, 1));
			} else {
				for (int i = 0; i < 4; i++) {
					if (view.max_count[i] != 0) {
						int current = (info.ck != null && info.ck.length > i) ? info.ck[i] : 0;
						items.add(new QuestSubItemInfo(i + 1, current, view.max_count[i]));
					}
				}
			}
			this.subItems = Collections.unmodifiableList(items);
		}
	}


    public static boolean sendUpdate(L1PcInstance pc, int questId, L1QuestInfo info) {
        return pc != null && sendUpdate(pc.getNetConnection(), questId, info);
    }

    public static boolean sendUpdate(CIOClient client, int questId, L1QuestInfo info) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final byte[] status = info == null ? null : buildQuestStatusBytes(new QuestInfoSnapshot(questId, info));
        final int size = 7 + (status == null ? 0 : 2 + status.length);
        final CIOFrame frame = connection.beginDirectFrame(size);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeC(0x07);
            buffer.writeC(0x02);
            if (status != null) {
                buffer.writeC(0x0a);
                buffer.writeC(status.length);
                buffer.writeByte(status);
            }
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

	private static byte[] buildQuestStatusBytes(QuestInfoSnapshot snap) {
		BinaryOutputStream os = new BinaryOutputStream();

		os.writeC(0x08); // 퀘스트 번호
		os.writeBit(snap.questId);

		os.writeC(0x10); // 시작 시간
		os.writeBit(snap.startTime);

		if (snap.isEnd) {
			os.writeC(0x18); // 종료 시간
			os.writeBit(snap.endTime);
		}

		for (QuestSubItemInfo sub : snap.subItems) {
			os.writeC(0x22);
			os.writeC(0x06);

			os.writeC(0x08);
			os.writeC(sub.id);

			os.writeC(0x10); // 현목표
			os.writeC(sub.currentVal);

			os.writeC(0x18); // 최종목표
			os.writeC(sub.maxVal);
		}

		os.writeC(0x28);
		os.writeC(0x01);

		try {
			os.close();
		} catch (IOException ignored) {}

		return os.getBytes();
	}
}
