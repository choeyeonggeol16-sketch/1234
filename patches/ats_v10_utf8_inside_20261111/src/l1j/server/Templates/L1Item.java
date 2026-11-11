/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.Templates;

import java.io.Serializable;

public abstract class L1Item implements Serializable {

	private static final long serialVersionUID = 1L;

	public L1Item() {
	}
    // 아이템 카테고리 
	public static final int CATEGORY_ETC = 0;
	public static final int CATEGORY_WEAPON = 1;
	public static final int CATEGORY_ARMOR = 2;

	// ■■■■■■ L1EtcItem, L1Weapon, L1Armor 에 공통되는 항목 ■■■■■■

	private int _type2; // ● 0=L1EtcItem, 1=L1Weapon, 2=L1Armor

	public static final int BLESS_CURSE = 2;
	/**
	 * @return 0 if L1EtcItem, 1 if L1Weapon, 2 if L1Armor
	 */
	////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////// 아이템 타입 /////////////////////////////////////////////
	public static final int TYPE_ETCITEM_ARROW = 0;
	public static final int TYPE_ETCITEM_WAND = 1;
	public static final int TYPE_ETCITEM_LIGHT = 2;
	public static final int TYPE_ETCITEM_GAM = 3;
	public static final int TYPE_ETCITEM_TOTEM = 4; //firecracker
	public static final int TYPE_ETCITEM_FIRECRACKER = 5; // potion
	public static final int TYPE_ETCITEM_POTION = 6;	
	public static final int TYPE_ETCITEM_FOOD = 7;
	public static final int TYPE_ETCITEM_SCROLL = 8;
	public static final int TYPE_ETCITEM_QUESTITEM = 9;
	public static final int TYPE_ETCITEM_SPELLBOOK = 10;
	public static final int TYPE_ETCITEM_PETITEM = 11;
	public static final int TYPE_ETCITEM_OTHER = 12;
	public static final int TYPE_ETCITEM_MATERIAL = 13;
	public static final int TYPE_ETCITEM_EVENT = 14;
	public static final int TYPE_ETCITEM_STING = 15;
	public static final int TYPE_ETCITEM_TREASURE_BOX = 16;
	public static final int TYPE_ETCITEM_SPELLSCR = 18;
	public static final int TYPE_ETCITEM_SPELLSCRV = 19;
	public static final int TYPE_ETCITEM_BLESSING = 501;
	public static final int TYPE_ETCITEM_JEWEL = 23;
	public static final int TYPE_ETCITEM_CONTROLLER = 80;
	public static final int TYPE_ETCITEM_HEALING_POTION = 81;
	
	////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////// 유즈 타입 /////////////////////////////////////////////
	public static final int TYPE_USE_NONE = -1;
	public static final int TYPE_USE_NORMAL = 0;
	public static final int TYPE_USE_WEAPON = 1;
	public static final int TYPE_USE_ARMOR = 2;
	public static final int TYPE_USE_SPELL_LONG = 5;
	public static final int TYPE_USE_NTELE = 6;
	public static final int TYPE_USE_IDENTIFY = 7;
	public static final int TYPE_USE_RES = 8;
	public static final int TYPE_USE_LETTER = 12;
	public static final int TYPE_USE_LETTER_W = 13;
	public static final int TYPE_USE_CHOICE = 14;
	public static final int TYPE_USE_INSTRUMENT = 15;
	public static final int TYPE_USE_SOSC = 16;
	public static final int TYPE_USE_SPELL_SHORT = 17;
	public static final int TYPE_USE_T = 18;
	public static final int TYPE_USE_CLOAK = 19;
	public static final int TYPE_USE_GLOVE = 20;
	public static final int TYPE_USE_BOOTS = 21;
	public static final int TYPE_USE_HELM = 22;
	public static final int TYPE_USE_RING = 23;
	public static final int TYPE_USE_AMULET = 24;
	public static final int TYPE_USE_SHIELD = 25;
	public static final int TYPE_USE_GARDER = 25;
	public static final int TYPE_USE_DAL = 26;
	public static final int TYPE_USE_ZEL = 27;
	public static final int TYPE_USE_BLANK = 28;
	public static final int TYPE_USE_BTELE = 29;
	public static final int TYPE_USE_SPELL_BUFF = 30;	
	public static final int TYPE_USE_CCARD = 31;
	public static final int TYPE_USE_CCARD_W = 32;
	public static final int TYPE_USE_VCARD = 33;
	public static final int TYPE_USE_VCARD_W = 34;
	public static final int TYPE_USE_WCARD = 35;
	public static final int TYPE_USE_WCARD_W = 36;
	public static final int TYPE_USE_BELT = 37;
	public static final int TYPE_USE_EARRING = 40;
	public static final int TYPE_USE_FISHING_ROD = 42;
	public static final int TYPE_USE_RON = 44;
	public static final int TYPE_USE_ACCZEL = 46;
	public static final int TYPE_USE_HEALING = 51;
	public static final int TYPE_USE_POLY = 70;
	public static final int TYPE_USE_NAGIC_DOLL = 73;
	public static final int TYPE_USE_MAP = 75;
	public static final int TYPE_USE_FOOD = 76;
	public static final int TYPE_USE_OMANTELL = 77;


