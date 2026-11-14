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
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import java.nio.charset.Charset;

import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.utils.SQLUtil;

public final class SC_EnchantRanking {

	
	
	
	
	
	
	

	/**
	 * 인챈트 및 자산 랭킹 게시판 패킷 생성 정적 팩토리
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
			java.util.logging.Logger.getLogger(SC_EnchantRanking.class.getName())
					.log(java.util.logging.Level.WARNING, "Ranking board request rejected", e);
		}
	}

	public static Data create(L1PcInstance pc, int number) {
		return new Data(pc, number);
	}

	// 레거시 호출부 호환용 생성자
	

	

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

	

	

    private static final Charset MS949 = Charset.forName("MS949");

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(MS949).length) + 1;
    }

    

    

    public static final class Data {
private final int _number;

private final String _date;

private final String _title;

private final String _content;

private final String[] _name = new String[10];

private final String[] _name1 = new String[10];

private final int[] _value = new int[10];

public Data(L1PcInstance pc, int number) {
		_number = number;
		_date = time();
		
		String title = "랭킹";
		switch (number) {
			case 1: title = "무기 인챈트 랭킹"; break;
			case 2: title = "방어구 인챈트 랭킹"; break;
			case 3: title = "아데나 랭킹"; break;
			case 4: title = "창고 아데나 랭킹"; break;
			case 5: title = "(1천만)수표 랭킹"; break;
			case 6: title = "(1천만)창고 수표 랭킹"; break;
			case 7: title = "(1억)수표 랭킹"; break;
			case 8: title = "(1억)창고 수표 랭킹"; break;
		}
		_title = title;

		loadRankData(number);
		_content = buildContent();
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
							"SELECT enchantlvl, weapon.name, characters.char_name FROM character_items, weapon, characters WHERE character_items.item_id in(select item_id from weapon) And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=weapon.item_id And character_items.char_id=characters.objid And count = 1 order by character_items.enchantlvl desc limit 10");
					break;
				case 2:
					pstm = con.prepareStatement(
							"SELECT enchantlvl, armor.name, characters.char_name FROM character_items, armor, characters WHERE character_items.item_id in(select item_id from armor) And character_items.char_id in(select objid from characters where AccessLevel = 0) And character_items.item_id=armor.item_id And character_items.char_id=characters.objid And count = 1 order by character_items.enchantlvl desc limit 10");
					break;
				case 3:
					pstm = con.prepareStatement(
							"SELECT count, characters.char_name FROM character_items, characters WHERE item_id in(select item_id from etcitem) And char_id in(select objid from characters where AccessLevel = 0) And character_items.char_id=characters.objid And item_id = 40308 order by count desc limit 10");
					break;
				case 4:
					pstm = con.prepareStatement(
							"SELECT count, accounts.login FROM character_warehouse, accounts WHERE login in(select login from accounts where access_level = 0) And character_warehouse.account_name =accounts.login And item_id = 40308 order by count desc limit 10");
					break;
				case 5:
					pstm = con.prepareStatement(
							"SELECT count, characters.char_name FROM character_items, characters WHERE item_id in(select item_id from etcitem) And char_id in(select objid from characters where AccessLevel = 0) And character_items.char_id=characters.objid And item_id = 600254 order by count desc limit 10");
					break;
				case 6:
					pstm = con.prepareStatement(
							"SELECT count, accounts.login FROM character_warehouse, accounts WHERE login in(select login from accounts where access_level = 0) And character_warehouse.account_name =accounts.login And item_id = 600254 order by count desc limit 10");
					break;
				case 7:
					pstm = con.prepareStatement(
							"SELECT count, characters.char_name FROM character_items, characters WHERE item_id in(select item_id from etcitem) And char_id in(select objid from characters where AccessLevel = 0) And character_items.char_id=characters.objid And item_id = 400254 order by count desc limit 10");
					break;
				case 8:
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
					_name1[i] = rs.getString(3);
					i++;
				}
			} else if (number >= 3 && number <= 8) {
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
	}

private void writeImpl(CIOClient client, WriteBuffer buffer) {
		buffer.writeC(Opcodes.S_BOARD_READ);
		buffer.writeD(_number);
		buffer.writeS("메티스");
		buffer.writeS(_title);
		buffer.writeS(_date);

		buffer.writeS(_content);
	}

private String buildContent() {
		StringBuilder sb = new StringBuilder();
		sb.append("\n\r");
		for (int i = 0; i < 10; i++) {
			int rank = i + 1;
			if (_number == 1 || _number == 2) {
				sb.append("  ").append(rank).append("위 +").append(_value[i]).append(" ").append(_name[i]).append("\n\r");
				sb.append("  소유자 : ").append(_name1[i]).append("\n\r");
			} else if (_number == 3) {
				sb.append("  ").append(rank).append("위 $ ").append(_value[i]).append(" 아데나\n\r");
				sb.append("  소유자 : ").append(_name[i]).append("\n\r");
			} else if (_number == 4) {
				sb.append("  ").append(rank).append("위 $ : ").append(_value[i]).append(" 아데나\n\r");
				sb.append("   계정명 : ").append(_name[i]).append("\n\r");
			} else if (_number >= 5 && _number <= 8) {
				sb.append("  ").append(rank).append("위 ").append(_value[i]).append("개의 수표\n\r");
				sb.append("  소유자 : ").append(_name[i]).append("\n\r");
			}
		}
		sb.append("     ");
		return sb.toString();
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
        return 2 + 1 + 4 + bytesOf("메티스") + bytesOf(_title)
                + bytesOf(_date) + bytesOf(_content);
    }
    }
}