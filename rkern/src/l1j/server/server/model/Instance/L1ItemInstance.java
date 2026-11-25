package l1j.server.server.model.Instance;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionException;

import l1j.server.Config;
import l1j.server.server.model.item.pride.PrideInfo;
import l1j.server.server.model.item.pride.PrideLoadManager;
import l1j.server.server.ThreadPool; //CrockController
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1EquipmentTimer;
import l1j.server.server.model.L1GroundInventory;
import l1j.server.server.model.L1HouseLocation;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.CInventory;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.CUserInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.assembly.CItemAssembler;
import l1j.server.server.model.item.assembly.CItemEnchantOption;
import l1j.server.server.model.item.collection.CollectionObject;
import l1j.server.server.model.item.collection.loader.CollectionLoadManager;
import l1j.server.server.model.item.collection.loader.CollectionSystemLoader;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.world.instance.inn.InnHelper;
import l1j.server.server.shared.L1Type;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.CItemBookmark;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.BytesOutputStream;
import l1j.server.server.utils.ItemPresentOutStream;

public class L1ItemInstance extends L1Object {

	// Intrusive list membership; only CItemList may modify these links.
	transient volatile CItemList inventoryLinkOwner;
	transient L1ItemInstance inventoryPrev, inventoryNext;

    // Only a PC-origin ground transfer sets this source row. The existing
    // CacheD item lane remains the authority until its admitted write settles.
    private transient volatile long groundDropSourceStamp;
    private static final java.util.concurrent.atomic.AtomicLongFieldUpdater<L1ItemInstance>
            GROUND_DROP_SOURCE = java.util.concurrent.atomic.AtomicLongFieldUpdater.newUpdater(
                    L1ItemInstance.class, "groundDropSourceStamp");

    public void markGroundDropSource(int sourceId) {
        // Generation prevents a stale query from clearing a later drop of
        // the same source ID. Writers are serialized by the ground monitor.
        final long next = ((groundDropSourceStamp + (1L << 32)) & 0xffffffff00000000L)
                | (sourceId & 0xffffffffL);
        groundDropSourceStamp = next;
    }

    public boolean isGroundDropUnsettled() {
        final long stamp = groundDropSourceStamp;
        final int sourceId = (int) stamp;
        if (sourceId == 0) return false;
        if (l1j.server.server.storage.CacheD.getInstance().isItemPersistencePending(sourceId))
            return true;
        return !GROUND_DROP_SOURCE.compareAndSet(this, stamp, stamp & 0xffffffff00000000L);
    }

    private transient volatile Object groundPickupReservation;
    private static final Object FROZEN_GROUND_PICKUP = new Object();
    private static final java.util.concurrent.atomic.AtomicReferenceFieldUpdater<L1ItemInstance,Object>
            GROUND_PICKUP_RESERVATION = java.util.concurrent.atomic.AtomicReferenceFieldUpdater.newUpdater(
                    L1ItemInstance.class, Object.class, "groundPickupReservation");

