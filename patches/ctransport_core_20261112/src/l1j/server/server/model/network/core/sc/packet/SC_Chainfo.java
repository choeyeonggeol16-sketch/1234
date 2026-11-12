package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.Opcodes;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;

import l1j.server.server.Account;

import l1j.server.server.storage.CacheD;

import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_Chainfo {

	private static final Logger _log = Logger.getLogger(SC_Chainfo.class.getName());

    private static final class Data {
	private final int _number;
	private final String _chaName;
	private final String _contentText;
	private final boolean _hasData;

	private Data(int number, String chaName, String contentText, boolean hasData) {
		_number = number;
		_chaName = chaName != null ? chaName : "";
		_contentText = contentText != null ? contentText : "";
		_hasData = hasData;
	}
    }


	/**
	 * DB 조회를 선행하여 완벽한 스냅샷을 생성하는 정적 팩토리 메서드
	 */
	private static Data loadData(int number, String cha) {
		if (cha == null || cha.isEmpty()) {
			return new Data(number, "", "", false);
		}

		String charObjId = null;
		int level = 0;
		int exp = 0;
		int hp = 0;
		int mp = 0;
		int ac = 0;
		int classType = 0;
		long adenaCount = 0;
		StringBuilder itemsSb = new StringBuilder();

		String charSql = "SELECT objid, level, Exp, Hp, Mp, Ac, Type FROM characters WHERE char_name=?";
		String adenaSql = "SELECT COALESCE(SUM(count),0) AS count FROM character_items WHERE char_id=? AND item_id=40308";
		String itemSql = "SELECT enchantlvl, item_name FROM character_items WHERE char_id=? ORDER BY enchantlvl DESC LIMIT 14";

		// 단 하나의 DB 커넥션으로 3개의 쿼리를 연쇄 처리 (커넥션 풀 고갈 차단)
		try (Connection con = DatabaseFactory.getInstance().getConnection()) {

			// 1. 캐릭터 기본 정보 조회
			try (PreparedStatement pstm = con.prepareStatement(charSql)) {
				pstm.setString(1, cha);
				try (ResultSet rs = pstm.executeQuery()) {
					if (rs.next()) {
						charObjId = rs.getString("objid");
						level = rs.getInt("level");
						exp = rs.getInt("Exp");
						hp = rs.getInt("Hp");
						mp = rs.getInt("Mp");
						ac = rs.getInt("Ac");
						classType = rs.getInt("Type");
					} else {
						return new Data(number, cha, "", false);
					}
				}
			}

			// 2. 아데나 보유량 조회
			if (charObjId != null) {
				try (PreparedStatement pstm = con.prepareStatement(adenaSql)) {
					pstm.setString(1, charObjId);
					try (ResultSet rs = pstm.executeQuery()) {
						if (rs.next()) {
							adenaCount = rs.getLong("count");
						}
					}
				}

				// 3. 인챈트 높은 순 상위 14개 아이템 조회
				try (PreparedStatement pstm = con.prepareStatement(itemSql)) {
					pstm.setString(1, charObjId);
					try (ResultSet rs = pstm.executeQuery()) {
						while (rs.next()) {
							int enchant = rs.getInt("enchantlvl");
							String itemName = rs.getString("item_name");
							itemsSb.append("\n +").append(enchant).append(" ").append(itemName != null ? itemName : "");
						}
					}
				}
			}

		} catch (SQLException e) {
			_log.log(Level.SEVERE, "SC_Chainfo DB 조회 중 예외 발생 - Char: " + cha, e);
			return new Data(number, cha, "", false);
		}

		// 경험치 퍼센트 계산 (0으로 나누기 예외 방지)
		int expPercent = 0;
		int currentLvExp = ExpTable.getExpByLevel(level);
		int nextLvExp = ExpTable.getExpByLevel(level + 1);
		double neededExp = nextLvExp - currentLvExp;
		if (neededExp > 0) {
			double currentExp = exp - currentLvExp;
			expPercent = (int) ((currentExp / neededExp) * 100.0);
		}

		String className = getClassType(classType);

		// 게시판 형태로 보일 텍스트 조합 사전 완료
		String contentText = " <" + cha + " 정보>\n 레벨: " + level + "." + expPercent + "    " +
				"클래스: " + className + "\n HP:" + hp + "  MP:" + mp + " " +
				"AC:" + ac + "\n 아데나:" + adenaCount + itemsSb.toString();

		return new Data(number, cha, contentText, true);
	}



	private static String getClassType(int classType) {
		switch (classType) {
		case 0: case 1: return "군주";
		case 48: case 61: return "기사";
		case 37: case 138: return "요정";
		case 734: case 1186: return "마법사";
		case 2786: case 2796: return "다크엘프";
		case 6658: case 6661: return "용기사";
		case 6671: case 6650: return "환술사";
		case 12490: case 12494: return "전사";
		default: return "미확인";
		}
	}

	private static void writeImpl(WriteBuffer buffer, Data obj) {
		if (!obj._hasData) {
			return;
		}

		buffer.writeC(Opcodes.S_BOARD_READ);
		buffer.writeD(obj._number);
		buffer.writeS("캐릭터 정보");
		buffer.writeS("");
		buffer.writeS(obj._chaName);
		buffer.writeS(obj._contentText);
	}

    public static boolean send(L1PcInstance receiver, int number, String cha) {
        if (receiver == null) return false;
        final CIOClient client = receiver.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        final Account account = client.getAccount();
        if (conn == null || !conn.isClientConnected() || account == null
                || client.getActiveChar() != receiver) return false;
        final int ownerShard = conn.affinityShard();
        try {
            CacheD.getInstance().submitDbJobOrdered(receiver.getId(),
                    CacheD.accountOrderKey(account.getName()), () -> {
                final Data obj;
                try {
                    obj = loadData(number, cha);
                } catch (Throwable e) {
                    java.util.logging.Logger.getLogger(SC_Chainfo.class.getName())
                            .log(java.util.logging.Level.SEVERE, "SC_Chainfo DB load failed", e);
                    return;
                }
                CIOThreadCallback.getInstance().completeSessionReply(client, () -> {
                    if (client.isConnected() && client.getConnection() == conn
                            && conn.isClientConnected() && client.getAccount() == account
                            && client.getActiveChar() == receiver
                            && receiver.getNetConnection() == client) {
                        sendPrepared(client, conn, obj);
                    }
                });
            });
            return true;
        } catch (RuntimeException e) {
            java.util.logging.Logger.getLogger(SC_Chainfo.class.getName())
                    .log(java.util.logging.Level.WARNING, "SC_Chainfo CacheD submit failed", e);
            return false;
        }
    }

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(java.nio.charset.Charset.forName("MS949")).length) + 1;
    }

    private static boolean sendPrepared(CIOClient client, CUserSocket conn, Data obj) {
        if (!(obj._hasData)) return false;
        final CIOFrame frame = conn.beginDirectFrame(2 + 1 + 4 + bytesOf("캐릭터 정보") + bytesOf("") + bytesOf(obj._chaName) + bytesOf(obj._contentText));
        if (frame == null) return false;
        boolean committed = false;
        try {
            writeImpl(frame.buffer(), obj);
            committed = conn.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) conn.abortDirectFrame(frame);
        }
    }
}
