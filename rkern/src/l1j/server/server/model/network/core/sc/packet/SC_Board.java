package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.nio.charset.Charset;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.Account;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.utill.ConnectionConfig;
import java.util.logging.Logger;
import java.util.logging.Level;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

import java.util.List;

import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.templates.L1BoardPost;

public final class SC_Board {

	private static final int TOPIC_LIMIT = 8;
	private static final Logger LOG = Logger.getLogger(SC_Board.class.getName());

	// 게시글 데이터 스냅샷 노드
	private static class BoardPostNode {
		final int id;
		final String name;
		final String date;
		final String title;

		BoardPostNode(int id, String name, String date, String title) {
			this.id = id;
			this.name = name != null ? name : "";
			this.date = date != null ? date : "";
			this.title = title != null ? title : "";
		}
	}

	// 버그베어 경기 상태 노드
	private static class BugRaceNode {
		final String name;
		final String condition;
		final String winRate;

		BugRaceNode(String name, String condition, String winRate) {
			this.name = name != null ? name : "";
			this.condition = condition != null ? condition : "";
			this.winRate = winRate != null ? winRate : "0.0%";
		}
	}

	private final int _mode;
	private final int _boardObjId;
	private final int _number;
	private final List<BoardPostNode> _posts;
	private final List<BugRaceNode> _bugRaceNodes;

	public static SC_Board create(L1NpcInstance board) {
		return create(board, 0);
	}

	public static SC_Board create(L1NpcInstance board, int number) {
		return createById(board != null ? board.getId() : 0,
				board != null ? board.getNpcId() : 0, number);
	}

	private static SC_Board createById(int boardObjId, int npcId, int number) {
		List<BoardPostNode> posts = new ArrayList<BoardPostNode>();
		List<BugRaceNode> bugNodes = new ArrayList<BugRaceNode>();

		int mode = 0;

		switch (npcId) {
		case 4200015: { // 서버정보게시판
			mode = 1;
			List<L1BoardPost> topics = L1BoardPost.indexGM(number, TOPIC_LIMIT);
			extractPosts(topics, posts);
			break;
		}
		case 4200020:
		case 42000162: { // 운영자1
			mode = 1;
			List<L1BoardPost> topics = L1BoardPost.indexGM1(number, TOPIC_LIMIT);
			extractPosts(topics, posts);
			break;
		}
		case 4200021:
		case 42000163: { // 운영자2
			mode = 1;
			List<L1BoardPost> topics = L1BoardPost.indexGM2(number, TOPIC_LIMIT);
			extractPosts(topics, posts);
			break;
		}
		case 71008:
		case 4200099: { // 운영자3
			mode = 1;
			List<L1BoardPost> topics = L1BoardPost.indexGM3(number, TOPIC_LIMIT);
			extractPosts(topics, posts);
			break;
		}
		case 500002: { // 건의사항
			mode = 1;
			List<L1BoardPost> topics = L1BoardPost.indexPhone(number, TOPIC_LIMIT);
			extractPosts(topics, posts);
			break;
		}
		case 900006: // Board unavailable in the supplied schema.
			mode = 1;
			break;
		case 999999: { // 버경게시판
			mode = 2;
			BugRaceController race = BugRaceController.getInstance();
			if (race != null && race._littleBugBear != null) {
				for (int i = 0; i < 5; i++) {
					String bName = (race._littleBugBear[i] != null) ? race._littleBugBear[i].getName() : "";
					String bCond = race._bugCondition != null ? race._bugCondition[i] : "";
					String bRate = race._winRate != null ? Double.toString(race._winRate[i]) + "%" : "0.0%";
					bugNodes.add(new BugRaceNode(bName, bCond, bRate));
				}
			}
			break;
		}
		case 500001: { // 전체랭킹
			mode = 3;
			break;
		}
		case 4200013: { // 버그게시판 (랭킹)
			mode = 4;
			break;
		}
		case 4200014: { // 수표 감시
			mode = 5;
			break;
		}
		default: { // 기본 게시판
			mode = 1;
			List<L1BoardPost> topics = L1BoardPost.index(number, TOPIC_LIMIT);
			extractPosts(topics, posts);
			break;
		}
		}
		return new SC_Board(mode, boardObjId, number, posts, bugNodes);
	}

