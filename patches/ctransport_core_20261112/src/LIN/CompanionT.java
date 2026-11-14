package LIN;

import java.util.ArrayList;
import l1j.server.server.packets.Protocol.ProtoWriter;



/**
 * CompanionT — 컴패니언 시스템 스키마 트리 (구 generated 완전 대체).
 * 독립 msgId 없음 — 상위 패킷의 서브메시지로만 사용 (saveBody 호출).
 *
 * 와이어 스키마 (구 generated에서 추출 — 필드번호/타입 계약 그대로):
 *  StatT            { repeated BaseStatBonusT BaseStatBonus=1; }
 *  BaseStatBonusT   { int32 id=1; enum statType=2; int32 value=3; int32 meleeDmg=4; int32 meleeHit=5;
 *                     int32 regenHP=6; int32 AC=7; int32 spellDmg=8; int32 spellHit=9; }
 *  SkillEnchantTierT{ uint32 tier=1; EnchantCostT EnchantCost=2; OpenCostT OpenCost=3; }
 *  EnchantCostT     { repeated uint32 friendship=1; uint32 catalystItem=2; }
 *  OpenCostT        { uint32 level=1; uint32 minEnchant=2; uint32 friendship=3; uint32 adena=4; }
 *  WildSkillT       { repeated SkillT Skill=1; }
 *  SkillT(Wild)     { int32 id=1; int32 descNum=2; repeated EnchantBonusT EnchantBonus=3; }
 *  EnchantBonusT    { int32 value=1; int32 meleeDmg=2; int32 meleeHit=3; double meleeCriHit=4; int32 ignoreReduction=5;
 *                     double bloodSuckHit=6; int32 bloodSuckHeal=7; int32 regenHP=8; int32 AC=9; int32 MR=10;
 *                     int32 potionHP=11; int32 dmgReduction=12; int32 maxHP=13; int32 spellDmgMulti=14;
 *                     int32 spellHit=15; double moveDelayReduce=16; double attackDelayReduce=17;
 *                     int32 fireElementalDmg=18; int32 waterElementalDmg=19; int32 airElementalDmg=20;
 *                     int32 earthElementalDmg=21; int32 lightElementalDmg=22; int32 comboDmgMulti=23; int32 spellDmgAdd=24; }
 *  ClassInfoT       { repeated ClassT Class=1; }
 *  ClassT           { string class=1; uint32 classId=2; enum category=3; enum element=5; repeated SkillT Skill=6; }  // field 4 없음
 *  SkillT(Class)    { uint32 tier=1; repeated uint32 skillId=2; }
 */
public final class CompanionT {

    public static LIN.CompanionT newInstance(){
        return new LIN.CompanionT();
    }
    private CompanionT(){}

    // ═══════════════════════════════════════════════════════════
    // StatT
    // ═══════════════════════════════════════════════════════════
    public static final class StatT {
        public static LIN.CompanionT.StatT newInstance(){
            return new LIN.CompanionT.StatT();
        }
        private ArrayList<LIN.CompanionT.StatT.BaseStatBonusT> _BaseStatBonus;
        private StatT(){}

        public ArrayList<LIN.CompanionT.StatT.BaseStatBonusT> BaseStatBonus(){
            return _BaseStatBonus;
        }
        public void add_BaseStatBonus(LIN.CompanionT.StatT.BaseStatBonusT val){
            if(!has_BaseStatBonus())
                _BaseStatBonus = new ArrayList<LIN.CompanionT.StatT.BaseStatBonusT>();
            _BaseStatBonus.add(val);
        }
        public boolean has_BaseStatBonus(){
            return _BaseStatBonus != null;
        }

        public void saveBody(ProtoWriter w){
            if(has_BaseStatBonus()){
                for(LIN.CompanionT.StatT.BaseStatBonusT val : _BaseStatBonus){
                    w.msg(1);
                    val.saveBody(w);
                    w.end();
                }
            }
        }

        public void dispose(){
            if(has_BaseStatBonus()){
                for(LIN.CompanionT.StatT.BaseStatBonusT val : _BaseStatBonus)
                    val.dispose();
                _BaseStatBonus.clear();
                _BaseStatBonus = null;
            }
        }

