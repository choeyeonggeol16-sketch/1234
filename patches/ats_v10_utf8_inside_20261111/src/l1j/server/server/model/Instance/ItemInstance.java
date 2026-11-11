
package l1j.server.server.model.Instance;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import I1j.server.server.model.Thread.EquipmentTimer;
import I1j.server.server.model.Thread.ItemOwnerTimer;
import l1j.server.CTSystem.MJCTObject;
import l1j.server.CTSystem.Loader.CTLoadManager;
import l1j.server.CTSystem.Loader.MJCTSystemLoader;
import l1j.server.InvenBonusItem.InvenBonusItemInfo;
import l1j.server.InvenBonusItem.InvenBonusItemLoader;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Npc;
import l1j.server.Templates.L1Pet;
import l1j.server.server.GeneralThreadPool;
import l1j.server.server.datatables.L1EnchantBonus;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.datatables.NoTradable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.item.BlessTypeEnchant;
import l1j.server.server.model.item.Cooking;
import l1j.server.server.model.item.ItemId;
import l1j.server.server.model.item.MagicDoll;
import l1j.server.server.serverpackets.S_ServerMessage;
import l1j.server.server.utils.ItemPresentOutStream;


public class ItemInstance extends L1Object {

	private Map<Integer, EnchantTimer> _skillEffect = new HashMap<Integer, EnchantTimer>();

	private static final long serialVersionUID = 1L;
	private static final SimpleDateFormat sdf = new SimpleDateFormat("MM-dd HH:mm", Locale.KOREA);

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

