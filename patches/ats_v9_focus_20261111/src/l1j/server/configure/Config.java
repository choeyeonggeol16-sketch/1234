/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.configure;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.Template.JsonUtil;
import l1j.server.configure.Annotations.Configure;

public final class Config {
	private static final Logger _log = Logger.getLogger(Config.class.getName());
	
	private static final String EVENT = "./config/linall.properties.xml";
	 
	public static boolean CHARACTER_CHECK_SYSTEM;

	public static boolean 킬_데스_멘트;
	
	public static int MAX_CLAN_MEMBER;
	public static boolean CLAN_ALLIANCE;
	public static boolean STANDBY_SERVER = false;
    public static double IMMNUE_TO_HARM_RATE;
 
	public static boolean CHAR_BALANCE_USE;
    
	public static String SERVER_NAME;
	public static int DODGEHIT;
	public static int AINBLESS;
	public static int LIMITLEVEL;
	public static int 교환레벨;
	public static int ALT_ClanLevel;
	public static int ALT_DROPLEVELLIMIT;
	
	public static int ALT_MAILBOXLEVEL;
	public static int ALT_RESULTLEVEL;
	public static int ALT_TEL_DELAY;
	
	public static boolean ACCOUNT_CLAN_JOIN;
	public static String ALT_ITEM_DELETION_TYPE;
	public static String DeleteTimeMent;
	
	public static int ROOMTIS_PROTECT_ENCHANT_LEVEL;
	public static int SNAPPER_PROTECT_ENCHANT_LEVEL;
	
	public static int Hour;
	public static int Minute;
	public static int Second;
	
	public static boolean 길팅체크;
	public static int 무기인첸트;
	public static int 방어구인첸트;
	public static int 고대_서_갑옷_인챈트;
	public static int 고대_서_무기_인챈트;
	public static int 고대_서_갑옷_인챈트_확률;
	public static int 고대_서_무기_인챈트_확률;
	
	public static int 악세사리;
	public static int 룸티스_악세사리_인챈트;
	public static int 스냅퍼_악세사리_인챈트;
	public static int 바포메트_시스템_보호_레벨;
	
	//TODO Synchronization.json
	public static ServerInfo ServerInfo = new ServerInfo();
	public static class ServerInfo{
		public boolean WALK_SPEED_CHECK;
		public int WALK_SPEED_OVERCOUNT;
		public int WALK_SPEED_CHECKTYPE;
		public boolean MAGIC_SPEED_CHECK;
		public int MAGIC_SPEED_OVERCOUNT;
		public boolean MAGIC_SPEED_LOGG;
		public int MAGIC_SPEED_CHECKTYPE;
		public boolean ATTACK_SPEED_PARSE;
		public boolean ITEMOPTIONMSG;
		public boolean ITEMOPTION;
		public boolean WantedSYM;
		public int[] AUTO_POTION_LIST;
		
		
		/** 서버 버그 체크용 **/
		public ServerInfo() {
			WALK_SPEED_CHECK						= false;
			WALK_SPEED_OVERCOUNT					= 5;
			WALK_SPEED_CHECKTYPE					= 3;
			MAGIC_SPEED_CHECK						= false;
			MAGIC_SPEED_OVERCOUNT					= 5;
			MAGIC_SPEED_CHECKTYPE					= 2;
			MAGIC_SPEED_LOGG						= false;
			ATTACK_SPEED_PARSE						= false;
			ITEMOPTIONMSG							= true; 
			WantedSYM								= true;
			AUTO_POTION_LIST						= new int[] { 40010, 40019, 40029, 40022, 40011, 40020, 40023, 40012, 40021,
																  40024, 40930, 40506, 40026, 40026, 40027, 40028, 40058, 40071, 
																  40734, 140010, 240010, 140011, 140012, 140506, 40858, 40043, 
																  41298, 41299, 41300, 41301 };
			
		}
	}
  
	
	/** Debug/release mode */
	public static final boolean DEBUG = false;
	
	
	public static int ALLOW_MAX_PACKET_SIZE;
	
	public static boolean DENY_NON_LC_CLIENT;

	/** Thread pools size */
	public static int THREAD_P_EFFECTS;

	public static int THREAD_P_GENERAL;

	public static int AI_MAX_THREAD;

	public static int THREAD_P_TYPE_GENERAL;
	
	public static int THREAD_F_TYPE_GENERAL;
	
	public static int THREAD_P_SIZE_GENERAL;
	
	public static int SELECT_THREAD_COUNT;

	/** Server control */
	public static String GAME_SERVER_HOST_NAME;

	public static int GAME_SERVER_PORT;
	
	public static int SCHEDULED_CORE_POOLSIZE;
	public static int SCHEDULED_USER_POOLSIZE;
	
	public static int AD_REPORT_SERVER_PORT;

	public static String DB_DRIVER;

	public static String DB_URL;

	public static String DB_LOGIN;

	public static String DB_PASSWORD;

	public static String TIME_ZONE;

	public static boolean HOSTNAME_LOOKUPS;

	public static int AUTOMATIC_KICK;
	
	public static int WHOIS_CONTER;

	public static boolean AUTO_CREATE_ACCOUNTS;

	public static short MAX_ONLINE_USERS;

	public static boolean CACHE_MAP_FILES;

	public static boolean LOAD_V2_MAP_FILES;

	public static short INJUSTICE_COUNT;

	public static int JUSTICE_COUNT;

	public static int CHECK_STRICTNESS;

	public static byte LOGGING_WEAPON_ENCHANT;

	public static byte LOGGING_ARMOR_ENCHANT;

	public static boolean LOGGING_CHAT_NORMAL;

	public static boolean LOGGING_CHAT_WHISPER;

	public static boolean LOGGING_CHAT_SHOUT;

	public static boolean LOGGING_CHAT_WORLD;

	public static boolean LOGGING_CHAT_CLAN;

	public static boolean LOGGING_CHAT_PARTY;

