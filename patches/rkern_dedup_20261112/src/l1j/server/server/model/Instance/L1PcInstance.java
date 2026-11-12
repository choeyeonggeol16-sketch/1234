package l1j.server.server.model.Instance;
import l1j.server.server.model.character.effect.PcPassiveEffects;
import l1j.server.server.model.character.effect.PcPotentialEffects;
import l1j.server.server.model.character.effect.PcClanBuffEffects;
import l1j.server.server.model.character.effect.PcWantedEffects;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_ClanName;
import l1j.server.server.model.network.core.sc.packet.SC_CastleMaster;

import l1j.server.server.model.network.core.sc.packet.SC_Fishing;



import l1j.server.server.model.network.core.sc.packet.SC_Weight;
import l1j.server.server.model.network.core.sc.packet.SC_CharTitle;
import l1j.server.server.model.network.core.sc.packet.SC_CharSpeedUpdate;
import java.sql.PreparedStatement;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingUtilities;
import javolution.util.FastMap;
import l1j.server.Config;
import l1j.server.MJ3SEx.EActionCodes;
import l1j.server.MJ3SEx.Loader.SpriteInformationLoader;
import l1j.server.MJCaptchaSystem.MJCaptcha;
import l1j.server.MJCombatSystem.MJCombatObserver;
import l1j.server.MJCombatSystem.Loader.MJCombatLoadManager;
import l1j.server.MJDungeonTimer.DungeonTimeInformation;
import l1j.server.MJDungeonTimer.DungeonTimeUserInformation;
import l1j.server.MJDungeonTimer.Loader.DungeonTimeProgressLoader;
import l1j.server.MJDungeonTimer.Progress.AccountTimeProgress;
import l1j.server.MJDungeonTimer.Progress.CharacterTimeProgress;
import l1j.server.MJDungeonTimer.Progress.DungeonTimeProgress;
import l1j.server.MJExpAmpSystem.MJExpAmplifier;
import l1j.server.MJInstanceSystem.MJInstanceEnums.InstStatus;
import l1j.server.MJInstanceSystem.MJInstanceSpace;

import l1j.server.MJItemSkillSystem.MJItemSkillModelLoader;
import l1j.server.MJItemSkillSystem.Model.MJItemSkillModel;
import l1j.server.MJPassiveSkill.MJPassiveID;
import l1j.server.MJPassiveSkill.MJPassiveInfo;
import l1j.server.MJRaidSystem.MJRaidSpace;
import l1j.server.MJTemplate.MJEPcStatus;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Selector;
import l1j.server.MJTemplate.MJSqlHelper.Executors.Updator;
import l1j.server.MJTemplate.MJSqlHelper.Handler.Handler;
import l1j.server.MJTemplate.MJSqlHelper.Handler.SelectorHandler;
import l1j.server.MJWarSystem.MJCastleWarBusiness;
import l1j.server.MJWarSystem.MJWar;
import l1j.server.MJWarSystem.MJWarFactory.WAR_TYPE;
import l1j.server.server.Account;
import l1j.server.server.ActionCodes;
import l1j.server.server.GMCommands;
import l1j.server.server.ThreadPool;
import l1j.server.server.Opcodes;
import l1j.server.server.RepeatTask;
import l1j.server.server.SkillCheck;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.Controller.PremiumTimeController;
import l1j.server.server.command.executor.L1HpBar;
import l1j.server.server.datatables.CharBuffTable;
import l1j.server.server.datatables.CharacterSlotItemTable;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.CharactersGiftItemTable;
import l1j.server.server.datatables.ClanBuffTable;
import l1j.server.server.datatables.ClanBuffTable.ClanBuff;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.model.Ability;
import l1j.server.server.model.Getback;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1ChatParty;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.L1DeathMatch;
import l1j.server.server.model.L1DwarfForPackageInventory;
import l1j.server.server.model.L1EquipmentSlot;
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
import l1j.server.server.model.L1Racing;
import l1j.server.server.model.L1ReturnStatTemp;
import l1j.server.server.model.L1SmallIntMap;
import l1j.server.server.model.L1StatReset;
import l1j.server.server.model.L1TownLocation;
import l1j.server.server.model.L1Trade;
import l1j.server.server.model.L1World;
import l1j.server.server.model.ReportDeley;
import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.BookQuest.UserSide.BQSCharacterData;
import l1j.server.server.model.character.TileOccupancy.TileOccupancy;
import l1j.server.server.model.character.action.CCreatureController;
import l1j.server.server.model.character.Utill.PcBaseAbilityCache;
import l1j.server.server.model.character.scheduler.AutoSaveChaQuitCheckScheduler;
import l1j.server.server.model.character.scheduler.CRegenController;
import l1j.server.server.model.character.scheduler.CharTamScheduler;
import l1j.server.server.model.character.scheduler.PcInventoryExpiryScheduler;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.character.slot.SlotChangeManager;
import l1j.server.server.model.classes.L1ClassFeature;
import l1j.server.server.model.gametime.L1GameTimeCarrier;
import l1j.server.server.model.item.Card.L1CardCollection;
import l1j.server.server.model.item.Exp.ExpBonusState;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.core.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;
import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_Teleport;
import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.monitor.Logger.ItemActionType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.Packet.SC_AttendanceUserDataExtend;
import l1j.server.server.packets.Protocol.Packet.SC_CharStat;
import l1j.server.server.packets.Protocol.Packet.SC_RestExpInfo;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eDurationShowType;
import l1j.server.server.packets.Protocol.Packet.SC_SpellBuffNoti.eNotiType;
import l1j.server.server.packets.Protocol.Stream.ProtoOutputStream;
import l1j.server.server.packets.Protocol.Packet.Attend.AttendanceUserFlag;
import l1j.server.server.packets.Protocol.Packet.Companion.CompanionT.eCommand;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeShape;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionShop;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_NewCreateItem;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_ReturnedStat;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1BookMark;
import l1j.server.server.templates.L1QuestInfo;
import l1j.server.server.utils.CalcStat;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.MJCommons;
import l1j.server.server.utils.ServerMessage;
import l1j.server.server.utils.eKind;
import l1j.server.swing.chocco;


import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
import l1j.server.server.model.network.core.sc.packet.SC_Lawful;
import l1j.server.server.model.network.core.sc.packet.SC_PinkName;
import l1j.server.server.model.network.core.sc.packet.SC_Poison;
import l1j.server.server.model.character.CYieldLock;
public class L1PcInstance extends L1Character {
    private volatile long cacheDSessionEpoch;

    public void setCacheDSessionEpoch(long epoch) { cacheDSessionEpoch = epoch; }
    public long getCacheDSessionEpoch() { return cacheDSessionEpoch; }


    private final CYieldLock userStateLock = new CYieldLock();
    private final CYieldLock masteryLock = new CYieldLock();

    public final void lockUserState() {
        userStateLock.lock();
    }

    public final void unlockUserState() {
        userStateLock.unlock();
    }

    private void lockMastery() {
        masteryLock.lock();
    }

    private void unlockMastery() {
        masteryLock.unlock();
    }

	private long FishingShopBuyTime_1;


	public boolean _CubeEffect = false;
	public boolean _Blessleaf = false;
	public boolean _EnchantWeaponSuccess = false;
	public boolean _EnchantArmorSuccess = false;

	public boolean _ErzabeBox = false;
	public boolean _SandwormBox = false;

	public int 상인찾기Objid = 0;
	public boolean war_zone = false;

	public boolean isTaitanM = false;
	private boolean _isMonsterHpBar = false;

	public boolean isMonsterHpBar() {
		return _isMonsterHpBar;
	}

	public void setMonsterHpBar(boolean flag) {
		this._isMonsterHpBar = flag;
	}

	public int getNcoin() {
		if (getAccount() != null) {
			return getNetConnection().getAccount().Ncoin_point;
		}
		return 0;
	}

	private boolean _isSM;

	public boolean isSM() {
		return _isSM;
	}

	public void setSM(boolean flag) {
		this._isSM = flag;
	}

	/**디스 중첩 by Neige**/
	private int _disNum = 0;

	public synchronized int getDisNum() {
		return _disNum;
	}

	public synchronized void setDisNum(int i) {
		_disNum = i;
	}
	/**성향 고정 by Neige**/
	private int _lawfulStop = 0;

	public synchronized int getLawfulStop() {
		return _lawfulStop;
	}

	public synchronized void setLawfulStop(int i) {
		_lawfulStop = i;
	}
	
	private L1StatReset _statReset;

	public void setStatReset(L1StatReset sr) {
		_statReset = sr;
	}

	public L1StatReset getStatReset() {
		return _statReset;
	}

	public L1ReturnStatTemp rst = null;

	public void addNcoin(int coin) {
		if (getNetConnection() != null) {
			if (getNetConnection().getAccount() != null) {
				getNetConnection().getAccount().Ncoin_point += coin;
				getNetConnection().getAccount().updateNcoin();
				// System.out.println(getNetConnection().getAccount().Ncoin_point);
			}
		} else {
			// System.out.println("무인에 걸린다.");
		}
	}

	public void addNcoin1(int coin) {
		if (getNetConnection() != null) {
			if (getNetConnection().getAccount() != null) {
				getNetConnection().getAccount().Ncoin_point -= coin;
				getNetConnection().getAccount().updateNcoin();
				// System.out.println(getNetConnection().getAccount().Ncoin_point);
			}
		}
	}

	private int boss_spawn_yn;

	public int getBossYN() {
		return boss_spawn_yn;
	}

	public void setBossYN(int i) {
		boss_spawn_yn = i;
	}

	private boolean specialBuff = false;

	public void setSpecialBuff(boolean flag) {
		this.specialBuff = flag;
	}

	public boolean isSpecialBuff() {
		return this.specialBuff;
	}

	private ReportDeley _reportdeley;
	private ScheduledFuture<?> _reportDelayFuture;

	public synchronized void startReportDeley() {
		if (isLogoutStarted()) return;
		final ReportDeley next = new ReportDeley(this);
		final ScheduledFuture<?> future = ThreadPool.getInstance().schedule(next, 100000L);
		// Never leave report permanently disabled if scheduler admission fails.
		if (future == null) return;
		final ScheduledFuture<?> previous = _reportDelayFuture;
		_reportdeley = next;
		_reportDelayFuture = future;
		setReport(false);
		if (previous != null) previous.cancel(false);
	}

	public synchronized void completeReportDeley(ReportDeley task) {
		if (_reportdeley != task) return;
		_reportdeley = null;
		_reportDelayFuture = null;
		if (!isLogoutStarted() && !isDead()) setReport(true);
	}

	private synchronized void stopReportDeley() {
		final ScheduledFuture<?> future = _reportDelayFuture;
		_reportDelayFuture = null;
		_reportdeley = null;
		if (future != null) future.cancel(false);
	}

	// /신고 추가
	private boolean _isReport = true;

	public void setReport(boolean _isreport) {
		_isReport = _isreport;
	}

	public boolean isReport() {
		return _isReport;
	}

	private static final long serialVersionUID = 1L;
	
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
	public static final int CLASSID_DRAGONKNIGHT_MALE = 6658;
	public static final int CLASSID_DRAGONKNIGHT_FEMALE = 6661;
	public static final int CLASSID_BLACKWIZARD_MALE = 6671;
	public static final int CLASSID_BLACKWIZARD_FEMALE = 6650;
	public static final int CLASSID_전사_MALE = 12490;
	public static final int CLASSID_전사_FEMALE = 12494;
	
	private int boss_get_x;
	private int boss_get_y;
	
	
// 콤보시스템
	public int getComboCount() {
		return this.comboCount;
	}

	public void setComboCount(int comboCount) {
		this.comboCount = comboCount;
	}

	public boolean PC방_버프 = false;
	public boolean PC방_버프삭제중 = false;

	public void setPC방_버프(boolean b) {
		PC방_버프 = b;
		SC_AttendanceUserDataExtend userData = getAttendanceData();
		if (userData != null) {
			AttendanceUserFlag flag = b ? AttendanceUserFlag.USER_FLAG_PC_CAFE : AttendanceUserFlag.USER_FLAG_NORMAL;
			if (!userData.get_userFlag().equals(flag)) {
				userData.set_userFlag(flag);
				SC_AttendanceUserDataExtend.send(this);
			}
		}
		SC_RestExpInfo.send(this);
	}

	private boolean isSafetyZone;

	public boolean getSafetyZone() {
		return isSafetyZone;
	}

	public void setSafetyZone(boolean value) {
		if (isSafetyZone == value)
			return;
		isSafetyZone = value;
		if (hasSkillEffect(L1SkillId.EXP_POTION)) {
			int time = getSkillEffectTimeSec(L1SkillId.EXP_POTION);
			if (isSafetyZone) {
				SC_InventoryIcon.icoEnd(this, L1SkillId.EXP_POTION);
				SC_InventoryIcon.iconNewUnLimit(this, L1SkillId.EXP_POTION + 1, 4973, true); // 세이프티존
			} else {
				SC_InventoryIcon.icoEnd(this, L1SkillId.EXP_POTION + 1);
				SC_InventoryIcon.icoNew(this, L1SkillId.EXP_POTION, 4973, time, true); // 사냥터
			}
		}
	}

	private int noDelayTime = 0;

	public int getNoDelayTime() {
		return noDelayTime;
	}

	public void setNoDelayTime(int noDelayTime) {
		this.noDelayTime = noDelayTime;
	}


	/**린엠 이펙트 켜고 끄기 Neige**/
	private int _linMeff = 0;

	public int getLinMeff() {
		return _linMeff;
	}

	public void setLinMeff(int ii) {
		_linMeff = ii;
	}

	/** 레이드 Y_N **/
	private boolean _raid = false;

	public void setRaidGame(boolean flag) {
		this._raid = flag;
	}

	public boolean getRaidGame() {
		return _raid;
	}

	private boolean _Mayo = false;

	public void setMayo(boolean flag) {
		this._Mayo = flag;
	}

	public boolean getMayo() {
		return _Mayo;
	}

	private boolean _Necross = false;

	public void setNecross(boolean flag) {
		this._Necross = flag;
	}

	public boolean getNecross() {
		return _Necross;
	}

	private boolean _Tebeboss = false;

	public void setTebeboss(boolean flag) {
		this._Tebeboss = flag;
	}

	public boolean getTebeboss() {
		return _Tebeboss;
	}

	private boolean _Curch = false;

	public void setCurch(boolean flag) {
		this._Curch = flag;
	}

	public boolean getCurch() {
		return _Curch;
	}

	private boolean _dtah = false;

	public void setDeat(boolean flag) {
		this._dtah = flag;
	}

	public boolean getDeat() {
		return _dtah;
	}

	private boolean _trac = false;

	public void setTrac(boolean flag) {
		this._trac = flag;
	}

	public boolean getTrac() {
		return _trac;
	}

	private boolean _girtas = false;

	public void setGirtas(boolean flag) {
		this._girtas = flag;
	}

	public boolean getGirtas() {
		return _girtas;
	}

	public int getBossx() {
		return boss_get_x;
	}

	public boolean isDesperado() {
		return hasSkillEffect(L1SkillId.DESPERADO);
	}

	public void setBossx(final int i) {
		boss_get_x = i;
	}

	public int getBossy() {
		return boss_get_y;
	}

	public void setBossy(final int i) {
		boss_get_y = i;
	}

	// 계정 정로 로드시 필요..
	public Account getAccount() {
		if (_netConnection == null){
			return null;
		}
		return this._netConnection.getAccount();
	}

	// 문장주시 변수
	public boolean 문장주시 = false;

	public int _x;

	// 성장의 낚시관련
	private L1ItemInstance _fishingitem;

	public L1ItemInstance getFishingItem() {
		return _fishingitem;
	}

	public void setFishingItem(L1ItemInstance item) {
		_fishingitem = item;
	}

	public int dx = 0;
	public int dy = 0;
	public short dm = 0;
	public int dh = 0;
	public int 상점변신 = 0;

