package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.DatabaseFactory;
import l1j.server.server.storage.CacheD;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;

public final class SC_LetterList {

	private static final Logger _log = Logger.getLogger(SC_LetterList.class.getName());

	public static final int WRITE_TYPE_PRIVATE_MAIL = 80;
	public static final int WRITE_TYPE_BLOODPLEDGE_MAIL = 81;

	public static final int TYPE_RECEIVE = 0;
	public static final int TYPE_SEND = 1;

	 // 1: 목록, 2: 작성 정보, 3: 본문 읽기, 4: 상태 변경
	
	
	
	
	
	
	
	

	/**
	 * 편지 목록용 DTO 
	 */
	public static class LetterHeaderData {
		public final int id;
		public final int isCheck;
		public final int time;
		public final String sender;
		public final String subject;

		public LetterHeaderData(int id, int isCheck, int time, String sender, String subject) {
			this.id = id;
			this.isCheck = isCheck;
			this.time = time;
			this.sender = sender != null ? sender : "";
			this.subject = subject != null ? subject : "";
		}
	}

	// ==================== 정적 팩토리 메서드 ====================

	public static Data create(L1PcInstance pc, int type, int count) {
		return new Data(pc, type, count);
	}

	public static Data create(int writeType, int id, int type, String name, String title) {
		return new Data(writeType, id, type, name, title);
	}

	public static Data create(L1PcInstance pc, int type, int id, int read) {
		return new Data(pc, type, id, read);
	}

	public static Data create(L1PcInstance pc, int type, int id, boolean value) {
		return new Data(pc, type, id, value);
	}

    /** Read body already authorized and loaded in the caller's transaction. */
    public static Data createRead(int type, int id, String content) {
        return new Data(type, id, content);
    }

    public static void sendListAsync(L1PcInstance pc, int type, int count) {
        if (pc == null || pc.getNetConnection() == null || !pc.getNetConnection().isConnected()) return;
        final CIOClient owner = pc.getNetConnection();
        CacheD.getInstance().submitDbJob(() -> {
            final Data packet = new Data(pc, type, count);
            if (!CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
                if (pc.getNetConnection() == owner && owner.isConnected()) packet.send(owner);
            })) {
                _log.warning("Mail list callback rejected: " + pc.getName());
            }
        });
    }

	// ==================== 레거시 생성자 오버로딩 ====================

	// 1. 편지 목록 조회 (DB 조회 및 DTO 가공을 생성자에서 사전 완료)
	

	// 2. 편지 작성 알림 정보
	

	// 3. 편지 본문 읽기 (DB 조회 사전 완료)
	

	// 4. 단순 상태 플래그 전송
	

	

    public static boolean sendState(L1PcInstance pc, int type, int id, boolean value) {
        return pc != null && new Data(pc, type, id, value).send(pc.getNetConnection());
    }

    public static boolean sendRead(L1PcInstance pc, int type, int id, int read) {
        return pc != null && new Data(pc, type, id, read).send(pc.getNetConnection());
    }

    public static boolean sendWritten(L1PcInstance pc, int writeType, int id, int type, String name, String title) {
        return pc != null && new Data(writeType, id, type, name, title).send(pc.getNetConnection());
    }

    private static long encodedStringLimit(String text, boolean doubled) {
        return text == null ? (doubled ? 2L : 1L)
                : 2L * text.length() + (doubled ? 2L : 1L);
    }

    

    

    public static final class Data {
private final int _mode;

private final int _type;

private final int _writeType;

private final int _id;

private final boolean _value;

private final String _name;

private final String _title;

private final String _content;

private final List<LetterHeaderData> _letters;

public Data(L1PcInstance pc, int type, int count) {
		_mode = 1;
		_type = type;
		_writeType = 0;
		_id = 0;
		_value = false;
		_name = "";
		_title = "";
		_content = "";

		List<LetterHeaderData> list = new ArrayList<>();
		if (pc != null) {
			String sql = "SELECT * FROM letter WHERE receiver=? AND template_id = ? ORDER BY date LIMIT ?";
			try (Connection con = DatabaseFactory.getInstance().getConnection();
				 PreparedStatement pstm = con.prepareStatement(sql)) {

				pstm.setString(1, pc.getName());
				pstm.setInt(2, type);
				pstm.setInt(3, count);

				try (ResultSet rs = pstm.executeQuery()) {
					while (rs.next()) {
						int id = rs.getInt(1);
						int isCheck = rs.getInt(9);
						Timestamp ts = rs.getTimestamp(5);
						int time = ts != null ? (int) (ts.getTime() / 1000L) : 0;
						String sender = rs.getString(3);
						String subject = rs.getString(7);

						list.add(new LetterHeaderData(id, isCheck, time, sender, subject));
					}
				}
			} catch (SQLException e) {
				_log.log(Level.SEVERE, "SC_LetterList list query error for " + pc.getName(), e);
			}
		}
		_letters = list;
	}

public Data(int writeType, int id, int type, String name, String title) {
		_mode = 2;
		_writeType = writeType;
		_id = id;
		_type = type;
		_name = name != null ? name : "";
		_title = title != null ? title : "";
		_value = false;
		_content = "";
		_letters = null;
	}

private Data(int type, int id, String content) {
		_mode = 3;
		_type = type;
		_id = id;
		_writeType = 0;
		_value = false;
		_name = "";
		_title = "";
		_letters = null;
		_content = content == null ? "" : content;
}

public Data(L1PcInstance pc, int type, int id, int read) {
		_mode = 3;
		_type = type;
		_writeType = 0;
		_value = false;
		_name = "";
		_title = "";
		_letters = null;

		int letterId = id;
		String content = "";

		String sql = "SELECT item_object_id, content FROM letter WHERE item_object_id=? AND receiver=? AND template_id=?";
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setInt(1, id);
			pstm.setString(2, pc == null ? "" : pc.getName());
			pstm.setInt(3, type - 16);

			try (ResultSet rs = pstm.executeQuery()) {
				if (rs.next()) {
					letterId = rs.getInt("item_object_id");
					content = rs.getString("content");
					if (content == null) {
						content = "";
					}
				}
			}
		} catch (Exception e) {
			_log.log(Level.WARNING, "SC_LetterList read query error for id: " + id, e);
		}

		_id = letterId;
		_content = content;
	}