	public static boolean LOGGING_CHAT_COMBINED;

	public static boolean LOGGING_CHAT_CHAT_PARTY;

	public static int AUTOSAVE_INTERVAL;

	public static int AUTOSAVE_INTERVAL_INVENTORY;

	public static boolean TELNET_SERVER;

	public static int TELNET_SERVER_PORT;

	public static int PC_RECOGNIZE_RANGE;

	public static boolean CHARACTER_CONFIG_IN_SERVER_SIDE;

	public static boolean ALLOW_2PC;

	public static int LEVEL_DOWN_RANGE;
	
	public static boolean DETECT_DB_RESOURCE_LEAKS;
	
	public static int ACCOUNT_LIMIT; //## A62 IP당 계정 생성 제한
	
	public static int GM_PASSWORD;
	public static int RANKING_LEVEL;

	/** Rate control */
	
	public static int GOGO_LEVEL;
	
	public static double RATE_XP;	
	
	public static double RATE_PET_XP; //## A137 펫 경험치 배율 설정 외부화 [넬] 

	public static double RATE_LA;

	public static double RATE_KARMA;

	public static double RATE_DROP_ADENA;

	public static double RATE_DROP_ITEMS;
 

	public static double RATE_WEIGHT_LIMIT;

	public static double RATE_WEIGHT_LIMIT_PET;

	public static double RATE_SHOP_SELLING_PRICE;

	public static double RATE_SHOP_PURCHASING_PRICE;
	
	public static int RATE_PREMIUM_TIME;

	public static int RATE_PREMIUM_NUMBER;
	
	/** AltSettings control */
	public static short GLOBAL_CHAT_LEVEL;

	public static byte AUTO_LOOT;

	public static int LOOTING_RANGE;

	public static boolean ALT_NONPVP;

	public static boolean ALT_ATKMSG;

	public static boolean CHANGE_TITLE_BY_ONESELF;

	public static int MAX_PT;

	public static boolean SIM_WAR_PENALTY;

	public static boolean GET_BACK;

	public static int ALT_ITEM_DELETION_TIME;

	public static int ALT_ITEM_DELETION_RANGE;

	public static boolean ALT_GMSHOP;

	public static int ALT_GMSHOP_MIN_ID;

	public static int ALT_GMSHOP_MAX_ID;

//## [A152] 기본 마을 설정
	public static boolean ALT_BASETOWN;

	public static int ALT_BASETOWN_MIN_ID;

	public static int ALT_BASETOWN_MAX_ID;

//## [A152] 기본 마을 설정
	public static boolean ALT_HALLOWEENIVENT;

	public static boolean ALT_WHO_COMMAND;
	
	public static int ATTR_ENCHANT_CHANCE;
	public static int ATTR_ENCHANT_CHANCE_TWO;
	public static int ATTR_ENCHANT_CHANCE_TREE;
	
	public static int WORLD_MSG_ENCHANT_WEAPON;
	public static int WORLD_MSG_ENCHANT_ARMOR;
	public static int WORLD_MSG_ENCHANT_ACCESS;

	public static int ALT_WAR_TIME;

	public static int ALT_WAR_TIME_UNIT;

	public static int ALT_WAR_INTERVAL;

	public static int ALT_WAR_INTERVAL_UNIT;

	public static int ALT_RATE_OF_DUTY;
	
	public static int ALT_WAR_LEVEL;
	
	public static int ALT_WAR_USER_COUNT;

	public static boolean SPAWN_HOME_POINT;

	public static int SPAWN_HOME_POINT_RANGE;

	public static int SPAWN_HOME_POINT_COUNT;

	public static int SPAWN_HOME_POINT_DELAY;

	public static boolean INIT_BOSS_SPAWN;

	public static int ELEMENTAL_STONE_AMOUNT;

	public static int HOUSE_TAX_INTERVAL;

	public static int MAX_DOLL_COUNT;

	public static boolean RETURN_TO_NATURE; 

	public static double GMCODE; //## A66 소스 제공 : (달) 운영자 코드 외부화 위해 

	/** CharSettings control */
	public static int PRINCE_MAX_HP;

	public static int PRINCE_MAX_MP;

	public static int KNIGHT_MAX_HP;

	public static int KNIGHT_MAX_MP;

	public static int ELF_MAX_HP;

	public static int ELF_MAX_MP;

	public static int WIZARD_MAX_HP;

	public static int WIZARD_MAX_MP;

	public static int DARKELF_MAX_HP;

	public static int DARKELF_MAX_MP;
	
	public static String GAME_MASTER_STUN_RND_TIME;
	
	public static String STUN_SKILL_RND_TIME;
	
	public static String EMP_SKILL_RND_TIME;
	
	public static String EARTH_TIME;
	
		
	/** 데이터베이스 풀 관련 */
	public static int min;
	public static int max;
	public static boolean run;

	/** Configuration files */
	public static final String SERVER_CONFIG_FILE = "./config/server.properties";

	public static final String RATES_CONFIG_FILE = "./config/rates.properties";

	public static final String ALT_SETTINGS_FILE = "./config/altsettings.properties";

	public static final String CHAR_SETTINGS_CONFIG_FILE = "./config/charsettings.properties";

	// NPC로부터 들이마실 수 있는 MP한계
	public static final int MANA_DRAIN_LIMIT_PER_NPC = 40;

	// NPC로부터 들이마실 수 있는 HP한계
	public static final int HEALTH_DRAIN_LIMIT_PER_NPC = 40;


	public static int 버닝_스피릿츠_발동_확률;
	public static int 더블_브레이크_발동_확률;
	public static int 엘리멘탈_파이어_발동_확률;
	public static int 카운터_배리어_발동_확률;	
	public static int 카운터_미러_발동_확률;
	public static int 인챈트_베놈_발동_확률;

    public static double 아머_브레이크;
    
