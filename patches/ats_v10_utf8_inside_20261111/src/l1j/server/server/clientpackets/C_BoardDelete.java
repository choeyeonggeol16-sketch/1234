package l1j.server.server.clientpackets;

import l1j.server.Templates.L1BoardPost;
import l1j.server.server.GameClient;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.BoardInstance;
import l1j.server.server.model.Instance.L1PcInstance;

public class C_BoardDelete extends ClientBasePacket {

	private static final String C_BOARD_DELETE = "[C] C_BoardDelete";


	public C_BoardDelete(byte decrypt[], GameClient client) {
		super(decrypt);
		int objId = readD();
		int topicId = readD();
		L1Object obj = L1World.getInstance().findObject(objId);
		BoardInstance board = (BoardInstance) obj;
		L1PcInstance pc = client.getActiveChar();
		if (pc == null || board == null || obj == null)
			return;

			L1BoardPost topic = L1BoardPost.findByIdGM(topicId);
			if (topic == null) {
				return;
			}
			
			if (!pc.isGm()) {
				pc.sendPackets("운영자만 삭제 가능합니다.");
				return;
			}
			
			String name = client.getActiveChar().getName();
			if (!name.equals(topic.getName())) {
				return;
			}
			
			if (board.getNpcId() == 81177) {
				topic.서버정보();
			} else if (board.getNpcId() == 81131) {
				topic.운영자1();
			} else if (board.getNpcId() == 81132) {
				topic.운영자2();
			} else if (board.getNpcId() == 81133) {
				topic.운영자3();
			} else if (board.getNpcId() == 71008) {
				topic.건의사항();
			} else {
				topic.자유게시판();
		}
	}

	@Override
	public String getType() {
		return C_BOARD_DELETE;
	}
}
