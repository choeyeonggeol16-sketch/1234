package l1j.server.server.model.Instance;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import I1j.server.server.model.Thread.LindviorBlessing;
import I1j.server.server.model.Thread.PapurionBlessing;
import I1j.server.server.model.Thread.ValakasBlessing;
import javolution.util.FastTable;
import l1j.server.L1DatabaseFactory;
import l1j.server.ActionCodes.EActionCodes;
import l1j.server.AutoHuntSystem.AutoHuntController;
import l1j.server.AutoHuntSystem.NativeStepSession;
import l1j.server.AutoPotionSystem.AutoPotionController;
import l1j.server.CharacterAction.AbstractActionHandler;
import l1j.server.CharacterAction.Executor.CharacterActionExecutor;
import l1j.server.CharacterAction.Teleport.Teleport;
import l1j.server.DungeonTimer.DungeonTimeInformation;
import l1j.server.DungeonTimer.DungeonTimeUserInformation;
import l1j.server.DungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.DungeonTimer.Progress.AccountTimeProgress;
import l1j.server.DungeonTimer.Progress.CharacterTimeProgress;
import l1j.server.DungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.ItemExChangeSystem.S_ItemExSelectPacket;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemAccount;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemAccountTable;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemCharacter;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemCharacterTable;
import l1j.server.LevelUpPresentSystem.LevelUpPresentSystemLoader;
import l1j.server.Templates.L1BookMark;
import l1j.server.Templates.L1EventNpcSpawn;
import l1j.server.Templates.L1PrivateShopBuyList;
import l1j.server.Templates.L1PrivateShopSellList;
import l1j.server.WarSystem.CastleWarBusiness;
import l1j.server.WarSystem.War;
import l1j.server.WarSystem.WarFactory.WAR_TYPE;
import l1j.server.configure.Config;
import l1j.server.server.Account;
import l1j.server.server.ActionCodes;
import l1j.server.server.GMCommands;
import l1j.server.server.GameClient;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.SkillCheck;
import l1j.server.server.Astar.L1Astar;
import l1j.server.server.Astar.L1Node;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.Controller.HpMpRegenController;
import l1j.server.server.datatables.CharBuffTable;
import l1j.server.server.datatables.CharacterSlotItemTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.EventNpcSpawnTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.datatables.PenaltyItemTable;
import l1j.server.server.datatables.PenaltyItemTable.L1PenaltyItem;
import l1j.server.server.model.Broadcaster;
import l1j.server.server.model.Getback;
import l1j.server.server.model.HpRegeneration;
import l1j.server.server.model.Attack;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1ChatParty;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.EquipmentSlot;
import l1j.server.server.model.L1HateList;
import l1j.server.server.model.L1HauntedHouse;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Karma;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1Party;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.L1PolyMorph;
import l1j.server.server.model.L1Quest;
import l1j.server.server.model.L1StatReset;
import l1j.server.server.model.L1Trade;
import l1j.server.server.model.L1World;
import l1j.server.server.model.MpRegeneration;
import l1j.server.server.model.classes.L1ClassFeature;
import l1j.server.server.model.gametime.GameTimeCarrier;
import l1j.server.server.model.item.armor.EquippedArmor;
import l1j.server.server.model.monitor.L1PcAutoUpdate;
import l1j.server.server.model.monitor.L1PcExpMonitor;
import l1j.server.server.model.monitor.L1PcGhostMonitor;
import l1j.server.server.model.monitor.L1PcInvisDelay;
import l1j.server.server.model.skill.SkillId;
import l1j.server.server.model.skill.SkillUse;
import l1j.server.server.serverpackets.S_CastleMaster;
import l1j.server.server.serverpackets.S_ChangeShape;
import l1j.server.server.serverpackets.S_CharVisualUpdate;
import l1j.server.server.serverpackets.S_Disconnect;
import l1j.server.server.serverpackets.S_DoActionGFX;
import l1j.server.server.serverpackets.S_DoActionShop;
import l1j.server.server.serverpackets.S_Emblem;
import l1j.server.server.serverpackets.S_Exp;
import l1j.server.server.serverpackets.S_HPMeter;
import l1j.server.server.serverpackets.S_HPUpdate;
import l1j.server.server.serverpackets.S_Invis;
import l1j.server.server.serverpackets.S_Lawful;
import l1j.server.server.serverpackets.S_Liquor;
import l1j.server.server.serverpackets.S_MPUpdate;
import l1j.server.server.serverpackets.S_OwnCharAttrDef;
import l1j.server.server.serverpackets.S_OwnCharStatus;
import l1j.server.server.serverpackets.S_OwnCharStatus2;
import l1j.server.server.serverpackets.S_PacketBox;
import l1j.server.server.serverpackets.S_Paralysis;
import l1j.server.server.serverpackets.S_PinkName;
import l1j.server.server.serverpackets.S_Poison;
import l1j.server.server.serverpackets.S_RemoveObject;
import l1j.server.server.serverpackets.S_ReturnedStat;
import l1j.server.server.serverpackets.S_SPMR;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.serverpackets.S_SkillSound;
import l1j.server.server.serverpackets.S_SystemMessage;
import l1j.server.server.serverpackets.S_Teleport;
import l1j.server.server.serverpackets.S_WorldObject;
import l1j.server.server.serverpackets.S_bonusstats;
import l1j.server.server.serverpackets.ServerBasePacket;
import l1j.server.server.utils.CalcUtil;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SQLUtil;
import manager.SJManager;

public class L1PcInstance extends L1Character {
	private static final long serialVersionUID = 1L;

	//////////////// 클래스 관련  //////////////////
	public static final int[][] CLASS_GFX_LIST = { { 0, 1 }, { 61, 48 }, { 138, 37 }, { 734, 1186 }, { 2786, 2796 } };

	public static final int CLASSID_PRINCE = 0;
	public static final int CLASSID_PRINCESS = 1;
	public static final int CLASSID_KNIGHT_MALE = 61;
	public static final int CLASSID_KNIGHT_FEMALE = 48;
	public static final int CLASSID_ELF_MALE = 138;
	public static final int CLASSID_ELF_FEMALE = 37;
	public static final int CLASSID_WIZARD_MALE = 734;
	public static final int CLASSID_WIZARD_FEMALE = 1186;
	public static final int CLASSID_DARK_ELF_MALE = 2786;
	public static final int CLASSID_DARK_ELF_FEMALE = 2796;


	public boolean isCrown() {
		return (getClassId() == CLASSID_PRINCE || getClassId() == CLASSID_PRINCESS);
	}
	public boolean isKnight() {
		return (getClassId() == CLASSID_KNIGHT_MALE || getClassId() == CLASSID_KNIGHT_FEMALE);
	}
	public boolean isElf() {
		return (getClassId() == CLASSID_ELF_MALE || getClassId() == CLASSID_ELF_FEMALE);
	}
	public boolean isWizard() {
		return (getClassId() == CLASSID_WIZARD_MALE || getClassId() == CLASSID_WIZARD_FEMALE);
	}
	public boolean isDarkelf() {
		return (getClassId() == CLASSID_DARK_ELF_MALE || getClassId() == CLASSID_DARK_ELF_FEMALE);
	}	
	///////////// 파푸리온  블레싱 관련 ////////////////
	private PapurionBlessing _PapuRegen;
	private boolean _PapuBlessingActive;
	private static Timer _Timer = new Timer(true);
	private static Timer _DargonBlessingTimer = new Timer(true);
	
	public boolean Full_Inventory = false;
	
	public short _젠도르빈줌갯수 = 0;
	
	public void startPapurionBlessing() {
		final int RegenTime = 180000;
		if (!_PapuBlessingActive) {
			_PapuRegen = new PapurionBlessing(this);
			_DargonBlessingTimer.scheduleAtFixedRate(_PapuRegen, RegenTime, RegenTime);
			_PapuBlessingActive = true;
		}
	}
	public void stopPapurionBlessing() {
		if (_PapuBlessingActive) {
			_PapuRegen.cancel();
			_PapuRegen = null;
			_PapuBlessingActive = false;
		}
	}
	///////////// 린드비오르  블레싱 관련 ////////////////
	private LindviorBlessing _LindRegen;
	private boolean _LindBlessingActive;

	public void startLindviorBlessing() {
		final int RegenTime = 180000;
		if (!_LindBlessingActive) {
			_LindRegen = new LindviorBlessing(this);
			_DargonBlessingTimer.scheduleAtFixedRate(_LindRegen, RegenTime, RegenTime);
			_LindBlessingActive = true;	
		}
	}

	public void stopLindviorBlessing() {
		if (_LindBlessingActive) {
			_LindRegen.cancel();
			_LindRegen = null;
			_LindBlessingActive = false;
		}
	}
	///////////// 발라카스 블레싱 관련 ////////////////
	private ValakasBlessing _vBlessing;
	private boolean _isValakasBlessing;

	public void startValakasBlessing() {
		final int RegenTime = 16000;	
		if (!_isValakasBlessing) {
			_vBlessing = new ValakasBlessing(this);
			_DargonBlessingTimer.scheduleAtFixedRate(_vBlessing, RegenTime, RegenTime);
			_isValakasBlessing = true;
		}
	}

	public void stopValakasBlessing() {
		if (_isValakasBlessing) {
			_vBlessing.cancel();
			_vBlessing = null;
			_isValakasBlessing = false;
		}
	}
	
	
	/** 수배관련시스템 **/
	private int[] beWanted = new int[7];

	public int[] getBeWanted() {
		return this.beWanted;
	}

	public void setBeWanted(int[] beWanted) {
		this.beWanted = beWanted;
	}

	public void initBeWanted() {
		addMeleeDmg(-beWanted[0]);
		addMeleeHit(-beWanted[1]);
		addRangedDmg(-beWanted[2]);
		addRangedHit(-beWanted[3]);
		addSp(-beWanted[4]);
		getAC().addAc(beWanted[5]);
		for (int i = 0; i < beWanted.length; i++)
			beWanted[i] = 0;
		
		sendPackets(new S_SPMR(this));
		sendPackets(new S_OwnCharAttrDef(this)); 
		sendPackets(new S_OwnCharStatus(this));
	}

	public void addBeWanted() {
		addMeleeDmg(beWanted[0]);
		addMeleeHit(beWanted[1]);
		addRangedDmg(beWanted[2]);
		addRangedHit(beWanted[3]);
		addSp(beWanted[4]);
		getAC().addAc(-beWanted[5]);
		sendPackets(new S_SPMR(this));
		sendPackets(new S_OwnCharAttrDef(this)); 
		sendPackets(new S_OwnCharStatus(this));
	}
	/** 수배관련시스템 **/
	//////////////// 캐릭터, 인형 회복 컨트롤러 관련  //////////////////
	public static final int STATE_NONE = 0x01;
	public static final int STATE_ATTACK = 0x02;
	public static final int STATE_MOVE = 0x03;	
	private int regenState;
	private long lastRegenTime;
	
	private HpRegeneration _hpRegen;
	private MpRegeneration _mpRegen;
	
	private HpMpRegenController _hpmpRegen;
	private boolean _hpmpRegenActive;

	public void setHpMpRegenActive(boolean flag) {
		_hpmpRegenActive = flag;
	}

	public long getLastRegenTime() {
		return lastRegenTime;
	}
	public int getRegenState() {
		return this.regenState;
	}
	
	public void setRegenState(int state) {
		if (_mpRegen != null) {
			_mpRegen.setState(state);
		}
		if (_hpRegen != null) {
			_hpRegen.setState(state);
		}
	}
	
	/** 피엠틱 컨트롤러 **/
	public void startHpMpRegeneration() {
		if (!_hpmpRegenActive) {
			_hpmpRegen = new HpMpRegenController(this);
			_Timer.scheduleAtFixedRate(_hpmpRegen, 1000, 1000);
			_hpmpRegenActive = true;
		}
	}

	public void stopHpMpRegeneration() {
		if (_hpmpRegenActive) {
			_hpmpRegen.cancel();
			_hpmpRegen = null;
			_hpmpRegenActive = false;
		}
	}
	
	/*private boolean regenActive;
	private CharacterHpMpController calcHpMpRegen;
	public void setRegenState(int state) {
		this.regenState = state;
		this.lastRegenTime = System.currentTimeMillis();
		if(!isDead()) {
			startHpMpRegeneration();
		}
	}*/
	/**
	 * hpmp Tick Timer 시작
	 */
	/*public void startHpMpRegeneration() {
		if(!regenActive) {
			regenActive = true;
			if(calcHpMpRegen == null) {
				calcHpMpRegen = new CharacterHpMpController(this);
				if(_Timer == null) {
					_Timer = new Timer();				
				}
				_Timer.scheduleAtFixedRate(calcHpMpRegen, 1000, 1000);
			}
		}
	}*/
	/**
	 * hpmp Tick Timer 종료
	 */
	/*public void stopHpMpRegeneration() {
		if(regenActive) {
			regenActive = false;
			if(calcHpMpRegen != null) {
				calcHpMpRegen.cancel();
				calcHpMpRegen = null;
			}
			if(_Timer != null) {
				_Timer.cancel();
				_Timer = null;
			}
		}		
	}*/
	public void startHpRegenerationByDoll() {
		DollInstance doll = getMagicDoll();
		if (doll != null) {
			doll.startHprTimer();
		}
	}
	public void stopHpRegenerationByDoll() {
		DollInstance doll = getMagicDoll();
		if (doll != null) {
			doll.stopHprTimer();
		}
	}
	public void startMpRegenerationByDoll() {
		DollInstance doll = getMagicDoll();
		if (doll != null) {
			doll.startMprTimer();
		}
	}
	public void stopMpRegenerationByDoll() {
		DollInstance doll = getMagicDoll();
		if (doll != null) {
			doll.stopMprTimer();
		}
	}
	//////////////////바포메트 시스템 ////////////////////////
	private int _nbapoLevel;
	private int _obapoLevel;
	private int _bapodmg;
	public int LawfulAC = 0;
	public int LawfulMR = 0;
	public int LawfulSP = 0;
	public int LawfulAT = 0;

	public int getBapodmg() {
		return _bapodmg;
	}

	public void setBapodmg(int i) {
		_bapodmg = i;
	}

	public int getNBapoLevel() {
		return _nbapoLevel;
	}

	public void setNBapoLevel(int i) {
		_nbapoLevel = i;
	}

	public int getOBapoLevel() {
		return _obapoLevel;
	}

	public void setOBapoLevel(int i) {
		_obapoLevel = i;
	}
	/////////////////// 콜 클랜 관련 구현 /////////////////////
	private int _callClanId;
	private int _callClanHeading;
	public int getCallClanId() { 
		return _callClanId; 
	}
	public void setCallClanId(int i) {
		_callClanId = i;
	}
	public int getCallClanHeading() { 
		return _callClanHeading; 	
	}
	public void setCallClanHeading(int i) {	
		_callClanHeading = i;	
	}
	////////////// 스킬 마스터리 관련 /////////////////////////
	private ArrayList<Integer> skillList = new ArrayList<Integer>();
	public void setSkillMastery(int skillid) {
		if (!skillList.contains(skillid)) {
			skillList.add(skillid);
		}
	}
	public void removeSkillMastery(int skillid) {
		if (skillList.contains((Object) skillid)) {
			skillList.remove((Object) skillid);
		}
	}
	public boolean isSkillMastery(int skillid) {
		return skillList.contains(skillid);
	}	
	////////////// 캐릭터 자동저장 시스템  /////////////////////////
	private long lastSavedTime = System.currentTimeMillis();
	private long lastSavedTime_inventory = System.currentTimeMillis();

	public long getlastSavedTime() {
		return lastSavedTime;
	}
	public long getlastSavedTime_inventory() {
		return lastSavedTime_inventory;
	}
	public void setlastSavedTime(long stime) {
		this.lastSavedTime = stime;
	}
	public void setlastSavedTime_inventory(long stime) {
		this.lastSavedTime_inventory = stime;
	}
	
	//////////// 피틱 관련 ///////////////
	private short _hpr = 0;
	private short _trueHpr = 0;

	public short getHpr() {
		return _hpr;
	}
	public void addHpr(int i) {
		_trueHpr += i;
		_hpr = _trueHpr;
	}
	
	
	/////////// 엠틱 관련 /////////////////
	private short _mpr = 0;
	private short _trueMpr = 0;

	public short getMpr() {
		return _mpr;
	}
	public void addMpr(int i) {
		_trueMpr += i;
		_mpr = (short) Math.max(0, _trueMpr);
	}
	////////////// 아인하사드 관련 /////////////
	private int _einhasad;
	public int getEinhasad() {
		return _einhasad;
	}
	public void calEinhasad(int i) {
		int calc = _einhasad + i;
		if (calc >= 2000000) {
			calc = 2000000;
		}
		if (calc < 0) {
			calc = 0;
		}
		_einhasad = calc;
	}
	public void setEinhasad(int einhasad) {
		_einhasad = einhasad;
	}

	////////////// 생일 관련 /////////////
	private int birthday;// 생일
	public int getBirthDay() {
		return birthday;
	}
	public void setBirthDay(int t) {
		birthday = t;
	}
	public boolean EquipChange = false;   
	public boolean _fake = false;
	public boolean war_zone = false;

