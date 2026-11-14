package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import java.nio.charset.Charset;

import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_ClanWareHouseHistory {

	private static final Logger _log = Logger.getLogger(SC_ClanWareHouseHistory.class.getName());

	// 창고 히스토리 단일 노드 DTO
	private static class HistoryNode {
		final String charName;
		final int type; // 0: 맡겼습니다, 1: 찾았습니다
		final String itemName;
		final int itemCount;
		final int elapsedTime;

		HistoryNode(String charName, int type, String itemName, int itemCount, int elapsedTime) {
			this.charName = charName != null ? charName : "";
			this.type = type;
			this.itemName = itemName != null ? itemName : "";
			this.itemCount = itemCount;
			this.elapsedTime = elapsedTime;
		}
	}



	/**
	 * 혈맹 창고 이용 내역 DB 조회 스냅샷 생성 정적 팩토리
	 */
	public static void sendAsync(L1PcInstance pc) {
		if (pc == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		final int clanId = pc.getClanid();
		if (clanId <= 0) return;
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final Data packet = create(clanId);
				if (!CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner && pc.getClanid() == clanId) {
						packet.writeTo(owner);
					}
				})) _log.warning("Clan warehouse history reply rejected");
			});
		} catch (RuntimeException e) {
			_log.log(Level.WARNING, "Clan warehouse history request rejected", e);
		}
	}

	public static Data create(L1PcInstance pc) {
		if (pc == null) {
			return new Data(Collections.<HistoryNode>emptyList());
		}
		return create(pc.getClanid());
	}

	public static Data create(int clanId) {
		if (clanId == 0) {
			return new Data(Collections.<HistoryNode>emptyList());
		}

		List<HistoryNode> list = new ArrayList<>();
		int realtime = (int) (System.currentTimeMillis() / 1000);
		String sql = "SELECT char_name, item_name, item_count, item_getorput, elapsed_time "
				+ "FROM clan_warehousehistory WHERE clan_id=? ORDER BY elapsed_time DESC";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
				PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, clanId);
			try (ResultSet rs = pstm.executeQuery()) {
				while (rs.next()) {
					String charName = rs.getString("char_name");
					String itemName = rs.getString("item_name");
					int itemCount = rs.getInt("item_count");
					String itemIndex = rs.getString("item_getorput");
					int elapsedTime = (realtime - rs.getInt("elapsed_time")) / 60;

					int type = "맡겼습니다.".equalsIgnoreCase(itemIndex) ? 0 : 1;

					list.add(new HistoryNode(charName, type, itemName, itemCount, elapsedTime));
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "SC_ClanWareHouseHistory DB 조회 중 예외 발생 - clanId: " + clanId, e);
		}

		return new Data(list);
	}







	private static final Charset MS949 = Charset.forName("MS949");

	private static int bytesOf(String s) {
		return (s == null ? 0 : s.getBytes(MS949).length) + 1;
	}





	public static final class Data {
		private final List<HistoryNode> _historyList;

		private Data(List<HistoryNode> historyList) {
			_historyList = historyList != null ? historyList : Collections.<HistoryNode>emptyList();
		}

		private void writeImpl(CIOClient client, WriteBuffer buffer) {
			buffer.writeC(Opcodes.S_EVENT);
			buffer.writeC(117);
			buffer.writeD(_historyList.size());

			for (HistoryNode node : _historyList) {
				buffer.writeS(node.charName);
				buffer.writeC(node.type);
				buffer.writeS(node.itemName);
				buffer.writeD(node.itemCount);
				buffer.writeD(node.elapsedTime);
			}
		}

		private boolean writeTo(CIOClient client) {
			if (client == null || !client.isConnected()) return false;
			final CUserSocket connection = client.getConnection();
			if (connection == null || !connection.isClientConnected()) return false;
			final CIOFrame frame = connection.beginDirectFrame(frameLength());
			if (frame == null) return false;
			boolean committed = false;
			try {
				writeImpl(client, frame.buffer());
				committed = connection.commitDirectFrame(frame);
				return committed;
			} finally {
				if (!committed) connection.abortDirectFrame(frame);
			}
		}

		private int frameLength() {
			int length = 2 + 1 + 1 + 4;
			for (HistoryNode node : _historyList) {
				length += bytesOf(node.charName) + 1 + bytesOf(node.itemName) + 4 + 4;
			}
			return length;
		}
	}
}