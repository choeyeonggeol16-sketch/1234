package l1j.server.server.model.Instance;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import l1j.server.Config;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.item.function.ItemBonusHooks;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.CNpcItemUse;
import l1j.server.server.model.npc.ai.action.dFollow;
import l1j.server.server.model.npc.ai.action.dPetDefault;
import l1j.server.server.packets.Protocol.MainServer.Pledge.Proto.SC_REST_EXP_INFO_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_DOLL_ON_OFF;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_WORLD_PUT_OBJECT_NOTI;
import l1j.server.server.shared.L1Type;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.eKind;

public class L1DollInstance extends L1NpcInstance {
	private static final long serialVersionUID = 1L;

	// TODO 1단계 마법인형
	public static final int DOLLTYPE_STONEGOLEM = 1; // 돌골렘 
	public static final int DOLLTYPE_WAREWOLF = 2; // 늑대인간
	public static final int DOLLTYPE_BUGBEAR = 3; // 버그베어
	public static final int DOLLTYPE_CRUSTACEA = 4; // 크러스트 시안 
	public static final int DOLLTYPE_SNOWMAN = 5; // 에티
	public static final int DOLLTYPE_MOKAK = 6; // 목각

	// TODO 2단계 마법인형
	public static final int DOLLTYPE_SUCCUBUS = 7; // 서큐버스
	public static final int DOLLTYPE_ELDER = 8; // 장로
	public static final int DOLLTYPE_COCA = 9; // 코카
	public static final int DOLLTYPE_SNOWMAN_NEW = 10; // 눈사람
	public static final int DOLLTYPE_SNOWMAN_A = 11; // 눈사람(A)
	public static final int DOLLTYPE_SNOWMAN_B = 12; // 눈사람(B)
	public static final int DOLLTYPE_SNOWMAN_C = 13; // 눈사람(C)
	public static final int DOLLTYPE_인어 = 14; // 인어
	public static final int DOLLTYPE_LAVAGOLREM = 15; // 라바 골렘

	// TODO 3단계 마법인형
	public static final int DOLLTYPE_자이언트 = 16; // 자이언트
	public static final int DOLLTYPE_흑장로 = 17; // 흑장로
	public static final int DOLLTYPE_SUCCUBUSQUEEN = 18; // 서큐버스 퀸
	public static final int DOLLTYPE_DRAKE = 19;// 드레이크
	public static final int DOLLTYPE_킹버그베어 = 20; // 킹 버그베어
	public static final int DOLLTYPE_DIAMONDGOLREM = 21; // 다이아몬드 골렘

	// TODO 4단계 마법인형
	public static final int DOLLTYPE_LICH = 22; // 리치
	public static final int DOLLTYPE_사이클롭스 = 23; // 사이클롭스
	public static final int DOLLTYPE_NIGHTBALD = 24; // 나이트발드
	public static final int DOLLTYPE_SIER = 25; // 시어
	public static final int DOLL_Iris = 26; // 아이리스
	public static final int DOLL_vampire = 27; // 뱀파이어
	public static final int DOLL_mummy = 28; // 머미로드

	// TODO 5단계 마법인형
	public static final int DOLLTYPE_DEMON = 29; // 데몬
	public static final int DOLLTYPE_DEATHNIGHT = 30; // 데스나이트
	public static final int DOLL_barranca = 31; // 바란카
	public static final int DOLL_corruption = 32; // 타락
	public static final int DOLL_Baphomet = 33; // 바포메트
	public static final int DOLL_icequeen = 34; // 얼음여왕
	public static final int DOLL_Kouts = 35; // 커츠

	// TODO 기타 마법인형
	public static final int DOLLTYPE_SEADANCER = 36; // 시댄서
	public static final int DOLLTYPE_SKELETON = 37; // 스파토이
	public static final int DOLLTYPE_SCARECROW = 38; // 허수아비
	public static final int DOLLTYPE_ETHYNE = 39; // 에틴
	public static final int DOLLTYPE_PIXIE_BLAG = 40; // 블레그
	public static final int DOLLTYPE_PIXIE_LESDAG = 41; // 레더그
	public static final int DOLLTYPE_PIXIE_ELREGEU = 42; // 엘레그
	public static final int DOLLTYPE_PIXIE_GREG = 43; // 그레그
	public static final int DOLLTYPE_HATCHLING = 44; // 해츨링

