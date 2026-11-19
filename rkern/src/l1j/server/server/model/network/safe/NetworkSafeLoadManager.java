package l1j.server.server.model.network.safe;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import l1j.server.Config;
import l1j.server.server.Opcodes;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.MJCommons;

public class NetworkSafeLoadManager {

	private NetworkSafeLoadManager() {}

	private static final class Holder {
		private static final NetworkSafeLoadManager INSTANCE = new NetworkSafeLoadManager();
	}

	public static NetworkSafeLoadManager getInstance() {
		return Holder.INSTANCE;
	}

	public static void commands(L1PcInstance gm, String param){
		try{
			ArrayDeque<Integer> argsQ = MJCommons.parseToIntQ(param);
			if(argsQ == null || argsQ.isEmpty())
				throw new Exception("");

			switch(argsQ.poll()){
			case 2:
				getInstance().reload();
				SC_SystemMessage.send(gm, "[MJNetSafe System Config reload completed.]");
				break;
			default:
				throw new Exception("");	
			}

		}catch(Exception e){
			SC_SystemMessage.send(gm, "사용방법 .넷세이프 [1. *][2. 리로드]");
		}
	}

	public void load(){
		loadConfig();
	}
	public void reload(){
		loadConfig();
	}

	public void release(){
		STATE.set(State.empty());
	}

	private static final int FILTER_NONE        = 0x00;
	private static final int FILTER_BYPASS      = 0x01; // 1
	private static final int FILTER_STANDBY     = 0x02; // 2
	private static final int FILTER_PARALYZED   = 0x04; // 4
	private static final int FILTER_NONMOVABLE  = 0x08; // 8
	private static final int FILTER_CHARNAME    = 0x10; // 16
	private static final int FILTER_TELEPORTING = 0x20; // 32 
	

	public static final int RESULT_BYPASS		= 0x01;
	public static final int RESULT_NORMAL		= 0x02;
	public static final int RESULT_FILTER		= 0x04;

	// =========================
	// State (single byte[] table)
	// =========================
	private static final int TABLE_SIZE = 256;
	private static final byte UNKNOWN = (byte) 0xFF; // 등록 안된 opcode => FILTER

	private static final class State {
		final byte[] filters; // length 256
		State(byte[] filters) { this.filters = filters; }

		static State empty() {
			return new State(newUnknownTable());
		}
	}

	private static final AtomicReference<State> STATE = new AtomicReference<>(State.empty());

	private static byte[] newUnknownTable() {
		final byte[] t = new byte[TABLE_SIZE];
		for (int i = 0; i < t.length; i++) t[i] = UNKNOWN;
		return t;
	}

	private static final String CONFIG_PATH = "./config/mjnetsafe.properties";

	// 기본값(초기 미설정 대비)
	private static final int DEF_CLIENT_VERSION       = 1704061101;
	private static final int DEF_AUTH_VERSION         = 2015090301;
	private static final int DEF_CACHE_VERSION        = 151112700;
	private static final int DEF_TAM_VERSION          = 161031701;
	private static final int DEF_ARCA_VERSION         = 161111700;
	private static final int DEF_HIBREED_VERSION      = 1701031002;
	private static final int DEF_ARENACO_VERSION      = 160531700;
	private static final int DEF_CLIENT_SETTING_SW    = 889191819;


