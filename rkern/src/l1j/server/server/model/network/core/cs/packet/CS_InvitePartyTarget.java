package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public final class CS_InvitePartyTarget {

	private CS_InvitePartyTarget() {
	}

	public static void handle(CIOClient client, int type, int targetId, String targetName) {

		if (client == null) {
			return;
		}

		final L1PcInstance pc = client.getActiveChar();

		if (pc == null || pc.isGhost()) {
			return;
		}

		if (pc.getMapId() == 5554 || !pc.is_world()) {
			SC_SystemMessage.send(pc, "해당 맵에서는 파티 기능을 사용 할 수 없습니다.");
			return;
		}

		if (pc.getAutoHunt()) {
			SC_SystemMessage.send(pc, "현재 상태에서는 사용 할 수 없습니다.");
			return;
		}

		switch (type) {
			case 0:
			case 1:
			case 4:
			case 5:
				handleNormalPartyInvite(pc, type, targetId, targetName);
				return;
			case 2:
				handleChatPartyInvite(pc, targetName);
				return;
			case 3:
				handlePassLeader(pc, targetId);
				return;
			default:
				return;
		}
	}

	private static void handleNormalPartyInvite(L1PcInstance pc, int type, int targetId, String targetName) {

		L1Object targetObject;

		/*
		 * 4/5 = 이름 기반 초대
		 */
		if (type == 4 || type == 5) {
			if (targetName == null || targetName.isEmpty()) {
				SC_ServerMessage.send(pc, 109);
				return;
			}

			final L1PcInstance target = L1World.getInstance().getPlayer(targetName);

			if (target == null) {
				SC_ServerMessage.send(pc, 109, targetName);
				return;
			}

			targetObject = target;

		} else {
			/*
			 * 0/1 = objectId 기반 초대
			 */
			targetObject = L1World.getInstance().findObject(targetId);
		}

		if (!(targetObject instanceof L1PcInstance)) {
			return;
		}

		final L1PcInstance targetPc = (L1PcInstance) targetObject;

		if (pc.getId() == targetPc.getId()) {
			return;
		}

		if (targetPc.isInParty()) {
			SC_ServerMessage.send(pc, 415);
			return;
		}

		/*
		 * 이미 파티에 속해 있음.
		 */
		if (pc.isInParty()) {
			if (!pc.getParty().isLeader(pc)) {
				SC_ServerMessage.send(pc, 416);
				return;
			}

			targetPc.setPartyID(pc.getId());

			/*
			 * 1/5 = 자동 분배
			 * 0/4 = 일반 파티
			 */
			if (type == 1 || type == 5) {
				SC_MessageYN.send(targetPc, 954, pc.getName());
			} else {
				SC_MessageYN.send(targetPc, 953, pc.getName());
			}
			return;
		}

		/*
		 * 아직 파티가 없는 상태.
		 */
		targetPc.setPartyID(pc.getId());

		if (type == 0 || type == 4) {
			pc.setPartyType(0);
			SC_MessageYN.send(targetPc, 953, pc.getName());
			return;
		}

		/*
		 * type 1 / 5
		 */
		pc.setPartyType(1);
		SC_MessageYN.send(targetPc, 954, pc.getName());
	}

	private static void handleChatPartyInvite(L1PcInstance pc, String targetName) {

		if (targetName == null || targetName.isEmpty()) {
			return;
		}

		final L1PcInstance targetPc = L1World.getInstance().getPlayer(targetName);

		if (targetPc == null) {
			SC_ServerMessage.send(pc, 109, targetName);
			return;
		}

		if (pc.getId() == targetPc.getId()) {
			return;
		}

		if (targetPc.isInChatParty()) {
			SC_ServerMessage.send(pc, 415);
			return;
		}

		if (pc.isInChatParty()) {
			if (!pc.getChatParty().isLeader(pc)) {
				SC_ServerMessage.send(pc, 416);
				return;
			}

			targetPc.setPartyID(pc.getId());
			SC_MessageYN.send(targetPc, 951, pc.getName());
			return;
		}

		targetPc.setPartyID(pc.getId());
		SC_MessageYN.send(targetPc, 951, pc.getName());
	}

	private static void handlePassLeader(L1PcInstance pc, int targetId) {

		final L1Object targetObject = L1World.getInstance().findObject(targetId);

		if (!(targetObject instanceof L1PcInstance)) {
			return;
		}

		final L1PcInstance targetPc = (L1PcInstance) targetObject;

		if (pc.getId() == targetPc.getId()) {
			return;
		}

		if (!pc.isInParty()) {
			return;
		}

		if (!targetPc.isInParty()) {
			SC_ServerMessage.send(pc, 1696);
			return;
		}

		if (!pc.getParty().isLeader(pc)) {
			SC_ServerMessage.send(pc, 1697);
			return;
		}

		if (pc.getLocation().getTileLineDistance(targetPc.getLocation()) >= 16) {
			SC_ServerMessage.send(pc, 1695);
			return;
		}

		pc.getParty().passLeader(targetPc);
	}
}