        // ── BaseStatBonusT ──
        public static final class BaseStatBonusT {
            public static LIN.CompanionT.StatT.BaseStatBonusT newInstance(){
                return new LIN.CompanionT.StatT.BaseStatBonusT();
            }
            private int _id;
            private LIN.CompanionT.eStatType _statType;
            private int _value;
            private int _meleeDmg;
            private int _meleeHit;
            private int _regenHP;
            private int _AC;
            private int _spellDmg;
            private int _spellHit;
            private int _bit;
            private BaseStatBonusT(){}

            public int id()                     { return _id; }
            public void set_id(int val)             { _bit |= 0x1; _id = val; }
            public boolean has_id()                 { return (_bit & 0x1) == 0x1; }
            public LIN.CompanionT.eStatType statType()         { return _statType; }
            public void set_statType(LIN.CompanionT.eStatType val) { _bit |= 0x2; _statType = val; }
            public boolean has_statType()           { return (_bit & 0x2) == 0x2; }
            public int value()                  { return _value; }
            public void set_value(int val)          { _bit |= 0x4; _value = val; }
            public boolean has_value()              { return (_bit & 0x4) == 0x4; }
            public int meleeDmg()               { return _meleeDmg; }
            public void set_meleeDmg(int val)       { _bit |= 0x8; _meleeDmg = val; }
            public boolean has_meleeDmg()           { return (_bit & 0x8) == 0x8; }
            public int meleeHit()               { return _meleeHit; }
            public void set_meleeHit(int val)       { _bit |= 0x10; _meleeHit = val; }
            public boolean has_meleeHit()           { return (_bit & 0x10) == 0x10; }
            public int regenHP()                { return _regenHP; }
            public void set_regenHP(int val)        { _bit |= 0x20; _regenHP = val; }
            public boolean has_regenHP()            { return (_bit & 0x20) == 0x20; }
            public int AC()                     { return _AC; }
            public void set_AC(int val)             { _bit |= 0x40; _AC = val; }
            public boolean has_AC()                 { return (_bit & 0x40) == 0x40; }
            public int spellDmg()               { return _spellDmg; }
            public void set_spellDmg(int val)       { _bit |= 0x80; _spellDmg = val; }
            public boolean has_spellDmg()           { return (_bit & 0x80) == 0x80; }
            public int spellHit()               { return _spellHit; }
            public void set_spellHit(int val)       { _bit |= 0x100; _spellHit = val; }
            public boolean has_spellHit()           { return (_bit & 0x100) == 0x100; }

            public void saveBody(ProtoWriter w){
                if(has_id())
                    w.int32(1, _id);
                if(has_statType())
                    w.enumV(2, _statType.toInt());
                if(has_value())
                    w.int32(3, _value);
                if(has_meleeDmg())
                    w.int32(4, _meleeDmg);
                if(has_meleeHit())
                    w.int32(5, _meleeHit);
                if(has_regenHP())
                    w.int32(6, _regenHP);
                if(has_AC())
                    w.int32(7, _AC);
                if(has_spellDmg())
                    w.int32(8, _spellDmg);
                if(has_spellHit())
                    w.int32(9, _spellHit);
            }

