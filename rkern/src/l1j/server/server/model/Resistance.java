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

    // Compatibility facade: all stat sources belong to the creature.
    private final L1Character owner;
    private L1Character character = null;

    public void dispose(){
        character = null;
    }
    
    Resistance(L1Character cha) {
        owner = java.util.Objects.requireNonNull(cha);
        character = cha;
        init();
    }
    
    public void init() {
        owner.m_nOrgMDefend = owner.m_nAddedMDefend = 0;
        owner.m_nFireResist = owner.m_nWaterResist = owner.m_nWindResist = owner.m_nEarthResist = 0;
        owner.m_nPvpDamageReduction =  owner.m_nPvpDamage = 0;
        owner.m_nAinEfficiency = 0;
        owner.m_nFouDamage = 0;
        owner.m_nPotionRecoveryAmount = owner.m_nPotionRecoveryRate = owner.m_nHealDefence = 0; // 초기화 시 함께 리셋
        owner.m_nResistAbility = owner.m_nResistSpirit = owner.m_nResistDragon = owner.m_nResistFear = owner.m_nResistAll = 0;
        owner.m_nPierceAbility = owner.m_nPierceSpirit = owner.m_nPierceDragon = owner.m_nPierceFear = owner.m_nPierceAll = 0;
    }
    
    private int checkMrRange(int i, final int MIN) {
        return IntRange.ensure(i, MIN, LIMIT_MAX_MR);
    }
    
    private byte checkRange(int i) {
        return (byte)IntRange.ensure(i, LIMIT_MIN, LIMIT_MAX);
    }
    
    private int GetResistSource(eKind kind) {
        return switch (kind) {
            case ABILITY -> owner.m_nResistAbility;
            case SPIRIT -> owner.m_nResistSpirit;
            case DRAGON_SPELL -> owner.m_nResistDragon;
            case FEAR -> owner.m_nResistFear;
            case ALL -> owner.m_nResistAll;
        };
    }

    private int GetPierceSource(eKind kind) {
        return switch (kind) {
            case ABILITY -> owner.m_nPierceAbility;
            case SPIRIT -> owner.m_nPierceSpirit;
            case DRAGON_SPELL -> owner.m_nPierceDragon;
            case FEAR -> owner.m_nPierceFear;
            case ALL -> owner.m_nPierceAll;
        };
    }

    public void addSpecialResistance(eKind kind, int value) {
        if (kind == null) return;
        switch (kind) {
            case ABILITY -> owner.m_nResistAbility += value;
            case SPIRIT -> owner.m_nResistSpirit += value;
            case DRAGON_SPELL -> owner.m_nResistDragon += value;
            case FEAR -> owner.m_nResistFear += value;
            case ALL -> owner.m_nResistAll += value;
        }
        if (character instanceof L1PcInstance pc) {
            SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
        }
    }

    public int getSpecialResistance(eKind kind) {
        if (kind == null) return 0;
        final int shift = (kind.value() - 1) << 3;
        final int bonus = (int) (owner.m_SkillSpecialResistance >>> shift) & 0xff;
        // Independent signed penalties must not wrap into an unsigned 255
        // or corrupt a neighboring byte of the positive resistance bitmap.
        final int penalty = kind.value() <= 4
                ? (byte) (owner.m_nSkillSpecialPenalty >>> shift) : 0;
        return checkRange(GetResistSource(kind) + bonus + penalty);
    }

    public void addSpecialPierce(eKind kind, int value) {
        if (kind == null) return; 
        switch (kind) {
            case ABILITY -> owner.m_nPierceAbility += value;
            case SPIRIT -> owner.m_nPierceSpirit += value;
            case DRAGON_SPELL -> owner.m_nPierceDragon += value;
            case FEAR -> owner.m_nPierceFear += value;
            case ALL -> owner.m_nPierceAll += value;
        }
        if (character instanceof L1PcInstance pc) {
            SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
        }
    }

    public int getSpecialPierce(eKind kind) {
        if (kind == null) return 0; 
        final int value = kind.value();
        final int pumped = value <= 4
                ? (owner.m_nSkillSpecialPierce >>> ((value - 1) << 3)) & 0xff : 0;
        final int shared = switch (kind) {
            case SPIRIT -> (int) ((owner.m_SkillSpecialResistance >>> 40) & 0xffL);
            case ALL -> (int) ((owner.m_SkillSpecialResistance >>> 48) & 0xffL);
            default -> 0;
        };
        return checkRange(GetPierceSource(kind) + pumped + shared);
    }

    public int getEffectedMrBySkill() {
        int effectedMr = getMr();
        
        if (character.hasSkillEffect(L1SkillId.ERASE_MAGIC)) 	
            effectedMr /= 2; // 25% 원본4
        
        return effectedMr;
    }

    public int getAddedMr() { return checkMrRange(owner.m_nAddedMDefend, -owner.m_nOrgMDefend); }

    void setSkillMr(int value) { if (owner.m_nSkillMDefend != value) owner.m_nSkillMDefend = value; }

    public int getMr() {
        if(character == null || !character.isPassive(PassiveID.RESIST_ELEMENT.toInt()))
            return checkMrRange(owner.m_nOrgMDefend + owner.m_nAddedMDefend + owner.m_nSkillMDefend, LIMIT_MIN_MR);
        return checkMrRange(owner.m_nOrgMDefend + owner.m_nAddedMDefend + owner.m_nSkillMDefend + 5, LIMIT_MIN_MR);
    }
    public int getBaseMr() 		 { return owner.m_nOrgMDefend; 										}
    public void addMr(int i) { owner.m_nAddedMDefend += i; }
    public void setBaseMr(int i) { owner.m_nOrgMDefend = checkMrRange(i, LIMIT_MIN_MR); 				}

    // Signed 16-bit values, PvP damage in the low half and reduction in the high half.
    // Five resistance bytes, followed by spirit pierce and all-type pierce.
    // Four signed 8-bit penalties (ability/spirit/dragon/fear) from effects.
    // Independent ability, spirit, dragon and fear pierce lanes.

    public void setSkillCombatModifiers(int pvp, long specialResistance, boolean notify) {
        setSkillCombatModifiers(pvp, specialResistance, 0, notify);
    }

    public void setSkillCombatModifiers(int pvp, long specialResistance, int penalty, boolean notify) {
        // Older callers did not own the extra pierce channels. Preserve their
        // active sources; only the five-argument owner publication clears them.
        setSkillCombatModifiers(pvp, specialResistance, penalty, owner.m_nSkillSpecialPierce, notify);
    }

    public void setSkillCombatModifiers(int pvp, long specialResistance, int penalty,
            int pierce, boolean notify) {
        if (owner.m_nSkillPvp != pvp) owner.m_nSkillPvp = pvp;
        if (owner.m_SkillSpecialResistance == specialResistance && owner.m_nSkillSpecialPenalty == penalty
                && owner.m_nSkillSpecialPierce == pierce) return;
        owner.m_SkillSpecialResistance = specialResistance;
        owner.m_nSkillSpecialPenalty = penalty;
        owner.m_nSkillSpecialPierce = pierce;
        if (notify && character instanceof L1PcInstance pc) SC_SPECIAL_RESISTANCE_NOTI.sendConstructed(pc, pc);
    }

    public int getcalcPcDefense() { return checkRange(owner.m_nPvpDamageReduction + (owner.m_nSkillPvp >> 16)); }
    public int getPVPweaponTotalDamage() { return checkRange(owner.m_nPvpDamage + (short) owner.m_nSkillPvp); }
    
    // Signed 16-bit values, from least to most significant: owner.m_nWaterResist, owner.m_nWindResist, owner.m_nEarthResist, owner.m_nFireResist.

    void setSkillElemental(long value) { if (owner.m_SkillElemental != value) owner.m_SkillElemental = value; }

    public int getFire()  { return increase_elemental_resist(checkRange(owner.m_nFireResist + (short) (owner.m_SkillElemental >>> 48))); }
    public int getWater() { return increase_elemental_resist(checkRange(owner.m_nWaterResist + (short) owner.m_SkillElemental)); }
    public int getWind()  { return increase_elemental_resist(checkRange(owner.m_nWindResist + (short) (owner.m_SkillElemental >>> 16))); }
    public int getEarth() { return increase_elemental_resist(checkRange(owner.m_nEarthResist + (short) (owner.m_SkillElemental >>> 32))); }

    private int increase_elemental_resist(int source_resist) {
        return character == null || !character.isPassive(PassiveID.RESIST_ELEMENT.toInt()) ?
                source_resist : source_resist + 5;
    }
    
    public void addFire(int i) { owner.m_nFireResist += i; }
    public void addWater(int i) { owner.m_nWaterResist += i; }
    public void addWind(int i) { owner.m_nWindResist += i; }
    public void addEarth(int i) { owner.m_nEarthResist += i; }
    
    public void addcalcPcDefense(int i) { owner.m_nPvpDamageReduction += i; }
    public void addPVPweaponTotalDamage(int i) { owner.m_nPvpDamage += i; }
    
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
        return owner.m_nAinEfficiency; 
    }

    public void addcalcAinPer(int i) { 
        owner.m_nAinEfficiency = checkMrRange(owner.m_nAinEfficiency + i, 0); 
    }

    public void setcalcAinPer(int i) { 
        owner.m_nAinEfficiency = checkMrRange(i, 0); 
    }
    
    // =====================================================
    // Fou
    // =====================================================
    public void addFouDmgUp(int v) {
        owner.m_nFouDamage += v;
        if (owner.m_nFouDamage < 0) owner.m_nFouDamage = 0;
    }

    public int getFouDmgUp() {
        return owner.m_nFouDamage;
    }

    public void addPotionRecAmt(int v)  { owner.m_nPotionRecoveryAmount += v; }
    public int  getPotionRecAmt()        { return owner.m_nPotionRecoveryAmount; }

    public void addPotionRecRate(int v) { owner.m_nPotionRecoveryRate += v; }
    public int  getPotionRecRate()      { return owner.m_nPotionRecoveryRate; }

    public void addHealDefence(int v)   { owner.m_nHealDefence += v; }
    public int  getHealDefence()        { return owner.m_nHealDefence; }
}