	private static void extractPosts(List<L1BoardPost> topics, List<BoardPostNode> targetList) {
		if (topics == null) return;
		for (L1BoardPost topic : topics) {
			if (topic != null) {
				targetList.add(new BoardPostNode(topic.getId(), topic.getName(), topic.getDate(), topic.getTitle()));
			}
		}
	}

	private SC_Board(int mode, int boardObjId, int number, List<BoardPostNode> posts, List<BugRaceNode> bugRaceNodes) {
		_mode = mode;
		_boardObjId = boardObjId;
		_number = number;
		_posts = posts;
		_bugRaceNodes = bugRaceNodes;
	}

	private void writeImpl(CIOClient client, WriteBuffer buffer) {
		switch (_mode) {
		case 1: { // 일반 게시판 패킷 구조
			buffer.writeC(Opcodes.S_BOARD_LIST);
			buffer.writeC(0);
			buffer.writeD(_boardObjId);
			if (_number == 0) {
				buffer.writeD(0x7FFFFFFF);
			} else {
				buffer.writeD(_number);
			}
			buffer.writeC(_posts.size());
			if (_number == 0) {
				buffer.writeC(0);
				buffer.writeH(300);
			}
			for (BoardPostNode post : _posts) {
				buffer.writeD(post.id);
				buffer.writeS(post.name);
				buffer.writeS(post.date);
				buffer.writeS(post.title);
			}
			break;
		}
		case 2: { // 버경 게시판
			buffer.writeC(Opcodes.S_HYPERTEXT);
			buffer.writeD(_boardObjId);
			buffer.writeS("maeno4");
			buffer.writeC(0);
			buffer.writeH(15);
			for (BugRaceNode node : _bugRaceNodes) {
				buffer.writeS(node.name);
				buffer.writeS(node.condition);
				buffer.writeS(node.winRate);
			}
			break;
		}
		case 3: { // 전체 랭킹
			buffer.writeC(Opcodes.S_BOARD_LIST);
			buffer.writeC(0);
			buffer.writeD(_boardObjId);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0x7F);
			buffer.writeH(9);
			buffer.writeH(300);

			String[] titles = {
				"--------- 전      사", "--------- 군      주", "--------- 기      사",
				"--------- 요      정", "--------- 법      사", "--------- 다      엘",
				"--------- 용 기 사", "--------- 환 술 사"
			};

			for (int i = 0; i < 8; ++i) {
				buffer.writeD(i + 1);
				buffer.writeS("");
				buffer.writeS("");
				buffer.writeS(titles[i]);
			}
			break;
		}
		case 4: { // 인챈트 랭킹
			buffer.writeC(Opcodes.S_BOARD_LIST);
			buffer.writeC(0);
			buffer.writeD(_boardObjId);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0x7F);
			buffer.writeH(2);
			buffer.writeH(300);

			buffer.writeD(1); buffer.writeS("정보 조회"); buffer.writeS(""); buffer.writeS("무기 인챈트 랭킹");
			buffer.writeD(2); buffer.writeS("정보 조회"); buffer.writeS(""); buffer.writeS("방어구 인챈트 랭킹");
			for (int i = 2; i < 8; ++i) {
				buffer.writeD(0); buffer.writeS(""); buffer.writeS(""); buffer.writeS("");
			}
			break;
		}
		case 5: { // 수표 감시
			buffer.writeC(Opcodes.S_BOARD_LIST);
			buffer.writeC(0);
			buffer.writeD(_boardObjId);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0x7F);
			buffer.writeH(2);
			buffer.writeH(300);

