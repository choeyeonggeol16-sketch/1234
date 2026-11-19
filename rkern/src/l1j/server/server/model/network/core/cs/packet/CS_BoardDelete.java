package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1BoardInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1BoardPost;

public final class CS_BoardDelete {

    private CS_BoardDelete() {
    }

    public static void handle(
            CIOClient client,
            int objId,
            int topicId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null) {
            return;
        }

        final L1Object obj =
                L1World
                        .getInstance()
                        .findObject(objId);

        if (!(obj instanceof L1BoardInstance)) {
            return;
        }

        final L1BoardInstance board =
                (L1BoardInstance) obj;

        final L1BoardPost topic =
                L1BoardPost.findById(topicId);

        if (topic == null) {
            return;
        }

        final String name =
                pc.getName();

        if (!name.equals(
                topic.getName())) {

            return;
        }

        if (!pc.isGm()) {

            SC_SystemMessage.send(pc, "게시판 글은 삭제 불가능 합니다.");

            return;
        }

        final int npcId =
                board.getNpcId();

        switch (npcId) {

        case 4200015:
            topic.서버정보();
            return;

        case 4200020:
            topic.운영자1();
            return;

        case 4200021:
            topic.운영자2();
            return;

        case 71008:
            topic.운영자3();
            return;

        case 500002:
            topic.건의사항();
            return;

        default:
            topic.자유게시판();
            return;
        }
    }
}