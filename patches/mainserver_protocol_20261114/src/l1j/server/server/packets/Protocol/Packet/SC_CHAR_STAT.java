package l1j.server.server.packets.Protocol.Packet;

import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.ProtoPacket;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.utils.CalcStat;
import l1j.server.server.utils.StatContext;

public final class SC_CHAR_STAT extends ProtoPacket {

	public static final int Str = 1, Int = 2, Wis = 3, Dex = 4, Con = 5, Cha = 6;

	public static final int Stat_Str = 0x30, Stat_Int = 0x38, Stat_Wis = 0x40;
	public static final int Stat_Dex = 0x48, Stat_Con = 0x50, Stat_Cha = 0x58;

	public static final int STAT_REFRESH = 0xEA;
	public static final int STAT_VIEW = 0xE7;

	private static final int SC_STAT_DETAIL  = 0x01E3;
	private static final int SC_STAT_VIEW    = 0x01E7;
	private static final int SC_STAT_REFRESH = 0x01EA;

	private static final int MODE_CLIENT_ALLOC   = 1;
	private static final int MODE_PC_STAT_CHANGE = 2;
	private static final int MODE_STAT_REFRESH   = 3;
	private static final int MODE_STAT_VIEW      = 4;

	private final int _mode;
	private final int _type;
	private final int _value;

	private final int _v1, _v2, _v3, _v4, _v5, _v6;


	public static SC_CHAR_STAT create(CIOClient client, StatContext stx) {
		if (client == null || stx == null)
			return null;

		final int[] stats = stx.getStats();
		if (stats == null || stats.length < 5)
			return null;

		final L1PcInstance pc = client.getActiveChar();

		final int str   = stats[0];
		final int intel = stats[1];
		final int wis   = stats[2];
		final int dex   = stats[3];
		final int con   = stats[4];

		final int targetType = stx.getTargetType();
		final int classType  = stx.getClassType();

		int v1 = 0, v2 = 0, v3 = 0;
		int v4 = 0, v5 = 0, v6 = 0;

		switch (targetType) {
		case Str:
			v1 = CalcStat.근거리대미지(str);
			v2 = CalcStat.근거리명중(str);
			v3 = CalcStat.근거리치명타(str);
			v4 = CalcStat.최대소지무게(str, con);
			break;

		case Int:
			v1 = CalcStat.마법대미지(intel);
			v2 = CalcStat.마법적중(intel);
			v3 = CalcStat.마법치명타(intel);
			v4 = CalcStat.마법보너스(classType, intel);
			v5 = CalcStat.엠소모감소(intel);
			break;

		case Wis:
			v1 = CalcStat.엠회복틱(wis);
			v2 = CalcStat.엠피물약회복(wis);
			v3 = CalcStat.마법방어(classType, wis);
			v4 = CalcStat.MinincreaseMp(classType, wis);
			v5 = CalcStat.MaxincreaseMp(classType, wis);
			v6 = getHpMpValue(pc, false);
			break;

		case Dex:
			v1 = CalcStat.원거리대미지(dex);
			v2 = CalcStat.원거리명중(dex);
			v3 = CalcStat.원거리치명타(dex);
			v4 = CalcStat.물리방어력(dex);
			v5 = CalcStat.원거리회피(dex);
			break;

		case Con:
			v1 = CalcStat.피회복틱(con);
			v2 = (int) CalcStat.물약회복증가(con);
			v3 = CalcStat.최대소지무게(str, con);
			v4 = CalcStat.PureHp(classType, con);
			v6 = getHpMpValue(pc, true);
			break;

		default:
			break;
		}

		return new SC_CHAR_STAT(
				MODE_CLIENT_ALLOC,
				targetType,
				stx.getStatValue() * 2,
				v1, v2, v3, v4, v5, v6
		);
	}


