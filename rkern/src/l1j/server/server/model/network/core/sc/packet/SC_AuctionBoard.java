package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import java.nio.charset.Charset;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_AuctionBoard {

	private static class AuctionNode {
		final int id;
		final String name;
		final int area;
		final int month;
		final int day;
		final int price;

		AuctionNode(int id, String name, int area, int month, int day, int price) {
			this.id = id;
			this.name = name;
			this.area = area;
			this.month = month;
			this.day = day;
			this.price = price;
		}
	}

	
	

	/**
	 * [C패킷 / 게임 스레드 처리]
	 * DB 조회를 선행하여 스냅샷 패킷을 생성하는 정적 팩토리 메서드
	 */
	public static void sendAsync(l1j.server.server.model.Instance.L1PcInstance pc, L1NpcInstance board) {
		if (pc == null || board == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		final int objId = board.getId(), x = board.getX(), y = board.getY();
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final Data packet = new Data(objId, loadAuctionNodes(x, y));
				CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner) packet.writeTo(owner);
				});
			});
		} catch (RuntimeException e) {
			java.util.logging.Logger.getLogger(SC_AuctionBoard.class.getName())
					.log(java.util.logging.Level.WARNING, "Auction board list request rejected", e);
		}
	}

	public static Data create(L1NpcInstance board) {
		if (board == null) {
			return new Data(0, new ArrayList<AuctionNode>());
		}
		return new Data(board.getId(), loadAuctionNodes(board.getX(), board.getY()));
	}

	

	private static List<AuctionNode> loadAuctionNodes(int bx, int by) {
		List<AuctionNode> list = new ArrayList<AuctionNode>();
		int minHouseId = 0;
		int maxHouseId = 0;

		if (bx == 33424 && by == 32824) { // 경매 게시판(기란)
			minHouseId = 262145;
			maxHouseId = 262189;
		} else if (bx == 33585 && by == 33235) { // 경매 게시판(하이네)
			minHouseId = 327681;
			maxHouseId = 327691;
		} else if (bx == 33959 && by == 33253) { // 경매 게시판(아덴)
			minHouseId = 458753;
			maxHouseId = 458819;
		} else if (bx == 32612 && by == 32775) { // 경매 게시판(글루디오)
			minHouseId = 524289;
			maxHouseId = 524294;
		}

		if (minHouseId == 0 || maxHouseId == 0) {
			return list;
		}

		String sql = "SELECT house_id, house_name, house_area, deadline, price FROM board_auction WHERE house_id >= ? AND house_id <= ? ORDER BY house_id";
		
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, minHouseId);
			pstm.setInt(2, maxHouseId);

			try (ResultSet rs = pstm.executeQuery()) {
				Calendar cal = Calendar.getInstance();
				while (rs.next()) {
					int id = rs.getInt(1);
					String name = rs.getString(2);
					int area = rs.getInt(3);

					Timestamp ts = rs.getTimestamp(4);
					int month = 0;
					int day = 0;
					if (ts != null) {
						cal.setTimeInMillis(ts.getTime());
						month = cal.get(Calendar.MONTH) + 1;
						day = cal.get(Calendar.DATE);
					}

					int price = rs.getInt(5);
					list.add(new AuctionNode(id, name, area, month, day, price));
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return list;
	}

	/**
	 * [AIO 스레드 처리]
	 * 순수 메모리 버퍼 직렬화 연산 수행
	 */
	

    private static final Charset MS949 = Charset.forName("MS949");

    private static int stringBytes(String s) {
        return (s == null ? 0 : s.getBytes(MS949).length) + 1;
    }

    

    


    public static final class Data {
private final int _boardObjId;

private final List<AuctionNode> _nodes;

public Data(int boardObjId, List<AuctionNode> nodes) {
		_boardObjId = boardObjId;
		_nodes = nodes != null ? nodes : new ArrayList<AuctionNode>();
	}

private void writeImpl(CIOClient client, WriteBuffer buffer) {
		buffer.writeC(Opcodes.S_AGIT_LIST);
		buffer.writeD(_boardObjId);
		buffer.writeH(_nodes.size());

		for (AuctionNode node : _nodes) {
			buffer.writeD(node.id);
			buffer.writeS(node.name);
			buffer.writeH(node.area);
			buffer.writeC(node.month);
			buffer.writeC(node.day);
			buffer.writeD(node.price);
		}
	}

private boolean writeTo(CIOClient client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int required = frameLength();
        if (required < 3) return false;
        final CIOFrame frame = connection.beginDirectFrame(required);
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
        long len = 2L + 1 + 4 + 2;
        for (AuctionNode n : _nodes) len += 4 + stringBytes(n.name) + 2 + 1 + 1 + 4;
        return len <= Integer.MAX_VALUE ? (int) len : 0;
    }
    }
}