package l1j.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.model.world.instance.inn.InnHelper;
import l1j.server.server.shared.PropertyReader;
import l1j.server.server.shared.arrangement.ArrangeParseeFactory;
import l1j.server.server.shared.arrangement.ArrangeParser;
import l1j.server.server.utils.GameUtils;
import l1j.server.server.utils.HexHelper;
import l1j.server.server.utils.IntRange;

public final class Config {
 
	public static boolean USE_ATTENDANCE_SYSTEM;
	public static String 랭킹멘트;
	public static boolean 멘트온오프;
	public static boolean USE_ACTION_TIME_LOGGING;
	public static boolean USE_POTION_EFFECT_LOGGIN;
	public static int 랭킹순위;
	public static int 몹레벨; 
	public static boolean 봉인템삭제;
	public static boolean 품질시스템사용;
	
	public static boolean BOSS_DROP_GROUND_ENABLE;
	public static int PARTY_LOOT_ELIGIBILITY;
	public static int PARTY_LOOT_RANGE;
	public static int PARTY_LOOT_MODE;
	public static boolean PARTY_LOOT_ENABLE;
	
	/** 캐릭정보 자동저장 시스템 **/ 
	public static int 혈맹포인트최소;
	public static int 혈맹포인트최대;
	public static int 혈맹버프지속시간;
	public static int 혈맹버프회복;
	public static int 방어구비법서확률;
	public static int AUTOSAVE_INTERVAL;
	public static int AUTOSAVE_INTERVAL_INVENTORY;
	/** 캐릭정보 자동저장 시스템 **/
	public static int 포스스턴레벨;

	public static boolean 인형소환유무;
	public static int 자동사냥맵번호;
	public static int 자동사냥맵x;
	public static int 자동사냥맵y;
	public static int 매직실드대기;
	public static String 서버점검맨트;
	public static int 아이템1등;
	public static int 아이템2등;
	public static int 아이템3등;
	
	public static int 무기안전0_1차;
	public static int 무기안전0_2차;
	public static int 무기안전0_3차;
	
	public static int 맵1번x좌표;
	public static int 맵1번y좌표;
	public static int 맵1번map좌표;
	public static int 맵2번x좌표;
	public static int 맵2번y좌표;
	public static int 맵2번map좌표;
	public static int 맵3번x좌표;
	public static int 맵3번y좌표;
	public static int 맵3번map좌표;
	
	public static int 맥스인첸1차;
	public static int 맥스인첸2차;
	public static int 맥스인첸3차;
	
	
	public static int 아이템4등;
	public static int 아이템5등;
	public static int 최대지급;
	public static int 지급아이템;
	public static int 개당지급;
	public static String 버프번호;
	
	//해상전 배 액션
	public static int seadunship;
	public static int seadunshippass;
	
	//로테던전
	public static int 잊섬오픈;
	public static int 거무오픈;
	public static int 라스타오픈;
	public static int 지배오픈;
	public static int 풍둥오픈;
	public static int 오설오픈;
	public static int 화둥오픈;
	public static int 용계오픈;
	public static int 테베오픈;
	public static int 배알림;
	public static int 하이네낚시;
	public static int 네쥬던전초기화;
	public static int 네쥬던전체킹;
	public static int DungeonResetHour;   // [던전초기화 시각 외부화] altsettings: DungeonResetHour
	public static int DungeonResetMinute; // [던전초기화 시각 외부화] altsettings: DungeonResetMinute
	
	public static int 랭킹아이템1등;
	public static int 랭킹아이템2등;
	public static int 랭킹아이템3등;
	public static int 랭킹아이템4등;
	public static int 랭킹아이템5등;
	public static int 랭킹아이템6등;
	public static int 랭킹아이템7등;
	public static int 랭킹아이템8등;
	public static int 랭킹아이템9등;
	public static int 랭킹아이템10등;
	
	public static int 랭킹아이템순위;

	
	
	public static String 공지제목;
	public static String 공지1번줄;
	public static String 공지2번줄;
	public static String 공지3번줄;
	public static String 공지4번줄;
	public static String 공지5번줄;
	public static String 공지6번줄;
	public static String 공지7번줄;
	public static String 공지8번줄;
	public static String 공지9번줄;
	
	public static int 매직실드지속;
	public static int 매직실드확률;
	public static String 운영자이벤트맵;
	public static String 변신몹아이디;
	public static String 라던펑몹;
	public static String 이벤트이름;
	public static boolean 운영자이벤트;
	public static boolean AUTO_GM_SHOP;
	public static int 운영자이벤트강한확률;
	public static int 운영자이벤트약한확률;
	public static int 운영자이벤트아이템;
	
	public static int qqq;
	public static int 스턴최소;
	public static int 네쥬스턴최소;
	public static int 네쥬스턴최대;
	public static int 맥스레벨;
	public static int 데페최소;
	public static int \uC9C0\uAE09\uC728;
	public static int 엠파최소;
	public static int 본브최소;
	public static int 포우최소;
	public static int 라우풀1;
	public static int 라우풀2;
	public static int 라우풀3;
	public static int 라우풀4;
	public static int 라우풀5;
	public static int 라우풀6;
	public static int 라우풀7;
	public static int 라우풀8;
	public static int accessorytest1;
	public static int 디케이포션;
	public static int 에테리얼랜덤;
	public static double 에테리얼수치;
	public static String 엠파이어오버로드;
	public static int 투견최소랜덤데미지;
	public static int 투견최대랜덤데미지;
	public static double 카배회복량;
	public static double 아머데미지;
	public static double 아머데미지타인;
	public static int 아인고통인챈;
	public static int 아인고통기본;
	public static String 아인고통스턴;
	public static double 투견공속;
	public static double 버닝샷대미지;
	public static int 버닝샷대기;
	public static int 버닝샷;
	public static int 버닝샷칸;
	public static int 이뮨투함블레이드양;
	public static int 이뮨투함블레이드음;
	public static int 게일샷;
	public static int 게일샷확률;
	public static int 게일샷몹;
	public static double 게일디버프;
	public static int 이뮨투함블레이드군주피;
	public static int 이뮨투함블레이드기사피;
	public static int 이뮨투함블레이드요정피;
	public static int 이뮨투함블레이드법사피;
	public static int 이뮨투함블레이드다엘피;
	public static int 이뮨투함블레이드용기사피;
	public static int 이뮨투함블레이드환술피;
	public static int 이뮨투함블레이드전사피;
	
	public static int 이뮨투함블레이드군주확률;
	public static int 이뮨투함블레이드기사확률;
	public static int 이뮨투함블레이드요정활확률;
	public static int 이뮨투함블레이드요정검확률;
	public static int 이뮨투함블레이드법사확률;
	public static int 이뮨투함블레이드다엘확률;
	public static int 이뮨투함블레이드용기사확률;
	public static int 이뮨투함블레이드환술확률;
	public static int 이뮨투함블레이드전사확률;
	
	public static int 악세축복1단;
	public static int 악세축복2단;
	public static int 악세축복3단;
	public static int 악세축복4단;
	public static int 악세축복5단;
	
	public static double 부여주문서1단;
	public static double 부여주문서2단;
	public static double 부여주문서3단;
	

	public static String clanraidmob;
	public static String clanraidmob2;
	public static String clanraidmob3;
	public static String clanraidmob4;
	public static String clanraidmob5;
	public static String clanraidmobboss;
	public static String mobboss;
	public static int boss1;
	public static int boss2;
	public static int boss3;
	public static int boss4;
	public static int boss5;
	public static int boss6;
	public static int boss7;
	public static int boss8;
	public static int boss9;
	public static int boss10;

	
	public static int warwinid;
	public static int warid;
	public static int warwinqty;
	public static int warqty;
	public static int warcrownid;
	public static int warcrownqty;
	public static int warcrownid2;
	public static int warcrownqty2;
	
	public static int dr1id;
	public static int dr2id;
	public static int dr3id;
	public static int dr4id;
	public static int dr5id;
	public static int dr6id;
	public static int dr7id;
	public static int dr8id;
	public static int dr9id;
	public static int dr10id;
	public static int dr11id;
	public static int dr12id;
	public static int dr13id;
	public static int dr14id;
	public static int dr15id;
	public static int dr16id;
	public static int dr17id;
	public static int dr18id;
	public static int dr19id;
	public static int dr20id;
		
	public static int dr1qty;
	public static int dr2qty;
	public static int dr3qty;
	public static int dr4qty;
	public static int dr5qty;
	public static int dr6qty;
	public static int dr7qty;
	public static int dr8qty;
	public static int dr9qty;
	public static int dr10qty;
	public static int dr11qty;
	public static int dr12qty;
	public static int dr13qty;
	public static int dr14qty;
	public static int dr15qty;
	public static int dr16qty;
	public static int dr17qty;
	public static int dr18qty;
	public static int dr19qty;
	public static int dr20qty;
	public static boolean royal;
	public static boolean totomo;
	public static String dr1m;
	public static String dr2m;
	public static String dr3m;
	public static String dr4m;
	public static String dr5m;
	public static String dr6m;
	public static String dr7m;
	public static String dr8m;
	public static String dr9m;
	public static String dr10m;
	public static String dr11m;
	public static String dr12m;
	public static String dr13m;
	public static String dr14m;
	public static String dr15m;
	public static String dr16m;
	public static String dr17m;
	public static String dr18m;
	public static String dr19m;
	public static String dr20m;	
	
	public static int dr1en;	
	public static int dr2en;	
	public static int dr3en;	
	public static int dr4en;	
	public static int dr5en;	
	public static int dr6en;	
	public static int dr7en;	
	public static int dr8en;	
	public static int dr9en;	
	public static int dr10en;	
	public static int dr11en;	
	public static int dr12en;	
	public static int dr13en;	
	public static int dr14en;	
	public static int dr15en;	
	public static int dr16en;	
	public static int dr17en;	
	public static int dr18en;	
	public static int dr19en;	
	public static int dr20en;	

	public static int warwinen;
	public static int waren; 
	
	public static int 이뮨투함블레이드지속;
	public static int 이뮨투함블레이드대기;
	public static int 이뮨투함블레이드레벨;
	public static int 메디테이션엠피;
	public static int 메디테이션엠피1;
	public static int 매스이뮨대기시간;
	public static int 매스이뮨엠피;
	public static int 매스이뮨마돌;
	public static int 비전대기시간;
	public static int 비전엠피;
	public static double 리버레이션절감;
	public static int 리버레이션대기;
	public static int 리버레이션지속;
	public static String STUN_SKILL_RND_TIME11;
	public static String STUN_SKILL_RND_TIME111;
	public static int 데몰리션대미지;
	public static int 데몰리션대기;
	public static int 데몰리션엠피;
	public static double 데페기본확률;
	public static int 어벤저확률;
	public static int 어벤저대기;
	public static int 어벤저배율;
	public static int 어드밴스레벨;
	public static int 어드밴스대기;
	public static int 쉐도우대기;
	public static int 이터니티대기;
	public static int 이터니티배율; 
	public static int 이터기본;
	public static int 이터양;
	public static int 이터음;
	public static int 네메시스기본;
	public static int 네메시스양;
	public static int 네메시스음;
	public static int 네메시스몬스터;
	public static int 쉐도우기본;
	public static int 쉐도우양;
	public static int 쉐도우음;
	public static int 엘븐스트라이크몬스터;
	public static int 엘븐스트라이크대기;
	public static int 엘븐스트라이크기본;
	public static int 엘븐스트라이크양;
	public static int 엘븐스트라이크음;
	public static int 엘븐스트라이크제약;
	public static int 엘븐스트라이크배율;
	
	public static int 할파스근뎀;
	public static int 할파스근명;
	public static int 할파스원뎀;
	public static int 할파스원명;
	public static int 할파스pvp뎀;
	public static int 할파스pvp리덕;
	public static int 할파스맥스피;
	public static int 할파스맥스엠피;
	public static int 할파스스펠;
	public static int 할파스추방;
	public static int 할파스올적중;
	public static int 할파스올레즈;
	public static int 할파스축복효율;
	public static int 할파스추가경험치;
	public static int 할파스마법명중;
	
	public static int 아우라근뎀;
	public static int 아우라근명;
	public static int 아우라원뎀;
	public static int 아우라원명;
	public static int 아우라pvp뎀;
	public static int 아우라pvp리덕;
	public static int 아우라맥스피;
	public static int 아우라맥스엠피;
	public static int 아우라스펠;
	public static int 아우라추방;
	public static int 아우라올적중;
	public static int 아우라올레즈;
	public static int 아우라축복효율;
	public static int 아우라추가경험치;
	public static int 아우라마법명중;
	public static int 드슬근뎀;
	public static int 드슬근명;
	public static int 드슬원뎀;
	public static int 드슬원명;
	public static int 드슬pvp뎀;
	public static int 드슬pvp리덕;
	public static int 드슬맥스피;
	public static int 드슬맥스엠피;
	public static int 드슬스펠;
	public static int 드슬추방;
	public static int 드슬올적중;
	public static int 드슬올레즈;
	public static int 드슬축복효율;
	public static int 드슬추가경험치;
	public static int 드슬마법명중;
	public static int 베히근뎀;
	public static int 베히근명;
	public static int 베히원뎀;
	public static int 베히원명;
	public static int 베히pvp뎀;
	public static int 베히pvp리덕;
	public static int 베히맥스피;
	public static int 베히맥스엠피;
	public static int 베히스펠;
	public static int 베히추방;
	public static int 베히올적중;
	public static int 베히올레즈;
	public static int 베히축복효율;
	public static int 베히추가경험치;
	public static int 베히마법명중;
	
	public static int 이터이차최소;
	public static int 이터이차최대;
	public static int 이터몬스터;
	public static int 쉐도우몬스터;
	public static int 이터제약;
	public static int 쉐도우제약;
	public static int 포텐셜대기시간;
	public static int 할파스확률;
	public static int 토마호크배율;
	public static int 토마호크배율1;
	public static int 버서크시간;
	public static int 버서크대기시간;
	public static int 프라임대기시간;
	public static boolean 낚시이펙트;
	public static boolean 보라채팅1;
	public static boolean 보라채팅2;
	public static boolean 보라채팅3;
	public static boolean 보라채팅4;
	public static boolean 바포시스템;
	public static boolean 스턴멘트;
	public static boolean 혈맹포인트상점멘트;
	public static boolean 혈맹포인트여부;
	public static boolean 혈맹포인트여부2;
	public static boolean 혈맹포인트상점;
	public static boolean 혈맹포인트조회;
	public static String 혈맹포인트맵번호;
	public static int 혈맹포인트카운트;
	public static int 혈맹포인트카운트22;
	public static int 보스한입상자번호;
	public static double 혈맹버프퍼센트;
	public static boolean 자동물약카오;
	//TODO 드랍 멘트 아이템
	public static int 고통최소;
	public static int 고통최대;
	public static int 고통인챈트;
	public static int 고통인챈트트;
	public static int 고통데미지인챈트;
	public static int Yuilper;
	
	public static int 결심최소;
	public static int 결심최대;
	public static int 결심인챈트;
	public static int 결심인챈트트;
	public static int 결심데미지인챈트;
	
	public static int 환상최소;
	public static int 환상최대;
	public static int 환상인챈트;
	public static int 환상인챈트트;
	public static int 환상데미지인챈트;
	
	public static int 공포최소;
	public static int 공포최대;
	public static int 공포인챈트;
	public static int 공포인챈트트;
	public static int 공포데미지인챈트;
	
	public static int 고독최소;
	public static int 고독최대;
	public static int 고독인챈트;
	public static int 고독인챈트트;
	public static int 고독데미지인챈트;
	
	public static HashMap<Integer, Integer> DROPMENT_ITEM = new HashMap<Integer, Integer>(32);
	public static int 수성시간;
	public static int 일반갯수;
	public static int 고급갯수;
	public static int 희귀갯수;
	public static int 영웅갯수;
	public static int 전설갯수;
	//TODO 로봇 군주 자동생성
	public static int 래스트;
	public static int 램페이지확률;
	public static double 램페이지데미지;
	public static boolean 상점버그;
	public static boolean 영자마크;
	public static int 인형디케이;
	public static int 인형pvp감소;
	public static int 인형올스탯;
	public static int 인형피;
	public static int 인형엠피;
	public static int 인형소울;
	public static int 인형이뮨;
	public static int 인형저지먼트;
	public static boolean 클랜이름;
	public static boolean 자동사냥;
	public static boolean 자동물약버프;
	public static boolean Clan_leader;
	public static boolean ALT_FISHEVENT;
	public static boolean LOGIN_ENCRYPTION;
	public static boolean 마일리지지급여부;
	public static boolean 알람멘트;
	public static boolean 성혈버프;
	public static boolean 공성수배;
	public static int 마일리지값;
	
	public static int 불멸가호아덴;
	public static int 불멸가호아덴1;
	public static int 불멸가호아덴2;
	public static int 불멸가호아덴22;
	public static String 마일리지엔피씨;
	public static int Neige_Count_Pass;
	public static int Neige_Myth_Skill;
	
	public static String DESPERADO_SKILL_RND_TIME;
	public static String DESPERADO1_SKILL_RND_TIME;
	public static String POWERRIP_SKILL_RND_TIME;
	public static String GRAB_SKILL_RND_TIME;
	public static String GRABS_SKILL_RND_TIME;
	public static String EMPIRE_SKILL_RND_TIME;
	public static String STUN_SKILL_RND_TIME;
	public static String STUN_SKILL_RND_TIME1;
	public static String 포스스턴;
	public static String 네메시스스턴;
	public static int 포스스턴배율;
	public static int 포스스턴확률;
	public static int 테테;
	public static boolean 지엠전용;
	/**환술사 생성 불가**/
	public static int cantblack;
	/** 캐릭 생성시 시작 레벨 **/
	public static int Start_Char_Level;
	public static boolean FISH_COMS;
	public static int Fishchance;
	public static boolean 캐릭터시작점;
	public static boolean 반딜압류;
	public static int Fishchance1;
	public static int 할파스쿨타임;
	public static int 할파스PVP쿨타임;
	public static int 할파스가호;
	public static int 할파스신의;
	public static int 할파스신의PVP;
	public static int 할파스갑옷확률;
	public static int 아인하사드인챈트;
	public static int 아인하사드인챈트트;
	public static int 아인하사드인챈트트1;
	public static int 아인데미지인챈트;
	public static int 아인데미지인챈트1;
	public static int 그랑데미지인챈트;
	public static int 그랑데미지인챈트1;
	public static int 아인최소;
	public static int 아인최대;
	public static int 아인하사드인챈트1;
	public static int 아인최소1;
	public static int 아인최대1;
	
	public static int 그랑카인인챈트;
	public static int 그랑카인인챈트트;
	public static int 그랑카인인챈트트1;
	public static int 그랑최소;
	public static int 그랑최대;
	public static int 에바인챈트;
	public static int 에바최소;
	public static int 에바최대;
	public static int 에바데미지인챈트;
	
	public static int 에바인챈트1;
	public static int 에바최소1;
	public static int 에바최대1;
	public static int 에바데미지인챈트1;
	
	
	public static int 그랑카인인챈트1;
	public static int 그랑최소1;
	public static int 그랑최대1;
	public static int PER_AC_PC_TO_PC;
	public static int PER_AC_NPC_TO_PC;
	public static int PER_DODGE;
	public static int PER_EVASION;
	public static boolean ItemDrops;
	public static boolean 잠재력활성화 = true;
	public static boolean 잠재력흡수유무;
	public static double 잠재력이뮨;
	public static boolean 잠재력성공;
	public static boolean 잠재력성공1;
	public static boolean 영웅등급멘트;
	public static boolean 전설등급멘트;
	public static int 잠재력없음;
	public static int 잠재력없음일반확률;
	public static int 잠재력없음고급확률;
	public static int 잠재력없음희귀확률;
	public static int 잠재력없음영웅확률;
	public static int 잠재력영웅될확률;
	public static int 잠재력영웅영웅확률;
	public static int 잠재력고급확률;
	public static int 잠재력영웅꺼;
	public static int 잠재력전설1;
	public static int 잠재력고급될확률;
	public static int 잠재력희귀될확률;
	
	public static int 잠재력희귀확률;
	public static int 잠재력희귀희귀확률;
	
	public static int 잠재력영웅확률;
	
	public static int 잠재력저지먼트;
	public static int 잠재력저지먼트1;
	public static int 잠재력저지먼트2;
	public static int 잠재력저지먼트3;
	public static int 잠재력저지먼트4;
	public static int 잠재력저지먼트5;
	public static int 잠재력디케이;
	public static int 잠재력디케이1;
	public static int 잠재력디케이2;
	public static int 잠재력디케이3;
	public static int 잠재력디케이4;
	public static int 잠재력디케이5;
	public static int 잠재력소울;
	public static int 잠재력흡수;
	public static int 잠재력흡수1;
	public static int 잠재력흡수2;
	public static int 잠재력흡수3;
	public static int 잠재력흡수4;
	public static int 잠재력흡수5;
	public static int 잠재력흡수0;
	public static int 잠재력흡수00;
	public static int 잠재력흡수000;
	public static int 잠재력흡수0000;
	public static int 잠재력흡수00000;
	public static int 잠재력흡수000000;
	
	public static int 영웅엠피;
	public static int 영웅엠피1;
	public static int 전설피;
	public static int 전설피1;
	public static int 전설엠피;
	public static int 전설엠피1;
	public static int 인형PVP감소수치;
	//TODO 경험치 보호 버프
	public static int Start_Char_Boho;
	  public static boolean 연스턴;
	  public static boolean 연데페;
	
	public static int QestLevel_End;
	
	
	public static int FISH_TIME1;
	public static int FISH_TIME2;
	public static int FISH_EXP1;
	public static int FISH_EXP2;
	/** 로또 시스템 **/
	public static boolean LOTTO;
	public static int LOTTO_LEVEL;
	public static int LOTTO_BATTING;
	public static int LOTTO_BONUS;
	public static String LOTTO_TIME;
	/** 로또 시스템 **/
	public static int 축복1단;
	public static int 축복2단;
	public static int 축복3단;
	public static int 축복4단;
	public static int 축복5단;
	public static int 피브이피1단;
	public static int 피브이피2단;
	public static int 피브이피3단;
	public static int 방어구축복1단;
	public static int 방어구축복2단;
	public static int 방어구축복3단;
	public static int 방어구축복4단;
	public static int 방어구축복5단;
	public static int 방어구축복6단;
	
	public static int 랜덤부여주문서;
	
	/** 서버관리 스레드 **/
	public static boolean CHARACTER_SAVED_SYSTEM;
	public static boolean CHARACTER_CHECK_SYSTEM;
	public static double 포우데미지1;
	public static double 포우데미지2;
	public static double 포우데미지3;
	public static double 포우데미지4;
	public static int STRIKERGALEs;
	public static double EMPIREHIT_TO_LEVEL;
	public static double STUNHIT_TO_LEVEL;
	public static double STUNHIT_TO_LEVELL;
	public static double STUNHIT_TO_LEVEL1;
	public static double STUNHIT_TO_LEVEL11;
	public static double HORRORHIT_TO_LEVEL;
	public static double DESTROYHIT_TO_LEVEL;
	public static int 포우스턴몹;
	public static double 가드확률;
	public static double 그립확률;
	public static boolean PRISONTEL;
	public static boolean PRISONTELS;
	private static final Logger _log = Logger.getLogger(Config.class.getName());
	public static boolean IS_CHECKBOX_RULES_USE;
	public static double CHARACTER_MAGICHIT_RATE;
	public static double CHARACTER_MAGICCRI_RATE;
	public static double MISSILE_CRITICAL_DAMAGE_RATE;
	public static double MELEE_CRITICAL_DAMAGE_RATE;
	public static double MAGIC_CRITICAL_DAMAGE_RATE;
	public static double REDUCTION_IGNORE_DAMAGERATE;

	public static boolean IS_VALIDSHOP;
	public static boolean IS_VALIDITEMID;
	
	public static int 가호영자이펙트;
	public static int 가호유저힐이펙트;
	public static int 몬스터정리이펙트;
	
	/** 로봇시스템 **/
	public static String ROBOT_NAME;
	/** 로봇시스템 **/

	/** 랭킹진입물약 외부화 **/
	public static int RANK_POTION;
	public static boolean METICE_CLAN_ATTACK_CUTTER;
	/** 11이상 인챈아이템을 무조건 증발되게할것인가 **/
	public static boolean ENCHANT_MAX_FAIL;
	public static boolean MASTERENCHANTMESS;
	public static int 악세사리1;
	public static int 악세사리2;
	public static int 악세사리3;
	public static int 악세사리4;
	public static int 악세사리5;
	public static int 악세사리6;
	public static int 악세사리7;
	public static int 악세사리8;
	public static int 악세사리9;
	public static int 악세사리10;
	public static int 악세사리11;
	public static int 악세사리12;
	public static int 악세사리13;
	public static int 악세사리14;
	public static int 악세사리15;
	
