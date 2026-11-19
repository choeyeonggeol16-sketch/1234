package l1j.server.server;

public class Opcodes {
	public static final int C_PLATE = 0x01;
	public static final int C_LOGIN_TEST = 0x04;
	public static final int C_CHANGE_ACCOUNTINFO = 0x05;
	public static final int C_BLINK = 0x08;
	public static final int C_BUYABLE_SPELL = 0x0B;
	public static final int C_HYPERTEXT_INPUT_RESULT = 0x0C;
	public static final int C_ADD_BUDDY = 0x0D;
	public static final int C_WITHDRAW = 0x0E;
	public static final int C_TELL = 0x11;
	public static final int C_ACCEPT_XCHG = 0x12;
	public static final int C_READ_NOTICE = 0x16;
	public static final int C_HACTION = 0x17;
	public static final int C_EXCHANGEABLE_SPELL = 0x18;
	public static final int C_USE_SPELL = 0x19;
	public static final int C_ATTACK = 0x1A;
	public static final int C_UPLOAD_EMBLEM = 0x1C;
	public static final int C_MERCENARYSELECT = 0x20;
	public static final int C_USE_ITEM = 0x21;
	public static final int C_LEAVE_PLEDGE = 0x22;
	public static final int C_RANK_CONTROL = 0x24;
	public static final int C_BOOKMARK = 0x28;
	public static final int C_MOVE = 0x29;
	public static final int C_SAY = 0x2A;
	public static final int C_WHO_PLEDGE = 0x2B;
	public static final int C_MERCENARYARRANGE = 0x2D;
	public static final int C_MONITOR_CONTROL = 0x2E;
	public static final int C_SLAVE_CONTROL = 0x30;
	public static final int C_SAVEIO = 0x31;
	public static final int C_WANTED = 0x32;
	public static final int C_EXCLUDE = 0x33;
	public static final int C_DROP = 0x34;
	public static final int C_ALIVE = 0x35;
	public static final int C_BUILDER_CONTROL = 0x36;
	public static final int C_SELECT_TIME = 0x3A;
	public static final int C_INCLUDE = 0x3B;
	public static final int C_PERSONAL_SHOP = 0x3C;
	public static final int C_DELETE_CHARACTER = 0x3D;
	public static final int C_SHUTDOWN = 0x3F;
	public static final int C_WHO = 0x42;
	public static final int C_VERSION = 0x46;
	public static final int C_WHO_PARTY = 0x47;
	public static final int C_LOGIN_RESULT = 0x4C;
	public static final int C_RETURN_SUMMON = 0x4D;
	public static final int C_QUERY_PERSONAL_SHOP = 0x4E;
	public static final int C_EXTENDED_PROTOBUF = 0x51;
	public static final int C_BAN = 0x52;
	public static final int C_CANCEL_XCHG = 0x53;
	public static final int C_NPC_ITEM_CONTROL = 0x54;
	public static final int C_LEAVE_PARTY = 0x55;
	public static final int C_GOTO_MAP = 0x59;
	public static final int C_ADD_XCHG = 0x5B;
	public static final int C_WAREHOUSE_CONTROL = 0x61;
	public static final int C_MARRIAGE = 0x62;
	public static final int C_CHANNEL = 0x63;
	public static final int C_CONTROL_WEATHER = 0x64;
	public static final int C_GET = 0x65;
	public static final int C_CREATE_CUSTOM_CHARACTER = 0x67;
	public static final int C_TELEPORT = 0x69;
	public static final int C_KICK = 0x6A;
	public static final int C_ADDR = 0x6B;
	public static final int C_DEAD_RESTART = 0x6D;
	public static final int C_QUERY_CASTLE_SECURITY = 0x6E;
	public static final int C_GIVE = 0x72;
	public static final int C_CHAT = 0x73;
	public static final int C_MERCENARYNAME = 0x74;
	public static final int C_FIX = 0x78;
	public static final int C_QUERY_BUDDY = 0x7A;
	public static final int C_FAR_ATTACK = 0x7B;
	public static final int C_CHANGE_PASSWORD = 0x7C;
	public static final int C_TELEPORT_USER = 0x7D;
	public static final int C_CHANGE_DIRECTION = 0x80;
	public static final int C_NEW_ACCOUNT = 0x82;
	public static final int C_SELECTABLE_TIME = 0x85;
	public static final int C_WISH = 0x86;
	public static final int C_EXTENDED = 0x89;
	public static final int C_READ_NEWS = 0x8B;
	public static final int C_OPEN = 0x8E;
	public static final int C_DUEL = 0x90;
	public static final int C_ASK_XCHG = 0x91;
	public static final int C_REGISTER_QUIZ = 0x93;
	public static final int C_FIXABLE_ITEM = 0x96;
	public static final int C_CHECK_PK = 0x97;
	public static final int C_SERVER_SELECT = 0x99;
	public static final int C_EXTENDED_HYBRID = 0x9A;
	public static final int C_TITLE = 0x9B;
	public static final int C_ARCHERARRANGE = 0x9C;
	public static final int C_DELETE_BOOKMARK = 0x9D;
	public static final int C_QUIT = 0xA0;
	public static final int C_BOARD_READ = 0xA2;
	public static final int C_MERCENARYEMPLOY = 0xA4;
	public static final int C_EMBLEM = 0xA7;
	public static final int C_ALT_ATTACK = 0xA8;
	public static final int C_ENTER_SHIP = 0xAB;
	public static final int C_INVITE_PARTY = 0xAC;
	public static final int C_REMOVE_BUDDY = 0xAD;
	public static final int C_EXCHANGE_SPELL = 0xAE;
	public static final int C_BANISH_PARTY = 0xAF;
	public static final int C_LOGOUT = 0xB0;
	public static final int C_SHIFT_SERVER = 0xB3;
	public static final int C_BOOK = 0xB7;
	public static final int C_INVITE_PARTY_TARGET = 0xB9;
	public static final int C_BOARD_DELETE = 0xBE;
	public static final int C_EXIT_GHOST = 0xC0;
	public static final int C_MATCH_MAKING = 0xC5;
	public static final int C_CHECK_INVENTORY = 0xC6;
	public static final int C_ENTER_PORTAL = 0xC7;
	public static final int C_THROW = 0xC8;
	public static final int C_SILENCE = 0xC9;
	public static final int C_GOTO_PORTAL = 0xCB;
	public static final int C_WAR = 0xCE;
	public static final int C_BOARD_WRITE = 0xCF;
	public static final int C_VOICE_CHAT = 0xD0;
	public static final int C_JOIN_PLEDGE = 0xD1;
	public static final int C_TAX = 0xD4;
	public static final int C_SMS = 0xD6;
	public static final int C_BUY_SELL = 0xD7;
	public static final int C_ONOFF = 0xD8;
	public static final int C_DEPOSIT = 0xD9;
	public static final int C_REQUEST_ROLL = 0xDA;
	public static final int C_START_CASTING = 0xDC;
	public static final int C_BOARD_LIST = 0xDD;
	public static final int C_MAIL = 0xDF;
	public static final int C_PLEDGE_WATCH = 0xE2;
	public static final int C_RESTART = 0xE4;
	public static final int C_SUMMON = 0xE5;
	public static final int C_CHAT_PARTY_CONTROL = 0xE7;
	public static final int C_CLIENT_READY = 0xE8;
	public static final int C_LOGIN = 0xE9;
	public static final int C_ENTER_WORLD = 0xEC;
	public static final int C_ATTACK_CONTINUE = 0xED;
	public static final int C_CREATE_PLEDGE = 0xF4;
	public static final int C_BAN_MEMBER = 0xF5;
	public static final int C_ACTION = 0xF6;
	public static final int C_CHANGE_CASTLE_SECURITY = 0xF7;
	public static final int C_ANSWER = 0xF8;
	public static final int C_DESTROY_ITEM = 0xFA;
	public static final int C_SAVE = 0xFB;
	public static final int C_DIALOG = 0xFC;
	public static final int C_BUY_SPELL = 0xFD;