	public static int isFilter(CIOClient clnt, int op){
	    final byte[] filters = STATE.get().filters;

	    // 1. 등록되지 않은 옵코드인가?
	    if (op < 0 || op >= filters.length) {
	        return RESULT_FILTER;
	    }

	    final byte raw = filters[op];
	    if (raw == UNKNOWN) {
	        return RESULT_FILTER;
	    }

	    final int flg = raw & 0xFF;

	    // 바이패스(로그인 등)는 무조건 통과
	    if ((flg & FILTER_BYPASS) != 0) return RESULT_BYPASS;

	    // 오픈대기 체크 
	    if ((flg & FILTER_STANDBY) != 0 && Config.STANDBY_SERVER) {
	        return RESULT_FILTER;
	    }

	    if (clnt == null) return RESULT_FILTER;
	    L1PcInstance pc = clnt.getActiveChar();
	    if (pc == null) return RESULT_NORMAL;

	    // 이름 변경 중 체크
	    if ((flg & FILTER_CHARNAME) != 0 && pc.isChangechaname()) {
	        return RESULT_FILTER;
	    }
	    
	    // 이동중 불가
	    if ((flg & FILTER_NONMOVABLE) != 0 && CommonUtil.isnonMoveSkill(pc)) {
	    	 return RESULT_FILTER;
	    }

	    // 마비 상태 체크
	    if ((flg & FILTER_PARALYZED) != 0 && (pc.isParalyzed() || pc.isSleeped())) {
	        return RESULT_FILTER;
	    }

	    // 텔레포트 상태 체크 
	    if ((flg & FILTER_TELEPORTING) != 0 && pc.get_teleport()) {
	        return RESULT_FILTER; 
	    }
    
	    return RESULT_NORMAL;
	}