    public boolean isGroundPickupPending() { return groundPickupReservation != null; }
    public boolean hasGroundPickupReservation(Object token) { return groundPickupReservation == token; }
    public boolean reserveGroundPickup(Object token) {
        return token != null && GROUND_PICKUP_RESERVATION.compareAndSet(this, null, token);
    }
    public void releaseGroundPickup(Object token) {
        if (token != null && token != FROZEN_GROUND_PICKUP)
            GROUND_PICKUP_RESERVATION.compareAndSet(this, token, null);
    }
    public void freezeGroundPickup(Object token) {
        if (token != null) GROUND_PICKUP_RESERVATION.compareAndSet(this, token, FROZEN_GROUND_PICKUP);
    }

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
		if (enchant > 0) return "+" + enchant + " ";
		if (enchant < 0) return enchant + " ";
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
		return attrEnchantLevel >= 0 && attrEnchantLevel < _attrMask.length
				? _attrMask[attrEnchantLevel] : 0;
	}

	public static int attrEnchantToElementalType(L1ItemInstance item) {
		return attrEnchantToElementalType(item.getAttrEnchantLevel());
	}

	public static int pureAttrEnchantLevel(int attrEnchantLevel) {
		if (attrEnchantLevel <= 0) return attrEnchantLevel;
		if (attrEnchantLevel >= _attrMask.length) return 0;
		return attrEnchantLevel - ((_attrMask[attrEnchantLevel] - 1) * 5);
	}

	public static int pureAttrEnchantLevel(L1ItemInstance item) {
		return pureAttrEnchantLevel(item.getAttrEnchantLevel());
	}

	public static boolean equalsElement(L1ItemInstance item, int elementalType, int elementalValue) {
		int attr = item.getAttrEnchantLevel();
		if (attr <= 0 || attr >= _attrMask.length) return false;
		int type = _attrMask[attr];
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

	private volatile LastStatus _lastStatus; // Only persistent/observed items need a snapshot

	public L1PcInstance _cha;

	public L1ItemInstance() {
		_count = 1;
		_enchantLevel = 0;
		_specialEnchant = 0;
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
		_specialEnchant = (_specialEnchant & ~0xFF) | 1;
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
		if (index < 0 || index > 3) return;
		final int shift = index << 3;
		final int mask = 0xFF << shift;
		_specialEnchant = (_specialEnchant & ~mask) | ((enchant & 0xFF) << shift);
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
        for (;;) {
            final CItemList list = inventoryLinkOwner;
            if (list == null) {
                _count = count;
                final CItemList linked = inventoryLinkOwner;
                if (linked != null) linked.invalidateCarryingWeight();
                return;
            }
            synchronized (list) {
                if (inventoryLinkOwner != list) continue;
                if (_count == count) return;
                final int before = list.weightBeforeChange(this);
                _count = count;
                list.weightChanged(this, before);
                return;
            }
        }
	}

	/** OFF-style quantity validation; callers retain the inventory/warehouse lock. */
    public static boolean CanMergeStack(L1ItemInstance a,L1ItemInstance b) {
        return a != null && b != null && !a.isEquipped() && !b.isEquipped() && a.isStackable() && b.isStackable() && a.getItemId()==b.getItemId()
                && a.isIdentified()==b.isIdentified() && a.get_durability()==b.get_durability()
                && a.getKeyId()==b.getKeyId() && a.getEnchantLevel()==b.getEnchantLevel() && a.getBless()==b.getBless()
                && a.getAttrEnchantLevel()==b.getAttrEnchantLevel() && a.getSpecialEnchant()==b.getSpecialEnchant()
                && a.getChargeCount()==b.getChargeCount() && a.getRemainingTime()==b.getRemainingTime()
                && a.get_bless_level()==b.get_bless_level() && a.get_bless_level2()==b.get_bless_level2()
                && a.get_item_level()==b.get_item_level() && a.isPackage()==b.isPackage()
                && java.util.Objects.equals(a.getEndTime(),b.getEndTime()) && java.util.Objects.equals(a.getLastUsed(),b.getLastUsed())
                && java.util.Objects.equals(a.getHotel_Town(),b.getHotel_Town());
    }

    /** Source-side quantity/equipment admission only. Caller owns the source lock.
     * Ownership, destination limits and durable transfer gates remain separate.
     */
    public boolean IsTransferableAmount(int amount) {
        final int available = getCount();
        return available > 0 && amount > 0 && amount <= available
                && !isEquipped() && (isStackable() || amount == 1);
    }

	public boolean IsAddableAmount(int amount) {
		final int before = _count;
		final long after = (long) before + amount;
		return before >= 0 && after >= 0 && after <= CInventory.MAX_AMOUNT;
	}

	public boolean AddAmount(int amount) {
		final int before = _count;
		final long after = (long) before + amount;
		if (before < 0 || after < 0 || after > CInventory.MAX_AMOUNT) return false;
		setCount((int) after);
		return true;
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
        final int id = item.getItemId();
        for (;;) {
            final CItemList list = inventoryLinkOwner;
            if (list == null) {
                _item = item;
                _itemId = id;
                final CItemList linked = inventoryLinkOwner;
                if (linked != null) linked.invalidateCarryingWeight();
                return;
            }
            synchronized (list) {
                if (inventoryLinkOwner != list) continue;
                final int before = list.weightBeforeChange(this);
                _item = item;
                _itemId = id;
                list.weightChanged(this, before);
                return;
            }
        }
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

    /** Initial charge for a newly created row, not an existing transfer. */
    public int GetInitialChargeCount(int classId) {
        return classId == 20383 ? 50 : getItem().getMaxChargeCount();
    }

    public static Timestamp CopyTimestamp(Timestamp time) {
        if (time == null) return null;
        Timestamp copy = new Timestamp(time.getTime());
        copy.setNanos(time.getNanos());
        return copy;
    }

    /**
     * Copy the shared persistent item attributes, not owner, identity, template,
     * equipment state, key ID or inventory membership.
     * A detached SQL image clones last-used time; live replay retains the
     * existing timestamp reference policy. Expiry is optional because PC
     * replay must cancel the old timer before changing it.
     */
    public final void CopyItemAttributesFrom(L1ItemInstance source, int count, int charge,
            boolean copyPackage, boolean copyExpiry, boolean snapshotLastUsed) {
        setCount(count);
        setEnchantLevel(source.getEnchantLevel());
        setIdentified(source.isIdentified());
        set_durability(source.get_durability());
        setChargeCount(charge);
        setRemainingTime(source.getRemainingTime());
        setLastUsed(snapshotLastUsed ? CopyTimestamp(source.getLastUsed()) : source.getLastUsed());
        setBless(source.getBless());
        setAttrEnchantLevel(source.getAttrEnchantLevel());
        setSpecialEnchant(source.getSpecialEnchant());
        if (copyPackage) setPackage(source.isPackage());
        set_bless_level(source.get_bless_level());
        set_bless_level2(source.get_bless_level2());
        if (copyExpiry) setEndTime(source.getEndTime());
        set_item_level(source.get_item_level());
        setHotel_Town(source.getHotel_Town());
    }

    /** Detached image only; a new reward may still have an unassigned ID.
     * Callers publishing or persisting a row must validate/reserve its ID.
     */
    public final L1ItemInstance CopyDetachedImage(int id, int count, int charge) {
        L1ItemInstance result = new L1ItemInstance();
        result.setId(id);
        result.setItem(getItem());
        result.CopyItemAttributesFrom(this, count, charge, true, false, true);
        result.setKeyId(getKeyId());
        result.setEndTime(CopyTimestamp(getEndTime()));
        return result;
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

	/** New-item initialization only: transfers must preserve existing lifetime and fuel. */
	public static void InitializeNewItemLifetimeAndFuel(L1ItemInstance item, boolean ordinary) {
		final int itemId = item.getItemId();
		switch (itemId) {
		// 호랑이/진돗개
		case 3000048: // 꼬마 요정의 마음
			SetDeleteTime(item, 1439); // 24시간 -1분
			break;
		case L1ItemId.KILLTON_CONTRACT:
		case L1ItemId.MERIN_CONTRACT:
			SetDeleteTime(item, 60);
			break;
		default:
			if (ordinary) SetOrdinaryItemDeleteTime(item, itemId);
			break;
		}

		if (item.getItem().getType2() == 0 && item.getItem().getType() == 2) { // light
			item.setRemainingTime(item.getItem().getLightFuel());
		} else {
			item.setRemainingTime(item.getItem().getMaxUseTime());
		}
	}

	private static void SetOrdinaryItemDeleteTime(L1ItemInstance item, int itemId) {
		switch (itemId) {
		case 80500:
			SetDeleteTime(item, 120);
			break;
		case 555000:
		case 555001: // 마법인형: 레데그
		case 555002: // 마법인형: 엘레그
		case 555003: // 마법인형: 그레그
			SetDeleteTime(item, Config.영웅유물); // 5시간
			break;
		// case L1ItemId.MERIN_CONTRACT:
		// SetDeleteTime(item, 60);
		// break;
		case 100036:
		case 500210: // 테베,쿠쿨 제단 열쇠
		case L1ItemId.DRAGON_KEY: // 드래곤키
		case 490012:
		case 490013:
		case 490014:
		case 40312:
			SetDeleteTime(item, 180); // 3시간
			break;
		case 30022: // 마법인형: 블레그
		case 30023: // 마법인형: 레데그
		case 30024: // 마법인형: 엘레그
		case 30025: // 마법인형: 그레그
			SetDeleteTime(item, 300); // 5시간
			break;
		case 22328: // 베테르랑 방어구
		case 22329:
		case 22330:
		case 22331:
		case 22332:
		case 22333:
		case 22334:
		case 22335:
		case 1126: // 베테르랑 무기
		case 1127:
		case 1128:
		case 1129:
		case 1130:
		case 1131:
		case 1132:
		case 1133:
			SetDeleteTime(item, 4320); // 3일
			break;
		case 20282: // 수련자의 방어구
		case 7: // 수련자의 무기
		case 105:
		case 120:
		case 156:
		case 174:
		case 224:
		case 203012:
			SetDeleteTime(item, 4320); // 3일
			break;
		case 3000213:// 자동사냥 3일
			SetDeleteTime(item, 4320);// 자동사냥 3일
			break;
		case 45453: // 마법인형: 그레그
			SetDeleteTime(item, 360); // 5시간
			break;
		case 45451: // 마법인형: 그레그
			SetDeleteTime(item, 1440); // 5시간
			break;
		case 45452: // 마법인형: 그레그
			SetDeleteTime(item, 4320); // 5시간
			break;
		case 41922:
		case 41923:
		case 41924:
		case 41925:
		case 3000209: // 자동사냥 1일
		case 775:
		case 774:
		case 773:
		case 772:
			SetDeleteTime(item, 1440); // 1일
			break;
		case 3000214:// 자동사냥 7일
			SetDeleteTime(item, 8640); // 7일
			break;
		case 210095:
		case 2100950:
		case 2100951:
		case 2100952:
		case 2100953:// 쫄사냥
			SetDeleteTime(item, 180); // 3일
			break;
		/** 신규 초보자 무기 **/
		case 7000137:
		case 7000138:
		case 7000139:
		case 7000140:
		case 7000141:
		case 7000142:
		case 7000143:
		case 7000144:
		case 505001:
		case 505002:
		case 505003:
		case 505004:
		case 505005:
		case 505006:
		case 505007:
		case 505008:
			SetDeleteTime1(item, 1440); // 3일
			break;
		default:
			break;
		}
	}

	private static void SetDeleteTime(L1ItemInstance item, int minute) {
		item.setEndTime(new Timestamp(Math.addExact(System.currentTimeMillis(), 60000L * minute)));
	}

	private static void SetDeleteTime1(L1ItemInstance item, int minute) {
		item.setEndTime(new Timestamp(Math.addExact(System.currentTimeMillis(), 20000L * minute)));
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
		return getMrAtEnchant(getEnchantLevel());
	}

	public int getMrAtEnchant(int enchant) {
		int mr = _item.get_mdef();
		int itemid = getItemId();
		if (itemid == 20011 || itemid == 20110 || itemid == 120011 || itemid == 22223 || itemid == 20117 // 바포갑빠
				//|| getItemId() == 22204 || itemid == 22205 || itemid == 22206 || itemid == 22207 // 린드비오르마갑주
				|| getItemId() == 22213 || itemid == 120110 || itemid == 93001 || itemid == 490008 || itemid == 22365
				|| itemid == 222328 || getItemId() >= 222300 && getItemId() <= 222303 || itemid == 222328) {
			mr += enchant;
		} else if (itemid == 20056 || itemid == 120056 || itemid == 220056 || itemid == 93002 || itemid == 20074
				|| itemid == 900090 || itemid == 120074 || itemid == 222324 || itemid == 222325 || itemid == 900043
				|| itemid == 900044) {
			mr += enchant * 2;
		} else if (itemid == 20079 || itemid == 20078 || itemid == 900047 || itemid == 900055 || itemid == 20049
				|| itemid == 20050 || itemid == 900057 || itemid == 900056) {
			mr += enchant * 3;
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
			return (int) Math.clamp((long) getCount() * getItem().getWeight() / 1000L, 1L, Integer.MAX_VALUE);
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
		LastStatus snapshot = _lastStatus;
		if (snapshot != null) return snapshot;
		synchronized (this) {
			snapshot = _lastStatus;
			if (snapshot == null) {
				snapshot = new LastStatus();
				_lastStatus = snapshot;
			}
		}
		return snapshot;
	}

	public int getRecordingColumns() {
		// LastStatus is the persisted item image, not the client display.
		// Every field compared by CTransaction.verifyItemPreimage must be
		// accounted for before a receipt-guarded item use.
		final LastStatus snapshot = getLastStatus();
		int column = 0;
		if (getCount() != snapshot.count) column |= CUserInventory.COL_COUNT;
		if (getItemId() != snapshot.itemId) column |= CUserInventory.COL_ITEMID;
		if (isEquipped() != snapshot.isEquipped) column |= CUserInventory.COL_EQUIPPED;
		if (getEnchantLevel() != snapshot.enchantLevel) column |= CUserInventory.COL_ENCHANTLVL;
		if (get_durability() != snapshot.durability) column |= CUserInventory.COL_DURABILITY;
		if (getChargeCount() != snapshot.chargeCount) column |= CUserInventory.COL_CHARGE_COUNT;
		if (!java.util.Objects.equals(getLastUsed(), snapshot.lastUsed))
			column |= CUserInventory.COL_DELAY_EFFECT;
		if (isIdentified() != snapshot.isIdentified) column |= CUserInventory.COL_IS_ID;
		if (getRemainingTime() != snapshot.remainingTime) column |= CUserInventory.COL_REMAINING_TIME;
		if (getBless() != snapshot.bless) column |= CUserInventory.COL_BLESS;
		if (getAttrEnchantLevel() != snapshot.attrenchantLevel) column |= CUserInventory.COL_ATTRENCHANTLVL;
		if (getSpecialEnchant() != snapshot.specialEnchant) column |= CUserInventory.COL_SPECIAL_ENCHANT;
		if (get_bless_level() != snapshot.bless_level) column |= CUserInventory.COL_BLESS_LEVEL;
		if (get_bless_level2() != snapshot.bless_level2) column |= CUserInventory.COL_BLESS_LEVEL2;
		if (!java.util.Objects.equals(getEndTime(), snapshot.endTime))
			column |= CUserInventory.COL_END_TIME;
		if (isPackage() != snapshot._isPackage) column |= CUserInventory.COL_PACKAGE;
		if (get_item_level() != snapshot.item_level) column |= CUserInventory.COL_ITEM_LEVEL;
		if (!java.util.Objects.equals(getHotel_Town(), snapshot.town_name))
			column |= CUserInventory.COL_HOTEL_TOWN;
		return column;
	}

	public String getNumberedViewName(int count) {
		if (PrideLoadManager.USE_PRIDE_SYSTEM && _itemId == PrideLoadManager.PRIDE_ITEM_ID) {
			PrideInfo pInfo = PrideLoadManager.getInstance().get_pride(getId());
			if (pInfo != null)
				return pInfo.get_target_name() + "의 자존심";
		}

		StringBuilder name = new StringBuilder(64);

		if (isSpecialEnchantable()) {
			name.append("\\f3");
		}
		appendNumberedName(name, count);
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

		if (itemId == InnHelper.INN_KEYID) {
			name.append(" (").append(getHotel_Town()).append(')');
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
		StringBuilder name = new StringBuilder(48);
		appendNumberedName(name, count);
		return name.toString();
	}

	private void appendNumberedName(StringBuilder name, int count) {

		if (isIdentified()) {
			if (getItem().getType2() == 1 || getItem().getType2() == 2) {
				
				name.append(get_attribute_enchant_description(getAttrEnchantLevel()));
				if (getEnchantLevel() >= 0) {
					name.append('+').append(getEnchantLevel()).append(' ');
				} else if (getEnchantLevel() < 0) {
					name.append(getEnchantLevel()).append(' ');
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
		} else if (getItem().getItemId() == CollectionLoadManager.CTSYSTEM_LOAD_ID) {
			CollectionObject obj = CollectionSystemLoader.getInstance().get(getId());
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
				name.append(" (").append(getChargeCount()).append(')');
			}
			if (getItem().getItemId() == 20383) {
				name.append(" (").append(getChargeCount()).append(')');
			}
			if (getItem().getMaxUseTime() > 0 && getItem().getType2() != 0) {
				name.append(" [").append(getRemainingTime()).append(']');
			}
		}
		if (count > 1) {
			name.append(" (").append(count).append(')');
		}
	}

	public static int presentationCode = 0;

	public byte[] getStatusBytes2() {
		return CItemAssembler.AssembleBriefImpl(this);
	}
	
	

	@SuppressWarnings("deprecation")
	public byte[] getStatusBytes() {
		return CItemAssembler.AssembleImpl(this);
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
		CItemEnchantOption.AssemblePotentialOption(this, 템);
	}
	
	private volatile L1EquipmentTimer _equipmentTimer;

	public boolean startEquipmentTimer(L1PcInstance pc) {
		if (getRemainingTime() <= 0) return getItem().getMaxUseTime() <= 0;
		if (pc.isLogoutStarted()) return false;
		final L1EquipmentTimer timer;
		synchronized (this) {
			if (_equipmentTimer != null && _equipmentTimer.isActive()) return true;
			timer = new L1EquipmentTimer(pc, this);
			_equipmentTimer = timer;
		}
		try {
			timer.scheduleNext();
			return timer.isActive();
		} catch (RejectedExecutionException unavailable) {
			return false;
		}
	}

	public void stopEquipmentTimer() {
		if (_equipmentTimer == null) return;
		final L1EquipmentTimer timer;
		synchronized (this) {
			timer = _equipmentTimer;
			_equipmentTimer = null;
		}
		if (timer != null) timer.cancel();
	}

	public boolean ownsEquipmentTimer(L1EquipmentTimer timer) {
		return _equipmentTimer == timer;
	}

	public synchronized void clearEquipmentTimer(L1EquipmentTimer timer) {
		if (_equipmentTimer == timer) _equipmentTimer = null;
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

	private ArrayList<CItemBookmark> _bookmarks;

	public synchronized ArrayList<CItemBookmark> getBookMark() {
        if (_bookmarks == null) _bookmarks = new ArrayList<CItemBookmark>();
		return _bookmarks;
	}

	public synchronized void addBookMark(CItemBookmark list) {
		getBookMark().add(list);
	}

	public byte[] getStatusBytes(L1PcInstance pc) {
		return CItemAssembler.AssembleItemForPc(this, pc);
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
		BytesOutputStream os = null;
		try {
			os = new BytesOutputStream(128);
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
	
	

	private static int _instanceType = -1;

	@Override
	public int getL1Type() {
		return _instanceType == -1 ? _instanceType = super.getL1Type() | L1Type.L1TYPE_ITEMINSTANCE : _instanceType;
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
	
	// 인형별 숫자는 절대 여기 넣지 않는다.
	// 옵션 종류 -> 패킷 표기 방식만 한 번 정의한다.
	
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
	
	@Override
	public void onPerceive(L1PcInstance viewer) {
		if (viewer != null) L1GroundInventory.reconcileItem(viewer, this, false);
	}

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

			deleteGroundItem(generation);
			break;

		default:
			break;
		}
	}

	/**
	 * 이 메서드는 지면에 존재하는 item 삭제용이다.
	 */
    public void Delete() {
        deleteGroundItem(0);
    }

    /** A timer must validate its generation again under the tile monitor.
     * Zero denotes explicit deletion, not a timing-wheel callback.
     */
    private void deleteGroundItem(int generation) {
        final int x = getX();
        if (x == 0) return;
        final int y = getY();
        final short mapId = getMapId();
        final L1World world = L1World.getInstance();
        final L1GroundInventory ground = world.findGroundInventory(x, y, mapId);
        if (ground == null) return;
        // Preserve the original visibility scan outside the tile monitor.
        // Its result can only affect this exact generation after revalidation.
        boolean deferForViewer = false;
        if (generation != 0) {
            if (world.findObject(getId()) != this || getItemId() == 40515) return;
            if ("auto".equalsIgnoreCase(Config.ALT_ITEM_DELETION_TYPE)) {
                if (L1HouseLocation.isInHouse(x, y, mapId)) return;
                deferForViewer = !world.getVisiblePlayer(this, Config.ALT_ITEM_DELETION_RANGE).isEmpty();
            }
        }
        synchronized (ground) {
            if ((generation != 0 && generation != _groundDeleteTimerGeneration)
                    || getX() != x || getY() != y || getMapId() != mapId
                    || world != L1World.getInstance()
                    || world.findGroundInventory(x, y, mapId) != ground
                    || ground.GetByServerID(getId()) != this) return;
            if (generation != 0 && (world.findObject(getId()) != this || getItemId() == 40515)) return;
            if (deferForViewer) {
                AddTimer(TIMER_GROUND_DELETE, groundDeleteDelayMs());
                return;
            }
            CancelTimer(TIMER_GROUND_DELETE);
            if (isGroundPickupPending() || isGroundDropUnsettled()) {
                AddTimer(TIMER_GROUND_DELETE, groundDeleteDelayMs());
                return;
            }
            ground.DeleteItemAmount(this);
        }
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
	
	private transient volatile l1j.server.server.model.character.Item.Drop.CContributeData _contributeData;

    public l1j.server.server.model.character.Item.Drop.CContributeData GetContributeData() { return _contributeData; }

    public void SetContributeData(l1j.server.server.model.character.Item.Drop.CContributeData data) {
        _itemOwner = null;
        _itemOwnerUntilNs = 0L;
        _contributeData = data;
    }

    public boolean TryPickContributedItem(l1j.server.server.model.L1Character actor) {
        final var data = _contributeData;
        return data == null || data.TryPickItem(actor, System.nanoTime());
    }

    public boolean SameGroundLootRights(L1ItemInstance other) {
        final long now = System.nanoTime();
        final var a = _contributeData;
        final var b = other._contributeData;
        final boolean activeA = a != null && !a.IsExpired(now);
        final boolean activeB = b != null && !b.IsExpired(now);
        if (activeA || activeB) return activeA && activeB && a == b;
        return getItemOwner() == null && other.getItemOwner() == null;
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
        _contributeData = null;
	    _itemOwnerUntilNs = 0L;
	    _itemOwner = pc;
	}

	public void startItemOwnerTimer(L1PcInstance pc) {
        _contributeData = null;
	    _itemOwnerUntilNs = pc == null
	            ? 0L
	            : System.nanoTime() + 10_000_000_000L;

	    _itemOwner = pc;
	}
}