	public static SC_CHAR_STAT create(L1PcInstance pc, int settingType, int statType) {
		int v1 = 0, v2 = 0, v3 = 0;
		int v4 = 0, v5 = 0, v6 = 0;

		if (pc != null) {
			final int str   = pc.getAbility().getTotalStr();
			final int intel = pc.getAbility().getTotalInt();
			final int wis   = pc.getAbility().getTotalWis();
			final int dex   = pc.getAbility().getTotalDex();
			final int con   = pc.getAbility().getTotalCon();
			final int type  = pc.getType();

			switch (statType) {
			case Stat_Str:
				v1 = CalcStat.근거리대미지(str);
				v2 = CalcStat.근거리명중(str);
				v3 = CalcStat.근거리치명타(str);
				v4 = CalcStat.최대소지무게(str, con);
				break;

			case Stat_Int:
				v1 = CalcStat.마법대미지(intel);
				v2 = CalcStat.마법적중(intel);
				v3 = CalcStat.마법치명타(intel);
				v4 = CalcStat.마법보너스(type, intel);
				v5 = CalcStat.엠소모감소(intel);
				break;

			case Stat_Wis:
				v1 = CalcStat.naturalMpRegen(0, wis, pc.getAbility().getWis());
				v2 = CalcStat.엠피물약회복(wis);
				v3 = CalcStat.마법방어(type, wis);
				v4 = CalcStat.MinincreaseMp(type, pc.getAbility().getWis());
				v5 = CalcStat.MaxincreaseMp(type, pc.getAbility().getWis());
				v6 = getHpMpValue(pc, false);
				break;

			case Stat_Dex:
				v1 = CalcStat.원거리대미지(dex);
				v2 = CalcStat.원거리명중(dex);
				v3 = CalcStat.원거리치명타(dex);
				v4 = CalcStat.물리방어력(dex);
				v5 = CalcStat.원거리회피(dex);
				break;

			case Stat_Con:
				v1 = CalcStat.피회복틱(con);
				v2 = (int) CalcStat.물약회복증가(con);
				v3 = CalcStat.최대소지무게(str, con);
				v4 = CalcStat.PureHp(type, pc.getAbility().getCon());
				v6 = getHpMpValue(pc, true);
				break;

			default:
				break;
			}
		}

		return new SC_CHAR_STAT(
				MODE_PC_STAT_CHANGE,
				statType,
				settingType,
				v1, v2, v3, v4, v5, v6
		);
	}


	public static SC_CHAR_STAT create(L1PcInstance pc, int code) {
		if (pc == null) {
			return new SC_CHAR_STAT(
					MODE_STAT_REFRESH, code, 0,
					0, 0, 0, 0, 0, 0
			);
		}

		return new SC_CHAR_STAT(
				MODE_STAT_REFRESH,
				code,
				0,
				pc.getAbility().getStr(),
				pc.getAbility().getInt(),
				pc.getAbility().getWis(),
				pc.getAbility().getDex(),
				pc.getAbility().getCon(),
				pc.getAbility().getCha()
		);
	}


	public static SC_CHAR_STAT create(int code, int levelThreshold) {
		return new SC_CHAR_STAT(
				MODE_STAT_VIEW,
				code,
				levelThreshold,
				0, 0, 0, 0, 0, 0
		);
	}


	private SC_CHAR_STAT(int mode, int type, int value,
			int v1, int v2, int v3, int v4, int v5, int v6) {

		_mode  = mode;
		_type  = type;
		_value = value;

		_v1 = v1;
		_v2 = v2;
		_v3 = v3;
		_v4 = v4;
		_v5 = v5;
		_v6 = v6;
	}


	@Override
	public int msgId() {
		switch (_mode) {
		case MODE_STAT_VIEW:
			return SC_STAT_VIEW;

		case MODE_STAT_REFRESH:
			return SC_STAT_REFRESH;

		case MODE_CLIENT_ALLOC:
		case MODE_PC_STAT_CHANGE:
		default:
			return SC_STAT_DETAIL;
		}
	}


	@Override
	protected void body(ProtoWriter w) {
		switch (_mode) {
		case MODE_CLIENT_ALLOC:
		case MODE_PC_STAT_CHANGE:
			writeStatDetail(w);
			break;

		case MODE_STAT_REFRESH:
			writeStatRefresh(w);
			break;

		case MODE_STAT_VIEW:
			writeStatView(w);
			break;
		}
	}


