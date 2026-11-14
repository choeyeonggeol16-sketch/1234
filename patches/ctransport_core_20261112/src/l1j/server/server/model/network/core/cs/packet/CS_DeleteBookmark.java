package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.ThreadPool;
import java.util.concurrent.RejectedExecutionException;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.templates.L1BookMark;

public final class CS_DeleteBookmark {

	private CS_DeleteBookmark() {
	}

	public static void handle(
			CIOClient client,
			String bookmarkName) {

		if (client == null
				|| bookmarkName == null
				|| bookmarkName.isEmpty()) {

			return;
		}

		final L1PcInstance pc =
				client.getActiveChar();

		if (pc == null
				|| pc.isGhost()) {

			return;
		}

        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    L1BookMark.deleteBookmark(pc, bookmarkName);
                } catch (Exception e) {
                    java.util.logging.Logger.getLogger(CS_DeleteBookmark.class.getName())
                            .log(java.util.logging.Level.WARNING, "Bookmark delete failed", e);
                    SC_SystemMessage.send(pc, "기억 장소 삭제에 실패했습니다. 다시 시도해 주세요.");
                }
            });
        } catch (RejectedExecutionException rejected) {
            // A DB operation must not execute synchronously in an I/O
            // completion thread. Leave the bookmark unchanged and notify.
            SC_SystemMessage.send(pc, "서버 저장 작업이 혼잡합니다. 잠시 후 다시 시도해 주세요.");
        }
	}
}