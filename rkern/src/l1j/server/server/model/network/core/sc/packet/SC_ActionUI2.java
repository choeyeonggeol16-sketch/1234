package l1j.server.server.model.network.core.sc.packet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.StringTokenizer;

import l1j.server.server.Opcodes;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.connection.CIOFrame;

public final class SC_ActionUI2 {

	public static final int Elixir = 0xe9;
	public static final int MAGICEVASION = 488;
	public static final int stateProfile = 0xe7;
	public static final int EMOTICON = 0x40;
	public static final int CLAN_JOIN_SETTING = 0x4D;
	public static final int CLAN_JOIN_WAIT = 0x45;
	public static final int unk1 = 0x41;
	public static final int unknown1 = 0x4E;
	public static final int unknown2 = 0x91;
	public static final int CLAN_RANK = 0x19;
	public static final int ICON_BUFF = 0x6e;

	public static final int[] hextable = { 0x80, 0x81, 0x82, 0x83, 0x84, 0x85,
		0x86, 0x87, 0x88, 0x89, 0x8a, 0x8b, 0x8c, 0x8d, 0x8e, 0x8f, 0x90,
		0x91, 0x92, 0x93, 0x94, 0x95, 0x96, 0x97, 0x98, 0x99, 0x9a, 0x9b,
		0x9c, 0x9d, 0x9e, 0x9f, 0xa0, 0xa1, 0xa2, 0xa3, 0xa4, 0xa5, 0xa6,
		0xa7, 0xa8, 0xa9, 0xaa, 0xab, 0xac, 0xad, 0xae, 0xaf, 0xb0, 0xb1,
		0xb2, 0xb3, 0xb4, 0xb5, 0xb6, 0xb7, 0xb8, 0xb9, 0xba, 0xbb, 0xbc,
		0xbd, 0xbe, 0xbf, 0xc0, 0xc1, 0xc2, 0xc3, 0xc4, 0xc5, 0xc6, 0xc7,
		0xc8, 0xc9, 0xca, 0xcb, 0xcc, 0xcd, 0xce, 0xcf, 0xd0, 0xd1, 0xd2,
		0xd3, 0xd4, 0xd5, 0xd6, 0xd7, 0xd8, 0xd9, 0xda, 0xdb, 0xdc, 0xdd,
		0xde, 0xdf, 0xe0, 0xe1, 0xe2, 0xe3, 0xe4, 0xe5, 0xe6, 0xe7, 0xe8,
		0xe9, 0xea, 0xeb, 0xec, 0xed, 0xee, 0xef, 0xf0, 0xf1, 0xf2, 0xf3,
		0xf4, 0xf5, 0xf6, 0xf7, 0xf8, 0xf9, 0xfa, 0xfb, 0xfc, 0xfd, 0xfe,
		0xff };

	private static final String 활력_활력버프1 = "00 08 02 10 f2 12 18";
	private static final String 활력_활력버프2 = "20 09 28 97 34 30 00 38 00 40 fb 21 48 00 50 00 58 01 60 01 68 e8 21 70 01 45 63";

	private static final String 활력_공격버프1 = "00 08 02 10 f3 12 18";
	private static final String 활력_공격버프2 = "20 09 28 97 34 30 00 38 00 40 fc 21 48 00 50 00 58 01 60 01 68 e8 21 70 01 6d 23";

	private static final String 활력_방어버프1 = "00 08 02 10 f4 12 18";
	private static final String 활력_방어버프2 = "20 09 28 97 34 30 00 38 00 40 fd 21 48 00 50 00 58 01 60 01 68 e8 21 70 01 d4 4c";

	private static final String 활력_마법버프1 = "00 08 02 10 f5 12 18";
	private static final String 활력_마법버프2 = "20 09 28 97 34 30 00 38 00 40 fe 21 48 00 50 00 58 01 60 01 68 e8 21 70 01 d4 4c";

	private static final String 활력_스턴버프1 = "00 08 02 10 f6 12 18";
	private static final String 활력_스턴버프2 = "20 09 28 97 34 30 00 38 00 40 ff 21 48 00 50 00 58 01 60 01 68 e8 21 70 01 d4 4c";

	private static final String 활력_홀드버프1 = "00 08 02 10 f7 12 18";
	private static final String 활력_홀드버프2 = "20 09 28 97 34 30 00 38 00 40 80 22 48 00 50 00 58 01 60 01 68 e8 21 70 01 d4 4c";