	// TODO 이벤트 마법인형
	public static final int DOLLTYPE_PSY_CHAMPION = 45; // 싸이
	public static final int DOLLTYPE_PSY_BIRD = 46; // 싸이
	public static final int DOLLTYPE_PSY_GANGNAM_STYLE = 47; // 싸이
	public static final int DOLLTYPE_GREMLIN = 48; // 그렘린
	public static final int DOLLTYPE_COBO = 49; // 다이노스
	public static final int 수련자마법인형 = 50; // 스파토이 수련자
	public static final int ruler1 = 51; // 지배자의 현신 (1등급)
	public static final int ruler2 = 52; // 지배자의 현신 (2등급)
	public static final int ruler3 = 53; // 지배자의 현신 (3등급)
	public static final int ruler4 = 54; // 지배자의 현신 (4등급)
	public static final int ruler5 = 55; // 지배자의 현신 (1등급) 1일
	public static final int ruler6 = 56; // 지배자의 현신 (2등급) 1일 
	public static final int ruler7 = 57; // 지배자의 현신 (3등급) 1일
	public static final int ruler8 = 58; // 지배자의 현신 (4등급) 1일
	public static final int Antaras = 59; // 마법인형: 안타라스
	public static final int Papou = 60; // 마법인형: 파푸리온
	public static final int Lynd = 61; // 마법인형: 린드비오르
	public static final int Valakas = 62; // 마법인형: 발라카스
	public static final int jindeathknight = 63; // 마법인형: 진 데스나이트
	public static final int pnix = 85;
	public static final int brich = 86;
	public static final int behe = 87; 
	public static final int au = 88;
	public static final int hal = 89;
	public static final int desel = 90;
	public static final int lanta = 91;
	public static final int lpap = 92;
	public static final int llynd = 93;
	public static final int lvala = 94;

	/////
	public static final int DOLLTYPE_축서큐퀸 = 64;
	public static final int DOLLTYPE_축흑장로 = 65;
	public static final int DOLLTYPE_축자이언트 = 66;
	public static final int DOLLTYPE_축드레이크 = 67;
	public static final int DOLLTYPE_축킹버그 = 68;
	public static final int DOLLTYPE_축다이아골렘 = 69;
	public static final int DOLLTYPE_축사이클롭스 = 70;
	public static final int DOLLTYPE_축리치 = 71;
	public static final int DOLLTYPE_축나발 = 72;
	public static final int DOLLTYPE_축시어 = 73;
	public static final int DOLLTYPE_축아이리스 = 74;
	public static final int DOLLTYPE_축뱀파 = 75;
	public static final int DOLLTYPE_축머미 = 76;
	public static final int DOLLTYPE_축데몬 = 77;
	public static final int DOLLTYPE_축데스 = 78;
	public static final int DOLLTYPE_축바란카 = 79;
	public static final int DOLLTYPE_축타락 = 80;
	public static final int DOLLTYPE_축바포 = 81;
	public static final int DOLLTYPE_축얼녀 = 82;
	public static final int DOLLTYPE_축커츠 = 83;
	public static final int DOLLTYPE_기르타스 = 95;
	// TODO 그외 설정
	public static final int DOLL_TIME = 1800000;
	private static final int TIMER_LIFETIME = 1;
	private static final int TIMER_LIFETIME_GENERATION = 1;

	private int _dollType;
	private int _dollLevel;
	private int _itemObjId;
	
    public transient double _expBonusDeltaExp = 0.0;
    public int _dollHpTickDelta = 0;
    public int _dollMpTickDelta = 0;

