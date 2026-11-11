package l1j.server.server.model.Instance;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Date;
import java.util.Map;
import l1j.server.Config;
import l1j.server.MJCTSystem.MJCTObject;
import l1j.server.MJCTSystem.Loader.MJCTLoadManager;
import l1j.server.MJCTSystem.Loader.MJCTSystemLoader;
import l1j.server.MJINNSystem.MJINNHelper;
import l1j.server.MJTemplate.MJL1Type;
import l1j.server.MJTemplate.ItemPresentator.ItemPresentatorFactory;
import l1j.server.PrideSystem.PrideInfo;
import l1j.server.PrideSystem.PrideLoadManager;
import l1j.server.server.ThreadPool; //CrockController
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1EquipmentTimer;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.ItemEffectRegistry;
import l1j.server.server.model.item.ItemStat;
import l1j.server.server.model.item.cooking.CookingRegistry;
import l1j.server.server.model.item.doll.DollOption;
import l1j.server.server.model.item.doll.DollRegistry;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1ItemBookMark;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.ItemPresentOutStream;
import l1j.server.server.utils.MJBytesOutputStream;
import l1j.server.server.utils.eKind;

public class L1ItemInstance extends L1Object {

	public static String to_simple_description(String name, int bless, int enchant, int elemental, int level) {
		StringBuilder sb = new StringBuilder(name.length() + 32);
		sb.append(get_blessed_description(bless));
		sb.append(get_attribute_enchant_description(elemental));
		sb.append(get_enchant_description(enchant));
		sb.append(name);
		sb.append(get_level_description(level));
		return sb.toString();
	}

	public static String get_level_description(int level) {
		switch (level) {
		case 1:
			return "[1단계]";
		case 2:
			return "[2단계]";
		case 3:
			return "[3단계]";
		case 4:
			return "[4단계]";
		case 5:
			return "[5단계]";
		}
		return "";
	}

	public static String get_enchant_description(int enchant) {
		if (enchant > 0)
			return String.format("+%d ", enchant);
		else if (enchant < 0)
			return String.format("-%d ", enchant);
		return "";
	}

	public static String get_blessed_description(int bless) {
		switch (bless) {
		case 0:
			return "축복받은 ";
		case 2:
			return "저주받은 ";
		}
		return "";
	}

	private static final String[] elemental_descriptions = new String[] { "", // 무속성
			"$6115", // 화령1
			"$6116", // 화령2
			"$6117", // 화령3
			"$14361", // 화령4
			"$14365", // 화령5

			"$6118", // 수령1
			"$6119", // 수령2
			"$6120", // 수령3
			"$14362", // 수령4
			"$14366", // 수령5

			"$6121", // 풍령1
			"$6122", // 풍령2
			"$6123", // 풍령3
			"$14363", // 풍령4
			"$14367", // 풍령5

			"$6124", // 지령1
			"$6125", // 지령2
			"$6126", // 지령3
			"$14364", // 지령4
			"$14368", // 지령5
	};

	public static String get_attribute_enchant_description(int attrEnchantLevel) {
		return attrEnchantLevel >= elemental_descriptions.length ? ""
				: attrEnchantLevel < 0 ? "" : elemental_descriptions[attrEnchantLevel];
	}

	private static final int[] _attrMask = new int[] { 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 3, 3, 3, 3, 3, 4, 4, 4, 4, 4 };

	public static int attrEnchantToElementalType(int attrEnchantLevel) {
		return _attrMask[attrEnchantLevel];
	}

	public static int attrEnchantToElementalType(L1ItemInstance item) {
		return attrEnchantToElementalType(item.getAttrEnchantLevel());
	}

	public static int pureAttrEnchantLevel(int attrEnchantLevel) {
		return attrEnchantLevel <= 0 ? attrEnchantLevel
				: attrEnchantLevel - ((attrEnchantToElementalType(attrEnchantLevel) - 1) * 5);
	}

	public static int pureAttrEnchantLevel(L1ItemInstance item) {
		return pureAttrEnchantLevel(item.getAttrEnchantLevel());
	}

	public static boolean equalsElement(L1ItemInstance item, int elementalType, int elementalValue) {
		int attr = item.getAttrEnchantLevel();
		int type = attrEnchantToElementalType(attr);
		if (type != elementalType)
			return false;

		int value = attr - ((type - 1) * 5);
		return value == elementalValue;
	}

	public static final int CHAOS_SPIRIT = 1;
	public static final int CORRUPT_SPIRIT = 2;
	public static final int BALLACAS_SPIRIT = 3;
	public static final int ANTARAS_SPIRIT = 4;
	public static final int LINDBIOR_SPIRIT = 5;
	public static final int PAPURION_SPIRIT = 6;
	public static final int DEATHKNIGHT_SPIRIT = 7;
	public static final int BAPPOMAT_SPIRIT = 8;
	public static final int BALLOG_SPIRIT = 9;
	public static final int ARES_SPIRIT = 10;

	private static final long serialVersionUID = 1L;

	public boolean _isSecond = false;

	private int _count;

	private int _itemId;

	private L1Item _item;

	private boolean _isEquipped = false;

	private int _enchantLevel;

	private int _attrenchantLevel;

	private boolean _isIdentified = false;

	private int _durability;

	private int _chargeCount;

	private int _specialEnchant;

	private int _remainingTime;

	private Timestamp _lastUsed = null;

	private Timestamp _endTime = null;

	/** 패키지상점 **/
	private boolean _isPackage = false;

	private int bless;

	private int _lastWeight;

	/** 인형 착용 여부 **/
	private boolean _isDollOn = false;

	private final LastStatus _lastStatus = new LastStatus();

	public L1PcInstance _cha;

	public L1ItemInstance() {
		_count = 1;
		_enchantLevel = 0;
		_specialEnchant = 0;
		_bookmarks = new ArrayList<L1ItemBookMark>();
	}

	public L1ItemInstance(L1Item item, int count) {
		this();
		setItem(item);
		setCount(count);
	}

	public L1ItemInstance(L1Item item) {
		this(item, 1);
	}

	/** 인형 착용 여부 **/
	public boolean isDollOn() {
		return _isDollOn;
	}

	public void setDollOn(boolean DollOn) {
		_isDollOn = DollOn;
	}

	/** 인형 착용 여부 **/

	public boolean isSpecialEnchantable() {
		return (_specialEnchant & 0xFF) == 1;
	}

	public void setSpecialEnchantable() {
		_specialEnchant = 1;
	}

	public int getSpecialEnchant() {
		return _specialEnchant;
	}

	public int getSpecialEnchant(int index) {
		return ((_specialEnchant >> (8 * index)) & 0xFF);
	}

	public void setSpecialEnchant(int enchant) {
		_specialEnchant = enchant;
	}

	public void setSpecialEnchant(int index, int enchant) {
		_specialEnchant |= enchant << (8 * index);
	}

	public boolean isIdentified() {
		return _isIdentified;
	}

	public void setIdentified(boolean identified) {
		_isIdentified = identified;
	}

	public String getName() {
		return _item.getName();
	}

	public int getCount() {
		return _count;
	}

	public void setCount(int count) {
		_count = count;
	}

	public boolean isEquipped() {
		return _isEquipped;
	}

	public void setEquipped(boolean equipped) {
		_isEquipped = equipped;
	}

	public L1Item getItem() {
		return _item;
	}

	public void setItem(L1Item item) {
		_item = item;
		_itemId = item.getItemId();
	}

	public int getItemId() {
		return _itemId;
	}

	public void setItemId(int itemId) {
		_itemId = itemId;
	}

	public boolean isStackable() {
		return _item.isStackable();
	}

	@Override
	public void onAction(L1PcInstance player) {
	}

	public int getEnchantLevel() {
		return _enchantLevel;
	}

	public void setEnchantLevel(int enchantLevel) {
		_enchantLevel = enchantLevel;
	}

	public int getAttrEnchantLevel() {
		return _attrenchantLevel;
	}

	public int getHitModifierByAttrEnchant() {
		if (getAttrEnchantLevel() == 0) {
			return 0;
		} else if (getAttrEnchantLevel() % 3 == 0) {
			return 3;
		}

		return getAttrEnchantLevel() % 3;
	}


	public int get_gfxid() {
		return _item.getGfxId();
	}

	public int get_durability() {
		return _durability;
	}

	public int getChargeCount() {
		return _chargeCount;
	}

	public void setChargeCount(int i) {
		_chargeCount = i;
	}

	public int getRemainingTime() {
		return _remainingTime;
	}

	public void setRemainingTime(int i) {
		_remainingTime = i;
	}

	public void setLastUsed(Timestamp t) {
		_lastUsed = t;
	}

	public Timestamp getLastUsed() {
		return _lastUsed;
	}

	public int getBless() {
		return bless;
	}

	public void setBless(int i) {
		bless = i;
	}

	public int getLastWeight() {
		return _lastWeight;
	}

	public void setLastWeight(int weight) {
		_lastWeight = weight;
	}

	public Timestamp getEndTime() {
		return _endTime;
	}

	public void setEndTime(Timestamp t) {
		_endTime = t;
	}

	/** 패키지상점 **/
	public boolean isPackage() {
		return _isPackage;
	}

	public void setPackage(boolean _isPackage) {
		this._isPackage = _isPackage;
	}

	private long _itemdelay3;

	public long getItemdelay3() {
		return _itemdelay3;
	}

	public void setItemdelay3(long itemdelay3) {
		_itemdelay3 = itemdelay3;
	}

	public int getMr() {
		int mr = _item.get_mdef();
		int itemid = getItemId();
		if (itemid == 20011 || itemid == 20110 || itemid == 120011 || itemid == 22223 || itemid == 20117 // 바포갑빠
				//|| getItemId() == 22204 || itemid == 22205 || itemid == 22206 || itemid == 22207 // 린드비오르마갑주
				|| getItemId() == 22213 || itemid == 120110 || itemid == 93001 || itemid == 490008 || itemid == 22365
				|| itemid == 222328 || getItemId() >= 222300 && getItemId() <= 222303 || itemid == 222328) {
			mr += getEnchantLevel();
		} else if (itemid == 20056 || itemid == 120056 || itemid == 220056 || itemid == 93002 || itemid == 20074
				|| itemid == 900090 || itemid == 120074 || itemid == 222324 || itemid == 222325 || itemid == 900043
				|| itemid == 900044) {
			mr += getEnchantLevel() * 2;
		} else if (itemid == 20079 || itemid == 20078 || itemid == 900047 || itemid == 900055 || itemid == 20049
				|| itemid == 20050 || itemid == 900057 || itemid == 900056) {
			mr += getEnchantLevel() * 3;
		}
		if (mr < 0)
			mr = 0;
		return mr;
	}

	public int addhp() {
		int hp = _item.get_addhp();

		return hp;
	}

	public int addmp() {
		int mp = _item.get_addmp();
		return mp;
	}

	public int addsp() {
		int sp = _item.get_addsp();
		return sp;
	}

	public void set_durability(int i) {
		if (i < 0) {
			i = 0;
		}

		if (i > 127) {
			i = 127;
		}
		_durability = i;
	}

	public int getWeight() {
		if (getItem().getWeight() == 0) {
			return 0;
		} else {
			return Math.max(getCount() * getItem().getWeight() / 1000, 1);
		}
	}

	public class LastStatus {
		public int count;
		public int itemId;
		public boolean isEquipped = false;
		public int enchantLevel;
		public boolean isIdentified = true;
		public int durability;
		public int chargeCount;
		public int remainingTime;
		public Timestamp lastUsed = null;
		public int bless;
		public int attrenchantLevel;
		public int specialEnchant;
		public int bless_level;
		public int bless_level2;
		public Timestamp endTime = null;
		/** 특수 인챈트 시스템 **/
		public int item_level;
		/** 여관 열쇠 리뉴얼 **/
		public String town_name;
		public boolean _isPackage;

		public void updateAll() {
			count = getCount();
			itemId = getItemId();
			isEquipped = isEquipped();
			isIdentified = isIdentified();
			enchantLevel = getEnchantLevel();
			durability = get_durability();
			chargeCount = getChargeCount();
			remainingTime = getRemainingTime();
			lastUsed = getLastUsed();
			bless = getBless();
			attrenchantLevel = getAttrEnchantLevel();
			specialEnchant = getSpecialEnchant();
			endTime = getEndTime();
			bless_level = get_bless_level();
			bless_level2 = get_bless_level2();
			_isPackage = isPackage();
			item_level = get_item_level();
			/** 여관 열쇠 리뉴얼 **/
			town_name = getHotel_Town();
		}

		public void updateSpecialEnchant() {
			specialEnchant = getSpecialEnchant();
		}

		public void updateCount() {
			count = getCount();
		}

		public void updateItemId() {
			itemId = getItemId();
		}

		public void updateEquipped() {
			isEquipped = isEquipped();
		}

		public void updateIdentified() {
			isIdentified = isIdentified();
		}

		public void updateEnchantLevel() {
			enchantLevel = getEnchantLevel();
		}

		public void updateDuraility() {
			durability = get_durability();
		}

		public void updateChargeCount() {
			chargeCount = getChargeCount();
		}

		public void updateRemainingTime() {
			remainingTime = getRemainingTime();
		}

		public void updateLastUsed() {
			lastUsed = getLastUsed();
		}

		public void updateBless() {
			bless = getBless();
		}

		public void updateAttrEnchantLevel() {
			attrenchantLevel = getAttrEnchantLevel();
		}

		public void updateEndTime() {
			endTime = getEndTime();
		}

		public void update_bless_level() {
			bless_level = get_bless_level();
		}
		public void update_bless_level2() {
			bless_level2 = get_bless_level2();
		}
		
		public void updatePackage() {
		   _isPackage = isPackage();
		}

		/** 특수 인챈트 시스템 **/
		public void update_item_level() {
			item_level = get_item_level();
		}

		/** 특수 인챈트 시스템 **/

		/** 여관 열쇠 리뉴얼 **/
		public void update_town_name() {
			town_name = getHotel_Town();
		}/** 여관 열쇠 리뉴얼 **/
	}

	public LastStatus getLastStatus() {
		return _lastStatus;
	}

	public int getRecordingColumns() {
		// LastStatus is the persisted item image, not the client display.
		// Every field compared by CTransaction.verifyItemPreimage must be
		// accounted for before a receipt-guarded item use.
		int column = 0;
		if (getCount() != _lastStatus.count) column |= L1PcInventory.COL_COUNT;
		if (getItemId() != _lastStatus.itemId) column |= L1PcInventory.COL_ITEMID;
		if (isEquipped() != _lastStatus.isEquipped) column |= L1PcInventory.COL_EQUIPPED;
		if (getEnchantLevel() != _lastStatus.enchantLevel) column |= L1PcInventory.COL_ENCHANTLVL;
		if (get_durability() != _lastStatus.durability) column |= L1PcInventory.COL_DURABILITY;
		if (getChargeCount() != _lastStatus.chargeCount) column |= L1PcInventory.COL_CHARGE_COUNT;
		if (!java.util.Objects.equals(getLastUsed(), _lastStatus.lastUsed))
			column |= L1PcInventory.COL_DELAY_EFFECT;
		if (isIdentified() != _lastStatus.isIdentified) column |= L1PcInventory.COL_IS_ID;
		if (getRemainingTime() != _lastStatus.remainingTime) column |= L1PcInventory.COL_REMAINING_TIME;
		if (getBless() != _lastStatus.bless) column |= L1PcInventory.COL_BLESS;
		if (getAttrEnchantLevel() != _lastStatus.attrenchantLevel) column |= L1PcInventory.COL_ATTRENCHANTLVL;
		if (getSpecialEnchant() != _lastStatus.specialEnchant) column |= L1PcInventory.COL_SPECIAL_ENCHANT;
		if (get_bless_level() != _lastStatus.bless_level) column |= L1PcInventory.COL_BLESS_LEVEL;
		if (get_bless_level2() != _lastStatus.bless_level2) column |= L1PcInventory.COL_BLESS_LEVEL2;
		if (!java.util.Objects.equals(getEndTime(), _lastStatus.endTime))
			column |= L1PcInventory.COL_END_TIME;
		if (isPackage() != _lastStatus._isPackage) column |= L1PcInventory.COL_PACKAGE;
		if (get_item_level() != _lastStatus.item_level) column |= L1PcInventory.COL_ITEM_LEVEL;
		if (!java.util.Objects.equals(getHotel_Town(), _lastStatus.town_name))
			column |= L1PcInventory.COL_HOTEL_TOWN;
		return column;
	}

	public String getNumberedViewName(int count) {
		if (PrideLoadManager.USE_PRIDE_SYSTEM && _itemId == PrideLoadManager.PRIDE_ITEM_ID) {
			PrideInfo pInfo = PrideLoadManager.getInstance().get_pride(getId());
			if (pInfo != null)
				return String.format("%s의 자존심", pInfo.get_target_name());
		}

		StringBuilder name = new StringBuilder();

		if (isSpecialEnchantable()) {
			name.append("\\f3");
		}
		name.append(getNumberedName(count));
		int itemType2 = getItem().getType2();
		int itemId = getItem().getItemId();

		if (itemId == 40314 || itemId == 40316) {
			L1Pet pet = PetTable.getInstance().getTemplate(getId());
			if (pet != null) {
				name.append("[Lv.");
				name.append(pet.get_level());
				name.append(" ");
				name.append(pet.get_name());
				name.append("]HP");
				name.append(pet.get_hp());
			}
		}

		if (getItem().getType2() == 0 && getItem().getType() == 2) { // light
			if (isNowLighting()) {
				name.append(" ($10)");
			}
			if (itemId == 40001 || itemId == 40002 || itemId == 7005) {
				if (getRemainingTime() <= 0) {
					name.append(" ($11)");
				}
			}
		}

		if (itemId == MJINNHelper.INN_KEYID) {
			name.append(" (" + getHotel_Town() + ")");
		}


		if (isEquipped()) {
			if (itemType2 == 1) {
				name.append(" ($9)");
			} else if (itemType2 == 2) {
				name.append(" ($117)");
			} else if (itemType2 == 0 && getItem().getType() == 11) { // petitem
				name.append(" ($117)");
			}
		}

		if (itemType2 == 0 && getItem().getType() == 0) {
			if (_cha != null && _cha.getInventory().getArrowItemId() == getItemId()) {
				name.append(" ($117)");
			}
		}

		return name.toString();
	}

	public String getViewName() {
		return getNumberedViewName(_count);
	}

	public String getLogName() {
		return getNumberedName(_count);
	}

	/** 속성 인챈트 **/
	public String getNumberedName(int count) {
		StringBuilder name = new StringBuilder();

		if (isIdentified()) {
			if (getItem().getType2() == 1 || getItem().getType2() == 2) {
				
				switch (getAttrEnchantLevel()) {
				case 1:
					name.append("$6115");
					break; // 화령1단
				case 2:
					name.append("$6116");
					break; // 화령2단
				case 3:
					name.append("$6117");
					break; // 화령3단 (불의속성)
				case 4:
					name.append("$14361");
					break; // 화령4단
				case 5:
					name.append("$14365");
					break; // 화령5단
				case 6:
					name.append("$6118");
					break; // 수령1단
				case 7:
					name.append("$6119");
					break; // 수령2단
				case 8:
					name.append("$6120");
					break; // 수령3단 (물의속성)
				case 9:
					name.append("$14362");
					break; // 수령4단
				case 10:
					name.append("$14366");
					break; // 수령5단
				case 11:
					name.append("$6121");
					break; // 풍령1단
				case 12:
					name.append("$6122");
					break; // 풍령2단
				case 13:
					name.append("$6123");
					break; // 풍령3단 (바람의속성)
				case 14:
					name.append("$14363");
					break; // 풍령4단
				case 15:
					name.append("$14367");
					break; // 풍령5단
				case 16:
					name.append("$6124");
					break; // 지령1단
				case 17:
					name.append("$6125");
					break; // 지령2단
				case 18:
					name.append("$6126");
					break; // 지령3단 (땅의속성)
				case 19:
					name.append("$14364");
					break; // 지령4단
				case 20:
					name.append("$14368");
					break; // 지령5단
				default:
					break;
				}
				if (getEnchantLevel() >= 0) {
					name.append("+" + getEnchantLevel() + " ");
				} else if (getEnchantLevel() < 0) {
					name.append(String.valueOf(getEnchantLevel()) + " ");
				}
			}
		}
		// TODO 미확인상태 이름 확인후 이름 변경
		if (!isIdentified() && getItem().getItemId() == 900075) {
			name.append("$25459");
		} else if (!isIdentified() && getItem().getItemId() == 40008
				|| !isIdentified() && getItem().getItemId() == 140008) {
			name.append("$27");
		} else if (!isIdentified() && getItem().getItemId() == 40007) {
			name.append("$263");
		} else if (!isIdentified() && getItem().getItemId() == 40006) {
			name.append("$28");
		} else if (!isIdentified() && getItem().getItemId() == 40015
				|| !isIdentified() && getItem().getItemId() == 140015) {
			name.append("$232");
		} else if (!isIdentified() && getItem().getItemId() == 40012) {
			name.append("$238");
		} else if (!isIdentified() && getItem().getItemId() == 40011) {
			name.append("$235");
		} else if (!isIdentified() && getItem().getItemId() == 40010
				|| !isIdentified() && getItem().getItemId() == 140010) {
			name.append("$237");

			/** MJCTSystem **/
		} else if (getItem().getItemId() == MJCTLoadManager.CTSYSTEM_LOAD_ID) {
			MJCTObject obj = MJCTSystemLoader.getInstance().get(getId());
			if (obj == null)
				name.append(_item.getNameId());
			else
				name.append("[").append(obj.name).append("]봉인 구슬");
		} else {
			name.append(_item.getNameId());
		}

		if (isSpecialEnchantable()) {
			for (int i = 1; i <= 3; ++i) {
				if (getSpecialEnchant(i) == 0) {
					break;
				}
				switch (getSpecialEnchant(i)) {
				case CHAOS_SPIRIT:
					name.append("[혼돈] ");
					break;
				case CORRUPT_SPIRIT:
					name.append("[타락] ");
					break;
				case BALLACAS_SPIRIT:
					name.append("[발라카스] ");
					break;
				case ANTARAS_SPIRIT:
					name.append("[안타라스] ");
					break;
				case LINDBIOR_SPIRIT:
					name.append("[린드비오르] ");
					break;
				case PAPURION_SPIRIT:
					name.append("[파푸리온] ");
					break;
				case DEATHKNIGHT_SPIRIT:
					name.append("[데스나이트] ");
					break;
				case BAPPOMAT_SPIRIT:
					name.append("[바포메트] ");
					break;
				case BALLOG_SPIRIT:
					name.append("[발록] ");
					break;
				case ARES_SPIRIT:
					name.append("[아레스] ");
					break;
				}
			}
		}

		if (isIdentified()) {
			if (getItem().getMaxChargeCount() > 0) {
				name.append(" (" + getChargeCount() + ")");
			}
			if (getItem().getItemId() == 20383) {
				name.append(" (" + getChargeCount() + ")");
			}
			if (getItem().getMaxUseTime() > 0 && getItem().getType2() != 0) {
				name.append(" [" + getRemainingTime() + "]");
			}
		}
		if (count > 1) {
			name.append(" (" + count + ")");
		}
		return name.toString();
	}

	public static int presentationCode = 0;

