package l1j.server.server.model.Instance;

import l1j.server.server.model.quest.CQuestSystem;

import l1j.server.server.model.character.action.CAttackAction;

import java.util.HashMap;
import java.util.Random;
import java.util.logging.Logger;

import l1j.server.server.model.npc.dialog.NPCTalkDataTable;
import l1j.server.server.model.npc.dialog.L1NpcTalkData;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.npc.dialog.L1NpcHtml;
import l1j.server.server.model.network.core.sc.packet.SC_NPCTalkReturn;
import l1j.server.server.templates.L1Npc;

public class L1TeleporterInstance extends L1NpcInstance {

	private static final long serialVersionUID = 1L;
	private static final long TELEPORT_DELAY_NANOS = 900_000_000_000L;
	private volatile long teleportDelayedUntil;
	
	public L1TeleporterInstance(L1Npc template) {
		super(template);
	}

	@Override
	public void onAction(L1PcInstance player) {
		CAttackAction.hitAndSwing(player, this);
	}

	@Override
	public void onTalkAction(L1PcInstance player) {
        if (player == null || this == null)
            return;
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(
				getNpcTemplate().get_npcId());
		int npcid = getNpcTemplate().get_npcId();
		CQuestSystem quest = player.getQuest();
		String htmlid = null;

		if (talking != null) {
			switch(npcid){
			case 50014: // 디론
				if (player.isWizard()) { // 위저드
					if (quest.get_step(CQuestSystem.QUEST_LEVEL30) == 1
							&& !player.getInventory().HaveItemByClassId(40579)) { // 안 데드의 뼈
						htmlid = "dilong1";
					} else {
						htmlid = "dilong3";
					}
				}
				break;
			case 70779: // 게이트 안트
				if (player.getCurrentSpriteId() == 1037) { // 쟈이안트안트 변신
					htmlid = "ants3";
				} else if (player.getCurrentSpriteId() == 1039) {// 쟈이안트안트소르쟈 변신
					if (player.isCrown()) { // 군주
						if (quest.get_step(CQuestSystem.QUEST_LEVEL30) == 1) {
							if (player.getInventory().HaveItemByClassId(40547)) { // 주민들의 유품
								htmlid = "antsn";
							} else {
								htmlid = "ants1";
							}
						} else { // Step1 이외
							htmlid = "antsn";
						}
					} else { // 군주 이외
						htmlid = "antsn";
					}
				}
				break;
			case 70853: // 페어리 프린세스
				if (player.isElf()) { // 에르프
					if (quest.get_step(CQuestSystem.QUEST_LEVEL30) == 1) {
						if (!player.getInventory().HaveItemByClassId(40592)) { // 저주해진 정령서
							Random random = new Random(System.nanoTime());
							if (random.nextInt(100) < 50) { // 50%로 다크마르단젼
								htmlid = "fairyp2";
							} else { // 다크 에르프 지하 감옥
								htmlid = "fairyp1";
							}
						}
					}
				}
				break;
			case 50031: // 세피아
				if (player.isElf()) { // 에르프
					if (quest.get_step(CQuestSystem.QUEST_LEVEL45) == 2) {
						if (!player.getInventory().HaveItemByClassId(40602)) { // 블루 플룻
							htmlid = "sepia1";
						}
					}
				}
				break;
			case 50043:
				if (quest.get_step(CQuestSystem.QUEST_LEVEL50) == CQuestSystem.QUEST_END) {
					htmlid = "ramuda2";
				} else if (quest.get_step(CQuestSystem.QUEST_LEVEL50) == 1) { // 디가르딘 동의가 끝난 상태
					if (player.isCrown()) { // 군주
						if (isTeleportDelayed()) { // 텔레포트 지연중
							htmlid = "ramuda4";
						} else {
							htmlid = "ramudap1";
						}
					} else { // 군주 이외
						htmlid = "ramuda1";
					}
				} else {
					htmlid = "ramuda3";
				}
				break;
			case 50082: // 노래하는 섬 텔레포터
				if (player.getLevel() < 13) {
					htmlid = "en0221";
				} else {
					if (player.isElf()) {
						htmlid = "en0222e";
					} else if (player.isDarkelf()) {
						htmlid = "en0222d";
					} else {
						htmlid = "en0222";
					}
				}
				break;
			case 50001: // 바르니아
				if (player.isElf()) {
					htmlid = "barnia3";
				} else if (player.isKnight() || player.isCrown() || player.isWarrior()) {
					htmlid = "barnia2";
				} else if (player.isWizard() || player.isDarkelf()) {
					htmlid = "barnia1";
				}
				break;
			case 9271: // 도리아
				if (player.getLevel() < 5){
					htmlid = "doria2";
				} else if (player.getLevel() >= 10 && player.getLevel() < 30){
					if (player.isElf()){
						htmlid = "doria4";
					} else {
						htmlid = "doria1";
					}
				} else {
					htmlid = "doria";
				}
				break;
			case 50056://메트
				if (player.getLevel() < 45){//숨계
					htmlid = "telesilver4";
				} else if (player.getLevel() >= 99 && player.getLevel() <= 99){//폭풍수련지역
					htmlid = "telesilver5";
				} else {
					htmlid = "telesilver1";
				}
				break;
			case 50020: // 스텐리
			case 50024: // 아스터
			case 50036: // 윌마
			case 5069: //린지
			case 50039: // 레슬리
			case 50044: // 시리우스
			case 50046: // 엘레리스
			case 50051: // 키리우스
			case 50054: // 트레이
			case 50066: // 리올
			case 7320051: // 지프란
				if (player.getLevel() < 45){
					htmlid = "starttel1";
				} else if (player.getLevel() >= 45 && player.getLevel() <= 51){
					htmlid = "starttel2";
				} else {
					htmlid = "starttel3";
				}
				break;
			default:
				break;
			}
			// html 표시
			if (htmlid != null) { // htmlid가 지정되고 있는 경우
				SC_NPCTalkReturn.send(player, objid, htmlid);
			} else {
				if (player.getLawful() < -1000) { // 플레이어가 카오틱
					SC_NPCTalkReturn.send(player, talking, objid, 2);
				} else {
					SC_NPCTalkReturn.send(player, talking, objid, 1);
				}
			}
		} else {
			_log.finest((new StringBuilder())
					.append("No actions for npc id : ").append(objid)
					.toString());
		}
	}

