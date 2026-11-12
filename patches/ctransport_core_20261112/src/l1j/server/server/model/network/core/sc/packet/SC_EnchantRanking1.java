package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Calendar;
import java.util.TimeZone;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.utils.SQLUtil;

import l1j.server.server.Account;

import l1j.server.server.storage.CacheD;

import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_EnchantRanking1 {

    private static final class Data {
	private final int _number;
	private final String _date;
	private final String _title;
	private final String[] _name = new String[10];
	private final int[] _value = new int[10];

	// 레거시 호출부 호환용 생성자
	private Data(L1PcInstance pc, int number) {
		_number = number;
		_date = time();
		
		String title = "수표 랭킹";
		switch (number) {
			case 1: title = "수표 랭킹"; break;
			case 2: title = "창고 수표"; break;
		}
		_title = title;

		loadRankData(number);
	}

	private void loadRankData(int number) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			switch (number) {
				case 1:
					pstm = con.prepareStatement(
							"SELECT count, characters.char_name FROM character_items, characters WHERE item_id in(select item_id from etcitem) And char_id in(select objid from characters where AccessLevel = 0) And character_items.char_id=characters.objid And item_id = 400254 order by count desc limit 10");
					break;
				case 2:
					pstm = con.prepareStatement(
							"SELECT count, accounts.login FROM character_warehouse, accounts WHERE login in(select login from accounts where access_level = 0) And character_warehouse.account_name =accounts.login And item_id = 400254 order by count desc limit 10");
					break;
				default:
					pstm = con.prepareStatement("SELECT char_name FROM characters WHERE AccessLevel = 0 order by Exp desc limit 10");
					break;
			}

			rs = pstm.executeQuery();
			int i = 0;
			if (number == 1 || number == 2) {
				while (rs.next() && i < 10) {
					_value[i] = rs.getInt(1);
					_name[i] = rs.getString(2);
					i++;
				}
			} else {
				while (rs.next() && i < 10) {
					_name[i] = rs.getString(1);
					i++;
				}
			}
			
			while (i < 10) {
				if (_name[i] == null) {
					_name[i] = "없음.";
				}
				i++;
			}
		} catch (SQLException e) {
			// 예외 처리
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}    private String content() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10; i++) {
            int rank = i + 1;
            sb.append("  ").append(rank).append("위 ").append(_value[i]).append("장의 수표\n\r");
            sb.append("  소유자 : ").append(_name[i]).append("\n\r");
        }
        sb.append("      ");
        return sb.toString();
    }

    }


	/**
	 * 수표 랭킹 게시판 패킷 생성 정적 팩토리
	 */
	private static Data loadData(L1PcInstance pc, int number) {
		return new Data(pc, number);
	}



	private static String time() {
		TimeZone tz = TimeZone.getTimeZone(Config.TIME_ZONE);
		Calendar cal = Calendar.getInstance(tz);
		int year = cal.get(Calendar.YEAR) - 2000;
		String year2 = year < 10 ? "0" + year : Integer.toString(year);
		int Month = cal.get(Calendar.MONTH) + 1;
		String Month2 = Month < 10 ? "0" + Month : Integer.toString(Month);
		int date = cal.get(Calendar.DATE);
		String date2 = date < 10 ? "0" + date : Integer.toString(date);
		return year2 + "/" + Month2 + "/" + date2;
	}

	private static void writeImpl(WriteBuffer buffer, Data obj) {
		buffer.writeC(Opcodes.S_BOARD_READ);
		buffer.writeD(obj._number);
		buffer.writeS("GM");
		buffer.writeS(obj._title);
		buffer.writeS(obj._date);

		buffer.writeS(obj.content());
	}

    public static boolean send(L1PcInstance receiver, L1PcInstance pc, int number) {
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
                    obj = loadData(null, number);
                } catch (Throwable e) {
                    java.util.logging.Logger.getLogger(SC_EnchantRanking1.class.getName())
                            .log(java.util.logging.Level.SEVERE, "SC_EnchantRanking1 DB load failed", e);
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
            java.util.logging.Logger.getLogger(SC_EnchantRanking1.class.getName())
                    .log(java.util.logging.Level.WARNING, "SC_EnchantRanking1 CacheD submit failed", e);
            return false;
        }
    }

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(java.nio.charset.Charset.forName("MS949")).length) + 1;
    }

    private static boolean sendPrepared(CIOClient client, CUserSocket conn, Data obj) {
        if (!(true)) return false;
        final CIOFrame frame = conn.beginDirectFrame(2 + 1 + 4 + bytesOf("GM") + bytesOf(obj._title) + bytesOf(obj._date) + bytesOf(obj.content()));
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