    public static int 장인_무기_마법_주문서_인챈트_제한;
    public static int 장인_무기_마법_주문서_인챈트_맥스_제한;
    public static int 장인_갑옷_마법_주문서_인챈트_제한;
    public static int 장인_갑옷_마법_주문서_인챈트_맥스_제한;
    
	public static boolean 신비한_날개_깃털;

	public static void load() {
	//	_log.info("loading gameserver config");
		
		try {
			ServerInfo = JsonUtil.fromFile("./config/ServerInfo.json", ServerInfo.class);
		} catch (Exception e) {
			System.out.println("Config->ServerInfo.json 에서 에러가 발생했습니다.");
			e.printStackTrace();
			throw new Error("Failed to Load ./config/ServerInfo.json File.");
		}
		
		// server.properties
		try {
			new ConfigLoader().load(Config.class);
			Properties serverSettings = new Properties();
			InputStream is = new FileInputStream(new File(SERVER_CONFIG_FILE));
			serverSettings.load(is);
			is.close();
			
			
			SCHEDULED_CORE_POOLSIZE = Integer.parseInt(serverSettings.getProperty("SCHEDULEDCOREPOOLSIZE", "512"));
			SCHEDULED_USER_POOLSIZE = Integer.parseInt(serverSettings.getProperty("SCHEDULEDUSERPOOLSIZE", "10"));
			/** 데이터 베이스 풀 */
			min = Integer.parseInt(serverSettings.getProperty("min"));
			max = Integer.parseInt(serverSettings.getProperty("max"));
			run = Boolean.parseBoolean(serverSettings.getProperty("run"));

			CHARACTER_CHECK_SYSTEM = Boolean.parseBoolean(serverSettings.getProperty("CHARACTER_CHECK_SYSTEM", "true"));
			
			
			SERVER_NAME = new String(serverSettings.getProperty("ServerName").getBytes("ISO-8859-1"), "euc-kr");
			GAME_SERVER_HOST_NAME = serverSettings.getProperty("GameserverHostname", "*");
			GAME_SERVER_PORT = Integer.parseInt(serverSettings.getProperty("GameserverPort", "2000"));
			AD_REPORT_SERVER_PORT = Integer.parseInt(serverSettings.getProperty("AdReportServerPort", "18182"));
			DB_DRIVER = serverSettings.getProperty("Driver", "com.mysql.jdbc.Driver");
			ALLOW_MAX_PACKET_SIZE = Integer.parseInt(serverSettings.getProperty("MaxPacketSize", "1"));
			DENY_NON_LC_CLIENT = Boolean.parseBoolean(serverSettings.getProperty("DenyNonLCClient", "true"));
			DB_URL = serverSettings.getProperty("URL","jdbc:mysql://localhost/l1jdb? useUnicode=true&characterEncoding=euckr");
			DB_LOGIN = serverSettings.getProperty("Login", "root");
			DB_PASSWORD = serverSettings.getProperty("Password", "");
			THREAD_P_TYPE_GENERAL = Integer.parseInt(serverSettings.getProperty("GeneralThreadPoolType", "0"), 10);
			THREAD_P_SIZE_GENERAL = Integer.parseInt(serverSettings.getProperty("GeneralThreadPoolSize", "0"), 10);
	
			SELECT_THREAD_COUNT = Integer.parseInt(serverSettings.getProperty("IoThreadPoolSize", "4"));
			TIME_ZONE = serverSettings.getProperty("TimeZone", "KST");
			HOSTNAME_LOOKUPS = Boolean.parseBoolean(serverSettings.getProperty("HostnameLookups", "false"));
			AUTOMATIC_KICK = Integer.parseInt(serverSettings.getProperty("AutomaticKick", "10"));
			AUTO_CREATE_ACCOUNTS = Boolean.parseBoolean(serverSettings.getProperty("AutoCreateAccounts", "true"));
			MAX_ONLINE_USERS = Short.parseShort(serverSettings.getProperty("MaximumOnlineUsers", "30"));
			CACHE_MAP_FILES = Boolean.parseBoolean(serverSettings.getProperty("CacheMapFiles", "false"));
			LOAD_V2_MAP_FILES = Boolean.parseBoolean(serverSettings.getProperty("LoadV2MapFiles", "false"));
		
			INJUSTICE_COUNT = Short.parseShort(serverSettings.getProperty("InjusticeCount", "10"));
			JUSTICE_COUNT = Integer.parseInt(serverSettings.getProperty("JusticeCount", "4"));
			CHECK_STRICTNESS = Integer.parseInt(serverSettings.getProperty("CheckStrictness", "102"));
			LOGGING_WEAPON_ENCHANT = Byte.parseByte(serverSettings.getProperty("LoggingWeaponEnchant", "0"));
			LOGGING_ARMOR_ENCHANT = Byte.parseByte(serverSettings.getProperty("LoggingArmorEnchant", "0"));
			LOGGING_CHAT_NORMAL = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatNormal", "false"));
			LOGGING_CHAT_WHISPER = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatWhisper", "false"));
			LOGGING_CHAT_SHOUT = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatShout", "false"));
			LOGGING_CHAT_WORLD = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatWorld", "false"));
			LOGGING_CHAT_CLAN = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatClan", "false"));
			LOGGING_CHAT_PARTY = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatParty", "false"));
			LOGGING_CHAT_COMBINED = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatCombined", "false"));
			LOGGING_CHAT_CHAT_PARTY = Boolean.parseBoolean(serverSettings.getProperty("LoggingChatChatParty", "false"));
			AUTOSAVE_INTERVAL = Integer.parseInt(serverSettings.getProperty("AutosaveInterval", "1200"), 10);
			AUTOSAVE_INTERVAL_INVENTORY = Integer
					.parseInt(serverSettings.getProperty("AutosaveIntervalOfInventory", "300"), 10);
			
			TELNET_SERVER = Boolean.parseBoolean(serverSettings.getProperty("TelnetServer", "false"));
			TELNET_SERVER_PORT = Integer.parseInt(serverSettings.getProperty("TelnetServerPort", "23"));
			PC_RECOGNIZE_RANGE = Integer.parseInt(serverSettings.getProperty("PcRecognizeRange", "20"));
			CHARACTER_CONFIG_IN_SERVER_SIDE = Boolean
					.parseBoolean(serverSettings.getProperty("CharacterConfigInServerSide", "true"));
			ALLOW_2PC = Boolean.parseBoolean(serverSettings.getProperty("Allow2PC", "true"));
			LEVEL_DOWN_RANGE = Integer.parseInt(serverSettings.getProperty("LevelDownRange", "0"));
			DETECT_DB_RESOURCE_LEAKS = Boolean.parseBoolean(serverSettings.getProperty("EnableDatabaseResourceLeaksDetection","false"));
			ACCOUNT_LIMIT = Integer.parseInt(serverSettings.getProperty( // ## A62 IP당 계정 생성 제한
					"AccountLimit", "1")); // #
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			throw new Error("Failed to Load " + SERVER_CONFIG_FILE + " File.");
		}

		// rates.properties
		try {
			Properties rateSettings = new Properties();
			InputStream is = new FileInputStream(new File(RATES_CONFIG_FILE));
			rateSettings.load(is);
			is.close();
			카운터_배리어_발동_확률 = Integer.parseInt(rateSettings.getProperty("Counter_Barrier_Rate", "20"));
		    장인_무기_마법_주문서_인챈트_제한 = Integer.parseInt(rateSettings.getProperty("master_Weapon_Scroll_Ent", "0"));
		    장인_무기_마법_주문서_인챈트_맥스_제한 = Integer.parseInt(rateSettings.getProperty("master_Weapon_Scroll_Ent_Max", "10"));
		    장인_갑옷_마법_주문서_인챈트_제한 = Integer.parseInt(rateSettings.getProperty("master_Armor_Scroll_Ent", "0"));
		    장인_갑옷_마법_주문서_인챈트_맥스_제한 = Integer.parseInt(rateSettings.getProperty("master_Armor_Scroll_Ent_Max", "10"));
		    
			버닝_스피릿츠_발동_확률 = Integer.parseInt(rateSettings.getProperty("Burbing_Spirit_Rate", "30"));
			더블_브레이크_발동_확률 = Integer.parseInt(rateSettings.getProperty("Double_Brake_Rate", "30"));
			엘리멘탈_파이어_발동_확률 = Integer.parseInt(rateSettings.getProperty("Elemental_Fire_Rate", "30"));		
			카운터_미러_발동_확률 = Integer.parseInt(rateSettings.getProperty("Counter_Mirror_Rate", "30"));
			인챈트_베놈_발동_확률 = Integer.parseInt(rateSettings.getProperty("Enchant_Venom_Rate", "10"));
			GOGO_LEVEL = Integer.parseInt(rateSettings.getProperty("Golevel", "62"));
			RATE_XP = Double.parseDouble(rateSettings.getProperty("RateXp", "1.0"));
			RATE_PET_XP = Double.parseDouble(rateSettings.getProperty("RatePetXp","1.0")); // #
			RATE_LA = Double.parseDouble(rateSettings.getProperty("RateLawful", "1.0"));
			RATE_KARMA = Double.parseDouble(rateSettings.getProperty("RateKarma", "1.0"));
			RATE_DROP_ADENA = Double.parseDouble(rateSettings.getProperty("RateDropAdena", "1.0"));
			RATE_DROP_ITEMS = Double.parseDouble(rateSettings.getProperty("RateDropItems", "1.0"));
			
			ATTR_ENCHANT_CHANCE = Integer.parseInt(rateSettings.getProperty("AttrEnchantChance", "30"));
			ATTR_ENCHANT_CHANCE_TWO = Integer.parseInt(rateSettings.getProperty("AttrEnchantChance_Two", "10"));
			ATTR_ENCHANT_CHANCE_TREE = Integer.parseInt(rateSettings.getProperty("AttrEnchantChance_Tree", "5"));
		
			WORLD_MSG_ENCHANT_WEAPON = Integer.parseInt(rateSettings.getProperty("WorldMsgEnchantWeapon", "15"));
			WORLD_MSG_ENCHANT_ARMOR = Integer.parseInt(rateSettings.getProperty("WorldMsgEnchantArmor", "15"));
			WORLD_MSG_ENCHANT_ACCESS = Integer.parseInt(rateSettings.getProperty("WorldMsgEnchantAccess", "15"));		
			
			RATE_WEIGHT_LIMIT = Double.parseDouble(rateSettings.getProperty("RateWeightLimit", "1"));
			RATE_WEIGHT_LIMIT_PET = Double.parseDouble(rateSettings.getProperty("RateWeightLimitforPet", "1"));
			RATE_SHOP_SELLING_PRICE = Double.parseDouble(rateSettings.getProperty("RateShopSellingPrice", "1.0"));
			RATE_SHOP_PURCHASING_PRICE = Double.parseDouble(rateSettings.getProperty("RateShopPurchasingPrice", "1.0"));
			RATE_PREMIUM_TIME = Integer.parseInt(rateSettings.getProperty("RatePremiumTime", "12")); // #
			RATE_PREMIUM_NUMBER = Integer.parseInt(rateSettings.getProperty("RatePremiumNumber", "6"));
			신비한_날개_깃털 = Boolean.parseBoolean(rateSettings.getProperty("OccultFeather", "false"));
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			throw new Error("Failed to Load " + RATES_CONFIG_FILE + " File.");
		}

		// altsettings.properties
		try {
			Properties altSettings = new Properties();
			InputStream is = new FileInputStream(new File(ALT_SETTINGS_FILE));
			altSettings.load(is);
			is.close();
			
			IMMNUE_TO_HARM_RATE = Double.parseDouble(altSettings.getProperty("IMMUNEDMG", "0.5"));

			
			킬_데스_멘트 = Boolean.parseBoolean(altSettings.getProperty("KillDeathMent", "false"));
			아머_브레이크 = Double.parseDouble(altSettings.getProperty("ARMOR_BRAKE", "1.58"));
			DODGEHIT = Integer.parseInt(altSettings.getProperty("DodGehit", "20"));
			AINBLESS = Integer.parseInt(altSettings.getProperty("Ain_bless", "0"));
 
			Hour = Integer.parseInt(altSettings.getProperty("hour", "9"));
			Minute = Integer.parseInt(altSettings.getProperty("minute", "0"));
			Second = Integer.parseInt(altSettings.getProperty("second", "0"));
			
			
			교환레벨 = Integer.parseInt(altSettings.getProperty("TradeLevel", "5"));
			ALT_ClanLevel = Integer.parseInt(altSettings.getProperty("altclanlevel", "55"));
			ALT_DROPLEVELLIMIT = Integer.parseInt(altSettings.getProperty("DropLevelLimit", "90"));
			
			ALT_MAILBOXLEVEL = Integer.parseInt(altSettings.getProperty("MailBoxLevel", "5"));
			
			CHAR_BALANCE_USE = Boolean.parseBoolean(altSettings.getProperty("CharBalanceUse", "false"));
	
		
			ACCOUNT_CLAN_JOIN = Boolean.parseBoolean(altSettings.getProperty("AccClanJoin", "true"));
			
			ALT_RESULTLEVEL = Integer.parseInt(altSettings.getProperty("ReusltLevel", "90"));
			WHOIS_CONTER = Integer.parseInt(altSettings.getProperty("WhoisConter", "0"));
			GLOBAL_CHAT_LEVEL = Short.parseShort(altSettings.getProperty("GlobalChatLevel", "1"));
			AUTO_LOOT = Byte.parseByte(altSettings.getProperty("AutoLoot", "0"));
			LOOTING_RANGE = Integer.parseInt(altSettings.getProperty("LootingRange", "3"));
			ALT_NONPVP = Boolean.parseBoolean(altSettings.getProperty("NonPvP", "true"));
			ALT_ATKMSG = Boolean.parseBoolean(altSettings.getProperty("AttackMessageOn", "true"));
			CHANGE_TITLE_BY_ONESELF = Boolean.parseBoolean(altSettings.getProperty("ChangeTitleByOneself", "false"));
			MAX_CLAN_MEMBER = Integer.parseInt(altSettings.getProperty("MaxClanMember", "200"));
			CLAN_ALLIANCE = Boolean.parseBoolean(altSettings.getProperty("ClanAlliance", "true"));
			MAX_PT = Integer.parseInt(altSettings.getProperty("MaxPT", "8"));
			SIM_WAR_PENALTY = Boolean.parseBoolean(altSettings.getProperty("SimWarPenalty", "true"));
			GET_BACK = Boolean.parseBoolean(altSettings.getProperty("GetBack", "false"));
 
			ALT_ITEM_DELETION_TYPE = altSettings.getProperty("ItemDeletionType", "auto");
			ALT_ITEM_DELETION_TIME = Integer.parseInt(altSettings.getProperty("ItemDeletionTime", "10"));
			ALT_ITEM_DELETION_RANGE = Integer.parseInt(altSettings.getProperty("ItemDeletionRange", "5"));
			 
			
			DeleteTimeMent = new String(altSettings.getProperty("DeleMent").getBytes("ISO-8859-1"), "euc-kr");
			
			
			ALT_GMSHOP = Boolean.parseBoolean(altSettings.getProperty("GMshop", "false"));
			ALT_GMSHOP_MIN_ID = Integer.parseInt(altSettings.getProperty("GMshopMinID", "0xffffffff")); // 취득 실패시는 무효
			ALT_GMSHOP_MAX_ID = Integer.parseInt(altSettings.getProperty("GMshopMaxID", "0xffffffff")); // 취득 실패시는 무효
//## [A152] 기본 마을 설정
			ALT_BASETOWN = Boolean.parseBoolean(altSettings.getProperty("BaseTown", "false"));
			ALT_BASETOWN_MIN_ID = Integer.parseInt(altSettings.getProperty("BaseTownMinID", "0xffffffff")); // 취득 실패시는
																											// 무효
			ALT_BASETOWN_MAX_ID = Integer.parseInt(altSettings.getProperty("BaseTownMaxID", "0xffffffff")); // 취득 실패시는
																											// 무효

			ALT_WHO_COMMAND = Boolean.parseBoolean(altSettings.getProperty("WhoCommand", "false"));
			
			
			길팅체크 = Boolean.parseBoolean(altSettings.getProperty("RiltingCheck", "false"));
			
			무기인첸트 = Integer.parseInt(altSettings.getProperty("LimitWeapon", "13")); 
			방어구인첸트 = Integer.parseInt(altSettings.getProperty("LimitArmor", "11")); 
			고대_서_갑옷_인챈트_확률 = Integer.parseInt(altSettings.getProperty("AncientArmorEnChace", "5")); 
			고대_서_무기_인챈트_확률 = Integer.parseInt(altSettings.getProperty("AncientWeaponEnChace", "5"));
			고대_서_갑옷_인챈트 = Integer.parseInt(altSettings.getProperty("AncientArmorEnMax", "10")); 
			고대_서_무기_인챈트 = Integer.parseInt(altSettings.getProperty("AncientWeaponEnMax", "11"));
			악세사리 = Integer.parseInt(altSettings.getProperty("Accessory", "8"));
			룸티스_악세사리_인챈트 = Integer.parseInt(altSettings.getProperty("RoomtisMaxEnchant", "8"));
			스냅퍼_악세사리_인챈트 = Integer.parseInt(altSettings.getProperty("SnapperMaxEnchant", "8"));
			

			String strWar;
			strWar = altSettings.getProperty("WarTime", "2h");
			if (strWar.indexOf("d") >= 0) {
				ALT_WAR_TIME_UNIT = Calendar.DATE;
				strWar = strWar.replace("d", "");
			} else if (strWar.indexOf("h") >= 0) {
				ALT_WAR_TIME_UNIT = Calendar.HOUR_OF_DAY;
				strWar = strWar.replace("h", "");
			} else if (strWar.indexOf("m") >= 0) {
				ALT_WAR_TIME_UNIT = Calendar.MINUTE;
				strWar = strWar.replace("m", "");
			}
			ALT_WAR_TIME = Integer.parseInt(strWar);
			strWar = altSettings.getProperty("WarInterval", "4d");
			if (strWar.indexOf("d") >= 0) {
				ALT_WAR_INTERVAL_UNIT = Calendar.DATE;
				strWar = strWar.replace("d", "");
			} else if (strWar.indexOf("h") >= 0) {
				ALT_WAR_INTERVAL_UNIT = Calendar.HOUR_OF_DAY;
				strWar = strWar.replace("h", "");
			} else if (strWar.indexOf("m") >= 0) {
				ALT_WAR_INTERVAL_UNIT = Calendar.MINUTE;
				strWar = strWar.replace("m", "");
			}
			ALT_WAR_INTERVAL = Integer.parseInt(strWar);
			
			ALT_RATE_OF_DUTY = Integer.parseInt(altSettings.getProperty("TaxRate", "10"));
			ALT_WAR_LEVEL = Integer.parseInt(altSettings.getProperty("WarLevel", "60"));
			ALT_WAR_USER_COUNT = Integer.parseInt(altSettings.getProperty("WarPlayer", "1"));
			
			SPAWN_HOME_POINT = Boolean.parseBoolean(altSettings.getProperty("SpawnHomePoint", "true"));
			SPAWN_HOME_POINT_COUNT = Integer.parseInt(altSettings.getProperty("SpawnHomePointCount", "2"));
			SPAWN_HOME_POINT_DELAY = Integer.parseInt(altSettings.getProperty("SpawnHomePointDelay", "100"));
			SPAWN_HOME_POINT_RANGE = Integer.parseInt(altSettings.getProperty("SpawnHomePointRange", "8"));
			INIT_BOSS_SPAWN = Boolean.parseBoolean(altSettings.getProperty("InitBossSpawn", "true"));
			ELEMENTAL_STONE_AMOUNT = Integer.parseInt(altSettings.getProperty("ElementalStoneAmount", "300"));
			HOUSE_TAX_INTERVAL = Integer.parseInt(altSettings.getProperty("HouseTaxInterval", "10"));
			MAX_DOLL_COUNT = Integer.parseInt(altSettings.getProperty("MaxDollCount", "1"));
			GMCODE = Double.parseDouble(altSettings.getProperty( // ## A66 소스 제공 : (달) 운영자 코드 외부화 위해
					"gmcode", "200")); // #
			RETURN_TO_NATURE = Boolean.parseBoolean(altSettings.getProperty("ReturnToNature", "false"));
			RANKING_LEVEL = Integer.parseInt(altSettings.getProperty("RanKingLevel", "0"));
			바포메트_시스템_보호_레벨 = Integer.parseInt(altSettings.getProperty("ProtectLevel", "0"));
			GM_PASSWORD = Integer.parseInt(altSettings.getProperty("GmPassword", "0"));
			ALT_TEL_DELAY = Integer.parseInt(altSettings.getProperty("TellDelay", "0"));
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			throw new Error("Failed to Load " + ALT_SETTINGS_FILE + " File.");
		}

		// charsettings.properties
		try {
			Properties charSettings = new Properties();
			InputStream is = new FileInputStream(new File(CHAR_SETTINGS_CONFIG_FILE));
			charSettings.load(is);
			is.close();
	
			PRINCE_MAX_HP = Integer.parseInt(charSettings.getProperty("PrinceMaxHP", "1000"));
			PRINCE_MAX_MP = Integer.parseInt(charSettings.getProperty("PrinceMaxMP", "800"));
			KNIGHT_MAX_HP = Integer.parseInt(charSettings.getProperty("KnightMaxHP", "1400"));
			KNIGHT_MAX_MP = Integer.parseInt(charSettings.getProperty("KnightMaxMP", "600"));
			ELF_MAX_HP = Integer.parseInt(charSettings.getProperty("ElfMaxHP", "1000"));
			ELF_MAX_MP = Integer.parseInt(charSettings.getProperty("ElfMaxMP", "900"));
			WIZARD_MAX_HP = Integer.parseInt(charSettings.getProperty("WizardMaxHP", "800"));
			WIZARD_MAX_MP = Integer.parseInt(charSettings.getProperty("WizardMaxMP", "1200"));
			DARKELF_MAX_HP = Integer.parseInt(charSettings.getProperty("DarkelfMaxHP", "1000"));
			DARKELF_MAX_MP = Integer.parseInt(charSettings.getProperty("DarkelfMaxMP", "900"));
			
			LIMITLEVEL = Integer.parseInt(charSettings.getProperty("LimitLevel", "99"));
			GAME_MASTER_STUN_RND_TIME = charSettings.getProperty("GM_StunSkillRndTime", "1000,1400,1800,2200,2600,3000,3400,3800");
			STUN_SKILL_RND_TIME = charSettings.getProperty("StunSkillRndTime", "1000,1400,1800,2200,2600,3000,3400,3800");
			
			EMP_SKILL_RND_TIME = charSettings.getProperty("EmpSkillRndTime", "1000,1400,1800,2200,2600,3000,3400,3800");
			
			EARTH_TIME = charSettings.getProperty("EarthTime", "3000,4000,5000,6000,7000,8000,9000,10000,11000,12000");
			
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			throw new Error("Failed to Load " + CHAR_SETTINGS_CONFIG_FILE + " File.");
		}
	}