            public void dispose(){
                _bit = 0;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SkillEnchantTierT
    // ═══════════════════════════════════════════════════════════
    public static final class SkillEnchantTierT {
        public static LIN.CompanionT.SkillEnchantTierT newInstance(){
            return new LIN.CompanionT.SkillEnchantTierT();
        }
        private int _tier;
        private LIN.CompanionT.SkillEnchantTierT.EnchantCostT _EnchantCost;
        private LIN.CompanionT.SkillEnchantTierT.OpenCostT _OpenCost;
        private int _bit;
        private SkillEnchantTierT(){}

        public int tier()                       { return _tier; }
        public void set_tier(int val)               { _bit |= 0x1; _tier = val; }
        public boolean has_tier()                   { return (_bit & 0x1) == 0x1; }
        public LIN.CompanionT.SkillEnchantTierT.EnchantCostT EnchantCost()       { return _EnchantCost; }
        public void set_EnchantCost(LIN.CompanionT.SkillEnchantTierT.EnchantCostT v) { _bit |= 0x2; _EnchantCost = v; }
        public boolean has_EnchantCost()            { return (_bit & 0x2) == 0x2; }
        public LIN.CompanionT.SkillEnchantTierT.OpenCostT OpenCost()             { return _OpenCost; }
        public void set_OpenCost(LIN.CompanionT.SkillEnchantTierT.OpenCostT val)     { _bit |= 0x4; _OpenCost = val; }
        public boolean has_OpenCost()               { return (_bit & 0x4) == 0x4; }

        public void saveBody(ProtoWriter w){
            if(has_tier())
                w.uint32(1, _tier);
            if(has_EnchantCost()){
                w.msg(2);
                _EnchantCost.saveBody(w);
                w.end();
            }
            if(has_OpenCost()){
                w.msg(3);
                _OpenCost.saveBody(w);
                w.end();
            }
        }

        public void dispose(){
            if(has_EnchantCost() && _EnchantCost != null){
                _EnchantCost.dispose();
                _EnchantCost = null;
            }
            if(has_OpenCost() && _OpenCost != null){
                _OpenCost.dispose();
                _OpenCost = null;
            }
            _bit = 0;
        }

        // ── EnchantCostT ──
        public static final class EnchantCostT {
            public static LIN.CompanionT.SkillEnchantTierT.EnchantCostT newInstance(){
                return new LIN.CompanionT.SkillEnchantTierT.EnchantCostT();
            }
            private ArrayList<Integer> _friendship;
            private int _catalystItem;
            private int _bit;
            private EnchantCostT(){}

            public ArrayList<Integer> friendship()      { return _friendship; }
            public void add_friendship(int val)             {
                if(!has_friendship())
                    _friendship = new ArrayList<Integer>();
                _friendship.add(val);
            }
            public boolean has_friendship()                 { return _friendship != null; }
            public int catalystItem()                   { return _catalystItem; }
            public void set_catalystItem(int val)           { _bit |= 0x2; _catalystItem = val; }
            public boolean has_catalystItem()               { return (_bit & 0x2) == 0x2; }

            public void saveBody(ProtoWriter w){
                if(has_friendship()){
                    for(int val : _friendship){
                        w.uint32(1, val);
                    }
                }
                if(has_catalystItem())
                    w.uint32(2, _catalystItem);
            }

            public void dispose(){
                if(has_friendship()){
                    _friendship.clear();
                    _friendship = null;
                }
                _bit = 0;
            }
        }

        // ── OpenCostT ──
        public static final class OpenCostT {
            public static LIN.CompanionT.SkillEnchantTierT.OpenCostT newInstance(){
                return new LIN.CompanionT.SkillEnchantTierT.OpenCostT();
            }
            private int _level;
            private int _minEnchant;
            private int _friendship;
            private int _adena;
            private int _bit;
            private OpenCostT(){}

            public int level()              { return _level; }
            public void set_level(int val)      { _bit |= 0x1; _level = val; }
            public boolean has_level()          { return (_bit & 0x1) == 0x1; }
            public int minEnchant()         { return _minEnchant; }
            public void set_minEnchant(int val) { _bit |= 0x2; _minEnchant = val; }
            public boolean has_minEnchant()     { return (_bit & 0x2) == 0x2; }
            public int friendship()         { return _friendship; }
            public void set_friendship(int val) { _bit |= 0x4; _friendship = val; }
            public boolean has_friendship()     { return (_bit & 0x4) == 0x4; }
            public int adena()              { return _adena; }
            public void set_adena(int val)      { _bit |= 0x8; _adena = val; }
            public boolean has_adena()          { return (_bit & 0x8) == 0x8; }

            public void saveBody(ProtoWriter w){
                if(has_level())
                    w.uint32(1, _level);
                if(has_minEnchant())
                    w.uint32(2, _minEnchant);
                if(has_friendship())
                    w.uint32(3, _friendship);
                if(has_adena())
                    w.uint32(4, _adena);
            }

            public void dispose(){
                _bit = 0;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // WildSkillT
    // ═══════════════════════════════════════════════════════════
    public static final class WildSkillT {
        public static LIN.CompanionT.WildSkillT newInstance(){
            return new LIN.CompanionT.WildSkillT();
        }
        private ArrayList<LIN.CompanionT.WildSkillT.SkillT> _Skill;
        private WildSkillT(){}

        public ArrayList<LIN.CompanionT.WildSkillT.SkillT> Skill()    { return _Skill; }
        public void add_Skill(LIN.CompanionT.WildSkillT.SkillT val)       {
            if(!has_Skill())
                _Skill = new ArrayList<LIN.CompanionT.WildSkillT.SkillT>();
            _Skill.add(val);
        }
        public boolean has_Skill()              { return _Skill != null; }

        public void saveBody(ProtoWriter w){
            if(has_Skill()){
                for(LIN.CompanionT.WildSkillT.SkillT val : _Skill){
                    w.msg(1);
                    val.saveBody(w);
                    w.end();
                }
            }
        }

        public void dispose(){
            if(has_Skill()){
                for(LIN.CompanionT.WildSkillT.SkillT val : _Skill)
                    val.dispose();
                _Skill.clear();
                _Skill = null;
            }
        }

        // ── SkillT (Wild) ──
        public static final class SkillT {
            public static LIN.CompanionT.WildSkillT.SkillT newInstance(){
                return new LIN.CompanionT.WildSkillT.SkillT();
            }
            private int _id;
            private int _descNum;
            private ArrayList<LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT> _EnchantBonus;
            private int _bit;
            private SkillT(){}

            public int id()                 { return _id; }
            public void set_id(int val)         { _bit |= 0x1; _id = val; }
            public boolean has_id()             { return (_bit & 0x1) == 0x1; }
            public int descNum()            { return _descNum; }
            public void set_descNum(int val)    { _bit |= 0x2; _descNum = val; }
            public boolean has_descNum()        { return (_bit & 0x2) == 0x2; }
            public ArrayList<LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT> EnchantBonus()  { return _EnchantBonus; }
            public void add_EnchantBonus(LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT val)     {
                if(!has_EnchantBonus())
                    _EnchantBonus = new ArrayList<LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT>();
                _EnchantBonus.add(val);
            }
            public boolean has_EnchantBonus()   { return _EnchantBonus != null; }

            public void saveBody(ProtoWriter w){
                if(has_id())
                    w.int32(1, _id);
                if(has_descNum())
                    w.int32(2, _descNum);
                if(has_EnchantBonus()){
                    for(LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT val : _EnchantBonus){
                        w.msg(3);
                        val.saveBody(w);
                        w.end();
                    }
                }
            }

            public void dispose(){
                if(has_EnchantBonus()){
                    for(LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT val : _EnchantBonus)
                        val.dispose();
                    _EnchantBonus.clear();
                    _EnchantBonus = null;
                }
                _bit = 0;
            }

            // ── EnchantBonusT ──
            public static final class EnchantBonusT {
                public static LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT newInstance(){
                    return new LIN.CompanionT.WildSkillT.SkillT.EnchantBonusT();
                }
                private int _value;
                private int _meleeDmg;
                private int _meleeHit;
                private double _meleeCriHit;
                private int _ignoreReduction;
                private double _bloodSuckHit;
                private int _bloodSuckHeal;
                private int _regenHP;
                private int _AC;
                private int _MR;
                private int _potionHP;
                private int _dmgReduction;
                private int _maxHP;
                private int _spellDmgMulti;
                private int _spellHit;
                private double _moveDelayReduce;
                private double _attackDelayReduce;
                private int _fireElementalDmg;
                private int _waterElementalDmg;
                private int _airElementalDmg;
                private int _earthElementalDmg;
                private int _lightElementalDmg;
                private int _comboDmgMulti;
                private int _spellDmgAdd;
                private int _bit;
                private EnchantBonusT(){}

                public int value()                          { return _value; }
                public void set_value(int val)                  { _bit |= 0x1; _value = val; }
                public boolean has_value()                      { return (_bit & 0x1) == 0x1; }
                public int meleeDmg()                       { return _meleeDmg; }
                public void set_meleeDmg(int val)               { _bit |= 0x2; _meleeDmg = val; }
                public boolean has_meleeDmg()                   { return (_bit & 0x2) == 0x2; }
                public int meleeHit()                       { return _meleeHit; }
                public void set_meleeHit(int val)               { _bit |= 0x4; _meleeHit = val; }
                public boolean has_meleeHit()                   { return (_bit & 0x4) == 0x4; }
                public double meleeCriHit()                 { return _meleeCriHit; }
                public void set_meleeCriHit(double val)         { _bit |= 0x8; _meleeCriHit = val; }
                public boolean has_meleeCriHit()                { return (_bit & 0x8) == 0x8; }
                public int ignoreReduction()                { return _ignoreReduction; }
                public void set_ignoreReduction(int val)        { _bit |= 0x10; _ignoreReduction = val; }
                public boolean has_ignoreReduction()            { return (_bit & 0x10) == 0x10; }
                public double bloodSuckHit()                { return _bloodSuckHit; }
                public void set_bloodSuckHit(double val)        { _bit |= 0x20; _bloodSuckHit = val; }
                public boolean has_bloodSuckHit()               { return (_bit & 0x20) == 0x20; }
                public int bloodSuckHeal()                  { return _bloodSuckHeal; }
                public void set_bloodSuckHeal(int val)          { _bit |= 0x40; _bloodSuckHeal = val; }
                public boolean has_bloodSuckHeal()              { return (_bit & 0x40) == 0x40; }
                public int regenHP()                        { return _regenHP; }
                public void set_regenHP(int val)                { _bit |= 0x80; _regenHP = val; }
                public boolean has_regenHP()                    { return (_bit & 0x80) == 0x80; }
                public int AC()                             { return _AC; }
                public void set_AC(int val)                     { _bit |= 0x100; _AC = val; }
                public boolean has_AC()                         { return (_bit & 0x100) == 0x100; }
                public int MR()                             { return _MR; }
                public void set_MR(int val)                     { _bit |= 0x200; _MR = val; }
                public boolean has_MR()                         { return (_bit & 0x200) == 0x200; }
                public int potionHP()                       { return _potionHP; }
                public void set_potionHP(int val)               { _bit |= 0x400; _potionHP = val; }
                public boolean has_potionHP()                   { return (_bit & 0x400) == 0x400; }
                public int dmgReduction()                   { return _dmgReduction; }
                public void set_dmgReduction(int val)           { _bit |= 0x800; _dmgReduction = val; }
                public boolean has_dmgReduction()               { return (_bit & 0x800) == 0x800; }
                public int maxHP()                          { return _maxHP; }
                public void set_maxHP(int val)                  { _bit |= 0x1000; _maxHP = val; }
                public boolean has_maxHP()                      { return (_bit & 0x1000) == 0x1000; }
                public int spellDmgMulti()                  { return _spellDmgMulti; }
                public void set_spellDmgMulti(int val)          { _bit |= 0x2000; _spellDmgMulti = val; }
                public boolean has_spellDmgMulti()              { return (_bit & 0x2000) == 0x2000; }
                public int spellHit()                       { return _spellHit; }
                public void set_spellHit(int val)               { _bit |= 0x4000; _spellHit = val; }
                public boolean has_spellHit()                   { return (_bit & 0x4000) == 0x4000; }
                public double moveDelayReduce()             { return _moveDelayReduce; }
                public void set_moveDelayReduce(double val)     { _bit |= 0x8000; _moveDelayReduce = val; }
                public boolean has_moveDelayReduce()            { return (_bit & 0x8000) == 0x8000; }
                public double attackDelayReduce()           { return _attackDelayReduce; }
                public void set_attackDelayReduce(double val)   { _bit |= 0x10000; _attackDelayReduce = val; }
                public boolean has_attackDelayReduce()          { return (_bit & 0x10000) == 0x10000; }
                public int fireElementalDmg()               { return _fireElementalDmg; }
                public void set_fireElementalDmg(int val)       { _bit |= 0x20000; _fireElementalDmg = val; }
                public boolean has_fireElementalDmg()           { return (_bit & 0x20000) == 0x20000; }
                public int waterElementalDmg()              { return _waterElementalDmg; }
                public void set_waterElementalDmg(int val)      { _bit |= 0x40000; _waterElementalDmg = val; }
                public boolean has_waterElementalDmg()          { return (_bit & 0x40000) == 0x40000; }
                public int airElementalDmg()                { return _airElementalDmg; }
                public void set_airElementalDmg(int val)        { _bit |= 0x80000; _airElementalDmg = val; }
                public boolean has_airElementalDmg()            { return (_bit & 0x80000) == 0x80000; }
                public int earthElementalDmg()              { return _earthElementalDmg; }
                public void set_earthElementalDmg(int val)      { _bit |= 0x100000; _earthElementalDmg = val; }
                public boolean has_earthElementalDmg()          { return (_bit & 0x100000) == 0x100000; }
                public int lightElementalDmg()              { return _lightElementalDmg; }
                public void set_lightElementalDmg(int val)      { _bit |= 0x200000; _lightElementalDmg = val; }
                public boolean has_lightElementalDmg()          { return (_bit & 0x200000) == 0x200000; }
                public int comboDmgMulti()                  { return _comboDmgMulti; }
                public void set_comboDmgMulti(int val)          { _bit |= 0x400000; _comboDmgMulti = val; }
                public boolean has_comboDmgMulti()              { return (_bit & 0x400000) == 0x400000; }
                public int spellDmgAdd()                    { return _spellDmgAdd; }
                public void set_spellDmgAdd(int val)            { _bit |= 0x800000; _spellDmgAdd = val; }
                public boolean has_spellDmgAdd()                { return (_bit & 0x800000) == 0x800000; }

                public void saveBody(ProtoWriter w){
                    if(has_value())
                        w.int32(1, _value);
                    if(has_meleeDmg())
                        w.int32(2, _meleeDmg);
                    if(has_meleeHit())
                        w.int32(3, _meleeHit);
                    if(has_meleeCriHit())
                        w.doubleV(4, _meleeCriHit);
                    if(has_ignoreReduction())
                        w.int32(5, _ignoreReduction);
                    if(has_bloodSuckHit())
                        w.doubleV(6, _bloodSuckHit);
                    if(has_bloodSuckHeal())
                        w.int32(7, _bloodSuckHeal);
                    if(has_regenHP())
                        w.int32(8, _regenHP);
                    if(has_AC())
                        w.int32(9, _AC);
                    if(has_MR())
                        w.int32(10, _MR);
                    if(has_potionHP())
                        w.int32(11, _potionHP);
                    if(has_dmgReduction())
                        w.int32(12, _dmgReduction);
                    if(has_maxHP())
                        w.int32(13, _maxHP);
                    if(has_spellDmgMulti())
                        w.int32(14, _spellDmgMulti);
                    if(has_spellHit())
                        w.int32(15, _spellHit);
                    if(has_moveDelayReduce())
                        w.doubleV(16, _moveDelayReduce);
                    if(has_attackDelayReduce())
                        w.doubleV(17, _attackDelayReduce);
                    if(has_fireElementalDmg())
                        w.int32(18, _fireElementalDmg);
                    if(has_waterElementalDmg())
                        w.int32(19, _waterElementalDmg);
                    if(has_airElementalDmg())
                        w.int32(20, _airElementalDmg);
                    if(has_earthElementalDmg())
                        w.int32(21, _earthElementalDmg);
                    if(has_lightElementalDmg())
                        w.int32(22, _lightElementalDmg);
                    if(has_comboDmgMulti())
                        w.int32(23, _comboDmgMulti);
                    if(has_spellDmgAdd())
                        w.int32(24, _spellDmgAdd);
                }

                public void dispose(){
                    _bit = 0;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // ClassInfoT
    // ═══════════════════════════════════════════════════════════
    public static final class ClassInfoT {
        public static LIN.CompanionT.ClassInfoT newInstance(){
            return new LIN.CompanionT.ClassInfoT();
        }
        private ArrayList<LIN.CompanionT.ClassInfoT.ClassT> _Class;
        private ClassInfoT(){}

        public ArrayList<LIN.CompanionT.ClassInfoT.ClassT> Class()    { return _Class; }
        public void add_Class(LIN.CompanionT.ClassInfoT.ClassT val)       {
            if(!has_Class())
                _Class = new ArrayList<LIN.CompanionT.ClassInfoT.ClassT>();
            _Class.add(val);
        }
        public boolean has_Class()              { return _Class != null; }

        public void saveBody(ProtoWriter w){
            if(has_Class()){
                for(LIN.CompanionT.ClassInfoT.ClassT val : _Class){
                    w.msg(1);
                    val.saveBody(w);
                    w.end();
                }
            }
        }

        public void dispose(){
            if(has_Class()){
                for(LIN.CompanionT.ClassInfoT.ClassT val : _Class)
                    val.dispose();
                _Class.clear();
                _Class = null;
            }
        }

        // ── ClassT ──
        public static final class ClassT {
            public static LIN.CompanionT.ClassInfoT.ClassT newInstance(){
                return new LIN.CompanionT.ClassInfoT.ClassT();
            }
            private String _class;
            private int _classId;
            private LIN.CompanionT.eCategory _category;
            private LIN.CompanionT.eElement _element;
            private ArrayList<LIN.CompanionT.ClassInfoT.ClassT.SkillT> _Skill; 
            private int _bit;
            private ClassT(){}

            public String class_()               { return _class; }
            public void set_class_(String val)       { _bit |= 0x1; _class = val; }
            public boolean has_class_()              { return (_bit & 0x1) == 0x1; }
            public int classId()                { return _classId; }
            public void set_classId(int val)        { _bit |= 0x2; _classId = val; }
            public boolean has_classId()            { return (_bit & 0x2) == 0x2; }
            public LIN.CompanionT.eCategory category()         { return _category; }
            public void set_category(LIN.CompanionT.eCategory val) { _bit |= 0x4; _category = val; }
            public boolean has_category()           { return (_bit & 0x4) == 0x4; }
            public LIN.CompanionT.eElement element()           { return _element; }
            public void set_element(LIN.CompanionT.eElement val)   { _bit |= 0x10; _element = val; }
            public boolean has_element()            { return (_bit & 0x10) == 0x10; }
            public ArrayList<LIN.CompanionT.ClassInfoT.ClassT.SkillT> Skill()     { return _Skill; }
            public void add_Skill(LIN.CompanionT.ClassInfoT.ClassT.SkillT val)        {
                if(!has_Skill())
                    _Skill = new ArrayList<LIN.CompanionT.ClassInfoT.ClassT.SkillT>();
                _Skill.add(val);
            }
            public boolean has_Skill()              { return _Skill != null; }

            public void saveBody(ProtoWriter w){
                if(has_class_())
                    w.string(1, _class);
                if(has_classId())
                    w.uint32(2, _classId);
                if(has_category())
                    w.enumV(3, _category.toInt());
                if(has_element())
                    w.enumV(5, _element.toInt());
                if(has_Skill()){
                    for(LIN.CompanionT.ClassInfoT.ClassT.SkillT val : _Skill){
                        w.msg(6);
                        val.saveBody(w);
                        w.end();
                    }
                }
            }

            public void dispose(){
                if(has_Skill()){
                    for(LIN.CompanionT.ClassInfoT.ClassT.SkillT val : _Skill)
                        val.dispose();
                    _Skill.clear();
                    _Skill = null;
                }
                _bit = 0;
            }

            // ── SkillT (Class) — field [1] tier, repeated [2] skillId ──
            public static final class SkillT {
                public static LIN.CompanionT.ClassInfoT.ClassT.SkillT newInstance(){
                    return new LIN.CompanionT.ClassInfoT.ClassT.SkillT();
                }
                private int _tier;
                private ArrayList<Integer> _skillId;
                private int _bit;
                private SkillT(){}

                public int tier()               { return _tier; }
                public void set_tier(int val)       { _bit |= 0x1; _tier = val; }
                public boolean has_tier()           { return (_bit & 0x1) == 0x1; }
                public ArrayList<Integer> skillId()     { return _skillId; }
                public void add_skillId(int val)            {
                    if(!has_skillId())
                        _skillId = new ArrayList<Integer>();
                    _skillId.add(val);
                }
                public boolean has_skillId()        { return _skillId != null; }

                public void saveBody(ProtoWriter w){
                    if(has_tier())
                        w.uint32(1, _tier);
                    if(has_skillId()){
                        for(int val : _skillId){
                            w.uint32(2, val);
                        }
                    }
                }

                public void dispose(){
                    if(has_skillId()){
                        _skillId.clear();
                        _skillId = null;
                    }
                    _bit = 0;
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // enums — 구와 1:1 (와이어 계약)
    // eCommand는 시스템 전반에서 Lineage_CommonDataTypes.CompanionT.eCommand를 쓰고 있으므로
    // 이 파일의 enum은 그쪽과 겹칠 경우 삭제하고 CommonDataTypes 쪽으로 통일할 것
    // ═══════════════════════════════════════════════════════════
    public enum eStatType{
        STR(0),
        CON(1),
        INT_(2);
        private int value;
        eStatType(int val){
            value = val;
        }
        public int toInt(){
            return value;
        }
        public boolean equals(LIN.CompanionT.eStatType v){
            return value == v.value;
        }
        public static LIN.CompanionT.eStatType fromInt(int i){
            switch(i){
            case 0: return STR;
            case 1: return CON;
            case 2: return INT_;
            default: return null;
            }
        }
    }
    public enum eCommand{
        TM_Aggressive(2),
        TM_Defensive(3),
        TM_GetItem(6),
        TM_Attack(7),
        TM_PullBack(9),
        Dismiss(100),
        Joke(101),
        Happy(102);
        private int value;
        eCommand(int val){
            value = val;
        }
        public int toInt(){
            return value;
        }
        public boolean equals(LIN.CompanionT.eCommand v){
            return value == v.value;
        }
        public static LIN.CompanionT.eCommand fromInt(int i){
            switch(i){
            case 2: return TM_Aggressive;
            case 3: return TM_Defensive;
            case 6: return TM_GetItem;
            case 7: return TM_Attack;
            case 9: return TM_PullBack;
            case 100: return Dismiss;
            case 101: return Joke;
            case 102: return Happy;
            default: return null;
            }
        }
    }
    public enum eCategory{
        FIERCE_ANIMAL(1),
        DEVINE_BEAST(2),
        PET(3),
        WILD(4),
        DOG_FIGHT(5);
        private int value;
        eCategory(int val){
            value = val;
        }
        public int toInt(){
            return value;
        }
        public boolean equals(LIN.CompanionT.eCategory v){
            return value == v.value;
        }
        public static LIN.CompanionT.eCategory fromInt(int i){
            switch(i){
            case 1: return FIERCE_ANIMAL;
            case 2: return DEVINE_BEAST;
            case 3: return PET;
            case 4: return WILD;
            case 5: return DOG_FIGHT;
            default: return null;
            }
        }
    }
    public enum eElement{
        NONE(0),
        FIRE(1),
        WATER(2),
        AIR(3),
        EARTH(4),
        LIGHT(5);
        private int value;
        eElement(int val){
            value = val;
        }
        public int toInt(){
            return value;
        }
        public boolean equals(LIN.CompanionT.eElement v){
            return value == v.value;
        }
        public static LIN.CompanionT.eElement fromInt(int i){
            switch(i){
            case 0: return NONE;
            case 1: return FIRE;
            case 2: return WATER;
            case 3: return AIR;
            case 4: return EARTH;
            case 5: return LIGHT;
            default: return null;
            }
        }
    }
}