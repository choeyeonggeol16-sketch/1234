package l1j.server.server.model;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_SPECIAL_RESISTANCE_NOTI;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.eKind;

public class Resistance {
    private static final int LIMIT_MIN = -128;
    private static final int LIMIT_MAX = 127;
    private static final int LIMIT_MIN_MR = 0;
    private static final int LIMIT_MAX_MR = 250;

    private int baseMr 	= 0; // 기본 마법 방어
    // Keep source totals intact. Clamp the effective value after combining them.
    private int addedMr = 0; // 아이템이나 마법에 의해 추가된 마법 방어를 포함한 마법 방어
    
    private int fire  = 0; // 불 저항
    private int water = 0; // 물 저항
    private int wind  = 0; // 바람 저항 
    private int earth = 0; // 땅 저항
    
    private int calcPcDefense = 0; //PVP 데미지 리덕
    private int PVPweaponTotalDamage = 0; //PVP 추가 데미지
    // 포우
    private int _fouDmgUp = 0;
    // 축복 소모 효율
    private int calcAinPer = 0;

    // 물약 관련 스탯 추가
    private int potionRecAmt  = 0; // 물약회복량 
    private int potionRecRate = 0; // 물약회복율 (%, +)
    private int healDefence   = 0; // 악화방어 
    
    // ═══ 특수 저항/관통 — SpecialKind 기반 primitive 배열 
    // 인덱스 = SpecialKind.value() (1~5 사용, 0 미사용)
    private final int[] _specialResist = new int[6];
    private final int[] _specialPierce = new int[6];
    
    private L1Character character = null;

    public void dispose(){
        character = null;
    }
    
    public Resistance() {}
    public Resistance(L1Character cha) {
        init();
        character = cha;
    }
    
    public void init() {
        baseMr = addedMr = 0;
        fire = water = wind = earth = 0;
        calcPcDefense =  PVPweaponTotalDamage = 0;
        calcAinPer = 0;
        _fouDmgUp = 0;
        potionRecAmt = potionRecRate = healDefence = 0; // 초기화 시 함께 리셋
        for (int i = 0; i < _specialResist.length; i++) { _specialResist[i] = 0; }
        for (int i = 0; i < _specialPierce.length; i++) { _specialPierce[i] = 0; }
    }
    
    private int checkMrRange(int i, final int MIN) {
        return IntRange.ensure(i, MIN, LIMIT_MAX_MR);
    }
    
    private byte checkRange(int i) {
        return (byte)IntRange.ensure(i, LIMIT_MIN, LIMIT_MAX);
    }
    
    public void addSpecialResistance(eKind kind, int value) {
        if (kind == null) return;
        _specialResist[kind.value()] += value;
        if (character instanceof L1PcInstance pc) {
            SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
        }
    }

    public int getSpecialResistance(eKind kind) {
        if (kind == null) return 0;
        final int shift = (kind.value() - 1) << 3;
        final int bonus = (int) (skillSpecialResistance >>> shift) & 0xff;
        // Independent signed penalties must not wrap into an unsigned 255
        // or corrupt a neighboring byte of the positive resistance bitmap.
        final int penalty = kind.value() <= 4
                ? (byte) (skillSpecialPenalty >>> shift) : 0;
        return checkRange(_specialResist[kind.value()] + bonus + penalty);
    }

    public void addSpecialPierce(eKind kind, int value) {
        if (kind == null) return; 
        _specialPierce[kind.value()] += value;
        if (character instanceof L1PcInstance pc) {
            SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
        }
    }

    public int getSpecialPierce(eKind kind) {
        if (kind == null) return 0; 
        final int value = kind.value();
        final int pumped = value <= 4
                ? (skillSpecialPierce >>> ((value - 1) << 3)) & 0xff : 0;
        final int shared = switch (kind) {
            case SPIRIT -> (int) ((skillSpecialResistance >>> 40) & 0xffL);
            case ALL -> (int) ((skillSpecialResistance >>> 48) & 0xffL);
            default -> 0;
        };
        return checkRange(_specialPierce[value] + pumped + shared);
    }

    public int getEffectedMrBySkill() {
        int effectedMr = getMr();
        
        if (character.hasSkillEffect(L1SkillId.ERASE_MAGIC)) 	
            effectedMr /= 2; // 25% 원본4
        
        return effectedMr;
    }

    public int getAddedMr() { return checkMrRange(addedMr, -baseMr); }
    private volatile int skillMr;

    void setSkillMr(int value) { if (skillMr != value) skillMr = value; }

    public int getMr() {
        if(character == null || !character.isPassive(PassiveID.RESIST_ELEMENT.toInt()))
            return checkMrRange(baseMr + addedMr + skillMr, LIMIT_MIN_MR);
        return checkMrRange(baseMr + addedMr + skillMr + 5, LIMIT_MIN_MR);
    }
    public int getBaseMr() 		 { return baseMr; 										}
    public void addMr(int i) { addedMr += i; }
    public void setBaseMr(int i) { baseMr = checkMrRange(i, LIMIT_MIN_MR); 				}