	// teleportURL
	private static final HashMap<Integer, String[]> _teleportPrice = new HashMap<Integer, String[]>();
	private static final String[]					_teleportPriceDummy = new String[]{""};
	static{
		// 말하는섬 루카
		_teleportPrice.put(50015, 	new String[]{"1500"});
		// 말하는 섬 케이스
		_teleportPrice.put(50017, 	new String[]{"50"});
		// 켄트 스탠리
		_teleportPrice.put(50020, 	new String[] { "50", "50", "120", "120", "50", "180", "120", "120", "180", "200", "200", "420", "600", "1155", "7100" });
		// 글루디오 아스터
		_teleportPrice.put(50024, 	new String[] { "132", "55", "198", "55", "132", "264", "55", "7777", "7777", "198", "264", "220", "220", "420", "550", "1155", "7480" });
		// 기란 윌마
		_teleportPrice.put(50036, 	new String[] { "126", "126", "52", "189", "52", "52", "189", "126", "126", "315", "315", "420", "735", "1155", "875" });
		// 기란 시장 린지
		_teleportPrice.put(5069, 	new String[] { "126", "126", "52", "189", "52", "52", "189", "126", "126", "315", "315", "420", "735", "1155", "875" });
		// 화말 지프란
		_teleportPrice.put(7320051,	new String[] { "126", "126", "52", "189", "52", "52", "189", "126", "126", "315", "315", "420", "735", "1155", "875" });
		// 웰던 레슬리
		_teleportPrice.put(50039, 	new String[] { "185", "185", "123", "247", "51", "123", "247", "51", "185", "420", "412", "412", "824", "1155", "7931" });
		String[] ss = new String[]{ "259","129","194","129","54","324","194","259","420", "450", "540","540","972","1155", "7992" };
		// 아덴 시리우스
		_teleportPrice.put(50044, 	ss);
		// 아덴 에레리스
		_teleportPrice.put(50046, 	ss);
		// 오렌키리우스
		_teleportPrice.put(50051, 	new String[] { "240", "240", "180", "300", "120", "180", "300", "50", "240", "420", "500", "500", "900", "1155", "8000" });
		// 윈다우드트레이
		_teleportPrice.put(50054, 	new String[] { "50", "50", "120", "120", "180", "180", "180", "240", "240", "300", "200", "200", "420", "500", "6500" });
		// 은기사마을 메트
		_teleportPrice.put(50056, 	new String[] {"55","55","55","132","132","132","198","198","270","7777","7777","246","420","770", "7480" });
		// 하이네리올
		_teleportPrice.put(50066, 	new String[] { "180", "50", "120", "120", "50", "50", "240", "120", "180", "420", "400", "400",	"800", "1155", "7100" });
		// 디아노스
		_teleportPrice.put(50068, 	new String[] { "1500", "800", "600", "1800", "1800", "1000", "300" });
		// 공간이동사 디아루즈
		_teleportPrice.put(50072, 	new String[] { "2200", "1800", "1000", "1600", "2200", "1200", "1300", "2000", "2000" });
		// 공간이동사 디아베스 // 사용안함
		_teleportPrice.put(50073, 	new String[] { "380", "850", "290", "290", "290", "180", "480", "150", "150", "380", "480", "380", "850" });
		// 마법사 다니엘
		_teleportPrice.put(50079, 	new String[] { "550", "550", "600", "550", "700", "600", "600", "750", "750", "550", "550", "700", "650" });
		// 데카비아 베히모스
		_teleportPrice.put(3000005, new String[] { "50", "50", "50", "50", "120", "120", "180", "180", "180", "240", "240", "400", "400", "800", "7700" });
		// 실베리아 샤리엘
		_teleportPrice.put(3100005, new String[] { "50", "50", "50", "120", "180", "180", "240", "240", "240", "300", "300", "500", "500", "900", "8000" });
		ss = new String[]{ "0","0","0"};
		// 그르딘 시장⇒기란 시장, 오렌 시장, 실버 나이트 타운 시장
		_teleportPrice.put(50026, 	ss);
		// 기란 시장⇒그르딘 시장, 오렌 시장, 실버 나이트 타운 시장
		_teleportPrice.put(50033, 	ss);
		// 오렌 시장⇒그르딘 시장, 기란 시장, 실버 나이트 타운 시장
		_teleportPrice.put(50049, 	ss);
		 // 실버 나이트 타운 시장⇒그르딘 시장, 기란 시장, 오렌 시장
		_teleportPrice.put(50059, 	ss);
		// 시종장 맘몬
		_teleportPrice.put(6000014, new String[]{"14000"});
		// 신녀 플로라
		_teleportPrice.put(6000016, new String[]{"1000"});
		//상아탑 피터
		_teleportPrice.put(900056, 	new String[]{"7000","7000","7000","14000","14000"});
		// 엘루나 [ 요정의숲 텔레포터 ]
		_teleportPrice.put(5091, 	new String[]{ "57", "57", "57", "138", "138", "138", "138", "207", "207", "230", "230", "690" });
	}
	