	// 타겟이 없는 경우의 처리
	@Override
	public boolean noTarget() {
		if (_master == null) {
			deleteDoll(true);
			return true;
		}
		if (_master.isDead()) {
			deleteDoll(true);
			return true;
		}
		if (_master.isInvisble()) {
			deleteDoll(true);
			return true;
		}
		if (_master.getMapId() == getMapId()) {
			final int distance = getLocation().getTileLineDistance(_master.getLocation());

			if(distance >= 40) {
				// Teleport is an L1 doll rule. Retire the old follow
				// destination before moving to the master's new position.
				dFollow.clear(this);
				teleport(_master.getX(), _master.getY(), getHeading());
				return false;
			}
			// Include arrival at <=2 tiles: NC dFollow clears its cached
			// destination when the follower has reached the master.
			return dPetDefault.follow(this, _master);
		}
		deleteDoll(true);
		return true;
	}
	
	@Override
	public boolean noTargetMaster() {
		return noTarget();
	}

	private final AtomicBoolean _cleanupDone = new AtomicBoolean(false);

	public L1DollInstance(L1Npc template, L1PcInstance master, int itemlevel, int dollType, int itemObjId, int bless) {
		super(template);
		setId(IdFactory.getInstance().nextId());
		setDollType(dollType);
		setDollLevel(itemlevel);
		setItemObjId(itemObjId);
		
		_cleanupDone.set(false);
		
		setDollBless(bless);
		setMaster(master);

		setMap(master.getMapId());

		int nx = master.getX() + ThreadLocalRandom.current().nextInt(5) - 2;
		int ny = master.getY() + ThreadLocalRandom.current().nextInt(5) - 2;

		if (!getMap().isInMap(nx, ny) || !getMap().isPassable(nx, ny)) {
			nx = master.getX();
			ny = master.getY();
		}
		setX(nx);
		setY(ny);
		
		setHeading(ThreadLocalRandom.current().nextInt(8));
		
		setLightSize(template.getLightSize());

		L1World.getInstance().storeAndEnterWorld(this);
		for (L1PcInstance pc : L1World.getInstance().getRecognizePlayer(this)) onPerceive(pc);

		master.addDoll(this);
		if (!isAiRunning()) startAI();

		// Apply direct options and lifecycle bonuses once on summon.
		handleDollOption(1);
		ItemBonusHooks.onDollSpawned(master, this);

		CIOThreadCallback.getInstance().scheduleNpcTimer(
				this,
				TIMER_LIFETIME,
				TIMER_LIFETIME_GENERATION,
				DOLL_TIME);
	}