	public static int 무기1;
	public static int 무기2;
	public static int 무기3;
	public static int 무기4;
	public static int 무기5;
	public static int 무기6;
	public static int 무기7;
	public static int 무기8;
	public static int 무기9;

	public static int 안전무기1;
	public static int 안전무기2;
	public static int 안전무기3;
	public static int 안전무기4;
	public static int 안전무기5;
	public static int 안전무기6;
	public static int 안전무기7;
	public static int 안전무기8;
	public static int 안전무기9;
	public static int 안전무기10;
	public static int 안전무기11;
	public static int 안전무기12;
	public static int 안전무기13;
	public static int 안전무기14;
	public static int 안전무기15;
	public static int 약점노출;
	public static int 방어구1;
	public static int 방어구2;
	public static int 방어구3;
	public static int 방어구4;
	public static int 방어구5;
	public static int 방어구6;
	public static int 방어구7;
	public static int 방어구8;
	public static int 방어구9;

	public static int 안전방어구1;
	public static int 안전방어구2;
	public static int 안전방어구3;
	public static int 안전방어구4;
	public static int 안전방어구5;
	public static int 안전방어구6;
	public static int 안전방어구7;
	public static int 안전방어구8;
	public static int 안전방어구9;
	public static int 안전방어구10;
	public static int 안전방어구11;
	public static int 안전방어구12;
	public static int 안전방어구13;
	public static int 안전방어구14;
	public static int 안전방어구15;
	
	public static int ENCHANT_COENT;
	public static int Master_Enchant;
	public static int New_Cha;
	public static int New_Cha1;
	public static int Npc_Max;
	public static int Pc_Reload;
	public static int Npc_Location;
	public static int Boss_Location;
	public static boolean MANAGER_CLEAR;
	public static int MANAGER_CLEAR_TIME;
	public static int COMBO_CHANCE;
	public static double BossGht_CHANCE;
	public static int 아덴상점타입;
	public static double 아덴상점엔샵아덴샵설정;
	public static double Finalburnpc;
	public static double Finalburnnpc;
	public static double 붉이더블;
	public static double BURNING_SPIRIT_PC;
	public static double BURNING_SPIRIT_NPC;
	
	public static double DOUBLE_BREAK_DESTINY_PC;
	public static double DOUBLE_BREAK_DESTINY_NPC;
	
	public static boolean LOGGING_ACCELERATOR;
	public static double DOUBLE_DMG;
	public static double 키링크sp;
	public static double 키링크sp몬스터;
	/** 행운의상점 이벤트 **/
	public static boolean 깃털이벤트상점;

	// 룸티스 검은빛 귀걸이 데미지추가 추가확률
	public static int FEATHER_SHOP_NUM; // 깃털 상점 사용갯수제한
	public static int FEATHERTIME; // 깃털지급시간

	// 강화버프(활력,공격,마법,홀드,스턴,방어)시간외부화
	// 강화버프_활력, 강화버프_공격, 강화버프_방어, 강화버프_마법, 강화버프_스턴, 강화버프_홀드
	public static boolean ACCOUNT_N_BUFF;
	public static int LIMIT_N_BUFF;
	public static int TIME_N_BUFF;

	public static int 감소숫자;
	public static boolean 감소체크 = true;
	/** NPC 물리데미지/ 마법데미지 **/
	public static int npcdmg1;
	public static int npcdmg2;
	public static int npcdmg3;
	public static int npcdmg4;
	public static double New_MagicDmg;
	public static int YN_pclevel;
	public static int 수배금액;
	public static boolean 수배작동유무;
	public static int pcnpcmagicdmg;
	public static boolean 허수아비지급;
	public static int tamsc;// 허수아비 탐
	public static int tamsc1;
	public static int tamsc2;
	public static int SCARELEVEL;
	public static int Characters_CharSlot;

	/** 고대무기류 **/
	public static Integer[] 할파스갑옷인챈;
	public static int 할파스갑옷;
	public static Integer[] rareweapon;
	public static Integer[] weapon;
	public static int rareweaponLevel;
	public static int weaponLevel;
	public static Integer[] armor;
	public static Integer[] armor1;
	public static int armorLevel;
	public static Integer[] accessory;
	public static int accessorytest;
	public static Integer[] ancient2;
	public static Integer[] ancient;
	public static Integer[] ancient1;
	public static int ancientLevel;
	public static Integer[] bless_orim;

	public static Integer[] Roomtis;
	public static int RoomtisLevel;
	public static Integer[] Sanpper;
	public static int SnapperLevel;
	public static Integer[] Sentence;
	public static int SentenceLevel;
	public static Integer[] Insignia;
	public static int Insignia_Level;
	public static Integer[] Should;
	public static int Should_Level;

	public static int BugBug;
	public static int BugBug1;
	public static int Dr_weapon;
	public static int Hero_weapon;
	public static int ADEN_SHOP_NPC;
	public static int Tam_Time;
	public static int[] level1 = new int[2];
	public static int[] level2_1 = new int[2];
	public static int[] level2_2 = new int[2];
	public static int[] level3_1 = new int[2];
	public static int[] level3_2 = new int[2];
	public static int[] level3_3 = new int[2];
	public static int[] level3_4 = new int[2];
	public static boolean 오토루팅가호;
	public static String 원격맵;
	
	public static String 기란마을;
	public static String 지엠룸;
	public static String 후원;
	public static String 상담;
	public static String 결투장;
	public static String 낚시터;
	public static String 기감1;
	public static String 기감2;
	public static String 지결1;
	public static String 지결2;
	public static String 고무;
	
	public static String 화둥;
	public static String 풍둥;
	public static String 설벽;
	public static String 용계;
	
	
	public static String 잊섬;
	public static String 악영;
	public static String 지옥;
	
	public static String 말던1;
	public static String 말던2;
	public static String 용던1;
	public static String 용던2;
	public static String 본던1;
	public static String 본던2;
	public static String 상아탑;
	
	public static String 장소1;
	public static String 장소2;
	public static String 장소3;
	public static String 장소4;
	public static String 장소5;
	public static String 장소6;
	public static String 지탑1;
	public static String 지탑2;
	public static String 지탑3;
	public static String 지탑4;
	public static String 지탑5;
	public static String 지탑6; 
	public static String 지탑7;
	public static String 지탑8;
	public static String 지탑9;
	public static String 지탑10;
	public static String 지탑11;
	
	public static String 오탑1;
	public static String 오탑2;
	public static String 오탑3;
	public static String 오탑4;
	public static String 오탑5;
	public static String 오탑6;
	public static String 오탑7;
	public static String 오탑8;
	public static String 오탑9;
	public static String 오탑10;
	public static String 오탑11;
	public static int RedKnight_dieCount;
	public static boolean 보스아이템지급여부;
	public static boolean 보스아이템지급여부1;
	public static int 화면템;
	public static int 화면템갯수;
	public static String BossAreaDropBossIds;
	public static String 맵번호;
	
	public static String BossAreaAttrBossIds;
	public static int BossAreaDropItemId;
	public static int BossAreaDropItemCount;
	public static String BossAreaDropBossIds1;
	public static String BossAreaAttrBossIds1;
	public static String ItemListId;

	public static int BossAreaDropItemId1;
	public static int BossAreaDropItemCount1;
	public static String BossAreaDropBossIds2;
	public static String BossAreaDropBossIds3;
	/** 서버매니저 **/
	public static boolean normal = false;
	public static boolean world = false;
	public static boolean whisper = false;
	public static boolean alliance = false;
	public static boolean party = false;
	public static boolean shout = false;
	public static boolean business = false;
	public static boolean shutdownCheck = false;
	/** 서버매니저 **/

	public static boolean IS_SELLINGS_SHOP_LOCK;

	/** Debug/release mode */
	public static final boolean DEBUG = false;
	public static boolean STANDBY_SERVER = false;
	public static boolean TARGETGFX = false;

	/** Thread pools size */
	public static int THREAD_P_EFFECTS;
	public static int THREAD_P_GENERAL;
	public static int AI_MAX_THREAD;
	public static int SELECT_THREAD_COUNT;

	/** Server control */
	public static String 한입만멘트;
	public static String 한입만멘트1;
	public static String 수배멘트;
	public static String 수배멘트1;
	public static int 수배번호;
	public static int 수배번호1;
	public static int 수배2단확률;
	public static boolean 수배온오프;
	public static int SCHEDULED_CORE_POOLSIZE;
	public static String GAME_SERVER_MENT;
	public static String GAME_SERVER_NAME;
	public static int GAME_SERVER_PORT;
	public static int AD_REPORT_SERVER_PORT;
	public static String DB_DRIVER;
	public static String DB_URL;
	public static String DB_LOGIN;
	public static String DB_PASSWORD;
	public static String TIME_ZONE;
	public static int CLIENT_LANGUAGE;
	public static boolean HOSTNAME_LOOKUPS;
	public static int AUTOMATIC_KICK;
	public static boolean AUTO_CREATE_ACCOUNTS;
	public static short MAX_ONLINE_USERS;
	public static boolean CACHE_MAP_FILES;
	public static boolean LOAD_V2_MAP_FILES;
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
	public static boolean CHAR_PASSWORD;
	public static int CHAR_PASSWORD_MAXIMUM_FAILCOUNT;

	public static String PRIVATE_SHOP_CHAT;
	public static int HAJA;
	public static boolean 테스트 = false;

	public static int PC_RECOGNIZE_RANGE;
	public static int TEST_QA;
	public static int TEST_QA2;
	
	public static boolean CHARACTER_CONFIG_IN_SERVER_SIDE;
	public static boolean ALLOW_2PC;
	public static int LEVEL_DOWN_RANGE;
	public static boolean SEND_PACKET_BEFORE_TELEPORT;
	public static boolean DETECT_DB_RESOURCE_LEAKS;
	public static boolean AUTH_CONNECT;
	public static int AUTH_KEY;

	/** Rate control */
	public static double RATE_XP;
	public static double RATE_XP_CLAUDIA;
	public static double RATE_LAWFUL;
	public static double RATE_KARMA;
	public static double RATE_DROP_ADENA;
	public static int Adena_LevelMin;
	public static int Adena_LevelMax;
	public static double Drop_Adena;
	public static int Adena_LevelMin1;
	public static int Adena_LevelMax1;
	public static double Drop_Adena1;
	public static double Drop_Adena2;
	public static double RATE_DROP_ITEMS;
	public static double RATE_DROP_RABBIT;// 신묘년 이벤트
	public static double RATE_DROP_RABBIT1;// 할로윈
	public static int ENCHANT_CHANCE_WEAPON;
	public static int ENCHANT_CHANCE_ARMOR;
	public static int ENCHANT_CHANCE_ACCESSORY;
	public static double RATE_WEIGHT_LIMIT;
	public static double RATE_WEIGHT_LIMIT_PET;
	public static double RATE_SHOP_SELLING_PRICE;
	public static double RATE_SHOP_PURCHASING_PRICE;
	public static int CREATE_CHANCE_DIARY;
	public static int CREATE_CHANCE_RECOLLECTION;
	public static int CREATE_CHANCE_MYSTERIOUS;
	public static int CREATE_CHANCE_PROCESSING;
	public static int CREATE_CHANCE_PROCESSING_DIAMOND;
	public static int CREATE_CHANCE_DANTES;
	public static int Dalgona;
	public static int CREATE_CHANCE_ANCIENT_AMULET;
	public static int CREATE_CHANCE_HISTORY_BOOK;
	public static int FEATHER_NUM;
	public static int FEATHER_NUM1;
	public static int FEATHER_NUM2;
	public static int 탐갯수;
//	public static int 탐혈맹갯수;
	//public static int 탐성혈갯수;
	public static int 시간아이템;
	public static int 시간아이템1;
	public static int 시간아이템2;

	/** 이벤트양말 지급 **/
	public static boolean 양말작동유무;
	//public static boolean 탐지급;
	public static boolean 깃털지급;
	public static int 깃털번호;
	public static int EVENT_TIME;
	public static int EVENT_NUMBER;
	public static int EVENT_ITEM;
	public static int 내성배율;
	/** AltSettings control */
	public static short GLOBAL_CHAT_LEVEL;
	public static short WHISPER_CHAT_LEVEL;
	public static short To_ChatLevel;
	public static int BoardLevel;
	public static byte AUTO_LOOT;
	public static int LOOTING_RANGE;
	public static boolean ALT_NONPVP;
	public static boolean ALT_ATKMSG;
	public static boolean CHANGE_TITLE_BY_ONESELF;
	public static int MAX_CLAN_MEMBER;
	public static boolean CLAN_ALLIANCE;
	public static int MAX_PT;
	public static int MAX_CHAT_PT;
	public static boolean SIM_WAR_PENALTY;
	public static boolean GET_BACK;
	public static String ALT_ITEM_DELETION_TYPE;
	public static int ALT_ITEM_DELETION_TIME;
	public static int ALT_ITEM_DELETION_RANGE;
	public static boolean ALT_GMSHOP;
	public static int ALT_GMSHOP_MIN_ID;
	public static int ALT_GMSHOP_MAX_ID;
	public static boolean ALT_BASETOWN;
	public static int ALT_BASETOWN_MIN_ID;
	public static int ALT_BASETOWN_MAX_ID;
	public static boolean ALT_BEGINNER_BONUS;
	public static int NOTIS_TIME; // 공지 사항
	public static int CLAN_COUNT; // 혈맹버프 인원
	public static int Crown_Level;
	public static double 성혈경험치;
	public static int AUTO_REMOVELEVEL;
	public static int 라던입장레벨;
	public static double 마법사마법대미지; // 마법사추타

	/** 새로추가 **/
	public static int 경험치지급단;
	public static int 키링크;
	public static int 데스나이트헬파이어;
	public static int 신화인형hal;
	public static int 신화인형behe;
	public static int 신화인형au;
	public static int 신화인형desel;
	public static int DEDMG;
	public static int halDMG;
	public static int beheDMG;
	public static int auDMG;
	public static int deselDMG;
	public static int 지배자헌신마법확률;
	public static int DEDMG1;
	public static int DEDMG2;
	public static String ChangeItemList1;
	public static String ChangeItemList2;
	public static String ChangeItemList3;
	public static String ChangeItemList4;
	public static String ChangeItemList5;
	public static String ChangeItemList6;
	public static String ChangeItemList7;
	public static String ChangeItemList8;
	public static String ChangeItemList9;
	public static String ChangeItemList10;
	public static String ChangeItemList11;
	public static String ChangeItemList12;
	public static String ChangeItemList13;
	public static String ChangeItemList14;
	public static String ChangeItemList15;
	public static String ChangeItemList16;
	public static String ChangeItemList17;
	public static String ChangeItemList18;
	public static String ChangeItemList19;
	public static String ChangeItemList20;
	/** 기운류 아이템 세부화 **/
	public static int Bless_Chance;
	public static double 기사;
	public static double 용기사;
	public static double 요정;
	public static double 군주;
	public static double 법사;
	public static double 다엘;
	public static double 환술사;
	public static double 전사;
	public static int 신규혈맹보호레벨;
	public static int 신규혈맹클랜;
	public static int 신규혈맹엠블런;
	public static boolean 신규혈맹보호처리;
	public static String 신규혈맹이름;
	public static int 무기인첸트;
	public static int 무기고급인첸트;
	public static int 방어구인첸트;
	public static int 방어구고급인첸트;
	public static int 룸티스;
	public static int 스냅퍼;
	public static int 용티;
	public static int 악세사리;
	public static int accessoryLevel;
	public static int 문장류;
	public static int 휘장류;
	public static double New_LevelExp1;
	public static double New_LevelMin1;
	public static double New_LevelMax1;
	public static double New_LevelExp2;
	public static double New_LevelMin2;
	public static double New_LevelMax2;
	public static double New_LevelExp3;
	public static double New_LevelMin3;
	public static double New_LevelMax3;
	public static double New_LevelExp4;
	public static double New_LevelMin4;
	public static double New_LevelMax4;
	public static boolean SysMsg;
	public static boolean SysMsg1;
	public static boolean POLY_EVENT1;
	public static boolean POLY_EVENT;
	public static int 선포레벨; // 선포레벨
	public static int 혈맹접속인원; // 혈맹접속인원
	public static boolean Arden = false;// 아덴상점 점검 유무
	public static boolean ALT_WHO_COMMAND;
	public static boolean ALT_REVIVAL_POTION;
	public static int ALT_WAR_TIME;
	public static int ALT_WAR_TIME_UNIT;
	public static int ALT_WAR_INTERVAL;
	public static int ALT_WAR_INTERVAL_UNIT;
	public static int ALT_RATE_OF_DUTY;
	public static boolean SPAWN_HOME_POINT;
	public static int SPAWN_HOME_POINT_RANGE;
	public static int SPAWN_HOME_POINT_COUNT;
	public static int SPAWN_HOME_POINT_DELAY;
	public static int HOUSE_TAX_INTERVAL;
	public static int MAX_DOLL_COUNT;
	public static boolean RETURN_TO_NATURE;
	public static int MAX_NPC_ITEM;
	public static int MAX_PERSONAL_WAREHOUSE_ITEM;
	public static int MAX_CLAN_WAREHOUSE_ITEM;
	public static boolean DELETE_CHARACTER_AFTER_7DAYS;
	public static int GMCODE;
	public static int SUB_GMCODE;
	public static int NEW_PLAYER;
	public static int PC_SHOP; 
	public static int ALT_DROPLEVELLIMIT;
	public static boolean Use_Show_Announcecycle; // 추가
	public static int Show_Announcecycle_Time; // 추가
	public static int HELL_TIME;
	public static int HELL_LEVEL;
	public static int 지급템번호;
	public static int 개당지급갯수;
	public static int 서버차수;
	public static double 보상배율;

	//카드 
	public static int hero_cost;
	public static int legend_cost;
	public static int myth_cost;
	public static int legend_per;
	public static int myth_per;
	public static int sole_per;
	
	public static int legend_per_potion;
	public static int myth_per_potion;
	public static int sole_per_potion;
	//합성 제한
	
	public static int myth_possible;
	public static int sole_possible;
	
	/** CharSettings control */
	public static double PHYSICAL_REDUCTION_RATION;
	public static double MAGIC_REDUCTION_RATION;
	
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
	public static int DRAGONKNIGHT_MAX_HP;
	public static int DRAGONKNIGHT_MAX_MP;
	public static int BLACKWIZARD_MAX_HP;
	public static int BLACKWIZARD_MAX_MP;
	public static int 전사_MAX_HP;
	public static int 전사_MAX_MP;
	public static int PRINCE_ADD_DAMAGEPC;
	public static int KNIGHT_ADD_DAMAGEPC;
	public static int ELF_ADD_DAMAGEPC;
	public static int WIZARD_ADD_DAMAGEPC;
	public static int DARKELF_ADD_DAMAGEPC;
	public static int DRAGONKNIGHT_ADD_DAMAGEPC;
	public static int BLACKWIZARD_ADD_DAMAGEPC;
	public static int 전사_ADD_DAMAGEPC;
	public static int LIMITLEVEL;
	public static int NEWDMG;
	
	public static int 기사활;
	public static int 기사도끼;
	public static int 기사체인소드;
	public static int 기사키링크;
	public static int 기사이도류;
	public static int 기사단검;
	public static int 기사지팡이;
	public static int 컬렉션;
	
	public static int 기사활1;
	public static int 기사도끼1;
	public static int 기사체인소드1;
	public static int 기사키링크1;
	public static int 기사이도류1;
	public static int 기사단검1;
	public static int 기사지팡이1;
	/*
	 * public static int MAX_LEVEL; public static int MAX_LEVEL_EXP = -1;
	 */
	public static int ACLEVEL;
	
	public static int LV50_EXP;
	public static int LV51_EXP;
	public static int LV52_EXP;
	public static int LV53_EXP;
	public static int LV54_EXP;
	public static int LV55_EXP;
	public static int LV56_EXP;
	public static int LV57_EXP;
	public static int LV58_EXP;
	public static int LV59_EXP;
	public static int LV60_EXP;
	public static int LV61_EXP;
	public static int LV62_EXP;
	public static int LV63_EXP;
	public static int LV64_EXP;
	public static int LV65_EXP;
	public static int LV66_EXP;
	public static int LV67_EXP;
	public static int LV68_EXP;
	public static int LV69_EXP;
	public static int LV70_EXP;
	public static int LV71_EXP;
	public static int LV72_EXP;
	public static int LV73_EXP;
	public static int LV74_EXP;
	public static int LV75_EXP;
	public static int LV76_EXP;
	public static int LV77_EXP;
	public static int LV78_EXP;
	public static int LV79_EXP;
	public static int LV80_EXP;
	public static int LV81_EXP;
	public static int LV82_EXP;
	public static int LV83_EXP;
	public static int LV84_EXP;
	public static int LV85_EXP;
	public static int LV86_EXP;
	public static int LV87_EXP;
	public static int LV88_EXP;
	public static int LV89_EXP;
	public static int LV90_EXP;
	public static int LV91_EXP;
	public static int LV92_EXP;
	public static int LV93_EXP;
	public static int LV94_EXP;
	public static int LV95_EXP;
	public static int LV96_EXP;
	public static int LV97_EXP;
	public static int LV98_EXP;
	public static int LV99_EXP;
	public static int 영웅유물;
	/** ServerCodes.properties 설정 **/ // TODO
	public static int summon_hpdmg;
	public static boolean IS_PARTY_EXP;
	public static double ADD_PARTY_EXP;
	public static int Tax_Rate;
	public static int Tax_RateMin;
	public static int Tax_RateMax;
	public static int Lineage_Buff;
	public static int ExpPo;
	public static int ExpPo1;
	public static int ExpPo2;
	public static double BLESSING;
	public static double UN_BLESSING;
	public static int ADENA_RATE_OF_BLESSING;
	public static int ADENA_RATE_OF_UNBLESSING;
	
	public static int Garnet_Rnd;
	public static int Garnet_Count;

	// TODO Magic.properties
	public static int SPELL_DELAYOVER_PENDING;
	public static boolean IS_SPELL_DELAY_RUN;
	public static long SPELL_DELAYERROR;
	public static long SPELL_NETWORK_TOLERANCE;
	
	public static boolean IS_ATTACK_DELAY_RUN;
	public static int ATTACK_DELAY_OVER_PENDING;
	public static long ATTACK_BURST_PACKETS;
	
	public static boolean WALK_POSITION_CHECK;
	public static long WALK_NETWORK_TOLERANCE;
	
	public static int IMMUNE_Level;
	public static double IMMUNE_DMG;
	public static int IMMUNE_Level1;
	public static double IMMUNE_DMG1;
	public static int IMMUNE_Level2;
	public static double IMMUNE_DMG2;
	public static int IMMUNE_Level3;
	public static double IMMUNE_DMG3;
	public static int IMMUNE_Level4;
	public static double IMMUNE_DMG4;
	public static int IMMUNE_Level5;
	public static double IMMUNE_DMG5;
	public static int IMMUNE_Level6;
	public static double IMMUNE_DMG6;
	public static double IMMUNE_DMG7;
	public static boolean CHAR_BALANCE_USE;
	//TODO 새로운 스킬 공식 변경
	public static double Erase_MagicT;
	public static double EARTH_BINDT;
	public static double STRIKER_GALET; 
	public static double UNCANNYDODGE1;
	public static double UNCANNYDODGE2;
	public static double UNCANNYDODGE3;
	public static double UNCANNYDODGE4;
	public static double UNCANNYDODGE5;
	public static double UNCANNYDODGE6;
	public static double UNCANNYDODGE7;
	public static double UNCANNYDODGE8;
	public static double UNCANNYDODGE9;
	public static double UNCANNYDODGE10;
	public static double UNCANNYDODGE11;
	public static double UNCANNYDODGE12;
	public static double UNCANNYDODGE13;
	public static double UNCANNYDODGE14;
	public static double UNCANNYDODGE15;
	public static double UNCANNYDODGE16;
	public static double UNCANNYDODGE17;
	public static double UNCANNYDODGE18;
	public static double UNCANNYDODGE19;
	
