package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.packets.Protocol.Proto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.storage.CacheD;

import l1j.server.DatabaseFactory;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;

public final class SC_TamWindow extends ProtoPacket {

    private static final Logger LOG = Logger.getLogger(SC_TamWindow.class.getName());

    public static void sendAsync(final L1PcInstance pc) {
        if (pc == null || pc.getNetConnection() == null) return;
        final CIOClient socket = pc.getNetConnection();
        final String account = pc.getAccountName();
        if (account == null || account.isEmpty()) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(account, () -> {
                try {
                    if (!socket.isConnected()) return;
                    final SC_TamWindow packet = create(account);
                    if (!CIOThreadCallback.getInstance().completeSessionReply(socket, () -> {
                        if (socket.isConnected() && socket.getActiveChar() == pc &&
                                account.equals(pc.getAccountName())) pc.sendPackets(packet);
                    })) {
                        LOG.warning("TAM window owner callback failed: " + account);
                    }
                } catch (Throwable e) {
                    LOG.log(Level.WARNING, "TAM window async load failed: " + account, e);
                }
            });
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "TAM window DB submission rejected: " + account, e);
        }
    }


	public static final int SC_SPELL_BUFF_NOTI = 0x006E;
	public static final int SC_ACCOUNT_TAM     = 0x01CD;

	private static final int MODE_BUFF        = 1;
	private static final int MODE_ACCOUNT_TAM = 2;

	private final int _mode;

	private final int _timeSec;
	private final int _buffType;

	private final TamCharacterSnapshot[] _characters;


	private static final class TamCharacterSnapshot {

		final int objId;
		final int level;
		final int clazz;
		final int sex;
		final int tamWait;

		final int remainSec;
		final String name;

		TamCharacterSnapshot(
				int objId,
				int level,
				int clazz,
				int sex,
				String name,
				int remainSec,
				int tamWait) {

			this.objId = objId;
			this.level = level;
			this.clazz = clazz;
			this.sex = sex;

			this.name = name != null ? name : "";

			this.remainSec = remainSec;
			this.tamWait = tamWait;
		}
	}


	// ========================================================================
	// FACTORY
	// ========================================================================

	public static SC_TamWindow create(String account) {
		return new SC_TamWindow(loadTamData(account));
	}


	public static SC_TamWindow createBuff(long timeMillis, int buffType) {
		long sec = timeMillis > 0L ? timeMillis / 1000L : 0L;
		int timeSec = sec > Integer.MAX_VALUE
				? Integer.MAX_VALUE
				: (int) sec;

		return new SC_TamWindow(timeSec, buffType);
	}


	private SC_TamWindow(TamCharacterSnapshot[] characters) {
		_mode = MODE_ACCOUNT_TAM;

		_timeSec = 0;
		_buffType = 0;

		_characters = characters != null
				? characters
				: new TamCharacterSnapshot[0];
	}


	private SC_TamWindow(int timeSec, int buffType) {
		_mode = MODE_BUFF;

		_timeSec = Math.max(0, timeSec);
		_buffType = buffType;

		_characters = null;
	}


	// ========================================================================
	// PROTO PACKET
	// ========================================================================

	@Override
	public int msgId() {
		return _mode == MODE_BUFF
				? SC_SPELL_BUFF_NOTI
				: SC_ACCOUNT_TAM;
	}


	@Override
	protected void body(ProtoWriter w) {
		if (_mode == MODE_BUFF) {
			writeBuff(w);
		} else {
			writeAccountTam(w);
		}
	}


	// ========================================================================
	// 0x006E - SC_SPELL_BUFF_NOTI
	// ========================================================================

	private void writeBuff(ProtoWriter w) {

		w.int32(1, 2);
		w.int32(2, 2272);
		w.int32(3, _timeSec);

		w.int32(4, 8);

		switch (_buffType) {
		case 2:
			w.int32(5, 6547);
			w.int32(6, 0);
			w.int32(7, 3);
			w.int32(8, 4178);
			w.int32(9, 4182);
			break;

		case 3:
			w.int32(5, 6546);
			w.int32(6, 0);
			w.int32(7, 3);
			w.int32(8, 4179);
			w.int32(9, 4183);
			break;

		case 1:
		default:
			w.int32(5, 6100);
			w.int32(6, 0);
			w.int32(7, 3);
			w.int32(8, 3913);
			w.int32(9, 4181);
			break;
		}

		w.int32(10, 0);
		w.int32(11, 1);

		w.int32(12, 0);
		w.int32(13, 0);
		w.int32(14, 0);
	}


	// ========================================================================
	// 0x01CD - ACCOUNT TAM
	// ========================================================================

	private void writeAccountTam(ProtoWriter w) {

		for (TamCharacterSnapshot c : _characters) {

			w.msg(1);

			w.int32(1, 0);
			w.int32(2, c.objId);
			w.int32(3, c.remainSec);
			w.int32(4, c.tamWait);

			w.string(5, c.name);

			w.int32(6, c.level);
			w.int32(7, c.clazz);
			w.int32(8, c.sex);

			w.end();
		}

		w.int32(2, 3);
		w.int32(3, 0);
		w.int32(4, 0);
	}


	// ========================================================================
	// DB SNAPSHOT
	// ========================================================================

	private static TamCharacterSnapshot[] loadTamData(String account) {

		if (account == null || account.isEmpty())
			return new TamCharacterSnapshot[0];

		final List<TamCharacterSnapshot> list = new ArrayList<>();
		final long now = System.currentTimeMillis();

		final String sql =
				"SELECT c.objid, c.TamEndTime, c.level, c.char_name, c.Type, c.Sex, "
				+ "(SELECT COUNT(*) FROM tam t WHERE t.objid = c.objid) AS tam_wait "
				+ "FROM characters c WHERE c.account_name=? "
				+ "ORDER BY c.TamEndTime DESC, c.EXP DESC";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql)) {

			pstm.setString(1, account);

			try (ResultSet rs = pstm.executeQuery()) {

				while (rs.next()) {

					final int objId = rs.getInt("objid");
					final int level = rs.getInt("level");
					final int clazz = rs.getInt("Type");
					final int sex = rs.getInt("Sex");
					final int tamWait = rs.getInt("tam_wait");

					final String name = rs.getString("char_name");
					final Timestamp endTime = rs.getTimestamp("TamEndTime");

					int remainSec = 0;

					if (endTime != null) {
						final long remain = endTime.getTime() - now;

						if (remain > 0L) {
							final long sec = remain / 1000L;

							remainSec = sec > Integer.MAX_VALUE
									? Integer.MAX_VALUE
									: (int) sec;
						}
					}

					list.add(new TamCharacterSnapshot(
							objId,
							level,
							clazz,
							sex,
							name,
							remainSec,
							tamWait
					));
				}
			}

		} catch (SQLException e) {
            throw new IllegalStateException("TAM window data load failed: " + account, e);
		}

		return list.toArray(new TamCharacterSnapshot[list.size()]);
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _mode, int _timeSec, int _buffType, TamCharacterSnapshot[] _characters) {
        if (_mode == MODE_BUFF) {
                writeBuffDirect(w, _mode, _timeSec, _buffType, _characters);
            } else {
                writeAccountTamDirect(w, _mode, _timeSec, _buffType, _characters);
            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _mode, int _timeSec, int _buffType, TamCharacterSnapshot[] _characters) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _mode, _timeSec, _buffType, _characters);
    }

    public static boolean sendFields(CIOClient recipient, int _mode, int _timeSec, int _buffType, TamCharacterSnapshot[] _characters) {
        final ProtoWriter writer = Proto.begin(recipient, _mode == MODE_BUFF ? SC_SPELL_BUFF_NOTI : SC_ACCOUNT_TAM, capacityFor(_characters));
        if (writer == null) return false;
        try {
            writeFields(writer, _mode, _timeSec, _buffType, _characters);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _mode, int _timeSec, int _buffType, TamCharacterSnapshot[] _characters) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _mode, _timeSec, _buffType, _characters);
        return writer.finishWithTrailer(_mode == MODE_BUFF ? SC_SPELL_BUFF_NOTI : SC_ACCOUNT_TAM);
    }

    private static void writeBuffDirect(ProtoWriter w, int _mode, int _timeSec, int _buffType, TamCharacterSnapshot[] _characters) {
        w.int32(1, 2);
            w.int32(2, 2272);
            w.int32(3, _timeSec);
            w.int32(4, 8);
            switch (_buffType) {
            case 2:
                w.int32(5, 6547);
                w.int32(6, 0);
                w.int32(7, 3);
                w.int32(8, 4178);
                w.int32(9, 4182);
                break;

            case 3:
                w.int32(5, 6546);
                w.int32(6, 0);
                w.int32(7, 3);
                w.int32(8, 4179);
                w.int32(9, 4183);
                break;

            case 1:

            default:
                w.int32(5, 6100);
                w.int32(6, 0);
                w.int32(7, 3);
                w.int32(8, 3913);
                w.int32(9, 4181);
                break;

            }
            w.int32(10, 0);
            w.int32(11, 1);
            w.int32(12, 0);
            w.int32(13, 0);
            w.int32(14, 0);
    }

    private static void writeAccountTamDirect(ProtoWriter w, int _mode, int _timeSec, int _buffType, TamCharacterSnapshot[] _characters) {
        for (TamCharacterSnapshot c : _characters) {
                w.msg(1);
                w.int32(1, 0);
                w.int32(2, c.objId);
                w.int32(3, c.remainSec);
                w.int32(4, c.tamWait);
                w.string(5, c.name);
                w.int32(6, c.level);
                w.int32(7, c.clazz);
                w.int32(8, c.sex);
                w.end();
            }
            w.int32(2, 3);
            w.int32(3, 0);
            w.int32(4, 0);
    }

    private static boolean sendCtor(CIOClient protoRecipient, TamCharacterSnapshot[] characters) {
        int proto65_mode = 0;
        int proto65_timeSec = 0;
        int proto65_buffType = 0;
        TamCharacterSnapshot[] proto65_characters = null;

            proto65_mode = MODE_ACCOUNT_TAM;
            proto65_timeSec = 0;
            proto65_buffType = 0;
            proto65_characters = characters != null ? characters : new TamCharacterSnapshot[0];

        return sendFields(protoRecipient, proto65_mode, proto65_timeSec, proto65_buffType, proto65_characters);
    }

    private static boolean sendCtor(CIOClient protoRecipient, int timeSec, int buffType) {
        int proto66_mode = 0;
        int proto66_timeSec = 0;
        int proto66_buffType = 0;
        TamCharacterSnapshot[] proto66_characters = null;

            proto66_mode = MODE_BUFF;
            proto66_timeSec = Math.max(0, timeSec);
            proto66_buffType = buffType;
            proto66_characters = null;

        return sendFields(protoRecipient, proto66_mode, proto66_timeSec, proto66_buffType, proto66_characters);
    }

    public static boolean sendCreate(CIOClient protoRecipient, String account) {
        return sendCtor(protoRecipient, loadTamData(account));
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, String account) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), account);
    }

    public static boolean sendCreateBuff(CIOClient protoRecipient, long timeMillis, int buffType) {
        long sec = timeMillis > 0L ? timeMillis / 1000L : 0L;
                int timeSec = sec > Integer.MAX_VALUE
                        ? Integer.MAX_VALUE
                        : (int) sec;

                return sendCtor(protoRecipient, timeSec, buffType);
    }

    public static boolean sendCreateBuff(L1PcInstance protoRecipient, long timeMillis, int buffType) {
        return sendCreateBuff(protoRecipient == null ? null : protoRecipient.getNetConnection(), timeMillis, buffType);
    }
    public static boolean sendConstructed(CIOClient protoRecipient, TamCharacterSnapshot[] characters) { return sendCtor(protoRecipient, characters); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, TamCharacterSnapshot[] characters) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), characters); }

    public static boolean sendConstructed(CIOClient protoRecipient, int timeSec, int buffType) { return sendCtor(protoRecipient, timeSec, buffType); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int timeSec, int buffType) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), timeSec, buffType); }


    private static int capacityFor(TamCharacterSnapshot[] characters) {
        long bytes = 128L;
        if (characters != null) for (TamCharacterSnapshot character : characters)
            bytes += 256L + 2L * character.name.length();
        return Proto.capacityBound(bytes);
    }
}
