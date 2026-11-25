package l1j.server.server.model.item;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.eKind;

/**
 * 아이템 보너스 스탯 — 장착 적용(apply)과 툴팁 opcode를 한 곳에 묶음.
 *
 * WType = 툴팁 write 방식
 *   NONE   : 툴팁 미표기
 *   B      : writeC(opcode) + writeC(value)
 *   H      : writeC(opcode) + writeH(value)
 *   AC     : AC 전용 라인(별도 섹션)으로 표기
 *   POTION : 물약회복 라인 (양+율 같은 값 표기)
 *   HEAL   : 악화방어 라인
 */
public enum ItemStat {

    AC         (2,  WType.AC)     { @Override public void apply(L1PcInstance p, int v) { p.addAc(v); } },
    MAX_HP     (14, WType.H)      { @Override public void apply(L1PcInstance p, int v) { p.addMaxHp(v); } },
    MAX_MP     (32, WType.H)      { @Override public void apply(L1PcInstance p, int v) { p.addMaxMp(v); } },
    DMG_RED    (63, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addDamageReductionByArmor(v); } },
    HIT        (48, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addHitup(v); } },
    DMG_RATE   (47, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addDmgRate(v); } },
    BOW_DMG    (35, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addBowDmgRate(v); } },
    MPR        (38, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addMpr(v); } },
    SP         (17, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.AddStatSp(v); } },
    WEIGHT     (68, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addWeightReduction(v); } },
    MR         (15, WType.H)      { @Override public void apply(L1PcInstance p, int v) { p.AddMDefend(v); } },
    BOW_HIT    (24, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addBowHitup(v); } },
	MAGIC_HIT  (40, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.addBaseMagicHitUp(v); } },
	PVP_DMG    (59, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.AddPvpDamage(v); } },
	PVP_DMG_RED    (60, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.AddPvpDamageReduction(v); } },
	
	
	PVP_MAGIC_DMG_RED   (135, WType.B)      { @Override public void apply(L1PcInstance p, int v) { p.AddPvpDamageReduction(v); } },

	BLESS_CONSUMO(116, WType.H)     { @Override public void apply(L1PcInstance p, int v) { p.addEinhasadBlessper(v); } },	
	
	ABILITY_RESIS(117, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialResistance(eKind.ABILITY, v); } },	
	SPIRIT_RESIS (118, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialResistance(eKind.SPIRIT, v); } },	
	DRAGON_SPELL_RESIS (119, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialResistance(eKind.DRAGON_SPELL, v); } },	
	FEAR_RESIS (120, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialResistance(eKind.FEAR, v); } },	
	ALL_RESIS  (121, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialResistance(eKind.ALL, v); } },
	
	ABILITY_PLERCE (122, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialPierce(eKind.ABILITY, v); } },	
	SPIRIT_PLERCE (123, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialPierce(eKind.SPIRIT, v); } },	
	DRAGON_SPELL_PLERCE (124, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialPierce(eKind.DRAGON_SPELL, v); } },
	FEAR_PLERCE (125, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialPierce(eKind.FEAR, v); } },
	ALL_PLERCE (126, WType.B)     { @Override public void apply(L1PcInstance p, int v) { p.AddSpecialPierce(eKind.ALL, v); } },
	EXP_RATE (36, WType.B)     { @Override public void apply(L1PcInstance p, int v) {  } },
	
    POTION_AMT (65,  WType.POTION) { @Override public void apply(L1PcInstance p, int v) { p.AddPotionRecAmt(v); p.AddPotionRecRate(v);} },
    BASE_MAGIC_CRITICAL(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.addBaseMagicCritical(v); } },
    BOW_DAMAGE_UP(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.addBowDmgup(v); } },
    DMG_RED_IGNORE(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.addDamageReductionIgnore(v); } },
    MELEE_DAMAGE_UP(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.addDmgup(v); } },
    EVASION(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.addEffectedER(v); } },
    CHARACTER_SP(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.addSp(v); } },
    MAGIC_CRITICAL_RATE(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.add_magic_critical_rate(v); } },
    MELEE_CRITICAL_RATE(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.add_melee_critical_rate(v); } },
    BOW_CRITICAL_RATE(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.add_missile_critical_rate(v); } },
    CHARISMA(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.AddStatCha(v); } },
    MAGIC_DODGE(0, WType.NONE) { @Override public void apply(L1PcInstance p, int v) { p.addMagicDodgeProbability(v); } },
    HEAL_DEF   (96,  WType.HEAL)   { @Override public void apply(L1PcInstance p, int v) { p.AddHealDefence(v); } };


	public enum WType { NONE, B, H, AC, POTION, HEAL, HELPER }

    public final int opcode;
    public final WType wt;
    ItemStat(int opcode, WType wt) {
    	this.opcode = opcode; 
    	this.wt = wt;
    }

    /** 장착 시 v=+값, 해제 시 v=-값. 스탯 변경만 하고 패킷 전송은 호출자가 담당 */
    public abstract void apply(L1PcInstance pc, int v);
}