	private void writeStatDetail(ProtoWriter w) {
		w.int32(1, _value);

		final int statIndex = _mode == MODE_PC_STAT_CHANGE
				? (_type - 40) / 8
				: _type;

		switch (statIndex) {
		case Str:
			w.msg(2);
			w.int32(1, _v1);
			w.int32(2, _v2);
			w.int32(3, _v3);
			w.int32(4, _v4);
			w.end();
			break;

		case Int:
			w.msg(3);
			w.int32(1, _v1);
			w.int32(2, _v2);
			w.int32(3, _v3);
			w.int32(4, _v4);
			w.int32(5, _v5);
			w.end();
			break;

		case Wis:
			w.msg(4);
			w.int32(1, _v1);
			w.int32(2, _v2);
			w.int32(3, _v3);
			w.int32(4, _v4);
			w.int32(5, _v5);
			w.int32(6, _v6);
			w.end();
			break;

		case Dex:
			w.msg(5);
			w.int32(1, _v1);
			w.int32(2, _v2);
			w.int32(3, _v3);
			w.int32(4, _v4);
			w.int32(5, _v5);
			w.end();
			break;

		case Con:
			w.msg(6);
			w.int32(1, _v1);
			w.int32(2, _v2);
			w.int32(3, _v3);
			w.int32(4, _v4);
			w.int32(5, _v6);
			w.end();
			break;

		case Cha:
			w.msg(7);
			w.int32(1, 0);
			w.end();
			break;
		}
	}


	private void writeStatRefresh(ProtoWriter w) {
		if (_type != STAT_REFRESH)
			return;

		w.int32(1, _v1);
		w.int32(2, _v2);
		w.int32(3, _v3);
		w.int32(4, _v4);
		w.int32(5, _v5);
		w.int32(6, _v6);
	}


	private void writeStatView(ProtoWriter w) {
		if (_type != STAT_VIEW)
			return;

		final int level = _value;

		if (level == 45) {
			writeView4(w, 1, level, 3, 3, 1);
			writeView4(w, 2, level, 3, 3, 1);

			w.msg(3);
			w.int32(1, level);
			w.int32(2, 3);
			w.int32(3, 3);
			w.int32(7, 150);
			w.end();

			writeView4(w, 4, level, 3, 3, 1);

			w.msg(5);
			w.int32(1, level);
			w.int32(2, 3);
			w.int32(3, 2);
			w.int32(6, 150);
			w.end();

		} else {
			writeView3(w, 1, level, 1, 1);
			writeView3(w, 2, level, 1, 1);

			w.msg(3);
			w.int32(1, level);
			w.int32(2, 1);
			w.int32(3, 1);
			w.int32(7, level == 35 ? 100 : 50);
			w.end();

			writeView3(w, 4, level, 1, 1);

			w.msg(5);
			w.int32(1, level);
			w.int32(2, 1);

			if (level != 25)
				w.int32(3, 1);

			w.int32(6, level == 25 ? 50 : 100);
			w.end();
		}

		w.msg(6);
		w.int32(1, 0);
		w.end();
	}


	private static void writeView3(
			ProtoWriter w,
			int field,
			int v1, int v2, int v3) {

		w.msg(field);
		w.int32(1, v1);
		w.int32(2, v2);
		w.int32(3, v3);
		w.end();
	}


	private static void writeView4(
			ProtoWriter w,
			int field,
			int v1, int v2, int v3, int v4) {

		w.msg(field);
		w.int32(1, v1);
		w.int32(2, v2);
		w.int32(3, v3);
		w.int32(4, v4);
		w.end();
	}


	private static int getHpMpValue(L1PcInstance pc, boolean hp) {
		if (pc == null || pc.getReturnStat() == 0 || pc.rst == null)
			return 0;

		return hp ? pc.rst.upHp : pc.rst.upMp;
	}


