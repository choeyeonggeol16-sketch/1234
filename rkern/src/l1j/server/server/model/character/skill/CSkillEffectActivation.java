package l1j.server.server.model.character.skill;

import l1j.server.server.shared.GameRandom;
import l1j.server.server.datatables.CObjectDB;
import java.util.Iterator;
import java.util.Random;

import l1j.server.Config;
import l1j.server.server.ActionCodes;
import l1j.server.server.model.world.CWorldWork;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.Instance.L1CompanionInstance;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.L1CastleLocation;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.character.effect.paralysis.CurseParalysis;
import l1j.server.server.model.world.effect.L1EffectSpawn;
import l1j.server.server.model.L1Location;
import l1j.server.server.model.L1Magic;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CParty;
import l1j.server.server.model.L1PinkName;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.skill.core.DurationResolver;
import l1j.server.server.model.character.skill.helper.SkillUseHelper;
import l1j.server.server.model.character.skill.helper.WeakExposeHelper;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.item.function.TeleportUseService;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.model.network.core.sc.packet.SC_ChangeName;
import l1j.server.server.model.network.core.sc.packet.SC_CharVisualUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_CurseBlind;
import l1j.server.server.model.network.core.sc.packet.SC_Dexup;
import l1j.server.server.model.network.core.sc.packet.SC_EffectLocation;
import l1j.server.server.model.network.core.sc.packet.SC_HpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.network.core.sc.packet.SC_IvenBuffIcon;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;
import l1j.server.server.model.network.core.sc.packet.SC_MpUpdate;
import l1j.server.server.model.network.core.sc.packet.SC_NpcChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharAttrDef;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Paralysis;
import l1j.server.server.model.network.core.sc.packet.SC_Poison;
import l1j.server.server.model.network.core.sc.packet.SC_SPMR;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SkillBrave;
import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconAura;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconShield;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWindShackle;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_Sound;
import l1j.server.server.model.network.core.sc.packet.SC_Strup;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_TrueTargetNew;
import l1j.server.server.model.network.core.sc.packet.SC_UseAttackSkill;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.npc.ai.NpcEventFactory;
import l1j.server.server.model.character.effect.poison.DamagePoison;
import l1j.server.server.model.character.effect.poison.ParalysisPoison;
import l1j.server.server.packets.Protocol.MainServer.Proto.Proto;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.utils.eKind;
/**
 *   CSkillEffectActivation
 */
public final class CSkillEffectActivation {

    /** Uses the existing recipient controller FIFO for all Prime state changes. */
    static void startPrimeFromOwner(L1PcInstance target, int skillId, int duration,
            int tierId, int casterId, boolean partyMember) {
        if ((skillId != L1SkillId.프라임 && skillId != L1SkillId.프라임1)
                || (tierId != L1SkillId.프라임85 && tierId != L1SkillId.프라임90
                    && tierId != L1SkillId.프라임95) || duration <= 0) return;
        final boolean casterSelf = target.getId() == casterId;
        if (!casterSelf) {
            final L1Object obj = CWorld.getInstance().findObject(casterId);
            if (!(obj instanceof L1PcInstance caster) || caster.getClanid() == 0
                    || target.getClanid() != caster.getClanid()
                    || caster.getMapId() != target.getMapId()
                    || Math.abs(caster.getX() - target.getX()) > 20
                    || Math.abs(caster.getY() - target.getY()) > 20) return;
        }
        if (target.hasSkillEffect(L1SkillId.프라임))
            target.skillEffectRemoveFromOwner(L1SkillId.프라임, true);
        if (target.hasSkillEffect(L1SkillId.프라임1))
            target.skillEffectRemoveFromOwner(L1SkillId.프라임1, true);
        if (target.hasSkillEffect(L1SkillId.프라임85))
            target.skillEffectRemoveFromOwner(L1SkillId.프라임85, false);
        if (target.hasSkillEffect(L1SkillId.프라임90))
            target.skillEffectRemoveFromOwner(L1SkillId.프라임90, false);
        if (target.hasSkillEffect(L1SkillId.프라임95))
            target.skillEffectRemoveFromOwner(L1SkillId.프라임95, false);
        // The captured source is independent of equipment, other active skills
        // and the byte-capped combat stats. One replacement, one owner commit.
        target.applySkillModifierFromOwner(skillId, 0);
        target.skillEffectSetFromOwner(tierId, duration * 1000);
        target.skillEffectSetFromOwner(skillId, duration * 1000);
        SC_InventoryIcon.icoNew(target, L1SkillId.프라임, 5383, duration, true);
        // The caster's original action continues its remaining packet sequence
        // after this inline owner entry. Other recipients send from their owner.
        if (!casterSelf) {
            SC_HpUpdate.send(target, target.getCurrentHp(), target.getMaxHp());
            SC_OwnCharAttrDef.send(target);
            SC_OwnCharStatus.send(target);
            SC_SPMR.send(target);
            SC_SkillSound.send(target, target.getId(), 12301);
            SC_SkillSound.broadcast(target, target.getId(), 12301);
        }
    }


	private CSkillEffectActivation() {}


	public static final int HD_NOT_HANDLED = 0;
	public static final int HD_HANDLED     = 1;
	public static final int HD_CONTINUE    = 2;
	public static final int HD_RETURN      = 3;
	public static final int HD_HANDLED_WITHOUT_TIMER = 4;

	// ===== 중요: fallback/handled 분리 =====
	enum HandlerResult {
		NOT_HANDLED, // 이 핸들러는 조건 불만족 -> 기존 switch로 처리
		HANDLED,     // 이 핸들러가 처리 완료 -> 기존 switch 스킵
		HANDLED_WITHOUT_TIMER,
		CONTINUE,    // 멀티타겟이면 다음 타겟으로
		RETURN       // runSkill 자체 종료
	}

	@FunctionalInterface
	interface SkillHandler extends CSkillEffect {
		HandlerResult handle(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter);

		@Override default HandlerResult instant(L1SkillUse su, L1Character cha, L1Magic magic,
				int undeadType, Iterator<?> iter) {
			return handle(su, cha, magic, undeadType, iter);
		}
	}