	public void setFake() {
		_fake = true;
	}
	public boolean getFake() {
		return _fake;
	}

	private long _lastQuizChangeTime;
	public long get_lastQuizChangeTime() {
		return _lastQuizChangeTime;
	}
	private String _tempQuiz;
	private boolean _waitQuiz = false;
	private boolean _isQuizValidated = false; 

	public void setQuizValidated() {
		_isQuizValidated = true;
	} 
	public boolean isQuizValidated() {
		return _isQuizValidated;
	}
	public void setTempQuiz(String quiz) {
		_tempQuiz = quiz;
		_waitQuiz = true;
	}
	public boolean waitQuiz() {
		return _waitQuiz;
	}
	public String getTempQuiz() {
		_waitQuiz = false;
		return _tempQuiz;
	}
	private boolean _needQuiz = false;

	public void setNeedQuiz(boolean needQuiz) {
		_needQuiz = needQuiz;
	} 
	public boolean isNeedQuiz() {
		return _needQuiz;
	}
	public void update_lastQuizChangeTime() {
		_lastQuizChangeTime = System.currentTimeMillis();
	}

	public boolean noPlayerck2 = false;
	public boolean 물품멘트 = false;
	public boolean _Blessleaf = false;
	public boolean RootMent = true;// 루팅 멘트
	public boolean 문장주시 = false;


	////////// 수배 관련 ///////////
	private String _reasontohunt;
	public String getReasonToHunt() {
		return _reasontohunt;
	}
	public void setReasonToHunt(String s) {
		_reasontohunt = s;
	}
	private int huntCount;
	public int getHuntCount() {
		return huntCount;
	}
	public void setHuntCount(int i) {
		huntCount = i;
	}
	private int huntPrice;
	public int getHuntPrice() {
		return huntPrice;
	}
	public void setHuntPrice(int i) {
		huntPrice = i;
	}
	private AtomicInteger _pinkNameTime;

	public int getPinkNameTime() {
		return _pinkNameTime.get();
	}
	public int DecrementPinkNameTime() {
		return _pinkNameTime.decrementAndGet();
	}
	public int SetPinkNameTime(int timeValue) {
		return _pinkNameTime.getAndSet(timeValue);
	}

	private static final long INTERVAL_AUTO_UPDATE = 300;
	private ScheduledFuture<?> _autoUpdateFuture;

	private static final long INTERVAL_EXP_MONITOR = 500;
	private ScheduledFuture<?> _expMonitorFuture;

	// 플레이어 캐릭터 오브젝트 업데이트 관련 
	public void startObjectAutoUpdate() {
		removeAllKnownObjects();
		_autoUpdateFuture = GeneralThreadPool.getInstance().pcScheduleAtFixedRate(new L1PcAutoUpdate(getId()), 0L, INTERVAL_AUTO_UPDATE);
	}
	/**
	 * 각종 모니터 태스크를 정지한다.
	 */
	public void stopEtcMonitor() {
		if (_autoUpdateFuture != null) {
			_autoUpdateFuture.cancel(true);
			_autoUpdateFuture = null;
		}
		if (_expMonitorFuture != null) {
			_expMonitorFuture.cancel(true);
			_expMonitorFuture = null;
		}
		if (_ghostFuture != null) {
			_ghostFuture.cancel(true);
			_ghostFuture = null;
		}
		if (_hellFuture != null) {
			_hellFuture.cancel(true);
			_hellFuture = null;
		}
	}
	public void onChangeExp() {
		int level = ExpTable.getLevelByExp(getExp());
		int char_level = getLevel();
		int gap = level - char_level;
		if (gap == 0) { 
			sendPackets(new S_Exp(this));
			return;
		}
		// 레벨이 변화했을 경우
		if (gap > 0) {
			levelUp(gap);
		} else if (gap < 0) {
			levelDown(gap);
		}
	}

	/**
	 * 근접 인식 리스너
	 */
	@Override
	public void onPerceive(L1PcInstance pc) {

		if (isGmInvis()) {
			return;
		}
		// 새롭게 오브젝트를 추가.
		pc.addKnownObject(this);

		pc.sendPackets(S_WorldObject.out(this));
		
		// 파티 갱신
		if(isInParty() && getParty().isMember(pc)) {
			pc.sendPackets(new S_HPMeter(this));
		}
		if (pc.isPinkName()) {
			pc.sendPackets(new S_PinkName(pc.getId(), pc.getPinkNameTime()));
		}
		for (L1PcInstance target : L1World.getInstance().getVisiblePlayer(pc)) {
			if (target == null) {
				continue;
			}
			if (target.isPinkName()) {
				pc.sendPackets(new S_PinkName(target.getId(), target.getPinkNameTime()));
			}
		}
		
		// 개인 상점 메세지 보냄 
		if(isPrivateShop()) {
			pc.sendPackets(new S_DoActionShop(getId(), ActionCodes.ACTION_Shop, getShopChat()));
		}
	
		
		// 피시가 군주 일때 왕관 이미지를 보냄 
		if (isCrown()) {
			L1Clan clan = L1World.getInstance().getClan(getClanname());
			if (clan != null) {
				if (getId() == clan.getLeaderId() && clan.getCastleId() != 0) {
					pc.sendPackets(new S_CastleMaster(clan.getCastleId(), getId()));
				}
			}
		}
	}
	// 범위외가 된 인식이 끝난 오브젝트를 제거
	private void removeOutOfRangeObjects() {
		for (L1Object known : getKnownObjects()) {
			if (known == null) {
				continue;
			}
			if (Config.PC_RECOGNIZE_RANGE == -1) {
				if (!getLocation().isInScreen(known.getLocation())) { // 화면외
					removeKnownObject(known);
					sendPackets(S_RemoveObject.get(known));
				}
			} else {
				if (getLocation().getTileLineDistance(known.getLocation()) > Config.PC_RECOGNIZE_RANGE) {
					removeKnownObject(known);
					sendPackets(S_RemoveObject.get(known));
				}
			}
		}
	}

	/**
	 * 오브젝트 인식 관련 
	 */
	public void updateObject() {
		removeOutOfRangeObjects();

		// 인식 범위내의 오브젝트 리스트를 작성
		for(L1Object visible : L1World.getInstance().getVisibleObjects(this, Config.PC_RECOGNIZE_RANGE)) {
			if (!knownsObject(visible)) {
				visible.onPerceive(this);
			} else {
				if (visible instanceof L1NpcInstance) {
					L1NpcInstance npc = (L1NpcInstance) visible;
					if (getLocation().isInScreen(npc.getLocation()) && npc.getHiddenStatus() != 0) {
						npc.approachPlayer(this);
					}
				}
			}
			// 게임 마스터 피바 켬 정보 보냄 
			if(hasSkillEffect(SkillId.GMSTATUS_HPBAR) && GMCommands.isHpBarTarget(visible)) {
				sendPackets(new S_HPMeter((L1Character) visible));		
			}
			if (isCompulsionAttack()) {
				if (visible instanceof L1PcInstance) {
					L1PcInstance target = (L1PcInstance) visible;
					if (target != null) {
						sendPackets(new S_PinkName(target.getId(), 1));
					}
				}
			}
		}
	}
	private void sendVisualEffect() {
		int poisonId = 0;
		if (getPoison() != null) { // 독상태
			poisonId = getPoison().getEffectId();
		}
		if (getParalysis() != null) { // 마비 상태
			// 마비 effect를 우선해 보내고 싶기 때문에, poisonId를 덧쓰기.
			poisonId = getParalysis().getEffectId();
		}
		if (poisonId != 0) {
			S_Poison ss = new S_Poison(getId(), poisonId);
			sendPackets(ss, false);
			broadcastPacket(ss, true);
		}
	}

	public void sendVisualEffectAtLogin() {
		sendVisualEffect();
	}

	public void sendVisualEffectAtTeleport() {
		if (isDrink()) { // liquor로 취하고 있다
			S_Liquor s = new S_Liquor(getId(), 1);
			sendPackets(s, false);
			broadcastPacket(s, true);
		}
		sendVisualEffect();
	}

	public L1PcInstance() {
		_accessLevel = 0;
		_currentWeapon = 0;
		_inventory = new L1PcInventory(this);
		_tradewindow = new L1Inventory();
		_bookmarks = new ArrayList<L1BookMark>();
		_quest = new L1Quest(this);
		_equipSlot = new EquipmentSlot(this);
		_pinkNameTime = new AtomicInteger(0);
		_Equipped = new EquippedArmor(this);
	}

	private EquippedArmor _Equipped;
	public EquippedArmor toArmorEquipped() {
		return _Equipped;
	}

	@Override
	public void setCurrentHp(int i) {
		if (getCurrentHp() == i) {
			return;
		}
		int currentHp = i;
		if (currentHp >= getMaxHp()) {
			currentHp = getMaxHp();
		}
		setCurrentHpDirect(currentHp);
		sendPackets(new S_HPUpdate(currentHp, getMaxHp()));
		if (isInParty()) { // 파티중
			getParty().updateMiniHP(this);
		}
	}

	@Override
	public void setCurrentMp(int i) {
		if (getCurrentMp() == i) {
			return;
		}
		int currentMp = i;
		if (currentMp >= getMaxMp() || isGm()) {
			currentMp = getMaxMp();
		}
		setCurrentMpDirect(currentMp);
		sendPackets(new S_MPUpdate(currentMp, getMaxMp()));
	}

	@Override
	public L1PcInventory getInventory() {
		return _inventory;
	}
	public L1Inventory getTradeWindowInventory() {
		return _tradewindow;
	}

	public boolean isGmInvis() {
		return _gmInvis;
	}
	public void setGmInvis(boolean flag) {
		_gmInvis = flag;
	}
	public int getCurrentWeapon() {
		return _currentWeapon;
	}
	public void setCurrentWeapon(int i) {
		_currentWeapon = i;
	}
	public short getAccessLevel() {
		return _accessLevel;
	}
	public void setAccessLevel(short i) {
		_accessLevel = i;
	}
	public int getClassId() {
		return _classId;
	}
	public void setClassId(int i) {
		_classId = i;
		_classFeature = L1ClassFeature.newClassFeature(i);
	}
	private L1ClassFeature _classFeature = null;

	public L1ClassFeature getClassFeature() {
		return _classFeature;
	}
	@Override
	public synchronized int getExp() {
		return _exp;
	}
	@Override
	public synchronized void setExp(int i) {
		_exp = i;
	}

	private int _returnstat;
	private int _returnstatus;
	private L1StatReset _statReset;

	public void setStatReset(L1StatReset sr) {
		_statReset = sr;
	}
	public L1StatReset getStatReset() {
		return _statReset;
	}
	public synchronized int getReturnStat() {
		return _returnstat;
	}
	public synchronized void setReturnStat(int i) {
		_returnstat = i;
	}
	public synchronized int getReturnStatus() {
		return _returnstatus;
	}
	public synchronized void setReturnStatus(int i) {
		_returnstatus = i;
	}
	public boolean LoadCheckStatus() {
		int totalS = getAbility().getAmount();
		int bonusS = (getHighLevel() - 50);
		if (bonusS < 0) { 
			bonusS = 0;
		}
		int calst = totalS - (bonusS + getElixirStats() + 75);

		if (calst > 0 && !isGm()) {
			SkillUse skill = new SkillUse();
			skill.handleCommands(this,	SkillId.캔슬레이션, getId(), getX(), getY(), null, 0, SkillUse.TYPE_LOGIN);

			if(getWeapon() != null) {
				getInventory().setEquipped(getWeapon(), false, false, false);
			}
			for (ItemInstance armor : getInventory().getItems()) {
				for (int type = 0; type <= 13; type++) {
					if (armor != null) {
						getInventory().setEquipped(armor, false, false, false);
					}
				}
			} 
			sendPackets(S_CharVisualUpdate.get(this));
			sendPackets(new S_SPMR(this));
			sendPackets(new S_OwnCharAttrDef(this)); 
			sendPackets(new S_OwnCharStatus(this));
			setReturnStat(getExp());
			setReturnStatus(1);
			sendPackets(new S_ReturnedStat(this, S_ReturnedStat.START));
			try {
				save();
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}

			return false;
		}

		return true;
	}

	public void CheckStatus() {
		int totalS = ability.getAmount();
		int bonusS = getLevel() - 50;
		if (bonusS < 0) {
			bonusS = 0;
		}
		int calst = totalS - (bonusS + getElixirStats() + 75);

		if (calst > 0 && !isGm()) {
			SkillUse l1skilluse = new SkillUse();
			l1skilluse.handleCommands(this, SkillId.캔슬레이션, getId(), getX(), getY(), null, 0, SkillUse.TYPE_LOGIN);

			if (getWeapon() != null) {
				getInventory().setEquipped(getWeapon(), false, false, false);
			}
			for (ItemInstance armor : getInventory().getItems()) {
				for (int type = 0; type <= 13; type++) {
					if (armor != null) {
						getInventory().setEquipped(armor, false, false, false);
					}
				}
			}
			setReturnStat(getExp());
			sendPackets(S_CharVisualUpdate.get(this));
			sendPackets(new S_SPMR(this));
			sendPackets(new S_OwnCharAttrDef(this));
			sendPackets(new S_OwnCharStatus2(this));
			sendPackets(new S_ReturnedStat(this, S_ReturnedStat.START));
			try {
				save();
			} catch (Exception e) {
				_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			}
		}
	}

	private int _PKcount; // ● PK카운트
	public int get_PKcount() {
		return _PKcount;
	}
	public void set_PKcount(int i) {
		_PKcount = i;
	}
	private int _clanid; // ● 클랜 아이디
	public int getClanid() {
		return _clanid;
	}
	public void setClanid(int i) {
		_clanid = i;
	}
	private String clanname; // ● 크란명
	public String getClanname() {
		return clanname;
	}
	public void setClanname(String s) {
		clanname = s;
	}
	// 참조를 가지도록(듯이) 하는 편이 좋을지도 모른다
	public L1Clan getClan() {
		return L1World.getInstance().getClan(getClanname());
	}
	private int _clanRank; // ● 혈맹 계급 ( 군주, 수호기사, 일반, 견습 )
	public int getClanRank() {
		return _clanRank;
	}
	public void setClanRank(int i) {
		_clanRank = i;
	}
	private byte _sex; // ● 성별
	public byte get_sex() {
		return _sex;
	}
	public void set_sex(int i) {
		_sex = (byte) i;
	}
	public boolean isGm() {
		return _gm;
	}
	public void setGm(boolean flag) {
		_gm = flag;
	}
	public boolean isMonitor() {
		return _monitor;
	}
	public void setMonitor(boolean flag) {
		_monitor = flag;
	}
	private L1PcInstance getStat() {
		return null;
	}
	public void reduceCurrentHp(double d, L1Character l1character) {
		getStat().reduceCurrentHp(d, l1character);
	}

