package l1j.server.server.model.item.doll;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.eKind;

/**
 * 마법인형 옵션 종류.
 *
 * directCapable=true  : 소환(+1)/해제(-1) 시 자동 적용 가능
 * directCapable=false : 경험치/회복/확률발동처럼 다른 계산 루틴에서 조회
 */
public enum DollOption {

    AC(true, TooltipKind.AC) {
        @Override public void apply(L1PcInstance p, int v) { p.getAC().addAc(-v); }
    },
    MAX_HP(true, TooltipKind.MAX_HP) {
        @Override public void apply(L1PcInstance p, int v) { p.addMaxHp(v); }
    },
    MAX_MP(true, TooltipKind.MAX_MP) {
        @Override public void apply(L1PcInstance p, int v) { p.addMaxMp(v); }
    },
    DAMAGE_REDUCTION(true, TooltipKind.DMG_DOWN) {
        @Override public void apply(L1PcInstance p, int v) { p.addDamageReductionByArmor(v); }
    },
    PC_DEFENSE(true, TooltipKind.DMG_DOWN) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addcalcPcDefense(v); }
    },
    MELEE_DMG(true, TooltipKind.MELEE_DMG) {
        @Override public void apply(L1PcInstance p, int v) { p.addDmgup(v); }
    },
    MELEE_HIT(true, TooltipKind.MELEE_HIT) {
        @Override public void apply(L1PcInstance p, int v) { p.addHitup(v); }
    },
    BOW_DMG(true, TooltipKind.BOW_DMG) {
        @Override public void apply(L1PcInstance p, int v) { p.addBowDmgup(v); }
    },
    BOW_HIT(true, TooltipKind.BOW_HIT) {
        @Override public void apply(L1PcInstance p, int v) { p.addBowHitup(v); }
    },
    SP(true, TooltipKind.SP) {
        @Override public void apply(L1PcInstance p, int v) { p.getAbility().addSp(v); }
    },
    MAGIC_HIT(true, TooltipKind.MAGIC_HIT) {
        @Override public void apply(L1PcInstance p, int v) { p.addBaseMagicHitUp(v); }
    },
    MELEE_CRIT(true, TooltipKind.MELEE_CRIT) {
        @Override public void apply(L1PcInstance p, int v) { p.add_melee_critical_rate(v); }
    },
    ABILITY_RESIS(true, TooltipKind.ABILITY_RESIS) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialResistance(eKind.ABILITY, v); }
    },
    SPIRIT_RESIS(true, TooltipKind.NONE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialResistance(eKind.SPIRIT, v); }
    },
    DRAGON_RESIS(true, TooltipKind.NONE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialResistance(eKind.DRAGON_SPELL, v); }
    },
    FEAR_RESIS(true, TooltipKind.FEAR_RESIS) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialResistance(eKind.FEAR, v); }
    },
    ALL_RESIS(true, TooltipKind.ALL_RESIS) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialResistance(eKind.ALL, v); }
    },
    ABILITY_PIERCE(true, TooltipKind.ABILITY_PIERCE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialPierce(eKind.ABILITY, v); }
    },
    SPIRIT_PIERCE(true, TooltipKind.SPIRIT_PIERCE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialPierce(eKind.SPIRIT, v); }
    },
    DRAGON_PIERCE(true, TooltipKind.DRAGON_PIERCE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialPierce(eKind.DRAGON_SPELL, v); }
    },
    FEAR_PIERCE(true, TooltipKind.FEAR_PIERCE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialPierce(eKind.FEAR, v); }
    },
    ALL_PIERCE(true, TooltipKind.ALL_PIERCE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addSpecialPierce(eKind.ALL, v); }
    },
    PVP_DMG(true, TooltipKind.PVP_DMG) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addPVPweaponTotalDamage(v); }
    },
    PVP_DMG_RED(true, TooltipKind.PVP_DMG_RED) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addcalcPcDefense(v); }
    },
    BLESS_RATE(true, TooltipKind.BLESS_RATE) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addcalcAinPer(v); }
    },
    WEIGHT_REDUCTION(true, TooltipKind.WEIGHT_X50) {
        @Override public void apply(L1PcInstance p, int v) { p.addWeightReduction(v); }
    },
    FOU_DMG(true, TooltipKind.FOUSLAYER) {
        @Override public void apply(L1PcInstance p, int v) { p.getResistance().addFouDmgUp(v); }
    },
    // query / proc / meta
    EXP_RATE(false, TooltipKind.EXP),
    MP_UP(false, TooltipKind.MP_UP),
    HP_UP(false, TooltipKind.HP_UP),
    MPR(false, TooltipKind.MPR),
    PERCENT_DMG(false, TooltipKind.PERCENT_DMG);

    public enum TooltipKind {
        NONE,
        AC,
        MAX_HP,
        MAX_MP,
        DMG_DOWN,
        MELEE_DMG,
        MELEE_HIT,
        BOW_DMG,
        BOW_HIT,
        SP,
        MAGIC_HIT,
        MELEE_CRIT,
        ABILITY_RESIS,
        FEAR_RESIS,
        ALL_RESIS,
        ABILITY_PIERCE,
        SPIRIT_PIERCE,
        DRAGON_PIERCE,
        FEAR_PIERCE,
        ALL_PIERCE,
        PVP_DMG,
        PVP_DMG_RED,
        BLESS_RATE,
        WEIGHT_X50,
        EXP,
        MP_UP,
        HP_UP,
        MPR,
        PERCENT_DMG,
        FOUSLAYER
    }

    private final boolean directCapable;
    private final TooltipKind tooltipKind;

    DollOption(boolean directCapable, TooltipKind tooltipKind) {
        this.directCapable = directCapable;
        this.tooltipKind = tooltipKind;
    }

    public boolean isDirectCapable() { return directCapable; }
    public TooltipKind getTooltipKind() { return tooltipKind; }
    public boolean canDisplay() { return tooltipKind != TooltipKind.NONE; }

    public void apply(L1PcInstance pc, int v) {
        // query/meta 기본 no-op
    }
}