	public static boolean setParameterValue(String pName, String pValue) {
		// server.properties
		if (pName.equalsIgnoreCase("GameserverHostname")) {
			GAME_SERVER_HOST_NAME = pValue;
		} else if (pName.equalsIgnoreCase("ServerName")) {
			try {
				SERVER_NAME = new String(pValue.getBytes("ISO-8859-1"), "euc-kr");
			} catch (UnsupportedEncodingException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		} else if (pName.equalsIgnoreCase("GameserverPort")) {
			GAME_SERVER_PORT = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("Driver")) {
			DB_DRIVER = pValue;
		} else if (pName.equalsIgnoreCase("URL")) {
			DB_URL = pValue;
		} else if (pName.equalsIgnoreCase("Login")) {
			DB_LOGIN = pValue;
		} else if (pName.equalsIgnoreCase("Password")) {
			DB_PASSWORD = pValue;
		} else if (pName.equalsIgnoreCase("TimeZone")) {
			TIME_ZONE = pValue;
		} else if (pName.equalsIgnoreCase("AutomaticKick")) {
			AUTOMATIC_KICK = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("AutoCreateAccounts")) {
			AUTO_CREATE_ACCOUNTS = Boolean.parseBoolean(pValue);
		} else if (pName.equalsIgnoreCase("MaximumOnlineUsers")) {
			MAX_ONLINE_USERS = Short.parseShort(pValue);
		} else if (pName.equalsIgnoreCase("LoggingWeaponEnchant")) {
			LOGGING_WEAPON_ENCHANT = Byte.parseByte(pValue);
		} else if (pName.equalsIgnoreCase("LoggingArmorEnchant")) {
			LOGGING_ARMOR_ENCHANT = Byte.parseByte(pValue);
		} else if (pName.equalsIgnoreCase("CharacterConfigInServerSide")) {
			CHARACTER_CONFIG_IN_SERVER_SIDE = Boolean.parseBoolean(pValue);
		} else if (pName.equalsIgnoreCase("Allow2PC")) {
			ALLOW_2PC = Boolean.parseBoolean(pValue);
		} else if (pName.equalsIgnoreCase("LevelDownRange")) {
			LEVEL_DOWN_RANGE = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("AccountLimit")) { //## A62 IP당 계정 생성 제한
			ACCOUNT_LIMIT = Integer.parseInt(pValue); //#
		} else if (pName.equalsIgnoreCase("WhoisCount")) {
			WHOIS_CONTER = Integer.valueOf(pValue);
		}
		// rates.properties
		else if (pName.equalsIgnoreCase("RateXp")) {
			RATE_XP = Double.parseDouble(pValue);
		} else if (pName.equalsIgnoreCase("Golevel")) {
			GOGO_LEVEL = Integer.parseInt(pValue);
			 
		} else if (pName.equalsIgnoreCase("RatePetXp")) { //## A137 펫 경험치 배율 설정 외부화 [넬] 
			RATE_PET_XP = Double.parseDouble(pValue); //#
		} else if (pName.equalsIgnoreCase("RateLawful")) {
			RATE_LA = Double.parseDouble(pValue);
		} else if (pName.equalsIgnoreCase("RateKarma")) {
			RATE_KARMA = Double.parseDouble(pValue);
		} else if (pName.equalsIgnoreCase("RateDropAdena")) {
			RATE_DROP_ADENA = Double.parseDouble(pValue);
		} else if (pName.equalsIgnoreCase("RateDropItems")) {
			RATE_DROP_ITEMS = Double.parseDouble(pValue);
		} else if (pName.equalsIgnoreCase("Weightrate")) {
			RATE_WEIGHT_LIMIT = Byte.parseByte(pValue);
		}
		// altsettings.properties
		else if (pName.equalsIgnoreCase("GlobalChatLevel")) {
			GLOBAL_CHAT_LEVEL = Short.parseShort(pValue);
		} else if (pName.equalsIgnoreCase("CharBalanceUse")) {
			CHAR_BALANCE_USE = Boolean.valueOf(pValue);		 
		} else if (pName.equalsIgnoreCase("AutoLoot")) {
			AUTO_LOOT = Byte.parseByte(pValue);
		} else if (pName.equalsIgnoreCase("LOOTING_RANGE")) {
			LOOTING_RANGE = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("AltNonPvP")) {
			ALT_NONPVP = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("AttackMessageOn")) {
			ALT_ATKMSG = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("ChangeTitleByOneself")) {
			CHANGE_TITLE_BY_ONESELF = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("MaxClanMember")) {
			MAX_CLAN_MEMBER = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("ClanAlliance")) {
			CLAN_ALLIANCE = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("MaxPT")) {
			MAX_PT = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("SimWarPenalty")) {
			SIM_WAR_PENALTY = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("GetBack")) {
			GET_BACK = Boolean.valueOf(pValue);
 
		} else if (pName.equalsIgnoreCase("GMshop")) {
			ALT_GMSHOP = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("GMshopMinID")) {
			ALT_GMSHOP_MIN_ID = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("GMshopMaxID")) {
			ALT_GMSHOP_MAX_ID = Integer.parseInt(pValue);
//## [A152] 기본 마을 설정
		} else if (pName.equalsIgnoreCase("IMMUNEDMG")) {
			IMMNUE_TO_HARM_RATE = Double.parseDouble(pValue);
			
		} else if (pName.equalsIgnoreCase("BaseTown")) {
			ALT_BASETOWN = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("BaseTownMinID")) {
			ALT_BASETOWN_MIN_ID = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("BaseTownMaxID")) {
			ALT_BASETOWN_MAX_ID = Integer.parseInt(pValue);
//## [A152] 기본 마을 설정
		} else if (pName.equalsIgnoreCase("HalloweenIvent")) {
			ALT_HALLOWEENIVENT = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("HouseTaxInterval")) {
			HOUSE_TAX_INTERVAL = Integer.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("MaxDollCount")) {
			MAX_DOLL_COUNT = Integer.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("Gmcode")) {
			GMCODE = Integer.parseInt(pValue); //#
		} else if (pName.equalsIgnoreCase("ReturnToNature")) {
			RETURN_TO_NATURE = Boolean.valueOf(pValue);
		} //#
		// charsettings.properties
		else if (pName.equalsIgnoreCase("PrinceMaxHP")) {
			PRINCE_MAX_HP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("PrinceMaxMP")) {
			PRINCE_MAX_MP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("KnightMaxHP")) {
			KNIGHT_MAX_HP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("KnightMaxMP")) {
			KNIGHT_MAX_MP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("ElfMaxHP")) {
			ELF_MAX_HP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("ElfMaxMP")) {
			ELF_MAX_MP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("WizardMaxHP")) {
			WIZARD_MAX_HP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("WizardMaxMP")) {
			WIZARD_MAX_MP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("DarkelfMaxHP")) {
			DARKELF_MAX_HP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("DarkelfMaxMP")) {
			DARKELF_MAX_MP = Integer.parseInt(pValue);
		} else if (pName.equalsIgnoreCase("LimitLevel")) {
			LIMITLEVEL = Integer.parseInt(pValue);
		} else {
			return false;
		}
		return true;
	}
	 
	private Config() {
	}
	
	
	public final static int etc_arrow = 0;

	public final static int etc_wand = 1;

	public final static int etc_light = 2;

	public final static int etc_gem = 3;

	public final static int etc_potion = 6;

	public final static int etc_firecracker = 5;

	public final static int etc_food = 7;

	public final static int etc_scroll = 8;

	public final static int etc_questitem = 9;

	public final static int etc_spellbook = 10;

	public final static int etc_other = 12;

	public final static int etc_material = 13;

	public final static int etc_sting = 15;

	public final static int etc_treasurebox = 16;


	
	private static Properties Settings = new Properties();
	
	public static String getString(String key) {
		return getString(key, "");
	}
	public static String getString(String key, String defaultValue) {
		return Settings.getProperty(key, defaultValue);
	}
	public static String[] getStringArray(String key, String defaultValue) {
		String value = Settings.getProperty(key, defaultValue);
		String[] result = null;
		
		// , 가 없으면.. 1배열
		if (value.indexOf(",") == -1) result = new String[] { value };
		else result = value.split(",");
		
		return result;
	}
	public static int getInt(String key) {
		return getInt(key, -1);
	}
	public static int getInt(String key, int defaultValue) {
		return Integer.parseInt(Settings.getProperty(key, Integer.toString(defaultValue)));
	}
	public static long getLong(String key) {
		return getLong(key, -1);
	}
	public static long getLong(String key, long defaultValue) {
		return Long.parseLong(Settings.getProperty(key, Long.toString(defaultValue)));
	}
	public static boolean getBoolean(String key) {
		return getBoolean(key, false);
	}
	public static boolean getBoolean(String key, boolean defaultValue) {
		return Boolean.parseBoolean(Settings.getProperty(key, Boolean.toString(defaultValue)));
	}
	public static float getFloat(String key, float defaultValue) {
		return Float.parseFloat(Settings.getProperty(key, Float.toString(defaultValue)));
	}
	public static double getDouble(String key, double defaultValue) {
		return Double.parseDouble(Settings.getProperty(key, Double.toString(defaultValue)));
	}
	
	
	/** 컨피그시작새로설정중 **/
	/** Events control */
		  
	@Configure(file = EVENT, key = "RankGahoItem")
	public static boolean RANK_GAHO_ITEM = true;
	
	@Configure(file = EVENT, key = "RankGiveItem")
	public static boolean RANK_GIVE_ITEM = true;
	  
	@Configure(file = EVENT, key = "RankGiveItemId")
	public static String RANK_GIVE_ITEM_ID = "40308,40308,40308";
	
	@Configure(file = EVENT, key = "RankGiveItemCount")
	public static String RANK_GIVE_ITEM_COUNT = "1,1,1";
	
	
	@Configure(file = EVENT, key = "AdenShopLevel") 
	public static int ADENASHOP_LEVEL = 70;
	
	@Configure(file = EVENT, key = "AdenShopsLevel") 
	public static int ADENASHOPS_LEVEL = 70;
	
	@Configure(file = EVENT, key = "MaxSell_Adena") 
	public static int MAX_SELL_ADENA = 10;
	
	@Configure(file = EVENT, key = "MinSell_Adena") 
	public static int MIN_SELL_ADENA = 1;
	
	@Configure(file = EVENT, key = "MinSell_Cash") 
	public static int MIN_SELL_CASH = 30000;
	
	@Configure(file = EVENT, key = "MaxSell_Cash") 
	public static int MAX_SELL_CASH = 10000000;
	
	@Configure(file = EVENT, key = "Adena_Check_Item") 
	public static int ADENA_CHECK_ITEM;
	
	@Configure(file = EVENT, key = "Adena_Check_Item_Count") 
	public static int ADENA_CHECK_ITEM_COUNT;

	@Configure(file = EVENT, key = "Cash_ment")
	public static String CASHMENT = "최저아데나시세는 1억당 5천원입니다";
	
	
	@Configure(file = EVENT, key = "Master_lista")
	public static String MASTER_LISTA = "9999";
	
	@Configure(file = EVENT, key = "Master_listq")
	public static String MASTER_LISTQ = "9999";
	
	@Configure(file = EVENT, key = "MasterPolyId")
	public static int MASTER_POLYID = 0;
	
	
	@Configure(file = EVENT, key = "Sell_Aden_Cash")
	public static int SELLADENCASH = 1000000;
	
	@Configure(file = EVENT, key = "Sell_Item_Cash")
	public static int SELLITEMCASH = 1000000;
	 
	@Configure(file = EVENT, key = "Quiz_sym")
	public static boolean QUIZSYM = false;
	
	@Configure(file = EVENT, key = "AUTO_SYM")
	public static boolean AUTOSYM = true;
	
	@Configure(file = EVENT, key = "Npc_Max")
	public static int NPCMAX = 60;
	
	@Configure(file = EVENT, key = "Npc_Location")
	public static int NPCLOCATION = 10;
	
	
}