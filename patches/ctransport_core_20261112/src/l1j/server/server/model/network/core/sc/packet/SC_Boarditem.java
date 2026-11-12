package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.nio.charset.Charset;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.utill.ConnectionConfig;

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
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_Boarditem {

	private static final Logger _log = Logger.getLogger(SC_Boarditem.class.getName());

	// 목록 노드 스냅샷
	private static class ItemBoardNode {
		final int id;
		final String title;
		final String writer;
		final String enchant;
		final String name;

		ItemBoardNode(int id, String title, String writer, String enchant, String name) {
			this.id = id;
			this.title = title != null ? title : "";
			this.writer = writer != null ? writer : "";
			this.enchant = enchant != null ? enchant : "";
			this.name = name != null ? name : "";
		}
	}

	// 상세보기 노드 스냅샷
	private static class ItemBoardReadNode {
		final int number;
		final String title;
		final String name;
		final String enchant;
		final String priceFormatted;
		final String writer;
		final String attrFormatted;
		final String blessFormatted;

		ItemBoardReadNode(int number, String title, String name, String enchant, String priceFormatted,
						  String writer, String attrFormatted, String blessFormatted) {
			this.number = number;
			this.title = title != null ? title : "";
			this.name = name != null ? name : "";
			this.enchant = enchant != null ? enchant : "";
			this.priceFormatted = priceFormatted;
			this.writer = writer != null ? writer : "";
			this.attrFormatted = attrFormatted;
			this.blessFormatted = blessFormatted;
		}
	}

	private final int _mode; // 1: 목록, 2: 상세보기
	private final int _boardObjId;
	private final List<ItemBoardNode> _items;
	private final ItemBoardReadNode _readNode;

	/**
	 * 아이템 게시판 목록 정적 팩토리 메서드
	 */
	public static void sendListAsync(L1PcInstance pc,
			L1NpcInstance board, int number) {
		if (pc == null || board == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		final int objId = board.getId();
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final SC_Boarditem packet = createList(objId, number);
				CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner) packet.send(owner);
				});
			});
		} catch (RuntimeException e) {
			Logger.getLogger(SC_Boarditem.class.getName())
					.log(Level.WARNING, "Trade board list request rejected", e);
		}
	}

	public static void sendReadAsync(L1PcInstance pc, int number) {
		if (pc == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final SC_Boarditem packet = createRead(number);
				CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner) packet.send(owner);
				});
			});
		} catch (RuntimeException e) {
			Logger.getLogger(SC_Boarditem.class.getName())
					.log(Level.WARNING, "Trade board read request rejected", e);
		}
	}

	public static SC_Boarditem createList(L1NpcInstance board, int number) {
		return createList(board != null ? board.getId() : 0, number);
	}

	public static SC_Boarditem createList(int boardObjId, int number) {
		List<ItemBoardNode> list = new ArrayList<ItemBoardNode>();

		String sql = "SELECT id, title, writer, item_enchantlvl, name FROM Board_item ORDER BY id DESC";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next() && list.size() < 8) {
				int id = rs.getInt(1);
				if (number == 0 || id <= number) {
					String title = rs.getString(2);
					String writer = rs.getString(3);
					String enchant = rs.getString(4);
					String name = rs.getString(5);
					list.add(new ItemBoardNode(id, title, writer, enchant, name));
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}

		return new SC_Boarditem(boardObjId, list);
	}

	/**
	 * 아이템 게시판 상세 보기 정적 팩토리 메서드
	 */
	public static SC_Boarditem createRead(int number) {
		ItemBoardReadNode readNode = null;
		String sql = "SELECT id, title, name, item_enchantlvl, price, writer, attr, bless FROM Board_item WHERE id=?";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, number);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					String title = rs.getString(2);
					String name = rs.getString(3);
					String enchant = rs.getString(4);
					String priceRaw = rs.getString(5);
					String writer = rs.getString(6);
					String attrRaw = rs.getString(7);
					String blessRaw = rs.getString(8);

					readNode = new ItemBoardReadNode(
						number,
						title,
						name,
						enchant,
						formatPrice(priceRaw),
						writer,
						formatAttribute(attrRaw),
						formatBless(blessRaw)
					);
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}

		return new SC_Boarditem(readNode);
	}

	private SC_Boarditem(int boardObjId, List<ItemBoardNode> items) {
		_mode = 1;
		_boardObjId = boardObjId;
		_items = items;
		_readNode = null;
	}

	private SC_Boarditem(ItemBoardReadNode readNode) {
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

			for (ItemBoardNode item : _items) {
				buffer.writeD(item.id);
				buffer.writeS(item.title);
				buffer.writeS(item.writer);
				buffer.writeS("+ " + item.enchant + " " + item.name);
			}
		} else if (_mode == 2) {
			if (_readNode == null) {
				return;
			}
			buffer.writeC(Opcodes.S_BOARD_READ);
			buffer.writeD(_readNode.number);
			buffer.writeS(_readNode.title);
			buffer.writeS(formatEnchant(_readNode.enchant) + " " + _readNode.name);
			buffer.writeS(_readNode.writer);
			buffer.writeS(readContent());
		}
	}

	private static String formatPrice(String g) {
		if (g == null || g.isEmpty()) return "0 아데나";
		try {
			int count = Integer.parseInt(g);
			if (count >= 10) {
				int sa = count / 10;
				int sb = sa * 10;
				int sc = (count - sb) * 1000;
				if (sc == 0) {
					return sa + "억";
				} else {
					return sa + "억" + sc + "만 아데나";
				}
			} else {
				int sd = count * 1000;
				return sd + "만 아데나";
			}
		} catch (NumberFormatException e) {
			return g + " 아데나";
		}
	}

	private static String formatAttribute(String z) {
		if (z == null || z.isEmpty()) return "없음";
		try {
			int val = Integer.parseInt(z);
			switch (val) {
			case 1: return "화령 1단계";
			case 2: return "화령 2단계";
			case 3: return "화령 3단계";
			case 4: return "화령 4단계";
			case 5: return "화령 5단계";
			case 6: return "수령 1단계";
			case 7: return "수령 2단계";
			case 8: return "수령 3단계";
			case 9: return "수령 4단계";
			case 10: return "수령 5단계";
			case 11: return "지령 1단계";
			case 12: return "지령 2단계";
			case 13: return "지령 3단계";
			case 14: return "지령 4단계";
			case 15: return "지령 5단계";
			case 16: return "풍령 1단계";
			case 17: return "풍령 2단계";
			case 18: return "풍령 3단계";
			case 19: return "풍령 4단계";
			case 20: return "풍령 5단계";
			default: return "없음";
			}
		} catch (NumberFormatException e) {
			return "없음";
		}
	}

	private static String formatBless(String z) {
		if (z == null || z.isEmpty()) return "축복단계없음";
		try {
			int val = Integer.parseInt(z);
			switch (val) {
			case 1: return "1단계";
			case 2: return "2단계";
			case 3: return "3단계";
			case 4: return "4단계";
			case 5: return "5단계";
			default: return "축복단계없음";
			}
		} catch (NumberFormatException e) {
			return "축복단계없음";
		}
	}

	private static String formatEnchant(String z) {
		if (z == null || z.isEmpty()) return "";
		try {
			int val = Integer.parseInt(z);
			return val > 0 ? "+" + val : String.valueOf(val);
		} catch (NumberFormatException e) {
			return z;
		}
	}

	private static final Charset MS949 = Charset.forName("MS949");

	private static int bytesOf(String value) {
		return value == null ? 1 : value.getBytes(MS949).length + 1;
	}

	public boolean send(CIOClient client) {
		if (client == null || !client.isConnected()) return false;
		final CUserSocket conn = client.getConnection();
		if (conn == null || !conn.isClientConnected()) return false;
		final long capacity = frameLength();
		if (capacity <= ConnectionConfig.HEADER_SIZE
				|| capacity > ConnectionConfig.MAX_WIRE_PACKET_SIZE) return false;
		final CIOFrame frame = conn.beginDirectFrame((int) capacity);
		if (frame == null) return false;
		boolean committed = false;
		try {
			writeImpl(client, frame.buffer());
			committed = conn.commitDirectFrame(frame);
			return committed;
		} catch (WriteBuffer.RegisteredCapacityExceededException e) {
			_log.warning("SC_Boarditem capacity calculation overflow mode=" + _mode
					+ " requested=" + capacity);
			return false;
		} finally {
			if (!committed) conn.abortDirectFrame(frame);
		}
	}

	private long frameLength() {
		if (_mode == 1) {
			long length = 1 + 1 + 4 + 4 + 2 + 2;
			for (ItemBoardNode i : _items) {
				length += 4 + bytesOf(i.title) + bytesOf(i.writer)
						+ bytesOf("+ " + i.enchant + " " + i.name);
			}
			return ConnectionConfig.HEADER_SIZE + length;
		}
		if (_mode == 2 && _readNode != null) {
			return ConnectionConfig.HEADER_SIZE + 1 + 4 + bytesOf(_readNode.title)
					+ bytesOf(formatEnchant(_readNode.enchant) + " " + _readNode.name)
					+ bytesOf(_readNode.writer)
					+ bytesOf(readContent());
		}
		return 0;
	}

	private String readContent() {
		return "\n<판매 물품>\n"
				+ "+" + _readNode.enchant + " " + _readNode.name + "\n\n"
				+ "\n<판매 정보>\n판매 금액 : " + _readNode.priceFormatted + "\n\n"
				+ "<판매 옵션 정보(무기/방어구)>\n"
				+ "속성 인첸트 : " + _readNode.attrFormatted + "\n"
				+ "축복단계 : " + _readNode.blessFormatted + "\n\n";
	}
}