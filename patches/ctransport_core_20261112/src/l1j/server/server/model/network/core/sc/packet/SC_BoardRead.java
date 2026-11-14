package l1j.server.server.model.network.core.sc.packet;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.templates.L1BoardPost;

import l1j.server.server.Account;

import l1j.server.server.storage.CacheD;

import l1j.server.server.model.network.thread.CIOThreadCallback;

public final class SC_BoardRead {

	private static final int CONTENT_MIN_LENGTH = 100;

    private static final class Data {
	private final int _number;
	private final String _name;
	private final String _title;
	private final String _date;
	private final byte[] _contentBytes;
	private final boolean _hasData;

	private Data(int number, String name, String title, String date, byte[] contentBytes, boolean hasData) {
		_number = number;
		_name = name;
		_title = title;
		_date = date;
		_contentBytes = contentBytes;
		_hasData = hasData;
	}
    }


	/**
	 * DB/저장소 조회를 선행하여 완료한 뒤 불변 스냅샷 패킷을 생성하는 정적 팩토리 메서드
	 */
	private static Data loadData(int npcId, int number) {
		if (npcId <= 0) {
			return new Data(number, "", "", "", new byte[0], false);
		}

		L1BoardPost topic = null;

		switch (npcId) {
		case 4200015: // 서버공지
			topic = L1BoardPost.findByIdGM(number);
			break;
		case 4200020: // GM1
			topic = L1BoardPost.findByIdGM1(number);
			break;
		case 4200021: // GM2
			topic = L1BoardPost.findByIdGM2(number);
			break;
		case 71008: // GM3 / GM4
			topic = L1BoardPost.findByIdGM3(number);
			break;
		case 900006: // 드래곤키
			break;
		case 500002: // 건의사항
			topic = L1BoardPost.findByIdPhone(number);
			break;
		default: // 기본값
			topic = L1BoardPost.findById(number);
			break;
		}

		if (topic == null) {
			return new Data(number, "", "", "", new byte[0], false);
		}

		String name = topic.getName() != null ? topic.getName() : "";
		String title = topic.getTitle() != null ? topic.getTitle() : "";
		String date = topic.getDate() != null ? topic.getDate() : "";
		String content = topic.getContent() != null ? topic.getContent() : "";

		byte[] preparedBytes = prepareContentBytes(content);

		return new Data(number, name, title, date, preparedBytes, true);
	}



	/**
	 * MS949 인코딩 변환 및 100바이트 패딩 처리를 선행 완료하여 writeImpl() 단계의 부하를 제거
	 */
	private static byte[] prepareContentBytes(String content) {
		try {
			byte[] contentBytes = content.getBytes("MS949");
			int addedLen = CONTENT_MIN_LENGTH - contentBytes.length;
			if (addedLen <= 0) {
				return contentBytes;
			}

			byte[] padded = new byte[contentBytes.length + addedLen];
			System.arraycopy(contentBytes, 0, padded, 0, contentBytes.length);
			for (int i = contentBytes.length; i < padded.length; i++) {
				padded[i] = 0x20; // 공백 패딩 (0x20)
			}
			return padded;
		} catch (Exception e) {
			e.printStackTrace();
			return new byte[0];
		}
	}

	private static void writeImpl(WriteBuffer buffer, Data obj) {
		if (!obj._hasData) {
			return;
		}

		buffer.writeC(Opcodes.S_BOARD_READ);
		buffer.writeD(obj._number);
		buffer.writeS(obj._name);
		buffer.writeS(obj._title);
		buffer.writeS(obj._date);
		buffer.writeByte(obj._contentBytes);
		buffer.writeS(".");
		buffer.writeC(0x00);
	}

    public static boolean send(L1PcInstance receiver, l1j.server.server.model.Instance.L1NpcInstance board, int number) {
        if (receiver == null) return false;
        final CIOClient client = receiver.getNetConnection();
        if (client == null || !client.isConnected()) return false;
        final CUserSocket conn = client.getConnection();
        final Account account = client.getAccount();
        if (conn == null || !conn.isClientConnected() || account == null
                || client.getActiveChar() != receiver) return false;
        final int npcId = board == null ? 0 : board.getNpcId();
        final int ownerShard = conn.affinityShard();
        try {
            CacheD.getInstance().submitDbJobOrdered(receiver.getId(),
                    CacheD.accountOrderKey(account.getName()), () -> {
                final Data obj;
                try {
                    obj = loadData(npcId, number);
                } catch (Throwable e) {
                    java.util.logging.Logger.getLogger(SC_BoardRead.class.getName())
                            .log(java.util.logging.Level.SEVERE, "SC_BoardRead DB load failed", e);
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
            java.util.logging.Logger.getLogger(SC_BoardRead.class.getName())
                    .log(java.util.logging.Level.WARNING, "SC_BoardRead CacheD submit failed", e);
            return false;
        }
    }

    private static int bytesOf(String s) {
        return (s == null ? 0 : s.getBytes(java.nio.charset.Charset.forName("MS949")).length) + 1;
    }

    private static boolean sendPrepared(CIOClient client, CUserSocket conn, Data obj) {
        if (!(obj._hasData)) return false;
        final CIOFrame frame = conn.beginDirectFrame(2 + 1 + 4 + bytesOf(obj._name) + bytesOf(obj._title) + bytesOf(obj._date) + obj._contentBytes.length + bytesOf(".") + 1);
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