	/**
	 * 지정된 플레이어군에게 로그아웃 한 것을 통지한다
	 * @param playersList 통지하는 플레이어의 배열
	 */
	public void notifyPlayersLogout(List<L1PcInstance> playersArray) {
		for (L1PcInstance player : playersArray) {
			if (player.knownsObject(this)) {
				player.removeKnownObject(this);
				player.sendPackets(S_RemoveObject.get(this), true);
			}
		}
	}
	public static void quitGame(L1PcInstance pc) {

		if (!pc.getFake() && (!pc.noPlayerck2)  && !pc.isPrivateShop() && pc != null) {
			SJManager.getInstance().LogLogOutAppend(pc.getName(), pc.getNetConnection().getHostname());
		} 
		// 사망하고 있으면(자) 거리에 되돌려, 공복 상태로 한다
		if (pc.isDead()) {
			int[] loc = Getback.GetBack_Location(pc, true);
			pc.setX(loc[0]);
			pc.setY(loc[1]);
			pc.setMap((short) loc[2]);
			pc.setCurrentHp(pc.getLevel());
			pc.setFood(3);
		}
		try {
			if (pc.isFishing()) {
				FishingTimeController.getInstance().endFishing(pc);
			}
		} catch (Exception e) {
			e.getStackTrace();
		}
		// 트레이드를 중지한다
		if (pc.getTradeID() != 0) { // 트레이드중
			L1Trade trade = new L1Trade();
			trade.TradeCancel(pc);
		}
		// 파티를 빠진다
		if (pc.isInParty()) { // 파티중
			pc.getParty().leaveMember(pc);
		}
		// 애완동물을 월드 MAP상으로부터 지운다
		Object[] petList = pc.getPetList().values().toArray();
		for (Object petObject : petList) {
			if (petObject instanceof PetInstance) { // 펫
				PetInstance pet = (PetInstance) petObject;
				pet.dropItem();
				pc.getPetList().remove(pet.getId());
				pet.deleteMe();
			}
			if (petObject instanceof SummonInstance) {
				SummonInstance summon = (SummonInstance) petObject;
				summon.Death(null);
			}
		}
		// 마법 인형을 월드 맵상으로부터 지운다
		DollInstance doll = pc.getMagicDoll();
		if (doll != null) {
			doll.deleteDoll();
		}
		CharBuffTable.DeleteBuff(pc);
		CharBuffTable.SaveBuff(pc);
		pc.clearSkillEffectTimer();
		SkillCheck.getInstance().QuitDelSkill(pc);

		// (버그 방지) -값 진품 만들기 버그 방지 
		List<ItemInstance> itemlist = pc.getInventory().getItems();
		for (ItemInstance item : itemlist) {
			if (item.getCount() <= 0) {
				pc.getInventory().deleteItem(item);
				continue;
			}
		}
		// pc의 모니터를 stop 한다.
		pc.stopEtcMonitor();
		// 온라인 상태를 OFF로 해, DB에 캐릭터 정보를 기입한다
		pc.setOnlineStatus(0);
		pc.EndAutoController();
		l1j.server.server.network.NativeStepControl.detach(pc);
		pc.EndAutoPotionController();
		try {
			pc.save();
			pc.saveInventory();
			DungeonTimeProgressLoader.update(pc);

		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
	}

	private boolean _destroyed = false; 
	public boolean isDestroyed() {
		return _destroyed;
	}

	public void logout() {
		try {
			synchronized (this) {
				L1World world = L1World.getInstance();
				try {
					world.removeVisibleObject(this);
					world.removeObject(this);
				} catch (Exception e) {}
				if (_destroyed) {
					return;
				}
				if (getAccount() != null) {
					getAccount().updateBlessOfAin();
				}
				if (!isPrivateShop()) {
					DungeonTimeProgressLoader.update(this);
				}
				CharacterSlotItemTable.getInstance().updateCharSlotItems(this);
				_slotItemTwo.clear();
				_slotItemOne.clear();

				notifyPlayersLogout(getKnownPlayers());
				notifyPlayersLogout(world.getRecognizePlayer(this));
				quitGame(this);
				world.removeVisibleObject(this);
				world.removeObject(this);
				_inventory.clearItems(); 
				removeAllKnownObjects();
				stopHpMpRegeneration();
				stopPapurionBlessing();
				stopLindviorBlessing();
				stopValakasBlessing();
				stopEquipmentTimer();
				setDead(true); // 사용법 이상할지도 모르지만, NPC에 소멸한 것을 알게 하기 (위해)때문에
				setNetConnection(null); 
				_destroyed = true;
				dispose();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	private GameClient _netConnection;
	// 계정 정로 로드시 필요..
	public Account getAccount() {
		if (_netConnection == null) {
			return null;
		}
		return _netConnection.getAccount();
	}
	public GameClient getNetConnection() {
		return _netConnection;
	}
	public void setNetConnection(GameClient clientthread) {
		_netConnection = clientthread;
	}
	public boolean isInParty() {
		return getParty() != null;
	}
	private L1Party _party;
	public L1Party getParty() {
		return _party;
	}
	public void setParty(L1Party p) {
		_party = p;
	}
	private int _partyID;
	public int getPartyID() {
		return _partyID;
	}
	public void setPartyID(int partyID) {
		_partyID = partyID;
	}	
	private int _partyType;
	public int getPartyType() {
		return _partyType;
	}
	public void setPartyType(int partyType) {
		_partyType = partyType;
	}
	private L1ChatParty _chatparty;
	public boolean isInChatParty() {
		return getChatParty() != null;
	}
	public L1ChatParty getChatParty() {
		return _chatparty;
	}
	public void setChatParty(L1ChatParty p) {
		_chatparty = p;
	}
	private int _chatPartyID;
	public int getChatPartyID() {
		return _chatPartyID;
	}
	public void setChatPartyID(int chatPartyID) {
		_chatPartyID = chatPartyID;
	}
	private int _tradeID;
	public int getTradeID() {
		return _tradeID;
	}
	public void setTradeID(int tradeID) {
		_tradeID = tradeID;
	}
	private boolean _tradeOk;
	public void setTradeOk(boolean tradeOk) {
		_tradeOk = tradeOk;
	}
	public boolean getTradeOk() {
		return _tradeOk;
	}
	private boolean _tradeReady; 	
	public boolean getTradeReady() {
		return _tradeReady;
	}
	public void setTradeReady(boolean tradeOk) {
		_tradeReady = tradeOk;
	}
	private int _tempID;
	public int getTempID() {
		return _tempID;
	}
	public void setTempID(int tempID) {
		_tempID = tempID;
	}
	private boolean _isCanWhisper = true; 
	public boolean isCanWhisper() {
		return _isCanWhisper;
	}
	public void setCanWhisper(boolean flag) {
		_isCanWhisper = flag;
	}
	private boolean _isTeleport;
	public boolean getTeleport() {
		return _isTeleport;
	}
	public void setTeleport(boolean flag) {
		this._isTeleport = flag;
		if (flag) nativeStepSession.cancelMovement();
		if (!flag) {
		}		
	}

	public boolean isDrink() {
		return _isDrink;
	}

	public void setDrink(boolean flag) {
		_isDrink = flag;
	}

	public boolean isGres() {
		return _isGres;
	}

	public void setGres(boolean flag) {
		_isGres = flag;
	}

	public boolean isPinkName() {
		return _isPinkName;
	}

	public void setPinkName(boolean flag) {
		_isPinkName = flag;
	}

	private ArrayList<L1PrivateShopSellList> _sellList = new ArrayList<L1PrivateShopSellList>();


	public ArrayList<L1PrivateShopSellList> getSellList() {
		return _sellList;
	}

	private ArrayList<L1PrivateShopBuyList> _buyList = new ArrayList<L1PrivateShopBuyList>();

	public ArrayList<L1PrivateShopBuyList> getBuyList() {
		return _buyList;
	}

	private byte[] _shopChat;

	public void setShopChat(byte[] chat) {
		_shopChat = chat;
	}

	public byte[] getShopChat() {
		return _shopChat;
	}

	private boolean _isPrivateShop = false;

	public boolean isPrivateShop() {
		return _isPrivateShop;
	}

	public void setPrivateShop(boolean flag) {
		_isPrivateShop = flag;
	}
	private boolean _isTradingInPrivateShop = false;
	public boolean isTradingInPrivateShop() {
		return _isTradingInPrivateShop;
	} 
	public void setTradingInPrivateShop(boolean flag) {
		_isTradingInPrivateShop = flag;
	}

	private int _partnersPrivateShopItemCount = 0; // 열람중의 개인 상점의 아이템수

	public int getPartnersPrivateShopItemCount() {
		return _partnersPrivateShopItemCount;
	}
	public void setPartnersPrivateShopItemCount(int i) {
		_partnersPrivateShopItemCount = i;
	}
	public void sendPackets(ServerBasePacket serverbasepacket) {
		if (getNetConnection() == null) {
			return;
		}
		try {
			getNetConnection().sendPacket(serverbasepacket);
		} catch (Exception e) {
		}
	}
	public void sendPackets(ServerBasePacket serverPacket, boolean clear) {
		try {
			sendPackets(serverPacket);
			if (clear) {
				serverPacket.clear();
				serverPacket = null;
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
			e.printStackTrace();
		}
	}

	public void sendPackets(ServerBasePacket[] pcks, boolean clear) {
		if (getNetConnection() != null) {
			for (ServerBasePacket pck : pcks) {
				sendPackets(pck);
			}
		}
	}

	public void sendPackets(String s) {
		sendPackets(new S_SystemMessage(s), true);
	}

	public void sendPackets(int code) {
		sendPackets(new S_ServerMessage(code));
	}

	private FastTable<String> last_packet = new FastTable<String>();

	public FastTable<String> getLastPacket() {
		if (last_packet == null) {
			return null;
		}
		return last_packet;
	}

	public void setLastPacket(String value) {
		if (last_packet.size() >= 20) {
			last_packet.remove(0);
		}
		last_packet.add(value);
	}

	@Override
	public void onAction(L1PcInstance attacker) {
		if (attacker == null) {
			return;
		}
		// 텔레포트 처리중
		if (getTeleport()) {
			return;
		}
		// 공격받는 측 또는 공격하는 측이 세이프티 존
		if (getZoneType() == 1 || attacker.getZoneType() == 1) {
			Attack attack_mortion = new Attack(attacker, this);
			attack_mortion.action();
			return;
		}
		if (checkNonPvP(this, attacker)) {
			return;
		}
		if (getCurrentHp() > 0 && !isDead()) {
			attacker.delInvis();

			boolean isCounterBarrier = false;
			Attack attack = new Attack(attacker, this);
			if (attack.calcHit()) {
				if (hasSkillEffect(SkillId.카운터_배리어)) {
					boolean isProbability = false;
					if (CommonUtil.randomRange(1, 100) <= Config.카운터_배리어_발동_확률) {
						isProbability = true;
					}
					boolean isShortDistance = attack.isShortDistance();
					if (isProbability && isShortDistance) {
						isCounterBarrier = true;
					}
				}
				if (!isCounterBarrier) {
					attacker.setPetTarget(this);
					attack.calcDamage();
				}
			}
			if (isCounterBarrier) {
				attack.actionCounterBarrier();
				attack.commitCounterBarrier();
			} else {
				attack.action();
				attack.commit();
			}
		}
	}

	public boolean checkNonPvP(L1PcInstance pc, L1Character target) {
		L1PcInstance targetpc = null;
		if (target instanceof L1PcInstance) {
			targetpc = (L1PcInstance) target;
		} else if (target instanceof PetInstance) {
			targetpc = (L1PcInstance) ((PetInstance) target).getMaster();
		} else if (target instanceof SummonInstance) {
			targetpc = (L1PcInstance) ((SummonInstance) target).getMaster();
		}
		if (targetpc == null) {
			return false; // 상대가 PC, 사몬, 애완동물 이외
		}
		if (!Config.ALT_NONPVP) { // Non-PvP 설정
			if (getMap().isCombatZone(getLocation())) {
				return false;
			}

			L1Clan clan = pc.getClan();
			L1Clan enemyclan = targetpc.getClan();
			if (clan != null && enemyclan != null) {
				War war = clan.getCurrentWar();
				War enemyWar = enemyclan.getCurrentWar();
				if (war != null && enemyWar != null && war.equals(enemyWar))
					return false;
			}

			if (target instanceof L1PcInstance) {
				L1PcInstance targetPc = (L1PcInstance) target;
				if (isInWarAreaAndWarTime(pc, targetPc)) {
					return false;
				}
			}
			return true;
		} else {
			return false;
		}
	}

	private boolean isInWarAreaAndWarTime(L1PcInstance pc, L1PcInstance target) {
		int castleId = L1CastleLocation.getCastleIdByArea(pc);
		int targetCastleId = L1CastleLocation.getCastleIdByArea(target);
		if (castleId != 0 && targetCastleId != 0 && castleId == targetCastleId) {
			if (CastleWarBusiness.getInstance().isNowWar(castleId)) {
				return true;
			}
		}
		return false;
	}

	public void setPetTarget(L1Character target) {
		Object[] petList = getPetList().values().toArray();
		for (Object pet : petList) {
			if (pet instanceof PetInstance) {
				PetInstance pets = (PetInstance) pet;
				pets.setMasterTarget(target);
			} else if (pet instanceof SummonInstance) {
				SummonInstance summon = (SummonInstance) pet;
				summon.setMasterTarget(target);
			}
		}
	}

	public void delInvis() {
		// 마법 접속 시간내는 이쪽을 이용
		if (hasSkillEffect(SkillId.인비지블리티) && !isGm()) {
			killSkillEffectTimer(SkillId.인비지블리티);
			sendPackets(new S_Invis(getId(), 0));
			L1World.getInstance().broadcastPacketToAll(new S_Invis(getId(), 0));
		}
		if (hasSkillEffect(SkillId.블라인드_하이딩)) {
			killSkillEffectTimer(SkillId.블라인드_하이딩);
			sendPackets(new S_Invis(getId(), 0));
			L1World.getInstance().broadcastPacketToAll(new S_Invis(getId(), 0));
		}
	}
	public boolean isInWarArea() {
		boolean isNowWar = false;
		int castleId = L1CastleLocation.getCastleIdByArea(this);

		if (castleId != 0) {
			isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
		}
		return isNowWar;
	}

	public void receiveManaDamage(L1Character attacker, int mpDamage) { // 공격으로 MP를 줄일 때는 여기를 사용
		if (mpDamage > 0 && !isDead()) {
			delInvis();
			L1PinkName.onAction(this, attacker);

			int newMp = getCurrentMp() - mpDamage;
			if (newMp > getMaxMp()) {
				newMp = getMaxMp();
			}
			if (newMp <= 0) {
				newMp = 0;
			}
			setCurrentMp(newMp);
		}
	}

	public synchronized void receiveDamage(L1Character attacker, int damage) {

		if (getCurrentHp() > 0 && !isDead()) {
			if (attacker != null && attacker != this && !knownsObject(attacker)
					&& attacker.getMapId() == this.getMapId()) {
				attacker.onPerceive(this);
			}
			if (damage > 0) {	
				//	delInvis();
				L1PinkName.onAction(this, attacker);

				if(hasSkillEffect(SkillId.포그_오브_슬리핑)) {
					removeSkillEffect(SkillId.포그_오브_슬리핑);
				}
			}
			int newHp = getCurrentHp() - damage;
			if (newHp > getMaxHp()) {
				newHp = getMaxHp();
			}
			if (newHp <= 0) {
				if (isGm()) {
					setCurrentHp(getMaxHp());
				} else {
					death(attacker); 
				}
			}
			if (newHp > 0) {
				setCurrentHp(newHp);
			} else if (isGm()) {
				setCurrentHp(getMaxHp());
			}
		} else if (!isDead()) { // 만약을 위해
			if (isGm()) {
				setCurrentHp(getMaxHp());
			} else {
				System.out.println("경고：플레이어의 HP감소 처리가 올바르게 행해지지 않은 개소가 있습니다.※혹은 최초부터 HP0");
				death(attacker);
			}
		}
	}

	public void death(L1Character lastAttacker) {
		synchronized (this) {
			if (isDead()) {
				return;
			}
			setDead(true);
			
			if (lastAttacker instanceof L1PcInstance) {
				L1PcInstance _atker = (L1PcInstance) lastAttacker;
				String locName = MapsTable.getInstance().getMapName(getMapId());
				int castleid = L1CastleLocation.getCastleIdByArea(this);
				if (castleid == 1) {
					locName = "켄트성";
				} else if (castleid == 2) {
					locName = "오크요새";
				} else if ((getX() >= 33450 && getX() <= 33550) && (getY() >= 33150 && getY() <= 33250)
						&& getMapId() == 4) {
					locName = "악어섬";
				} else if ((getX() >= 34170 && getX() <= 34306) && (getY() >= 33311 && getY() <= 33411)
						&& getMapId() == 4) {
					locName = "에이션트 자이언트 인근";
				} else if ((getX() >= 33680 && getX() <= 33780) && (getY() >= 32215 && getY() <= 32315)
						&& getMapId() == 4) {
					locName = "화염의 지배자 인근";
				} else if ((getX() >= 34214 && getX() <= 34314) && (getY() >= 32745 && getY() <= 32845)
						&& getMapId() == 4) {
					locName = "마이노 샤먼 인근";
				} else if ((getX() >= 33713 && getX() <= 33813) && (getY() >= 33306 && getY() <= 33406)
						&& getMapId() == 4) {
					locName = "도펠겡어 보스 인근";
				} else if ((getX() >= 33791 && getX() <= 32870) && (getY() >= 32891 && getY() <= 32970)
						&& getMapId() == 4) {
					locName = "아르피어 인근";
				} else if ((getX() >= 32655 && getX() <= 32755) && (getY() >= 32191 && getY() <= 32291)
						&& getMapId() == 4) {
					locName = "네크로스 인근";
				} else if ((getX() >= 32838 && getX() <= 32938) && (getY() >= 32377 && getY() <= 32477)
						&& getMapId() == 4) {
					locName = "리칸트 인근";
				}
				if (getZoneType() == 0) {	

					if(!GMCommands.IS_PROTECTION) {

						if (this.getHuntCount() >= 1) {
							try {
								int price = getHuntPrice() / 2;
								_atker.getInventory().storeItem(40308, price);
								_atker.sendPackets(new S_SystemMessage("수배 캐릭터 현상금 ["+ price +"] 아데나 획득 하였습니다."));
								this.killSkillEffectTimer(SkillId.수배_버프_아이콘);
								this.initBeWanted();
								this.setHuntCount(0);
								this.setHuntPrice(0);
								this.save();
							} catch (Exception e) { }
						}
						_atker.setKills(_atker.getKills() + 1); // 공격자 킬 
						this.setDeaths(this.getDeaths() + 1);   // 피격자 데스		
						if(Config.킬_데스_멘트) {

							L1World.getInstance().broadcastPacketToAll(new S_SystemMessage("\\fW" + lastAttacker.getName()+"의 공격으로 "+ this.getName()+"(이)가 사망하였습니다."));
							L1World.getInstance().broadcastPacketToAll(new S_SystemMessage("\\fY사망한 지역 :  [" + locName + "]"));

						}
					}
					
				} 
			}
		}
		GeneralThreadPool.getInstance().execute(new Death(lastAttacker));
	}

	private class Death implements Runnable {
		L1Character _lastAttacker;
		boolean isExpDrop;
		boolean isItemDrop;

		Death(L1Character cha) {
			_lastAttacker = cha;
			isExpDrop = true;
			isItemDrop = true;
		}

		public void run() {
			L1Character lastAttacker = _lastAttacker;
			_lastAttacker = null;
			setCurrentHp(0);
			setGresValid(false); // EXP 로스트 할 때까지 G-RES 무효
			stopHpMpRegeneration();

			getMap().setPassable(getLocation(), true);

			// 변신 상태도 해제되기 때문
			int tempchargfx = 0;
			if (hasSkillEffect(SkillId.셰이프_체인지)) {
				tempchargfx = getCurrentSpriteId();
				if(tempchargfx == 0 && getClassId() != 0) {
					setTempCharGfxAtDead(getClassId());
				} else {
					setTempCharGfxAtDead(tempchargfx);
				}			
			} else {
				setTempCharGfxAtDead(getClassId());
			}
			// 스킬 상태의 해제 
			for (int skillNum = SkillId.스킬_시작; skillNum <= SkillId.스킬_끝; skillNum++) {
				removeSkillEffect(skillNum);
			}
			curePoison();
			cureParalaysis();
			for (int skillNum = SkillId.상태_시작; skillNum <= SkillId.상태_끝; skillNum++) {
				if (skillNum == SkillId.상태_채팅_금지) { // 채팅 금지는 해제하지 않는다
					continue;
				}
				removeSkillEffect(skillNum);
			}
			
			int cooking_id = getCookingId();
			int dessert_id = getDessertId();
			if (dessert_id != 0) {
				removeSkillEffect(dessert_id);
			}
			if (cooking_id != 0) {
				removeSkillEffect(cooking_id);
			}
		      
			// 그림자계 변신중에 사망하면(자) 클라이언트가 떨어지기 (위해)때문에 잠정 대응
			if (tempchargfx == 5727 || tempchargfx == 5730 || tempchargfx == 5733 || tempchargfx == 5736) {
				tempchargfx = 0;
			}
			if (tempchargfx != 0) {
				S_ChangeShape cs = new S_ChangeShape(getId(), tempchargfx);
				sendPackets(cs);
				Broadcaster.broadcastPacket(L1PcInstance.this, cs, true);
			}
			
			S_DoActionGFX s = S_DoActionGFX.get(getId(), ActionCodes.ACTION_Die);
			sendPackets(s);
			broadcastPacket(s);
			


			if (lastAttacker != L1PcInstance.this) {
				// 세이프티 존, 컴배트 존에서 마지막에 죽인 캐릭터가
				// 플레이어 or펫이라면, 패널티 없음
				if (getZoneType() != 0) {
					L1PcInstance player = null;
					if (lastAttacker instanceof L1PcInstance) {
						player = (L1PcInstance) lastAttacker;
					} else if (lastAttacker instanceof PetInstance) {
						player = (L1PcInstance) ((PetInstance) lastAttacker).getMaster();
					} else if (lastAttacker instanceof SummonInstance) {
						player = (L1PcInstance) ((SummonInstance) lastAttacker).getMaster();
					}
					if (player != null) {
						// 전쟁중에 전쟁 에리어에 있는 경우는 예외
						if (!isInWarAreaAndWarTime(L1PcInstance.this, player)) {
							return;
						}
					}
				}

				boolean sim_ret = simWarResult(lastAttacker); // 모의전
				if (sim_ret) { // 모의 전시중이라면 패널티 없음
					return;
				}
			}
			
			
			// 세이프 존 에선 패널티 없음
			if (getZoneType() == 1) {
				sendPackets(new S_ServerMessage(3805));
				return;
			}
			if (!getMap().isEnabledDeathPenalty()) {
				return;
			}
			
			if (GMCommands.IS_PROTECTION) { 
				isExpDrop = false;
				isItemDrop = false;
			}

			List<ItemInstance> items = getInventory().getItems();
			L1PenaltyItem pli = null;
			for (ItemInstance tem : items) {
				int inven_count = 0;
				pli = PenaltyItemTable.getInstance().getPenaltyItem(tem.getItemId());
				if (pli != null) {
					if ((pli.getItemCheckType() == 0) && (tem.isEquipped())) {
						int chance = CommonUtil.randomRange(0, 99);
						if (pli.isEquipDelete()) {
							getInventory().setEquipped(tem, false);
							getInventory().removeItem(tem);
						}
						if (pli.isItemPenalty()) {
							if (lastAttacker instanceof L1PcInstance) {
								if (chance < pli.getPcPenaltyChance()) {
									isItemDrop = false;
								}
							} else {
								if (chance < pli.getNpcPenaltyChance()) {
									isItemDrop = false;
								}
							}
						}

						if (pli.isExpPenalty()) {
							if (lastAttacker instanceof L1PcInstance) {
								if (chance < pli.getPcPenaltyChance()) {
									isExpDrop = false;
								}
							} else {
								if (chance < pli.getNpcPenaltyChance()) {
									isExpDrop = false;
								}
							}
						}

						if (pli.getDropItemId() != 0) {
							ItemInstance drop = ItemTable.getInstance().createItem(pli.getDropItemId());

							if (drop != null) {
								drop.setCount(pli.getDropItemCount());
								L1World.getInstance().getInventory(getX(), getY(), getMapId()).storeItem(drop);
							}
						}
						inven_count++;
					}

					if (pli.getItemCheckType() == 1) {
						int chance = CommonUtil.randomRange(0, 99);
						if (pli.isEquipDelete()) {
							if (tem.isEquipped()) {
								getInventory().setEquipped(tem, false);
							}
							if (tem.isStackable()) {
								getInventory().removeItem(tem, pli.getDropItemCount());
							} else {
								getInventory().removeItem(tem);
							}
						}
						if (pli.isItemPenalty()) {
							if (lastAttacker instanceof L1PcInstance) {
								if (chance < pli.getPcPenaltyChance()) {
									isItemDrop = false;
								}
							} else {
								if (chance < pli.getNpcPenaltyChance()) {
									isItemDrop = false;
								}
							}
						}

						if (pli.isExpPenalty()) {
							if (lastAttacker instanceof L1PcInstance) {
								if (chance < pli.getPcPenaltyChance()) {
									isExpDrop = false;
								}
							} else {
								if (chance < pli.getNpcPenaltyChance()) {
									isExpDrop = false;
								}
							}
						}

						if (pli.getDropItemId() != 0) {
							ItemInstance drop = ItemTable.getInstance().createItem(pli.getDropItemId());

							if (drop != null) {
								drop.setCount(pli.getDropItemCount());
								L1World.getInstance().getInventory(getX(), getY(), getMapId()).storeItem(drop);
							}
						}
						inven_count++;
					}
				}
				if (inven_count >= 1) {
					break;
				}
			}

			if(isExpDrop) {
				deathPenalty(); // EXP 로스트 
				
				if (getExpRes() == 0) {
					setExpRes(1);
				}
			}
			setGresValid(true); // EXP 로스트 하면(자) G-RES 유효

			if (get_PKcount() > 0) {
				set_PKcount(get_PKcount() - 1);
			}
			setLastPk(null);

			/** 사망시 템&마법 드랍확률. 바포시스템화. */
			if (getLawful() < 32767) {
				int lostRate = (int) (((getLawful() - 32768D) / 100D - 65D) * 5D);
				if (lostRate < 0) {
					lostRate *= -1;
					if (getLawful() < 32767) {
						lostRate *= 6;
					}
					Random random = new Random();
					int rnd = random.nextInt(1000) + 1;
					if (rnd <= lostRate) {
						int count = 1; 
						if (getLawful() <= -30000) {
							count = random.nextInt(4) + 1; 
						} else if (getLawful() <= -20000) {
							count = random.nextInt(3) + 1; 
						} else if (getLawful() <= -10000) {
							count = random.nextInt(2) + 1; 
						} else if (getLawful() <= 0) {
							count = random.nextInt(1) + 1; 
						} else if (getLawful() <= 30000) {
							count = random.nextInt(1) + 1; // skillcount = 0;
						} else if (getLawful() <= 32766) {
							count = random.nextInt(1); // skillcount = 0; //
						}

						if (isItemDrop) {
							caoPenaltyResult(count);
						}
					}
				}
			}

			boolean castle_ret = castleWarResult(); // 공성전
			if (castle_ret == true) { // 공성 전시중에 기내라면 빨강 네임 패널티 없음
				isExpDrop = false;
				isItemDrop = false;
			}
			L1PcInstance player = null;
			if (lastAttacker instanceof L1PcInstance) {
				player = (L1PcInstance) lastAttacker;
				if (player != null) {
					if (getLawful() >= 0 && isPinkName() == false) {

						if (player.getLawful() < 30000) {
							player.set_PKcount(player.get_PKcount() + 1);
							player.setLastPk();
						}

						int lawful_diff = 3000;

						if (player.getLevel() > getLevel()) {
							lawful_diff += (player.getLevel() - getLevel()) * 1000;

							if (lawful_diff > 10000) {
								lawful_diff = 10000; 
							}
						}

						int lawful = player.getLawful() - lawful_diff;

						if (lawful <= -32768) {
							lawful = -32768;
						}
						player.setLawful(lawful);

						S_Lawful s_lawful = new S_Lawful(player.getId(), player.getLawful());
						player.sendPackets(s_lawful);
						player.broadcastPacket(s_lawful);

					} else {
						setPinkName(false);
					}
				}
			}
		}
		
	}
	///////////////////// 카오 패널티 관련 ///////////////////////////////////
	private void caoPenaltyResult(int count) {
		/** 공성존에서 드랍안되도록 **/
		int castle_id = L1CastleLocation.getCastleIdByArea(this);
		if (castle_id != 0) {
			return;
		}
		if (getAccessLevel() == Config.GMCODE) {
			return;
		}
		if(getInventory() == null) {
			return;
		}
		
		if(getInventory().getSize() <= 0) {
			return;
		}
		
		for (int i = 0; i < count; i++) {
			ItemInstance item = getInventory().CaoPenalty();
			
			if(item == null) {
				return;
			}
			
			

			if (item.getBless() > 3) {
				getInventory().removeItem(item, item.isStackable() ? item.getCount() : 1);
				SJManager.getInstance().PenaltyAppend(item.getLogName(), getName(), count, 1);
			} else {
				getInventory().tradeItem(item, item.isStackable() ? item.getCount() : 1,L1World.getInstance().getInventory(getX(), getY(), getMapId()));
				sendPackets(new S_ServerMessage(638, item.getLogName()));
				SJManager.getInstance().PenaltyAppend(item.getLogName(), getName(), count, 0);
			}
			
		}
	}

	public boolean castleWarResult() {
		L1Clan clan = getClan();
		if (clan != null && isCrown() && getId() == clan.getLeaderId()) {
			War war = clan.getCurrentWar();
			if (war != null) {
				WAR_TYPE type = war.getWarType();
				if (type.equals(WAR_TYPE.CASTLE) && war.getOffenseClan(clan.getClanId()) != null) {
					L1Clan defense = war.getDefenseClan();
					clan.outOfWarArea(defense.getCastleId());
					war.notifyEndWar(defense, clan); // 종결
				}
			}
		}

		int castleId = 0;
		boolean isNowWar = false;
		castleId = L1CastleLocation.getCastleIdByArea(this);
		if (castleId != 0) {
			isNowWar = CastleWarBusiness.getInstance().isNowWar(castleId);
		}
		return isNowWar;
	}

	public boolean simWarResult(L1Character lastAttacker) {
		if (getClanid() == 0) { // 크란 소속하지 않았다
			return false;
		}
		if (Config.SIM_WAR_PENALTY) { // 모의전 패널티 있는 경우는 false
			return false;
		}
		L1PcInstance attacker = null;

		if (lastAttacker instanceof L1PcInstance) {
			attacker = (L1PcInstance) lastAttacker;
		} else if (lastAttacker instanceof PetInstance) {
			attacker = (L1PcInstance) ((PetInstance) lastAttacker).getMaster();
		} else if (lastAttacker instanceof SummonInstance) {
			attacker = (L1PcInstance) ((SummonInstance) lastAttacker).getMaster();
		} else {
			return false;
		}
		L1Clan clan = getClan();
		L1Clan enemy = attacker.getClan();
		if (clan == null || enemy == null || clan.getLeaderId() != getId()) {
			return false;
		}
		War war = clan.getCurrentWar();
		War enemyWar = enemy.getCurrentWar();
		if (war == null || enemyWar == null || !war.equals(enemyWar)) {
			return false;
		}
		WAR_TYPE type = war.getWarType();
		if (!type.equals(WAR_TYPE.NORMAL)) {
			return false;
		}
		war.notifyWinner(enemy, clan);
		war.notifyEndWar(clan, enemy);
		war.dispose();
		return false;
	}

	public void resExp() {
		int oldLevel = getLevel();
		int needExp = ExpTable.getNeedExpNextLevel(oldLevel);
		int exp = 0;
		double ratio;

		if (oldLevel < 45) {
			ratio = 0.05;
		} else if (oldLevel >= 49) {
			ratio = 0.025;
		} else {
			ratio = 0.05 - (oldLevel - 44) * 0.005;
		}
		exp = (int) (needExp * ratio);

		if (exp == 0) {
			return;
		}
		addExp(exp);
	}

	public void deathPenalty() {
		int oldLevel = getLevel();
		int needExp = ExpTable.getNeedExpNextLevel(oldLevel);
		int exp = 0;
		if (oldLevel <= 30) {
			return;
		}
		if (oldLevel >= Config.LIMITLEVEL) {   
			return;
		}
		if (oldLevel >= 1 && oldLevel < 11) {
			exp = 0;
		} else if (oldLevel >= 11 && oldLevel < 45) {
			exp = (int) (needExp * 0.1);
		} else if (oldLevel == 45) {
			exp = (int) (needExp * 0.09);
		} else if (oldLevel == 46) {
			exp = (int) (needExp * 0.08);
		} else if (oldLevel == 47) {
			exp = (int) (needExp * 0.07);
		} else if (oldLevel == 48) {
			exp = (int) (needExp * 0.06);
		} else if (oldLevel >= 49) {
			exp = (int) (needExp * 0.05);
		}
		if (exp == 0) {
			return;
		}
		addExp(-exp);

		SJManager.getInstance().PenaltyAppend(null, getName(), exp, 2);
	}

	public L1BookMark getBookMark(String name) {
		for (int i = 0; i < _bookmarks.size(); i++) {
			L1BookMark element = _bookmarks.get(i);
			if (element.getName().equalsIgnoreCase(name)) {
				return element;
			}
		}
		return null;
	}
	public L1BookMark getBookMark(int id) {
		for (int i = 0; i < _bookmarks.size(); i++) {
			L1BookMark element = _bookmarks.get(i);
			if (element.getId() == id) {
				return element;
			}
		}
		return null;
	}
	public L1BookMark getBookMark(int x, int y, int mapid) {
		L1BookMark element = null;
		for (int i = 0; i < _bookmarks.size(); i++) {
			element = _bookmarks.get(i);
			if (element.getLocX() == x && element.getLocY() == y && element.getMapId() == mapid) {
				return element;
			}
		}
		return null;
	}
	public int getBookMarkSize() {
		return _bookmarks.size();
	}
	public void addBookMark(L1BookMark book) {
		_bookmarks.add(book);
	}
	public void removeBookMark(L1BookMark book) {
		_bookmarks.remove(book);
	}
	public ItemInstance getWeapon() {
		return _weapon;
	}
	public void setWeapon(ItemInstance weapon) {
		_weapon = weapon;
	}
	public L1Quest getQuest() {
		return _quest;
	}
	public String getClassName() {
		if (isCrown()) {
			return "군주";
		} else if (isKnight()) {
			return "기사";
		} else if (isElf()) {
			return "요정";
		} else if (isWizard()) {
			return "마법사";
		} else if (isDarkelf()) {
			return "다엘";
		}
		return "직업명";
	}
	private static Logger _log = Logger.getLogger(L1PcInstance.class.getName());

	private int _classId;
	private int _exp;
	private final L1Karma _karma = new L1Karma();
	private boolean _gm; 

	private boolean _monitor;
	private boolean _gmInvis;
	private short _accessLevel;
	private int _currentWeapon;
	private final L1PcInventory _inventory;
	private final L1Inventory _tradewindow;
	private ItemInstance _weapon;

	private boolean _isDrink = false;
	private boolean _isGres = false;
	private boolean _isPinkName = false;
	private final ArrayList<L1BookMark> _bookmarks;
	private L1Quest _quest; 

	private EquipmentSlot _equipSlot;
	private static L1PcInstance _firewallUser; // ● 파이어월 시전자

	public static L1PcInstance getFirewallUser() {
		return _firewallUser;
	}
	public static void setFirewallUser(L1PcInstance firewallUser) {
		_firewallUser = firewallUser;
	}
	private String _accountName; // ● 어카운트 네임

	public String getAccountName() {
		return _accountName;
	}
	public void setAccountName(String s) {
		_accountName = s;
	}
	private short _baseMaxHp = 0; // ● MAXHP 베이스(1~32767)
	public short getBaseMaxHp() {
		return _baseMaxHp;
	}
	public void addBaseMaxHp(short i) {
		i += _baseMaxHp;
		if (i >= 32767) {
			i = 32767;
		} else if (i < 1) {
			i = 1;
		}
		addMaxHp(i - _baseMaxHp);
		_baseMaxHp = i;
	}

	private short _baseMaxMp = 0; // ● MAXMP 베이스(0~32767)
	public short getBaseMaxMp() {
		return _baseMaxMp;
	}
	public void addBaseMaxMp(short i) {
		i += _baseMaxMp;
		if (i >= 32767) {
			i = 32767;
		} else if (i < 0) {
			i = 0;
		}
		addMaxMp(i - _baseMaxMp);
		_baseMaxMp = i;
	}

	private int _baseAc = 0; // ● AC베이스(-128~127)
	public int getBaseAc() {
		return _baseAc;
	}
	private int _baseDmgup = 0; // ● 데미지 보정 베이스(-128~127)
	public int getBaseDmgup() {
		return _baseDmgup;
	}
	private int _baseHitup = 0; // ● 명중 보정 베이스(-128~127)
	public int getBaseHitup() {
		return _baseHitup;
	}
	private int _baseBowHitup = 0; // ● 활명중 보정 베이스(-128~127)

	public int getBaseBowHitup() {
		return _baseBowHitup;
	}
	private int _baseBowDmgup = 0;
	public int getBaseBowDmgup() {
		return _baseBowDmgup;
	}
	private int _baseMagicHit = 0;
	public int getBaseMagicHit() {
		return _baseMagicHit;
	}
	private int _baseMagicCri = 0;
	public int getMagicCritical() {
		return _baseMagicCri;
	} 
	public void addMagicCritical(int i) {
		_baseMagicCri += i;
	} 	
	private int _MeleeCritical = 0;
	public int getMeleeCritical() {
		return _MeleeCritical;
	} 
	public void addMeleeCritical(int i) {
		_MeleeCritical += i;
	} 
	private int _RangdeCritical = 0;
	public int getRangedCritical() {
		return _RangdeCritical;
	} 
	public void addRangedCritical(int i) {
		_RangdeCritical += i;
	}
	private int _meleeLevelHit = 0;
	public int getMeleeLevelHit() {
		return _meleeLevelHit;
	}
	public void addMeleeLevelHit(int i) {
		_meleeLevelHit += i;
	}
	private int _RangedLevelHit = 0;
	public int getRangedLevelHit() {
		return _RangedLevelHit;
	}
	public void addRangedLevelHit(int i) {
		_RangedLevelHit += i;
	}
	private byte _attrFireDmg = 0;
	public byte getAttrFireDmg() {
		return _attrFireDmg;
	} 
	public void addAttrFireDmg(int i) {
		_attrFireDmg += i;
	} 
	private byte _attrWaterDmg = 0;
	public byte getAttrWaterDmg() {
		return _attrWaterDmg;
	} 
	public void addAttrWaterDmg(int i) {
		_attrWaterDmg += i;
	}
	private byte _attrWindDmg = 0;
	public byte getAttrWindDmg() {
		return _attrWindDmg;
	} 
	public void addAttrWindDmg(int i) {
		_attrWindDmg += i;
	}
	private byte _attrEarthDmg = 0;
	public byte getAttrEarthDmg() {
		return _attrEarthDmg;
	} 
	public void addAttrEarthDmg(int i) {
		_attrWaterDmg += i;
	}

	private int _baseMr = 0;
	public int getBaseMr() {
		return _baseMr;
	}
	private int _advenHp;
	public int getAdvenHp() {
		return _advenHp;
	}
	public void setAdvenHp(int i) {
		_advenHp = i;
	}
	private int _advenMp;
	public int getAdvenMp() {
		return _advenMp;
	}
	public void setAdvenMp(int i) {
		_advenMp = i;
	}
	private int _highLevel;
	public int getHighLevel() {
		return _highLevel;
	}
	public void setHighLevel(int i) {
		_highLevel = i;
	}
	private int _bonusStats;
	public int getBonusStats() {
		return _bonusStats;
	}
	public void setBonusStats(int i) {
		_bonusStats = i;
	}
	private int _elixirStats;
	public int getElixirStats() {
		return _elixirStats;
	}

	public void setElixirStats(int i) {
		_elixirStats = i;
	}
	private int _elfAttr;

	public int getElfAttr() {
		return _elfAttr;
	}

	public void setElfAttr(int i) {
		_elfAttr = i;
	}

	private int _expRes; // ● EXP 복구

	public int getExpRes() {
		return _expRes;
	}

	public void setExpRes(int i) {
		_expRes = i;
	}

	private int _partnerId; // ● 결혼상대

	public int getPartnerId() {
		return _partnerId;
	}

	public void setPartnerId(int i) {
		_partnerId = i;
	}

	private int _onlineStatus; // ● 온라인 상태

	public int getOnlineStatus() {
		return _onlineStatus;
	}

	public void setOnlineStatus(int i) {
		_onlineStatus = i;
	}

	private int _homeTownId; // ● 홈 타운

	public int getHomeTownId() {
		return _homeTownId;
	}

	public void setHomeTownId(int i) {
		_homeTownId = i;
	}

	private int _contribution; // ● 공헌도

	public int getContribution() {
		return _contribution;
	}

	public void setContribution(int i) {
		_contribution = i;
	}

	// 지옥에 체재하는 시간(초)
	private int _hellTime;

	public int getHellTime() {
		return _hellTime;
	}

	public void setHellTime(int i) {
		_hellTime = i;
	}

	private boolean _banned; // ● 동결

	public boolean isBanned() {
		return _banned;
	}

	public void setBanned(boolean flag) {
		_banned = flag;
	}

	private int _food; // ● 만복도

	public int getFood() {
		return _food;
	}

	public void setFood(int i) {
		_food = i;
	}
	public void addFood(int i) {
		_food += i;
		if (_food > 225) {
			_food = 225; 
		} else if (_food < 1) {
			_food = 1;
		}
	}

	public EquipmentSlot getEquipSlot() {
		return _equipSlot;
	}

	public static L1PcInstance load(String charName) {
		L1PcInstance result = null;
		try {
			result = CharacterTable.getInstance().loadCharacter(charName);
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		return result;
	}

	/**
	 * 이 플레이어 상태를 스토리지에 기입한다.
	 * @throws Exception
	 */
	public void save() throws Exception {
		if (isGhost()) {
			return;
		}
		if (noPlayerck2) {
			return;
		}
		CharacterTable.getInstance().storeCharacter(this);
	}
	/**
	 * 이 플레이어의 목록 아이템 상태를 스토리지에 기입한다.
	 */
	public void saveInventory() {
		if (noPlayerck2) {
			return;
		}
		for (ItemInstance item : getInventory().getItems()) {
			if (item != null) {
				getInventory().saveItem(item, item.getRecordingColumns() != 0 ? L1PcInventory.COL_SAVE_ALL : 0);
			}
		}
	}

	public int getMaxWeight() {
		int str = getAbility().getTotalStr();
		int con = getAbility().getTotalCon();
		int maxWeight = CalcUtil.getMaxWeight(str, con);
		int weightReductionByArmor = getWeightReduction(); 
		int dollWeight = 0;
		if (getMagicDoll() != null) {
			dollWeight += getMagicDoll().getDoll().getEffect().getWeightReduction();
		} 
		int weightReductionByMagic = 0;
		if (hasSkillEffect(SkillId.디크리즈_웨이트) 
				|| hasSkillEffect(SkillId.안타라스_레이드_버프_크레이)) {
			weightReductionByMagic += 10; 
		}
		int weightReduction = weightReductionByArmor + dollWeight + weightReductionByMagic;
		maxWeight += ((maxWeight / 100) * weightReduction);
		maxWeight *= Config.RATE_WEIGHT_LIMIT;
		return maxWeight;
	}

	private int invisDelayCounter = 0;

	public boolean isInvisDelay() {
		return (invisDelayCounter > 0);
	}
	private Object _invisTimerMonitor = new Object();
	public void addInvisDelayCounter(int counter) {
		synchronized (_invisTimerMonitor) {
			invisDelayCounter += counter;
		}
	}
	private static final long DELAY_INVIS = 3000L;

	public void beginInvisTimer() {
		addInvisDelayCounter(1);
		GeneralThreadPool.getInstance().pcSchedule(new L1PcInvisDelay(getId()), DELAY_INVIS);
	}

	public synchronized void addExp(int exp) {
		if (Config.STANDBY_SERVER) {
			exp = 0;
			sendPackets(new S_SystemMessage("오픈대기중에는 경험치를 획득할 수 없습니다."));
		}
		_exp += exp;
		if (_exp > ExpTable.MAX_EXP) {
			_exp = ExpTable.MAX_EXP;
		}
		if (_exp >= ExpTable.getExpByLevel(Config.LIMITLEVEL + 1)) {
			_exp = (ExpTable.getExpByLevel(Config.LIMITLEVEL + 1) - 1);
		}
	}

	public synchronized void addExp(int exp, boolean flag) {
		_exp += exp;

		if (_exp > ExpTable.MAX_EXP) {
			_exp = ExpTable.MAX_EXP;
		}
		if (this._exp >= ExpTable.getExpByLevel(Config.LIMITLEVEL + 1)) {
			this._exp = (ExpTable.getExpByLevel(Config.LIMITLEVEL + 1) - 1);
		}
	}

	public synchronized void addContribution(int contribution) {
		_contribution += contribution;
	}

	public void beginExpMonitor() {
		_expMonitorFuture = GeneralThreadPool.getInstance().pcScheduleAtFixedRate(new L1PcExpMonitor(getId()), 0L, INTERVAL_EXP_MONITOR);
	}

	private void levelUp(int gap) {
		resetLevel();

		for (int i = 0; i < gap; i++) {
			short randomHp = CalcUtil.calcStatHp(getType(), 0, getAbility().getCon());
			short randomMp = CalcUtil.calcStatMp(getType(), 0, getAbility().getWis());
			addBaseMaxHp(randomHp);
			addBaseMaxMp(randomMp);
		}
		setCurrentHp(getBaseMaxHp()); 
		setCurrentMp(getBaseMaxMp()); 
		resetBaseHit();
		resetBaseDmg();
		resetBaseMagicCritical();
		resetBaseMagicHit();
		resetBaseAc();
		resetBaseMr();
		if (getLevel() > getHighLevel()) {
			setHighLevel(getLevel());
		}
		LevelUpPresentSystem(this);
		try {
			// DB에 캐릭터 정보를 기입한다
			save();
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		
		
		L1Quest quest = getQuest();
		ItemInstance item = null;
		ItemInstance item1 = null;
		int lv15_step = quest.get_step(L1Quest.QUEST_LEVEL15);
		if (getLevel() >= 15 && lv15_step != L1Quest.QUEST_END) {
			switch (getType()) { // <--케릭 클래스 구분

			case 0:// 군주라면
				item = getInventory().storeItem(40226, 1);// 트루타겟
				item1 = getInventory().storeItem(20065, 1);
				if ((item != null) && (item1 != null))
					sendPackets(new S_SystemMessage("Level(15)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL15);
				break;
			case 1:
				item = getInventory().storeItem(20027, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(15)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL15);
				break;
			case 2:
				item = getInventory().storeItem(20021, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(15)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL15);
				break;
			case 3:
				item = getInventory().storeItem(20226, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(15)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL15);
				break;

			case 4:
				item = getInventory().storeItem(40598, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(15)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL15);
				break;

			/*
			 * case 5: item =
			 * getInventory().storeItem(L1ItemId.DRAGONKNIGHTTABLET_DRAGONSKIN,
			 * 1); //item1 = getInventory().storeItem(410002, 1); if((item !=
			 * null) && (item1 != null)) sendPackets(new S_SystemMessage(
			 * "Level(15)퀘스트를 완료 하였습니다."));
			 * getQuest().set_end(L1Quest.QUEST_LEVEL15); break;
			 */

			/*
			 * case 6: item =
			 * getInventory().storeItem(L1ItemId.MEMORIALCRYSTAL_CUBE_IGNITION,
			 * 1); //item1 = getInventory().storeItem(410005, 1); if((item !=
			 * null) && (item1 != null)) sendPackets(new S_SystemMessage(
			 * "Level(15)퀘스트를 완료 하였습니다."));
			 * getQuest().set_end(L1Quest.QUEST_LEVEL15); break;
			 */
			}
		}
		int lv30_step = quest.get_step(L1Quest.QUEST_LEVEL30);
		if (getLevel() >= 30 && lv30_step != L1Quest.QUEST_END) {
			switch (getType()) { // <--케릭 클래스 구분
			case 0:
				item = getInventory().storeItem(40570, 1);

				if (item != null)
					sendPackets(new S_SystemMessage("Level(30)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL30);
				break;

			case 1:
				item = getInventory().storeItem(20230, 1);
				item1 = getInventory().storeItem(30, 1);
				if ((item != null) && (item1 != null))
					sendPackets(new S_SystemMessage("Level(30)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL30);
				break;

			case 2:
				item = getInventory().storeItem(40588, 1);
				item = getInventory().storeItem(40240, 1);// 트리
				if (item != null)
					sendPackets(new S_SystemMessage("Level(30)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL30);
				break;
			case 3:
				item = getInventory().storeItem(115, 1);
				item = getInventory().storeItem(40196, 1);// 서먼
				if (item != null)
					sendPackets(new S_SystemMessage("Level(30)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL30);
				break;

			case 4:
				item = getInventory().storeItem(40545, 1);
				item = getInventory().storeItem(40270, 1);// 무빙
				if (item != null)
					sendPackets(new S_SystemMessage("Level(30)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL30);
				break;

			/*
			 * case 5: item =
			 * getInventory().storeItem(L1ItemId.DRAGONKNIGHTTABLET_BLOODLUST,
			 * 1); //item1 = getInventory().storeItem(420001, 1); if((item !=
			 * null) && (item1 != null)) sendPackets(new S_SystemMessage(
			 * "Level(30)퀘스트를 완료 하였습니다."));
			 * getQuest().set_end(L1Quest.QUEST_LEVEL30); break;
			 */
			// case 6:
			// item =
			// getInventory().storeItem(L1ItemId.MEMORIALCRYSTAL_CUBE_SHOCK, 1);
			// item1 = getInventory().storeItem(420006, 1);
			// if((item != null) && (item1 != null))
			// sendPackets(new S_SystemMessage("Level(30)퀘스트를 완료 하였습니다."));
			// getQuest().set_end(L1Quest.QUEST_LEVEL30);
			// break;
			}
		}
		int lv45_step = quest.get_step(L1Quest.QUEST_LEVEL45);
		if (getLevel() >= 45 && lv45_step != L1Quest.QUEST_END) {
			switch (getType()) { // <--케릭 클래스 구분
			case 0:
				item = getInventory().storeItem(20287, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(45)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL45);
				break;

			case 1:
				item = getInventory().storeItem(20318, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(45)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL45);
				break;

			case 2:
				item = getInventory().storeItem(40546, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(45)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL45);
				break;

			case 3:
				item = getInventory().storeItem(40599, 1);// 법사
				if (item != null)
					sendPackets(new S_SystemMessage("Level(45)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL45);
				break;

			case 4:
				item = getInventory().storeItem(40553, 1);
				if (item != null)
					sendPackets(new S_SystemMessage("Level(45)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL45);
				break;

			/*
			 * case 5: //item = getInventory().storeItem(420004, 1); if(item !=
			 * null) sendPackets(new S_SystemMessage("Level(45)퀘스트를 완료 하였습니다."
			 * )); getQuest().set_end(L1Quest.QUEST_LEVEL45); break;
			 * 
			 * case 6: //item = getInventory().storeItem(420005, 1); if(item !=
			 * null) sendPackets(new S_SystemMessage("Level(45)퀘스트를 완료 하였습니다."
			 * )); getQuest().set_end(L1Quest.QUEST_LEVEL45); break;
			 */
			}
			// item = getInventory().storeItem(45077, 3);
		}
		int lv50_step = quest.get_step(L1Quest.QUEST_LEVEL50);
		if (getLevel() >= 50 && lv50_step != L1Quest.QUEST_END) {
			switch (getType()) { // <--케릭 클래스 구분
			case 0:

				 item = getInventory().storeItem(51, 1);//45레벨에선지급
				if (item != null)
					sendPackets(new S_SystemMessage("Level(50)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL50);
				break;

			case 1:
				 item = getInventory().storeItem(56, 1);//45레벨에선지급
				if (item != null)
					sendPackets(new S_SystemMessage("Level(50)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL50);
				break;

			case 2:
				 item = getInventory().storeItem(184, 1);//45레벨에선지급
				 item1 = getInventory().storeItem(50, 1);//45레벨에선지급
				if ((item != null) && (item1 != null))
					sendPackets(new S_SystemMessage("Level(50)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL50);
				break;

			case 3:
				 item = getInventory().storeItem(20225, 1);//45레벨에선지급
				if (item != null)
					sendPackets(new S_SystemMessage("Level(50)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL50);
				break;

			case 4:
				 item = getInventory().storeItem(13, 1); //45레벨에선지급
				if (item != null)
					sendPackets(new S_SystemMessage("Level(50)퀘스트를 완료 하였습니다."));
				getQuest().set_end(L1Quest.QUEST_LEVEL50);
				break;

			/*
			 * case 5: //item = getInventory().storeItem(410000, 1); if(item !=
			 * null) sendPackets(new S_SystemMessage("Level(50)퀘스트를 완료 하였습니다."
			 * )); getQuest().set_end(L1Quest.QUEST_LEVEL50); break;
			 * 
			 * case 6: // item = getInventory().storeItem(410003, 1); if(item !=
			 * null) sendPackets(new S_SystemMessage("Level(50)퀘스트를 완료 하였습니다."
			 * )); getQuest().set_end(L1Quest.QUEST_LEVEL50); break;
			 */
			}
		}
		if (getLevel() >= 51 && getLevel() - 50 > getBonusStats()) {
			if (getAbility().getAmount() < 150) {
				sendPackets(new S_bonusstats(getId(), 1));
			}
		}
		L1EventNpcSpawn event_map_check = EventNpcSpawnTable.getInstance().getEventMapLevelUpCheck(getMapId());
		if (event_map_check != null && event_map_check.getJoinMaxLevel() != 0
				&& getLevel() > event_map_check.getJoinMaxLevel()) {
			sendPackets((ServerBasePacket) new S_SystemMessage(
					"\\fY[!] : 레벨 " + event_map_check.getJoinMaxLevel() + " 이하만 입장할 수 있습니다."));
			this.teleport(event_map_check.getExitLoc()[0], event_map_check.getExitLoc()[1], (short) event_map_check.getExitLoc()[2], this.getHeading(), 169, false, false);
		}
		
		// 일정 레벨에 도달하면 귀환 처리
		if (getLevel() >= 52) { // 지정 레벨
			if (getMapId() == 777) { // 버림받은 사람들의 땅(그림자의 신전)
				this.teleport(34043, 32184, (short) 4, 5, 169, true, false); 
			} else if (getMapId() == 778 || getMapId() == 779) { // 버림받은 사람들의땅(욕망의 동굴)
				this.teleport(32608, 33178, (short) 4, 5, 169, true, false); 
			}
		}
		sendPackets(new S_OwnCharStatus(this));
		sendPackets(S_SkillSound.get(getId(), 8688));
	}

	private void levelDown(int gap) {
		resetLevel();

		for (int i = 0; i > gap; i--) {
			// 레벨 다운시는 랜덤치를 그대로 마이너스 하기 위해, base치에 0을 설정
			short randomHp = CalcUtil.calcStatHp(getType(), 0, getAbility().getCon());
			short randomMp = CalcUtil.calcStatMp(getType(), 0, getAbility().getWis());
			addBaseMaxHp((short) -randomHp);
			addBaseMaxMp((short) -randomMp);
		}
		resetBaseHit();
		resetBaseDmg();
		resetBaseMagicCritical();
		resetBaseMagicHit();
		resetBaseAc();
		resetBaseMr();
		if (Config.LEVEL_DOWN_RANGE != 0) {
			if (getHighLevel() - getLevel() >= Config.LEVEL_DOWN_RANGE) {
				sendPackets(new S_ServerMessage(64)); // 서버와의 접속이 끊어졌습니다.
				sendPackets(new S_Disconnect());
				_log.info(String.format("레벨 다운의 허용 범위를 넘었기 때문에 %s를 강제 절단 했습니다.", getName()));
			}
		}

		try {
			// DB에 캐릭터 정보를 기입한다
			save();
		} catch (Exception e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		sendPackets(new S_OwnCharStatus(this));
	}

	public void beginGameTimeCarrier() {
		new GameTimeCarrier(this).start();
	}

	private boolean _ghost = false; 

	public boolean isGhost() {
		return _ghost;
	}
	private void setGhost(boolean flag) {
		_ghost = flag;
	}

	private boolean _ghostCanTalk = true; // NPC에 말을 건넬 수 있을까
	public boolean isGhostCanTalk() {
		return _ghostCanTalk;
	}
	private void setGhostCanTalk(boolean flag) {
		_ghostCanTalk = flag;
	}

	public void beginGhost(int locx, int locy, short mapid, boolean canTalk) {
		beginGhost(locx, locy, mapid, canTalk, 0);
	}

	public void beginGhost(int locx, int locy, short mapid, boolean canTalk, int sec) {
		if (isGhost()) {
			return;
		}
		setGhost(true);
		_ghostSaveLocX = getX();
		_ghostSaveLocY = getY();
		_ghostSaveMapId = getMapId();
		_ghostSaveHeading = getHeading();
		setGhostCanTalk(canTalk);
		this.teleport(locx, locy, mapid, getHeading(),169 , true, false);
		if (sec > 0) {
			_ghostFuture = GeneralThreadPool.getInstance().pcSchedule(new L1PcGhostMonitor(getId()), sec * 1000);
		}
	}

	public void endGhost() {
		setGhost(false);
		setGhostCanTalk(true);
		this.teleport(_ghostSaveLocX, _ghostSaveLocY, _ghostSaveMapId, _ghostSaveHeading, 169, true, false);
	}

	private ScheduledFuture<?> _ghostFuture;

	private int _ghostSaveLocX = 0;
	private int _ghostSaveLocY = 0;
	private short _ghostSaveMapId = 0;
	private int _ghostSaveHeading = 0;

	private ScheduledFuture<?> _hellFuture;

	@Override
	public void setPoisonEffect(int effectId) {
		
		S_Poison ss = new S_Poison(getId(), effectId);
		sendPackets(ss);
		if (!isGmInvis() && !isGhost() && !isInvisble()) {
			broadcastPacket(ss);
		}
	}

	@Override
	public void healHp(int pt) {
		super.healHp(pt);
		sendPackets(new S_HPUpdate(this));
	}

	@Override
	public int getKarma() {
		return _karma.get();
	}

	@Override
	public void setKarma(int i) {
		_karma.set(i);
	}

	public void addKarma(int i) {
		synchronized (_karma) {
			_karma.add(i);
		}
	}

	public int getKarmaLevel() {
		return _karma.getLevel();
	}

	public int getKarmaPercent() {
		return _karma.getPercent();
	}

	private Timestamp _lastPk;

	/**
	 * 플레이어의 최종 PK시간을 돌려준다.
	 * 
	 * @return _lastPk
	 * 
	 */
	public Timestamp getLastPk() {
		return _lastPk;
	}

	/**
	 * 플레이어의 최종 PK시간을 설정한다.
	 * 
	 * @param time 최종 PK시간(Timestamp형) 해제하는 경우는 null를 대입
	 */
	public void setLastPk(Timestamp time) {
		_lastPk = time;
	}

	/**
	 * 플레이어의 최종 PK시간을 현재의 시각으로 설정한다.
	 */
	public void setLastPk() {
		_lastPk = new Timestamp(System.currentTimeMillis());
	}
	private Timestamp _lastLoginTime;

	public Timestamp getLastLoginTime() { return _lastLoginTime; }
	public void setLastLoginTime(Timestamp time) {	_lastLoginTime = time;	}

	/**
	 * 플레이어가 준비중일까를 돌려준다.
	 * 
	 * @return 준비중이면, true
	 */
	public boolean isWanted() {
		if (_lastPk == null) {
			return false;
		} else if (System.currentTimeMillis() - _lastPk.getTime() > 24 * 3600 * 1000) {
			setLastPk(null);
			return false;
		}
		return true;
	}

	/**
	 * 캐릭터 레벨에 의한 매직 레벨
	 */
	@Override
	public int getMagicLevel() {
		return getClassFeature().getMagicLevel(getLevel());
	}
	private int _weightReduction = 0;
	public int getWeightReduction() {
		return _weightReduction;
	}
	public void addWeightReduction(int i) {
		_weightReduction += i;
	}
	private int _DmgupPvP = 0;
	private int _DmgDownPvP = 0;
	private int _AdenaBonus = 0;

	public int getDmgupPvP() {
		return _DmgupPvP;
	}
	public void addDmgupPvP(int i) {
		_DmgupPvP += i;
	}
	public int getDmgDownPvP() {
		return _DmgDownPvP;
	}
	public void addDmgDownPvP(int i) {
		_DmgDownPvP += i;
	}
	public int getAdenaBonus() {
		return _AdenaBonus;
	}
	public void addAdenaBonus(int i) {
		_AdenaBonus += i;
	}
	private int _hasteItemEquipped = 0;
	public int getHasteItemEquipped() {
		return _hasteItemEquipped;
	}
	public void addHasteItemEquipped(int i) {
		_hasteItemEquipped += i;
	}

	public void removeHasteSkillEffect() {
		if (hasSkillEffect(SkillId.슬로우)) {
			removeSkillEffect(SkillId.슬로우);
		}
		if (hasSkillEffect(SkillId.매스_슬로우)) {
			removeSkillEffect(SkillId.매스_슬로우);
		}
		if (hasSkillEffect(SkillId.인탱글)) {
			removeSkillEffect(SkillId.인탱글);
		}
		if (hasSkillEffect(SkillId.헤이스트)) {
			removeSkillEffect(SkillId.헤이스트);
		}
		if (hasSkillEffect(SkillId.그레이터_헤이스트)) {
			removeSkillEffect(SkillId.그레이터_헤이스트);
		}
		if (hasSkillEffect(SkillId.상태_1단_가속)) {
			removeSkillEffect(SkillId.상태_1단_가속);
		}
	}
	private int _damageReduction = 0;
	public int getDamageReduction() {
		return _damageReduction;
	}
	public void addDamageReduction(int i) {
		_damageReduction += i;
	}
	// 물약 회복량
	private int _potionRecoveryAmount = 0;
	public int getPotionRecoveryAmount() {
		return _potionRecoveryAmount;
	}
	public void addPotionRecoveryAmount(int i) {
		_potionRecoveryAmount += i;
	}
	// 물약 회복량 보조
	private int _potionRecovery = 0;
	public int getPotionRecovery() {
		return _potionRecovery;
	}
	public void addPotionRecovery(int i) {
		_potionRecovery += i;
	}

	private boolean _gresValid; // G-RES가 유효한가

	private void setGresValid(boolean valid) {
		_gresValid = valid;
	}

	public boolean isGresValid() {
		return _gresValid;
	}

	private long _fishingTime = 0;

	public long getFishingTime() {
		return _fishingTime;
	}

	public void setFishingTime(long i) {
		_fishingTime = i;
	}

	private boolean _isFishing = false;

	public boolean isFishing() {
		return _isFishing;
	}

	public void setFishing(boolean flag) {
		_isFishing = flag;
	}

	public ItemInstance _fishingRod = null;

	public ItemInstance getFishingRod() {
		return _fishingRod;
	}

	public void setFishingRod(ItemInstance item) {
		_fishingRod = item;
	}

	private int _cookingId = 0;

	public int getCookingId() {
		return _cookingId;
	}

	public void setCookingId(int i) {
		_cookingId = i;
	}

	private int _dessertId = 0;

	public int getDessertId() {
		return _dessertId;
	}

	public void setDessertId(int i) {
		_dessertId = i;
	}

	/**
	 * 레벨, 베이스 스텟에 따라 대미지 값을 계산한다. 레벨업, 리스타트 시에 재 호출 된다.
	 * @return resetBaseDmg
	 */
	public void resetBaseDmg() {
		int baseMeleeDmg = CalcUtil.calcBaseMeleeDmg(getType(), getAbility().getBaseStr(), getLevel());
		int	baseRangedDmg = CalcUtil.calcBaseRangedDmg(getType(), getAbility().getBaseDex(), getLevel());
		addMeleeDmg(baseMeleeDmg - _baseDmgup);
		addRangedDmg(baseRangedDmg - _baseBowDmgup);
		_baseDmgup = baseMeleeDmg;
		_baseBowDmgup = baseRangedDmg;
	}
	/**
	 * 베이스 스텟에 따라 마법 명중 값을 계산한다. 레벨업, 리스타트 시에 재 호출 된다.
	 * @return resetBaseMagicHit 
	 */
	public void resetBaseMagicHit() {
		int BaseMagicHit = CalcUtil.getBaseMagicHit(getType(), getAbility().getBaseInt());
		addMagicHit(BaseMagicHit - _baseMagicHit);
		_baseMagicHit = BaseMagicHit;
	}
	/**
	 * 레벨, 베이스 스텟에 따라 마법 크리티컬 값을 계산한다. 레벨업, 리스타트 시에 재 호출 된다.
	 * @return resetBaseMagicCritical
	 */
	public void resetBaseMagicCritical() {
		int BaseMagicCritical = CalcUtil.getBaseMagicCritical(getType(), getAbility().getBaseInt());
		int LevelMagicCritical = 0;
		if(isWizard()) {
			LevelMagicCritical = (int) (Math.floor(getLevel() - 52) / 2);
		} 
		addMagicCritical((LevelMagicCritical + BaseMagicCritical) - _baseMagicCri);
		_baseMagicCri = LevelMagicCritical + BaseMagicCritical;
	}
	/**
	 * 레벨, 베이스 스텟에 따라 명중 값을 계산한다. 레벨업, 리스타트 시에 재 호출 된다.
	 * @return resetBaseHit
	 */
	public void resetBaseHit() {
		int BaseMeleeHit = CalcUtil.calcBaseMeleeHit(getType(), getAbility().getBaseStr(), getLevel());
		int BaseRangedHit = CalcUtil.calcBaseRangedHit(getType(), getAbility().getBaseDex(), getLevel());	
		addMeleeLevelHit(BaseMeleeHit - _baseHitup);
		addRangedLevelHit(BaseRangedHit - _baseBowHitup);
		_baseHitup = BaseMeleeHit;
		_baseBowHitup = BaseRangedHit;		
	}
	/**
	 * 레벨, 베이스 스텟에 따라 추가 방어력 값을 계산한다. 레벨업, 리스타트 시에 재 호출 된다.
	 * @ return resetBaseAc
	 */
	public void resetBaseAc() {
		int CalcAc = CalcUtil.calcAc(getLevel(), getAbility().getDex());
		int BaseAc = CalcUtil.calcBaseAc(getType(), getAbility().getBaseDex());
		this.ac.addAc((CalcAc + BaseAc) - _baseAc);
		_baseAc = CalcAc + BaseAc;
	}
	/**
	 * 레벨, 베이스 스텟에 따라 마법 방어 값을 계산한다. 레벨업, 리스타트 시에 재 호출 된다.
	 * @ return resetBaseAc
	 */
	public void resetBaseMr() {
		int BaseMr = CalcUtil.calcBaseMr(getType(), getAbility().getBaseWis(), getLevel());
		int StatMr = CalcUtil.calcStatMr(getAbility().getTotalWis());	
		resistance.setBaseMr(StatMr + BaseMr);
	}
	/**
	 * EXP로부터 현재의 Lv를 재계산해 설정한다. 초기설정시, 사망시나 LVUP시에 호출한다.
	 */
	public void resetLevel() {
		setLevel(ExpTable.getLevelByExp(_exp));
	}

	public void refresh() {
		resetLevel();
		resetBaseHit();
		resetBaseDmg();
		resetBaseMagicCritical();
		resetBaseMagicHit();
		resetBaseMr();
		resetBaseAc();
	}
	/**
	 * 스킬에 의한 DG 회피율을 계산해 돌려준다 
	 * @return
	 */
	private int _Dg;
	public void addDg(int i) {
		_Dg += i;
		sendPackets(new S_PacketBox(S_PacketBox.UPDATE_DG, _Dg), true);
	}
	public int getDg() {
		return _Dg;

	}
	/**
	 * 스킬, 캐릭터의 베이스 ER을 계산해 돌려준다 
	 * @return
	 */
	public int getEr() {
		int er = CalcUtil.getRangedEvasion(this, getType(), getAbility().getTotalDex(), getLevel());
		int BaseEr = CalcUtil.calcBaseEr(getType(), getAbility().getBaseDex());
		if (hasSkillEffect(SkillId.스트라이커_게일)) {
			return 0;
		}
		return (er + BaseEr);
	}


	private ArrayList<String> _excludeList = new ArrayList<String>();

	public void addExclude(String name) {
		_excludeList.add(name);
	}

	/**
	 * 지정한 이름의 캐릭터를 차단 리스트로부터 삭제한다
	 * @param name 대상의 캐릭터명
	 * @return 실제로 삭제된, 클라이언트의 차단 리스트상의 캐릭터명. 지정한 이름이 리스트에 발견되지 않았던 경우는 null를 돌려준다.
	 */
	public String removeExclude(String name) {
		for (String each : _excludeList) {
			if (each.equalsIgnoreCase(name)) {
				_excludeList.remove(each);
				return each;
			}
		}
		return null;
	}

	/**
	 * 지정한 이름의 캐릭터를 차단하고 있는 경우 true를 돌려준다
	 */
	public boolean excludes(String name) {
		for (String each : _excludeList) {
			if (each.equalsIgnoreCase(name)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 차단 리스트가 상한의 16명에 이르고 있을까를 돌려준다
	 */
	public boolean isExcludeListFull() {
		return (_excludeList.size() >= 16) ? true : false;
	}

	private CharacterActionExecutor _actionExecutor;

	public void registerActionHandler(int idx, AbstractActionHandler handler) {
		if (_actionExecutor == null)
			_actionExecutor = CharacterActionExecutor.execute(this);
		_actionExecutor.register(idx, handler);
	}

	public void unreigsterActionHandler(int idx) {
		if (_actionExecutor != null)
			_actionExecutor.unregister(idx);
	}

	public boolean hasAction(int idx) {
		return _actionExecutor != null ? _actionExecutor.hasAction(idx) : false;
	}
	public long lastCheckSpellUseMillis = 0L;
	public long lastSpellUseMillis = 0L;
	public int lastSpellUsePending = 0;

	private long _lastMoveActionMillis;
	public void setLastMoveActionMillis(long lastMoveActionMillis) {
		_lastMoveActionMillis = lastMoveActionMillis;	}

	public long getLastMoveActionMillis() {
		return _lastMoveActionMillis;
	}
	private long _lastAttackActionMillis;
	public void setLastAttackActionMillis(long value) {
		_lastAttackActionMillis = value;
	}	
	public long getLastAttackActionMillis() {
		return _lastAttackActionMillis;
	}
	private int attackRange;
	public int getAttackRange() {
		return attackRange;
	}
	public void setAttackRange(int attackRange) {
		this.attackRange = attackRange;
	}

	private static final int spearGfxIds[] = { 
		1002, 6830, 6831, 7143, 7144, 7147, 7148, 7151, 7152, 7155, 7156, 7159,
		7160, 7163, 7164, // 용기사 샤르나
		11330, 11351, 11344, 11368, 12240, 11447, 14928, 13389, 12237, 11419, 11418, 14923, 13715, 13717, 13719, 15115,
		13721, 13723, 13725, 13727, 13729, 13731, 13733, 13735, 13737, 13739, 13741, 13743, 13745, // 랭킹변신.
		13380, 13381, 14487, // PC꼬꼬마변신, 오렌지
		11121,11125, // 조우변신
		16008,16056, //신규변신
		15115, //남기사 창
		15539, 15537, 15534, 11376, 15599, 15834, 15832, 15833, 15531,
		17275,17272,
		11685,12015,
		17545,17549
	};

	public int getAttackRang() {
		int range = 0;
		int charGfxId = getCurrentSpriteId();
		int charBaseGfxId = getClassId();
		ItemInstance weapon = this.getWeapon();
		if (weapon != null && weapon.isEquipped()) {
			int weaponType = weapon.getItem().getType();
			switch (weaponType) {
			case 4:
			case 13:
			case 10:
				range = 15;
				if (weaponType == 4)
					range = 17; // 양손활일때
				break; // 양손활,한손활,건틀렛
			case 5:
			case 14:
				range = 2; // 양손창, 한손창, 체인소드
				boolean gfxCk = false;
				for (int gfxId : spearGfxIds) {
					if (gfxId == charGfxId) {
						gfxCk = true;
						break;
					}
				}
				if (!gfxCk && charBaseGfxId != charGfxId)
					range = 1;
				break;
			default: // 그밖에 무기들 없다.
				range = 1;
				break;
			}
		} else {
			range = 1;
		}
		return range;
	}

	public void broadcastRemoveAllKnownObjects() {
		for (L1Object known : getKnownObjects()) {
			if (known == null) {
				continue;
			}
			sendPackets(S_RemoveObject.get(known), true);
		}
	}
	@Override
	public long getCurrentSpriteInterval(EActionCodes actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	}
	//자동 사냥
	private final NativeStepSession nativeStepSession = new NativeStepSession(
			message -> l1j.server.server.network.NativeStepControl.send(this, message));

	public NativeStepSession getNativeStepSession() {
		return nativeStepSession;
	}

	private volatile boolean isAutoHunt = false;

    public String getAtsBlockReason() {
        l1j.server.AutoHuntSystem.AtsUsageTimer timer = atsUsageTimer;
        return timer == null ? "ATS is not running." : timer.blockReason();
    }

	public boolean getAutoHunt() {
		return isAutoHunt;
	}

	public void setAutoHunt(boolean flag) {
		isAutoHunt = flag;
	}

	public ScheduledFuture<?> _AutoController;
    private volatile l1j.server.AutoHuntSystem.AtsUsageTimer atsUsageTimer;
    private ScheduledFuture<?> atsUsageFuture;

	public synchronized void StartAutoController() {
		if (getAutoHunt()) return;
        if (!l1j.server.server.network.NativeStepControl.isBound(this)) {
            sendPackets(new S_SystemMessage("Client input module not ready. Restart the game client."));
            return;
        }
		resetAuto();
		setAutoPotion(40022);
		setAutoPolyID(getCurrentSpriteId());
		try {
            atsUsageTimer = l1j.server.AutoHuntSystem.AtsUsageTimer.start(this);
			nativeStepSession.begin();
			setAutoHunt(true);
			_AutoController = GeneralThreadPool.getInstance().scheduleAtFixedRate(new AutoHuntController(getId()), 0L, 10L);
            atsUsageFuture = GeneralThreadPool.getInstance().scheduleAtFixedRate(atsUsageTimer, 0L, 1000L);
		} catch (Exception failure) {
            EndAutoController();
            String message = failure instanceof l1j.server.AutoHuntSystem.AtsUsageTimer.StartDenied
                    ? failure.getMessage() : "\uc790\ub3d9\uc0ac\ub0e5\uc744 \uc2dc\uc791\ud560 \uc218 \uc5c6\uc2b5\ub2c8\ub2e4. \uad00\ub9ac\uc790\uc5d0\uac8c \ubb38\uc758\ud574 \uc8fc\uc138\uc694.";
            if (!(failure instanceof l1j.server.AutoHuntSystem.AtsUsageTimer.StartDenied))
                java.util.logging.Logger.getLogger("ATS").log(java.util.logging.Level.WARNING, "ATS start failed", failure);
            sendPackets(new S_SystemMessage(message));
            return;
		}

		this.setAlarmHp(true);
		this.setAlarmPotion(true);
		this.setAlarmPvp(true);
		this.setAlarmWeight(true);
	}

	public synchronized void EndAutoController() {
		setAutoHunt(false);
		nativeStepSession.end(); // Release input and hide HUD before any DB refund I/O.
        if (atsUsageFuture != null) { atsUsageFuture.cancel(false); atsUsageFuture = null; }
        if (atsUsageTimer != null) { atsUsageTimer.stop(); atsUsageTimer = null; }
		if (_AutoController != null) {
			_AutoController.cancel(true);
			_AutoController = null;
			resetAuto();
			toCharacterRefresh();
		}
		setAutoHunt(false);
	}

	public void resetAuto() {
		setAutoStatus(0);
		setAutoMoveCount(0);
		setAutoSkillDelay(0);		
		setAutoTarget(null);
		getAutoTargetList().clear();
		setAutoAiTime(0);		
	}

	public void toCharacterRefresh() {
		broadcastRemoveAllKnownObjects();
		removeAllKnownObjects();
		sendPackets(S_WorldObject.in(this));
		updateObject();
		sendPackets(S_CharVisualUpdate.get(this));
	}

	private L1HateList _autoTargetList = new L1HateList();

	public L1HateList getAutoTargetList() {
		return _autoTargetList;
	}

	public void setAutoTargetList(L1HateList attackList) {
		this._autoTargetList = attackList;
	}

	public void addAutoTargetList(L1Character mon) {
		if (_autoTargetList.containsKey(mon)) {
			return;
		}
		_autoTargetList.add(mon, 0);
	}

	public void removeAutoTargetList(L1Character mon) {
		if (mon == null || !_autoTargetList.containsKey(mon))
			return;
		_autoTargetList.remove(mon);
	}

	private int _autoPolyId;

	public int getAutoPolyID() {
		return _autoPolyId;
	}

	public void setAutoPolyID(int i) {
		_autoPolyId = i;
	}

	private int _autoLocX;

	public int getAutoLocX() {
		return _autoLocX;
	}

	public void setAutoLocX(int i) {
		_autoLocX = i;
	}

	private int _autoLocY;

	public int getAutoLocY() {
		return _autoLocY;
	}

	public void setAutoLocY(int i) {
		_autoLocY = i;
	}

	private L1Character _autoTarget;

	public L1Character getAutoTarget() {
		return _autoTarget;
	}

	public void setAutoTarget(L1Character mon) {
		_autoTarget = mon;
	}

	private L1Astar _autoAStar = new L1Astar();

	public L1Astar getAutoAstar() {
		return _autoAStar;
	}

	public void setAutoAstar(L1Astar a) {
		_autoAStar = a;
	}

	private int[][] _autoPath = new int[300][2];

	public int[][] getAutoPath() {
		return _autoPath;
	}

	public void setAutoPath(int[][] i) {
		_autoPath = i;
	}

	private int _autoMoveCount = CommonUtil.randomRange(50, 200);

	public int getAutoMoveCount() {
		return _autoMoveCount;
	}

	public void setAutoMoveCount(int i) {
		_autoMoveCount = i;
	}

	private long _autoSkillDelay;

	public long getAutoSkillDelay() {
		return _autoSkillDelay;
	}

	public void setAutoSkillDelay(long i) {
		_autoSkillDelay = i;
	}

	private int _autoStatus;

	public int getAutoStatus() {
		return _autoStatus;
	}

	public void setAutoStatus(int i) {
		_autoStatus = i;
	}

	private L1Node _autoTail;

	public L1Node getAutoTail() {
		return _autoTail;
	}

	public void setAutoTail(L1Node node) {
		_autoTail = node;
	}

	private boolean _autoPathFirst;

	public boolean isAutoPathFirst() {
		return _autoPathFirst;
	}

	public void setAutoPathFirst(boolean a) {
		_autoPathFirst = a;
	}

	public int _autoCurrentPath;

	private int _autoPotion;

	public int getAutoPotion() {
		return _autoPotion;
	}

	public void setAutoPotion(int i) {
		_autoPotion = i;
	}

	private int _autoPotion_percent;

	public int getAutoPotion_Percent() {
		return _autoPotion_percent;
	}

	public void setAutoPotion_Percent(int i) {
		_autoPotion_percent = i;
	}

	private long _autoTimeAttack;

	public long getAutoTimeAttack() {
		return _autoTimeAttack;
	}

	public void setAutoTimeAttack(long time) {
		_autoTimeAttack = time;
	}

	private long _autoTimeMove;

	public long getAutoTimeMove() {
		return _autoTimeMove;
	}

	public void setAutoTimeMove(long time) {
		_autoTimeMove = time;
	}

	private long _autoAiTime;

	public long getAutoAiTime() {
		return _autoAiTime;
	}

	public void setAutoAiTime(long l) {
		_autoAiTime = l;
	}

	private long _autoDropTime;

	public long getAutoDropTime() {
		return _autoDropTime;
	}

	public void setAutoDropTime(long l) {
		_autoDropTime = l;
	}

	private boolean _alarm_pvp = false;

	public boolean isAlarmPvp() {
		return _alarm_pvp;
	}

	public void setAlarmPvp(boolean flag) {
		_alarm_pvp = flag;
	}

	private long _PvpArlamTime;

	public long getPvpArlamTime() {
		return _PvpArlamTime;
	}

	public void setPvpArlamTime(long l) {
		_PvpArlamTime = l;
	}

	private boolean _alarm_potion = false;

	public boolean isAlarmPotion() {
		return _alarm_potion;
	}

	public void setAlarmPotion(boolean flag) {
		_alarm_potion = flag;
	}

	private long _PotionArlamTime;

	public long getPotionArlamTime() {
		return _PotionArlamTime;
	}

	public void setPotionArlamTime(long l) {
		_PotionArlamTime = l;
	}

	private boolean _alarm_weight = false;

	public boolean isAlarmWeight() {
		return _alarm_weight;
	}

	public void setAlarmWeight(boolean flag) {
		_alarm_weight = flag;
	}

	private long _WeightArlamTime;

	public long getWeightArlamTime() {
		return _WeightArlamTime;
	}

	public void setWeightArlamTime(long l) {
		_WeightArlamTime = l;
	}

	private boolean _alarm_hp = false;

	public boolean isAlarmHp() {
		return _alarm_hp;
	}

	public void setAlarmHp(boolean flag) {
		_alarm_hp = flag;
	}

	private long _HpArlamTime;

	public long getHpArlamTime() {
		return _HpArlamTime;
	}

	public void setHpArlamTime(long l) {
		_HpArlamTime = l;
	}
	public int[] _Insert_speed_over_loc = new int[3];

	public int _move_speed_over_count;
	public int getSpeedOverCount() {
		return _move_speed_over_count;
	}
	public void addSpeedOverCount() {
		_move_speed_over_count += 1;
	}
	public void delSpeedOverCount() {
		if (_move_speed_over_count <= 0)
			_move_speed_over_count = 0;
		else
			_move_speed_over_count -= 1;
	}

	private HashMap<Integer, ArrayList<ItemInstance>> _equip_set_items = new HashMap<Integer, ArrayList<ItemInstance>>();

	public HashMap<Integer, ArrayList<ItemInstance>> getEquipSetItems() {
		return _equip_set_items;
	}
	private FastTable<ItemInstance> equip_setitem = new FastTable<ItemInstance>();

	public FastTable<ItemInstance> getEquipSetItemList() {
		if (equip_setitem == null)
			return null;
		return equip_setitem;
	}
	private HashMap<String, Integer> _set_option = new HashMap<String, Integer>();

	public HashMap<String, Integer> getSet_Option() {
		return _set_option;
	}
	public static boolean LevelUpPresentSystem(L1PcInstance pc) {
		ItemInstance item = null;
		LevelUpPresentSystemLoader.L1PresentItems present_item = LevelUpPresentSystemLoader.getInstance()
				.getPresentItem(pc.getLevel());
		if (present_item != null) {
			int present_type = present_item.isPresentType();
			if (present_type == 1) {
				LevelUpPresentSystemAccount limit_account = LevelUpPresentSystemAccountTable.getInstance()
						.getPresentTable(pc.getAccountName(), pc.getLevel());
				if (present_item != null) {
					if (limit_account != null) {
						// 이미 보상 완료 해서 리턴
						return true;
					}
					item = ItemTable.getInstance().createItem(present_item.getItemId());
					if (item != null) {
						item.setCount(present_item.getItemCount());
						item.setEnchantLevel(present_item.getItemEnchantLevel());
						item.setAttrEnchantLevel(present_item.getItemAttrLevel());
						item.setIdentified(true);
						if (pc.getInventory().checkAddItem(item, present_item.getItemCount()) == L1Inventory.OK) {
							pc.getInventory().storeItem(item);
							item.setBless(present_item.getItemBless());
							pc.getInventory().updateItem(item, L1PcInventory.COL_ENCHANTLVL);
							pc.getInventory().saveItem(item, L1PcInventory.COL_ENCHANTLVL);
						}
					} else {
						System.out.println(String.format("보상 아이템 에러 itemId : %d 가 존재 하지 않음.", present_item.getItemId()));
					}
					pc.sendPackets(String.format("%d 레벨을 달성하여 보상 아이템을 지급 합니다. (계정당 1회 지급)", present_item.getLevel()));
					pc.sendPackets(new S_SystemMessage("" + present_item.getLevel() + " 레벨보상  : " + item.getLogName()
							+ " 갯수 : " + present_item.getItemCount() + " 개 지급"));

					LevelUpPresentSystemAccountTable.getInstance().updatePresentItem(pc, item, pc.getLevel(), true);
				}
			} else if (present_type == 2) {
				LevelUpPresentSystemCharacter limit_character = LevelUpPresentSystemCharacterTable.getInstance()
						.getPresentTable(pc, pc.getLevel());
				if (present_item != null) {
					if (limit_character != null) {
						// 이미 보상 완료 해서 리턴
						return true;
					}
					item = ItemTable.getInstance().createItem(present_item.getItemId());
					if (item != null) {
						item.setCount(present_item.getItemCount());
						item.setEnchantLevel(present_item.getItemEnchantLevel());
						item.setIdentified(true);
						if (pc.getInventory().checkAddItem(item, present_item.getItemCount()) == L1Inventory.OK) {
							pc.getInventory().storeItem(item);
							pc.getInventory().updateItem(item, L1PcInventory.COL_ENCHANTLVL);
							pc.getInventory().saveItem(item, L1PcInventory.COL_ENCHANTLVL);
						}
					} else {
						System.out
						.println(String.format("보상 아이템 에러 itemId : %d 가 존재 하지 않음.", present_item.getItemId()));
					}
					pc.sendPackets(String.format("%d 레벨을 달성하여 보상 아이템을 지급 합니다. (캐릭당 1회 지급)", present_item.getLevel()));
					pc.sendPackets(new S_SystemMessage("" + present_item.getLevel() + " 레벨보상  : " + item.getLogName()
							+ " 갯수 : " + present_item.getItemCount() + " 개 지급"));
					LevelUpPresentSystemCharacterTable.getInstance().updatePresentItem(pc, item, true);
				}
			}
		}
		return false;
	}


	private int _teleportX = 0;

	public int getTeleportX() {
		return _teleportX;
	}

	public void setTeleportX(int i) {
		_teleportX = i;
	}

	private int _teleportY = 0;

	public int getTeleportY() {
		return _teleportY;
	}

	public void setTeleportY(int i) {
		_teleportY = i;
	}

	private short _teleportMapId = 0;

	public short getTeleportMapId() {
		return _teleportMapId;
	}

	public void setTeleportMapId(short i) {
		_teleportMapId = i;
	}

	private int _teleportHeading = 0;

	public int getTeleportHeading() {
		return _teleportHeading;
	}

	public void setTeleportHeading(int i) {
		_teleportHeading = i;
	}

	private long _PostDelay;

	public long getPostDelay() {
		return _PostDelay;
	}

	public void setPostDelay(long i) {
		_PostDelay = i;
	}

	private boolean _isShowTradeChat = true;

	public boolean isShowTradeChat() {
		return _isShowTradeChat;
	}

	public void setShowTradeChat(boolean flag) {
		_isShowTradeChat = flag;
	}

	private boolean _isShowWorldChat = true;

	public boolean isShowWorldChat() {
		return _isShowWorldChat;
	}

	public void setShowWorldChat(boolean flag) {
		_isShowWorldChat = flag;
	}

	public void stopEquipmentTimer() {
		List<ItemInstance> allItems = this.getInventory().getItems();
		for(ItemInstance item : allItems) {
			if(item.isEquipped() && item.getRemainingTime() > 0) {
				item.stopEquipmentTimer(this);
			}
		}
	}

	private int _Bonus_drop_npc = 0;

	public int getBonusDropNpc() {
		return _Bonus_drop_npc;
	}

	public void setBonusDropNpc(int value) {
		_Bonus_drop_npc = value;
	}
	private boolean _isBossNotify = true;

	public boolean isBossNotify() {
		return _isBossNotify;
	}

	public void setBossNotify(boolean b) {
		_isBossNotify = b;
	}

	private int boss_spawn_yn;

	public int getBossYN() {
		return boss_spawn_yn;
	}

	public void setBossYN(int i) {
		boss_spawn_yn = i;
	}

	private boolean _bossYn = true;

	public boolean isBossYNMessage() {
		return _bossYn;
	}

	public void setBossYNMessage(boolean flag) {
		_bossYn = flag;
	}

	public boolean AccountClanCheck(String i, int SS) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			int count = 0;

			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT ClanID FROM characters WHERE account_name = ?");
			pstm.setString(1, i);

			rs = pstm.executeQuery();
			while (rs.next()) {
				int aa = rs.getInt("ClanID");
				if (aa != 0 && aa != SS) {
					count++;
					break;
				}
			}
			if (count > 0) {
				return true;
			}
			return false;
		} catch (Exception e) {

		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return false;
	}

	private DungeonTimeUserInformation _dtInfo = DungeonTimeUserInformation.newInstance();

	public DungeonTimeUserInformation get_dungeon_information() {
		return _dtInfo;
	}

	public DungeonTimeProgress<?> get_progress(DungeonTimeInformation dtInfo) {
		return _dtInfo.get_progress(dtInfo);
	}

	public void dec_dungeon_progress(DungeonTimeInformation dtInfo) {
		_dtInfo.dec_dungeon_progress(this, dtInfo);
	}

	public Collection<DungeonTimeProgress<?>> get_character_progresses() {
		return _dtInfo.get_character_progresses().values();
	}

	public Collection<DungeonTimeProgress<?>> get_account_progresses() {
		return _dtInfo.get_account_progresses().values();
	}

	public void put_dungeon_progress(int timer_id, AccountTimeProgress progress) {
		_dtInfo.put_dungeon_progress(timer_id, progress);
	}

	public void put_dungeon_progress(int timer_id, CharacterTimeProgress progress) {
		_dtInfo.put_dungeon_progress(timer_id, progress);
	}

	public void send_dungeon_progress(DungeonTimeInformation dtInfo) {
		_dtInfo.send_dungeon_progress(this, dtInfo);
	}

	public void initialize_dungeon_progress() {
		_dtInfo.initialize();
	}

	private boolean _isDistributionlogin;

	public boolean isDistributionlogin() {
		return _isDistributionlogin;
	}

	public void setDistributionlogin(boolean flag) {
		_isDistributionlogin = flag;
	}

	private boolean _isDistributionMsg;

	public boolean isDistributionMsg() {
		return _isDistributionMsg;
	}

	public void setDistributionMsg(boolean flag) {
		_isDistributionMsg = flag;
	}

	private boolean _isworldkillmessage;

	public boolean isWorldKillMessage() {
		return _isworldkillmessage;
	}

	public void setWolrdKillMessage(boolean flag) {
		_isworldkillmessage = flag;
	}

	private boolean _compulsion_attack = false;

	public boolean isCompulsionAttack() {
		return _compulsion_attack;
	}

	public void setCompulsionAttack(boolean value) {
		_compulsion_attack = value;
	}
	private int _emblem = 0;

	public int getExpEmblem() {
		return this._emblem;
	}

	public void setExpEmblem(int i) {
		this._emblem = i;
	}

	public void addExpEmblem(int i) {
		this._emblem += i;
	}
	private int _reduc_cancel;
	public int getReducCancel() {
		return _reduc_cancel;
	}
	public void addReducCancel(int i) {
		_reduc_cancel += i;
	}
	private ArrayList<Integer> _inven_bonus_items = new ArrayList<Integer>();

	public void addInvenBonusItems(int itemid) {
		_inven_bonus_items.add(itemid);
	}
	public void delInvenBonusItems(int itemid) {
		_inven_bonus_items.remove((Object) itemid);
	}
	public boolean isInvenBonusItems(int itemid) {
		return _inven_bonus_items.contains(itemid);
	}

	private long _EquipChange = 0;
	public long getEquipChange() {
		return _EquipChange;
	}

	public void setEquipChange(long l) {
		_EquipChange = l;
	}

	private long _quiztime = 0;
	public long getQuizTime() {
		return _quiztime;
	}

	public void setQuizTime(long l) {
		_quiztime = l;
	}

	private long _quiztime2 = 0;
	public long getQuizTime2() {
		return _quiztime2;
	}

	public void setQuizTime2(long l) {
		_quiztime2 = l;
	}


	private long _quiztime3 = 0;
	public long getQuizTime3() {
		return _quiztime3;
	}

	public void setQuizTime3(long l) {
		_quiztime3 = l;
	}

	private long _quiztime4 = 0;
	public long getQuizTime4() {
		return _quiztime4;
	}

	public void setQuizTime4(long l) {
		_quiztime4 = l;
	}
	private S_ItemExSelectPacket _select_item;

	public void on_select_item(S_ItemExSelectPacket pck) {
		final int id = pck.get_id();
		_select_item = pck;
		sendPackets(pck);
		GeneralThreadPool.getInstance().schedule(new Runnable() {
			@Override
			public void run() {
				S_ItemExSelectPacket pck = _select_item;
				if (pck != null) {
					if (pck.get_id() == id) {
						_select_item = null;
						pck.dispose();
					}
				}
			}
		}, 20000L);
	}

	public S_ItemExSelectPacket get_select_item() {
		S_ItemExSelectPacket pck = _select_item;
		_select_item = null;
		return pck;
	}


	public void sendClanMarks() {
		for (L1Clan clan : L1World.getInstance().getAllClans()) {
			sendPackets(new S_Emblem(clan.getEmblemId()), true);
		}
		if (getClanid() != 0) {
			L1Clan clan = L1World.getInstance().getClan(getClanname());
			if (clan != null) {
				if (isCrown() && getId() == clan.getLeaderId() && clan.getCastleId() != 0) {
					sendPackets(new S_CastleMaster(clan.getCastleId(), getId()), true);
				}
			}
		}
	}

	// 자동 물약
	private boolean isAutoPotion = false;

	public boolean getAutoPotionUse() {
		return isAutoPotion;
	}

	public void setAutoPotionUse(boolean flag) {
		isAutoPotion = flag;
	}

	public ScheduledFuture<?> _AutoPotionController;

	public void StartAutoPotionController() {
		if (getAutoPotion() == 0) {
			setAutoPotion(40022);
		}
		if (getAutoPotion_Percent() == 0) {
			setAutoPotion_Percent(90);
		}
		setAutoPotionUse(true);
		_AutoPotionController = GeneralThreadPool.getInstance().scheduleAtFixedRate(new AutoPotionController(getId()), 0L, 10L);
	}

	public void EndAutoPotionController() {
		if (_AutoPotionController != null) {
			_AutoPotionController.cancel(true);
			_AutoPotionController = null;
			setAutoPotionUse(false);
		}
	}
	
	public void settingdmg() {
		removeSkillEffect(SkillId.SPR_TIME);
		setPcDmg((short) 0);
		setMinDmg((short) 0);
		setMaxDmg((short) 0);
		setDmgCount((short) 0);
		setDmgPesent((short) 0);
		setMinTime((short) 0);
		setMaxTime((short) 0);
		setStartTime((short) 0);
		setEndTime((short) 0);
	}
	
	private long _startTime = 0;
	public long getStartTime() { return _startTime; }	
	public void setStartTime(long l) { _startTime = l ; }
	
	private long _endTime = 0;
	public long getEndTime() { return _endTime; }	
	public void setEndTime(long l) { _endTime = l ; }
	
	private double _minTime = 0;
	public double getMinTime() { return _minTime; }	
	public void setMinTime(double l) { _minTime = l ; }
	
	private double _maxTime = 0;
	public double getMaxTime() { return _maxTime; }	
	public void setMaxTime(double l) { _maxTime = l ; }
	
	private int _pcDmg;
	public int getPcDmg() {return _pcDmg;}
	public void setPcDmg(int pcDmg) {_pcDmg = pcDmg;}
	
	private int _minDmg;
	public int getMinDmg() {return _minDmg;}
	public void setMinDmg(int minDmg) {_minDmg = minDmg;}
	
	private int _maxDmg;
	public int getMaxDmg() {return _maxDmg;}
	public void setMaxDmg(int maxDmg) {_maxDmg = maxDmg;}
	
	private int dmgcount;
	public int getDmgCount() {return dmgcount;}
	public void setDmgCount(int i) {dmgcount = i;}
	
	private int dmgrate;
	public int getDmgPesent() {return dmgrate;}
	public void setDmgPesent(int i) {dmgrate = i;}


	/** 슬롯체인지 **/
	private List<Integer> _slotItemOne = new ArrayList<Integer>();
	private List<Integer> _slotItemTwo = new ArrayList<Integer>();

	public void addSlotItem(int slotNum, int itemobjid, boolean flag) {
		if (flag) {
			if (slotNum == 0) {
				_slotItemOne.clear();
				for (ItemInstance item : getInventory().getItems()) {
					if (item.isEquipped()) { 
						_slotItemOne.add(item.getId());
					}
				}
			} else if (slotNum == 1) {
				_slotItemTwo.clear();
				for (ItemInstance item : getInventory().getItems()) {
					if (item.isEquipped()) { 
						_slotItemTwo.add(item.getId());
					}
				}
			}
		} else {
			if (slotNum == 0) {
				_slotItemOne.add(itemobjid);
			} else if (slotNum == 1) {
				_slotItemTwo.add(itemobjid);
			}
		}
	}

	public List<Integer> getSlotItems(int slotNum) {
		if (slotNum == 0) {
			return _slotItemOne;
		} else if (slotNum == 1) {
			return _slotItemTwo;
		}
		return null;
	}

	public void getChangeSlot(int slotNum) {
		if (slotNum == 0) {
			for (ItemInstance item : this.getInventory().getItems()) {
				if (!_slotItemOne.contains(item.getId())) {
					if (item.isEquipped()) {
						getInventory().setEquipped(item, false);
					} else {

					}
				}
			}
			for (ItemInstance item : this.getInventory().getItems()) {
				if (_slotItemOne.contains(item.getId())) {
					if (item.isEquipped()) {
						if (item.getItem().getType2() == 1) {
							if (!L1PolyMorph.isEquipableWeapon(getCurrentSpriteId(), item.getItem().getType())) {
								getInventory().setEquipped(item, false);
							}
						}
					} else {
						if (item.getItem().getType2() == 1) {
							if (!L1PolyMorph.isEquipableWeapon(getCurrentSpriteId(), item.getItem().getType())) {
								continue;
							}
						}
						getInventory().setEquipped(item, true);
					}
				}
			}
		} else if (slotNum == 1) {
			for (ItemInstance item : this.getInventory().getItems()) {
				if (!_slotItemTwo.contains(item.getId())) {
					if (item.isEquipped()) {
						getInventory().setEquipped(item, false);
					} else {

					}
				}
			}
			for (ItemInstance item : this.getInventory().getItems()) {
				if (_slotItemTwo.contains(item.getId())) {
					if (item.isEquipped()) {
						if (item.getItem().getType2() == 1) {
							if (!L1PolyMorph.isEquipableWeapon(getCurrentSpriteId(), item.getItem().getType())) {
								getInventory().setEquipped(item, false);
							}
						}
					} else {
						if (item.getItem().getType2() == 1) {
							if (!L1PolyMorph.isEquipableWeapon(getCurrentSpriteId(), item.getItem().getType())) {
								continue;
							}
						}
						getInventory().setEquipped(item, true);
					}
				}
			}
		}
	}

	private int slotNumber = 0;

	public int getSlotNumber() {
		return slotNumber;
	}

	public void setSlotNumber(int i) {
		slotNumber = i;
	}

	public boolean isTwoLogin(L1PcInstance c) {
		boolean bool = false;
		for (L1PcInstance target : L1World.getInstance().getAllPlayers()) {
			if (target.noPlayerck2 || target instanceof FakePcInstance) {
				continue;
			}
			if ((target == null) || (target.getNetConnection() == null)) {
				continue;
			}
			if (c.getId() != target.getId() && !target.isPrivateShop()) {
				if (c.getNetConnection().getAccountName().equalsIgnoreCase(target.getNetConnection().getAccountName())) {
					bool = true;
					break;
				}
			}
		}
		return bool;
	}
	private int _tempCharGfxAtDead;
	public int getTempCharGfxAtDead() {
		return _tempCharGfxAtDead;
	}
	public void setTempCharGfxAtDead(int i) {
		_tempCharGfxAtDead = i;
	}

	////////////// 제작 관련 /////////////////
	public int Craft_Npcid;
	public int getCraft_Npcid() {
		return Craft_Npcid;
	}
	public void setCraft_Npcid(int craft_Npcid) {
		Craft_Npcid = craft_Npcid;
	}
	public int getCraft_Orderid() {
		return Craft_Orderid;
	}
	public void setCraft_Orderid(int craft_Orderid) {
		Craft_Orderid = craft_Orderid;
	}
	public int Craft_Orderid;
	
	/**
	 * @param x
	 * @param y
	 * @param map
	 * @param heading
	 * @param effectId
	 * @param effectCheck
	 * @param skillCheck
	 */
	public void teleportScoll(final int x, final int y, final short map, final int heading, boolean effectCheck, final int effectId) {
		
		this.setTeleport(true);
		this.setTeleportX(x);
		this.setTeleportY(y);
		this.setTeleportMapId(map);
		this.setTeleportHeading(heading);
		this.sendPackets(new S_Teleport(this));	
		
		if(effectCheck) {
			setTemporaryEffect(S_SkillSound.get(getId(), effectId));
		}
		
	}
	
	//////////////////////////////////
	/**
	 * 기존의 텔레포트에 문제가 많아 새로 구현한다.
	 */
	public void teleport(final int x, final int y, final short map, final int heading, final int effectId, boolean effectCheck, boolean skillCheck) {
		try {	

			// 펫레이싱 , 유령의 집 버프 삭제 
			if ((this.getMapId() == 5140 || this.getMapId() == 5143) && this.getMapId() != map) {
				L1HauntedHouse.getInstance().clearBuff(this);
			}		
			// 고스트 상태일때 종료 처리하기.
			if (this.getMapId() == 5153 && this.getMapId() != map && this.isGhost()) {
				this.endGhost();
			}
			// 낚시 상태 체크 
			if (isFishing()) {
				FishingTimeController.getInstance().endFishing(this);
			}
			this.setTeleport(true);
			this.setTeleportX(x);
			this.setTeleportY(y);
			this.setTeleportMapId(map);
			this.setTeleportHeading(heading);
			
		//	L1World.getInstance().removeVisibleObject(this);
			this.sendPackets(new S_Teleport(this));				
		//	clearTemporaryEffect();	
	
			if(skillCheck && effectCheck) {
				
		//		System.out.println("? ? ");
				
				
				S_SkillSound ss = S_SkillSound.get(getId(), effectId);
				sendPackets(ss, false);
				Broadcaster.broadcastPacket(this, ss, false);
				
			//	notifyPlayersLogout(getKnownPlayers());
			//	notifyPlayersLogout(L1World.getInstance().getRecognizePlayer(this));
				
				Runnable teleport = () -> { Teleport.getInstance().Teleportation(this); };
				GeneralThreadPool.getInstance().schedule(teleport, 200L);
			}
			else {
				
				if (effectCheck) {	
					setTemporaryEffect(S_SkillSound.get(getId(), effectId));
				}			
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private ServerBasePacket _tempEffect;
	public void setTemporaryEffect(ServerBasePacket pck) {
		clearTemporaryEffect();
		_tempEffect = pck;
	}
	public ServerBasePacket getTemporaryEffect() {
		return _tempEffect;
	}
	public void clearTemporaryEffect() {
		if (_tempEffect != null) {
			_tempEffect.clear();
			_tempEffect = null;
		}
	}

	public void SimpleTeleport(int x, int y, int mapid) {
		try {
			if (isFishing()) {
				FishingTimeController.getInstance().endFishing(this);
			}
			S_SkillSound ss = S_SkillSound.get(getId(), 169);
			sendPackets(ss, false);
			Broadcaster.broadcastPacket(this, ss, false);
			ss.clear(); 
			this.teleport(x, y, (short) mapid, this.getHeading(), 169, true, false);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
}