	////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////// 아머 타입 /////////////////////////////////////////////
	public static final int TYPE_ARMOR_NONE = 0;
	public static final int TYPE_ARMOR_HELM = 1;
	public static final int TYPE_ARMOR_ARMOR = 2;
	public static final int TYPE_ARMOR_T = 3;
	public static final int TYPE_ARMOR_CLOAK = 4;
	public static final int TYPE_ARMOR_GLOVE = 5;
	public static final int TYPE_ARMOR_BOOTS = 6;
	public static final int TYPE_ARMOR_SHIELD = 7;
	public static final int TYPE_ARMOR_AMULET = 8;
	public static final int TYPE_ARMOR_RING = 9;
	public static final int TYPE_ARMOR_BELT = 10;
	public static final int TYPE_ARMOR_RING_TWO = 11;
	public static final int TYPE_ARMOR_EARRING = 12;
	public static final int TYPE_ARMOR_GARDER = 13;
	public static final int TYPE_ARMOR_RON = 14;

	////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////// 무기 타입 /////////////////////////////////////////////

	public static final int TYPE_WEAPON_SWORD = 1;
	public static final int TYPE_WEAPON_DAGGER = 2;
	public static final int TYPE_WEAPON_TOHANDSWORD = 3;
	public static final int TYPE_WEAPON_BOW = 4;
	public static final int TYPE_WEAPON_SPEAR = 5;
	public static final int TYPE_WEAPON_BLUNT = 6;
	public static final int TYPE_WEAPON_STAFF = 7;
	public static final int TYPE_WEAPON_TGROWINMGKNIFE = 8;
	public static final int TYPE_WEAPON_ARROW = 9;
	public static final int TYPE_WEAPON_GAUNTLET = 10;
	public static final int TYPE_WEAPON_CLAW = 11;
	public static final int TYPE_WEAPON_EDORYU = 12;
	public static final int TYPE_WEAPON_SINGLEBOW = 13;
	public static final int TYPE_WEAPON_SINGLESPEAR = 14;
	public static final int TYPE_WEAPON_TOHANDBLUNT = 15;
	public static final int TYPE_WEAPON_TOHANDSTAFF = 16;

	////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////// 무기 아이디 타입 /////////////////////////////////////////////
	public static final int TYPE_WEAPONID_SWORD = 4;
	public static final int TYPE_WEAPONID_DAGGER = 46;
	public static final int TYPE_WEAPONID_TOHANDSWORD = 50;
	public static final int TYPE_WEAPONID_BOW = 20;
	public static final int TYPE_WEAPONID_BLUNT = 11;
	public static final int TYPE_WEAPONID_SPEAR = 24;
	public static final int TYPE_WEAPONID_STAFF = 40;
	public static final int TYPE_WEAPONID_THROWINGKNIFE = 2922;
	public static final int TYPE_WEAPONID_ARROW = 66;
	public static final int TYPE_WEAPONID_GAUNTLET = 62;
	public static final int TYPE_WEAPONID_CLAW = 58;
	public static final int TYPE_WEAPONID_EDORYU = 54;
	public static final int TYPE_WEAPONID_SINGLE_BOW = 20;
	public static final int TYPE_WEAPONID_SINGLE_SPEAR = 24;
	public static final int TYPE_WEAPONID_TOHAND_BLUNT = 11;
	public static final int TYPE_WEAPONID_TOHAND_STAFF = 40;

	////////////////////////////////////////////////////////////////////////////////////////////////////
	//////////////////////////////////////////// 재질 타입 /////////////////////////////////////////////

