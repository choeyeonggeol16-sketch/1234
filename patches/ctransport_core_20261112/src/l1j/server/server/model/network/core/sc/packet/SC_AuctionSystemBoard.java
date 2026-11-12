package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import java.nio.charset.Charset;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;

import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_AuctionSystemBoard {

	private static final Logger _log = Logger.getLogger(SC_AuctionSystemBoard.class.getName());

	private static class ItemNode {
		final int id;
		final String title;
		final String writer;
		final String priceBill;
		final String priceKrw;

		ItemNode(int id, String title, String writer, String priceBill, String priceKrw) {
			this.id = id;
			this.title = title != null ? title : "";
			this.writer = writer != null ? writer : "";
			this.priceBill = priceBill != null ? priceBill : "";
			this.priceKrw = priceKrw != null ? priceKrw : "";
		}
	}

	private static class ReadNode {
		final int number;
		final String title;
		final String priceBill;
		final String priceKrw;
		final String writer;
		final String content;

		ReadNode(int number, String title, String priceBill, String priceKrw, String writer, String content) {
			this.number = number;
			this.title = title != null ? title : "";
			this.priceBill = priceBill != null ? priceBill : "";
			this.priceKrw = priceKrw != null ? priceKrw : "";
			this.writer = writer != null ? writer : "";
			this.content = content != null ? content : "";
		}
	}

	 // 1: 게시판 목록, 2: 게시글 읽기
	
	
	

	/**
	 * 경매 게시판 목록 패킷 생성 정적 팩토리 메서드
	 */
	public static void sendListAsync(l1j.server.server.model.Instance.L1PcInstance pc,
			L1NpcInstance board, int number) {
		if (pc == null || board == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		final int objId = board.getId();
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final Data packet = createList(objId, number);
				CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner) packet.writeTo(owner);
				});
			});
		} catch (RuntimeException e) {
			java.util.logging.Logger.getLogger(SC_AuctionSystemBoard.class.getName())
					.log(java.util.logging.Level.WARNING, "Trade board list request rejected", e);
		}
	}

	public static void sendReadAsync(l1j.server.server.model.Instance.L1PcInstance pc, int number) {
		if (pc == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final Data packet = createRead(number);
				CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner) packet.writeTo(owner);
				});
			});
		} catch (RuntimeException e) {
			java.util.logging.Logger.getLogger(SC_AuctionSystemBoard.class.getName())
					.log(java.util.logging.Level.WARNING, "Trade board read request rejected", e);
		}
	}

	public static Data createList(L1NpcInstance board, int number) {
		return createList(board != null ? board.getId() : 0, number);
	}

	public static Data createList(int boardObjId, int number) {
		List<ItemNode> items = new ArrayList<ItemNode>();

		String sql = "SELECT * FROM Auction ORDER BY id DESC";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next() && items.size() < 8) {
				int id = rs.getInt(1);
				if (number == 0 || id <= number) {
					String title = rs.getString(2);
					String writer = rs.getString(8);
					String priceBill = rs.getString(5);
					String priceKrw = rs.getString(6);
					items.add(new ItemNode(id, title, writer, priceBill, priceKrw));
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}

		return new Data(boardObjId, items);
	}

	/**
	 * 경매 게시판 상세 보기 패킷 생성 정적 팩토리 메서드
	 */
	public static Data createRead(int number) {
		ReadNode readNode = null;
		String sql = "SELECT * FROM Auction WHERE id=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, number);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					String title = rs.getString(2);
					String content = rs.getString(4);
					String priceBill = rs.getString(5);
					String priceKrw = rs.getString(6);
					String writer = rs.getString(8);
					readNode = new ReadNode(number, title, priceBill, priceKrw, writer, content);
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}

		return new Data(readNode);
	}

	

	

	

	

    private static final Charset MS949 = Charset.forName("MS949");

    private static int stringBytes(String s) {
        return (s == null ? 0 : s.getBytes(MS949).length) + 1;
    }

    

    


    public static final class Data {
private final int _mode;

private final int _boardObjId;

private final List<ItemNode> _items;

private final ReadNode _readNode;

private Data(int boardObjId, List<ItemNode> items) {
		_mode = 1;
		_boardObjId = boardObjId;
		_items = items;
		_readNode = null;
	}

private Data(ReadNode readNode) {
		_mode = 2;
		_boardObjId = 0;
		_items = null;
		_readNode = readNode;
	}

private void writeImpl(CIOClient client, WriteBuffer buffer) {
		if (_mode == 1) {
			buffer.writeC(Opcodes.S_BOARD_LIST);
			buffer.writeC(0);
			buffer.writeD(_boardObjId);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0x7F);
			buffer.writeH(_items.size());
			buffer.writeH(300);

			for (ItemNode item : _items) {
				buffer.writeD(item.id);
				buffer.writeS(item.title);
				buffer.writeS(item.writer);
				buffer.writeS("수표:" + item.priceBill + "억 [" + item.priceKrw + "원]");
			}
		} else if (_mode == 2) {
			if (_readNode == null) {
				return;
			}
			buffer.writeC(Opcodes.S_BOARD_READ);
			buffer.writeD(_readNode.number);
			buffer.writeS(_readNode.title);
			buffer.writeS(_readNode.priceBill + "억 [" + _readNode.priceKrw + "원]");
			buffer.writeS(_readNode.writer);
			buffer.writeS("▣내용 클릭시 팅길수 있습니다▣\n\n"
					+ _readNode.content + " : " + _readNode.priceBill + " 억"
					+ "\n판매 금액 : " + _readNode.priceKrw + " 원"
					+ "\n\n▣구매 방법▣\n\n.구매신청 (게시번호) 입력후\n편지 내용 확인.");
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
        if (_mode == 1) {
            long len = 2L + 1 + 1 + 4 + 4 + 2 + 2;
            for (ItemNode n : _items) {
                len += 4 + stringBytes(n.title) + stringBytes(n.writer)
                     + stringBytes("수표:" + n.priceBill + "억 [" + n.priceKrw + "원]");
            }
            return len <= Integer.MAX_VALUE ? (int) len : 0;
        }
        if (_mode == 2 && _readNode != null) {
            return 2 + 1 + 4 + stringBytes(_readNode.title)
                     + stringBytes(_readNode.priceBill + "억 [" + _readNode.priceKrw + "원]")
                     + stringBytes(_readNode.writer)
                     + stringBytes("▣내용 클릭시 팅길수 있습니다▣\n\n"
                          + _readNode.content + " : " + _readNode.priceBill + " 억"
                          + "\n판매 금액 : " + _readNode.priceKrw + " 원"
                          + "\n\n▣구매 방법▣\n\n.구매신청 (게시번호) 입력후\n편지 내용 확인.");
        }
        return 0;
    }
    }
}