	/**
	 * 인형의 모든 스탯/잠재력 옵션을 통합 관리 (f가 1이면 추가, -1이면 삭제)
	 */
	private void handleDollOption(int f) {
		L1PcInstance pc = (L1PcInstance) _master;
		if (pc == null) return;

		//  잠재력 옵션 (level 기반)
		if (Config.잠재력활성화) {
			int level = getDollLevel();
			switch (level) {
			case 1: pc.add_melee_critical_rate(1 * f); break;
			case 2: pc.add_missile_critical_rate(1 * f); break;
			case 3: pc.add_magic_critical_rate(1 * f); break;
			case 4: pc.addDmgup(1 * f); break;
			case 5: pc.addBowDmgup(1 * f); break;
			case 6: pc.addHitup(1 * f); break;
			case 7: pc.addBowHitup(1 * f); break;
			case 8: pc.addDamageReductionByArmor(1 * f); break;
			case 9: pc.addAc(-1 * f); break;
			case 10: pc.AddMDefend(3 * f); break;
			case 11: pc.addMaxHp(30 * f); break;
			case 12: pc.addMaxMp(20 * f); break;
			case 13: pc.AddPvpDamage(1 * f); break;
			case 14: pc.AddPvpDamageReduction(1 * f); break;
			case 15: pc.AddFireResist(10 * f); break;
			case 16: pc.AddEarthResist(10 * f); break;
			case 17: pc.AddWaterResist(10 * f); break;
			case 18: pc.AddWindResist(10 * f); break;
			case 19: pc.addWeightReduction(100 * f); break;
			case 20: pc.add_melee_critical_rate(3 * f); break;
			case 21: pc.add_missile_critical_rate(3 * f); break;
			case 22: pc.add_magic_critical_rate(3 * f); break;
			case 23: pc.addDmgup(2 * f); break;
			case 24: pc.addBowDmgup(2 * f); break;
			case 25: pc.addHitup(2 * f); break;
			case 26: pc.addBowHitup(2 * f); break;
			case 27: 
				pc.addDamageReductionByArmor(2 * f);
				break;
			case 28: pc.addAc(-2 * f); break;
			case 29: pc.AddMDefend(6 * f); break;
			case 30: pc.addMaxHp(60 * f); break;
			case 31: pc.addMaxMp(40 * f); break;
			case 32: pc.addDg(3 * f); break;
			case 35: pc.addEffectedER(3 * f); break;
			case 36: pc.AddPvpDamage(2 * f); break;
			case 37: pc.AddPvpDamageReduction(2 * f); break;
			case 38: handleSkillEffect(L1SkillId.잠재력pvp마법감소3, f); break;
			case 39: pc.AddSpecialResistance(eKind.ABILITY, 3 * f); break;
			case 40: pc.AddSpecialResistance(eKind.SPIRIT, 3 * f); break;
			case 41: pc.AddSpecialResistance(eKind.DRAGON_SPELL, 3 * f); break;
			case 42: pc.AddSpecialResistance(eKind.FEAR, 3 * f); break;
			case 43: pc.AddFireResist(20 * f); break;
			case 44: pc.AddEarthResist(20 * f); break;
			case 45: pc.AddWaterResist(20 * f); break;
			case 46: pc.AddWindResist(20 * f); break;
			case 47: pc.AddStatBonusStr((byte) (1 * f)); break;
			case 48: pc.AddStatBonusDex((byte) (1 * f)); break;
			case 49: pc.AddStatBonusCon((byte) (1 * f)); break;
			case 50: pc.AddStatBonusWis((byte) (1 * f)); break;
			case 51: pc.AddStatBonusInt((byte) (1 * f)); break;
			case 52: pc.addWeightReduction(200 * f); break;
			case 53: pc.add_melee_critical_rate(5 * f); break;
			case 54: pc.add_missile_critical_rate(5 * f); break;
			case 55: pc.add_magic_critical_rate(5 * f); break;
			case 56: pc.addDmgup(3 * f); break;
			case 57: pc.addBowDmgup(3 * f); break;
			case 58: pc.addHitup(3 * f); break;
			case 59: pc.addBowHitup(3 * f); break;
			case 60: pc.AddStatSp(3 * f); break;
			case 61: pc.addBaseMagicHitUp(3 * f); break;
			case 62:
				pc.addDamageReductionByArmor(3 * f);
				break;
			case 63: pc.addAc(-3 * f); break;
			case 64: pc.AddMDefend(10 * f); break;
			case 65: pc.addMaxHp(150 * f); break;
			case 66: pc.addMaxMp(100 * f); break;
			case 67: pc.addDg(5 * f); break;
			case 70: pc.addEffectedER(5 * f); break;
			case 71: 
				pc.AddPvpDamage(3 * f);

				break;
			case 72: pc.AddPvpDamageReduction(3 * f); break;
			case 73: handleSkillEffect(L1SkillId.잠재력pvp마법감소2, f); break;
			case 74: pc.AddSpecialResistance(eKind.ABILITY, 5 * f); break;
			case 75: pc.AddSpecialResistance(eKind.SPIRIT, 5 * f); break;
			case 76: pc.AddSpecialResistance(eKind.DRAGON_SPELL, 5 * f); break;
			case 77: pc.AddSpecialResistance(eKind.FEAR, 5 * f); break;
			case 78: pc.AddSpecialPierce(eKind.ABILITY, 3 * f); break;
			case 79: pc.AddSpecialPierce(eKind.SPIRIT, 3 * f); break;
			case 80: pc.AddSpecialPierce(eKind.DRAGON_SPELL, 3 * f); break;
			case 81: pc.AddSpecialPierce(eKind.FEAR, 3 * f); break;
			case 82: pc.AddFireResist(30 * f); break;
			case 83: pc.AddEarthResist(30 * f); break;
			case 84: pc.AddWaterResist(30 * f); break;
			case 85: pc.AddWindResist(30 * f); break;
			case 86: pc.AddAllNaturalResistance(10 * f); break;
			case 87: pc.AddStatBonusStr((byte) (2 * f)); break;
			case 88: pc.AddStatBonusDex((byte) (2 * f)); break;
			case 89: pc.AddStatBonusCon((byte) (2 * f)); break;
			case 90: pc.AddStatBonusWis((byte) (2 * f)); break;
			case 91: pc.AddStatBonusInt((byte) (2 * f)); break;
			case 92: pc.addWeightReduction(300 * f); break;
			case 93: pc.addDmgup(5 * f); break;
			case 94: pc.addBowDmgup(5 * f); break;
			case 95: pc.addHitup(5 * f); break;
			case 96: pc.addBowHitup(5 * f); break;
			case 97: pc.AddStatSp(5 * f); break;
			case 98: pc.addBaseMagicHitUp(5 * f); break;
			case 99: pc.addDamageReductionByArmor(5 * f); break;
			case 100: pc.addAc(-5 * f); break;
			case 101: pc.AddMDefend(15 * f); break;
			case 102: pc.addMaxHp(300 * f); break;
			case 103: pc.addMaxMp(200 * f); break;
			case 104: pc.addDg(10 * f); break;
			case 107: pc.addEffectedER(12 * f); break;
			case 108: pc.AddPvpDamage(7 * f); break;
			case 109: pc.AddPvpDamageReduction(7 * f); break;
			case 110: handleSkillEffect(L1SkillId.잠재력pvp마법감소1, f); break;
			case 111: handleSkillEffect(L1SkillId.잠재력pvp감소무시, f); break;
			case 112: pc.AddSpecialResistance(eKind.ABILITY, 8 * f); break;
			case 113: pc.AddSpecialResistance(eKind.SPIRIT, 8 * f); break;
			case 114: pc.AddSpecialResistance(eKind.DRAGON_SPELL, 8 * f); break;
			case 115: pc.AddSpecialResistance(eKind.FEAR, 8 * f); break;
			case 116: pc.AddSpecialResistance(eKind.ALL, 3 * f); break;
			case 117: pc.AddSpecialPierce(eKind.ABILITY, 5 * f); break;
			case 118: pc.AddSpecialPierce(eKind.SPIRIT, 5 * f); break;
			case 119: pc.AddSpecialPierce(eKind.DRAGON_SPELL, 5 * f); break;
			case 120: pc.AddSpecialPierce(eKind.FEAR, 5 * f); break;
			case 121: pc.AddSpecialPierce(eKind.ALL, 3 * f); break;
			case 122: pc.AddAllNaturalResistance(30 * f); break;
			case 123: pc.AddStatBonusStr((byte) (3 * f)); break;
			case 124: pc.AddStatBonusDex((byte) (3 * f)); break;
			case 125: pc.AddStatBonusCon((byte) (3 * f)); break;
			case 126: pc.AddStatBonusWis((byte) (3 * f)); break;
			case 127: pc.AddStatBonusInt((byte) (3 * f)); break;
			case 128:
				pc.AddStatBonusStr((byte) (1 * f)); pc.AddStatBonusDex((byte) (1 * f));
				pc.AddStatBonusCon((byte) (1 * f)); pc.AddStatBonusWis((byte) (1 * f));
				pc.AddStatBonusInt((byte) (1 * f)); break;
			case 129: handleSkillEffect(L1SkillId.잠재력영웅마나, f); break;
			case 130: pc.addWeightReduction(500 * f); break;
			case 131: handleSkillEffect(L1SkillId.잠재력디케이, f); break;
			case 132: handleSkillEffect(L1SkillId.잠재력pvp감소무시1, f); break;
			case 133:
				pc.AddStatBonusStr((byte) (3 * f)); pc.AddStatBonusDex((byte) (3 * f));
				pc.AddStatBonusCon((byte) (3 * f)); pc.AddStatBonusWis((byte) (3 * f));
				pc.AddStatBonusInt((byte) (3 * f)); break;
			case 134: handleSkillEffect(L1SkillId.잠재력전설피, f); break;
			case 135: handleSkillEffect(L1SkillId.잠재력전설마나, f); break;
			case 136: handleSkillEffect(L1SkillId.잠재력소울, f); break;
			case 137: handleSkillEffect(L1SkillId.잠재력이뮨무시, f); break;
			case 138: handleSkillEffect(L1SkillId.잠재력저지먼트, f); break;
			case 139: // 진주 잠재력 (특수)
				if (f > 0) { pc.setSkillEffect(L1SkillId.잠재력진주, 3600 * 1000); SC_SystemMessage.send(pc, "\\f4이미 3단 가속효과가 적용중입니다."); }
				else { pc.removeSkillEffect(L1SkillId.잠재력진주); SC_SystemMessage.send(pc, "\\f4마법 인형의 효과가 사라졌습니다."); }
				break;
			}
		}

		// 인형 타입별 고유 옵션 - 단일 Registry
		DollRegistry.applyDirect(pc, getDollType(), f);

		updateClientPackets(pc);
	}

