package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;

import l1j.server.DatabaseFactory;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import java.nio.charset.Charset;

import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_AuctionBoardRead {

	
	
	
	
	
	
	
	
	
	
	
	

	/**
	 * DB 조회를 선행 처리하여 완벽한 스냅샷을 만들어내는 정적 팩토리 메서드
	 */
	public static void sendAsync(l1j.server.server.model.Instance.L1PcInstance pc,
			int objectId, String houseNumber) {
		if (pc == null) return;
		final CIOClient owner = pc.getNetConnection();
		if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
		try {
			CacheD.getInstance().submitDbJobOrdered(pc.getId(), () -> {
				final Data packet = create(objectId, houseNumber);
				CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
					if (owner.isConnected() && owner.getActiveChar() == pc
							&& pc.getNetConnection() == owner) packet.writeTo(owner);
				});
			});
		} catch (RuntimeException e) {
			java.util.logging.Logger.getLogger(SC_AuctionBoardRead.class.getName())
					.log(java.util.logging.Level.WARNING, "Auction board read request rejected", e);
		}
	}

	public static Data create(int objectId, String houseNumber) {
		int number;
		try {
			number = Integer.parseInt(houseNumber);
		} catch (NumberFormatException e) {
			return new Data(objectId, houseNumber, "", "", "", "", "", "", "", "", "", false);
		}

		String sql = "SELECT * FROM board_auction WHERE house_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, number);
			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					String houseName = rs.getString(2);
					String area = String.valueOf(rs.getInt(3));
					Timestamp ts = rs.getTimestamp(4);
					String price = String.valueOf(rs.getInt(5));
					String location = rs.getString(6);
					String oldOwner = rs.getString(7);
					String bidder = rs.getString(9);

					int month = 0;
					int day = 0;
					int hour = 0;
					if (ts != null) {
						Calendar cal = Calendar.getInstance();
						cal.setTimeInMillis(ts.getTime());
						month = cal.get(Calendar.MONTH) + 1;
						day = cal.get(Calendar.DATE);
						hour = cal.get(Calendar.HOUR_OF_DAY);
					}

					return new Data(
						objectId,
						houseNumber,
						houseName != null ? houseName : "",
						location != null ? location : "",
						area,
						oldOwner != null ? oldOwner : "",
						bidder != null ? bidder : "",
						price,
						String.valueOf(month),
						String.valueOf(day),
						String.valueOf(hour),
						true
					);
				}
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}

		return new Data(objectId, houseNumber, "", "", "", "", "", "", "", "", "", false);
	}

	

	

    private static final Charset MS949 = Charset.forName("MS949");

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(MS949).length) + 1;
    }

    

    

    public static final class Data {
private final int _objectId;

private final String _houseNumber;

private final String _houseName;

private final String _location;

private final String _area;

private final String _oldOwner;

private final String _bidder;

private final String _price;

private final String _month;

private final String _day;

private final String _hour;

private final boolean _hasData;

private Data(int objectId, String houseNumber, String houseName, String location,
			String area, String oldOwner, String bidder, String price, String month, String day,
			String hour, boolean hasData) {
		_objectId = objectId;
		_houseNumber = houseNumber;
		_houseName = houseName;
		_location = location;
		_area = area;
		_oldOwner = oldOwner;
		_bidder = bidder;
		_price = price;
		_month = month;
		_day = day;
		_hour = hour;
		_hasData = hasData;
	}

private void writeImpl(CIOClient client, WriteBuffer buffer) {
		if (!_hasData) {
			return;
		}

		buffer.writeC(Opcodes.S_HYPERTEXT);
		buffer.writeD(_objectId);
		buffer.writeS("agsel");
		buffer.writeS(_houseNumber);
		buffer.writeH(9);
		buffer.writeS(_houseName);
		buffer.writeS(_location);
		buffer.writeS(_area);
		buffer.writeS(_oldOwner);
		buffer.writeS(_bidder);
		buffer.writeS(_price);
		buffer.writeS(_month);
		buffer.writeS(_day);
		buffer.writeS(_hour);
	}

private boolean writeTo(CIOClient client) {
        if (!_hasData) return false;
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
        if (!_hasData) return 0;
        return 2 + 1 + 4 + bytesOf("agsel") + bytesOf(_houseNumber) + 2
                + bytesOf(_houseName) + bytesOf(_location) + bytesOf(_area)
                + bytesOf(_oldOwner) + bytesOf(_bidder) + bytesOf(_price)
                + bytesOf(_month) + bytesOf(_day) + bytesOf(_hour);
    }
    }
}