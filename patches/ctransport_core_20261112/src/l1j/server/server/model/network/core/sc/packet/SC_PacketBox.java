package l1j.server.server.model.network.core.sc.packet;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Iterator;
import java.util.List;
import java.util.TimeZone;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.Account;
import l1j.server.server.Opcodes;
import l1j.server.server.model.network.core.CUserSocket;
import l1j.server.server.model.network.core.CIOFrame;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.buffer.WriteBuffer;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.utill.ConnectionConfig;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.utils.SQLUtil;

/**
 * 스킬 아이콘이나 차단 리스트의 표시 등 복수의 용도에 사용되는 패킷의 클래스
 */
public final class SC_PacketBox {

	// *** S_107 sub code list ***
	public static final int MSG_WAR_BEGIN = 0;
	public static final int MSG_WAR_END = 1;
	public static final int MSG_WAR_GOING = 2;
	public static final int MSG_WAR_INITIATIVE = 3;
	public static final int MSG_WAR_OCCUPY = 4;
	public static final int MSG_DUEL = 5;
	public static final int MSG_SMS_SENT = 6;
	public static final int MSG_MARRIED = 9;
	public static final int WEIGHT = 10;
	public static final int FOOD = 11;
	public static final int MSG_LEVEL_OVER = 12;
	public static final int HTML_UB = 14;
	public static final int MSG_ELF = 15;
	public static final int SHOW_LIST_EXCLUDE = 17;
	public static final int ADD_EXCLUDE = 18;
	public static final int REM_EXCLUDE = 19;
	public static final int PC방버프 = 127;
	public static final int ICONS1 = 20;
	public static final int ICONS2 = 21;
	public static final int ICON_AURA = 22;
	public static final int MSG_TOWN_LEADER = 23;
	public static final int PLEDGE_TWO = 24;
	public static final int PLEDGE_REFRESH_PLUS = 25;
	public static final int PLEDGE_REFRESH_MINUS = 26;
	public static final int MSG_RANK_CHANGED = 27;
	public static final int MSG_WIN_LASTAVARD = 30;
	public static final int MSG_FEEL_GOOD = 31;
	public static final int LOGIN_UNKNOWN3 = 32;
	public static final int SOMETHING1 = 33;
	public static final int ICON_BLUEPOTION = 34;
	public static final int ICON_POLYMORPH = 35;
	public static final int ICON_CHATBAN = 36;
	public static final int SOMETHING2 = 37;
	public static final int HTML_CLAN1 = 38;
	public static final int ICON_I2H = 40;
	public static final int CHARACTER_CONFIG = 41;
	public static final int LOGOUT = 42;
	public static final int MSG_CANT_LOGOUT = 43;
	public static final int CALL_SOMETHING = 45;
	public static final int MSG_COLOSSEUM = 49;
	public static final int HTML_CLAN2 = 51;
	public static final int COOK_WINDOW = 52;
	public static final int ICON_COOKING = 53;
	public static final int FISHING = 55;
	public static final int DEL_ICON = 59;
	public static final int DRAGON_PEARL = 60;
	public static final int ALLIANCE_LIST = 62;
	public static final int MINIGAME_START_COUNT = 64;
	public static final int MINIGAME_TIME2 = 65;
	public static final int MINIGAME_LIST = 66;
	public static final int MINIGAME_10SECOND_COUNT = 69;
	public static final int MINIGAME_END = 70;
	public static final int MINIGAME_TIME = 71;
	public static final int MINIGAME_TIME_CLEAR = 72;
	public static final int SPOT = 75;
	public static final int aaaa1 = 78;
	public static final int bbbb2 = 79;
	public static final int cccc3 = 80;
	public static final int EINHASAD = 82;
	public static final int HADIN_DISPLAY = 83;
	public static final int GREEN_MESSAGE = 84;
	public static final int YELLOW_MESSAGE = 61;
	public static final int RED_MESSAGE = 51;
	public static final int SCORE_MARK = 4;
	public static final int EMERALD_ICON = 86;
	public static final int KARMA = 87;
	public static final int INIT_DODGE = 88;
	public static final int DRAGONBLOOD = 100;
	public static final int DODGE = 101;
	public static final int DragonMenu = 102;
	public static final int MINI_MAP_SEND = 111;
	public static final int CLAN_WAREHOUSE_LIST = 117;
	public static final int BAPO = 114;
	public static final int ICON_SECURITY_SERVICES = 125;
	public static final int ICON_PC_BUFF = 127;
	public static final int ER_UpDate = 132;
	public static final int BOOKMARK_SIZE_PLUS_10 = 141;
	public static final int BOOKMARK = 142;
	public static final int UNLIMITED_ICON = 147;
	public static final int UNLIMITED_ICON1 = 180;
	public static final int NONE_TIME_ICON = 180;
	public static final int ITEM_STATUS = 149;
	public static final int MAP_TIMER = 153;
	public static final int BUFFICON = 154;
	public static final int ROUND = 156;
	public static final int ROUND1 = 156;
	public static final int DungeonTime = 159;
	public static final int POSION_ICON = 161;
	public static final int CLAN_BUFF_ICON = 165;
	public static final int HTML_PLEDGE_ANNOUNCE = 167;
	public static final int HTML_PLEDGE_REALEASE_ANNOUNCE = 168;
	public static final int HTML_PLEDGE_WRITE_NOTES = 169;
	public static final int HTML_PLEDGE_MEMBERS = 170;
	public static final int HTML_PLEDGE_ONLINE_MEMBERS = 171;
	public static final int ITEM_ENCHANT_UPDATE = 172;
	public static final int PLEDGE_EMBLEM_STATUS = 173;
	public static final int TOWN_TELEPORT = 176;
	public static final int 공격가능거리 = 160;
	public static final int WORLDMAP_UNKNOWN1 = 184;
	public static final int 몰라3 = 188;
	public static final int 인벤저장 = 189;
	public static final int 배틀샷 = 181;
	public static final int 상점개설횟수 = 198;
	public static final int 유저빽스탭 = 193;
	public static final int ICON_COMBO_BUFF = 204;
	public static final int DRAGONRAID_BUFF = 179;
	public static final int a = 103;



    