	static void registerEffects() {
		final SkillHandler cursePoison = new CSkillEffect.DamagePoison(3000, 5, false, 7000);
		CSkillInfo.registerCast(L1SkillId.ICE_LANCE, CSkillEffectActivation::castFreeze, false);
		CSkillInfo.registerCast(L1SkillId.EARTH_BIND, CSkillEffectActivation::castFreeze, false);
		CSkillInfo.registerCast(L1SkillId.MOB_BERSERKERS, CSkillEffectActivation::castMonsterBerserkers, false);

		CSkillInfo.registerInstant(L1SkillId.TRUE_TARGET, CSkillEffectActivation::onTrueTarget);
		registerStun(L1SkillId.SHOCK_STUN, 81162, CSkillEffectActivation::officialStun, true);
		registerStun(L1SkillId.EMPIRE, 50000066, CSkillEffectActivation::officialStun, true);
		registerStun(L1SkillId.BONE_BREAK, 200020, (cast, target) -> SkillUseHelper.pickMillis(SkillUseHelper.BONE_BREAK_DUR), true);
		CSkillInfo.registerInstant(L1SkillId.CANCELLATION, CSkillEffectActivation::onCancellation);
		CSkillInfo.registerInstant(L1SkillId.RESURRECTION, CSkillEffectActivation::onResurrection);
		CSkillInfo.registerInstant(L1SkillId.GREATER_RESURRECTION, CSkillEffectActivation::onResurrection);


		// multi-hit
		CSkillInfo.registerInstant(L1SkillId.TRIPLE_ARROW, CSkillEffectActivation::onTripleArrow);
		CSkillInfo.registerInstant(L1SkillId.FOU_SLAYER, CSkillEffectActivation::onFouSlayer);

		// buffs (이관)












		CSkillInfo.registerInstant(L1SkillId.TAMING_MONSTER, CSkillEffectActivation::onTamingMonster);
		CSkillInfo.registerInstant(L1SkillId.CREATE_ZOMBIE, CSkillEffectActivation::onCreateZombie);
		CSkillInfo.registerInstant(L1SkillId.WEAK_ELEMENTAL, CSkillEffectActivation::onWeakElemental);
		CSkillInfo.registerInstant(L1SkillId.RETURN_TO_NATURE, CSkillEffectActivation::onReturnToNature);


		CSkillInfo.registerInstant(L1SkillId.LESSER_ELEMENTAL, CSkillEffectActivation::onElementalSummon);
		CSkillInfo.registerInstant(L1SkillId.GREATER_ELEMENTAL, CSkillEffectActivation::onElementalSummon);

		CSkillInfo.registerInstant(L1SkillId.TELEPORT, CSkillEffectActivation::onTeleport);
		CSkillInfo.registerInstant(L1SkillId.MASS_TELEPORT, CSkillEffectActivation::onTeleport);
		CSkillInfo.registerInstant(L1SkillId.TELEPORT_TO_MATHER, CSkillEffectActivation::onTeleportToMather);

		CSkillInfo.registerInstant(L1SkillId.BRING_STONE, CSkillEffectActivation::onBringStone);
		CSkillInfo.registerInstant(L1SkillId.SUMMON_MONSTER, CSkillEffectActivation::onSummonMonster);


		CSkillInfo.registerCast(L1SkillId.GREATER_HASTE, CSkillEffectActivation::castMovementSpeed, false);




		CSkillInfo.registerInstant(L1SkillId.REMOVE_CURSE, CSkillEffectActivation::onRemoveCurse);
		CSkillInfo.registerInstant(L1SkillId.CALL_OF_NATURE, CSkillEffectActivation::onCallOfNature);

		CSkillInfo.registerCast(L1SkillId.SLOW, CSkillEffectActivation::castMovementSpeed, false);
		CSkillInfo.registerCast(L1SkillId.MOB_SLOW_1, CSkillEffectActivation::castMovementSpeed, false);
		CSkillInfo.registerCast(L1SkillId.MOB_SLOW_18, CSkillEffectActivation::castMovementSpeed, false);



		CSkillInfo.registerCast(L1SkillId.HASTE, CSkillEffectActivation::castMovementSpeed, false);


		// 은신몹 싱크 해제 계열
		CSkillInfo.registerInstant(L1SkillId.DETECTION, CSkillEffectActivation::onDetectionLike);
		CSkillInfo.registerInstant(L1SkillId.IZE_BREAK, CSkillEffectActivation::onDetectionLike);
		CSkillInfo.registerInstant(L1SkillId.EYE_OF_DRAGON, CSkillEffectActivation::onDetectionLike);

		// PC면 마법데미지 / NPC면 sink면 드러내기 아니면 0
		CSkillInfo.registerInstant(L1SkillId.COUNTER_DETECTION, CSkillEffectActivation::onCounterDetection);


		CSkillInfo.registerInstant(L1SkillId.MIND_BREAK, CSkillEffectActivation::onMindBreak);





		CSkillInfo.registerInstant(L1SkillId.TURN_UNDEAD, CSkillEffectActivation::onTurnUndead);

		CSkillInfo.registerInstant(L1SkillId.MANA_DRAIN, CSkillEffectActivation::onManaDrain);

		CSkillInfo.registerInstant(L1SkillId.WEAPON_BREAK, CSkillEffectActivation::onWeaponBreak);



		CSkillInfo.registerInstant(L1SkillId.CURSE_POISON, cursePoison);

		CSkillInfo.registerInstant(L1SkillId.CURSE_PARALYZE, CSkillEffect.Paralysis.CURSE);
		CSkillInfo.registerInstant(L1SkillId.CURSE_PARALYZE2, CSkillEffect.Paralysis.CURSE);
		CSkillInfo.registerInstant(L1SkillId.MOB_CURSEPARALYZ1, CSkillEffect.Paralysis.CURSE);
		CSkillInfo.registerInstant(L1SkillId.MOB_CURSEPARALYZ_18, CSkillEffect.Paralysis.CURSE);
		CSkillInfo.registerInstant(L1SkillId.MOB_CURSEPARALYZ_19, CSkillEffect.Paralysis.CURSE);




		CSkillInfo.registerInstant(L1SkillId.MOB_BASILL, CSkillEffect.Paralysis.PETRIFY);
		CSkillInfo.registerInstant(L1SkillId.MOB_COCA, CSkillEffect.Paralysis.PETRIFY);

		registerStun(L1SkillId.MOB_RANGESTUN_18, 81162, CSkillEffectActivation::monsterStun, false);
		registerStun(L1SkillId.MOB_RANGESTUN_19, 81162, CSkillEffectActivation::monsterStun, false);
		registerStun(L1SkillId.MOB_SHOCKSTUN_30, 81162, CSkillEffectActivation::monsterStun, false);

		// 힐계열: WATER_LIFE 타이머 제거 + 아이콘 삭제
		CSkillInfo.registerInstant(L1SkillId.HEAL, CSkillEffectActivation::onHealFamily);
		CSkillInfo.registerInstant(L1SkillId.EXTRA_HEAL, CSkillEffectActivation::onHealFamily);
		CSkillInfo.registerInstant(L1SkillId.GREATER_HEAL, CSkillEffectActivation::onHealFamily);
		CSkillInfo.registerInstant(L1SkillId.FULL_HEAL, CSkillEffectActivation::onHealFamily);
		CSkillInfo.registerInstant(L1SkillId.HEAL_ALL, CSkillEffectActivation::onHealFamily);
		CSkillInfo.registerInstant(L1SkillId.NATURES_TOUCH, CSkillEffectActivation::onHealFamily);
		CSkillInfo.registerInstant(L1SkillId.NATURES_BLESSING, CSkillEffectActivation::onHealFamily);

		// 흡혈계열: heal = dmg/2 (데미지 계산 이후 훅에서 반영해야 함)
		CSkillInfo.registerInstant(L1SkillId.CHILL_TOUCH, CSkillEffectActivation::onVampiricTouchLike);
		CSkillInfo.registerInstant(L1SkillId.VAMPIRIC_TOUCH, CSkillEffectActivation::onVampiricTouchLike);


		CSkillInfo.registerInstant(L1SkillId.CURE_POISON, CSkillEffectActivation::onCurePoison);












		CSkillInfo.registerInstant(10026, CSkillEffectActivation::onDgUiAndKill);
		CSkillInfo.registerInstant(10027, CSkillEffectActivation::onDgUiAndKill);
		CSkillInfo.registerInstant(10028, CSkillEffectActivation::onDgUiAndKill);
		CSkillInfo.registerInstant(10029, CSkillEffectActivation::onDgUiAndKill);

		CSkillInfo.registerInstant(10057, CSkillEffectActivation::onTeleportFront5);

		CSkillInfo.registerInstant(L1SkillId.BODY_TO_MIND, new CSkillEffect.RestoreMana(2));
		CSkillInfo.registerInstant(L1SkillId.BLOODY_SOUL, new CSkillEffect.RestoreMana(15));
		CSkillInfo.registerInstant(L1SkillId.AREA_OF_SILENCE, CSkillEffectActivation::onAreaOfSilenceSound);
		CSkillInfo.registerInstant(L1SkillId.BOSS_COUNTER_BARRIER, CSkillEffectActivation::onBossCounterBarrierEffect);

		registerStun(L1SkillId.OMAN_STUN, 81162, CSkillEffectActivation::omanStun, false);
		registerStun(L1SkillId.Maeno_STUN, 81162, CSkillEffectActivation::omanStun, false);

		// 몹버서커

		// =====_BUFF =====





		// 마안

		// 드래곤블러드(버프)


		// ===== N_BUFF =====


		// 기르타스
		CSkillInfo.registerInstant(L1SkillId.TROGIR_MILPITAS1, cursePoison);
		CSkillInfo.registerInstant(L1SkillId.TROGIR_MILPITAS2, CSkillEffectActivation::castMilpitasSummon);
		registerBossDamage(L1SkillId.TROGIR_MILPITAS3, 11002, false);
		registerBossDamage(L1SkillId.TROGIR_MILPITAS4, 11003, false);
		registerBossDamage(L1SkillId.TROGIR_MILPITAS5, 11004, false);
		registerBossDamage(L1SkillId.TROGIR_MILPITAS6, 11008, false);

		// 원본 공통 보조 / 이벤트 버프
		CSkillInfo.registerInstant(L1SkillId.CONCENTRATION, CSkillEffectActivation::onConcentration);

		// 원본에서 본문이 비어 있던 case도 handled로 보존
		CSkillInfo.registerInstant(L1SkillId.잠재력소울, CSkillEffectActivation::ignoreNonPlayerBuff);
		CSkillInfo.registerInstant(L1SkillId.할파스의신의PVP, CSkillEffectActivation::ignoreNonPlayerBuff);
		CSkillInfo.registerInstant(L1SkillId.할파스의신의, CSkillEffectActivation::ignoreNonPlayerBuff);

		// 피닉스 / 드레이크
		registerBossDamage(L1SkillId.Phoenix_Skill1, 16258, true);
		registerBossDamage(L1SkillId.Phoenix_Skill2, 16299, true);
		registerBossDamage(L1SkillId.DRAKE_Skill1, 16333, true);
		registerBossDamage(L1SkillId.DRAKE_Skill2, 16335, true);
		CSkillInfo.registerInstant(L1SkillId.DRAKE_MASSTELEPORT, CSkillEffectActivation::onDrakeMassTeleport);
		CSkillInfo.registerInstant(L1SkillId.EFRETE_SUMMON_MONSTER, CSkillEffectActivation::onEfreetSummon);
		CSkillInfo.registerInstant(L1SkillId.PHOENIX_SUMMON_MONSTER, CSkillEffectActivation::onPhoenixSummon);
		CSkillInfo.registerInstant(L1SkillId.PHOENIX_CANCELLATION, CSkillEffectActivation::onDragonCancellation);

		// 샌드웜
		registerSandWormCue(L1SkillId.Sand_worms, 10145);
		registerSandWormCue(L1SkillId.Sand_worms1, 10195);
		registerSandWormCue(L1SkillId.Sand_worms2, 10194);
		registerSandWormCue(L1SkillId.Sand_worms3, 10191);

		// 오만
		CSkillInfo.registerInstant(L1SkillId.OMAN_CANCELLATION, CSkillEffectActivation::onOmanCancellation);

		// 안타라스
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_1, 900011, true, CSkillEffectActivation::cancelBossTargetEffects, CSkillEffectActivation::antarasParalysisRoll);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_2, 900011, true, CSkillEffectActivation::antarasDamagePoison);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_3, 900011, true);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_4, 900011, true);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_5, 900011, true, CSkillEffectActivation::antarasDamagePoison);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_6, 900011, false, CSkillEffectActivation::antarasStun);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_7, 900011, false, CSkillEffectActivation::antarasStun);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_8, 900011, false, CSkillEffectActivation::antarasStun);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_9, 900011, true, CSkillEffectActivation::antarasDamagePoison, CSkillEffectActivation::antarasWeaponBreak);
		registerNpcBossEffects(L1SkillId.ANTA_MESSAGE_10, 900011, true, CSkillEffectActivation::antarasParalysisRoll, CSkillEffectActivation::antarasWeaponBreak);
		registerNpcBossEffects(L1SkillId.ANTA_CANCELLATION, 900011, true, CSkillEffectActivation::cancelBossTargetEffects);
		registerNpcBossEffects(L1SkillId.ANTA_WEAPON_BREAK, 900011, true, CSkillEffectActivation::antarasWeaponBreak);
		registerNpcBossEffects(L1SkillId.ANTA_SHOCKSTUN, 900011, false, CSkillEffectActivation::antarasStun);

		// 파푸리온
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE1, 900038, true, CSkillEffectActivation::summonPapMinions);
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE3, 900038, true, timedBossDebuff(7781, L1SkillId.PAP_DEATH_PORTION));
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE5, 900038, true, timedBossDebuff(7782, L1SkillId.PAP_REDUCE_HELL));
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE6, 900038, true, timedBossDebuff(7780, L1SkillId.PAP_DEATH_HELL));
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE7, 900038, true, CSkillEffectActivation::cancelBossTargetEffects);
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE8, 900038, true, CSkillEffectActivation::papWeaponBreak);
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE9, 900038, true);
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE11, 900038, true, CSkillEffectActivation::cancelBossTargetEffects, timedBossDebuff(7780, L1SkillId.PAP_DEATH_HELL));
		registerNpcBossEffects(L1SkillId.PAP_PREDICATE12, 900038, true, CSkillEffectActivation::cancelBossTargetEffects, timedBossDebuff(7780, L1SkillId.PAP_DEATH_HELL));

		// 린드비오르
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_SUMMON_MONSTER_CLOUD, CSkillEffectActivation::onRindviorSummonCloud);
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_PREDICATE, CSkillEffectActivation::onRindviorPredicate);
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_SUMMON_MONSTER, CSkillEffectActivation::onRindviorSummon);
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_BOW, CSkillEffectActivation::onRindviorBow);
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_PREDICATE_CANCELLATION, CSkillEffectActivation::onRindviorPredicateCancellation);
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_CANCELLATION, CSkillEffectActivation::onDragonCancellation);
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_WEAPON, CSkillEffectActivation::onRindviorWeapon);
		CSkillInfo.registerInstant(L1SkillId.RINDVIOR_WEAPON_2, CSkillEffectActivation::onRindviorWeapon);

		// 흑장로
		CSkillInfo.registerInstant(L1SkillId.BLACKELDER_DEATH_HELL, CSkillEffectActivation::onBlackElderDeathHell);
		CSkillInfo.registerInstant(L1SkillId.BLACKELDER_DEATH_POTION, timedBossDebuff(7781, L1SkillId.PAP_DEATH_PORTION));

		// 에르자베 / 제니스
		CSkillInfo.registerCast(L1SkillId.DESERT_SKILL1, CSkillEffectActivation::castDesertParalysis, false);
		CSkillInfo.registerCast(L1SkillId.DESERT_SKILL2, CSkillEffectActivation::castDesertParalysis, false);
		CSkillInfo.registerInstant(L1SkillId.DESERT_SKILL3, CSkillEffectActivation::castDesertManaDrain);
		CSkillInfo.registerInstant(L1SkillId.DESERT_SKILL4, CSkillEffectActivation::castDesertPoison);
		CSkillInfo.registerInstant(L1SkillId.ZENITH_Poison, CSkillEffectActivation::castDesertPoison);
		CSkillInfo.registerCast(L1SkillId.DESERT_SKILL5, CSkillEffectActivation::castDesertDebuffs, false);
		CSkillInfo.registerInstant(L1SkillId.DESERT_SKILL6, CSkillEffectActivation::castDesertBlindness);
		CSkillInfo.registerInstant(L1SkillId.DESERT_SKILL7, CSkillEffectActivation::castDesertSleep);
		CSkillInfo.registerInstant(L1SkillId.DESERT_SKILL8, CSkillEffectActivation::castDesertSound);
		CSkillInfo.registerInstant(L1SkillId.DESERT_SKILL9, CSkillEffectActivation::castDesertSummon);
		CSkillInfo.registerInstant(L1SkillId.DESERT_SKILL10, CSkillEffectActivation::castDesertSummon);
		

	}

	static HandlerResult ignoreNonPlayerBuff(
			L1SkillUse cast, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		return HandlerResult.HANDLED;
	}

	public static int dispatch(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final int id = su.hdSkillId();
		final CSkillInfo effect = CSkillInfo.get(id);
		if (effect == null) return HD_NOT_HANDLED;

		final HandlerResult r = effect.activateSkillEffects(su, cha, magic, undeadType, iter);
		switch (r) {
		case RETURN:
			return HD_RETURN;
		case CONTINUE:
			return HD_CONTINUE;
		case HANDLED:
			return HD_HANDLED;
		case HANDLED_WITHOUT_TIMER:
			return HD_HANDLED_WITHOUT_TIMER;
		case NOT_HANDLED:
		default:
			return HD_NOT_HANDLED;
		}
	}

	/** Polymorph's player mutation is owned by the target, not a remote caster. */
	static void startShapeChangeFromOwner(L1PcInstance pc, int skillId, int duration,
			int polyId, int casterId, boolean partyMember) {
		if (pc.isDead()) return;
		L1PcInstance caster = null;
		if (casterId != 0 && casterId != pc.getId()) {
			final L1Object source = CWorld.getInstance().findObject(casterId);
			if (!(source instanceof L1PcInstance other)) return;
			caster = other;
			if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
				sendSkillSoundBroadcast(pc, 15846);
				SC_ServerMessage.send(caster, 280);
				return;
			}
		}
		if (pc.getInventory().IsEquippedByClassID(20281)) {
			SC_MessageYN.send(pc, 180);
			return;
		}
		L1PolyMorph.doPoly(pc, polyId, duration, L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
		if (caster != null) SC_ServerMessage.send(pc, 241, caster.getName());
	}

	// =========================
	//  handler implementations
	// =========================
	static HandlerResult onShapeChange(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		boolean isSameClan = false;
		final L1PcInstance caster = su.hdPlayer();

		if (cha instanceof L1PcInstance && caster != null) {
			final L1PcInstance pc = (L1PcInstance) cha;
			if (pc.getClanid() != 0 && caster.getClanid() == pc.getClanid()) {
				isSameClan = true;
			}
			// 폴리링 마스터 보호 (타인 시전 차단)
			if (pc.getId() != caster.getId() && pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
				sendSkillSoundBroadcast(pc, 15846);
				SC_ServerMessage.send(caster, 280);
				if (iter != null) iter.remove(); // 대상만 제거
				return HandlerResult.CONTINUE;
			}
		}

		if (cha instanceof L1MonsterInstance) {
			final L1MonsterInstance mon = (L1MonsterInstance) cha;
			// 멀티타겟에서 전체 취소되는 문제 방지: 대상만 스킵
			if (!mon.getNpcTemplate().isShapeChange()) {
				return HandlerResult.CONTINUE;
			}
		}

		// 타인/타클랜이면 확률 적용
		if (caster != null && caster.getId() != cha.getId() && !isSameClan) {
			final int probability = 80;
			final int rnd = su.hdRandom().nextInt(100) + 1;
			if (rnd > probability) {
				return HandlerResult.CONTINUE;
			}
		}

		final int polyId = SkillUseHelper.pickRandomPolyId();

		if (cha instanceof L1PcInstance pc) {
			final L1Skills skillTemp = SkillsTable.getInstance().getTemplate(L1SkillId.SHAPE_CHANGE);
			return submitContextBuff(pc, L1SkillId.SHAPE_CHANGE, skillTemp.getBuffDuration(),
					polyId, caster != null ? caster.getId() : 0, null)
					? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
		} else if (cha instanceof L1MonsterInstance) {
			final L1MonsterInstance mon = (L1MonsterInstance) cha;
			final L1Skills skillTemp = SkillsTable.getInstance().getTemplate(L1SkillId.SHAPE_CHANGE);
			L1PolyMorph.doPoly(mon, polyId, skillTemp.getBuffDuration(), L1PolyMorph.MORPH_BY_ITEMMAGIC, false);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTrueTarget(
	        L1SkillUse su,
	        L1Character cha,
	        L1Magic magic,
	        int undeadType,
	        Iterator<?> iter) {

	    final L1Character user = su.hdUser();
	    final L1Character target =
	            (cha != null ? cha : su.hdTarget());

	    if (!(user instanceof L1PcInstance)) {
	        return HandlerResult.NOT_HANDLED;
	    }

	    if (target == null) {
	        return HandlerResult.NOT_HANDLED;
	    }

	    final L1PcInstance pri =
	            (L1PcInstance) user;

	    final int targetId =
	            target.getId();

	    // =====================================================
	    // 시전자 본인 표시
	    // =====================================================
	    try(var targetShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_TrueTargetNew.nativeWriter(targetId,true),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(targetShared,pri);

	    // =====================================================
	    // 공격 계산용 트루타겟 단계
	    // =====================================================
	    if (target instanceof L1PcInstance) {

	        final L1PcInstance targetPc =
	                (L1PcInstance) target;

	        final int step =
	                pri.getLevel() / 15;

	        if (step > 0) {
	            targetPc.setTrueTarget(step);
	        }
	    }

	    // =====================================================
	    // 같은 혈맹원에게 트루타겟 표시
	    // =====================================================
	    for (L1PcInstance pc
	            : CWorld.getInstance()
	                    .getRecognizePlayer((L1Object) target)) {

	        if (pri.getClanid() == pc.getClanid()) {

	            l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(targetShared,pc);
	        }
	    }
}

	    // =====================================================
	    // 실제 트루타겟 상태 관리
	    // =====================================================
	    TrueTargetManager.apply(
	            pri,
	            target,
	            16 * 1000);
	    return HandlerResult.HANDLED;
	}

	// ===== dup 제거: 이펙트 스폰 + 스턴/마비 적용 공통 =====
	private static void spawnEffectAtTarget(final L1Character target, final int effectId, final int durationMs) {
		if (target == null) return;
		L1EffectSpawn.getInstance().spawnEffect(effectId, durationMs, target.getX(), target.getY(), target.getMapId());
	}

	private static boolean admitNpcControl(L1SkillUse cast, L1NpcInstance npc, int durationMs) {
		final int resolved = DurationResolver.INSTANCE.resolveDurationMs(cast);
		final int duration = resolved > 0 ? resolved : durationMs;
		return duration > 0 && npc.trySetSkillEffect(cast.hdSkillId(), duration);
	}

	private static HandlerResult applyStunLikeEffect(L1SkillUse cast, L1Character target, int durationMs, int effectId) {
		if (target instanceof L1NpcInstance npc && !admitNpcControl(cast, npc, durationMs))
			return HandlerResult.CONTINUE;
		spawnEffectAtTarget(target, effectId, durationMs);
		if (target instanceof L1PcInstance pc) SC_Paralysis.send(pc, SC_Paralysis.TYPE_STUN, true);
		return HandlerResult.HANDLED;
	}

	@FunctionalInterface
	private interface StunDuration {
		int calculate(L1SkillUse cast, L1Character target);
	}

	private static void registerStun(int skillId, int gfx, StunDuration duration, boolean playerSkill) {
		CSkillInfo.registerCast(skillId, (cast, target, magic, undeadType, targets) -> {
			if (target == null) return playerSkill ? HandlerResult.NOT_HANDLED : HandlerResult.HANDLED;
			final int millis = duration.calculate(cast, target);
			if (millis <= 0 && (skillId == L1SkillId.SHOCK_STUN || skillId == L1SkillId.EMPIRE))
				return HandlerResult.NOT_HANDLED;
			cast.hdSetShockStunDuration(millis);
			return applyStunLikeEffect(cast, target, millis, gfx);
		}, false);
	}

	private static int officialStun(L1SkillUse cast, L1Character target) {
		return SkillUseHelper.pickMillis(SkillUseHelper.STUN_OFFICIAL);
	}

	private static int omanStun(L1SkillUse cast, L1Character target) {
		return 2500 + 500 * cast.hdRandom().nextInt(3);
	}

	private static int monsterStun(L1SkillUse cast, L1Character target) {
		final int delta = cast.hdUser().getLevel() - target.getLevel();
		return Math.clamp(2250 + delta * 80 + cast.hdRandom().nextInt(1600) - 800, 1000, 5000);
	}

	private static HandlerResult onCancellation(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		try {
			// =========================
			// NPC: 특수 처리
			// =========================
			if (cha instanceof L1NpcInstance) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				int npcId = npc.getNpcTemplate().get_npcId();

				if (npcId == 71092) {
					if (!npc.equalsCurrentSprite(1314)) {
						npc.setCurrentSprite(1314);
						npc.sendShape(1314);
					}
					return HandlerResult.RETURN;
				}

				if (npcId == 45640) {
					if (npc.equalsCurrentSprite(2755)) {
						npc.setCurrentHp(npc.getMaxHp());
						npc.setCurrentSprite(2332);
						npc.sendShape(2332);
						npc.setName("$2103");
						npc.setNameId("$2103");
						SC_ChangeName.broadcastFast(npc, npc.getId(), "$2103");
					} else {
						npc.setCurrentHp(npc.getMaxHp());
						npc.setCurrentSprite(2755);
						npc.sendShape(2755);
						npc.setName("$2488");
						npc.setNameId("$2488");
						SC_ChangeName.broadcastFast(npc, npc.getId(), "$2488");
					}
				}

				if (npcId == 81209) {
					if (!npc.equalsCurrentSprite(4310)) {
						npc.setCurrentSprite(4310);
						npc.sendShape(4310);
					}
					return HandlerResult.RETURN;
				}
			}

			// =========================
			// NPC: 이동/브레이브/상태 리셋
			// =========================
			if (!(cha instanceof L1PcInstance)) {
				L1NpcInstance npc = (L1NpcInstance) cha;
				npc.setMoveSpeed(0);
				npc.setBraveSpeed(0);
				SC_SkillHaste.broadcastFast(npc, cha.getId(), 0, 0);
				SC_SkillBrave.broadcastFast(npc, cha.getId(), 0, 0);
				npc.setWeaponBreaked(false);
				npc.setParalyzed(false);
			}

			// =========================
			// PC: 디텍션 처리
			// =========================
			if (cha instanceof L1PcInstance) {
				su.hdDetection((L1PcInstance) cha, false);
			}

			// =========================
			// 캔슬  "절대 해제 불가"를 확실히 보호
			// - 실제 걸린 효과만 remove
			// =========================
			final int skillsBegin = su.hdSkillsBegin();
			final int skillsEnd   = su.hdSkillsEnd();

			long ss = SC_SkillSound.pack(cha.getId(), 15846);

			for (int skillNum = skillsBegin; skillNum <= skillsEnd; skillNum++) {
				if (SkillUseHelper.isNotCancelable(skillNum) && !cha.isDead()) continue;
				if (!cha.hasSkillEffect(skillNum)) continue;

				// 폴리 보호
				if (skillNum == L1SkillId.SHAPE_CHANGE) {
					if (cha instanceof L1PcInstance) {
						L1PcInstance pc = (L1PcInstance) cha;

						if (pc.getRankLevel() >= 9 && PolyTable.getInstance().isRankingPoly(pc.getCurrentSpriteId())) {
							continue;
						}
						if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
							SC_SkillSound.sendPacked(pc, ss);
							SC_SkillSound.broadcastFastPacked(pc, ss);
							continue;
						}
					}
				}
				cha.removeSkillEffect(skillNum);
			}

			// =========================
			// 상태(Status) 제거
			// =========================
			final int statusBegin = su.hdStatusBegin();
			final int statusEnd   = su.hdStatusEnd();

			for (int skillNum = statusBegin; skillNum <= statusEnd; skillNum++) {
				// 예외 상태는 건너뜀
				if (skillNum == su.hdStatusChatProhibited()
						|| skillNum == su.hdStatusCurseBarlog()
						|| skillNum == su.hdStatusCurseYahee()) {
					continue;
				}
				if (SkillUseHelper.isNotCancelable(skillNum) && !cha.isDead()) continue;

				if (!cha.hasSkillEffect(skillNum)) continue;
				cha.removeSkillEffect(skillNum);
			}

			// 과일/포이즌/패럴 제거
			try {
				int fruit = su.hdStatusFruit();
				if (fruit > 0 && cha.hasSkillEffect(fruit)) {
					cha.removeSkillEffect(fruit);
				}
			} catch (Throwable t) {
				// ignore
			}

			cha.curePoison();
			cha.cureParalaysis();

			// =========================
			// 요리(Cooking) 제거 
			// =========================
			final int cookBegin = su.hdCookingBegin();
			final int cookEnd   = su.hdCookingEnd();

			for (int skillNum = cookBegin; skillNum <= cookEnd; skillNum++) {
				if (SkillUseHelper.isNotCancelable(skillNum) && !cha.isDead()) continue;

				if (!cha.hasSkillEffect(skillNum)) continue;
				cha.removeSkillEffect(skillNum);
			}

			// =========================
			// 헤이스트 아이템 착용이면 CONTINUE
			// =========================
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				if (pc.getHasteItemEquipped() > 0) {
					return HandlerResult.CONTINUE;
				}
			}

			// 프리즈 제거
			int freeze = su.hdStatusFreeze();
			if (freeze > 0 && cha.hasSkillEffect(freeze)) {
				cha.removeSkillEffect(freeze);
			}

			// 핑크네임
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				L1Character user = su.hdUser();
				L1PinkName.onAction(pc, user);
			}

			return HandlerResult.HANDLED;

		} catch (Exception e) {
			e.printStackTrace();
			return HandlerResult.NOT_HANDLED;
		}
	}

	static HandlerResult castElementalFallDown(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(su.hdUser() instanceof L1PcInstance) || su.hdPlayer() == null) return HandlerResult.NOT_HANDLED;
		final L1PcInstance caster = su.hdPlayer();
		if (!(target instanceof L1PcInstance || target instanceof L1MonsterInstance)) return HandlerResult.NOT_HANDLED;
		final int attribute = caster.getElfAttr();
		final int durationMs = DurationResolver.INSTANCE.resolveDurationMs(su);

		if (attribute == 0) SC_ServerMessage.send(caster, 79);
		final boolean accepted = target instanceof L1PcInstance pc
				? submitContextBuff(pc, L1SkillId.ELEMENTAL_FALL_DOWN, durationMs, attribute, su.hdGetBuffIconDuration(), null)
				: CIOThreadCallback.getInstance().submitNpcElementalFallDown((L1MonsterInstance) target, durationMs, attribute);
		return accepted ? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	static void startElementalFallDown(L1PcInstance pc, int id, int durationMs, int attribute, int iconDuration, boolean partyMember) {
		applyElementalFallDownFromOwner(pc, id, durationMs, attribute);
		SC_InventoryIcon.icoNew(pc, id, 1009, iconDuration, true);
		sendSkillSoundBroadcast(pc, 4399);
	}

	public static void startNpcElementalFallDownFromOwner(L1NpcInstance npc, int durationMs, int attribute) {
		applyElementalFallDownFromOwner(npc, L1SkillId.ELEMENTAL_FALL_DOWN, durationMs, attribute);
		sendSkillSoundBroadcast(npc, 4399);
	}

	private static void applyElementalFallDownFromOwner(L1Character target, int id, int durationMs, int attribute) {
		target.skillEffectRemoveFromOwner(id, true);
		if (durationMs <= 0) return;
		target.skillEffectSetFromOwner(id, durationMs);
		if (attribute != 0 && target.hasSkillEffect(id)) target.applySkillModifierFromOwner(id, attribute);
	}

	private static HandlerResult onResurrection(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		final int skillId = su.hdSkillId();

		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;

			if (caster.getId() != pc.getId()) {

				if (CWorld.getInstance().getVisiblePlayer(pc, 0).size() > 0) {
					for (L1PcInstance visiblePc : CWorld.getInstance().getVisiblePlayer(pc, 0)) {
						if (!visiblePc.isDead()) {
							SC_ServerMessage.send(caster, 592);
							return HandlerResult.RETURN;
						}
					}
				}

				int castle_id = L1CastleLocation.getCastleIdByArea(pc);
				if (castle_id != 0) {
					SC_SystemMessage.send(pc, "사용할 수 없는 지역입니다.");
					return HandlerResult.RETURN;
				}

				if (pc.getCurrentHp() == 0 && pc.isDead()) {
					if (pc.getMap().isUseResurrection()) {
						if (skillId == L1SkillId.RESURRECTION) {
							pc.setGres(false);
						} else if (skillId == L1SkillId.GREATER_RESURRECTION) {
							pc.setGres(true);
						}
						pc.setTempID(caster.getId());
						SC_MessageYN.send(pc, 322, "");
					}
				}
			}
			return HandlerResult.HANDLED;
		}

		if (cha instanceof L1NpcInstance) {
			if (!(cha instanceof L1TowerInstance)) {
				final L1NpcInstance npc = (L1NpcInstance) cha;
				final int npcId = npc.getNpcId();

				if (npcId != 0) {
					caster.sendPackets("부활 할 수 없습니다.");
					return HandlerResult.RETURN;
				}

				if (npc.getNpcTemplate().isCantResurrect()
						&& !(npc instanceof L1CompanionInstance)) {
					return HandlerResult.RETURN;
				}

				if ((npc instanceof L1CompanionInstance )
						&& CWorld.getInstance().getVisiblePlayer(npc, 0).size() > 0) {

					for (L1PcInstance visiblePc : CWorld.getInstance().getVisiblePlayer(npc, 0)) {
						if (!visiblePc.isDead()) {
							SC_ServerMessage.send(caster, 592);
							return HandlerResult.RETURN;
						}
					}
				}

				if (npc.getCurrentHp() == 0 && npc.isDead()) {
					npc.resurrect(npc.getMaxHp() / 4);
					npc.setResurrect(true);
				}
				return HandlerResult.HANDLED;
			}
		}

		return HandlerResult.NOT_HANDLED;
	}

	// =====================================================
	// Multi-hit hook (스킬별 before/after)
	// =====================================================
	@FunctionalInterface
	private interface MultiHitHook {
		void onHit(L1PcInstance a, L1Character t, int hitIndex, int hits, boolean before);
		MultiHitHook NONE = (a, t, i, h, before) -> {};
	}

	// =====================================================
	// Triple Arrow
	// =====================================================
	private static HandlerResult onTripleArrow(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final L1PcInstance player = su.hdPlayer();
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (player == null || target == null) return HandlerResult.NOT_HANDLED;

		final int wt = weaponType1(player);
		if (wt != 20) return HandlerResult.NOT_HANDLED;
		if (player.getCurrentSpriteId() == 3784) return HandlerResult.NOT_HANDLED;

		final int hits = 3;
		scheduleMultiHit(
				player,
				target,
				hits,
				80,
				Integer.MAX_VALUE,
				(a, t, idx, hs, before) -> {
					if (!before && idx == hs - 1) {
						sendSkillSoundOnce(a, 15103);
					}
				}
				);

		return HandlerResult.HANDLED;
	}

	// =====================================================
	// Fou Slayer
	// =====================================================
	private static HandlerResult onFouSlayer(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance player = su.hdPlayer();
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (player == null || target == null) return HandlerResult.NOT_HANDLED;

		final int wt = weaponType1(player);
		if (!isOneOf(wt, 4, 11, 24, 50, 83)) return HandlerResult.NOT_HANDLED;

		final int hits = 3;

		// token 생성
		final int token = player.newMultiHitToken();

		// 현재 스택(충전) 스냅샷
		final int charges = fouPeekCharges(player); // 0~4
		player.fouBegin(token, charges, hits);

		WeakExposeHelper.consumeAllNowOnFouCast(player);

		int effect = 6509;
		if (player.isPassive(PassiveID.FOU_SLAYER_BRAVE.toInt())) {
			effect = 17231;
		}
		sendSkillSoundBroadcast(target, effect);

		scheduleMultiHitWithToken(player, target, hits, 80, Integer.MAX_VALUE, token, (a, t, idx, hs, before) -> {
			if (!before) {
				if (idx == hs - 1) {
					sendSkillSoundOnce(a, 7020);
				}
			}
		});

		return HandlerResult.HANDLED;
	}

	// =====================================================
	// stage(1~4) -> charges(1~4)
	// =====================================================
	private static int fouPeekCharges(L1PcInstance pc) {
		if (pc == null) return 0;

		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD4)) return 4;
		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD3)) return 3;
		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD2)) return 2;
		if (pc.hasSkillEffect(L1SkillId.CHAINSWORD1)) return 1;
		return 0;
	}


	static void startAdvanceSpirit(L1PcInstance pc, int skillId, int duration) {
		applyAdvanceSpirit(pc, skillId, duration, false);
	}

	public static void startSupportAdvanceFromOwner(L1PcInstance pc, int duration) {
		applyAdvanceSpirit(pc, L1SkillId.ADVANCE_SPIRIT, duration, true);
	}

	private static void applyAdvanceSpirit(L1PcInstance pc, int skillId, int duration, boolean support) {
		resetEffect(pc, skillId);
		if (support) applySkillEffectSec(pc, skillId, duration);
		final int hp = pc.getBaseMaxHp() / 5;
		final int mp = pc.getBaseMaxMp() / 5;
		pc.applySkillModifierFromOwner(skillId, ((long) mp << 32) | Integer.toUnsignedLong(hp));

		if (!support) refreshPartyStatusIfNeeded(pc);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		if (support) refreshPartyStatusIfNeeded(pc);
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());

		if (!support) {
			applySkillEffectSec(pc, skillId, duration);
			sendRestatBuffNoti(pc, skillId, duration, 1607, 0, 982, 982, 2180, true);
		}
	}

	static void startScaledHp(L1PcInstance pc, int skillId, int duration) {
		final var effect = skillId == L1SkillId.PRIDE ? CSkillEffect.MaxHp.PRIDE : CSkillEffect.MaxHp.GIGANTIC;
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, effect.capture(pc));
		refreshPartyStatusIfNeeded(pc);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, duration, effect.icon, 0,
				effect.startMessage, effect.startMessage, effect.stopMessage, true);
	}

	static void startGuardBreak(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, 10);
		sendSkillSoundOnce(pc, 9801);
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, duration, 3187, 10, 5109, 5111, 5110, true);
		SC_OwnCharAttrDef.send(pc);
	}

	static HandlerResult castGuardBreakOnNpc(L1SkillUse su, L1Character target, L1Magic magic,
			int undeadType, Iterator<?> targets) {
		if (target instanceof L1MonsterInstance || target instanceof L1SummonInstance
				 || target instanceof L1CompanionInstance) {
			sendSkillSoundBroadcast(target, 9801);
			return HandlerResult.HANDLED;
		}
		return HandlerResult.NOT_HANDLED;
	}


	// =========================
	// N_BUFF handlers
	// =========================

	static void startInventoryResistanceBuff(L1PcInstance pc, int skillId, int duration) {
		final long bonus = switch (skillId) {
			case L1SkillId.N_BUFF_PVP_DMG -> (1L << 32) | 1;
			case L1SkillId.N_BUFF_STUN -> 2L << ((eKind.ABILITY.value() - 1) << 3);
			case L1SkillId.N_BUFF_HOLD -> 2L << ((eKind.SPIRIT.value() - 1) << 3);
			default -> throw new IllegalArgumentException("Unknown inventory resistance buff " + skillId);
		};
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, bonus);
		applySkillEffectSec(pc, skillId, duration);
		sendIvenBuffIcon(pc, skillId, duration);
	}

	static void startInventoryHealthBuff(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, (50L << 32) | 50);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		applySkillEffectSec(pc, skillId, duration);
		sendIvenBuffIcon(pc, skillId, duration);
	}

	static void startInventoryDamageBuff(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, (1L << 32) | 1);
		applySkillEffectSec(pc, skillId, duration);
		sendIvenBuffIcon(pc, skillId, duration);
	}

	static void startInventoryArmorBuff(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, 1);
		applySkillEffectSec(pc, skillId, duration);
		sendIvenBuffIcon(pc, skillId, duration);
	}

	static void startWeaponBuff(L1PcInstance pc, int skillId, int iconDuration) {
		final int duration = Math.max(1, iconDuration);
		// Holy Weapon clears itself before checking the higher-priority effects.
		if (skillId == L1SkillId.HOLY_WEAPON) resetEffect(pc, skillId);
		if (skillId != L1SkillId.SHADOW_FANG && pc.hasSkillEffect(L1SkillId.SHADOW_FANG)) return;
		if ((skillId == L1SkillId.HOLY_WEAPON || skillId == L1SkillId.ENCHANT_WEAPON)
				&& pc.hasSkillEffect(L1SkillId.BLESS_WEAPON)) return;
		if (skillId == L1SkillId.HOLY_WEAPON && pc.hasSkillEffect(L1SkillId.ENCHANT_WEAPON)) return;

		switch (skillId) {
		case L1SkillId.BLESS_WEAPON -> {
			resetEffect(pc, skillId);
			resetEffect(pc, L1SkillId.HOLY_WEAPON);
			resetEffect(pc, L1SkillId.ENCHANT_WEAPON);
		}
		case L1SkillId.ENCHANT_WEAPON -> {
			resetEffect(pc, skillId);
			resetEffect(pc, L1SkillId.HOLY_WEAPON);
		}
		case L1SkillId.SHADOW_FANG -> {
			resetEffect(pc, skillId);
			resetEffect(pc, L1SkillId.BLESS_WEAPON);
			resetEffect(pc, L1SkillId.HOLY_WEAPON);
			resetEffect(pc, L1SkillId.ENCHANT_WEAPON);
		}
		default -> { }
		}

		final boolean timerFirst = skillId == L1SkillId.BLESS_WEAPON || skillId == L1SkillId.ENCHANT_WEAPON;
		if (timerFirst) applySkillEffectSec(pc, skillId, duration);
		pc.applySkillModifierFromOwner(skillId, 0);
		switch (skillId) {
		case L1SkillId.BLESS_WEAPON -> sendRestatBuffNoti(pc, skillId, duration, 728, 0, 857, 857, 2167, true);
		case L1SkillId.HOLY_WEAPON -> sendRestatBuffNoti(pc, skillId, duration, 777, 0, 685, 0, 718, true);
		case L1SkillId.ENCHANT_WEAPON -> sendRestatBuffNoti(pc, skillId, duration, 5265, 0, 3064, 0, 2239, true);
		case L1SkillId.SHADOW_FANG -> sendRestatBuffNoti(pc, skillId, duration, 1128, 0, 1339, 0, 2234, true);
		default -> throw new IllegalArgumentException("Unknown weapon buff " + skillId);
		}
		if (!timerFirst) applySkillEffectSec(pc, skillId, duration);
	}

	static void startSpellPowerBuff(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		final boolean inventory = skillId == L1SkillId.N_BUFF_SP;
		pc.applySkillModifierFromOwner(skillId, inventory ? 1 : 4);
		SC_SPMR.send(pc);
		applySkillEffectSec(pc, skillId, duration);
		if (inventory) sendIvenBuffIcon(pc, skillId, duration);
		else sendRestatBuffNoti(pc, skillId, duration, 3115, 10, 1343, 1369, 1369, true);
	}

	static void startInventoryStatBuff(L1PcInstance pc, int skillId, int duration) {
		final int bonus = switch (skillId) {
			case L1SkillId.N_BUFF_STR -> 1;
			case L1SkillId.N_BUFF_DEX -> 1 << 8;
			case L1SkillId.N_BUFF_INT -> 1 << 16;
			case L1SkillId.N_BUFF_WIS -> 1 << 24;
			case L1SkillId.N_BUFF_WATER -> 5;
			case L1SkillId.N_BUFF_WIND -> 5 << 8;
			case L1SkillId.N_BUFF_EARTH -> 5 << 16;
			case L1SkillId.N_BUFF_FIRE -> 5 << 24;
			case L1SkillId.N_BUFF_ALL_RESIST -> 0x05050505;
			default -> throw new IllegalArgumentException("Unknown inventory buff " + skillId);
		};
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, bonus);
		if (skillId >= L1SkillId.N_BUFF_WATER && skillId <= L1SkillId.N_BUFF_ALL_RESIST)
			SC_OwnCharAttrDef.send(pc);
		applySkillEffectSec(pc, skillId, duration);
		sendIvenBuffIcon(pc, skillId, duration);
	}

	static void startScalesEvasion(L1PcInstance pc, int skillId, int duration) {
		resetScalesEffectIfPresent(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, 10);
		final boolean water = skillId == L1SkillId.SCALES_WATER_DRAGON;
		if (water) SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		applySkillEffectSec(pc, skillId, duration);
		if (water) {
			sendRestatBuffNoti(pc, skillId, duration, 3184, 10, 2271, 0, 2424, true);
		} else {
			sendRestatBuffNoti(pc, skillId, duration, 8887, 10, 5270, 5270, 5271, true);
		}
	}

	static void startScalesEarthDragon(L1PcInstance pc, int skillId, int duration) {
		resetScalesEffectIfPresent(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, (5L << 32) | Integer.toUnsignedLong(-3));
		SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, duration, 3182, 10, 2267, 0, 2420, true);
	}

	static void startScalesFireDragon(L1PcInstance pc, int skillId, int duration) {
		resetScalesEffectIfPresent(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, 5);
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, duration, 3078, 10, 2274, 0, 2427, true);
	}

	static void startIllusionDiamondGolem(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, -10);
		SC_OwnCharAttrDef.send(pc);
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, duration, 3113, 10, 1373, 1373, 2214, true);
	}

	static void startCasterTimer(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		if (skillId == L1SkillId.LUCIFER) resetEffect(pc, L1SkillId.IMMUNE_TO_HARM);
		applySkillEffectSec(pc, skillId, duration);
		if (skillId == L1SkillId.CYCLONE) {
			sendRestatBuffNoti(pc, skillId, duration, 9190, 9192, 10, 5446, 5446, 5450, true);
		} else {
			sendRestatBuffNoti(pc, skillId, duration, 4503, 10, 5268, 5268, 5269, true);
		}
	}


	private static final int PARTY_BUFF_RANGE = 18;

	private static int skillBuffDurationSecFromTemplate(int skillId, int fallbackSec) {
		final L1Skills template = SkillsTable.getInstance().getTemplate(skillId);
		return template != null && template.getBuffDuration() > 0 ? template.getBuffDuration() : fallbackSec;
	}

	private static boolean submitContextBuff(L1PcInstance pc, int id, int duration, int option, int gfx, CParty party) {
		final var controller = pc.getCreatureController();
		return controller != null && controller.addExecutableCastBuff(pc, id, duration, option, gfx, party);
	}

	private static boolean submitPartyBuff(L1PcInstance caster, int id, int duration, int option, int gfx) {
		if (!submitContextBuff(caster, id, duration, option, gfx, null)) return false;
		final CParty party = caster.getParty();
		if (party == null) return true;
		for (L1PcInstance member : party.getMembers()) {
			if (member != null && member != caster && member.getMapId() == caster.getMapId()
					&& caster.getLocation().getTileLineDistance(member.getLocation()) <= PARTY_BUFF_RANGE) {
				submitContextBuff(member, id, duration, option, gfx, party);
			}
		}
		return true;
	}

	static HandlerResult castCubeBuff(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;
		final int skillId = su.hdSkillId();
		final int iconDuration = durationSecForBuff(su, skillId);
		final int duration = skillBuffDurationSecFromTemplate(skillId, iconDuration);
		return submitPartyBuff(caster, skillId, duration, iconDuration, su.hdSkillCastGfx())
				? HandlerResult.HANDLED : HandlerResult.CONTINUE;
	}

	static void startCubeBuff(L1PcInstance pc, int skillId, int duration, int iconDuration, int gfx,
			boolean partyMember, int icon, int tooltip) {
		final boolean blocked = (skillId == L1SkillId.CUBE_RICH && pc.hasSkillEffect(L1SkillId.IllUSION_LICH))
				|| (skillId == L1SkillId.CUBE_GOLEM && pc.hasSkillEffect(L1SkillId.IllUSION_DIAMONDGOLEM));
		if (!blocked) {
			resetEffect(pc, skillId);
			if (skillId == L1SkillId.CUBE_OGRE) resetEffect(pc, L1SkillId.IllUSION_OGRE);
			if (skillId == L1SkillId.CUBE_AVATAR) {
				if (partyMember) resetEffect(pc, L1SkillId.IllUSION_AVATAR);
			} else {
				pc.applySkillModifierFromOwner(skillId, 0);
				if (skillId == L1SkillId.CUBE_RICH) SC_SPMR.send(pc);
				if (skillId == L1SkillId.CUBE_GOLEM) SC_OwnCharAttrDef.send(pc);
			}
			if (partyMember) pc._CubeEffect = true;
		}
		pc.send_effect(gfx);
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, iconDuration, icon, 10, tooltip, tooltip, 0, true);
	}

	static HandlerResult castDeathHeal(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final L1Character recipient = target != null ? target : su.hdTarget();
		if (!(recipient instanceof L1PcInstance pc)) return HandlerResult.HANDLED;
		final Random random = su.hdRandom();
		final int seconds = random != null ? random.nextInt(10) + 1 : GameRandom.next(1, 10);
		final int originalTimerMs = target == pc ? DurationResolver.INSTANCE.resolveDurationMs(su) : 0;
		return submitContextBuff(pc, su.hdSkillId(), originalTimerMs, seconds, 0, null)
				? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	static void startDeathHeal(L1PcInstance pc, int id, int originalTimerMs, int seconds, int gfx, boolean partyMember) {
		final int effect = L1SkillId.DEATH_HEAL;
		if (pc.hasSkillEffect(effect)) {
			SC_InventoryIcon.icoReset(pc, effect, seconds, false);
			pc.skillEffectRemoveFromOwner(effect, true);
		} else {
			SC_InventoryIcon.icoNew(pc, effect, seconds, false);
		}
		pc.skillEffectSetFromOwner(effect, seconds * 1000);
		SC_SkillSound.send(pc, pc.getId(), 14501);
		SC_SkillSound.broadcastFast(pc, pc.getId(), 14501);
		if (originalTimerMs > 0) pc.skillEffectSetFromOwner(id, originalTimerMs);
	}


	private static HandlerResult onCurePoison(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;

		target.curePoison();
		return HandlerResult.HANDLED;
	}

	static void startSeonghyeolBuff(L1PcInstance pc, int skillId, int duration) {
		if (pc.hasSkillEffect(skillId)) pc.removeSkillEffect(skillId);
		pc.applySkillModifierFromOwner(skillId, 0);
		SC_InventoryIcon.icoNew(pc, skillId, 3944, 3600, true);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);
		applySkillEffectSec(pc, skillId, duration);
	}

	private static HandlerResult onDetectionLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1NpcInstance)) return HandlerResult.HANDLED;

		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) 
			return HandlerResult.HANDLED;

		final L1NpcInstance npc = (L1NpcInstance) cha;
		if (npc.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_SINK) {
			npc.appearOnGround(caster);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onCounterDetection(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		int dmg = 0;

		if (cha instanceof L1PcInstance) {
			if (magic != null) {	
				dmg = caster.getId() != cha.getId() ? magic.calcMagicDamage(L1SkillId.COUNTER_DETECTION) : 0;
			}	
		} else if (cha instanceof L1MonsterInstance) {
			final L1MonsterInstance npc = (L1MonsterInstance) cha;
			if (npc.getHiddenStatus() == L1NpcInstance.HIDDEN_STATUS_SINK) {
				if (caster != null) 
					npc.appearOnGround(caster);
				dmg = 0;
			} else {
				dmg = 0;
			}
		} else {
			dmg = 0;
		}
		su.hdSetDamage(dmg);   
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onMindBreak(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;
		if (target.getZoneType() == 1) {
			return HandlerResult.HANDLED;
		}
		if (target.getCurrentMp() < 5) {
			return HandlerResult.HANDLED;
		}
		target.setCurrentMp(target.getCurrentMp() - 5);

		su.hdSetDamage(15); 
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onHealFamily(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			pc.killSkillEffectTimer(L1SkillId.WATER_LIFE);
			SC_PacketBox.send(pc, SC_PacketBox.DEL_ICON);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onVampiricTouchLike(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final int dmg = su.hdGetDamage();
		if (dmg > 0) {
			su.hdSetHeal(dmg / 2);   
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult castMonsterBerserkers(L1SkillUse cast, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1NpcInstance npc)) return HandlerResult.HANDLED;
		final int duration = DurationResolver.INSTANCE.resolveDurationMs(cast);
		if (duration <= 0) return HandlerResult.HANDLED_WITHOUT_TIMER;
		return CIOThreadCallback.getInstance().submitNpcBerserkers(npc, duration)
				? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	public static void startNpcBerserkersFromOwner(L1NpcInstance npc, int id, int durationMs) {
		npc.setMoveSpeed(1);
		npc.setBraveSpeed(1);
		npc.skillEffectSetFromOwner(id, durationMs);
	}







	private static HandlerResult onElementalSummon(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int attr = pc.getElfAttr();
		if (attr == 0) {
			return HandlerResult.HANDLED;
		}

		if (!canRecallElemental(pc)) {
			SC_ServerMessage.send(pc, 79);
			return HandlerResult.HANDLED;
		}

		int summonCount = countSummons(pc);

		if (summonCount == 1) {
			deleteAllSummons(pc);
			summonCount = 0;
		}

		if (summonCount != 0) {
			SC_ServerMessage.send(pc, 319);
			return HandlerResult.HANDLED;
		}

		final int skillId = su.hdSkillId();
		final boolean mythPoly = isOneOfSprite(pc.getCurrentSpriteId(), MYTH_SPRITES);

		final int[] candidates = pickElementalCandidates(skillId, mythPoly);
		final int summonId = pickSummonIdByAttr(su.hdRandom(), candidates, attr);

		if (summonId == 0) {
			return HandlerResult.HANDLED;
		}

		final L1Npc npcTemp = CObjectDB.GetNPC(summonId);
		if (npcTemp == null) {
			return HandlerResult.HANDLED;
		}

		new L1SummonInstance(npcTemp, pc);
		return HandlerResult.HANDLED;
	}
	
	private static HandlerResult onTeleport(
			L1SkillUse su,
			L1Character cha,
			L1Magic magic,
			int undeadType,
			Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.NOT_HANDLED;
		}

		final L1PcInstance pc = (L1PcInstance) cha;

		final int skillId = su.hdSkillId();
		TeleportUseService.handleSkillTeleport(
				pc,
				skillId,
				su.hdBookmarkX(),
				su.hdBookmarkY(),
				su.hdBookmarkMapId()
				);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTeleportToMather(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		if (pc.getMap().isEscapable() || pc.isGm()) {
			pc.start_teleport(33051, 32337, 4, 5, 169, true, true);
		} else {
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_TELEPORT_UNLOCK, false);
			SC_ServerMessage.send(pc, 647);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBringStone(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		final int itemObjId = su.hdItemObjId();
		if (itemObjId <= 0) return HandlerResult.HANDLED;

		final L1ItemInstance item = pc.getInventory().GetByServerID(itemObjId);
		if (item == null || item.getItem() == null) return HandlerResult.HANDLED;

		final int chance = su.hdRandom().nextInt(100) + 1;

		final int dark  = (int) (10 + (pc.getLevel() * 0.8) + (pc.GetTotalStatWis() - 6) * 1.2);
		final int brave = (int) (dark / 2.1);
		final int wise  = (int) (brave / 2.0);
		final int kayser= (int) (wise / 1.9);

		final int itemId = item.getItem().getItemId();

		if (itemId == 40320) {
			pc.getInventory().DeleteItemAmount(item, 1);
			if (dark >= chance) {
				pc.getInventory().CreateOrAddItem(40321, 1);
				SC_ServerMessage.send(pc, 403, "$2475");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}

		if (itemId == 40321) {
			pc.getInventory().DeleteItemAmount(item, 1);
			if (brave >= chance) {
				pc.getInventory().CreateOrAddItem(40322, 1);
				SC_ServerMessage.send(pc, 403, "$2476");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}

		if (itemId == 40322) {
			pc.getInventory().DeleteItemAmount(item, 1);
			if (wise >= chance) {
				pc.getInventory().CreateOrAddItem(40323, 1);
				SC_ServerMessage.send(pc, 403, "$2477");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}

		if (itemId == 40323) {
			pc.getInventory().DeleteItemAmount(item, 1);
			if (kayser >= chance) {
				pc.getInventory().CreateOrAddItem(40324, 1);
				SC_ServerMessage.send(pc, 403, "$2478");
			} else {
				SC_ServerMessage.send(pc, 280);
			}
			return HandlerResult.HANDLED;
		}
		return HandlerResult.HANDLED;
	}


	static HandlerResult castImmuneToHarm(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance caster = su.hdPlayer();
		final int level = caster != null ? caster.getLevel() : -1;
		final int id = L1SkillId.IMMUNE_TO_HARM;
		return submitContextBuff(pc, id, durationSecForBuff(su, id), level, 0, null)
				? HandlerResult.HANDLED : HandlerResult.CONTINUE;
	}

	static void startImmuneToHarm(L1PcInstance pc, int skillId, int duration, int level, int gfx, boolean partyMember) {
		if (pc.hasSkillEffect(L1SkillId.LUCIFER)) pc.removeSkillEffect(L1SkillId.LUCIFER);
		else resetEffect(pc, skillId);
		sendSkillSoundOnce(pc, 15959);
		if (level >= 0) pc.setLastImmuneLevel(level);
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, duration, 1562, 10, 966, 314, 315, true);
	}

	private static HandlerResult onSummonMonster(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = (L1PcInstance) cha;

		// 원본: 이미 SM 상태면 319
		if (pc.isSM()) {
			SC_ServerMessage.send(pc, 319);
			return HandlerResult.HANDLED;
		}

		final boolean canRecall =
				((pc.getMap().isRecallPets() && !pc.isInWarArea()
						&& pc.getMapId() != 781 && pc.getMapId() != 782)
						|| pc.isGm());

		if (!canRecall) {
			SC_ServerMessage.send(pc, 79);
			return HandlerResult.HANDLED;
		}

		if (pc.getInventory().IsEquippedByClassID(20284)) {
			// 원본: summonMonster(pc, _bookmark_x);
			su.hdSummonMonster(pc, su.hdBookmarkX());
		} else {
			final L1Npc npcTemp = CObjectDB.GetNPC(7320137);
			if (npcTemp != null) {
				final L1SummonInstance summon = new L1SummonInstance(npcTemp, pc);
				summon.setPetcost(0);
			}
		}

		pc.setSM(true);
		return HandlerResult.HANDLED;
	}

	// ===== tame/zombie helpers =====
	private static int calcPetCost(final L1PcInstance pc) {
		if (pc == null) return 0;
		int petcost = 0;
		try {
			for (Object o : pc.getPetList().values()) {
				if (o instanceof L1NpcInstance) {
					petcost += ((L1NpcInstance) o).getPetcost();
				}
			}
		} catch (Throwable t) {
			// ignore
		}
		return petcost;
	}

	private static int calcRemainingCharisma(final L1PcInstance pc) {
		if (pc == null) return 0;

		int cha = 0;
		try {
			cha = pc.GetTotalStatCha();
			if (pc.isElf()) cha += 12;
			else if (pc.isWizard()) cha += 6;
		} catch (Throwable t) {
			// ignore
		}
		cha -= calcPetCost(pc);
		return cha;
	}

	private static HandlerResult tryCreateSummonByCharisma(
			final L1PcInstance caster,
			final L1NpcInstance targetNpc,
			final boolean isZombie
			) {
		if (caster == null || targetNpc == null) return HandlerResult.NOT_HANDLED;

		final int charisma = calcRemainingCharisma(caster);
		if (charisma >= 6) {
			new L1SummonInstance(targetNpc, caster, isZombie);
			return HandlerResult.HANDLED;
		} else {
			SC_ServerMessage.send(caster, 319);
			return HandlerResult.HANDLED;
		}
	}

	// ===== TAMING_MONSTER =====
	private static HandlerResult onTamingMonster(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// 원본: (_calcType==PC_NPC||NPC_NPC) 안에서 monster tamable 일 때만
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		if (!(cha instanceof L1MonsterInstance)) return HandlerResult.NOT_HANDLED;

		final L1MonsterInstance mon = (L1MonsterInstance) cha;
		if (!mon.getNpcTemplate().isTamable()) return HandlerResult.NOT_HANDLED;

		return tryCreateSummonByCharisma(caster, mon, false);
	}

	// ===== CREATE_ZOMBIE =====
	private static HandlerResult onCreateZombie(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		if (!(cha instanceof L1MonsterInstance)) return HandlerResult.NOT_HANDLED;

		// 원본은 tamable 체크 없이 _targetNpc로 소환(좀비)했는데,
		// 여기선 대상이 monster면 그대로 진행
		final L1MonsterInstance mon = (L1MonsterInstance) cha;
		return tryCreateSummonByCharisma(caster, mon, true);
	}

	// ===== WEAK_ELEMENTAL =====
	private static HandlerResult onWeakElemental(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1MonsterInstance)) return HandlerResult.NOT_HANDLED;

		final L1Npc npcTemp = ((L1MonsterInstance) cha).getNpcTemplate();
		if (npcTemp == null) return HandlerResult.HANDLED;

		final int weakAttr = npcTemp.get_weakAttr();

		// 원본 사운드 그대로
		if ((weakAttr & 1) == 1) SC_SkillSound.broadcastFast(cha, cha.getId(), 2169);
		if ((weakAttr & 2) == 2) SC_SkillSound.broadcastFast(cha, cha.getId(), 2167);
		if ((weakAttr & 4) == 4) SC_SkillSound.broadcastFast(cha, cha.getId(), 2166);
		if ((weakAttr & 8) == 8) SC_SkillSound.broadcastFast(cha, cha.getId(), 2168);

		return HandlerResult.HANDLED;
	}

    public static void startComaRewardFromOwner(L1PcInstance pc, int skillId, int seconds) {
        final boolean large = skillId == L1SkillId.COMA_B;
        pc.skillEffectRemoveFromOwner(large ? L1SkillId.COMA_A : L1SkillId.COMA_B, true);
        if (pc.hasSkillEffect(skillId)) {
            pc.skillEffectSetFromOwner(skillId, seconds * 1000);
        } else {
            CSkillInfo.pumpStartFromOwner(pc, skillId, seconds);
            SC_OwnCharStatus.send(pc);
        }
        final int sound = large ? 7383 : 7382;
        SC_SkillSound.send(pc, pc.getId(), sound);
        SC_SkillSound.broadcast(pc, pc.getId(), sound);
    }

	static void startComa(L1PcInstance pc, int skillId, int duration) {
		pc.applySkillModifierFromOwner(skillId, 0);
		if (skillId == L1SkillId.COMA_B) sendSpMr(pc);
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startMiso(L1PcInstance pc, int skillId, int duration) {
		final int icon = switch (skillId) {
			case L1SkillId.miso1 -> 4995;
			case L1SkillId.miso2 -> 4996;
			case L1SkillId.miso3 -> 4997;
			default -> throw new IllegalArgumentException("Unknown Miso buff " + skillId);
		};
		if (pc.hasSkillEffect(skillId)) {
			SC_InventoryIcon.icoReset(pc, skillId, icon, 1800L, true);
		} else {
			if (skillId != L1SkillId.miso1) {
				pc.applySkillModifierFromOwner(skillId, 0);
				SC_SPMR.send(pc);
				SC_OwnCharAttrDef.send(pc);
			}
			SC_InventoryIcon.icoNew(pc, skillId, icon, 1800L, true);
		}
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startClanStance(L1PcInstance pc, int skillId, int duration) {
		final var clan = pc.getClan();
		if (clan == null) return;

		final int blessIdx = Math.max(0, clan.getBless() - 1);
		final int[] times = clan.getBuffTime();
		final int buffTime = times != null && blessIdx < times.length ? times[blessIdx] : 0;
		final int stance = skillId - L1SkillId.CLAN_BUFF1;
		if (stance <= 1) pc.applySkillModifierFromOwner(skillId, 0);
		if (skillId == L1SkillId.CLAN_BUFF2) sendAttrDef(pc);
		SC_ActionUI2.send(pc, 2724 + stance, buffTime, 7, 7233 + 2 * stance, 4650 + stance);
		SC_ServerMessage.send(pc, 4618, clanStanceName(skillId));
		SC_SkillSound.send(pc, pc.getId(), 14482);
		applySkillEffectSec(pc, skillId, duration);
	}

	static String clanStanceName(int skillId) {
		return switch (skillId) {
			case L1SkillId.CLAN_BUFF1 -> "$22503";
			case L1SkillId.CLAN_BUFF2 -> "$22504";
			case L1SkillId.CLAN_BUFF3 -> "$22505";
			case L1SkillId.CLAN_BUFF4 -> "$22506";
			default -> throw new IllegalArgumentException("Unknown clan stance " + skillId);
		};
	}

	// ===== RETURN_TO_NATURE =====
	private static HandlerResult onReturnToNature(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha instanceof L1SummonInstance) {
			final L1SummonInstance summon = (L1SummonInstance) cha;
			SC_SkillSound.broadcastFast(summon, summon.getId(), 2245);
			summon.returnToNature();
			return HandlerResult.HANDLED;
		}

		final L1Character user = su.hdUser();
		if (user instanceof L1PcInstance) {
			SC_ServerMessage.send(((L1PcInstance) user), 79);
			return HandlerResult.HANDLED;
		}
		return HandlerResult.HANDLED;
	}

	/** Dedicated control effects run only on the recipient's existing controller FIFO. */
	static void startPowerRipFromOwner(L1PcInstance pc, int id, int durationMs,
			int option, int gfx, boolean partyMember) {
		if (durationMs <= 0 || pc.isDead()) return;
		pc.skillEffectSetFromOwner(L1SkillId.POWERRIP, durationMs);
		spawnEffectAtTarget(pc, 9415, durationMs);
		SC_Paralysis.send(pc, SC_Paralysis.TYPE_RIP, true);
	}

	static void startDesperadoFromOwner(L1PcInstance pc, int id, int durationMs,
			int attackerLevel, int gfx, boolean partyMember) {
		if (durationMs <= 0 || pc.isDead()) return;
		if (pc.hasSkillEffect(L1SkillId.DESPERADO))
			pc.skillEffectRemoveFromOwner(L1SkillId.DESPERADO, true);
		pc.Desperadolevel = attackerLevel;
		pc.skillEffectSetFromOwner(L1SkillId.DESPERADO, durationMs);
		spawnEffectAtTarget(pc, gfx, durationMs);
		SC_Paralysis.send(pc, SC_Paralysis.TYPE_PERADO, true);
	}

	static void startThunderGrabFromOwner(L1PcInstance pc, int id, int durationMs,
			int option, int gfx, boolean partyMember) {
		if (durationMs <= 0 || pc.isDead()) return;
		pc.skillEffectSetFromOwner(L1SkillId.STATUS_FREEZE, durationMs);
		spawnEffectAtTarget(pc, 81182, durationMs);
		sendSkillSoundOnce(pc, 4184);
		SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, true);
	}

	// ===== POWERRIP =====
	static HandlerResult onPowerRip(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;

		final int dur = SkillUseHelper.pickMillis(SkillUseHelper.GRIP_OFFICIAL);

		if (target instanceof L1PcInstance pc) {
			return submitContextBuff(pc, L1SkillId.POWERRIP, dur, 0, 0, null)
					? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;

		} else if (target instanceof L1MonsterInstance
				|| target instanceof L1SummonInstance
				
				|| target instanceof L1CompanionInstance) {

			final L1NpcInstance npc = (L1NpcInstance) target;
			if (!npc.trySetSkillEffect(L1SkillId.POWERRIP, dur)) {
				throw new java.util.concurrent.RejectedExecutionException("NPC POWERRIP admission exhausted");
			}
			spawnEffectAtTarget(target, 9415, dur);
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	// ===== 인벤 아이콘 + 버프 리프레시(UI 정책 그대로 유지) =====
	private static void refreshInventoryBuffIcon(final L1PcInstance pc, final int skillId, final int durSec) {
		if (pc == null) return;

		if (pc.hasSkillEffect(skillId)) {
			pc.removeSkillEffect(skillId); // 종료(원복) 먼저 태움
			SC_InventoryIcon.icoReset(pc, skillId, durSec, true);
		} else {
			SC_InventoryIcon.icoNew(pc, skillId, durSec, true);
		}
	}

	private static int clampImpactUpByLevel(final int lvl) {
		// 5, lvl>=80이면 5+(lvl-79) 를 5~10으로 clamp
		// => lvl 80=6 ... lvl84=10, 이후 10 고정
		int up = 5;
		if (lvl >= 80) up = 5 + (lvl - 79);
		if (up < 5) up = 5;
		if (up > 10) up = 10;
		return up;
	}

	// =========================
	// IMPACT (파티 시야 18셀 버프)
	// =========================
	static HandlerResult castImpact(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;
		final int id = L1SkillId.IMPACT;
		return submitPartyBuff(caster, id, durationSecForBuff(su, id), clampImpactUpByLevel(caster.getLevel()), 0)
				? HandlerResult.HANDLED : HandlerResult.CONTINUE;
	}

	static void startImpact(L1PcInstance pc, int skillId, int duration, int bonus, int gfx, boolean partyMember) {
		refreshInventoryBuffIcon(pc, skillId, duration);
		pc.applySkillModifierFromOwner(skillId, bonus);
		applySkillEffectSec(pc, skillId, duration);
	}

	static HandlerResult castSleep(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target == null) return HandlerResult.HANDLED_WITHOUT_TIMER;
		final int id = su.hdSkillId();
		final int durationMs = DurationResolver.INSTANCE.resolveDurationMs(su);
		if (durationMs <= 0) return HandlerResult.HANDLED_WITHOUT_TIMER;

		if (target instanceof L1PcInstance pc) {
			return submitContextBuff(pc, id, durationMs, 0, 0, null)
					? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
		}
		if (target instanceof L1NpcInstance npc && !npc.trySetSkillEffect(id, durationMs))
			return HandlerResult.HANDLED_WITHOUT_TIMER;
		if (id == L1SkillId.FOG_OF_SLEEPING) broadcastSleepSound(target);
		return HandlerResult.HANDLED;
	}

	static void startSleep(L1PcInstance pc, int id, int durationMs, int option, int gfx, boolean partyMember) {
		if (option != 0) {
			if (pc.isDead() || pc.hasSkillEffect(id)) return;
			pc.skillEffectSetFromOwner(id, durationMs);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_SLEEP, true);
			sendSkillSoundBroadcast(pc, 760);
			return;
		}
		final boolean fog = id == L1SkillId.FOG_OF_SLEEPING;
		if (fog) {
			SC_SkillSound.send(pc, pc.getId(), 9218);
			SC_SkillSound.send(pc, pc.getId(), 760);
		}
		SC_Paralysis.send(pc, SC_Paralysis.TYPE_SLEEP, true);
		if (fog) broadcastSleepSound(pc);
		pc.skillEffectSetFromOwner(id, durationMs);
	}

	private static void broadcastSleepSound(L1Character target) {
		SC_SkillSound.broadcastFast(target, target.getId(), 9218);
		SC_SkillSound.broadcastFast(target, target.getId(), 760);
	}

	static HandlerResult castWindShackle(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc)) return HandlerResult.HANDLED;
		final int id = su.hdSkillId();
		if (isDragonWindShackle(id) && pc.isDead()) return HandlerResult.CONTINUE;
		return submitContextBuff(pc, id, DurationResolver.INSTANCE.resolveDurationMs(su), su.hdGetBuffIconDuration(), 0, null)
				? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	private static boolean isDragonWindShackle(int id) {
		return id == L1SkillId.RINDVIOR_WIND_SHACKLE || id == L1SkillId.RINDVIOR_WIND_SHACKLE_1
				|| id == L1SkillId.DRAKE_WIND_SHACKLE;
	}

	static void startWindShackle(L1PcInstance pc, int id, int durationMs, int iconDuration, int gfx, boolean partyMember) {
		if (isDragonWindShackle(id)) {
			if (pc.isDead()) return;
			pc.skillEffectSetFromOwner(L1SkillId.WIND_SHACKLE, 12_000);
			if (pc.getCurrentWeapon() != 88) SC_SkillIconWindShackle.send(pc, pc.getId(), iconDuration);
			sendSkillSoundBroadcast(pc, 1799);
			if (durationMs > 0) pc.skillEffectSetFromOwner(id, durationMs);
		} else {
			SC_SkillIconWindShackle.send(pc, pc.getId(), iconDuration);
			pc.skillEffectSetFromOwner(id, (int) Math.clamp(iconDuration * 1000L, 0, Integer.MAX_VALUE));
			if (durationMs > 0) pc.skillEffectSetFromOwner(id, durationMs);
		}
	}

	private static HandlerResult onTurnUndead(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		if (undeadType == 1 || undeadType == 3) {
			su.hdSetDamage(cha.getCurrentHp());

			L1Character target = su.hdTarget();
			if (target instanceof L1MonsterInstance) {
				L1MonsterInstance m = (L1MonsterInstance) target;
				L1Character user = su.hdUser();
				if (user instanceof L1PcInstance) {
					m.setHate(user, 1);
				}
			}
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onManaDrain(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		int chance = su.hdRandom().nextInt(5) + 10;

		L1Character user = su.hdUser();
		L1PcInstance pc = su.hdPlayer();
		int drainMana = chance + (user.GetTotalStatInt() / 2);

		if (cha.getCurrentMp() < drainMana) {
			drainMana = cha.getCurrentMp();
		}

		if (user instanceof L1PcInstance) {
			SC_SkillSound.send(pc, pc.getId(), 2171);
			SC_SkillSound.broadcastFast(pc, pc.getId(), 2171);
		} else {
			SC_SkillSound.broadcastFast(user, user.getId(), 2171);
		}

		su.hdSetDrainMana(drainMana);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onWeaponBreak(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.HANDLED;

		final int calcType = su.hdCalcType();
		if (calcType == su.hdCalcTypePcPc() || calcType == su.hdCalcTypeNpcPc()) {
			if (cha instanceof L1PcInstance) {
				L1PcInstance pc = (L1PcInstance) cha;
				L1ItemInstance weapon = pc.getWeapon();
				if (weapon != null) {
					L1Character user = su.hdUser();
					int weaponDamage = su.hdRandom().nextInt(user.GetTotalStatInt() / 3) + 1;
					SC_ServerMessage.send(pc, 268, weapon.getLogName());
					pc.getInventory().receiveDamage(weapon, weaponDamage);
				}
			}
		} else {
			if (cha instanceof L1NpcInstance) {
				((L1NpcInstance) cha).setWeaponBreaked(true);
			}
		}
		return HandlerResult.HANDLED;
	}

	static HandlerResult castStatusFreeze(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc)) return HandlerResult.HANDLED;
		return submitContextBuff(pc, su.hdSkillId(), DurationResolver.INSTANCE.resolveDurationMs(su), 0, 0, null)
				? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	static void startStatusFreeze(L1PcInstance pc, int id, int durationMs, int option, int gfx, boolean partyMember) {
		SC_Paralysis.send(pc, SC_Paralysis.TYPE_BIND, true);
		if (durationMs > 0) pc.skillEffectSetFromOwner(id, durationMs);
	}

	// =========================
	// GRACE_AVATAR (자기 + 파티 시야 18셀)
	// =========================
	static HandlerResult castGraceAvatar(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final L1PcInstance caster = target instanceof L1PcInstance pc ? pc : su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;
		return submitPartyBuff(caster, L1SkillId.GRACE_AVATAR, 15, 0, 0)
				? HandlerResult.HANDLED : HandlerResult.CONTINUE;
	}

	static void startGraceAvatar(L1PcInstance pc, int skillId, int duration, int option, int gfx, boolean partyMember) {
		sendRestatBuffNoti(pc, skillId, duration, 7427, 10, 4734, 4734, 4741, true);
		pc.applySkillModifierFromOwner(skillId, 0);
		applySkillEffectSec(pc, skillId, duration);
		if (partyMember) SC_ServerMessage.send(pc, 4734);
		else sendSkillSoundOnce(pc, 14495);
	}

	static void startResistMagic(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		CSkillInfo.applyFixedModifierFromOwner(pc, skillId);
		SC_SPMR.send(pc);
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startGodBuff(L1PcInstance pc, int skillId, int duration) {
		pc.applySkillModifierFromOwner(skillId, 3);
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startArmorBuff(L1PcInstance pc, int skillId, int iconDuration) {
		final int bonus = switch (skillId) {
			case L1SkillId.IRON_SKIN -> -10;
			case L1SkillId.FIRE_SHIELD -> -4;
			case L1SkillId.BLESSED_ARMOR -> -3;
			default -> throw new IllegalArgumentException("Unknown armor buff " + skillId);
		};
		if (skillId == L1SkillId.BLESSED_ARMOR) {
			resetEffect(pc, skillId);
			final int millis = (int) Math.clamp(iconDuration * 1000L, 0, Integer.MAX_VALUE);
			pc.setSkillEffect(skillId, millis);
			pc.applySkillModifierFromOwner(skillId, bonus);
			sendRestatBuffNoti(pc, skillId, iconDuration, 5316, 5316, 0, 3060, 3060, 2240, true);
			SC_OwnCharStatus.send(pc);
			return;
		}

		pc.applySkillModifierFromOwner(skillId, bonus);
		SC_SkillIconShield.send(pc, -bonus, iconDuration);
		applySkillEffectSec(pc, skillId, iconDuration > 0 ? iconDuration : durationSecForBuff(null, skillId));
	}

	static void startPhysicalEnchant(L1PcInstance pc, int skillId, int iconDuration) {
		final boolean dexterity = skillId == L1SkillId.PHYSICAL_ENCHANT_DEX;
		// Support grants and ordinary casts share the owner-side exclusion rule.
		SkillUseHelper.deleteRepeatedSkills(pc, skillId);
		resetEffect(pc, skillId);
		CSkillInfo.applyFixedModifierFromOwner(pc, skillId);
		final int duration = iconDuration > 0 ? iconDuration : durationSecForBuff(null, skillId);
		if (dexterity) SC_Dexup.send(pc, pc, 5, duration);
		else SC_Strup.send(pc, pc, 5, duration);
		applySkillEffectSec(pc, skillId, duration);
	}



	/** REDUCE_WEIGHT / DECREASE_WEIGHT 공용 */
	static void startFixedCombatBuff(L1PcInstance pc, int skillId, int duration) {
		pc.applySkillModifierFromOwner(skillId, 0);
		switch (skillId) {
		case L1SkillId.BURNING_WEAPON -> SC_SkillIconAura.send(pc, 162, duration);
		case L1SkillId.STORM_EYE -> SC_SkillIconAura.send(pc, 155, duration);
		case L1SkillId.STORM_SHOT -> SC_SkillIconAura.send(pc, 165, duration);
		case L1SkillId.EARTH_GUARDIAN -> SC_SkillIconShield.send(pc, 7, duration);
		default -> { }
		}
		applySkillEffectSec(pc, skillId, duration > 0 ? duration : durationSecForBuff(null, skillId));
	}





	static void startBraveSpeed(L1PcInstance pc, int skillId, int duration) {
		final boolean bloodLust = skillId == L1SkillId.BLOOD_LUST;
		pc.setBraveSpeed(bloodLust ? 1 : 4);
		sendBraveMode(pc, bloodLust ? 6 : 4, duration);
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startBraveVisual(L1PcInstance pc, int skillId, int iconDuration) {
		final int mode = switch (skillId) {
			case L1SkillId.HURRICANE -> 9;
			case L1SkillId.FOCUS_WAVE -> 10;
			default -> 1;
		};
		final int duration = iconDuration > 0 ? iconDuration : durationSecForBuff(null, skillId);
		if (skillId == L1SkillId.FOCUS_WAVE) {
			resetEffect(pc, skillId);
			pc.set_focus_wave_level(1);
		}
		// Client icon and speed-mode duration must match the installed timer.
		sendBraveMode(pc, mode, duration);
		if (skillId == L1SkillId.DANCING_BLADES) SC_SkillIconAura.send(pc, 154, duration);
		applySkillEffectSec(pc, skillId, duration);
	}

	private static void sendBraveMode(L1PcInstance pc, int mode, int duration) {
		SC_SkillBrave.send(pc, pc.getId(), mode, duration);
		SC_SkillBrave.broadcastFast(pc, pc.getId(), mode, 0);
	}

	static HandlerResult castBlind(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc)) return HandlerResult.HANDLED;
		final int durationMs = DurationResolver.INSTANCE.resolveDurationMs(su);
		return submitContextBuff(pc, su.hdSkillId(), durationMs, su.hdStatusFloatingEye(), 0, null)
				? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	static void startBlind(L1PcInstance pc, int id, int durationMs, int floatingEyeId, int gfx, boolean partyMember) {
		final boolean floatingEye = pc.hasSkillEffect(floatingEyeId);
		pc.sendPackets(floatingEye ? 2 : 1);
		if (floatingEye && !pc.isBlind()) pc.showFloatingEyeToInvis();
		if (durationMs > 0) pc.skillEffectSetFromOwner(id, durationMs);
	}



	static void startDisease(L1PcInstance pc, int skillId, int duration) {
		if (duration < 0) {
			// The desert NPC version reduces weapon damage instead of accuracy.
			pc.applySkillModifierFromOwner(skillId, -6L << 32);
			applySkillEffectSec(pc, skillId, -duration);
		} else {
			startCommonStatBuff(pc, skillId, duration);
		}
	}


	static void startCommonStatBuff(L1PcInstance pc, int skillId, int duration) {
		if (duration <= 0) return;
		// Replacing the recorded source is idempotent; the common timer keeps its refresh policy.
		CSkillInfo.applyFixedModifierFromOwner(pc, skillId);
		applySkillEffectSec(pc, skillId, duration);
		switch (skillId) {
		case L1SkillId.RESIST_ELEMENTAL, L1SkillId.ELEMENTAL_PROTECTION,
			 L1SkillId.MIRROR_IMAGE, L1SkillId.UNCANNY_DODGE -> SC_OwnCharAttrDef.send(pc);
		case L1SkillId.INSIGHT, L1SkillId.PANIC -> pc.resetBaseMr();
		default -> { }
		}
	}



	static HandlerResult castInvisibility(L1SkillUse cast, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc)) return HandlerResult.NOT_HANDLED;
		if (pc.is_combat_field()) return HandlerResult.RETURN;
		final int id = cast.hdSkillId();
		return submitContextBuff(pc, id, durationSecForBuff(cast, id), 0, 0, null)
				? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	static void startInvisibility(L1PcInstance pc, int id, int duration, int option, int gfx, boolean partyMember) {
		if (pc.is_combat_field()) return;
		if (id == L1SkillId.BLIND_HIDING && pc.hasSkillEffect(id)) {
			pc.delBlindHiding();
			return;
		}
		if (pc.getDollList() != null) {
			for (Object doll : pc.getDollList().values()) {
				if (doll instanceof L1DollInstance active) active.deleteDoll(false);
			}
		}
		applySkillEffectSec(pc, id, duration);
		SC_Invis.send(pc, pc.getId(), 1);
		SC_Invis.broadcastFast(pc, pc.getId(), 1);
		NpcEventFactory.onPlayerInvisibility(pc);
	}


	static void startNormalBlessing(L1PcInstance pc, int skillId, int duration) {
		pc.applySkillModifierFromOwner(skillId, 0);
		SC_OwnCharAttrDef.send(pc);
		applySkillEffectSec(pc, skillId, duration);
	}

	private static HandlerResult onAreaOfSilenceSound(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		SC_SkillSound.send(pc, pc.getId(), 10708);
		SC_SkillSound.broadcastFast(pc, pc.getId(), 10708);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onBossCounterBarrierEffect(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (!(cha instanceof L1PcInstance)) return HandlerResult.HANDLED;
		L1PcInstance pc = (L1PcInstance) cha;

		pc.send_effect(10709);
		return HandlerResult.HANDLED;
	}





	static void startBerserkers(L1PcInstance pc, int skillId, int duration) {
		if (duration < 0) {
			resetEffect(pc, skillId);
			applySkillEffectSec(pc, skillId, -duration);
			pc.applySkillModifierFromOwner(skillId, (5L << 32) | 2);
			SC_OwnCharAttrDef.send(pc);
		} else {
			CSkillInfo.applyFixedModifierFromOwner(pc, skillId);
			applySkillEffectSec(pc, skillId, duration);
		}
	}



	static void startClearMind(L1PcInstance pc, int skillId, int durSec) {
		if (durSec <= 0) return;
		resetEffect(pc, skillId);
		CSkillInfo.applyFixedModifierFromOwner(pc, skillId);
		applySkillEffectSec(pc, skillId, durSec);

		// on_icon=745, off_icon=5279 (서로 다름)
		sendRestatBuffNotiDualIcon(
				pc,
				L1SkillId.CLEAR_MIND,
				durSec,
				745,    // on icon
				5279,   // off icon
				0,      // priority
				861,    // tooltip
				861,    // new
				0,      // end 없음
				true
				);
	}




	static void startMeleeBuff(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		CSkillInfo.applyFixedModifierFromOwner(pc, skillId);
		if (skillId == L1SkillId.GLOWING_AURA) {
			SC_SkillIconAura.send(pc, 113, duration);
			applySkillEffectSec(pc, skillId, duration > 0 ? duration : durationSecForBuff(null, skillId));
			return;
		}

		applySkillEffectSec(pc, skillId, duration);
		if (skillId == L1SkillId.IllUSION_OGRE) {
			sendRestatBuffNoti(pc, skillId, duration, 3117, 10, 1340, 1366, 2212, true);
		} else if (skillId == L1SkillId.ENCHANT_ACURUCY) {
			SC_SystemMessage.send(pc, "인챈트 어큐러시: 근거리 명중+5");
			sendRestatBuffNoti(pc, skillId, duration, 9487, 0, 5888, 0, 0, true);
		}
	}

	static void startShiningShield(L1PcInstance pc, int skillId, int iconDuration) {
		resetEffect(pc, skillId);
		final boolean shining = skillId == L1SkillId.SHINING_AURA;
		pc.applySkillModifierFromOwner(skillId, shining ? -8 : -2);
		SC_OwnCharAttrDef.send(pc);
		if (shining) SC_SkillIconAura.send(pc, 114, iconDuration);
		else SC_SkillIconShield.send(pc, 2, iconDuration);
		int duration = iconDuration > 0 ? iconDuration : durationSecForBuff(null, skillId);
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startBraveAura(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		SC_SkillIconAura.send(pc, 116, duration);
		applySkillEffectSec(pc, skillId, duration > 0 ? duration : durationSecForBuff(null, skillId));
	}

	static void startShadowArmor(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, 5L << 32);
		SC_SPMR.send(pc);
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startDressStatBuff(L1PcInstance pc, int skillId, int iconDuration) {
		final boolean dexterity = skillId == L1SkillId.DRESS_DEXTERITY;
		resetEffect(pc, skillId);
		pc.applySkillModifierFromOwner(skillId, dexterity ? 3 << 8 : 3);
		if (dexterity) SC_Dexup.send(pc, pc, 2, iconDuration);
		else SC_Strup.send(pc, pc, 2, iconDuration);
		final int duration = iconDuration > 0 ? iconDuration : durationSecForBuff(null, skillId);
		applySkillEffectSec(pc, skillId, duration);
	}


	static HandlerResult onDesperado(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character user = su.hdUser();
		final L1PcInstance player = su.hdPlayer();
		final L1Character target = (cha != null ? cha : su.hdTarget());

		if (user == null || target == null) return HandlerResult.NOT_HANDLED;

		int shockDur = SkillUseHelper.pickMillis(SkillUseHelper.DEPE_OFFICIAL);
		int effectNpcId = 9416;

		if (player != null && player.isPassive(PassiveID.DESPERADO_ABSOLUTE.toInt())) {		
			int bonusTime = SkillUseHelper.pickMillis(SkillUseHelper.BOUNS);
			shockDur += bonusTime;
			effectNpcId = 9418;
		}

		su.hdSetShockStunDuration(shockDur);

		if (target instanceof L1PcInstance pc) {
			return submitContextBuff(pc, L1SkillId.DESPERADO, shockDur,
					user.getLevel(), effectNpcId, null)
					? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
		} else if (target instanceof L1MonsterInstance
				|| target instanceof L1SummonInstance
				
				|| target instanceof L1CompanionInstance) {

			L1NpcInstance npc = (L1NpcInstance) target;
			if (!npc.trySetSkillEffect(L1SkillId.DESPERADO, shockDur)) {
				throw new java.util.concurrent.RejectedExecutionException("NPC DESPERADO admission exhausted");
			}
			spawnEffectAtTarget(target, effectNpcId, shockDur);
		} else {
			spawnEffectAtTarget(target, effectNpcId, shockDur);
		}

		return HandlerResult.HANDLED;
	}

	static HandlerResult onThunderGrab(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final L1PcInstance player = su.hdPlayer();
		// 확률 판정
		boolean ok = false;
		try {
			if (magic != null) ok = magic.calcProbabilityMagic(su.hdSkillId());
		} catch (Throwable ignore) {
		}
		if (!ok) return HandlerResult.HANDLED;

		int shockDur = SkillUseHelper.pickMillis(SkillUseHelper.DEPE_OFFICIAL);

		if (player != null && player.isPassive(PassiveID.THUNDER_GRAP_BRAVE.toInt())) {		
			int bonusTime = SkillUseHelper.pickMillis(SkillUseHelper.BOUNS);
			shockDur += bonusTime;
		}
		// PC
		if (cha instanceof L1PcInstance pc) {
			return submitContextBuff(pc, L1SkillId.THUNDER_GRAB, shockDur, 0, 0, null)
					? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
		}

		// NPC/소환/펫/동료
		if (cha instanceof L1MonsterInstance || cha instanceof L1SummonInstance  || cha instanceof L1CompanionInstance) {
			final L1NpcInstance npc = (L1NpcInstance) cha;

			if (!npc.trySetSkillEffect(L1SkillId.STATUS_FREEZE, shockDur)) {
				throw new java.util.concurrent.RejectedExecutionException("NPC STATUS_FREEZE admission exhausted");
			}
			spawnEffectAtTarget(cha, 81182, shockDur);
			sendSkillSoundBroadcast(npc, 4184);
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	// =========================
	//  REMOVE_CURSE
	// =========================
	private static HandlerResult onRemoveCurse(
	        L1SkillUse su,
	        L1Character cha,
	        L1Magic magic,
	        int undeadType,
	        Iterator<?> iter) {

	    if (cha == null) {
	        return HandlerResult.NOT_HANDLED;
	    }

	    cha.curePoison();

	    if (cha.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZING)
	            || cha.hasSkillEffect(L1SkillId.STATUS_CURSE_PARALYZED)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_1)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_6)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_7)
	            || cha.hasSkillEffect(L1SkillId.ANTA_MESSAGE_8)
	            || cha.hasSkillEffect(L1SkillId.OMAN_STUN)
	            || cha.hasSkillEffect(L1SkillId.Maeno_STUN)) {
	        cha.cureParalaysis();
	    }

	    // Both removable blind sources may coexist. Let each source's owner
	    // expiry handler restore sight only when the last blind source is gone.
	    if (cha.hasSkillEffect(L1SkillId.CURSE_BLIND)) {
	        cha.removeSkillEffect(L1SkillId.CURSE_BLIND);
	    }
	    if (cha.hasSkillEffect(L1SkillId.DARKNESS)) {
	        cha.removeSkillEffect(L1SkillId.DARKNESS);
	    }

	    return HandlerResult.HANDLED;
	}

	private static HandlerResult castFreeze(L1SkillUse cast, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target == null) return HandlerResult.HANDLED_WITHOUT_TIMER;
		final int id = cast.hdSkillId();
		final boolean ice = id == L1SkillId.ICE_LANCE;
		if (ice && (magic == null || !magic.calcProbabilityMagic(id))) return HandlerResult.HANDLED_WITHOUT_TIMER;
		final int duration = DurationResolver.INSTANCE.resolveDurationMs(cast);
		final int visual = ice ? (int) Math.clamp(cast.hdGetBuffIconDuration() * 1000L, 0, Integer.MAX_VALUE) : 0;
		if (target instanceof L1PcInstance && duration <= 0) return HandlerResult.HANDLED_WITHOUT_TIMER;
		if (target instanceof L1NpcInstance npc) {
			final int fallback = ice ? visual : SkillUseHelper.pickMillis(SkillUseHelper.EARTH_BIND_DUR);
			final int npcDuration = duration > 0 ? duration : fallback;
			if (!canFreezeNpc(npc) || npcDuration <= 0 || !npc.trySetSkillEffect(id, npcDuration))
				return HandlerResult.HANDLED_WITHOUT_TIMER;
		}
		if (ice) spawnEffectAtTarget(target, 81168, visual > 0 ? visual : duration);
		sendFreeze(target);
		return HandlerResult.HANDLED;
	}

	private static boolean canFreezeNpc(L1NpcInstance target) {
		return target instanceof L1MonsterInstance || target instanceof L1SummonInstance
				 || target instanceof L1CompanionInstance;
	}

	private static void sendFreeze(L1Character target) {
		if (target instanceof L1PcInstance pc) {
			SC_Poison.send(pc, pc.getId(), 2);
			SC_Poison.broadcastFast(pc, pc.getId(), 2);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);
		} else if (target instanceof L1NpcInstance) {
			SC_Poison.broadcastFast(target, target.getId(), 2);
		}
	}

	private static HandlerResult onDgUiAndKill(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1Character user = su.hdUser();
		final L1PcInstance player = su.hdPlayer();

		if (user instanceof L1NpcInstance) {
			final L1NpcInstance npc = (L1NpcInstance) user;
			SC_NpcChatPacket.broadcastNpcFast(npc, npc, "$3717", 0);
		} else if (player != null) {
			SC_ChatPacket.broadcastComplex0(player, "$3717", 0);
		}

		if (cha != null) {
			su.hdSetDamage(cha.getCurrentHp());
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onTeleportFront5(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		// 10057 : 타겟 앞 5칸 소환
		if (cha == null) return HandlerResult.HANDLED;
		final L1Character user = su.hdUser();
		if (user == null) return HandlerResult.HANDLED;

		L1Teleport.getInstance().teleportToTargetFront(cha, user, 5, true);
		return HandlerResult.HANDLED;
	}


	public static void notifyCashScrollStats(L1PcInstance pc, int id) {
		switch (id) {
		case L1SkillId.STATUS_CASHSCROLL -> {
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			if (pc.isInParty()) pc.getParty().refreshPartyMemberStatus(pc);
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		}
		case L1SkillId.STATUS_CASHSCROLL2 -> SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		case L1SkillId.STATUS_CASHSCROLL3, L1SkillId.STATUS_CASHSCROLL4 -> SC_SPMR.send(pc);
		default -> { }
		}
	}

	static void startNpcBloodBuff(L1PcInstance pc, int skillId, int duration) {
		final boolean showDefense = skillId == L1SkillId.BUFF_SAEL || skillId == L1SkillId.BUFF_GUNTER;
		if (showDefense && pc.hasSkillEffect(skillId)) pc.removeSkillEffect(skillId);
		pc.applySkillModifierFromOwner(skillId, 0);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		if (showDefense) SC_OwnCharAttrDef.send(pc);
		SC_SPMR.send(pc);
		applySkillEffectSec(pc, skillId, duration);
	}

	// =========================
	//  CALL_OF_NATURE (부활계열)
	// =========================
	private static HandlerResult onCallOfNature(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		if (cha == null) return HandlerResult.NOT_HANDLED;

		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		// 대상이 PC
		if (cha instanceof L1PcInstance) {
			final L1PcInstance pc = (L1PcInstance) cha;
			if (caster.getId() != pc.getId()) {
				if (CWorld.getInstance().getVisiblePlayer(pc, 0).size() > 0) {
					for (L1PcInstance v : CWorld.getInstance().getVisiblePlayer(pc, 0)) {
						if (!v.isDead()) {
							SC_ServerMessage.send(caster, 592);
							return HandlerResult.RETURN;
						}
					}
				}

				// 죽어있으면 Y/N 부활창
				if (pc.getCurrentHp() == 0 && pc.isDead()) {
					pc.setTempID(caster.getId());
					SC_MessageYN.send(pc, 322, "");
				}
			}
			return HandlerResult.HANDLED;
		}

		// 대상이 NPC
		if (cha instanceof L1NpcInstance) {
			if (cha instanceof L1TowerInstance) return HandlerResult.HANDLED;

			final L1NpcInstance npc = (L1NpcInstance) cha;
			if ((npc instanceof L1CompanionInstance)
					&& CWorld.getInstance().getVisiblePlayer(npc, 0).size() > 0) {
				for (L1PcInstance v : CWorld.getInstance().getVisiblePlayer(npc, 0)) {
					if (!v.isDead()) {
						SC_ServerMessage.send(caster, 592);
						return HandlerResult.RETURN;
					}
				}
			}

			// 죽어있으면 부활
			if (npc.getCurrentHp() == 0 && npc.isDead()) {
				npc.resurrect(cha.getMaxHp());
				npc.resurrect(cha.getMaxMp() / 100);
				npc.setResurrect(true);
			}
			return HandlerResult.HANDLED;
		}

		return HandlerResult.NOT_HANDLED;
	}

	private static HandlerResult castMovementSpeed(L1SkillUse cast, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final int id = cast.hdSkillId();
		final boolean greater = id == L1SkillId.GREATER_HASTE;
		if (target == null || (greater && !(target instanceof L1PcInstance))) return HandlerResult.NOT_HANDLED;
		final L1PcInstance pc = target instanceof L1PcInstance player ? player : null;
		if (pc != null && pc.getHasteItemEquipped() > 0)
			return id == L1SkillId.HASTE ? HandlerResult.HANDLED : HandlerResult.CONTINUE;

		final int speed = target.getMoveSpeed();
		final int duration = durationSecForBuff(cast, id);
		if (id != L1SkillId.HASTE && !greater) {
			if (speed == 0) {
				if (pc != null) SC_SkillHaste.send(pc, pc.getId(), 2, duration);
				SC_SkillHaste.broadcastFast(target, target.getId(), 2, duration);
				target.setMoveSpeed(2);
			} else if (speed == 1 && cancelOpposingSpeed(target, id, MovementSpeedEffects.activeHaste(target, 0))) {
				return HandlerResult.CONTINUE;
			}
			return HandlerResult.HANDLED;
		}

		if (speed == 2) {
			final boolean removed = cancelOpposingSpeed(target, id, MovementSpeedEffects.activeSlow(target, 0));
			return greater && removed ? HandlerResult.CONTINUE : HandlerResult.HANDLED;
		}
		if (pc != null) {
			pc.setDrink(false);
			if (greater) pc.setMoveSpeed(1);
			SC_SkillHaste.send(pc, pc.getId(), 1, duration);
		}
		SC_SkillHaste.broadcastFast(target, target.getId(), 1, 0);
		if (greater) applySkillEffectSec(pc, id, duration);
		else target.setMoveSpeed(1);
		return HandlerResult.HANDLED;
	}

	private static boolean cancelOpposingSpeed(L1Character target, int incoming, int opposing) {
		if (opposing == 0) return false;
		target.removeSkillEffect(opposing);
		target.removeSkillEffect(incoming);
		return true;
	}

	static void startDragonMaan(L1PcInstance pc, int skillId, int duration) {
		pc.applySkillModifierFromOwner(skillId, 0);
		if (hasDragonMaanSpellPower(skillId)) sendSpMr(pc);
		applySkillEffectSec(pc, skillId, duration);
	}

	static boolean hasDragonMaanSpellPower(int skillId) {
		return skillId == L1SkillId.LIND_MAAN || skillId == L1SkillId.SHAPE_MAAN || skillId == L1SkillId.LIFE_MAAN;
	}


	static void startDragonBlood(L1PcInstance pc, int skillId, int duration) {
		if (pc.hasSkillEffect(skillId)) pc.removeSkillEffect(skillId);
		pc.applySkillModifierFromOwner(skillId, 0);
		sendOwnStatus(pc);
		sendDragonBloodIcon(pc, dragonBloodIcon(skillId), Math.max(1, duration / 60));
		applySkillEffectSec(pc, skillId, duration);
	}

	static int dragonBloodIcon(int skillId) {
		return switch (skillId) {
			case L1SkillId.ANTA_BUFF -> 82;
			case L1SkillId.FAFU_BUFF -> 85;
			case L1SkillId.RIND_BUFF -> 88;
			default -> throw new IllegalArgumentException("Unknown dragon blood effect " + skillId);
		};
	}

	private static void sendOwnStatus(L1PcInstance pc) {
		try { SC_OwnCharStatus.send(pc); } catch (Throwable ignore) {}
	}

	private static void sendDragonBloodIcon(L1PcInstance pc, int iconId, int minutes) {
		try { SC_PacketBox.send(pc, SC_PacketBox.DRAGONBLOOD, iconId, minutes); } catch (Throwable ignore) {}
	}

	/** Keep the original 2-6 tick distribution; begin generation on the victim owner. */
	static void startTomahawkFromOwner(L1PcInstance pc, int id, int ticks,
			int casterLevel, int casterId, boolean partyMember) {
		if (pc.isDead() || ticks < 2 || ticks > 6 || casterLevel <= 0) return;
		TomahawkBleedService.getInstance().startResolvedFromOwner(
				pc, casterId, casterLevel, ticks);
	}

	/**
	 * 토마호크:
	 */
	static HandlerResult onTomahawk(L1SkillUse su, L1Character cha, L1Magic magic, int undeadType, Iterator<?> iter) {
		final L1PcInstance caster = su.hdPlayer();
		if (caster == null) return HandlerResult.NOT_HANDLED;

		final L1Character target = (cha != null ? cha : su.hdTarget());
		if (target == null) return HandlerResult.NOT_HANDLED;

		if (target instanceof L1PcInstance pc) {
			final int ticks = 2 + su.hdRandom().nextInt(5);
			return submitContextBuff(pc, L1SkillId.TOMAHAWK, ticks,
					caster.getLevel(), caster.getId(), null)
					? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
		}
		TomahawkBleedService.getInstance().start(su, caster, target);
		return HandlerResult.HANDLED;
	}

	static void startTimedInventoryIcon(L1PcInstance pc, int skillId, int duration) {
		if (pc.hasSkillEffect(skillId)) {
			pc.removeSkillEffect(skillId);
			SC_InventoryIcon.icoReset(pc, skillId, duration, true);
		} else {
			SC_InventoryIcon.icoNew(pc, skillId, duration, true);
		}
		applySkillEffectSec(pc, skillId, duration);
		if (skillId == L1SkillId.TITANL_RISING) pc.setRisingUp(5 + Math.clamp(pc.getLevel() - 60, 1, 5));
	}



	static void startFeatherBuff(L1PcInstance pc, int skillId, int duration) {
		final int reduction = switch (skillId) {
			case L1SkillId.FEATHER_BUFF_A -> 3;
			case L1SkillId.FEATHER_BUFF_B -> 2;
			default -> 0;
		};
		pc.applySkillModifierFromOwner(skillId, reduction);
		if (reduction != 0) sendSpMr(pc);
		else sendAttrDef(pc);
		if (skillId != L1SkillId.FEATHER_BUFF_D) {
			refreshParty(pc);
			sendHpMp(pc);
		}
		applySkillEffectSec(pc, skillId, duration);
	}

	// ===== helpers (CSkillEffectActivation 안에 같이 둬도 되고 Helper 클래스로 빼도 됨) =====

	private static void refreshParty(L1PcInstance pc) {
		try {
			if (pc.isInParty() && pc.getParty() != null) {
				pc.getParty().refreshPartyMemberStatus(pc);
			}
		} catch (Throwable ignore) {}
	}

	private static void sendHpMp(L1PcInstance pc) {
		try {
			SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		} catch (Throwable ignore) {}
	}

	private static void sendSpMr(L1PcInstance pc) {
		try {
			SC_SPMR.send(pc);
		} catch (Throwable ignore) {}
	}

	private static void sendAttrDef(L1PcInstance pc) {
		try {
			SC_OwnCharAttrDef.send(pc);
		} catch (Throwable ignore) {}
	}

	static void startEvasionBuff(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		CSkillInfo.applyFixedModifierFromOwner(pc, skillId);
		SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
		applySkillEffectSec(pc, skillId, duration);

		switch (skillId) {
		case L1SkillId.DRESS_EVASION -> sendEvasionIcon(pc, skillId, duration, 1608, 970, 2176);
		case L1SkillId.AQUA_PROTECTER -> sendEvasionIcon(pc, skillId, duration, 2342, 1085, 2201);
		case L1SkillId.SOLID_CARRIAGE -> sendEvasionIcon(pc, skillId, duration, 2351, 1087, 2203);
		case L1SkillId.FREEZEENG_ARMOR -> sendEvasionIcon(pc, skillId, duration, 9490, 5889, 0);
		case L1SkillId.SHINING_ARMOR -> sendEvasionIcon(pc, skillId, duration, 9483, 5892, 0);
		}
	}

	private static void sendEvasionIcon(L1PcInstance pc, int skillId, int duration,
			int icon, int statusMessage, int endMessage) {
		sendRestatBuffNoti(pc, skillId, duration, icon, icon, 0, statusMessage, statusMessage, endMessage, true);
	}

	// =========================
	// helpers
	// =========================
	static void startInventoryIconTimer(L1PcInstance pc, int skillId, int duration) {
		resetEffect(pc, skillId);
		applySkillEffectSec(pc, skillId, duration);
		sendIvenBuffIcon(pc, skillId, duration);
	}

	private static int weaponType1(final L1PcInstance pc) {
		if (pc == null) return -1;
		if (pc.getWeapon() == null || pc.getWeapon().getItem() == null) return -1;
		return pc.getWeapon().getItem().getType1();
	}

	private static boolean isOneOf(int v, int... arr) {
		for (int a : arr) if (v == a) return true;
		return false;
	}

	static int durationSecForBuff(final L1SkillUse su, final int skillId) {
		int d = 0;
		if (su != null) d = su.hdGetBuffIconDuration();
		if (d > 0) return d;

		try {
			final L1Skills t = SkillsTable.getInstance().getTemplate(skillId);
			if (t != null && t.getBuffDuration() > 0) return t.getBuffDuration();
		} catch (Throwable ignore) {
		}

		return 1;
	}

	private static void applySkillEffectSec(final L1PcInstance pc, final int skillId, final int durSec) {
		if (pc == null) return;
		if (durSec <= 0) return;
		pc.setSkillEffect(skillId, (int) Math.min(Integer.MAX_VALUE, durSec * 1000L));
	}

	private static void refreshPartyStatusIfNeeded(L1PcInstance pc) {
		if (pc != null && pc.isInParty() && pc.getParty() != null) {
			pc.getParty().refreshPartyMemberStatus(pc);
		}
	}

	private static void sendRestatBuffNoti(
			final L1PcInstance pc,
			final int spellId,
			final int durSec,
			final int iconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (pc == null) return;

		l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
		int notiWire_onIconId = 0;
		int notiWire_offIconId = 0;
		int notiWire_iconPriority = 0;
		int notiWire_tooltipStrId = 0;
		int notiWire_newStrId = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


		{ notiWire_bit |= 1; notiWire_notiType = eNotiType.RESTAT; }
		{ notiWire_bit |= 2; notiWire_spellId = spellId; }
		{ notiWire_bit |= 4; notiWire_duration = durSec; }
		{ notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }

		{ notiWire_bit |= 16; notiWire_onIconId = iconId; }
		{ notiWire_bit |= 32; notiWire_offIconId = iconId; }

		if (iconPriority > 0) { notiWire_bit |= 64; notiWire_iconPriority = iconPriority; }

		if (tooltipStrId > 0) { notiWire_bit |= 128; notiWire_tooltipStrId = tooltipStrId; }
		if (newStrId > 0)     { notiWire_bit |= 256; notiWire_newStrId = newStrId; }
		if (endStrId > 0)     { notiWire_bit |= 512; notiWire_endStrId = endStrId; }

		{ notiWire_bit |= 1024; notiWire_isGood = isGood; }
		SC_SPELL_BUFF_NOTI.sendFields(
		        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
		        notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId,
		        notiWire_isGood, 0, 0, 0, notiWire_bit);
	}

	static void startNoticeTimer(L1PcInstance pc, int skillId, int duration,
			int icon, int priority, int tooltip, int startMessage, int endMessage, boolean statModifier) {
		resetEffect(pc, skillId);
		if (statModifier) pc.applySkillModifierFromOwner(skillId, 0);
		applySkillEffectSec(pc, skillId, duration);
		sendRestatBuffNoti(pc, skillId, duration, icon, icon, priority, tooltip, startMessage, endMessage, true);
	}

	private static void sendRestatBuffNoti(
			final L1PcInstance pc,
			final int spellId,
			final int durSec,
			final int onIconId,
			final int offIconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (pc == null) return;

		l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eNotiType notiWire_notiType = null;
		int notiWire_spellId = 0;
		int notiWire_duration = 0;
		l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPELL_BUFF_NOTI.eDurationShowType notiWire_durationShowType = null;
		int notiWire_onIconId = 0;
		int notiWire_offIconId = 0;
		int notiWire_iconPriority = 0;
		int notiWire_tooltipStrId = 0;
		int notiWire_newStrId = 0;
		int notiWire_endStrId = 0;
		boolean notiWire_isGood = false;
		int notiWire_bit = 0;


		{ notiWire_bit |= 1; notiWire_notiType = eNotiType.RESTAT; }
		{ notiWire_bit |= 2; notiWire_spellId = spellId; }
		{ notiWire_bit |= 4; notiWire_duration = durSec; }
		{ notiWire_bit |= 8; notiWire_durationShowType = eDurationShowType.TYPE_EFF_AUTO_DAY_HOUR_MIN_SEC; }

		{ notiWire_bit |= 16; notiWire_onIconId = onIconId; }
		{ notiWire_bit |= 32; notiWire_offIconId = offIconId; }

		if (iconPriority > 0) { notiWire_bit |= 64; notiWire_iconPriority = iconPriority; }

		if (tooltipStrId > 0) { notiWire_bit |= 128; notiWire_tooltipStrId = tooltipStrId; }
		if (newStrId > 0)     { notiWire_bit |= 256; notiWire_newStrId = newStrId; }
		if (endStrId > 0)     { notiWire_bit |= 512; notiWire_endStrId = endStrId; }

		{ notiWire_bit |= 1024; notiWire_isGood = isGood; }
		SC_SPELL_BUFF_NOTI.sendFields(
		        pc, notiWire_notiType, notiWire_spellId, notiWire_duration, notiWire_durationShowType, notiWire_onIconId,
		        notiWire_offIconId, notiWire_iconPriority, notiWire_tooltipStrId, notiWire_newStrId, notiWire_endStrId,
		        notiWire_isGood, 0, 0, 0, notiWire_bit);
	}

	private static void sendRestatBuffNotiDualIcon(
			final L1PcInstance pc,
			final int spellId,
			final int durSec,
			final int onIconId,
			final int offIconId,
			final int iconPriority,
			final int tooltipStrId,
			final int newStrId,
			final int endStrId,
			final boolean isGood
			) {
		if (pc == null) return;
		sendRestatBuffNoti(pc, spellId, durSec, onIconId, offIconId, iconPriority, tooltipStrId, newStrId, endStrId, isGood);
	}

private static void sendIvenBuffIcon(final L1PcInstance pc, final int skillId, final int durSec) {
		if (pc == null) return;

		SC_IvenBuffIcon.send(pc, skillId, true, 0, durSec);
	}



	// =====================================================
	// 원본 L1SkillUse 누락 이관 handlers
	// 기존 handler 본문은 수정하지 않음
	// =====================================================

	private static void registerNpcBossEffects(int id, int firstTemplate, boolean instant, SkillHandler... actions) {
		CSkillInfo.registerCast(id, (su, target, magic, undeadType, targets) -> {
			if (target == null || !(su.hdUser() instanceof L1NpcInstance npc)) return HandlerResult.HANDLED;
			final L1Npc template = npc.getNpcTemplate();
			if (template == null || template.get_npcId() < firstTemplate || template.get_npcId() > firstTemplate + 2)
				return HandlerResult.HANDLED;
			for (SkillHandler action : actions) {
				final HandlerResult result = action.handle(su, target, magic, undeadType, targets);
				if (result != HandlerResult.HANDLED) return result;
			}
			return HandlerResult.HANDLED;
		}, instant);
	}

	private static HandlerResult cancelBossTargetEffects(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target instanceof L1PcInstance pc) applyBossCancellation(su, pc, 870, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult antarasParalysisRoll(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		// Keep the original RNG consumption even when the roll cannot inflict paralysis.
		final int roll = su.hdRandom().nextInt(5) + 1;
		if (target instanceof L1PcInstance pc && roll > 10) ParalysisPoison.doInfection(pc, su.hdSkillId());
		return HandlerResult.HANDLED;
	}

	private static HandlerResult antarasDamagePoison(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final int damage = su.hdRandom().nextInt(50) + 1;
		final int seconds = su.hdRandom().nextInt(15) + 1;
		if (target instanceof L1PcInstance pc && seconds > 2) {
			final L1Character primary = su.hdTarget() != null ? su.hdTarget() : target;
			DamagePoison.doInfection(pc, primary, seconds * 1000, damage, true, 7000);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult antarasStun(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final int duration = 4500 + 500 * su.hdRandom().nextInt(3);
		su.hdSetShockStunDuration(duration);
		final L1Character primary = su.hdTarget() != null ? su.hdTarget() : target;
		final int effectDuration = primary.hasSkillEffect(L1SkillId.리버레이션)
				? (int) (duration * Config.리버레이션절감) : duration;
		if ((target instanceof L1MonsterInstance || target instanceof L1SummonInstance
				 || target instanceof L1CompanionInstance)
				&& !admitNpcControl(su, (L1NpcInstance) target, duration)) return HandlerResult.CONTINUE;
		L1EffectSpawn.getInstance().spawnEffect(81162, effectDuration, target.getX(), target.getY(), target.getMapId());
		if (target instanceof L1PcInstance pc) SC_Paralysis.send(pc, SC_Paralysis.TYPE_STUN, true);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult antarasWeaponBreak(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		applyBossWeaponDamage(su, target, 3, false, false);
		return HandlerResult.HANDLED;
	}

	static void startBattleScroll(L1PcInstance pc, int skillId, int iconDuration) {
		if (pc.hasSkillEffect(skillId)) return;
		pc.applySkillModifierFromOwner(skillId, 0);
		SC_SPMR.send(pc);
		SC_OwnCharAttrDef.send(pc);
		sendRestatBuffNoti(pc, skillId, iconDuration, 9151, 0, 5021, 5022, 5023, true);
		applySkillEffectSec(pc, skillId, 300);
	}

	private static HandlerResult onBlackElderDeathHell(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final int chance =
				su.hdRandom().nextInt(100) + 1;

		applyTimedBossDebuff(
				cha,
				7780,
				L1SkillId.PAP_DEATH_HELL);

		if (chance > 33
				&& cha instanceof L1PcInstance) {

			final L1PcInstance pc =
					(L1PcInstance) cha;

			applyBossCancellation(
					su,
					pc,
					su.hdSkillCastGfx(),
					false);

			// 원본은 캔슬 후 870을 한 번 더 전송.
			sendSkillSoundBroadcast(
					pc,
					870);
		}

		return HandlerResult.HANDLED;
	}


	private static void registerBossDamage(int id, int sound, boolean playerOnlySound) {
		CSkillInfo.registerInstant(id, (su, target, magic, undeadType, targets) -> {
			if (target == null || magic == null) return HandlerResult.HANDLED;
			if (!playerOnlySound) SC_Sound.sendAndBroadcastFast(target, sound);
			else if (target instanceof L1PcInstance) sendSkillSoundBroadcast(target, sound);
			su.hdSetDamage(magic.calcMagicDamage(id));
			return HandlerResult.HANDLED;
		});
	}

	private static HandlerResult castMilpitasSummon(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target == null || magic == null) return HandlerResult.HANDLED;
		SC_Sound.sendAndBroadcastFast(target, 11009);
		SC_Sound.sendAndBroadcastFast(target, 11006);
		SC_SkillSound.sendAndBroadcast(target, 11473);
		if (target instanceof L1PcInstance pc) L1SpawnUtil.spawn(pc, 181163, 5, 6 * 2000, true);
		su.hdSetDamage(magic.calcMagicDamage(L1SkillId.TROGIR_MILPITAS2));
		return HandlerResult.HANDLED;
	}

	private static HandlerResult onConcentration(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (cha == null) return HandlerResult.HANDLED;

		final L1Character user = su.hdUser();
		if (user instanceof L1PcInstance) {
			L1PinkName.onHelp(cha, user);
		}

		return HandlerResult.HANDLED;
	}
	
	static void startMetisBlessScroll(L1PcInstance pc, int skillId, int duration) {
		if (pc.hasSkillEffect(skillId)) pc.removeSkillEffect(skillId);
		pc.applySkillModifierFromOwner(skillId, 0);
		SC_SPMR.send(pc);
		SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
		SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
		applySkillEffectSec(pc, skillId, duration);
	}
	
	static void startSpecialBuff(L1PcInstance pc, int skillId, int iconDuration) {
		if (pc.isDead()) return;
		if (pc.hasSkillEffect(skillId)) pc.removeSkillEffect(skillId);
		if (skillId != L1SkillId.BUFF_특별한성장축복) {
			pc.applySkillModifierFromOwner(skillId, 0);
			if (skillId == L1SkillId.BUFF_특별한방어축복) SC_HpUpdate.send(pc, pc.getCurrentHp(), pc.getMaxHp());
			else SC_MpUpdate.send(pc, pc.getCurrentMp(), pc.getMaxMp());
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
		}
		SC_InventoryIcon.icoNew(pc, skillId, iconDuration, true);
		applySkillEffectSec(pc, skillId, iconDuration > 0 ? iconDuration : durationSecForBuff(null, skillId));
	}









	private static HandlerResult castDesertParalysis(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc) || pc.hasSkillEffect(L1SkillId.EARTH_BIND)
				|| pc.hasSkillEffect(L1SkillId.ICE_LANCE) || pc.hasSkillEffect(L1SkillId.DESERT_SKILL1)
				|| pc.hasSkillEffect(L1SkillId.DESERT_SKILL2)) return HandlerResult.HANDLED;
		if (pc.isDead()) return HandlerResult.CONTINUE;
		if (su.hdSkillId() == L1SkillId.DESERT_SKILL1) {
			CurseParalysis.curse(pc, L1SkillId.DESERT_SKILL1);
		} else {
			pc.setSkillEffect(L1SkillId.EARTH_BIND, 12_000);
			SC_Poison.send(pc, pc.getId(), 2);
			SC_Poison.broadcastFast(pc, pc.getId(), 2);
			SC_Paralysis.send(pc, SC_Paralysis.TYPE_FREEZE, true);
			sendSkillSoundBroadcast(pc, 2251);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult castDesertManaDrain(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target == null) return HandlerResult.HANDLED;
		final int drain = su.hdRandom().nextInt(20);
		if (target instanceof L1PcInstance pc) {
			if (pc.getCurrentMp() <= drain || pc.isDead()) return HandlerResult.CONTINUE;
			pc.setCurrentMp(pc.getCurrentMp() - drain);
			sendSkillSoundBroadcast(pc, 2172);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult castDesertPoison(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target == null) return HandlerResult.HANDLED;
		final int seconds = su.hdRandom().nextInt(5) + 1;
		if (target instanceof L1PcInstance && seconds > 2) {
			DamagePoison.doInfection(su.hdUser(), target, seconds * 1000, 500, true, 7000);
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult castDesertDebuffs(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc)) return HandlerResult.HANDLED;

		if (pc.isDead() || pc.hasSkillEffect(L1SkillId.CURSE_PARALYZE)) return HandlerResult.CONTINUE;
		CurseParalysis.curse(pc, su.hdSkillId());
		pc.setSkillEffect(L1SkillId.CURSE_PARALYZE, 4_000);
		sendSkillSoundBroadcast(pc, 10704);

		if (pc.isDead() || pc.hasSkillEffect(L1SkillId.DECAY_POTION)) return HandlerResult.CONTINUE;
		pc.setSkillEffect(L1SkillId.DECAY_POTION, 16_000);
		sendSkillSoundBroadcast(pc, 2232);
		if (!applyDesertBlindness(pc)) return HandlerResult.CONTINUE;

		if (!applyDesertStatDebuff(pc, L1SkillId.DISEASE, -64, 2230)) return HandlerResult.CONTINUE;
		return applyDesertStatDebuff(pc, L1SkillId.WEAKNESS, 64, 2228)
				? HandlerResult.HANDLED : HandlerResult.CONTINUE;
	}

	private static boolean applyDesertStatDebuff(L1PcInstance pc, int id, int duration, int sound) {
		if (pc.isDead() || pc.hasSkillEffect(id) || !pc.submitSkillPumpStart(id, duration)) return false;
		sendSkillSoundBroadcast(pc, sound);
		return true;
	}

	private static HandlerResult castDesertBlindness(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		return !(target instanceof L1PcInstance pc) || applyDesertBlindness(pc) ? HandlerResult.HANDLED : HandlerResult.CONTINUE;
	}

	private static boolean applyDesertBlindness(L1PcInstance pc) {
		if (pc.isDead() || pc.hasSkillEffect(L1SkillId.DARKNESS)) return false;
		SC_CurseBlind.send(pc, pc.hasSkillEffect(L1SkillId.STATUS_FLOATING_EYE) ? 2 : 1);
		pc.setSkillEffect(L1SkillId.DARKNESS, 32_000);
		sendSkillSoundBroadcast(pc, 2175);
		return true;
	}

	private static HandlerResult castDesertSleep(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target == null) return HandlerResult.HANDLED;
		final int id = L1SkillId.FOG_OF_SLEEPING;
		if (target.isDead() || target.hasSkillEffect(id)) return HandlerResult.CONTINUE;
		if (target instanceof L1PcInstance pc) {
			if (!submitContextBuff(pc, id, 32_000, 1, 0, null)) return HandlerResult.CONTINUE;
		} else if (target instanceof L1NpcInstance npc) {
			if (!npc.trySetSkillEffect(id, 32_000)) return HandlerResult.CONTINUE;
		} else {
			target.setSkillEffect(id, 32_000);
		}
		return HandlerResult.HANDLED_WITHOUT_TIMER;
	}

	private static HandlerResult castDesertSound(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target instanceof L1PcInstance pc) sendSkillSoundBroadcast(pc, 10082);
		return HandlerResult.HANDLED;
	}

	private static HandlerResult castDesertSummon(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (target == null || !(su.hdUser() instanceof L1NpcInstance npc)) return HandlerResult.HANDLED;
		if (su.hdSkillId() == L1SkillId.DESERT_SKILL9) {
			for (int wave = 0; wave < 4; wave++) {
				for (int npcId = 5138; npcId <= 5145; npcId++) L1SpawnUtil.spawn(npc, npcId, 6, 120_000);
			}
		} else {
			final int count = su.hdRandom().nextInt(3) + 1;
			for (int wave = 0; wave < count; wave++) L1SpawnUtil.spawn(npc, 5095, 6, 3000);
		}
		return HandlerResult.HANDLED;
	}

	static void startDragonSet(L1PcInstance pc, int skillId, int duration) {
		if (pc.hasSkillEffect(skillId)) {
			SC_InventoryIcon.icoReset(pc, skillId, 5016, 600L, true);
		} else {
			pc.applySkillModifierFromOwner(skillId, 0);
			SC_SPMR.send(pc);
			SC_OwnCharAttrDef.send(pc);
			SC_InventoryIcon.icoNew(pc, skillId, 5016, 600L, true);
		}
		applySkillEffectSec(pc, skillId, duration);
	}

	private static HandlerResult onDrakeMassTeleport(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		if (pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		final L1Location newLocation =
				pc.getLocation()
						.randomLocation(
								5,
								true);

		pc.start_teleport(
				newLocation.getX(),
				newLocation.getY(),
				pc.getMapId(),
				pc.getHeading(),
				169,
				true,
				true);

		return HandlerResult.HANDLED;
	}


	private static HandlerResult onEfreetSummon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		summonBossMinions(
				su,
				5121);

		return HandlerResult.HANDLED;
	}

	static void startEventIconBuff(L1PcInstance pc, int skillId, int duration) {
		final int tooltip = switch (skillId) {
			case L1SkillId.DRAGON_HUNTER_BLESS -> 5016;
			case L1SkillId.HUNTER_BLESS -> 4992;
			case L1SkillId.HUNTER_BLESS2 -> 993;
			default -> throw new IllegalArgumentException("Unknown hunting effect " + skillId);
		};
		if (pc.hasSkillEffect(skillId)) SC_InventoryIcon.icoReset(pc, skillId, tooltip, 1800L, true);
		else SC_InventoryIcon.icoNew(pc, skillId, tooltip, 1800L, true);
		applySkillEffectSec(pc, skillId, duration);
	}

	private static HandlerResult summonPapMinions(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		final L1Character source = su.hdUser();
		for (int wave = 0; wave < 2; wave++) {
			for (int npcId = 900049; npcId <= 900052; npcId++) {
				final int duration = npcId == 900052 ? 120_000 : 60_000;
				L1SpawnUtil.spawn2(source.getX(), source.getY(), (short) source.getMap().getId(), npcId, 8, duration, 0);
			}
		}
		return HandlerResult.HANDLED;
	}

	private static HandlerResult papWeaponBreak(L1SkillUse su, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		applyBossWeaponDamage(su, target, 2, true, false);
		return HandlerResult.HANDLED;
	}

	private static SkillHandler timedBossDebuff(int sound, int debuffId) {
		return (su, target, magic, undeadType, targets) -> {
			applyTimedBossDebuff(target, sound, debuffId);
			return HandlerResult.HANDLED;
		};
	}

	private static HandlerResult onDragonCancellation(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (su.hdRandom().nextInt(100) + 1 > 33
				&& cha instanceof L1PcInstance) {

			applyBossCancellation(
					su,
					(L1PcInstance) cha,
					870,
					true);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onPhoenixSummon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		summonBossMinions(
				su,
				900177);

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorBow(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		// 원본은 cha가 아니라 primary _target 기준.
		final L1Character primary =
				su.hdTarget();

		if (!(primary instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) primary;

		if (pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		final L1Character user =
				su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		int sprNum = 0;
		int pcX = pc.getX();
		int pcY = pc.getY();

		switch (npc.getNpcTemplate().get_npcId()) {
		case 5097:
			pcY -= 6;
			sprNum = 7987;
			break;

		case 5098:
			pcX += 4;
			pcY -= 4;
			sprNum = 8050;
			break;

		case 5099:
			pcX += 5;
			sprNum = 8051;
			break;

		default:
			break;
		}

		final long packet = SC_EffectLocation.pack(
						pcX,
						pcY,
						sprNum);

		SC_EffectLocation.sendPacked(pc, packet);
		SC_EffectLocation.broadcastFastPacked(pc, packet);

		return HandlerResult.HANDLED;
	}


	private static HandlerResult onRindviorPredicate(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1Character user = su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		if (npc.getLocation()
				.getTileLineDistance(
						pc.getLocation()) > 4) {

			for (int count = 0;
					count < 10;
					count++) {

				final L1Location newLoc =
						npc.getLocation()
								.randomLocation(
										3,
										4,
										false);

				if (npc.glanceCheck(
						newLoc.getX(),
						newLoc.getY())) {

					pc.start_teleport(
							newLoc.getX(),
							newLoc.getY(),
							npc.getMapId(),
							pc.getHeading(),
							169,
							true,
							true);

					break;
				}
			}
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorPredicateCancellation(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		if (su.hdRandom().nextInt(100) + 1 <= 33) {
			return HandlerResult.HANDLED;
		}

		if (!(cha instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1Character user =
				su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) cha;

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		if (npc.getLocation()
				.getTileLineDistance(
						pc.getLocation()) > 4) {

			for (int count = 0;
					count < 10;
					count++) {

				final L1Location newLoc =
						npc.getLocation()
								.randomLocation(
										3,
										4,
										false);

				if (npc.glanceCheck(
						newLoc.getX(),
						newLoc.getY())) {

					pc.start_teleport(
							newLoc.getX(),
							newLoc.getY(),
							npc.getMapId(),
							5,
							169,
							true,
							true);

					break;
				}
			}
		}

		applyBossCancellation(
				su,
				pc,
				870,
				true);

		return HandlerResult.HANDLED;
	}

	static HandlerResult castRindviorSilence(L1SkillUse cast, L1Character target, L1Magic magic, int undeadType, Iterator<?> targets) {
		if (!(target instanceof L1PcInstance pc)) return HandlerResult.HANDLED;
		if (pc.isDead()) return HandlerResult.CONTINUE;
		return submitContextBuff(pc, cast.hdSkillId(), DurationResolver.INSTANCE.resolveDurationMs(cast), 0, 0, null)
				? HandlerResult.HANDLED_WITHOUT_TIMER : HandlerResult.CONTINUE;
	}

	static void startRindviorSilence(L1PcInstance pc, int id, int durationMs, int option, int gfx, boolean partyMember) {
		if (pc.isDead()) return;
		pc.skillEffectSetFromOwner(L1SkillId.SILENCE, 12_000);
		sendSkillSoundBroadcast(pc, 2177);
		if (durationMs > 0) pc.skillEffectSetFromOwner(id, durationMs);
	}

	private static HandlerResult onRindviorSummon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final L1Character user = su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		final int[] mobIds = {
				5106,
				5107,
				5108,
				5109
		};

		final int rnd =
				su.hdRandom().nextInt(100);

		final int count =
				su.hdRandom().nextInt(2) + 1;

		for (int i = 0; i < count; i++) {
			L1SpawnUtil.spawn(
					npc,
					mobIds[rnd % mobIds.length],
					su.hdRandom().nextInt(3) + 8);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorSummonCloud(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		final L1Character user = su.hdUser();

		if (user instanceof L1NpcInstance) {
			L1SpawnUtil.spawn(
					(L1NpcInstance) user,
					5110,
					10);
		}

		return HandlerResult.HANDLED;
	}

	private static HandlerResult onRindviorWeapon(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {

		return applyBossWeaponDamage(
				su,
				cha,
				3,
				true,
				true);
	}


	private static void applyBossCancellation(
			L1SkillUse su,
			L1PcInstance pc,
			int soundGfx,
			boolean preserveChatProhibited) {

		if (pc == null) return;

		sendSkillSoundBroadcast(
				pc,
				soundGfx);

		// 일반 스킬 효과
		for (int skillNum = su.hdSkillsBegin();
				skillNum <= su.hdSkillsEnd();
				skillNum++) {

			if (SkillUseHelper.isNotCancelable(skillNum)
					&& !pc.isDead()) {
				continue;
			}

			// 랭킹 변신 / 폴리링 보호
			if (skillNum == L1SkillId.SHAPE_CHANGE) {

				if (pc.getRankLevel() >= 9
						&& PolyTable.getInstance()
								.isRankingPoly(
										pc.getCurrentSpriteId())) {
					continue;
				}

				if (pc.hasSkillEffect(L1SkillId.POLY_RING_MASTER)) {
					continue;
				}
			}

			pc.removeSkillEffect(
					skillNum);
		}

		// 상태 효과
		for (int skillNum = su.hdStatusBegin();
				skillNum <= su.hdStatusEnd();
				skillNum++) {

			if (preserveChatProhibited
					&& skillNum
							== L1SkillId.STATUS_CHAT_PROHIBITED) {
				continue;
			}

			pc.removeSkillEffect(
					skillNum);
		}

		// 요리
		for (int skillNum = su.hdCookingBegin();
				skillNum <= su.hdCookingEnd();
				skillNum++) {

			if (SkillUseHelper.isNotCancelable(skillNum)
					&& !pc.isDead()) {
				continue;
			}

			pc.removeSkillEffect(
					skillNum);
		}

		pc.curePoison();
		pc.cureParalaysis();

		// 랭킹 변신 보호. 원본 그대로.
		if (!(pc.getRankLevel() >= 9
				&& PolyTable.getInstance()
						.isRankingPoly(
								pc.getCurrentSpriteId()))) {

			L1PolyMorph.undoPoly(pc);

			SC_CharVisualUpdate.sendPacked(pc, SC_CharVisualUpdate.pack(pc));

			SC_CharVisualUpdate.broadcastFastPacked(pc, SC_CharVisualUpdate.pack(pc));
		}

		if (pc.getHasteItemEquipped() > 0) {
			pc.setMoveSpeed(0);

			SC_SkillHaste.send(pc, pc.getId(), 0, 0);

			SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
		}

		removeBossInvisibility(pc);

		pc.removeSkillEffect(
				L1SkillId.STATUS_FREEZE);

		/*
		 * 원본 getTreePotionBuff(pc)는
		 * 현재 원본에서도 본문 전체가 주석이라 실제 동작 없음.
		 */
	}

	private static HandlerResult applyBossWeaponDamage(
			L1SkillUse su,
			L1Character target,
			int maxDamage,
			boolean chanceCheck,
			boolean continueIfDead) {

		if (!(target instanceof L1PcInstance)) {
			return HandlerResult.HANDLED;
		}

		final L1PcInstance pc =
				(L1PcInstance) target;

		final L1ItemInstance weapon =
				pc.getWeapon();

		if (weapon == null) {
			return HandlerResult.HANDLED;
		}

		if (chanceCheck
				&& su.hdRandom().nextInt(100) + 1 <= 33) {
			return HandlerResult.HANDLED;
		}

		if (continueIfDead
				&& pc.isDead()) {
			return HandlerResult.CONTINUE;
		}

		final int weaponDamage =
				su.hdRandom()
						.nextInt(maxDamage) + 1;

		SC_ServerMessage.send(pc, 268, weapon.getLogName());

		pc.getInventory()
				.receiveDamage(
						weapon,
						weaponDamage);

		sendSkillSoundBroadcast(
				pc,
				172);

		return HandlerResult.HANDLED;
	}

	private static void applyTimedBossDebuff(
			L1Character target,
			int soundId,
			int effectId) {

		if (!(target instanceof L1PcInstance)) {
			return;
		}

		final L1PcInstance pc =
				(L1PcInstance) target;

		sendSkillSoundBroadcast(
				pc,
				soundId);

		pc.setSkillEffect(
				effectId,
				12 * 1000);
	}

	private static void removeBossInvisibility(
			L1PcInstance pc) {

		if (pc == null
				|| !pc.isInvisble()) {
			return;
		}

		if (pc.hasSkillEffect(
				L1SkillId.INVISIBILITY)) {

			pc.setGmInvis(false);

			pc.killSkillEffectTimer(
					L1SkillId.INVISIBILITY);

			SC_Invis.send(pc, 
							pc.getId(),
							0);

			SC_Invis.broadcastFast(pc, 
							pc.getId(),
							0);

			SC_Sound.send(pc, 147);
		}

		if (pc.hasSkillEffect(
				L1SkillId.BLIND_HIDING)) {

			pc.setGmInvis(false);

			pc.killSkillEffectTimer(
					L1SkillId.BLIND_HIDING);

			SC_Invis.send(pc, 
							pc.getId(),
							0);

			SC_Invis.broadcastFast(pc, 
							pc.getId(),
							0);
		}
	}

	private static void summonBossMinions(
			L1SkillUse su,
			int npcId) {

		final L1Character user =
				su.hdUser();

		if (!(user instanceof L1NpcInstance)) {
			return;
		}

		final L1NpcInstance npc =
				(L1NpcInstance) user;

		for (int i = 0; i < 2; i++) {
			L1SpawnUtil.spawn(
					npc,
					npcId,
					su.hdRandom()
							.nextInt(3) + 8);
		}
	}

	private static HandlerResult onOmanCancellation(
			L1SkillUse su, L1Character cha, L1Magic magic,
			int undeadType, Iterator<?> iter) {
		if (cha instanceof L1PcInstance) {
			applyBossCancellation(su, (L1PcInstance) cha, 870, true);
		}
		return HandlerResult.HANDLED;
	}

	static void startLegacyBlessing(L1PcInstance pc, int skillId, int duration) {
		pc.applySkillModifierFromOwner(skillId, 0);
		applySkillEffectSec(pc, skillId, duration);
	}

	static void startStrongmanBuff(L1PcInstance pc, int skillId, int duration) {
		if (pc.hasSkillEffect(skillId)) pc.removeSkillEffect(skillId);
		pc.applySkillModifierFromOwner(skillId, 0);
		SC_PacketBox.send(pc, SC_PacketBox.ICON_COOKING, pc, 187, 1800);
		pc.setDessertId(skillId);
		SC_ServerMessage.send(pc, 1426);
		applySkillEffectSec(pc, skillId, duration);
	}

	private static void registerSandWormCue(int id, int gfx) {
		CSkillInfo.registerInstant(id, (su, target, magic, undeadType, targets) -> {
			final L1PcInstance player = su.hdPlayer();
			final L1Character caster = su.hdUser();
			if (player == null || caster == null) return HandlerResult.HANDLED;
			SC_UseAttackSkill.broadcastPair(caster, caster, player, player.getId(), gfx,
					player.getX(), player.getY(), ActionCodes.ACTION_Attack, false);
			SC_SkillSound.send(player, player.getId(), gfx);
			return HandlerResult.HANDLED;
		});
	}


	// ===== common small utilities (dup 제거) =====
	private static void resetEffect(final L1Character cha, final int skillId) {
		if (cha == null) return;
		if (cha.hasSkillEffect(skillId)) cha.removeSkillEffect(skillId);
	}

	private static void sendSkillSoundBroadcast(final L1Character cha, final int gfx) {
		if (cha == null) return;
		final long snd = SC_SkillSound.pack(cha.getId(), gfx);

		// PC/NPC는 sendPackets가 존재 (캐스팅 없이도 안전하게 처리)
		if (cha instanceof L1PcInstance) {
			SC_SkillSound.sendPacked(((L1PcInstance) cha), snd);
		} 
		SC_SkillSound.broadcastFastPacked(cha, snd);
	}


	private static void sendSkillSoundOnce(final L1PcInstance pc, final int gfx) {
		if (pc == null) return;
		final long snd = SC_SkillSound.pack(pc.getId(), gfx);
		SC_SkillSound.sendPacked(pc, snd);
		SC_SkillSound.broadcastFastPacked(pc, snd);
	}

	private static final int[] ELEM_LESSER = { 45306, 45303, 45304, 45305 };

	private static final int[] ELEM_GREATER = { 460000355, 460000355, 460000355, 460000355 };

	private static final int[] MYTH_SPRITES = { 21070, 21053, 21066, 21049, 21140, 19514 };

	private static boolean isOneOfSprite(int sprite, int[] arr) {
		for (int v : arr) if (v == sprite) return true;
		return false;
	}

	private static boolean canRecallElemental(final L1PcInstance pc) {
		if (pc == null) return false;
		return ((pc.getMap().isRecallPets() && !pc.isInWarArea()) || pc.isGm());
	}

	private static int countSummons(final L1PcInstance pc) {
		if (pc == null) return 0;
		int c = 0;
		try {
			for (Object pet : pc.getPetList().values()) {
				if (pet instanceof L1SummonInstance) c += 1;
			}
		} catch (Throwable t) {
			// ignore
		}
		return c;
	}

	private static void deleteAllSummons(final L1PcInstance pc) {
		if (pc == null) return;
		try {
			for (Object pet : pc.getPetList().values()) {
				if (pet instanceof L1SummonInstance) {
					((L1NpcInstance) pet).deleteMe();
				}
			}
		} catch (Throwable t) {
			// ignore
		}
	}

	private static int[] pickElementalCandidates(final int skillId, final boolean mythPoly) {
		if (skillId == L1SkillId.LESSER_ELEMENTAL) {
			return ELEM_LESSER;
		}
		// GREATER_ELEMENTAL
		if (mythPoly) {
			return ELEM_GREATER;
		} else {
			return ELEM_GREATER;
		}
	}

	private static int pickSummonIdByAttr(final Random rng, final int[] candidates, final int elfAttr) {
		if (candidates == null || candidates.length == 0) return 0;

		int summonId = 0;
		int npcattr = 1;
		for (int i = 0; i < candidates.length; i++) {
			if (npcattr == elfAttr) {
				summonId = candidates[i];
				break;
			}
			npcattr *= 2;
		}

		if (summonId == 0) {
			summonId = candidates[rng.nextInt(candidates.length)];
		}
		return summonId;
	}

	private static void resetScalesEffectIfPresent(final L1PcInstance pc, final int skillId) {
		if (pc == null) return;
		if (!pc.hasSkillEffect(skillId)) return;

		switch (skillId) {
		case L1SkillId.SCALES_WATER_DRAGON:
			pc.removeSkillModifierFromOwner(skillId);
			SC_PacketBox.send(pc, SC_PacketBox.ER_UpDate, pc.getTotalER());
			break;
		case L1SkillId.SCALES_EARTH_DRAGON:
			pc.removeSkillModifierFromOwner(skillId);
			SC_OwnCharAttrDef.send(pc);
			SC_SPMR.send(pc);
			break;
		case L1SkillId.SCALES_RINDVIOR_DRAGON:
			pc.removeSkillModifierFromOwner(skillId);
			break;
		case L1SkillId.SCALES_FIRE_DRAGON:
			pc.removeSkillModifierFromOwner(skillId);
			break;
		}
		pc.killSkillEffectTimer(skillId);
	}

	private static void scheduleMultiHit(final L1PcInstance attacker, final L1Character target, final int hits, 
            final int intervalMs, final int maxRange, final MultiHitHook hook) {

        if (attacker == null || target == null || hits <= 0) {
            return;
        }

        final int token = attacker.newMultiHitToken();
        scheduleMultiHitWithToken(attacker, target, hits, intervalMs, maxRange, token, hook);
    }

    // =====================================================
    // token fixed multi-hit
    // Runnable 1개만 생성해서 순차 재사용
    // =====================================================
    private static void scheduleMultiHitWithToken(final L1PcInstance attacker, final L1Character target, 
            final int hits, final int intervalMs, final int maxRange, final int token, final MultiHitHook hook) {

        if (attacker == null || target == null || hits <= 0) {
            return;
        }

        if (attacker.isDead() || target.isDead()) {
            return;
        }

        final int attackerId = attacker.getId();
        final int targetId = target.getId();
        final short mapId = attacker.getMapId();

        final int delay = Math.max(0, intervalMs);
        final MultiHitHook h = hook != null ? hook : MultiHitHook.NONE;
        final long startNanos = System.nanoTime();

        final Runnable task = new Runnable() {
            private int hitIndex;

            @Override
            public void run() {
                boolean cancelled = false;

                try {
                    final CWorld world = CWorld.getInstance();
                    final L1Object attackerObj = world.findObject(attackerId);

                    if (!(attackerObj instanceof L1PcInstance)) {
                        cancelled = true;
                        return;
                    }

                    final L1PcInstance a = (L1PcInstance) attackerObj;

                    if (a.getMultiHitToken() != token) {
                        cancelled = true;
                        return;
                    }

                    final L1Object targetObj = world.findObject(targetId);

                    if (!(targetObj instanceof L1Character)) {
                        cancelled = true;
                        return;
                    }

                    final L1Character t = (L1Character) targetObj;

                    if (a.isDead() || t.isDead()) return;
                    if (a.getMapId() != mapId || t.getMapId() != mapId) return;

                    if (maxRange != Integer.MAX_VALUE) {
                        final int dx = Math.abs(a.getX() - t.getX());
                        final int dy = Math.abs(a.getY() - t.getY());

                        if (Math.max(dx, dy) > maxRange) {
                            return;
                        }
                    }
                    h.onHit(a, t, hitIndex, hits, true);
                    t.onAction(a);
                    h.onHit(a, t, hitIndex, hits, false);

                } catch (Throwable ignore) {
                } finally {
                    if (!cancelled) {
                        scheduleNext();
                    }
                }
            }

            private void scheduleNext() {
                final int nextHit = ++hitIndex;

                if (nextHit >= hits) {
                    return;
                }
                final long targetNanos = startNanos + ((long) nextHit * (long) delay * 1_000_000L);
                final long remainNanos = targetNanos - System.nanoTime();
                final long nextDelayMs;

                if (remainNanos <= 0L) {
                    nextDelayMs = 0L;
                } else {
                    nextDelayMs = (remainNanos + 999_999L) / 1_000_000L;
                }

                if (nextDelayMs <= 0L) {
                    CWorldWork.execute(this);
                } else {
                    CWorldWork.scheduleRequired(this, nextDelayMs);
                }
            }
        };

        CWorldWork.execute(task);
    }
}