	static class HtmlPricePair{
		HtmlPricePair(String h, String[] p){
			html = h;
			price= p;
		}
		String 		html;
		String[] 	price;
	}
	
	// teleportURLA
	private static final HashMap<Integer, HtmlPricePair> 		_teleportPriceA 		= new HashMap<Integer, HtmlPricePair>();
	private static final HtmlPricePair							_teleportPriceDummyA	= new HtmlPricePair("", new String[]{""});
	static{
		// 다니엘
		_teleportPriceA.put(50079, 		new HtmlPricePair("telediad3", 	new String[]{ "700","800","800","1000" }));
		// 데카비아
		_teleportPriceA.put(3000005, 	new HtmlPricePair("dekabia3", 	new String[]{ "100","220","220","220","330","330","330","330","440","440" }));
		// 샤리엘
		_teleportPriceA.put(3100005, 	new HtmlPricePair("sharial",	new String[]{ "220","330","330","330","440","440","550","550","550","550" }));
	}
	
	// teleportURLL
	private static final HashMap<Integer, HtmlPricePair> 		_teleportPriceL 		= new HashMap<Integer, HtmlPricePair>();
	private static final HtmlPricePair							_teleportPriceDummyL	= new HtmlPricePair("telesilver3", new String[] { "780","780","780","780","780","1230","1080","1080","1080","1080" });
	static{
		// 메트
		_teleportPriceL.put(50056, new HtmlPricePair("guide_0_1", new String[]{ "30","30","30", "70", "80", "90","100", "30" }));
		
		HtmlPricePair p = new HtmlPricePair("guide_6", new String[]{ "500","500" });
		// 스텐리
		_teleportPriceL.put(50020, p);
		// 아스터
		_teleportPriceL.put(50024, p);
		// 윌마
		_teleportPriceL.put(50036, p);
		// 린지
		_teleportPriceL.put(5069, p);
		// 레슬리
		_teleportPriceL.put(50039, p);
		// 시리우스
		_teleportPriceL.put(50044, p);
		// 엘레리스
		_teleportPriceL.put(50046, p);
		// 키리우스
		_teleportPriceL.put(50051, p);
		// 트레이
		_teleportPriceL.put(50054, p);
		// 리올
		_teleportPriceL.put(50066, p);
		_teleportPriceL.put(5069, p);
		_teleportPriceL.put(7320051, p);
	}
	