    public static void broadcastGreen(String message) {
        for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
            if (pc != null) send(pc, GREEN_MESSAGE, message);
        }
    }

    public static void broadcastGreenMap(int mapId, String message) {
        for (L1PcInstance pc : L1World.getInstance().getVisiblePlayers(mapId)) {
            if (pc != null) send(pc, GREEN_MESSAGE, message);
        }
    }

    public static boolean send(L1PcInstance receiver, int subCode) {
        return sendEncoded(receiver,
                1, subCode, 0, 0, 0, 0, false, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int time, boolean ck, boolean ck2) {
        return sendEncoded(receiver,
                2, 0x56, time, 0, 0, 0, ck, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, L1PcInstance pc, L1ItemInstance weapon) {
        return sendEncoded(receiver,
                3, subCode, 0, 0, 0, 0, false, null, null, weapon, pc, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int range, int type, boolean bow, L1PcInstance pc) {
        return sendEncoded(receiver,
                4, subCode, range, type, 0, 0, bow, null, null, null, pc, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int time1, int time2, int time3, int time4) {
        return sendEncoded(receiver,
                5, subCode, time1, time2, time3, time4, false, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, L1PcInstance pc) {
        return sendEncoded(receiver,
                6, subCode, 0, 0, 0, 0, false, null, null, null, pc, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int value) {
        return sendEncoded(receiver,
                7, subCode, value, 0, 0, 0, false, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int type, int time, boolean second, boolean temp) {
        return sendEncoded(receiver,
                8, subCode, type, time, 0, 0, second, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int time, int gfxid, int type) {
        return sendEncoded(receiver,
                9, subCode, time, gfxid, type, 0, false, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int type, int time) {
        return sendEncoded(receiver,
                10, subCode, type, time, 0, 0, false, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int time, int val, boolean ck, boolean ck2) {
        return sendEncoded(receiver,
                11, EMERALD_ICON, time, val, 0, 0, ck, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int i, int time, boolean ck, boolean ck2, boolean ck3) {
        return sendEncoded(receiver,
                12, EMERALD_ICON, i, time, 0, 0, ck, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, String name) {
        return sendEncoded(receiver,
                13, subCode, 0, 0, 0, 0, false, name, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int id, String name, String clanName) {
        return sendEncoded(receiver,
                14, subCode, id, 0, 0, 0, false, name, clanName, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, L1ItemInstance item, int type) {
        return sendEncoded(receiver,
                15, subCode, type, 0, 0, 0, false, null, null, item, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode1, int subCode2, String name) {
        return sendEncoded(receiver,
                16, subCode1, subCode2, 0, 0, 0, false, name, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int range, int type, boolean bow) {
        return sendEncoded(receiver,
                17, subCode, range, type, 0, 0, bow, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, Object[] names) {
        return sendEncoded(receiver,
                18, subCode, 0, 0, 0, 0, false, null, null, null, null, names, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, String[] names, int type) {
        return sendEncoded(receiver,
                19, subCode, type, 0, 0, 0, false, null, null, null, null, null, names, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, L1ItemInstance item) {
        return sendEncoded(receiver,
                20, subCode, 0, 0, 0, 0, false, null, null, item, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, L1PcInstance pc, int value1, int value2) {
        return sendEncoded(receiver,
                21, subCode, value1, value2, 0, 0, false, null, null, null, pc, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, L1PcInstance pc, int subCode) {
        return sendEncoded(receiver,
                22, subCode, 0, 0, 0, 0, false, null, null, null, pc, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, String name, int mapid, int x, int y, int Mid) {
        return sendEncoded(receiver,
                23, subCode, mapid, x, y, Mid, false, name, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int value, boolean show) {
        return sendEncoded(receiver,
                24, subCode, value, 0, 0, 0, show, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, boolean show) {
        return sendEncoded(receiver,
                25, subCode, 0, 0, 0, 0, show, null, null, null, null, null, null, null);
    }

    public static boolean send(L1PcInstance receiver, int subCode, int itemid, String name, List<L1BookMark> list) {
        return sendEncoded(receiver,
                26, subCode, itemid, 0, 0, 0, false, name, null, null, null, null, null, list);
    }


    private static boolean sendEncoded(L1PcInstance receiver, int _constructorType, int _subCode, int _val1, int _val2, int _val3, int _val4, boolean _bool1, String _strVal1, String _strVal2, L1ItemInstance _item, L1PcInstance _pc, Object[] _objs, String[] _strs, List<L1BookMark> _bookmarks) {
        if (receiver == null) return false;
        final CIOClient socket = receiver.getNetConnection();
        if (socket == null || !socket.isConnected()) return false;
        final CUserSocket connection = socket.getConnection();
        if (connection == null || !connection.isClientConnected()) return false;
        final int capacity = estimateFrameCapacity(_constructorType, _subCode, _val1, _val2, _val3, _val4, _bool1, _strVal1, _strVal2, _item, _pc, _objs, _strs, _bookmarks);
        if (capacity <= 0) return false;
        final CIOFrame frame = connection.beginDirectFrame(capacity);
        if (frame == null) return false;
        boolean committed = false;
        try {
            writePayload(frame.buffer(), _constructorType, _subCode, _val1, _val2, _val3, _val4, _bool1, _strVal1, _strVal2, _item, _pc, _objs, _strs, _bookmarks);
            committed = connection.commitDirectFrame(frame);
            return committed;
        } catch (WriteBuffer.RegisteredCapacityExceededException exceeded) {
            return false;
        } finally {
            if (!committed) connection.abortDirectFrame(frame);
        }
    }

    private static int estimateFrameCapacity(int _constructorType, int _subCode, int _val1, int _val2, int _val3, int _val4, boolean _bool1, String _strVal1, String _strVal2, L1ItemInstance _item, L1PcInstance _pc, Object[] _objs, String[] _strs, List<L1BookMark> _bookmarks) {
        if ((_constructorType == 22 && _subCode == CLAN_WAREHOUSE_LIST)
                || (_constructorType == 1 && _subCode == CALL_SOMETHING)) {
            return ConnectionConfig.MAX_WIRE_PACKET_SIZE;
        }
        long size = (_constructorType == 7 ? 128L : 256L);
        if (_strVal1 != null) size += ((long) _strVal1.length() << 1) + 2;
        if (_strVal2 != null) size += ((long) _strVal2.length() << 1) + 2;
        if (_strs != null) {
            for (String value : _strs) {
                if (value != null) size += ((long) value.length() << 1) + 2;
            }
        }
        if (_objs != null) {
            for (Object obj : _objs) {
                if (obj instanceof L1PcInstance) {
                    final String name = ((L1PcInstance) obj).getName();
                    if (name != null) size += ((long) name.length() << 1) + 4;
                }
            }
        }
        if (_bookmarks != null) {
            for (L1BookMark book : _bookmarks) {
                if (book == null) continue;
                final String name = book.getName();
                size += 16L + (name == null ? 0L : ((long) name.length() << 1));
            }
        }
        return size > Integer.MAX_VALUE ? -1 : (int) size;
    }

	private static void writePayload(WriteBuffer buffer, int _constructorType, int _subCode, int _val1, int _val2, int _val3, int _val4, boolean _bool1, String _strVal1, String _strVal2, L1ItemInstance _item, L1PcInstance _pc, Object[] _objs, String[] _strs, List<L1BookMark> _bookmarks) {
		buffer.writeC(Opcodes.S_EVENT);
		buffer.writeC(_subCode);

		switch (_constructorType) {
		case 1:
			switch (_subCode) {
			case aaaa1:
			case bbbb2:
				buffer.writeC(7);
				for (int i = 0; i < 7; i++) buffer.writeS("");
				buffer.writeH(0);
				break;
			case 몰라3:
				buffer.writeD(0);
				buffer.writeD(0);
				break;
			case 인벤저장:
				buffer.writeD(0x00);
				break;
			case WORLDMAP_UNKNOWN1:
				buffer.writeH(0);
				break;
			case MSG_WAR_INITIATIVE:
			case MSG_WAR_OCCUPY:
			case MSG_MARRIED:
			case MSG_FEEL_GOOD:
			case MSG_CANT_LOGOUT:
			case LOGOUT:
			case ICON_SECURITY_SERVICES:
			case FISHING:
			case MINIGAME_TIME2:
				break;
			case CALL_SOMETHING:
				callSomething(buffer);
				break;
			case MINIGAME_10SECOND_COUNT:
				buffer.writeC(10); buffer.writeC(109); buffer.writeC(85); buffer.writeC(208); buffer.writeC(2); buffer.writeC(220);
				break;
			case DEL_ICON:
				buffer.writeH(0);
				break;
			case MINIGAME_END:
				buffer.writeC(147); buffer.writeC(92); buffer.writeC(151); buffer.writeC(220); buffer.writeC(42); buffer.writeC(74);
				break;
			case MINIGAME_START_COUNT:
				buffer.writeC(5); buffer.writeC(129); buffer.writeC(252); buffer.writeC(125); buffer.writeC(110); buffer.writeC(17);
				break;
			case ICON_AURA:
				buffer.writeC(0x98); buffer.writeC(0); buffer.writeC(0); buffer.writeC(0); buffer.writeC(0); buffer.writeC(0);
				break;
			default:
				break;
			}
			break;

		case 2:
			buffer.writeC(0xAA);
			buffer.writeC(0x01);
			buffer.writeH(_val1 / 16);
			buffer.writeH(0x00);
			break;

		case 3: {
			int range = 1;
			int equipType = 0;
			int isTohand = 0;

			if (_item != null && _item.isEquipped()) {
				int weaponType = _item.getItem().getType();
				if (_item.getItem().isTwohandedWeapon()) {
					isTohand = 1;
				}
				switch (weaponType) {
				case 1: case 3: equipType = 1; break;
				case 6: case 15: equipType = 2; break;
				case 4: range = 17; equipType = 3; break;
				case 10: case 13: range = 15; equipType = 3; break;
				case 12: equipType = 4; break;
				case 5: case 14: case 18:
					range = 2;
					if (weaponType == 18) equipType = 10;
					else equipType = 5;
					break;
				case 7: case 16: equipType = 6; break;
				case 2: equipType = 7; break;
				case 11: case 17: equipType = 8; break;
				default: equipType = 0; break;
				}
			}
			if (_pc != null) {
				_pc.setAttackRang(range);
			}
			buffer.writeC(range);
			buffer.writeC(equipType);
			buffer.writeC(isTohand);
			break;
		}

		case 4:
			switch (_subCode) {
			case 공격가능거리:
				buffer.writeC(_val1);
				buffer.writeC(_val2);
				if (_bool1) buffer.writeC(1);
				else buffer.writeC(0);
				if (_pc != null) {
					_pc.setAutoRange(_val1);
				}
				break;
			}
			break;

		case 5:
			switch (_subCode) {
			case DungeonTime:
				buffer.writeD(7); buffer.writeD(1); buffer.writeS("$12125"); buffer.writeD(_val1);
				buffer.writeD(2); buffer.writeS("$6081"); buffer.writeD(_val2);
				buffer.writeD(15); buffer.writeS("$13527"); buffer.writeD(_val3);
				buffer.writeD(500); buffer.writeS("$19375"); buffer.writeD(_val4);
				buffer.writeD(49200);
				break;
			default:
				break;
			}
			break;

		case 6:
			switch (_subCode) {
			case TOWN_TELEPORT:
				buffer.writeC(0x01);
				if (_pc != null) {
					buffer.writeH(_pc.getX());
					buffer.writeH(_pc.getY());
				} else {
					buffer.writeH(0); buffer.writeH(0);
				}
				break;
			case 유저빽스탭:
				if (_pc != null) {
					buffer.writeH(_pc.getX());
					buffer.writeH(_pc.getY());
				} else {
					buffer.writeH(0); buffer.writeH(0);
				}
				break;
			}
			break;

		case 7:
			switch (_subCode) {
			case LOGIN_UNKNOWN3:
				buffer.writeC(0x10);
				buffer.writeD(0); 
				buffer.writeD(2);
				buffer.writeD(0);
				buffer.writeD(0x00200000);
				break;
			case DRAGONRAID_BUFF:
				buffer.writeC(0x01); buffer.writeC(0x27); buffer.writeC(0x0E);
				buffer.writeD(_val1);
				buffer.writeH(0x63EF);
				break;
			case 204:
				buffer.writeC(_val1);
				buffer.writeH(0);
				break;
			case PC방버프:
				if (_val1 == 1) buffer.writeC(0x18);
				else buffer.writeC(0);
				break;
			case 상점개설횟수:
				buffer.writeD(_val1); buffer.writeD(0x28); buffer.writeD(0x00);
				break;
			case ICON_BLUEPOTION:
			case ICON_CHATBAN:
			case ICON_I2H:
			case MINIGAME_TIME:
			case INIT_DODGE:
				buffer.writeH(_val1);
				break;
			case MAP_TIMER:
				buffer.writeD(_val1);
				break;
			case 배틀샷:
				buffer.writeD(_val1);
				break;
			case MSG_WAR_BEGIN:
			case MSG_WAR_END:
			case MSG_WAR_GOING:
				buffer.writeC(_val1);
				buffer.writeH(0);
				break;
			case MSG_SMS_SENT:
			case WEIGHT:
			case FOOD:
			case DODGE:
				buffer.writeC(_val1);
				break;
			case MSG_ELF:
			case MSG_COLOSSEUM:
			case SPOT:
			case ER_UpDate:
				buffer.writeC(_val1);
				break;
			case MSG_LEVEL_OVER:
				buffer.writeC(0);
				buffer.writeC(_val1);
				break;
			case COOK_WINDOW:
				buffer.writeC(0xdb); buffer.writeC(0x31); buffer.writeC(0xdf); buffer.writeC(0x02); buffer.writeC(0x01);
				buffer.writeC(_val1);
				break;
			case MINIGAME_LIST:
				buffer.writeH(0x00); buffer.writeH(0x00);
				break;
			case EINHASAD: {
				int einVal = _val1 / 10000;
				buffer.writeD(einVal);
				buffer.writeC(0);
				buffer.writeD(16);
				buffer.writeC(32); buffer.writeC(0); buffer.writeC(0);
				buffer.writeD(1);
				buffer.writeC(0); buffer.writeC(0); buffer.writeC(0); buffer.writeC(0);
				break;
			}
			case HADIN_DISPLAY:
				buffer.writeC(_val1);
				break;
			case BOOKMARK_SIZE_PLUS_10:
				buffer.writeC(_val1);
				break;
			case PLEDGE_EMBLEM_STATUS:
				buffer.writeC(1);
				if (_val1 == 0) buffer.writeC(0);
				else if (_val1 == 1) buffer.writeC(1);
				buffer.writeD(0x00);
				break;
			case ROUND1:
				buffer.writeD(_val1);
				buffer.writeD(12);
				break;
			default:
				break;
			}
			break;

		case 8:
			switch (_subCode) {
			case BUFFICON:
				buffer.writeH(_val2);
				buffer.writeH(_val1);
				buffer.writeH(0x00);
				buffer.writeH(_bool1 ? 0x01 : 0x00);
				break;
			}
			break;

		case 9:
			switch (_subCode) {
			case SOMETHING2:
				buffer.writeC(_val1);
				buffer.writeD(_val2);
				buffer.writeH(_val3);
				break;
			case BUFFICON:
				buffer.writeH(_val1);
				buffer.writeD(_val2);
				buffer.writeC(_val3);
				buffer.writeC(0x00);
				break;
			case EMERALD_ICON:
				buffer.writeC(_val1);
				buffer.writeC(1);
				switch (_val1) {
				case 62:
					buffer.writeH(_val2);
					buffer.writeC(20);
					buffer.writeC(_val3);
					break;
				case 112:
				case 129:
					buffer.writeC(_val2);
					buffer.writeH(_val3);
					break;
				default:
					buffer.writeD(_val2);
				}
				break;
			}
			break;

		case 10:
			switch (_subCode) {
			case ICON_POLYMORPH:
				buffer.writeH(_val1);
				buffer.writeC(_val2);
				break;
			case ICON_COOKING:
				if (_val1 != 7) {
					buffer.writeC(0x0c); buffer.writeC(0x0c); buffer.writeC(0x0c); buffer.writeC(0x12);
					buffer.writeC(0x0c); buffer.writeC(0x09); buffer.writeC(0x00); buffer.writeC(0x00);
					buffer.writeC(_val1); buffer.writeC(0x24); buffer.writeH(_val2); buffer.writeH(0x00);
				} else {
					buffer.writeC(0x0c); buffer.writeC(0x0c); buffer.writeC(0x0c); buffer.writeC(0x12);
					buffer.writeC(0x0c); buffer.writeC(0x09); buffer.writeC(0xc8); buffer.writeC(0x00);
					buffer.writeC(_val1); buffer.writeC(0x26); buffer.writeH(_val2); buffer.writeC(0x3e); buffer.writeC(0x87);
				}
				break;
			case ICON_AURA:
				buffer.writeC(0xdd);
				buffer.writeH(_val2);
				buffer.writeC(_val1);
				break;
			case MSG_DUEL:
				buffer.writeD(_val1);
				buffer.writeD(_val2);
				break;
			case BUFFICON:
				buffer.writeH(_val2);
				buffer.writeH(_val1);
				buffer.writeH(0);
				break;
			case DRAGONBLOOD:
				buffer.writeC(_val1);
				buffer.writeD(_val2);
				break;
			case ROUND:
				buffer.writeD(_val1);
				buffer.writeD(_val2);
				break;
			case DRAGON_PEARL:
				buffer.writeC(_val2);
				buffer.writeC(_val1);
				break;
			case EMERALD_ICON:
				buffer.writeC(0x70);
				buffer.writeC(0x01);
				buffer.writeC(_val1);
				buffer.writeH(_val2);
				break;
			case NONE_TIME_ICON:
				buffer.writeC(_val1);
				buffer.writeD(_val2);
				buffer.writeD(0x00000D67);
				buffer.writeH(0x00);
				break;
			default:
				break;
			}
			break;

		case 11:
			buffer.writeC(0x81);
			buffer.writeC(0x01);
			buffer.writeC(_val2);
			buffer.writeH(_val1);
			break;

		case 12:
			buffer.writeC(0x3e);
			buffer.writeC(_val1);
			buffer.writeH(_val2);
			buffer.writeC(0x14);
			buffer.writeC(0x86);
			break;

		case 13:
			switch (_subCode) {
			case MSG_TOWN_LEADER:
			case HTML_PLEDGE_REALEASE_ANNOUNCE:
				buffer.writeS(_strVal1);
				break;
			case GREEN_MESSAGE:
				buffer.writeC(2);
				buffer.writeS(_strVal1);
				break;
			default:
				break;
			}
			break;

		case 14:
			switch (_subCode) {
			case MSG_WIN_LASTAVARD:
				buffer.writeD(_val1);
				buffer.writeS(_strVal1);
				buffer.writeS(_strVal2);
				break;
			default:
				break;
			}
			break;

		case 15:
			switch (_subCode) {
			case ITEM_STATUS:
				if (_item != null) {
					buffer.writeD(_item.getId());
				} else {
					buffer.writeD(0);
				}
				buffer.writeH(_val1);
				break;
			}
			break;

		case 16:
			switch (_val1) {
			case RED_MESSAGE:
			case YELLOW_MESSAGE:
				buffer.writeC(2);
				buffer.writeH(26204);
				buffer.writeC(_val1);
				buffer.writeS(_strVal1);
				break;
			case SCORE_MARK:
				buffer.writeC(_val1);
				buffer.writeS(_strVal1);
				break;
			default:
				switch (_subCode) {
				case MSG_RANK_CHANGED:
					buffer.writeC(_val1);
					buffer.writeS(_strVal1);
					break;
				case ADD_EXCLUDE:
				case REM_EXCLUDE:
					buffer.writeS(_strVal1);
					buffer.writeC(_val1);
					break;
				}
				break;
			}
			break;

		case 17:
			switch (_subCode) {
			case EMERALD_ICON:
				buffer.writeC(62);
				if (_val2 == 0 || _val2 == 2) {
					buffer.writeC(_bool1 ? 1 : 2);
				} else {
					buffer.writeC(_bool1 ? 1 : 3);
				}
				buffer.writeH(_val1);
				buffer.writeC(0); buffer.writeC(0);
				if (_val2 == 0) buffer.writeC(0x86);
				else buffer.writeC(0x49);
				break;
			}
			break;

		case 18:
			switch (_subCode) {
			case HTML_PLEDGE_ONLINE_MEMBERS:
				if (_objs != null) {
					buffer.writeH(_objs.length);
					for (Object name : _objs) {
						if (name == null) continue;
						L1PcInstance memberPc = (L1PcInstance) name;
						buffer.writeS(memberPc.getName());
						buffer.writeC(0);
					}
				} else {
					buffer.writeH(0);
				}
				break;
			default:
				break;
			}
			break;

		case 19:
			buffer.writeC(0);
			switch (_subCode) {
			case SHOW_LIST_EXCLUDE:
				buffer.writeC(_val1);
				if (_strs != null) {
					buffer.writeC(_strs.length);
					for (String name : _strs) {
						buffer.writeS(name);
					}
				} else {
					buffer.writeC(0);
				}
				buffer.writeH(0);
				break;
			}
			break;

		case 20:
			switch (_subCode) {
			case DragonMenu:
				if (_item != null) {
					buffer.writeD(_item.getId());
					buffer.writeC(_item.getItemId() == 490012 ? 0x01 : 0x00);
					buffer.writeC(_item.getItemId() == 490013 ? 0x01 : 0x00);
					buffer.writeC(_item.getItemId() == 490014 ? 0x01 : 0x00);
				} else {
					buffer.writeD(0); buffer.writeC(0); buffer.writeC(0); buffer.writeC(0);
				}
				buffer.writeC(0);
				break;
			case ITEM_ENCHANT_UPDATE:
				if (_item != null) {
					buffer.writeD(_item.getId());
					buffer.writeC(24);
					buffer.writeC(0);
					buffer.writeH(0);
					buffer.writeH(0);
					buffer.writeC(_item.getEnchantLevel());
					buffer.writeD(_item.getId());
					buffer.writeD(0);
					buffer.writeD(0);
					buffer.writeD(_item.getBless() >= 128 ? 3 : _item.getItem().isTradable() ? 7 : 2);
					buffer.writeC(0);
					buffer.writeC(_item.getAttrEnchantBit(_item.getAttrEnchantLevel()));
					buffer.writeH(0);
				}
				break;
			default:
				break;
			}
			break;

		case 21:
			switch (_subCode) {
			case POSION_ICON:
				buffer.writeC(_val1);
				buffer.writeD(_val2);
				break;
			default:
				break;
			}
			break;

		case 22:
			if (_subCode == CLAN_WAREHOUSE_LIST) {
				int count = 0;
				Connection con = null;
				PreparedStatement pstm = null;
				PreparedStatement pstm2 = null;
				PreparedStatement pstm3 = null;
				ResultSet rs = null;
				ResultSet rs3 = null;
				try {
					con = DatabaseFactory.getInstance().getConnection();
					String clanName = (_pc != null) ? _pc.getClanname() : "";
					pstm = con.prepareStatement("SELECT id, time FROM clan_warehouse_log WHERE clan_name=?");
					pstm.setString(1, clanName);
					rs = pstm.executeQuery();
					while (rs.next()) {
						Timestamp ts = rs.getTimestamp(2);
						if (ts != null && System.currentTimeMillis() - ts.getTime() > 4320000) {
							pstm2 = con.prepareStatement("DELETE FROM clan_warehouse_log WHERE id=?");
							pstm2.setInt(1, rs.getInt(1));
							pstm2.execute();
							SQLUtil.close(pstm2);
						} else {
							count++;
						}
					}
					buffer.writeD(count);
					pstm3 = con.prepareStatement("SELECT name, item_name, item_count, type, time FROM clan_warehouse_log WHERE clan_name=?");
					pstm3.setString(1, clanName);
					rs3 = pstm3.executeQuery();
					while (rs3.next()) {
						buffer.writeS(rs3.getString(1));
						buffer.writeC(rs3.getInt(4));
						buffer.writeS(rs3.getString(2));
						buffer.writeD(rs3.getInt(3));
						Timestamp ts = rs3.getTimestamp(5);
						long diff = ts != null ? System.currentTimeMillis() - ts.getTime() : 0L;
						buffer.writeD((int) (diff / 60000));
					}
				} catch (SQLException e) {
				} finally {
					SQLUtil.close(rs3);
					SQLUtil.close(rs);
					SQLUtil.close(pstm3);
					SQLUtil.close(pstm2);
					SQLUtil.close(pstm);
					SQLUtil.close(con);
				}
			} else if (_subCode == PLEDGE_REFRESH_MINUS) {
				if (_pc != null) {
					buffer.writeS(_pc.getName());
					buffer.writeC(_pc.getClanRank());
				} else {
					buffer.writeS(""); buffer.writeC(0);
				}
				buffer.writeH(0);
			} else if (_subCode == KARMA) {
				if (_pc != null) {
					buffer.writeD(_pc.getKarma());
				} else {
					buffer.writeD(0);
				}
			}
			break;

		case 23:
			switch (_subCode) {
			case MINI_MAP_SEND:
				buffer.writeS(_strVal1);
				buffer.writeH(_val1);
				buffer.writeH(_val2);
				buffer.writeH(_val3);
				buffer.writeD(_val4);
				break;
			default:
				break;
			}
			break;

		case 24:
			switch (_subCode) {
			case BAPO:
				buffer.writeD(_val1);
				buffer.writeD(_bool1 ? 0x01 : 0x00);
				break;
			case UNLIMITED_ICON:
				buffer.writeC(_bool1 ? 0x01 : 0x00);
				buffer.writeC(_val1);
				break;
			case UNLIMITED_ICON1:
				buffer.writeC(_bool1 ? 0x01 : 0x00);
				buffer.writeD(_val1);
				buffer.writeD(0);
				buffer.writeH(0);
				break;
			default:
				break;
			}
			break;

		case 25:
			switch (_subCode) {
			case CLAN_BUFF_ICON:
				buffer.writeC(_bool1 ? 0x01 : 0x00);
				break;
			}
			break;

		case 26:
			switch (_subCode) {
			case BOOKMARK:
				buffer.writeC(0);
				buffer.writeD(_val1);
				buffer.writeS(_strVal1);
				if (_bookmarks != null) {
					buffer.writeC(_bookmarks.size());
					for (L1BookMark book : _bookmarks) {
						buffer.writeS(book.getName());
						buffer.writeD(book.getMapId());
						buffer.writeH(book.getLocX());
						buffer.writeH(book.getLocY());
					}
				} else {
					buffer.writeC(0);
				}
				break;
			}
			break;

		default:
			break;
		}
	}


	private static void callSomething(WriteBuffer buffer) {
		Iterator<L1PcInstance> itr = L1World.getInstance().getAllPlayers().iterator();

		buffer.writeC(L1World.getInstance().getAllPlayers().size());
		L1PcInstance targetPc = null;
		Account acc = null;
		Calendar cal = null;
		while (itr.hasNext()) {
			targetPc = itr.next();
			acc = Account.load(targetPc.getAccountName());
			if (acc == null) {
				buffer.writeD(0);
			} else {
				cal = Calendar.getInstance(TimeZone.getTimeZone(Config.TIME_ZONE));
				long lastactive = acc.getLastActive().getTime();
				cal.setTimeInMillis(lastactive);
				cal.set(Calendar.YEAR, 1970);
				int time = (int) (cal.getTimeInMillis() / 1000);
				buffer.writeD(time);
			}

			buffer.writeS(targetPc.getName());
			buffer.writeS(targetPc.getClanname());
		}
	}
}