	public byte[] getStatusBytes2() {
		int itemType2 = getItem().getType2();

		try {
			byte[] presentation_bytes = ItemPresentatorFactory.do_presentation(this);
			if (presentation_bytes != null)
				return presentation_bytes;
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		ItemPresentOutStream os = new ItemPresentOutStream();

		try {
			if (itemType2 == 0) { 
			switch (getItem().getItemId()) {
			case 875640331:
			case 875640332:
			case 875640333:
			case 875640334:
			case 875640335:
				os.writeC(39);
				os.writeS("구분: \\fB조우");
				break;
			case 875640336:
			case 875640337:
			case 875640338:
				os.writeC(39);
				os.writeS("구분: \\fF크리스터");
				break;
			case 875640339:
			case 875640340:
			case 875640341:
				os.writeC(39);
				os.writeS("구분: \\fD이실로테");
				break;
			case 875640446:
			case 875640447:
			case 875640448:
				os.writeC(39);
				os.writeS("구분: \\aG아툰");
				break;
				
			}
			}
			if (getEndTime() != null) {
				if (isIdentified()) {
					long temp = Date.parse("January 1, 2017 0:00:00");
					os.writeC(61);
					os.writeD((getEndTime().getTime() - temp) / 1000 * 6);
				}

				// long temp = Date.parse("January 1, 2017 19:00:00");

				// os.writeC(112);
				// os.writeD((getEndTime().getTime() / 1000));
			}

		} catch (

		Exception e) {
			e.printStackTrace();
		} finally {
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return os.getBytes();
	}
	
	@SuppressWarnings("deprecation")
	public byte[] getStatusBytes() {
		int itemType2 = getItem().getType2();
		int itemId = getItemId();
		int itemgrade = getItem().getGrade();

		try {
			byte[] presentation_bytes = ItemPresentatorFactory.do_presentation(this);
			if (presentation_bytes != null)
				return presentation_bytes;
		} catch (Exception e) {
			e.printStackTrace();
		}

		ItemPresentOutStream os = new ItemPresentOutStream();
		try {
			if (itemType2 == 0) { // etcitem
				switch (getItem().getType()) {
				case 2: // light
					os.writeLightRange(getItem().getLightRange());
					break;
				case 7: // food
					os.writeFoodVolume(getItem().getFoodVolume());
					break;
				case 0: // arrow
				case 15: // sting
					os.writeDMG(getItem().getDmgSmall(), getItem().getDmgLarge());
					break;
				default:
					os.writeC(23);
					break;
				}
				os.writeC(getItem().getMaterial());
				os.writeD(getWeight());


				switch (getItem().getItemId()) {
				case 145001:
				case 145002:
				case 145003:
				case 145004:
				case 145005:
				case 145006:
				case 145007:
				case 145008:
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA"+Config.혈맹버프지속시간+"분");
				
					break;
				}
				
				switch (getItem().getItemId()) {
				case 145010:
				os.writeC(39);
				os.writeS("\\fH랜덤획득: "+Config.혈맹포인트최소+"~"+Config.혈맹포인트최대);
				break;
				}
				switch (getItem().getItemId()) {
				case 3000209:
				case 3000213:
				case 3000214:
					os.writeC(39);
					os.writeS("\\fH포션: \\aA신속강력체력회복제");
					os.writeC(39);
					os.writeS("\\f2시작: \\aA더블클릭");
					os.writeC(39);
					os.writeS("\\f3종료: \\aA더블클릭");
					os.writeC(39);
					os.writeS("\\f9소멸 시 자동종료");
					break;
				}
				switch (getItem().getItemId()) {
				case 4100076:
					os.writeAddDMG(3);
					os.writeLongDMG(3);
					os.writeAddSP(2);
					os.writeShortHIT(3);
					os.writeLongHIT(3);
					os.writeAddEXP(20);
					os.writeAddAc(3);
					break;
				}
				switch (getItem().getItemId()) {
				case 4100039:
					os.writeAddSP(3);
					os.writeMagicHIT(5);
					os.writePVPAddDMGdown(3);
					break;
				}
				switch (getItem().getItemId()) {
				case 4100041:
					os.writeLongDMG(3);
					os.writeLongHIT(5);
					os.writePVPAddDMGdown(3);
					break;
				}
				switch (getItem().getItemId()) {
				case 4100042:
					os.writeShortDMG(3);
					os.writeShortHIT(5);
					os.writePVPAddDMGdown(3);
					break;
				}
				// TODO 각클래스 스킬 표기
				switch (getItem().getItemId()) {
				// 1단계 힐
				case 45000:
					os.writeClass(1 + 2 + 4 + 8 + 16 + 128);
					os.writeLawful(1);
					os.writeStep(0);
					break;
				}
				switch (getItem().getItemId()) {
				// 1단계
				case 45001:
				case 45002:
				case 45003:
				case 45004:
				case 45005:
				case 45006:
				case 45007:
					os.writeClass(1 + 2 + 4 + 8 + 16 + 128);
					os.writeLawful(0);
					os.writeStep(0);
					break;
				}
				switch (getItem().getItemId()) {
				// 2단계 큐어
				case 45008:
					os.writeClass(1 + 4 + 8 + 16);
					os.writeLawful(1);
					os.writeStep(2);
					break;
				}
				switch (getItem().getItemId()) {
				// 2단계 칠터치,커스
				case 45009:
				case 45010:
					os.writeClass(1 + 4 + 8 + 16);
					os.writeLawful(2);
					os.writeStep(2);
					break;
				}
				switch (getItem().getItemId()) {
				// 2단계
				case 45011:
				case 45012:
				case 45013:
				case 45014:
				case 45015:
					os.writeClass(1 + 4 + 8 + 16);
					os.writeLawful(0);
					os.writeStep(2);
					break;
				}
				switch (getItem().getItemId()) {
				// 3단계
				case 45019:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(3);
					break;
				}
				switch (getItem().getItemId()) {
				// 3단계
				case 45018:
				case 45021:
					os.writeClass(4 + 8);
					os.writeLawful(1);
					os.writeStep(3);
					break;
				}
				switch (getItem().getItemId()) {
				// 3단계
				case 45016:
				case 45017:
				case 45020:
				case 45022:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(3);
					break;
				}
				switch (getItem().getItemId()) {
				// 4단계
				case 40171:
					os.writeClass(4 + 8);
					os.writeLawful(1);
					os.writeStep(4);
					break;
				}
				switch (getItem().getItemId()) {
				// 4단계
				case 40173:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(4);
					break;
				}
				switch (getItem().getItemId()) {
				// 4단계
				case 40170:
				case 40172:
				case 40174:
				case 40175:
				case 40176:
				case 40177:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(4);
					break;
				}
				switch (getItem().getItemId()) {
				// 5단계
				case 40178:
				case 40185:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(5);
					break;
				}
				switch (getItem().getItemId()) {
				// 5단계
				case 40180:
				case 40182:
					os.writeClass(4 + 8);
					os.writeLawful(1);
					os.writeStep(5);
					break;
				}
				switch (getItem().getItemId()) {
				// 5단계
				case 40184:
				case 40183:
				case 40179:
				case 40181:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(5);
					break;
				}
				switch (getItem().getItemId()) {
				// 6단계
				case 40186:
				case 40192:
					os.writeClass(4 + 8);
					os.writeLawful(2);
					os.writeStep(6);
					break;
				}
				switch (getItem().getItemId()) {
				// 6단계
				case 40187:
				case 40188:
				case 40189:
				case 40190:
				case 40191:
				case 40193:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(6);
					break;
				}
				// 7단계
				switch (getItem().getItemId()) {
				case 40199:
				case 40200:
				case 40195:
				case 40198:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(7);
					break;
				}
				// 7단계
				switch (getItem().getItemId()) {
				case 40201:
				case 40196:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(7);
					break;
				}
				// 7단계
				switch (getItem().getItemId()) {
				case 40197:
				case 40194:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(7);
					break;
				}
				// 8단계
				switch (getItem().getItemId()) {
				case 40208:
				case 40209:
				case 40207:
				case 40205:
				case 40203:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(8);
					break;
				}
				// 8단계
				switch (getItem().getItemId()) {
				case 40206:
				case 40202:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(8);
					break;
				}
				// 8단계
				switch (getItem().getItemId()) {
				case 40204:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(8);
					break;
				}
				// 9단계
				switch (getItem().getItemId()) {
				case 40216:
				case 40210:
				case 40214:
				case 40212:
				case 40217:
				case 40215:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(9);
					break;
				}
				// 9단계
				switch (getItem().getItemId()) {
				case 40213:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(9);
					break;
				}
				// 9단계
				switch (getItem().getItemId()) {
				case 40211:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(9);
					break;
				}
				// 10단계
				switch (getItem().getItemId()) {
				case 40220:
				case 40222:
					os.writeClass(8);
					os.writeLawful(1);
					os.writeStep(10);
					break;
				}
				// 10단계
				switch (getItem().getItemId()) {
				case 40219:
				case 40221:
				case 40223:
				case 40224:
				case 40225:
					os.writeClass(8);
					os.writeLawful(0);
					os.writeStep(10);
					break;
				}
				// 10단계
				switch (getItem().getItemId()) {
				case 3000095:
					os.writeClass(8);
					os.writeLawful(2);
					os.writeStep(10);
					break;
				}
				switch (getItem().getItemId()) {
				case 40066:
					os.writeC(39);
					os.writeS("\\fIMP회복: \\aA5~10");
					break;
				}
				switch (getItem().getItemId()) {
				case 40067:
					os.writeC(39);
					os.writeS("\\fIMP회복: \\aA15~30");
					break;
				}
				switch (getItem().getItemId()) {
				case 65648: // 흑사의 코인
					os.writeAddAc(2);
					os.writeaspirit_resis(10);
					os.writeAddMaxHP(20);
					os.writeMaxMP(13);
					os.writeDMGdown(3);
					break;
				}
				switch (getItem().getItemId()) {
				case 667799:
				case 779988:
					os.writeC(39);
					os.writeS("\\fH오픈 시 확률적 획득");
					break;
				}

				switch (getItem().getItemId()) {
				case 1000007:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+500%");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000231:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+1300%");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000225: // 잊혀진 상자
					os.writeC(39);
					os.writeS("\\fI내용: \\aA최상급 무기 상자.");
					break;
				}
				switch (getItem().getItemId()) {
				case 310064:
				case 310065:
				case 310066:
				case 310067:
					os.writeC(39);
					os.writeS("\\f9속성 [5단계] 100% 성공");
					break;
				}
				switch (getItem().getItemId()) {
				case 500220:
					os.writeC(39);
					os.writeS("\\fI효과: \\aAMP 1000 회복.");
					break;
				}
				switch (getItem().getItemId()) {
				case 600223:
				case 600225:
				case 3000237:// PC방 버프 코인
					os.writeC(39);
					os.writeS("\\fI기간: \\aA3일");
					os.writeAddEXP(40);
					break;
				}
				switch (getItem().getItemId()) {
				case 600198:
				case 600199:
				case 600200:
				case 600201:
				case 600202:
				case 600203:
				case 600204:
				case 600205:
				case 600206:
				case 600207:
				case 600208:
				case 600209:
				case 600210:
				case 600211:
				case 600212:
				case 600213:
				case 600214:
				case 600215:
				case 600216:
				case 600217:
					os.writeC(39);
					os.writeS("\\fI기간: \\aA3일");
					//os.writeAddEXP(40);
					break;
				}

				// TODO 마법인형 리스트
				setMagicDollStatus(os, itemId);

				switch (getItem().getItemId()) {
				case 300001:
					os.writeC(39);
					os.writeS("\\aR잠재력 일반: "+Config.일반갯수+"개");
					os.writeC(39);
					os.writeS("\\aH잠재력 고급: "+Config.고급갯수+"개");
					os.writeC(39);
					os.writeS("\\aL잠재력 희귀: "+Config.희귀갯수+"개");
					os.writeC(39);
					os.writeS("\\aG잠재력 영웅: "+Config.영웅갯수+"개");
					os.writeC(39);
					os.writeS("\\aF잠재력 전설: "+Config.전설갯수+"개");
					break;
				}
				switch (getItem().getItemId()) {
				case 45453:
				case 45451:
				case 45452:
					os.writeC(39);
					os.writeS("\\fH소멸 시 자동해제");
					break;
				}
				
				switch (getItem().getItemId()) {
				case 410032:
				case 410033:
				case 410034:
				case 410035:
				case 410036:
				case 410037:
				case 410038:
					os.writeC(39);
					os.writeS("\\fI재사용 대기: \\aA3600초.");
					break;
				}
				switch (getItem().getItemId()) {
				case 51093:
				case 51094:
				case 51095:
				case 51096:
				case 51097:
				case 51098:
				case 51099:
				case 51100:
					os.writeC(39);
					os.writeS("\\fI효과: \\aA클래스 변경.");
					os.writeC(39);
					os.writeS("\\aA기존 스킬 삭제.");
					break;
				}
				switch (getItem().getItemId()) {
				case 60208:// 완력의빙수
					os.writeShortDMG(3);
					os.writeShortHIT(5);
					os.writeaSTR_Bu(1);
					break;
				}
				switch (getItem().getItemId()) {
				case 60209:// 민첩의빙수
					os.writeLongDMG(3);
					os.writeLongHIT(5);
					os.writeaDEX_Bu(1);
					break;
				}
				switch (getItem().getItemId()) {
				case 60210:// 지식의빙수
					os.writeaINT_Bu(1);
					os.writeAddSP(2);
					os.writeMaxMP(50);
					os.writeAddMPPrecovery(5);
					break;
				}
				switch (getItem().getItemId()) {
				case 3000456:
				case 30105:// 성장의물약
					os.writeAddEXP(30);
					break;
				}
		
				switch (getItem().getItemId()) {
				case 701:
					os.writeC(39);
					os.writeS("\\fI수량: \\aA7개.");
					break;
				}
				switch (getItem().getItemId()) {
				case 4100037:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA영웅의 가넷");
					break;
				}
				switch (getItem().getItemId()) {
				case 44114:
					os.writeC(39);
					os.writeS("\\fH조건: 파티상태");
					os.writeC(39);
					os.writeS("타겟을 지정하면 파티원들에게");
					os.writeC(39);
					os.writeS("타겟의 이펙트&멘트 출력 ");
					break;
				}
				switch (getItem().getItemId()) {
				case 1000002:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+30%");
					break;
				}
				switch (getItem().getItemId()) {
				case 1000003:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+50%");
					break;
				}
				switch (getItem().getItemId()) {
				case 410064:
				case 1000004:
					os.writeC(39);
					os.writeS("\\fI축복 수치: \\aA+100%");
					break;
				}
				switch (getItem().getItemId()) {
				case 4100160:
				case 4100161:
				case 4100162:
				case 4100163:
					os.writeC(39);
					os.writeS("\\fI불가능: \\f3PK시 어택/마법");
					break;
				}
				switch (getItem().getItemId()) {
				case 410012:
					os.writeShortHIT(3);
					os.writeShortDMG(3);
					os.writeLongHIT(3);
					os.writeLongDMG(3);
					os.writeAddSP(3);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (getItem().getItemId()) {
				case 410010:
					os.writeAddMaxHP(50);
					os.writeAddHPPrecovery(4);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (getItem().getItemId()) {// 다이노스
				case 500212:
					os.writeC(39);
					os.writeS("\\fIMP 회복 + 15");
					os.writeC(39);
					os.writeS("\\fI근거리 대미지 +1");
					os.writeC(39);
					os.writeS("\\fI원거리 대미지 +1");
					os.writeC(39);
					os.writeS("\\fI추가 대미지 +1");
					os.writeC(39);
					os.writeS("\\fISP +1");
					os.writeAddEXP(5);
					break;
				}
				switch (getItem().getItemId()) {
				case 410011:
					os.writeAddMaxHP(40);
					os.writeAddMPPrecovery(4);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (getItem().getItemId()) {
				case 201548:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA아르카의 유물.");
					os.writeC(39);
					os.writeS("\\aA행운 아이템.");
					break;
				}
				switch (getItem().getItemId()) {
				case 30072:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA단테스의 유물.");
					break;
				}
				switch (getItem().getItemId()) {
				case 700085:
					os.writeC(39);
					os.writeS("\\fI효과: \\aA메세지 출력(20초).");
					os.writeUseLevel(82);
					break;
				}
				switch (getItem().getItemId()) {
				case 44001:
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.버닝샷대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(30)정령옥(2)");
					os.writeC(39);
					os.writeS("\\fH효과: \\aA사용 시 채창출력");
					break;
				}
				switch (getItem().getItemId()) {
				case 90088:
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.비전대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP("+Config.비전엠피+")");
					os.writeC(39);
					os.writeS("\\aA6칸 내 순간이동");
					break;
				}
				switch (getItem().getItemId()) {
				case 70706:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA"+Config.버서크시간+"초");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.버서크대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAHP(100)/결정체(100)");
					os.writeC(39);
					os.writeS("\\aA모든 내성+20/근거리대미지+20");
					break;
				}
				switch (getItem().getItemId()) {
				case 70710:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA128초");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA20초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(18)각인뼈조각(1)");
					os.writeC(39);
					os.writeS("\\fH효과: \\aA물리회피 및 반격");
					break;
				}
				switch (getItem().getItemId()) {
				case 70711:
					os.writeC(39);
					os.writeS("\\fH획득조건: \\aA용기사&85레벨↑");
					os.writeC(39);
					os.writeS("\\fI클릭 시 할파스 스킬 획득");
					os.writeC(39);
					os.writeS("\\fI획득 후 교환/창고 불가");
				
					break;
				}switch (getItem().getItemId()) {
				case 70722:
					os.writeC(39);
					os.writeS("\\fI사용레벨: \\aA"+Config.어드밴스레벨);
					
					break;
				}switch (getItem().getItemId()) {
				case 70741:
					os.writeC(39);
					os.writeS("\\fI클래스: \\aA전체");
					os.writeC(39);
					os.writeS("\\fI사용레벨: \\aA"+Config.이뮨투함블레이드레벨);
					
					break;
				}switch (getItem().getItemId()) {
				case 70721:
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.어드밴스대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(20)/결정체(200)");
					os.writeC(39);
					os.writeS("본인 8셀 이내에 ");
					os.writeC(39);
					os.writeS("파티맺은 혈맹원 1인 소환 ");
				
					break;
				}
				switch (getItem().getItemId()) {
				case 70708:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA10분");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.프라임대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(50)/결정체(1000)");
					os.writeC(39);
					os.writeS("\\fH효과: \\aA버프창에 효과 표기");
					break;
				}switch (getItem().getItemId()) {
				case 707777:
					
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.쉐도우대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(15)");
					os.writeC(39);
					os.writeS("대상에게 빠르게 접근하여");
					os.writeC(39);
					os.writeS("데미지+확률 이동불가 효과");
					break;
				}switch (getItem().getItemId()) {
				case 707781:
					
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.데몰리션대기+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP("+Config.데몰리션엠피+")결정체(100)");
					os.writeC(39);
					os.writeS("최대 4초간 이동 불가");
					os.writeC(39);
					os.writeS("지속 시간 동안 대미지");
					break;
					
				}
				switch (getItem().getItemId()) {
				case 70740:
					os.writeC(39);
					os.writeS("\\fH클래스: \\aA전체");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.이뮨투함블레이드대기+"초");
					
					if (_cha.getType() == 0 ) {
					os.writeC(39);
					os.writeS("\\fH소모: \\aAHP ("+Config.이뮨투함블레이드군주피+")");
					}
					if (_cha.getType() == 1 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드기사피+")");
						}
					if (_cha.getType() == 2 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드요정피+")");
						}
					if (_cha.getType() == 3 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드법사피+")");
						}
					if (_cha.getType() == 4 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드다엘피+")");
						}
					if (_cha.getType() == 5 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드용기사피+")");
						}
					if (_cha.getType() == 6 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드환술피+")");
						}
					if (_cha.getType() == 7 ) {
						os.writeC(39);
						os.writeS("\\fH소모: \\aAHP("+Config.이뮨투함블레이드전사피+")");
						}
					os.writeC(39);
					os.writeS("확률적 이뮨 투 함 제거");
					break;
				
			}
				switch (getItem().getItemId()) {
				case 592247:
					os.writeLongDMG(3);
					os.writeShortDMG(3);
					os.writeAddAc(3);
					os.writePVPAddDMG(3);
					os.writePVPAddDMGdown(3);
					os.writeAddSP(2);
					break;
				
			}	
			switch (getItem().getItemId()) { 
			case 94268:
				os.writeShortHIT(5);
				os.writeLongHIT(5);
				os.writeLongDMG(5);
				os.writeShortDMG(5);
				os.writeAddSP(2);
				os.writeAddAc(5);
				break;
			
		}
				switch (getItem().getItemId()) {
			case 707779:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.어벤저대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(25)흑요석(2)");
				os.writeC(39);
				os.writeS("대상에게 물리대미지");
				os.writeC(39);
				os.writeS("체력 30% 이하 확률적 즉사");
				break;
			}switch (getItem().getItemId()) {
			case 80888:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.이터니티대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(80)");
				os.writeC(39);
				os.writeS("확률적 이동,귀환 불가");
				os.writeC(39);
				os.writeS("추가 마법 대미지");
				break;
			}switch (getItem().getItemId()) {
			case 220012:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.매직실드대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)정령옥(5)");
				os.writeC(39);
				os.writeS("첫번째 디버프 마법 면역");
				os.writeC(39);
				os.writeS("두번째 확률적 효과 해제");
				break;
			}switch (getItem().getItemId()) {
			case 875640444:
				os.writeC(39);
				os.writeS("\\fG실패 쿨타임: \\aA"+Config.엘븐스트라이크대기+"초");
				os.writeC(39);
				os.writeS("\\fH성공 쿨타임: \\aA15초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)");
				os.writeC(39);
				os.writeS("6칸 이내의 적을 기절시킨다.");
				break;
			}switch (getItem().getItemId()) {
			case 70888:
				
				os.writeC(39);
				os.writeS("\\fH추가명중: \\aA"+Config.엘븐스트라이크대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)정령옥(2)");
				os.writeC(39);
				os.writeS("주변 대상에게 물리대미지");
				os.writeC(39);
				os.writeS("확률적 홀드 효과");
				break;
			}switch (getItem().getItemId()) {
			case 70735:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.리버레이션대기+"초");
				os.writeC(39);
				os.writeS("\\fH소모: \\aAMP(20)정령옥(4)");
				os.writeC(39);
				os.writeS("상태 이상 스킬의 시간이");
				os.writeC(39);
				os.writeS("일정량 감소되어 적용");
				break;
			
			}switch (getItem().getItemId()) {
			case 70733:
				
				os.writeC(39);
				os.writeS("\\fH대기시간: \\aA"+Config.매스이뮨대기시간+"초");
				os.writeC(39);
				os.writeS("소모: MP("+Config.매스이뮨엠피+")마력의돌("+Config.매스이뮨마돌+")");
				os.writeC(39);
				os.writeS("8칸 내 파티 중인 혈맹원들");
				os.writeC(39);
				os.writeS("이뮨 투 함 효과 적용");
				break;
			}
				switch (getItem().getItemId()) {
				case 70714:
					
					os.writeC(39);
					os.writeS("\\fH지속시간: \\aA128초");
					os.writeC(39);
					os.writeS("\\fH대기시간: \\aA"+Config.포텐셜대기시간+"초");
					os.writeC(39);
					os.writeS("\\fH소모: \\aAMP(30)/속성석(2)");
					
					break;
				}
				switch (getItem().getItemId()) {
				case 210068:
				case 810012:
				case 810013:
					os.writeC(39);
					os.writeS("\\f9최대인챈가능: \\aA+"+Config.악세사리);
					break;
				}
				switch (getItem().getItemId()) {
				case 70707:
					os.writeC(39);
					os.writeS("\\fH획득조건: \\aA전사 & 85레벨↑");
					os.writeC(39);
					os.writeS("\\fI클릭 시 버서크 스킬 획득");
					os.writeC(39);
					os.writeS("\\fI획득 후 교환/창고 불가");
				
					break;
				}
				switch (getItem().getItemId()) {
				case 70709:
					os.writeC(39);
					os.writeS("\\fH획득조건: \\aA군주 & 85레벨↑");
					os.writeC(39);
					os.writeS("\\fI클릭 시 프라임 스킬 획득");
					os.writeC(39);
					os.writeS("\\fI획득 후 교환/창고 불가");
				
					break;
				}
				switch (getItem().getItemId()) {
				case 700086:
					os.writeC(39);
					os.writeC(39);
					os.writeS("\\fI효과: \\aA메세지 출력(40초).");
					os.writeUseLevel(82);
					break;
				}
				switch (getItem().getItemId()) {
				case 777000:
				case 777001:
				case 777002:
				case 777003:
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA" + Config.영웅유물 + "분");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000455:
				case 30104:// 코마의축복
					os.writeaSTR_Bu(5);
					os.writeaDEX_Bu(5);
					os.writeaCON_Bu(1);
					os.writeShortHIT(5);
					os.writeAddSP(1);
					os.writeAddAc(8);
					os.writeAddEXP(20);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA2시간.");
					break;
				}
				
				switch (getItem().getItemId()) {
				case 70703:
					os.writeC(39);
					os.writeS("8장 클릭 시 완제품 제작");
					break;
				}
				switch (getItem().getItemId()) {
				case 879977:
					os.writeC(39);
					os.writeS("\\fI확률지급");
					os.writeC(39);
					os.writeS("전설급 방어구,악세");
					os.writeC(39);
					os.writeS("\\aA좀더 높은 확률로 지급");
					break;
				}
				switch (getItem().getItemId()) {
				case 889977:
					os.writeC(39);
					os.writeS("\\fI확률지급");
					os.writeC(39);
					os.writeS("전설급 방어구,악세");
					break;
				}
				switch (getItem().getItemId()) {
				case 779977:
					os.writeC(39);
					os.writeS("\\fI확률지급");
					os.writeC(39);
					os.writeS("전설급 방어구,악세");

					break;
				}
				switch (getItem().getItemId()) {
				
				case 3000130:
					os.writeC(39);
					os.writeS("공격력,방어력,회피력");
					os.writeC(39);
					os.writeS("MR,모든 저항 상승");
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000129:
				
					os.writeDMGdown(5);
					os.writeAddEXP(5);
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000128:// 메티스의축복
					os.writeC(39);
					os.writeS("\\fI효과: \\aA공격력,방어력");
					os.writeC(39);
					os.writeS("\\aA회피력 상승");
					os.writeC(39);
					os.writeS("\\aA최대 MP/HP 상승");
					os.writeC(39);
					os.writeS("\\fI사용 시간: \\aA30분.");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000049:// 구호 증서
					os.writeC(39);
					os.writeS("\\fI효과: \\aA경험치 100% 복구");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000155:// 속죄 성서
					os.writeC(39);
					os.writeS("\\fI효과: \\aA라우풀 복구");
					break;
				}
				switch (getItem().getItemId()) {
				case 490028:// 라우풀물약
					os.writeC(39);
					os.writeS("\\fI효과: \\aA성향 증가");
					break;
				}
				switch (getItem().getItemId()) {
				case 490029:// 카오틱물약
					os.writeC(39);
					os.writeS("\\fI효과: \\aA성향 감소");
					break;
				}
				switch (getItem().getItemId()) {
				case 4100209:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA특정 기간에 사용");
					break;
				}
				switch (getItem().getItemId()) {
				case 707:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA4대 용 갑옷.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 706:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA휘장.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 705:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA문장.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 704:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA룸티스의 귀걸이.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 703:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA스냅퍼의 반지.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000148:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA전설급 무기.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 4100148: // 사신의 무기 마법 주문서
					os.writeC(39);
					os.writeS("\\aA100% 무기인챈트 +1 성공");
					break;
				}
				/*switch (getItem().getItemId()) {
				case 32001:
				case 32002:
				case 32003:
				case 32004:
				case 32005:
					os.writeC(39);
					os.writeS("\\fH[획득 방법]");
					os.writeC(39);
					os.writeS("\\aA8장 소지시 클릭");
					break;
				}*/