	public static final String WANTED_TITLE = Config.수배멘트;
	public static final String WANTED_TITLE1 = Config.수배멘트1;
    public void doWanted1(boolean isOn) { PcWantedEffects.apply(this, PcWantedEffects.Kind.doWanted1, isOn); }
    public void doWanted(boolean isOn) { PcWantedEffects.apply(this, PcWantedEffects.Kind.doWanted, isOn); }
    public void 포우브레이브(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.포우브레이브); }
    public void 램페이지(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.램페이지); }
    public void 래스트(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.래스트); }
    public void 엠파이어오버로드(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.엠파이어오버로드); }
    public void 기간틱(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.기간틱); }
    public void 샤이닝아머(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.샤이닝아머); }
    public void 마제스티(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.마제스티); }
    public void 포스스턴(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.포스스턴); }
    public void 네메시스(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.네메시스); }
    public void 가드피어(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.가드피어); }
    public void 가드피어호러(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.가드피어호러); }
    public void hp300(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.hp300); }
    public void mp300(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.mp300); }
    public void 대미지감소5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.대미지감소5); }
    public void ac5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.ac5); }
    public void mr15(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.mr15); }
    public void 경험치10(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.경험치10); }
    public void 모든속성저항20(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.모든속성저항20); }
    public void 힘3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.힘3); }
    public void 덱3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.덱3); }
    public void 콘3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.콘3); }
    public void 위즈3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.위즈3); }
    public void 인트3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.인트3); }
    public void pvp대미지7(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.pvp대미지7); }
    public void pvp감소7(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.pvp감소7); }
    public void 근거리대미지5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.근거리대미지5); }
    public void 원거리대미지5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.원거리대미지5); }
    public void 근거리명중5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.근거리명중5); }
    public void 원거리명중5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.원거리명중5); }
    public void sp5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.sp5); }
    public void 마법적중5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.마법적중5); }
    public void 기술내성8(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.기술내성8); }
    public void 정령내성8(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.정령내성8); }
    public void 용언내성3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.용언내성3); }
    public void 공포내성3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.공포내성3); }
    public void 모든내성3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.모든내성3); }
    public void 정령적중5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.정령적중5); }
    public void 용언적중5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.용언적중5); }
    public void 공포적중5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.공포적중5); }
    public void 기술적중5(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.기술적중5); }
    public void 모든적중3(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.모든적중3); }
    public void 경험치보너스20(boolean isOn) { PcPotentialEffects.apply(this, PcPotentialEffects.Kind.경험치보너스20); }
    public void 일루션리치(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.일루션리치); }
    public void 일루션골렘(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.일루션골렘); }
    public void 솔리드캐리지(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.솔리드캐리지); }
    public void 버닝(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.버닝); }
    public void 이베이젼(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.이베이젼); } 
    public void 에테리얼(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.에테리얼); } 
    public void 비욘드(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.비욘드); }
    public void 혈맹버프AC(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프AC); }
    public void 혈맹버프마방(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프마방); }
    public void 혈맹버프PVP추가(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프PVP추가); }
    public void 혈맹버프PVP감소(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프PVP감소); }
    public void 혈맹버프HP증가(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프HP증가); }
    public void 혈맹버프올내성(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프올내성); }
    public void 혈맹버프올적중(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프올적중); }
    public void 혈맹버프올스텟(boolean isOn) { PcClanBuffEffects.apply(this, PcClanBuffEffects.Kind.혈맹버프올스텟); }
    public void 어드밴스(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.어드밴스); }
    public void 게일샷(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.게일샷); }
    public void 프라이드(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.프라이드); }
    public void 세인트(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.세인트); }
    public void 레이징포스(boolean isOn) { PcPassiveEffects.apply(this, PcPassiveEffects.Kind.레이징포스); }
	/** 캐릭별 추가데미지, 추가리덕션, 확률 **/
	private int _AddDamage = 0;
	private int _AddDamageRate = 0;
	private int _AddReduction = 0;
	private int _AddReductionRate = 0;

	public int getAddDamage() {
		return _AddDamage;
	}

	public void setAddDamage(int addDamage) {
		_AddDamage = addDamage;
	}

	public int getAddDamageRate() {
		return _AddDamageRate;
	}

	public void setAddDamageRate(int addDamageRate) {
		_AddDamageRate = addDamageRate;
	}

	public int getAddReduction() {
		return _AddReduction;
	}

	public void setAddReduction(int addReduction) {
		_AddReduction = addReduction;
	}

	public int getAddReductionRate() {
		return _AddReductionRate;
	}

	public void setAddReductionRate(int addReductionRate) {
		_AddReductionRate = addReductionRate;
	}

	/** 캐릭별 추가데미지, 추가리덕션, 확률 **/

	private int _ubscore;

	public int getUbScore() {
		return _ubscore;
	}

	public void setUbScore(int i) {
		_ubscore = i;
	}
	/** 브레이브 아바타 **/
	private boolean _Pbavatar = false;

	public boolean getPbavatar() {
		return _Pbavatar;
	}

	public void setPbavatar(boolean Pbavatar) {
		_Pbavatar = Pbavatar;
	}

	private boolean _Pbavataron = false;

	public boolean getPbavataron() {
		return _Pbavataron;
	}

	public void setPbavataron(boolean Pbavataron) {
		_Pbavataron = Pbavataron;
	}

	private int _Pbacount = 0;

	public int getPbacount() {
		return _Pbacount;
	}

	public void setPbacount(int i) {
		_Pbacount = i;
	}

	/** 브레이브아바타 **/

	public L1ItemInstance _fishingRod = null;
	private L1ClassFeature _classFeature = null;
	private L1EquipmentSlot _equipSlot;
	private String _accountName;
	private int _classId;
	private int _type;
	private int _exp;
	private int _age;
	/** 나이설정 **/
	private short _accessLevel;
	/**인형**/
	private int _dollskill;
	/**마법 치명**/
	private int _magicadd;

	
	private short _baseMaxHp = 0;
	private int _baseMaxMp = 0;
	private int _baseAc = 0;
	private int _originalMagicHit = 0;
	private int _baseBowDmgup = 0;
	private int _baseDmgup = 0;
	private int _baseHitup = 0;
	private int _baseBowHitup = 0;

    // Level/class snapshot; STR/DEX/INT table values remain in CalcStat.
    private final PcBaseAbilityCache _pcBaseAbilityCache = new PcBaseAbilityCache();

    public PcBaseAbilityCache.BaseAbility getBaseAbility() {
        _pcBaseAbilityCache.refresh(this);
        return _pcBaseAbilityCache.get();
    }

	private int _baseMagicHitup = 0; // 베이스 스탯에 의한 마법 명중
	private int _baseMagicCritical = 0; // 베이스 스탯에 의한 마법 치명타(%)
	private int _baseMagicDmg = 0; // 베이스 스탯에 의한 마법 대미지
	private int _baseMagicDecreaseMp = 0; // 베이스 스탯에 의한 마법 대미지

	private int _DmgupByArmor = 0; // 방어용 기구에 의한 근접무기 추타율
	private int _bowDmgupByArmor = 0; // 방어용 기구에 의한 활의 추타율

	private int _PKcount;
	public int _fishingX = 0;
	public int _fishingY = 0;
	private int _clanid;
	private int _redKnightClanId;
	private int _redKnightDamage;
	private int _redKnightKill;
	private String clanname;
	private int _clanRank;
	private byte _sex;
	private int _returnstat;
	private short _hpr = 0;
	private short _trueHpr = 0;
	private short _mpr = 0;
	private short _trueMpr = 0;

	private int _advenHp;
	private int _advenMp;
	private int _magicBuffHp;
	private int _magicBuffMp;
	private int _magicBuffMR;
	private int _magicBuffSP;
	private int _magicBuffDG;
	private int _magicBuffER;
	private int _highLevel;
	private int _bonusStats;
	public boolean isInFantasy = false; // 몽섬리뉴얼
	public boolean _is특정보스 = false;
	public boolean _is특정보스투 = false;
	/** 화룡의 안식처 **/
	public boolean isInValakasBoss = false;
	public boolean isInValakas = false;
	private boolean _ghost = false;
	private boolean _ghostCanTalk = true;
	private boolean _isReserveGhost = false;
	private boolean _isShowTradeChat = true;
	private boolean _isCanWhisper = true;
	private boolean _isFishing = false;
	private boolean _isFishingReady = false;
	private boolean isDeathMatch = false; // 데스매치
	private boolean _isSupporting = false;
	private boolean _isShowWorldChat = true;
	private boolean _gm;
	private boolean _monitor;
	private boolean _gmInvis;
	// private boolean _isTeleport = false;
	private boolean _isDrink = false;
	private boolean _isGres = false;
	private boolean _isPinkName = false;
	private boolean _isAutofish = false;
	private boolean _banned;
	private boolean _isAutoClanjoin = false;
	private boolean _gresValid;
	private volatile boolean _tradeOk;
    // Covers the short interval between a durable item transfer's prepare
    // and the CIO owner's post-COMMIT replay. Does NOT wait for JDBC.
    private volatile int itemTransferBusy;
    private static final AtomicIntegerFieldUpdater<L1PcInstance> ITEM_TRANSFER_BUSY =
            AtomicIntegerFieldUpdater.newUpdater(L1PcInstance.class, "itemTransferBusy");

    public boolean tryBeginItemTransfer() {
        return ITEM_TRANSFER_BUSY.compareAndSet(this, 0, 1);
    }

    public void endItemTransfer() {
        ITEM_TRANSFER_BUSY.set(this, 0);
    }

    public boolean isItemTransferBusy() {
        return itemTransferBusy != 0;
    }

    // One selected warehouse list at a time. CacheD still owns each durable
    // item transfer; this gate only prevents a second list from interleaving
    // between the asynchronous COMMIT callbacks of the first list.
    private volatile Object warehouseBatchOwner;
    private static final java.util.concurrent.atomic.AtomicReferenceFieldUpdater<L1PcInstance, Object>
            WAREHOUSE_BATCH_OWNER = java.util.concurrent.atomic.AtomicReferenceFieldUpdater
                    .newUpdater(L1PcInstance.class, Object.class, "warehouseBatchOwner");

    public boolean tryBeginWarehouseBatch(Object owner) {
        return owner != null && WAREHOUSE_BATCH_OWNER.compareAndSet(this, null, owner);
    }

    public boolean isWarehouseBatchActive() {
        return warehouseBatchOwner != null;
    }

    public boolean isWarehouseBatchOwner(Object owner) {
        return owner != null && warehouseBatchOwner == owner;
    }

    public void endWarehouseBatch(Object owner) {
        if (owner != null) WAREHOUSE_BATCH_OWNER.compareAndSet(this, owner, null);
    }

	private volatile int tradeResolutionBusy;
	private static final AtomicIntegerFieldUpdater<L1PcInstance> TRADE_RESOLUTION =
			AtomicIntegerFieldUpdater.newUpdater(L1PcInstance.class, "tradeResolutionBusy");

	public boolean tryBeginTradeResolution() {
		return TRADE_RESOLUTION.compareAndSet(this, 0, 1);
	}

	public void endTradeResolution() {
		TRADE_RESOLUTION.set(this, 0);
	}

    public boolean isTradeResolving() {
        return tradeResolutionBusy != 0;
    }
	public int tt_clanid = -1;
	public int tt_partyid = -1;
	public int tt_level = 0;
	public boolean 물품멘트 = false;
	public boolean 퀘스트멘트 = false;
	public boolean RootMent = true;// 루팅 멘트]
	public boolean RootMent1 = true;// 루팅 멘트]
	public boolean noPlayerck2 = false;
	public boolean noPlayerCK = false;
	public boolean noPlayerRobot = false;

	private int _ghostSaveLocX = 0;
	private int _ghostSaveLocY = 0;
	private short _ghostSaveMapId = 0;
	 private int _ghostSaveHeading = 0;

	private Timestamp _lastPk;
	private Timestamp _deleteTime;
	private Timestamp _lastLoginTime;
	
	private int _weightReduction = 0;
	private int _hasteItemEquipped = 0;
	private int _damageReductionByArmor = 0;
	private int _damageReductionIgnore = 0;
	private int _DamageReduction = 0;
	private int _DmgRate = 0; // 방어용 기구에 의한 근접무기 추타율
	private int _HitRate = 0; // 방어용 기구에 의한 근접무기 명중율
	private int _bowHitRate = 0; // 방어용 기구에 의한 활의 명중율
	private int _bowDmgRate = 0; // 방어용 기구에 의한 활의 추타율
	
	private int _tempCharGfxAtDead;
	private int _fightId;
	private byte _chatCount = 0;
	private long _oldChatTimeInMillis = 0L;

	private int _elixirStats;
	private int _elfAttr;
	private int _expRes;

	private int _onlineStatus;
	private int _homeTownId;
	private int _contribution;
	private int _food;
	private int _partnerId;
	private long _fishingTime = 0;
	private int _dessertId = 0;
	private int _callClanId;
	private int _callClanHeading;
	public int changeNpcId5;
	private boolean isChangeItem5;
	private L1ItemInstance defaultItem5;
	public int changeNpcId1;
	private boolean isChangeItem1;
	private L1ItemInstance defaultItem1;
	public int changeNpcId2;
	private boolean isChangeItem2;
	private L1ItemInstance defaultItem2;
	public int changeNpcId3;
	private boolean isChangeItem3;
	private L1ItemInstance defaultItem3;
	public int changeNpcId4;
	private boolean isChangeItem4;
	private L1ItemInstance defaultItem4;
	public int changeNpcId6;
	private boolean isChangeItem6;
	private L1ItemInstance defaultItem6;
	public int changeNpcId7;
	private boolean isChangeItem7;
	private L1ItemInstance defaultItem7;
	public int changeNpcId8;
	private boolean isChangeItem8;
	private L1ItemInstance defaultItem8;
	public int changeNpcId9;
	private boolean isChangeItem9;
	private L1ItemInstance defaultItem9;
	public int changeNpcId10;
	private boolean isChangeItem10;
	private L1ItemInstance defaultItem10;
	public int changeNpcId11;
	private boolean isChangeItem11;
	private L1ItemInstance defaultItem11;
	public int changeNpcId12;
	private boolean isChangeItem12;
	private L1ItemInstance defaultItem12;
	public int changeNpcId13;
	private boolean isChangeItem13;
	private L1ItemInstance defaultItem13;
	public int changeNpcId14;
	private boolean isChangeItem14;
	private L1ItemInstance defaultItem14;
	public int changeNpcId15;
	private boolean isChangeItem15;
	private L1ItemInstance defaultItem15;
	public int changeNpcId16;
	private boolean isChangeItem16;
	private L1ItemInstance defaultItem16;
	public int changeNpcId17;
	private boolean isChangeItem17;
	private L1ItemInstance defaultItem17;
	public int changeNpcId18;
	private boolean isChangeItem18;
	private L1ItemInstance defaultItem18;
	public int changeNpcId19;
	private boolean isChangeItem19;
	private L1ItemInstance defaultItem19;
	public int changeNpcId20;
	private boolean isChangeItem20;
	private L1ItemInstance defaultItem20;
	private boolean isChangeItem;
	public int changeNpcId;
	private L1ItemInstance defaultItem;
	private int _currentWeapon; // 로봇 관련
	private final L1Karma _karma = new L1Karma();
	private final L1PcInventory _inventory;
	private final L1DwarfForPackageInventory _dwarfForPackage;
	private final L1Inventory _tradewindow;

	private L1ItemInstance _weapon;
	private L1ItemInstance _secondweapon;
	private L1ItemInstance _armor;
	private L1Party _party;
	private L1ChatParty _chatParty;

	private int _cookingId = 0;
	private int _partyID;
	private int _partyType;
	private volatile int _tradeID;
	private int _tempID;

	private L1Quest _quest;


	private boolean _isPrivateShop = false;
	private boolean _isPrivateReady = false;
	private int _partnersPrivateShopItemCount = 0;

	private long _lastPasswordChangeTime;
	private long _lastLocalTellTime;

	private boolean 무인상점 = false;

	boolean isExpDrop;
	boolean isItemDrop;

	public final ArrayList<L1BookMark> _speedbookmarks;

	public L1BookMark[] getBookMarkArray() {
		return _bookmarks.toArray(new L1BookMark[_bookmarks.size()]);
	}

	public L1BookMark[] getSpeedBookMarkArray() {
		return _speedbookmarks.toArray(new L1BookMark[_speedbookmarks.size()]);
	}

	private int _markcount;
	private int bug_check;
	private int kir_time;
	private int level_q;
	
	//컬렉션
	private int col_y;
	private int col_m;
	private int col_l;
	private int col_h;
	
	public void setBug_check(int check) {
		bug_check = check;
	}
	public int getLevel_q() {
		return level_q;
	}
	public void setLevel_q(int leq) {
		level_q = leq;
	}
	public int getKir_time() {
		return kir_time;
	}
	public void setKir_time(int kir) {
		kir_time = kir;
	}
	//컬렉션
	public int getBcol_y() {
		return col_y;
	}
	public void setBcol_y(int coly) {
		col_y = coly;
	}
	public int getBcol_m() {
		return col_m;
	}
	public void setBcol_m(int colm) {
		col_m = colm;
	}
	public int getBcol_l() {
		return col_l;
	}
	public void setBcol_l(int coll) {
		col_l = coll;
	}
	public int getBcol_h() {
		return col_h;
	}
	public void setBcol_h(int colh) {
		col_h = colh;
	}
	
	//
	public int getBug_check() {
		return bug_check;
	}
	public void setMark_count(int i) {
		_markcount = i;
	}

	public int getMark_count() {
		return _markcount;
	}

	public boolean is무인상점() {
		return 무인상점;
	}

	public void set무인상점(boolean c) {
		무인상점 = c;
	}

	/** 혈맹버프 **/
	private boolean _clanbuff = false;

	public boolean isClanBuff() {
		return _clanbuff;
	}

	public void setClanBuff(boolean c) {
		_clanbuff = c;
	}

	/** 혈맹버프 **/

	// 생존의외침
	public int _getLive = 0;

	public int getLive() {
		return _getLive;
	}

	public void addLive(int Live) {
		_getLive += Live;
	}

	public void setLive(int Live) {
		_getLive = Live;
	}

	// 조우의 불골렘
	public int[] FireGolem = new int[18];
	public int[] FireEnchant = new int[18];

	// 피어스
	public int[] PiersItemId = new int[19];
	public int[] PiersEnchant = new int[19];

	private boolean _텔대기 = false;
	private boolean _isHomnam = false;
	private boolean _isBosMon = false;
	private boolean _isBosMon1 = false;
	private boolean _isElrzabe = false;
	private boolean _isSandWarm = false;
	private boolean _is드레이크 = false;
	private boolean _is제로스 = false;
	private boolean _is기르타스 = false;
	private boolean _is발라카스 = false;
	private boolean _is파푸리온 = false;
	private boolean _is린드비오르 = false;
	private boolean _is안타라스 = false;
	private boolean _isNCoinMon = false;

	private boolean _magicitem = false;
	private int _magicitemid;

	// 3.63아이템패킷추가
	public boolean isDanteasBuff = false;

	public boolean isGotobokBuff = false;

	public boolean 서버다운중 = false;
	public boolean 바포방 = false;
	// 젠도르 제작 관련
	public int _getCount;

	private long _npcaction;

	public final ArrayList<L1BookMark> _bookmarks;
	private byte[] _shopChat;
	private AtomicInteger _pinkNameTime;
	private CIOClient _netConnection;
	private static Logger _log = Logger.getLogger(L1PcInstance.class.getName());

	public L1PcInstance() {
		_creatureController = new CCreatureController(this);
		_regenController = new CRegenController(this);
		_accessLevel = 0;
		_currentWeapon = 0;
		_inventory = new L1PcInventory(this);
		_dwarfForPackage = new L1DwarfForPackageInventory(this);
		_tradewindow = new L1Inventory();
		_bookmarks = new ArrayList<L1BookMark>();
		_speedbookmarks = new ArrayList<L1BookMark>();
		_quest = new L1Quest(this);
		_equipSlot = new L1EquipmentSlot(this);
		_pinkNameTime = new AtomicInteger(0);
		changeNpcId5 = 88001;
		isChangeItem5 = false;
		changeNpcId1 = 88002;
		isChangeItem1 = false;
		changeNpcId2 = 88003;
		isChangeItem2 = false;
		changeNpcId3 = 88004;
		isChangeItem3 = false;
		changeNpcId4 = 88005;
		isChangeItem4 = false;
		changeNpcId6 = 88006;
		isChangeItem6 = false;
		changeNpcId7 = 88007;
		isChangeItem7 = false;
		changeNpcId8 = 88008;
		isChangeItem8 = false;
		changeNpcId9 = 88009;
		isChangeItem9 = false;
		changeNpcId10 = 88010;
		isChangeItem10 = false;
		changeNpcId11 = 88011;
		isChangeItem11 = false;
		changeNpcId12 = 88012;
		isChangeItem12 = false;
		changeNpcId13 = 88013;
		isChangeItem13 = false;
		changeNpcId14 = 88014;
		isChangeItem14 = false;
		changeNpcId15 = 88015;
		isChangeItem15 = false;
		changeNpcId16 = 88016;
		isChangeItem16 = false;
		changeNpcId17 = 88017;
		isChangeItem17 = false;
		changeNpcId18 = 88018;
		isChangeItem18 = false;
		changeNpcId19 = 88019;
		isChangeItem19 = false;
		changeNpcId20 = 88020;
		isChangeItem20 = false;
		isChangeItem = false;
		changeNpcId = 88021;
	}

	public boolean getIsChangeItem5() {
		return isChangeItem5;
	}

	public void setIsChangeItem5(boolean a) {
		isChangeItem5 = a;
	}

	public void setDefaultItem5(L1ItemInstance i) {
		defaultItem5 = i;
	}

	public L1ItemInstance getDefaultItem5() {
		return defaultItem5;

	}

	public boolean getIsChangeItem1() {
		return isChangeItem1;
	}

	public void setIsChangeItem1(boolean a) {
		isChangeItem1 = a;
	}

	public void setDefaultItem1(L1ItemInstance i) {
		defaultItem1 = i;
	}

	public L1ItemInstance getDefaultItem1() {
		return defaultItem1;
	}

	public boolean getIsChangeItem2() {
		return isChangeItem2;
	}

	public void setIsChangeItem2(boolean a) {
		isChangeItem2 = a;
	}

	public void setDefaultItem2(L1ItemInstance i) {
		defaultItem2 = i;
	}

	public L1ItemInstance getDefaultItem2() {
		return defaultItem2;
	}

	public boolean getIsChangeItem3() {
		return isChangeItem3;
	}

	public void setIsChangeItem3(boolean a) {
		isChangeItem3 = a;
	}

	public void setDefaultItem3(L1ItemInstance i) {
		defaultItem3 = i;
	}

	public L1ItemInstance getDefaultItem3() {
		return defaultItem3;
	}

	public boolean getIsChangeItem4() {
		return isChangeItem4;
	}

	public void setIsChangeItem4(boolean a) {
		isChangeItem4 = a;
	}

	public void setDefaultItem4(L1ItemInstance i) {
		defaultItem4 = i;
	}

	public L1ItemInstance getDefaultItem4() {
		return defaultItem4;
	}

	public boolean getIsChangeItem6() {
		return isChangeItem6;
	}

	public void setIsChangeItem6(boolean a) {
		isChangeItem6 = a;
	}

	public void setDefaultItem6(L1ItemInstance i) {
		defaultItem6 = i;
	}

	public L1ItemInstance getDefaultItem6() {
		return defaultItem6;
	}

	public boolean getIsChangeItem7() {
		return isChangeItem7;
	}

	public void setIsChangeItem7(boolean a) {
		isChangeItem7 = a;
	}

	public void setDefaultItem7(L1ItemInstance i) {
		defaultItem7 = i;
	}

	public L1ItemInstance getDefaultItem7() {
		return defaultItem7;
	}

	public boolean getIsChangeItem8() {
		return isChangeItem8;
	}

	public void setIsChangeItem8(boolean a) {
		isChangeItem8 = a;
	}

	public void setDefaultItem8(L1ItemInstance i) {
		defaultItem8 = i;
	}

	public L1ItemInstance getDefaultItem8() {
		return defaultItem8;
	}

	public boolean getIsChangeItem9() {
		return isChangeItem9;
	}

	public void setIsChangeItem9(boolean a) {
		isChangeItem9 = a;
	}

	public void setDefaultItem9(L1ItemInstance i) {
		defaultItem9 = i;
	}

	public L1ItemInstance getDefaultItem9() {
		return defaultItem9;
	}

	public boolean getIsChangeItem10() {
		return isChangeItem10;
	}

	public void setIsChangeItem10(boolean a) {
		isChangeItem10 = a;
	}

	public void setDefaultItem10(L1ItemInstance i) {
		defaultItem10 = i;
	}

	public L1ItemInstance getDefaultItem10() {
		return defaultItem10;
	}

	public boolean getIsChangeItem11() {
		return isChangeItem11;
	}

	public void setIsChangeItem11(boolean a) {
		isChangeItem11 = a;
	}

	public void setDefaultItem11(L1ItemInstance i) {
		defaultItem11 = i;
	}

	public L1ItemInstance getDefaultItem11() {
		return defaultItem11;
	}

	public boolean getIsChangeItem12() {
		return isChangeItem12;
	}

	public void setIsChangeItem12(boolean a) {
		isChangeItem12 = a;
	}

	public void setDefaultItem12(L1ItemInstance i) {
		defaultItem12 = i;
	}

	public L1ItemInstance getDefaultItem12() {
		return defaultItem12;
	}

	public boolean getIsChangeItem13() {
		return isChangeItem13;
	}

	public void setIsChangeItem13(boolean a) {
		isChangeItem13 = a;
	}

	public void setDefaultItem13(L1ItemInstance i) {
		defaultItem13 = i;
	}

	public L1ItemInstance getDefaultItem13() {
		return defaultItem13;
	}

	public boolean getIsChangeItem14() {
		return isChangeItem14;
	}

	public void setIsChangeItem14(boolean a) {
		isChangeItem14 = a;
	}

	public void setDefaultItem14(L1ItemInstance i) {
		defaultItem14 = i;
	}

	public L1ItemInstance getDefaultItem14() {
		return defaultItem14;
	}

	public boolean getIsChangeItem15() {
		return isChangeItem15;
	}

	public void setIsChangeItem15(boolean a) {
		isChangeItem15 = a;
	}

	public void setDefaultItem15(L1ItemInstance i) {
		defaultItem15 = i;
	}

	public L1ItemInstance getDefaultItem15() {
		return defaultItem15;
	}

	public boolean getIsChangeItem16() {
		return isChangeItem16;
	}

	public void setIsChangeItem16(boolean a) {
		isChangeItem16 = a;
	}

	public void setDefaultItem16(L1ItemInstance i) {
		defaultItem16 = i;
	}

	public L1ItemInstance getDefaultItem16() {
		return defaultItem16;
	}

	public boolean getIsChangeItem17() {
		return isChangeItem17;
	}

	public void setIsChangeItem17(boolean a) {
		isChangeItem17 = a;
	}

	public void setDefaultItem17(L1ItemInstance i) {
		defaultItem17 = i;
	}

	public L1ItemInstance getDefaultItem17() {
		return defaultItem17;
	}

	public boolean getIsChangeItem18() {
		return isChangeItem18;
	}

	public void setIsChangeItem18(boolean a) {
		isChangeItem18 = a;
	}

	public void setDefaultItem18(L1ItemInstance i) {
		defaultItem18 = i;
	}

	public L1ItemInstance getDefaultItem18() {
		return defaultItem18;
	}

	public boolean getIsChangeItem19() {
		return isChangeItem19;
	}

	public void setIsChangeItem19(boolean a) {
		isChangeItem19 = a;
	}

	public void setDefaultItem19(L1ItemInstance i) {
		defaultItem19 = i;
	}

	public L1ItemInstance getDefaultItem19() {
		return defaultItem19;
	}

	public boolean getIsChangeItem20() {
		return isChangeItem20;
	}

	public void setIsChangeItem20(boolean a) {
		isChangeItem20 = a;
	}

	public void setDefaultItem20(L1ItemInstance i) {
		defaultItem20 = i;
	}

	public L1ItemInstance getDefaultItem20() {
		return defaultItem20;
	}

	public boolean getIsChangeItem() {
		return isChangeItem;
	}

	public void setIsChangeItem(boolean a) {
		isChangeItem = a;
	}

	public void setDefaultItem(L1ItemInstance i) {
		defaultItem3 = i;
	}

	public L1ItemInstance getDefaultItem() {
		return defaultItem;
	}

	public long get_lastPasswordChangeTime() {
		return _lastPasswordChangeTime;
	}

	public long get_lastLocalTellTime() {
		return _lastLocalTellTime;
	}

	public void update_lastPasswordChangeTime() {
		_lastPasswordChangeTime = System.currentTimeMillis();
	}

	public void update_lastLocalTellTime() {
		_lastLocalTellTime = System.currentTimeMillis();
	}

	private long _lastShellUseTime;

	public long getlastShellUseTime() {
		return _lastShellUseTime;
	}

	public void updatelastShellUseTime() {
		_lastShellUseTime = System.currentTimeMillis();
	}
	
	private transient ScheduledFuture<?> _pinkNameFuture;
	public ScheduledFuture<?> getPinkNameFuture() {
	    return _pinkNameFuture;
	}
	public void setPinkNameFuture(ScheduledFuture<?> f) {
	    _pinkNameFuture = f;
	}

	public int getPinkNameTime() {
		return _pinkNameTime.get();
	}

	public int DecrementPinkNameTime() {
		return _pinkNameTime.decrementAndGet();
	}

	public int SetPinkNameTime(int timeValue) {
		return _pinkNameTime.getAndSet(timeValue);
	}

	public void setSkillMastery(int skillid) {
        lockUserState();
        try {
            lockMastery();
            try {
                if (!skillList.contains(skillid)) skillList.add(skillid);
            } finally {
                unlockMastery();
            }
        } finally {
            unlockUserState();
        }
	}

	public void removeSkillMastery(int skillid) {
        lockUserState();
        try {
            lockMastery();
            try {
                skillList.remove((Object) skillid);
            } finally {
                unlockMastery();
            }
        } finally {
            unlockUserState();
        }
	}

	public boolean isSkillMastery(int skillid) {
        lockMastery();
        try {
            return skillList.contains(skillid);
        } finally {
            unlockMastery();
        }
	}

	public short getHpr() {
		return _hpr;
	}

	public void addHpr(int i) {
		_trueHpr += i;
		_hpr = (short) Math.max(0, _trueHpr);
	}

	public short getMpr() {
		return _mpr;
	}

	public void addMpr(int i) {
		_trueMpr += i;
		_mpr = (short) Math.max(0, _trueMpr);
	}

	public void setHomnam(boolean flag) {
		_isHomnam = flag;
	}

	public boolean isHomnam() {
		return _isHomnam;
	}

	public void set특정보스(boolean flag) {
		_is특정보스 = flag;
	}

	public boolean is특정보스() {
		return _is특정보스;
	}

	public void set특정보스투(boolean flag) {
		_is특정보스투 = flag;
	}

	public boolean is특정보스투() {
		return _is특정보스투;
	}
	
	public boolean _is공성보상 = false; {
	}

	public void set공성보상(boolean flag) {
		_is공성보상 = flag;
	}

	public boolean is공성보상() {
		return _is공성보상;
	}

	public void setBosMon(boolean flag) {
		_isBosMon = flag;
	}

	public boolean isBosMon() {
		return _isBosMon;
	}

	public void setBosMon1(boolean flag) {
		_isBosMon1 = flag;
	}

	public boolean isBosMon1() {
		return _isBosMon1;
	}

	public void setElrzabe(boolean flag) {
		_isElrzabe = flag;
	}

	public boolean isElrzabe() {
		return _isElrzabe;
	}

	public void setSandWarm(boolean flag) {
		_isSandWarm = flag;
	}

	public boolean isSandWarm() {
		return _isSandWarm;
	}

	public void set드레이크(boolean flag) {
		_is드레이크 = flag;
	}

	public boolean is드레이크() {
		return _is드레이크;
	}

	public void set제로스(boolean flag) {
		_is제로스 = flag;
	}

	public boolean is제로스() {
		return _is제로스;
	}


	public boolean isNCoinMon() {
		return _isNCoinMon;
	}

	public void setNCoinMon(boolean flag) {
		_isNCoinMon = flag;
	}

	public void set기르타스(boolean flag) {
		_is기르타스 = flag;
	}

	public boolean is기르타스() {
		return _is기르타스;
	}

	public void set발라카스(boolean flag) {
		_is발라카스 = flag;
	}

	public boolean is발라카스() {
		return _is발라카스;
	}

	public void set파푸리온(boolean flag) {
		_is파푸리온 = flag;
	}

	public boolean is파푸리온() {
		return _is파푸리온;
	}

	public void set린드비오르(boolean flag) {
		_is린드비오르 = flag;
	}

	public boolean is린드비오르() {
		return _is린드비오르;
	}

	public void set안타라스(boolean flag) {
		_is안타라스 = flag;
	}

	public boolean is안타라스() {
		return _is안타라스;
	}

	public long getNpcActionTime() {
		return _npcaction;
	}

	public void setNpcActionTime(long flag) {
		_npcaction = flag;
	}

	public boolean isMagicItem() {
		return _magicitem;
	}

	public void setMagicItem(boolean flag) {
		_magicitem = flag;
	}

	public int getMagicItemId() {
		return _magicitemid;
	}

	public void setMagicItemId(int itemid) {
		_magicitemid = itemid;
	}

	/** 피엠틱 컨트롤러 **/

	public void 포커스3단() {	
		if (hasSkillEffect(L1SkillId.포커스3단)){
		inc_focus_wave_level();
		SC_SkillSound.send(this, getId(), 16533);
		SC_SkillSound.broadcast(this, getId(), 16533);
		SC_SkillBrave.send(this, getId(), 12, getSkillEffectTimeSec(L1SkillId.FOCUS_WAVE));
		SC_SkillBrave.broadcast(this, getId(), 12, 0);
		
		}
}
	public void 레벨버프() {
		if (this.getLevel() == 90) {
			this.addMaxHp(100);
			this.getResistance().addPVPweaponTotalDamage(1);
			SC_SystemMessage.send(this, "\\aH[90레벨버프] : HP+100, PVP데미지+1");
			SC_InventoryIcon.iconNewUnLimit(this, L1SkillId.lv90, 2242, true);
		} else if (this.getLevel() == 91) { 
			SC_InventoryIcon.icoEnd(this, L1SkillId.lv90);
			this.addMaxHp(150);
			this.getResistance().addPVPweaponTotalDamage(2);
			SC_SystemMessage.send(this, "\\aH[91레벨버프] : HP+150, PVP데미지+2");
			SC_InventoryIcon.iconNewUnLimit(this, L1SkillId.lv91, 2243, true);
		} else if (this.getLevel() == 92) {
			SC_InventoryIcon.icoEnd(this, L1SkillId.lv91);
			this.addMaxHp(200);
			this.getResistance().addPVPweaponTotalDamage(3);
			SC_SystemMessage.send(this, "\\aH[92레벨버프] : HP+200, PVP데미지+3");		
			SC_InventoryIcon.iconNewUnLimit(this, L1SkillId.lv92, 2244, true);
		} else if (this.getLevel() >= 93) {
			SC_InventoryIcon.icoEnd(this, L1SkillId.lv92);
			this.addMaxHp(300);
			this.getResistance().addPVPweaponTotalDamage(5);
			SC_SystemMessage.send(this, "\\aH[93레벨버프] : HP+300, PVP데미지+5");
			SC_InventoryIcon.iconNewUnLimit(this, L1SkillId.lv93, 2245, true);
		}

		SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
		SC_MpUpdate.send(this, getCurrentMp(), getMaxMp());
		SC_SPMR.send(this);
		SC_OwnCharAttrDef.send(this);
	
	}
	
	public void 저지먼트1() {
		if (hasSkillEffect(L1SkillId.잠재력저지먼트1)){
			getResistance().addSpecialResistance(eKind.ABILITY, -15);
			SC_InventoryIcon.icoNew(this, L1SkillId.저지먼트, 3881, 8, true);
			SC_OwnCharAttrDef.send(this);
		}
	}
	public void 저지먼트2() {
		if (hasSkillEffect(L1SkillId.잠재력저지먼트2)){
			getResistance().addSpecialResistance(eKind.SPIRIT, -15);
			SC_InventoryIcon.icoNew(this, L1SkillId.저지먼트, 3882, 8, true);
			SC_OwnCharAttrDef.send(this);
		}
	}
	public void 저지먼트3() {
		if (hasSkillEffect(L1SkillId.잠재력저지먼트3)){
			getResistance().addSpecialResistance(eKind.DRAGON_SPELL, -15);
			SC_InventoryIcon.icoNew(this, L1SkillId.저지먼트, 3883, 8, true);
			SC_OwnCharAttrDef.send(this);
		}
	}
	public void 저지먼트4() {
		if (hasSkillEffect(L1SkillId.잠재력저지먼트4)){
			getResistance().addSpecialResistance(eKind.FEAR, -15);
			SC_InventoryIcon.icoNew(this, L1SkillId.저지먼트, 3887, 8, true);
			SC_OwnCharAttrDef.send(this);
		}
	}
	public void 저지먼트5() {
		if (hasSkillEffect(L1SkillId.잠재력저지먼트5)){
			getResistance().addMr(-15);
			SC_InventoryIcon.icoNew(this, L1SkillId.저지먼트, 3884, 8, true);
			SC_SPMR.send(this);
			
		}
	}

	public void startObjectAutoUpdate() { 
		PremiumTimeController.getInstance().register(this);
		CharTamScheduler.getInstance().onEnterWorld(this);
		PcInventoryExpiryScheduler.getInstance().register(this);
		AutoSaveChaQuitCheckScheduler.getInstance().register(this);
		nextSessionStamp();
		CIOThreadCallback.getInstance().clearPcState(this);
		CIOThreadCallback.getInstance().registerPc(this);
	}

	public void stopEquipmentTimer() {
		List<L1ItemInstance> allItems = this.getInventory().getItems();
		for (L1ItemInstance item : allItems) {
			if (item == null)
				continue;
			if (item.isEquipped() && item.getRemainingTime() > 0) {
				item.stopEquipmentTimer(this);
			}
		}
	}

	public void onChangeExp() {
		int level = ExpTable.getLevelByExp(getExp());
		int char_level = getLevel();
		int gap = level - char_level;
		if (gap == 0) {
			SC_OwnCharStatus.send(this);
			int percent = ExpTable.getExpPercentage(char_level, getExp());
			if (char_level >= 60 && char_level <= 64) {
				if (percent >= 10)
					removeSkillEffect(L1SkillId.레벨업보너스);
			} else if (char_level >= 65) {
				if (percent >= 5) {
					removeSkillEffect(L1SkillId.레벨업보너스);
				}
			}
			return;
		}

		if (gap > 0) {
			levelUp(gap);
			if (getLevel() >= 60) {
				setSkillEffect(L1SkillId.레벨업보너스, 10800000);
				SC_PacketBox.send(this, 10800, true, true);
			}
			RenewStat();
		} else if (gap < 0) {
			levelDown(gap);
			removeSkillEffect(L1SkillId.레벨업보너스);
		}
	}
	
	@Override
	public void onPerceive(L1PcInstance pc) {
	    if (isGmInvis() /* || isGhost() || isInvisble() */) {
	        return;
	    }

	    pc.addKnownObject(this);

	    if (pc.is_combat_field()) {

	        if (isGm() || pc.isGm()) {

	            SC_WorldPutObject.get(
	                    pc.getNetConnection(),
	                    this);

	        } else {

	            if (get_current_combat_team_id()
	                    == pc.get_current_combat_team_id()) {

	                SC_WorldPutObject.get(
	                        pc.getNetConnection(),
	                        this,
	                        "   \\aJ아군",
	                        true);

	            } else {

	                SC_WorldPutObject.get(
	                        pc.getNetConnection(),
	                        this,
	                        "   \\f3적군",
	                        false);
	            }
	        }

	    } else if (pc.getMapId() == 5554) {

	        if (isGm() || pc.isGm()) {

	            SC_WorldPutObject.get(
	                    pc.getNetConnection(),
	                    this);

	        } else {

	            SC_WorldPutObject.get(
	                    pc.getNetConnection(),
	                    this,
	                    "ID비공개",
	                    false);
	        }

	    } else {

	        if ((SC_WorldPutObject.IS_PRESENTATION_MARK
	                && pc.getMapId() >= 1708
	                && pc.getMapId() <= 1710)
	                || (pc.getMapId() >= 12852
	                        && pc.getMapId() <= 12862)
	                || pc.getMapId() == 521
	                || pc.getMapId() == 522
	                || pc.getMapId() == 400) {

	            if (pc.getClan() != null
	                    && getClan() != null
	                    && pc.getClan() == getClan()) {

	                SC_WorldPutObject.get(
	                        pc.getNetConnection(),
	                        this,
	                        true);

	            } else {

	                SC_WorldPutObject.get(
	                        pc.getNetConnection(),
	                        this,
	                        false);
	            }

	        } else {

	            SC_WorldPutObject.get(
	                    pc.getNetConnection(),
	                    this);
	        }
	    }

	    if (pc.isPinkName()) {
	        SC_PinkName.send(pc, pc.getId(), pc.getPinkNameTime());
	    }

	    if (isPinkName()) {
	        SC_PinkName.send(pc, getId(), getPinkNameTime());
	    }

	    if (isPrivateShop()) {
	        SC_DoActionShop.send(pc, getId(), ActionCodes.ACTION_Shop, getShopChat());
	    }

	    if (isFishing()) {
	        SC_Fishing.send(pc, getId(), ActionCodes.ACTION_Fishing, _fishingX, _fishingY);
	    }

	    if (isCrown()) {
	        L1Clan clan =
	                L1World.getInstance()
	                        .getClan(getClanid());

	        if (clan != null) {
	            if (getId() == clan.getLeaderId()
	                    && clan.getCastleId() != 0) {

	                SC_CastleMaster.send(pc, clan.getCastleId(), getId());
	            }
	        }
	    }

	    L1Party party = getParty();

	    if (party != null
	            && party.isMember(pc)) {

	        party.handshakePartyMemberStatus(
	                this,
	                pc);
	    }
	}

	public void broadcastRemoveAllKnownObjects() {
		for (L1Object known : getKnownObjects()) {
			if (known == null) {
				continue;
			}
			SC_RemoveObject.send(
					getNetConnection(),
					known);
		}
	}
	
	private void sendVisualEffect() {
		int poisonId = 0;
		if (getPoison() != null) {
			poisonId = getPoison().getEffectId();
		}
		if (getParalysis() != null) {
			poisonId = getParalysis().getEffectId();
		}
		if (poisonId != 0) {
			SC_Poison.send(this, getId(), poisonId);
			SC_Poison.broadcast(this, getId(), poisonId);
		}
	}

	public void sendClanMarks() {
		if (getClanid() != 0) {
			L1Clan clan = L1World.getInstance().getClan(getClanid());
			if (clan != null) {
				if (isCrown() && getId() == clan.getLeaderId() && clan.getCastleId() != 0) {
					SC_CastleMaster.send(this, clan.getCastleId(), getId());
				}
			}
		}
	}

	public void sendVisualEffectAtLogin() {
		sendVisualEffect();
	}

	public void sendVisualEffectAtTeleport() {
		if (isDrink()) {
			// sendPackets(new S_Liquor(getId()));
		}

		sendVisualEffect();
	}

	@Override
	public void setCurrentHp(int i) {
		if (getCurrentHp() == i)
			return;

		/** 2016.11.26 MJ 앱센터 LFC **/
		/* lfc중이고 hp가 깎였으면, */
		if ((getInstStatus() == InstStatus.INST_USERSTATUS_LFC) && i > getCurrentHp())
			addDamageFromLfc(i - getCurrentHp());

		super.setCurrentHp(i);
		SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
		if (isInParty()) {
			// TODO 파티 프로토
			getParty().refreshPartyMemberStatus(this);
		}
	}

	@Override
	public void setCurrentMp(int i) {
		if (getCurrentMp() == i)
			return;
		if (isGm())// 원본
			i = getMaxMp();

		// i = getMaxMp();//MP소비안댐 테스트용삭제요망

		super.setCurrentMp(i);
		SC_MpUpdate.send(this, getCurrentMp(), getMaxMp());
		if (isInParty()) {
			// TODO 파티 프로토
			getParty().refreshPartyMemberStatus(this);
		}
	}

	@Override
	public L1PcInventory getInventory() {
		return _inventory;
	}

	public L1DwarfForPackageInventory getDwarfForPackageInventory() {
		return _dwarfForPackage;
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

	public int CubeMr;

	public int getCurrentWeapon() {
		return _currentWeapon;
	}

	public void setCurrentWeapon(int i) {
		_currentWeapon = i;
	}

	private Ability pc;

	public int getType() {
		return _type;
	}

	public void setType(int i) {
		_type = i;
        _pcBaseAbilityCache.invalidate();
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

	public L1ClassFeature getClassFeature() {
		return _classFeature;
	}

	@Override
	public int getExp() {
		return _exp;
	}

	@Override
	public void setExp(int i) {
		_exp = i;
	}

	public synchronized int getReturnStat() {
		return _returnstat;
	}

	public synchronized void setReturnStat(int i) {
		_returnstat = i;
	}

	private L1PcInstance getStat() {
		return null;
	}

	public void reduceCurrentHp(double d, L1Character l1character) {
		getStat().reduceCurrentHp(d, l1character);
	}

	private void notifyPlayersLogout(Collection<L1PcInstance> playersArray) {
		for (L1PcInstance player : playersArray) {
			if (player == null) {
				continue;
			}

			if (player.knownsObject(this)) {
				player.removeKnownObject(this);

				SC_RemoveObject.send(
						player.getNetConnection(),
						this);
			}
		}
	}

	public void quitGame() {
		try {		
			remove_companion();
		} catch (Exception e) {
		}
		if (!(noPlayerCK || noPlayerck2 || pc != null)) {

			SwingUtilities.invokeLater(() -> {
				try {
					chocco.count -= 1;
					chocco.label2.setText(" " + chocco.count);
					chocco.userlist.remove(getName());
				} catch (Throwable ignore) {}
			});

		}

		try {
			if (isFishing()) {
				FishingTimeController.getInstance().endFishing(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			if (L1HauntedHouse.getInstance().isMember(this)) {
				if (getMapId() == 5140) {
					L1HauntedHouse.getInstance().clearBuff(this);
				}

				L1HauntedHouse.getInstance().removeMember(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			if (L1Racing.getInstance().isMember(this)) {
				if (getMapId() == 5143) {
					L1HauntedHouse.getInstance().clearBuff(this);
				}

				L1Racing.getInstance().removeMember(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			// 사망하고 있으면(자) 거리에 되돌려, 공복 상태로 한다
			if (isDead()) {
				int[] loc = Getback.GetBack_Location(this, true);
				setX(loc[0]);
				setY(loc[1]);
				setMap((short) loc[2]);
				setCurrentHp(getLevel());
				set_food(39); // 10%
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 트레이드를 중지한다
		try {
			if (getTradeID() != 0) { // 트레이드중
				L1Trade trade = new L1Trade();
				trade.TradeCancel(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 결투중
		try {
			if (getFightId() != 0) {
				L1PcInstance fightPc = (L1PcInstance) L1World.getInstance().findObject(getFightId());
				if (fightPc != null) {
					fightPc.setFightId(0);
					SC_PacketBox.send(fightPc, SC_PacketBox.MSG_DUEL, 0, 0);
				}
				setFightId(0);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 파티를 빠진다
		try {
			if (isInParty() || getParty() != null) { // 파티중
				getParty().leaveMember(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 채팅파티를 빠진다
		try {
			if (isInChatParty()) { // 채팅파티중
				getChatParty().leaveMember(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			if (L1DeathMatch.getInstance().isMember(this)) {
				L1DeathMatch.getInstance().removeMember(this);
			}
			if (L1HauntedHouse.getInstance().isMember(this)) {
				L1HauntedHouse.getInstance().removeMember(this);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 애완동물을 월드 MAP상으로부터 지운다

		try {
			Object[] petList = getPetList().values().toArray();
			L1PetInstance pet = null;
			// L1SummonInstance summon = null;
			for (Object petObject : petList) {
				if (petObject instanceof L1PetInstance) {
					pet = (L1PetInstance) petObject;
					pet.unloadMaster();
				}
				// 서먼
				if (petObject instanceof L1SummonInstance) {
					L1SummonInstance summon = (L1SummonInstance) petObject;
					summon.onDeath(null);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		// 마법인형을 월드 맵상으로부터 지운다
		try {
			L1DollInstance doll = null;
			for (Object dollObject : getDollList().values()) {
				doll = (L1DollInstance) dollObject;
				doll.deleteDoll(false);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			L1SupportInstance support = null;
			for (Object supportObject : getSupportList().values()) {
				if (supportObject == null)
					continue;
				support = (L1SupportInstance) supportObject;
				support.deleteSupport();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try {
			L1FollowerInstance follower = null;
			for (Object followerObject : getFollowerList().values()) {
				if (followerObject == null)
					continue;
				follower = (L1FollowerInstance) followerObject;
				follower.setParalyzed(true);
				follower.spawn(follower.getNpcTemplate().get_npcId(), follower.getX(), follower.getY(),
						follower.getHeading(), follower.getMapId());
				follower.deleteMe();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		try {
			BQSCharacterDataLoader.out(this);
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Buff persistence failure must not retain live skill timers on a disconnected PC.
		try { CharBuffTable.SaveBuff(this); }
		catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout buff persistence failed: " + getId(), e); }
		try { clearSkillEffectTimer(); }
		catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout skill timer cleanup failed: " + getId(), e); }
		try { SkillCheck.getInstance().QuitDelSkill(this); }
		catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout skill effect cleanup failed: " + getId(), e); }
		try {
			for (L1ItemInstance item : getInventory().getItems()) {
				if (item == null)
					continue;
				if (item.getCount() <= 0) {
					getInventory().deleteItem(item);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		try{
		L1ItemInstance invisItem = this.getInventory().getEquippedItem(20077);
		L1ItemInstance invisItem2 = this.getInventory().getEquippedItem(120077);
		if(invisItem != null){
			this.setGmInvis(false);
			this.getInventory().setEquipped(invisItem, false);
			//this.delInvis();
		}else if(invisItem2 != null){
			this.setGmInvis(false);
			this.getInventory().setEquipped(invisItem2, false);
		}
		}  catch (Exception e) {
			e.printStackTrace();
		}
		try{
			L1CardCollection.out(this);
			}  catch (Exception e) {
				e.printStackTrace();
		}
        stopAllRegenerations();
		// 온라인 상태를 OFF로 해, DB에 캐릭터 정보를 기입한다
		setOnlineStatus(0);


        try { save(); }
        catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout character save admission failed: " + getId(), e); }
        try { saveInventory(); }
        catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout item save admission failed: " + getId(), e); }
        try { L1BookMark.WriteBookmark(this); }
        catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout bookmark save failed: " + getId(), e); }
	}

	private final AtomicBoolean _destroyed = new AtomicBoolean(false);
    private volatile boolean _logoutComplete;

	public boolean isLogoutStarted() {
		return _destroyed.get();
	}

    /** True only after all PC logout finally/teardown steps have finished. */
    public boolean isLogoutComplete() { return _logoutComplete; }

	public void logout() {
        if (!_destroyed.compareAndSet(false, true)) return;
        try {
            try { PcInventoryExpiryScheduler.getInstance().unregister(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout expiry unregister failed: " + getId(), e); }
            try { l1j.server.server.model.character.Item.Drop.PartyLootCursor.clearSolo(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout party loot release failed: " + getId(), e); }
            // Optional teardown/persistence steps must not suppress each other.
            try { remove_companion(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout companion release failed: " + getId(), e); }
            if (getAccount() != null) {
                try { getAccount().updateBlessOfAin(); }
                catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout blessing save failed: " + getId(), e); }
                try { getAccount().updateLastLogOut(); }
                catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout timestamp save failed: " + getId(), e); }
            }
            try { DungeonTimeProgressLoader.update(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout dungeon time save failed: " + getId(), e); }
            try {
                if (!is무인상점()) {
                    SC_AttendanceUserDataExtend userData = getAttendanceData();
                    if (userData != null) {
                        SC_AttendanceUserDataExtend.update(getAccountName(), userData);
                        setAttendanceData(null);
                    }
                }
            } catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout attendance save failed: " + getId(), e); }
            try {
                if (is_combat_field()) {
                    MJCombatObserver observer = MJCombatLoadManager.getInstance()
                            .get_current_observer(get_current_combat_id());
                    if (observer != null) observer.remove(this);
                }
            } catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout combat observer failed: " + getId(), e); }
            try { RankUserLoader.getInstance().offUser(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout rank index failed: " + getId(), e); }
            try { MJRaidSpace.getInstance().getBackPc(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout raid space failed: " + getId(), e); }
            try { MJInstanceSpace.getInstance().getBackPc(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout instance space failed: " + getId(), e); }
            try { CharacterSlotItemTable.getInstance().updateCharSlotItems(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout slot item save rejected: " + getId(), e); }
        } finally {
            // Session/world ownership must not depend on the DB submit capacity.
            try { quitGame(); }
            catch (RuntimeException e) {
                _log.log(Level.SEVERE, "Logout final save failed: " + getId(), e);
                try { stopAllRegenerations(); }
                catch (RuntimeException cleanup) { _log.log(Level.SEVERE, "Logout regeneration cleanup failed: " + getId(), cleanup); }
            }
            try { CacheD.getInstance().requestCharacterItemOwnerRelease(getId(), getCacheDSessionEpoch()); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout CacheD owner release failed: " + getId(), e); }
            final L1World world = L1World.getInstance();
            try { notifyPlayersLogout(getKnownPlayers()); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout known players notify failed: " + getId(), e); }
            try { world.leaveAndRemoveObject(this); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout world removal failed: " + getId(), e); }
            try { notifyPlayersLogout(world.getRecognizePlayer(this)); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout visible players notify failed: " + getId(), e); }
            try { clearSlotChange(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout slot cleanup failed: " + getId(), e); }
            try { _inventory.clearItems(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout inventory cleanup failed: " + getId(), e); }
            try { _dwarfForPackage.clearItems(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout package cleanup failed: " + getId(), e); }
            try { removeAllKnownObjects(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout known objects cleanup failed: " + getId(), e); }
            try { stopReportDeley(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout report timer failed: " + getId(), e); }
            try { stopEquipmentTimer(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout equipment timer failed: " + getId(), e); }
            try { stopEinhasadTimer(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout Einhasad timer failed: " + getId(), e); }
            try { setDead(true); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout dead state failed: " + getId(), e); }
            try { setNetConnection(null); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout client reference failed: " + getId(), e); }
            try { getAC().addAc(1); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout AC cleanup failed: " + getId(), e); }
            try { dispose(); }
            catch (RuntimeException e) { _log.log(Level.SEVERE, "Logout dispose failed: " + getId(), e); }
            _logoutComplete = true;
        }
	}

	/**
	 * 중복 코드를 줄이기 위해 Regen 정지 함수들을 묶어주는 예시
	 */
	public void stopAllRegenerations() {
		try { AutoSaveChaQuitCheckScheduler.getInstance().unregister(this);  } catch (Throwable ignore) {}
		try { PremiumTimeController.getInstance().unregister(this);  } catch (Throwable ignore) {}
		try { CharTamScheduler.getInstance().onLeaveWorld(this); } catch (Throwable ignore) {}
		try { CIOThreadCallback.getInstance().unregisterPc(this);  } catch (Throwable ignore) {}
		try { stopGameTimeCarrier(); } catch (Throwable ignore) {}
		if (this.getNetConnection() != null) {
			Account acc = this.getNetConnection().getAccount();
			if (acc != null) {
				try { acc.updateTam(); } catch (Throwable ignore) {}
				try { acc.invalidateTamCache(); } catch (Throwable ignore) {}
			}
		}

		//try { disposeActionExecutor();} catch (Throwable ignore) {}
        
	}
	public CIOClient getNetConnection() {
		return _netConnection;
	}

	public void setNetConnection(CIOClient clientthread) {
		_netConnection = clientthread;
	}

	public boolean isInParty() {
		return getParty() != null;
	}

	public L1Party getParty() {
		return _party;
	}

	public void setParty(L1Party p) {
		_party = p;
	}

	public boolean isInChatParty() {
		return getChatParty() != null;
	}

	public L1ChatParty getChatParty() {
		return _chatParty;
	}

	public void setChatParty(L1ChatParty cp) {
		_chatParty = cp;
	}

	public int getPartyID() {
		return _partyID;
	}

	public void setPartyID(int partyID) {
		_partyID = partyID;
	}

	public int getPartyType() {
		return _partyType;
	}

	public void setPartyType(int partyType) {
		_partyType = partyType;
	}

	public int getTradeID() {
		return _tradeID;
	}

	public void setTradeID(int tradeID) {
		_tradeID = tradeID;
	}

	public void setTradeOk(boolean tradeOk) {
		_tradeOk = tradeOk;
	}

	public boolean getTradeOk() {
		return _tradeOk;
	}

	public int getTempID() {
		return _tempID;
	}

	public void setTempID(int tempID) {
		_tempID = tempID;
	}

	public boolean 텔대기() {
		return _텔대기;
	}

	public void 텔대기(boolean flag) {
		_텔대기 = flag;
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
	public boolean isAutoClanjoin() {
		return _isAutoClanjoin;
	}

	public void setAutoClanjoin(boolean flag) {
		_isAutoClanjoin = flag;
	}
	public boolean isAutoFish() {
		return _isAutofish;
	}

	public void setAutoFish(boolean flag) {
		_isAutofish = flag;
	}
	public void setShopChat(byte[] chat) {
		_shopChat = chat;
	}

	public byte[] getShopChat() {
		return _shopChat;
	}

	public boolean isPrivateShop() {
		return _isPrivateShop;
	}

	public void setPrivateShop(boolean flag) {
		_isPrivateShop = flag;
	}

	public boolean isPrivateReady() {
		return _isPrivateReady;
	}

	public void setPrivateReady(boolean b) {
		_isPrivateReady = b;
	}

	private int _special_size;

	public int get_SpecialSize() {
		return _special_size;
	}

	public void set_SpecialSize(int special_size) {
		_special_size = special_size;
	}

	public int getPartnersPrivateShopItemCount() {
		return _partnersPrivateShopItemCount;
	}

	public void setPartnersPrivateShopItemCount(int i) {
		_partnersPrivateShopItemCount = i;
	}

	private int birthday;// 생일

	public int getBirthDay() {
		return birthday;
	}

	public void setBirthDay(int t) {
		birthday = t;
	}

	private int _TelType = 0;

	public int getTelType() {
		return _TelType;
	}

	public void setTelType(int i) {
		_TelType = i;
	}

	private int AinState = 0;

	public int getAinState() {
		return AinState;
	}

	public void setAinState(int AinState) {
		this.AinState = AinState;
	}

	public boolean SurvivalState; // 생존의 외침 상태
	private int SurvivalGauge; // 생존의 외침 게이지

	public int getSurvivalGauge() {
		return SurvivalGauge;
	}

	public void setSurvivalGauge(int SurvivalGauge) {
		this.SurvivalGauge = SurvivalGauge;
	}

	public int[] DragonPortalLoc = new int[3];// 드래곤 포탈


	public void sendPackets(ProtoOutputStream stream, boolean isClear) {
		if (getNetConnection() != null)
			getNetConnection().sendPacket(stream, isClear);
	}

	public void sendPackets(ServerPacket[] sp, boolean clear) {
		if (getNetConnection() != null) {
			for (ServerPacket pck : sp)
				getNetConnection().sendPacket(pck);
		}
	}

	public void sendPackets(ServerPacket sp, boolean clear) {
		try {
			if (getNetConnection() != null) {
				getNetConnection().sendPacket(sp);
			}
		} catch (Exception e) {
		}
	}
	public void sendPackets(ServerPacket sp, boolean clear, int clanid) {
		try {
			if (getNetConnection() != null) {
				if(this.getClanid() == clanid && this.getClanid() != 0){
				getNetConnection().sendPacket(sp);
				
				}
			}
		} catch (Exception e) {
		}
	}

    

    

    

    

    

    

    

    

    

    public void sendPackets(l1j.server.server.model.network.packet.WritablePacket<CIOClient>[] packets, boolean clear) {
        if (packets == null) return;
        for (int i = 0; i < packets.length; i++) {
            sendPackets(packets[i], clear);
        }
    }

    public void sendPackets(l1j.server.server.model.network.packet.WritablePacket<CIOClient> packet, boolean clear) {
        if (packet == null) return;
        final CIOClient socket = getNetConnection();
        if (socket == null || !socket.isConnected()) return;
        socket.getConnection().enqueuePacket(packet);
    }

    public void sendPackets(String s) {
        SC_SystemMessage.send(this, s);
    }

    public void sendPackets(int code) {
        SC_ServerMessage.send(this, code);
    }

    

    

    

    public void sendPackets(ServerPacket sp) {
        if (getNetConnection() == null)
            return;
        try {
            getNetConnection().sendPacket(sp);
        } catch (Exception e) {
        }
    }

	@Override
	public void onAction(L1PcInstance attacker) {
	    if (attacker == null || isDead() || get_teleport()) {
	        return;
	    }

	    // 세이프티 존 등 공격 불가 지역 체크
	    if (getZoneType() == 1 || attacker.getZoneType() == 1) {
	        l1j.server.server.model.character.action.CAttackAction.playSafeZoneAttack(attacker, this);
	        return;
	    }

	    if (checkNonPvP(this, attacker)) {
	        return;
	    }

	    // 서포트 시스템(도우미 등) 해제
	    if (attacker.isSupporting()) {
	        clearAttackerSupport(attacker);
	    }

	    // 체력이 있을 때만 실제 공격 처리
	    if (getCurrentHp() > 0) {
	        l1j.server.server.model.character.action.CAttackAction.attackPcTarget(attacker, this);
	    }
	}
	
	/**
	 * 실제 공격 및 카운터/일반 대미지 로직 처리
	 */
	private void clearAttackerSupport(L1PcInstance attacker) {
	    Object[] supportList = attacker.getSupportList().values().toArray();
	    for (Object supportObject : supportList) {
	        if (supportObject instanceof L1SupportInstance) {
	            ((L1SupportInstance) supportObject).deleteSupport();
	        }
	    }
	    SC_OwnCharStatus.send(attacker);
	    attacker.setSupporting(false);
	}

	public boolean checkNonPvP(L1PcInstance pc, L1Character target) {
		L1PcInstance targetpc = null;
		if (target instanceof L1PcInstance) {
			targetpc = (L1PcInstance) target;
		} else if (target instanceof L1CompanionInstance) {
			targetpc = ((L1CompanionInstance) target).get_master();
		} else if (target instanceof L1PetInstance) {
			targetpc = (L1PcInstance) ((L1PetInstance) target).getMaster();
		} else if (target instanceof L1SummonInstance) {
			targetpc = (L1PcInstance) ((L1SummonInstance) target).getMaster();
		}
		if (targetpc == null) {
			return false;
		}
		if (!Config.ALT_NONPVP) {
			if (getMap().isCombatZone(getLocation())) {
				return false;
			}

			L1Clan clan = pc.getClan();
			L1Clan enemyclan = targetpc.getClan();
			if (clan != null && enemyclan != null) {
				MJWar war = clan.getCurrentWar();
				MJWar enemyWar = enemyclan.getCurrentWar();
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

	public boolean isInWarAreaAndWarTime(L1PcInstance pc, L1PcInstance target) {
		int castleId = L1CastleLocation.getCastleIdByArea(pc);
		int targetCastleId = L1CastleLocation.getCastleIdByArea(target);
		if (castleId != 0 && targetCastleId != 0 && castleId == targetCastleId) {
			if (MJCastleWarBusiness.getInstance().isNowWar(castleId)) {
				return true;
			}
		}
		return false;
	}

	public void setPetTarget(L1Character target) {
		Object[] petList = getPetList().values().toArray();
		L1PetInstance pets = null;
		L1SummonInstance summon = null;
		for (Object pet : petList) {
			if (pet == null)
				continue;
			if (pet instanceof L1PetInstance) {
				pets = (L1PetInstance) pet;
				pets.setMasterTarget(target);
			} else if (pet instanceof L1SummonInstance) {
				summon = (L1SummonInstance) pet;
				summon.setMasterTarget(target);
			}
		}
	}

	public void set_pet_target(L1Character target) {
		setPetTarget(target);
		if (m_companion != null)
			m_companion.set_target(target);
	}

	public boolean isstop() {
		return (hasSkillEffect(L1SkillId.SHOCK_STUN)) ||hasSkillEffect(L1SkillId.EMPIRE) || (hasSkillEffect(L1SkillId.ICE_LANCE))
				|| (hasSkillEffect(L1SkillId.BONE_BREAK)) || (hasSkillEffect(L1SkillId.EARTH_BIND)) || (hasSkillEffect(L1SkillId.MOB_RANGESTUN_19))
				|| (hasSkillEffect(L1SkillId.MOB_SHOCKSTUN_30)) || (hasSkillEffect(L1SkillId.OMAN_STUN)) || (hasSkillEffect(L1SkillId.Maeno_STUN))
				|| (hasSkillEffect(L1SkillId.ANTA_MESSAGE_6)) || (hasSkillEffect(L1SkillId.ANTA_MESSAGE_7))
				|| (hasSkillEffect(L1SkillId.ANTA_MESSAGE_8)) || (hasSkillEffect(L1SkillId.ANTA_SHOCKSTUN));
	}
	
    public boolean isFloatingEye() {
		return hasSkillEffect(L1SkillId.STATUS_FLOATING_EYE) && isBlind();
	}
	
	public boolean isBlind() {
		return hasSkillEffect(L1SkillId.CURSE_BLIND) || hasSkillEffect(L1SkillId.DARKNESS) || hasSkillEffect(L1SkillId.LINDBIOR_SPIRIT_EFFECT);
	}

	public void receiveDamage(L1Character attacker, int damage, int attr) {
		if (damage == 0)
			return;
		Random random = new Random(System.nanoTime());
		int player_mr = getResistance().getEffectedMrBySkill();
		int rnd = random.nextInt(100) + 1;
		if (player_mr >= rnd) {
			damage /= 2;
		}

		receiveDamage(attacker, damage);
	}

	public void receiveManaDamage(L1Character attacker, int mpDamage) {
		if (mpDamage > 0 && !isDead()) {
			delInvis();
			if (attacker instanceof L1PcInstance) {
				L1PinkName.onAction(this, attacker);
			}
			int newMp = getCurrentMp() - mpDamage;
			this.setCurrentMp(newMp);
		}
	}

	public boolean isInWarArea() {
		boolean isNowWar = false;
		int castleId = L1CastleLocation.getCastleIdByArea(this);

		if (castleId != 0) {
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(castleId);
		}
		return isNowWar;
	}

	public void receiveCounterBarrierDamage(L1Character attacker, int damage) {
		// The PC is no longer a valid damage target after controller detachment.
		_creatureController.OnDamaged(attacker, damage, true);
	}

	/** NC-style victim damage application; invoked only by CCreatureController.OnDamagedMessage. */
	public void GotCounterBarrierDamage(L1Character attacker, int damage) {
		try {
			if (getCurrentHp() > 0 && !isDead()) {
				if (attacker != null && attacker != this && !knownsObject(attacker)
						&& attacker.getMapId() == this.getMapId()) {
					attacker.onPerceive(this);
				}

				if (damage > 0) {
					delInvis();
					if (hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)) {
						removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
					} else if (hasSkillEffect(L1SkillId.PHANTASM)) {
						removeSkillEffect(L1SkillId.PHANTASM);
					}
					if (attacker.instanceOf(MJL1Type.L1TYPE_PC)) {
						if (is_combat_field()) {
							MJCombatObserver observer = MJCombatLoadManager.getInstance()
									.get_current_observer(get_current_combat_id());
							if (observer != null) {
								observer.on_damage((L1PcInstance) attacker, this, damage);
							} else {
								set_instance_status(MJEPcStatus.WORLD);
							}
						}
					}
				} else if (damage < 0) {
					return;
				}
				if (getInventory().checkEquipped(145) || getInventory().checkEquipped(149)) {
					damage *= 1.5;
				}

				final long hpChange = changeHpAtomicPair(-(long) damage);
				if (hpChange < 0L) return;
				final int oldHp = (int) (hpChange >>> 32);
				final int newHp = (int) hpChange;
				if (newHp > 0) {
					if (newHp != oldHp) {
						if (getInstStatus() == InstStatus.INST_USERSTATUS_LFC && newHp > oldHp)
							addDamageFromLfc(newHp - oldHp);
						SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
						if (isInParty()) getParty().refreshPartyMemberStatus(this);
					}
				} else {
					try {
					if ((getInventory().checkEquipped(123111) || getInventory().checkEquipped(123112)
							|| getInventory().checkEquipped(123113))) {
						if (!hasSkillEffect(L1SkillId.할파스의신의)) {

							for (L1ItemInstance item : getInventory().getItems()) {
								if (item.isEquipped()) {
									if (item.getEnchantLevel() >= 0 && (item.getItemId() == 123111
											|| item.getItemId() == 123112 || item.getItemId() == 123113)) {
										int gg = ((Config.할파스쿨타임 * 60 * 1000) * item.getEnchantLevel());
										int hh = Config.할파스가호 * 60 * 1000;
										int gh = hh - gg;
										int aa = (12 * 1000) + (Config.할파스PVP쿨타임 * 1000 * item.getEnchantLevel());

										this.setCurrentHp(getMaxHp());
										setSkillEffect(L1SkillId.할파스의신의, gh);
										setSkillEffect(L1SkillId.할파스의신의PVP,
												(Config.할파스신의 * 1000) + (Config.할파스PVP쿨타임 * 1000 * item.getEnchantLevel()));
										 SC_InventoryIcon.icoEnd(this, L1SkillId.할파스의신의1);
										 SC_InventoryIcon.icoNew(this, L1SkillId.할파스의신의, 3942, getSkillEffectTimeSec(L1SkillId.할파스의신의), true);
										SC_PacketBox.send(this, SC_PacketBox.GREEN_MESSAGE,
												"\\f3할파스의 신의: PVP대미지리덕션 대폭상승 (" + aa / 1000 + "초)");
										sendPackets("\\fY할파스의 신의가 발동되었습니다.");

										if (this.isInParty()) {
											for (L1PcInstance partymember : this.getParty().getMembers()) {
												//partymember.sendPackets(new S_SkillSound(getId(), 12444), true);
												SC_SkillSound.send(partymember, getId(), 14446);
											}
										} else {
											//this.sendPackets(new S_SkillSound(getId(), 12444), true);
											SC_SkillSound.send(this, getId(), 14446);
										}
									}

								}
							}
						} else {
							if (isGm()) {
								setCurrentHp(getMaxHp());
							} else {
								if (isElf() && hasSkillEffect(L1SkillId.SOUL_BARRIER)) {
									int newMp = getCurrentMp() - damage;
									this.setCurrentHp(10);
									SC_SkillSound.send(this, getId(), 14541);
									SC_SkillSound.broadcast(this, getId(), 14541);
									if (newMp <= 0) {
										death(attacker, true);
										this.setCurrentHp(0);
									}
									this.setCurrentMp(newMp);
								} else { 
									death(attacker, true);
								}
									
							}	
						}
					} else if (isElf() && hasSkillEffect(L1SkillId.SOUL_BARRIER)) {
						int newMp = getCurrentMp() - damage;
						this.setCurrentHp(10);
						SC_SkillSound.send(this, getId(), 14541);
						SC_SkillSound.broadcast(this, getId(), 14541);
						if (newMp <= 0) {
							death(attacker, true);
							this.setCurrentHp(0);
						}
						this.setCurrentMp(newMp);
					} else {
						if (isGm()) {
							this.setCurrentHp(getMaxHp());
						} else {
							//카운터배리어, 타이탄데미지처리
							if (isDeathMatch()) {
								if (getMapId() == 5153) {
									try {
										save();
										beginGhost(getX(), getY(), (short) getMapId(), true);
										SC_ServerMessage.send(this, 1271);
									} catch (Exception e) {
										e.printStackTrace();
									}
									return;
								}
							} else {
								death(attacker, false);
							}
						}
					}
					} finally {
						if (getCurrentHp() == 0) {
							SC_HpUpdate.send(this, 0, getMaxHp());
							if (isInParty()) getParty().refreshPartyMemberStatus(this);
						}
					}
				}
			} else if (!isDead()) {
				// A successful lethal HP CAS has already claimed the transition.
				// It may be resolving a shield/item before setting the dead flag.
				return;
			}
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(
					String.format("%s -> %s : %d", attacker == null ? "null" : attacker.getName(), getName(), damage));
		}
	}

	private int calculate_sprite_pvp_damage(int sprite_id) {
		switch (sprite_id) {
		case 15986:
		case 16002:
		case 16008:
		case 16014:
		case 16027:
		case 16040:
		case 16053:
		case 16056:
		case 16074:
		case 16284:
		case 17515:
		case 17531:
		case 17535:
		case 17541:
		case 17545:
		case 17549:
		case 13715:
		case 13717:
		case 15115:
		case 13721:
		case 13723:
		case 13725:
		case 13727:
		case 13729:
		case 13731:
		case 13733:
		case 13735:
		case 13737:
		case 13739:
		case 13741:
		case 13743:
		case 13745:
			return 2;
		}
		return 0;
	}

    private static final long MAGIC_STACK_WINDOW_NS = 2_000_000_000L;
    private long magicStackStartNs;
    private int magicStackCount = -1;
    private int magicStackSession;
    private int magicStackLife;

    /** The boolean means stacking permission, not the controller's counter flag.
     * Only delivery on the victim controller may update the state below. */
    public void receiveDamage(L1Character attacker, int damage, boolean applyMagicStack) {
        if (applyMagicStack && damage <= 0) return;
        _creatureController.OnDamaged(attacker, damage, false, applyMagicStack && damage > 0);
    }

	public void receiveDamage(L1Character attacker, int damage) {
        receiveDamage(attacker, damage, false);
    }

    /** Bare-hand provenance is captured at the attack, never inferred from a
     * later weapon swap or from an ordinary spell's caster equipment. */
    public void receiveUnarmedPhysicalDamage(L1Character attacker, int damage) {
        _creatureController.OnDamaged(attacker, damage, false, false, true);
    }

    private int applyMagicStackToDamage(int damage, long now) {
        if (damage <= 0 || getCurrentHp() <= 0 || isDead()) return damage;
        final long elapsed = now - magicStackStartNs;
        if (magicStackCount < 0 || magicStackSession != getSessionStamp()
                || magicStackLife != _deathTaskGeneration || elapsed < 0L
                || elapsed >= MAGIC_STACK_WINDOW_NS) {
            magicStackStartNs = now;
            magicStackCount = 0;
            magicStackSession = getSessionStamp();
            magicStackLife = _deathTaskGeneration;
            return damage;
        }
        if (magicStackCount < Integer.MAX_VALUE) ++magicStackCount;
        return (int) Math.floor(damage * Math.pow(2.0 / 3.0, magicStackCount));
    }


	/**
	 * CCreature::GotDamage-inspired victim-side application. All legacy armor, barrier,
	 * combat-status, atomic HP, resurrection item and death side effects remain
	 * here, executed by the target controller rather than the attacker thread.
	 */
    public void GotDamage(L1Character attacker, int damage) {
        GotDamage(attacker, damage, false);
    }

	public void GotDamage(L1Character attacker, int damage, boolean applyMagicStack) {
        applyReceivedDamage(attacker, damage, applyMagicStack, false);
    }

    public void GotUnarmedPhysicalDamage(L1Character attacker, int damage) {
        applyReceivedDamage(attacker, damage, false, true);
    }

    private void applyReceivedDamage(L1Character attacker, int damage,
                                     boolean applyMagicStack, boolean unarmedPhysical) {
        if ((applyMagicStack || unarmedPhysical) && (hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)
                || MJCommons.isUnbeatable(this))) return;
		if(get_teleport()) {
			return;
		}
	
		if (!unarmedPhysical && hasSkillEffect(L1SkillId.LUCIFER) && damage > 0) {
			damage -= (damage * 0.10);
		}

		if (hasSkillEffect(L1SkillId.DARK_BLIND)) {
			removeSkillEffect(L1SkillId.DARK_BLIND);
		}

		if (attacker instanceof L1PcInstance) {
            if (!unarmedPhysical)
                damage += calculate_sprite_pvp_damage(attacker.getCurrentSpriteId());
			if (is_combat_field()) {
				L1PcInstance attacker_pc = (L1PcInstance) attacker;
				if (get_current_combat_id() == attacker_pc.get_current_combat_id()
						&& get_current_combat_team_id() == attacker_pc.get_current_combat_team_id()) {
					return;
				}
			}
            // Victim-owned PVP base reduction, exactly once for physical and magic hits.
            if (!unarmedPhysical && damage > 0) {
                damage = Math.max(0, damage - getBaseAbility().getPVP대미지리덕션());
            }
		}

		if ((getCurrentHp() > 0 && !isDead()) || isGm()) {
			if (attacker != null && attacker != this && !knownsObject(attacker)
					&& attacker.getMapId() == this.getMapId()) {
				attacker.onPerceive(this);
			}

			if (damage > 0) {
				delInvis();
				if (attacker instanceof L1PcInstance) {
					L1PcInstance attacker_pc = (L1PcInstance) attacker;
					L1PinkName.onAction(this, attacker);
					if (is_combat_field()) {
						MJCombatObserver observer = MJCombatLoadManager.getInstance()
								.get_current_observer(get_current_combat_id());
						if (observer != null) {
							observer.on_damage(attacker_pc, this, damage);
						} else {
							set_instance_status(MJEPcStatus.WORLD);
						}
					}
					L1CompanionInstance companion = attacker_pc.get_companion();
					if (companion != null && companion.get_command_state().equals(eCommand.TM_Aggressive)) {
						if (getZoneType() == 0 && companion.getZoneType() == 0)
							companion.do_pink_name();
					}
				} else if (attacker instanceof L1CompanionInstance) {
					L1CompanionInstance companion = (L1CompanionInstance) attacker;
					if (getZoneType() == 0 && companion.getZoneType() == 0)
						companion.do_pink_name();
				}

				for (L1ItemInstance item : _equipSlot.getArmors()) {
					MJItemSkillModel model = MJItemSkillModelLoader.getInstance().getDef(item.getItemId());
                    if (model != null) {
                        final double reduction = model.get(attacker, this, item, damage);
                        // Keep defense proc side effects; its DR cannot change
                        // the confirmed bare-hand 1/2 damage component.
                        if (!unarmedPhysical) damage -= reduction;
                    }
				}

//				for (L1ItemInstance item : getInventory().getItems()) {
//					CharmSkillModel model = CharmModelLoader.getInstance().getDef(item.getItemId());
//					if (model != null)
//						damage -= model.get(attacker, this, item, damage);
//				}

                if (damage <= 0) {
                    damage = 10; // Preserve the existing armor-proc minimum.
                    applyMagicStack = false; // A blocked hit must not advance the window.
                }
//				if (getAI() != null && getAI().getBotType() == MJBotType.REDKNIGHT) {
//					damage += (getMaxHp() / Config.RedKnight_dieCount);
//				}

				if (hasSkillEffect(L1SkillId.FOG_OF_SLEEPING)) {
					removeSkillEffect(L1SkillId.FOG_OF_SLEEPING);
				} else if (hasSkillEffect(L1SkillId.PHANTASM)) {
					removeSkillEffect(L1SkillId.PHANTASM);
				}
			} else if (damage < 0) {
				if (attacker instanceof L1PcInstance) {
					L1PinkName.onHelp(this, attacker);
				}
			}
            if (applyMagicStack && damage > 0) {
                damage = applyMagicStackToDamage(damage, System.nanoTime());
                // Attacker-side capping would reduce an overkill spell before
                // stacking. Cap after attenuation on the victim owner instead.
                int limit = getCurrentHp();
                if (isElf() && hasSkillEffect(L1SkillId.SOUL_BARRIER))
                    limit += getCurrentMp();
                damage = Math.min(damage, Math.max(0, limit));
            }
			// The attacker's controller is serialized, but two attackers can
			// damage the same target on different completion threads. Reuse
			// L1Character's existing primitive HP CAS instead of a volatile
			// read followed by a blind set that loses one attack.
			final long hpChange = changeHpAtomicPair(-(long) damage);
			if (hpChange < 0L) {
				// Another attacker already claimed the lethal transition.
				if (isGm()) setCurrentHp(getMaxHp());
				return;
			}
			final int oldHp = (int) (hpChange >>> 32);
			final int newHp = (int) hpChange;
			if (newHp > 0) {
				// The atomic update already changed HP, so calling setCurrentHp
				// here would overwrite another attack. Keep its original LFC,
				// packet and party-status side effects without a second store.
				if (newHp != oldHp) {
					if (getInstStatus() == InstStatus.INST_USERSTATUS_LFC && newHp > oldHp)
						addDamageFromLfc(newHp - oldHp);
					SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
					if (isInParty()) getParty().refreshPartyMemberStatus(this);
				}
			} else {
				try {
				if ((getInventory().checkEquipped(123111) || getInventory().checkEquipped(123112)
						|| getInventory().checkEquipped(123113))) {
					if (!hasSkillEffect(L1SkillId.할파스의신의)) {

						for (L1ItemInstance item : getInventory().getItems()) {
							if (item.isEquipped()) {
								if (item.getEnchantLevel() >= 0 && (item.getItemId() == 123111
										|| item.getItemId() == 123112 || item.getItemId() == 123113)) {
									int gg = ((Config.할파스쿨타임 * 60 * 1000) * item.getEnchantLevel());
									int hh = Config.할파스가호 * 60 * 1000;
									int gh = hh - gg;
									int aa = (12 * 1000) + (Config.할파스PVP쿨타임 * 1000 * item.getEnchantLevel());

									this.setCurrentHp(getMaxHp());
									setSkillEffect(L1SkillId.할파스의신의, gh);
									setSkillEffect(L1SkillId.할파스의신의PVP,
											(Config.할파스신의 * 1000) + (Config.할파스PVP쿨타임 * 1000 * item.getEnchantLevel()));
									 SC_InventoryIcon.icoEnd(this, L1SkillId.할파스의신의1);
									 SC_InventoryIcon.icoNew(this, L1SkillId.할파스의신의, 3942, getSkillEffectTimeSec(L1SkillId.할파스의신의), true);
									SC_PacketBox.send(this, SC_PacketBox.GREEN_MESSAGE,
											"\\f3할파스의 신의: PVP대미지리덕션 대폭상승 (" + aa / 1000 + "초)");
									sendPackets("\\fY할파스의 신의가 발동되었습니다.");

									if (this.isInParty()) {
										for (L1PcInstance partymember : this.getParty().getMembers()) {
											//partymember.sendPackets(new S_SkillSound(getId(), 12444), true);
											SC_SkillSound.send(partymember, getId(), 14446);
										}
									} else {
										//this.sendPackets(new S_SkillSound(getId(), 12444), true);
										SC_SkillSound.send(this, getId(), 14446);
									}
								}

							}
						}
					} else {
						if (isGm()) {
							setCurrentHp(getMaxHp());
						} else {
							if (isElf() && hasSkillEffect(L1SkillId.SOUL_BARRIER)) {
								int newMp = getCurrentMp() - damage;
								this.setCurrentHp(10);
								SC_SkillSound.send(this, getId(), 14541);
								SC_SkillSound.broadcast(this, getId(), 14541);
								if (newMp <= 0) {
									death(attacker, true);
									this.setCurrentHp(0);
								}
								this.setCurrentMp(newMp);
							} else { 
								death(attacker, true);
							}
								
						}	
					}
				} else if (isElf() && hasSkillEffect(L1SkillId.SOUL_BARRIER)) {
					int newMp = getCurrentMp() - damage;
					this.setCurrentHp(10);
					SC_SkillSound.send(this, getId(), 14541);
					SC_SkillSound.broadcast(this, getId(), 14541);
					if (newMp <= 0) {
						death(attacker, true);
						this.setCurrentHp(0);
					}
					this.setCurrentMp(newMp);
				} else { 
					if (isGm()) {
						setCurrentHp(getMaxHp());
					} else {
						if (attacker instanceof L1PcInstance) {

							death(attacker, true);
						}

						if (isDeathMatch()) {
							if (getMapId() == 5153) {
								try {
									save();
									beginGhost(getX(), getY(), (short) getMapId(), true);
									SC_ServerMessage.send(this, 1271);
								} catch (Exception e) {
									e.printStackTrace();
								}
								return;
							}

						} else {
							
							death(attacker, true);
						}
					}
				}
				} finally {
					// death() calls setCurrentHp(0), which now sees HP already
					// zero. Publish that terminal value once, unless an item
					// or skill has already restored positive HP.
					if (getCurrentHp() == 0) {
						SC_HpUpdate.send(this, 0, getMaxHp());
						if (isInParty()) getParty().refreshPartyMemberStatus(this);
					}
				}
			}
			
		} else if (!isDead()) {
			// Another attacker may be resolving the winning lethal transition.
			return;
		}
	}

	// Reused PC objects can have an old Death runnable queued behind a restart.
	// A primitive generation prevents that runnable from altering a newer life.
	private volatile int _deathTaskGeneration;

	public void death(L1Character lastAttacker, boolean deathPenalty) {
	    final int deathGeneration;
	    final int deathSession;
	    synchronized (this) {
	        if (isDead()) {
	            return;
	        }
	        setDead(true);
	        deathGeneration = ++_deathTaskGeneration;
	        deathSession = getSessionStamp();
	    }

	    // 3. 혈맹 및 동반자/봇 정리
	    L1Clan clan = getClan();
	    if (clan != null) {
	        clan.deleteClanRetrieveUser(getId());
	    }
	    
	    remove_companion();
	    // 스킬 효과 제거
	    if (hasSkillEffect(L1SkillId.STATUS_TOMAHAWK)) {
	        killSkillEffectTimer(L1SkillId.STATUS_TOMAHAWK);
	    }
	    if (hasSkillEffect(L1SkillId.STATUS_DRAGON_PEARL)) {
	        removeSkillEffect(L1SkillId.STATUS_DRAGON_PEARL);
	    }
	    //  HP 및 액션 상태 갱신
	    setCurrentHp(0);
	    setStatus(ActionCodes.ACTION_Die);

	    //  가해자(LastAttacker) 및 PvP / 쟁 콘텐츠 처리
	    if (lastAttacker instanceof L1PcInstance) {
	        L1PcInstance player = (L1PcInstance) lastAttacker;
	        
	        // 붉은 기사단 킬 처리
//	        if (getRedKnightClanId() != 0 && player.getRedKnightClanId() != 0) {
//	            player.addRedKnightKill(1);
//	            if (player.getRedKnightKill() >= 5) {
//	                player.setRedKnightKill(0);
//	                player.setRedKnightDamage(0);
//	                player.setRedKnightClanId(0);
//	                player.sendPackets(new SC_SystemMessage("붉은 기사단 혈원을 5회 이상 죽여 강제로 탈퇴됩니다."));
//	            }
//	        }

	        // AI / 봇 처리
//	        if (getAI() != null) {
//	            MJBotType type = getAI().getBotType();
//	            if (type == MJBotType.REDKNIGHT || type == MJBotType.PROTECTOR) {
//	                broadcastPacket(new S_SkillSound(getId(), 2236));
//	                return;
//	            }
//	        }

	        // 전투 필드(컴뱃) / 일반 KDA 처리
	        if (is_combat_field()) {
	            MJCombatObserver observer = MJCombatLoadManager.getInstance()
	                    .get_current_observer(get_current_combat_id());
	            if (observer == null) {
	                set_instance_status(MJEPcStatus.WORLD);
	                if (getKDA() != null) {
	                    getKDA().onKill(player, this);
	                }
	            } else {
	                try {
	                    observer.on_kill(player, this);
	                    SC_ChatPacket.broadcastOpcode(String.format("\\aG승리[%s] \\aA=> \\aL패배[%s]", player.getName(), getName()), Opcodes.S_MESSAGE);
                    SC_SystemMessage.broadcast("==========================");
	                } catch (Exception e) {
	                    e.printStackTrace();
	                }
	            }
	        } else {
	            if (getKDA() != null) {
	                getKDA().onKill(player, this);
	            }
	        }
	    }
        final Death mandatoryDeath = new Death(lastAttacker, deathPenalty,
                deathGeneration, deathSession);
        try {
            ThreadPool.getInstance().execute(mandatoryDeath);
        } catch (RejectedExecutionException rejected) {
            // Death has already been published; dropping this continuation
            // strands corpse/penalty processing. Explicit overload fallback.
            _log.log(Level.SEVERE,
                    "Mandatory character death settlement rejected; running in caller", rejected);
            mandatoryDeath.run();
        }
	}

	private boolean isCurrentDeath(int generation, int session) {
		return isDead() && _deathTaskGeneration == generation
				&& getSessionStamp() == session;
	}

	private class Death implements Runnable {
		private final L1Character _lastAttacker;
		private final int _generation;
		private final int _session;

		Death(L1Character cha, boolean deathPenalty, int generation, int session) {
			_lastAttacker = cha;
			_generation = generation;
			_session = session;
		}

		@Override
		public void run() {
			if (!isCurrentDeath(_generation, _session)) return;
			L1Character lastAttacker = _lastAttacker;
			setCurrentHp(0);
			setGresValid(false);
			TileOccupancy.getInstance().releaseNow(getMapId(), getX(), getY(), getId());
			int targetobjid = getId();		
			int tempchargfx = 0;
			if (hasSkillEffect(L1SkillId.SHAPE_CHANGE)) {
				tempchargfx = getCurrentSpriteId();
				setTempCharGfxAtDead(tempchargfx == 0 && getClassId() != 0 ? getClassId() : tempchargfx);
			} else {
				setTempCharGfxAtDead(getClassId());
			}

			setCurrentSprite(getClassId());
			

			L1SkillUse.cast(
					L1PcInstance.this,
					L1SkillId.CANCELLATION,
					getId(),
					getX(),
					getY(),
					null,
					0,
					L1SkillUse.TYPE_LOGIN);
			
			if (tempchargfx == 5727 || tempchargfx == 5730 || tempchargfx == 5733 || tempchargfx == 5736) {
				tempchargfx = 0;
			}
			if (tempchargfx != 0) {
				sendShape(tempchargfx);
			}
			sendShape(getTempCharGfxAtDead());

			SC_DoActionGFX.send(
			        L1PcInstance.this.getNetConnection(),
			        targetobjid,
			        ActionCodes.ACTION_Die);

			SC_DoActionGFX.broadcast(
			        L1PcInstance.this,
			        ActionCodes.ACTION_Die);

			if (lastAttacker != L1PcInstance.this && lastAttacker != null) {
				L1PcInstance player = getAttackerPlayer(lastAttacker);
				if (player != null && getZoneType() == -1) {
					return;
				}
				if (simWarResult(lastAttacker)) {
					return;
				}
			}

			if (!isCurrentDeath(_generation, _session)) return;
			if (!getMap().isEnabledDeathPenalty()) {
				return;
			}

			// 결투(디펜스/듀얼) 처리
			if (lastAttacker instanceof L1PcInstance) {
				L1PcInstance fightPc = (L1PcInstance) lastAttacker;
				if (getFightId() == fightPc.getId() && fightPc.getFightId() == getId()) {
					setFightId(0);
					SC_PacketBox.send(L1PcInstance.this, SC_PacketBox.MSG_DUEL, 0, 0);
					fightPc.setFightId(0);
					SC_PacketBox.send(fightPc, SC_PacketBox.MSG_DUEL, 0, 0);
					return;
				}
			}

			if (is_combat_field()) {
				return;
			}

			// 공성장 사망 처리
			if (castleWarResult()) {
				SC_ServerMessage.send(L1PcInstance.this, 3798); // 경험치 손실이 없는 지역
				return;
			}

			if (GMCommands.IS_PROTECTION) {
				if (getKDA() != null) {
					getKDA().onProtection(L1PcInstance.this);
				}
				return;
			}
			
			if (getMapId() == 13005 || getZoneType() == 1 || getZoneType() == -1) {
				SC_ServerMessage.send(L1PcInstance.this, 3798);
				return;
			}

			if (getLevel() <= 55) {
				SC_ServerMessage.send(L1PcInstance.this, 3804);
				return;
			}

			// [불멸의 가호 / 고급 가호 / 고대 가호 체크 압축
			if (getInventory().hasItemDirect(4100122)) {
				gahodrop(lastAttacker);
				return;
			}
			if (getInventory().hasItemDirect(4100121)) {
				specialgahodrop(lastAttacker);
				return;
			}
			if ((getMapId() >= 1708 && getMapId() <= 1709) && getInventory().isEquippedDirect(900022)) {
				drop1();
				return;
			}
			if (getInventory().isEquippedDirect(10000)) {
				drop2();
				return;
			}
			if (getInventory().isEquippedDirect(10001)) {
				drop3();
				return;
			}

			// A restart or a second death may have superseded this queued task
			// while the earlier graphics/skill callbacks were running.
			if (!isCurrentDeath(_generation, _session)) return;
			// 기본 데스 페널티(경험치 하락) 적용
			deathPenalty();
			setGresValid(true);

			if (getExpRes() == 0) {
				if (!(lastAttacker instanceof L1PcInstance && getLevel() < Config.NEW_PLAYER && (lastAttacker.getLevel() - getLevel()) >= 10)) {
					setExpRes(1);
				}
			}

			// 경비병 사망 시 PK 카운트 차감
			if (lastAttacker instanceof L1GuardInstance) {
				if (get_PKcount() > 0) {
					set_PKcount(get_PKcount() - 1);
				}
				setLastPk(null);
			}

			// 카오틱 성향치에 따른 아이템 드랍/소멸 처리
			if (!getInventory().isEquippedDirect(10001) && !getInventory().hasItemDirect(4100121)) {
				int dropCount = calculateChaosDropCount(getLawful());
				caoPenaltyResult(dropCount);
			}

			if (castleWarResult()) {
				return;
			}
		}
		/** 가해자(소환수/펫 포함)로부터 마스터(PC)를 추출하는 헬퍼 메서드 **/
		private L1PcInstance getAttackerPlayer(L1Character attacker) {
			if (attacker instanceof L1PcInstance) return (L1PcInstance) attacker;
			if (attacker instanceof L1CompanionInstance) return ((L1CompanionInstance) attacker).get_master();
			if (attacker instanceof L1PetInstance) return (L1PcInstance) ((L1PetInstance) attacker).getMaster();
			if (attacker instanceof L1SummonInstance) return (L1PcInstance) ((L1SummonInstance) attacker).getMaster();
			return null;
		}
		/** 라우풀 수치에 따른 아이템 드랍 개수 산출 로직 깔끔하게 압축 **/
		private int calculateChaosDropCount(int lawful) {
			if (lawful < -30000) return MJRnd.next(Config.라우풀1) + 1;
			if (lawful < -20000) return MJRnd.next(Config.라우풀2) + 1;
			if (lawful < -10000) return MJRnd.next(Config.라우풀3) + 1;
			if (lawful < 0) return MJRnd.next(Config.라우풀4) + 1;
			if (lawful < 10000) return MJRnd.next(Config.라우풀5) + 1;
			if (lawful < 20000) return MJRnd.next(Config.라우풀6) + 1;
			if (lawful <= 32766) return MJRnd.next(Config.라우풀7) + 1;
			return MJRnd.next(Config.라우풀8) + 1;
		}
	}
	

	private void caoPenaltyResult(int count) {
		/** 공성존에서 드랍안되도록 **/
		int castle_id = L1CastleLocation.getCastleIdByArea(this);
		if (castle_id != 0) {
			return;
		}
		/** 로봇시스템 **/
		if (getAccessLevel() == Config.\u0047\u004D\u0043\u004F\u0044\u0045) {
			return;
		}
		
		   for (int i = 0; i < count; i++) {
	            L1ItemInstance item = getInventory().CaoPenalty();

	            if (item != null) {
	                if (item.getBless() > 3) {
	                    getInventory().removeItem(item, item.isStackable() ? item.getCount() : 1);
	                    SC_ServerMessage.send(this, 158, item.getLogName());// 증발
	                    for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
							if (pc.isGm()) {
								SC_SystemMessage.send(pc, "\\fY[GM] " + getName() + "님의 [" + item.getLogName() + "] 증발");
								;
							}
						}
	                    /** 파일로그저장 **/ 
	                    LoggerInstance.getInstance().addItemAction(ItemActionType.del, this, item, count);
	                } else {
	                    getInventory().tradeItem(item, item.isStackable() ? item.getCount() : 1,
	                            L1World.getInstance().getInventory(getX(), getY(), getMapId()));
	                    SC_SystemMessage.send(this, "사망 패널티(드랍): [" + item.getLogName() + "] ");

	                   // sendPackets(new SC_ServerMessage(638, item.getLogName()));// 증발


	                    for (L1PcInstance pc : L1World.getInstance().getAllPlayers()) {
							if (pc.isGm()) {
								SC_SystemMessage.send(pc, "\\fY[GM] " + getName() + "님의 [" + item.getLogName() + "] 드랍");
								;
							}
						}
	                    /** 파일로그저장 **/
	                    LoggerInstance.getInstance().addItemAction(ItemActionType.del, this, item, count);
	                }
	            }
	        }
		

	}


	// 군주 죽으면 혈원 자동 베르
	public boolean castleWarResult() {
		L1Clan clan = getClan();
		if (clan != null && isCrown() && getId() == clan.getLeaderId()) {
			MJWar war = clan.getCurrentWar();
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
			isNowWar = MJCastleWarBusiness.getInstance().isNowWar(castleId);
		}
		return isNowWar;
	}

	// 군주 죽으면 혈원 자동 베르 //
	public boolean simWarResult(L1Character lastAttacker) {
		if (getClanid() == 0) {
			return false;
		}
		if (Config.SIM_WAR_PENALTY) {
			return false;
		}
		L1PcInstance attacker = null;

		if (lastAttacker instanceof L1PcInstance) {
			attacker = (L1PcInstance) lastAttacker;
		} else if (lastAttacker instanceof L1CompanionInstance) {
			attacker = ((L1CompanionInstance) lastAttacker).get_master();
		} else if (lastAttacker instanceof L1PetInstance) {
			attacker = (L1PcInstance) ((L1PetInstance) lastAttacker).getMaster();
		} else if (lastAttacker instanceof L1SummonInstance) {
			attacker = (L1PcInstance) ((L1SummonInstance) lastAttacker).getMaster();
		} else {
			return false;
		}
		L1Clan clan = getClan();
		L1Clan enemy = attacker.getClan();
		if (clan == null || enemy == null || clan.getLeaderId() != getId())
			return false;

		MJWar war = clan.getCurrentWar();
		MJWar enemyWar = enemy.getCurrentWar();
		if (war == null || enemyWar == null || !war.equals(enemyWar))
			return false;

		WAR_TYPE type = war.getWarType();
		if (!type.equals(WAR_TYPE.NORMAL))
			return false;

		war.notifyWinner(enemy, clan);
		war.notifyEndWar(clan, enemy);
		war.dispose();
		return true;
	}

	public void resExp() {
		int oldLevel = getLevel();
		int needExp = ExpTable.getNeedExpNextLevel(oldLevel);
		int exp = 0;
		double ratio;

		if (oldLevel < 45)
			ratio = 0.05;
		else if (oldLevel >= 49)
			ratio = 0.025;
		else
			ratio = 0.05 - (oldLevel - 44) * 0.005;

		exp = (int) (needExp * ratio);

		if (exp == 0)
			return;

		addExp(exp);
	}

	public void resExpToTemple() {
		int oldLevel = getLevel();
		int needExp = ExpTable.getNeedExpNextLevel(oldLevel);
		int exp = 0;
		double ratio;
        ratio = 0.025;

		exp = (int) (needExp * ratio);
		if (exp == 0)
			return;

		addExp(exp);
	}

	public void deathPenalty() {
		int oldLevel = getLevel();
		int needExp = ExpTable.getNeedExpNextLevel(oldLevel);
		int exp = 0;
		if (oldLevel >= 1 && oldLevel < 11)
			exp = 0;
		else if (oldLevel >= 11 && oldLevel < 45)
			exp = (int) (needExp * 0.1);
		else if (oldLevel == 45)
			exp = (int) (needExp * 0.09);
		else if (oldLevel == 46)
			exp = (int) (needExp * 0.08);
		else if (oldLevel == 47)
			exp = (int) (needExp * 0.07);
		else if (oldLevel == 48)
			exp = (int) (needExp * 0.06);
		else if (oldLevel >= 49)
			exp = (int) (needExp * 0.05);

		if (exp == 0)
			return;

		addExp(-exp);
	}


	public L1BookMark getBookMark(String name) {
		L1BookMark element = null;
		int size = _bookmarks.size();
		for (int i = 0; i < size; i++) {
			element = _bookmarks.get(i);
			if (element == null)
				continue;
			if (element.getName().equalsIgnoreCase(name)) {
				return element;
			}
		}
		return null;
	}

	public L1BookMark getBookMark(int id) {
		L1BookMark element = null;
		int size = _bookmarks.size();
		for (int i = 0; i < size; i++) {
			element = _bookmarks.get(i);
			if (element == null)
				continue;
			if (element.getId() == id) {
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

	public L1ItemInstance getWeapon() {
		return _weapon;
	}

	public void setWeapon(L1ItemInstance weapon) {
		_weapon = weapon;
	}

	public L1ItemInstance getWeaponSwap() {
		return getEquipSlot().getWeaponSwap();
	}

	public L1ItemInstance getArmor() {
		return _armor;
	}

	public void setArmor(L1ItemInstance armor) {
		_armor = armor;
	}

	public L1ItemInstance getSecondWeapon() {
		return _secondweapon;
	}

	public void setSecondWeapon(L1ItemInstance weapon) {
		_secondweapon = weapon;
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
			return "엘프";
		} else if (isWizard()) {
			return "마법사";
		} else if (isDarkelf()) {
			return "다크엘프";
		} else if (isDragonknight()) {
			return "용기사";
		} else if (isBlackwizard()) {
			return "환술사";
		} else if (isWarrior()) {
			return "전사";
		}

		return "직업명";
	}

	public int getClassNumber() {
		if (isCrown()) {
			return 0;
		} else if (isKnight()) {
			return 1;
		} else if (isElf()) {
			return 2;
		} else if (isWizard()) {
			return 3;
		} else if (isDarkelf()) {
			return 4;
		} else if (isDragonknight()) {
			return 5;
		} else if (isBlackwizard()) {
			return 6;
		} else {
			return 7;
		}
	}

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

	public boolean isDragonknight() {
		return (getClassId() == CLASSID_DRAGONKNIGHT_MALE || getClassId() == CLASSID_DRAGONKNIGHT_FEMALE);
	}

	public boolean isBlackwizard() {
		return (getClassId() == CLASSID_BLACKWIZARD_MALE || getClassId() == CLASSID_BLACKWIZARD_FEMALE);
	}

	public boolean isWarrior() {
		return (getClassId() == CLASSID_전사_MALE || getClassId() == CLASSID_전사_FEMALE);
	}

	public String getAccountName() {
		return _accountName;
	}

	public void setAccountName(String s) {
		_accountName = s;
	}

	public short getBaseMaxHp() {
		return _baseMaxHp;
	}

	public void addBaseMaxHp(short i) {
        final int next = Math.max(1, Math.min(32767, (int) _baseMaxHp + i));
        addMaxHp(next - _baseMaxHp);
        _baseMaxHp = (short) next;
    }

	public int getBaseMaxMp() {
		return _baseMaxMp;
	}

	public void addBaseMaxMp(int i) {
		i += _baseMaxMp;
		if (i >= 32767) {
			i = 32767;
		} else if (i < 0) {
			i = 0;
		}
		addMaxMp(i - _baseMaxMp);
		_baseMaxMp = i;
	}

	public int getOriginalMagicHit() {
		return _originalMagicHit;
	}

	public int getBaseAc() {
		return _baseAc;
	}

	public int getBaseDmgup() {
		return _baseDmgup;
	}

	public int getBaseBowDmgup() {
		return _baseBowDmgup;
	}

	public int getBaseHitup() {
		return _baseHitup;
	}

	public int getBaseBowHitup() {
		return _baseBowHitup;
	}

	public void setBaseMagicHitUp(int i) {
		_baseMagicHitup = i;
	}

	public void addBaseMagicHitUp(int i) {
		_baseMagicHitup += i;
	}

	public int getBaseMagicHitUp() {
		return _baseMagicHitup;
	}

	public int getTotalMagicHitup() {
		return getBaseAbility().get마법적중() + getBaseMagicHitUp() + getOriginalMagicHit();
	}

	public void setBaseMagicCritical(int i) {
		_baseMagicCritical = i;
	}

	public int getBaseMagicCritical() {
		return getBaseAbility().get마법치명타() + _baseMagicCritical;
	}

	public void addBaseMagicCritical(int i) {
		_baseMagicCritical += i;
	}

	public void setBaseMagicDmg(int i) {
		_baseMagicDmg = i;
	}

	public int getBaseMagicDmg() {
		return getBaseAbility().get마법대미지() + _baseMagicDmg;
	}

	public void setBaseMagicDecreaseMp(int i) {
		_baseMagicDecreaseMp = i;
	}

	public int getBaseMagicDecreaseMp() {
		return _baseMagicDecreaseMp;
	}

	public int getAdvenHp() {
		return _advenHp;
	}

	public void setAdvenHp(int i) {
		_advenHp = i;
	}

	public int getAdvenMp() {
		return _advenMp;
	}

	public void setAdvenMp(int i) {
		_advenMp = i;
	}

	public int getMagicBuffHp() {
		return _magicBuffHp;
	}

	public void setMagicBuffHp(int i) {
		_magicBuffHp = i;
	}
	
	
	
	public int getMagicBuffMp() {
		return _magicBuffMp;
	}

	public void setMagicBuffMp(int i) {
		_magicBuffMp = i;
	}
	
	
	
	
	public int getMagicBuffMR() {
		return _magicBuffMR;
	}

	public void setMagicBuffMR(int i) {
		_magicBuffMR = i;
	}
	
	
	
	
	public int getMagicBuffSP() {
		return _magicBuffSP;
	}

	public void setMagicBuffSP(int i) {
		_magicBuffSP = i;
	}
	
	
	
	public int getMagicBuffDG() {
		return _magicBuffDG;
	}

	public void setMagicBuffDG(int i) {
		_magicBuffDG = i;
	}
	
	
	
	
	public int getMagicBuffER() {
		return _magicBuffER;
	}

	public void setMagicBuffER(int i) {
		_magicBuffER = i;
	}
	
	
	public int getHighLevel() {
		return _highLevel;
	}

	public void setHighLevel(int i) {
		_highLevel = i;
	}

	public int getBonusStats() {
		return _bonusStats;
	}

	public void setBonusStats(int i) {
		_bonusStats = i;
	}

	public int getElixirStats() {
		return _elixirStats;
	}

	public void setElixirStats(int i) {
		_elixirStats = i;
	}

	public int getElfAttr() {
		return _elfAttr;
	}

	public void setElfAttr(int i) {
		_elfAttr = i;
	}

	public int getExpRes() {
		return _expRes;
	}

	public void setExpRes(int i) {
		_expRes = i;
	}

	public int getPartnerId() {
		return _partnerId;
	}

	public void setPartnerId(int i) {
		_partnerId = i;
	}

	public int getOnlineStatus() {
		return _onlineStatus;
	}

	public void setOnlineStatus(int i) {
		_onlineStatus = i;
	}

	public int getHomeTownId() {
		return _homeTownId;
	}

	public void setHomeTownId(int i) {
		_homeTownId = i;
	}

	public int getContribution() {
		return _contribution;
	}

	public void setContribution(int i) {
		_contribution = i;
	}

	private boolean _morning = false;

	public void setMorning(boolean flag) {
		this._morning = flag;
	}

	public boolean getMorning() {
		return _morning;
	}

	public boolean isBanned() {
		return _banned;
	}

	public void setBanned(boolean flag) {
		_banned = flag;
	}

	public int get_food() {
		return _food;
	}

	public void set_food(int i) {
		_food = i;
	}

	/** 생존의외침 **/
	public void add_food(int i) {
		_food += i;
		if (_food > 225) {
			_food = 225;
			if (getCryOfSurvivalTime() == 0)
				_cryofsurvivaltime = System.currentTimeMillis() / 1000;
		} else if (_food < 1) {
			_food = 1;
		}
	}

	private long _cryofsurvivaltime;

	public long getCryOfSurvivalTime() {
		return _cryofsurvivaltime;
	}

	public void setCryOfSurvivalTime() {
		if (get_food() >= 225) {
			_cryofsurvivaltime = System.currentTimeMillis() / 1000;

		}
	}

	/** 생존의외침 **/

	public L1EquipmentSlot getEquipSlot() {
		return _equipSlot;
	}

	public static L1PcInstance load(String charName) {
		L1PcInstance result = null;
		try {
			result = CharacterTable.getInstance().loadCharacter(charName);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return result;
	}

	public void save() {
		try {
			if (isGhost()) {
				return;
			}
			if (noPlayerCK || noPlayerRobot || noPlayerck2)
				return;

			CharacterTable.getInstance().storeCharacter(this);
		} catch (RuntimeException e) {
			// The scheduler must observe failed submission and retry.
			throw e;
		} catch (Exception e) {
			throw new IllegalStateException("Character save admission failed: " + getId(), e);
		}
	}

	/** 로봇관련 빽섭 수정 **/
	public void saveInventory() {
		if (noPlayerCK || noPlayerRobot || noPlayerck2)
			return;
		for (L1ItemInstance item : getInventory().getItems()) {
			if (item != null)
				getInventory().saveItem(item, item.getRecordingColumns() != 0 ? L1PcInventory.COL_SAVE_ALL : 0);
		}
	}

	private int _maxweight = 0;

	public int getMaxWeight() {
		try {
			_maxweight = CalcStat.최대소지무게(getAbility().getTotalStr(), getAbility().getTotalCon());
			_maxweight += getWeightReduction();
		} catch (Exception e) {
			e.printStackTrace();
		}
		return _maxweight;
	}

	public boolean isFastMovable() {
		return (hasSkillEffect(L1SkillId.HOLY_WALK) || hasSkillEffect(L1SkillId.MOVING_ACCELERATION));
	}

	public boolean isBlood_lust() {
		return hasSkillEffect(L1SkillId.BLOOD_LUST);
	}

	public int getBraveSpeed() {
		// 환술 용기속도
		if (hasSkillEffect(L1SkillId.STATUS_FRUIT))
			return isPassive(MJPassiveID.DARK_HORSE.toInt()) ? 3 : 4;
		return super.getBraveSpeed();
	}

	public boolean isBrave() {
		return (hasSkillEffect(L1SkillId.STATUS_BRAVE) );
	}

	public boolean isElfBraveMagicShort() {
		return hasSkillEffect(L1SkillId.DANCING_BLADES) || hasSkillEffect(L1SkillId.SAND_STORM);
	}

	public boolean isElfBraveMagicLong() {
		return hasSkillEffect(L1SkillId.HURRICANE);
	}
	public boolean istest() {
		return hasSkillEffect(L1SkillId.FOCUS_WAVE);
	}
	public boolean isDragonPearl() {
		return (hasSkillEffect(L1SkillId.STATUS_DRAGON_PEARL) || hasSkillEffect(L1SkillId.잠재력진주)||  getPearl() == 1);
	}

	public boolean isElfBrave() {
		return (hasSkillEffect(L1SkillId.STATUS_ELFBRAVE));
	}

	public boolean isFruit() {
		return hasSkillEffect(L1SkillId.STATUS_FRUIT);
	}

	private int _pearl;

	public int getPearl() {
		return _pearl;
	}

	public void setPearl(int i) {
		_pearl = i;
	}
	
	private static final long DELAY_INVIS = 3000L;

	private volatile long _invisDelayUntil = 0L;

	public void beginInvisTimer() {
	    _invisDelayUntil = System.currentTimeMillis() + DELAY_INVIS;
	}

	public boolean isInvisDelay() {
	    return System.currentTimeMillis() < _invisDelayUntil;
	}

	public void clearInvisDelay() {
	    _invisDelayUntil = 0L;
	}


    public void addExp(int exp) {
        // Same lock/order as character snapshots: EXP, level, earned growth and
        // totals form one captured state. Do not hold this monitor while waiting
        // for the user lock (snapshot getters also use this monitor).
        lockUserState();
        try {
	    if (Config.STANDBY_SERVER) {
	        return;
	    }

	    final int oldExp = _exp;

	    _exp += exp;

	    if (_exp > ExpTable.MAX_EXP) {
	        _exp = ExpTable.MAX_EXP;
	    }

	    if (_exp != oldExp) {
	        onChangeExp();
	    }

        } finally { unlockUserState(); }
    }

	public void onChangeLawful() {
	    final int lawful = getLawful();

	    SC_Lawful.send(this, getId(), lawful);
	    SC_Lawful.broadcastFast(this, getId(), lawful);

	    if (Config.바포시스템) {
	        CIOThreadCallback.LawfulBonus(this);
	    }
	}
	
	@Override
	public void addLawful(int value) {
	    final int oldLawful = getLawful();

	    super.addLawful(value);

	    if (getLawful() != oldLawful) {
	        onChangeLawful();
	    }
	}

	public synchronized void addContribution(int contribution) {
		_contribution += contribution;
	}

	public static void remove_skills(L1PcInstance pc, Integer[] skills) {
		for(Integer skillId : skills) {
			if(pc.hasSkillEffect(skillId)) {
				pc.removeSkillEffect(skillId);
			}
		}
	}
	
    // Level-gift completion is not a normal quest. Bit zero in the durable
    // ledger means this character's legacy quest flags have been reviewed once.
    // The login DB worker loads this snapshot before the CIO owner uses it.
    private final java.util.BitSet _settledLevelGifts = new java.util.BitSet();
    private boolean _levelGiftMigrated;
    private boolean _levelGiftSnapshotLoaded;

    public static int[] readLevelGiftSnapshot(int characterId) {
        int[] levels = new int[8];
        int count = 0;
        try (java.sql.Connection con = l1j.server.DatabaseFactory.getInstance().getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT gift_level FROM character_level_gifts WHERE char_id=?")) {
            ps.setInt(1, characterId);
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    final int giftLevel = rows.getInt(1);
                    if (rows.wasNull())
                        throw new java.sql.SQLException("NULL persisted gift level: " + characterId);
                    // BitSet.set() may allocate an enormous array for one
                    // corrupt database value. Level zero is the migration
                    // marker; all real gift levels must fit the EXP table.
                    if (giftLevel < 0 || giftLevel > ExpTable.MAX_LEVEL)
                        throw new java.sql.SQLException("Invalid persisted gift level: " + giftLevel);
                    if (count == levels.length)
                        levels = java.util.Arrays.copyOf(levels, levels.length << 1);
                    levels[count++] = giftLevel;
                }
            }
        } catch (java.sql.SQLException failure) {
            throw new IllegalStateException("Level gift ledger load failed: " + characterId, failure);
        }
        return java.util.Arrays.copyOf(levels, count);
    }

    public void applyLevelGiftSnapshot(int[] levels) {
        if (levels == null) throw new IllegalArgumentException("Missing level gift ledger snapshot");
        // Validate the *entire* DB-worker snapshot before changing any live
        // state. A rejected snapshot must never partially replace the claims
        // that protect an already logged-in character from duplicate gifts.
        for (int level : levels)
            if (level < 0 || level > ExpTable.MAX_LEVEL)
                throw new IllegalArgumentException("Invalid level gift ledger level: " + level);
        _settledLevelGifts.clear();
        _levelGiftMigrated = false;
        for (int level : levels) {
            if (level == 0) _levelGiftMigrated = true;
            else _settledLevelGifts.set(level);
        }
        _levelGiftSnapshotLoaded = true;
    }

    /** Grant rewards and mark their independent durable ledger in one receipt.
     * The first settlement imports historical QUEST_END flags conservatively,
     * without rewriting any regular quest. Unclear legacy overlaps are not
     * guessed to be unpaid: they stay marked claimed to prevent duplicate items.
     */
    public void settleOutstandingLevelGifts() {
        final CIOClient session = getNetConnection();
        if (!_levelGiftSnapshotLoaded || session == null || !session.isConnected()
                || session.getActiveChar() != this || isItemTransferBusy()
                || getTradeID() != 0 || isTradeResolving()) return;
        final int characterId = getId();
        final boolean migrate = !_levelGiftMigrated;
        final ArrayList<Integer> due = new ArrayList<>();
        final ArrayList<Integer> legacy = new ArrayList<>();
        boolean hasEligible = false;
        for (int lv = 1, level = getLevel(); lv <= level; lv++) {
            CharactersGiftItemTable.Item[] gifts = CharactersGiftItemTable.getInstance().getItems(lv);
            if (gifts == null) continue;
            boolean eligible = false;
            for (CharactersGiftItemTable.Item gift : gifts) {
                if (gift != null && gift.getType() == getType()) {
                    eligible = true;
                    break;
                }
            }
            if (!eligible) continue;
            hasEligible = true;
            if (_settledLevelGifts.get(lv)) continue;
            if (migrate && getQuest().get_step(lv) == L1Quest.QUEST_END)
                legacy.add(lv);
            else due.add(lv);
        }
        if (!hasEligible || (due.isEmpty() && legacy.isEmpty() && !migrate)) return;
        try (l1j.server.server.model.L1InventoryTransaction tx =
                l1j.server.server.model.L1InventoryTransaction.begin(this,
                        CacheD.accountOrderKey("level-gifts:" + characterId))) {
            for (int lv : due) {
                CharactersGiftItemTable.Item[] gifts = CharactersGiftItemTable.getInstance().getItems(lv);
                if (gifts == null) throw new IllegalStateException("Level gift configuration changed: " + lv);
                boolean eligible = false;
                for (CharactersGiftItemTable.Item gift : gifts) {
                    if (gift == null || gift.getType() != getType()) continue;
                    eligible = true;
                    final l1j.server.server.templates.L1Item template =
                            ItemTable.getInstance().getTemplate(gift.getItemId());
                    if (template == null || gift.getCount() <= 0)
                        throw new IllegalStateException("Invalid level gift: " + lv);
                    L1ItemInstance reward = new L1ItemInstance(template, gift.getCount());
                    reward.setId(l1j.server.server.IdFactory.getInstance().nextId());
                    reward.setEnchantLevel(gift.getEnchant());
                    reward.setAttrEnchantLevel(gift.getAttrLevel());
                    reward.setBless(gift.getBless());
                    reward.setIdentified(true);
                    if (tx.grant(reward) == null)
                        throw new IllegalStateException("Level gift inventory capacity: " + lv);
                }
                if (!eligible)
                    throw new IllegalStateException("Level gift class configuration changed: " + lv);
            }
            tx.requireTables("character_level_gifts");
            tx.sql(con -> {
                if (migrate) {
                    // A second session must not silently replay a stale snapshot.
                    try (PreparedStatement lock = con.prepareStatement(
                            "SELECT gift_level FROM character_level_gifts WHERE char_id=? AND gift_level=0 FOR UPDATE")) {
                        lock.setInt(1, characterId);
                        try (ResultSet rows = lock.executeQuery()) {
                            if (rows.next()) throw new java.sql.SQLException("Level gift legacy migration already completed");
                        }
                    }
                    for (int lv : legacy) {
                        try (PreparedStatement lock = con.prepareStatement(
                                "SELECT quest_step FROM character_quests WHERE char_id=? AND quest_id=? FOR UPDATE")) {
                            lock.setInt(1, characterId);
                            lock.setInt(2, lv);
                            try (ResultSet rows = lock.executeQuery()) {
                                if (!rows.next())
                                    throw new java.sql.SQLException("Legacy level gift step missing: " + lv);
                                final int step = rows.getInt(1);
                                if (rows.wasNull() || step != L1Quest.QUEST_END)
                                    throw new java.sql.SQLException("Legacy level gift step changed: " + lv);
                            }
                        }
                    }
                    for (int lv : due) {
                        // A legacy QUEST_END arriving after the login snapshot
                        // cannot be mistaken for permission to award twice.
                        try (PreparedStatement lock = con.prepareStatement(
                                "SELECT quest_step FROM character_quests WHERE char_id=? AND quest_id=? FOR UPDATE")) {
                            lock.setInt(1, characterId);
                            lock.setInt(2, lv);
                            try (ResultSet rows = lock.executeQuery()) {
                                if (rows.next()) {
                                    final int step = rows.getInt(1);
                                    if (rows.wasNull())
                                        throw new java.sql.SQLException("NULL legacy gift quest step: " + lv);
                                    if (step == L1Quest.QUEST_END)
                                        throw new java.sql.SQLException("Legacy gift completed during settlement: " + lv);
                                }
                            }
                        }
                    }
                }
                // The PRIMARY KEY(char_id,level) rejects any duplicate, including
                // concurrent logins, rather than updating or overwriting rows.
                try (PreparedStatement write = con.prepareStatement(
                        "INSERT INTO character_level_gifts (char_id,gift_level) VALUES (?,?)")) {
                    write.setInt(1, characterId);
                    for (int lv : legacy) { write.setInt(2, lv); write.executeUpdate(); }
                    for (int lv : due) { write.setInt(2, lv); write.executeUpdate(); }
                    if (migrate) { write.setInt(2, 0); write.executeUpdate(); }
                }
            });
            tx.onFailure(() -> _log.warning("Level gift settlement rolled back: " + characterId));
            tx.commit(() -> {
                if (migrate) _levelGiftMigrated = true;
                for (int lv : legacy) _settledLevelGifts.set(lv);
                for (int lv : due) {
                    _settledLevelGifts.set(lv);
                    SC_ChatPacket.sendDirect(this, "Level(" + lv + ")퀘스트를 완료 하였습니다.");
                }
            });
        } catch (RuntimeException failure) {
            _log.log(Level.WARNING, "Level gift settlement deferred: " + characterId, failure);
        }
    }

	/** 해당레벨 이상시 조건을 출력한다 **/
    private l1j.server.server.model.HpMpGrowthHistory hpMpGrowth;
    private boolean warnedLegacyGrowth;

    public String encodeHpMpGrowth() {
        lockUserState();
        try { return hpMpGrowth == null ? null : hpMpGrowth.encode(); }
        finally { unlockUserState(); }
    }
    public void restoreHpMpGrowth(String encoded) {
        lockUserState();
        try { hpMpGrowth = l1j.server.server.model.HpMpGrowthHistory.decode(encoded); }
        finally { unlockUserState(); }
    }
    public void completeStatResetGrowth(l1j.server.server.model.HpMpGrowthHistory history) {
        lockUserState();
        try { hpMpGrowth = history; warnedLegacyGrowth = false; }
        finally { unlockUserState(); }
    }
    /** Called on the character owner; same snapshot/SQL stores deltas and totals. */
    public void applyHpMpLevelTransition(int from, int to) {
        lockUserState();
        try { applyHpMpLevelTransitionOwned(from, to); }
        finally { unlockUserState(); }
    }
    private void applyHpMpLevelTransitionOwned(int from, int to) {
        if (from < 1 || to < 1 || from > 127 || to > 127)
            throw new IllegalArgumentException("Invalid HP/MP level transition");
        if (hpMpGrowth == null) hpMpGrowth = new l1j.server.server.model.HpMpGrowthHistory(from);
        final int step = to > from ? 1 : -1;
        for (int level = from; level != to;) {
            int target = level + step;
            int earnedLevel = step > 0 ? target : level;
            if (earnedLevel <= hpMpGrowth.floorLevel()) {
                // Unknown legacy levels are preserved, not randomly subtracted.
                // Regaining these same levels grants no second HP/MP reward.
                if (!warnedLegacyGrowth) {
                    _log.warning("HP/MP legacy growth retained below history baseline: char=" + getId());
                    warnedLegacyGrowth = true;
                }
            } else if (step < 0) {
                addBaseMaxHp((short) -hpMpGrowth.hp(earnedLevel));
                addBaseMaxMp(-hpMpGrowth.mp(earnedLevel));
            } else if (hpMpGrowth.known(earnedLevel)) {
                addBaseMaxHp((short) hpMpGrowth.hp(earnedLevel));
                addBaseMaxMp(hpMpGrowth.mp(earnedLevel));
            } else {
                int oldHp = getBaseMaxHp(), oldMp = getBaseMaxMp();
                addBaseMaxHp(CalcStat.increaseHp(getType(), getAbility().getCon()));
                addBaseMaxMp(CalcStat.increaseMp(getType(), getAbility().getWis()));
                hpMpGrowth.record(earnedLevel, getBaseMaxHp() - oldHp, getBaseMaxMp() - oldMp);
            }
            level = target;
        }
    }

    private void updateLevelAndGrowth(int gap) {
        lockUserState();
        try {
            resetLevel();
            applyHpMpLevelTransitionOwned(getLevel() - gap, getLevel());
        } finally { unlockUserState(); }
    }

	private void levelUp(int gap) {
        updateLevelAndGrowth(gap);

		SC_RestExpInfo.send(this);
		RankUserLoader.getInstance().onUser(this);

		/** 특정렙 이상 초보혈맹 자동탈퇴 **/
		String BloodName = getClanname();
		if (getLevel() >= Config.신규혈맹보호레벨 && BloodName.equalsIgnoreCase(Config.신규혈맹이름)
				|| BloodName.equalsIgnoreCase("초보")) {
			try {
				// L1Clan clan = L1World.getInstance().findClan(Config.신규혈맹이름);
				L1Clan clan = L1World.getInstance().findClan("중립");
				L1PcInstance clanMember[] = clan.getOnlineClanMember();
				String player_name = getName();
				String clan_name = getClanname();
				for (int i = 0; i < clanMember.length; i++) {
					SC_ServerMessage.send(clanMember[i], ServerMessage.LEAVE_CLAN, player_name, clan_name);
				}
				ClearPlayerClanData(clan);
				clan.removeClanMember(player_name);
				this.start_teleport(this.getX(), this.getY(), this.getMapId(), this.getHeading(), 169, false, false);
				save();
				saveInventory();
			} catch (Exception e) {
			}
		}
		
		if (hasSkillEffect(L1SkillId.어드밴스)) {
		if (hasSkillEffect(L1SkillId.ADVANCE_SPIRIT)) {
			removeSkillEffect(L1SkillId.ADVANCE_SPIRIT);
		} else {
		addMaxHp(-getAdvenHp());
		addMaxMp(-getAdvenMp());
		setAdvenHp(getBaseMaxHp() / 5);
		setAdvenMp(getBaseMaxMp() / 5);
		addMaxHp(getAdvenHp());
		addMaxMp(getAdvenMp());
		if (isInParty()) {
			//TODO 파티 프로토
			getParty().refreshPartyMemberStatus(this);
		}
		}
		SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
		SC_MpUpdate.send(this, getCurrentMp(), getMaxMp());
		
		}
		
		if (hasSkillEffect(L1SkillId.프라이드)) {
			addMaxHp(-getMagicBuffHp());
			setMagicBuffHp(0);
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
			double percent = getLevel() / 4;
			int addHp = (int) Math.round(getBaseMaxHp() * (percent * 0.01));
			setMagicBuffHp(addHp);
			addMaxHp(getMagicBuffHp());
			if (isInParty()) {
				//TODO 파티 프로토
				getParty().refreshPartyMemberStatus(this);
			}
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
		}
		
		if (hasSkillEffect(L1SkillId.기간틱)) {
			addMaxHp(-getMagicBuffHp());
			setMagicBuffHp(0);
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
			double percent = getLevel() / 2;
			int addHp = (int) Math.round(getBaseMaxHp() * (percent * 0.01));
			setMagicBuffHp(addHp);
			addMaxHp(getMagicBuffHp());
			if (isInParty()) {
				//TODO 파티 프로토
				getParty().refreshPartyMemberStatus(this);
			}
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
		}
		
		if (getLevel() > 90 && getTitle().contains(Config.GAME_SERVER_NAME)) {
			setTitle("");
			SC_CharTitle.send(this, getId(), "");
			SC_CharTitle.broadcast(this, getId(), "");
		}


		this.setCurrentHp(getMaxHp());
		this.setCurrentMp(getMaxMp());

		
		resetBaseAc();
		resetBaseMr();
		if (getLevel() > getHighLevel() && getReturnStat() == 0) {
			setHighLevel(getLevel());
		}

		try {
			save();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Level gifts and their completion flags must share one durable receipt.
		// Also retried after inventory restoration on login, even if DB level
		// was saved just before a previous crash interrupted this settlement.
		settleOutstandingLevelGifts();
		L1Quest quest = getQuest();
		// TODO 스냅퍼 개방
		int lv59_step = quest.get_step(L1Quest.QUEST_SLOT76);
		if (getLevel() == 59 && lv59_step != L1Quest.QUEST_END) {
			SC_PacketBox.send(this, SC_PacketBox.GREEN_MESSAGE, "Lv.59 귀걸이개방이 가능해졌습니다.");
			SC_NewCreateItem.sendSubtype(this, SC_NewCreateItem.신규패킷10, 0);
		}
		int lv76_step = quest.get_step(L1Quest.QUEST_SLOT76);
		if (getLevel() == 76 && lv76_step != L1Quest.QUEST_END) {
			SC_PacketBox.send(this, SC_PacketBox.GREEN_MESSAGE, "Lv.76 반지개방이 가능해졌습니다.");
		}
		int lv81_step = quest.get_step(L1Quest.QUEST_SLOT81);
		if (getLevel() == 81 && lv81_step != L1Quest.QUEST_END) {
			SC_PacketBox.send(this, SC_PacketBox.GREEN_MESSAGE, "Lv.81 반지개방이 가능해졌습니다.");
		}

		if (getLevel() >= 51 && getLevel() - 50 > getBonusStats() && getAbility().getAmount() < 210) {
			int upstat = (getLevel() - 50) - (getBonusStats());
			String s = Integer.toString(upstat);
			SC_MessageYN.send(this, 479, s);
		}
		if (getLevel() > 1 && getLevel() <= Config.Lineage_Buff) {
			SC_InventoryIcon.iconNewUnLimit(this, L1SkillId.HUNTER_BLESS3, 4126, true);
		} else {
			SC_InventoryIcon.icoEnd(this, L1SkillId.HUNTER_BLESS3);
		}

		if (getLevel() > 1 && getLevel() <= Config.Start_Char_Boho) {
			SC_InventoryIcon.iconNewUnLimit(this, L1SkillId.Start_BUFF, 3804, true);
		} else {
			SC_InventoryIcon.icoEnd(this, L1SkillId.Start_BUFF);
		}

		if (getLevel() >= 75) { // 천상의 계곡
			if (getMapId() == 1911 || getMapId() == 1912) {
				this.start_teleport(33438, 32805, 4, 5, 169, true, false);
			}
		}
		if (getLevel() >= Config.New_Cha1) { // 말하는섬 던전
			if ((getMapId() >= 1 && getMapId() <= 2) || getMapId() == 785) {
				int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_GIRAN);
				this.start_teleport(loc[0], loc[1], loc[2], this.getHeading(), 169, true, false);
			}
		}
		if (getLevel() >= 70) { // 본섭은 52렙까지 가능하다.
			if (getMapId() == 777) { // 버림받은 사람들의 땅(그림자의 신전)
				this.start_teleport(34043, 32184, 4, 5, 169, true, false);
			} else if (getMapId() == 778 || getMapId() == 779) {
				this.start_teleport(32608, 33178, 4, 5, 169, true, false);
			} else if (getMapId() == 2010) {
				int[] loc = L1TownLocation.getGetBackLoc(L1TownLocation.TOWNID_SILVER_KNIGHT_TOWN);
				this.start_teleport(loc[0], loc[1], loc[2], this.getHeading(), 169, true, false);
			}
		}


		setAttackSpeed();
		SC_OwnCharStatus.send(this);
	}

	private void levelDown(int gap) {
        updateLevelAndGrowth(gap);

		SC_RestExpInfo.send(this);

		resetBaseAll();
		resetBaseAc();
		resetBaseMr();

		if (!isGm() && Config.LEVEL_DOWN_RANGE != 0) {
			if (getHighLevel() - getLevel() == Config.LEVEL_DOWN_RANGE - 1) {
				SC_SystemMessage.send(this, "한번 더 레벨 다운시 캐릭터가 압류 됩니다.");

			}
			if (!isGm() && getHighLevel() - getLevel() >= Config.LEVEL_DOWN_RANGE) {
				SC_ServerMessage.send(this, 64);
				SC_Disconnect.send(this);
				_log.info(String.format("레벨 다운의 허용 범위를 넘었기 때문에 %s를 강제 절단 했습니다.", getName()));
			}
		}
		if (hasSkillEffect(L1SkillId.어드밴스)) {
			if (hasSkillEffect(L1SkillId.ADVANCE_SPIRIT)) {
				removeSkillEffect(L1SkillId.ADVANCE_SPIRIT);
			} else {
			addMaxHp(-getAdvenHp());
			addMaxMp(-getAdvenMp());
			setAdvenHp(getBaseMaxHp() / 5);
			setAdvenMp(getBaseMaxMp() / 5);
			addMaxHp(getAdvenHp());
			addMaxMp(getAdvenMp());
			if (isInParty()) {
				//TODO 파티 프로토
				getParty().refreshPartyMemberStatus(this);
			}
			}
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
			SC_MpUpdate.send(this, getCurrentMp(), getMaxMp());
			
			}
		if (hasSkillEffect(L1SkillId.프라이드)) {
			addMaxHp(-getMagicBuffHp());
			setMagicBuffHp(0);
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
			double percent = getLevel() / 4;
			int addHp = (int) Math.round(getBaseMaxHp() * (percent * 0.01));
			setMagicBuffHp(addHp);
			addMaxHp(getMagicBuffHp());
			if (isInParty()) {
				//TODO 파티 프로토
				getParty().refreshPartyMemberStatus(this);
			}
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
		}
		if (hasSkillEffect(L1SkillId.기간틱)) {
			addMaxHp(-getMagicBuffHp());
			setMagicBuffHp(0);
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
			double percent = getLevel() / 2;
			int addHp = (int) Math.round(getBaseMaxHp() * (percent * 0.01));
			setMagicBuffHp(addHp);
			addMaxHp(getMagicBuffHp());
			if (isInParty()) {
				//TODO 파티 프로토
				getParty().refreshPartyMemberStatus(this);
			}
			SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
		}
		
		try {
			save();
		} catch (Exception e) {
			e.printStackTrace();
		}
		setAttackSpeed();
		SC_OwnCharStatus.send(this);
	}

	private L1GameTimeCarrier _gameTimeCarrier;

	public void beginGameTimeCarrier() {
	    stopGameTimeCarrier();
	    _gameTimeCarrier = new L1GameTimeCarrier(this);
	    _gameTimeCarrier.start();
	}

	public void stopGameTimeCarrier() {
	    if (_gameTimeCarrier != null) {
	        _gameTimeCarrier.stop();
	        _gameTimeCarrier = null;
	    }
	}
	public boolean isGhost() {
		return _ghost;
	}

	public void setGhost(boolean flag) {
		_ghost = flag;
	}

	public boolean isGhostCanTalk() {
		return _ghostCanTalk;
	}

	public void setGhostCanTalk(boolean flag) {
		_ghostCanTalk = flag;
	}

	public boolean isReserveGhost() {
		return _isReserveGhost;
	}

	public void setReserveGhost(boolean flag) {
		_isReserveGhost = flag;
	}

	public void beginGhost(int locx, int locy, short mapid, boolean canTalk) {
		beginGhost(locx, locy, mapid, canTalk, 0);
	}

	private transient CIOThreadCallback.Handle _ghostHandle;
	
	public void beginGhost(int locx, int locy, short mapid, boolean canTalk, int sec) {
		if (isGhost()) {
			return;
		}
		_ghostSaveLocX = getX();
		_ghostSaveLocY = getY();
		_ghostSaveMapId = getMapId();
		setGhost(true);
		setGhostCanTalk(canTalk);
		setReserveGhost(false);
		this.start_teleport(locx, locy, mapid, getHeading(), 169, true, false);
		if (sec > 0) {
			final int stamp = getSessionStamp();
			CIOThreadCallback.getInstance().scheduleEndGhost(getId(), stamp, sec * 1000L);
		}
	}


	public void DeathMatchEndGhost() {
		setReserveGhost(true);
		this.start_teleport(32614, 32735, 4, getHeading(), 169, true, false);
	}

	public void endGhost() {
	    CIOThreadCallback.Handle h = _ghostHandle;
	    if (h != null) {
	        h.cancel();
	        _ghostHandle = null;
	    }   
		setGhost(false);
		setGhostCanTalk(true);
		setReserveGhost(false);
		this.start_teleport(_ghostSaveLocX, _ghostSaveLocY, _ghostSaveMapId, _ghostSaveHeading, 169, true, false);
	}

	
	@Override
	public void setPoisonEffect(int effectId) {
		SC_Poison.send(this, getId(), effectId);
		if (!isGmInvis() && !isGhost() && !isInvisble()) {
			SC_Poison.broadcast(this, getId(), effectId);
		}
	}

    @Override
    public void healHp(int pt) {
        if (pt == 0) return;
        // A heal from a potion/trap may race another controller's damage.
        // Preserve the original HP/LFC/party notifications without a blind
        // read-modify-write that overwrites an intervening hit.
        final long hpChange = changeHpAtomicPair((long) pt);
        if (hpChange < 0L) return;
        final int oldHp = (int) (hpChange >>> 32);
        final int newHp = (int) hpChange;
        if (oldHp == newHp) return;
        if (getInstStatus() == InstStatus.INST_USERSTATUS_LFC && newHp > oldHp)
            addDamageFromLfc(newHp - oldHp);
        SC_HpUpdate.send(this, getCurrentHp(), getMaxHp());
        if (isInParty()) getParty().refreshPartyMemberStatus(this);
    }

	@Override
	public void addDg(int i) {
		super.addDg(i);
		SC_OwnCharAttrDef.send(this);
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

	public Timestamp getLastPk() {
		return _lastPk;
	}

	public void setLastPk(Timestamp time) {
		_lastPk = time;
	}

	public void setLastPk() {
		_lastPk = new Timestamp(System.currentTimeMillis());
	}

	public boolean isWanted() {
		if (_lastPk == null) {
			return false;
		} else if (System.currentTimeMillis() - _lastPk.getTime() > 24 * 3600 * 1000) {
			setLastPk(null);
			return false;
		}
		return true;
	}

	public Timestamp getDeleteTime() {
		return _deleteTime;
	}

	public void setDeleteTime(Timestamp time) {
		_deleteTime = time;
	}

	public Timestamp getLastLoginTime() {
		return _lastLoginTime;
	}

	public void setLastLoginTime(Timestamp time) {
		_lastLoginTime = time;
	}

	@Override
	public int getMagicLevel() {
		return getBaseAbility().get매직레벨();
	}

	public int getWeightReduction() {
		return _weightReduction;
	}

	public void addWeightReduction(int i) {
		_weightReduction += i;
		SC_Weight.send(this);
	}

	public int getHasteItemEquipped() {
		return _hasteItemEquipped;
	}

	public void addHasteItemEquipped(int i) {
		_hasteItemEquipped += i;
	}

	public void removeHasteSkillEffect() {
		if (hasSkillEffect(L1SkillId.SLOW))
			removeSkillEffect(L1SkillId.SLOW);
		if (hasSkillEffect(L1SkillId.HASTE))
			removeSkillEffect(L1SkillId.HASTE);
		if (hasSkillEffect(L1SkillId.GREATER_HASTE))
			removeSkillEffect(L1SkillId.GREATER_HASTE);
		if (hasSkillEffect(L1SkillId.STATUS_HASTE))
			removeSkillEffect(L1SkillId.STATUS_HASTE);
	}

	private Timestamp _tamTime;

	public Timestamp getTamTime() {
		return _tamTime;
	}
	public void setTamTime(Timestamp time) {
		_tamTime = time;
	}
	
	private final PcBaseAbilityCache _baseCache = new PcBaseAbilityCache();

	public PcBaseAbilityCache getBaseCache() {
		return _baseCache;
	}
	/** 베이스 근거리대미지(클래스/레벨) */
	public int getBaseMeleeDmgByLevelClass() {
	    return _baseCache.get().get근거리대미지();
	}
	/** 베이스 원거리대미지(클래스/레벨) */
	public int getBaseBowDmgByLevelClass() {
	    return _baseCache.get().get원거리대미지();
	}
	/** 베이스 마법대미지(클래스/레벨) */
	public int getBaseMagicDmgByLevelClass() {
	    return _baseCache.get().get마법대미지();
	}
	/** 베이스 근거리명중(클래스/레벨) */
	public int getBaseMeleeHitByLevelClass() {
	    return _baseCache.get().get근거리명중률();
	}
	/** 베이스 원거리명중(클래스/레벨) */
	public int getBaseBowHitByLevelClass() {
	    return _baseCache.get().get원거리명중률();
	}
	/** 베이스 마법적중(클래스/레벨) */
	public int getBaseMagicHitByLevelClass() {
	    return _baseCache.get().get마법적중();
	}
	/** 베이스 근거리치명(클래스/레벨) */
	public int getBaseMeleeCriByLevelClass() {
	    return _baseCache.get().get근거리치명률();
	}
	/** 베이스 원거리치명(클래스/레벨) */
	public int getBaseBowCriByLevelClass() {
	    return _baseCache.get().get원거리치명률();
	}
	/** 베이스 마법치명(클래스/레벨) */
	public int getBaseMagicCriByLevelClass() {
	    return _baseCache.get().get마법치명타();
	}
	/** 베이스 PVP DR(클래스/레벨) */
	public int getBasePvpDrByLevelClass() {
	    return _baseCache.get().getPVP대미지리덕션();
	}
	/** 베이스 매직레벨(클래스/레벨) */
	public int getBaseMLByLevelClass() {
	    return _baseCache.get().get매직레벨();
	}
	
    public void resetBaseAll() {
   	 _baseCache.invalidate();
   	 _baseCache.refresh(this);
   }
	
	public void resetBaseAc() {
		int newAc = CalcStat.물리방어력(getAbility().getTotalDex());
		final int div = getBaseAcLevelDiv();
		if (div > 0) {
			newAc -= getLevel() / div;
		}
		ac.addAc(newAc - _baseAc);
		_baseAc = newAc;
	}
	
	private int getBaseAcLevelDiv() {
	    if (isElf()) return 7;
	    if (isWizard() || isBlackwizard()) return 8;
	    if (isCrown() || isKnight() || isDarkelf() || isDragonknight() || isWarrior()) return 6;
	    return 0;
	}

	public void resetBaseMr() {
		int newMr = 0;
		newMr += CalcStat.마법방어(getType(), getAbility().getTotalWis());
		newMr += getLevel() / 2;
		SC_SPMR.send(this);
	}

	public void resetLevel() {
		setLevel(ExpTable.getLevelByExp(_exp));
	}

	public void refresh() {
		CheckChangeExp();
		resetLevel();
		resetBaseAll();
		resetBaseMr();
		resetBaseAc();	
	}

	public void checkChatInterval() {
		long nowChatTimeInMillis = System.currentTimeMillis();
		if (_chatCount == 0) {
			_chatCount++;
			_oldChatTimeInMillis = nowChatTimeInMillis;
			return;
		}

		long chatInterval = nowChatTimeInMillis - _oldChatTimeInMillis;
		if (chatInterval > 2000) {
			_chatCount = 0;
			_oldChatTimeInMillis = 0;
		} else {
			if (_chatCount >= 3) {
				setSkillEffect(L1SkillId.STATUS_CHAT_PROHIBITED, 120 * 1000);
				SC_SkillIconGFX.send(this, 36, 120);
				SC_ServerMessage.send(this, 153);
				_chatCount = 0;
				_oldChatTimeInMillis = 0;
			}
			_chatCount++;
		}
	}

	public void CheckChangeExp() {
		int level = ExpTable.getLevelByExp(getExp());
		int char_level = CharacterTable.getInstance().PcLevelInDB(getId());
		if (char_level == 0) {
			return;
		}
		int gap = level - char_level;
		if (gap == 0) {
			SC_OwnCharStatus.send(this);
			int percent = ExpTable.getExpPercentage(char_level, getExp());
			if (char_level >= 60 && char_level <= 64) {
				if (percent >= 10)
					removeSkillEffect(L1SkillId.레벨업보너스);
			} else if (char_level >= 65) {
				if (percent >= 5) {
					removeSkillEffect(L1SkillId.레벨업보너스);
				}
			}
			return;
		}

		if (gap > 0) {
			levelUp(gap);
			if (getLevel() >= 60) {
				setSkillEffect(L1SkillId.레벨업보너스, 10800000);
				SC_PacketBox.send(this, 10800, true, true);
			}
		} else if (gap < 0) {
			levelDown(gap);
			removeSkillEffect(L1SkillId.레벨업보너스);
		}
	}

	public void LoadCheckStatus() {
		int totalS = getAbility().getAmount();
		int bonusS = getHighLevel() - 50;
		if (bonusS < 0) {
			bonusS = 0;
		}

		//int calst = totalS - (bonusS + getElixirStats() + 75);
		int calst = totalS - (bonusS + getElixirStats() + 150);

		if (calst > 0 && !isGm()) {
			
			L1SkillUse.cast(
					this,
					L1SkillId.CANCELLATION,
					getId(), getX(), getY(),
					null,
					0,
					L1SkillUse.TYPE_LOGIN);
			
			if (getWeapon() != null) {
				getInventory().setEquipped(getWeapon(), false, false, false, false);
			}

			SC_CharVisualUpdate.sendPacked(this, SC_CharVisualUpdate.pack(this));
			SC_OwnCharStatus2.send(this);

			for (L1ItemInstance armor : getInventory().getItems()) {
				for (int type = 0; type <= 12; type++) {
					if (armor != null) {
						getInventory().setEquipped(armor, false, false, false, false);
					}
				}
			}

			setReturnStat(getExp());
			SC_SPMR.send(this);
			SC_OwnCharAttrDef.send(this);
			SC_OwnCharStatus2.send(this);
			SC_ReturnedStat.sendPc(this, SC_ReturnedStat.START);
			try {
				save();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public void CheckStatus() {
		int totalS = ability.getAmount();
		int bonusS = getLevel() - 50;
		if (bonusS < 0) {
			bonusS = 0;
		}

		//int calst = totalS - (bonusS + getElixirStats() + 75);
		int calst = totalS - (bonusS + getElixirStats() + 150);

		if (calst > 0 && !isGm()) {
			L1SkillUse.cast(
					this,
					L1SkillId.CANCELLATION,
					getId(), getX(), getY(),
					null,
					0,
					L1SkillUse.TYPE_LOGIN);

			if (getWeapon() != null) {
				getInventory().setEquipped(getWeapon(), false, false, false, false);
			}

			SC_CharVisualUpdate.sendPacked(this, SC_CharVisualUpdate.pack(this));
			SC_OwnCharStatus2.send(this);

			for (L1ItemInstance armor : getInventory().getItems()) {
				for (int type = 0; type <= 12; type++) {
					if (armor != null) {
						getInventory().setEquipped(armor, false, false, false, false);
					}
				}
			}

			setReturnStat(getExp());
			SC_SPMR.send(this);
			SC_OwnCharAttrDef.send(this);
			SC_OwnCharStatus2.send(this);
			SC_ReturnedStat.sendPc(this, SC_ReturnedStat.START);
			try {
				save();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	// pc 탐 처리 
	public int tamCount() {
	    Account acc = (getNetConnection() != null) ? getNetConnection().getAccount() : null;
	    return (acc != null) ? acc.getTamActiveCountCached() : 0;
	}

	public long tamTime() {
	    Account acc = (getNetConnection() != null) ? getNetConnection().getAccount() : null;
	    return (acc != null) ? acc.getTamRemainTimeMsCached() : 0L;
	}
	

	public int getRankLevel() {
		return RankUserLoader.getInstance().getRankLevel(this);
	}

	public void cancelAbsoluteBarrier() { // 아브소르트바리아의 해제
		if (hasSkillEffect(L1SkillId.ABSOLUTE_BARRIER)) {
			removeSkillEffect(L1SkillId.ABSOLUTE_BARRIER);
		}
	}

	public void cancelMoebius() {
		if (hasSkillEffect(L1SkillId.MOEBIUS)) {
			removeSkillEffect(L1SkillId.MOEBIUS);
		}
	}

	public int get_PKcount() {
		return _PKcount;
	}

	public void set_PKcount(int i) {
		_PKcount = i;
	}

	public int getClanid() {
		return _clanid;
	}

	public void setClanid(int i) {
		_clanid = i;
	}

	public String getClanname() {
		return clanname;
	}

	public void setClanname(String s) {
		clanname = s;
	}

	public int getRedKnightClanId() {
		return _redKnightClanId;
	}

	public void setRedKnightClanId(int i) {
		_redKnightClanId = i;
	}

	public int getRedKnightDamage() {
		return _redKnightDamage;
	}

	public void setRedKnightDamage(int i) {
		_redKnightDamage = i;
	}

	public void addRedKnightDamage(int i) {
		_redKnightDamage += i;
	}

	public int getRedKnightKill() {
		return _redKnightKill;
	}

	public void setRedKnightKill(int i) {
		_redKnightKill = i;
	}

	public void addRedKnightKill(int i) {
		_redKnightKill += i;
	}

	private String _sealingPW; //

	public String TempQuiz = "";

	public String getSealingPW() {
		return _sealingPW;
	}

	public void setSealingPW(String s) {
		_sealingPW = s;
	}

	int _sealScrollTime;

	public void setSealScrollTime(int sealScrollTime) {
		_sealScrollTime = sealScrollTime;
	}

	public int getSealScrollTime() {
		return _sealScrollTime;
	}
	int _sealScrollTime2;

	public void setSealScrollTime2(int sealScrollTime2) {
		_sealScrollTime2 = sealScrollTime2;
	}

	public int getSealScrollTime2() {
		return _sealScrollTime2;
	}

	int _sealScrollCount;

	public void setSealScrollCount(int sealScrollCount) {
		_sealScrollCount = sealScrollCount;
	}

	public int getSealScrollCount() {
		return _sealScrollCount;
	}

	public L1Clan getClan() {
		return L1World.getInstance().getClan(getClanid());
	}

	public int getClanRank() {
		return _clanRank;
	}

	public void setClanRank(int i) {
		_clanRank = i;
	}

	public byte get_sex() {
		return _sex;
	}

	public void set_sex(int i) {
		_sex = (byte) i;
	}
	/** 인형 스킬 **/
	public int getDollskill() {
		return _dollskill;
	}

	public void setDollskill(int doll) {
		_dollskill = doll;
	}
	/** 마법 이팩 **/
	public int getMagicadd() {
		return _magicadd;
	}

	public void setMagicadd(int mgadd) {
		_magicadd = mgadd;
	}

	/** 나이설정 **/
	public int getAge() {
		return _age;
	}

	public void setAge(int i) {
		_age = i;
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

	public int getDamageReductionByArmor() {
		return _damageReductionByArmor;
	}

	public void addDamageReductionByArmor(int i) {
		_damageReductionByArmor += i;
	}

	public int getDamageReductionIgnore() {
		return _damageReductionIgnore;
	}

	public void addDamageReductionIgnore(int i) {
		_damageReductionIgnore += i;
	}

	private int _PVPMagicDamageReduction = 0;

	public int getPVPMagicDamageReduction() { // PVP마법대미지 감소
		return _PVPMagicDamageReduction;
	}

	public int addPVPMagicDamageReduction(int i) {
		return _PVPMagicDamageReduction += i;
	}

	public int getDamageReduction() {
		return _DamageReduction;
	}

	public void addDamageReduction(int i) {
		_DamageReduction += i;
	}

	public int getBowDmgRate() {
		return _bowDmgRate;
	}

	public void addBowDmgRate(int i) {
		_bowDmgRate += i;
	}

	public int getDmgRate() {
		return _DmgRate;
	}

	public void addDmgRate(int i) {
		_DmgRate += i;
	}

	public int getBowHitRate() {
		return _bowHitRate;
	}

	public void addBowHitRate(int i) {
		_bowHitRate += i;
	}

	public int getHitRate() {
		return _HitRate;
	}

	public void addHitRate(int i) {
		_HitRate += i;
	}

	public int getDmgupByArmor() {
		return _DmgupByArmor;
	}

	public void addDmgupByArmor(int i) {
		_DmgupByArmor += i;
	}

	public int getBowDmgupByArmor() {
		return _bowDmgupByArmor;
	}

	public void addBowDmgupByArmor(int i) {
		_bowDmgupByArmor += i;
	}

	private void setGresValid(boolean valid) {
		_gresValid = valid;
	}

	public boolean isGresValid() {
		return _gresValid;
	}

	public long getFishingTime() {
		return _fishingTime;
	}

	public void setFishingTime(long i) {
		_fishingTime = i;
	}

	public boolean isFishing() {
		return _isFishing;
	}

	public boolean isFishingReady() {
		return _isFishingReady;
	}

	public void setFishing(boolean flag) {
		_isFishing = flag;
	}

	public void setFishingReady(boolean flag) {
		_isFishingReady = flag;
	}

	public int getCookingId() {
		return _cookingId;
	}

	public void setCookingId(int i) {
		_cookingId = i;
	}

	public int getDessertId() {
		return _dessertId;
	}

	public void setDessertId(int i) {
		_dessertId = i;
	}

	/** 패키지상점 **/
	private int CashStep = 0;

	public int getCashStep() {
		return CashStep;
	}

	public void setCashStep(int cashStep) {
		CashStep = cashStep;
	}

	/** 패키지상점 **/

	/** 로봇 시작 **/
	private int teleportTime = 0;
	private int teleportTime2 = 0;
	private int skillTime = 0;
	private int skillTime2 = 0;
	private long _quiztime = 0;
	private long _quiztime2 = 0;
	private long _quiztime3 = 0;
	// private int currentTeleportCount = 0;
	private int currentSkillCount = 0;
	private int currentSkillCount2 = 0;

	// 콤보시스템
	private int comboCount;

	public long getQuizTime() {
		return _quiztime;
	}

	public void setQuizTime(long l) {
		_quiztime = l;
	}

	public long getQuizTime2() {
		return _quiztime2;
	}

	public void setQuizTime2(long l) {
		_quiztime2 = l;
	}

	public long getQuizTime3() {
		return _quiztime3;
	}

	public void setQuizTime3(long l) {
		_quiztime3 = l;
	}

	public int getTeleportTime() {
		return teleportTime;
	}

	public void setTeleportTime(int teleportTime) {
		this.teleportTime = teleportTime;
	}

	public int getTeleportTime2() {
		return teleportTime2;
	}

	public void setTeleportTime2(int teleportTime2) {
		this.teleportTime2 = teleportTime2;
	}

	public int getSkillTime2() {
		return skillTime2;
	}

	public void setSkillTime2(int skillTime2) {
		this.skillTime2 = skillTime2;
	}

	public int getSkillTime() {
		return skillTime;
	}

	public void setSkillTime(int skillTime) {
		this.skillTime = skillTime;
	}

	public int getCurrentSkillCount() {
		return currentSkillCount;
	}

	public void setCurrentSkillCount(int currentSkillCount) {
		this.currentSkillCount = currentSkillCount;
	}

	public int getCurrentSkillCount2() {
		return currentSkillCount2;
	}

	public void setCurrentSkillCount2(int currentSkillCount2) {
		this.currentSkillCount2 = currentSkillCount2;
	}

	
	public int getTempCharGfxAtDead() {
		return _tempCharGfxAtDead;
	}

	public void setTempCharGfxAtDead(int i) {
		_tempCharGfxAtDead = i;
	}

	public boolean isCanWhisper() {
		return _isCanWhisper;
	}

	public void setCanWhisper(boolean flag) {
		_isCanWhisper = flag;
	}

	public boolean isShowTradeChat() {
		return _isShowTradeChat;
	}

	public void setShowTradeChat(boolean flag) {
		_isShowTradeChat = flag;
	}

	public boolean isShowWorldChat() {
		return _isShowWorldChat;
	}

	public void setShowWorldChat(boolean flag) {
		_isShowWorldChat = flag;
	}

	public int getFightId() {
		return _fightId;
	}

	public void setFightId(int i) {
		_fightId = i;
	}

	public void setDeathMatch(boolean i) {
		this.isDeathMatch = i;
	}

	public boolean isDeathMatch() {
		return isDeathMatch;
	}

	public boolean isSupporting() {
		return _isSupporting;
	}

	public void setSupporting(boolean flag) {
		_isSupporting = flag;
	}

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

	 /** 바포메트 시스템 **/
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


	/** 코마버프 시작 **/
	private int _deathmatch;

	public int getDeathMatchPiece() {
		return _deathmatch;
	}

	public void setDeathMatchPiece(int i) {
		_deathmatch = i;
	}

	private int _petrace;

	public int getPetRacePiece() {
		return _petrace;
	}

	public void setPetRacePiece(int i) {
		_petrace = i;
	}

	private int _ultimatebattle;

	public int getUltimateBattlePiece() {
		return _ultimatebattle;
	}

	public void setUltimateBattlePiece(int i) {
		_ultimatebattle = i;
	}

	private int _petmatch;

	public int getPetMatchPiece() {
		return _petmatch;
	}

	public void setPetMatchPiece(int i) {
		_petmatch = i;
	}

	private int _ghosthouse;

	public int getGhostHousePiece() {
		return _ghosthouse;
	}

	public void setGhostHousePiece(int i) {
		_ghosthouse = i;
	}

	/** 코마버프 끝 **/

	/** 장신구인첸트리뉴얼 **/
	public int _accessoryHeal = 0;

	public int getAccessoryHeal() {
		return _accessoryHeal;
	}

	public void setAccessoryHeal(int i) {
		_accessoryHeal = i;
	}

	public void addAccessoryHeal() {
		_accessoryHeal += 1;
	}

	private boolean _isGambling = false;

	public boolean isGambling() {
		return _isGambling;
	}

	public void setGambling(boolean flag) {
		_isGambling = flag;
	}

	private int _gamblingmoney = 0;

	public int getGamblingMoney() {
		return _gamblingmoney;
	}

	public void setGamblingMoney(int i) {
		_gamblingmoney = i;
	}

	// 소막
	private boolean _isGambling3 = false;

	public boolean isGambling3() {
		return _isGambling3;
	}

	public void setGambling3(boolean flag) {
		_isGambling3 = flag;
	}

	private int _gamblingmoney3 = 0;

	public int getGamblingMoney3() {
		return _gamblingmoney3;
	}

	public void setGamblingMoney3(int i) {
		_gamblingmoney3 = i;
	}

	private int monsterkill = 0;

	public int getMonsterkill() {
		return monsterkill;
	}

	public void setMonsterkill(int monster) {
		monsterkill = monster;
		SC_OwnCharStatus.send(this);
	}

	public void addMonsterKill(int i) {
		monsterkill += i;
		SC_OwnCharStatus.send(this);
	}

	private ArrayList<String> _cmalist = new ArrayList<String>();

	/**
	 * 클랜 매칭 신청,요청 목록 유저가 사용할땐 배열에 혈맹의 이름을 넣고 군주가 사용할땐 배열에 신청자의 이름을 넣는다.
	 */
	public void addCMAList(String name) {
		if (_cmalist.contains(name)) {
			return;
		}
		_cmalist.add(name);
	}

	public void removeCMAList(String name) {
		if (!_cmalist.contains(name)) {
			return;
		}
		_cmalist.remove(name);
	}

	public ArrayList<String> getCMAList() {
		return _cmalist;
	}

	private ArrayList<Integer> skillList = new ArrayList<Integer>();

	private int _clanMemberId;

	public int getClanMemberId() {
		return _clanMemberId;
	}

	public void setClanMemberId(int i) {
		_clanMemberId = i;
	}

	private String _clanMemberNotes;

	public String getClanMemberNotes() {
		return _clanMemberNotes;
	}

	public void setClanMemberNotes(String s) {
		_clanMemberNotes = s;
	}

	// 고대의 가호 효과 구현
	private void drop1() {
		if ((getMapId() >= 1708 && getMapId() <= 1709) && getInventory().checkEquipped(900022)) { // 착용한
																									// 아이템
			L1ItemInstance drop = ItemTable.getInstance().createItem(3000122); // 드랍
																				// 시킬아이템
			for (L1ItemInstance item : getInventory().getItems()) {
				if (item.getItemId() == 900022 & item.isEquipped()) {
					SC_ServerMessage.send(this, 3802);
					SC_ServerMessage.send(this, 158, "$22172");
					getInventory().removeItem(item, 1);
					L1World.getInstance().getInventory(getX(), getY(), getMapId()).storeItem(drop);
					break;
				}
			}
		}
	}

	// 불멸의 가호
	private void gahodrop(L1Character lastAttacker) {
		if (getInventory().checkItem(4100122)) {
			L1ItemInstance drop = ItemTable.getInstance().createItem(4100120);
			for (L1ItemInstance item : getInventory().getItems()) {
				if (item.getItemId() == 4100122) {
					SC_ServerMessage.send(this, 3802);
					SC_ServerMessage.send(this, 158, "$27279"); 
					getInventory().removeItem(item, 1);
					 L1World.getInstance().getInventory(getX(), getY(),
					 getMapId()).storeItem(drop);
					//lastAttacker.getInventory().storeItem(drop);
					//lastAttacker.sendPackets(new SC_SystemMessage("타버린 불멸의 가호를 획득하였습니다."));
					break;
				}
			}
		}
	}

	// 고급 불멸의 가호
	private void specialgahodrop(L1Character lastAttacker) {

		if (getInventory().checkItem(4100121)) {
			L1ItemInstance drop = ItemTable.getInstance().createItem(4100119);
			for (L1ItemInstance item : getInventory().getItems()) {
				if (item.getItemId() == 4100121) {
					SC_ServerMessage.send(this, 3802);
					SC_ServerMessage.send(this, 158, "$26711");
					getInventory().removeItem(item, 1);
					 L1World.getInstance().getInventory(getX(), getY(),
					 getMapId()).storeItem(drop);
				//	 L1World.getInstance().getInventory(getX(), getY(),
				//	 getMapId()).storeItem(drop0, count22);
				//	 sendPackets(new SC_SystemMessage("투게더코인 [드랍] : "+count22+"개"));
					//lastAttacker.getInventory().storeItem(drop);
					//lastAttacker.sendPackets(new SC_SystemMessage("타버린 고급 불멸의 가호를 획득하였습니다."));
					break;
				}

			}
		} 
		if (getInventory().checkItem(592247)) { 

			L1ItemInstance drop2 = ItemTable.getInstance().createItem(592248);
			for (L1ItemInstance item : getInventory().getItems()) {
				if (item.getItemId() == 592247) {
					SC_SystemMessage.send(this, "수호자의 가호를 드랍하였습니다.");
					getInventory().removeItem(item, 1);
					 L1World.getInstance().getInventory(getX(), getY(),
					 getMapId()).storeItem(drop2);
				//	 L1World.getInstance().getInventory(getX(), getY(),
				//	 getMapId()).storeItem(drop0, count22);
				//	 sendPackets(new SC_SystemMessage("투게더코인 [드랍] : "+count22+"개"));
					//lastAttacker.getInventory().storeItem(drop);
					//lastAttacker.sendPackets(new SC_SystemMessage("타버린 고급 불멸의 가호를 획득하였습니다."));
					break;
				}
			}
		} 

	}

	private void drop2() {
		if (getInventory().checkEquipped(10000)) { // 착용한 아이템
			L1ItemInstance drop = ItemTable.getInstance().createItem(738); // 드랍
																			// 시킬아이템
			for (L1ItemInstance item : getInventory().getItems()) {
				if (item.getItemId() == 10000 & item.isEquipped()) {
					SC_ServerMessage.send(this, 3802);
					SC_ServerMessage.send(this, 158, "$25382");
					getInventory().removeItem(item, 1);
					L1World.getInstance().getInventory(getX(), getY(), getMapId()).storeItem(drop);
					break;
				}
			}
		}
	}

	private void drop3() {
		if (getInventory().checkEquipped(10001)) { // 착용한 아이템
			L1ItemInstance drop = ItemTable.getInstance().createItem(739); // 드랍
																			// 시킬아이템
			for (L1ItemInstance item : getInventory().getItems()) {
				if (item.getItemId() == 10001 & item.isEquipped()) {
					SC_ServerMessage.send(this, 3802);
					SC_ServerMessage.send(this, 158, "$25384");
					getInventory().removeItem(item, 1);
					L1World.getInstance().getInventory(getX(), getY(), getMapId()).storeItem(drop);
					break;
				}
			}
		}
	}

	public void movePlayerClanData(L1Clan move_clan) {
		setClanid(move_clan.getClanId());
		setClanname(move_clan.getClanName());
		setTitle("");
		setClanMemberNotes("");
		setClanRank(L1Clan.수련);
		save();
		move_clan.addClanMember(getName(), getClanRank(), getLevel(), "", getId(), getType(), getOnlineStatus(), null);
	}

	public void ClearPlayerClanData(L1Clan clan) throws Exception {
		setClanid(0);
		setClanname("");
		setTitle("");
		setClanMemberId(0);
		setClanMemberNotes("");
		String broadcastTitle = null;
		if (Config.수배온오프) {
		if (hasSkillEffect(L1SkillId.USER_WANTED)  ) {
			broadcastTitle = WANTED_TITLE;
		}
		if(hasSkillEffect(L1SkillId.USER_WANTED1)){
			broadcastTitle = L1PcInstance.WANTED_TITLE1;
		}
		}

		SC_CharTitle.send(this, getId(), broadcastTitle);
		SC_CharTitle.broadcast(this, getId(), broadcastTitle);
		SC_ReturnedStat.sendEmblem(this, getId(), 0);
		SC_ReturnedStat.broadcastEmblem(this, getId(), 0);
		SC_ClanName.send(this, this, 0, 0);
		setClanRank(0);
		save();
	}

	public void startEinhasadTimer() {
		synchronized (this) {
			if (_einhasadTimer != null) {
				return;
			}
            final EinhasadTimer next = new EinhasadTimer();
            // Do not retain an unscheduled timer after bounded scheduler rejection.
            if (ThreadPool.getInstance().schedule(next, EinhasadTimer.INTERVAL) != null) {
                _einhasadTimer = next;
            } else {
                next.cancel();
            }
		}
	}

	public void stopEinhasadTimer() {
		synchronized (this) {
			if (_einhasadTimer == null) {
				return;
			}
			_einhasadTimer.cancel();
			_einhasadTimer = null;
		}
	}

	EinhasadTimer _einhasadTimer;

	class EinhasadTimer extends RepeatTask {

		public static final int INTERVAL = 15 * 60 * 1000;

		public EinhasadTimer() {
			super(INTERVAL);
		}

        @Override
        protected void onScheduleRejected() {
            // The rejected tick is not running anymore; movement/login may
            // safely attempt to arm a new timer without a permanently stale ref.
            synchronized (L1PcInstance.this) {
                if (_einhasadTimer == this) _einhasadTimer = null;
            }
        }

		@Override
		public void execute() {
            // Preserve the existing increment and cap, with one atomic update
            // so a concurrent recharge cannot be overwritten by a stale read.
            getAccount().recoverBlessOfAin(1L, 2000000);

			if (getAccount().getBlessOfAin() > 10000) {
				SC_RestExpInfo.send(L1PcInstance.this);
			}
		}
	}

	public long getFishingShopBuyTime_1() {
		return FishingShopBuyTime_1;
	}

	public void setFishingShopBuyTime_1(long fishingShopBuyTime_1) {
		FishingShopBuyTime_1 = fishingShopBuyTime_1;
	}

	private boolean createNewItem(L1PcInstance pc, int item_id, int count, int EnchantLevel, int AttEnchantLevel,
			int Bless) {
		L1ItemInstance item = ItemTable.getInstance().createItem(item_id);
		if (item != null) {
			item.setCount(count);
			item.setEnchantLevel(EnchantLevel);
			item.setAttrEnchantLevel(AttEnchantLevel);
			item.setIdentified(true);
			pc.getInventory().storeItem(item);
			item.setBless(Bless);
			pc.getInventory().updateItem(item, L1PcInventory.COL_BLESS);
			pc.getInventory().saveItem(item, L1PcInventory.COL_BLESS);
			return true;
		} else {
			return false;
		}
	}

	private int risingUp = 0;

	public int getRisingUp() {
		return risingUp;
	}

	public void setRisingUp(int i) {
		risingUp = i;
	}

	private int impactUp = 0;

	public void setImpactUp(int i) {
		impactUp = i;
	}

	public int getImpactUp() {
		return impactUp;
	}

	private int graceLv = 0;

	public int getGraceLv() {
		return graceLv;
	}

	public void setGraceLv(int i) {
		graceLv = i - 80;
		if (graceLv < 0) {
			graceLv = 0;
		} else if (graceLv > 10) {
			graceLv = 10;
		}
	}
	public void 어드밴스(final int x, final int y, final int map, final int heading, final int effect_id,
			boolean effect_check, boolean skill_check) {
		tpBeginStart(x, y, map, heading, effect_id, effect_check, skill_check);
	}
	public void start_teleportForGM(final int x, final int y, final int map, final int heading, final int effect_id,
			boolean effect_check, boolean skill_check) {
		tpBeginStart(x, y, map, heading, effect_id, effect_check, skill_check);
	}

	public void start_teleport(final int x, final int y, final int map, final int heading, final int effect_id,
			boolean effect_check, boolean skill_check) {
		tpBeginStart(x, y, map, heading, effect_id, effect_check, skill_check);

	}

	public void start_teleport(final int x, final int y, final int map, final int heading, final int effect_id,
			boolean effect_check, boolean skill_check, boolean isAlways) {
		tpBeginStart(x, y, map, heading, effect_id, effect_check, skill_check);
	}

	public void do_simple_teleport(int x, int y, int mapid) {
		if (isFishing()) {
			FishingTimeController.getInstance().endFishing(this);
		}
		if (FishingTimeController.getInstance().isMember(this)) {
			FishingTimeController.getInstance().endFishing(this);
		}

		if (getInventory().checkEquipped(900022)) {
			boolean mapcheck = getMapId() >= 1708 && getMapId() <= 1712;
			if (!mapcheck) {
				SC_PacketBox.send(this, SC_PacketBox.UNLIMITED_ICON1, 484, false);
			}
		}
		long ss = SC_SkillSound.pack(getId(), 169);
		SC_SkillSound.sendPacked(this, ss);
		SC_SkillSound.broadcastPacked(this, ss);
		L1Teleport.getInstance().doTeleport(L1PcInstance.this, x, y, mapid);
	}

	private int teleport_x;

	public int get_teleport_x() {
		return teleport_x;
	}

	public void set_teleport_x(int i) {
		this.teleport_x = i;
	}

	private int teleport_y;

	public int get_teleport_y() {
		return teleport_y;
	}

	public void set_teleport_y(int i) {
		this.teleport_y = i;
	}

	private int teleport_map;

	public int get_teleport_map() {
		return teleport_map;
	}

	public void set_teleport_map(int i) {
		this.teleport_map = i;
	}

	// ------------------------------ 텔레포트 End ------------------------------//

	private int _attackSpeed;

	public int getAttackSpeed() {
		return this._attackSpeed;
	}

	public void setAttackSpeed() {
		int spriteId = getCurrentSpriteId();
	    int level = getLevel();
	    L1PolyMorph poly = PolyTable.getInstance().find(spriteId);
	    if (poly != null) {
	        level = Math.max(level, poly.getMinLevel());
	    }
	    int status = SpriteInformationLoader.levelToIndex(level, spriteId);
	    if (getPolyIdByEquip() == 11365) {
	        status = Math.max(status, 9);
	    }
	    if (this._attackSpeed != status) {
	        this._attackSpeed = status;
	        
	        SC_CharSpeedUpdate.send(this, getId(), status, level);
            SC_CharSpeedUpdate.broadcast(this, getId(), status, level);
	    }
	}

	private int _polyIdByEquip = 0;

	public int getPolyIdByEquip() {
		return this._polyIdByEquip;
	}

	public void setPolyIdByEquip(int i) {
		this._polyIdByEquip = i;
	}

	private int _autoPotion;

	public int getAutoPotion() {
		return _autoPotion;
	}

	public void setAutoPotion(int i) {
		_autoPotion = i;
	}

	private boolean _시간제;

	public boolean is시간제() {
		return _시간제;
	}

	public void set시간제(boolean b) {
		_시간제 = b;
	}
	
	
	private int _autoDeadTime = 5;

	public int getAutoDeadTime() {
		return _autoDeadTime;
	}

	public void setAutoDeadTime(int i) {
		_autoDeadTime = i;
	}
	private int _autoRange;

	public int getAutoRange() {
		return _autoRange;
	}

	public void setAutoRange(int i) {
		_autoRange = i;
	}

	private int _autoMapId;

	public int getAutoMapId() {
		return _autoMapId;
	}

	public void setAutoMapId(int i) {
		_autoMapId = i;
	}
	private long _buffTime;

	public long getBuffTime() {
		return _buffTime;
	}

	public void setBuffTime(long i) {
		_buffTime = i;
	}

	private boolean _isOneTel = false;

	public void setOneTel(boolean a) {
		_isOneTel = a;
	}

	public boolean isOneTel() {
		return _isOneTel;
	}

	private int _robotAIType;

	public int getRobotAIType() {
		return _robotAIType;
	}

	public void setRobotAIType(int i) {
		_robotAIType = i;
	}

	private int _robotPattern = -1;

	public int getRobotPattern() {
		return _robotPattern;
	}

	public void setRobotPattern(int i) {
		_robotPattern = i;
	}


	private int attackRange;

	public void setAttackRang(int attackRange) {
		this.attackRange = attackRange;
	}

	public int getAttackRang() {
		return attackRange;
	}

	public boolean isCastle;

	private boolean _isPotyRingMaster = false;

	public boolean isPolyRingMaster() {
		return _isPotyRingMaster;
	}

	public void setPolyRingMaster(boolean flag) {
		_isPotyRingMaster = flag;
	}

	private int _elfAttrInitCount;

	public int getElfAttrInitCount() {
		return _elfAttrInitCount;
	}

	public void setElfAttrInitCount(int i) {
		if (i >= 20) {
			i = 20;
		}
		_elfAttrInitCount = i;
	}

	public void addElfAttrInitCount(int i) {
		int count = _elfAttrInitCount + i;
		if (count >= 20) {
			count = 20;
		}
		_elfAttrInitCount = count;
	}

	private int _lastImmuneLevel = 0;

	// TODO 이뮨투함 대미지 감소율 %
	public double getImmuneReduction() {
		// System.out.println(_lastImmuneLevel);

		if (_lastImmuneLevel < 36)
			return 0;
		else if (_lastImmuneLevel >= Config.IMMUNE_Level)
			return Config.IMMUNE_DMG;
		else if (_lastImmuneLevel >= Config.IMMUNE_Level1)
			return Config.IMMUNE_DMG1;
		else if (_lastImmuneLevel >= Config.IMMUNE_Level2)
			return Config.IMMUNE_DMG2;
		else if (_lastImmuneLevel >= Config.IMMUNE_Level3)
			return Config.IMMUNE_DMG3;
		else if (_lastImmuneLevel >= Config.IMMUNE_Level4)
			return Config.IMMUNE_DMG4;
		else if (_lastImmuneLevel >= Config.IMMUNE_Level5)
			return Config.IMMUNE_DMG5;
		else if (_lastImmuneLevel >= Config.IMMUNE_Level6)
			return Config.IMMUNE_DMG6;
		else
			return Config.IMMUNE_DMG7;
	}

	public int getLastImmuneLevel() {
		return _lastImmuneLevel;
	}

	public void setLastImmuneLevel(int i) {
		_lastImmuneLevel = i;
	}

	private boolean _isBossNotify = true;

	public boolean isBossNotify() {
		return _isBossNotify;
	}

	public void setBossNotify(boolean b) {
		_isBossNotify = b;
	}

	/** 2016.11.26 MJ 앱센터 LFC **/
	/** instance space 공간중 어떤 상태에 있는지 여부를 나타냄 **/
	private InstStatus _instStatus = InstStatus.INST_USERSTATUS_NONE;

	public InstStatus getInstStatus() {
		return _instStatus;
	}

	public void setInstStatus(InstStatus status) {
		_instStatus = status;
	}

	/** lfc 중 받은 데미지를 누적한다. **/
	private int _dmgLfc;

	public int getDamageFromLfc() {
		return _dmgLfc;
	}

	public void addDamageFromLfc(int i) {
		_dmgLfc = +i;
	}

	public void setDamageFromLfc(int i) {
		_dmgLfc = i;
	}

	/** 2016.11.26 MJ 앱센터 LFC **/

	/** 2016.12.01 MJ 앱센터 LFC **/
	private int _findMerchantId = 0;

	public int getFindMerchantId() {
		return _findMerchantId;
	}

	public void setFindMerchantId(int i) {
		_findMerchantId = i;
	}

	/** 2016.12.01 MJ 앱센터 LFC **/

	private boolean _isValakasProduct;

	public boolean isValakasProduct() {
		return _isValakasProduct;
	}

	public void setValakasProduct(boolean b) {
		_isValakasProduct = b;
	}

	private MJExpAmplifier _expAmplifier;

	public MJExpAmplifier getExpAmplifier() {
		return _expAmplifier;
	}

	public void setExpAmplifier(MJExpAmplifier amp) {
		if (_expAmplifier != null) {
			if (_expAmplifier.equals(amp))
				return;

			killSkillEffectTimer(L1SkillId.EINHASAD_AMPLIFIER);
			SC_InventoryIcon.icoEnd(this, L1SkillId.EINHASAD_AMPLIFIER);
		}
		_expAmplifier = amp;
		if (amp != null) {
			setSkillEffect(L1SkillId.EINHASAD_AMPLIFIER, -1);
			SC_InventoryIcon.iconNewUnLimitAndPriority(this, 0, L1SkillId.EINHASAD_AMPLIFIER, amp.getMessageId(),
					true);
		}
	}

	private L1ItemInstance _collecItem;
	public L1ItemInstance getCollecItem() {
		return _collecItem;
	}

	public void setCollecItem(L1ItemInstance item) {
		_collecItem = item;
	}
	/**캐릭터 저장 구슬 찾기**/
	private String _findchar = null;
	public String getFindchar() {
		return _findchar;
	}
	public void setFindchar(String s) {
		_findchar = s;
	}
	private int _findcharno = 0;
	public int getFindcharno() {
			return _findcharno;
	}
	public void setFindcharno(int fc) {
		_findcharno = fc;		
	}
	/** kill death initialize temporary item instance. **/
	private L1ItemInstance _kdInitItem;

	public L1ItemInstance getKillDeathInitializeItem() {
		return _kdInitItem;
	}

	public void setKillDeathInitializeItem(L1ItemInstance item) {
		_kdInitItem = item;
	}

	public void RenewStat() {
		SC_CharStat.sendCreate(this, this, SC_CharStat.STAT_REFRESH);
		SC_CharStat.sendCreate(this, this, 1, SC_CharStat.Stat_Str); // 스탯 상세능력
		SC_CharStat.sendCreate(this, this, 1, SC_CharStat.Stat_Int);
		SC_CharStat.sendCreate(this, this, 1, SC_CharStat.Stat_Wis);
		SC_CharStat.sendCreate(this, this, 1, SC_CharStat.Stat_Dex);
		SC_CharStat.sendCreate(this, this, 1, SC_CharStat.Stat_Con);
		SC_CharStat.sendCreate(this, SC_CharStat.STAT_VIEW, 25); // 스탯능력치
		SC_CharStat.sendCreate(this, SC_CharStat.STAT_VIEW, 35);
		SC_CharStat.sendCreate(this, SC_CharStat.STAT_VIEW, 45);
		SC_CharStat.sendCreate(this, this, SC_CharStat.STAT_REFRESH);
	}

	private boolean _isOutsideChat = true;

	public boolean isOutsideChat() {
		return _isOutsideChat;
	}

	public void setOutSideChat(boolean b) {
		_isOutsideChat = b;
	}

	private long _lastNpcClickMs = 0L;

	public long getLastNpcClickMs() {
		return _lastNpcClickMs;
	}

	public void setLastNpcClickMs(long l) {
		_lastNpcClickMs = l;
	}

	private int _charLevelBonus;

	public int getCharLevelBonus() {
		return _charLevelBonus;
	}

	public void setCharLevelBonus(int i) {
		_charLevelBonus = i;
	}
	public boolean onStat(String statName) throws Exception {
	    if (getOnlineStatus() != 1) { // 127 스텟 버그 수정
	        SC_Disconnect.send(this);
	        return false;
	    }

	    String stat = statName.toLowerCase();
	    boolean isWeightUpdated = false;

	    synchronized (this) {
	        final int bonusStats = getBonusStats();
	        if (getLevel() - 50 <= bonusStats) {
	            return false;
	        }
	        // 스탯 수치 추출
	        int currentStatVal;
	        switch (stat) {
	        case "str": currentStatVal = getAbility().getStr(); break;
	        case "dex": currentStatVal = getAbility().getDex(); break;
	        case "con": currentStatVal = getAbility().getCon(); break;
	        case "int": currentStatVal = getAbility().getInt(); break;
	        case "wis": currentStatVal = getAbility().getWis(); break;
	        case "cha": currentStatVal = getAbility().getCha(); break;
	        default: throw new Exception(statName);
	        }

	        int maxStat = (getLevel() >= 90) ? 50 : 45;
	        if (currentStatVal >= maxStat) {
	            SC_ServerMessage.send(this, 481);
	            throw new Exception(statName);
	        }

	        switch (stat) {
	        case "str":
	        	getAbility().addStr((byte) 1);
	        	isWeightUpdated = true;
	        	break;
	        case "dex":
	        	getAbility().addDex((byte) 1);
	        	resetBaseAc();
	        	break;
	        case "con":
	        	getAbility().addCon((byte) 1);
	        	isWeightUpdated = true;
	        	break;
	        case "int": getAbility().addInt((byte) 1); break;
	        case "wis": getAbility().addWis((byte) 1); break;
	        case "cha": getAbility().addCha((byte) 1); break;
	        }
	        setBonusStats(bonusStats + 1);
	    }
	    SC_OwnCharStatus2.send(this);
	    SC_CharVisualUpdate.sendPacked(this, SC_CharVisualUpdate.pack(this));    
	    if (isWeightUpdated) {
	        SC_Weight.send(this);
	    }
	    save(); 
	    CheckStatus();

	    int remainingStats = (getLevel() - 50) - getBonusStats();
	    if (getLevel() >= 51 && remainingStats > 0) {
	        int totalStats = getAbility().getStr() + getAbility().getDex() + getAbility().getCon()
	                       + getAbility().getInt() + getAbility().getWis() + getAbility().getCha();
	        if (totalStats < 150) {
	            SC_MessageYN.send(this, 479, String.valueOf(remainingStats));
	        }
	    }
	    return true;
	}
	
	private L1ItemInstance _NameInstance;

	public L1ItemInstance getNameInstance() {
		return _NameInstance;
	}

	public void setNameInstance(L1ItemInstance item) {
		_NameInstance = item;
	}

	private int _magicDodgeProb = 0;

	public int getMagicDodgeProbability() {
		return _magicDodgeProb;
	}

	public void setMagicDodgeProbability(int i) {
		_magicDodgeProb = i;
	}

	public void addMagicDodgeProbability(int i) {
		_magicDodgeProb += i;

		SC_ActionUI2.send(this, SC_ActionUI2.MAGICEVASION, _magicDodgeProb);
	}

	private int _temporaryItemObjectId = -1;

	public int getTemporaryItemObjectId() {
		return _temporaryItemObjectId;
	}

	public void setTemporaryItemObjectId(int i) {
		_temporaryItemObjectId = i;
	}

	public void clearTemporaryItemObjectId() {
		_temporaryItemObjectId = -1;
	}

	private static int _instanceType = -1;

	@Override
	public int getL1Type() {
		return _instanceType == -1 ? _instanceType = super.getL1Type() | MJL1Type.L1TYPE_PC : _instanceType;
	}

	@Override
	public long getCurrentSpriteInterval(EActionCodes actionCode) {
		return (long) _currentSpriteInfo.getInterval(this, actionCode);
	}

	@Override
	public void sendShape(int poly) {
		SC_ChangeShape.send(this, getId(), poly, getCurrentWeapon());
		SC_ChangeShape.broadcast(this, getId(), poly, getCurrentWeapon());
	}

	public void offFishing() {
		if (isFishing()) {
			try {
				setFishing(false);
				setFishingTime(0);
				setFishingReady(false);
				SC_CharVisualUpdate.sendPacked(this, SC_CharVisualUpdate.pack(this));
				SC_CharVisualUpdate.broadcastPacked(this, SC_CharVisualUpdate.pack(this));
				FishingTimeController.getInstance().removeMember(this);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private SC_AttendanceUserDataExtend _attendance_data;

	public void setAttendanceData(SC_AttendanceUserDataExtend attendance_data) {
		_attendance_data = attendance_data;
	}

	public SC_AttendanceUserDataExtend getAttendanceData() {
		return _attendance_data;
	}

	// 타겟티 사용 유무 _isOnTargetEffect
	private boolean _isOnTargetEffect = Config.TARGETGFX;

	public boolean isOnTargetEffect() {
		return _isOnTargetEffect;
	}

	public void setOnTargetEffect(boolean b) {
		_isOnTargetEffect = b;
	}

	private Timestamp _lastTopBless;

	public void setLastTopBless(Timestamp ts) {
		_lastTopBless = ts;
	}

	public Timestamp getLastTopBless() {
		return _lastTopBless;
	}

	private BQSCharacterData _bqsData;

	public BQSCharacterData getBqs() {
		return _bqsData;
	}

	public void setBqs(BQSCharacterData bqs) {
		_bqsData = bqs;
	}

	private MJEPcStatus _instance_status = MJEPcStatus.NONE;
	private int _current_combat_id = 0;
	private int _current_combat_team_id = -1;

	public void set_instance_status(MJEPcStatus instance_status) {
		_instance_status = instance_status;
	}

	public MJEPcStatus get_instance_status() {
		return _instance_status;
	}

	public boolean is_none() {
		return _instance_status.equals(MJEPcStatus.NONE);
	}

	public boolean is_world() {
		return _instance_status.equals(MJEPcStatus.WORLD);
	}

	public boolean is_combat_field() {
		return _instance_status.equals(MJEPcStatus.COMBAT_FIELD);
	}

	public void set_current_combat_id(int current_combat_id) {
		_current_combat_id = current_combat_id;
	}

	public int get_current_combat_id() {
		return _current_combat_id;
	}

	public void set_current_combat_team_id(int current_combat_team_id) {
		_current_combat_team_id = current_combat_team_id;
	}

	public int get_current_combat_team_id() {
		return _current_combat_team_id;
	}

	private boolean _is_non_action = false;

	public boolean is_non_action() {
		return _is_non_action;
	}

	public void set_is_non_action(boolean b) {
		_is_non_action = b;
	}

	private int _mark_status = 0;

	public void set_mark_status(int mark_status) {
		_mark_status = mark_status;
	}

	public int get_mark_status() {
		return _mark_status;
	}

	private MJCaptcha _captcha;

	public MJCaptcha get_captcha() {
		return _captcha;
	}

	public MJCaptcha create_captcha() {
		return (_captcha = MJCaptcha.newInstance(getId()));
	}

	private int _lateral_damage;
	private int _lateral_reduction;
	private int _lateral_magic_rate;

	public void set_lateral_damage(int lateral_damage) {
		_lateral_damage = lateral_damage;
	}

	public void add_lateral_damage(int lateral_damage) {
		_lateral_damage += lateral_damage;
	}

	public int get_lateral_damage() {
		return _lateral_damage;
	}

	public void set_lateral_reduction(int lateral_reduction) {
		_lateral_reduction = lateral_reduction;
	}

	public void add_lateral_reduction(int lateral_reduction) {
		_lateral_reduction += lateral_reduction;
	}

	public int get_lateral_reduction() {
		return _lateral_reduction;
	}

	public void set_lateral_magic_rate(int lateral_magic_rate) {
		_lateral_magic_rate = lateral_magic_rate;
	}

	public void add_lateral_magic_rate(int lateral_magic_rate) {
		_lateral_magic_rate += lateral_magic_rate;
	}

	public int get_lateral_magic_rate() {
		return _lateral_magic_rate;
	}

	public void load_lateral_status() {
		Selector.exec("select * from tb_lateral_status where character_id=?", new SelectorHandler() {
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, getId());
			}

			@Override
			public void result(ResultSet rs) throws Exception {
				while (rs.next()) {
					add_lateral_damage(rs.getInt("lateral_damage"));
					add_lateral_reduction(rs.getInt("lateral_reduction"));
					add_lateral_magic_rate(rs.getInt("lateral_magic_rate"));
				}
			}
		});
	}

	public void delete_lateral_status() {
		set_lateral_damage(0);
		set_lateral_reduction(0);
		set_lateral_magic_rate(0);
		Updator.exec("delete from tb_lateral_status where character_id=?", new Handler() {
			@Override
			public void handle(PreparedStatement pstm) throws Exception {
				pstm.setInt(1, getId());
			}
		});
	}

	public void update_lateral_status() {
		Updator.exec(
				"insert into tb_lateral_status set character_id=?, lateral_damage=?, lateral_reduction=?, lateral_magic_rate=? on duplicate key update  lateral_damage=?, lateral_reduction=?, lateral_magic_rate=?",
				new Handler() {
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						int idx = 0;
						pstm.setInt(++idx, getId());
						pstm.setInt(++idx, get_lateral_damage());
						pstm.setInt(++idx, get_lateral_reduction());
						pstm.setInt(++idx, get_lateral_magic_rate());
						pstm.setInt(++idx, get_lateral_damage());
						pstm.setInt(++idx, get_lateral_reduction());
						pstm.setInt(++idx, get_lateral_magic_rate());
					}
				});
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

	public boolean is_ranking_buff() {
		return hasSkillEffect(L1SkillId.RANK_BUFF_1) || hasSkillEffect(L1SkillId.RANK_BUFF_2)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_3) || hasSkillEffect(L1SkillId.RANK_BUFF_4)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_5) || hasSkillEffect(L1SkillId.RANK_BUFF_6)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_7) || hasSkillEffect(L1SkillId.RANK_BUFF_8)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_9) || hasSkillEffect(L1SkillId.RANK_BUFF_10_STR)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_10_DEX) || hasSkillEffect(L1SkillId.RANK_BUFF_10_INT)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_11_STR) || hasSkillEffect(L1SkillId.RANK_BUFF_11_DEX)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_11_INT);
	}

	public boolean is_top_ranker() {
		return hasSkillEffect(L1SkillId.RANK_BUFF_11_STR) || hasSkillEffect(L1SkillId.RANK_BUFF_11_DEX)
				|| hasSkillEffect(L1SkillId.RANK_BUFF_11_INT);
	}

	public FastMap<Integer, L1QuestInfo> quest_list = new FastMap<Integer, L1QuestInfo>();
	public Object syncTalkIsland = new Object();

	public L1QuestInfo getQuestList(int id) {
		L1QuestInfo info = quest_list.get(id);
		return info;
	}

	private long _PostDelay;

	public long getPostDelay() {
		return _PostDelay;
	}

	public void setPostDelay(long i) {
		_PostDelay = i;
	}

	private HashMap<Integer, MJPassiveInfo> m_passives;

	public void addPassive(MJPassiveInfo pInfo) {
		if (m_passives == null)
			m_passives = new HashMap<Integer, MJPassiveInfo>();

		m_passives.put(pInfo.getPassiveId(), pInfo);
	}

	public ArrayList<MJPassiveInfo> getPassives() {
		if (m_passives == null)
			return null;
		return new ArrayList<MJPassiveInfo>(m_passives.values());
	}

	public MJPassiveInfo getPassive(int passiveId) {
		if (m_passives == null)
			return null;
		return m_passives.get(passiveId);
	}

	public boolean isPassive(int passiveId) {
		return getPassive(passiveId) != null;
	}

	// TODO 혈맹 버프 리뉴얼 2017-11-12
	private int _ClanBuffMap = 0;

	public int getClanBuffMap() {
		return _ClanBuffMap;
	}

	public void setClanBuffMap(int i) {
		_ClanBuffMap = i;
	}

	private long _lastMoveActionMillis;

	public void setLastMoveActionMillis(long lastMoveActionMillis) {
		_lastMoveActionMillis = lastMoveActionMillis;
	}

	public long getLastMoveActionMillis() {
		return _lastMoveActionMillis;
	}
	
	private boolean _one_minute_start = false;

	public boolean isOneMinuteStart() {
		return _one_minute_start;
	}

	public void setOneMinuteStart(boolean flag) {
		_one_minute_start = flag;
	}

	private int _EnviroMentCount;

	public int getEnviroMentCount() {
		return _EnviroMentCount;
	}

	public void setEnviroMentCount(int i) {
		_EnviroMentCount = i;
	}

	public void addEnviroMentCount(int i) {
		int plus = _EnviroMentCount + i;
		if (plus > 4) {
			plus = 5;
		}
		_EnviroMentCount = plus;
	}

	private Calendar _pcLocalTime;

	public Calendar getEnviroMentLocalTime() {
		return _pcLocalTime;
	}

	public void setEnviroMentLocalTime(Calendar time) {
		_pcLocalTime = time;
	}

	/** 2017-11-06 아인하사드 축복 감소 확률 리뉴얼 **/
	private int _EinhasadBlessper = 0;

	public int getEinhasadBlessper() {
		return _EinhasadBlessper;
	}

	public void setEinhasadBlessper(int einhasadblessper) {
		_EinhasadBlessper = einhasadblessper;
		SC_RestExpInfo.send(this);
	}

	public void addEinhasadBlessper(int i) {
		_EinhasadBlessper += i;
		SC_RestExpInfo.send(this);
	}

	
	private L1CompanionInstance m_companion;

	public void set_companion(L1CompanionInstance companion) {
		if (companion != null)
			remove_companion();
		m_companion = companion;
	}

	public L1CompanionInstance get_companion() {
		return m_companion;
	}

	public void remove_companion() {
		if (m_companion != null) {
			m_companion.deleteMe();
			m_companion = null;
		}
	}


	@Override
	public void send_pink_name(int remain_seconds) {
		SC_PinkName.send(this, getId(), remain_seconds);
		if(!isGm()) {
			SC_PinkName.broadcastFast(this, getId(), remain_seconds);
		}
	}

	private boolean _playsupport;
	
	public boolean 마스터;
	
	private boolean changechaname;

	public boolean isChangechaname() {
		return changechaname;
	}
	
	public void setChangechaname(boolean changechaname) {
		this.changechaname = changechaname;
	}

	public boolean isPlaySupport() {
		return _playsupport;
	}

	public void setPlaySupport(boolean flag) {
		_playsupport = flag;
	}
	
	public boolean 영웅합성확률증가 = false;
	public boolean 전설합성확률증가 = false;
	public boolean 신화합성확률증가 = false;
	
    private final AtomicBoolean _tpLock = new AtomicBoolean(false);
    private volatile int teleportTimerEpoch;
    private static final AtomicIntegerFieldUpdater<L1PcInstance> TELEPORT_TIMER_EPOCH =
            AtomicIntegerFieldUpdater.newUpdater(L1PcInstance.class, "teleportTimerEpoch");

    public int nextTeleportTimerEpoch() {
        return TELEPORT_TIMER_EPOCH.incrementAndGet(this);
    }

    public int teleportTimerEpoch() {
        return teleportTimerEpoch;
    }

	public void tpBeginStart(int x, int y, int mapId, int heading, int effectId, boolean effectCheck, boolean skillCheck) {
		// 원자적 락 체크 (중복 실행 방지)
		if (!_tpLock.compareAndSet(false, true)) {
			return;
		}
		
	    boolean committed = false;
	    
		try {
			// 액션 불가능 상태 체크
			if (MJCommons.isNonAction(this)) {
				return;
			}
			// 혈맹 아인하사드 축복 버프 업데이트
			updateClanEinhasadBuff(mapId);

			// 텔레포트 상태 및 좌표 설정
            set_teleport(true);
            set_teleport_x(x);
            set_teleport_y(y);
            set_teleport_map(mapId);

			// 클라이언트에 텔레포트 시작 알림
			SC_Teleport.send(getNetConnection());

			// 이펙트 출력 처리
			if (effectCheck && effectId > 0) {
				long ss = SC_SkillSound.pack(getId(), effectId);
				SC_SkillSound.sendPacked(this, ss);
				SC_SkillSound.broadcastFastPacked(this, ss);
			}
			// 실제 이동 커밋
            if (!tpThreadDelay(skillCheck ? (getAutoHunt() ? 280 : 30) : 280)) {
                return;
            }
			
		    committed = true;
			   
		} catch (Throwable e) {
			e.printStackTrace();
		} finally {
	        if (!committed) {
	            tpEndFinish();
	        }
	    }
	}

	/**
	 * 텔레포트 시 혈맹 아인하사드 축복 버프 맵 체크 및 업데이트
	 */
	private void updateClanEinhasadBuff(int targetMapId) {
		if (getClan() == null || getClan().getEinhasadBlessBuff() == 0) {
			return;
		}
		ClanBuff buff = ClanBuffTable.getBuffList(getClan().getEinhasadBlessBuff());
		if (buff == null || buff.buffmaplist == null) {
			return;
		}
		// 현재 맵이 버프 대상 맵인지 확인
		boolean isBuffMap = false;
		String[] mapList = buff.buffmaplist.split(",");
		for (String idStr : mapList) {
			if (Integer.parseInt(idStr.trim()) == targetMapId) {
				isBuffMap = true;
				break;
			}
		}
		int currentBuffMap = getClanBuffMap();
		// 버프 맵에 진입하는 경우
		if (isBuffMap && currentBuffMap == 0) {
			setClanBuffMap(targetMapId);
			addEinhasadBlessper(5);
			SC_RestExpInfo.send(this);
		} 
		// 버프 맵에서 벗어나는 경우
		else if (!isBuffMap && currentBuffMap != 0) {
			setClanBuffMap(0);
			addEinhasadBlessper(-5);
			SC_RestExpInfo.send(this);
		}
	}
	// ====== teleport state fields  ======	
	private volatile boolean teleport;

	public boolean get_teleport() {
		return teleport;
	}

	public void set_teleport(boolean b) {
		this.teleport = b;
	}
	
    public boolean tpThreadDelay(int d) {
        return CIOThreadCallback.getInstance()
                .schedulePcTeleport(this, Math.max(1, d));
    }

    public void tpCommitTeleport() {
        try {
            L1Teleport.getInstance().doTeleportation(this);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

	// 텔 끝(무조건 여기서 락/플래그 해제)
	public void tpEndFinish() {
        // Any delayed completion from a cancelled/finished teleport is stale.
        TELEPORT_TIMER_EPOCH.incrementAndGet(this);
		try {
			try { set_teleport(false); } catch (Throwable ignored) {}
			try { _tpLock.set(false); } catch (Throwable ignored) {}
			try { SC_Paralysis.send(this, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			} catch (Throwable ignored) {}
		} catch (Throwable ignored) {}
	}
	
    public void teleportunLock(int skillId) {
    	switch (skillId) {
		case L1SkillId.TELEPORT:
		case L1SkillId.MASS_TELEPORT:
		case L1SkillId.TELEPORT_TO_MATHER:
			tpEndFinish();
			break;
		}
    }

	
	private long _autoDropTime;
	public long getAutoDropTime() {
		return _autoDropTime;
	}
	public void setAutoDropTime(long l) {
		_autoDropTime = l;
	}
	
	public int _autoCurrentPath;
	
	/* 자동사냥 무시 타겟 관련 변수 */
	private int _autoIgnoreTarget = 0; // 무시할 몹의 ObjectId
	private long _autoIgnoreTime = 0;  // 언제까지 무시할건지 시간 (Timestamp)

	public int getAutoIgnoreTarget() { return _autoIgnoreTarget; }
	public void setAutoIgnoreTarget(int id) { this._autoIgnoreTarget = id; }

	public long getAutoIgnoreTime() { return _autoIgnoreTime; }
	public void setAutoIgnoreTime(long time) { this._autoIgnoreTime = time; }

	private long _autoDelayTime;
	public long getAutoDelayTime() { return _autoDelayTime; }
	public void setAutoDelayTime(long l) { _autoDelayTime = l; }
	
	private int _autoNearbyMobCount;
	private long _autoThreatScanTime;

	public int getAutoNearbyMobCount() {
	    return _autoNearbyMobCount;
	}

	public void setAutoNearbyMobCount(int count) {
	    _autoNearbyMobCount = count;
	}

	public long getAutoThreatScanTime() {
	    return _autoThreatScanTime;
	}

	public void setAutoThreatScanTime(long time) {
	    _autoThreatScanTime = time;
	}
	
	private boolean isAutoHunt = false;

	public boolean getAutoHunt() {
	    return isAutoHunt;
	}

	public void setAutoHunt(boolean flag) {
	    if (isAutoHunt != flag) {
	        resetAutoRoam();
	    }
	    isAutoHunt = flag;
	}
	private int _autoRoamMapId = -1;
	private int _autoRoamState;

	private long _autoRoamHistory0;
	private long _autoRoamHistory1;
	private long _autoRoamHistory2;
	private long _autoRoamHistory3;

	public int getAutoRoamMapId() {
	    return _autoRoamMapId;
	}

	public void setAutoRoamMapId(int mapId) {
	    _autoRoamMapId = mapId;
	}

	public int getAutoRoamState() {
	    return _autoRoamState;
	}

	public void setAutoRoamState(int state) {
	    _autoRoamState = state;
	}

	public long getAutoRoamHistory0() {
	    return _autoRoamHistory0;
	}

	public void setAutoRoamHistory0(long value) {
	    _autoRoamHistory0 = value;
	}

	public long getAutoRoamHistory1() {
	    return _autoRoamHistory1;
	}

	public void setAutoRoamHistory1(long value) {
	    _autoRoamHistory1 = value;
	}

	public long getAutoRoamHistory2() {
	    return _autoRoamHistory2;
	}

	public void setAutoRoamHistory2(long value) {
	    _autoRoamHistory2 = value;
	}

	public long getAutoRoamHistory3() {
	    return _autoRoamHistory3;
	}

	public void setAutoRoamHistory3(long value) {
	    _autoRoamHistory3 = value;
	}

	public void resetAutoRoam() {
	    _autoRoamMapId = -1;
	    _autoRoamState = 0;
	    _autoRoamHistory0 = 0L;
	    _autoRoamHistory1 = 0L;
	    _autoRoamHistory2 = 0L;
	    _autoRoamHistory3 = 0L;
	    _autoLocX = 0;
	    _autoLocY = 0;
	}

	private int _autoMoveFailCount;

	public int getAutoMoveFailCount() {
		return _autoMoveFailCount;
	}

	public void setAutoMoveFailCount(int count) {
		_autoMoveFailCount = count;
	}
	
	private L1ItemInstance autoTargetItem;
	public L1ItemInstance getAutoTargetItem() {
	    return autoTargetItem;
	}

	public void setAutoTargetItem(L1ItemInstance item) {
	    this.autoTargetItem = item;
	}
	
	private boolean _isAutoSetting;

	public boolean isAutoSetting() {
		return _isAutoSetting;
	}

	public void setAutoSetting(boolean b) {
		_isAutoSetting = b;
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

	private int _autoMoveCount = 0;

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
	
	private long _autoTargetSearchTime;

	public long getAutoTargetSearchTime() {
	    return _autoTargetSearchTime;
	}

	public void setAutoTargetSearchTime(long time) {
	    _autoTargetSearchTime = time;
	}

	private int _autoStatus;

	public int getAutoStatus() {
		return _autoStatus;
	}

	public void setAutoStatus(int i) {
		_autoStatus = i;
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

	private boolean _autoDead;

	public boolean isAutoDead() {
		return _autoDead;
	}

	public void setAutoDead(boolean b) {
		_autoDead = b;
	}
	
	private int _manaDrainCount = 0;
	private int _lastTargetId = 0;

	public int get_manaDrainCount() { return _manaDrainCount; }
	public void set_manaDrainCount(int i) { _manaDrainCount = i; }

	public int get_lastTargetId() { return _lastTargetId; }
	public void set_lastTargetId(int i) { _lastTargetId = i; }
	
	private long _autoTeleportTime;

	public long getAutoTeleportTime() {
	    return _autoTeleportTime;
	}
	public void setAutoTeleportTime(long l) {
	    _autoTeleportTime = l;
	}
	// --- 자동사냥 중 귀환 시 마지막 위치 및 맵 기억 변수 ---
	private int autoHuntMapId = 0; // 자사하던 맵 ID
	public int getAutoHuntMapId() {
		return autoHuntMapId;
	}
	public void setAutoHuntMapId(int autoHuntMapId) {
		this.autoHuntMapId = autoHuntMapId;
	}
	
	private transient volatile int _sessionStamp = 0;
	public int nextSessionStamp() { return ++_sessionStamp; }
	public int getSessionStamp() { return _sessionStamp; }
	
	////////////// 아이템 착용 체크 ///////////////


	private int _totalDollHpRegenTick = 0;
	private int _totalDollMpRegenTick = 0;

	public void addDollHpRegenTick(int v) { _totalDollHpRegenTick += v; }
	public void addDollMpRegenTick(int v) { _totalDollMpRegenTick += v; }

	public int getDollHpRegenTick() { return _totalDollHpRegenTick; }
	public int getDollMpRegenTick() { return _totalDollMpRegenTick; }

	public void resetDollRegenTick() {
	    _totalDollHpRegenTick = 0;
	    _totalDollMpRegenTick = 0;
	}
	
	private transient ExpBonusState _expBonusState;
	public ExpBonusState expBonusState() {
	    ExpBonusState s = _expBonusState;
	    if (s == null) _expBonusState = s = new ExpBonusState();
	    return s;
	}

    // -------------------------------------------------------------------
	// [스피드핵 방어 로직 전용 변수] 
	// -------------------------------------------------------------------
	private int _speedHackStrikes = 0;
	private int _speedHackAnchorX = 0;
	private int _speedHackAnchorY = 0;
	private int _speedHackAnchorH = 0;

	public int getSpeedHackAnchorH() {
		return _speedHackAnchorH;
	}
	public void setSpeedHackAnchorH(int _speedHackAnchorH) {
		this._speedHackAnchorH = _speedHackAnchorH;
	}
	public int getSpeedHackStrikes() { return _speedHackStrikes; }
	public void setSpeedHackStrikes(int strikes) { _speedHackStrikes = strikes; }
	public void addSpeedHackStrike() { _speedHackStrikes++; }
	public void reduceSpeedHackStrike() { 
		if (_speedHackStrikes > 0) _speedHackStrikes--; 
	}
	public int getSpeedHackAnchorX() { return _speedHackAnchorX; }
	public void setSpeedHackAnchorX(int x) { _speedHackAnchorX = x; }

	public int getSpeedHackAnchorY() { return _speedHackAnchorY; }
	public void setSpeedHackAnchorY(int y) { _speedHackAnchorY = y; }
	
	private final SlotChangeManager _slotChange = new SlotChangeManager(this);
	public int getSlotNumber() {
		return _slotChange.getSlotNumber();
	}
	public void setSlotNumber(int i) {
		_slotChange.setSlotNumber(i);
	}
	public void addSlotItem(int slotNum, int itemobjid, boolean flag) {
		_slotChange.addSlotItem(slotNum, itemobjid, flag);
	}
	public List<Integer> getSlotItems(int slotNum) {
		return _slotChange.getSlotItems(slotNum);
	}
	public void getChangeSlot(int slotNum) {
		_slotChange.applySlot(slotNum);
	}

	public void clearSlotChange() {
		_slotChange.clear();
	}
	// 바포메트 시스템 
	public void restoreBapoLevel() {
	    if (!Config.바포시스템) return;
	    
	    setOBapoLevel(-1);
	    setNBapoLevel(-1);
	    
	    if(getLevel() <= 50) {
	    	SC_PacketBox.send(this, SC_PacketBox.BAPO, 6, true);
	    	return;
	    }    
	    CIOThreadCallback.LawfulBonus(this);
	}
	/**
	 * 캐릭터 상속으로 인해 노드 객체를 많이 생성하기 때문에 피시인스턴스로 이관
	 */
	private volatile L1SmallIntMap<L1NpcInstance> _petlist;
	private volatile L1SmallIntMap<L1SupportInstance> _supportlist;
	private volatile L1SmallIntMap<L1DollInstance> _dolllist;
	private volatile L1SmallIntMap<L1FollowerInstance> _followerlist;

	@Override
	public void dispose() {
		if (_petlist != null) {
			_petlist.clear();
			_petlist = null;
		}
		if (_supportlist != null) {
			_supportlist.clear();
			_supportlist = null;
		}
		if (_dolllist != null) {
			_dolllist.clear();
			_dolllist = null;
		}
		if (_followerlist != null) {
			_followerlist.clear();
			_followerlist = null;
		}
		_creatureController.ClearItemDelays();

		super.dispose();
	}
	
	public boolean addItemDelay(int delayId, int delayTime) {
	    return _creatureController.AddItemDelay(delayId, delayTime);
	}

	public void removeItemDelay(int delayId) {
	    _creatureController.RemoveItemDelay(delayId);
	}

	public boolean hasItemDelay(int delayId) {
	    return _creatureController.HasItemDelay(delayId);
	}

	private L1SmallIntMap<L1NpcInstance> ensurePetList() {
	    L1SmallIntMap<L1NpcInstance> list = _petlist;

	    if (list != null) {
	        return list;
	    }

	    synchronized (this) {
	        list = _petlist;

	        if (list == null) {
	            list = new L1SmallIntMap<L1NpcInstance>(2);
	            _petlist = list;
	        }

	        return list;
	    }
	}

	private L1SmallIntMap<L1SupportInstance> ensureSupportList() {
	    L1SmallIntMap<L1SupportInstance> list = _supportlist;

	    if (list != null) {
	        return list;
	    }

	    synchronized (this) {
	        list = _supportlist;

	        if (list == null) {
	            list = new L1SmallIntMap<L1SupportInstance>(1);
	            _supportlist = list;
	        }

	        return list;
	    }
	}

	private L1SmallIntMap<L1DollInstance> ensureDollList() {
	    L1SmallIntMap<L1DollInstance> list = _dolllist;

	    if (list != null) {
	        return list;
	    }

	    synchronized (this) {
	        list = _dolllist;

	        if (list == null) {
	            list = new L1SmallIntMap<L1DollInstance>(2);
	            _dolllist = list;
	        }

	        return list;
	    }
	}

	private L1SmallIntMap<L1FollowerInstance> ensureFollowerList() {
	    L1SmallIntMap<L1FollowerInstance> list = _followerlist;

	    if (list != null) {
	        return list;
	    }

	    synchronized (this) {
	        list = _followerlist;

	        if (list == null) {
	            list = new L1SmallIntMap<L1FollowerInstance>(1);
	            _followerlist = list;
	        }

	        return list;
	    }
	}

	public void addPet(L1NpcInstance npc) {
	    ensurePetList().put(npc.getId(), npc);
	    sendPetCtrlMenu(npc, true);
	}

	public void removePet(L1NpcInstance npc) {
	    final L1SmallIntMap<L1NpcInstance> list = _petlist;

	    if (list != null) {
	        list.remove(npc.getId());
	    }

	    sendPetCtrlMenu(npc, true);
	}

	public Map<Integer, L1NpcInstance> getPetList() {
	    final L1SmallIntMap<L1NpcInstance> list = _petlist;

	    return list != null
	            ? list
	            : Collections.<Integer, L1NpcInstance>emptyMap();
	}

    // Last summoned eligible doll owns the attack proc. No per-hit list scan.
    private transient volatile L1DollInstance combatProcDoll;

    public L1DollInstance getCombatProcDoll() { return combatProcDoll; }

	public void addDoll(L1DollInstance doll) {
	    ensureDollList().put(doll.getId(), doll);
        if (l1j.server.server.model.character.ItemSkill.Doll.MagicDollCombatProcs.isProcDoll(doll.getDollType())) {
            // Resolve the immutable configuration at summon time, never on the first hot attack.
            l1j.server.server.model.character.ItemSkill.Doll.MagicDollCombatConfig.get();
            combatProcDoll = doll;
        }
	}

	public void removeDoll(L1DollInstance doll) {
	    final L1SmallIntMap<L1DollInstance> list = _dolllist;
	    if (list != null) {
	        list.remove(doll.getId(), doll);
	    }
        if (combatProcDoll == doll) {
            L1DollInstance next = null;
            if (list != null) {
                for (L1DollInstance remaining : list.values()) {
                    if (remaining != null && !remaining.isDeleted()
                            && l1j.server.server.model.character.ItemSkill.Doll.MagicDollCombatProcs.isProcDoll(remaining.getDollType())) {
                        next = remaining;
                        break;
                    }
                }
            }
            combatProcDoll = next;
        }
	}

	public Map<Integer, L1DollInstance> getDollList() {
	    final L1SmallIntMap<L1DollInstance> list = _dolllist;

	    return list != null
	            ? list
	            : Collections.<Integer, L1DollInstance>emptyMap();
	}

	public void addSupport(L1SupportInstance support) {
	    ensureSupportList().put(support.getId(), support);
	}

	public void removeSupport(L1SupportInstance support) {
	    final L1SmallIntMap<L1SupportInstance> list = _supportlist;

	    if (list != null) {
	        list.remove(support.getId());
	    }
	}

	public Map<Integer, L1SupportInstance> getSupportList() {
	    final L1SmallIntMap<L1SupportInstance> list = _supportlist;

	    return list != null
	            ? list
	            : Collections.<Integer, L1SupportInstance>emptyMap();
	}

	public void addFollower(L1FollowerInstance follower) {
	    ensureFollowerList().put(follower.getId(), follower);
	}

	public void removeFollower(L1FollowerInstance follower) {
	    final L1SmallIntMap<L1FollowerInstance> list = _followerlist;

	    if (list != null) {
	        list.remove(follower.getId());
	    }
	}

	public Map<Integer, L1FollowerInstance> getFollowerList() {
	    final L1SmallIntMap<L1FollowerInstance> list = _followerlist;

	    return list != null
	            ? list
	            : Collections.<Integer, L1FollowerInstance>emptyMap();
	}

	private final AtomicInteger _multiHitToken = new AtomicInteger();

	public int newMultiHitToken() {
	    return _multiHitToken.incrementAndGet();
	}

	public int getMultiHitToken() {
	    return _multiHitToken.get();
	}
	
	// =====================================================
	// Fou Slayer WeakExpose
	// =====================================================
	private volatile int fouToken;
	private volatile int fouRemainCharges;   // 0~4 (포우 시작 시 스냅샷)
	private volatile int fouHitsTotal;       // 보통 3
	private volatile int fouHitsConsumed;    // 0..hitsTotal

	public void fouBegin(int token, int charges, int hits) {
	    this.fouToken = token;
	    this.fouRemainCharges = Math.max(0, Math.min(4, charges));
	    this.fouHitsTotal = hits;
	    this.fouHitsConsumed = 0;
	}

	public boolean fouIsActive(int token) {
	    return token != 0 && this.fouToken == token && fouHitsConsumed < fouHitsTotal;
	}
	public int fouConsumeAddThisHit(int token) {
	    if (!fouIsActive(token)) return 0;

	    fouHitsConsumed++;
	    if (fouRemainCharges <= 0) return 0;
	    fouRemainCharges--;
	    return 20;
	}

	public boolean fouIsFinished(int token) {
	    return token != 0 && this.fouToken == token && fouHitsConsumed >= fouHitsTotal;
	}

	public int fouGetRemainCharges(int token) {
	    if (token == 0 || fouToken != token) return 0;
	    return fouRemainCharges;
	}

	public void fouClear() {
	    fouToken = 0;
	    fouRemainCharges = 0;
	    fouHitsTotal = 0;
	    fouHitsConsumed = 0;
	}
	
	private int targetId;
	public int getTargetId() {
		return this.targetId;
	}
	public void setTargetId(int targetId) {
		this.targetId = targetId;
	}
	/**
	 * 투명 아이템 사용
	 */
	public void invisibleItem() {
	    if (hasSkillEffect(L1SkillId.INVISIBILITY)) {
	        return;
	    }
	    removeSkillEffect(L1SkillId.BLIND_HIDING);
	    setSkillEffect(L1SkillId.INVISIBILITY, 0);
	
	    invisible();
	}

	/**
	 * 투명 상태 시작
	 */
	public void invisible() {

	    // 본인
	    SC_Invis.send(this, getId(), 1);
		NpcEventFactory.onPlayerInvisibility(this);
	    // 인형 제거
	    if (getDollList() != null) {
	        for (L1DollInstance doll : getDollList().values()) {
	            if (doll != null) {
	                doll.deleteDoll(false);
	            }
	        }
	    }

	    final L1Party party = isInParty() ? getParty() : null;

	    final int clanId = getClanid();

	    for (L1PcInstance viewer : L1World.getInstance() .getVisiblePlayerFast(this)) {
	        if (viewer == null) {
	            continue;
	        }

	        final boolean sameParty = party != null && party.isMember(viewer);
	        final boolean sameClan = clanId != 0 && clanId == viewer.getClanid();
	        final boolean showName = sameParty || sameClan || viewer.canDetectInvisible();

	        syncInvisibleObject(viewer, this, showName);
	    }
	}
	
	public boolean canDetectInvisible() {
	    return isBlind() && hasSkillEffect(L1SkillId.STATUS_FLOATING_EYE);
	}

	/**
	 * 투명 상태 해제
	 */
	public void delInvis() {

	    if (isGmInvis()) {
	        return;
	    }

	    boolean changed = false;
	    if (hasSkillEffect(L1SkillId.INVISIBILITY)) {
	        killSkillEffectTimer(L1SkillId.INVISIBILITY);
	        changed = true;
	    }
	    if (hasSkillEffect(L1SkillId.BLIND_HIDING)) {
	        killSkillEffectTimer( L1SkillId.BLIND_HIDING);
	        changed = true;
	    }
	    if (changed) {
	        delInvisObject();
	    }
	}

	/**
	 * 블라인드 하이딩 해제
	 */
	public void delBlindHiding() {
	    if (hasSkillEffect(L1SkillId.BLIND_HIDING)) {
	        killSkillEffectTimer(L1SkillId.BLIND_HIDING);
	    }
	    delInvisObject();
	}

	/**
	 * 투명 상태 종료 후
	 * 캐릭터 화면에 복구
	 */
	public void delInvisObject() {

	    

	    // 투명 해제
	    SC_Invis.send(this, getId(), 0);

	    for (L1PcInstance viewer
	            : L1World.getInstance()
	                    .getVisiblePlayerFast(this)) {

	        if (viewer == null) {
	            continue;
	        }

	        SC_WorldPutObject.get(
	                viewer.getNetConnection(),
	                this);

	        // 투명 해제
	        SC_Invis.send(viewer, getId(), 0);
	    }
	}

	/**
	 * 괴물눈 고기 + 눈멀기 상태가 되었을 때 화면 안의 투명 캐릭터를 보여준다.
	 * (파티원 / 혈맹원은 원래 보이므로 제외)
	 */
	public void showFloatingEyeToInvis() {

	    if (!canDetectInvisible()) {
	        return;
	    }

	    final L1Party party =
	            isInParty() ? getParty() : null;

	    final int clanId =
	            getClanid();

	    for (L1PcInstance target
	            : L1World.getInstance()
	                    .getVisiblePlayerFast(this)) {

	        if (target == null
	                || !target.isInvisble()) {
	            continue;
	        }

	        if (clanId != 0
	                && clanId == target.getClanid()) {
	            continue;
	        }

	        if (party != null
	                && party.isMember(target)) {
	            continue;
	        }

	        syncInvisibleObject(
	                this,
	                target,
	                true);
	    }
	}

	/**
	 * 눈멀기 상태가 해제되어 괴물눈의 투명 감지가 끝났을 때
	 * 화면의 투명 캐릭터를 다시 감춘다.
	 */
	public void hideBlindToInvis() {

	    if (canDetectInvisible()) {
	        return;
	    }

	    final L1Party party =
	            isInParty() ? getParty() : null;

	    final int clanId =
	            getClanid();

	    for (L1PcInstance target
	            : L1World.getInstance()
	                    .getVisiblePlayerFast(this)) {

	        if (target == null
	                || !target.isInvisble()) {
	            continue;
	        }

	        if (clanId != 0
	                && clanId == target.getClanid()) {
	            continue;
	        }

	        if (party != null
	                && party.isMember(target)) {
	            continue;
	        }

	        syncInvisibleObject(
	                this,
	                target,
	                false);
	    }
	}

	private static void syncInvisibleObject(
	        final L1PcInstance viewer,
	        final L1PcInstance target,
	        final boolean showName) {

	    if (viewer == null
	            || target == null) {
	        return;
	    }

	    final CIOClient client = viewer.getNetConnection();

	    if (showName) {
	        SC_WorldPutObject.getDetectedInvis(
	                client,
	                target);

	    } else {
	        SC_WorldPutObject.getinvis(
	                client,
	                target,
	                true);
	    }

	    SC_Invis.send(viewer, 
	                    target.getId(),
	                    1);
	}


	/**
	 * User::InSight / OutOfSight 쪽에서 사용할 최종  화면 판정.
	 * 화면 모양은  Point.isInScreen()을 그대로 사용한다.
	 */
	public boolean canSeeObject(L1Object obj) {
		if (obj == null || obj == this) {
			return false;
		}
		if (obj.getMapId() != getMapId()) {
			return false;
		}
		
		final int range = Config.PC_RECOGNIZE_RANGE;
		if (range == -1) {
			return getLocation().isInScreen(obj.getLocation());
		}
		return getLocation().getTileLineDistance(obj.getLocation()) <= range;
	}

	
	public void inSight(L1Object obj) {
		if (obj == null || obj == this || knownsObject(obj) || !canSeeObject(obj)) {
			return;
		}
		obj.onPerceive(this);

	}

	public void outOfSight(L1Object obj) {
		if (obj == null || obj == this || !knownsObject(obj)) {
			return;
		}

		removeKnownObject(obj);
		SC_RemoveObject.send(
				getNetConnection(),
				obj);
	}

	public void onWorldMove(int oldMap, int oldX, int oldY) {
		
		for (L1PcInstance viewer : getKnownPlayers()) {
			if (viewer == null || viewer == this) {
				continue;
			}
			if (!viewer.canSeeObject(this)) {
				viewer.outOfSight(this);
			}
		}
		for (L1Object known : getKnownObjects()) {
			if (known == null || known == this) {
				continue;
			}
			if (!canSeeObject(known)) {
				outOfSight(known);
			}
		}

		final List<L1Object> nearby = L1World.getInstance().getClusterGrid().queryNearbyReusable(this, Config.PC_RECOGNIZE_RANGE);

		for (int i = 0, size = nearby.size(); i < size; i++) {
			final L1Object visible = nearby.get(i);

			if (visible == null || visible == this || !canSeeObject(visible)) {
				continue;
			}
			reconcileSight(visible);
			if (visible instanceof L1PcInstance) {
				final L1PcInstance viewer = (L1PcInstance) visible;
				if (viewer.canSeeObject(this)) {
					viewer.inSight(this);
				} else {
					viewer.outOfSight(this);
				}
			}
		}
	}
	
	public void reconcileSight(L1Object obj) {
		if (obj == null || obj == this) {
			return;
		}

		if (!canSeeObject(obj)) {
			outOfSight(obj);
			return;
		}

		if (!knownsObject(obj)) {
			inSight(obj);
		}
		if (knownsObject(obj) && obj instanceof L1NpcInstance) {
			final L1NpcInstance npc = (L1NpcInstance) obj;

			if (npc.getHiddenStatus() != 0) {
				npc.approachPlayer(this);
			}
		}
	}
	
	public void refreshSight() {
		for (L1Object known : getKnownObjects()) {
			if (known != null && !canSeeObject(known)) {
				outOfSight(known);
			}
		}
		for (L1Object visible : L1World.getInstance().getClusterGrid().queryNearbyReusable(this, Config.PC_RECOGNIZE_RANGE)) {
			if (visible == null || visible == this) {
				continue;
			}
			reconcileSight(visible);
		}
	}
	
	public void updateHpBar() {
		if (!hasSkillEffect(L1SkillId.GMSTATUS_HPBAR)) {
			return;
		}

		for (L1Object visible : L1World.getInstance().getClusterGrid().queryNearbyReusable(this, Config.PC_RECOGNIZE_RANGE)) {
			if (visible == null
					|| !canSeeObject(visible)
					|| !L1HpBar.isHpBarTarget(visible) ) {
				continue;
			}
			SC_HPMeter.send(this, (L1Character) visible);
		}
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
		if (mon == null || !_autoTargetList.containsKey(mon)) {
			return;
		}
		_autoTargetList.remove(mon);
	}

	private boolean _autoTargetDirty;

	public boolean isAutoTargetDirty() {
		return _autoTargetDirty;
	}

	public void setAutoTargetDirty(boolean dirty) {
		_autoTargetDirty = dirty;
	}
	
	// 트루타겟 
	public int _trueTarget = 0;
	public int getTrueTarget() {
		return _trueTarget;
	}
	public void setTrueTarget(int i) {
		_trueTarget = i;
	}
	private volatile int _trueTargetId;

	public int getTrueTargetId() {
	    return _trueTargetId;
	}
	public void setTrueTargetId(int targetId) {
	    _trueTargetId = targetId;
	}
	public void clearTrueTargetId() {
	    _trueTargetId = 0;
	}
	
	private final CCreatureController _creatureController;
	public CCreatureController getCreatureController() {
		return _creatureController;
	}
	
	private final CRegenController _regenController;
	public CRegenController getRegenController() {
		return _regenController;
	}
}