    /** Direct serialization with primitive presence bits; no message instance. */
    public static void writeFields(ProtoWriter w, int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        switch (_mode) {
            case MODE_CLIENT_ALLOC:

            case MODE_PC_STAT_CHANGE:
                writeStatDetailDirect(w, _mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6);
                break;

            case MODE_STAT_REFRESH:
                writeStatRefreshDirect(w, _mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6);
                break;

            case MODE_STAT_VIEW:
                writeStatViewDirect(w, _mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6);
                break;

            }
    }
    private static int messageIdFields(int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        switch (_mode) {
            case MODE_STAT_VIEW:
                return SC_STAT_VIEW;

            case MODE_STAT_REFRESH:
                return SC_STAT_REFRESH;

            case MODE_CLIENT_ALLOC:

            case MODE_PC_STAT_CHANGE:

            default:
                return SC_STAT_DETAIL;

            }
    }

    public static boolean sendFields(L1PcInstance recipient, int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        return sendFields(recipient == null ? null : recipient.getNetConnection(), _mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6);
    }

    public static boolean sendFields(CIOClient recipient, int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        final ProtoWriter writer = Proto.begin(recipient, messageIdFields(_mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6), Proto.capacityBound(1024L));
        if (writer == null) return false;
        try {
            writeFields(writer, _mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6);
            return Proto.commit(writer);
        } finally {
            Proto.abort(writer);
        }
    }

    public static byte[] encodeFields(int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        final ProtoWriter writer = ProtoWriter.tl();
        writeFields(writer, _mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6);
        return writer.finishWithTrailer(messageIdFields(_mode, _type, _value, _v1, _v2, _v3, _v4, _v5, _v6));
    }

    private static void writeStatDetailDirect(ProtoWriter w, int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        w.int32(1, _value);
            final int statIndex = _mode == MODE_PC_STAT_CHANGE ? (_type - 40) / 8 : _type;
            switch (statIndex) {
            case Str:
                w.msg(2);
                w.int32(1, _v1);
                w.int32(2, _v2);
                w.int32(3, _v3);
                w.int32(4, _v4);
                w.end();
                break;

            case Int:
                w.msg(3);
                w.int32(1, _v1);
                w.int32(2, _v2);
                w.int32(3, _v3);
                w.int32(4, _v4);
                w.int32(5, _v5);
                w.end();
                break;

            case Wis:
                w.msg(4);
                w.int32(1, _v1);
                w.int32(2, _v2);
                w.int32(3, _v3);
                w.int32(4, _v4);
                w.int32(5, _v5);
                w.int32(6, _v6);
                w.end();
                break;

            case Dex:
                w.msg(5);
                w.int32(1, _v1);
                w.int32(2, _v2);
                w.int32(3, _v3);
                w.int32(4, _v4);
                w.int32(5, _v5);
                w.end();
                break;

            case Con:
                w.msg(6);
                w.int32(1, _v1);
                w.int32(2, _v2);
                w.int32(3, _v3);
                w.int32(4, _v4);
                w.int32(5, _v6);
                w.end();
                break;

            case Cha:
                w.msg(7);
                w.int32(1, 0);
                w.end();
                break;

            }
    }

    private static void writeStatRefreshDirect(ProtoWriter w, int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        if (_type != STAT_REFRESH) return;
            w.int32(1, _v1);
            w.int32(2, _v2);
            w.int32(3, _v3);
            w.int32(4, _v4);
            w.int32(5, _v5);
            w.int32(6, _v6);
    }

    private static void writeStatViewDirect(ProtoWriter w, int _mode, int _type, int _value, int _v1, int _v2, int _v3, int _v4, int _v5, int _v6) {
        if (_type != STAT_VIEW) return;
            final int level = _value;
            if (level == 45) {
                writeView4(w, 1, level, 3, 3, 1);
                writeView4(w, 2, level, 3, 3, 1);
                w.msg(3);
                w.int32(1, level);
                w.int32(2, 3);
                w.int32(3, 3);
                w.int32(7, 150);
                w.end();
                writeView4(w, 4, level, 3, 3, 1);
                w.msg(5);
                w.int32(1, level);
                w.int32(2, 3);
                w.int32(3, 2);
                w.int32(6, 150);
                w.end();
            } else {
                writeView3(w, 1, level, 1, 1);
                writeView3(w, 2, level, 1, 1);
                w.msg(3);
                w.int32(1, level);
                w.int32(2, 1);
                w.int32(3, 1);
                w.int32(7, level == 35 ? 100 : 50);
                w.end();
                writeView3(w, 4, level, 1, 1);
                w.msg(5);
                w.int32(1, level);
                w.int32(2, 1);
                if (level != 25) w.int32(3, 1);
                w.int32(6, level == 25 ? 50 : 100);
                w.end();
            }
            w.msg(6);
            w.int32(1, 0);
            w.end();
    }