	public static final int TYPE_MATARIAL_NONE = 0;
	public static final int TYPE_MATARIAL_LIQUID = 1;
	public static final int TYPE_MATARIAL_WED = 2;
	public static final int TYPE_MATARIAL_VEGETATION = 3;
	public static final int TYPE_MATARIAL_ANIMALMATTER = 4;
	public static final int TYPE_MATARIAL_PAPER = 5;
	public static final int TYPE_MATARIAL_CLOTH = 6;
	public static final int TYPE_MATARIAL_LEATHER = 7;
	public static final int TYPE_MATARIAL_WOOD = 8;
	public static final int TYPE_MATARIAL_BONE = 9;
	public static final int TYPE_MATARIAL_DRAGONSCALE = 10;
	public static final int TYPE_MATARIAL_IRON = 11;
	public static final int TYPE_MATARIAL_STEEL = 12;
	public static final int TYPE_MATARIAL_COPPER = 13;
	public static final int TYPE_MATARIAL_SILVER = 14;
	public static final int TYPE_MATARIAL_GOLD = 15;
	public static final int TYPE_MATARIAL_PLATINUM = 16;
	public static final int TYPE_MATARIAL_MITHRIL = 17;
	public static final int TYPE_MATARIAL_BLACKMITHRIL = 18;
	public static final int TYPE_MATARIAL_GLASS = 19;
	public static final int TYPE_MATARIAL_GEMSTONE = 20;
	public static final int TYPE_MATARIAL_MINERAL = 21;
	public static final int TYPE_MATARIAL_ORIHARUKON = 22;
	
	
	// ■■■■■■ L1EtcItem, L1Weapon, L1Armor 에 공통되는 항목 ■■■■■■

	public int getType2() {
		return _type2;
	}

	public void setType2(int type) {
		_type2 = type;
	}

	private int _itemId; // ● 아이템 ID

	public int getItemId() {
		return _itemId;
	}

	public void setItemId(int itemId) {
		_itemId = itemId;
	}

	private String _name; // ● 아이템명

	public String getName() {
		return _name;
	}

	public void setName(String name) {
		_name = name;
	}

	private String _nameId; // ● 네임 ID

	public String getNameId() {
		return _nameId;
	}

	public void setNameId(String nameid) {
		_nameId = nameid;
	}



	private int _type; // ● 상세한 타입

	/**
	 * 아이템의 종류를 돌려준다. <br>
	 *
	 * @return
	 * <p>
	 * [etcitem]<br>
	 * 0:arrow, 1:wand, 2:light, 3:gem, 4:totem, 5:firecracker, 6:potion,
	 * 7:food, 8:scroll, 9:questitem, 10:spellbook, 11:petitem, 12:other,
	 * 13:material, 14:event, 15:sting
	 * </p>
	 * <p>
	 * [weapon]<br>
	 * 1:sword, 2:dagger, 3:tohandsword, 4:bow, 5:spear, 6:blunt, 7:staff,
	 * 8:throwingknife, 9:arrow, 10:gauntlet, 11:claw, 12:edoryu, 13:singlebow,
	 * 14:singlespear, 15:tohandblunt, 16:tohandstaff
	 * </p>
	 * <p>
	 * [armor]<br>
	 * 1:helm, 2:armor, 3:T, 4:cloak, 5:glove, 6:boots, 7:shield, 8:amulet,
	 * 9:ring, 10:belt, 11:ring2, 12:earring
	 * 

목걸이	8
반지		9
벨트		10
귀걸이	12

	 */
	public int getType() {
		return _type;
	}

	public void setType(int type) {
		_type = type;
	}

	private int _type1; // ● 타입