	public static double MIRRORIMAGE;
	public static double POLLUTE_WATERT;
	public static double WIND_SHACKLET;
	public static double ARMOR_BRAKET;
	public static double 아머기본;
	public static double 아머곱연산확률;
	public static double GUARD_BREAKT;
	public static double FEAR_T; 
	public static double MORTALBODY;
	public static double THUNDER_GRABT;
	public static double HORROR_OF_DEATHT;
	public static double CONFUSION_PHANTASMT;
	public static double POWERRIP_T;
	public static double TOMAHAWKPoint_T;
	public static double[] INFERNO_RATION;
	public static int[] INFERNO_EFFECTS;
	 public static int 추타버그;
	 public static double ngstunpoint;
	 public static double ngmgpoint;
	 public static int ngsdefault;
	 public static int nggdefault;
	 public static int ngslvpoint;
	 public static double ngmtunpoint;
	 public static int ngmdefault;
	 public static int ngmlvpoint;
	  public static int 공성버그;
	  public static int 활타버그;
	  public static int 활명버그;
	  public static int 스펠버그;
	  public static int 리덕버그;
	  public static int 스텟힘;
		public static int 스텟덱스;
		public static int 스텟콘;
		public static int 스텟위즈;
		public static int 스텟인트;
	public static double EMPIRE;
	public static double SHOCK_STUN;
	public static double SHOCK_STUN1;
	public static int DESPERADO;
	public static double BONE_BREAK;
	public static int DISINT_CHAOTIC_WEIGHT;
	public static int DISINT_LAWFUL_WEIGHT;
	public static int DISINT_MAX_DMG;
	public static int DISINT_MIN_DMG;
	public static int TurnChant80UP;
	public static int TurnChant76UP;
	public static int TurnChant75UP;
	public static int TurnChant74UP;
	public static int TurnChant73UP;
	public static int TurnChant72UP;
	public static int TurnChant71UP;
	public static int TurnChant70UP;
	public static int TurnChant69UP;
	public static int TurnChant68UP;
	public static int TurnChant67UP;
	public static int TurnChant66UP;
	public static int TurnChant65UP;
	public static int TurnChant64UP;
	public static int TurnChant63UP;
	public static int TurnChant62UP;
	public static int TurnChant61UP;
	public static int TurnChant60UP;
	public static int TurnChant59UP;
	public static int TurnChant58UP;
	public static int TurnChant57UP;
	public static int TurnChant56UP;
	public static int TurnChant55UP;
	
	public static int TurnChantElf;
	public static int furyFo;
	public static double furydmg;
	public static int crashFo;
	public static double crashdmg;
	public static double BLOW_ATTACKDMG;

	public static double Counter;
	public static double RockDMG;
	public static double 할파스데미지;
	public static int FOU_DAMAGEN;
	public static int TRIPLE_DAMAGEN;
	
	public static double[] FOU_DAMAGE_TABLE;
	public static double[] TRIPLE_DAMAGE_TABLE;

	public static double sim_levelmin1;
	public static double sim_levelmax1;
	public static double sim_levelmin2;
	public static double sim_levelmax2;
	public static double sim_levelmin3;
	public static double sim_levelmax3;
	public static double sim_levelmin4;
	public static double sim_levelmax4;
	public static double sim_levelmin5;
	public static double sim_levelmax5;
	public static double sim_levelmin6;
	public static double sim_levelmax6;
	public static double simsimDmg1;
	public static double simsimDmg2;
	public static double simsimDmg3;
	public static double simsimDmg4;
	public static double simsimDmg5;
	public static double simsimDmg6;

	/** 데이터베이스 풀 관련 */
	public static int min;
	public static int max;
	public static boolean run;

	/** 특수 인챈트 시스템 **/
	public static int Doll_Enchant_Per_lvl1;
	public static int Doll_Enchant_Per_lvl2;
	public static int Doll_Enchant_Per_lvl3;
	public static int Doll_Enchant_Per_lvl4;
	public static int Doll_Enchant_Per_lvl5;
	public static int Doll_Attack_Chance;
	public static int Doll_Attack_Dmg_Lvl1;
	public static int Doll_Attack_Dmg_Lvl2;
	public static int Doll_Attack_Dmg_Lvl3;
	public static int Doll_Attack_Dmg_Lvl4;
	public static int Doll_Attack_Dmg_Lvl5;

	public static int Weapon_Magic_Per;
	public static int Weapon_Enchant_Per_lvl1;
	public static int Weapon_Enchant_Per_lvl2;
	public static int Weapon_Enchant_Per_lvl3;
	public static int Weapon_Enchant_Per_lvl4;

	public static int Weapon_Enchant_Dmg_lvl1;
	public static int Weapon_Enchant_Dmg_lvl2;
	public static int Weapon_Enchant_Dmg_lvl3;
	public static int Weapon_Enchant_Dmg_lvl4;
	
	/**
	 * 서포터 명령어 설정
	 */
	public static int SEASON_REWARD_LEVEL;          // 보상 수령 가능 레벨
	public static int SEASON_REWARD_EFFECT;         // 수령 시 출력될 이펙트 번호
	
	public static int STARTER_REWARD_LEVEL;         // 보급품상자 시작 레벨
	
	/** 특수 인챈트 시스템 **/

	/** Configuration files */
	public static final String SERVER_CONFIG_FILE = "./config/server.properties";
	public static final String RATES_CONFIG_FILE = "./config/rates.properties";
	public static final String ALT_SETTINGS_FILE = "./config/altsettings.properties";
	public static final String CHAR_SETTINGS_CONFIG_FILE = "./config/charsettings.properties";
	public static final String ServerCodes_SETTINGS_CONFIG_FILE = "./config/ServerCodes.properties";
	public static final String Magic_SETTINGS_CONFIG_FILE = "./config/Magic.properties";
	/** 특수 인챈트 시스템 **/
	public static final String MAGIC_ENCHANT_SETTINGS_CONFIG_FILE = "./config/magicenchant.properties";
	public static final String LOTTO_SETTINGS_CONFIG_FILE = "./config/Lotto.properties";
	public static final String DOLL_SETTINGS_CONFIG_FILE = "./config/Dollenchant.properties";
	public static final String GMteleport_CONFIG_FILE = "./config/GMteleport.properties";
	
	/** 그 외의 설정 */

	// NPC로부터 들이마실 수 있는 MP한계
	public static final int MANA_DRAIN_LIMIT_PER_NPC = 40;

	// 1회의 공격으로 들이마실 수 있는 MP한계(SOM, 강철 SOM)
	public static final int MANA_DRAIN_LIMIT_PER_SOM_ATTACK = 9;

	public static double CRAFT_INCREASE_PROB_BYMILLION;

	public static int CRAFT_TRANSMIT_SAFELINE;
	public static int CRAFT_FLAGMENTATION_SIZE;
	public static int[] ALCHEMY_VERSION_HASH;
	public static int ALCHEMY_TRANSMIT_SAFELINE;
	public static int ALCHEMY_HYPERSUCCESS_PROB_BYMILLION;
	public static int ALCHEMY_NOTIFY_LEVEL;
	public static int[] CRAFT_VERSION_HASH;

	//
	public static void loadCraftConfig() {
		PropertyReader reader = new PropertyReader("./config/mj_craftinfo.properties");
		CRAFT_INCREASE_PROB_BYMILLION = reader.readDouble("CraftIncreaseProbByMillionPercent", "1.00");
		CRAFT_VERSION_HASH = HexHelper.parseHexStringToInt32Array(
				reader.readString("CraftVersionHashVal", "C4 C3 E9 D0 DC 18 04 24 DB A5 CD AD E0 0D 31 27 4C EE 48 28"),
				" ");
		CRAFT_TRANSMIT_SAFELINE = reader.readInt("CraftTransmitSafeLine", "500");
		CRAFT_FLAGMENTATION_SIZE = reader.readInt("CraftFlagmentationSize", "20");

		ALCHEMY_VERSION_HASH = HexHelper.parseHexStringToInt32Array(reader.readString("AlchemyVersionHashVal",
				"C4 C3 E9 D0 DC 18 04 24 DB A5 CD AD E0 0D 31 27 4C EE 48 28"), " ");
		ALCHEMY_TRANSMIT_SAFELINE = reader.readInt("AlchemyTransmitSafeLine", "1000");

		ALCHEMY_HYPERSUCCESS_PROB_BYMILLION = reader.readInt("AlchemyHyperSuccessProbability_by_million", "100000");

		ALCHEMY_NOTIFY_LEVEL = reader.readInt("AlchemyNotifyLevel", "4");
		reader.dispose();
	}