	public static String get_blessed_description(int bless) {
		switch (bless) {
		case 0:
			return "축복받은 ";
		case 2:
			return "저주받은 ";
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

	private static final String[] elemental_descriptions = new String[] { "", // 무속성
		"$6115", // 화령1
		"$6116", // 화령2
		"$6117", // 화령3

		"$6118", // 수령1
		"$6119", // 수령2
		"$6120", // 수령3

		"$6121", // 풍령1
		"$6122", // 풍령2
		"$6123", // 풍령3

		"$6124", // 지령1
		"$6125", // 지령2
		"$6126", // 지령3
	};

	public static String get_attribute_enchant_description(int attrEnchantLevel) {
		return attrEnchantLevel >= elemental_descriptions.length ? ""
				: attrEnchantLevel < 0 ? "" : elemental_descriptions[attrEnchantLevel];
	}

	private int _count;
	private int _itemId;
	private L1Item _item;
	private boolean _isEquipped = false;
	private int _enchantLevel;
	private int _attrenchantLevel;
	private boolean _isIdentified = false;
	private int _durability;
	private int _chargeCount;
	private int _remainingTime;
	private Timestamp _lastUsed = null;
	private Timestamp _endTime = null;
	private int bless;
	private int _lastWeight;
	private final LastStatus _lastStatus = new LastStatus();
	public ItemInstance() {
		_count = 1;
		_enchantLevel = 0;
	}

	public ItemInstance(L1Item item, int count) {
		this();
		setItem(item);
		setCount(count);
	}

	/**
	 * 아이템이 확인(감정)이 끝난 상태일까를 돌려준다.
	 * 
	 * @return 확인이 끝난 상태라면 true, 미확인이라면 false.
	 */
	public boolean isIdentified() {
		return _isIdentified;
	}

	/**
	 * 아이템이 확인(감정)이 끝난 상태인지를 설정한다.
	 * 
	 * @param identified
	 *            확인이 끝난 상태라면 true, 미확인이라면 false.
	 */
	public void setIdentified(boolean identified) {
		_isIdentified = identified;
	}

	public String getName() {
		return _item.getName();
	}

	/**
	 * 아이템의 개수를 돌려준다.
	 * 
	 * @return 아이템의 개수
	 */
	public int getCount() {
		return _count;
	}

	/**
	 * 아이템의 개수를 설정한다.
	 * 
	 * @param count
	 *            아이템의 개수
	 */
	public void setCount(int count) {
		_count = count;
	}
	private int _range;
	public int getRange() {
		return _range;
	}
	public void setRange(int count) {
		_range = count;
	}

	/**
	 * 아이템이 장비 되고 있을까를 돌려준다.
	 * 
	 * @return 아이템이 장비 되고 있으면 true, 장비되어 있지 않으면 false.
	 */
	public boolean isEquipped() {
		return _isEquipped;
	}

	/**
	 * 아이템이 장비 되고 있는지를 설정한다.
	 * 
	 * @param equipped
	 *            아이템이 장비 되고 있으면 true, 장비되어 있지 않으면 false.
	 */
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
	public void onAction(L1PcInstance player) {}

	public int getEnchantLevel() {
		return _enchantLevel;
	}
	public void setEnchantLevel(int enchantLevel) {
		_enchantLevel = enchantLevel;
	}
	public int getAttrEnchantLevel() {
		return _attrenchantLevel;
	}
	public void setAttrEnchantLevel(int attrenchantLevel) {
		_attrenchantLevel = attrenchantLevel;
	}
	public int get_gfxid() {
		return _item.getGfxId();
	}
	public int getDurability() {
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
	/**
	 * 블레스 타입
	 */
	private int _bless_type;
	public int getBlessType() {
		return _bless_type;
	}
	public void setBlessType(int i) {
		_bless_type = i;
	}
	private int _bless_type_value;
	public int getBlessTypeValue() {
		return _bless_type_value;
	}
	public void setBlessTypeValue(int i) {
		_bless_type_value = i;
	}

	/**
	 * 전회 DB에 보존했을 때의 아이템의 스테이터스를 격납하는 클래스
	 */
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
		public Timestamp endTime = null;
		public int _blessType;
		public int _blessTypeValue;
		public void updateAll() {
			count = getCount();
			itemId = getItemId();
			isEquipped = isEquipped();
			isIdentified = isIdentified();
			enchantLevel = getEnchantLevel();
			durability = getDurability();
			chargeCount = getChargeCount();
			remainingTime = getRemainingTime();
			lastUsed = getLastUsed();
			bless = getBless();
			attrenchantLevel = getAttrEnchantLevel();
			endTime = getEndTime();  
			_blessType = getBlessType();
			_blessTypeValue = getBlessTypeValue();
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
			durability = getDurability();
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
		public void updateBlessType() {
			_blessType = getBlessType();
		}
		public void updateBlessTypeValue() {
			_blessTypeValue = getBlessTypeValue();
		}
	}

	public LastStatus getLastStatus() {
		return _lastStatus;
	}

	/**
	 * 전회 DB에 보존했을 때로부터 변화하고 있는 컬럼을 비트 집합으로서 돌려준다.
	 */
	public int getRecordingColumns() {
		int column = 0;

		if (getCount() != _lastStatus.count) {
			column += L1PcInventory.COL_COUNT;
		}
		if (getItemId() != _lastStatus.itemId) {
			column += L1PcInventory.COL_ITEMID;
		}
		if (isEquipped() != _lastStatus.isEquipped) {
			column += L1PcInventory.COL_EQUIPPED;
		}
		if (getEnchantLevel() != _lastStatus.enchantLevel) {
			column += L1PcInventory.COL_ENCHANTLVL;
		}
		if (getDurability() != _lastStatus.durability) {
			column += L1PcInventory.COL_DURABILITY;
		}
		if (getChargeCount() != _lastStatus.chargeCount) {
			column += L1PcInventory.COL_CHARGE_COUNT;
		}
		if (getLastUsed() != _lastStatus.lastUsed) {
			column += L1PcInventory.COL_DELAY_EFFECT;
		}
		if (isIdentified() != _lastStatus.isIdentified) {
			column += L1PcInventory.COL_IS_ID;
		}
		if (getRemainingTime() != _lastStatus.remainingTime) {
			column += L1PcInventory.COL_REMAINING_TIME;
		}
		if (getBless() != _lastStatus.bless) {
			column += L1PcInventory.COL_BLESS;
		}
		if (getAttrEnchantLevel() != _lastStatus.attrenchantLevel) {
			column += L1PcInventory.COL_ATTRENCHANTLVL;
		}
		return column;
	}

	/**
	 * 가방이나 창고에서 표시되는 형식의 이름을 개수를 지정해 취득한다. <br>
	 */
	public String getNumberedViewName(int count) {
		
		StringBuilder name = new StringBuilder(getNumberedName(count));
		int itemType2 = getItem().getType2(), itemId = getItem().getItemId();
		if (getItem().getItemId() == 40314 || // 펫의 아뮤렛트
				getItem().getItemId() == 40316) {
			L1Pet pet = PetTable.getInstance().getTemplate(getId());
			if (pet != null) {
				L1Npc npc = NpcTable.getInstance().getTemplate(pet.get_npcid());
				name.append("[Lv." + pet.get_level() + " " + npc.get_nameid() + "]");
			}
		}
		if (itemType2 == 0 && getItem().getType() == 2) { // light
			if (isNowLighting()) {
				name.append(" ($10)");
			}
			if (itemId == 40001 || itemId == 40002) {
				if (getRemainingTime() <= 0) {
					name.append(" ($11)");
				}
			}
		}
		if (getEndTime() != null) {
			if (isIdentified()) {
				name.append(" [" + sdf.format(Long.valueOf(getEndTime().getTime())) + "]");
			} else {
				name.append("");
			}
		}
		if (isEquipped()) {
			if (itemType2 == 1) {
				name.append(" ($9)"); // 장비(Armed)
			} else if (itemType2 == 2) {
				name.append(" ($117)"); // 장비(Worn)
			}
		}
		return name.toString();
	}

	/**
	 * 가방이나 창고에서 표시되는 형식의 이름을 돌려준다. <br>
	 * 예:+10 카타나 (장비)
	 */
	public String getViewName() {
		return getNumberedViewName(_count);
	}

	/**
	 * 로그에 표시되는 형식의 이름을 돌려준다. <br>
	 * 예:아데나(250) / +6 다가
	 */
	public String getLogName() {
		return getNumberedName(_count);
	}

	/**
	 * 로그에 표시되는 형식의 이름을, 개수를 지정해 취득한다.
	 */
	public String getNumberedName(int count) {
		StringBuilder name = new StringBuilder();
		int itemType = getItem().getType2();
		int	attrEnchant = getAttrEnchantLevel();
		if (isIdentified()) {
			if ((getBless() >= 128) && (getBless() <= 131)) {
				name.append("\\fY[봉인]"); 
			}
			if (itemType == L1Item.CATEGORY_WEAPON || itemType == L1Item.CATEGORY_ARMOR) {
				switch(attrEnchant) {
				case 1: name.append("불의 "); break;	// 화령 1단
				case 2: name.append("폭발의 "); break;	// 화령 2단
				case 3: name.append("이그니스의 "); break;	// 화령 3단
				case 4: name.append("물의 "); break;	// 수령 1단
				case 5: name.append("해일의 "); break;	// 수령 2단
				case 6: name.append("운디네의 "); break;	// 수령 3단
				case 7: name.append("바람의 "); break;	// 풍령 1단
				case 8: name.append("태풍의 "); break;	// 풍령 2단
				case 9: name.append("실프의 "); break;	// 풍령 3단
				case 10: name.append("대지의 "); break;	// 지령 1단
				case 11: name.append("파괴의 "); break;	// 지령 2단
				case 12: name.append("클레이의 "); break;	// 지령 3단
				default: break;
				}
				if (getEnchantLevel() >= 0) {
					name.append("+" + getEnchantLevel() + " ");
				} else if (getEnchantLevel() < 0) {
					name.append(String.valueOf(getEnchantLevel()) + " ");
				}
			}
		}
		if (getItem().getItemId() == CTLoadManager.CTSYSTEM_LOAD_ID) {
			MJCTObject obj = MJCTSystemLoader.getInstance().get(getId());
			if (obj == null)
				name.append(_item.getNameId());
			else
				name.append("[").append(obj.name).append("]봉인 액자");
		} else {
			name.append(_item.getName());
		}
		if (isIdentified()) {
			if (getItem().getMaxChargeCount() > 0) {
				name.append(" (" + getChargeCount() + ")");
			}
			if (getItem().getItemId() == ItemId.기마용_투구) { // 기마용 투구
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

	/**
	 * 아이템 상태로부터 서버 패킷으로 이용하는 형식의 바이트열을 생성해 돌려준다.
	 */
	public byte[] getStatusBytes() {
		int itemType = getItem().getType2(), etcItemType = getItem().getType(), itemId = getItem().getItemId(), useType = getItem().getUseType();
		ItemPresentOutStream os = new ItemPresentOutStream();

		try {
			// 인벤토리 보너스 아이템  표기 시작
			InvenBonusItemInfo info = InvenBonusItemLoader.getInstance().getInvenBonusItemInfo(itemId);
			if (info != null) {
				try {
					os.write(InvenBonusItemInfo.getItemView(info));
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
			// 인형에 대한 아이템 표기 시작
			if (useType == L1Item.TYPE_USE_NAGIC_DOLL) {
				MagicDoll magicDoll = MagicDoll.get(getItemId());
				if (magicDoll != null) {
					try {
						os.write(MagicDoll.getItemView(magicDoll, this));
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}
			// 요리에 대한 패킷 설정 패킷길이가 정해져 있으므로 모두 표기를 표현하고 싶지만 
			// 밑에서   같이 출력되는 경우 아이템 표기가 안나오는 문제로 인해 따로 분리한다.
			if(itemType == L1Item.CATEGORY_ETC && etcItemType == L1Item.TYPE_ETCITEM_FOOD) {
				Cooking cook = Cooking.get(getItem().getItemId());
				if (cook != null) {
					try {
						os.write(Cooking.getItemView(cook, this));
						os.writeC(23);
						os.writeMaterial(getItem().getMaterial(), getWeight());
					} catch (IOException e) {
						e.printStackTrace();
					}
				}							
			}

			if(itemType == L1Item.CATEGORY_ETC) { // 기타 아이템 타입 시작
				switch (etcItemType) {
				case L1Item.TYPE_ETCITEM_LIGHT: // 라이트
					os.writeLightRange(getItem().getLightRange());
					break;
				case L1Item.TYPE_ETCITEM_ARROW: // 애로우
				case L1Item.TYPE_ETCITEM_STING: // 스팅
					os.writeDMG(getItem().getDmgSmall(), getItem().getDmgLarge());
					break;
				default:
					os.writeC(23); // 무게
					break;
				}
				os.writeMaterial(getItem().getMaterial(), getWeight());
				// 그외 기타 아이템 표기  일부 주문서류나,일부 39번 표기가 필요한 아이템 표기 시작
				switch (itemId) {
				case ItemId.불멸_가호: 
					os.writeC(39);
					os.writeS("패널티: 경험치 보호");
					break;
				case ItemId.고급_불멸_가호: // 고급 불멸의 가호
					os.writeC(39);
					os.writeS("패널티: 경험치 보호");
					os.writeC(39);
					os.writeS("패널티: 아이템 보호");
					break;
				case ItemId.코마_축복_코인:
					os.writeC(39);
					os.writeS(String.format("AC -%d", 8));
					os.writeMeleeHit(5);
					os.writeRagendHit(5);
					os.writeaStr(5);
					os.writeaDex(5);
					os.writeaCon(1);
					os.writeAddSP(1);
					os.writeAddEXP(20);
					break;
				case ItemId.장비_스왑:
					os.writeC(39);
					os.writeS("유저 명렁어");
					os.writeC(39);
					os.writeS(".저장 1번");
					os.writeC(39);
					os.writeS(".저장 2번");
					break;	
				case ItemId.체력_증강_주문서: 
					os.writeAddMaxHP(50);
					os.writeAddHPPrecovery(4);
					break;
				case ItemId.마력_증강_주문서:
					os.writeMaxMP(50);
					os.writeAddMPPrecovery(4);
					break;
				case ItemId.전투_강화_주문서:	
					os.writeMeleeHit(3);
					os.writeRagendHit(3);
					os.writeMeleeDmg(3);					
					os.writeRagendDmg(3);
					os.writeAddSP(3);
					break;
				case ItemId.글로잉_오라_주문서:
					os.writeC(39);
					os.writeS("근거리 명중 +5");
					os.writeC(39);
					os.writeS("원거리 명중 +5");
					os.writeC(39);
					os.writeS("MR +20%");
					break;
				case ItemId.샤이닝_오라_주문서:
					os.writeC(39);
					os.writeS("AC-8");
					break;
				case ItemId.브레이브_오라_주문서:
					os.writeC(39);
					os.writeS("근거리 대미지 +5");
					os.writeC(39);
					os.writeS("원거리 대미지 +5");
					break;
				case ItemId.드래곤_진주:
					os.writeC(39);
					os.writeS("3단 가속 효과");
					break;
				case ItemId.천상_물약: // 천상의물약
					os.writeC(39);
					os.writeS("경험치 20% 증가");
					break;
				}
				
				if (NoShopAndWare.getInstance().isNoShopAndWare(itemId)) {
					os.writeC(39);
					os.writeS("불가: 창고");
				}
				if (NoDropItem.getInstance().isNoDropItem(itemId))  {
					os.writeC(39);
					os.writeS("불가: 이동");
				}
				if (NoTradable.getInstance().isNoTradable(itemId))  {
					os.writeC(39);
					os.writeS("불가: 교환");
				}
				if (!getItem().isTradable()) {
					os.writeC(39);
					os.writeS("불가:이동,창고,교환");
				}
			}
			// 무기 , 방어구 아이템 표기 시작 
			else if(itemType == L1Item.CATEGORY_WEAPON || itemType == L1Item.CATEGORY_ARMOR) {
				if(itemType == L1Item.CATEGORY_WEAPON) {
					os.writeDMG(getItem().getDmgSmall(), getItem().getDmgLarge());
					os.writeMaterial(getItem().getMaterial(), getWeight());			
				} 
				else if(itemType == L1Item.CATEGORY_ARMOR) {	
					os.writeC(19);
					int ac = getAc();
					if (ac < 0) {
						ac = ac - ac - ac;
					}
					os.writeC(ac);
					os.writeC(getItem().getMaterial());
					os.writeC(-1);
					os.writeD(getWeight());		
				}
				if(getEnchantLevel() != 0) { // 인챈트 레벨
					os.writeEnchantLevel(getAcByEnchantLevel());
				}
				if(getDurability() != 0) { // 손상도
					os.writeDurability(getDurability());
				}
				if(getItem().isTwohandedWeapon()) { // 양손무기 일때
					os.writeTwoHand();
				}
				if(itemType == L1Item.CATEGORY_WEAPON) {
					if(getHitModifier() != 0) {
						os.writeMeleeHit(getHitModifier());
					}
					if(getBowHitModifier() != 0) {
						os.writeRagendHit(getBowHitModifier());
					}
					if(getWeaponMeleeDmg() != 0) { 
						os.writeMeleeDmg(getWeaponMeleeDmg());
					}
					if(getWeaponRangedDmg() != 0) {
						os.writeRagendDmg(getWeaponRangedDmg());
					}
					int bit = 0;  // 클래스 , 전체 표기 
					bit |= getItem().isUseRoyal() ? 1 : 0;
					bit |= getItem().isUseKnight() ? 2 : 0;
					bit |= getItem().isUseElf() ? 4 : 0;
					bit |= getItem().isUseMage() ? 8 : 0;
					bit |= getItem().isUseDarkelf() ? 16 : 0; 

					if(bit == 31) {
						bit = 127;
					}
					os.writeC(7);
					os.writeC(bit);
				}

				if (getStr() != 0) { // 힘 
					os.writeaStr(getStr());
				}
				if (getDex() != 0) { // 덱
					os.writeaDex(getDex());
				}
				if (getCon() != 0) { // 콘
					os.writeaCon(getCon()); 
				}
				if (getWis() != 0) { // 위즈
					os.writeaWis(getWis()); 
				}
				if (getInt() != 0) { // 인트
					os.writeaInt(getInt()); 
				}
				if (getCha() != 0) { // 카리
					os.writeaCha(getCha()); 
				}
				//-- 방어구 전부클래스 표기
				if(itemType == L1Item.CATEGORY_ARMOR) {
					int bit = 0;  // 클래스 , 전체 표기 
					bit |= getItem().isUseRoyal() ? 1 : 0;
					bit |= getItem().isUseKnight() ? 2 : 0;
					bit |= getItem().isUseElf() ? 4 : 0;
					bit |= getItem().isUseMage() ? 8 : 0;
					bit |= getItem().isUseDarkelf() ? 16 : 0; 

					if(bit == 31) {
						bit = 127;
					}
					os.writeC(7);
					os.writeC(bit);

					if(getBowHitModifier() != 0) {
						os.writeRagendHit(getBowHitModifier());
					}
					if(getHitModifier() != 0) {
						os.writeMeleeHit(getHitModifier());
					}
					if(getArmorMeleeDmg() != 0) { 
						os.writeMeleeDmg(getArmorMeleeDmg());
					}
					if(getArmorRegendDmg() != 0) {
						os.writeRagendDmg(getArmorRegendDmg());
					}
				}
				if (getMeleeCritical() != 0) {
					os.writeC(39);
					os.writeS("근거리 치명타 " + getMeleeCritical() + "%");
				}
				if (getRangedCritical() != 0) {
					os.writeC(39);
					os.writeS("원거리 치명타 " + getRangedCritical() + "%");
				}
				if (getMagicCritical() != 0) {
					os.writeC(39);
					os.writeS("마법 치명타 " + getMagicCritical() + "%");
				}		
				if(getItem().isHpDrain()) { // Hp 흡수
					os.writeHpDrain();
				}
				if(getItem().isMpDrain()) { // Mp 흡수
					os.writeMpDrain();
				}
				if (getItem().isHasteItem()) { // 헤이스트 
					os.writeC(18);
				}
				// 물약 회복량
				if(getPotionRecoveryAmount() != 0 || getPotionRecovery() != 0) {
					os.writeC(39); os.writeS("물약 회복량: +" + getPotionRecoveryAmount() + "%+" +getPotionRecovery());
				}
				if (get_regist_technology() != 0) { // 스턴내성
					os.writeC(39);
					os.writeS(String.format("스턴 내성 +%d", get_regist_technology()));
				}	
				if (get_technique_hit() != 0) { // 스턴적중
					os.writeC(39); 
					os.writeS(String.format("스턴 적중 +%d", get_technique_hit()));
				}			
				if (get_regist_spirit() != 0) { // 정령내성
					os.writeC(39);
					os.writeS(String.format("정령 내성 +%d", get_regist_spirit())); 
				}		
				if (get_spirit_hit() != 0) { // 정령적중
					os.writeC(39);
					os.writeS(String.format("정령 적중 +%d", get_spirit_hit()));  
				} 	
				if (get_magic_hit() != 0) { // 마법적중
					os.writeC(39);
					os.writeS(String.format("마법 명중 +%d", get_magic_hit()));   
				} 
				if (getHp() != 0) { // 최대 HP
					os.writeC(39);
					os.writeS(String.format("최대 HP +%d", getHp()));
				}
				if (getMp() != 0) { // 최대  MP
					os.writeC(39);
					os.writeS(String.format("최대 MP +%d", getMp()));
				}	
				if (getMr() != 0) { // MR
					os.writeAddMR(getMr());
				}
				if (getHpr() != 0) { // 피틱
					os.writeAddHPPrecovery(getHpr());
				}
				// 엠틱 표시
				if (getMpr() != 0) {
					os.writeAddMPPrecovery(getMpr());
				} 
				if(getSp() != 0) { // 스펠
					os.writeAddSP(getSp());
				}
				if (getDamageReduction() != 0) { // 대미지리덕션
					os.writeC(39);
					os.writeS(String.format("대미지 감소 +%d", getDamageReduction()));
				}	
				if (getPvPDamage() != 0) { // PVP추가 대미지
					os.writeC(39);
					os.writeS(String.format("PVP 추가 대미지 +%d", getPvPDamage())); 
				} 			
				if (getPvpReduction() != 0) { 	// PVP 대미지감소
					os.writeC(39);
					os.writeS(String.format("PVP 대미지 감소 +%d", getPvpReduction())); 
				} 
				if (getReductionCancel() != 0) { // 대미지 감소 무시
					os.writeC(39);
					os.writeS(String.format("대미지 감소 무시 +%d", getReductionCancel()));
				} 
				if (getWeightReduction() != 0) { // 무게 증가
					os.writeC(39);
					os.writeS(String.format("소지 무게 증가 +%d", getWeightReduction()));
				} 	
				// 데미지 감소 확률 
				if(getProbDamageReduc() != 0 || getProbDamageReduction() != 0) {
					os.writeC(39); os.writeS("대미지 감소 확률 +" + getProbDamageReduc() + "(" +getProbDamageReduction()+"%)");
				}
				// 추가 대미지 확률		
				if(getProbAddDamage() != 0 || getProbDamage() != 0) {
					os.writeC(39); os.writeS("추가 대미지 확률 +" + getProbAddDamage() + "(" +getProbDamage()+"%)");
				}
				if(getProbabilityMagicEvasion() != 0) {
					os.writeC(39); os.writeS(String.format("확률 마법 회피 +%d", getProbabilityMagicEvasion()));
				}
				if(getDefenseFire() != 0) { // 불 속성
					os.writeRegistFire(getDefenseFire());
				}
				if(getDefenseWater() != 0) { // 물 속성
					os.writeRegistWater(getDefenseWater());
				}
				if(getDefenseWind() != 0) { // 바람 속성
					os.writeRegistWind(getDefenseWind());
				}
				if(getDefenseEarth() != 0) { // 땅 속성
					os.writeRegistEarth(getDefenseEarth());
				}			
				if(getExpByItem() != 0) { 	// 경험치 보너스
					os.writeC(39); os.writeS(String.format("EXP +%d", getExpByItem()));
				}			
				// 마법 발동 
				if(getMagicName() != null) {
					os.writeC(39);
					os.writeS("발동:" + getMagicName());
				}
				/** 아이템 안전인챈 표시 추가 **/
				int SafeEnchant = getItem().get_safeenchant();
				if (SafeEnchant >= 0) {
					os.writeC(39);
					os.writeS("[안전 인챈트: +" + SafeEnchant + "]");		
				} else {
					os.writeC(39);
					os.writeS("[인챈트 불가]");
				}
				
				if (NoShopAndWare.getInstance().isNoShopAndWare(itemId)) {
					os.writeC(39);
					os.writeS("불가: 창고");
				}
				if (NoDropItem.getInstance().isNoDropItem(itemId))  {
					os.writeC(39);
					os.writeS("불가: 이동");
				}
				if (NoTradable.getInstance().isNoTradable(itemId))  {
					os.writeC(39);
					os.writeS("불가: 교환");
				}
				if (!getItem().isTradable()) {
					os.writeC(39);
					os.writeS("불가:이동,창고,교환");
				}
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

	class EnchantTimer implements Runnable {

		private int _skillId;
		private boolean _active = true;
		private boolean _effectClear = false;
		private long _expireTime;

		public EnchantTimer(int skillId, long expireTime) {
			_skillId = skillId;
			_expireTime = expireTime;
		}

		public int getRemainTime() {
			int remainTime = (int) (_expireTime - System.currentTimeMillis()) / 1000;

			if (remainTime < 1) {
				remainTime = 1;
			}
			return remainTime;
		}

		@Override
		public void run() {
			try {
				if (!_active) {
					return;
				}
				ClearEffect();
			} catch (Exception e) {
			}
		}

		public void cancel() {
			_active = false;
			ClearEffect();
			if(_owner != null) {
				_owner.sendPackets(new S_ServerMessage(308, getLogName()), true);
			}
		}

		public void ClearEffect() {
			synchronized (this) {
				if (_effectClear) {
					return;
				}
				_effectClear = true;
			}
			switch (_skillId) { 
			default:
				break;
			}
			removeSkillEffectTimer(_skillId);
		}
	}
	protected void removeSkillEffectTimer(int skillId) {
		_skillEffect.remove(skillId);
	}
	public boolean hasSkillEffectTimer(int skillId) {
		return _skillEffect.containsKey(skillId);
	}
	protected void killSkillEffectTimer(int skillId) {
		EnchantTimer timer = _skillEffect.remove(skillId);
		if (timer != null) {
			timer.cancel();
		}
	} 
	private L1PcInstance _owner;
	private L1PcInstance _itemOwner;
	public L1PcInstance getOwner() {
		return _owner;
	}
	public void setOwner(L1PcInstance pc) {
		_owner = pc;
	}
	public L1PcInstance getItemOwner() {
		return _itemOwner;
	}
	public void setItemOwner(L1PcInstance pc) {
		_itemOwner = pc;
	}
	public void startItemOwnerTimer(L1PcInstance pc) {
		setItemOwner(pc);
		ItemOwnerTimer timer = new ItemOwnerTimer(this, 10000);
		timer.begin();
	}
	private EquipmentTimer _equipmentTimer;
	public void startEquipmentTimer(L1PcInstance pc) {
		if (getRemainingTime() > 0) {
			_equipmentTimer = new EquipmentTimer(pc, this, 1000);
			GeneralThreadPool.getInstance().schedule(_equipmentTimer, 1000);
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
	public int getWeightReduction() {
		int result = getItem().getWeightReduction();
		return result;
	}
	public int getEr() { 
		return 0;
	}
	// 보조 물약 회복량 
	public int getPotionRecovery() {
		int result = getItem().getProbMagicEvasion(), 인챈트레벨 = getEnchantLevel(), 
				아이템아이디 = getItemId(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType();
		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_AMULET) { 
					result += (인챈트레벨 >= 10 ? 9 : 인챈트레벨 == 9 ? 8 :  인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 3 : 0);
				}
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_EARRING) { 
					result += (인챈트레벨 >= 10 ? 8 : 인챈트레벨 == 9 ? 7 :  인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 2 : 0);
				}
			}
		}
		return result;
	}
	// 물약 회복량 
	public int getPotionRecoveryAmount() {
		int result = getItem().getProbMagicEvasion(), 인챈트레벨 = getEnchantLevel(), 
				아이템아이디 = getItemId(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType();	
		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_AMULET) { 
					result += (인챈트레벨 >= 10 ? 11 : 인챈트레벨 == 9 ? 10 :  인챈트레벨 == 8 ? 9 : 인챈트레벨 == 7 ? 7 : 인챈트레벨 == 6 ? 5 : 인챈트레벨 == 5 ? 3 :0);
				}
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_EARRING) { 
					result += (인챈트레벨 >= 10 ? 10 : 인챈트레벨 == 9 ? 9 :  인챈트레벨 == 8 ? 8 : 인챈트레벨 == 7 ? 6 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 2 : 0);
				}
			}
		}
		return result;
	}
	/**
	 * 확률 마법 회피 처리 
	 */
	public int getProbabilityMagicEvasion() {
		int result = getItem().getProbMagicEvasion(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();	
		switch (아이템아이디) {
		case ItemId.스냅퍼_마법저항_반지: result += (인챈트레벨 >= 9 ? 7 : 인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 1 : 0); break;
		case ItemId.축복_스냅퍼_마법저항_반지: result += (인챈트레벨 >= 9 ? 9 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 1 : 0); break;
		}	
		return result;
	}
	
	public int getDoubleDmgChance() {
		int result = getItem().getDoubleDmgChance(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();		
		switch (아이템아이디) {
		case ItemId.포효_이도류: result += 인챈트레벨; break;
		}
		return result;
	}
	/**
	 * 데이터베이스에서 매직네임은 인챈트레벨이 필요없는 아이템 여기는 인챈트 레벨이 1이상인 아이템
	 * @return
	 */
	public String getMagicName() {
		String result = getItem().getMagicName();
		int 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		
		if(아이템아이디 == ItemId.악몽_장궁 && 인챈트레벨 > 9) {
			result = "악몽";
		}
		if(아이템아이디 == ItemId.드래곤_슬레이어 && 인챈트레벨 > 9) {
			result = "드래곤의 일격";
		}
		return result;
	}
	//-- 마법 치명타
	public int getMagicCritical() {
		int result = getItem().getMagicCritical(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		switch (아이템아이디) {
		case ItemId.발라카스_마력: result += (인챈트레벨 >= 9 ? 3 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0); break;
		}
		return result;
	}
	//-- 원거리 치명타
	public int getRangedCritical() {
		int result = getItem().getRangedCritical(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		switch (아이템아이디) {
		case ItemId.발라카스_인내력: result += (인챈트레벨 >= 9 ? 3 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0); break;
		}
		return result;
	}
	//-- 근거리 치명타
	public int getMeleeCritical() {
		int result = getItem().getMeleeCritical(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		switch (아이템아이디) {
		case ItemId.발라카스_완력: case ItemId.발라카스_예지력: result += (인챈트레벨 >= 9 ? 3 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0); break;
		case ItemId.진명황_집행검:  result += (인챈트레벨 >= 9 ? 10 : 인챈트레벨 == 8 ? 9 : 인챈트레벨 == 7 ? 8 : 인챈트레벨 == 6 ? 7 : 인챈트레벨 == 5 ? 6 : 인챈트레벨 == 4 ? 5 :
			 인챈트레벨 == 3 ? 4 : 인챈트레벨 == 2 ? 3 : 인챈트레벨 == 1 ? 2 : 인챈트레벨 == 0 ? 1 : 0); break;
		}
		return result;
	}
	//-- 데미지 감소
	public int getDamageReduction() {
		int result = getItem().getDamageReduction(), 
				인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 
				아이템종류 = getItem().getType(), 아이템아이디 = getItemId();
		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_BELT) { 
					result += (인챈트레벨 == 9 ? 5 :인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0);
				}
			}
		}
		switch (아이템아이디) {
		case ItemId.룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 || 인챈트레벨 == 3 ? 1 : 0); break;
		case ItemId.축복_룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 인챈트레벨 == 4 ? 2 : 인챈트레벨 == 3 ? 1 : 0); break;		
		case ItemId.스냅퍼_체력_반지: result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 1 : 0); break;
		case ItemId.축복_스냅퍼_체력_반지: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 2 : 인챈트레벨 == 6 ? 1 : 0); break;
		case ItemId.지룡_티셔츠: case ItemId.축복_지룡_티셔츠: result += (인챈트레벨 == 10 || 인챈트레벨 == 9 ? 1 : 0); break;
		}

		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getDamageReduction(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.DmgReduction) {
			result += getBlessTypeValue();
		}
		return result;
	}
	//-- PVP 대미지 감소
	public int getPvpReduction() {
		int result = 0, 
				인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 
				아이템종류 = getItem().getType(), 아이템아이디 = getItemId();

		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_BELT) { 
					result += (인챈트레벨 == 9 ? 7 :  인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 1 : 0);
				}
			}
		}	
		if (아이템타입 == L1Item.CATEGORY_ARMOR || 아이템타입 == L1Item.CATEGORY_WEAPON) {
			result += getItem().getPvpReduction();
		}
		switch (아이템아이디) {
		case ItemId.지룡_티셔츠: case ItemId.축복_지룡_티셔츠: case ItemId.화룡_티셔츠: case ItemId.축복_화룡_티셔츠: case ItemId.풍룡_티셔츠: 
		case ItemId.축복_풍룡_티셔츠: case ItemId.수룡_티셔츠: case ItemId.축복_수룡_티셔츠: result += (인챈트레벨 == 10 ? 1 : 0); break;
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getPvpReduction(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.PvpReduction) {
			result += getBlessTypeValue();
		}
		return result;
	}
	//-- PVP 대미지
	public int getPvPDamage() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();

		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_RING || 아이템종류 == L1Item.TYPE_ARMOR_RING_TWO) { 
					result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 2 : 인챈트레벨 == 6 ? 1 : 0);
				}
			}
		}		
		if (아이템타입 == L1Item.CATEGORY_ARMOR || 아이템타입 == L1Item.CATEGORY_WEAPON) {
			result += getItem().getPvpDmg();
		}
		switch (아이템아이디) {
		case ItemId.스냅퍼_용사_반지: case ItemId.스냅퍼_체력_반지: case ItemId.스냅퍼_마법저항_반지: case ItemId.스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 1 : 0); break;		
		case ItemId.축복_스냅퍼_용사_반지: case ItemId.축복_스냅퍼_체력_반지: case ItemId.축복_스냅퍼_마법저항_반지: case ItemId.축복_스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 1 : 0); break;	
		case ItemId.지룡_티셔츠: case ItemId.축복_지룡_티셔츠: case ItemId.화룡_티셔츠: case ItemId.축복_화룡_티셔츠:
		case ItemId.풍룡_티셔츠: case ItemId.축복_풍룡_티셔츠: case ItemId.수룡_티셔츠: case ItemId.축복_수룡_티셔츠: result += (인챈트레벨 == 10 ? 1 : 0); break;
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getPvpDmg(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.PvpDmg) {
			result += getBlessTypeValue();
		}
		return result;
	}

	public int getMpr() {
		int result = getItem().get_addmpr(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getMpr(enchantLevel);
		}
		if(getBlessType() == BlessTypeEnchant.Mpr) {
			result += getBlessTypeValue();
		}
		return result;
	}
	public int getHpr() {
		int result = getItem().get_addhpr(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getHpr(enchantLevel);
		}
		if(getBlessType() == BlessTypeEnchant.Hpr) {
			result += getBlessTypeValue();
		}
		return result;
	}
	public int getExpByItem() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();;

		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getExpBonus(인챈트레벨);
		}
		switch (아이템아이디) {
		case ItemId.지룡_티셔츠: result += (인챈트레벨 == 10 ? 6 : 인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 2 : 0); break;
		case ItemId.축복_지룡_티셔츠: result += (인챈트레벨 == 10 ? 8 : 인챈트레벨 == 9 ? 6 : 인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 2 : 0); break;
		}
		if(getBlessType() == BlessTypeEnchant.Exp) {
			result += getBlessTypeValue();
		}
		return result;
	}
	// 추가 대미지 확률 표기만 (실제 효과는 attack.java 참조)
	public int getProbAddDamage() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		switch (아이템아이디) {
		case ItemId.룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 || 인챈트레벨 == 8 ? 25 : 인챈트레벨 >= 5 && 인챈트레벨 <= 7 ? 20 : 0); break;
		case ItemId.축복_룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 || 인챈트레벨 == 8 ? 25 : 인챈트레벨 >= 4 && 인챈트레벨 <= 7 ? 20 : 0); break;
		}
		return result;
	}
	public int getProbDamage() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		switch (아이템아이디) {
		case ItemId.룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 0); break;
		case ItemId.축복_룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 ? 8 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 인챈트레벨 == 4 ? 2 : 0); break;	
		}
		return result;
	}
	// 데미지 감소 확률 표기만 (실제 효과는 attack.java 참조)
	public int getProbDamageReduc() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		switch (아이템아이디) {
		case ItemId.룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 || 인챈트레벨 == 8 ? 25 : 인챈트레벨 >= 5 && 인챈트레벨 <= 7 ? 20 : 0); break;
		case ItemId.축복_룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 || 인챈트레벨 == 8 ? 30 : 인챈트레벨 >= 4 && 인챈트레벨 <= 7 ? 20 : 0); break;
		case ItemId.스냅퍼_체력_반지: result += (인챈트레벨 <= 9 && 인챈트레벨 >= 7 ? 20 : 0); break;
		case ItemId.축복_스냅퍼_체력_반지: result += (인챈트레벨 <= 9 && 인챈트레벨 >= 6 ? 20 : 0); break;
		}
		return result;
	}
	public int getProbDamageReduction() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();

		switch (아이템아이디) {
		case ItemId.룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 0); break;
		case ItemId.축복_룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 8 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 인챈트레벨 == 4 ? 2 : 0); break;
		case ItemId.스냅퍼_체력_반지: result += (인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0); break;
		case ItemId.축복_스냅퍼_체력_반지: result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 2 : 인챈트레벨 == 6 ? 1 : 0); break;
		}
		return result;
	}
	public int getDefenseFire() {
		int result = getItem().get_defense_fire(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getDefenseFire(enchantLevel);
		}
		return result;
	}

	public int getDefenseWater() {
		int result = getItem().get_defense_water(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getDefenseWater(enchantLevel);
		}
		return result;
	}

	public int getDefenseWind() {
		int result = getItem().get_defense_wind(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getDefenseWind(enchantLevel);
		}
		return result;
	}

	public int getDefenseEarth() {
		int result = getItem().get_defense_earth(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getDefenseEarth(enchantLevel);
		}
		return result;
	}

	//-- 스턴내성
	public int get_regist_technology() {
		int result = getItem().getregist_technology(), 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_EARRING || 아이템종류 == L1Item.TYPE_ARMOR_AMULET) { 
					result += (인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 2 : 0);
				}
			}
		}
		switch (아이템아이디) {
		case ItemId.스냅퍼_체력_반지: case ItemId.축복_스냅퍼_체력_반지: case ItemId.스냅퍼_용사_반지: case ItemId.축복_스냅퍼_용사_반지:
		case ItemId.스냅퍼_마법저항_반지: case ItemId.축복_스냅퍼_마법저항_반지: case ItemId.스냅퍼_지혜_반지: case ItemId.축복_스냅퍼_지혜_반지:
			result += (인챈트레벨 == 9 || 인챈트레벨 == 8 ? 9 : 인챈트레벨 == 7 ? 7 : 인챈트레벨 == 6 ? 5 : 0); break;
		case ItemId.화룡_티셔츠: case ItemId.축복_화룡_티셔츠: case ItemId.풍룡_티셔츠: case ItemId.축복_풍룡_티셔츠: case ItemId.수룡_티셔츠: case ItemId.축복_수룡_티셔츠: 
			result += (인챈트레벨 == 10 ? 18 : 인챈트레벨 == 9 ? 15 : 인챈트레벨 == 8 ? 12 : 인챈트레벨 == 7 ? 10 : 인챈트레벨 == 6 ? 9 : 인챈트레벨 == 5 ? 8 :0); break;
		}

		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.get_regist_technology(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.regist_technology) {
			result += getBlessTypeValue();
		}
		return result;
	}

	//-- 스턴 적중
	public int get_technique_hit() {
		int result = getItem().gettechnique_hit() , 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		switch (아이템아이디) {
		case ItemId.나이트발드의_양손검: result += (인챈트레벨 >= 12 ? 4 : 인챈트레벨 == 11 ? 3 : 인챈트레벨 == 10 ? 2 : 인챈트레벨 == 9 ? 1 : 0); break;
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.get_technique_hit(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.technique_hit) {
			result += getBlessTypeValue();
		}

		return result;
	}
	//-- 정령 내성
	public int get_regist_spirit() {
		int result = getItem().getregist_spirit(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();

		switch (아이템아이디) {
		case ItemId.축복_화룡_티셔츠: case ItemId.축복_풍룡_티셔츠: case ItemId.축복_수룡_티셔츠: result += (인챈트레벨 == 10 ? 6 : 인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0); break;
		}

		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.get_regist_spirit(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.regist_spirit) {
			result += getBlessTypeValue();
		}

		return result;
	}
	//-- 정령 적중
	public int get_spirit_hit() {
		int result = getItem().getspirit_hit(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();
		
		switch(아이템아이디) {
		case ItemId.진노_크로우: result += (인챈트레벨 >= 10 ? 3 : 인챈트레벨 == 9 ? 2 : 인챈트레벨 == 8 ? 1 : 0); break;
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.get_spirit_hit(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.spirit_hit) {
			result += getBlessTypeValue();
		}

		return result;
	}

	//--마법 적중
	public int get_magic_hit() {

		int result = getItem().getmagic_hit(), 인챈트레벨 = getEnchantLevel(), 아이템아이디 = getItemId();

		switch (아이템아이디) {
		case ItemId.룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 1 : 0); break;
		case ItemId.축복_룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 12 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 1 : 0); break;
		case ItemId.스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 1 : 0); break;
		case ItemId.축복_스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 2 : 인챈트레벨 == 6 ? 1 : 0); break;
		case ItemId.수룡_티셔츠: result += (인챈트레벨 == 10 ? 4 : 인챈트레벨 == 9 ? 3 : 인챈트레벨 == 8 ? 1 : 0); break;
		case ItemId.축복_수룡_티셔츠: result += (인챈트레벨 == 10 ? 5 : 인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 2 : 0); break;
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.get_magic_hit(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.magic_hit) {
			result += getBlessTypeValue();
		}

		return result;
	}

	public int getSp() {
		int result = getItem().get_addsp(), 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();
		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_RING || 아이템종류 == L1Item.TYPE_ARMOR_RING_TWO) { 
					result += (인챈트레벨 == 9 ? 3 :인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0);
				}
			}
		}
		switch (아이템아이디) {
		case ItemId.룸티스_보랏빛_귀걸이: 	result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 ? 1 : 인챈트레벨 == 3 ? 1 : 0); break;
		case ItemId.축복_룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 ? 2 : 인챈트레벨 == 3 ? 1 : 0); break;
		case ItemId.스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0); break;
		case ItemId.축복_스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 :  인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 ? 1 : 0); break;
		case ItemId.리치로브: result += (인챈트레벨 - 2); break;
		case ItemId.머미_로드_장갑: 	result += (인챈트레벨 >= 10 ? 4 : 인챈트레벨 == 9 ? 3 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0); break;
		case ItemId.수룡_티셔츠: result += (인챈트레벨 == 10 ? 2 : 인챈트레벨 == 9 || 인챈트레벨 == 8 ? 1 : 0); break;
		case ItemId.축복_수룡_티셔츠: result += (인챈트레벨 == 10 ? 2 : 인챈트레벨 == 9 || 인챈트레벨 == 8 || 인챈트레벨 == 7 ? 1 : 0); break;
		}

		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getSp(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.Sp) {
			result += getBlessTypeValue();
		} 
		if(result < 0) {
			result = 0;
		}
		return result;
	}

	public int getHp() {
		int result = getItem().get_addhp(), 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();

		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_BELT) { 
					result += (인챈트레벨 == 9 ? 50 : 인챈트레벨 == 8 ? 40 : 인챈트레벨 == 7 ? 30 : 인챈트레벨 == 6 ? 20 : 0);
				}
			}
		}	
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON))  {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_AMULET 
						|| 아이템종류 == L1Item.TYPE_ARMOR_EARRING || 아이템종류 == L1Item.TYPE_ARMOR_RING || 아이템종류 == L1Item.TYPE_ARMOR_RING_TWO) { 
					result += (인챈트레벨 == 9 ? 60 : 인챈트레벨 == 8 || 인챈트레벨 == 7 ? 50 : 인챈트레벨 == 6 || 인챈트레벨 == 5 ? 40 : 인챈트레벨 == 4 ? 30 : 인챈트레벨 == 3 ? 20 : 인챈트레벨 == 2 ? 10 : 인챈트레벨 == 1 ? 5 : 0);
				}
			}
		}	
		switch (아이템아이디) {
		case ItemId.룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 100 : 인챈트레벨 == 8 ? 90 : 인챈트레벨 == 7 ? 80 : 인챈트레벨 == 6 ? 70 : 인챈트레벨 == 5 ? 60 : 인챈트레벨 == 4 ? 50 : 인챈트레벨 == 3 ? 40 : 인챈트레벨 == 2 ? 30 : 인챈트레벨 == 1 ? 20 : 0); break;			
		case ItemId.축복_룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 150 : 인챈트레벨 == 8 ? 140 : 인챈트레벨 == 7 ? 90 : 인챈트레벨 == 6 ? 80 : 인챈트레벨 == 5 ? 70 : 인챈트레벨 == 4 ? 60 : 인챈트레벨 == 3 ? 50 : 인챈트레벨 == 2 ? 30 : 인챈트레벨 == 1 ? 20 : 0); break;				  
		case ItemId.룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 100 : 인챈트레벨 == 8 ? 50 : 0); break;
		case ItemId.축복_룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 150 : 인챈트레벨 == 8 ? 100 : 0); break;
		case ItemId.스냅퍼_용사_반지: 	result += (인챈트레벨 == 8 ? 30 : 인챈트레벨 == 7 ? 25 : 인챈트레벨 == 6 ? 20 : 인챈트레벨 == 5 ? 15 : 인챈트레벨 == 4 ? 10 :  인챈트레벨 == 3 ? 5 :  0); break;
		case ItemId.축복_스냅퍼_용사_반지: result += (인챈트레벨 == 8 || 인챈트레벨 == 7 ? 30 : 인챈트레벨 == 6 ? 25 : 인챈트레벨 == 5 ? 20 : 인챈트레벨 == 4 ? 15 : 인챈트레벨 == 3 ? 10 : 0); break;
		case ItemId.스냅퍼_체력_반지: case ItemId.스냅퍼_마법저항_반지: 	result += (인챈트레벨 == 9 ? 60 : 인챈트레벨 == 8 ? 50 : 인챈트레벨 == 7 ? 45 : 인챈트레벨 == 6 ? 40 : 인챈트레벨 == 5 ? 35 : 인챈트레벨 == 4 ? 30 : 인챈트레벨 == 3 ? 25 : 인챈트레벨 == 2 ? 20 : 인챈트레벨 == 1 ? 15 : 0); break;	
		case ItemId.축복_스냅퍼_마법저항_반지: result += (인챈트레벨 == 9 ? 60 :인챈트레벨 == 8 || 인챈트레벨 == 7 ? 50 : 인챈트레벨 == 6 ? 45 : 인챈트레벨 == 5 ? 40 : 인챈트레벨 == 4 ? 35 : 인챈트레벨 == 3 ? 30 : 인챈트레벨 == 2 ? 20 : 인챈트레벨 == 1 ? 15 : 0); break;		
		case ItemId.축복_스냅퍼_체력_반지: result += (인챈트레벨 == 8 ? 65 : 인챈트레벨 == 7 ? 55 : 인챈트레벨 == 6 ? 45 : 인챈트레벨 == 5 ? 40 : 인챈트레벨 == 4 ? 35 : 인챈트레벨 == 3 ? 30 : 인챈트레벨 == 2 ? 20 : 인챈트레벨 == 1 ? 15 : 0); break;	
		case ItemId.스냅퍼_지혜_반지: 	result += (인챈트레벨 == 9 ? 50 : 인챈트레벨 == 8 ? 40 : 인챈트레벨 == 7 ? 35 : 인챈트레벨 == 6 ? 30 : 인챈트레벨 == 5 ? 25 : 인챈트레벨 == 4 ? 20 : 인챈트레벨 == 3 ? 15 : 인챈트레벨 == 2 ? 10 : 인챈트레벨 == 1 ? 5 : 0); break;
		case ItemId.축복_스냅퍼_지혜_반지: 	result += (인챈트레벨 == 9 ? 60 : 인챈트레벨 == 8 ? 50 : 인챈트레벨 == 7 ? 40 : 인챈트레벨 == 6 ? 35 : 인챈트레벨 == 5 ? 30 : 인챈트레벨 == 4 ? 25 : 인챈트레벨 == 3 ? 20 : 인챈트레벨 == 2 ? 10 : 인챈트레벨 == 1 ? 5 : 0); break;		
		case ItemId.지룡_티셔츠: case ItemId.축복_지룡_티셔츠: case ItemId.화룡_티셔츠: case ItemId.축복_화룡_티셔츠: case ItemId.풍룡_티셔츠: case ItemId.축복_풍룡_티셔츠: case ItemId.수룡_티셔츠: case ItemId.축복_수룡_티셔츠: result += (인챈트레벨 == 10 ? 200 : 0); break;
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());	
		if (eb != null) {
			result += eb.getHp(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.Hp) {
			result += getBlessTypeValue();
		}
		return result;
	}

	public int getMp() {
		int result = getItem().get_addmp(), 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();	
		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_BELT) { 
					result += (인챈트레벨 == 9 ? 60 : 인챈트레벨 == 8 || 인챈트레벨 == 7 ? 50 : 인챈트레벨 == 6 || 인챈트레벨 == 5 ? 40 : 인챈트레벨 == 4 ? 30 : 인챈트레벨 == 3 ? 20 : 인챈트레벨 == 2 ? 10 : 인챈트레벨 == 1 ? 5 : 0);
				}
			}
		}
		switch (아이템아이디) {
		case ItemId.룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 125 : 인챈트레벨 == 8 ? 95 : 인챈트레벨 == 7 ? 70 : 인챈트레벨 == 6 ? 55 : 인챈트레벨 == 5 ? 50 : 인챈트레벨 == 4 ? 35 : 인챈트레벨 == 3 ? 30 : 인챈트레벨 == 2 ? 15 : 인챈트레벨 == 1 ? 10 : 0); break;		
		case ItemId.축복_룸티스_보랏빛_귀걸이:result += (인챈트레벨 == 9 ? 155 : 인챈트레벨 == 8 ? 125 : 인챈트레벨 == 7 ? 95 : 인챈트레벨 == 6 ? 70 : 인챈트레벨 == 5 ? 55 : 인챈트레벨 == 4 ? 50 : 인챈트레벨 == 3 ? 35 : 인챈트레벨 == 2 ? 15 : 인챈트레벨 == 1 ? 10 : 0); break;			
		case ItemId.스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 55 : 인챈트레벨 == 8 ? 15 : 0); break;
		case ItemId.축복_스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 65 : 인챈트레벨 == 8 ? 20 : 인챈트레벨 == 7 ? 15 : 0); break;
		}

		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getMp(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.Mp) {
			result += getBlessTypeValue();
		}
		if(result < 0) {
			result = 0;
		}
		return result;
	}
	public int getStr() {
		int result = getItem().get_addstr(), enchantLevel = getEnchantLevel();

		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null)
			result += eb.getStr(enchantLevel);
		if(getBlessType() == BlessTypeEnchant.Str) {
			result += getBlessTypeValue();   
		} 
		return result;
	}

	public int getCon() {
		int result = getItem().get_addcon(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null)
			result += eb.getCon(enchantLevel);
		if(getBlessType() == BlessTypeEnchant.Con) {
			result += getBlessTypeValue();
		}
		return result;
	}

	public int getDex() {
		int result = getItem().get_adddex(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null)
			result += eb.getDex(enchantLevel);
		if(getBlessType() == BlessTypeEnchant.Dex) {
			result += getBlessTypeValue();
		}
		return result;
	}

	public int getWis() {
		int result = getItem().get_addwis(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null)
			result += eb.getWis(enchantLevel);
		if(getBlessType() == BlessTypeEnchant.Wis) {
			result += getBlessTypeValue();
		}
		return result;
	}

	public int getInt() {
		int result = getItem().get_addint(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null)
			result += eb.getInt(enchantLevel);
		if(getBlessType() == BlessTypeEnchant.Int) {
			result += getBlessTypeValue();
		}
		return result;
	}

	public int getCha() {
		int result = getItem().get_addcha(), enchantLevel = getEnchantLevel();
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null)
			result += eb.getCha(enchantLevel);
		if(getBlessType() == BlessTypeEnchant.Cha) {
			result += getBlessTypeValue();
		}
		return result;
	}
	public int getBowHitModifier() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이텝타입 = getItem().getType(), 타입 = getItem().getType2(), 아이템아이디 = getItemId();
		if (타입 == L1Item.CATEGORY_WEAPON && (아이텝타입 == L1Item.TYPE_WEAPON_BOW 
				|| 아이텝타입 == L1Item.TYPE_WEAPON_GAUNTLET || 아이텝타입 == L1Item.TYPE_WEAPON_SINGLEBOW)) {
			result += getItem().getHitModifier(); // 무기에 포함된 원거리
		}
		if(타입 == L1Item.CATEGORY_ARMOR) {
			result += getItem().getBowHitRate(); // 무기에 포함된 원거리
		}
		switch (아이템아이디) {
		case ItemId.룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 1 : 0); break;			
		case ItemId.축복_룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 1 : 0); break;
		case ItemId.풍룡_티셔츠: result += (인챈트레벨 == 10 ? 6 : 인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 2 : 0); break;
		case ItemId.축복_풍룡_티셔츠: result += (인챈트레벨 == 10 ? 7 : 인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 1 : 0); break;
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getBowHitModifier(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.BowHit) {
			result += getBlessTypeValue();
		}
		return result;
	}
	/**
	 * 무기 원거리 데미지 처리 
	 * @return
	 */
	public int getWeaponRangedDmg() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType();

		if (아이템타입 == L1Item.CATEGORY_WEAPON && (아이템종류 == L1Item.TYPE_WEAPON_BOW 
				|| 아이템종류 == L1Item.TYPE_WEAPON_GAUNTLET || 아이템종류 == L1Item.TYPE_WEAPON_SINGLEBOW)) {
			result += getItem().getDmgModifier(); // 무기에 포함된 원거리
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getBowDmgModifier(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.BowDmg) {
			result += getBlessTypeValue();
		}
		return result;
	}
	/**
	 * 방어구 원거리 대미지 처리
	 * @return
	 */
	public int getArmorRegendDmg() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();
		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON))  {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_RING || 아이템종류 == L1Item.TYPE_ARMOR_RING_TWO) { 
					result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0);
				}
			}
		}
		if(아이템타입 == L1Item.CATEGORY_ARMOR) {
			result += getItem().getRangedDmg();
		}
		switch (아이템아이디) {  
		case ItemId.머미_로드_왕관: result += (인챈트레벨 >= 10 ? 6 : 인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0); break;
		case ItemId.룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 ? 1 : 인챈트레벨 == 3 ? 1 : 0); break;			
		case ItemId.축복_룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 ? 12 : 인챈트레벨 == 8 ? 9 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 인챈트레벨 == 4 ? 2 : 인챈트레벨 == 3 ? 1 : 0); break;
		case ItemId.풍룡_티셔츠: case ItemId.축복_풍룡_티셔츠: result += (인챈트레벨 == 10 || 인챈트레벨 == 9 ? 1 : 0); break;
		}
		if(getBlessType() == BlessTypeEnchant.BowDmg) {
			result += getBlessTypeValue();
		}
		return result;
	}
	/**
	 * 방어구 근거리 데미지 처리 
	 * @return
	 */
	public int getArmorMeleeDmg() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();

		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON))  {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_RING || 아이템종류 == L1Item.TYPE_ARMOR_RING_TWO) { 
					result += (인챈트레벨 == 9 ? 5 :인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0);
				}
			}	
		}	
		switch (아이템아이디) {
		case ItemId.룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 ? 9 :인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 || 인챈트레벨 == 3 ? 1 : 0); break;			
		case ItemId.축복_룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 ? 12 : 인챈트레벨 == 8 ? 9 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 ? 3 : 인챈트레벨 == 4 ? 2 : 인챈트레벨 == 3 ? 1 : 0); break;
		case ItemId.스냅퍼_용사_반지: case ItemId.스냅퍼_체력_반지: case ItemId.스냅퍼_마법저항_반지: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0); break;
		case ItemId.축복_스냅퍼_용사_반지: case ItemId.축복_스냅퍼_체력_반지: case ItemId.축복_스냅퍼_마법저항_반지: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 4 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 ? 1 : 0); break;
		case ItemId.화룡_티셔츠: case ItemId.축복_화룡_티셔츠: result += (인챈트레벨 == 10 || 인챈트레벨 == 9 ? 1 : 0); break;
		}
		if ((아이템타입 == L1Item.CATEGORY_ARMOR)){
			result += getItem().getMeleeDmg();
		}
		if(getBlessType() == BlessTypeEnchant.Dmg) {	
			result += getBlessTypeValue();
		}
		return result;
	}
	/**
	 * 무기 근거리 데미지 처리 
	 * @return
	 */
	public int getWeaponMeleeDmg() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType();

		if (아이템타입 == L1Item.CATEGORY_WEAPON && (아이템종류 != L1Item.TYPE_WEAPON_BOW 
				&& 아이템종류 != L1Item.TYPE_WEAPON_GAUNTLET && 아이템종류 != L1Item.TYPE_WEAPON_SINGLEBOW)) {
			result += getItem().getDmgModifier();
		} 
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getDmgModifier(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.Dmg) {	
			result += getBlessTypeValue();
		}
		return result;
	}

	//-- 근거리 명중
	public int getHitModifier() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();
		if (아이템타입 == L1Item.CATEGORY_WEAPON && (아이템종류 != L1Item.TYPE_WEAPON_BOW 
				&& 아이템종류 != L1Item.TYPE_WEAPON_GAUNTLET && 아이템종류 != L1Item.TYPE_WEAPON_SINGLEBOW)) {
			result += getItem().getHitModifier(); // 무기에 포함된 근거리
		} 
		switch (아이템아이디) {
		case ItemId.룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 1 : 0); break;			
		case ItemId.축복_룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 1 :0); break;
		case ItemId.스냅퍼_용사_반지: result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3  : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0); break;
		case ItemId.축복_스냅퍼_용사_반지: result += (인챈트레벨 == 9 ? 9 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 4  : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 2 : 인챈트레벨 == 4 ? 1 : 0); break;
		case ItemId.화룡_티셔츠: result += (인챈트레벨 == 10 ? 6 : 인챈트레벨 == 9 ? 4 : 인챈트레벨 == 8 ? 2 : 0); break;
		case ItemId.축복_화룡_티셔츠: result += (인챈트레벨 == 10 ? 7 : 인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 1 : 0); break;
		}
		if ((아이템타입 == L1Item.CATEGORY_ARMOR)){
			result += getItem().getHitRate();
		}
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getHitModifier(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.Hit) {
			result += getBlessTypeValue();
		}
		return result;
	}
	//-- 데미지 리덕션 무시 
	public int getReductionCancel() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템아이디 = getItemId();

		if (아이템타입 == L1Item.CATEGORY_ARMOR || 아이템타입 == L1Item.CATEGORY_WEAPON) {
			result += getItem().getDmgReductionCancel();
		}	
		if (아이템아이디 == ItemId.발라카스_완력 || 아이템아이디 == ItemId.발라카스_인내력 || 아이템아이디 == ItemId.발라카스_예지력 || 아이템아이디 == ItemId.발라카스_마력) {
			result += (인챈트레벨 >= 9 ? 3 : 인챈트레벨 == 8 ? 2 : 인챈트레벨 == 7 ? 1 : 0);
		}	
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getReductionCancel(인챈트레벨);
		}
		return result;
	}
	public int getAcByEnchantLevel() {
		int result = 0, 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();

		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(!(아이템종류 == L1Item.TYPE_ARMOR_RING) && !(아이템종류 == L1Item.TYPE_ARMOR_RING_TWO) && !(아이템종류 == L1Item.TYPE_ARMOR_BELT)) {
					if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_AMULET || 아이템종류 == L1Item.TYPE_ARMOR_EARRING) { // 목걸이, 귀걸이 일경우 
						result += (인챈트레벨 == 9 ? 5 :  인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0);	
					} else {
						result = getEnchantLevel();
					}
				}		
			}	
		} else {
			result = getEnchantLevel();
		}
		switch (아이템아이디) {
		case ItemId.스냅퍼_용사_반지: result += (인챈트레벨 == 9 ? 8 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 || 인챈트레벨 == 6 || 인챈트레벨 == 5 || 인챈트레벨 == 4 ? 4 : 인챈트레벨 == 3 ? 3 : 인챈트레벨 == 2 ? 2 : 인챈트레벨 == 1 ? 1 : 0); break;					
		case ItemId.축복_스냅퍼_용사_반지: result += (인챈트레벨 == 9 ? 10 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 || 인챈트레벨 == 5 || 인챈트레벨 == 4 ? 4 : 인챈트레벨 == 3 ? 3 : 인챈트레벨 == 2 ? 2 : 인챈트레벨 == 1 ? 1 : 0); break;
		case ItemId.룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 10 : 인챈트레벨 == 8 ? 9 : 인챈트레벨 == 7 ? 8 : 인챈트레벨 == 6 ? 7 : 0); break;
		case ItemId.축복_룸티스_붉은빛_귀걸이: result += (인챈트레벨 == 9 ? 11 : 인챈트레벨 == 8 ? 10 : 인챈트레벨 == 7 ? 9 : 인챈트레벨 == 6 ? 8 : 인챈트레벨 == 5 ? 7 : 0); break;
		case ItemId.축복_룸티스_검은빛_귀걸이: result += (인챈트레벨 == 9 ? 10 : 인챈트레벨 == 8 ? 9 : 인챈트레벨 == 7 ? 8  : 인챈트레벨 == 6 ? 7 : 인챈트레벨 == 5 ? 6 : 인챈트레벨 == 4 ? 5 : 인챈트레벨 == 3 ? 4 : 인챈트레벨 == 2 ? 2 : 인챈트레벨 == 1 ? 1: 0); break;
		case ItemId.룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 || 인챈트레벨 == 8 ? 3 : 인챈트레벨 == 7 ? 2 : 인챈트레벨 == 6 ? 1 : 0); break;
		case ItemId.축복_룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 || 인챈트레벨 == 8 ? 4 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 2 : 인챈트레벨 == 5 ? 1 : 0); break;
		case ItemId.스냅퍼_마법저항_반지: result += (인챈트레벨 == 9 ? 8 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 || 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 || 인챈트레벨 == 4 ? 3 : 인챈트레벨 == 3 ? 2 : 인챈트레벨 == 2 ? 1: 0); break;
		case ItemId.축복_스냅퍼_마법저항_반지: result += (인챈트레벨 == 9 ? 10  : 인챈트레벨 == 8 ? 7  : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 || 인챈트레벨 == 5 ? 4 : 인챈트레벨 == 4 ? 3 : 인챈트레벨 == 3 ? 2 : 인챈트레벨 == 2 ? 1: 0); break;
		case ItemId.스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 8 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 || 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 || 인챈트레벨 == 4 ? 3 : 인챈트레벨 == 3 ? 2 : 인챈트레벨 == 2 ? 1: 0); break;
		case ItemId.축복_스냅퍼_지혜_반지: result += (인챈트레벨 == 9 ? 10  : 인챈트레벨 == 8 ? 7  : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 || 인챈트레벨 == 4 ? 3 : 인챈트레벨 == 3 ? 2 : 인챈트레벨 == 2 ? 1: 0); break;
		case ItemId.스냅퍼_체력_반지: result += (인챈트레벨 == 9 ? 8 : 인챈트레벨 == 8 ? 6 : 인챈트레벨 == 7 || 인챈트레벨 == 6 ? 4 : 인챈트레벨 == 5 || 인챈트레벨 == 4 ? 3 : 인챈트레벨 == 3 ? 2 : 인챈트레벨 == 2 ? 1: 0); break;
		case ItemId.축복_스냅퍼_체력_반지: result += (인챈트레벨 == 9 ? 10  : 인챈트레벨 == 8 ? 7  : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ||인챈트레벨 == 5 ? 4 : 인챈트레벨 == 4 ? 3 : 인챈트레벨 == 3 ? 2 : 인챈트레벨 == 2 ? 1: 0); break;
		}	
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getAc(인챈트레벨);
		}
		if(getBlessType() == BlessTypeEnchant.Ac) {
			result += getBlessTypeValue();
		}

		return result;
	}
	public int getAc() {
		int result = _item.get_ac();
		return result;
	}

	public boolean isCanbeDmg() {
		boolean result = getItem().get_canbedmg() == 0 ? true : false; 
		return result;
	}
	// 마방
	public int getMr() {
		int result = _item.get_mdef(), 인챈트레벨 = getEnchantLevel(), 아이템타입 = getItem().getType2(), 아이템종류 = getItem().getType(), 아이템아이디 = getItemId();

		// 오림 악세 인첸트 
		if(!(아이템타입 == L1Item.CATEGORY_WEAPON)) {
			if(!(cashItems(아이템아이디))) { // 캐쉬 아이템이 아닐때 
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_AMULET) { 
					result += (인챈트레벨 == 9 ? 10 : 인챈트레벨 == 8 ? 7 : 인챈트레벨 == 7 ? 5 : 인챈트레벨 == 6 ? 3 : 인챈트레벨 == 5 ? 1 : 0);
				}
				if(아이템타입 == L1Item.CATEGORY_ARMOR && 아이템종류 == L1Item.TYPE_ARMOR_RING || 아이템종류 == L1Item.TYPE_ARMOR_RING_TWO) { 
					result += (인챈트레벨 == 9 ? 7 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 3 : 인챈트레벨 == 6 ? 1 : 0);
				}
			}	
		}	
		switch (아이템아이디) {
		case ItemId.머미_로드_왕관: result += (인챈트레벨 >= 10 ? 18 : 인챈트레벨 == 9 ? 15 : 인챈트레벨 == 8 ? 12 : 인챈트레벨 == 7 ? 9 : 인챈트레벨 == 6 ? 6 : 인챈트레벨 == 5 ? 3 : 0); break;
		case ItemId.스냅퍼_마법저항_반지: result += (인챈트레벨 == 9 ? 5 : 인챈트레벨 == 8 ? 2 : 0); break;					
		case ItemId.축복_스냅퍼_마법저항_반지: result += (인챈트레벨 == 9 ? 8 : 인챈트레벨 == 8 ? 5 : 인챈트레벨 == 7 ? 2 : 인챈트레벨 == 6 ? 1 : 0); break;
		case ItemId.룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 18 :인챈트레벨 == 8 ? 15 : 인챈트레벨 == 7 ? 10 : 인챈트레벨 == 6 ? 8 : 인챈트레벨 == 5 ? 7 : 인챈트레벨 == 4 ? 6 : 인챈트레벨 == 3 ? 5 : 인챈트레벨 == 2 ? 4 : 인챈트레벨 == 1 ? 3 : 0); break;
		case ItemId.축복_룸티스_보랏빛_귀걸이: result += (인챈트레벨 == 9 ? 21 : 인챈트레벨 == 8 ? 18 : 인챈트레벨 == 7 ? 13 : 인챈트레벨 == 6 ? 10 : 인챈트레벨 == 5 ? 8 : 인챈트레벨 == 4 ? 7 : 인챈트레벨 == 3 ? 6 : 인챈트레벨 == 2 ? 4 : 인챈트레벨 == 1 ? 3 : 0); break;
		case ItemId.지룡_티셔츠: case ItemId.축복_지룡_티셔츠: result += (인챈트레벨 == 10 ? 14 : 인챈트레벨 == 9 ? 11 : 인챈트레벨 == 8 ? 8 : 인챈트레벨 == 7 ? 6 : 인챈트레벨 == 6 ? 5 : 인챈트레벨 == 5 ? 4 : 0); break;
		}	
		// 인챈트 보너스 아이템 
		L1EnchantBonus eb = L1EnchantBonus.get(getItemId());
		if (eb != null) {
			result += eb.getMr(인챈트레벨);
		}
		// 블레스 타입 인챈트 
		if(getBlessType() == BlessTypeEnchant.Mr) {
			result += getBlessTypeValue();
		}
		return result;
	}
	public boolean isAccessory() {
		return getItem().getType2() == L1Item.CATEGORY_ARMOR && (getItem().getType() == L1Item.TYPE_ARMOR_AMULET 
				|| getItem().getType() == L1Item.TYPE_ARMOR_RING || getItem().getType() == L1Item.TYPE_ARMOR_BELT
				|| getItem().getType() == L1Item.TYPE_ARMOR_RING_TWO || getItem().getType() == L1Item.TYPE_ARMOR_EARRING);
	}
	public boolean isIvoryTowerArmor(int itemId) {
		return getItem().getType2() == L1Item.CATEGORY_ARMOR && (itemId == ItemId.상아탑_가죽_투구 || itemId == ItemId.상아탑_티셔츠
				|| itemId == ItemId.상아탑_가죽_갑옷 || itemId == ItemId.상아탑_가죽_장갑 || itemId == ItemId.상아탑_가죽_샌달 || itemId == ItemId.상아탑_가죽_방패);
	}		
	public boolean isIvoryTowerWeapon(int itemId) {
		return getItem().getType2() == L1Item.CATEGORY_WEAPON && (itemId == ItemId.상아탑_단검 || itemId == ItemId.상아탑_한손검
				|| itemId == ItemId.상아탑_양손검 || itemId == ItemId.상아탑_이도류 || itemId == ItemId.상아탑_창 || itemId == ItemId.상아탑_지팡이
				|| itemId == ItemId.상아탑_도끼 || itemId == ItemId.상아탑_크로우 || itemId == ItemId.상아탑_석궁 || itemId == ItemId.상아탑_활);
	}
	public boolean isDragonT(int itemId) {
		return getItem().getType2() == L1Item.CATEGORY_ARMOR && (itemId == ItemId.지룡_티셔츠 || itemId == ItemId.화룡_티셔츠
				|| itemId == ItemId.풍룡_티셔츠 || itemId == ItemId.수룡_티셔츠 || itemId == ItemId.축복_지룡_티셔츠 || itemId == ItemId.축복_화룡_티셔츠 
				|| itemId == ItemId.축복_풍룡_티셔츠 || itemId == ItemId.축복_수룡_티셔츠);
	}	
	public boolean isSnapper(int itemId) {
		return getItem().getType2() == L1Item.CATEGORY_ARMOR
				&& (itemId == ItemId.스냅퍼_용사_반지 || itemId == ItemId.축복_스냅퍼_용사_반지 || itemId == ItemId.스냅퍼_체력_반지 || itemId == ItemId.축복_스냅퍼_체력_반지
				|| itemId == ItemId.스냅퍼_마법저항_반지 || itemId == ItemId.축복_스냅퍼_마법저항_반지 || itemId == ItemId.스냅퍼_지혜_반지 || itemId == ItemId.축복_스냅퍼_지혜_반지);
	}
	public boolean isRoontis(int itemId) {
		return getItem().getType2() == L1Item.CATEGORY_ARMOR && (itemId == ItemId.룸티스_검은빛_귀걸이 || itemId == ItemId.룸티스_붉은빛_귀걸이
				|| itemId == ItemId.룸티스_보랏빛_귀걸이 || itemId == ItemId.축복_룸티스_검은빛_귀걸이 || itemId == ItemId.축복_룸티스_붉은빛_귀걸이 || itemId == ItemId.축복_룸티스_보랏빛_귀걸이);
	}

	public boolean cashItems(int itemId) {
		return getItem().getType2() == L1Item.CATEGORY_ARMOR && itemId == ItemId.룸티스_검은빛_귀걸이 || itemId == ItemId.룸티스_붉은빛_귀걸이
				|| itemId == ItemId.룸티스_보랏빛_귀걸이 || itemId == ItemId.스냅퍼_용사_반지 || itemId == ItemId.축복_룸티스_검은빛_귀걸이
				|| itemId == ItemId.축복_룸티스_붉은빛_귀걸이 || itemId == ItemId.축복_룸티스_보랏빛_귀걸이 || itemId == ItemId.축복_스냅퍼_용사_반지
				|| itemId == ItemId.스냅퍼_체력_반지 || itemId == ItemId.축복_스냅퍼_체력_반지 || itemId == ItemId.스냅퍼_마법저항_반지
				|| itemId == ItemId.축복_스냅퍼_마법저항_반지 || itemId == ItemId.스냅퍼_지혜_반지 || itemId == ItemId.축복_스냅퍼_지혜_반지;			
	}
	// 손상도 
	public void setDurability(int i) {
		if (i < 0) {
			i = 0;
		}
		if (i > 127) {
			i = 127;
		}
		_durability = i;
	}

	/**
	 * 무게 증가 
	 * @return
	 */
	public int getWeight() {
		if (getItem().getWeight() == 0) {
			return 0;
		} else {
			return Math.max(getCount() * getItem().getWeight() / 1000, 1);
		}
	}
	private boolean m_is_give = false;

	public boolean isGiveItem() {
		return m_is_give;
	}

	public void setGiveItem(boolean is_give) {
		m_is_give = is_give;
	}

	private int _owner_id = 0;

	public int getOwnerId() {
		return _owner_id;
	}

	public void setOwnerId(int value) {
		_owner_id = value;
	}
}