	/**
	 * 아이템의 종류를 돌려준다. <br>
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

	private int _material; // ● 소재

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

	private int _weight; // ● 중량

	public int getWeight() {
		return _weight;
	}

	public void setWeight(int weight) {
		_weight = weight;
	}

	private int _gfxId; // ● 인벤트리내의 그래픽 ID

	public int getGfxId() {
		return _gfxId;
	}

	public void setGfxId(int gfxId) {
		_gfxId = gfxId;
	}

	private int _groundGfxId; // ● 지면에 두었을 때의 그래픽 ID

	public int getGroundGfxId() {
		return _groundGfxId;
	}

	public void setGroundGfxId(int groundGfxId) {
		_groundGfxId = groundGfxId;
	}

	private int _minLevel; // ● 사용, 장비 가능 최소 LV

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

	private int _maxLevel; // ● 사용, 장비 가능 최대 LV

	public int getMaxLevel() {
		return _maxLevel;
	}

	public void setMaxLevel(int maxlvl) {
		_maxLevel = maxlvl;
	}

	private int _bless; // ● 축복 상태

	public int getBless() {
		return _bless;
	}

	public void setBless(int i) {
		_bless = i;
	}

	private boolean _tradable; // ● 트레이드가능/불가

	public boolean isTradable() {
		return _tradable;
	}

	public void setTradable(boolean flag) {
		_tradable = flag;
	}

	private boolean _cantDelete; // ● 삭제 금지

	public boolean isCantDelete() {
		return _cantDelete;
	}

	public void setCantDelete(boolean flag) {
		_cantDelete = flag;
	}

	private boolean _save_at_once;

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

	private int _dmgSmall = 0; // ● 최소 데미지

	public int getDmgSmall() {
		return _dmgSmall;
	}

	public void setDmgSmall(int dmgSmall) {
		_dmgSmall = dmgSmall;
	}

	private int _dmgLarge = 0; // ● 최대 데미지

	public int getDmgLarge() {
		return _dmgLarge;
	}

	public void setDmgLarge(int dmgLarge) {
		_dmgLarge = dmgLarge;
	}

	// ■■■■■■ L1EtcItem, L1Armor 에 공통되는 항목 ■■■■■■

	// ■■■■■■ L1Weapon, L1Armor 에 공통되는 항목 ■■■■■■

	private int _safeEnchant = 0; // ● OE안전권

	public int get_safeenchant() {
		return _safeEnchant;
	}

	public void set_safeenchant(int safeenchant) {
		_safeEnchant = safeenchant;
	}

	private boolean _useRoyal = false; // ● 로열 클래스를 장비 할 수 있을까

	public boolean isUseRoyal() {
		return _useRoyal;
	}

	public void setUseRoyal(boolean flag) {
		_useRoyal = flag;
	}

	private boolean _useKnight = false; // ● 나이트 클래스를 장비 할 수 있을까

	public boolean isUseKnight() {
		return _useKnight;
	}

	public void setUseKnight(boolean flag) {
		_useKnight = flag;
	}

	private boolean _useElf = false; // ● 에르프 클래스를 장비 할 수 있을까

	public boolean isUseElf() {
		return _useElf;
	}

	public void setUseElf(boolean flag) {
		_useElf = flag;
	}

	private boolean _useMage = false; // ● 메이지크라스를 장비 할 수 있을까

	public boolean isUseMage() {
		return _useMage;
	}

	public void setUseMage(boolean flag) {
		_useMage = flag;
	}

	private boolean _useDarkelf = false; // ● 다크 에르프 클래스를 장비 할 수 있을까

	public boolean isUseDarkelf() {
		return _useDarkelf;
	}

	public void setUseDarkelf(boolean flag) {
		_useDarkelf = flag;
	}

	private byte _addstr = 0; // ● STR 보정

	public byte get_addstr() {
		return _addstr;
	}

	public void set_addstr(byte addstr) {
		_addstr = addstr;
	}

	private byte _adddex = 0; // ● DEX 보정

	public byte get_adddex() {
		return _adddex;
	}

	public void set_adddex(byte adddex) {
		_adddex = adddex;
	}

	private byte _addcon = 0; // ● CON 보정

	public byte get_addcon() {
		return _addcon;
	}

	public void set_addcon(byte addcon) {
		_addcon = addcon;
	}

	private byte _addint = 0; // ● INT 보정

	public byte get_addint() {
		return _addint;
	}

	public void set_addint(byte addint) {
		_addint = addint;
	}

	private byte _addwis = 0; // ● WIS 보정

	public byte get_addwis() {
		return _addwis;
	}

	public void set_addwis(byte addwis) {
		_addwis = addwis;
	}

	private byte _addcha = 0; // ● CHA 보정

	public byte get_addcha() {
		return _addcha;
	}

	public void set_addcha(byte addcha) {
		_addcha = addcha;
	}

	private int _addhp = 0; // ● HP보정

	public int get_addhp() {
		return _addhp;
	}

	public void set_addhp(int addhp) {
		_addhp = addhp;
	}

	private int _addmp = 0; // ● MP보정

	public int get_addmp() {
		return _addmp;
	}

	public void set_addmp(int addmp) {
		_addmp = addmp;
	}

	private int _addhpr = 0; // ● HPR 보정

	public int get_addhpr() {
		return _addhpr;
	}

	public void set_addhpr(int addhpr) {
		_addhpr = addhpr;
	}

	private int _addmpr = 0; // ● MPR 보정

	public int get_addmpr() {
		return _addmpr;
	}

	public void set_addmpr(int addmpr) {
		_addmpr = addmpr;
	}

	private int _addsp = 0; // ● SP보정

	public int get_addsp() {
		return _addsp;
	}

	public void set_addsp(int addsp) {
		_addsp = addsp;
	}

	private int _mdef = 0; // ● MR

	public int get_mdef() {
		return _mdef;
	}

	public void set_mdef(int i) {
		this._mdef = i;
	}

	private boolean _isHasteItem = false; // ● 헤이 파업 효과의 유무

	public boolean isHasteItem() {
		return _isHasteItem;
	}

	public void setHasteItem(boolean flag) {
		_isHasteItem = flag;
	}
	
	private boolean _isHpDrain = false; 
	/**
	 * hp 흡수 아이템 체크 
	 */
	public boolean isHpDrain() {
		return _isHpDrain;
	}
	/**
	 * hp 흡수 아이템 체크 
	 */
	public void setHpDrain(boolean flag) {
		_isHpDrain = flag;
	}
	