//				switch (getItem().getItemId()) {
//				case 32015:
//				case 32087:
//				case 32001:
//				case 32002:
//				case 32003:
//				case 32004:
//				case 32005:
//					os.writeC(39);
//					os.writeS("\\fH8장 클릭 시 제작");
//					break;
//				}
				switch (getItem().getItemId()) {
				case 5922910:
					os.writeC(39);
					os.writeS("\\fI랜덤 Hp증가: \\aH+10 ~ +30");
					break;
				case 5922911:
					os.writeC(39);
					os.writeS("\\fI랜덤 Hp증가: \\aH+30 ~ +50");
					break;
				}
				switch (getItem().getItemId()) {
				case 5922101:
					os.writeC(39);
					os.writeS("\\fI획득: \\aH귀신망토/마에티셔츠");
					break;
				}
				switch (getItem().getItemId()) {
				case 5922810:
					os.writeC(39);
					os.writeS("\\fI획득:\\aH에픽코인50~500개");
					break;
				}
				switch (getItem().getItemId()) {
				case 4100150:
					os.writeC(39);
					os.writeS("\\fI획득: \\aH3대 전설인형");
					break;
				}
				switch (getItem().getItemId()) {
				case 719:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA4대 마법.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA동일 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000169:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA접설급 인챈 무기.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA+0~3 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 718:
					os.writeC(39);
					os.writeS("\\fI내용: \\aA영웅급 인챈 무기.");
					os.writeC(39);
					os.writeS("\\fI획득: \\aA+0~3 인챈 랜덤.");
					break;
				}
				switch (getItem().getItemId()) {
				case 100002:
				case 100003:// 캐릭터교환권
					os.writeC(39);
					os.writeS("\\fI내용: \\aA캐릭터 교환.");
					os.writeC(39);
					os.writeS("\\fI사용: \\aA교환 창 거래.");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000353:
				case 410062:
				case 1000011:
				case 410135:
				case 4100185:
					os.writeC(39);
					os.writeS("\\fI효과: \\aA던전 시간 초기화.");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000159:// 생마 일회용
					os.writeC(39);
					os.writeS("\\fI효과: \\aA1회용 생명의 마안.");
					break;
				}
				switch (getItem().getItemId()) {
				case 779911:
					os.writeC(39);
					os.writeS("\\fI지역: \\aA기란감옥1층");
					os.writeC(39);
					os.writeS("\\fI사용법: \\aA특정지역에서 사용");
					break;
				}
				switch (getItem().getItemId()) {
				case 779922:
					os.writeC(39);
					os.writeS("\\fI지역: \\aA지배결계1층");
					os.writeC(39);
					os.writeS("\\fI사용법: \\aA특정지역에서 사용");
					break;
				}
				switch (getItem().getItemId()) {
				case 779933:
					os.writeC(39);
					os.writeS("\\fI지역: \\aA지배정상층");
					os.writeC(39);
					os.writeS("\\fI사용법: \\aA특정지역에서 사용");
					break;
				}

				

				switch (getItem().getItemId()) {
				case 7010:
				case 7011:
					os.writeC(39);
					os.writeS("\\fH단계마다 추타/SP 증가");
					os.writeC(39);
					os.writeS("\\f9숨결 인챈시 옵션 삭제");

					break;
				}
				/*switch (getItem().getItemId()) {
				case 210064:
				case 210065:
				case 210066:
				case 210067:
					os.writeC(39);
					os.writeS("\\f9숨결 인챈실패시 옵션 삭제");

					break;
				}*/
				switch (getItem().getItemId()) {
				case 3000171:// 마안 상자
					os.writeC(39);
					os.writeS("\\fI내용: \\aA마안 랜덤 획득.");
					break;
				}
				/*switch (getItem().getItemId()) {
				case 40308:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA화폐.");
					break;
				}*/
				switch (getItem().getItemId()) {
				case 145009:// 고대의 서: 무기
					os.writeC(39);
					os.writeS("\\fI물약회복률증가: \\aA+"+Config.혈맹버프회복+"%");
					break;
				}
				
				switch (getItem().getItemId()) {
				case 68076:// 고대의 서: 무기
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					break;
				}
				switch (getItem().getItemId()) {
				case 98077:// 고대의 서: 방어구
					os.writeC(39);
					os.writeS("\\fI[할파스 전용 강화]");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					break;
				}
				switch (getItem().getItemId()) {
				case 68077:// 고대의 서: 방어구
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					os.writeC(39);
					os.writeS("\\fI[제외품목]");
					os.writeC(39);
					os.writeS("\\fI악세/벨트/휘장/문장");
					
					break;
				}
				switch (getItem().getItemId()) {
				case 3000380:// 고대의 서:악세
					os.writeC(39);
					os.writeS("\\fI설명: \\aA확률 인챈 +1");
					os.writeC(39);
					os.writeS("\\fI설명: \\aA증발하지 않음.");
					os.writeC(39);
					os.writeS("\\fI[제외품목]");
					os.writeC(39);
					os.writeS("\\fI스냅퍼/룸티스/문장/휘장");

					break;
				}
				switch (getItem().getItemId()) {
				case 300000:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA전설급 무기류");
					os.writeC(39);
					os.writeS("\\fI인챈: \\aA+5/+7/+8/+9 사용");
					break;
				}
				switch (getItem().getItemId()) {
				case 4100139:
				case 4100179:
				case 4100180:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA자신과 동일인 분신");
					os.writeC(39);
					os.writeS("\\fI업그레이드: \\aA총 3단계 가능");
					os.writeC(39);
					os.writeS("\\fI인챈소비재료: \\aA영생의 빛");
					break;
				}
				/** 특수 마법인형 인챈트 시스템 **/
				잠재력(os);
				
				switch (getItem().getItemId()) {
				case 410063:
				case 410137:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA3단 가속");
					break;
				}

				switch (getItem().getItemId()) {
				case 5100135:
					os.writeC(39);
					os.writeS("\\fH[재료]");
					os.writeC(39);
					os.writeS("환상의 오만의 탑 지배부적: 1개");
					os.writeC(39);
					os.writeS("오만의 탑 정상주문서: 200장");
					os.writeC(39);
					os.writeS("아데나: 5억");
					break;
				}

				switch (getItem().getItemId()) {
				case 4100186:
					os.writeC(39);
					os.writeS("\\fI설명: \\aA아덴용사 방어구류");
					os.writeC(39);
					os.writeS("\\fI인챈: \\aA3~5 랜덤");
					break;
				}
				switch (getItem().getItemId()) {
				case 3000518:
					os.writeC(39);
					os.writeS("인챈시 옵션 증가");
					
					break;
				}
				switch (getItem().getItemId()) {
				case 3000517:
				case 3000518:
				case 4000517:
				case 4000518:
					os.writeC(39);
					os.writeS("인챈시 옵션 증가");
					break;
				}

				switch (getItem().getItemId()) {
				case 40014:
				case 140014:
				case 41415:
					os.writeClass(2 + 128);
					break;
				}
				switch (getItem().getItemId()) {
				case 40068:
				case 140068:
				case 210110:
					os.writeClass(4);
					break;
				}
				switch (getItem().getItemId()) {
				case 713:
				case 210036:
					os.writeClass(64);
					break;
				}
				switch (getItem().getItemId()) {
				case 712:
				case 40031:
					os.writeClass(1);
					break;
				}
				switch (getItem().getItemId()) {
				case 40016:
				case 140016:
				case 210113:
					os.writeClass(8 + 64);
					break;
				}
				switch (getItem().getItemId()) {
				case 40184:
				case 40183:
				case 40179:
				case 40181:
					os.writeClass(4 + 8);
					os.writeLawful(0);
					os.writeStep(5);
					break;
				}
				
				// 요리 표기
				CookingRegistry.writeItemOptions(os, itemId);
				
				
				// TODO ETC 표기 설정 시작
				int dmg = getItem().getDmgModifier();
				int hit = getItem().getHitModifier();

				if (getItem().getMinLevel() != 0) { // 최소 사용 레벨
					os.writeUseLevel(getItem().getMinLevel());
				}
				if (getItem().getMaxLevel() != 0) { // 최소~최대 사용 레벨
					os.writeLimitLevel(getItem().getMinLevel(), getItem().getMaxLevel());
				}

				if (dmg > 0)
					os.writeLongDMG(dmg);
				if (hit > 0)
					os.writeLongHIT(hit);
				if (itemId == 3000516) {
					os.writeAttrDmg(3);
				}

				if (itemType2 == 0 || itemType2 == 15) {
					if (isUndeadDmg()) {
						os.writeC(114);
						os.writeD(1);
					}
				}

				os.writeC(130);
				if (!getItem().isTradable()) {
					os.writeD(6);
				} else {
					os.writeD(7);
				}

			} else if (itemType2 == 1 || itemType2 == 2) { // weapon | armor

				int op_addAc = 0;

				/** 아이템 안전인챈 표시 추가 **/
				
				  int SafeEnchant = getItem().get_safeenchant(); os.writeC(39);
				  if (SafeEnchant < 0) {
					  SafeEnchant = 0; }
				  os.writeS("\\aG[안전인챈:+" + SafeEnchant + "]");
				 

				if (itemType2 == 1) { // weapon 무기 타격치
					os.writeC(1);
					os.writeC(getItem().getDmgSmall());
					os.writeC(getItem().getDmgLarge());
					os.writeC(getItem().getMaterial());
					os.writeD(getWeight());
				} else if (itemType2 == 2) { // armor
					if (getItem().getType() == 14) {
						os.writeC(19);
						int ac = getRuneAc();
						os.writeC(ac - ac - ac);
					} else {
						os.writeC(19);
						int ac = ((L1Armor) getItem()).get_ac();
						int Grade = ((L1Armor) getItem()).getGrade();
						if (ac < 0) {
							ac = ac - ac - ac;
						} else
							ac *= -1;
						os.writeC(ac - get_durability());
					}

					os.writeC(getItem().getMaterial());
					os.writeH(-1);
					os.writeD(getWeight());
				}

				if (itemId == 900081 || itemId == 900082 || itemId == 900083) { // 커츠의
					if (getEnchantLevel() == 4) {
						os.writeC(2);
						os.writeC(1);
					} else if (getEnchantLevel() == 5) {
						os.writeC(2);
						os.writeC(3);
					} else if (getEnchantLevel() == 6) {
						os.writeC(2);
						os.writeC(5);
					} else if (getEnchantLevel() == 7) {
						os.writeC(2);
						os.writeC(6);
					} else if (getEnchantLevel() == 8) {
						os.writeC(2);
						os.writeC(7);
					} else if (getEnchantLevel() == 9) {
						os.writeC(2);
						os.writeC(8);
					}
				
				} else if (itemId == 900152 || itemId == 900153 || itemId == 900154) {
					if (getEnchantLevel() == 4) {
						os.writeC(2);
						os.writeC(1);
					} else if (getEnchantLevel() == 5) {
						os.writeC(2);
						os.writeC(2);
					} else if (getEnchantLevel() == 6) {
						os.writeC(2);
						os.writeC(3);
					} else if (getEnchantLevel() == 7) {
						os.writeC(2);
						os.writeC(3);
					} else if (getEnchantLevel() == 8) {
						os.writeC(2);
						os.writeC(3);
					}
				} else if (itemId == 900084) { // 커츠의 수호 휘장
					if (getEnchantLevel() >= 6) {
						os.writeC(2);
						os.writeC(getEnchantLevel() - 1);
					} else if (getEnchantLevel() >= 3) {
						os.writeC(2);
						os.writeC(getEnchantLevel() - 2);
					}
				} else if (itemId == 900118 || itemId == 900117 || itemId == 900119 || itemId == 900120) {
					int enchant = getEnchantLevel();
					os.write(0x02);
					if (enchant >= 5) {
						os.write(enchant);
					} else {
						os.write(0x00);
					}
				} else if (getEnchantLevel() >= 5 && itemType2 == 2
						&& (getItem().get장신구처리() == 8 || getItem().get장신구처리() == 12)) {
					int enchantAc = getEnchantLevel() - 4;
					if (getEnchantLevel() >= 10) {
						enchantAc = 6;
					} else {
					if (enchantAc >= 5) {
						enchantAc = 5;
					}
					}
					os.writeC(2);
					os.writeC(enchantAc);
				} else if (getEnchantLevel() != 0 && !(itemType2 == 2 && getItem().getGrade() >= 0)) {
					if (getItemId() == 66) {
						os.writeC(0x6B);
						int v = getEnchantLevel() * 2;
						os.writeC(v);
						os.writeC(v);
					} else {
						os.writeC(2);
						os.writeC(getEnchantLevel());
					}
					/** 룸티스 검은빛 귀걸이 AC표현처리 부분 **/
				} else if (itemId == 22229) {
					int enchant = getEnchantLevel();
					if (enchant >= 6) {
						os.writeC(0x02);
						 if (enchant >= 9)
								os.writeC(10);
						else if (enchant >= 8)
							os.writeC(9);
						else if (enchant >= 7)
							os.writeC(8);
						else
							os.writeC(7);
					}
				} else if (itemId == 222337) {
					int enchant = getEnchantLevel();
					if (enchant >= 5) {
						os.writeC(0x02);
						 if (enchant >= 9)
								os.writeC(11);
						else if (enchant >= 8)
							os.writeC(10);
						else if (enchant >= 7)
							os.writeC(9);
						else if (enchant >= 6)
							os.writeC(8);
						else
							os.writeC(7);
					}
				} else if (itemId == 22231) {
					int enchant = getEnchantLevel();
					if (enchant >= 6) {
						os.writeC(0x02);
						if (enchant >= 8)
							os.writeC(3);
						else if (enchant >= 7)
							os.writeC(2);
						else
							os.writeC(1);
					}
				} else if (itemId == 222339) {
					int enchant = getEnchantLevel();
					if (enchant >= 5) {
						os.writeC(0x02);
						if (enchant >= 8)
							os.writeC(4);
						else if (enchant >= 7)
							os.writeC(3);
						else if (enchant >= 6)
							os.writeC(2);
						else
							os.writeC(1);
					}
				} else if (itemType2 == 2 && itemId == 222340 || itemId == 222341) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(1 + op_addAc);
						break;
					case 2:
						os.writeC(2 + op_addAc);
						break;
					case 3:
						if (itemId == 222341) {
							os.writeC(4 + op_addAc);
						} else {
							os.writeC(3 + op_addAc);
						}
						break;
					case 4:
						if (itemId == 222341) {
							os.writeC(5 + op_addAc);
						} else {
							os.writeC(4 + op_addAc);
						}
						break;
					case 5:
						if (itemId == 222341) {
							os.writeC(6 + op_addAc);
						} else {
							os.writeC(5 + op_addAc);
						}
						break;
					case 6:
						if (itemId == 222341) {
							os.writeC(7 + op_addAc);
						} else {
							os.writeC(6 + op_addAc);
						}
						break;
					case 7:
						if (itemId == 222341) {
							os.writeC(8 + op_addAc);
						} else {
							os.writeC(7 + op_addAc);
						}
						break;
					case 8:
						if (itemId == 222341) {
							os.writeC(9 + op_addAc);
						} else {
							os.writeC(8 + op_addAc);
						}
						break;
					case 9:
						if (itemId == 222341) {
							os.writeC(10 + op_addAc);
						} else {
							os.writeC(9 + op_addAc);
						}
						break;
					default:
						os.writeC(0 + op_addAc);
					}

					/** 스냅퍼의 회복 반지/스냅퍼의 집중 반지/스냅퍼의 마나 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 22224 || itemId == 22225 || itemId == 22227) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeC(3 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 스냅퍼의 마법저항반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 22228 || itemId == 22226) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
					case 5:
						os.writeC(3 + op_addAc);
						break;
					case 6:
					case 7:
						os.writeC(4 + op_addAc);
						break;
					case 8:
						os.writeC(6 + op_addAc);
						break;
					case 9:
						os.writeC(8 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 스냅퍼의 지혜 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222290) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
						os.writeC(32);
						os.writeH(15);
						break;
					case 2:
						os.writeC(2);
						os.writeC(1 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						break;
					case 3:
						os.writeC(2);
						os.writeC(2 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						break;
					case 4:
					case 5:
						os.writeC(2);
						os.writeC(3 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						break;
					case 6:
						os.writeC(2);
						os.writeC(4 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						os.writeability_resis(5);
						break;
					case 7:
						os.writeC(2);
						os.writeC(4 + op_addAc);
						os.writeC(32);
						os.writeH(15);
						os.writeMagicHIT(1);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeC(2);
						os.writeC(6 + op_addAc);
						os.writeC(32);
						os.writeH(30);
						os.writeMagicHIT(3);
						os.writeability_resis(9);
						os.writeafear_resis(1);
						break;
					case 9:
						os.writeC(2);
						os.writeC(8 + op_addAc);
						os.writeC(32);
						os.writeH(70);
						os.writeMagicHIT(5);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					default:
						os.writeC(2);
						os.writeC(0 + op_addAc);
					}
					/** 축복받은 스냅퍼의 지혜 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222335) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 1:
						os.writeAddMaxHP(5);
						break;
					case 2:
						os.writeC(1 + op_addAc);
						os.writeAddMaxHP(10);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeC(3 + op_addAc);
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeC(3 + op_addAc);
						os.writeAddMaxHP(30);
						break;
					case 6:
						os.writeC(3 + op_addAc);
						os.writeAddMaxHP(35);
						os.writeMagicHIT(1);
						os.writeability_resis(5);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						os.writeAddMaxHP(40);
						os.writeMagicHIT(2);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						os.writeAddMaxHP(50);
						os.writeMagicHIT(5);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						os.writeAddMaxHP(60);
						os.writeMagicHIT(7);
						os.writeability_resis(9);
						os.writeafear_resis(4);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 스냅퍼의 용사 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222291) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(1 + op_addAc);
						break;
					case 2:
						os.writeC(2 + op_addAc);
						break;
					case 3:
						os.writeC(3 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeC(4 + op_addAc);
						break;
					case 8:
						os.writeC(6 + op_addAc);
						break;
					case 9:
						os.writeC(8 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
				} else if (itemId == 222332) {
					
					os.writeC(2);
					switch (getEnchantLevel()) {
					
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
						os.writeC(3 + op_addAc);
						break;
					case 5:
					case 6:
						os.writeC(4 + op_addAc);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					
					/** 축복받은 스냅퍼 체력,마법저항 반지 AC부분 처리 **/
				} else if (itemId == 222334) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
						os.writeC(3 + op_addAc);
						break;
					case 5:
					case 6:
						os.writeC(4 + op_addAc);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 축복받은 스냅퍼의 회복,집중,마나 반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222330 || itemId == 222331 || itemId == 222333) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 2:
						os.writeC(1 + op_addAc);
						break;
					case 3:
						os.writeC(2 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeC(3 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
					/** 축복받은 스냅퍼 용사의반지 AC부분 처리 **/
				} else if (itemType2 == 2 && itemId == 222336) {
					os.writeC(2);
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(1 + op_addAc);
						break;
					case 2:
						os.writeC(2 + op_addAc);
						break;
					case 3:
						os.writeC(3 + op_addAc);
						break;
					case 4:
					case 5:
					case 6:
						os.writeC(4 + op_addAc);
						break;
					case 7:
						os.writeC(5 + op_addAc);
						break;
					case 8:
						os.writeC(7 + op_addAc);
						break;
					case 9:
						os.writeC(10 + op_addAc);
						break;
					default:
						os.writeC(0 + op_addAc);
					}
				}
				
				// TODO 85레벨 엘릭서 룬 옵션 표시
				if (itemId == 900140 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+2%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(5);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(5);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(5);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(5);
						break;
					case 4:
						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(5);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(5);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(5);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(5);
						break;
					}
				}
				if (itemId == 900141 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+2%");
					switch (_cha.getType()) {
					case 0: // 군주
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(5);
						break;
					case 1: // 기사
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(5);
						break;
					case 2:// 요정
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(5);
						break;
					case 3:// 법사
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(5);
						break;
					case 4:// 다엘

						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(5);
						break;
					case 5:// 용기사
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(5);
						break;
					case 6:// 환술
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(5);
						break;
					case 7: // 전사
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(5);
						break;
					}
				}
				if (itemId == 900142 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+2%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(5);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(5);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(5);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(5);
						break;
					case 4:

						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(5);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(5);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(5);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(5);
						break;
					}
				}
				if (itemId == 900143 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+2%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(5);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(5);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(5);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(5);
						break;
					case 4:

						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(5);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(5);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(5);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(5);
						break;
					}
				}
				if (itemId == 900144 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+2%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(5);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(5);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(5);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(5);
						break;
					case 4:

						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(5);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(5);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(5);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(5);
						break;
					}
				}
				// TODO 90레벨 엘릭서 룬 옵션 표시
				if (itemId == 900145 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+3%");
					// System.out.println(_cha.getType());
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(10);
						break;
					case 1:// 기사
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(10);
						break;
					case 2:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(10);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(10);
						break;
					case 4:
						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(10);
						break;
					case 5:
						os.writeC(5);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(10);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(32);
						os.writeH(50);
						os.writeadragonS_pierce(10);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(10);
						break;
					}
				}
				if (itemId == 900146 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+3%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(10);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(10);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(10);
						break;
					case 4:
						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(10);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(10);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(10);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(10);
						break;
					}
				}
				if (itemId == 900147 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+3%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(10);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(10);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(10);
						break;
					case 4:
						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(10);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(10);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(10);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(10);
						break;
					}
				}
				if (itemId == 900148 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+3%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(10);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(10);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(10);
						break;
					case 4:
						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(10);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(10);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(10);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(10);
						break;
					}
				}
				if (itemId == 900149 && _cha != null) {
					os.writeC(39);
					os.writeS("\\f9PVP 마법 대미지 감소: \\aA+3%");
					switch (_cha.getType()) {
					case 0:
						os.writeC(63);
						os.writeC(3);
						os.writeC(48);
						os.writeC(2);
						os.writeability_pierce(10);
						break;
					case 1:
						os.writeC(31);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeability_pierce(10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(50);
						os.writeC(47);
						os.writeC(1);
						os.writeC(35);
						os.writeC(1);
						os.writeaspirit_pierce(10);
						break;
					case 3:
						os.writeC(38);
						os.writeC(3);
						os.writeC(17);
						os.writeC(1);
						os.writeMagicHIT(10);
						break;
					case 4:
						os.writeC(32);
						os.writeH(30);
						os.writeaspirit_pierce(10);
						break;
					case 5:
						os.writeC(48);
						os.writeC(3);
						os.writeC(63);
						os.writeC(1);
						os.writeadragonS_pierce(10);
						break;
					case 6:
						os.writeC(68);
						os.writeC(5);
						os.writeC(31);
						os.writeH(50);
						os.writeadragonS_pierce(10);
						break;
					case 7:
						os.writeC(15);
						os.writeH(5);
						os.writeC(31);
						os.writeH(50);
						os.writeafear_pierce(10);
						break;
					}
				}
				
				ItemEffectRegistry.Def regDef = ItemEffectRegistry.get(itemId);
				if (regDef != null) {
					 final int ctype = (_cha != null) ? _cha.getType() : -1;  
				    if (regDef.isAlwaysAcLine()) {
				        os.writeC(2);
				        os.writeC(regDef.acLineValue(getEnchantLevel()) + op_addAc);
				    }
		
				    for (Map.Entry<ItemStat, Integer> e : regDef.aggregatedStatsFor(ItemEffectRegistry.TooltipSection.EFFECT, ctype, getEnchantLevel()).entrySet()) {
				        ItemStat st = e.getKey();
				        int v = e.getValue();
				        if (st.wt == ItemStat.WType.B) {
				        	os.writeC(st.opcode); 
				        	os.writeC(v);
				        } else { 
				        	os.writeC(st.opcode); 
				        	os.writeH(v);
				        }
				    }
				    for (Map.Entry<ItemStat, Integer> e : regDef.aggregatedStatsFor(ItemEffectRegistry.TooltipSection.POTION, ctype, getEnchantLevel()).entrySet()) {
				        if (e.getKey() == ItemStat.POTION_AMT) os.writePotionrecovery(e.getValue(), e.getValue());
				        else if (e.getKey() == ItemStat.HEAL_DEF) os.writeHealDefence(e.getValue());
				    }
				    if (regDef.getTooltipText() != null) {
				        os.writeC(39);
				        os.writeS(regDef.getTooltipText());
				    }
				}
				

				/** 스냅퍼의 반지 추타 표기 **/
				if (itemType2 == 2 && itemId == 22224 ||itemId == 22225|| itemId == 22227 && getEnchantLevel() > 4) {
					os.writeC(6);
					os.writeC(getItem().getDmgModifier() + getEnchantLevel() - 4);
					/** 축복받은 스냅퍼의 반지 추타 표기 **/
				} else if(itemType2 == 2 && itemId == 222291 || itemId == 22228 || itemId == 22226 ) {//////////// 걍스냅
					if (getEnchantLevel() > 4 && getEnchantLevel() <= 7 ) {
					os.writeC(6);
					os.writeC(getItem().getDmgModifier() + getEnchantLevel() - 4);
					} else if (getEnchantLevel() == 8) {
						os.writeC(6);
						os.writeC(getItem().getDmgModifier() + getEnchantLevel() - 3);
					}else if (getEnchantLevel() >= 9) {
						os.writeC(6);
						os.writeC(getItem().getDmgModifier() + getEnchantLevel() - 2);
					}
					
				} else if (itemType2 == 2 && itemId == 222330 &&  getEnchantLevel() > 3) {
					os.writeC(6);
					os.writeC(getItem().getDmgModifier() + getEnchantLevel() - 3);
				} else if(itemType2 == 2 && itemId == 222336 || itemId == 222334 || itemId == 222332) {////////////// 축스냅
					if (getEnchantLevel() > 3 && getEnchantLevel() <= 7 ) {
					os.writeC(6);
					os.writeC(getItem().getDmgModifier() + getEnchantLevel() - 3);
					} else if (getEnchantLevel() == 8) {
						os.writeC(6);
						os.writeC(getItem().getDmgModifier() + getEnchantLevel() - 1);
					} else if (getEnchantLevel() >= 9) {
						os.writeC(6);
						os.writeC(getItem().getDmgModifier() + getEnchantLevel());
					}
					
				} else if (itemType2 == 1 && itemId == 202012) {
					if (getEnchantLevel() > 0) {
						os.writeWeaponDmg(getEnchantLevel() * 2, getEnchantLevel() * 2);
						os.writeShortDMG(getItem().getDmgModifier());
						os.writeShortHIT(0);
					}
					
				} else if (getItem().getDmgModifier() != 0) {
					if (itemType2 == 1 && getItem().getType1() != 20) {
						
						if (getEnchantLevel() > 0) {
							if(추타2무기(itemId)){
								os.writeWeaponDmg(getEnchantLevel() * 2, getEnchantLevel() * 2);
							}else {
								os.writeWeaponDmg(getEnchantLevel(), getEnchantLevel());
							}
						}
						os.writeShortDMG(getItem().getDmgModifier());
					} else if (itemType2 == 1) {
						
						if (getEnchantLevel() > 0) {
							if(itemId == 202011 || itemId == 2202011){
								os.writeWeaponDmg(getEnchantLevel() * 2, getEnchantLevel() * 2);
							}else {
								os.writeWeaponDmg(getEnchantLevel(), getEnchantLevel());
							}
						}
						os.writeLongDMG(getItem().getDmgModifier());
					} else {
						os.writeAddDMG(getItem().getDmgModifier());
					}
				} else {
					if (isUndeadDmg()) {
						if (itemType2 == 1 && getItem().getType1() != 20) {
							os.writeShortDMG(0);
						} else {
							os.writeLongDMG(0);
						}
					}
				}
				if (getItem().isTwohandedWeapon()) { // 양손무기
					os.writeC(4);
				}
				if (get_durability() != 0) { // 손상도
					os.writeC(3);
					os.writeC(get_durability());
				}
				/** 클래스 착용 부분 **/
				int bit = 0;
				bit |= getItem().isUseRoyal() ? 1 : 0;
				bit |= getItem().isUseKnight() ? 2 : 0;
				bit |= getItem().isUseElf() ? 4 : 0;
				bit |= getItem().isUseMage() ? 8 : 0;
				bit |= getItem().isUseDarkelf() ? 16 : 0;
				bit |= getItem().isUseDragonKnight() ? 32 : 0;
				bit |= getItem().isUseBlackwizard() ? 64 : 0;
				bit |= getItem().isUse전사() ? 128 : 0;
				os.writeC(7);
				os.writeC(bit);

				os.writeC(130);
				if (!getItem().isTradable()) {
					os.writeD(6);
				} else {
					os.writeD(7);
				}

				if (isCanbeDmg()) {
					os.writeC(131);
					os.writeD(1); // 비손상
				}

				os.writeC(132);
				os.writeD(3); // 성별 구분 1.남자 2.여자 3.전체

				if (itemType2 == 1 && isUndeadDmg()) {
					os.writeC(114);
					os.writeD(1); // 언데드
				}
				/** 스냅퍼의 용사 반지 공격성공 표기 **/
				if (itemType2 == 2 && itemId == 222291) {
					
					if (getEnchantLevel() > 4 && getEnchantLevel() <= 7 ) {
						os.writeShortHIT(getItem().getHitModifier() + getEnchantLevel() - 4);
						os.writeLongHIT(getItem().getHitModifier() + getEnchantLevel() - 4);
						} else if (getEnchantLevel() == 8) {
							os.writeShortHIT(getItem().getHitModifier() + getEnchantLevel() - 3);
							os.writeLongHIT(getItem().getHitModifier() + getEnchantLevel() - 3);
						}else if (getEnchantLevel() >= 9) {
							os.writeShortHIT(getItem().getHitModifier() + getEnchantLevel() - 2);
							os.writeLongHIT(getItem().getHitModifier() + getEnchantLevel() - 2);
						}
				/** 축복받은 스냅퍼의 용사 반지 공격성공 표기 **/
			} else if(itemType2 == 2 && itemId == 222336) {
				if (getEnchantLevel() > 3 && getEnchantLevel() <= 7 ) {
					os.writeShortHIT(getItem().getHitModifier() + getEnchantLevel() - 3);
					os.writeLongHIT(getItem().getHitModifier() + getEnchantLevel() - 3);
				} else if (getEnchantLevel() == 8) {
					os.writeShortHIT(getItem().getHitModifier() + getEnchantLevel() - 1);
					os.writeLongHIT(getItem().getHitModifier() + getEnchantLevel() - 1);
				} else if (getEnchantLevel() >= 9) {
					os.writeShortHIT(getItem().getHitModifier() + getEnchantLevel());
					os.writeLongHIT(getItem().getHitModifier() + getEnchantLevel());
				}
			}
				/** 반지 5이상 추가 대미지 **/
				if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& getItem().get장신구처리() == 9 || getItem().get장신구처리() == 11) {
					if (getEnchantLevel() > 4) {
						os.writeShortDMG(getEnchantLevel() - 4);
						os.writeLongDMG(getEnchantLevel() - 4);
					}
				}
				/** 스냅퍼의 반지 PvP추가대미지 표시 **/
				if (itemType2 == 2 && itemId >= 22224 && itemId <= 22228 && getEnchantLevel() == 7
						|| itemId >= 222290 && itemId <= 222291 && getEnchantLevel() == 7
						|| itemId >= 222330 && itemId <= 222336 && getEnchantLevel() == 7) { // 인챈트가
																								// 7이면
					os.writeC(59);
					os.writeC(getEnchantLevel() - 6);
				} else if (itemType2 == 2 && itemId >= 22224 && itemId <= 22228
						|| itemId >= 222290 && itemId <= 222291 
						|| itemId == 222330 || itemId == 222331 || itemId == 222333 ) { // 인챈트가8이면
					if (getEnchantLevel() == 8){
					os.writeC(59);
					os.writeC(getEnchantLevel() - 5);
					} else if (getEnchantLevel() >= 9) {
							os.writeC(59);
							os.writeC(getEnchantLevel() - 2);
						}
				} else if (itemType2 == 2 && itemId == 222336 || itemId == 222333 ||  itemId == 222335  ||  itemId == 222332 ||  itemId == 222334 ) {
						if (getEnchantLevel() == 8) { // 인챈트가8이면
					os.writeC(59);
					os.writeC(getEnchantLevel() - 3);
						} else if (getEnchantLevel() >= 9) {
							os.writeC(59);
							os.writeC(getEnchantLevel());
						}
					
				} else if (itemId >= 307 && itemId <= 314) {
					if (getEnchantLevel() == 7) {
						os.writeC(59);
						os.writeC(getEnchantLevel() - 4);
					} else if (getEnchantLevel() == 8) {
						os.writeC(59);
						os.writeC(getEnchantLevel() - 3);
					} else if (getEnchantLevel() == 9) {
						os.writeC(59);
						os.writeC(getEnchantLevel() - 2);
					} else if (getEnchantLevel() == 10) {
						os.writeC(59);
						os.writeC(getEnchantLevel());
					}
				}

				/** 스냅퍼의 반지류 HP증가 표시 **/
				if (itemType2 == 2 && itemId >= 22224 && itemId <= 22228) {
					if (getEnchantLevel() > 0   && getEnchantLevel() <= 8) {
					int 스냅퍼HP증가 = getEnchantLevel() * 5 + 10;
					os.writeC(14);
					os.writeH(getItem().get_addhp() + 스냅퍼HP증가);
					} else if ( getEnchantLevel() >= 9) {
						if (itemId == 22226) {
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 55);
					} else {
						os.writeC(14);
						os.writeH(60);
					}
					}
				
					/** 축복받은 스냅퍼의 체력 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222332 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 15);
						break;
					case 2:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 20);
						break;
					case 3:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 30);
						break;
					case 4:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 35);
						break;
					case 5:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 40);
						break;
					case 6:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 45);
						break;
					case 7:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 55);
						break;
					case 8:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 65);
						break;
					case 9:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 75);
						break;
					default:
						break;
					}
					/** 축복받은 스냅퍼의 마법저항,집중,마나 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId >= 222330 && itemId <= 222331
						|| itemId >= 222333 && itemId <= 222334 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 15);
						break;
					case 2:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 20);
						break;
					case 3:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 30);
						break;
					case 4:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 35);
						break;
					case 5:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 40);
						break;
					case 6:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 45);
						break;
					case 7:
					case 8:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 50);
						break;
					case 9:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 60);
						break;
					default:
						break;
					}
					/** 스냅퍼의 지혜 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222290) {
					if (getEnchantLevel() > 0 && getEnchantLevel() <= 8) {
					int 지혜반지HP증가 = (getEnchantLevel() * 5);
					os.writeC(14);
					os.writeH(getItem().get_addhp() + 지혜반지HP증가);
					} else if (getEnchantLevel() >= 9) {
						os.writeC(14);
						os.writeH(50);
					}
					/** 축복받은 스냅퍼의 지혜 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222335 && getEnchantLevel() >= 0) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 1:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 5);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 2:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 10);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 3:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 20);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 4:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 25);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 5:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 30);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 6:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 35);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 7:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 40);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 30);
						break;
					case 8:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 50);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 35);
						break;
					case 9:
						//os.writeC(14);
						//os.writeH(getItem().get_addhp() + 50);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 80);
						break;
					default:
						break;
					}
					/** 스냅퍼의 용사 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222291) {
					if (getEnchantLevel() >= 3 && getEnchantLevel() <= 8) {
				
					int 용사반지HP증가 = (getEnchantLevel() - 2) * 5;
					os.writeC(14);
					os.writeH(getItem().get_addhp() + 용사반지HP증가);
					} else if (getEnchantLevel() >= 9) {
						
						os.writeC(14);
						os.writeH(40);
					}
					/** 축복받은 스냅퍼의 용사 반지 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222336 && getEnchantLevel() > 2) {
					switch (getEnchantLevel()) {
					case 3:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 10);
						break;
					case 4:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 15);
						break;
					case 5:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 20);
						break;
					case 6:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 25);
						break;
					case 7:
					case 8:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 30);
						break;
					case 9:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 40);
						break;
					default:
						break;
					}
					/** 룸티스 붉은빛 귀걸이 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 22229 && getEnchantLevel() > 0) {
					int 붉귀HP증가 = (getEnchantLevel() * 10) + 10;
					os.writeC(14);
					os.writeH(getItem().get_addhp() + 붉귀HP증가);
					/** 축복받은 룸티스 붉은빛 귀걸이 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222337 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 20);
						break;
					case 2:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 30);
						break;
					case 3:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 50);
						break;
					case 4:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 60);
						break;
					case 5:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 70);
						break;
					case 6:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 80);
						break;
					case 7:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 90);
						break;
					case 8:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 140);
						break;
					case 9:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 150);
						break;
					default:
						break;
					}
					/** 체력의 가더 HP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 22256 && getEnchantLevel() >= 5) {
					switch (getEnchantLevel()) {
					case 5:
					case 6:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 25);
						break;
					case 7:
					case 8:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 50);
						break;
					case 9:
					case 10:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 75);
						break;
					default:
						break;
					}
					// TODO 반지 귀걸이 인챈에대한 HP표기
				} else if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5 || itemgrade == 6)
						&& getItem().get장신구처리() != 10 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 5);
						break;
					case 2:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 10);
						break;
					case 3:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 20);
						break;
					case 4:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 30);
						break;
					case 5:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 40);
						break;
					case 6:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 40);
						break;
					case 7:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 50);
						break;
					case 8:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 50);
						break;
					case 9:
						os.writeC(14);
						os.writeH(getItem().get_addhp() + 60);
						break;
					
					case 10:
						os.writeC(14);
						if (getItem().get장신구처리() == 8) {
						os.writeH(getItem().get_addhp() + 100);
						} else{
							os.writeH(getItem().get_addhp() + 70);
						}
						break;
					default:
						break;
					}
				} else if (getEnchantLevel() >= 6 && itemType2 == 2 && getItem().get장신구처리() == 10) {
					int enchantAddHp = (getEnchantLevel() - 4) * 10;
					if (getEnchantLevel() >= 10) {
						enchantAddHp = 100;
					} else {
					if (enchantAddHp >= 50) {
						enchantAddHp = 50;
					}
					}
					os.writeC(14);
					os.writeH(getItem().get_addhp() + enchantAddHp);
				} else if (getItem().get_addhp() != 0) {
					os.writeC(14);
					os.writeH(getItem().get_addhp());
				}
				// TODO 벨트 인챈에 대한 MP증가 표기
				if (itemType2 == 2 && getItem().getType() == 10 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 5);
						break;
					case 2:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 10);
						break;
					case 3:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 20);
						break;
					case 4:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 30);
						break;
					case 5:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 40);
						break;
					case 6:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 40);
						break;
					case 7:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 50);
						break;
					case 8:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 50);
						break;
					case 9:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 60);
						break;
					case 10:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 70);
						break;
					default:
						break;
					}
					/** 룸티스의 보랏빛 귀걸이 MP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 22231 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 3:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 30);
						break;
					case 4:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 35);
						break;
					case 5:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 50);
						break;
					case 6:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 55);
						break;
					case 7:
						os.writeMagicHIT(1);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 70);
						break;
					case 8:
						os.writeMagicHIT(5);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 95);
						os.writeAddMaxHP(50);
						break;
					case 9:
						os.writeMagicHIT(9);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 125);
						os.writeAddMaxHP(100);
						break;
					default:
						break;
					}
					/** 축복받은 룸티스의 보랏빛 귀걸이 MP증가 표시 **/
				} else if (itemType2 == 2 && itemId == 222339 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 1:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 10);
						break;
					case 2:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 15);
						break;
					case 3:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 35);
						break;
					case 4:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 50);
						break;
					case 5:
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 55);
						break;
					case 6:
						os.writeMagicHIT(1);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 70);
						break;
					case 7:
						os.writeMagicHIT(3);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 95);
						break;
					case 8:
						os.writeAddMaxHP(100);
						os.writeMagicHIT(7);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 125);
						break;
					case 9:
						os.writeAddMaxHP(150);
						os.writeMagicHIT(12);
						os.writeC(32);
						os.writeH(getItem().get_addmp() + 155);
						break;
					default:
						break;
					}
					/** 대마법사의 모자 인챈트 MP증가 표시 **/
				} else if (itemId == 202022) {
					os.writeC(32);
					os.writeH(getItem().get_addmp() + (getEnchantLevel() * 10));
					os.writeC(15);
					os.writeH(getMr() + (getEnchantLevel() * 2));
					/** MP패킷변경됨 C/H **/
				} else if (addmp() != 0) {
					os.writeC(32);
					os.writeH(addmp());// mp부분패킷변경됨
				}
				// 피틱 표시
				if (getItem().get_addhpr() != 0) {
					os.writeC(37);
					os.writeC(getItem().get_addhpr());
				}
				// 엠틱 표시
				if (itemId == 1134 || itemId == 101134) {
					os.writeC(38);
					os.writeC(getItem().get_addmpr() + getEnchantLevel()); // 명상의
				} else if (getItem().get_addmpr() != 0) {
					os.writeC(38);
					os.writeC(getItem().get_addmpr());
				}
	
				if (getItem().getType2() == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& getEnchantLevel() > 4) {
					if (getItem().getType() == 8) { // 목걸이
						int enchantPotion = ((getEnchantLevel() - 4) * 2) + 1;
						if (getEnchantLevel() >= 10) {
							enchantPotion = 11;
						} else {
							if (enchantPotion >= 10) {
								enchantPotion = 10;
						}
						}
						os.writeC(65);
						os.writeC(enchantPotion);
						os.writeC(getEnchantLevel() == 6 ? 3
								: getEnchantLevel() == 7 ? 5
										: getEnchantLevel() == 8 ? 7 : getEnchantLevel() == 9 ? 8 : getEnchantLevel() >= 10 ? 9 : 0);
						os.writeC(96);
						os.writeC(enchantPotion);
						os.writeC(15);//마방
						os.writeH(getEnchantLevel() == 5 ? 1 : getEnchantLevel() == 6 ? 3 :getEnchantLevel() == 7 ? 5 :
							getEnchantLevel() == 8 ? 7 : getEnchantLevel() == 9 ? 10 : getEnchantLevel() >= 10 ? 12 : 0 );
					}
					if (getItem().getType() == 12) { // 귀걸이
						int enchantPotion = (getEnchantLevel() - 4) * 2;
						if (getEnchantLevel() >= 10) {
							enchantPotion = 10;
						} else {
						if (enchantPotion >= 9) {
							enchantPotion = 9;
						}
						}
						os.writeC(65);
						os.writeC(enchantPotion);
						os.writeC(getEnchantLevel() == 6 ? 2
								: getEnchantLevel() == 7 ? 4
										: getEnchantLevel() == 8 ? 6 : getEnchantLevel() == 9 ? 7: getEnchantLevel() >= 10 ? 8 : 0);
						os.writeC(96);
						os.writeC(enchantPotion);
					}
				}

				if (itemType2 == 2 && itemId == 222340 || itemId == 222341) {
					if (getEnchantLevel() == 3) {
						if (getItemId() == 222341) {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 2);
						} else {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 2);
						}
					} else if (getEnchantLevel() == 4) {
						if (getItemId() == 222341) {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 2);
							os.writeAddDmgPer(2, 20);
						} else {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 3);
						}
					} else if (getEnchantLevel() == 5) {
						if (getItemId() == 222341) {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 2);
							os.writeAddDmgPer(3, 20);
						} else {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 3);
							os.writeAddDmgPer(2, 20);
						}
					} else if (getEnchantLevel() == 6) {
						if (getItemId() == 222341) {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 2);
							os.writeAddDmgPer(4, 20);
						} else {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 3);
							os.writeAddDmgPer(3, 20);
						}
					} else if (getEnchantLevel() == 7) {
						if (getItemId() == 222341) {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 2);
							os.writeAddDmgPer(5, 20);
						} else {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 3);
							os.writeAddDmgPer(4, 20);
						}
					} else if (getEnchantLevel() == 8) {
						if (getItemId() == 222341) {
							os.writeC(6);
							os.writeC(getEnchantLevel() + 1);
							os.writeAddDmgPer(6, 30);
						} else {
							os.writeC(6);
							os.writeC(getEnchantLevel() - 1);
							os.writeAddDmgPer(5, 25);
						}
					}else if (getEnchantLevel() == 9) {
						if (getItemId() == 222341) {
							os.writeC(6);
							os.writeC(getEnchantLevel() + 3);
							os.writeAddDmgPer(8, 30);
						} else {
							os.writeC(6);
							os.writeC(getEnchantLevel());
							os.writeAddDmgPer(7, 25);
						}
					}
				}

				/** 축복받은 스냅퍼의 마법 저항 반지 MR표시 **/
				if (itemType2 == 2 && itemId == 222334) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeC(15);
						os.writeH(7);
						break;
					case 6:
						os.writeC(15);
						os.writeH(8);
						break;
					case 7:
						os.writeC(15);
						os.writeH(9);
						break;
					case 8:
						os.writeC(15);
						os.writeH(12);
						break;
					case 9:
						os.writeC(15);
						os.writeH(15);
						break;
					default:
						break;
					}
					/** 룸티스의 보랏빛 귀걸이 마방 표시 **/
				} else if (itemType2 == 2 && itemId == 22231 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeC(15);
						os.writeH(getMr() + 2);
						break;
					case 1:
						os.writeC(15);
						os.writeH(getMr() + 5);
						break;
					case 2:
						os.writeC(15);
						os.writeH(getMr() + 6);
						break;
					case 3:
						os.writeC(15);
						os.writeH(getMr() + 7);
						break;
					case 4:
						os.writeC(15);
						os.writeH(getMr() + 8);
						break;
					case 5:
						os.writeC(15);
						os.writeH(getMr() + 9);
						break;
					case 6:
						os.writeC(15);
						os.writeH(getMr() + 10);
						break;
					case 7:
						os.writeC(15);
						os.writeH(getMr() + 12);
						break;
					case 8:
						os.writeC(15);
						os.writeH(getMr() + 17);
						break;
					case 9:
						os.writeC(15);
						os.writeH(getMr() + 20);
						break;
					default:
						break;
					}
					/** 축복받은 룸티스의 보랏빛 귀걸이 마방 표시 **/
				} else if (itemType2 == 2 && itemId == 222339 && getEnchantLevel() > 0) {
					switch (getEnchantLevel()) {
					case 3:
						os.writeC(15);
						os.writeH(getMr() + 8);
						break;
					case 4:
						os.writeC(15);
						os.writeH(getMr() + 9);
						break;
					case 5:
						os.writeC(15);
						os.writeH(getMr() + 10);
						break;
					case 6:
						os.writeC(15);
						os.writeH(getMr() + 12);
						break;
					case 7:
						os.writeC(15);
						os.writeH(getMr() + 15);
						break;
					case 8:
						os.writeC(15);
						os.writeH(getMr() + 20);
						break;
					case 9:
						os.writeC(15);
						os.writeH(getMr() + 23);
						break;
					default:
						break;
					}
				} else if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& (getItem().get장신구처리() == 9 || getItem().get장신구처리() == 11) && getEnchantLevel() >= 6) {
					int enchantMr = 0;
					switch (getEnchantLevel()) {
					case 6:
						enchantMr = 1;
						break;
					case 7:
						enchantMr = 3;
						break;
					case 8:
						enchantMr = 5;
						break;
					case 9:
						enchantMr = 7;
						break;
					case 10:
						enchantMr = 9;
						break;
					}
					if (getEnchantLevel() >= 10) {
						enchantMr = 9;
					}
					os.writeC(15);
					os.writeH(getMr() + enchantMr);
				} else if (getMr() != 0) { // MR
					os.writeC(15);
					os.writeH(getMr());
				}
				/** 스냅퍼의 지혜 반지 SP표시 **/
				if (itemType2 == 2 && itemId == 222290 && getEnchantLevel() > 4) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 6:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 7:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 3);
						break;
					case 8:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 5);
						break;
					case 9:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 7);
						break;
					default:
						break;
					}
					/** 축복받은 스냅퍼의 지혜 반지 SP표시 **/
				} else if (itemType2 == 2 && itemId == 222335 && getEnchantLevel() > 3) {
					switch (getEnchantLevel()) {
					case 4:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 5:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 6:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 3);
						break;
					case 7:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 4);
						break;
					case 8:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 7);
						break;
					case 9:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 9);
						break;
					default:
						break;
					}
					/** 룸티스의 보랏빛 귀걸이 SP표시 **/
				} else if (itemType2 == 2 && itemId == 22231 && getEnchantLevel() > 2) {
					switch (getEnchantLevel()) {
					case 3:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 4:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 5:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 6:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 7:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 3);
						break;
					case 8:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 5);
						break;
					case 9:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 7);
						break;
					default:
						break;
					}
					/** 축복받은 룸티스의 보랏빛 귀걸이 SP표시 **/
				} else if (itemType2 == 2 && itemId == 222339 && getEnchantLevel() > 2) {
					switch (getEnchantLevel()) {
					case 3:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 4:
					case 5:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 6:
					case 7:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 3);
						break;
					case 8:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 5);
						break;
					case 9:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 9);
						break;
					default:
						break;
					}
					/** 화령의 가더 **/
				} else if (itemId == 900117) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddDMG(1);
						break;
					case 5:
						os.writeAddDMG(1);
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeAddDMG(1);
						os.writeShortHIT(2);
						os.writeaAll_resis(1);
						break;
					case 7:
						os.writeAddDMG(2);
						os.writeShortHIT(3);
						os.writeaAll_resis(2);
						break;
					case 8:
						os.writeAddDMG(3);
						os.writeShortHIT(4);
						os.writeaAll_resis(3);
						break;
					case 9:
						os.writeAddDMG(4);
						os.writeShortHIT(5);
						os.writeaAll_resis(4);
						break;
					case 10:
						os.writeAddDMG(5);
						os.writeShortHIT(6);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
					/** 풍령의 가더 **/
				} else if (itemId == 900118) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
						os.writeLongDMG(1);
						os.writeLongHIT(1);
						break;
					case 6:
						os.writeLongDMG(1);
						os.writeLongHIT(2);
						os.writeaAll_resis(1);
						break;
					case 7:
						os.writeLongDMG(2);
						os.writeLongHIT(3);
						os.writeaAll_resis(2);
						break;
					case 8:
						os.writeLongDMG(3);
						os.writeLongHIT(4);
						os.writeaAll_resis(3);
						break;
					case 9:
						os.writeLongDMG(4);
						os.writeLongHIT(5);
						os.writeaAll_resis(4);
						break;
					case 10:
						os.writeLongDMG(5);
						os.writeLongHIT(6);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
					/** 수령의 가더 **/
				} else if (itemId == 900119) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddSP(1);
						break;
					case 5:
						os.writeAddSP(1);
						os.writeMagicHIT(1);
						break;
					case 6:
						os.writeAddSP(1);
						os.writeMagicHIT(2);
						os.writeaAll_resis(1);
						break;
					case 7:
						os.writeAddSP(2);
						os.writeMagicHIT(3);
						os.writeaAll_resis(2);
						break;
					case 8:
						os.writeAddSP(3);
						os.writeMagicHIT(4);
						os.writeaAll_resis(3);
						break;
					case 9:
						os.writeAddSP(4);
						os.writeMagicHIT(5);
						os.writeaAll_resis(4);
						break;
					case 10:
						os.writeAddSP(5);
						os.writeMagicHIT(6);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
					/** 지령의 가더 **/
				} else if (itemId == 900120) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeDMGdown(1);
						break;
					case 5:
						os.writeDMGdown(1);
						os.writeAddMR(2);
						break;
					case 6:
						os.writeDMGdown(1);
						os.writeAddMR(3);
						break;
					case 7:
						os.writeDMGdown(2);
						os.writeAddMR(4);
						break;
					case 8:
						os.writeDMGdown(3);
						os.writeAddMR(5);
						break;
					case 9:
						os.writeDMGdown(4);
						os.writeAddMR(6);
						break;
					case 10:
						os.writeDMGdown(5);
						os.writeAddMR(7);
						break;
					default:
						break;
					}
					/** 마법사의 가더 SP표시 **/
				} else if (itemType2 == 2 && itemId == 22255 && getEnchantLevel() > 4) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 6:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 7:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 8:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 9:
					case 10:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 3);
						break;
					default:
						break;
					}
					/** 바포메트의 지팡이 SP표시 **/
				} else if (itemType2 == 1 && itemId == 124 && getEnchantLevel() > 6) {
					switch (getEnchantLevel()) {
					case 7:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 1);
						break;
					case 8:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 2);
						break;
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeC(17);
						os.writeC(getItem().get_addsp() + 3);
						break;
					default:
						break;
					}
					/** 리치 로브 SP표시 **/
				} else if (itemType2 == 2 && itemId == 20107 && getEnchantLevel() >= 3) {
					os.writeC(17);
					os.writeC(getItem().get_addsp() + getEnchantLevel() - 2);
				} else if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& (getItem().get장신구처리() == 9 || getItem().get장신구처리() == 11) && getEnchantLevel() >= 7) {
					int enchantSp = getEnchantLevel() - 6;
					if (enchantSp >= 4) {
						enchantSp = 4;
					}
					if (itemId != 900163 ) {
					os.writeC(17);
					// os.writeC(addsp() + enchantSp);
					os.writeC(enchantSp);
					}
				}
				if (itemId == 800110) {
					if(_cha.isCrown() || _cha.isKnight() || _cha.isDarkelf() || _cha.isDragonknight() || _cha.isWarrior()) {
						os.writeAddAc(8);
						os.writeShortHIT(2);
						os.writeShortDMG(2);
						os.writeDMGdown(2);
						os.writeafear_resis(2);
					} else if(_cha.isBlackwizard() || _cha.isWizard()) {
						os.writeAddAc(8);
						os.writeAddSP(2);
						os.writeMagicHIT(2);
						os.writeDMGdown(2);
						os.writeafear_resis(2);
					} else if(_cha.isElf() && _cha.getAbility().getBaseDex() >= 16){
						os.writeAddAc(8);
						os.writeLongHIT(2);
						os.writeLongDMG(2);
						os.writeDMGdown(2);
						os.writeafear_resis(2);
					} else {
						os.writeAddAc(8);
						os.writeShortHIT(2);
						os.writeShortDMG(2);
						os.writeDMGdown(2);
						os.writeafear_resis(2);
					}
				}
				// 마나 흡수
			if (itemId == 126 || itemId == 127) {
				os.writeC(16);
			}

				// 피 흡수
				if (itemId == 12 || itemId == 601 || itemId == 1123 || itemId == 202013) {
					os.writeC(34);
				}

				/** 벨트 5이상 대미지 리덕션 **/
				if (itemType2 == 2 && (getItem().getType() == 10 && getEnchantLevel() >= 5)) {// 벨트
					os.writeDMGdown(getEnchantLevel() - 4);
				}
				/** 룸티스의 붉은빛 귀걸이 대미지 리덕션 **/
				if (getItemId() == 22229 && getEnchantLevel() > 2) {
					switch (getEnchantLevel()) {
					case 3:
					case 4:
						os.writeDMGdown(getItem().get_damage_reduction() + 1);
						break;
					case 5:
						os.writeDMGdownprobability(2, 20);
						os.writeDMGdown(getItem().get_damage_reduction() + 2);
						break;
					case 6:
						os.writeDMGdownprobability(3, 20);
						os.writeDMGdown(getItem().get_damage_reduction() + 3);
						break;
					case 7:
						os.writeDMGdownprobability(4, 20);
						os.writeDMGdown(getItem().get_damage_reduction() + 4);
						os.writeShortHIT(1);
						os.writeLongHIT(1);
						break;
					case 8:
						os.writeDMGdownprobability(5, 25);
						os.writeDMGdown(getItem().get_damage_reduction() + 7);
						os.writeShortHIT(3);
						os.writeLongHIT(3);
						break;
					case 9:
						os.writeDMGdownprobability(7, 25);
						os.writeDMGdown(getItem().get_damage_reduction() + 7);
						os.writeShortHIT(5);
						os.writeLongHIT(5);
						break;
					default:
						break;
					}
				}
				if (getItemId() == 22228) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeAddMR(7);
						break;
					case 6:
						os.writeability_resis(5);
						os.writeAddMR(7);
						break;
					case 7:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+1");
						os.writeability_resis(7);
						os.writeAddMR(7);
						break;
					case 8:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+4");
						os.writeability_resis(9);
						os.writeafear_resis(1);
						os.writeAddMR(9);
						break;
					case 9:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+7");
						os.writeability_resis(9);
						os.writeafear_resis(2);
						os.writeAddMR(12);
						break;
					default:
						break;
					}
				}
				if (getItemId() == 222334) {
					switch (getEnchantLevel()) {
					case 6:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+1");
						os.writeability_resis(5);
						break;
					case 7:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+3");
						os.writeability_resis(7);
						break;
					case 8:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+7");
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					case 9:
						os.writeC(39);
						os.writeS("\\fI확률 마법 회피: \\aA+9");
						os.writeability_resis(9);
						os.writeafear_resis(4);
						break;
					default:
						break;
					}
				}
				/** 축복받은 룸티스의 붉은빛 귀걸이 대미지 리덕션 **/
				if (itemId == 222337 && getEnchantLevel() > 2) {
					switch (getEnchantLevel()) {
					case 3:
						os.writeDMGdown(getItem().get_damage_reduction() + 1);
						break;
					case 4:
						os.writeDMGdownprobability(2, 20);
						os.writeDMGdown(getItem().get_damage_reduction() + 2);
						break;
					case 5:
						os.writeDMGdownprobability(3, 20);
						os.writeDMGdown(getItem().get_damage_reduction() + 3);
						break;
					case 6:
						os.writeDMGdownprobability(4, 20);
						os.writeDMGdown(getItem().get_damage_reduction() + 4);
						os.writeShortHIT(1);
						os.writeLongHIT(1);
						break;
					case 7:
						os.writeDMGdownprobability(5, 20);
						os.writeDMGdown(getItem().get_damage_reduction() + 5);
						os.writeShortHIT(3);
						os.writeLongHIT(3);
						break;
					case 8:
						os.writeDMGdownprobability(6, 30);
						os.writeDMGdown(getItem().get_damage_reduction() + 7);
						os.writeShortHIT(5);
						os.writeLongHIT(5);
						break;
					case 9:
						os.writeDMGdownprobability(8, 30);
						os.writeDMGdown(getItem().get_damage_reduction() + 9);
						os.writeShortHIT(7);
						os.writeLongHIT(7);
						break;
					default:
						break;
					}
				}

				if (itemId == 988171) {
					switch (getEnchantLevel()) {
					case 0:

					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeAddMaxHP(50);
						os.writeMaxMP(20);
						os.writeAddMR(20);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+2%");						
						os.writeAddAc(5);
						os.writePVPAddDMG(1);
						break;
					case 8:
						os.writeAddMaxHP(60);
						os.writeMaxMP(30);
						os.writeAddMR(21);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+3%");						
						os.writeAddAc(6);
						os.writePVPAddDMG(2);
						break;
					case 9:
						os.writeAddMaxHP(70);
						os.writeMaxMP(40);
						os.writeAddMR(22);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+4%");						
						os.writeAddAc(7);
						os.writePVPAddDMG(3);
						break;
					case 10:
						os.writeAddMaxHP(80);
						os.writeMaxMP(50);
						os.writeAddMR(23);
						os.writeC(39);
						os.writeS("\\f9ER: \\aA+5%");						
						os.writeAddAc(8);
						os.writePVPAddDMG(4); 
						break;
					default:

					}
				}
				/** 실프의티셔츠 **/
				if (itemId == 900019) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 1:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 2:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 3:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 4:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 5:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeaAll_resis(1);
						os.writeability_resis(11);
						break;
					case 6:
						os.writeAddMR(13);
						os.writeDMGdown(2);
						os.writeaAll_resis(2);
						os.writeability_resis(11);
						break;
					case 7:
						os.writeAddMR(14);
						os.writeDMGdown(3);
						os.writeaAll_resis(3);
						os.writeability_resis(11);
						break;
					case 8:
						os.writeAddMR(15);
						os.writeDMGdown(4);
						os.writeaAll_resis(4);
						os.writeability_resis(11);
						break;
					case 9:
						os.writeAddMR(16);
						os.writeDMGdown(5);
						os.writeaAll_resis(5);
						os.writeability_resis(11);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddMR(17);
						os.writeDMGdown(6);
						os.writeaAll_resis(6);
						os.writeability_resis(11);
						break;
					default:
						break;
					}
				}
				
