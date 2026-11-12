package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import java.nio.charset.Charset;

import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_KillRanking {

	private static final Logger _log = Logger.getLogger(SC_KillRanking.class.getName());

	private static final String[] BOARD_TITLES = {
		"   KILL [1위 ~ 10위]",
		"   KILL [11위 ~ 20위]",
		"   KILL [21위 ~ 30위]",
		"   KILL [31위 ~ 40위]",
		"   KILL [41위 ~ 50위]",
		"   KILL [51위 ~ 60위]",
		"   KILL [61위 ~ 70위]",
		"   KILL [71위 ~ 80위]"
	};

	 // 1: 게시판 목록, 2: 게시글 읽기
	
	
	
	
	
	

	/**
	 * 킬 랭킹 게시판 목록 패킷 생성 정적 팩토리
	 */
	public static void sendReadAsync(L1PcInstance pc, int number) {
		if (pc == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final Data packet = new Data(null, number);
				CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner) packet.writeTo(owner);
				});
			});
		} catch (RuntimeException e) {
			java.util.logging.Logger.getLogger(SC_KillRanking.class.getName())
					.log(java.util.logging.Level.WARNING, "Ranking board request rejected", e);
		}
	}

	public static Data create(L1NpcInstance board) {
		return new Data(board);
	}

	/**
	 * 킬 랭킹 상세 내용 읽기 패킷 생성 정적 팩토리
	 */
	public static Data create(L1PcInstance pc, int number) {
		return new Data(pc, number);
	}

	// 레거시 호출부 호환용 생성자 1 (게시판 목록)
	

	// 레거시 호출부 호환용 생성자 2 (게시글 읽기 - DB 조회 및 가공 사전 수행)
	

	

	private static void loadRankData(int number, String[] names, int[] kills) {
		int limitStart = (number >= 1 && number <= 8) ? (number - 1) * 10 : 0;
		String sql = "SELECT killing, name FROM tb_kda WHERE name NOT IN ('메티스', '미소피아', '카시오페아', '운영자') ORDER BY killing DESC LIMIT ?, 10";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, limitStart);

			try (ResultSet rs = pstm.executeQuery()) {
				int idx = 0;
				while (rs.next() && idx < 10) {
					kills[idx] = rs.getInt(1);
					names[idx] = rs.getString(2);
					idx++;
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	private static String formatDate() {
		TimeZone tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		Calendar cal = Calendar.getInstance(tz);
		int year = cal.get(Calendar.YEAR) - 2000;
		String year2 = (year < 10) ? "0" + year : Integer.toString(year);

		int month = cal.get(Calendar.MONTH) + 1;
		String month2 = (month < 10) ? "0" + month : Integer.toString(month);

		int date = cal.get(Calendar.DATE);
		String date2 = (date < 10) ? "0" + date : Integer.toString(date);

		return year2 + "/" + month2 + "/" + date2;
	}

    private static final Charset MS949 = Charset.forName("MS949");

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(MS949).length) + 1;
    }

    

    

    public static boolean sendList(L1PcInstance pc, L1NpcInstance board) {
        return pc != null && new Data(board).writeTo(pc.getNetConnection());
    }

    public static final class Data {
private final int _mode;

private final int _boardObjId;

private final int _rankConfig;

private final int _number;

private final String _title;

private final String _date;

private final String _content;

public Data(L1NpcInstance board) {
		_mode = 1;
		_boardObjId = board != null ? board.getId() : 0;
		_rankConfig = Config.랭킹순위;
		_number = 0;
		_title = "";
		_date = "";
		_content = "";
	}

public Data(L1PcInstance pc, int number) {
		_mode = 2;
		_boardObjId = 0;
		_rankConfig = 0;
		_number = number;
		_date = formatDate();

		if (number >= 1 && number <= 8) {
			int startRank = (number - 1) * 10 + 1;
			int endRank = number * 10;
			_title = "KILL [" + startRank + "위-" + endRank + "위]";
		} else {
			_title = "KILL 랭킹";
		}

		String[] names = new String[10];
		int[] kills = new int[10];
		for (int i = 0; i < 10; i++) {
			names[i] = "없음.";
			kills[i] = 0;
		}

		loadRankData(number, names, kills);

		StringBuilder sb = new StringBuilder();
		int startIdx = (number >= 1 && number <= 8) ? (number - 1) * 10 : 1;
		for (int i = 0; i < 10; i++) {
			int rankNum = startIdx + i;
			sb.append(" ").append(rankNum).append("위 ").append(names[i]).append(" \n\r")
			  .append("   Kill : ").append(kills[i]).append("\n\r");
		}
		sb.append("     ");
		_content = sb.toString();
	}

private void writeImpl(CIOClient client, WriteBuffer buffer) {
		if (_mode == 1) { // 게시판 목록 출력
			buffer.writeC(Opcodes.S_BOARD_LIST);
			buffer.writeC(0x00);
			buffer.writeD(_boardObjId);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0xFF);
			buffer.writeC(0x7F);
			buffer.writeH(_rankConfig);
			buffer.writeH(300);
			for (int i = 0; i < 8; ++i) {
				buffer.writeD(i + 1);
				buffer.writeS("");
				buffer.writeS("");
				buffer.writeS(BOARD_TITLES[i]);
			}
		} else if (_mode == 2) { // 게시글 읽기 출력
			buffer.writeC(Opcodes.S_BOARD_READ);
			buffer.writeD(_number);
			buffer.writeS("운영자");
			buffer.writeS(_title);
			buffer.writeS(_date);
			buffer.writeS(_content);
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
        if (_mode == 1) {
            int length = 2 + 1 + 1 + 4 + 4 + 2 + 2;
            for (String title : BOARD_TITLES) {
                length += 4 + bytesOf("") + bytesOf("") + bytesOf(title);
            }
            return length;
        }
        return 2 + 1 + 4 + bytesOf("운영자") + bytesOf(_title)
                + bytesOf(_date) + bytesOf(_content);
    }
    }
}