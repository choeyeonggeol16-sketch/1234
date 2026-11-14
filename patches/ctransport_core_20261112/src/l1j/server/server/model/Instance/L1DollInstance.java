package l1j.server.server.model.Instance;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;

import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import java.util.Arrays;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;

import l1j.server.Config;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.ActionCodes;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.item.function.ItemBonusHooks;
import l1j.server.server.model.npc.ai.NpcPotionPolicy;
import l1j.server.server.model.npc.ai.dFollow;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.packets.Protocol.Packet.SC_DollOnOff;
import l1j.server.server.packets.Protocol.Packet.SC_RestExpInfo;
import LIN.SC_WORLD_PUT_OBJECT_NOTI;

import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;


import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
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

	private static Random _random = new Random(System.nanoTime());
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
				teleport(_master.getX(), _master.getY(), getHeading());
				return false;
			}
			else if (distance > 2 && distance < 40) {
				return dFollow.follow(this, _master);
			}
			return false;
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

		// 모든 옵션 로직 실행
		handleDollOption(1);

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
			case 9: pc.getAC().addAc(-1 * f); break;
			case 10: pc.getResistance().addMr(3 * f); break;
			case 11: pc.addMaxHp(30 * f); break;
			case 12: pc.addMaxMp(20 * f); break;
			case 13: pc.getResistance().addPVPweaponTotalDamage(1 * f); break;
			case 14: pc.getResistance().addcalcPcDefense(1 * f); break;
			case 15: pc.getResistance().addFire(10 * f); break;
			case 16: pc.getResistance().addEarth(10 * f); break;
			case 17: pc.getResistance().addWater(10 * f); break;
			case 18: pc.getResistance().addWind(10 * f); break;
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
			case 28: pc.getAC().addAc(-2 * f); break;
			case 29: pc.getResistance().addMr(6 * f); break;
			case 30: pc.addMaxHp(60 * f); break;
			case 31: pc.addMaxMp(40 * f); break;
			case 32: pc.addDg(3 * f); break;
			case 35: pc.addEffectedER(3 * f); break;
			case 36: pc.getResistance().addPVPweaponTotalDamage(2 * f); break;
			case 37: pc.getResistance().addcalcPcDefense(2 * f); break;
			case 38: handleSkillEffect(L1SkillId.잠재력pvp마법감소3, f); break;
			case 39: pc.getResistance().addSpecialResistance(eKind.ABILITY, 3 * f); break;
			case 40: pc.getResistance().addSpecialResistance(eKind.SPIRIT, 3 * f); break;
			case 41: pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 3 * f); break;
			case 42: pc.getResistance().addSpecialResistance(eKind.FEAR, 3 * f); break;
			case 43: pc.getResistance().addFire(20 * f); break;
			case 44: pc.getResistance().addEarth(20 * f); break;
			case 45: pc.getResistance().addWater(20 * f); break;
			case 46: pc.getResistance().addWind(20 * f); break;
			case 47: pc.getAbility().addAddedStr((byte) (1 * f)); break;
			case 48: pc.getAbility().addAddedDex((byte) (1 * f)); break;
			case 49: pc.getAbility().addAddedCon((byte) (1 * f)); break;
			case 50: pc.getAbility().addAddedWis((byte) (1 * f)); break;
			case 51: pc.getAbility().addAddedInt((byte) (1 * f)); break;
			case 52: pc.addWeightReduction(200 * f); break;
			case 53: pc.add_melee_critical_rate(5 * f); break;
			case 54: pc.add_missile_critical_rate(5 * f); break;
			case 55: pc.add_magic_critical_rate(5 * f); break;
			case 56: pc.addDmgup(3 * f); break;
			case 57: pc.addBowDmgup(3 * f); break;
			case 58: pc.addHitup(3 * f); break;
			case 59: pc.addBowHitup(3 * f); break;
			case 60: pc.getAbility().addSp(3 * f); break;
			case 61: pc.addBaseMagicHitUp(3 * f); break;
			case 62:
				pc.addDamageReductionByArmor(3 * f);
				break;
			case 63: pc.getAC().addAc(-3 * f); break;
			case 64: pc.getResistance().addMr(10 * f); break;
			case 65: pc.addMaxHp(150 * f); break;
			case 66: pc.addMaxMp(100 * f); break;
			case 67: pc.addDg(5 * f); break;
			case 70: pc.addEffectedER(5 * f); break;
			case 71: 
				pc.getResistance().addPVPweaponTotalDamage(3 * f);

				break;
			case 72: pc.getResistance().addcalcPcDefense(3 * f); break;
			case 73: handleSkillEffect(L1SkillId.잠재력pvp마법감소2, f); break;
			case 74: pc.getResistance().addSpecialResistance(eKind.ABILITY, 5 * f); break;
			case 75: pc.getResistance().addSpecialResistance(eKind.SPIRIT, 5 * f); break;
			case 76: pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 5 * f); break;
			case 77: pc.getResistance().addSpecialResistance(eKind.FEAR, 5 * f); break;
			case 78: pc.getResistance().addSpecialPierce(eKind.ABILITY, 3 * f); break;
			case 79: pc.getResistance().addSpecialPierce(eKind.SPIRIT, 3 * f); break;
			case 80: pc.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 3 * f); break;
			case 81: pc.getResistance().addSpecialPierce(eKind.FEAR, 3 * f); break;
			case 82: pc.getResistance().addFire(30 * f); break;
			case 83: pc.getResistance().addEarth(30 * f); break;
			case 84: pc.getResistance().addWater(30 * f); break;
			case 85: pc.getResistance().addWind(30 * f); break;
			case 86: pc.getResistance().addAllNaturalResistance(10 * f); break;
			case 87: pc.getAbility().addAddedStr((byte) (2 * f)); break;
			case 88: pc.getAbility().addAddedDex((byte) (2 * f)); break;
			case 89: pc.getAbility().addAddedCon((byte) (2 * f)); break;
			case 90: pc.getAbility().addAddedWis((byte) (2 * f)); break;
			case 91: pc.getAbility().addAddedInt((byte) (2 * f)); break;
			case 92: pc.addWeightReduction(300 * f); break;
			case 93: pc.addDmgup(5 * f); break;
			case 94: pc.addBowDmgup(5 * f); break;
			case 95: pc.addHitup(5 * f); break;
			case 96: pc.addBowHitup(5 * f); break;
			case 97: pc.getAbility().addSp(5 * f); break;
			case 98: pc.addBaseMagicHitUp(5 * f); break;
			case 99: pc.addDamageReductionByArmor(5 * f); break;
			case 100: pc.getAC().addAc(-5 * f); break;
			case 101: pc.getResistance().addMr(15 * f); break;
			case 102: pc.addMaxHp(300 * f); break;
			case 103: pc.addMaxMp(200 * f); break;
			case 104: pc.addDg(10 * f); break;
			case 107: pc.addEffectedER(12 * f); break;
			case 108: pc.getResistance().addPVPweaponTotalDamage(7 * f); break;
			case 109: pc.getResistance().addcalcPcDefense(7 * f); break;
			case 110: handleSkillEffect(L1SkillId.잠재력pvp마법감소1, f); break;
			case 111: handleSkillEffect(L1SkillId.잠재력pvp감소무시, f); break;
			case 112: pc.getResistance().addSpecialResistance(eKind.ABILITY, 8 * f); break;
			case 113: pc.getResistance().addSpecialResistance(eKind.SPIRIT, 8 * f); break;
			case 114: pc.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, 8 * f); break;
			case 115: pc.getResistance().addSpecialResistance(eKind.FEAR, 8 * f); break;
			case 116: pc.getResistance().addSpecialResistance(eKind.ALL, 3 * f); break;
			case 117: pc.getResistance().addSpecialPierce(eKind.ABILITY, 5 * f); break;
			case 118: pc.getResistance().addSpecialPierce(eKind.SPIRIT, 5 * f); break;
			case 119: pc.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, 5 * f); break;
			case 120: pc.getResistance().addSpecialPierce(eKind.FEAR, 5 * f); break;
			case 121: pc.getResistance().addSpecialPierce(eKind.ALL, 3 * f); break;
			case 122: pc.getResistance().addAllNaturalResistance(30 * f); break;
			case 123: pc.getAbility().addAddedStr((byte) (3 * f)); break;
			case 124: pc.getAbility().addAddedDex((byte) (3 * f)); break;
			case 125: pc.getAbility().addAddedCon((byte) (3 * f)); break;
			case 126: pc.getAbility().addAddedWis((byte) (3 * f)); break;
			case 127: pc.getAbility().addAddedInt((byte) (3 * f)); break;
			case 128:
				pc.getAbility().addAddedStr((byte) (1 * f)); pc.getAbility().addAddedDex((byte) (1 * f));
				pc.getAbility().addAddedCon((byte) (1 * f)); pc.getAbility().addAddedWis((byte) (1 * f));
				pc.getAbility().addAddedInt((byte) (1 * f)); break;
			case 129: handleSkillEffect(L1SkillId.잠재력영웅마나, f); break;
			case 130: pc.addWeightReduction(500 * f); break;
			case 131: handleSkillEffect(L1SkillId.잠재력디케이, f); break;
			case 132: handleSkillEffect(L1SkillId.잠재력pvp감소무시1, f); break;
			case 133:
				pc.getAbility().addAddedStr((byte) (3 * f)); pc.getAbility().addAddedDex((byte) (3 * f));
				pc.getAbility().addAddedCon((byte) (3 * f)); pc.getAbility().addAddedWis((byte) (3 * f));
				pc.getAbility().addAddedInt((byte) (3 * f)); break;
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

		// 기존 query/proc/회복 후처리 유지
        ItemBonusHooks.onDollSpawned(pc, this);

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
		SC_RestExpInfo.send(pc);
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
			SC_DollOnOff.sendCreate(pc, null, null, 0, false);
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
		perceivedFrom.addKnownObject(this);
		LIN.SC_WORLD_PUT_OBJECT_NOTI.sendNewInstance(perceivedFrom, this);
	}

	@Override
	public void onItemUse() {
		if (!isActived()) {
			// 100%의 확률로 헤이 파업 일부 사용
			useItem(NpcPotionPolicy.USEITEM_HASTE, 100);
		}
	}

	@Override
	public void onGetItem(L1ItemInstance item) {
		if (getNpcTemplate().get_digestitem() > 0) {
			setDigestItem(item);
		}
		if (Arrays.binarySearch(NpcPotionPolicy.haestPotions, item.getItem().getItemId()) >= 0) {
			useItem(NpcPotionPolicy.USEITEM_HASTE, 100);
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

	public int getDamageByDoll() {// 근접무기 착용시에만 불려간다.
		int damage = 0;
		if (getDollType() == DOLLTYPE_WAREWOLF) { // 늑인
			int chance = _random.nextInt(100) + 1;
			if (chance <= 5) {
				damage = 15;
				if (_master instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) _master;
					SC_SkillSound.send(pc, _master.getId(), 6319);
				}
				SC_SkillSound.broadcast(_master, _master.getId(), 6319);
			}
		}

		if (getDollType() == DOLLTYPE_CRUSTACEA) { // 시안
			int chance = _random.nextInt(100) + 1;
			if (chance <= 10) {
				damage = 15;
				if (_master instanceof L1PcInstance) {
					L1PcInstance pc = (L1PcInstance) _master;
					SC_SkillSound.send(pc, _master.getId(), 6319);
				}
				SC_SkillSound.broadcast(_master, _master.getId(), 6319);
			}
		}
		return damage;
	}


	public int fou_DamageUp() {
		int fou = 0;
		switch (getDollType()) {
		case DOLL_Iris:
		case DOLL_Kouts:
		case DOLLTYPE_축커츠:
		case DOLLTYPE_축아이리스:
			fou = 10;
			break;
		}
		return fou;
	}

	// 픽시 마법인형 [ 브레그, 레데그, 엘레그 ]
	@SuppressWarnings("unused") // 추가 데스나이트 데미지
	public double attackPixieDamage(L1PcInstance pc, L1Character cha) {
		double dmg = 0;
		int type = getDollType();
		int effect = 0;
		switch (type) {
		case DOLLTYPE_흑장로:
		case DOLLTYPE_축흑장로:
			effect = 7004; // 콜라이트닝
			break;
		case DOLLTYPE_PIXIE_BLAG:
			effect = 1809; // 콘 오브 콜드
			break;
		case DOLLTYPE_PIXIE_LESDAG:
			effect = 1583; // 파이어 애로우
			break;
		case DOLLTYPE_PIXIE_ELREGEU:
			effect = 7331; // 회오리
			break;
		case DOLLTYPE_DEATHNIGHT:
			effect = 11660; // 헬파이어
			break;
		case DOLLTYPE_축데몬:
		case DOLLTYPE_축바란카:
		case DOLLTYPE_축타락:
		case DOLLTYPE_축바포:
		case DOLLTYPE_축얼녀:
		case DOLLTYPE_축데스:
			effect = 4179; 
			break;
		case desel:
			effect = 15519; 
			break;
		case hal:
			effect = 13987; 
			break;
		case au:
			effect = 2953; 
			break;
		case behe:
			effect = 12119; 
			break;
		case ruler1:
		case ruler2:
		case ruler3:
		case ruler4:
		case ruler5:
		case ruler6:
		case ruler7:
		case ruler8:
			effect = 13340; // 파이어 밤
			break;
		case Antaras:
		case Papou:
		case Lynd:
		case Valakas:
			effect = 21165; 
			break;
		default:
			break;
		}


		if (type >= DOLLTYPE_PIXIE_BLAG && type <= DOLLTYPE_PIXIE_ELREGEU) {
			int chance = _random.nextInt(100) + 1;
			if (10 >= chance) {
				dmg = 10;
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == DOLLTYPE_흑장로 || type == DOLLTYPE_축흑장로) {
			int chance = _random.nextInt(100) + 1;
			if (10 >= chance) {
				dmg = 20;
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == DOLLTYPE_DEATHNIGHT) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= Config.데스나이트헬파이어) {
				if (cha.hasSkillEffect(L1SkillId.ERASE_MAGIC)) {
					cha.killSkillEffectTimer(L1SkillId.ERASE_MAGIC);
				}
				dmg = Config.DEDMG + chance2 + (TotalInt * 3); // 데미지 : 고정25 +
				// 랜덤 35+ 케릭인트수치
				// *2 = 35인트일경우
				// 80~110데미지
				// SC_UseAttackSkill direct path replaces S_UseAttackSkill(this,
				// cha.getId(), effect, cha.getX(), cha.getY(),
				// ActionCodes.ACTION_SkillAttack, false);

				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false); 
			}
		}
		if (type == hal) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= Config.신화인형hal) {

				dmg = Config.halDMG + chance2 + (TotalInt * 3); // 데미지 : 고정25 +
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == behe) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= Config.신화인형behe) {

				dmg = Config.beheDMG + chance2 + (TotalInt * 3); // 데미지 : 고정25 +

				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == au) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= Config.신화인형au) {

				dmg = Config.auDMG + chance2 + (TotalInt * 3); // 데미지 : 고정25 +

				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == desel) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= Config.신화인형au) {

				dmg = Config.auDMG + chance2 + (TotalInt * 3); // 데미지 : 고정25 +
				// 랜덤 35+ 케릭인트수치
				// *2 = 35인트일경우
				// 80~110데미지
				// SC_UseAttackSkill direct path replaces S_UseAttackSkill(this,
				// cha.getId(), effect, cha.getX(), cha.getY(),
				// ActionCodes.ACTION_SkillAttack, false);

				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == ruler1) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= Config.지배자헌신마법확률) {
				dmg = Config.DEDMG1 + chance2 + (TotalInt * 3); // 데미지 : 고정25 +
				// 랜덤 35+ 케릭인트수치
				// *2 = 35인트일경우
				// 80~110데미지
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == ruler5) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= 20) {
				dmg = 40 + chance2 + (TotalInt * 3); // 데미지 : 고정25 + 랜덤 35+
				// 케릭인트수치 *2 = 35인트일경우
				// 80~110데미지
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}

		if (type == ruler2 || type == ruler6) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= 8) {
				dmg = 12 + chance2 + (TotalInt * 2); // 데미지 : 고정25 + 랜덤 35+
				// 케릭인트수치 *2 = 35인트일경우
				// 80~110데미지
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == ruler3 || type == ruler7) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= 6) {
				dmg = 9 + chance2 + (TotalInt * 2); // 데미지 : 고정25 + 랜덤 35+
				// 케릭인트수치 *2 = 35인트일경우
				// 80~110데미지
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == ruler4 || type == ruler8) {
			int chance = _random.nextInt(100) + 1;
			int chance2 = _random.nextInt(35) + 1;
			int chance3 = _random.nextInt(10) + 1;
			int TotalInt = cha.getAbility().getTotalInt();
			if (chance <= 5) {
				dmg = 7 + chance2 + (TotalInt * 2); // 데미지 : 고정25 + 랜덤 35+
				// 케릭인트수치 *2 = 35인트일경우
				// 80~110데미지
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
		if (type == DOLLTYPE_축데몬 || type == DOLLTYPE_축데스  || type == DOLLTYPE_축바란카  || type == DOLLTYPE_축타락 
				|| type == DOLLTYPE_축바포   || type == DOLLTYPE_축얼녀  || type == DOLLTYPE_축커츠) {
			int chancea = _random.nextInt(100) + 1;
			if (chancea <= 5) {

				pc.setDollskill(2);
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						3,
						false);
			}
		}
		if (type == Valakas || type == Antaras  || type == Papou  || type == Lynd) {
			int chance = _random.nextInt(100) + 1;
			if (chance <= 5) {

				pc.setDollskill(1);
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						effect,
						cha.getX(),
						cha.getY(),
						3,
						false);
			}
		}
		return dmg;
	}

	// 픽시 마법인형 [ 그레그 ]
	public void getPixieGreg(L1PcInstance pc, L1Character cha) {
		int type = getDollType();
		if (type == DOLLTYPE_PIXIE_GREG) {
			int chance = _random.nextInt(100) + 1;
			if (10 >= chance) {
				SC_UseAttackSkill.sendAndBroadcast(
						pc.getNetConnection(),
						this,
						pc,
						cha.getId(),
						4022,
						cha.getX(),
						cha.getY(),
						ActionCodes.ACTION_SkillAttack,
						false);
			}
		}
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
		return _instanceType == -1 ? _instanceType = super.getL1Type() | MJL1Type.L1TYPE_DOLL : _instanceType;
	}
}