    // Signed 16-bit values, PvP damage in the low half and reduction in the high half.
    private volatile int skillPvp;
    // Five resistance bytes, followed by spirit pierce and all-type pierce.
    private volatile long skillSpecialResistance;
    // Four signed 8-bit penalties (ability/spirit/dragon/fear) from effects.
    private volatile int skillSpecialPenalty;
    // Independent ability, spirit, dragon and fear pierce lanes.
    private volatile int skillSpecialPierce;

    public void setSkillCombatModifiers(int pvp, long specialResistance, boolean notify) {
        setSkillCombatModifiers(pvp, specialResistance, 0, notify);
    }

    public void setSkillCombatModifiers(int pvp, long specialResistance, int penalty, boolean notify) {
        // Older callers did not own the extra pierce channels. Preserve their
        // active sources; only the five-argument owner publication clears them.
        setSkillCombatModifiers(pvp, specialResistance, penalty, skillSpecialPierce, notify);
    }

    public void setSkillCombatModifiers(int pvp, long specialResistance, int penalty,
            int pierce, boolean notify) {
        if (skillPvp != pvp) skillPvp = pvp;
        if (skillSpecialResistance == specialResistance && skillSpecialPenalty == penalty
                && skillSpecialPierce == pierce) return;
        skillSpecialResistance = specialResistance;
        skillSpecialPenalty = penalty;
        skillSpecialPierce = pierce;
        if (notify && character instanceof L1PcInstance pc) SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
    }

    public int getcalcPcDefense() { return checkRange(calcPcDefense + (skillPvp >> 16)); }
    public int getPVPweaponTotalDamage() { return checkRange(PVPweaponTotalDamage + (short) skillPvp); }
    
    // Signed 16-bit values, from least to most significant: water, wind, earth, fire.
    private volatile long skillElemental;

    void setSkillElemental(long value) { if (skillElemental != value) skillElemental = value; }

    public int getFire()  { return increase_elemental_resist(checkRange(fire + (short) (skillElemental >>> 48))); }
    public int getWater() { return increase_elemental_resist(checkRange(water + (short) skillElemental)); }
    public int getWind()  { return increase_elemental_resist(checkRange(wind + (short) (skillElemental >>> 16))); }
    public int getEarth() { return increase_elemental_resist(checkRange(earth + (short) (skillElemental >>> 32))); }

    private int increase_elemental_resist(int source_resist) {
        return character == null || !character.isPassive(PassiveID.RESIST_ELEMENT.toInt()) ?
                source_resist : source_resist + 5;
    }
    
    public void addFire(int i) { fire += i; }
    public void addWater(int i) { water += i; }
    public void addWind(int i) { wind += i; }
    public void addEarth(int i) { earth += i; }
    
    public void addcalcPcDefense(int i) { calcPcDefense += i; }
    public void addPVPweaponTotalDamage(int i) { PVPweaponTotalDamage += i; }
    
    public void addAllNaturalResistance(int i) {
        addFire(i);
        addWater(i);
        addWind(i);
        addEarth(i);
    }
    
    public int getMrAfterEraseRemove(){
        int effectedMr = getMr();
        if (character.hasSkillEffect(L1SkillId.ERASE_MAGIC)){
            character.removeSkillEffect(L1SkillId.ERASE_MAGIC);
            if (character instanceof L1PcInstance) SC_PacketBox.send(((L1PcInstance) character), SC_PacketBox.UNLIMITED_ICON1, 55, false);
            effectedMr /= 2; // 25%
        }
        return effectedMr;
    }
    
    public int getcalcAinPer() { 
        return calcAinPer; 
    }

    public void addcalcAinPer(int i) { 
        calcAinPer = checkMrRange(calcAinPer + i, 0); 
    }

    public void setcalcAinPer(int i) { 
        calcAinPer = checkMrRange(i, 0); 
    }
    
    // =====================================================
    // Fou
    // =====================================================
    public void addFouDmgUp(int v) {
        _fouDmgUp += v;
        if (_fouDmgUp < 0) _fouDmgUp = 0;
    }

    public int getFouDmgUp() {
        return _fouDmgUp;
    }

    public void addPotionRecAmt(int v)  { this.potionRecAmt += v; }
    public int  getPotionRecAmt()        { return potionRecAmt; }

    public void addPotionRecRate(int v) { this.potionRecRate += v; }
    public int  getPotionRecRate()      { return potionRecRate; }

    public void addHealDefence(int v)   { this.healDefence += v; }
    public int  getHealDefence()        { return healDefence; }
}