	private boolean _isMpDrain = false; 
	/**
	 * Mp 흡수 아이템 체크 
	 */
	public boolean isMpDrain() {
		return _isMpDrain;
	}
	/**
	 * Mp 흡수 아이템 체크 
	 */
	public void setMpDrain(boolean flag) {
		_isMpDrain = flag;
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
		if (_itemId == 40001) { // 램프
			return 11;
		} else if (_itemId == 40002) { // 랜턴
			return 13;
		} else if (_itemId == 40004) { // 매직 랜턴
			return 13;
		} else if (_itemId == 40005) { // 양초
			return 8;
		} else {
			return 0;
		}
	}
    /**
     * 마법검 마법 발동 표기 
     */
	private String _Magic_name;

	public String getMagicName() {
		return _Magic_name;
	}

	public void setMagicName(String name) {
		_Magic_name = name;
	}
	public int getLightFuel() {
		if (_itemId == 40001) { 
			return 600;
		} else if (_itemId == 40002) { 
			return 600;
		} else if (_itemId == 40003) { 
			return 600;
		} else if (_itemId == 40004) { 
			return 600;
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
	public int getHitModifier() {
		return 0;
	}

	public int getDmgModifier() {
		return 0;
	}
	public int getDoubleDmgChance() {
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
	public int getProbMagicEvasion() {
		return 0;
	}
	public int getDamageReduction() {
		return 0;
	}
	public int getWeightReduction() {
		return 0;
	}

	public int getMeleeDmg() {
		return 0;
	}

	public int getHitRate() {
		return 0;
	}

	public int getBowHitRate() {
		return 0;
	}

	public int getRangedDmg() {
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



	private int _price; // ● 가격

	public int get_price() {
		return _price;
	}

	public void set_price(int price) {
		_price = price;
	}


	private int _regist_technology;
	private int _technique_hit;
	private int _regist_spirit;
	private int _spirit_hit;
	private int _magic_hit;

	public int getregist_technology() {
		return this._regist_technology;
	}

	public void setregist_technology(int i) {
		this._regist_technology = i;
	}

	public int gettechnique_hit() {
		return this._technique_hit;
	}

	public void settechnique_hit(int i) {
		this._technique_hit = i;
	}

	public int getregist_spirit() {
		return this._regist_spirit;
	}

	public void setregist_spirit(int i) {
		this._regist_spirit = i;
	}

	public int getspirit_hit() {
		return this._spirit_hit;
	}

	public void setspirit_hit(int i) {
		this._spirit_hit = i;
	}

	public int getmagic_hit() {
		return this._magic_hit;
	}

	public void setmagic_hit(int i) {
		this._magic_hit = i;
	}
	private int _MeleeCritical;
	public int getMeleeCritical() {
		return _MeleeCritical;
	}
	public void setMeleeCritical(int 근거리치명타) { 
		_MeleeCritical = 근거리치명타;
	}
	private int _RangedCritical;
	public int getRangedCritical() {
		return _RangedCritical;
	}
	public void setRangedCritical(int 원거리치명타) { 
		_RangedCritical = 원거리치명타;
	}
	private int _MagicCritical;
	public int getMagicCritical() {
		return _MagicCritical;
	}
	public void setMagicCritical(int 마법치명타) { 
		_MagicCritical = 마법치명타;
	}
	private int _PvpReduction;
	public int getPvpReduction() {
		return _PvpReduction;
	}
	public void setPvpReduction(int 데미지감소) { 
		_PvpReduction = 데미지감소;
	}
	private int _PvpDmg;
	public int getPvpDmg() {
		return _PvpDmg;
	}
	public void setPvpDmg(int 데미지) {
		_PvpDmg = 데미지;
	}
	private int _reduc_cancel;
	public int getDmgReductionCancel() {
		return _reduc_cancel;
	}
	public void setDmgReductionCancel(int i) {
		_reduc_cancel = i;
	}

	private int _maxUseTime = 0;

	public int getMaxUseTime() {
		return _maxUseTime;
	}

	public void setMaxUseTime(int i) {
		_maxUseTime = i;
	}
}