	private static class ClanWaitPlayerSnapshot {
		final int index;
		final byte[] nameBytes;
		final int isOnline;
		final int pcType;

		ClanWaitPlayerSnapshot(int index, L1PcInstance pc) {
			this.index = index;
			this.nameBytes = pc.getName().getBytes();
			this.isOnline = L1World.getInstance().getPlayer(pc.getName()) != null ? 1 : 0;
			this.pcType = pc.getType();
		}
	}

    public static boolean send(L1PcInstance pc, int type, int stat) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(160);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(type);
			buffer.writeC(0x01);

			switch (type) {
			case MAGICEVASION:
			case Elixir:
				buffer.writeC(0x08);
				buffer.writeC(stat);
				break;
			case stateProfile:
				buffer.writeC(0x0a);
				if (stat == 45) {
					buffer.writeH(0x0808);
					buffer.writeC(stat);
					buffer.writeD(0x03180310);
					buffer.writeH(0x0120);
				} else {
					buffer.writeH(0x0806);
					buffer.writeC(stat);
					buffer.writeD(0x01180110);
				}

				buffer.writeC(0x12);
				if (stat == 45) {
					buffer.writeH(0x0808);
					buffer.writeC(stat);
					buffer.writeD(0x03180310);
					buffer.writeH(0x0120);
				} else {
					buffer.writeH(0x0806);
					buffer.writeC(stat);
					buffer.writeD(0x01180110);
				}

				buffer.writeC(0x1a);
				if (stat == 25) {
					buffer.writeH(0x0806);
					buffer.writeC(stat);
					buffer.writeD(0x01180110);
					buffer.writeH(0x3238);
				} else if (stat == 35) {
					buffer.writeH(0x0808);
					buffer.writeC(stat);
					buffer.writeD(0x01180110);
					buffer.writeH(0x6438);
				} else if (stat == 45) {
					buffer.writeH(0x0809);
					buffer.writeC(stat);
					buffer.writeD(0x03180310);
					buffer.writeC(0x38);
					buffer.writeH(0x0196);
				}

				buffer.writeC(0x22);
				if (stat == 45) {
					buffer.writeH(0x0808);
					buffer.writeC(stat);
					buffer.writeD(0x03180310);
					buffer.writeH(0x0120);
				} else {
					buffer.writeH(0x0806);
					buffer.writeC(stat);
					buffer.writeD(0x01180110);
				}

				buffer.writeC(0x2a);
				if (stat == 25) {
					buffer.writeH(0x0806);
					buffer.writeC(stat);
					buffer.writeD(0x32300110);
				} else if (stat == 35) {
					buffer.writeH(0x0808);
					buffer.writeC(stat);
					buffer.writeD(0x01180110);
					buffer.writeH(0x6430);
				} else if (stat == 45) {
					buffer.writeH(0x0809);
					buffer.writeC(stat);
					buffer.writeD(0x02180310);
					buffer.writeC(0x30);
					buffer.writeH(0x0196);
				}

				buffer.writeD(0xff080b32);
				buffer.writeD(0xffffffff);
				buffer.writeD(0xffffffff);
				buffer.writeC(0x01);
				break;
			}
			buffer.writeH(0);
			
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance pc, int i, int t, int o, int gf, int ms) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(160);
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeC(ICON_BUFF);
            buffer.writeC(0);
            buffer.writeC(0x08);
            buffer.writeC(0x02);
            buffer.writeC(0x10);
            buffer.write7B(i);
            buffer.writeC(0x18);
            buffer.write7B(t);
            buffer.writeC(0x20);
            buffer.writeC(0x09);
            buffer.writeC(0x28);
            buffer.write7B(gf);
            buffer.writeC(0x30);
            buffer.writeC(0);
            buffer.writeC(0x38);
            buffer.writeC(o);
            buffer.writeC(0x40);
            buffer.write7B(ms);
            buffer.writeC(0x48);
            buffer.writeC(0);
            buffer.writeC(0x50);
            buffer.writeC(0);
            buffer.writeC(0x58);
            buffer.writeC(1);
            buffer.writeH(0);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    public static boolean send(L1PcInstance recipient, L1PcInstance source, int code) {
        if (recipient == null) return false;
        final CIOClient socket = recipient.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        byte[] nameBytes = null;
        int rank = 0;
        if (code == CLAN_RANK && source != null && source.getClanname() != null) {
            nameBytes = source.getClanname().getBytes();
            final int rawRank = source.getClanRank();
            switch (rawRank) {
            case L1Clan.일반: rank = L1Clan.CLAN_RANK_LEAGUE_PROBATION; break;
            case L1Clan.수련: rank = L1Clan.CLAN_RANK_LEAGUE_PUBLIC; break;
            case L1Clan.수호: rank = L1Clan.CLAN_RANK_LEAGUE_GUARDIAN; break;
            case L1Clan.군주: rank = L1Clan.CLAN_RANK_LEAGUE_PRINCE; break;
            default: rank = rawRank; break;
            }
        }
        final CIOFrame frame = connection.beginDirectFrame(160 + (nameBytes != null ? nameBytes.length : 0));
        if (frame == null) return false;
        boolean committed = false;
        try {
            final WriteBuffer buffer = frame.buffer();
            buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
            buffer.writeC(code);
            if (code == CLAN_RANK) {
                buffer.writeC(2);
                buffer.writeC(0x0a);
                if (nameBytes != null && nameBytes.length > 0) {
                    buffer.writeC(nameBytes.length);
                    buffer.writeByte(nameBytes);
                    buffer.writeC(0x10);
                    buffer.writeC(rank);
                } else {
                    buffer.writeC(0);
                }
                buffer.writeH(0);
            }
            committed = connection.commitDirectFrame(frame);
            return committed;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

	private final int _mode;
	private final int _type;
	private final int _val1;
	private final int _val2;
	private final int _val3;
	private final int _val4;
	private final boolean _boolVal;
	private final long _longVal;
	private final String _strVal;
	private final byte[] _byteData;
	private final List<ClanWaitPlayerSnapshot> _waitPlayers;

	public SC_ActionUI2(int type, boolean ck) {
		_mode = 1;
		_type = type;
		_val1 = 0;
		_val2 = 0;
		_val3 = 0;
		_val4 = 0;
		_boolVal = ck;
		_longVal = 0;
		_strVal = null;
		_byteData = null;
		_waitPlayers = Collections.emptyList();
	}

	public SC_ActionUI2(int i, int t, int o, int gf, int ms) {
		_mode = 2;
		_type = 0;
		_val1 = i;
		_val2 = t;
		_val3 = o;
		_val4 = gf;
		_boolVal = false;
		_longVal = ms;
		_strVal = null;
		_byteData = null;
		_waitPlayers = Collections.emptyList();
	}

	public SC_ActionUI2(L1PcInstance pc, int code) {
		_mode = 3;
		_type = code;
		_val2 = 0;
		_val3 = 0;
		_val4 = 0;
		_boolVal = false;
		_longVal = 0;
		_strVal = null;

		if (code == CLAN_RANK && pc != null && pc.getClanname() != null) {
			_byteData = pc.getClanname().getBytes();
			int rawRank = pc.getClanRank();
			switch (rawRank) {
			case L1Clan.일반:
				_val1 = L1Clan.CLAN_RANK_LEAGUE_PROBATION;
				break;
			case L1Clan.수련:
				_val1 = L1Clan.CLAN_RANK_LEAGUE_PUBLIC;
				break;
			case L1Clan.수호:
				_val1 = L1Clan.CLAN_RANK_LEAGUE_GUARDIAN;
				break;
			case L1Clan.군주:
				_val1 = L1Clan.CLAN_RANK_LEAGUE_PRINCE;
				break;
			default:
				_val1 = rawRank;
				break;
			}
		} else {
			_byteData = null;
			_val1 = 0;
		}
		_waitPlayers = Collections.emptyList();
	}

	public SC_ActionUI2(int subCode) {
		_mode = 4;
		_type = subCode;
		_val1 = 0;
		_val2 = 0;
		_val3 = 0;
		_val4 = 0;
		_boolVal = false;
		_longVal = 0;
		_strVal = null;
		_byteData = null;

		if (subCode == CLAN_JOIN_WAIT) {
			Collection<L1PcInstance> list = L1World.getInstance().getAllPlayers();
			List<ClanWaitPlayerSnapshot> snapshots = new ArrayList<>();
			int i = 0;
			for (L1PcInstance pc : list) {
				if (pc == null) continue;
				snapshots.add(new ClanWaitPlayerSnapshot(i, pc));
				i++;
				if (i == 2) break;
			}
			_waitPlayers = Collections.unmodifiableList(snapshots);
		} else {
			_waitPlayers = Collections.emptyList();
		}
	}

	public SC_ActionUI2(int sub, int skillId, boolean on, int msgNum, int time) {
		_mode = 5;
		_type = sub;
		_val1 = skillId;
		_val2 = msgNum;
		_val3 = time;
		_val4 = 0;
		_boolVal = on;
		_longVal = 0;
		_strVal = null;
		_byteData = null;
		_waitPlayers = Collections.emptyList();
	}

	public SC_ActionUI2(String 활력코드, long 시간) {
		_mode = 6;
		_type = 0;
		_val1 = 0;
		_val2 = 0;
		_val3 = 0;
		_val4 = 0;
		_boolVal = false;
		_longVal = 시간;
		_strVal = 활력코드;
		_byteData = null;
		_waitPlayers = Collections.emptyList();
	}

	public SC_ActionUI2(int type, int stat) {
		_mode = 7;
		_type = type;
		_val1 = stat;
		_val2 = 0;
		_val3 = 0;
		_val4 = 0;
		_boolVal = false;
		_longVal = 0;
		_strVal = null;
		_byteData = null;
		_waitPlayers = Collections.emptyList();
	}

	public SC_ActionUI2(int type, int subtype, int objid) {
		_mode = 8;
		_type = type;
		_val1 = subtype;
		_val2 = objid;
		_val3 = 0;
		_val4 = 0;
		_boolVal = false;
		_longVal = 0;
		_strVal = null;
		_byteData = null;
		_waitPlayers = Collections.emptyList();
	}

	private void byteWrite(WriteBuffer buffer, long value) {
		long temp = value / 128;
		if (temp > 0) {
			buffer.writeC(hextable[(int) value % 128]);
			while (temp >= 128) {
				buffer.writeC(hextable[(int) temp % 128]);
				temp = temp / 128;
			}
			if (temp > 0)
				buffer.writeC((int) temp);
		} else {
			if (value == 0) {
				buffer.writeC(0);
			} else {
				buffer.writeC(hextable[(int) value]);
				buffer.writeC(0);
			}
		}
	}

	private void writeImpl(WriteBuffer buffer) {
		switch (_mode) {
		case 1:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(_type);
			if (_type == CLAN_JOIN_WAIT) {
				buffer.writeC(1);
				buffer.writeC(8);
				buffer.writeC(2);
				buffer.writeH(0);
			}
			break;

		case 2:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(ICON_BUFF);
			buffer.writeC(0x00);
			buffer.writeC(0x08);
			buffer.writeC(0x02);
			buffer.writeC(0x10);
			buffer.write7B(_val1);
			buffer.writeC(0x18);
			buffer.write7B(_val2);
			buffer.writeC(0x20);
			buffer.writeC(0x09);
			buffer.writeC(0x28);
			buffer.write7B(_val4);
			buffer.writeC(0x30);
			buffer.writeC(0x00);
			buffer.writeC(0x38);
			buffer.writeC(_val3);
			buffer.writeC(0x40);
			buffer.write7B(_longVal);
			buffer.writeC(0x48);
			buffer.writeC(0x00);
			buffer.writeC(0x50);
			buffer.writeC(0x00);
			buffer.writeC(0x58);
			buffer.writeC(0x01);
			buffer.writeH(0);
			break;

		case 3:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(_type);
			if (_type == CLAN_RANK) {
				buffer.writeC(0x02);
				buffer.writeC(0x0a);
				if (_byteData != null && _byteData.length > 0) {
					buffer.writeC(_byteData.length);
					buffer.writeByte(_byteData);
					buffer.writeC(0x10);
					buffer.writeC(_val1);
				} else {
					buffer.writeC(0x00);
				}
				buffer.writeH(0x00);
			}
			break;

		case 4:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(_type);
			switch (_type) {
			case unknown2:
				buffer.writeC(01);
				buffer.writeC(0x88);
				buffer.writeC(0xd4);
				break;
			case unk1:
				buffer.writeC(1);
				buffer.writeC(8);
				buffer.writeC(0x80);
				buffer.writeC(0xe1);
				buffer.writeC(1);
				buffer.writeC(0x10);
				buffer.writeC(0xe5);
				buffer.writeC(0xe0);
				buffer.writeC(1);
				buffer.writeC(0x4a);
				buffer.writeC(0);
				break;
			case unknown1:
				buffer.writeC(1);
				buffer.writeC(8);
				buffer.writeC(3);
				buffer.writeC(0x10);
				buffer.writeC(0);
				buffer.writeC(0x18);
				buffer.writeC(0);
				buffer.writeH(0);
				break;
			case CLAN_JOIN_WAIT:
				buffer.writeC(0x01);
				buffer.writeH(0x0208);
				if (!_waitPlayers.isEmpty()) {
					for (ClanWaitPlayerSnapshot pc : _waitPlayers) {
						buffer.writeC(0x12);
						if (pc.index == 0) buffer.writeC(39);
						else if (pc.index == 1) buffer.writeC(38);
						else if (pc.index == 2) buffer.writeC(40);
						else buffer.writeC(39);

						buffer.writeC(0x08);
						buffer.writeD(0x1203A9A2);
						buffer.writeD(0xC5C3B208);
						buffer.writeD(0xB7ACC5EB);
						buffer.writeH(0x18B4);

						if (pc.index == 0 || pc.index >= 3) {
							buffer.writeD(0x02D8D1BE);
						} else {
							buffer.writeC(0xC3);
							buffer.writeH(0x5C8A);
						}
						buffer.writeC(0x22);
						buffer.writeC(pc.nameBytes.length);
						buffer.writeByte(pc.nameBytes);
						buffer.writeC(0x2A);
						buffer.writeC("1".getBytes().length);
						buffer.writeByte("1".getBytes());
						buffer.writeC(0x30);
						buffer.writeC(pc.isOnline);
						buffer.writeC(0x38);
						buffer.writeC(pc.pcType);
					}
				}
				buffer.writeH(0x00);
				break;
			}
			break;

		case 5:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeH(_type);
			if (_type == ICON_BUFF) {
				buffer.writeC(0x08);
				buffer.writeC(_boolVal ? 2 : 3);
				buffer.writeC(0x10);
				buffer.writeBit(_type);
				buffer.writeC(0x18);
				buffer.writeBit(_val3);
				buffer.writeC(0x20);
				buffer.writeC(0x08);
				buffer.writeC(0x28);
				buffer.writeBit(_val1);
				buffer.writeH(0x30);
				buffer.writeC(0x38);
				buffer.writeC(0x03);
				buffer.writeC(0x40);
				buffer.writeBit(_val2);
				buffer.writeC(0x48);
				buffer.writeC(0x00);
				buffer.writeH(0x0050);
				buffer.writeC(0x58);
				buffer.writeC(0x01);
				buffer.writeC(0x60);
				buffer.writeC(0x00);
				buffer.writeC(0x68);
				buffer.writeC(0x00);
				buffer.writeC(0x70);
				buffer.writeC(0x00);
				buffer.writeH(0x00);
			}
			break;

		case 6:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(ICON_BUFF);

			String p1 = "";
			String p2 = "";

			if ("활력".equals(_strVal)) {
				p1 = 활력_활력버프1;
				p2 = 활력_활력버프2;
			} else if ("공격".equals(_strVal)) {
				p1 = 활력_공격버프1;
				p2 = 활력_공격버프2;
			} else if ("방어".equals(_strVal)) {
				p1 = 활력_방어버프1;
				p2 = 활력_방어버프2;
			} else if ("마법".equals(_strVal)) {
				p1 = 활력_마법버프1;
				p2 = 활력_마법버프2;
			} else if ("스턴".equals(_strVal)) {
				p1 = 활력_스턴버프1;
				p2 = 활력_스턴버프2;
			} else if ("홀드".equals(_strVal)) {
				p1 = 활력_홀드버프1;
				p2 = 활력_홀드버프2;
			}

			StringTokenizer st = new StringTokenizer(p1);
			while (st.hasMoreTokens()) {
				buffer.writeC(Integer.parseInt(st.nextToken(), 16));
			}

			byteWrite(buffer, _longVal / 1000);

			st = new StringTokenizer(p2);
			while (st.hasMoreTokens()) {
				buffer.writeC(Integer.parseInt(st.nextToken(), 16));
			}
			break;

		case 7:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(_type);
			buffer.writeC(0x01);

			switch (_type) {
			case MAGICEVASION:
			case Elixir:
				buffer.writeC(0x08);
				buffer.writeC(_val1);
				break;
			case stateProfile:
				buffer.writeC(0x0a);
				if (_val1 == 45) {
					buffer.writeH(0x0808);
					buffer.writeC(_val1);
					buffer.writeD(0x03180310);
					buffer.writeH(0x0120);
				} else {
					buffer.writeH(0x0806);
					buffer.writeC(_val1);
					buffer.writeD(0x01180110);
				}

				buffer.writeC(0x12);
				if (_val1 == 45) {
					buffer.writeH(0x0808);
					buffer.writeC(_val1);
					buffer.writeD(0x03180310);
					buffer.writeH(0x0120);
				} else {
					buffer.writeH(0x0806);
					buffer.writeC(_val1);
					buffer.writeD(0x01180110);
				}

				buffer.writeC(0x1a);
				if (_val1 == 25) {
					buffer.writeH(0x0806);
					buffer.writeC(_val1);
					buffer.writeD(0x01180110);
					buffer.writeH(0x3238);
				} else if (_val1 == 35) {
					buffer.writeH(0x0808);
					buffer.writeC(_val1);
					buffer.writeD(0x01180110);
					buffer.writeH(0x6438);
				} else if (_val1 == 45) {
					buffer.writeH(0x0809);
					buffer.writeC(_val1);
					buffer.writeD(0x03180310);
					buffer.writeC(0x38);
					buffer.writeH(0x0196);
				}

				buffer.writeC(0x22);
				if (_val1 == 45) {
					buffer.writeH(0x0808);
					buffer.writeC(_val1);
					buffer.writeD(0x03180310);
					buffer.writeH(0x0120);
				} else {
					buffer.writeH(0x0806);
					buffer.writeC(_val1);
					buffer.writeD(0x01180110);
				}

				buffer.writeC(0x2a);
				if (_val1 == 25) {
					buffer.writeH(0x0806);
					buffer.writeC(_val1);
					buffer.writeD(0x32300110);
				} else if (_val1 == 35) {
					buffer.writeH(0x0808);
					buffer.writeC(_val1);
					buffer.writeD(0x01180110);
					buffer.writeH(0x6430);
				} else if (_val1 == 45) {
					buffer.writeH(0x0809);
					buffer.writeC(_val1);
					buffer.writeD(0x02180310);
					buffer.writeC(0x30);
					buffer.writeH(0x0196);
				}

				buffer.writeD(0xff080b32);
				buffer.writeD(0xffffffff);
				buffer.writeD(0xffffffff);
				buffer.writeC(0x01);
				break;
			}
			buffer.writeH(0);
			break;

		case 8:
			buffer.writeC(Opcodes.S_EXTENDED_PROTOBUF);
			buffer.writeC(_type);
			switch (_type) {
			case EMOTICON:
				buffer.writeC(0x01);
				buffer.writeC(0x08);
				int temp = _val2 / 128;
				if (temp > 0) {
					buffer.writeC(hextable[_val2 % 128]);
					while (temp > 128) {
						buffer.writeC(hextable[temp % 128]);
						temp = temp / 128;
					}
					buffer.writeC(temp);
				} else {
					if (_val2 == 0) {
						buffer.writeC(0);
					} else {
						buffer.writeC(hextable[_val2]);
						buffer.writeC(0);
					}
				}
				buffer.writeC(0x10);
				buffer.writeC(0x02);
				buffer.writeC(0x18);
				buffer.writeC(_val1);
				buffer.writeH(0);
				break;
			case CLAN_JOIN_SETTING:
				buffer.writeD(0x10010801);
				buffer.writeC(_val1);
				buffer.writeC(0x18);
				buffer.writeC(_val2);
				buffer.writeD(0x00001422);
				buffer.writeD(0x00);
				buffer.writeD(0x00);
				buffer.writeD(0x00);
				buffer.writeD(0x00);
				buffer.writeD(0x00);
				break;
			}
			break;
		}
	}

    private int frameCapacity() {
        int cap = 160;
        if (_byteData != null) cap += _byteData.length;
        for (ClanWaitPlayerSnapshot player : _waitPlayers) cap += 64 + player.nameBytes.length;
        return cap;
    }

    public boolean send(L1PcInstance pc) {
        if (pc == null) return false;
        final CIOClient socket = pc.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final CIOFrame frame = connection.beginDirectFrame(frameCapacity());
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