			buffer.writeD(1); buffer.writeS("수표감시"); buffer.writeS(""); buffer.writeS("1. 인벤 수표 조회");
			buffer.writeD(2); buffer.writeS("수표감시"); buffer.writeS(""); buffer.writeS("2. 창고 수표 조회");
			for (int i = 2; i < 8; ++i) {
				buffer.writeD(0); buffer.writeS(""); buffer.writeS(""); buffer.writeS("");
			}
			break;
		}
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
			LOG.warning("SC_Board capacity calculation overflow mode=" + _mode
					+ " requested=" + capacity);
			return false;
		} finally {
			if (!committed) conn.abortDirectFrame(frame);
		}
	}

	private long frameLength() {
		long length;
		switch (_mode) {
		case 1:
			length = 1 + 1 + 4 + 4 + 1 + (_number == 0 ? 3 : 0);
			for (BoardPostNode p : _posts)
				length += 4 + bytesOf(p.name) + bytesOf(p.date) + bytesOf(p.title);
			return ConnectionConfig.HEADER_SIZE + length;
		case 2:
			length = 1 + 4 + bytesOf("maeno4") + 1 + 2;
			for (BugRaceNode n : _bugRaceNodes)
				length += bytesOf(n.name) + bytesOf(n.condition) + bytesOf(n.winRate);
			return ConnectionConfig.HEADER_SIZE + length;
		case 3: {
			String[] titles = {
				"--------- 전      사", "--------- 군      주", "--------- 기      사",
				"--------- 요      정", "--------- 법      사", "--------- 다      엘",
				"--------- 용 기 사", "--------- 환 술 사"
			};
			length = 1 + 1 + 4 + 4 + 2 + 2;
			for (String title : titles) length += 4 + bytesOf("") + bytesOf("") + bytesOf(title);
			return ConnectionConfig.HEADER_SIZE + length;
		}
		case 4:
			length = 1 + 1 + 4 + 4 + 2 + 2;
			length += 4 + bytesOf("정보 조회") + bytesOf("") + bytesOf("무기 인챈트 랭킹");
			length += 4 + bytesOf("정보 조회") + bytesOf("") + bytesOf("방어구 인챈트 랭킹");
			return ConnectionConfig.HEADER_SIZE + length + 6L * (4 + 3);
		case 5:
			length = 1 + 1 + 4 + 4 + 2 + 2;
			length += 4 + bytesOf("수표감시") + bytesOf("") + bytesOf("1. 인벤 수표 조회");
			length += 4 + bytesOf("수표감시") + bytesOf("") + bytesOf("2. 창고 수표 조회");
			return ConnectionConfig.HEADER_SIZE + length + 6L * (4 + 3);
		default:
			return 0;
		}
	}

	public static boolean send(L1PcInstance pc, L1NpcInstance board) {
		return send(pc, board, 0);
	}

	public static boolean send(L1PcInstance pc, L1NpcInstance board, int number) {
		if (pc == null || board == null) return false;
		final CIOClient client = pc.getNetConnection();
		if (client == null || !client.isConnected()) return false;
		final CUserSocket conn = client.getConnection();
		if (conn == null || !conn.isClientConnected() || client.getActiveChar() != pc) return false;

		final int npcId = board.getNpcId();
		final int boardObjId = board.getId();
		if (npcId == 999999 || npcId == 500001 || npcId == 4200013 || npcId == 4200014) {
			return createById(boardObjId, npcId, number).send(client);
		}

		final Account account = client.getAccount();
		if (account == null) return false;
		final int ownerShard = conn.affinityShard();
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(),
					CacheD.accountOrderKey(account.getName()), () -> {
				final SC_Board snapshot;
				try {
					snapshot = createById(boardObjId, npcId, number);
				} catch (Throwable e) {
					LOG.log(Level.SEVERE, "SC_Board DB snapshot failed npcId=" + npcId, e);
					return;
				}
				if (!CIOThreadCallback.getInstance().completeSessionReply(client, () -> {
					if (client.isConnected() && client.getConnection() == conn
							&& conn.isClientConnected() && client.getAccount() == account
							&& client.getActiveChar() == pc && pc.getNetConnection() == client) {
						if (!snapshot.send(client)) {
							LOG.warning("SC_Board send failed npcId=" + npcId + " number=" + number);
						}
					}
				})) {
					LOG.warning("SC_Board CIO completion rejected npcId=" + npcId);
				}
			});
			return true;
		} catch (RuntimeException e) {
			LOG.log(Level.WARNING, "SC_Board CacheD submission failed npcId=" + npcId, e);
			return false;
		}
	}

}