    private static boolean sendCtor(CIOClient protoRecipient, int mode, int type, int value, int v1, int v2, int v3, int v4, int v5, int v6) {
        int proto19_mode = 0;
        int proto19_type = 0;
        int proto19_value = 0;
        int proto19_v1 = 0;
        int proto19_v2 = 0;
        int proto19_v3 = 0;
        int proto19_v4 = 0;
        int proto19_v5 = 0;
        int proto19_v6 = 0;

            proto19_mode = mode;
            proto19_type = type;
            proto19_value = value;
            proto19_v1 = v1;
            proto19_v2 = v2;
            proto19_v3 = v3;
            proto19_v4 = v4;
            proto19_v5 = v5;
            proto19_v6 = v6;

        return sendFields(protoRecipient, proto19_mode, proto19_type, proto19_value, proto19_v1, proto19_v2, proto19_v3, proto19_v4, proto19_v5, proto19_v6);
    }

    public static boolean sendCreate(CIOClient protoRecipient, CIOClient client, StatContext stx) {
        if (client == null || stx == null)
                    return false;

                final int[] stats = stx.getStats();
                if (stats == null || stats.length < 5)
                    return false;

                final L1PcInstance pc = client.getActiveChar();

                final int str   = stats[0];
                final int intel = stats[1];
                final int wis   = stats[2];
                final int dex   = stats[3];
                final int con   = stats[4];

                final int targetType = stx.getTargetType();
                final int classType  = stx.getClassType();

                int v1 = 0, v2 = 0, v3 = 0;
                int v4 = 0, v5 = 0, v6 = 0;

                switch (targetType) {
                case Str:
                    v1 = CalcStat.근거리대미지(str);
                    v2 = CalcStat.근거리명중(str);
                    v3 = CalcStat.근거리치명타(str);
                    v4 = CalcStat.최대소지무게(str, con);
                    break;

                case Int:
                    v1 = CalcStat.마법대미지(intel);
                    v2 = CalcStat.마법적중(intel);
                    v3 = CalcStat.마법치명타(intel);
                    v4 = CalcStat.마법보너스(classType, intel);
                    v5 = CalcStat.엠소모감소(intel);
                    break;

                case Wis:
                    v1 = CalcStat.엠회복틱(wis);
                    v2 = CalcStat.엠피물약회복(wis);
                    v3 = CalcStat.마법방어(classType, wis);
                    v4 = CalcStat.MinincreaseMp(classType, wis);
                    v5 = CalcStat.MaxincreaseMp(classType, wis);
                    v6 = getHpMpValue(pc, false);
                    break;

                case Dex:
                    v1 = CalcStat.원거리대미지(dex);
                    v2 = CalcStat.원거리명중(dex);
                    v3 = CalcStat.원거리치명타(dex);
                    v4 = CalcStat.물리방어력(dex);
                    v5 = CalcStat.원거리회피(dex);
                    break;

                case Con:
                    v1 = CalcStat.피회복틱(con);
                    v2 = (int) CalcStat.물약회복증가(con);
                    v3 = CalcStat.최대소지무게(str, con);
                    v4 = CalcStat.PureHp(classType, con);
                    v6 = getHpMpValue(pc, true);
                    break;

                default:
                    break;
                }

                return sendCtor(protoRecipient, MODE_CLIENT_ALLOC, targetType, stx.getStatValue() * 2, v1, v2, v3, v4, v5, v6);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, CIOClient client, StatContext stx) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), client, stx);
    }

    public static boolean sendCreate(CIOClient protoRecipient, L1PcInstance pc, int settingType, int statType) {
        int v1 = 0, v2 = 0, v3 = 0;
                int v4 = 0, v5 = 0, v6 = 0;

                if (pc != null) {
                    final int str   = pc.getAbility().getTotalStr();
                    final int intel = pc.getAbility().getTotalInt();
                    final int wis   = pc.getAbility().getTotalWis();
                    final int dex   = pc.getAbility().getTotalDex();
                    final int con   = pc.getAbility().getTotalCon();
                    final int type  = pc.getType();

                    switch (statType) {
                    case Stat_Str:
                        v1 = CalcStat.근거리대미지(str);
                        v2 = CalcStat.근거리명중(str);
                        v3 = CalcStat.근거리치명타(str);
                        v4 = CalcStat.최대소지무게(str, con);
                        break;

                    case Stat_Int:
                        v1 = CalcStat.마법대미지(intel);
                        v2 = CalcStat.마법적중(intel);
                        v3 = CalcStat.마법치명타(intel);
                        v4 = CalcStat.마법보너스(type, intel);
                        v5 = CalcStat.엠소모감소(intel);
                        break;

                    case Stat_Wis:
                        v1 = CalcStat.naturalMpRegen(0, wis, pc.getAbility().getWis());
                        v2 = CalcStat.엠피물약회복(wis);
                        v3 = CalcStat.마법방어(type, wis);
                        v4 = CalcStat.MinincreaseMp(type, pc.getAbility().getWis());
                        v5 = CalcStat.MaxincreaseMp(type, pc.getAbility().getWis());
                        v6 = getHpMpValue(pc, false);
                        break;

                    case Stat_Dex:
                        v1 = CalcStat.원거리대미지(dex);
                        v2 = CalcStat.원거리명중(dex);
                        v3 = CalcStat.원거리치명타(dex);
                        v4 = CalcStat.물리방어력(dex);
                        v5 = CalcStat.원거리회피(dex);
                        break;

                    case Stat_Con:
                        v1 = CalcStat.피회복틱(con);
                        v2 = (int) CalcStat.물약회복증가(con);
                        v3 = CalcStat.최대소지무게(str, con);
                        v4 = CalcStat.PureHp(type, pc.getAbility().getCon());
                        v6 = getHpMpValue(pc, true);
                        break;

                    default:
                        break;
                    }
                }

                return sendCtor(protoRecipient, MODE_PC_STAT_CHANGE, statType, settingType, v1, v2, v3, v4, v5, v6);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, L1PcInstance pc, int settingType, int statType) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), pc, settingType, statType);
    }

    public static boolean sendCreate(CIOClient protoRecipient, L1PcInstance pc, int code) {
        if (pc == null) {
                    return sendCtor(protoRecipient, MODE_STAT_REFRESH, code, 0, 0, 0, 0, 0, 0, 0);
                }

                return sendCtor(
                        protoRecipient, MODE_STAT_REFRESH, code, 0, pc.getAbility().getStr(), pc.getAbility().getInt(),
                        pc.getAbility().getWis(), pc.getAbility().getDex(), pc.getAbility().getCon(),
                        pc.getAbility().getCha());
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, L1PcInstance pc, int code) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), pc, code);
    }

    public static boolean sendCreate(CIOClient protoRecipient, int code, int levelThreshold) {
        return sendCtor(protoRecipient, MODE_STAT_VIEW, code, levelThreshold, 0, 0, 0, 0, 0, 0);
    }

    public static boolean sendCreate(L1PcInstance protoRecipient, int code, int levelThreshold) {
        return sendCreate(protoRecipient == null ? null : protoRecipient.getNetConnection(), code, levelThreshold);
    }
    public static boolean sendConstructed(
            CIOClient protoRecipient, int mode, int type, int value, int v1, int v2, int v3, int v4, int v5, int v6) { return sendCtor(protoRecipient, mode, type, value, v1, v2, v3, v4, v5, v6); }

    public static boolean sendConstructed(
            L1PcInstance protoRecipient, int mode, int type, int value, int v1, int v2, int v3, int v4, int v5, int v6) { return sendCtor(protoRecipient == null ? null : protoRecipient.getNetConnection(), mode, type, value, v1, v2, v3, v4, v5, v6); }

}