	// =========================
	// Registered opcodes (exactly same meaning as your many put(...) calls)
	// =========================
	private static final int[] REGISTERED_OPS = new int[] {
			Opcodes.C_ARCHERARRANGE,
			Opcodes.C_SAY,
			Opcodes.C_GOTO_MAP,
			Opcodes.C_GIVE,
			Opcodes.C_TELL,
			Opcodes.C_TAX,
			Opcodes.C_BOARD_LIST,
			Opcodes.C_CHAT_PARTY_CONTROL,
			Opcodes.C_PLATE,
			Opcodes.C_HYPERTEXT_INPUT_RESULT,
			Opcodes.C_TELEPORT,
			Opcodes.C_WANTED,
			Opcodes.C_SAVEIO,
			Opcodes.C_BUYABLE_SPELL,
			Opcodes.C_GOTO_PORTAL,
			Opcodes.C_MERCENARYEMPLOY,
			Opcodes.C_SHIFT_SERVER,
			Opcodes.C_SMS,
			Opcodes.C_ONOFF,
			Opcodes.C_LOGIN_RESULT,
			Opcodes.C_HACTION,
			Opcodes.C_FIXABLE_ITEM,
			Opcodes.C_LOGIN_TEST,
			Opcodes.C_MONITOR_CONTROL,
			Opcodes.C_WHO,
			Opcodes.C_VOICE_CHAT,
			Opcodes.C_CHANGE_DIRECTION,
			Opcodes.C_FIX,
			Opcodes.C_MAIL,
			Opcodes.C_NEW_ACCOUNT,
			Opcodes.C_ALT_ATTACK,
			Opcodes.C_JOIN_PLEDGE,
			Opcodes.C_RESTART,
			Opcodes.C_TITLE,
			Opcodes.C_ENTER_PORTAL,
			Opcodes.C_SAVE,
			Opcodes.C_ACTION,
			Opcodes.C_CREATE_CUSTOM_CHARACTER,
			Opcodes.C_PLEDGE_WATCH,
			Opcodes.C_CHECK_PK,
			Opcodes.C_USE_SPELL,
			Opcodes.C_QUERY_PERSONAL_SHOP,
			Opcodes.C_ENTER_WORLD,
			Opcodes.C_CHANGE_ACCOUNTINFO,
			Opcodes.C_EXIT_GHOST,
			Opcodes.C_ENTER_SHIP,
			Opcodes.C_CANCEL_XCHG,
			Opcodes.C_SILENCE,
			Opcodes.C_CHECK_INVENTORY,
			Opcodes.C_RETURN_SUMMON,
			Opcodes.C_TELEPORT_USER,
			Opcodes.C_ANSWER,
			Opcodes.C_CHAT,
			Opcodes.C_MERCENARYARRANGE,
			Opcodes.C_INVITE_PARTY_TARGET,
			Opcodes.C_EXTENDED,
			Opcodes.C_BANISH_PARTY,
			Opcodes.C_THROW,
			Opcodes.C_ATTACK,
			Opcodes.C_INCLUDE,
			Opcodes.C_BOARD_DELETE,
			Opcodes.C_USE_ITEM,
			Opcodes.C_SELECT_TIME,
			Opcodes.C_EXCHANGEABLE_SPELL,
			Opcodes.C_WITHDRAW,
			Opcodes.C_WAREHOUSE_CONTROL,
			Opcodes.C_MERCENARYSELECT,
			Opcodes.C_MOVE,
			Opcodes.C_ADD_XCHG,
			Opcodes.C_KICK,
			Opcodes.C_WAR,
			Opcodes.C_START_CASTING,
			Opcodes.C_GET,
			Opcodes.C_BUILDER_CONTROL,
			Opcodes.C_LEAVE_PLEDGE,
			Opcodes.C_ADDR,
			Opcodes.C_EXCHANGE_SPELL,
			Opcodes.C_PERSONAL_SHOP,
			Opcodes.C_SLAVE_CONTROL,
			Opcodes.C_BLINK,
			Opcodes.C_ATTACK_CONTINUE,
			Opcodes.C_WHO_PARTY,
			Opcodes.C_REQUEST_ROLL,
			Opcodes.C_EMBLEM,
			Opcodes.C_CLIENT_READY,
			Opcodes.C_QUERY_BUDDY,
			Opcodes.C_OPEN,
			Opcodes.C_BOOK,
			Opcodes.C_SUMMON,
			Opcodes.C_BOOKMARK,
			Opcodes.C_BOARD_WRITE,
			Opcodes.C_INVITE_PARTY,
			Opcodes.C_CREATE_PLEDGE,
			Opcodes.C_DROP,
			Opcodes.C_CONTROL_WEATHER,
			Opcodes.C_LOGIN,
			Opcodes.C_ADD_BUDDY,
			Opcodes.C_LEAVE_PARTY,
			Opcodes.C_DESTROY_ITEM,
			Opcodes.C_SERVER_SELECT,
			Opcodes.C_SHUTDOWN,
			Opcodes.C_DUEL,
			Opcodes.C_REMOVE_BUDDY,
			Opcodes.C_WHO_PLEDGE,
			Opcodes.C_CHANNEL,
			Opcodes.C_LOGOUT,
			Opcodes.C_REGISTER_QUIZ,
			Opcodes.C_BAN,
			Opcodes.C_EXTENDED_HYBRID,
			Opcodes.C_MARRIAGE,
			Opcodes.C_BAN_MEMBER,
			Opcodes.C_BUY_SELL,
			Opcodes.C_BOARD_READ,
			Opcodes.C_CHANGE_PASSWORD,
			Opcodes.C_WISH,
			Opcodes.C_SELECTABLE_TIME,
			Opcodes.C_UPLOAD_EMBLEM,
			Opcodes.C_DEPOSIT,
			Opcodes.C_QUERY_CASTLE_SECURITY,
			Opcodes.C_DELETE_BOOKMARK,
			Opcodes.C_ASK_XCHG,
			Opcodes.C_READ_NEWS,
			Opcodes.C_ACCEPT_XCHG,
			Opcodes.C_READ_NOTICE,
			Opcodes.C_ALIVE,
			Opcodes.C_EXCLUDE,
			Opcodes.C_DELETE_CHARACTER,
			Opcodes.C_DIALOG,
			Opcodes.C_EXTENDED_PROTOBUF,
			Opcodes.C_BUY_SPELL,
			Opcodes.C_QUIT,
			Opcodes.C_MATCH_MAKING,
			Opcodes.C_FAR_ATTACK,
			Opcodes.C_CHANGE_CASTLE_SECURITY,
			Opcodes.C_DEAD_RESTART,
			Opcodes.C_NPC_ITEM_CONTROL,
			Opcodes.C_RANK_CONTROL,
		    Opcodes.C_VERSION,        
		    Opcodes.C_MERCENARYNAME,   
	};

