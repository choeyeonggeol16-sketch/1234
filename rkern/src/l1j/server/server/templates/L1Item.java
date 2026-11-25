package l1j.server.server.templates;

import java.io.Serializable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.CommonUtil;
import l1j.server.server.utils.SpecialStats;

public abstract class L1Item implements Serializable {

	private static final long serialVersionUID = 1L;

	public L1Item() {
	}

	// ■■■■■■ L1EtcItem, L1Weapon, L1Armor 에 공통되는 항목 ■■■■■■

	private int _type2; //  0=L1EtcItem, 1=L1Weapon, 2=L1Armor

	/**
	 * @return 0 if L1EtcItem, 1 if L1Weapon, 2 if L1Armor
	 */
	public int getType2() {
		return _type2;
	}

	public void setType2(int type) {
		_type2 = type;
	}

	private int _itemId;

	public int getItemId() {
		return _itemId;
	}

	public void setItemId(int itemId) {
		_itemId = itemId;
	}

	private String _name;

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		_name = name;
	}
	
	private String _Magic_name;

	public String getMagicName() {
		return _Magic_name;
	}

	public void setMagicName(String name) {
		_Magic_name = name;
	}
	
	private String _nameId;

	public String getNameId() {
		return _nameId;
	}

	public void setNameId(String nameid) {
		_nameId = nameid;
	}

	private int _type;

	/**
	 * 아이템의 종류를 돌려준다.<br>
	 * 
	 * @return
	 * <p>
	 * [etcitem]<br>
	 * 0:arrow, 1:wand, 2:light, 3:gem, 4:totem, 5:firecracker, 6:potion,
	 * 7:food, 8:scroll, 9:questitem, 10:spellbook, 11:petitem, 12:other,
	 * 13:material, 14:event, 15:sting, 17: teleport_scroll
	 * </p>
	 * <p>
	 * [weapon]<br>
	 * 1:sword, 2:dagger, 3:tohandsword, 4:bow, 5:spear, 6:blunt, 7:staff,
	 * 8:throwingknife, 9:arrow, 10:gauntlet, 11:claw, 12:edoryu, 13:singlebow,
	 * 14:singlespear, 15:tohandblunt, 16:tohandstaff, 17:keyring, 18:chainsword
	 * </p>
	 * <p>
	 * [armor]<br>
	 * 1:helm, 2:armor, 3:T, 4:cloak, 5:glove, 6:boots, 7:shield, 8:amulet,
	 * 9:ring, 10:belt, 11:ring2, 12:earring
	 */
	public int getType() {
		return _type;
	}

	public void setType(int type) {
		_type = type;
	}

	private int _type1;

	/**
	 * 아이템의 종류를 돌려준다.<br>
	 * 
	 * @return
	 * <p>
	 * [weapon]<br>
	 * sword:4, dagger:46, tohandsword:50, bow:20, blunt:11, spear:24, staff:40,
	 * throwingknife:2922, arrow:66, gauntlet:62, claw:58, edoryu:54,
	 * singlebow:20, singlespear:24, tohandblunt:11, tohandstaff:40
	 * </p>
	 */
	public int getType1() {
		return _type1;
	}

	public void setType1(int type1) {
		_type1 = type1;
	}

	private int _material;

	/**
	 * 아이템의 소재를 돌려준다
	 *
	 * @return 0:none 1:액체 2:web 3:식물성 4:동물성 5:지 6:포 7:피 8:목 9:골 10:룡의 린 11:철
	 *         12:강철 13:동 14:은 15:금 16:플라티나 17:미스릴 18:브락크미스릴 19:유리 20:보석
	 *         21:광물 22:오리하르콘
	 */
	public int getMaterial() {
		return _material;
	}

	public void setMaterial(int material) {
		_material = material;
	}

	private volatile int _weight;

    // Templates normally become immutable after loading; rare in-place weight
    // edits must also invalidate inventories using the existing template object.
    private static final java.util.concurrent.atomic.AtomicLong WEIGHT_EPOCH =
            new java.util.concurrent.atomic.AtomicLong();

    public static long WeightEpoch() { return WEIGHT_EPOCH.get(); }

	public int getWeight() {
		return _weight;
	}

	public void setWeight(int weight) {
        if (_weight == weight) return;
		_weight = weight;
        WEIGHT_EPOCH.incrementAndGet();
	}

	private int _gfxId;

	public int getGfxId() {
		return _gfxId;
	}

	public void setGfxId(int gfxId) {
		_gfxId = gfxId;
	}

	private int _groundGfxId;

	public int getGroundGfxId() {
		return _groundGfxId;
	}

	public void setGroundGfxId(int groundGfxId) {
		_groundGfxId = groundGfxId;
	}

	private int _minLevel;

	private int _itemDescId;

	/**
	 * 감정시에 표시되는 ItemDesc.tbl의 메세지 ID를 돌려준다.
	 */
	public int getItemDescId() {
		return _itemDescId;
	}

	public void setItemDescId(int descId) {
		_itemDescId = descId;
	}

	public int getMinLevel() {
		return _minLevel;
	}

	public void setMinLevel(int level) {
		_minLevel = level;
	}

	private int _maxLevel;

	public int getMaxLevel() {
		return _maxLevel;
	}

	public void setMaxLevel(int maxlvl) {
		_maxLevel = maxlvl;
	}

	private int _bless;

	public int getBless() {
		return _bless;
	}

	public void setBless(int i) {
		_bless = i;
	}

	private boolean _tradable;

	public boolean isTradable() {
		return _tradable;
	}

	public void setTradable(boolean flag) {
		_tradable = flag;
	}

	private boolean _cantDelete;

	public boolean isCantDelete() {
		return _cantDelete;
	}

	public void setCantDelete(boolean flag) {
		_cantDelete = flag;
	}

	private boolean _save_at_once;
	
	private int _장신구처리;

	public int get장신구처리() {
		return _장신구처리;
	}

	public void set장신구처리(int i) {
		_장신구처리 = i;
	}

	/**
	 * 아이템의 개수가 변화했을 때에 곧바로 DB에 기입해야할 것인가를 돌려준다.
	 */
	public boolean isToBeSavedAtOnce() {
		return _save_at_once;
	}

	public void setToBeSavedAtOnce(boolean flag) {
		_save_at_once = flag;
	}

	// ■■■■■■ L1EtcItem, L1Weapon 에 공통되는 항목 ■■■■■■


	private int _dmgSmall = 0; 

	public int getDmgSmall() {
		return _dmgSmall;
	}

	public void setDmgSmall(int dmgSmall) {
		_dmgSmall = dmgSmall;
	}

	private int _dmgLarge = 0;

	public int getDmgLarge() {
		return _dmgLarge;
	}

	public void setDmgLarge(int dmgLarge) {
		_dmgLarge = dmgLarge;
	}

	// ■■■■■■ L1EtcItem, L1Armor 에 공통되는 항목 ■■■■■■

	// ■■■■■■ L1Weapon, L1Armor 에 공통되는 항목 ■■■■■■

	private int _safeEnchant = 0;

	public int get_safeenchant() {
		return _safeEnchant;
	}

	public void set_safeenchant(int safeenchant) {
		_safeEnchant = safeenchant;
	}

	private boolean _useRoyal = false;

	public boolean isUseRoyal() {
		return _useRoyal;
	}

	public void setUseRoyal(boolean flag) {
		_useRoyal = flag;
	}

	private boolean _useKnight = false;

	public boolean isUseKnight() {
		return _useKnight;
	}

	public void setUseKnight(boolean flag) {
		_useKnight = flag;
	}

	private boolean _useElf = false;

	public boolean isUseElf() {
		return _useElf;
	}

	public void setUseElf(boolean flag) {
		_useElf = flag;
	}

	private boolean _useMage = false;

	public boolean isUseMage() {
		return _useMage;
	}

	public void setUseMage(boolean flag) {
		_useMage = flag;
	}

	private boolean _useDarkelf = false;

	public boolean isUseDarkelf() {
		return _useDarkelf;
	}

	public void setUseDarkelf(boolean flag) {
		_useDarkelf = flag;
	}
	
	private boolean _useDragonKnight = false;

	public boolean isUseDragonKnight() {
		return _useDragonKnight;
	}

	public void setUseDragonKnight(boolean flag) {
		_useDragonKnight = flag;
	}
	
	private boolean _useBlackwizard = false;

	public boolean isUseBlackwizard() {
		return _useBlackwizard;
	}
	
	private boolean _use전사 = false;

	public boolean isUse전사() {
		return _use전사;
	}
	public void setUse전사(boolean flag) {
		_use전사 = flag;
	}
	
	private boolean _useHighPet = false;
	public boolean isUseHighPet() {	return _useHighPet;	}
	public void setUseHighPet(boolean flag) {	_useHighPet = flag;	}

	public void setUseBlackwizard(boolean flag) {
		_useBlackwizard = flag;
	}

	private byte _addstr = 0;

	public byte get_addstr() {
		return _addstr;
	}

	public void set_addstr(byte addstr) {
		_addstr = addstr;
	}

	private byte _adddex = 0;

	public byte get_adddex() {
		return _adddex;
	}

	public void set_adddex(byte adddex) {
		_adddex = adddex;
	}

	private byte _addcon = 0; 

	public byte get_addcon() {
		return _addcon;
	}

	public void set_addcon(byte addcon) {
		_addcon = addcon;
	}
		
	private byte _addexp1 = 0; 

	public byte get_addexp1() {
		return _addexp1;
	}

	public void set_addexp1(byte addexp1) {
		_addexp1 = addexp1;
	}

	private byte _addint = 0; 

	public byte get_addint() {
		return _addint;
	}

	public void set_addint(byte addint) {
		_addint = addint;
	}

	private byte _addwis = 0;

	public byte get_addwis() {
		return _addwis;
	}

	public void set_addwis(byte addwis) {
		_addwis = addwis;
	}

	private byte _addcha = 0;

	public byte get_addcha() {
		return _addcha;
	}

	public void set_addcha(byte addcha) {
		_addcha = addcha;
	}

	private int _addhp = 0;

	public int get_addhp() {
		return _addhp;
	}

	public void set_addhp(int addhp) {
		_addhp = addhp;
	}

	private int _addmp = 0;

	public int get_addmp() {
		return _addmp;
	}

	public void set_addmp(int addmp) {
		_addmp = addmp;
	}

	private int _addhpr = 0;

	public int get_addhpr() {
		return _addhpr;
	}

	public void set_addhpr(int addhpr) {
		_addhpr = addhpr;
	}

	private int _addmpr = 0;

	public int get_addmpr() {
		return _addmpr;
	}

	public void set_addmpr(int addmpr) {
		_addmpr = addmpr;
	}

	private int _addsp = 0;

	public int get_addsp() {
		return _addsp;
	}

	public void set_addsp(int addsp) {
		_addsp = addsp;
	}
	
	/** 2017-11-06 아인하사드 축복 감소 확률 리뉴얼 **/
	private int _addeinhasadper = 0;

	public int get_addeinhasadper() {
		return _addeinhasadper;
	}

	public void set_addeinhasadper(int einhasad) {
		_addeinhasadper = einhasad;
	}
	/** 2017-11-06 아인하사드 축복 감소 확률 리뉴얼 **/

	private int _mdef = 0;

	public int get_mdef() {
		return _mdef;
	}

	public void set_mdef(int i) {
		this._mdef = i;
	}

	private boolean _isHasteItem = false;

	public boolean isHasteItem() {
		return _isHasteItem;
	}

	public void setHasteItem(boolean flag) {
		_isHasteItem = flag;
	}
	
	private int _maxUseTime = 0;

	public int getMaxUseTime() {
		return _maxUseTime;
	}

	public void setMaxUseTime(int i) {
		_maxUseTime = i;
	}

	private int _useType;

	/**
	 * 사용했을 때의 리액션을 결정하는 타입을 돌려준다.
	 */
	public int getUseType() {
		return _useType;
	}

	public void setUseType(int useType) {
		_useType = useType;
	}

	private int _foodVolume;

	/**
	 * 고기등의 아이템으로 설정되어 있는 만복도를 돌려준다.
	 */
	public int getFoodVolume() {
		return _foodVolume;
	}

	public void setFoodVolume(int volume) {
		_foodVolume = volume;
	}

	/**
	 * 램프등의 아이템으로 설정되어 있는 밝음을 돌려준다.
	 */
	public int getLightRange() {
		if (_itemId == 40001) {
			return 11;
		} else if (_itemId == 7005) {
			return 14;
		} else if (_itemId == 40002) {
			return 14;
		} else if (_itemId == 40004) {
			return 14;
		} else if (_itemId == 40005) {
			return 8;
		} else {
			return 0;
		}
	}
	
	public int getLightFuel() {
		if (_itemId == 40001) { 
			return 600;
		} else if (_itemId == 7005) {
			return 600;
		} else if (_itemId == 40002) { 
			return 0;
		} else if (_itemId == 40003) { 
			return 600;
		} else if (_itemId == 40004) { 
			return 0;
		} else if (_itemId == 40005) { 
			return 600;
		} else {
			return 0;
		}
	}

	// ■■■■■■ L1EtcItem 로 오버라이드(override) 하는 항목 ■■■■■■
	public boolean isStackable() {
		return false;
	}

	public int get_locx() {
		return 0;
	}

	public int get_locy() {
		return 0;
	}

	public short get_mapid() {
		return 0;
	}

	public int get_delayid() {
		return 0;
	}

	public int get_delaytime() {
		return 0;
	}

	public int getMaxChargeCount() {
		return 0;
	}
	
	public int get_delayEffect() {
		return 0;
	}

	// ■■■■■■ L1Weapon 로 오버라이드(override) 하는 항목 ■■■■■■
		
	private int _hitModifier = 0; 
	public int getHitModifier() {
		return _hitModifier;
	}
	public void setHitModifier(int i) {
		_hitModifier = i;
	}
	
	private int _dmgModifier = 0;

	public int getDmgModifier() {
		return _dmgModifier;
	}

	public void setDmgModifier(int i) {
		_dmgModifier = i;
	}

	public int getDoubleDmgChance() {
		return 0;
	}

	public int getMagicDmgModifier() {
		return 0;
	}

	public int get_canbedmg() {
		return 0;
	}

	public boolean isTwohandedWeapon() {
		return false;
	}

	// ■■■■■■ L1Armor 로 오버라이드(override) 하는 항목 ■■■■■■
	public int get_ac() {
		return 0;
	}