	public static void load() {
		// _log.info("loading gameserver config");
		// System.out.println("loading gameserver config");

		// TODO server.properties 셋팅
		try {
			loadCraftConfig();

			Properties serverSettings = new Properties();
			InputStream is = new FileInputStream(new File(SERVER_CONFIG_FILE));
			serverSettings.load(is);
			is.close();

			/** 캐릭정보 자동저장 시스템 **/
			AUTOSAVE_INTERVAL = Integer.parseInt(serverSettings.getProperty("AutosaveInterval", "1200"), 10);

			\u0041\u0055\u0054\u004F\u0053\u0041\u0056\u0045\u005F\u0049\u004E\u0054\u0045\u0052\u0056\u0041\u004C\u005F\u0049\u004E\u0056\u0045\u004E\u0054\u004F\u0052Y = Integer
					.parseInt(serverSettings.getProperty("AutosaveIntervalOfInventory", "300"), 10);
			/** 캐릭정보 자동저장 시스템 **/

			
			SCHEDULED_CORE_POOLSIZE = Integer.parseInt(serverSettings.getProperty("SCHEDULEDCOREPOOLSIZE", "512"));
			IS_SELLINGS_SHOP_LOCK = Boolean.parseBoolean(serverSettings.getProperty("IsSellingsShopLocked", "false"));
			min = Integer.parseInt(serverSettings.getProperty("min"));
			max = Integer.parseInt(serverSettings.getProperty("max"));
			run = Boolean.parseBoolean(serverSettings.getProperty("run"));
			TARGETGFX = Boolean.parseBoolean(serverSettings.getProperty("Targetgfx", "true"));
			STANDBY_SERVER = Boolean.parseBoolean(serverSettings.getProperty("StandbyServer", "true"));
			GAME_SERVER_MENT = new String(serverSettings.getProperty("GAMESERVERMENT", "서버").getBytes("ISO-8859-1"),
					"euc-kr");
			GAME_SERVER_NAME = new String(serverSettings.getProperty("GameServerName", "서버").getBytes("ISO-8859-1"),
					"euc-kr");
			GAME_SERVER_PORT = Integer.parseInt(serverSettings.getProperty("GameserverPort", "2000"));
			AD_REPORT_SERVER_PORT = Integer.parseInt(serverSettings.getProperty("AdReportServerPort", "18182"));
			DB_DRIVER = serverSettings.getProperty("Driver", "com.mysql.jdbc.Driver");
			DB_URL = serverSettings.getProperty("URL",
					"jdbc:mysql://localhost/l1jdb?useUnicode=true&characterEncoding=euckr");
			DB_LOGIN = serverSettings.getProperty("Login", "root");
			DB_PASSWORD = serverSettings.getProperty("Password", "");
			SELECT_THREAD_COUNT = Integer.parseInt(serverSettings.getProperty("IoThreadPoolSize", "4"));
			CLIENT_LANGUAGE = Integer.parseInt(serverSettings.getProperty("ClientLanguage", "4"));
			TIME_ZONE = serverSettings.getProperty("TimeZone", "JST");
			HOSTNAME_LOOKUPS = Boolean.parseBoolean(serverSettings.getProperty("HostnameLookups", "false"));
			AUTOMATIC_KICK = Integer.parseInt(serverSettings.getProperty("AutomaticKick", "10"));
			AUTO_CREATE_ACCOUNTS = Boolean.parseBoolean(serverSettings.getProperty("AutoCreateAccounts", "true"));
			MAX_ONLINE_USERS = Short.parseShort(serverSettings.getProperty("MaximumOnlineUsers", "30"));
			CACHE_MAP_FILES = Boolean.parseBoolean(serverSettings.getProperty("CacheMapFiles", "false"));
			LOAD_V2_MAP_FILES = Boolean.parseBoolean(serverSettings.getProperty("LoadV2MapFiles", "false"));
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
			PC_RECOGNIZE_RANGE = Integer.parseInt(serverSettings.getProperty("PcRecognizeRange", "20"));
			PRIVATE_SHOP_CHAT = new String(serverSettings.getProperty("PrivateShopChat").getBytes("ISO-8859-1"),
					"MS949");
			반딜압류 = Boolean.parseBoolean(serverSettings.getProperty("Skillban", "false"));
			CHAR_PASSWORD = Boolean.parseBoolean(serverSettings.getProperty("CharPassword", "true"));
			CHAR_PASSWORD_MAXIMUM_FAILCOUNT = Integer.parseInt(serverSettings.getProperty("CharPasswordMaximumFailureCount", "5"));
			HAJA = Integer.parseInt(serverSettings.getProperty("Haja", "2"));
			CHARACTER_CONFIG_IN_SERVER_SIDE = Boolean
					.parseBoolean(serverSettings.getProperty("CharacterConfigInServerSide", "true"));
			ALLOW_2PC = Boolean.parseBoolean(serverSettings.getProperty("Allow2PC", "true"));
			LEVEL_DOWN_RANGE = Integer.parseInt(serverSettings.getProperty("LevelDownRange", "0"));
			SEND_PACKET_BEFORE_TELEPORT = Boolean
					.parseBoolean(serverSettings.getProperty("SendPacketBeforeTeleport", "true"));
			DETECT_DB_RESOURCE_LEAKS = Boolean
					.parseBoolean(serverSettings.getProperty("EnableDatabaseResourceLeaksDetection", "false"));
			AUTH_CONNECT = Boolean.parseBoolean(serverSettings.getProperty("AuthConnect", "false"));
			AUTH_KEY = Integer.parseInt(serverSettings.getProperty("AuthKey", "136"));
			시간아이템 = Integer.parseInt(serverSettings.getProperty("TimeaItem", "3600"));
			시간아이템1 = Integer.parseInt(serverSettings.getProperty("TimeaItem1", "3600"));
			시간아이템2 = Integer.parseInt(serverSettings.getProperty("TimeaItem2", "3600"));
			CHARACTER_SAVED_SYSTEM = Boolean.parseBoolean(serverSettings.getProperty("CHARACTER_SAVED_SYSTEM", "true"));
			CHARACTER_CHECK_SYSTEM = Boolean.parseBoolean(serverSettings.getProperty("CHARACTER_CHECK_SYSTEM", "true"));
			
			LOGIN_ENCRYPTION = Boolean.parseBoolean(serverSettings.getProperty("LoginEncryption", "false"));
			USE_ATTENDANCE_SYSTEM = Boolean.parseBoolean(serverSettings.getProperty("UseAttendanceSystem", "false"));
			
			
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed to Load " + SERVER_CONFIG_FILE + " File.");
		}

		// TODO rate.properties 셋팅
		try {
			Properties rateSettings = new Properties();
			FileReader is = new FileReader(new File(RATES_CONFIG_FILE));
			rateSettings.load(is);
			is.close();
			CHAR_BALANCE_USE = Boolean.parseBoolean(rateSettings.getProperty("CharBalanceUse", "false"));
			Show_Announcecycle_Time = Integer.parseInt(rateSettings.getProperty("Show_Announcecycle_Time", "10"));
			Use_Show_Announcecycle = Boolean.parseBoolean(rateSettings.getProperty("Use_Show_Announcecycle", "false"));

			RATE_XP = Double.parseDouble(rateSettings.getProperty("RateXp", "1.0"));
			RATE_XP_CLAUDIA = Double.parseDouble(rateSettings.getProperty("RateXpClaudia", "1.0"));
			성혈경험치 = Double.parseDouble(rateSettings.getProperty("BloodBonus", "1.0"));
			RATE_LAWFUL = Double.parseDouble(rateSettings.getProperty("RateLawful", "1.0"));
			RATE_KARMA = Double.parseDouble(rateSettings.getProperty("RateKarma", "1.0"));
			RATE_DROP_ADENA = Double.parseDouble(rateSettings.getProperty("RateDropAdena", "1.0"));
			
			Adena_LevelMin = Integer.parseInt(rateSettings.getProperty("AdenaLevelMin", "1"));
			Adena_LevelMax = Integer.parseInt(rateSettings.getProperty("AdenaLevelMax", "1"));
			Drop_Adena = Double.parseDouble(rateSettings.getProperty("DropAdena", "1.0"));
			Adena_LevelMin1 = Integer.parseInt(rateSettings.getProperty("AdenaLevelMin1", "1"));
			Adena_LevelMax1 = Integer.parseInt(rateSettings.getProperty("AdenaLevelMax1", "1"));
			Drop_Adena1 = Double.parseDouble(rateSettings.getProperty("DropAdena1", "1.0"));
			Drop_Adena2 = Double.parseDouble(rateSettings.getProperty("DropAdena2", "1.0"));

			RATE_DROP_ITEMS = Double.parseDouble(rateSettings.getProperty("RateDropItems", "1.0"));
			RATE_DROP_RABBIT = Double.parseDouble(rateSettings.getProperty("RateDropRabbit", "10.0"));
			RATE_DROP_RABBIT1 = Double.parseDouble(rateSettings.getProperty("Hallowdrop", "10.0"));
			ENCHANT_CHANCE_WEAPON = Integer.parseInt(rateSettings.getProperty("EnchantChanceWeapon", "68"));
			ENCHANT_CHANCE_ARMOR = Integer.parseInt(rateSettings.getProperty("EnchantChanceArmor", "52"));
			ENCHANT_CHANCE_ACCESSORY = Integer.parseInt(rateSettings.getProperty("EnchantChanceAccessory", "5"));
			RATE_WEIGHT_LIMIT = Double.parseDouble(rateSettings.getProperty("RateWeightLimit", "1"));
			RATE_WEIGHT_LIMIT_PET = Double.parseDouble(rateSettings.getProperty("RateWeightLimitforPet", "1"));
			RATE_SHOP_SELLING_PRICE = Double.parseDouble(rateSettings.getProperty("RateShopSellingPrice", "1.0"));
			RATE_SHOP_PURCHASING_PRICE = Double.parseDouble(rateSettings.getProperty("RateShopPurchasingPrice", "1.0"));
			CREATE_CHANCE_DIARY = Integer.parseInt(rateSettings.getProperty("CreateChanceDiary", "33"));
			CREATE_CHANCE_RECOLLECTION = Integer.parseInt(rateSettings.getProperty("CreateChanceRecollection", "90"));
			CREATE_CHANCE_MYSTERIOUS = Integer.parseInt(rateSettings.getProperty("CreateChanceMysterious", "90"));
			CREATE_CHANCE_PROCESSING = Integer.parseInt(rateSettings.getProperty("CreateChanceProcessing", "90"));
			CREATE_CHANCE_PROCESSING_DIAMOND = Integer
					.parseInt(rateSettings.getProperty("CreateChanceProcessingDiamond", "90"));
			CREATE_CHANCE_DANTES = Integer.parseInt(rateSettings.getProperty("CreateChanceDantes", "90"));
			Dalgona = Integer.parseInt(rateSettings.getProperty("dalgona", "90"));
			CREATE_CHANCE_ANCIENT_AMULET = Integer
					.parseInt(rateSettings.getProperty("CreateChanceAncientAmulet", "90"));
			CREATE_CHANCE_HISTORY_BOOK = Integer.parseInt(rateSettings.getProperty("CreateChanceHistoryBook", "10"));
			FEATHER_NUM = Integer.parseInt(rateSettings.getProperty("FeatherNum", "6"));
			FEATHER_NUM1 = Integer.parseInt(rateSettings.getProperty("FeatherNum1", "2"));
			FEATHER_NUM2 = Integer.parseInt(rateSettings.getProperty("FeatherNum2", "3"));

			FEATHERTIME = Integer.parseInt(rateSettings.getProperty("featherTime", "15"));
			Tam_Time = Integer.parseInt(rateSettings.getProperty("TamTime", "15"));
			PRISONTEL = Boolean.parseBoolean(rateSettings.getProperty("Prisontels", "true"));
		      PRISONTELS = Boolean.parseBoolean(rateSettings.getProperty("Prisontelsa", "true"));
			HELL_TIME = Integer.parseInt(rateSettings.getProperty("HellTime", "6"));
			HELL_LEVEL = Integer.parseInt(rateSettings.getProperty("HellLevel", "70"));
			탐갯수 = Integer.parseInt(rateSettings.getProperty("TamNum", "6"));
		//	탐혈맹갯수 = Integer.parseInt(rateSettings.getProperty("TamNum1", "6"));
		//	탐성혈갯수 = Integer.parseInt(rateSettings.getProperty("TamNum2", "6"));
			양말작동유무 = Boolean.parseBoolean(rateSettings.getProperty("Eventof", "false"));
		//	탐지급 = Boolean.parseBoolean(rateSettings.getProperty("Tamonoff", "false"));
			깃털지급 = Boolean.parseBoolean(rateSettings.getProperty("Featheronoff", "false"));
			깃털번호 = Integer.parseInt(rateSettings.getProperty("Featheritem", "1"));
			EVENT_TIME = Integer.parseInt(rateSettings.getProperty("EventTime", "1"));
			EVENT_NUMBER = Integer.parseInt(rateSettings.getProperty("EventNumber", "1"));
			EVENT_ITEM = Integer.parseInt(rateSettings.getProperty("EventItem", "1"));
			
			
			// TODO 경험치 세부화
			New_LevelExp1 = Double.parseDouble(rateSettings.getProperty("NewLevelExp1", "1"));
			New_LevelMin1 = Double.parseDouble(rateSettings.getProperty("NewLevelMin1", "1"));
			New_LevelMax1 = Double.parseDouble(rateSettings.getProperty("NewLevelMax1", "1"));

			New_LevelExp2 = Double.parseDouble(rateSettings.getProperty("NewLevelExp2", "1"));
			New_LevelMin2 = Double.parseDouble(rateSettings.getProperty("NewLevelMin2", "1"));
			New_LevelMax2 = Double.parseDouble(rateSettings.getProperty("NewLevelMax2", "1"));

			New_LevelExp3 = Double.parseDouble(rateSettings.getProperty("NewLevelExp3", "1"));
			New_LevelMin3 = Double.parseDouble(rateSettings.getProperty("NewLevelMin3", "1"));
			New_LevelMax3 = Double.parseDouble(rateSettings.getProperty("NewLevelMax3", "1"));

			New_LevelExp4 = Double.parseDouble(rateSettings.getProperty("NewLevelExp4", "1"));
			New_LevelMin4 = Double.parseDouble(rateSettings.getProperty("NewLevelMin4", "1"));
			New_LevelMax4 = Double.parseDouble(rateSettings.getProperty("NewLevelMax4", "1"));
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed to Load " + RATES_CONFIG_FILE + " File.");
		}

		// TODO altsettings.properties 셋팅
		try {
			Properties altSettings = new Properties();
			InputStream is = new FileInputStream(new File(ALT_SETTINGS_FILE));
			altSettings.load(is);
			is.close();
			ChangeItemList1 = altSettings.getProperty("ChangeItem1", "");
			ChangeItemList2 = altSettings.getProperty("ChangeItem2", "");
			ChangeItemList3 = altSettings.getProperty("ChangeItem3", "");
			ChangeItemList4 = altSettings.getProperty("ChangeItem4", "");
			ChangeItemList5 = altSettings.getProperty("ChangeItem5", "");
			ChangeItemList6 = altSettings.getProperty("ChangeItem6", "");
			ChangeItemList7 = altSettings.getProperty("ChangeItem7", "");
			ChangeItemList8 = altSettings.getProperty("ChangeItem8", "");
			ChangeItemList9 = altSettings.getProperty("ChangeItem9", "");
			ChangeItemList10 = altSettings.getProperty("ChangeItem10", "");

			ChangeItemList11 = altSettings.getProperty("ChangeItem11", "");
			ChangeItemList12 = altSettings.getProperty("ChangeItem12", "");
			ChangeItemList13 = altSettings.getProperty("ChangeItem13", "");
			ChangeItemList14 = altSettings.getProperty("ChangeItem14", "");
			ChangeItemList15 = altSettings.getProperty("ChangeItem15", "");
			ChangeItemList16 = altSettings.getProperty("ChangeItem16", "");
			ChangeItemList17 = altSettings.getProperty("ChangeItem17", "");
			ChangeItemList18 = altSettings.getProperty("ChangeItem18", "");
			ChangeItemList19 = altSettings.getProperty("ChangeItem19", "");
			ChangeItemList20 = altSettings.getProperty("ChangeItem20", "");
			
			운영자이벤트 = Boolean.parseBoolean(altSettings.getProperty("gmevent",
					"")); 
			AUTO_GM_SHOP = Boolean.parseBoolean(altSettings.getProperty("AutoGMShop", "false"));
			운영자이벤트맵 = altSettings.getProperty("gmeventmap", "");
			변신몹아이디 = altSettings.getProperty("gmeventMOBID", "");
			라던펑몹 = altSettings.getProperty("neiMOBID", "");
			이벤트이름 = toKor(altSettings.getProperty("gmeventName", "메티스 이벤트"));
			운영자이벤트약한확률= Integer.parseInt(altSettings.getProperty(
					"gmeventplus", ""));
			
			운영자이벤트강한확률= Integer.parseInt(altSettings.getProperty(
					"gmeventplus1", ""));
			운영자이벤트아이템 = Integer.parseInt(altSettings.getProperty("gmeventitme", "900075"));
			
			공지제목 = new String(altSettings.getProperty("notop", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			공지1번줄 = new String(altSettings.getProperty("no1", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지2번줄 = new String(altSettings.getProperty("no2", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지3번줄 = new String(altSettings.getProperty("no3", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지4번줄 = new String(altSettings.getProperty("no4", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지5번줄 = new String(altSettings.getProperty("no5", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지6번줄 = new String(altSettings.getProperty("no6", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지7번줄 = new String(altSettings.getProperty("no7", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지8번줄 = new String(altSettings.getProperty("no8", "").getBytes("ISO-8859-1"),
					"euc-kr");
			공지9번줄 = new String(altSettings.getProperty("no9", "").getBytes("ISO-8859-1"),
					"euc-kr");
			스턴멘트 = Boolean.parseBoolean(altSettings.getProperty("Stunment", "true"));
			자동사냥 = Boolean.parseBoolean(altSettings.getProperty("Autohunt", "true"));
			자동물약버프 = Boolean.parseBoolean(altSettings.getProperty("Autohunt1", "true"));
			SysMsg = Boolean.parseBoolean(altSettings.getProperty("Sys_Msg", "false"));
			SysMsg1 = Boolean.parseBoolean(altSettings.getProperty("Sys_Msg1", "false"));
			보스아이템지급여부 = Boolean.parseBoolean(altSettings.getProperty("BossItem", "false"));
			알람멘트 = Boolean.parseBoolean(altSettings.getProperty("Eventalam", "true"));
			BossAreaDropBossIds = altSettings.getProperty("BossAreaDropBossIds", "");
			BossAreaDropItemId = Integer.parseInt(altSettings.getProperty("BossAreaDropItemId", "40308"));
			BossAreaDropItemCount = Integer.parseInt(altSettings.getProperty("BossAreaDropItemCount", "1"));
			METICE_CLAN_ATTACK_CUTTER = Boolean
					.parseBoolean(altSettings.getProperty("MeticeClanAttackCutter", "false"));
			BossAreaAttrBossIds = altSettings.getProperty("BossAreaAttrBossIds", "");
			화면템 = Integer.parseInt(altSettings.getProperty("Givetem", "1"));
			화면템갯수 = Integer.parseInt(altSettings.getProperty("Givetem1", "1"));
			ItemListId = altSettings.getProperty("Item_Listid", "");
			맵번호 = altSettings.getProperty("Mapnum", "");
			버프번호 = altSettings.getProperty("buffno0", "");
			
			맵1번x좌표 = Integer.parseInt(altSettings.getProperty("int1x", "140"));
			맵1번y좌표 = Integer.parseInt(altSettings.getProperty("int1y", "140"));
			맵1번map좌표 = Integer.parseInt(altSettings.getProperty("int1map", "140"));
			
			맵2번x좌표 = Integer.parseInt(altSettings.getProperty("int2x", "140"));
			맵2번y좌표 = Integer.parseInt(altSettings.getProperty("int2y", "140"));
			맵2번map좌표 = Integer.parseInt(altSettings.getProperty("int2map", "140"));
			
			맵3번x좌표 = Integer.parseInt(altSettings.getProperty("int3x", "140"));
			맵3번y좌표 = Integer.parseInt(altSettings.getProperty("int3y", "140"));
			맵3번map좌표 = Integer.parseInt(altSettings.getProperty("int3map", "140"));
			
			맥스인첸1차 = Integer.parseInt(altSettings.getProperty("maxwaponenchan1", "1"));
			맥스인첸2차 = Integer.parseInt(altSettings.getProperty("maxwaponenchan2", "1"));
			맥스인첸3차 = Integer.parseInt(altSettings.getProperty("maxwaponenchan3", "1"));
			TEST_QA = Integer.parseInt(altSettings.getProperty("testQA", "1"));
			TEST_QA2 = Integer.parseInt(altSettings.getProperty("testQA2", "1"));
			
			
			보스아이템지급여부1 = Boolean.parseBoolean(altSettings.getProperty("BossItem1", "false"));
			BossAreaDropBossIds1 = altSettings.getProperty("BossAreaDropBossIds1", "");
			BossAreaDropItemId1 = Integer.parseInt(altSettings.getProperty("BossAreaDropItemId1", "40308"));
			BossAreaDropItemCount1 = Integer.parseInt(altSettings.getProperty("BossAreaDropItemCount1", "1"));
			ItemDrops = Boolean.parseBoolean(altSettings.getProperty("ItemDrop", "false"));
			추타버그 = Integer.parseInt(altSettings.getProperty("DmgupBug", "140"));
			공성버그 = Integer.parseInt(altSettings.getProperty("HitupBug", "140"));
			활타버그 = Integer.parseInt(altSettings.getProperty("BowDmgupBog", "140"));
			활명버그 = Integer.parseInt(altSettings.getProperty("BowHitupBog", "140"));
			스펠버그 = Integer.parseInt(altSettings.getProperty("SpBog", "140"));
			리덕버그 = Integer.parseInt(altSettings.getProperty("ByArmorBug", "140"));
			스텟힘 = Integer.parseInt(altSettings.getProperty("Bugstat1", "50"));
			스텟덱스 = Integer.parseInt(altSettings.getProperty("Bugstat2", "50"));
			스텟콘 = Integer.parseInt(altSettings.getProperty("Bugstat3", "50"));
			스텟위즈 = Integer.parseInt(altSettings.getProperty("Bugstat4", "50"));
			\uC11C\uBC84\uC810\uAC80\uB9E8\uD2B8 = new String(altSettings.getProperty("\u0073\u0074\u0062\u0079\u006D\u0065\u006E\u0074", " ").getBytes("ISO-8859-1"),
					"euc-kr");
			
			BOSS_DROP_GROUND_ENABLE = Boolean.parseBoolean(altSettings.getProperty("BossDropGroundEnable", "true"));
			PARTY_LOOT_ELIGIBILITY = Integer.parseInt(altSettings.getProperty("PartyLootEligibility", "0"));
			PARTY_LOOT_RANGE = Integer.parseInt(altSettings.getProperty("PartyLootRange", "14"));
			PARTY_LOOT_MODE = Integer.parseInt(altSettings.getProperty("PartyLootMode", "1"));
			PARTY_LOOT_ENABLE = Boolean.parseBoolean(altSettings.getProperty("PartyLootEnable", "true"));
			
			스텟인트 = Integer.parseInt(altSettings.getProperty("Bugstat5", "50"));
			랭킹순위 = Integer.parseInt(altSettings.getProperty("RK", "8"));
			BossAreaDropBossIds3 = altSettings.getProperty("BoxList", "");
			BossAreaDropBossIds2 = altSettings.getProperty("BoxItemList", "");
			InnHelper.USAGECOUNT = Integer.parseInt(altSettings.getProperty("InnMaximumCount", "20"));
			

			USE_POTION_EFFECT_LOGGIN = Boolean.parseBoolean(altSettings.getProperty("UsePotionEffectLogging", "false"));
			USE_ACTION_TIME_LOGGING = Boolean.parseBoolean(altSettings.getProperty("UseActionTimeLogging", "false"));
			QestLevel_End = Short.parseShort(altSettings.getProperty("QestLevelEnd", "1"));
			Neige_Count_Pass = Short.parseShort(altSettings.getProperty("NegieCount", "1"));
			Neige_Myth_Skill = Short.parseShort(altSettings.getProperty("NegieMyth", "1"));
			Start_Char_Level = Short.parseShort(altSettings.getProperty("StartCharLevel", "1"));
			cantblack = Short.parseShort(altSettings.getProperty("cantblack", "1"));
			Clan_leader = Boolean.parseBoolean(altSettings.getProperty("Clanleader", "true"));
			GLOBAL_CHAT_LEVEL = Short.parseShort(altSettings.getProperty("GlobalChatLevel", "30"));
			WHISPER_CHAT_LEVEL = Short.parseShort(altSettings.getProperty("WhisperChatLevel", "7"));
			To_ChatLevel = Short.parseShort(altSettings.getProperty("ToChatLevel", "1"));
			BoardLevel = Integer.parseInt(altSettings.getProperty("BoardLevel", "70"));
			
			AUTO_LOOT = Byte.parseByte(altSettings.getProperty("AutoLoot", "2"));
			
			CLAN_COUNT = Integer.parseInt(altSettings.getProperty("Clancount", "5"));
			혈맹버프퍼센트 = Double.parseDouble(altSettings.getProperty("Clanexp", "5"));
			Crown_Level = Integer.parseInt(altSettings.getProperty("CrownLevel", "1"));
			ALT_NONPVP = Boolean.parseBoolean(altSettings.getProperty("NonPvP", "true"));
			ALT_ATKMSG = Boolean.parseBoolean(altSettings.getProperty("AttackMessageOn", "true"));
			CHANGE_TITLE_BY_ONESELF = Boolean.parseBoolean(altSettings.getProperty("ChangeTitleByOneself", "false"));
			MAX_CLAN_MEMBER = Integer.parseInt(altSettings.getProperty("MaxClanMember", "0"));
			CLAN_ALLIANCE = Boolean.parseBoolean(altSettings.getProperty("ClanAlliance", "true"));
			MAX_PT = Integer.parseInt(altSettings.getProperty("MaxPT", "8"));
			MAX_CHAT_PT = Integer.parseInt(altSettings.getProperty("MaxChatPT", "8"));
			SIM_WAR_PENALTY = Boolean.parseBoolean(altSettings.getProperty("SimWarPenalty", "true"));
			GET_BACK = Boolean.parseBoolean(altSettings.getProperty("GetBack", "false"));
			ALT_ITEM_DELETION_TYPE = altSettings.getProperty("ItemDeletionType", "auto");
			ALT_ITEM_DELETION_TIME = Integer.parseInt(altSettings.getProperty("ItemDeletionTime", "10"));
			ALT_ITEM_DELETION_RANGE = Integer.parseInt(altSettings.getProperty("ItemDeletionRange", "5"));
			ALT_GMSHOP = Boolean.parseBoolean(altSettings.getProperty("GMshop", "false"));
			ALT_GMSHOP_MIN_ID = Integer.parseInt(altSettings.getProperty("GMshopMinID", "0xffffffff"));
			ALT_GMSHOP_MAX_ID = Integer.parseInt(altSettings.getProperty("GMshopMaxID", "0xffffffff"));
			ALT_BASETOWN = Boolean.parseBoolean(altSettings.getProperty("BaseTown", "false"));
			ALT_BASETOWN_MIN_ID = Integer.parseInt(altSettings.getProperty("BaseTownMinID", "0xffffffff"));
			ALT_BASETOWN_MAX_ID = Integer.parseInt(altSettings.getProperty("BaseTownMaxID", "0xffffffff"));
			ALT_WHO_COMMAND = Boolean.parseBoolean(altSettings.getProperty("WhoCommand", "false"));
			ALT_REVIVAL_POTION = Boolean.parseBoolean(altSettings.getProperty("RevivalPotion", "false"));
			ALT_BEGINNER_BONUS = Boolean.parseBoolean(altSettings.getProperty("BeginnerEvent", "false"));
			ALT_DROPLEVELLIMIT = Integer.parseInt(altSettings.getProperty("DropLevelLimit", "90"));
			AUTO_REMOVELEVEL = Integer.parseInt(altSettings.getProperty("AutoRemoveLevel", "50"));
			라던입장레벨 = Integer.parseInt(altSettings.getProperty("LarvaLevel", "70"));
			마법사마법대미지 = Double.parseDouble(altSettings.getProperty("Wizdmg", "1.0"));
			신규혈맹보호레벨 = Integer.parseInt(altSettings.getProperty("NewClanLevel", "50"));
			신규혈맹클랜 = Integer.parseInt(altSettings.getProperty("NewClanid", "1"));
			신규혈맹엠블런 = Integer.parseInt(altSettings.getProperty("NewClanEMB", "1"));
			신규혈맹보호처리 = Boolean.parseBoolean(altSettings.getProperty("NewClanPvP", "true"));
			신규혈맹이름 = altSettings.getProperty("NewClanName", "중립");
			FISH_COMS = Boolean.parseBoolean(altSettings.getProperty("FishSys", "false"));
			
			dr1m = new String(altSettings.getProperty("\u0064\u0072\u0031\u006D", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr2m = new String(altSettings.getProperty("dr2m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr3m = new String(altSettings.getProperty("dr3m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr4m = new String(altSettings.getProperty("dr4m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr5m = new String(altSettings.getProperty("dr5m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr6m = new String(altSettings.getProperty("dr6m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr7m = new String(altSettings.getProperty("dr7m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr8m = new String(altSettings.getProperty("dr8m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr9m = new String(altSettings.getProperty("dr9m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr10m = new String(altSettings.getProperty("dr10m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr11m = new String(altSettings.getProperty("dr11m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr12m = new String(altSettings.getProperty("dr12m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr13m = new String(altSettings.getProperty("dr13m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr14m = new String(altSettings.getProperty("dr14m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr15m = new String(altSettings.getProperty("dr15m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr16m = new String(altSettings.getProperty("dr16m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr17m = new String(altSettings.getProperty("dr17m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr18m = new String(altSettings.getProperty("dr18m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr19m = new String(altSettings.getProperty("dr19m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			dr20m = new String(altSettings.getProperty("dr20m", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			
			dr1id = Integer.parseInt(altSettings.getProperty("dr1id", "900075"));
			dr2id = Integer.parseInt(altSettings.getProperty("dr2id", "900111"));
			dr3id = Integer.parseInt(altSettings.getProperty("dr3id", "1"));
			dr4id = Integer.parseInt(altSettings.getProperty("dr4id", "1"));
			dr5id = Integer.parseInt(altSettings.getProperty("dr5id", "1"));
			dr6id = Integer.parseInt(altSettings.getProperty("dr6id", "1"));
			dr7id = Integer.parseInt(altSettings.getProperty("dr7id", "1"));
			dr8id = Integer.parseInt(altSettings.getProperty("dr8id", "1"));
			dr9id = Integer.parseInt(altSettings.getProperty("dr9id", "1"));
			dr10id = Integer.parseInt(altSettings.getProperty("dr10id", "1"));
			dr11id = Integer.parseInt(altSettings.getProperty("dr11id", "1"));
			dr12id = Integer.parseInt(altSettings.getProperty("dr12id", "1"));
			dr13id = Integer.parseInt(altSettings.getProperty("dr13id", "1"));
			dr14id = Integer.parseInt(altSettings.getProperty("dr14id", "1"));
			dr15id = Integer.parseInt(altSettings.getProperty("dr15id", "1"));
			dr16id = Integer.parseInt(altSettings.getProperty("dr16id", "1"));
			dr17id = Integer.parseInt(altSettings.getProperty("dr17id", "1"));
			dr18id = Integer.parseInt(altSettings.getProperty("dr18id", "1"));
			dr19id = Integer.parseInt(altSettings.getProperty("dr19id", "1"));
			dr20id = Integer.parseInt(altSettings.getProperty("dr20id", "1"));

			dr1qty = Integer.parseInt(altSettings.getProperty("dr1qty", "1"));
			dr2qty = Integer.parseInt(altSettings.getProperty("dr2qty", "1"));
			dr3qty = Integer.parseInt(altSettings.getProperty("dr3qty", "1"));
			dr4qty = Integer.parseInt(altSettings.getProperty("dr4qty", "1"));
			dr5qty = Integer.parseInt(altSettings.getProperty("dr5qty", "1"));
			dr6qty = Integer.parseInt(altSettings.getProperty("dr6qty", "1"));
			dr7qty = Integer.parseInt(altSettings.getProperty("dr7qty", "1"));
			dr8qty = Integer.parseInt(altSettings.getProperty("dr8qty", "1"));
			dr9qty = Integer.parseInt(altSettings.getProperty("dr9qty", "1"));
			dr10qty = Integer.parseInt(altSettings.getProperty("dr10qty", "1"));
			dr11qty = Integer.parseInt(altSettings.getProperty("dr11qty", "1"));
			dr12qty = Integer.parseInt(altSettings.getProperty("dr12qty", "1"));
			dr13qty = Integer.parseInt(altSettings.getProperty("dr13qty", "1"));
			dr14qty = Integer.parseInt(altSettings.getProperty("dr14qty", "1"));
			dr15qty = Integer.parseInt(altSettings.getProperty("dr15qty", "1"));
			dr16qty = Integer.parseInt(altSettings.getProperty("dr16qty", "1"));
			dr17qty = Integer.parseInt(altSettings.getProperty("dr17qty", "1"));
			dr18qty = Integer.parseInt(altSettings.getProperty("dr18qty", "1"));
			dr19qty = Integer.parseInt(altSettings.getProperty("dr19qty", "1"));
			dr20qty = Integer.parseInt(altSettings.getProperty("dr20qty", "1"));
			
			dr1en = Integer.parseInt(altSettings.getProperty("dr1en", "1"));
			dr2en = Integer.parseInt(altSettings.getProperty("dr2en", "1"));
			dr3en = Integer.parseInt(altSettings.getProperty("dr3en", "1"));
			dr4en = Integer.parseInt(altSettings.getProperty("dr4en", "1"));
			dr5en = Integer.parseInt(altSettings.getProperty("dr5en", "1"));
			dr6en = Integer.parseInt(altSettings.getProperty("dr6en", "1"));
			dr7en = Integer.parseInt(altSettings.getProperty("dr7en", "1"));
			dr8en = Integer.parseInt(altSettings.getProperty("dr8en", "1"));
			dr9en = Integer.parseInt(altSettings.getProperty("dr9en", "1"));
			dr10en = Integer.parseInt(altSettings.getProperty("dr10en", "1"));
			dr11en = Integer.parseInt(altSettings.getProperty("dr11en", "1"));
			dr12en = Integer.parseInt(altSettings.getProperty("dr12en", "1"));
			dr13en = Integer.parseInt(altSettings.getProperty("dr13en", "1"));
			dr14en = Integer.parseInt(altSettings.getProperty("dr14en", "1"));
			dr15en = Integer.parseInt(altSettings.getProperty("dr15en", "1"));
			dr16en = Integer.parseInt(altSettings.getProperty("dr16en", "1"));
			dr17en = Integer.parseInt(altSettings.getProperty("dr17en", "1"));
			dr18en = Integer.parseInt(altSettings.getProperty("dr18en", "1"));
			dr19en = Integer.parseInt(altSettings.getProperty("dr19en", "1"));
			dr20en = Integer.parseInt(altSettings.getProperty("dr20en", "1"));
			
			
			warwinen = Integer.parseInt(altSettings.getProperty("warwinen", "1"));
			waren = Integer.parseInt(altSettings.getProperty("waren", "1"));
			
			warwinid = Integer.parseInt(altSettings.getProperty("warwinid", "900075"));
			warid = Integer.parseInt(altSettings.getProperty("warid", "900111"));
			warwinqty = Integer.parseInt(altSettings.getProperty("warwinqty", "1"));
			warqty = Integer.parseInt(altSettings.getProperty("warqty", "1"));
			warcrownid = Integer.parseInt(altSettings.getProperty("warcrownid", "900075"));
			warcrownqty = Integer.parseInt(altSettings.getProperty("warcrownqty", "900075"));
			warcrownid2 = Integer.parseInt(altSettings.getProperty("warcrownid2", "900075"));
			warcrownqty2 = Integer.parseInt(altSettings.getProperty("warcrownqty2", "900075"));
			\uC9C0\uAE09\uC728 =  Integer.parseInt(altSettings.getProperty("\u0067\u0069\u0076\u0065\u0031", "900075"));

			boss1 = Integer.parseInt(altSettings.getProperty("boss1", "1"));
			boss2 = Integer.parseInt(altSettings.getProperty("boss2", "1"));
			boss3 = Integer.parseInt(altSettings.getProperty("boss3", "1"));
			boss4 = Integer.parseInt(altSettings.getProperty("boss4", "1"));
			boss5 = Integer.parseInt(altSettings.getProperty("boss5", "1"));
			boss6 = Integer.parseInt(altSettings.getProperty("boss6", "1"));
			boss7 = Integer.parseInt(altSettings.getProperty("boss7", "1"));
			boss8 = Integer.parseInt(altSettings.getProperty("boss8", "1"));
			boss9 = Integer.parseInt(altSettings.getProperty("boss9", "1"));
			boss10 = Integer.parseInt(altSettings.getProperty("boss10", "1"));
			
			지급템번호 = Integer.parseInt(altSettings.getProperty("itemno", "1"));
			개당지급갯수 = Integer.parseInt(altSettings.getProperty("itemno01", "1"));
			개당지급 = Integer.parseInt(altSettings.getProperty("coin80", "15"));
			서버차수 = Integer.parseInt(altSettings.getProperty("serverno1", "1"));
			보상배율 = Double.parseDouble(altSettings.getProperty("Bosang", "1"));
			
			hero_cost = Integer.parseInt(altSettings.getProperty("hero_cost", "1"));
			legend_cost = Integer.parseInt(altSettings.getProperty("legend_cost", "1"));
			myth_cost = Integer.parseInt(altSettings.getProperty("myth_cost", "1"));
			legend_per = Integer.parseInt(altSettings.getProperty("legend_per", "1"));
			myth_per = Integer.parseInt(altSettings.getProperty("myth_per", "1"));
			sole_per =  Integer.parseInt(altSettings.getProperty("sole_per", "1"));
			
			legend_per_potion = Integer.parseInt(altSettings.getProperty("legend_per_potion", "1"));
			myth_per_potion = Integer.parseInt(altSettings.getProperty("myth_per_potion", "1"));
			sole_per_potion =  Integer.parseInt(altSettings.getProperty("sole_per_potion", "1"));
			
			myth_possible = Integer.parseInt(altSettings.getProperty("myth_possible", "1"));
			sole_possible = Integer.parseInt(altSettings.getProperty("sole_possible", "1"));
			
			
			royal = Boolean.parseBoolean(altSettings.getProperty("royal", "true"));
			totomo = Boolean.parseBoolean(altSettings.getProperty("totomo", "true"));
			clanraidmob = altSettings.getProperty("clanraidmob", "");
			clanraidmob2 = altSettings.getProperty("clanraidmob2", "");
			clanraidmob3 = altSettings.getProperty("clanraidmob3", "");
			clanraidmob4 = altSettings.getProperty("clanraidmob4", "");
			clanraidmob5 = altSettings.getProperty("clanraidmob5", "");
			clanraidmobboss = altSettings.getProperty("clanraidmobboss", "");
			mobboss = altSettings.getProperty("clanraidmobboss", "");
			
			Fishchance = Integer.parseInt(altSettings.getProperty("fish12", "1"));
			Fishchance1 = Integer.parseInt(altSettings.getProperty("fish13", "1"));
			
			자동물약카오 = Boolean.parseBoolean(altSettings.getProperty("Autoonoff", "false"));
			
			마일리지엔피씨 = altSettings.getProperty("Mm1", "");
			마일리지지급여부 = Boolean.parseBoolean(altSettings.getProperty("Mm2", "false"));
			마일리지값 = Integer.parseInt(altSettings.getProperty("Mm3",
					"80"));
			랭킹멘트 = new String(altSettings.getProperty("MENT", "서버").getBytes("ISO-8859-1"),
					"euc-kr");
			멘트온오프 = Boolean.parseBoolean(altSettings.getProperty("MENTonoff", "false"));
			String strWar;
			strWar = altSettings.getProperty("WarTime", "1h");
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
			strWar = altSettings.getProperty("WarInterval", "2d");
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
			수성시간 = Integer.parseInt(altSettings.getProperty("Wardf", "15"));
			상점버그 = Boolean.parseBoolean(altSettings.getProperty("Shopbug", "true"));
			영자마크 = Boolean.parseBoolean(altSettings.getProperty("GMmark", "true"));
			공성수배 = Boolean.parseBoolean(altSettings.getProperty("Castlehunt", "true"));
			성혈버프 = Boolean.parseBoolean(altSettings.getProperty("Castlebuff", "true"));
			클랜이름 = Boolean.parseBoolean(altSettings.getProperty("Clanname", "true"));
			보라채팅1 = Boolean.parseBoolean(altSettings.getProperty("Caoch1", "true"));
			보라채팅2 = Boolean.parseBoolean(altSettings.getProperty("Caoch2", "true"));
			보라채팅3 = Boolean.parseBoolean(altSettings.getProperty("Caoch3", "true"));
			보라채팅4 = Boolean.parseBoolean(altSettings.getProperty("Caoch4", "true"));
			낚시이펙트 = Boolean.parseBoolean(altSettings.getProperty("FishEF", "true"));
			FISH_TIME1 = Integer.parseInt(altSettings.getProperty("Fishtime1", "10"));
			FISH_TIME2 = Integer.parseInt(altSettings.getProperty("Fishtime2", "10"));
			FISH_EXP1 = Integer.parseInt(altSettings.getProperty("Fishexp1", "10"));
			FISH_EXP2 = Integer.parseInt(altSettings.getProperty("Fishexp2", "10"));
			
			아이템1등 = Integer.parseInt(altSettings.getProperty("itemno1", "400254"));
			아이템2등 = Integer.parseInt(altSettings.getProperty("itemno2", "400254"));
			아이템3등 = Integer.parseInt(altSettings.getProperty("itemno3", "400254"));
			아이템4등 = Integer.parseInt(altSettings.getProperty("itemno4", "400254"));
			아이템5등 = Integer.parseInt(altSettings.getProperty("itemno5", "400254"));
			
			Yuilper = Integer.parseInt(altSettings.getProperty("Yuilper", "1"));
			seadunship = Integer.parseInt(altSettings.getProperty("seadunship", "0"));
			seadunshippass = Integer.parseInt(altSettings.getProperty("seadunshippass", "0"));
			랭킹아이템1등 = Integer.parseInt(altSettings.getProperty("Rankkingno1", "400254"));
			랭킹아이템2등 = Integer.parseInt(altSettings.getProperty("Rankkingno2", "400254"));
			랭킹아이템3등 = Integer.parseInt(altSettings.getProperty("Rankkingno3", "400254"));
			랭킹아이템4등 = Integer.parseInt(altSettings.getProperty("Rankkingno4", "400254"));
			랭킹아이템5등 = Integer.parseInt(altSettings.getProperty("Rankkingno5", "400254"));
			랭킹아이템6등 = Integer.parseInt(altSettings.getProperty("Rankkingno6", "400254"));
			랭킹아이템7등 = Integer.parseInt(altSettings.getProperty("Rankkingno7", "400254"));
			랭킹아이템8등 = Integer.parseInt(altSettings.getProperty("Rankkingno8", "400254"));
			랭킹아이템9등 = Integer.parseInt(altSettings.getProperty("Rankkingno9", "400254"));
			랭킹아이템10등 = Integer.parseInt(altSettings.getProperty("Rankkingno10", "400254"));
			랭킹아이템순위 = Integer.parseInt(altSettings.getProperty("Rankking", "400254"));
			
			잊섬오픈 = Integer.parseInt(altSettings.getProperty("rodun01", "0"));
			거무오픈 = Integer.parseInt(altSettings.getProperty("rodun02", "0"));
			라스타오픈 = Integer.parseInt(altSettings.getProperty("rodun03", "0"));
			지배오픈 = Integer.parseInt(altSettings.getProperty("rodun04", "0"));
			풍둥오픈 = Integer.parseInt(altSettings.getProperty("rodun05", "0"));
			오설오픈 = Integer.parseInt(altSettings.getProperty("rodun06", "0"));
			화둥오픈 = Integer.parseInt(altSettings.getProperty("rodun07", "0"));
			용계오픈 = Integer.parseInt(altSettings.getProperty("rodun08", "0"));
			테베오픈 = Integer.parseInt(altSettings.getProperty("rodun09", "0"));
			배알림 = Integer.parseInt(altSettings.getProperty("neigeship", "0"));
			하이네낚시 = Integer.parseInt(altSettings.getProperty("neshenie", "0"));
			네쥬던전초기화 = Integer.parseInt(altSettings.getProperty("nedungeon", "0"));
			네쥬던전체킹 = Integer.parseInt(altSettings.getProperty("neduncheck", "0"));
			// [던전초기화 시각 외부화] 기본 08:59 (기존 하드코딩 값)
			DungeonResetHour = Integer.parseInt(altSettings.getProperty("DungeonResetHour", "8"));
			DungeonResetMinute = Integer.parseInt(altSettings.getProperty("DungeonResetMinute", "59"));
			맥스레벨 = Integer.parseInt(altSettings.getProperty("nemaxlv", "70"));
			최대지급 = Integer.parseInt(altSettings.getProperty("maxcount", "10"));
			지급아이템 = Integer.parseInt(altSettings.getProperty("itemname", "10"));
			
			ALT_FISHEVENT = Boolean.parseBoolean(altSettings.getProperty("FishEvent", "true"));
			ALT_WAR_INTERVAL = Integer.parseInt(strWar);
			캐릭터시작점 = Boolean.parseBoolean(altSettings.getProperty("Newpoint", "true"));
			SPAWN_HOME_POINT = Boolean.parseBoolean(altSettings.getProperty("SpawnHomePoint", "true"));
			SPAWN_HOME_POINT_COUNT = Integer.parseInt(altSettings.getProperty("SpawnHomePointCount", "2"));
			SPAWN_HOME_POINT_DELAY = Integer.parseInt(altSettings.getProperty("SpawnHomePointDelay", "100"));
			SPAWN_HOME_POINT_RANGE = Integer.parseInt(altSettings.getProperty("SpawnHomePointRange", "8"));
			HOUSE_TAX_INTERVAL = Integer.parseInt(altSettings.getProperty("HouseTaxInterval", "10"));
			MAX_DOLL_COUNT = Integer.parseInt(altSettings.getProperty("MaxDollCount", "1"));
			RETURN_TO_NATURE = Boolean.parseBoolean(altSettings.getProperty("ReturnToNature", "false"));
			MAX_NPC_ITEM = Integer.parseInt(altSettings.getProperty("MaxNpcItem", "8"));
			MAX_PERSONAL_WAREHOUSE_ITEM = Integer.parseInt(altSettings.getProperty("MaxPersonalWarehouseItem", "100"));
			MAX_CLAN_WAREHOUSE_ITEM = Integer.parseInt(altSettings.getProperty("MaxClanWarehouseItem", "200"));
			DELETE_CHARACTER_AFTER_7DAYS = Boolean
					.parseBoolean(altSettings.getProperty("DeleteCharacterAfter7Days", "True"));
			한입만멘트 = new String(altSettings.getProperty("BOSSMENT", "보스").getBytes("ISO-8859-1"),
					"euc-kr");
			한입만멘트1 = new String(altSettings.getProperty("BOSSMENT1", "보스").getBytes("ISO-8859-1"),
					"euc-kr");
			수배멘트 = new String(altSettings.getProperty("Huntment", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			수배멘트1 = new String(altSettings.getProperty("Huntment1", "수배").getBytes("ISO-8859-1"),
					"euc-kr");
			수배2단확률 = Integer.parseInt(altSettings.getProperty("Hunt2", "9999"));
			수배번호 = Integer.parseInt(altSettings.getProperty("Hunticon", "9999"));
			수배번호1 = Integer.parseInt(altSettings.getProperty("Hunticon1", "9999"));
			수배온오프 = Boolean.parseBoolean(altSettings.getProperty("Huntonoff", "true"));
			GMCODE = Integer.parseInt(altSettings.getProperty("GMCODE", "9999"));
			SUB_GMCODE = Integer.parseInt(altSettings.getProperty("SUBGMCODE", "8888"));
			선포레벨 = Integer.parseInt(altSettings.getProperty("WarLevel", "60"));
			혈맹접속인원 = Integer.parseInt(altSettings.getProperty("WarPlayer", "1"));
			NEW_PLAYER = Integer.parseInt(altSettings.getProperty("NewPlayer", "0"));
			PC_SHOP = Integer.parseInt(altSettings.getProperty("Pcshop", "0"));
			NOTIS_TIME = Integer.parseInt(altSettings.getProperty("NotisTime", "10"));
			인형소환유무 = Boolean.parseBoolean(altSettings.getProperty("Dollss", "true"));
			봉인템삭제 = Boolean.parseBoolean(altSettings.getProperty("blockitem", "true"));
			품질시스템사용 = Boolean.parseBoolean(altSettings.getProperty("poom", "true"));
			
			
			SEASON_REWARD_LEVEL = Integer.parseInt(altSettings.getProperty("SeasonLevel", "80"));
			SEASON_REWARD_EFFECT = Integer.parseInt(altSettings.getProperty("SeasonEffectId", "7976"));
			STARTER_REWARD_LEVEL = Integer.parseInt(altSettings.getProperty("StartLevel", "70"));

		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed to Load " + ALT_SETTINGS_FILE + " File.");
		}

		// TODO charsettings.properties 셋팅
		try {
			Properties charSettings = new Properties();
			InputStream is = new FileInputStream(new File(CHAR_SETTINGS_CONFIG_FILE));
			charSettings.load(is);
			is.close();

			감소체크 = Boolean.parseBoolean(charSettings.getProperty("DMGon", "true"));
			감소숫자 = Integer.parseInt(charSettings.getProperty("DMGre", "800"));
			
			PHYSICAL_REDUCTION_RATION = Double.parseDouble(charSettings.getProperty("PhysicalReductionRation", "1.3"));
			MAGIC_REDUCTION_RATION = Double.parseDouble(charSettings.getProperty("MagicReductionRation", "1.3"));
			
			
			라우풀1 = Integer.parseInt(charSettings.getProperty("LWP1", "1"));
			라우풀2 = Integer.parseInt(charSettings.getProperty("LWP2", "1"));
			라우풀3 = Integer.parseInt(charSettings.getProperty("LWP3", "1"));
			라우풀4 = Integer.parseInt(charSettings.getProperty("LWP4", "1"));
			라우풀5 = Integer.parseInt(charSettings.getProperty("LWP5", "1"));
			라우풀6 = Integer.parseInt(charSettings.getProperty("LWP6", "1"));
			라우풀7 = Integer.parseInt(charSettings.getProperty("LWP7", "1"));
			라우풀8 = Integer.parseInt(charSettings.getProperty("LWP8", "1"));
			
			
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
			DRAGONKNIGHT_MAX_HP = Integer.parseInt(charSettings.getProperty("DragonknightMaxHP", "1000"));
			DRAGONKNIGHT_MAX_MP = Integer.parseInt(charSettings.getProperty("DragonknightMaxMP", "900"));
			BLACKWIZARD_MAX_HP = Integer.parseInt(charSettings.getProperty("BlackwizardMaxHP", "900"));
			BLACKWIZARD_MAX_MP = Integer.parseInt(charSettings.getProperty("BlackwizardMaxMP", "1100"));
			전사_MAX_HP = Integer.parseInt(charSettings.getProperty("WarriorMaxHP", "1400"));
			전사_MAX_MP = Integer.parseInt(charSettings.getProperty("WarriorMaxMP", "600"));
			LIMITLEVEL = Integer.parseInt(charSettings.getProperty("LimitLevel", "99"));
		    NEWDMG = Integer.parseInt(charSettings.getProperty("NEWDMG", "99"));
		    기사활 = Integer.parseInt(charSettings.getProperty("dmg1", "99"));
		    기사도끼 = Integer.parseInt(charSettings.getProperty("dmg2", "99"));
		    기사단검 = Integer.parseInt(charSettings.getProperty("dmg3", "99"));
		    기사지팡이 = Integer.parseInt(charSettings.getProperty("dmg4", "99"));
		    기사체인소드 = Integer.parseInt(charSettings.getProperty("dmg5", "99"));
		    기사키링크 = Integer.parseInt(charSettings.getProperty("dmg6", "99"));
		    기사이도류 = Integer.parseInt(charSettings.getProperty("dmg7", "99"));
		    컬렉션 = Integer.parseInt(charSettings.getProperty("colec", "0"));
		    
		    기사활1 = Integer.parseInt(charSettings.getProperty("dmg11", "99"));
		    기사도끼1 = Integer.parseInt(charSettings.getProperty("dmg22", "99"));
		    기사단검1 = Integer.parseInt(charSettings.getProperty("dmg33", "99"));
		    기사지팡이1 = Integer.parseInt(charSettings.getProperty("dmg44", "99"));
		    기사체인소드1 = Integer.parseInt(charSettings.getProperty("dmg55", "99"));
		    기사키링크1 = Integer.parseInt(charSettings.getProperty("dmg66", "99"));
		    기사이도류1 = Integer.parseInt(charSettings.getProperty("dmg77", "99"));
			/*
			 * MAX_LEVEL = Integer.parseInt(charSettings.getProperty("maxLevel",
			 * "90")); MAX_LEVEL_EXP = ExpTable.getExpByLevel(MAX_LEVEL) + 5;
			 */
			ACLEVEL = Integer.parseInt(charSettings.getProperty("aclevel", "200"));
			PRINCE_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("PrinceAddDamagePc", "0"));
			KNIGHT_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("KnightAddDamagePc", "0"));
			ELF_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("ElfAddDamagePc", "0"));
			WIZARD_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("WizardAddDamagePc", "0"));
			DARKELF_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("DarkelfAddDamagePc", "0"));
			DRAGONKNIGHT_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("DragonknightAddDamagePc", "0"));
			BLACKWIZARD_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("BlackwizardAddDamagePc", "0"));
			전사_ADD_DAMAGEPC = Integer.parseInt(charSettings.getProperty("WarriorAddDamagePc", "0"));
			
			PER_AC_PC_TO_PC = Integer.parseInt(charSettings.getProperty("AcPercentPcToPc", "33"));
			PER_AC_NPC_TO_PC = Integer.parseInt(charSettings.getProperty("AcPercentNpcToPc", "33"));
			PER_DODGE = Integer.parseInt(charSettings.getProperty("DodgePercent", "33"));
			PER_EVASION = Integer.parseInt(charSettings.getProperty("EvasionPercent", "33"));
			키링크sp  = Double.parseDouble(charSettings.getProperty("Ksp", "1.2"));
			키링크sp몬스터  = Double.parseDouble(charSettings.getProperty("Kspp", "1.2"));
			LV50_EXP = Integer.parseInt(charSettings.getProperty("Lv50Exp", "1"));
			LV51_EXP = Integer.parseInt(charSettings.getProperty("Lv51Exp", "1"));
			LV52_EXP = Integer.parseInt(charSettings.getProperty("Lv52Exp", "1"));
			LV53_EXP = Integer.parseInt(charSettings.getProperty("Lv53Exp", "1"));
			LV54_EXP = Integer.parseInt(charSettings.getProperty("Lv54Exp", "1"));
			LV55_EXP = Integer.parseInt(charSettings.getProperty("Lv55Exp", "1"));
			LV56_EXP = Integer.parseInt(charSettings.getProperty("Lv56Exp", "1"));
			LV57_EXP = Integer.parseInt(charSettings.getProperty("Lv57Exp", "1"));
			LV58_EXP = Integer.parseInt(charSettings.getProperty("Lv58Exp", "1"));
			LV59_EXP = Integer.parseInt(charSettings.getProperty("Lv59Exp", "1"));
			LV60_EXP = Integer.parseInt(charSettings.getProperty("Lv60Exp", "1"));
			LV61_EXP = Integer.parseInt(charSettings.getProperty("Lv61Exp", "1"));
			LV62_EXP = Integer.parseInt(charSettings.getProperty("Lv62Exp", "1"));
			LV63_EXP = Integer.parseInt(charSettings.getProperty("Lv63Exp", "1"));
			LV64_EXP = Integer.parseInt(charSettings.getProperty("Lv64Exp", "1"));
			LV65_EXP = Integer.parseInt(charSettings.getProperty("Lv65Exp", "2"));
			LV66_EXP = Integer.parseInt(charSettings.getProperty("Lv66Exp", "2"));
			LV67_EXP = Integer.parseInt(charSettings.getProperty("Lv67Exp", "2"));
			LV68_EXP = Integer.parseInt(charSettings.getProperty("Lv68Exp", "2"));
			LV69_EXP = Integer.parseInt(charSettings.getProperty("Lv69Exp", "2"));
			LV70_EXP = Integer.parseInt(charSettings.getProperty("Lv70Exp", "4"));
			LV71_EXP = Integer.parseInt(charSettings.getProperty("Lv71Exp", "4"));
			LV72_EXP = Integer.parseInt(charSettings.getProperty("Lv72Exp", "4"));
			LV73_EXP = Integer.parseInt(charSettings.getProperty("Lv73Exp", "4"));
			LV74_EXP = Integer.parseInt(charSettings.getProperty("Lv74Exp", "4"));
			LV75_EXP = Integer.parseInt(charSettings.getProperty("Lv75Exp", "8"));
			LV76_EXP = Integer.parseInt(charSettings.getProperty("Lv76Exp", "8"));
			LV77_EXP = Integer.parseInt(charSettings.getProperty("Lv77Exp", "8"));
			LV78_EXP = Integer.parseInt(charSettings.getProperty("Lv78Exp", "8"));
			LV79_EXP = Integer.parseInt(charSettings.getProperty("Lv79Exp", "16"));
			LV80_EXP = Integer.parseInt(charSettings.getProperty("Lv80Exp", "32"));
			LV81_EXP = Integer.parseInt(charSettings.getProperty("Lv81Exp", "64"));
			LV82_EXP = Integer.parseInt(charSettings.getProperty("Lv82Exp", "128"));
			LV83_EXP = Integer.parseInt(charSettings.getProperty("Lv83Exp", "256"));
			LV84_EXP = Integer.parseInt(charSettings.getProperty("Lv84Exp", "512"));
			LV85_EXP = Integer.parseInt(charSettings.getProperty("Lv85Exp", "1024"));
			LV86_EXP = Integer.parseInt(charSettings.getProperty("Lv86Exp", "2048"));
			LV87_EXP = Integer.parseInt(charSettings.getProperty("Lv87Exp", "4096"));
			LV88_EXP = Integer.parseInt(charSettings.getProperty("Lv88Exp", "8192"));
			LV89_EXP = Integer.parseInt(charSettings.getProperty("Lv89Exp", "16384"));
			LV90_EXP = Integer.parseInt(charSettings.getProperty("Lv90Exp", "32768"));
			LV91_EXP = Integer.parseInt(charSettings.getProperty("Lv91Exp", "65536"));
			LV92_EXP = Integer.parseInt(charSettings.getProperty("Lv92Exp", "131072"));
			LV93_EXP = Integer.parseInt(charSettings.getProperty("Lv93Exp", "262144"));
			LV94_EXP = Integer.parseInt(charSettings.getProperty("Lv94Exp", "524288"));
			LV95_EXP = Integer.parseInt(charSettings.getProperty("Lv95Exp", "1048576"));
			LV96_EXP = Integer.parseInt(charSettings.getProperty("Lv96Exp", "2097152"));
			LV97_EXP = Integer.parseInt(charSettings.getProperty("Lv97Exp", "4194304"));
			LV98_EXP = Integer.parseInt(charSettings.getProperty("Lv98Exp", "8388608"));
			LV99_EXP = Integer.parseInt(charSettings.getProperty("Lv99Exp", "16777216"));
		} catch (Exception e) {
			e.printStackTrace();
			throw new Error("Failed to Load " + CHAR_SETTINGS_CONFIG_FILE + " File.");
		}

		// TODO Magic.properties 셋팅
		try {
			Properties Magic = new Properties();
			InputStream is = new FileInputStream(new File(Magic_SETTINGS_CONFIG_FILE));
			Magic.load(is);
			is.close();

			String s = Magic.getProperty("FouDamageTable", "0.3,0.3,0.3,0.3,0.3,0.5,0.6,0.7,1.0,1.0,1.0,1.0");
			String[] arr = s.split(",");
			FOU_DAMAGE_TABLE = new double[arr.length];
			for (int i = 0; i < arr.length; i++)
				FOU_DAMAGE_TABLE[i] = Double.parseDouble(arr[i]);

			s = Magic.getProperty("TripleDamageTable", "0.3,0.3,0.3,0.3,0.3,0.5,0.5,0.6,0.7,0.8,0.9,1.0");
			arr = s.split(",");
			TRIPLE_DAMAGE_TABLE = new double[arr.length];
			for (int i = 0; i < arr.length; i++)
				TRIPLE_DAMAGE_TABLE[i] = Double.parseDouble(arr[i]);

			FOU_DAMAGEN = Integer.parseInt(Magic.getProperty("FOUDAMAGEN", "10"));
			TRIPLE_DAMAGEN = Integer.parseInt(Magic.getProperty("TRIPLEDAMAGEN", "10"));
			
			IS_SPELL_DELAY_RUN = Boolean.parseBoolean(Magic.getProperty("IsSpellDelayRun", "true"));
			SPELL_DELAYOVER_PENDING = Integer.parseInt(Magic.getProperty("SpellDelayOverPending", "5"));
			SPELL_NETWORK_TOLERANCE = Long.parseLong(Magic.getProperty("SpellNetworkTolerance", "10L"));
			
			IS_ATTACK_DELAY_RUN = Boolean.parseBoolean(Magic.getProperty("IsAttackDelayRun", "true"));
			ATTACK_DELAY_OVER_PENDING = Integer.parseInt(Magic.getProperty("AttackDelayOverPending", "10"));
			ATTACK_BURST_PACKETS = Long.parseLong(Magic.getProperty("AttackBurstPackets", "3"));
			
			WALK_POSITION_CHECK = Boolean.parseBoolean(Magic.getProperty("WalkPositionCheck", "true"));
			WALK_NETWORK_TOLERANCE = Long.parseLong(Magic.getProperty("WalkNetworkTolerance", "19"));
			
			포우데미지1 = Double.parseDouble(Magic.getProperty("Fous1", "5.0"));
			포우데미지2 = Double.parseDouble(Magic.getProperty("Fous2", "5.0"));
			포우데미지3 = Double.parseDouble(Magic.getProperty("Fous3", "5.0"));
			포우데미지4 = Double.parseDouble(Magic.getProperty("Fous4", "5.0"));
			// TODO 이뮨 투함
			IMMUNE_Level = Integer.parseInt(Magic.getProperty("IMMUNELevel", "87"));
			IMMUNE_DMG = Double.parseDouble(Magic.getProperty("IMMUNEDMG", "0.60"));
			IMMUNE_Level1 = Integer.parseInt(Magic.getProperty("IMMUNELevel1", "86"));
			IMMUNE_DMG1 = Double.parseDouble(Magic.getProperty("IMMUNEDMG1", "0.50"));
			IMMUNE_Level2 = Integer.parseInt(Magic.getProperty("IMMUNELevel2", "85"));
			IMMUNE_DMG2 = Double.parseDouble(Magic.getProperty("IMMUNEDMG2", "0.45"));
			IMMUNE_Level3 = Integer.parseInt(Magic.getProperty("IMMUNELevel3", "80"));
			IMMUNE_DMG3 = Double.parseDouble(Magic.getProperty("IMMUNEDMG3", "0.35"));
			IMMUNE_Level4 = Integer.parseInt(Magic.getProperty("IMMUNELevel4", "75"));
			IMMUNE_DMG4 = Double.parseDouble(Magic.getProperty("IMMUNEDMG4", "0.25"));
			IMMUNE_Level5 = Integer.parseInt(Magic.getProperty("IMMUNELevel5", "70"));
			IMMUNE_DMG5 = Double.parseDouble(Magic.getProperty("IMMUNEDMG5", "0.20"));
			IMMUNE_Level6 = Integer.parseInt(Magic.getProperty("IMMUNELevel6", "65"));
			IMMUNE_DMG6 = Double.parseDouble(Magic.getProperty("IMMUNEDMG6", "0.15"));
			IMMUNE_DMG7 = Double.parseDouble(Magic.getProperty("IMMUNEDMG7", "0.10"));
			DESPERADO_SKILL_RND_TIME = Magic.getProperty("DesperadoSkillRndTime", "1500,2000,2500,3000");
			DESPERADO1_SKILL_RND_TIME = Magic.getProperty("DesperadoSkillRndTime1", "1500,2000,2500,3000");
			POWERRIP_SKILL_RND_TIME = Magic.getProperty("PowerSkillRndTime", "1500,2000,2500,3000,3500,4000,4500,5000");
			GRAB_SKILL_RND_TIME = Magic.getProperty("GrabSkillRndTime", "1000,2000,2500,3000");
			EMPIRE_SKILL_RND_TIME = Magic.getProperty("EmpireSkillRndTime", "1500,2000,2500,3000,3500,4000");
			엠파이어오버로드 = Magic.getProperty("EmpireOV", "1500,2000,2500,3000,3500,4000");
		    STUN_SKILL_RND_TIME = Magic.getProperty("StunSkillRndTime", "1000,1400,1800,2200,2600,3000,3400,3800");
		    STUN_SKILL_RND_TIME1 = Magic.getProperty("StunSkillRndTime1", "1000,1400,1800,2200,2600,3000,3400,3800");
			GRABS_SKILL_RND_TIME = Magic.getProperty("GrabSSkillRndTime", "3000,3500,4000,4500");
			 포스스턴 = Magic.getProperty("Fstun", "1000,1400,1800,2200,2600,3000,3400,3800");
			 네메시스스턴 = Magic.getProperty("NMstun", "1000,1400,1800,2200,2600,3000,3400,3800");
			 포스스턴배율 = Integer.parseInt(Magic.getProperty("Fstun1", "15"));
			 포스스턴확률 = Integer.parseInt(Magic.getProperty("Fstun2", "15"));
			 포스스턴레벨 = Integer.parseInt(Magic.getProperty("Fstunlevel", "15"));
			 
			 ngstunpoint = Double.parseDouble(Magic.getProperty("ngstunpoint", "1"));
			 ngmgpoint = Double.parseDouble(Magic.getProperty("ngmgpoint", "1"));
			 ngsdefault = Integer.parseInt(Magic.getProperty("ngsdefault", "1"));
			 nggdefault = Integer.parseInt(Magic.getProperty("nggdefault", "1"));
			 ngslvpoint = Integer.parseInt(Magic.getProperty("ngslvpoint", "1"));
			 
			 ngmtunpoint = Double.parseDouble(Magic.getProperty("ngmtunpoint", "1"));
			 ngmdefault = Integer.parseInt(Magic.getProperty("ngmdefault", "1"));
			 ngmlvpoint = Integer.parseInt(Magic.getProperty("ngmlvpoint", "1"));
				//TODO 신규 적중/내성에 대한 외부화
			 테테  = Integer.parseInt(Magic.getProperty("tt1", "15"));
			Erase_MagicT = Double.parseDouble(Magic.getProperty("EraseMagicT", "1"));
			EARTH_BINDT = Double.parseDouble(Magic.getProperty("EARTHBINDT", "1"));
			STRIKER_GALET = Double.parseDouble(Magic.getProperty("STRIKERGALET", "1"));
			UNCANNYDODGE1 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE1", "1"));
			UNCANNYDODGE2 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE2", "1"));
			UNCANNYDODGE3 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE3", "1"));
			UNCANNYDODGE4 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE4", "1"));
			UNCANNYDODGE5 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE5", "1"));
			UNCANNYDODGE6 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE6", "1"));
			UNCANNYDODGE7 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE7", "1"));
			UNCANNYDODGE8 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE8", "1"));
			UNCANNYDODGE9 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE9", "1"));
			UNCANNYDODGE10 = Double.parseDouble(Magic.getProperty("UNCANNYDODGE10", "1"));
			UNCANNYDODGE11= Double.parseDouble(Magic.getProperty("UNCANNYDODGE11", "1"));
			UNCANNYDODGE12= Double.parseDouble(Magic.getProperty("UNCANNYDODGE12", "1"));
			UNCANNYDODGE13= Double.parseDouble(Magic.getProperty("UNCANNYDODGE13", "1"));
			UNCANNYDODGE14= Double.parseDouble(Magic.getProperty("UNCANNYDODGE14", "1"));
			UNCANNYDODGE15= Double.parseDouble(Magic.getProperty("UNCANNYDODGE15", "1"));
			UNCANNYDODGE16= Double.parseDouble(Magic.getProperty("UNCANNYDODGE16", "1"));
			UNCANNYDODGE17= Double.parseDouble(Magic.getProperty("UNCANNYDODGE17", "1"));
			UNCANNYDODGE18= Double.parseDouble(Magic.getProperty("UNCANNYDODGE18", "1"));
			UNCANNYDODGE19= Double.parseDouble(Magic.getProperty("UNCANNYDODGE19", "1"));
			
			MIRRORIMAGE = Double.parseDouble(Magic.getProperty("MIRRORIMAGE", "1"));
			POLLUTE_WATERT = Double.parseDouble(Magic.getProperty("POLLUTEWATERT", "1"));
			WIND_SHACKLET = Double.parseDouble(Magic.getProperty("WINDSHACKLET", "1"));
			ARMOR_BRAKET = Double.parseDouble(Magic.getProperty("ARMORBRAKET", "1"));
			아머곱연산확률 = Double.parseDouble(Magic.getProperty("ARMORBRAKET77", "1"));
			아머기본 =  Double.parseDouble(Magic.getProperty("nesarmorp", "1"));
			GUARD_BREAKT = Double.parseDouble(Magic.getProperty("GUARDBREAKT", "1"));
			FEAR_T = Double.parseDouble(Magic.getProperty("FEART", "1"));

			THUNDER_GRABT = Double.parseDouble(Magic.getProperty("THUNDERGRABT", "1"));
			HORROR_OF_DEATHT = Double.parseDouble(Magic.getProperty("HORROROFDEATHT", "1"));
			CONFUSION_PHANTASMT = Double.parseDouble(Magic.getProperty("CONFUSIONPHANTASMT", "1"));
			POWERRIP_T = Double.parseDouble(Magic.getProperty("POWERRIPT", "1"));
			TOMAHAWKPoint_T = Double.parseDouble(Magic.getProperty("TOMAHAWKPointT", "1"));
	
			포우스턴몹 = Integer.parseInt(Magic.getProperty("Foumon", "5"));
			
			STRIKERGALEs = Integer.parseInt(Magic.getProperty("STRIKERGALE", "5"));
			EMPIREHIT_TO_LEVEL = Double.parseDouble(Magic.getProperty("EmpireHitToLevel", "5"));
			STUNHIT_TO_LEVEL = Double.parseDouble(Magic.getProperty("StunHitToLevel", "5"));
			STUNHIT_TO_LEVELL = Double.parseDouble(Magic.getProperty("StunHitToLevell", "5"));
			STUNHIT_TO_LEVEL1 = Double.parseDouble(Magic.getProperty("StunHitToLevel1", "5"));
			HORRORHIT_TO_LEVEL = Double.parseDouble(Magic.getProperty("HorrorHitToLevel", "5"));
			DESTROYHIT_TO_LEVEL = Double.parseDouble(Magic.getProperty("DestroyHitToLevel", "5"));
			CHARACTER_MAGICHIT_RATE = Double.parseDouble(Magic.getProperty("CharacterMagicHitRate", "0.01"));
			CHARACTER_MAGICCRI_RATE = Double.parseDouble(Magic.getProperty("CharacterMagicCriticalRate", "1.5"));
			가드확률 = Double.parseDouble(Magic.getProperty("GUARDBREAKT1", "50.0"));
			그립확률 = Double.parseDouble(Magic.getProperty("GRIP1", "50.0"));
			MISSILE_CRITICAL_DAMAGE_RATE = Double.parseDouble(Magic.getProperty("MissileCriticalDamageRate", "1.5"));
			MAGIC_CRITICAL_DAMAGE_RATE = Double.parseDouble(Magic.getProperty("MAGICCriticalDamageRate", "1.5"));
			MELEE_CRITICAL_DAMAGE_RATE = Double.parseDouble(Magic.getProperty("MeleeCriticalDamageRate", "1.5"));
			REDUCTION_IGNORE_DAMAGERATE = Double.parseDouble(Magic.getProperty("ReductionIgnoreDamageRate", "0.5"));
			
			
			게일샷 = Integer.parseInt(Magic.getProperty("Gshot", "1"));
			게일샷몹 = Integer.parseInt(Magic.getProperty("Gshot1", "1"));
			게일샷확률 = Integer.parseInt(Magic.getProperty("Gshot2", "1"));
			게일디버프  = Double.parseDouble(Magic.getProperty("Gdbf", "0.5"));
			EMPIRE = Double.parseDouble(Magic.getProperty("Empire", "1"));
			SHOCK_STUN = Double.parseDouble(Magic.getProperty("ShockStun", "1"));
			SHOCK_STUN1 = Double.parseDouble(Magic.getProperty("ShockStun1", "1"));
			DESPERADO = Integer.parseInt(Magic.getProperty("DESPERADO", "1"));
			BONE_BREAK = Double.parseDouble(Magic.getProperty("BONEBREAK", "1"));
			DISINT_CHAOTIC_WEIGHT = Integer.parseInt(Magic.getProperty("DisInt_Chaotic_Weight", "5000"));
			DISINT_LAWFUL_WEIGHT = Integer.parseInt(Magic.getProperty("DisInt_Lawful_Weight", "200"));
			DISINT_MAX_DMG = Integer.parseInt(Magic.getProperty("DisInt_MaxDamage", "1500"));
			DISINT_MIN_DMG = Integer.parseInt(Magic.getProperty("DisInt_MinDamage", "200"));
			TurnChant80UP = Integer.parseInt(Magic.getProperty("turnchant80up", "1"));
			TurnChant76UP = Integer.parseInt(Magic.getProperty("turnchant76up", "1"));
			TurnChant75UP = Integer.parseInt(Magic.getProperty("turnchant75up", "1"));
			TurnChant74UP = Integer.parseInt(Magic.getProperty("turnchant74up", "1"));
			TurnChant73UP = Integer.parseInt(Magic.getProperty("turnchant73up", "1"));
			TurnChant72UP = Integer.parseInt(Magic.getProperty("turnchant72up", "1"));
			TurnChant71UP = Integer.parseInt(Magic.getProperty("turnchant71up", "1"));
			TurnChant70UP = Integer.parseInt(Magic.getProperty("turnchant70up", "1"));
			TurnChant69UP = Integer.parseInt(Magic.getProperty("turnchant69up", "1"));
			TurnChant68UP = Integer.parseInt(Magic.getProperty("turnchant68up", "1"));
			TurnChant67UP = Integer.parseInt(Magic.getProperty("turnchant67up", "1"));
			TurnChant66UP = Integer.parseInt(Magic.getProperty("turnchant66up", "1"));
			TurnChant65UP = Integer.parseInt(Magic.getProperty("turnchant65up", "1"));
			TurnChant64UP = Integer.parseInt(Magic.getProperty("turnchant64up", "1"));
			TurnChant63UP = Integer.parseInt(Magic.getProperty("turnchant63up", "1"));
			TurnChant62UP = Integer.parseInt(Magic.getProperty("turnchant62up", "1"));
			TurnChant61UP = Integer.parseInt(Magic.getProperty("turnchant61up", "1"));
			TurnChant60UP = Integer.parseInt(Magic.getProperty("turnchant60up", "1"));
			TurnChant59UP = Integer.parseInt(Magic.getProperty("turnchant59up", "1"));
			TurnChant58UP = Integer.parseInt(Magic.getProperty("turnchant58up", "1"));
			TurnChant57UP = Integer.parseInt(Magic.getProperty("turnchant57up", "1"));
			TurnChant56UP = Integer.parseInt(Magic.getProperty("turnchant56up", "1"));
			TurnChant55UP = Integer.parseInt(Magic.getProperty("turnchant55up", "1"));
			연스턴 = Boolean.parseBoolean(Magic.getProperty("STUNtrs", "true"));
		    연데페 = Boolean.parseBoolean(Magic.getProperty("STDNtrs", "false"));
		    토마호크배율 = Integer.parseInt(Magic.getProperty("toma", "15"));
		    토마호크배율1 = Integer.parseInt(Magic.getProperty("toma1", "150"));
		    
		    버서크시간 = Integer.parseInt(Magic.getProperty("BSK", "15"));
		    버서크대기시간 = Integer.parseInt(Magic.getProperty("BSK1", "15"));
		    프라임대기시간 = Integer.parseInt(Magic.getProperty("Prime", "10"));
		    포텐셜대기시간 = Integer.parseInt(Magic.getProperty("PTS", "10"));
		    어드밴스대기 = Integer.parseInt(Magic.getProperty("ADV", "10"));
		    쉐도우대기 = Integer.parseInt(Magic.getProperty("SDW", "10"));
		    이터니티대기 = Integer.parseInt(Magic.getProperty("ITwait", "10"));
		    이터니티배율 = Integer.parseInt(Magic.getProperty("ITsp", "10"));
		    어드밴스레벨 = Integer.parseInt(Magic.getProperty("ADVlevel", "10"));
			TurnChantElf = Integer.parseInt(Magic.getProperty("TurnChantElf", "1"));
			crashFo = Integer.parseInt(Magic.getProperty("crashFor", "1"));
			crashdmg = Double.parseDouble(Magic.getProperty("crashDMG", "1"));
			furyFo = Integer.parseInt(Magic.getProperty("furyFor", "1"));
			furydmg = Double.parseDouble(Magic.getProperty("FuryDMG", "1"));
			Counter = Double.parseDouble(Magic.getProperty("CounterR", "1"));
			RockDMG = Double.parseDouble(Magic.getProperty("RockDMGR", "1"));
			Finalburnpc = Double.parseDouble(Magic.getProperty("Finalburnpc", "1"));
			Finalburnnpc = Double.parseDouble(Magic.getProperty("Finalburnnpc", "1"));
			붉이더블 = Double.parseDouble(Magic.getProperty("DOUBLEBREAKCHANCE1", "1"));
			BURNING_SPIRIT_PC = Double.parseDouble(Magic.getProperty("BURNINGSPIRITPC", "1"));
			BURNING_SPIRIT_NPC = Double.parseDouble(Magic.getProperty("BURNINGSPIRITPCNPC", "1"));
			DOUBLE_DMG = Double.parseDouble(Magic.getProperty("DOUBLEDMG", "1"));
			DOUBLE_BREAK_DESTINY_PC = Double.parseDouble(Magic.getProperty("DOUBLEBREAKDESTINYPC", "1"));
			DOUBLE_BREAK_DESTINY_NPC = Double.parseDouble(Magic.getProperty("DOUBLEBREAKDESTINYNPC", "1"));
			BLOW_ATTACKDMG = Double.parseDouble(Magic.getProperty("BLOWATTACKDMG", "10"));

			sim_levelmin1 = Integer.parseInt(Magic.getProperty("simlevelmin1", "1"));
			sim_levelmax1 = Integer.parseInt(Magic.getProperty("simlevelmax1", "1"));

			sim_levelmin2 = Integer.parseInt(Magic.getProperty("simlevelmin2", "1"));
			sim_levelmax2 = Integer.parseInt(Magic.getProperty("simlevelmax2", "1"));

			sim_levelmin3 = Integer.parseInt(Magic.getProperty("simlevelmin3", "1"));
			sim_levelmax3 = Integer.parseInt(Magic.getProperty("simlevelmax3", "1"));

			sim_levelmin4 = Integer.parseInt(Magic.getProperty("simlevelmin4", "1"));
			sim_levelmax4 = Integer.parseInt(Magic.getProperty("simlevelmax4", "1"));

			sim_levelmin5 = Integer.parseInt(Magic.getProperty("simlevelmin5", "1"));
			sim_levelmax5 = Integer.parseInt(Magic.getProperty("simlevelmax5", "1"));

			sim_levelmin6 = Integer.parseInt(Magic.getProperty("simlevelmin6", "1"));
			sim_levelmax6 = Integer.parseInt(Magic.getProperty("simlevelmax6", "1"));
			약점노출 = Integer.parseInt(Magic.getProperty("Sworld12", "2"));
			simsimDmg1 = Double.parseDouble(Magic.getProperty("simsimDmg1", "3"));
			simsimDmg2 = Double.parseDouble(Magic.getProperty("simsimDmg2", "3"));
			simsimDmg3 = Double.parseDouble(Magic.getProperty("simsimDmg3", "3"));
			simsimDmg4 = Double.parseDouble(Magic.getProperty("simsimDmg4", "3"));
			simsimDmg5 = Double.parseDouble(Magic.getProperty("simsimDmg5", "3"));
			simsimDmg6 = Double.parseDouble(Magic.getProperty("simsimDmg6", "3"));
			할파스갑옷확률 = Integer.parseInt(Magic.getProperty("Halpas1", "5"));
			할파스쿨타임 = Integer.parseInt(Magic.getProperty("Halpasenchant", "5"));
			할파스PVP쿨타임 = Integer.parseInt(Magic.getProperty("Halpascoolpvp", "5"));
			할파스가호 = Integer.parseInt(Magic.getProperty("Halpascool1", "5"));
			할파스신의 = Integer.parseInt(Magic.getProperty("Halpas2", "5"));
			할파스신의PVP = Integer.parseInt(Magic.getProperty("Halpas3", "5"));
			아인하사드인챈트 = Integer.parseInt(Magic.getProperty("Inenchant", "2"));
			아인데미지인챈트 = Integer.parseInt(Magic.getProperty("Inenchantdmg", "2"));
			아인데미지인챈트1 = Integer.parseInt(Magic.getProperty("Inenchantdmg1", "2"));
			그랑데미지인챈트 = Integer.parseInt(Magic.getProperty("Grenchantdmg", "2"));
			그랑데미지인챈트1 = Integer.parseInt(Magic.getProperty("Grenchantdmg1", "2"));
			아인최소 = Integer.parseInt(Magic.getProperty("Inmin", "2"));
			아인최대 = Integer.parseInt(Magic.getProperty("Inmax", "2"));
			아인하사드인챈트1 = Integer.parseInt(Magic.getProperty("Inenchant1", "2"));
			아인최소1 = Integer.parseInt(Magic.getProperty("Inmin1", "2"));
			아인최대1 = Integer.parseInt(Magic.getProperty("Inmax1", "2"));
			그랑카인인챈트 = Integer.parseInt(Magic.getProperty("GRenchant", "2"));
			그랑최소 = Integer.parseInt(Magic.getProperty("GRmin", "2"));
			그랑최대 = Integer.parseInt(Magic.getProperty("GRmax", "2"));
			그랑카인인챈트트 = Integer.parseInt(Magic.getProperty("GRenchantt", "2"));
			그랑카인인챈트트1 = Integer.parseInt(Magic.getProperty("GRenchant11", "2"));
			
			
			카배회복량 = Double.parseDouble(Magic.getProperty("CBmaster", "2"));
			
			아머데미지 = Double.parseDouble(Magic.getProperty("amordmg", "0.38"));
			아머데미지타인 = Double.parseDouble(Magic.getProperty("amordmgto", "0.20"));
			
			아인고통인챈 = Integer.parseInt(Magic.getProperty("InGT1", "2"));
			아인고통기본 = Integer.parseInt(Magic.getProperty("InGT2", "2"));
			아인고통스턴 = Magic.getProperty("InGT3", "1000,1400,1800,2200,2600,3000,3400,3800");
			 
			
			고통최소 = Integer.parseInt(Magic.getProperty("GT1", "2"));
			고통최대 = Integer.parseInt(Magic.getProperty("GT2", "2"));
			고통인챈트 = Integer.parseInt(Magic.getProperty("GT3", "2"));
			고통인챈트트 = Integer.parseInt(Magic.getProperty("GT4", "2"));
			고통데미지인챈트 = Integer.parseInt(Magic.getProperty("GT5", "2"));
			
			결심최소 = Integer.parseInt(Magic.getProperty("GS1", "2"));
			결심최대 = Integer.parseInt(Magic.getProperty("GS2", "2"));
			결심인챈트 = Integer.parseInt(Magic.getProperty("GS3", "2"));
			결심인챈트트 = Integer.parseInt(Magic.getProperty("GS4", "2"));
			결심데미지인챈트 = Integer.parseInt(Magic.getProperty("GS5", "2"));
			
			환상최소 = Integer.parseInt(Magic.getProperty("GH1", "2"));
			환상최대 = Integer.parseInt(Magic.getProperty("GH2", "2"));
			환상인챈트 = Integer.parseInt(Magic.getProperty("GH3", "2"));
			환상인챈트트 = Integer.parseInt(Magic.getProperty("GH4", "2"));
			환상데미지인챈트 = Integer.parseInt(Magic.getProperty("GH5", "2"));
			
			공포최소 = Integer.parseInt(Magic.getProperty("GP1", "2"));
			공포최대 = Integer.parseInt(Magic.getProperty("GP2", "2"));
			공포인챈트 = Integer.parseInt(Magic.getProperty("GP3", "2"));
			공포인챈트트 = Integer.parseInt(Magic.getProperty("GP4", "2"));
			공포데미지인챈트 = Integer.parseInt(Magic.getProperty("GP5", "2"));
			
			고독최소 = Integer.parseInt(Magic.getProperty("GQ1", "2"));
			고독최대 = Integer.parseInt(Magic.getProperty("GQ2", "2"));
			고독인챈트 = Integer.parseInt(Magic.getProperty("GQ3", "2"));
			고독인챈트트 = Integer.parseInt(Magic.getProperty("GQ4", "2"));
			고독데미지인챈트 = Integer.parseInt(Magic.getProperty("GQ5", "2"));
			
			
			스턴최소 = Integer.parseInt(Magic.getProperty("STmin", "2"));
			데페최소 = Integer.parseInt(Magic.getProperty("DPmin", "2"));
			엠파최소 = Integer.parseInt(Magic.getProperty("EMmin", "2"));
			본브최소 = Integer.parseInt(Magic.getProperty("BBmin", "2"));
			포우최소 = Integer.parseInt(Magic.getProperty("FOUmin", "2"));
			네쥬스턴최소 = Integer.parseInt(Magic.getProperty("nesmin", "20"));
			네쥬스턴최대 = Integer.parseInt(Magic.getProperty("nesmax", "60"));
			아인하사드인챈트트 = Integer.parseInt(Magic.getProperty("Inenchantt", "2"));
			아인하사드인챈트트1 = Integer.parseInt(Magic.getProperty("Inenchant11", "2"));
			
			
			에바인챈트 = Integer.parseInt(Magic.getProperty("EVenchant", "2"));
			에바최소 = Integer.parseInt(Magic.getProperty("EVmin", "2"));
			에바최대 = Integer.parseInt(Magic.getProperty("EVmax", "2"));
			에바데미지인챈트 = Integer.parseInt(Magic.getProperty("EVenchantdmg", "2"));
			
			
			에바데미지인챈트1 = Integer.parseInt(Magic.getProperty("EVenchant1", "2"));
			
			
			그랑카인인챈트1 = Integer.parseInt(Magic.getProperty("GRenchant1", "2"));
			그랑최소1 = Integer.parseInt(Magic.getProperty("GRmin1", "2"));
			그랑최대1 = Integer.parseInt(Magic.getProperty("GRmax1", "2"));
			내성배율 = Integer.parseInt(Magic.getProperty("Res1", "2"));
			
			래스트 = Integer.parseInt(Magic.getProperty("BoneR", "2"));
			램페이지확률 = Integer.parseInt(Magic.getProperty("Ramran", "2"));
			램페이지데미지 = Double.parseDouble(Magic.getProperty("Ramdmg", "3"));
			
			할파스확률 = Integer.parseInt(Magic.getProperty("HPS", "2"));
			할파스데미지 = Double.parseDouble(Magic.getProperty("HPSdmg", "1.0"));
			
			할파스근뎀 = Integer.parseInt(Magic.getProperty("hal1", "2"));
			할파스근명 = Integer.parseInt(Magic.getProperty("hal2", "2"));
			할파스원뎀 = Integer.parseInt(Magic.getProperty("hal3", "2"));
			할파스원명 = Integer.parseInt(Magic.getProperty("hal4", "2"));
			할파스pvp뎀 = Integer.parseInt(Magic.getProperty("hal5", "2"));
			할파스pvp리덕 = Integer.parseInt(Magic.getProperty("hal6", "2"));
			할파스맥스피 = Integer.parseInt(Magic.getProperty("hal7", "2"));
			할파스맥스엠피 = Integer.parseInt(Magic.getProperty("hal8", "2"));
			할파스스펠 = Integer.parseInt(Magic.getProperty("hal9", "2"));
			할파스추방 = Integer.parseInt(Magic.getProperty("hal10", "2"));
			할파스올적중 = Integer.parseInt(Magic.getProperty("hal11", "2"));
			할파스올레즈 = Integer.parseInt(Magic.getProperty("hal12", "2"));
			할파스축복효율 = Integer.parseInt(Magic.getProperty("hal13", "2"));
			할파스추가경험치 = Integer.parseInt(Magic.getProperty("hal14", "2"));
			할파스마법명중 = Integer.parseInt(Magic.getProperty("hal15", "2"));
			
			아우라근뎀 = Integer.parseInt(Magic.getProperty("aura1", "2"));
			아우라근명 = Integer.parseInt(Magic.getProperty("aura2", "2"));
			아우라원뎀 = Integer.parseInt(Magic.getProperty("aura3", "2"));
			아우라원명 = Integer.parseInt(Magic.getProperty("aura4", "2"));
			아우라pvp뎀 = Integer.parseInt(Magic.getProperty("aura5", "2"));
			아우라pvp리덕 = Integer.parseInt(Magic.getProperty("aura6", "2"));
			아우라맥스피 = Integer.parseInt(Magic.getProperty("aura7", "2"));
			아우라맥스엠피 = Integer.parseInt(Magic.getProperty("aura8", "2"));
			아우라스펠 = Integer.parseInt(Magic.getProperty("aura9", "2"));
			아우라추방 = Integer.parseInt(Magic.getProperty("aura10", "2"));
			아우라올적중 = Integer.parseInt(Magic.getProperty("aura11", "2"));
			아우라올레즈 = Integer.parseInt(Magic.getProperty("aura12", "2"));
			아우라축복효율 = Integer.parseInt(Magic.getProperty("aura13", "2"));
			아우라추가경험치 = Integer.parseInt(Magic.getProperty("aura14", "2"));
			아우라마법명중 = Integer.parseInt(Magic.getProperty("aura15", "2"));
			
			베히근뎀 = Integer.parseInt(Magic.getProperty("behe1", "2"));
			베히근명 = Integer.parseInt(Magic.getProperty("behe2", "2"));
			베히원뎀 = Integer.parseInt(Magic.getProperty("behe3", "2"));
			베히원명 = Integer.parseInt(Magic.getProperty("behe4", "2"));
			베히pvp뎀 = Integer.parseInt(Magic.getProperty("behe5", "2"));
			베히pvp리덕 = Integer.parseInt(Magic.getProperty("behe6", "2"));
			베히맥스피 = Integer.parseInt(Magic.getProperty("behe7", "2"));
			베히맥스엠피 = Integer.parseInt(Magic.getProperty("behe8", "2"));
			베히스펠 = Integer.parseInt(Magic.getProperty("behe9", "2"));
			베히추방 = Integer.parseInt(Magic.getProperty("behe10", "2"));
			베히올적중 = Integer.parseInt(Magic.getProperty("behe11", "2"));
			베히올레즈 = Integer.parseInt(Magic.getProperty("behe12", "2"));
			베히축복효율 = Integer.parseInt(Magic.getProperty("behe13", "2"));
			베히추가경험치 = Integer.parseInt(Magic.getProperty("behe14", "2"));
			베히마법명중 = Integer.parseInt(Magic.getProperty("behe15", "2"));
			드슬근뎀 = Integer.parseInt(Magic.getProperty("desel1", "2"));
			드슬근명 = Integer.parseInt(Magic.getProperty("desel2", "2"));
			드슬원뎀 = Integer.parseInt(Magic.getProperty("desel3", "2"));
			드슬원명 = Integer.parseInt(Magic.getProperty("desel4", "2"));
			드슬pvp뎀 = Integer.parseInt(Magic.getProperty("desel5", "2"));
			드슬pvp리덕 = Integer.parseInt(Magic.getProperty("desel6", "2"));
			드슬맥스피 = Integer.parseInt(Magic.getProperty("desel7", "2"));
			드슬맥스엠피 = Integer.parseInt(Magic.getProperty("desel8", "2"));
			드슬스펠 = Integer.parseInt(Magic.getProperty("desel9", "2"));
			드슬추방 = Integer.parseInt(Magic.getProperty("desel10", "2"));
			드슬올적중 = Integer.parseInt(Magic.getProperty("desel11", "2"));
			드슬올레즈 = Integer.parseInt(Magic.getProperty("desel12", "2"));
			드슬축복효율 = Integer.parseInt(Magic.getProperty("desel13", "2"));
			드슬추가경험치 = Integer.parseInt(Magic.getProperty("desel14", "2"));
			드슬마법명중 = Integer.parseInt(Magic.getProperty("desel15", "2"));
			
			이터기본 = Integer.parseInt(Magic.getProperty("ITN1", "2"));
			이터양 = Integer.parseInt(Magic.getProperty("ITN2", "2"));
			이터음 = Integer.parseInt(Magic.getProperty("ITN3", "2"));
			이터이차최소 = Integer.parseInt(Magic.getProperty("ITN4", "2"));
			이터이차최대 = Integer.parseInt(Magic.getProperty("ITN5", "2"));
			이터몬스터 = Integer.parseInt(Magic.getProperty("ITN6", "2"));
			
			
			네메시스기본 = Integer.parseInt(Magic.getProperty("NM1", "2"));
			네메시스양 = Integer.parseInt(Magic.getProperty("NM2", "2"));
			네메시스음 = Integer.parseInt(Magic.getProperty("NM3", "2"));
			네메시스몬스터  = Integer.parseInt(Magic.getProperty("NM4", "2"));
			
			쉐도우기본 = Integer.parseInt(Magic.getProperty("SDW1", "2"));
			쉐도우양 = Integer.parseInt(Magic.getProperty("SDW2", "2"));
			쉐도우음 = Integer.parseInt(Magic.getProperty("SDW3", "2"));
			쉐도우제약 = Integer.parseInt(Magic.getProperty("SDW7", "2"));
			쉐도우몬스터  = Integer.parseInt(Magic.getProperty("SDW6", "2"));
			 
			매직실드대기 = Integer.parseInt(Magic.getProperty("MagicS", "10"));
			매직실드지속 = Integer.parseInt(Magic.getProperty("MagicSS", "10"));
			매직실드확률 = Integer.parseInt(Magic.getProperty("MagicS1", "10"));
		
			
			엘븐스트라이크몬스터 = Integer.parseInt(Magic.getProperty("EVS5", "10"));
			엘븐스트라이크대기 = Integer.parseInt(Magic.getProperty("EVS", "10"));
			엘븐스트라이크기본 = Integer.parseInt(Magic.getProperty("EVS1", "2"));
			엘븐스트라이크양 = Integer.parseInt(Magic.getProperty("EVS2", "2"));
			엘븐스트라이크음 = Integer.parseInt(Magic.getProperty("EVS3", "2"));
			STUN_SKILL_RND_TIME111 = Magic.getProperty("StunSkillRndTime111", "1000,1400,1800,2200,2600,3000,3400,3800");
			엘븐스트라이크배율  = Integer.parseInt(Magic.getProperty("EVS4", "2"));
			
			
			이터제약 = Integer.parseInt(Magic.getProperty("ITN7", "2"));
			어벤저대기   = Integer.parseInt(Magic.getProperty("AVG1", "2"));
			어벤저확률  = Integer.parseInt(Magic.getProperty("AVG", "2"));
			어벤저배율  = Integer.parseInt(Magic.getProperty("AVG2", "2"));
			
			데몰리션엠피   = Integer.parseInt(Magic.getProperty("DM4", "2"));
			데몰리션대기   = Integer.parseInt(Magic.getProperty("DM1", "2"));
			데몰리션대미지   = Integer.parseInt(Magic.getProperty("DM3", "2"));
			STUNHIT_TO_LEVEL11 = Double.parseDouble(Magic.getProperty("StunHitToLevel11", "5"));
			데페기본확률  = Double.parseDouble(Magic.getProperty("DM2", "1"));
			
			STUN_SKILL_RND_TIME11 = Magic.getProperty("StunSkillRndTime11", "1000,1400,1800,2200,2600,3000,3400,3800");
			
			리버레이션절감  = Double.parseDouble(Magic.getProperty("RV", "0.5"));
			리버레이션대기   = Integer.parseInt(Magic.getProperty("RV1", "2"));
			리버레이션지속   = Integer.parseInt(Magic.getProperty("RV2", "2"));
			매스이뮨대기시간  = Integer.parseInt(Magic.getProperty("MSIM", "2"));
			
			
			버닝샷   = Integer.parseInt(Magic.getProperty("BN", "2"));
			버닝샷대기   = Integer.parseInt(Magic.getProperty("BNwait", "2"));
			버닝샷칸   = Integer.parseInt(Magic.getProperty("BN12", "2"));
			버닝샷대미지  = Double.parseDouble(Magic.getProperty("BN13", "2"));
			
			매스이뮨엠피  = Integer.parseInt(Magic.getProperty("MSIM1", "2"));
			매스이뮨마돌  = Integer.parseInt(Magic.getProperty("MSIM2", "2"));
			메디테이션엠피   = Integer.parseInt(Magic.getProperty("MEDI", "2"));
			메디테이션엠피1   = Integer.parseInt(Magic.getProperty("MEDI1", "2"));
			
			비전대기시간  = Integer.parseInt(Magic.getProperty("VSw", "2"));
			비전엠피  = Integer.parseInt(Magic.getProperty("VSmp", "2"));
			
			이뮨투함블레이드레벨  = Integer.parseInt(Magic.getProperty("IMB", "2"));
			
			
			이뮨투함블레이드양  = Integer.parseInt(Magic.getProperty("IMLV1", "2"));
			이뮨투함블레이드음  = Integer.parseInt(Magic.getProperty("IMLV2", "2"));
			이뮨투함블레이드대기  = Integer.parseInt(Magic.getProperty("IMB1", "2"));
			이뮨투함블레이드지속  = Integer.parseInt(Magic.getProperty("IMB2", "2"));
			
			
			이뮨투함블레이드군주피  = Integer.parseInt(Magic.getProperty("IMB3", "2"));
			이뮨투함블레이드기사피  = Integer.parseInt(Magic.getProperty("IMB4", "2"));
			이뮨투함블레이드요정피  = Integer.parseInt(Magic.getProperty("IMB5", "2"));
			이뮨투함블레이드법사피  = Integer.parseInt(Magic.getProperty("IMB6", "2"));
			이뮨투함블레이드다엘피  = Integer.parseInt(Magic.getProperty("IMB7", "2"));
			이뮨투함블레이드용기사피  = Integer.parseInt(Magic.getProperty("IMB8", "2"));
			이뮨투함블레이드환술피  = Integer.parseInt(Magic.getProperty("IMB9", "2"));
			이뮨투함블레이드전사피  = Integer.parseInt(Magic.getProperty("IMB10", "2"));
			
			이뮨투함블레이드군주확률  = Integer.parseInt(Magic.getProperty("IMBB1", "2"));
			이뮨투함블레이드기사확률  = Integer.parseInt(Magic.getProperty("IMBB2", "2"));
			이뮨투함블레이드요정활확률  = Integer.parseInt(Magic.getProperty("IMBB3", "2"));
			이뮨투함블레이드요정검확률  = Integer.parseInt(Magic.getProperty("IMBB33", "2"));
			이뮨투함블레이드법사확률  = Integer.parseInt(Magic.getProperty("IMBB4", "2"));
			이뮨투함블레이드다엘확률  = Integer.parseInt(Magic.getProperty("IMBB5", "2"));
			이뮨투함블레이드용기사확률  = Integer.parseInt(Magic.getProperty("IMBB6", "2"));
			이뮨투함블레이드환술확률  = Integer.parseInt(Magic.getProperty("IMBB7", "2"));
			이뮨투함블레이드전사확률  = Integer.parseInt(Magic.getProperty("IMBB8", "2"));
			
			에테리얼수치  = Double.parseDouble(Magic.getProperty("ETdmg", "2"));
			에테리얼랜덤  = Integer.parseInt(Magic.getProperty("ETrnd", "2"));
			디케이포션  = Integer.parseInt(Magic.getProperty("DCY", "50"));
			몹레벨  = Integer.parseInt(Magic.getProperty("Moblv", "60"));
		} catch (Exception e) {
			_log.log(Level.SEVERE, "Config.에서 에러가 발생했습니다.", e);
			throw new Error("Failed to Load " + Magic_SETTINGS_CONFIG_FILE + " File.");
		}

		try {
			Properties ServerCodes = new Properties();
			InputStream is = new FileInputStream(new File(ServerCodes_SETTINGS_CONFIG_FILE));
			ServerCodes.load(is);
			is.close();
			DROPMENT_ITEM.clear();
			String ments = ServerCodes.getProperty("Dropmentitem", "").trim();
			if(!GameUtils.isNullOrEmpty(ments)){
				String[] mentArray = ments.split(",");
				for(int i=mentArray.length - 1; i>=0; --i){
					int itemId = Integer.parseInt(mentArray[i]);
					DROPMENT_ITEM.put(itemId, itemId);
				}
			}
			원격맵 = ServerCodes.getProperty("Trademap", "");
			불멸가호아덴 = Integer.parseInt(ServerCodes.getProperty("Gahoaden", "5"));
			불멸가호아덴1 = Integer.parseInt(ServerCodes.getProperty("Gahoaden1", "5"));
			불멸가호아덴2 = Integer.parseInt(ServerCodes.getProperty("Gahoaden2", "5"));
			불멸가호아덴22 = Integer.parseInt(ServerCodes.getProperty("Gahoaden22", "5"));
			오토루팅가호 = Boolean.parseBoolean(ServerCodes.getProperty("Autogaho", "false"));
			IS_CHECKBOX_RULES_USE = Boolean.parseBoolean(ServerCodes.getProperty("IsCheckBoxRulesUse", "true"));
			IS_VALIDSHOP = Boolean.parseBoolean(ServerCodes.getProperty("IsValidShopSystem", "true"));
			IS_VALIDITEMID = Boolean.parseBoolean(ServerCodes.getProperty("IsValidItemId", "true"));
			// TODO ServerCodes.properties 셋팅
			RedKnight_dieCount = Integer.parseInt(ServerCodes.getProperty("RedKnightdieCount", "5"));
			Arden = Boolean.parseBoolean(ServerCodes.getProperty("ArdenTo", "false"));
			아덴상점엔샵아덴샵설정 = Double.parseDouble(ServerCodes.getProperty("Adentype", "5"));
			ENCHANT_MAX_FAIL = Boolean.parseBoolean(ServerCodes.getProperty("EnchantMaxFail", "true"));
			MASTERENCHANTMESS = Boolean.parseBoolean(ServerCodes.getProperty("MasterEnchantMess", "true"));
			Master_Enchant = Integer.parseInt(ServerCodes.getProperty("MasterEnchant", "5"));
			무기1 = Integer.parseInt(ServerCodes.getProperty("WeaponE1", "0"));
			무기2 = Integer.parseInt(ServerCodes.getProperty("WeaponE2", "1"));
			무기3 = Integer.parseInt(ServerCodes.getProperty("WeaponE3", "1"));
			무기4 = Integer.parseInt(ServerCodes.getProperty("WeaponE4", "2"));
			무기5 = Integer.parseInt(ServerCodes.getProperty("WeaponE5", "2"));
			무기6 = Integer.parseInt(ServerCodes.getProperty("WeaponE6", "3"));
			무기7 = Integer.parseInt(ServerCodes.getProperty("WeaponE7", "3"));
			무기8 = Integer.parseInt(ServerCodes.getProperty("WeaponE8", "4"));
			무기9 = Integer.parseInt(ServerCodes.getProperty("WeaponE9", "4"));
			
			안전무기1 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE1", "0"));
			안전무기2 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE2", "1"));
			안전무기3 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE3", "1"));
			안전무기4 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE4", "2"));
			안전무기5 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE5", "2"));
			안전무기6 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE6", "3"));
			안전무기7 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE7", "3"));
			안전무기8 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE8", "4"));
			안전무기9 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE9", "4"));
			안전무기10 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE10", "4"));
			안전무기11 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE11", "4"));
			안전무기12 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE12", "4"));
			안전무기13 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE13", "4"));
			안전무기14 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE14", "4"));
			안전무기15 = Integer.parseInt(ServerCodes.getProperty("SafeWeaponE15", "4"));
			
			
			방어구1 = Integer.parseInt(ServerCodes.getProperty("ArmorE1", "0"));
			방어구2 = Integer.parseInt(ServerCodes.getProperty("ArmorE2", "1"));
			방어구3 = Integer.parseInt(ServerCodes.getProperty("ArmorE3", "1"));
			방어구4 = Integer.parseInt(ServerCodes.getProperty("ArmorE4", "2"));
			방어구5 = Integer.parseInt(ServerCodes.getProperty("ArmorE5", "2"));
			방어구6 = Integer.parseInt(ServerCodes.getProperty("ArmorE6", "3"));
			방어구7 = Integer.parseInt(ServerCodes.getProperty("ArmorE7", "3"));
			방어구8 = Integer.parseInt(ServerCodes.getProperty("ArmorE8", "4"));
			방어구9 = Integer.parseInt(ServerCodes.getProperty("ArmorE9", "4"));
			
			안전방어구1 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE1", "0"));
			안전방어구2 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE2", "1"));
			안전방어구3 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE3", "1"));
			안전방어구4 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE4", "2"));
			안전방어구5 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE5", "2"));
			안전방어구6 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE6", "3"));
			안전방어구7 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE7", "3"));
			안전방어구8 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE8", "4"));
			안전방어구9 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE9", "4"));
			안전방어구10 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE10", "4"));
			안전방어구11 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE11", "4"));
			안전방어구12 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE12", "4"));
			안전방어구13 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE13", "4"));
			안전방어구14 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE14", "4"));
			안전방어구15 = Integer.parseInt(ServerCodes.getProperty("SafeArmorE15", "4"));
			
			악세사리1 = Integer.parseInt(ServerCodes.getProperty("Acc1", "0"));		
			악세사리2 = Integer.parseInt(ServerCodes.getProperty("Acc2", "1"));
			악세사리3 = Integer.parseInt(ServerCodes.getProperty("Acc3", "1"));
			악세사리4 = Integer.parseInt(ServerCodes.getProperty("Acc4", "2"));
			악세사리5 = Integer.parseInt(ServerCodes.getProperty("Acc5", "2"));
			악세사리6 = Integer.parseInt(ServerCodes.getProperty("Acc6", "3"));
			악세사리7 = Integer.parseInt(ServerCodes.getProperty("Acc7", "3"));
			악세사리8 = Integer.parseInt(ServerCodes.getProperty("Acc8", "1"));
			악세사리9 = Integer.parseInt(ServerCodes.getProperty("Acc9", "1"));
			악세사리10 = Integer.parseInt(ServerCodes.getProperty("Acc10", "1"));
			악세사리11 = Integer.parseInt(ServerCodes.getProperty("Acc11", "1"));
			악세사리12 = Integer.parseInt(ServerCodes.getProperty("Acc12", "1"));
			악세사리13 = Integer.parseInt(ServerCodes.getProperty("Acc13", "1"));
			악세사리14 = Integer.parseInt(ServerCodes.getProperty("Acc14", "1"));
			악세사리15 = Integer.parseInt(ServerCodes.getProperty("Acc15", "1"));

			방어구비법서확률 = Integer.parseInt(ServerCodes.getProperty("Armoruprate", "30"));

			영웅유물 = Integer.parseInt(ServerCodes.getProperty("Heroron", "3"));
			New_Cha = Integer.parseInt(ServerCodes.getProperty("NewCha", "1"));
			New_Cha1 = Integer.parseInt(ServerCodes.getProperty("NewCha1", "1"));
			경험치지급단 = Integer.parseInt(ServerCodes.getProperty("Expreturn", "75"));
			Npc_Max = Integer.parseInt(ServerCodes.getProperty("NpcMax", "70"));
			Pc_Reload = Integer.parseInt(ServerCodes.getProperty("PcReload", "70"));
			Npc_Location = Integer.parseInt(ServerCodes.getProperty("NpcLocation", "15"));
			Boss_Location = Integer.parseInt(ServerCodes.getProperty("BossLocation", "15"));
			COMBO_CHANCE = Integer.parseInt(ServerCodes.getProperty("ComboChance", "10"));
			BossGht_CHANCE = Double.parseDouble(ServerCodes.getProperty("BossGhtChance", "0.1"));
			무기인첸트 = Integer.parseInt(ServerCodes.getProperty("LimitWeapon", "13")); // 일반무기
			무기고급인첸트 = Integer.parseInt(ServerCodes.getProperty("LimitWeapon2", "5")); // 특수무기
			방어구인첸트 = Integer.parseInt(ServerCodes.getProperty("LimitArmor", "11")); // 일반갑옷
			방어구고급인첸트 = Integer.parseInt(ServerCodes.getProperty("LimitArmor2", "7")); // 특수아머
			룸티스 = Integer.parseInt(ServerCodes.getProperty("RoomT", "3"));
			스냅퍼 = Integer.parseInt(ServerCodes.getProperty("Snapper", "3"));
			용티 = Integer.parseInt(ServerCodes.getProperty("DragonT", "3"));
			악세사리 = Integer.parseInt(ServerCodes.getProperty("Accessory", "3"));
			accessoryLevel = Integer.parseInt(ServerCodes.getProperty("accessoryLevel", "3"));
			accessorytest = Integer.parseInt(ServerCodes.getProperty("Accessorytest", "3"));
			accessorytest1 = Integer.parseInt(ServerCodes.getProperty("Accessorytest1", "3"));
			문장류 = Integer.parseInt(ServerCodes.getProperty("sentence", "3"));
			휘장류 = Integer.parseInt(ServerCodes.getProperty("HJenchant", "3"));
			summon_hpdmg = Integer.parseInt(ServerCodes.getProperty("summonhpdmg", "100"));
			FEATHER_SHOP_NUM = Integer.parseInt(ServerCodes.getProperty("FeatherShopNum", "100000"));
			ACCOUNT_N_BUFF = Boolean.parseBoolean(ServerCodes.getProperty("AccountNBuff", "true"));
			LIMIT_N_BUFF = Integer.parseInt(ServerCodes.getProperty("LimitNBuff", "30"));
			TIME_N_BUFF = Integer.parseInt(ServerCodes.getProperty("TimeNBuff", "30"));
			/** NPC물리대미지/마법대미지 수정 **/
			npcdmg1 = Integer.parseInt(ServerCodes.getProperty("npcdmg1", "1"));
			npcdmg2 = Integer.parseInt(ServerCodes.getProperty("npcdmg2", "1"));
			npcdmg3 = Integer.parseInt(ServerCodes.getProperty("npcdmg3", "1"));
			npcdmg4 = Integer.parseInt(ServerCodes.getProperty("npcdmg4", "1"));
			pcnpcmagicdmg = Integer.parseInt(ServerCodes.getProperty("pcnpcmagicdmg", "14"));
			New_MagicDmg = Double.parseDouble(ServerCodes.getProperty("NewMagicDmg", "1"));

			BugBug = Integer.parseInt(ServerCodes.getProperty("BugBug", "1"));
			BugBug1 = Integer.parseInt(ServerCodes.getProperty("BugBug1", "1"));
			tamsc = Integer.parseInt(ServerCodes.getProperty("tamsc", "0"));
			tamsc1 = Integer.parseInt(ServerCodes.getProperty("tamsc1", "0"));
			tamsc2 = Integer.parseInt(ServerCodes.getProperty("tamsc2", "0"));
			허수아비지급 = Boolean.parseBoolean(ServerCodes.getProperty("Scon", "false"));
			SCARELEVEL = Integer.parseInt(ServerCodes.getProperty("ScareLevel", "5"));
			Characters_CharSlot = Integer.parseInt(ServerCodes.getProperty("CharactersCharSlot", "3"));
			YN_pclevel = Integer.parseInt(ServerCodes.getProperty("YNpclevel", "3"));
			수배금액 = Integer.parseInt(ServerCodes.getProperty("Huntprice", "3"));
			수배작동유무 = Boolean.parseBoolean(ServerCodes.getProperty("Huntonoff", "false"));
			/** 고대의서 **/

			rareweapon = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("rareweapon", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",", ArrangeParseeFactory.createIntArrange())
					.result();
			weapon = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("weapon", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			rareweaponLevel = Integer.parseInt(ServerCodes.getProperty("rareweaponLevel", "1"));
			weaponLevel = Integer.parseInt(ServerCodes.getProperty("weaponLevel", "1"));
			armor = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("armor", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			
			armor1 = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("armor1", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			
			할파스갑옷 = Integer.parseInt(ServerCodes.getProperty("Halpasec", "1"));
			할파스갑옷인챈 = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Halpasec1", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			가호영자이펙트 = Integer.parseInt(ServerCodes.getProperty("Gaho1", "1"));
			가호유저힐이펙트 = Integer.parseInt(ServerCodes.getProperty("Gaho2", "1"));
			몬스터정리이펙트 = Integer.parseInt(ServerCodes.getProperty("Mon1", "1"));
			
			armorLevel = Integer.parseInt(ServerCodes.getProperty("armorLevel", "1"));
			accessory = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("accessory", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			ancient2 = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Ancient2", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			ancient = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Ancient", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			ancient1 = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Ancient1", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			bless_orim = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Bless_orim", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();

			Roomtis = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Roomtis", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			Sanpper = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Sanpper", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			Sentence = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Sentence", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			Insignia = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Insignia", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();
			Should = (Integer[]) ArrangeParser
					.parsing(ServerCodes.getProperty("Should", "30,20,15,13,10,8,6,4,2,1,1,1,1,1,1"), ",",
							ArrangeParseeFactory.createIntArrange())
					.result();

			투견최소랜덤데미지 = Integer.parseInt(ServerCodes.getProperty("DogMIN", "100"));
			투견최대랜덤데미지 = Integer.parseInt(ServerCodes.getProperty("DogMAX", "100"));
			투견공속 = Double.parseDouble(ServerCodes.getProperty("Dogattack", "1.5"));
			RoomtisLevel = Integer.parseInt(ServerCodes.getProperty("RoomtisLevel", "1"));
			SnapperLevel = Integer.parseInt(ServerCodes.getProperty("SanpperLevel", "1"));
			SentenceLevel = Integer.parseInt(ServerCodes.getProperty("SentenceLevel", "1"));
			Insignia_Level = Integer.parseInt(ServerCodes.getProperty("InsigniaLevel", "1"));
			Should_Level = Integer.parseInt(ServerCodes.getProperty("ShouldLevel", "1"));

			Dr_weapon = Integer.parseInt(ServerCodes.getProperty("Drweapon", "1"));
			Hero_weapon = Integer.parseInt(ServerCodes.getProperty("Heroweapon", "1"));
			바포시스템 = Boolean.parseBoolean(ServerCodes.getProperty("Bapo1", "true"));
			POLY_EVENT1 = Boolean.parseBoolean(ServerCodes.getProperty("PolyEvent1", "false"));
			POLY_EVENT = Boolean.parseBoolean(ServerCodes.getProperty("PolyEvent", "false"));
			MANAGER_CLEAR = Boolean.parseBoolean(ServerCodes.getProperty("ManagerClear", "true"));
			MANAGER_CLEAR_TIME = Integer.parseInt(ServerCodes.getProperty("ManagerClearTime", "7200"));
			/** 새로추가 **/
			키링크 = Integer.parseInt(ServerCodes.getProperty("KingD", "12"));// 키링크
			데스나이트헬파이어 = Integer.parseInt(ServerCodes.getProperty("dethshellpa", "1"));
			신화인형hal = Integer.parseInt(ServerCodes.getProperty("sinwahal", "1"));
			신화인형behe = Integer.parseInt(ServerCodes.getProperty("sinwabehe", "1"));
			신화인형au = Integer.parseInt(ServerCodes.getProperty("sinwaau", "1"));
			신화인형desel = Integer.parseInt(ServerCodes.getProperty("sinwadesel", "1"));
			DEDMG = Integer.parseInt(ServerCodes.getProperty("Dedmg", "1"));
			halDMG = Integer.parseInt(ServerCodes.getProperty("halDMG", "1"));
			beheDMG = Integer.parseInt(ServerCodes.getProperty("beheDMG", "1"));
			auDMG = Integer.parseInt(ServerCodes.getProperty("auDMG", "1"));
			deselDMG = Integer.parseInt(ServerCodes.getProperty("deselDMG", "1"));
			DEDMG2 = Integer.parseInt(ServerCodes.getProperty("Dedmg2", "1"));
			지배자헌신마법확률 = Integer.parseInt(ServerCodes.getProperty("effectDmg", "1"));
			DEDMG1 = Integer.parseInt(ServerCodes.getProperty("Dedmg1", "1"));
			RANK_POTION = Integer.parseInt(ServerCodes.getProperty("RankPotion", "10"));
			Bless_Chance = Integer.parseInt(ServerCodes.getProperty("blessChance", "1"));
			Tax_Rate = Integer.parseInt(ServerCodes.getProperty("TaxRate", "10"));
			Tax_RateMin = Integer.parseInt(ServerCodes.getProperty("TaxRateMin", "10"));
			Tax_RateMax = Integer.parseInt(ServerCodes.getProperty("TaxRateMax", "10"));
			Lineage_Buff = Integer.parseInt(ServerCodes.getProperty("LineageBuff", "10"));
			Start_Char_Boho = Integer.parseInt(ServerCodes.getProperty("StartCharBoho", "10"));
			ExpPo = Integer.parseInt(ServerCodes.getProperty("ExpPosis", "86"));
			ExpPo1 = Integer.parseInt(ServerCodes.getProperty("ExpPosis1", "87"));
			ExpPo2 = Integer.parseInt(ServerCodes.getProperty("ExpPosis2", "87"));
			Garnet_Rnd = Integer.parseInt(ServerCodes.getProperty("GarnetRnd", "300000"));
			Garnet_Count = Integer.parseInt(ServerCodes.getProperty("GarnetCount", "1"));
			
			기사 = Double.parseDouble(ServerCodes.getProperty("KK", "1.5"));
			용기사 = Double.parseDouble(ServerCodes.getProperty("DK", "1.5"));
			요정 = Double.parseDouble(ServerCodes.getProperty("EF", "1.5"));
			군주 = Double.parseDouble(ServerCodes.getProperty("KC", "1.5"));
			법사 = Double.parseDouble(ServerCodes.getProperty("MM", "1.5"));
			다엘 = Double.parseDouble(ServerCodes.getProperty("DE", "1.5"));
			환술사 = Double.parseDouble(ServerCodes.getProperty("MB", "1.5"));
			전사 = Double.parseDouble(ServerCodes.getProperty("WR", "1.5"));

			IS_PARTY_EXP = Boolean.parseBoolean(ServerCodes.getProperty("IsPartyExp", "false"));
			ADD_PARTY_EXP = Double.parseDouble(ServerCodes.getProperty("AddPartyExp", "1.0"));
			BLESSING = Double.parseDouble(ServerCodes.getProperty("Blessing", "0.0"));
			UN_BLESSING = Double.parseDouble(ServerCodes.getProperty("Unblessing", "0.0"));
			ADENA_RATE_OF_BLESSING =  Integer.parseInt(ServerCodes.getProperty("AdenaRateOfBlessing", "0"));
			ADENA_RATE_OF_UNBLESSING =  Integer.parseInt(ServerCodes.getProperty("AdenaRateOfUnBlessing", "0"));
			
			혈맹포인트상점멘트 = Boolean.parseBoolean(ServerCodes.getProperty("Clanpointment", "true"));
			혈맹포인트여부 = Boolean.parseBoolean(ServerCodes.getProperty("Clanpoint", "true"));
			혈맹포인트여부2 = Boolean.parseBoolean(ServerCodes.getProperty("Clanpoint2", "true"));
			보스한입상자번호 = Integer.parseInt(ServerCodes.getProperty("Clanpointcount2", "10"));
			혈맹포인트맵번호 = ServerCodes.getProperty("Clanpointmap", "");
			혈맹포인트상점 = Boolean.parseBoolean(ServerCodes.getProperty("Clanpointshop", "true"));
			혈맹포인트조회 = Boolean.parseBoolean(ServerCodes.getProperty("Clanpoint1", "true"));
			혈맹포인트카운트 = Integer.parseInt(ServerCodes.getProperty("Clanpointcount", "10"));
			혈맹포인트카운트22 = Integer.parseInt(ServerCodes.getProperty("Clanpointcount11", "10"));
			혈맹버프지속시간 = Integer.parseInt(ServerCodes.getProperty("Clanpointtime", "10"));
			혈맹포인트최소 = Integer.parseInt(ServerCodes.getProperty("Clanpointmin", "10"));
			혈맹포인트최대 = Integer.parseInt(ServerCodes.getProperty("Clanpointmax", "10"));
			혈맹버프회복 = Integer.parseInt(ServerCodes.getProperty("ClanpoinHP", "10"));
			자동사냥맵번호 = Integer.parseInt(ServerCodes.getProperty("Auto1", "10"));
			자동사냥맵x = Integer.parseInt(ServerCodes.getProperty("Auto2", "10"));
			자동사냥맵y = Integer.parseInt(ServerCodes.getProperty("Auto3", "10"));
			
			StringTokenizer st = new StringTokenizer(ServerCodes.getProperty("buyer1", ""), ",");
			int index = 0;
			while (st.hasMoreTokens()) {
				level1[index] = (Integer.valueOf(st.nextToken()));
				index++;
			}
			st = new StringTokenizer(ServerCodes.getProperty("buyer2", ""), ",");
			index = 0;
			while (st.hasMoreTokens()) {
				level2_1[index] = (Integer.valueOf(st.nextToken()));
				index++;
			}
			st = new StringTokenizer(ServerCodes.getProperty("buyer2_2", ""), ",");
			index = 0;
			while (st.hasMoreTokens()) {
				level2_2[index] = (Integer.valueOf(st.nextToken()));
				index++;
			}
			st = new StringTokenizer(ServerCodes.getProperty("buyer3", ""), ",");
			index = 0;
			while (st.hasMoreTokens()) {
				level3_1[index] = (Integer.valueOf(st.nextToken()));
				index++;
			}
			st = new StringTokenizer(ServerCodes.getProperty("buyer3_2", ""), ",");
			index = 0;
			while (st.hasMoreTokens()) {
				level3_2[index] = (Integer.valueOf(st.nextToken()));
				index++;
			}
			st = new StringTokenizer(ServerCodes.getProperty("buyer3_3", ""), ",");
			index = 0;
			while (st.hasMoreTokens()) {
				level3_3[index] = (Integer.valueOf(st.nextToken()));
				index++;
			}

		} catch (Exception e) {
			_log.log(Level.SEVERE, "Config.에서 에러가 발생했습니다.", e);
			throw new Error("Failed to Load " + ServerCodes_SETTINGS_CONFIG_FILE + " File.");
		}
		/** 특수 인챈트 시스템 **/
		try {
			Properties MagicEnchantSettings = new Properties();
			InputStream is = new FileInputStream(new File(MAGIC_ENCHANT_SETTINGS_CONFIG_FILE));
			MagicEnchantSettings.load(is);
			is.close();
			악세축복1단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("AccBless1", "10"));
			악세축복2단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("AccBless2", "8"));
			악세축복3단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("AccBless3", "5"));
			악세축복4단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("AccBless4", "2"));
			악세축복5단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("AccBless5", "2"));
			
			부여주문서1단  = Double.parseDouble(MagicEnchantSettings.getProperty("enchantcar1", "0.5"));
			부여주문서2단  = Double.parseDouble(MagicEnchantSettings.getProperty("enchantcar2", "0.5"));
			부여주문서3단  = Double.parseDouble(MagicEnchantSettings.getProperty("enchantcar3", "0.5"));

			
			축복1단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless1", "10"));
			축복2단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless2", "8"));
			축복3단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless3", "5"));
			축복4단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless4", "2"));
			축복5단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless5", "2"));
			방어구축복1단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless11", "10"));
			방어구축복2단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless22", "8"));
			방어구축복3단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless33", "5"));
			방어구축복4단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless44", "2"));
			방어구축복5단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless55", "2"));
			방어구축복6단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless66", "2"));
			
			랜덤부여주문서 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Bless00", "2"));
			
			피브이피1단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("PVPBless1", "10"));
			피브이피2단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("PVPBless2", "8"));
			피브이피3단 = Integer
					.parseInt(MagicEnchantSettings.getProperty("PVPBless3", "5"));
			// TODO ServerCodes.properties 셋팅
			Doll_Enchant_Per_lvl1 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_Enchant_Per_lvl1", "10"));
			Doll_Enchant_Per_lvl2 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_Enchant_Per_lvl2", "8"));
			Doll_Enchant_Per_lvl3 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_Enchant_Per_lvl3", "6"));
			Doll_Enchant_Per_lvl4 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_Enchant_Per_lvl4", "4"));
			Doll_Enchant_Per_lvl5 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_Enchant_Per_lvl5", "2"));

			Doll_Attack_Chance = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_attack_chance", "10"));
			Doll_Attack_Dmg_Lvl1 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_attack_dmg_lvl_1", "100"));
			Doll_Attack_Dmg_Lvl2 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_attack_dmg_lvl_2", "110"));
			Doll_Attack_Dmg_Lvl3 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_attack_dmg_lvl_3", "120"));
			Doll_Attack_Dmg_Lvl4 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_attack_dmg_lvl_4", "130"));
			Doll_Attack_Dmg_Lvl5 = Integer.parseInt(MagicEnchantSettings.getProperty("Doll_attack_dmg_lvl_5", "140"));

			Weapon_Magic_Per = Integer.parseInt(MagicEnchantSettings.getProperty("Weapon_Magic_Per", "10"));

			Weapon_Enchant_Per_lvl1 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Per_lvl1", "10"));
			Weapon_Enchant_Per_lvl2 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Per_lvl2", "8"));
			Weapon_Enchant_Per_lvl3 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Per_lvl3", "5"));
			Weapon_Enchant_Per_lvl4 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Per_lvl3", "2"));

			Weapon_Enchant_Dmg_lvl1 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Dmg_lvl1", "80"));
			Weapon_Enchant_Dmg_lvl2 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Dmg_lvl2", "100"));
			Weapon_Enchant_Dmg_lvl3 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Dmg_lvl3", "120"));
			Weapon_Enchant_Dmg_lvl4 = Integer
					.parseInt(MagicEnchantSettings.getProperty("Weapon_Enchant_Dmg_lvl3", "140"));

		} catch (Exception e) {
			_log.log(Level.SEVERE, "Config.에서 에러가 발생했습니다.", e);
			throw new Error("Failed to Load " + MAGIC_ENCHANT_SETTINGS_CONFIG_FILE + " File.");
		}
		/** 특수 인챈트 시스템 **/

		
		try {
			Properties DollSettings = new Properties();
			InputStream is = new FileInputStream(new File(DOLL_SETTINGS_CONFIG_FILE));
			DollSettings.load(is);
			is.close();
			
			
			//잠재력활성화 = Boolean.parseBoolean(DollSettings.getProperty("Dollonoff", "true"));
			일반갯수 =  Integer.parseInt(DollSettings.getProperty("Dollcount1", "0"));
			고급갯수 =  Integer.parseInt(DollSettings.getProperty("Dollcount2", "0"));
			희귀갯수 =  Integer.parseInt(DollSettings.getProperty("Dollcount3", "0"));
			영웅갯수 =  Integer.parseInt(DollSettings.getProperty("Dollcount4", "0"));
			전설갯수 =  Integer.parseInt(DollSettings.getProperty("Dollcount5", "0"));
	잠재력이뮨 =  Double.parseDouble(DollSettings.getProperty("testtest", "0.1"));
			잠재력없음 =  Integer.parseInt(DollSettings.getProperty("DollK1", "0"));
			잠재력없음일반확률 =  Integer.parseInt(DollSettings.getProperty("DollK2", "0"));
			잠재력없음고급확률 =  Integer.parseInt(DollSettings.getProperty("DollK3", "0"));
			잠재력없음희귀확률 =  Integer.parseInt(DollSettings.getProperty("DollK4", "0"));
			잠재력흡수유무 = Boolean.parseBoolean(DollSettings.getProperty("Dollmphp", "true"));
			잠재력성공 = Boolean.parseBoolean(DollSettings.getProperty("Dollsuc", "false"));
			잠재력성공1 = Boolean.parseBoolean(DollSettings.getProperty("Dollsuc1", "false"));
			영웅등급멘트 = Boolean.parseBoolean(DollSettings.getProperty("Dollmm", "false"));
			전설등급멘트 = Boolean.parseBoolean(DollSettings.getProperty("Dollnn", "false"));
			
			잠재력고급확률 =  Integer.parseInt(DollSettings.getProperty("DollK5", "0"));
			잠재력고급될확률 =  Integer.parseInt(DollSettings.getProperty("DollK6", "0"));
			잠재력희귀될확률 =  Integer.parseInt(DollSettings.getProperty("DollK7", "0"));
			
			잠재력희귀확률 =  Integer.parseInt(DollSettings.getProperty("DollK8", "0"));
			잠재력희귀희귀확률 =  Integer.parseInt(DollSettings.getProperty("DollK9", "0"));
			
			잠재력영웅확률 =  Integer.parseInt(DollSettings.getProperty("DollK10", "0"));
			
			잠재력영웅될확률 =  Integer.parseInt(DollSettings.getProperty("DollK11", "0"));
			잠재력영웅영웅확률 =  Integer.parseInt(DollSettings.getProperty("DollK12", "0"));
			잠재력영웅꺼 =  Integer.parseInt(DollSettings.getProperty("DollK13", "0"));
			잠재력전설1 =  Integer.parseInt(DollSettings.getProperty("DollK14", "0"));
			잠재력없음영웅확률 =  Integer.parseInt(DollSettings.getProperty("DollK15", "0"));

			잠재력디케이 =  Integer.parseInt(DollSettings.getProperty("DollK99", "0"));
			잠재력디케이1 =  Integer.parseInt(DollSettings.getProperty("DollK991", "0"));
			잠재력디케이2 =  Integer.parseInt(DollSettings.getProperty("DollK992", "0"));
			잠재력디케이3 =  Integer.parseInt(DollSettings.getProperty("DollK993", "0"));
			잠재력디케이4 =  Integer.parseInt(DollSettings.getProperty("DollK994", "0"));
			잠재력디케이5 =  Integer.parseInt(DollSettings.getProperty("DollK995", "0"));
			
			잠재력흡수0 =  Integer.parseInt(DollSettings.getProperty("DollK991", "0"));
			잠재력흡수00 =  Integer.parseInt(DollSettings.getProperty("DollK9911", "0"));
			잠재력흡수000 =  Integer.parseInt(DollSettings.getProperty("DollK9921", "0"));
			잠재력흡수0000 =  Integer.parseInt(DollSettings.getProperty("DollK9931", "0"));
			잠재력흡수00000 =  Integer.parseInt(DollSettings.getProperty("DollK9941", "0")); 
			잠재력흡수000000 =  Integer.parseInt(DollSettings.getProperty("DollK9951", "0"));
			
			
			잠재력소울 =  Integer.parseInt(DollSettings.getProperty("DollK999", "0"));
			잠재력흡수 =  Integer.parseInt(DollSettings.getProperty("DollK88", "0"));
			잠재력흡수1 =  Integer.parseInt(DollSettings.getProperty("DollK881", "0"));
			잠재력흡수2 =  Integer.parseInt(DollSettings.getProperty("DollK882", "0"));
			잠재력흡수3 =  Integer.parseInt(DollSettings.getProperty("DollK883", "0"));
			잠재력흡수4 =  Integer.parseInt(DollSettings.getProperty("DollK884", "0"));
			잠재력흡수5 =  Integer.parseInt(DollSettings.getProperty("DollK885", "0"));
			
			잠재력저지먼트 =  Integer.parseInt(DollSettings.getProperty("DollK123", "0"));
			잠재력저지먼트1 =  Integer.parseInt(DollSettings.getProperty("DollK1231", "0"));
			잠재력저지먼트2 =  Integer.parseInt(DollSettings.getProperty("DollK1232", "0"));
			잠재력저지먼트3 =  Integer.parseInt(DollSettings.getProperty("DollK1233", "0"));
			잠재력저지먼트4 =  Integer.parseInt(DollSettings.getProperty("DollK1234", "0"));
			잠재력저지먼트5 =  Integer.parseInt(DollSettings.getProperty("DollK1235", "0"));
			
			영웅엠피 =  Integer.parseInt(DollSettings.getProperty("DollK71", "0"));
			영웅엠피1 =  Integer.parseInt(DollSettings.getProperty("Dollk72", "0"));
			전설엠피 =  Integer.parseInt(DollSettings.getProperty("DollK73", "0"));
			전설엠피1 =  Integer.parseInt(DollSettings.getProperty("DollK74", "0"));
			전설피 =  Integer.parseInt(DollSettings.getProperty("DollK75", "0"));
			전설피1 =  Integer.parseInt(DollSettings.getProperty("DollK76", "0"));
			인형PVP감소수치 =  Integer.parseInt(DollSettings.getProperty("DollK77", "0"));
			인형디케이  =  Integer.parseInt(DollSettings.getProperty("Dolllegend1", "0"));
			인형pvp감소 =  Integer.parseInt(DollSettings.getProperty("Dolllegend2", "0"));
			인형올스탯 =  Integer.parseInt(DollSettings.getProperty("Dolllegend3", "0"));
			인형피 =  Integer.parseInt(DollSettings.getProperty("Dolllegend4", "0"));
			인형엠피 =  Integer.parseInt(DollSettings.getProperty("Dolllegend5", "0"));
			인형소울 =  Integer.parseInt(DollSettings.getProperty("Dolllegend6", "0"));
			인형이뮨 =  Integer.parseInt(DollSettings.getProperty("Dolllegend7", "0"));
			인형저지먼트 =  Integer.parseInt(DollSettings.getProperty("Dolllegend8", "0"));
		} catch (Exception e) {
			_log.log(Level.SEVERE, "Config.에서 에러가 발생했습니다.", e);
			throw new Error("Failed to Load " + DOLL_SETTINGS_CONFIG_FILE + " File.");
		}
		
		
		try {
			Properties GMteleport = new Properties();
			InputStream is = new FileInputStream(new File(GMteleport_CONFIG_FILE));
			GMteleport.load(is);
			is.close();
			
			
			지엠전용  = Boolean.parseBoolean(GMteleport.getProperty("Master", "true"));
			기란마을 = GMteleport.getProperty("giran", "");
			지엠룸 = GMteleport.getProperty("GM", "");
			후원 = GMteleport.getProperty("coin", "");
			상담 = GMteleport.getProperty("talk", "");
			결투장 = GMteleport.getProperty("pvp", "");
			낚시터 = GMteleport.getProperty("fish", "");
			기감1 = GMteleport.getProperty("gidun1", "");
			기감2 = GMteleport.getProperty("gidun2", "");
			지결1 = GMteleport.getProperty("JG1", "");
			지결2 = GMteleport.getProperty("JG2", "");
			고무 = GMteleport.getProperty("GOMU", "");
			
			화둥 = GMteleport.getProperty("fire", "");
			풍둥 = GMteleport.getProperty("wind", "");
			설벽 = GMteleport.getProperty("oren", "");
			용계 = GMteleport.getProperty("dragon", "");
			
			잊섬 = GMteleport.getProperty("islan", "");
			악영 = GMteleport.getProperty("devil1", "");
			지옥 = GMteleport.getProperty("devil2", "");
			
			
			말던1 = GMteleport.getProperty("md1", "");
			말던2 = GMteleport.getProperty("md2", "");
			용던1 = GMteleport.getProperty("yd1", "");
			용던2 = GMteleport.getProperty("yd2", "");
			본던1 = GMteleport.getProperty("bd1", "");
			본던2 = GMteleport.getProperty("bd2", "");
			상아탑 = GMteleport.getProperty("sd1", "");
			
			장소1 = GMteleport.getProperty("want1", "");
			장소2 = GMteleport.getProperty("want2", "");
			장소3 = GMteleport.getProperty("want3", "");
			장소4 = GMteleport.getProperty("want4", "");
			장소5 = GMteleport.getProperty("want5", "");
			장소6 = GMteleport.getProperty("want6", "");
			
			
			지탑1 = GMteleport.getProperty("jbt1", "");
			지탑2 = GMteleport.getProperty("jbt2", "");
			지탑3 = GMteleport.getProperty("jbt3", "");
			지탑4 = GMteleport.getProperty("jbt4", "");
			지탑5 = GMteleport.getProperty("jbt5", "");
			지탑6 = GMteleport.getProperty("jbt6", "");
			지탑7 = GMteleport.getProperty("jbt7", "");
			지탑8 = GMteleport.getProperty("jbt8", "");
			지탑9 = GMteleport.getProperty("jbt9", "");
			지탑10 = GMteleport.getProperty("jbt10", "");
			지탑11 = GMteleport.getProperty("jbt11", "");
			
			
			오탑1 = GMteleport.getProperty("omt1", "");
			오탑2 = GMteleport.getProperty("omt2", "");
			오탑3 = GMteleport.getProperty("omt3", "");
			오탑4 = GMteleport.getProperty("omt4", "");
			오탑5 = GMteleport.getProperty("omt5", "");
			오탑6 = GMteleport.getProperty("omt6", "");
			오탑7 = GMteleport.getProperty("omt7", "");
			오탑8 = GMteleport.getProperty("omt8", "");
			오탑9 = GMteleport.getProperty("omt9", "");
			오탑10 = GMteleport.getProperty("omt10", "");
			오탑11 = GMteleport.getProperty("omt11", "");
			
		} catch (Exception e) {
			_log.log(Level.SEVERE, "Config.에서 에러가 발생했습니다.", e);
			throw new Error("Failed to Load " + GMteleport_CONFIG_FILE + " File.");
		}
		
		/** 로또 시스템 **/
		
		try {
			Properties LottotSettings = new Properties();
			InputStream is = new FileInputStream(new File(LOTTO_SETTINGS_CONFIG_FILE));
			LottotSettings.load(is);
			is.close();

			// TODO Lotto.properties 셋팅
			LOTTO = Boolean.parseBoolean(LottotSettings.getProperty("Lotto", "true"));
			LOTTO_LEVEL = Integer.parseInt(LottotSettings.getProperty("LottoLevel", "52"));
			LOTTO_BATTING = Integer.parseInt(LottotSettings.getProperty("LottoBatting", "50000"));
			LOTTO_BONUS = Integer.parseInt(LottotSettings.getProperty("LottoBonus", "0"));
			LOTTO_TIME = LottotSettings.getProperty("LottoTime", "1:30,5:30,14:30,20:30");

		} catch (Exception e) {
			_log.log(Level.SEVERE, "Config.에서 에러가 발생했습니다.", e);
			throw new Error("Failed to Load " + LOTTO_SETTINGS_CONFIG_FILE + " File.");
		}
		/** 로또 시스템 **/
		
		
		
		validate();
	}
	public static String toKor(String src) {
		String str = null;
		try {
			byte[] b = src.getBytes("8859_1");
			str = new String(b, "ms949");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return str;
	}
	private static void validate() {
		if (!IntRange.includes(Config.ALT_ITEM_DELETION_RANGE, 0, 5)) {
			throw new IllegalStateException("ItemDeletionRange의 값이 설정 가능 범위외입니다. ");
		}
		if (!IntRange.includes(Config.ALT_ITEM_DELETION_TIME, 1, 35791)) {
			throw new IllegalStateException("ItemDeletionTime의 값이 설정 가능 범위외입니다. ");
		}
	}

	public static boolean setParameterValue(String pName, String pValue) {
		/** server.properties **/
		if (pName.equalsIgnoreCase("GameserverName")) {
			try {
				GAME_SERVER_NAME = new String(pValue.getBytes("ISO-8859-1"), "euc-kr");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		} else if (pName.equalsIgnoreCase("AltNonPvP")) {
			ALT_NONPVP = Boolean.valueOf(pValue);
		} else if (pName.equalsIgnoreCase("AttackMessageOn")) {
			ALT_ATKMSG = Boolean.valueOf(pValue);
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
	
}