	public static int NS_CLIENT_VERSION;
	public static int NS_AUTHSERVER_VERSION;
	public static int NS_CACHESERVER_VERSION;
	public static int NS_TAMSERVER_VERSION;
	public static int NS_ARCASERVER_VERSION;
	public static int NS_HIBREED_INTERSERVER_VERSION;
	public static int NS_ARENACOSERVER_VERSION;
	public static int NS_CLIENT_SETTING_SWITCH;

	private static void loadConfig(){
		try{
			// 1) next table: unknown default
			final byte[] next = newUnknownTable();

			// 2) register base ops as FILTER_NONE (known)
			for (int opcode : REGISTERED_OPS) {
				put(next, opcode, FILTER_NONE);
			}

			// 무시할 패킷 옵코드
			setOpFlag(next, new int[] {
					//Opcodes.C_ALIVE,
					Opcodes.C_CLIENT_READY,
					Opcodes.C_SHIFT_SERVER,
					Opcodes.C_TELL,
					Opcodes.C_ENTER_PORTAL,
			}, FILTER_BYPASS);

			// 오픈대기시 무시 패킷 옵코드
			setOpFlag(next, new int[] {
					Opcodes.C_ALIVE,
					Opcodes.C_CLIENT_READY,
					Opcodes.C_SHIFT_SERVER,
					Opcodes.C_TELL,
			}, FILTER_STANDBY);

			// 굳어있을 경우 무시할 패킷 옵코드
			setOpFlag(next, new int[] {
					Opcodes.C_ATTACK,
					Opcodes.C_ASK_XCHG,
					Opcodes.C_ACCEPT_XCHG,
					Opcodes.C_DROP,
					Opcodes.C_GET,
					Opcodes.C_FAR_ATTACK,
					Opcodes.C_GIVE,
					Opcodes.C_USE_SPELL,
					Opcodes.C_RESTART,
					Opcodes.C_ADD_XCHG,
					Opcodes.C_LOGOUT,
			}, FILTER_PARALYZED);

			// 이동 불가 시 무시할 패킷 옵코드
			setOpFlag(next, new int[] {
					Opcodes.C_DROP,
					Opcodes.C_GET,
					Opcodes.C_GIVE,
					Opcodes.C_RESTART,
					Opcodes.C_MOVE,
			}, FILTER_NONMOVABLE);


			// 이름 변경중인경우 무시할 패킷 옵코드
			setOpFlag(next, new int[] {
					Opcodes.C_DROP,
					Opcodes.C_GET,
					Opcodes.C_GIVE,
					Opcodes.C_RESTART,
					Opcodes.C_ATTACK,
					Opcodes.C_FAR_ATTACK,
					Opcodes.C_USE_SPELL,
					Opcodes.C_LOGOUT,
					Opcodes.C_RESTART,
					Opcodes.C_MOVE,
					Opcodes.C_TELEPORT,
					Opcodes.C_TELL,
					Opcodes.C_USE_ITEM,
					Opcodes.C_RETURN_SUMMON,
					Opcodes.C_ATTACK_CONTINUE,
					Opcodes.C_BUY_SELL
			}, FILTER_CHARNAME);

			setOpFlag(next, new int[] {
					Opcodes.C_MOVE,
					Opcodes.C_ATTACK,
					Opcodes.C_ATTACK_CONTINUE,
					Opcodes.C_FAR_ATTACK,

					Opcodes.C_USE_ITEM,
					Opcodes.C_USE_SPELL,

			}, FILTER_TELEPORTING);
			

			// properties (로컬 변수로 읽고 마지막 반영)
			final Properties settings = new Properties();
			try (InputStream is = new FileInputStream(CONFIG_PATH)) {
				settings.load(is);
			} catch (Exception ignore) {
				// 파일 없어도 운영상 defaults/기존값으로 동작 가능해야 함.
			}

			// 기존값이 이미 세팅되어 있으면 그걸 def로 사용
	
			final int defClientVersion       = (NS_CLIENT_VERSION != 0) ? NS_CLIENT_VERSION : DEF_CLIENT_VERSION;
			final int defAuthVersion         = (NS_AUTHSERVER_VERSION != 0) ? NS_AUTHSERVER_VERSION : DEF_AUTH_VERSION;
			final int defCacheVersion        = (NS_CACHESERVER_VERSION != 0) ? NS_CACHESERVER_VERSION : DEF_CACHE_VERSION;
			final int defTamVersion          = (NS_TAMSERVER_VERSION != 0) ? NS_TAMSERVER_VERSION : DEF_TAM_VERSION;
			final int defArcaVersion         = (NS_ARCASERVER_VERSION != 0) ? NS_ARCASERVER_VERSION : DEF_ARCA_VERSION;
			final int defHibreedInterVersion = (NS_HIBREED_INTERSERVER_VERSION != 0) ? NS_HIBREED_INTERSERVER_VERSION : DEF_HIBREED_VERSION;
			final int defArenacoVersion      = (NS_ARENACOSERVER_VERSION != 0) ? NS_ARENACOSERVER_VERSION : DEF_ARENACO_VERSION;
			final int defClientSettingSwitch = (NS_CLIENT_SETTING_SWITCH != 0) ? NS_CLIENT_SETTING_SWITCH : DEF_CLIENT_SETTING_SW;

			final int clientVersion       = parseInt(settings.getProperty("NetSafe_ClientVersion"), defClientVersion);
			final int authVersion         = parseInt(settings.getProperty("NetSafe_AuthVersion"), defAuthVersion);
			final int cacheVersion        = parseInt(settings.getProperty("NetSafe_GlobalCacheVersion"), defCacheVersion);
			final int tamVersion          = parseInt(settings.getProperty("NetSafe_TamServerVersion"), defTamVersion);
			final int arcaVersion         = parseInt(settings.getProperty("NetSafe_ArcaServerVersion"), defArcaVersion);
			final int hibreedInterVersion = parseInt(settings.getProperty("NetSafe_HibreedInterServerVersion"), defHibreedInterVersion);
			final int arenacoVersion      = parseInt(settings.getProperty("NetSafe_ArenacoServerVersion"), defArenacoVersion);
			final int clientSettingSwitch = parseInt(settings.getProperty("NetSafe_ClientSettingSwitch"), defClientSettingSwitch);

			//  Commit
			// - 필터 먼저 스왑 (패킷 처리 즉시 반영)
			STATE.set(new State(next));

			NS_CLIENT_VERSION = clientVersion;
			NS_AUTHSERVER_VERSION = authVersion;
			NS_CACHESERVER_VERSION = cacheVersion;
			NS_TAMSERVER_VERSION = tamVersion;
			NS_ARCASERVER_VERSION = arcaVersion;
			NS_HIBREED_INTERSERVER_VERSION = hibreedInterVersion;
			NS_ARENACOSERVER_VERSION = arenacoVersion;
			NS_CLIENT_SETTING_SWITCH = clientSettingSwitch;

		}catch(Exception e){
			e.printStackTrace();
		}
	}

	// 등록 (unknown -> known)
	private static void put(byte[] filters, int opcode, int flag) {
		if (opcode < 0 || opcode >= filters.length) return;
		filters[opcode] = (byte) (flag & 0xFF);
	}

	// 등록된 opcode만 OR
	private static void setOpFlag(byte[] filters, int[] list, int flg) {
		for (int opcode : list) {
			if (opcode < 0 || opcode >= filters.length) continue;
			final byte cur = filters[opcode];
			if (cur == UNKNOWN) continue;
			filters[opcode] = (byte) (((cur & 0xFF) | (flg & 0xFF)) & 0xFF);
		}
	}

	private static int parseInt(String s, int def) {
		try {
			if (s == null) return def;
			s = s.trim();
			if (s.isEmpty()) return def;
			return Integer.parseInt(s);
		} catch (Exception e) {
			return def;
		}
	}
}