//	public int getDamageReduction() {
//		return 0;
//	}

	public int getWeightReduction() {
		return 0;
	}

	public int getDmgRate() {
		return 0;
	}
	
	public int getHitRate() {
		return 0;
	}
	
	public int getBowHitRate() {
		return 0;
	}
	
	public int getBowDmgRate() {
		return 0;
	}

	public int get_defense_water() {
		return 0;
	}

	public int get_defense_fire() {
		return 0;
	}

	public int get_defense_earth() {
		return 0;
	}

	public int get_defense_wind() {
		return 0;
	}
	
	public int get_regist_calcPcDefense() {
		return 0;
	}
	
	public int get_regist_PVPweaponTotalDamage() {
		return 0;
	}
		
	private int _grade; // ● 장신구 단계

	public int getGrade() {
		return _grade;
	}

	public void setGrade(int grade) {
		_grade = grade;
	}

	private int _price; // ● 가격
	
	public int get_price() {
		return _price;
	}

	public void set_price(int price) {
		_price = price;
	}
	
	private int damage_reduction;
	public int get_damage_reduction() {
		return damage_reduction;
	}
	public void set_damage_reduction(int i) {
		this.damage_reduction = CommonUtil.get_current(i, 0, 30);
	}
	
	private boolean _isEndedTimeMessage;
	public boolean isEndedTimeMessage(){
		return _isEndedTimeMessage;
	}
	
	public void setEndedTimeMessage(boolean b){
		_isEndedTimeMessage = b;
	}
	
	private int _missile_critical_probability;
	public void set_missile_critical_probability(int missile_critical_probability){
		_missile_critical_probability = missile_critical_probability;
	}
	
	public int get_missile_critical_probability(){
		return _missile_critical_probability;
	}
	private int _melee_critical_probability;
	public void set_melee_critical_probability(int melee_critical_probability){
		_melee_critical_probability = melee_critical_probability;
	}
	
	public int get_melee_critical_probability(){
		return _melee_critical_probability;
	}
	
	protected final SpecialStats _specialStats = new SpecialStats();
	public SpecialStats getSpecialStats() { return _specialStats; }

	
	public abstract int getMagicHitup();
	public abstract void setMagicHitup(int i);
	
	public abstract void equipmentItem(L1PcInstance pc, boolean isEquipped);
	
	
	
}