//				//21주년
				if (itemId == 474700) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMR(1);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 1:
						os.writeAddMR(3);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 2:
						os.writeAddMR(5);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 3:
						os.writeAddMR(7);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 4:
						os.writeAddMR(9);
						os.writeafear_resis(5);
						os.writeability_resis(5);
						break;
					case 5:
						os.writeAddMR(11);
						os.writeafear_resis(6);
						os.writeability_resis(6);
						break;
					case 6:
						os.writeAddMR(13);
						os.writeafear_resis(7);
						os.writeability_resis(7);
						break;
					case 7:
						os.writeAddMR(15);
						os.writeafear_resis(8);
						os.writeability_resis(8);
						break;
					case 8:
						os.writeAddMR(17);
						os.writeafear_resis(9);
						os.writeability_resis(9);
						break;
					case 9:
						os.writeAddMR(19);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
					case 10:
						os.writeAddMR(21);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
					case 11:
						os.writeAddMR(23);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
					case 12:
						os.writeAddMR(25);
						os.writeafear_resis(10);
						os.writeability_resis(10);
						break;
//				default:
//				if (getEnchantLevel() > 12 ){
//						os.writeAddMR(((getEnchantLevel() - 12) * 2) + 25);
//						os.writeafear_resis(10);
//						os.writeability_resis(10);
//					}
//						break;
					}
				}

				
				if (itemId == 565611) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeAddMR(12);
						os.writeDMGdown(1);
						os.writeafear_resis(1);
						os.writeaspirit_resis(1);
						os.writeadragonS_resis(1);
						os.writeability_resis(12);
						break;
					default:
						if (getEnchantLevel() > 5) {
							os.writeAddMR(getEnchantLevel() - 5 + 12);
							os.writeDMGdown(getEnchantLevel() - 5 + 1);
							os.writeafear_resis(getEnchantLevel() - 5 + 1);
							os.writeaspirit_resis(getEnchantLevel() - 5 + 1);
							os.writeadragonS_resis(getEnchantLevel() - 5 + 1);
							os.writeability_resis(getEnchantLevel() - 5 + 12);
							os.writePVPAddDMG(getEnchantLevel() - 5);
						}
						break;
					}
				}

				/** 수호의 가더 대미지 리덕션 **/
				if (itemId == 22254) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeDMGdown(1);
						break;
					case 5:
					case 6:
						os.writeDMGdown(2);
						break;
					case 7:
					case 8:
						os.writeDMGdown(3);
						break;
					case 9:
					case 10:
						os.writeDMGdown(4);
						break;
					default:
						break;
					}
				}
				/** 유니콘의 완력 각반 **/
				if (itemId == 900030) {
					if (getEnchantLevel() >= 9)
						os.writeShortDMG(1);
				}
				/** 유니콘의 민첩 각반 **/
				if (itemId == 900031) {
					if (getEnchantLevel() >= 9)
						os.writeLongDMG(1);
				}
				/** 유니콘의 지식 각반 **/
				if (itemId == 900032) {
					if (getEnchantLevel() >= 9)
						os.writeAddSP(1);
				}
				/** 나이트발드의 양손검 스턴레벨+1 **/
				if (itemId == 59 || itemId == 617  || itemId == 11121) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 15)
						os.writeability_pierce(5);
				}
				/** 악몽의 장궁 정령 적중 **/
				if (itemId == 622 || itemId == 1136) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 15)
						os.writeaspirit_pierce(3);
				}
				/** 섬멸자의 체인소드 용언 적중 **/
				if (itemId == 203017 || itemId == 618) {
				switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeadragonS_pierce(1);
						break;
					case 8:
						os.writeadragonS_pierce(2);
						break;
					case 9:
						os.writeadragonS_pierce(3);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_pierce(4);
						break;
					default:
						break;
						}
				}
				/** 악마왕의 도끼**/
				if (itemId == 147) {
					if (getEnchantLevel() >= 1 && getEnchantLevel() <= 15)
					os.writeShortDMG(getEnchantLevel());
				}
			
		
			
				/** 아르카의 유물 **/
				if (itemId == 900033 || itemId == 9961) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
						os.writeAddEXP(2);
						break;
					default:
						break;
					}
				}

				if (itemId == 555000 || itemId == 666000) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
						os.writeAddEXP(2);
						break;
					default:
						break;
					}
				}
				if (itemId == 555001 || itemId == 555002 || itemId == 555003 || itemId == 666001 || itemId == 666002
						|| itemId == 666003) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
						os.writeAddEXP(10);
						break;
					default:
						break;
					}
				}
				if (itemId == 700117) {
					
					
					
					os.writeAddMR(getEnchantLevel() * 2 + 3);
					os.writeAddSP(getEnchantLevel() + 2);
					os.writePVPAddDMG(getEnchantLevel() + 2);
					os.writePVPAddDMGdown(getEnchantLevel() + 2);
					os.writeAddEXP(getEnchantLevel() + 1);
					os.writeMagicHIT(getEnchantLevel()+ 1);
					
					if (getEnchantLevel() >= 5) {
					os.writeaAll_pierce(getEnchantLevel() - 4);
					os.writeaAll_resis(getEnchantLevel() - 4);
					}
					
				} 
				if (itemId == 900116) {
					if(getEnchantLevel() <= 5 ) {
						os.writeAddMaxHP(100);
						os.writeAddMR(10);
						os.writePVPAddDMG(3);
						os.writePVPAddDMGdown(1);
						os.writeAddEXP(5);
					} else if(getEnchantLevel()== 6 ) {
						os.writeAddMaxHP(120);
						os.writeAddMR(12);
						os.writePVPAddDMG(4);
						os.writePVPAddDMGdown(2);
						os.writeAddEXP(6);
					} else if(getEnchantLevel()== 7 ) {
						os.writeAddMaxHP(130);
						os.writeAddMR(13);
						os.writePVPAddDMG(5);
						os.writePVPAddDMGdown(3);
						os.writeAddEXP(8);
					} else if(getEnchantLevel()== 8 ) {
						os.writeAddMaxHP(150);
						os.writeAddMR(15);
						os.writePVPAddDMG(6);
						os.writePVPAddDMGdown(4);
						os.writeAddEXP(10);
					}

				}
				if (itemId == 900024) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 10)
						os.writeMagicHIT(3);
				}
				if (itemId == 900114) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 10)
						os.writeMagicHIT(2);
				}
				if (itemId == 541) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 10)
						os.writeAddStunHit(10);
				}
				if (itemId == 542) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeDMGdown(2);
						os.writeReductiondown(12);
						break;
					default:
						break;
					}
				}
				if (itemId == 543) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeShortDMG(2);
						break;
					default:
						break;
					}
				}
				if (itemId == 544) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeShortDMG(10);
						break;
					default:
						break;
					}
				}
				if (itemId == 545) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeShortDMG(17);
						break;
					default:
						break;
					}
				}
				if (itemId == 547) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeShortDMG(11);
						break;
					default:
						break;
					}
				}
				/** 축복받은 제로스의 지팡이 **/
				if (itemId == 620) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeMagicCritical(5);
						break;
					default:
						break;
					}
				}
				if (itemId == 7000223 || itemId == 505015) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
						os.writeMagicCritical(1);
						break;
					case 7:
						os.writeadragonS_pierce(1);
						os.writeMagicCritical(2);
						break;
					case 8:
						os.writeadragonS_pierce(2);
						os.writeMagicCritical(3);
						break;
					case 9:
						os.writeadragonS_pierce(3);
						os.writeMagicCritical(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_pierce(4);
						os.writeMagicCritical(5);
						break;
					default:
						break;
					}
				}

				/** 태풍의 도끼 공포적중+1 **/
				if (itemId == 203006 || itemId == 616) {
					switch (getEnchantLevel()) {
					case 8:
						os.writeafear_pierce(1);
						break;
					case 9:
						os.writeafear_pierce(2);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeafear_pierce(3);
						break;
					default:
						break;
					}
				}

				if (itemId == 14061 || itemId == 14012 || itemId == 2202013 
						|| itemId == 2202014 || itemId == 14086 ) {
					os.writeDMGdown(1);					
				}
				if (itemId == 14134 || itemId == 2202012 ) {
					os.writeDMGdown(2);					
				}
				if (itemId == 61 || itemId == 14061) {
					if (getEnchantLevel() > 0) {
						os.writeability_pierce(8 + getEnchantLevel());
						os.writeShortCritical(1 + getEnchantLevel());
					} else {
						os.writeability_pierce(8);
						os.writeShortCritical(1);
					}
					
				}
				
				if (itemId == 12 || itemId == 14012) {
					if (getEnchantLevel() > 0) {
						os.writeaAll_pierce(getEnchantLevel());
					} 
				}
				
				if (itemId == 134 || itemId == 14134) {
					if (getEnchantLevel() > 0) {
						os.writeAddSP(7 + getEnchantLevel());
						os.writeMagicHIT(2 + getEnchantLevel());
					} else {
						os.writeAddSP(7);
						os.writeMagicHIT(2);
					}
				}
				
				if (itemId == 86 || itemId == 14086) {
					if (getEnchantLevel() > 0) {
						os.writeaspirit_pierce(5 + getEnchantLevel());
						os.writeC(39);
						os.writeS("\\fI더블 대미지 발동 확률 증가");
					} else {
						os.writeaspirit_pierce(5);
						os.writeC(39);
						os.writeS("\\fI더블 대미지 발동 확률 증가");
					}
				}
				if (itemId == 202011 || itemId == 2202011) {
					if (getEnchantLevel() > 0) {
						os.writeaspirit_pierce(5 + getEnchantLevel());
						os.writeReductiondown(9+ getEnchantLevel());
						os.writeLongCritical(1 + getEnchantLevel());
						os.writeDMGdown(2);
					} else {
						os.writeaspirit_pierce(5);
						os.writeLongCritical(1);
						os.writeReductiondown(9);
						os.writeDMGdown(2);
					}
				}
				
				if (itemId == 202013 || itemId == 2202013) {
					if (getEnchantLevel() > 0) {
						os.writeShortCritical(1 + getEnchantLevel());
						os.writeadragonS_pierce(7 + getEnchantLevel());
					}else {
						os.writeShortCritical(1);
						os.writeadragonS_pierce(7);
					}
				}
				
				
				if (itemId == 202012 || itemId == 2202012) {
					if (getEnchantLevel() > 0) {
						os.writeAddSP(2 + getEnchantLevel());
						os.writeadragonS_pierce(7 + getEnchantLevel());
						os.writeMagicCritical(1 + getEnchantLevel());
					}else {
						os.writeAddSP(2);
						os.writeadragonS_pierce(7);
						os.writeMagicCritical(1);
					}
					
				}
				
			if (itemId == 202014 || itemId == 2202014) {
				if (getEnchantLevel() > 0) {
					os.writeShortCritical(1 + getEnchantLevel());
					os.writeafear_pierce(3 + getEnchantLevel());
					os.writeaTitan(5);
				}else {
					os.writeShortCritical(1);
					os.writeafear_pierce(3);
					os.writeaTitan(5);
				}
			}
				/** 고대의 가호 타입 **/
				if (itemId == 900022) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeC(39);
						os.writeS("\\fI효과:\\aA착용중 사망시");
						os.writeC(39);
						os.writeS("\\aA패널티없음");
						break;
					default:
						break;
					}
				}
	
				/** 신성한 엘름의 축복 **/
				if (itemId == 900035 || itemId == 900072 && getEnchantLevel() >= 5) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeC(15);
						os.writeH(getMr() + 4);
						break;
					case 6:
						os.writeC(15);
						os.writeH(getMr() + 8);
						break;
					case 7:
						os.writeC(15);
						os.writeH(getMr() + 12);
						break;
					case 8:
						os.writeC(15);
						os.writeH(getMr() + 16);
						break;
					case 9:
						os.writeC(15);
						os.writeH(getMr() + 20);
						break;
					case 10:
						os.writeC(15);
						os.writeH(getMr() + 24);
						break;
					default:
						break;
					}
				}
				/** 흑기사의 면갑 **/
				if (itemId == 900038 || itemId == 900054 && getEnchantLevel() >= 5) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeC(15);
						os.writeH(getMr() + 4);
						break;
					case 6:
						os.writeC(15);
						os.writeH(getMr() + 8);
						break;
					case 7:
						os.writeC(15);
						os.writeH(getMr() + 12);
						break;
					case 8:
						os.writeC(15);
						os.writeH(getMr() + 16);
						break;
					case 9:
						os.writeC(15);
						os.writeH(getMr() + 20);
						break;
					case 10:
						os.writeC(15);
						os.writeH(getMr() + 24);
						break;
					default:
						break;
					}
				}
				/** 신성한 완력의 목걸이 **/
				if (itemId == 900039) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 10)
						os.writeShortHIT(2);
				}
				/** 신성한 민첩의 목걸이 **/
				if (itemId == 900040) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 10)
						os.writeLongHIT(2);
				}
				/** 신성한 지식의 목걸이 **/
				if (itemId == 900041) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 10)
						os.writeMagicHIT(2);
				}
				/** 수호성의 파워 글로브 **/
				if (itemId == 900036 || itemId == 900073 && getEnchantLevel() >= 5) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeShortHIT(2);
						break;
					case 7:
						os.writeShortHIT(3);
						break;
					case 8:
						os.writeShortHIT(4);
						break;
					case 9:
						os.writeShortHIT(5);
						break;
					case 10:
						os.writeShortHIT(5);
						break;
					default:
						if (getEnchantLevel() > 10) {
							os.writeShortHIT(5);
						}
						break;
					}
				}
				/** 수호성의 활 골무 **/
				if (itemId == 900037 || itemId == 900074 && getEnchantLevel() >= 5) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeLongHIT(1);
						break;
					case 6:
						os.writeLongHIT(2);
						break;
					case 7:
						os.writeLongHIT(3);
						break;
					case 8:
						os.writeLongHIT(4);
						break;
					case 9:
						os.writeLongHIT(5);
						break;
					case 10:
						os.writeLongHIT(5);
						break;
					default:
						if (getEnchantLevel() > 10) {
							os.writeLongHIT(5);
						}
						break;
					}
				}
				/** 격분의 장갑 **/
				if (itemId == 222317) {
					switch (getEnchantLevel()) {
					case 7:
						os.writeShortHIT(4);
						break;
					case 8:
						os.writeShortHIT(5);
						break;
					case 9:
						os.writeShortHIT(6);
						break;
					case 10:
						os.writeShortHIT(7);
						break;
					default:
						if (getEnchantLevel() > 10) {
							os.writeShortHIT(getEnchantLevel() - 3);
						}
						break;
					}
				}

				if (itemId == 22214) {

					if (getEnchantLevel() > 6) {
						os.writeMagicHIT(getEnchantLevel() - 6);

					}
				}

				if (itemId == 20279) {

					os.writePVPAddDMGdown(2);

				}
				if (itemId == 20314) {

					os.writePVPAddDMG(2);

				}

				/** 머미로드의 왕관 **/
				if (itemId == 20017) {
					int cc = getEnchantLevel() - 4;
					if (getEnchantLevel() > 4) {
						os.writeLongDMG(cc);
						os.writeAddMR(cc * 3);

					}
				}
				/** 체력의 각반 **/
				if (itemId == 900023) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(10);
						break;
					case 1:
						os.writeAddMaxHP(15);
						break;
					case 2:
						os.writeAddMaxHP(20);
						break;
					case 3:
						os.writeAddMaxHP(25);
						break;
					case 4:
						os.writeAddMaxHP(30);
						break;
					case 5:
						os.writeAddMaxHP(35);
						break;
					case 6:
						os.writeAddMaxHP(40);
						break;
					case 7:
						os.writeAddMaxHP(45);
						break;
					case 8:
						os.writeAddMaxHP(50);
						break;
					case 9:
						os.writeAddMaxHP(55);
						break;
					default:
						break;
					}
				}
				/** 예언자의 견갑 **/
				if (itemId == 900080) {
					if (getEnchantLevel() >= 0 && getEnchantLevel() <= 15)
						os.writeMagicHIT(1);
				}

				/** 맘보 모자 **/
				if (itemId == 20016 || itemId == 120016 || itemId == 20112 || itemId == 120112) {
					switch (getEnchantLevel()) {
					case 7:
					case 8:
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaCHA_Bu(1);
						break;
					default:
						break;
					}
				}
				/** 투사의 휘장 **/
				if (itemId == 900152) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortDMG(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeShortDMG(2);
						os.writeShortCritical(1);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeShortDMG(3);
						os.writeShortCritical(3);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeShortDMG(4);
						os.writeShortCritical(5);
						break;
					default:
						break;
					}
				}
				/** 명궁의 휘장 **/
				if (itemId == 900153) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeLongDMG(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeLongDMG(2);
						os.writeLongCritical(1);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeLongDMG(3);
						os.writeLongCritical(3);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeLongDMG(4);
						os.writeLongCritical(5);
						break;
					default:
						break;
					}
				}
				if (itemId == 123001) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeability_pierce(2);
						os.writeaspirit_pierce(2);
						os.writeafear_pierce(2);
						break;
					case 9:
						os.writeability_pierce(3);
						os.writeaspirit_pierce(3);
						os.writeafear_pierce(3);
						break;
					case 10:
						os.writeability_pierce(4);
						os.writeaspirit_pierce(4);
						os.writeafear_pierce(4);
						break;
					case 11:
						os.writeability_pierce(5);
						os.writeaspirit_pierce(5);
						os.writeafear_pierce(5);
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeability_pierce(5);
							os.writeaspirit_pierce(5);
							os.writeafear_pierce(5);
						}
						break;
					}
				}
				if (itemId == 123002) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
						os.writeability_pierce(5);
						break;
					case 8:
						os.writeability_pierce(6);
						break;
					case 9:
						os.writeability_pierce(7);
						break;
					case 10:
						os.writeability_pierce(8);
						break;
					case 11:
						os.writeability_pierce(8);
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeability_pierce(8);
						}
						break;
					}
				}
				if (itemId == 123003) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+2");
						break;
					case 9:
						os.writeaspirit_pierce(1);
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+3");
						break;
					case 10:
						os.writeaspirit_pierce(2);
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+4");
						break;
					case 11:
						os.writeaspirit_pierce(3);
						os.write(0x27);
						os.writeS("\\f9리덕션 무시: \\aA+5");
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeaspirit_pierce(3);
							os.write(0x27);
							os.writeS("\\f9리덕션 무시: \\aA+5");
						}
						break;
					}
				}
				if (itemId == 123004) {
					os.write(0x27);
					os.writeS("\\f9더블 발동 확률 증가");
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeaspirit_pierce(2);
						break;
					case 9:
						os.writeaspirit_pierce(3);
						break;
					case 10:
						os.writeaspirit_pierce(4);
						break;
					case 11:
						os.writeaspirit_pierce(5);
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeaspirit_pierce(5);
						}
						break;
					}
				}
				if (itemId == 123005) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeMagicHIT(1);
						break;
					case 9:
						os.writeMagicHIT(2);
						break;
					case 10:
						os.writeMagicHIT(3);
						break;
					case 11:
						os.writeMagicHIT(4);
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeMagicHIT(4);
						}
						break;
					}
				}

				if (itemId == 123006) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeafear_pierce(2);
						break;
					case 9:
						os.writeafear_pierce(3);
						break;
					case 10:
						os.writeafear_pierce(4);
						break;
					case 11:
						os.writeafear_pierce(5);
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeafear_pierce(5);
						}
						break;
					}
				}
				if (itemId == 123007) {
					os.write(0x27);
					os.writeS("\\f9약점 노출 확률 증가");
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeadragonS_pierce(5);
						break;
					case 9:
						os.writeadragonS_pierce(6);
						break;
					case 10:
						os.writeadragonS_pierce(7);
						break;
					case 11:
						os.writeadragonS_pierce(8);
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeadragonS_pierce(8);
						}
						break;
					}
				}
				if (itemId == 123008) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
					case 7:
					case 8:
						os.writeadragonS_pierce(5);
						os.writeAddSP(1);
						break;
					case 9:
						os.writeadragonS_pierce(5);
						os.writeAddSP(2);
						break;
					case 10:
						os.writeadragonS_pierce(5);
						os.writeAddSP(3);
						break;
					case 11:
						os.writeadragonS_pierce(5);
						os.writeAddSP(4);
						break;
					default:
						if (getEnchantLevel() > 11) {
							os.writeadragonS_pierce(5);
							os.writeAddSP(4);
						}
						break;
					}
				}
				
				if (itemId == 808011) {
					 os.writeC(39);
					 os.writeS("\\fv인챈: 안전 +0/최대 +5");
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMR(12);
						
						break;
					case 1:
						os.writeAddMR(26);
						os.writeAddAc(6);
						break;
					case 2:
						os.writeAddMR(28);
						os.writeAddAc(6);
						os.writeAddMaxHP(20);
						os.writeMaxMP(10);
						break;
					case 3:
						os.writeAddMR(30);
						os.writeAddAc(6);
						os.writeAddMaxHP(40);
						os.writeMaxMP(20);
						os.writeaAll_resis(3);
						break;
					case 4:
						os.writeAddMR(32);
						os.writeAddAc(6);
						os.writeAddMaxHP(60);
						os.writeMaxMP(30);
						os.writeaAll_resis(4);
						break;
					case 5:
						os.writeAddMR(34);
						os.writeAddAc(6);
						os.writeAddMaxHP(80);
						os.writeMaxMP(40);
						os.writeaAll_resis(5);
						break;
				
					default:
						if (getEnchantLevel() > 5) {
							os.writeAddMR(34);
							os.writeAddAc(6);
							os.writeAddMaxHP(80);
							os.writeMaxMP(40);
							os.writeaAll_resis(5);
						}
						break;
					}
				}
				// 할파스의 마력

				if (itemId == 123111) {
					//os.write(0x27);
					//os.writeS("\\f9대미지 리덕션 무시: \\aA+5");
					os.writeReductiondown(5);
					switch (getEnchantLevel()) {
					case 0:
						os.writeMagicCritical(5);
						os.writeDMGdown(7);
						break;
					case 1:
						os.writeMagicCritical(5);
						os.writeDMGdown(9);
						break;
					case 2:
						os.writeMagicCritical(5);
						os.writeDMGdown(11);
						break;
					case 3:
						os.writeMagicCritical(5);
						os.writeDMGdown(13);
						break;
					case 4:
						os.writeMagicCritical(5);
						os.writeDMGdown(15);
						break;
					case 5:
						os.writeMagicCritical(5);
						os.writeDMGdown(17);
						break;
					case 6:
						os.writeMagicCritical(5);
						os.writeDMGdown(19);
						break;
					case 7:
						os.writeMagicCritical(5);
						os.writeDMGdown(21);
						break;
					case 8:
						os.writeMagicCritical(5);
						os.writeDMGdown(23);
						break;
					case 9:
						os.writeMagicCritical(5);
						os.writeDMGdown(25);
						break;
					case 10:
						os.writeMagicCritical(5);
						os.writeDMGdown(27);
						break;
					default:
						if (getEnchantLevel() > 10) {
							os.writeMagicCritical(5);
							os.writeDMGdown(getEnchantLevel() * 2 + 7);
						}
						break;
					}
				}
				// 할파스의 예지력
				if (itemId == 123112) {
					//os.write(0x27);
					//os.writeS("\\f9대미지 리덕션 무시: \\aA+5");
					os.writeReductiondown(5);
					switch (getEnchantLevel()) {
					case 0:
						os.writeLongCritical(5);
						os.writeDMGdown(7);
						break;
					case 1:
						os.writeLongCritical(5);
						os.writeDMGdown(9);
						break;
					case 2:
						os.writeLongCritical(5);
						os.writeDMGdown(11);
						break;
					case 3:
						os.writeLongCritical(5);
						os.writeDMGdown(13);
						break;
					case 4:
						os.writeLongCritical(5);
						os.writeDMGdown(15);
						break;
					case 5:
						os.writeLongCritical(5);
						os.writeDMGdown(17);
						break;
					case 6:
						os.writeLongCritical(5);
						os.writeDMGdown(19);
						break;
					case 7:
						os.writeLongCritical(5);
						os.writeDMGdown(21);
						break;
					case 8:
						os.writeLongCritical(5);
						os.writeDMGdown(23);
						break;
					case 9:
						os.writeLongCritical(5);
						os.writeDMGdown(25);
						break;
					case 10:
						os.writeLongCritical(5);
						os.writeDMGdown(27);
						break;
					default:
						if (getEnchantLevel() > 10) {
							os.writeLongCritical(5);
							os.writeDMGdown(getEnchantLevel() * 2 + 7);
						}
						break;
					}
				}

				/** 현자의 휘장 **/
				if (itemId == 900154) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeShortHIT(2);
						os.writeMagicCritical(1);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeShortHIT(3);
						os.writeMagicCritical(2);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeShortHIT(4);
						os.writeMagicCritical(4);
						break;
					default:
						break;
					}
				}
				/** 커츠의 투사 휘장 **/
				if (itemId == 900081) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortDMG(1);
						os.writeDMGdown(1);
						os.writePVPAddDMGdown(1);
						os.writeaBlesssomo(5);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeShortDMG(2);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						os.writeShortHIT(1);
						os.writePVPAddDMGdown(2);
						os.writeaBlesssomo(10);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeShortDMG(3);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						os.writeShortHIT(3);
						os.writePVPAddDMGdown(3);
						os.writeaBlesssomo(15);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeShortDMG(4);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						os.writeShortHIT(5);
						os.writePVPAddDMGdown(5);
						os.writeaBlesssomo(20);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeShortDMG(5);
						os.writeDMGdown(5);
						os.writeAddMR(9);
						os.writeShortHIT(7);
						os.writePVPAddDMGdown(7);
						os.writeaBlesssomo(25);
						break;
					default:
						break;
					}
				}
				/** 커츠의 명궁 휘장 **/
				if (itemId == 900082) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeLongDMG(1);
						os.writeDMGdown(1);
						os.writePVPAddDMGdown(1);
						os.writeaBlesssomo(5);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeLongDMG(2);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						os.writeLongHIT(1);
						os.writePVPAddDMGdown(2);
						os.writeaBlesssomo(10);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeLongDMG(3);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						os.writeLongHIT(3);
						os.writePVPAddDMGdown(3);
						os.writeaBlesssomo(15);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeLongDMG(4);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						os.writeLongHIT(5);
						os.writePVPAddDMGdown(5);
						os.writeaBlesssomo(20);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeLongDMG(5);
						os.writeDMGdown(5);
						os.writeAddMR(9);
						os.writeLongHIT(7);
						os.writePVPAddDMGdown(7);
						os.writeaBlesssomo(25);
						break;
					default:
						break;
					}
				}
				/** 커츠의 현자 휘장 **/
				if (itemId == 900083) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeShortHIT(1);
						os.writeDMGdown(1);
						os.writePVPAddDMGdown(1);
						os.writeaBlesssomo(5);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						os.writeShortHIT(2);
						os.writeMagicCritical(1);
						os.writeMagicHIT(1);
						os.writePVPAddDMGdown(2);
						os.writeaBlesssomo(10);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						os.writeShortHIT(3);
						os.writeMagicCritical(2);
						os.writeMagicHIT(3);
						os.writePVPAddDMGdown(3);
						os.writeaBlesssomo(15);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						os.writeShortHIT(4);
						os.writeMagicCritical(3);
						os.writeMagicHIT(5);
						os.writePVPAddDMGdown(5);
						os.writeaBlesssomo(20);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeDMGdown(5);
						os.writeAddMR(9);
						os.writeShortHIT(5);
						os.writeMagicCritical(4);
						os.writeMagicHIT(7);
						os.writePVPAddDMGdown(7);
						os.writeaBlesssomo(25);
						break;
					default:
						break;
					}
				}
				/** 커츠의 수호 휘장 **/
				if (itemId == 900084) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeAddMaxHP(5);
						break;
					case 1:
						os.writeAddMaxHP(10);
						break;
					case 2:
						os.writeAddMaxHP(15);
						break;
					case 3:
						os.writeAddMaxHP(20);
						break;
					case 4:
						os.writeAddMaxHP(25);
						break;
					case 5:
						os.writeAddMaxHP(30);
						os.writeDMGdown(1);
						break;
					case 6:
						os.writeAddMaxHP(35);
						os.writeDMGdown(2);
						os.writeAddMR(3);
						break;
					case 7:
						os.writeAddMaxHP(40);
						os.writeDMGdown(3);
						os.writeAddMR(5);
						break;
					case 8:
						os.writeAddMaxHP(50);
						os.writeDMGdown(4);
						os.writeAddMR(7);
						break;
					default:
						break;
					}
				}
				/** 대마법사의 모자 **/
				if (itemId == 202022) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 4:
					case 5:
						os.writeaspirit_resis(2);
						break;
					case 6:
						os.writeaspirit_resis(3);
						break;
					case 7:
						os.writeaspirit_resis(4);
						break;
					case 8:
						os.writeaspirit_resis(5);
						break;
					case 9:
						os.writeaspirit_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaspirit_resis(7);
						break;
					default:
						break;
					}
				}
				if (itemId == 20050) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+1");
						break;
					case 6:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+2");
						break;
					case 7:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+3");
						break;
					case 8:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+4");
						break;
					case 9:
						os.write(0x27);
						os.writeS("\\f9ER: \\aA+5");
					
						break;
						
					default:
						if (getEnchantLevel() > 9) {
							os.write(0x27);
							os.writeS("\\f9ER: \\aA+5");
						}
						break;
					}
				}
				/** 거대여왕 개미의 금빛,은빛 날개 **/
				if (itemId == 20049 || itemId == 20050 || itemId == 900056 || itemId == 900057) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeaspirit_resis(3);
						break;
					case 6:
						os.writeaspirit_resis(4);
						break;
					case 7:
						os.writeaspirit_resis(5);
						break;
					case 8:
						os.writeaspirit_resis(6);
						break;
					case 9:
						os.writeaspirit_resis(7);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaspirit_resis(7);
						break;
					default:
						break;
					}
				}
				/** 안타라스의 완력/예지력/인내력/마력 **/
				if (itemId >= 22196 && itemId <= 22199) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(2);
						os.writeDMGdown(3);
						break;
					case 6:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(3);
						os.writeDMGdown(3);
						break;
					case 7:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(4);
						os.writeDMGdown(4);
						break;
					case 8:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(5);
						os.writeDMGdown(5);
						break;
					case 9:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(6);
						os.writeDMGdown(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeC(70);
						os.writeC(2);
						os.writeadragonS_resis(7);
						os.writeDMGdown(6);
						break;
					default:
						break;
					}
				}
				/** 파푸리온의 완력/예지력/인내력/마력 **/
				if (itemId >= 22200 && itemId <= 22203) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeadragonS_resis(2);
						break;
					case 6:
						os.writeadragonS_resis(3);
						break;
					case 7:
						os.writeadragonS_resis(4);
						break;
					case 8:
						os.writeadragonS_resis(5);
						break;
					case 9:
						os.writeadragonS_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_resis(7);
						break;
					default:
						break;
					}
				}
				/** 린드비오르의 완력/예지력/인내력/마력 **/
				if (itemId >= 22204 && itemId <= 22207) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeadragonS_resis(2);
						break;
					case 5:
						os.writeadragonS_resis(3);
						break;
					case 6:
						os.writeadragonS_resis(4);
						break;
					case 7:
						os.writeadragonS_resis(5);
						break;
					case 8:
						os.writeadragonS_resis(6);
						break;
					case 9:
						os.writeadragonS_resis(7);
						break;
					case 10:
						os.writeadragonS_resis(8);
						break;
					default:
						if (getEnchantLevel() > 10) {
							os.writeadragonS_resis(9);
						}
						break;
					}
				}
				/** 발라카스의 완력 **/
				if (itemId >= 22208 && itemId <= 22211) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeReductiondown(3);
						os.writeadragonS_resis(2);
						break;
					case 6:
						os.writeReductiondown(3);
						os.writeadragonS_resis(3);
						break;
					case 7:
						os.writeReductiondown(4);
						os.writeadragonS_resis(4);
						break;
					case 8:
						os.writeReductiondown(5);
						os.writeadragonS_resis(5);
						break;
					case 9:
						os.writeReductiondown(6);
						os.writeadragonS_resis(6);
						break;
					case 10:
						os.writeReductiondown(7);
						os.writeadragonS_resis(7);
						break;
					default:
						break;
					}
				}

			
				/** 에바의 방패 **/
				if (itemId == 20235) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeadragonS_resis(2);
						break;
					case 6:
						os.writeadragonS_resis(3);
						break;
					case 7:
						os.writeadragonS_resis(4);
						break;
					case 8:
						os.writeadragonS_resis(5);
						break;
					case 9:
						os.writeadragonS_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeadragonS_resis(7);
						break;
					default:
						break;
					}
				}
				/** 시어의 심안 **/
				if (itemId == 22214) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeaspirit_resis(3);
						break;
					case 6:
						os.writeaspirit_resis(4);
						break;
					case 7:
						os.writeaspirit_resis(5);
						break;
					case 8:
						os.writeaspirit_resis(6);
						break;
					case 9:
						os.writeaspirit_resis(7);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaspirit_resis(8);
						break;
					default:
						break;
					}
				}
				/** 리치의 반지 **/
				if (itemId == 900163) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
						os.writeAddSP(3);
						os.writeMagicCritical(3);
						break;
					case 7:
						os.writeAddSP(4);
						os.writeMagicCritical(3);
						break;
					case 8:
						os.writeAddSP(5);
						os.writeMagicCritical(3);
						break;
					case 9:
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(6);
						os.writeMagicCritical(3);
						break;
					default:
						break;
					}
				}

				/** 뱀파이어의 망토 **/
				if (itemId == 20079) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeafear_resis(2);
						break;
					case 6:
						os.writeafear_resis(3);
						break;
					case 7:
						os.writeafear_resis(4);
						break;
					case 8:
						os.writeafear_resis(5);
						break;
					case 9:
						os.writeafear_resis(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeafear_resis(7);
						break;
					default:
						break;
					}
				}
				/** 아이리스의 부츠 **/
				if (itemId == 900155) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeaAll_resis(1);
						break;
					case 6:
						os.writeaAll_resis(2);
						break;
					case 7:
						os.writeaAll_resis(3);
						break;
					case 8:
						os.writeaAll_resis(4);
						break;
					case 9:
						os.writeaAll_resis(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
				}

				/** 머미로드의 장갑 **/
				if (itemId == 900156) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
					case 6:
						os.writeAddSP(1);
						break;
					case 7:
						os.writeAddSP(2);
						break;
					case 8:
						os.writeAddSP(3);
						break;
					case 9:
						os.writeAddSP(3);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(3);
						break;
					default:
						break;
					}
				}
				/** 쿠거의 가더 **/
				if (itemId == 900157) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeShortHIT(3);
						break;
					case 5:
						os.writeShortHIT(4);
						os.writeaAll_resis(1);
						break;
					case 6:
						os.writeShortHIT(5);
						os.writeaAll_resis(2);
						break;
					case 7:
						os.writeShortHIT(6);
						os.writeaAll_resis(3);
						break;
					case 8:
						os.writeShortHIT(7);
						os.writeaAll_resis(4);
						break;
					case 9:
						os.writeShortHIT(8);
						os.writeaAll_resis(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortHIT(8);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
				}
				/** 우그누스의 가더 **/
				if (itemId == 900158) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongHIT(3);
						break;
					case 5:
						os.writeLongHIT(4);
						os.writeaAll_resis(1);
						break;
					case 6:
						os.writeLongHIT(5);
						os.writeaAll_resis(2);
						break;
					case 7:
						os.writeLongHIT(6);
						os.writeaAll_resis(3);
						break;
					case 8:
						os.writeLongHIT(7);
						os.writeaAll_resis(4);
						break;
					case 9:
						os.writeLongHIT(8);
						os.writeaAll_resis(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongHIT(8);
						os.writeaAll_resis(5);
						break;
					default:
						break;
					}
				}
				/** 나이트발드의 각반 **/
				if (itemId == 900159) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeShortHIT(1);
						break;
					case 6:
						os.writeShortHIT(2);
						break;
					case 7:
						os.writeShortHIT(3);
						break;
					case 8:
						os.writeShortHIT(4);
						break;
					case 9:
						os.writeShortHIT(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortHIT(5);
						break;
					default:
						break;
					}
				}
				/** 아이리스의 각반 **/
				if (itemId == 900160) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeLongHIT(1);
						break;
					case 6:
						os.writeLongHIT(2);
						break;
					case 7:
						os.writeLongHIT(3);
						break;
					case 8:
						os.writeLongHIT(4);
						break;
					case 9:
						os.writeLongHIT(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongHIT(5);
						break;
					default:
						break;
					}
				}
				/** 뱀파이어의 각반 **/
				if (itemId == 900161) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeMagicHIT(1);
						break;
					case 6:
						os.writeMagicHIT(2);
						break;
					case 7:
						os.writeMagicHIT(3);
						break;
					case 8:
						os.writeMagicHIT(4);
						break;
					case 9:
						os.writeMagicHIT(5);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeMagicHIT(5);
						break;
					default:
						break;
					}
				}
				/** 지룡의 티셔츠 **/
				if (itemId == 900025) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writeAddMR(4);
						os.writeDMGdown(1);
						break;
					case 6:
						os.writeAddMR(5);
						os.writeDMGdown(1);
						break;
					case 7:
						os.writeAddMR(6);
						os.writeDMGdown(1);
						break;
					case 8:
						os.writeAddMR(8);
						os.writeAddEXP(2);
						os.writeDMGdown(1);
						break;
					case 9:
						os.writeAddMR(11);
						os.writeAddEXP(4);
						os.writeDMGdown(2);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddMR(14);
						os.writeAddEXP(6);
						os.writeDMGdown(2);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 화룡의 티셔츠 **/
				if (itemId == 900026) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeShortDMG(1);
						break;
					case 5:
						os.writeShortDMG(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeShortDMG(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeShortDMG(1);
						os.writeability_resis(10);
						break;
					case 8:
						os.writeShortDMG(1);
						os.writeShortHIT(2);
						os.writeability_resis(12);
						break;
					case 9:
						os.writeShortDMG(2);
						os.writeShortHIT(4);
						os.writeability_resis(15);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortDMG(2);
						os.writeShortHIT(6);
						os.writeability_resis(18);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 풍룡의티셔츠 **/
				if (itemId == 900027) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
						os.writeLongDMG(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeLongDMG(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeLongDMG(1);
						os.writeability_resis(10);
						break;
					case 8:
						os.writeLongDMG(1);
						os.writeLongHIT(2);
						os.writeability_resis(12);
						break;
					case 9:
						os.writeLongDMG(2);
						os.writeLongHIT(4);
						os.writeability_resis(15);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongDMG(2);
						os.writeLongHIT(6);
						os.writeability_resis(18);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 수룡의 티셔츠 **/
				if (itemId == 900028) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddSP(1);
						break;
					case 5:
						os.writeAddSP(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeAddSP(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeAddSP(1);
						os.writeability_resis(10);
						break;
					case 8:
						os.writeAddSP(2);
						os.writeMagicHIT(1);// 마법 적중
						os.writeability_resis(12);
						break;
					case 9:
						os.writeAddSP(2);
						os.writeMagicHIT(3);// 마법 적중
						os.writeability_resis(15);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(3);
						os.writeMagicHIT(4);// 마법 적중
						os.writeability_resis(18);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}

				/** 축복받은 지룡의 티셔츠 **/
				if (itemId == 900184) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeAddMR(4);
						break;
					case 6:
						os.writeAddMR(5);
						break;
					case 7:
						os.writeAddMR(6);
						os.writeDMGdown(1);
						os.writeAddEXP(2);
						break;
					case 8:
						os.writeAddMR(8);
						os.writeDMGdown(1);
						os.writeAddEXP(4);
						break;
					case 9:
						os.writeAddMR(11);
						os.writeDMGdown(2);
						os.writeAddEXP(6);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddMR(14);
						os.writeDMGdown(2);
						os.writeAddMaxHP(100);
						os.writeAddEXP(8);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 축복받은 화룡의티셔츠 **/
				if (itemId == 900185) {
					switch (getEnchantLevel()) {
					case 5:
						os.writeability_resis(8);
						break;
					case 6:
						os.writeability_resis(9);
						break;
					case 7:
						os.writeShortDMG(1);
						os.writeShortHIT(1);
						os.writeability_resis(10);
						os.writeaAll_resis(1);
						break;
					case 8:
						os.writeShortDMG(1);
						os.writeShortHIT(3);
						os.writeability_resis(12);
						os.writeaAll_resis(2);
						break;
					case 9:
						os.writeShortDMG(2);
						os.writeShortHIT(5);
						os.writeability_resis(15);
						os.writeaAll_resis(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeShortDMG(2);
						os.writeShortHIT(7);
						os.writeability_resis(18);
						os.writeaAll_resis(6);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 축복받은 풍룡의티셔츠 **/
				if (itemId == 900186) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
						os.writeLongDMG(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeLongDMG(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeLongDMG(1);
						os.writeLongHIT(1);
						os.writeability_resis(10);
						os.writeaAll_resis(1);
						break;
					case 8:
						os.writeLongDMG(1);
						os.writeLongHIT(3);
						os.writeability_resis(12);
						os.writeaAll_resis(2);
						break;
					case 9:
						os.writeLongDMG(2);
						os.writeLongHIT(5);
						os.writeability_resis(15);
						os.writeaAll_resis(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeLongDMG(2);
						os.writeLongHIT(7);
						os.writeability_resis(18);
						os.writeaAll_resis(6);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}
				/** 축복받은 수룡의 티셔츠 **/
				if (itemId == 900187) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddSP(1);
						break;
					case 5:
						os.writeAddSP(1);
						os.writeability_resis(8);
						break;
					case 6:
						os.writeAddSP(1);
						os.writeability_resis(9);
						break;
					case 7:
						os.writeAddSP(2);
						os.writeability_resis(10);
						os.writeaAll_resis(1);
						break;
					case 8:
						os.writeAddSP(2);
						os.writeMagicHIT(2);// 마법 적중
						os.writeability_resis(12);
						os.writeaAll_resis(2);
						break;
					case 9:
						os.writeAddSP(2);
						os.writeMagicHIT(4);// 마법 적중
						os.writeability_resis(15);
						os.writeaAll_resis(4);
						break;
					case 10:
					case 11:
					case 12:
					case 13:
					case 14:
					case 15:
						os.writeAddSP(3);
						os.writeMagicHIT(5);// 마법 적중
						os.writeability_resis(18);
						os.writeaAll_resis(6);
						os.writeAddMaxHP(100);
						os.writePVPAddDMG(1);
						os.writePVPAddDMGdown(1);
						break;
					default:
						break;
					}
				}

				/** 고대투사의가더 근거리 대미지 **/
				if (itemId == 22003) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeShortDMG(1);
						break;
					case 5:
					case 6:
						os.writeShortDMG(2);
						break;
					case 7:
					case 8:
						os.writeShortDMG(3);
						break;
					case 9:
					case 10:
						os.writeShortDMG(4);
						break;
					default:
						break;
					}
				}

				/** 고대명궁의가더 원거리 대미지 **/
				if (itemId == 22000) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeLongDMG(1);
						break;
					case 5:
					case 6:
						os.writeLongDMG(2);
						break;
					case 7:
					case 8:
						os.writeLongDMG(3);
						break;
					case 9:
					case 10:
						os.writeLongDMG(4);
						break;
					default:
						break;
					}
				}

				if (itemId == 20051) {
					switch (getEnchantLevel()) {
					case 0:
						os.writeC(15);
						os.writeH(getMr() + 4);
						break;
					case 1:
						os.writeC(15);
						os.writeH(getMr() + 6);
						break;
					default:
						if (getEnchantLevel() > 1) {
							os.writeC(15);
							os.writeH(getMr() + getEnchantLevel() * 2 + 4);
						}
						break;
					}
				}
				if (itemId == 22360) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeC(15);
						os.writeH(1);
						break;

					default:
						if (getEnchantLevel() > 4) {
							os.writeC(15);
							os.writeH(((getEnchantLevel() - 4) * 2 )+ 1);
						}
						break;
					}
				}
				
				if (itemId == 900007 || itemId == 500012) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
					case 5:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 2);
						break;
					case 6:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 3);
						break;
					case 7:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 5);
						break;
					case 8:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 7);
						break;
					case 9:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 9);
						break;
					case 10:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 11);
						break;
					}
				}

				/** 벨트 PVP 데미지 감소 **/
				if (getEnchantLevel() >= 6 && itemType2 == 2 && getItem().get장신구처리() == 10) {
					if (!(itemId == 900007 || itemId == 500012)) {
					
					switch (getEnchantLevel()) {
					case 6:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 1);
						break;
					case 7:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 3);
						break;
					case 8:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 5);
						break;
					case 9:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 7);
						break;
					case 10:
						os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense() + 9);
						break;
					}
					}
				}

				/** PVP 추가 데미지 **/
				if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& (getItem().get장신구처리() == 9 || getItem().get장신구처리() == 11)) {
					switch (getEnchantLevel()) {
					case 6:
						os.writePVPAddDMG(getItem().get_regist_PVPweaponTotalDamage() + 1);

						break;
					case 7:
						os.writePVPAddDMG(getItem().get_regist_PVPweaponTotalDamage() + 2);

						break;
					case 8:
						os.writePVPAddDMG(getItem().get_regist_PVPweaponTotalDamage() + 3);

						break;
					case 9:
						os.writePVPAddDMG(getItem().get_regist_PVPweaponTotalDamage() + 5);

						break;
					default:
						if (getEnchantLevel() > 9) {
							os.writePVPAddDMG(getItem().get_regist_PVPweaponTotalDamage() + 7);
						}
						break;
					}
				}
				
				/** 빛나는 마력의 장갑 **/
				
				if (itemId == 20274) {
					switch (getEnchantLevel()) {
					case 0:
					case 1:
					case 2:
					case 3:
					case 4:
						os.writeAddWeight(300);
						break;
					default:
						if (getEnchantLevel() > 4) {
							os.writeAddWeight(300 + ((getEnchantLevel() - 4) * 60));
						}
						break;
					}
				}
				
				if (itemId == 900090) {
					os.writeDMGdownprobability(getEnchantLevel(), 20);
				}
				if (itemId == 22263) {
					os.writeDMGdownprobability(getEnchantLevel() * 2, 50);
					if (getEnchantLevel() > 4) {
						os.writeAddMaxHP((getEnchantLevel() - 4) * 20);
					}
				}
				if (itemId == 900046 || itemId == 900071) {
					os.writeDMGdownprobability(getEnchantLevel(), 10);
				}
				if (itemId == 22359 || itemId == 222308 || itemId == 222309 || itemId == 222307) {
					switch (getEnchantLevel()) {
					case 7:
						os.writeAddMaxHP(20);
						break;
					case 8:
						os.writeAddMaxHP(40);
						break;
					case 9:
						os.writeAddMaxHP(60);
						os.writeDMGdown(1);
						break;
					case 10:
						os.writeAddMaxHP(60);
						os.writeDMGdown(1);
						break;
					default:
						break;
					}
				}
				
				// TODO 스냅퍼의 반지류 기술 내성 추가
				if (itemId == 22225 || itemId == 222331 || itemId == 22227 || itemId == 222333 || itemId == 22224
						|| itemId == 222330 || itemId == 222291 || itemId == 222336) {
					switch (getEnchantLevel()) {
					case 6:
						os.writeability_resis(5);
						break;
					case 7:
						os.writeability_resis(7);
						break;
					case 8:
						os.writeability_resis(9);
						if (itemId == 222291) {
							os.writeafear_resis(1);
						} else if (itemId == 222336) {
							os.writeafear_resis(2);
						}
						break;
					case 9:
						os.writeability_resis(9);
						if (itemId == 222291) {
							os.writeafear_resis(2);
						} else if (itemId == 222336) {
							os.writeafear_resis(4);
						}
						break;
					
					}
				}
				// TODO 축복받은 스냅퍼의 체력 반지 리뉴얼
				if (itemId == 222332) {
					switch (getEnchantLevel()) {
					case 6:
						os.writeDMGdown(1);
						os.writeDMGdownprobability(1, 20);
						os.writeability_resis(5);
						break;
					case 7:
						os.writeDMGdown(2);
						os.writeDMGdownprobability(2, 20);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeDMGdown(5);
						os.writeDMGdownprobability(3, 20);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					case 9:
						os.writeDMGdown(7);
						os.writeDMGdownprobability(5, 20);
						os.writeability_resis(9);
						os.writeafear_resis(4);
						break;
					}
				}
				
		        
				// TODO 스냅퍼의 체력 반지 리뉴얼
				if (itemId == 22226) {
					switch (getEnchantLevel()) {
					case 6:
						os.writeability_resis(5);
						break;
					case 7:
						os.writeDMGdown(1);
						os.writeDMGdownprobability(1, 20);
						os.writeability_resis(7);
						break;
					case 8:
						os.writeDMGdown(3);
						os.writeDMGdownprobability(2, 20);
						os.writeability_resis(9);
						os.writeafear_resis(1);
						break;
					case 9:
						os.writeDMGdown(5);
						os.writeDMGdownprobability(4, 20);
						os.writeability_resis(9);
						os.writeafear_resis(2);
						break;
					}
				}

				// 디비 아이템 추가 옵션 부분
				if (getItem().getHitModifier() != 0) { // 무기에 붙는 명중
					if (itemType2 == 1 && getItem().getType1() != 20) {
						os.writeShortHIT(getItem().getHitModifier());
					} else {
						os.writeLongHIT(getItem().getHitModifier());
					}
				}
				if (getItem().getHitRate() != 0) { // 근거리 명중
					os.writeShortHIT(getItem().getHitRate());
				} else {
					os.writeShortHIT(0);
				}
				if (getItem().getDmgRate() != 0) { // 근거리 데미지
					os.writeShortDMG(getItem().getDmgRate());
				}
				if (getItem().getBowHitRate() != 0) { // 원거리 명중
					os.writeLongHIT(getItem().getBowHitRate());
				}
				if (getItem().getBowDmgRate() != 0) { // 원거리 대미지
					os.writeLongDMG(getItem().getBowDmgRate());
				}
				// STR~CHA
				if (getItem().get_addstr() != 0) { // 스탯 힘
					os.writeaSTR_Bu(getItem().get_addstr());
				}
				if (getItem().get_adddex() != 0) { // 스탯 덱스
					os.writeaDEX_Bu(getItem().get_adddex());
				}
				if (getItem().get_addcon() != 0) { // 스탯 콘
					os.writeaCON_Bu(getItem().get_addcon());
				}
				if (getItem().get_addwis() != 0) { // 스탯 위즈
					os.writeaWIS_Bu(getItem().get_addwis());
				}
				if (getItem().get_addint() != 0) { // 스탯 인트
					os.writeaINT_Bu(getItem().get_addint());
				}
				if (getItem().get_addcha() != 0) { // 스탯 카리
					os.writeaCHA_Bu(getItem().get_addcha());
				}
				if (getItem().get_addeinhasadper() != 0) { // 축복 소모 효율
					os.writeaBlesssomo(getItem().get_addeinhasadper());
				}
				if (getItem().getMinLevel() != 0) { // 최소 사용 레벨
					os.writeUseLevel(getItem().getMinLevel());
				}
				if (getItem().getMaxLevel() != 0) { // 최소~최대 사용 레벨
					os.writeLimitLevel(getItem().getMinLevel(), getItem().getMaxLevel());
				}
				if (addsp() != 0) { // sp
					os.writeAddSP(addsp());
				}
				if (getItem().isHasteItem()) { // 헤이스트 아이템
					os.writeC(39);
					os.writeS("\\fI효과: \\aA헤이스트");
				}
				if (getItem().get_damage_reduction() != 0) { // 대미지 리덕션
					os.writeDMGdown(getItem().get_damage_reduction());
				}
				if (getItem().getWeightReduction() != 0) { // 무게 게이지
					os.writeAddWeight(getItem().getWeightReduction());
				}
				if (getItem().get_regist_PVPweaponTotalDamage() != 0) { // PVP 대미지
					os.writePVPAddDMG(getItem().get_regist_PVPweaponTotalDamage());
				}
				if (getItem().get_regist_calcPcDefense() != 0) { // PVP 리덕션
					os.writePVPAddDMGdown(getItem().get_regist_calcPcDefense());
				}
				if (getItem().get_melee_critical_probability() > 0) { // 근거리 치명타
					os.writeShortCritical(getItem().get_melee_critical_probability());
				}
				if (getItem().get_missile_critical_probability() > 0) { // 원거리
																		// 치명타
					os.writeLongCritical(getItem().get_missile_critical_probability());
				}
				if (getItem().getMagicHitup() != 0) { // 마법 적중
					os.writeMagicHIT(getItem().getMagicHitup());
				}

				// 불의 속성
				if (getItem().get_defense_fire() != 0) {
					os.writeRegistFire(getItem().get_defense_fire());
				}
				// 물의 속성
				if (getItem().get_defense_water() != 0) {
					os.writeRegistWater(getItem().get_defense_water());
				}
				// 바람 속성
				if (getItem().get_defense_wind() != 0) {
					os.writeRegistWind(getItem().get_defense_wind());
				}
				// 땅의 속성
				if (getItem().get_defense_earth() != 0) {
					os.writeRegistEarth(getItem().get_defense_earth());
				}
				
				if (getItem().getMagicName() != null) {
				    os.writeC(74);
				    os.writeS(getItem().getMagicName());
				}

				if (itemType2 == 2) {
					int pierce = getItem().getSpecialStats().getPierce(eKind.ABILITY);
					int lvl = getEnchantLevel();
					if (itemId == 22263 && getEnchantLevel() > 4) {
						int tempval = (lvl - 4);
						pierce += tempval;
					}

					if (pierce > 0) {
						os.writeC(122);
						os.writeC(pierce);
					}

					pierce = getItem().getSpecialStats().getPierce(eKind.SPIRIT);
					if (pierce > 0) {
						os.writeC(123);
						os.writeC(pierce);
					}

					pierce = getItem().getSpecialStats().getPierce(eKind.DRAGON_SPELL);
					if (pierce > 0) {
						os.writeC(124);
						os.writeC(pierce);
					}

					pierce = getItem().getSpecialStats().getPierce(eKind.FEAR);
					if (pierce > 0) {
						os.writeC(125);
						os.writeC(pierce);
					}

					pierce = getItem().getSpecialStats().getPierce(eKind.ALL);
					if (pierce > 0) {
						os.writeC(126);
						os.writeC(pierce);
					}
				}

				// 정령내성
				int resistance = getItem().getSpecialStats().getResist(eKind.SPIRIT);
				if (resistance > 0) {
					os.writeC(118);
					os.writeC(resistance);
				}

				// 용언내성
				resistance = getItem().getSpecialStats().getResist(eKind.DRAGON_SPELL);
				if (resistance > 0) {
					os.writeC(119);
					os.writeC(resistance);
				}

				// 공포내성
				resistance = getItem().getSpecialStats().getResist(eKind.FEAR);
				if ( itemType2 == 2 && (getItem().get장신구처리() == 12)) {
					int tempval = 0;
					if (getEnchantLevel() >= 10) 
						tempval = 2;
					resistance += tempval;
				}
				if (itemType2 == 2 && (getItem().get장신구처리() == 8)) {
					int tempval = 0;
					if (getEnchantLevel() >= 10) 
						tempval = 2;
					resistance += tempval;
				}
				if (itemType2 == 2 && (getItem().get장신구처리() == 10)) {
					int tempval = 0;
					if (getEnchantLevel() >= 10) 
						tempval = 2;
					resistance += tempval;
				}
				if (resistance > 0) {
					os.writeC(120);
					os.writeC(resistance);
				}

				// 전체내성
				resistance = getItem().getSpecialStats().getResist(eKind.ALL);
				
				if ( itemType2 == 2 && (getItem().get장신구처리() == 12)) {
					int tempval = 0;
					if (getEnchantLevel() >= 10) 
						tempval = 1;
					resistance += tempval;
				}
				if (itemType2 == 2 && ((itemgrade >= 0 && itemgrade <= 2) || itemgrade == 5)
						&& getItem().get장신구처리() == 9 || getItem().get장신구처리() == 11) {
					int tempval = 0;
					if (getEnchantLevel() >= 10 ) {
						tempval = 1;
						resistance += tempval;
					}
				}
				if (resistance > 0) {
					os.writeC(121);
					os.writeC(resistance);
				}

				// 기술내성
				resistance = getItem().getSpecialStats().getResist(eKind.ABILITY);
				int lvl = getEnchantLevel();

				if (getEnchantLevel() >= 7 && itemType2 == 2
						&& (getItem().get장신구처리() == 8 || getItem().get장신구처리() == 12)) {
					int tempval = (lvl - 5);
					if (lvl >= 10) {
						tempval = 4;
					} 
					resistance += tempval;
				}
				if (itemId == 22360 && getEnchantLevel() > 4) {
					int tempval = (lvl - 4);
					resistance += tempval;
				}

				if (resistance > 0) {
					os.writeC(117);
					os.writeC(resistance);
				}


				if (getItem().getType2() == 1 && get_bless_level() != 0) {//sgmall
					int type = getItem().getType();
					if (getItem().getType2() == 1 ) { 
					if (type == 7 || type == 16 || type == 17) {
						os.writeC(39);
						os.writeS("축복" + get_bless_level() + "단: SP +" + get_bless_level());
					} else {
						os.writeC(39);
						os.writeS("축복" + get_bless_level() + "단: DMG +" + get_bless_level());
					}
					} 
					if (getItem().getType2() == 2 ) { 
						if (get_bless_level() == 1) {
							os.writeC(39); 
							os.writeS("\\aL최대HP : 30");
						} else if (get_bless_level() == 2) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 20");
						} else if (get_bless_level() == 3) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 10");
						} else if (get_bless_level() == 4) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 30");
						} else if (get_bless_level() == 5) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 40");
						} else if (get_bless_level() == 6) {
							os.writeC(39);
							os.writeS("\\aL최대HP : 50");
						} else if (get_bless_level() == 7) {
							os.writeC(39);
							os.writeS("\\aL리덕션 : +1");
						} else if (get_bless_level() == 8) {
							os.writeC(39);
							os.writeS("\\aL리덕션 : +2");
						} else if (get_bless_level() == 10) {
							os.writeC(39);
							os.writeS("\\aL기술적중 : +1");
						} else if (get_bless_level() == 11) {
							os.writeC(39);
							os.writeS("\\aL공포적중 : +1");
						} else if (get_bless_level() == 12) {
							os.writeC(39);
							os.writeS("\\aL용언적중 : +1");
						} else if (get_bless_level() == 13) {
							os.writeC(39);
							os.writeS("\\aL정령적중 : +1");
						} else if (get_bless_level() == 14) {
							os.writeC(39);
							os.writeS("\\aL마법적중 : +1");
						} else if (get_bless_level() == 15) {
							os.writeC(39);
							os.writeS("\\aL기술내성 : +1");
						} else if (get_bless_level() == 16) {
							os.writeC(39);
							os.writeS("\\aL공포내성 : +1");
						} else if (get_bless_level() == 17) {
							os.writeC(39);
							os.writeS("\\aL용언내성 : +1");
						} else if (get_bless_level() == 18) {
							os.writeC(39);
							os.writeS("\\aL정령내성 : +1");
						} else if (get_bless_level() == 19) {
							os.writeC(39);
							os.writeS("\\aL근거리대미지 : +1");
						} else if (get_bless_level() == 20) {
							os.writeC(39);
							os.writeS("\\aL원거리대미지 : +1");
						} else if (get_bless_level() == 21) {
							os.writeC(39);
							os.writeS("\\aL근거리명중 : +1");
						} else if (get_bless_level() == 22) {
							os.writeC(39);
							os.writeS("\\aL원거리명중 : +1");
						} else if (get_bless_level() == 23) {
							os.writeC(39);
							os.writeS("\\aL리덕션 : +1");
						} else if (get_bless_level() == 24) {
							os.writeC(39);
							os.writeS("\\aL추가방어력 : +1");
						} else if (get_bless_level() == 25) {
							os.writeC(39);
							os.writeS("\\aLMR : +3");
						} else if (get_bless_level() == 26) {
							os.writeC(39);
							os.writeS("\\aLSP : +1");
						}
					}
				}
				if (get_bless_level2() != 0) {
					int level = get_bless_level2();
					double dmg = (level *1.0)/10; 
					int type = getItem().getType();
					if (getItem().getType2() == 1 ) { 						
					if (type == 7 || type == 16 || type == 17) {						
						os.writeC(39);
						os.writeS("품질: " + get_bless_level2());
						os.writeC(39);
						os.writeS("추댐: "+ dmg +"%");
					} else {
						os.writeC(39);
						os.writeS("품질: " + get_bless_level2());
						os.writeC(39);
						os.writeS("추댐: "+ dmg +"%");
					}
					} else {
						os.writeC(39);
						os.writeS("품질: " + get_bless_level2());
						os.writeC(39);
						os.writeS("추가HP: "+ get_bless_level2());
					}
				}
				/** 특수 인챈트 시스템 **/

				if (getItemId() == 20100 || getItemId() == 20099 || getItemId() == 20150 || getItemId() == 22258
						|| getItemId() == 20118 || getItemId() == 20151 || getItemId() == 22301
						|| getItemId() == 20091) { // 세트아이템
					os.writeC(69); // -- 셋트아이템에 대한 표기 ( 고정값 )
					os.writeC(get_main_set_armor() ? 1 : 2); // -- 1: 셋트 아이템 다
																// 착용시 /
																// 2: 셋트 아이템 미
																// 착용시
					// os.writeC(71); // -- [ 셋트 보너스 ] //더미값지정 본섭화
					switch (getItemId()) {
					case 20100:
						os.writeC(71);
						os.writeH(23929);// 검은 데스나이트
						os.writeAddAc(10);
						os.writeaSTR_Bu(2);
						os.writeShortDMG(2);
						break;
					case 20099:
						os.writeC(71);
						os.writeH(4241);// 데몬
						os.writeAddAc(2);
						os.writeAddHPPrecovery(5);
						break;
					case 22258:
						os.writeAddAc(5);
						os.writeAddMR(15);
						os.writeAddMaxHP(100);
						os.writeAddHPPrecovery(8);
						os.writeAddMPPrecovery(4);
						os.writeRegistWater(20);
						break;
					case 20150:
						os.writeC(71);
						os.writeH(23926);// 커츠
						os.writeAddAc(10);
						os.writeaCON_Bu(2);
						os.writeAddMaxHP(100);
						os.writeDMGdown(2);
						break;
					case 20118:
						os.writeC(71);
						os.writeH(23928);// 켄라우헬
						os.writeAddAc(3);
						os.writeAddMPPrecovery(18);
						break;
					case 20151:
						os.writeC(71);
						os.writeH(23927);// 케레니스
						os.writeaINT_Bu(2);
						os.writeAddMaxHP(100);
						os.writeAddMPPrecovery(18);
						break;
					case 22301:
						os.writeAddAc(3);
						os.writeAddHPPrecovery(4);
						os.writeAddMPPrecovery(1);
						break;
					case 20091:// 강철세트
						os.writeAddAc(3);
						break;
					}
					os.writeC(69); // -- dummy
					os.writeC(0x00); // -- dummy
				}
			}

			// 원거리 치명타
			if (itemId == 900082) {
				switch (getEnchantLevel()) {
				case 6:
					os.writeLongCritical(1);
					break;
				case 7:
					os.writeLongCritical(3);
					break;
				case 8:
					os.writeLongCritical(5);
					break;
				case 9:
					os.writeLongCritical(7);
					break;
				}
			}

			// 근거리 치명타
			if (itemId == 203019) { /** 포르세의 검 **/
				if (getEnchantLevel() >= 0 && getEnchantLevel() <= 15)
					os.writeShortCritical(10);
			} else if (itemId == 900081) {
				switch (getEnchantLevel()) {
				case 6:
					os.writeShortCritical(1);
					break;
				case 7:
					os.writeShortCritical(3);
					break;
				case 8:
					os.writeShortCritical(5);
					break;
				case 9:
					os.writeShortCritical(7);
					break;
				}
			}
		
			// TODO 라이브형식 자동삭제 표기
			/*
			 * if (itemId == 40312 && getEndTime() != null) { os.writeC(112);
			 * os.writeD((getEndTime().getTime() / 1000)); }
			 */
			if (getEndTime() != null) {
				if (isIdentified()) {
					long temp = Date.parse("January 1, 2017 0:00:00");
					os.writeC(61);
					os.writeD((getEndTime().getTime() - temp) / 1000 * 6);
				}

				// long temp = Date.parse("January 1, 2017 19:00:00");

				// os.writeC(112);
				// os.writeD((getEndTime().getTime() / 1000));
			}

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				os.close();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		return os.getBytes();
	}

	private L1PcInstance _owner;

	public static boolean 추타2무기(int itemId) {
		switch (itemId) {
		case 12:
		case 61:
		case 66:
		case 86:
		case 134:		
		case 202013:
		case 202014:
		case 202011:
		case 202012:
		case 14012:
		case 14061:
		case 14066:
		case 14086:
		case 14134:		
		case 2202013:
		case 2202014:
		case 2202011:
		case 2202012:
	
			return true;
		}

		return false;
	}
	public void 잠재력(ItemPresentOutStream 템) {
		
		if (Config.잠재력활성화) {
			switch (get_item_level()) {
			case 1:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH근거리 치명타: \\aA+1%");break;
			case 2:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH원거리 치명타: \\aA+1%");break;
			case 3:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH마법 치명타: \\aA+1%");break;
			case 4:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH근거리 대미지: \\aA+1");break;
			case 5:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH원거리 대미지: \\aA+1");break;
			case 6:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH근거리 명중: \\aA+1");break;
			case 7:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH원거리 명중: \\aA+1");break;
			case 8:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH대미지 감소: \\aA+1");break;
			case 9:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH추가 방어력: \\aA+1");break;
			case 10:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fHMR: \\aA+3");break;
			case 11:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH최대 HP: \\aA+30");break;
			case 12:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH최대 MP: \\aA+20");break;
			case 13:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fHPVP 추가 대미지: \\aA+1");break;
			case 14:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fHPVP 대미지 감소: \\aA+1");break;
			case 15:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA불 +10");break;
			case 16:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA땅 +10");break;
			case 17:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA물 +10");break;
			case 18:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH속성 저항: \\aA바람 +10");break;
			case 19:
				템.writeC(39);템.writeS("\\fH[잠재력 강화: 일반]");
				템.writeC(39);템.writeS("\\fH소지 무게 증가: \\aA+100");break;
			case 20:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH근거리 치명타: \\aA+3%");break;
			case 21:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH원거리 치명타: \\aA+3%");break;
			case 22:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH마법 치명타: \\aA+3%");break;
			case 23:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH근거리 대미지: \\aA+2");break;
			case 24:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH원거리 대미지: \\aA+2");break;
			case 25:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH근거리 명중: \\aA+2");break;
			case 26:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH원거리 명중: \\aA+2");break;
			case 27:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH대미지 감소: \\aA+2");break;
			case 28:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH추가 방어력: \\aA+2");break;
			case 29:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHMR: \\aA+6");break;
			case 30:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH최대 HP: \\aA+60");break;
			case 31:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH최대 MP: \\aA+40");break;
			case 32:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHDG: \\aA+3");break;
			case 33:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHHP 절대 회복: \\aA+30(32초)");break;
			case 34:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHMP 절대 회복: \\aA+10(64초)");break;
			case 35:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHER: \\aA+3");break;
			case 36:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHPVP 추가 대미지: \\aA+2");break;
			case 37:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHPVP 대미지 감소: \\aA+2");break;
			case 38:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aHPVP마법대미지 감소: \\aA+2%");break;
			case 39:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA기술 +3");break;
			case 40:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA정령 +3");break;
			case 41:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA용언 +3");break;
			case 42:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH내성 :\\aA공포 +3");break;
				
			case 43:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA불+20");break;
			case 44:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA땅+20");break;
			case 45:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA물+20");break;
			case 46:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH속성 저항: \\aA바람+20");break;
			case 47:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aASTR +1");break;
			case 48:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aADEX +1");break;
			case 49:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aACON +1");break;
			case 50:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aAWIS +1");break;
			case 51:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH스탯: \\aAINT +1");break;
			case 52:
				템.writeC(39);템.writeS("\\aH[잠재력 강화: 고급]");
				템.writeC(39);템.writeS("\\aH소지 무게 증가: \\aA+200");break;
			case 53:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL근거리 치명타: \\aA+5%");break;
			case 54:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL원거리 치명타: \\aA+5%");break;
			case 55:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL마법 치명타: \\aA+5%");break;
			case 56:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL근거리 대미지: \\aA+3");break;
			case 57:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL원거리 대미지: \\aA+3");break;
			case 58:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL근거리 명중: \\aA+3");break;
			case 59:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL원거리 명중: \\aA+3");break;
			case 60:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aLSP: \\aA+3");break;
			case 61:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);템.writeS("\\aL마법적중: \\aA+3");break;
			case 62:
				템.writeC(39);템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL대미지감소: \\aA+3");
				break;
			case 63:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL추가 방어력: \\aA+3");
				break;
			case 64:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLMR: \\aA+10");
				break;
			case 65:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL최대 HP: \\aA+150");
				break;
			case 66:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL최대 MP: \\aA+100");
				break;
			case 67:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLDG: \\aA+5");
				break;
			case 68:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLHP절대 회복: \\aA+50(32초)");
				break;
			case 69:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLMP절대 회복: \\aA+12(64초)");
				break;
			case 70:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLER: \\aA+5");
				break;
			case 71:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLPVP 추가 대미지: \\aA+3");
				break;
			case 72:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLPVP 대미지 감소: \\aA+3");
				break;
			case 73:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aLPVP마법대미지 감소 :\\aA+5%");
				break;
			case 74:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA기술 +5");
				break;
			case 75:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA정령 +5");
				break;
			case 76:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA용언 +5");
				break;
			case 77:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL내성 :\\aA공포 +5");
				break;
			case 78:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA기술 +3");
				break;
			case 79:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA정령 +3");
				break;
			case 80:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA용언 +3");
				break;
			case 81:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL적중 :\\aA공포 +3");
				break;
			case 82:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA불+30");
				break;
			case 83:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA땅+30");
				break;
			case 84:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA물+30");
				break;
			case 85:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL속성 저항: \\aA바람+30");
				break;
			case 86:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL모든 속성 저항: \\aA+10");
				break;
			case 87:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aASTR+2");
				break;
			case 88:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aADEX+2");
				break;
			case 89:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aACON+2");
				break;
			case 90:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aAWIS+2");
				break;
			case 91:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL스탯: \\aAINT+2");
				break;
			case 92:
				템.writeC(39);
				템.writeS("\\aL[잠재력 강화: 희귀]");
				템.writeC(39);
				템.writeS("\\aL소지 무게 증가: \\aA+200");
				break;
			case 93:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3근거리 대미지: \\aA+5");
				break;
			case 94:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3원거리 대미지: \\aA+5");
				break;
			case 95:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3근거리 명중: \\aA+5");
				break;
			case 96:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3원거리 명중: \\aA+5");
				break;
			case 97:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3SP: \\aA+5");
				break;
			case 98:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3마법적중: \\aA+5");
				break;
			case 99:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3대미지감소: \\aA+5");
				break;
			case 100:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3추가 방어력: \\aA+5");
				break;
			case 101:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3MR: \\aA+15");
				break;
			case 102:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3최대 HP: \\aA+300");
				break;
			case 103:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3최대 MP: \\aA+200");
				break;
			case 104:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3DG: \\aA+10");
				break;
			case 105:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3HP절대 회복: \\aA+150(32초)");
				break;
			case 106:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3MP절대 회복: \\aA+30(64초)");
				break;
			case 107:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3ER: \\aA+12");
				break;
			case 108:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP 추가 대미지: \\aA+7");
				break;
			case 109:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP 대미지 감소: \\aA+7");
				break;
			case 110:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP마법대미지 감소 :\\aA+10%");
				break;
			case 111:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3PVP대미지감소무시 :\\aA+10");
				break;
			case 112:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA기술 +8");
				break;
			case 113:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA정령 +8");
				break;
			case 114:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA용언 +8");
				break;
			case 115:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3내성 :\\aA공포 +8");
				break;
			case 116:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 내성 :\\aA+3");
				break;
			case 117:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA기술 +5");
				break;
			case 118:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA정령 +5");
				break;
			case 119:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA용언 +5");
				break;
			case 120:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3적중 :\\aA공포 +5");
				break;
			case 121:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 적중 :\\aA+3");
				break;
		case 122:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 속성 저항: \\aA+30");;
				break;
			case 123:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aASTR +3");
				break;
			case 124:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aADEX +3");
				break;
			case 125:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aACON +3");
				break;
			case 126:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aAWIS +3");
				break;
			case 127:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3스탯: \\aAINT +3");
				break;
			case 128:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3모든 스탯: \\aA+1");
				break;
			case 129:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3MP 흡수");
				break;
			case 130:
				템.writeC(39);
				템.writeS("\\f3[잠재력 강화: 영웅]");
				템.writeC(39);
				템.writeS("\\f3소지 무게 증가: \\aA+500");
				break;
			case 131:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4디케이 포션 발동");
				break;
			case 132:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4PVP대미지감소무시 :\\aA+"+Config.인형PVP감소수치);
				break;
			case 133:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4모든 스탯 :\\aA+3");
				break;
			case 134:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4HP 흡수");
				break;
			case 135:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4MP 흡수");
				break;
			case 136:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4소울오브프레임 발동");
				break;
			case 137:
				int aa = (int) ((double) Config.잠재력이뮨 * 100);
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4이뮨투함 무시 \\aA+"+aa+"%");
				break;
			case 138:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f4저지먼트 발동");
				break;
			case 139:
				템.writeC(39);
				템.writeS("\\f4[잠재력 강화: 전설]");
				템.writeC(39);
				템.writeS("\\f43단가속 상시 효과");
				break;
			}
			}
		
		
	}
	
	private L1EquipmentTimer _equipmentTimer;

	public void startEquipmentTimer(L1PcInstance pc) {
		if (getRemainingTime() > 0) {
			_equipmentTimer = new L1EquipmentTimer(pc, this, 1000);
			ThreadPool.getInstance().scheduleRequired(_equipmentTimer, 1000);
		}
	}

	public void stopEquipmentTimer(L1PcInstance pc) {
		if (getRemainingTime() > 0) {
			_equipmentTimer.cancel();
			_equipmentTimer = null;
		}
	}

	private boolean _isNowLighting = false;

	public boolean isNowLighting() {
		return _isNowLighting;
	}

	public void setNowLighting(boolean flag) {
		_isNowLighting = flag;
	}

	private int _DropMobId = 0;

	public int isDropMobId() {
		return _DropMobId;
	}

	public void setDropMobId(int i) {
		_DropMobId = i;
	}

	private int _keyId = 0;

	public int getKeyId() {
		return _keyId;
	}

	public void setKeyId(int i) {
		_keyId = i;
	}

	private boolean armor_set;

	public boolean get_armor_set() {
		return armor_set;
	}

	public void set_armor_set(boolean b) {
		this.armor_set = b;
	}

	private boolean main_set_armor;

	public boolean get_main_set_armor() {
		return main_set_armor;
	}

	public void set_main_set_armor(boolean b) {
		this.main_set_armor = b;
	}

	private int bless_level;

	public int get_bless_level() {
		return bless_level;
	}

	public void set_bless_level(int i) {
		this.bless_level = i;
	}
	
	private int bless_level2;

	public int get_bless_level2() {
		return bless_level2;
	}

	public void set_bless_level2(int i) {
		this.bless_level2 = i;
	}
	
	/** 특수 인챈트 시스템 **/
	private int item_level;

	public int get_item_level() {
		return item_level;
	}

	public void set_item_level(int i) {
		this.item_level = i;
	}

	/** 특수 인챈트 시스템 **/

	private String _Hotel_Town;

	public String getHotel_Town() {
		return _Hotel_Town;
	}

	public void setHotel_Town(String name) {
		_Hotel_Town = name;
	}

	public ArrayList<L1ItemBookMark> _bookmarks;

	public ArrayList<L1ItemBookMark> getBookMark() {
		return _bookmarks;
	}

	public void addBookMark(L1ItemBookMark list) {
		_bookmarks.add(list);
	}

	public byte[] getStatusBytes(L1PcInstance pc) {
		int itemType2 = getItem().getType2();
		int itemId = getItemId();
		ItemPresentOutStream os = new ItemPresentOutStream();

		if (itemType2 == 0) { // etcitem
			switch (getItem().getType()) {
			case 2: // light
				os.writeC(22);
				os.writeH(getItem().getLightRange());
				break;
			case 7: // food
				os.writeC(21);
				os.writeH(getItem().getFoodVolume());
				break;
			case 0: // arrow
			case 15: // sting
				os.writeC(1);
				os.writeC(getItem().getDmgSmall());
				os.writeC(getItem().getDmgLarge());
				break;
			default:
				os.writeC(23);
				break;
			}
			os.writeC(getItem().getMaterial());
			os.writeD(getWeight());

		} else if (itemType2 == 1 || itemType2 == 2) { // weapon | armor
			if (itemType2 == 1) { // weapon 무기 타격치
				os.writeC(1);
				os.writeC(getItem().getDmgSmall());
				os.writeC(getItem().getDmgLarge());
				os.writeC(getItem().getMaterial());
				os.writeD(getWeight());
			} else if (itemType2 == 2) { // armor
				os.writeC(19);
				int ac = ((L1Armor) getItem()).get_ac();
				int Grade = ((L1Armor) getItem()).getGrade();
				if (ac < 0) {
					ac = ac - ac - ac;
				}
				os.writeC(ac - get_durability());
				os.writeC(getItem().getMaterial());
				os.writeH(-1);
				os.writeD(getWeight());
			}

			if (getEnchantLevel() != 0 && !(itemType2 == 2 && getItem().getGrade() >= 0)) {
				os.writeC(2);
				os.writeC(getEnchantLevel());
			}
			if (getItem().isTwohandedWeapon()) { // 양손무기
				os.writeC(4);
			}
			if (get_durability() != 0) { // 손상도
				os.writeC(3);
				os.writeC(get_durability());
			}

			/** 클래스 착용 부분 **/
			int bit = 0;
			bit |= getItem().isUseRoyal() ? 1 : 0;
			bit |= getItem().isUseKnight() ? 2 : 0;
			bit |= getItem().isUseElf() ? 4 : 0;
			bit |= getItem().isUseMage() ? 8 : 0;
			bit |= getItem().isUseDarkelf() ? 16 : 0;
			bit |= getItem().isUseDragonKnight() ? 32 : 0;
			bit |= getItem().isUseBlackwizard() ? 64 : 0;
			bit |= getItem().isUse전사() ? 128 : 0;
			os.writeC(7);
			os.writeC(bit);

			os.writeC(130);
			if (!getItem().isTradable()) {
				os.writeD(6);
			} else {
				os.writeD(7);
			}

			if (isCanbeDmg()) {
				os.writeC(131);
				os.writeD(1); // 비손상
			}

			os.writeC(132);
			os.writeD(3); // 성별 구분 1.남자 2.여자 3.전체

			if (itemType2 == 1 && isUndeadDmg()) {
				os.writeC(114);
				os.writeD(1); // 언데드
			}


			/** 70레벨 엘릭서 룬 옵션 표시 **/
			if (itemId == 222312) { // 민첩의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(5);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(32);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222313) { // 체력의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222314) { // 지식의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222315) { // 지혜의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			if (itemId == 222316) { // 힘의 엘릭서
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					break;
				case 4:
					os.writeAddAc(3);
					os.writeC(32);
					os.writeH(30);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					break;
				}
			}
			// TODO 80레벨 엘릭서 룬 옵션 표시
			if (itemId == 900135) {
				// System.out.println(pc.getType());
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:// 기사
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:// 다엘
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(5);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(32);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900136) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900137) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900138) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
			if (itemId == 900139) {
				switch (pc.getType()) {
				case 0:
					os.writeC(63);
					os.writeC(3);
					os.writeC(48);
					os.writeC(2);
					os.writeaBlesssomo(5);
					break;
				case 1:
					os.writeC(31);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 2:
					os.writeC(32);
					os.writeH(50);
					os.writeC(47);
					os.writeC(1);
					os.writeC(35);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 3:
					os.writeC(38);
					os.writeC(3);
					os.writeC(17);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 4:
					os.writeC(19);
					os.writeC(3);
					os.writeC(32);
					os.writeH(30);
					os.writeaBlesssomo(5);
					break;
				case 5:
					os.writeC(48);
					os.writeC(3);
					os.writeC(63);
					os.writeC(1);
					os.writeaBlesssomo(5);
					break;
				case 6:
					os.writeC(68);
					os.writeC(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				case 7:
					os.writeC(15);
					os.writeH(5);
					os.writeC(31);
					os.writeH(50);
					os.writeaBlesssomo(5);
					break;
				}
			}
		}

		addMythicWeaponStatus(os);
		return os.getBytes();
	}

	private void addMythicWeaponStatus(ItemPresentOutStream os) {
		int enc = Math.min(getEnchantLevel(), 15);
		int ignoreReduction = 20 + (enc * 2);
		switch (getItemId()) {
		case 123555: { // 파아그리오의 공포 (근거리)
			os.writeShortDMG(44 + enc * 5);
			os.writeShortHIT(20 + enc + (enc / 2));
			os.writeShortCritical(7 + enc * 2);
			os.writeafear_pierce(18 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123777: { // 아인하사드의 섬광 (근거리)
			os.writeShortDMG(40 + enc * 5);
			os.writeShortHIT(22 + enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeability_pierce(16 + enc + (enc / 2));
			os.writeaspirit_pierce(16 + enc + (enc / 2));
			os.writeafear_pierce(16 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123888: { // 그랑카인의 심판 (근거리)
			os.writeShortDMG(44 + enc * 5);
			os.writeShortHIT(enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeability_pierce(20 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123900: { // 실렴의 결의 (근거리)
			os.writeShortDMG(36 + enc * 5);
			os.writeShortHIT(20 + enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeaAll_pierce(16 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123800: { // 사이하의 집념 (원거리/활)
			os.writeLongDMG(39 + enc * 5);
			os.writeLongHIT(18 + enc + (enc / 2));
			os.writeLongCritical(10 + enc * 2);
			os.writeaspirit_pierce(10 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123444: { // 단테스의 시련 (근거리)
			os.writeShortDMG(36 + enc * 5);
			os.writeShortHIT(21 + enc + (enc / 2));
			os.writeShortCritical(7 + enc * 2);
			os.writeaspirit_pierce(17 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 123333: { // 테이아의 혼돈 (마법/지팡이)
			os.writeShortHIT(enc / 2);
			os.writeAddDMG(enc * 2);
			os.writeMagicCritical(7 + enc * 2);
			os.writeAddSP(enc * 2 + (enc / 2));
			os.writeadragonS_pierce(18 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		case 124111: { // 아우라키아의 초월 (근거리)
			os.writeShortDMG(40 + enc * 5);
			os.writeShortHIT(20 + enc + (enc / 2));
			os.writeShortCritical(enc * 2);
			os.writeadragonS_pierce(18 + enc + (enc / 2));
			os.writeReductiondown(ignoreReduction);
			break;
		}
		default:
			break;
		}
	}

	public int getAttrEnchantBit(int attr) {
		int attr_bit = 0;
		int result_bit = 0;
		if (attr >= 1 && attr <= 5) {
			attr_bit = 1;
		}
		if (attr >= 6 && attr <= 10) {
			attr_bit = 2;
			attr = attr - 5;
		}
		if (attr >= 11 && attr <= 15) {
			attr_bit = 3;
			attr = attr - 10;
		}
		if (attr >= 16 && attr <= 20) {
			attr_bit = 4;
			attr = attr - 15;
		}

		if (attr > 0) {
			result_bit = attr_bit + (16 * attr);
		}

		return result_bit;
	}

	private int _openEffect = 0;

	public void setOpenEffect(int i) {
		_openEffect = i;
	}

	public int getOpenEffect() {
		return _openEffect;
	}

	public byte[] serialize() {
		byte[] data = null;
		MJBytesOutputStream os = null;
		try {
			os = new MJBytesOutputStream(128);
			os.write(0x08); // object_id
			os.writeBit(getId());
			os.write(0x10); // name_id
			os.writeBit(_item.getItemDescId());
			os.write(0x18); // db_id
			os.writeBit(getId());
			os.write(0x20); // count
			os.writeBit(_count);
			/*
			 * os.write(0x28); // interact_type os.writeBit(_item.getUseType());
			 * os.write(0x30); // number_of_use os.writeBit(0);
			 */
			os.write(0x28);
			os.writeBit(_item.getUseType());

			os.write(0x38); // icon_id
			os.writeBit(_item.getGfxId());
			os.write(0x40); // bless_code_for_display
			os.writeBit(bless);

			/**
			 * 2 : 교환 불가 4 : 삭제 불가 8 : 인챈 불가 16 : 창고 보관 가능 32 : 봉인 64 : 특수 봉인
			 **/
			os.write(0x48); // attribute_bit_set
			int bit = (!_item.isTradable() ? 2 : 16) | (_item.isCantDelete() ? 4 : 0)
					| (_item.get_safeenchant() < 0 ? 8 : 0) | (isIdentified() ? 1 : 0);
			os.writeBit(bit);

			os.write(0x50); // attribute_bit_set_ex(usetype)
			if (_item.isEndedTimeMessage())
				os.writeBit(0x01); // 각인
			else
				os.writeBit(0x08); // 낚시
			os.write(0x58); // is_timeout
			os.writeB(false);
			/*
			 * os.write(0x60); // category os.writeBit(0x1); os.write(0x68); // enchant
			 * os.writeBit(1);
			 */

			/**
			 * 0:창고불가 2:특수가능 3:개인/특수가능 7:개인/혈/특수가능
			 **/
			os.write(0x70); // deposit
			os.write(0x03);
			/*
			 * os.write(0x78); // overlay_surf_id os.writeBit(0); os.writeBit(0x80); //
			 * elemental_enchant_type os.writeBit(0x01); os.writeBit(0x88); //
			 * elemental_enchant_value os.writeBit(0x05);
			 */
			/*
			 * os.writeBit(0x8A); // description byte[] tmp = getStatusBytes();
			 * os.writeBit(tmp.length); os.write(tmp); tmp = null;
			 */
			os.writeBit(0x92); // extra_description
			os.writeS2(getViewName());
			/*
			 * os.writeBit(0x92); // description os.writeS2(getViewName());
			 * if(isIdentified()){ os.writeBit(0x9A); // extra_description byte[] tmp =
			 * getStatusBytes(); os.writeBit(tmp.length); os.write(tmp); tmp = null; }
			 * os.writeBit(0xA0); // left_time_for_pre_notify os.writeBit(100);
			 */
			data = os.toArray();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			os.close();
			os.dispose();
		}
		return data;
	}
	public int getStatusBit()
	  {
	    int b = 0;
	    if (!getItem().isTradable()) {
	      b += 2;
	    }
	    if (getItem().isCantDelete()) {
	      b += 4;
	    }
	    if (getItem().get_safeenchant() < 0) {
	      b += 8;
	    }
	    int bless = getBless();
	    if (bless >= 128) {
	      b = 46;
	    }
	    if (isIdentified()) {
	      b++;
	    }
	    return b;
	  }
	  
	  public int getTradeBit()
	  {
	    int b = 2;
	    if (getBless() >= 128) {
	      b = 3;
	    } else if (!getItem().isTradable()) {
	      b = 3;
	    } else if (getItem().isTradable()) {
	      b = 7;
	    }
	    return b;
	  }
	
	private int getRuneAc() {
		int ac = getItem().get_ac();

		if ((getItemId() >= 900135 && getItemId() <= 900149) || (getItemId() >= 222295 && getItemId() <= 222316)) {
			if (_cha != null && _cha.getType() == 4) {
				ac += 3;
			}
		}

		return ac;
	}

	private static int _instanceType = -1;

	@Override
	public int getL1Type() {
		return _instanceType == -1 ? _instanceType = super.getL1Type() | MJL1Type.L1TYPE_ITEMINSTANCE : _instanceType;
	}

	private boolean m_is_give = false;

	public boolean isGiveItem() {
		return m_is_give;
	}

	public void setGiveItem(boolean is_give) {
		m_is_give = is_give;
	}

	public boolean isCanbeDmg() {
		boolean result = getItem().get_canbedmg() == 0 ? true : false;

		return result;
	}

	private boolean not_pick_up_item = false;
	private L1PcInstance _bonusitemOwner;

	public boolean is_Not_PickUp_Item() {
		return this.not_pick_up_item;
	}

	public void set_Not_PickUp_Item(boolean is_not) {
		this.not_pick_up_item = is_not;
	}

	public L1PcInstance getBonusItemOwner() {
		return this._bonusitemOwner;
	}

	public void setBonusItemOwner(L1PcInstance pc) {
		this._bonusitemOwner = pc;
	}

	public boolean isUndeadDmg() {
		boolean result = false;

		if (getItem().getMaterial() == 14 || getItem().getMaterial() == 17 || getItem().getMaterial() == 22)
			result = true;

		return result;
	}
	
	/**
	 * 인형 옵션 처리
	 * @param os
	 * @param itemId
	 */
	private void setMagicDollStatus(ItemPresentOutStream os, int itemId) {
	    if (os == null) return;

	    final DollRegistry.Def def = DollRegistry.byItemId(itemId);
	    if (def == null) return;

	    final DollRegistry.Profile p = def.getProfile();

	    for (int i = 0; i < p.displayCount(); i++) {
	        final DollRegistry.DisplayEntry e = p.displayAt(i);
	        final int value = e.getValue();
	        if (value != 0) {
	            writeDollOption(os, e.getOption(), value);
	        }
	    }

	    final String magic = p.getMagicText();
	    if (magic != null && !magic.isEmpty()) {
	        switch (p.getMagicMode()) {
	        case MAGIC:
	            os.writeMagic(magic);
	            break;
	        case MAGIC2:
	            os.writeMagic2(magic);
	            break;
	        default:
	            break;
	        }
	    }
	}
	// 인형별 숫자는 절대 여기 넣지 않는다.
	// 옵션 종류 -> 패킷 표기 방식만 한 번 정의한다.
	private void writeDollOption(ItemPresentOutStream os, DollOption option, int value) {
	    switch (option.getTooltipKind()) {
	    case AC:              os.writeAddAc(value); break;
	    case MAX_HP:          os.writeAddMaxHP(value); break;
	    case MAX_MP:          os.writeMaxMP(value); break;
	    case DMG_DOWN:        os.writeDMGdown(value); break;
	    case MELEE_DMG:       os.writeShortDMG(value); break;
	    case MELEE_HIT:       os.writeShortHIT(value); break;
	    case BOW_DMG:         os.writeLongDMG(value); break;
	    case BOW_HIT:         os.writeLongHIT(value); break;
	    case SP:              os.writeAddSP(value); break;
	    case MAGIC_HIT:       os.writeMagicHIT(value); break;
	    case MELEE_CRIT:      os.writeShortCritical(value); break;
	    case ABILITY_RESIS:   os.writeability_resis(value); break;
	    case FEAR_RESIS:      os.writeafear_resis(value); break;
	    case ALL_RESIS:       os.writeaAll_resis(value); break;
	    case ABILITY_PIERCE:  os.writeability_pierce(value); break;
	    case SPIRIT_PIERCE:   os.writeaspirit_pierce(value); break;
	    case DRAGON_PIERCE:   os.writeadragonS_pierce(value); break;
	    case FEAR_PIERCE:     os.writeafear_pierce(value); break;
	    case ALL_PIERCE:      os.writeaAll_pierce(value); break;
	    case PVP_DMG:         os.writePVPAddDMG(value); break;
	    case PVP_DMG_RED:     os.writePVPAddDMGdown(value); break;
	    case BLESS_RATE:      os.writeaBlesssomo(value); break;
	    case WEIGHT_X50:      os.writeAddWeight(value * 50); break;
	    case EXP:             os.writeAddEXP(value); break;
	    case MP_UP:           os.writeaMPUP(value); break;
	    case HP_UP:           os.writeaHPUP(value); break;
	    case MPR:             os.writeAddMPPrecovery(value); break;
	    case PERCENT_DMG:     os.writeaPercentDmg(value); break;
	    case FOUSLAYER:       os.writeaFouslayer(value); break;
	    case NONE:
	    default:
	        break;
	    }
	}
	// [클라이언트가 보낸 조작값이 들어와도 1~20 밖은 절대 반환 안 함
	// 네이밍 테이블 기준: type 1~4(화/수/풍/지), 단계 1~5 → 레벨 1~20
	public static int calculateElementalEnchant(int elementType, int elementalValue) {
	    if (elementType < 1 || elementType > 4)   return 0;
	    if (elementalValue < 1 || elementalValue > 5) return 0;
	    final int level = (elementType - 1) * 5 + elementalValue; 
	    return (level >= 1 && level <= 20) ? level : 0;
	}
	
	//  방어 — 어디서 호출되든 1~20 밖 값은 저장 차단
	public void setAttrEnchantLevel(int level) {
	    if (level < 0 || level > 20) {
	        System.out.println("[속성인챈트] 비정상 레벨 저장 시도 차단: " + level);
	        return;
	    }
	    _attrenchantLevel = level; 
	}
	
	public transient double _expBonusDeltaExp = 0.0;
	
	public static final int TIMER_GROUND_DELETE = 1;

	/*
	 * ground timer generation.
	 * item을 줍거나 다시 드랍했을 때 예전 CIO timer를 O(1) stale 처리한다.
	 * 별도 Future/Handle/Map을 item마다 들고 있지 않는다.
	 */
	private volatile int _groundDeleteTimerGeneration;
	
	/**
	 * 지면에 실제 spawn된 아이템만 삭제 타이머를 건다.
	 */
	public void OnSpawned() {
		if (getId() <= 0 || getX() == 0) {
			return;
		}

		// 정령의 돌 제외.
		if (getItemId() == 40515) {
			return;
		}

		final String type = Config.ALT_ITEM_DELETION_TYPE;

		if (!"std".equalsIgnoreCase(type)
				&& !"auto".equalsIgnoreCase(type)) {
			return;
		}
		AddTimer(TIMER_GROUND_DELETE, groundDeleteDelayMs());
	}

	/**
	 * 실제 timing wheel 소유권은 CIOThreadCallback.
	 * ItemInstance는 primitive generation만 가진다.
	 */
	public void AddTimer(int timerId, long delayMs) {
		if (delayMs <= 0L || getId() <= 0) {
			return;
		}

		switch (timerId) {
		case TIMER_GROUND_DELETE: {
			final int generation = nextGroundDeleteTimerGeneration();
			CIOThreadCallback.getInstance().scheduleItemTimer(getId(), timerId, generation, delayMs);
			break;
		}
		default:
			break;
		}
	}

	/**
	 * 특정 Item timer 무효화.
	 * wheel에서 물리적으로 제거할 필요 없이 generation mismatch로 폐기한다.
	 */
	public void CancelTimer(int timerId) {
		switch (timerId) {
		case TIMER_GROUND_DELETE:
			nextGroundDeleteTimerGeneration();
			break;

		default:
			break;
		}
	}

	/**
	 * CIO ItemTimerTask가 world object를 다시 확인한 후 여기로 진입한다.
	 */
	public void TimerExpired(
			int timerId,
			int generation) {

		switch (timerId) {
		case TIMER_GROUND_DELETE:
			if (generation != _groundDeleteTimerGeneration) {
				return;
			}

			onGroundDeleteTimerExpired();
			break;

		default:
			break;
		}
	}

	/**
	 * 이 메서드는 지면에 존재하는 item 삭제용이다.
	 */
	public void Delete() {
		CancelTimer(TIMER_GROUND_DELETE);

		final int x = getX();

		if (x == 0) {
			return;
		}

		final int y = getY();
		final short mapId = getMapId();

		final L1GroundInventory ground = L1World.getInstance().getInventory(x, y, mapId);

		if (ground == null) {
			return;
		}
		synchronized (ground) {
			if (getX() == 0) {
				return;
			}

			ground.removeItem(this);
		}
	}

	private void onGroundDeleteTimerExpired() {
		final L1Object current = L1World.getInstance().findObject(getId());
		if (current != this) {
			return;
		}
		if (getX() == 0 || getItemId() == 40515) {
			return;
		}
		final String type = Config.ALT_ITEM_DELETION_TYPE;
		if ("auto".equalsIgnoreCase(type)) {
			if (L1HouseLocation.isInHouse(getX(), getY(), getMapId())) {
				return;
			}
			if (!L1World.getInstance().getVisiblePlayer(this, Config.ALT_ITEM_DELETION_RANGE).isEmpty()) {
				AddTimer(TIMER_GROUND_DELETE, groundDeleteDelayMs());
				return;
			}
		}
		Delete();
	}

	private int nextGroundDeleteTimerGeneration() {
		int next = _groundDeleteTimerGeneration + 1;

		if (next == 0) {
			next = 1;
		}
		_groundDeleteTimerGeneration = next;
		return next;
	}

	private static long groundDeleteDelayMs() {
		final long minutes = Config.ALT_ITEM_DELETION_TIME;

		final long delayMs = minutes * 60_000L;

		return Math.max(delayMs, 60_000L);
	}
	
	private volatile L1PcInstance _itemOwner;
	private volatile long _itemOwnerUntilNs;

	public L1PcInstance getItemOwner() {
	    final L1PcInstance owner = _itemOwner;
	    if (owner == null) {
	        return null;
	    }
	    final long until = _itemOwnerUntilNs;
	    if (until != 0L && System.nanoTime() - until >= 0L) {
	        return null;
	    }
	    return owner;
	}

	public void setItemOwner(L1PcInstance pc) {
	    _itemOwnerUntilNs = 0L;
	    _itemOwner = pc;
	}

	public void startItemOwnerTimer(L1PcInstance pc) {
	    _itemOwnerUntilNs = pc == null
	            ? 0L
	            : System.nanoTime() + 10_000_000_000L;

	    _itemOwner = pc;
	}
}