public Data(L1PcInstance pc, int type, int id, boolean value) {
		_mode = 4;
		_type = type;
		_id = id;
		_value = value;
		_writeType = 0;
		_name = "";
		_title = "";
		_content = "";
		_letters = null;
	}

private void writeImpl(WriteBuffer buffer) {
		buffer.writeC(Opcodes.S_MAIL_INFO);

		if (_mode == 1) { // 편지 목록 출력
			buffer.writeC(_type);
			if (_letters != null) {
				buffer.writeH(_letters.size());
				for (LetterHeaderData item : _letters) {
					buffer.writeD(item.id);
					buffer.writeC(item.isCheck);
					buffer.writeD(item.time);
					buffer.writeC(0);
					buffer.writeS(item.sender);
					buffer.writeSS(item.subject);
				}
			} else {
				buffer.writeH(0);
			}
		} else if (_mode == 2) { // 작성 알림 출력
			buffer.writeC(_writeType);
			buffer.writeD(_id);
			buffer.writeC(_type);
			buffer.writeS(_name);
			buffer.writeSS(_title);
		} else if (_mode == 3) { // 본문 읽기 출력
			buffer.writeC(_type);
			buffer.writeD(_id);
			buffer.writeSS(_content);
		} else if (_mode == 4) { // 상태값 출력
			buffer.writeC(_type);
			buffer.writeD(_id);
			buffer.writeC(_value ? 1 : 0);
		}
	}

private long frameCapacity() {
        long size = 32L;
        switch (_mode) {
            case 1:
                size += 4;
                for (int i = 0, n = _letters.size(); i < n; i++) {
                    LetterHeaderData d = _letters.get(i);
                    size += 10L + encodedStringLimit(d.sender, false)
                            + encodedStringLimit(d.subject, true);
                }
                break;
            case 2:
                size += 6L + encodedStringLimit(_name, false)
                        + encodedStringLimit(_title, true);
                break;
            case 3:
                size += 5L + encodedStringLimit(_content, true);
                break;
            case 4:
                size += 7L;
                break;
            default:
                throw new IllegalStateException("Mail mode " + _mode);
        }
        if (size > Integer.MAX_VALUE) {
            throw new IllegalStateException("Mail packet too large: " + size);
        }
        return size;
    }

public boolean send(CIOClient client) {
        if (client == null || !client.isConnected()) return false;
        final CUserSocket connection = client.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame((int) frameCapacity());
        if (frame == null) return false;
        boolean committed = false;
        try {
            writeImpl(frame.buffer());
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }
    }
}