	public static final int S_CHANGE_PASSWORD_CHECK = 0x00;
	public static final int S_WEATHER = 0x01;
	public static final int S_DRUNKEN = 0x02;
	public static final int S_POISON = 0x04;
	public static final int S_XCHG_RESULT = 0x05;
	public static final int S_NOT_ENOUGH_FOR_SPELL = 0x0C;
	public static final int S_CHANGE_ATTR = 0x0D;
	public static final int S_TELL = 0x0F;
	public static final int S_REQUEST_SUMMON = 0x11;
	public static final int S_BOARD_READ = 0x12;
	public static final int S_ROLL_RESULT = 0x14;
	public static final int S_REMOVE_INVENTORY = 0x15;
	public static final int S_CHANGE_DIRECTION = 0x16;
	public static final int S_ADD_SPELL = 0x19;
	public static final int S_MERCENARYNAME = 0x1A;
	public static final int S_CHANGE_ALIGNMENT = 0x1D;
	public static final int S_HIT_POINT = 0x20;
	public static final int S_NOTICE = 0x21;
	public static final int S_CLONE = 0x22;
	public static final int S_DELETE_CHARACTER_CHECK = 0x23;
	public static final int S_EFFECT = 0x25;
	public static final int S_PERSONAL_SHOP_LIST = 0x26;
	public static final int S_BLIND = 0x2A;
	public static final int S_CREATE_CHARACTER_CHECK = 0x2D;
	public static final int S_CHANGE_DESC = 0x2E;
	public static final int S_BUYABLE_SPELL_LIST = 0x30;
	public static final int S_CHANGE_ITEM_USE = 0x31;
	public static final int S_ATTACK_ALL = 0x32;
	public static final int S_EXP = 0x34;
	public static final int S_MOVE_OBJECT = 0x36;
	public static final int S_TAX = 0x3A;
	public static final int S_VOICE_CHAT = 0x3B;
	public static final int S_COMMAND_TARGET = 0x3D;
	public static final int S_ADD_INVENTORY_BATCH = 0x41;
	public static final int S_AGIT_MAP = 0x42;
	public static final int S_SERVER_LIST = 0x44;
	public static final int S_AC = 0x45;
	public static final int S_NEW_CHAR_INFO = 0x47;
	public static final int S_ACTION = 0x48;
	public static final int S_CHARACTER_INFO = 0x4B;
	public static final int S_MAGE_STRENGTH = 0x4D;
	public static final int S_WITHDRAW = 0x4E;
	public static final int S_PING = 0x4F;
	public static final int S_MASTER = 0x51;
	public static final int S_CRIMINAL = 0x52;
	public static final int S_HYPERTEXT = 0x53;
	public static final int S_CHANGE_LEVEL = 0x55;
	public static final int S_ASK = 0x57;
	public static final int S_CASTLE_OWNER = 0x59;
	public static final int S_RESTART = 0x5D;
	public static final int S_BLINK = 0x5E;
	public static final int S_SELECTABLE_TIME_LIST = 0x5F;
	public static final int S_VERSION_CHECK = 0x61;
	public static final int S_WAR = 0x63;
	public static final int S_AGIT_LIST = 0x64;
	public static final int S_CHANGE_LIGHT = 0x65;
	public static final int S_SHOW_MAP = 0x67;
	public static final int S_RESURRECT = 0x6D;
	public static final int S_PORTAL = 0x6E;
	public static final int S_ATTACK_MANY = 0x70;
	public static final int S_REMOVE_SPELL = 0x78;
	public static final int S_EVENT = 0x79;
	public static final int S_ADD_BOOKMARK = 0x7B;
	public static final int S_ABILITY_SCORES = 0x7C;
	public static final int S_IDENTIFY_CODE = 0x7D;
	public static final int S_HIT_RATIO = 0x7E;
	public static final int S_CHANGE_ITEM_DESC_EX = 0x80;
	public static final int S_NUM_CHARACTER = 0x82;
	public static final int S_CHANGE_ITEM_DESC = 0x86;
	public static final int S_INVISIBLE = 0x87;
	public static final int S_SAY_CODE = 0x88;
	public static final int S_MESSAGE = 0x8A;
	public static final int S_PLEDGE_WATCH = 0x8B;
	public static final int S_SELL_LIST = 0x8D;
	public static final int S_FIXABLE_ITEM_LIST = 0x8E;
	public static final int S_MAGE_SHIELD = 0x90;
	public static final int S_READ_MAIL = 0x95;
	public static final int S_BOARD_LIST = 0x97;
	public static final int S_SLAVE_CONTROL = 0x99;
	public static final int S_EMBLEM = 0x9A;
	public static final int S_ADD_XCHG = 0x9B;
	public static final int S_SAY = 0x9C;
	public static final int S_STATUS = 0xA0;
	public static final int S_MAGE_DEXTERITY = 0xA1;
	public static final int S_TITLE = 0xA2;
	public static final int S_WARNING_CODE = 0xA3;
	public static final int S_CHANGE_ITEM_TYPE = 0xA4;
	public static final int S_BOOK_LIST = 0xA5;
	public static final int S_MERCENARYARRANGE = 0xA9;
	public static final int S_ATTACK = 0xAB;
	public static final int S_CLIENT_READY = 0xB2;
	public static final int S_PUT_OBJECT = 0xB3;
	public static final int S_EXTENDED_PROTOBUF = 0xB4;
	public static final int S_EXTENDED_HYBRID = 0xB5;
	public static final int S_RETRIEVE_LIST = 0xB6;
	public static final int S_HYPERTEXT_INPUT = 0xB8;
	public static final int S_WIELD = 0xBB;
	public static final int S_POLYMORPH = 0xBC;
	public static final int S_MANA_POINT = 0xBD;
	public static final int S_WORLD = 0xBE;
	public static final int S_SOUND_EFFECT = 0xC2;
	public static final int S_MAIL_INFO = 0xC3;
	public static final int S_EXCHANGEABLE_SPELL_LIST = 0xC6;
	public static final int S_ENTER_WORLD_CHECK = 0xC8;
	public static final int S_XCHG_START = 0xC9;
	public static final int S_TIME = 0xCA;
	public static final int S_MATCH_MAKING = 0xCC;
	public static final int S_MERCENARYSELECT = 0xCE;
	public static final int S_LOGIN_CHECK = 0xCF;
	public static final int S_PLEDGE = 0xD0;
	public static final int S_WANTED_LOGIN = 0xD1;
	public static final int S_KICK = 0xD2;
	public static final int S_BUY_LIST = 0xD4;
	public static final int S_EFFECT_LOC = 0xD5;
	public static final int S_ARCHERARRANGE = 0xD7;
	public static final int S_DEPOSIT = 0xD8;
	public static final int S_EXTENDED = 0xDB;
	public static final int S_CHANGE_ACCOUNTINFO_CHECK = 0xDD;
	public static final int S_CHANGE_COUNT = 0xDE;
	public static final int S_CHANGE_ABILITY = 0xE0;
	public static final int S_DECREE = 0xE1;
	public static final int S_MAGIC_STATUS = 0xE5;
	public static final int S_CHANGE_ITEM_BLESS = 0xE7;
	public static final int S_NEWS = 0xE9;
	public static final int S_KEY = 0xEB;
	public static final int S_EMOTION = 0xED;
	public static final int S_MERCENARYEMPLOY = 0xF0;
	public static final int S_ADD_INVENTORY = 0xF5;
	public static final int S_PARALYSE = 0xF9;
	public static final int S_REMOVE_OBJECT = 0xFA;
	public static final int S_NEW_ACCOUNT_CHECK = 0xFB;
	public static final int S_SPEED = 0xFC;
	public static final int S_MESSAGE_CODE = 0xFE;
	public static final int S_BREATH = 0xFF;

	
	public static final byte[] VERSIONBYTES_HEAD = {
			(byte) 0x35, (byte) 0x03,
			(byte) 0xC4, (byte) 0x8E, (byte) 0x03, (byte) 0x09, 
			(byte) 0xC4, (byte) 0x8E, (byte) 0x03, (byte) 0x09, 
			(byte) 0x7D, (byte) 0xD6, (byte) 0x1B, (byte) 0x78, 
			(byte) 0xC4, (byte) 0x8E, (byte) 0x03, (byte) 0x09,
			(byte) 0x27, (byte) 0x07, (byte) 0x83, (byte) 0x56,
			(byte) 0x00, (byte) 0x00, (byte) 0x00, 
			(byte) 0x8B, (byte) 0xFD, (byte) 0xFF, (byte) 0x34, 
		};

		public static final byte[] VERSIONBYTES_TAIL = {
			(byte) 0xFC, (byte) 0xCB, (byte) 0x01, (byte) 0x09, 
			(byte) 0xDD, (byte) 0xC5, (byte) 0xF5, (byte) 0x08, 
			(byte) 0x1C, (byte) 0x91, (byte) 0x03, (byte) 0x09, 
		};
		
		/** 160810100 **/
		public static final byte[] S_KEYBYTES = {
			(byte) (S_KEY & 0xff),
			(byte) 0x52, (byte) 0x1c, (byte) 0x6e, (byte) 0x45,
			(byte) 0xd5, (byte) 0x97, (byte) 0xe6, (byte) 0x26
		  };


		public static long getSeed(){
			long seed = S_KEYBYTES[1] & 0xff;
			seed |= ((S_KEYBYTES[2] << 8) & 0xff00);
			seed |= ((S_KEYBYTES[3] << 16) & 0xff0000);
			seed |= ((S_KEYBYTES[4] << 24) & 0xff000000);
			
			return seed;
		}
	private Opcodes() { }
}