	// teleportURLM
	private static final HashMap<Integer, HtmlPricePair> 		_teleportPriceM 		= new HashMap<Integer, HtmlPricePair>();
	private static final HtmlPricePair							_teleportPriceDummyM	= new HtmlPricePair("", new String[]{""});
	static{
		_teleportPriceM.put(50056, new HtmlPricePair("hp_storm1", new String[]{""}));
		HtmlPricePair pair = new HtmlPricePair("guide_7", new String[]{ "500","500","500","500","500","500","500","500","500","500","500" });
		// 스텐리
		_teleportPriceM.put(50020, pair);
		// 아스터
		_teleportPriceM.put(50024, pair);
		// 윌마
		_teleportPriceM.put(50036, pair);
		// 린지
		_teleportPriceM.put(5069, pair);
		// 레슬리
		_teleportPriceM.put(50039, pair);
		// 시리우스
		_teleportPriceM.put(50044, pair);
		// 엘레리스
		_teleportPriceM.put(50046, pair);
		// 키리우스
		_teleportPriceM.put(50051, pair);
		// 트레이
		_teleportPriceM.put(50054, pair);
		// 리올
		_teleportPriceM.put(50066, pair);
		_teleportPriceM.put(5069, pair);
		//지프란
		_teleportPriceM.put(7320051, pair);
	}
	