	private void handleSkillEffect(int skillId, int f) {
		if (f > 0) _master.setSkillEffect(skillId, 3600 * 1000);
		else _master.removeSkillEffect(skillId);
	}

	private void updateClientPackets(L1PcInstance pc) {
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);
		SC_OwnCharStatus.send(pc);
		SC_OwnCharStatus2.send(pc);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		SC_REST_EXP_INFO_NOTI.send(pc);
	}
	
	@Override
	public void TimerExpired(int timerId, int generation) {
		if (timerId != TIMER_LIFETIME
				|| generation != TIMER_LIFETIME_GENERATION
				|| _destroyed) {
			return;
		}
		deleteDoll(true);
	}

	public boolean isDeleted() {
		return _cleanupDone.get();
	}

	public void deleteDoll(boolean flag) {
		if (!_cleanupDone.compareAndSet(false, true)) {
			return;
		}
		L1PcInstance pc = (L1PcInstance) _master;
		if (pc != null) {
	        ItemBonusHooks.onDollRemoved(pc, this);		
			handleDollOption(-1); 
			pc.removeDoll(this);
			SC_DOLL_ON_OFF.sendCreate(pc, null, null, 0, false);
			long ss = SC_SkillSound.pack(getId(), 5936);
			SC_SkillSound.sendPacked(pc, ss);
			SC_SkillSound.broadcastFastPacked(pc, ss);
		}
		deleteMe();
	}
	@Override
	public void onPerceive(L1PcInstance perceivedFrom) {
		if (perceivedFrom == null) return;
		if (isDeleted()) return;
		
		if (SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(perceivedFrom, this)) {
			perceivedFrom.addKnownObject(this);
		}
	}

	@Override
	public void onItemUse() {
		if (!isActived()) {
			// 100%의 확률로 헤이 파업 일부 사용
			useItem(CNpcItemUse.USEITEM_HASTE, 100);
		}
	}

	@Override
	public void onGetItem(L1ItemInstance item) {
		if (getNpcTemplate().get_digestitem() > 0) {
			setDigestItem(item);
		}
		if (CNpcItemUse.IsHastePotion(item.getItem().getItemId())) {
			useItem(CNpcItemUse.USEITEM_HASTE, 100);
		}
	}

	private int _dollBless;

	public int getDollBless() {
		return _dollBless;
	}

	public void setDollBless(int i) {
		_dollBless = i;
	}

	public int getDollType() {
		return _dollType;
	}

	public void setDollType(int i) {
		_dollType = i;
	}

	public int getItemObjId() {
		return _itemObjId;
	}

	public void setItemObjId(int i) {
		_itemObjId = i;
	}

	public int getDollLevel() {
		return _dollLevel;
	}

	public void setDollLevel(int i) {
		_dollLevel = i;
	}

	/** 특수 마법인형 인챈트 시스템 **/

	@Override
	public boolean checkCondition() {
		if (_master == null) {
			return true;
		}
		if (_master instanceof L1PcInstance && (_master.getMapId() == 30000 || _master.getMapId() == 30001) ) {
			deleteDoll(false);
			return true;
		}
		return false;
	}

	private int _invGfx;

	public int getInvGfx() {
		return _invGfx;
	}

	public void setInvGfx(int i) {
		_invGfx = i;
	}

	private static int _instanceType = -1;

	@Override
	public int getL1Type() {
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_DOLL : _instanceType;
	}
}