	// other
	private static final HashMap<String, HtmlPricePair> 		_teleportPriceOther 	= new HashMap<String, HtmlPricePair>();
	static{
		_teleportPriceOther.put("teleportURLB", new HtmlPricePair("guide_1_1", new String[]{ "450","450","450","450" }));
		_teleportPriceOther.put("teleportURLC", new HtmlPricePair("guide_1_2", new String[]{ "465","465","465","465","1065","1065" }));
		_teleportPriceOther.put("teleportURLD", new HtmlPricePair("guide_1_3", new String[]{ "480","480","480","480","630","1080","630" }));
		_teleportPriceOther.put("teleportURLE", new HtmlPricePair("guide_2_1", new String[]{ "600","600","750","750" }));
		_teleportPriceOther.put("teleportURLF", new HtmlPricePair("guide_2_2", new String[]{ "615","615","915","765" }));
		_teleportPriceOther.put("teleportURLG", new HtmlPricePair("guide_2_3", new String[]{ "630","780","630","1080","930" }));
		_teleportPriceOther.put("teleportURLH", new HtmlPricePair("guide_3_1", new String[]{ "750","750","750","1200","1050" }));
		_teleportPriceOther.put("teleportURLI", new HtmlPricePair("guide_3_2", new String[]{ "765","765","765","765","1515","1215","915" }));
		_teleportPriceOther.put("teleportURLJ", new HtmlPricePair("guide_3_3", new String[]{ "780","780","780","780","780","1230","1080" }));
		_teleportPriceOther.put("teleportURLK", new HtmlPricePair("guide_4",   new String[]{ "780","780","780","780","780","1230","1080" }));
		_teleportPriceOther.put("teleportURLO", new HtmlPricePair("guide_8",   new String[]{ "750" }));
	}
	
	@Override
	public void onFinalAction(L1PcInstance player, String action) {
        if (player == null)
            return;
		int objid = getId();
		L1NpcTalkData talking = NPCTalkDataTable.getInstance().getTemplate(getNpcTemplate().get_npcId());
		if (action.equalsIgnoreCase("teleportURL")) {
			L1NpcHtml html = new L1NpcHtml(talking.getTeleportURL());
			String[] price = null;
			int npcid = getNpcTemplate().get_npcId();
			price = _teleportPrice.get(npcid);
			if(price == null) price = _teleportPriceDummy;
			SC_NPCTalkReturn.send(player, objid, html, price);
		} else if (action.equalsIgnoreCase("teleportURLA")) {
			int npcid = getNpcTemplate().get_npcId();
			HtmlPricePair 		pair = _teleportPriceA.get(npcid);
			if(pair == null) 	pair = _teleportPriceDummyA;
			SC_NPCTalkReturn.send(player, objid, pair.html, pair.price);
		}else if (action.equalsIgnoreCase("teleportURLL")){
			int npcid = getNpcTemplate().get_npcId();
			HtmlPricePair 		pair = 	_teleportPriceL.get(npcid);
			if(pair == null) 	pair =	_teleportPriceDummyL;
			SC_NPCTalkReturn.send(player, objid, pair.html, pair.price);
		} else if (action.equalsIgnoreCase("teleportURLM")){
			int npcid = getNpcTemplate().get_npcId();
			HtmlPricePair		pair =	_teleportPriceM.get(npcid);
			if(pair == null)	pair =	_teleportPriceDummyM;
			SC_NPCTalkReturn.send(player, objid, pair.html, pair.price);
		} else {
			HtmlPricePair 		pair =	_teleportPriceOther.get(action);
			if(pair != null)
				SC_NPCTalkReturn.send(player, objid, pair.html, pair.price);
		}
		
		if (action.startsWith("teleport")) {
			_log.finest((new StringBuilder()).append("Setting action to : ").append(action).toString());
			doFinalAction(player, action);
		}
	}

	private void doFinalAction(L1PcInstance player, String action) {
        if (player == null) {
            return;
        }
		int objid = getId();

		int npcid = getNpcTemplate().get_npcId();
		String htmlid = null;
		boolean isTeleport = true;

		if (npcid == 50014) { // 디 론
			if (!player.getInventory().HaveItemByClassId(40581)) { // 안 데드의 키
				isTeleport = false;
				htmlid = "dilongn";
			}
		} else if (npcid == 50043) { // Lambda
			if (isTeleportDelayed()) { // 텔레포트 지연중
				isTeleport = false;
			}
		} else if (npcid == 50625) { // 고대인(Lv50 퀘스트 고대의 공간 2 F)
			if (isTeleportDelayed()) { // 텔레포트 지연중
				isTeleport = false;
			}
		}

		if (isTeleport) { // 텔레포트 실행
			try {
				//  뮤탄트안트단젼(군주 Lv30 퀘스트)
				if (action.equalsIgnoreCase("teleport mutant-dungen_la")) {
					// 3 매스 이내의 Pc
					for (L1PcInstance otherPc : CWorld.getInstance()
							.getVisiblePlayer(player, 3)) {
						if (otherPc.getClanid() == player.getClanid()
								&& otherPc.getId() != player.getId()) {
//							L1Teleport.teleport(otherPc, 32740, 32800, (short) 217, 5,
//									true);
							otherPc.start_teleport(32740, 32800, 217, 5, 169, true, false);
						}
					}
					player.start_teleport(32740, 32800, 217, 5, 169, true, false);
				}
				// 시련의 지하 감옥(위저드 Lv30 퀘스트)
				else if (action.equalsIgnoreCase("teleport mage-quest-dungen_la")) {
					player.start_teleport(32791, 32788, 201, 5, 169, true, false);
				} else if (action.equalsIgnoreCase("teleport 29_la")) { // Lambda
					L1PcInstance kni = null;
					L1PcInstance elf = null;
					L1PcInstance wiz = null;
					// 3 매스 이내의 Pc
					CQuestSystem quest = null;
					for (L1PcInstance otherPc : CWorld.getInstance()
							.getVisiblePlayer(player, 3)) {
						quest = otherPc.getQuest();
						if (otherPc.isKnight() // 나이트
								&& quest.get_step(CQuestSystem.QUEST_LEVEL50) == 1) { // 디가르딘 동의가 끝난 상태
							if (kni == null) {
								kni = otherPc;
							}
						} else if (otherPc.isElf() // 요정
								&& quest.get_step(CQuestSystem.QUEST_LEVEL50) == 1) { // 디가르딘 동의가 끝난 상태
							if (elf == null) {
								elf = otherPc;
							}
						} else if (otherPc.isWizard() // 마법사
								&& quest.get_step(CQuestSystem.QUEST_LEVEL50) == 1) { // 디가르딘 동의가 끝난 상태
							if (wiz == null) {
								wiz = otherPc;
							}
						}
					}
					if (kni != null && elf != null && wiz != null) { // 전클래스 갖추어져 있다
						player.start_teleport(32723, 32850, 2000, 2, 169, true, false);
						kni.start_teleport(32750, 32851, 2000, 6, 169, true, false);
						elf.start_teleport(32878, 32980, 2000, 6, 169, true, false);
						wiz.start_teleport(32876, 33003, 2000, 0, 169, true, false);
						
						final long deadline = System.nanoTime() + TELEPORT_DELAY_NANOS;
						teleportDelayedUntil = deadline == 0L ? 1L : deadline;
					}
				} else if (action.equalsIgnoreCase("teleport barlog_la")) { // 고대인(Lv50 퀘스트 고대의 공간 2 F)
					player.start_teleport(32755, 32844, 2002, 5, 169, true, false);
					teleportDelayedUntil = 0L;
				}			

			} catch (Exception e) {
			}
		}
		if (htmlid != null) { // 표시하는 html가 있는 경우
			SC_NPCTalkReturn.send(player, objid, htmlid);
		}
	}

	private boolean isTeleportDelayed() {
		final long deadline = teleportDelayedUntil;
		return deadline != 0L && System.nanoTime() - deadline < 0L;
	}

	private static Logger _log = Logger.getLogger(l1j.server.server.model.Instance.L1TeleporterInstance.class.getName());

}