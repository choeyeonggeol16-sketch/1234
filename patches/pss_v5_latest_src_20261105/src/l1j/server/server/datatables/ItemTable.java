package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import gnu.trove.TCollections;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.MJTemplate.FindItemTable.TableFindItemFilter;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItem;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1RaceTicket;
import l1j.server.server.templates.L1Weapon;
import l1j.server.server.utils.eKind;

public class ItemTable {
	private static final Logger _log = Logger.getLogger(ItemTable.class.getName());

	private TIntObjectMap<L1Item> _allTemplates;
	public final TIntObjectMap<L1EtcItem> _etcitems;
	public final TIntObjectMap<L1Weapon> _weapons;
	public final TIntObjectMap<L1Armor> _armors;
	public final TIntObjectMap<List<L1Item>> _descCached;
	private final TIntObjectMap<L1RaceTicket> _race = new TIntObjectHashMap<L1RaceTicket>(64);
	
	private Map<String, List<L1Item>> _nameToItemsMap;
	private Map<String, List<L1Item>> _nameWithoutSpaceToItemsMap;

	public TIntObjectMap<L1Item> getAllTemplates() {
		return _allTemplates;
	}

	private static class SingletonHolder {
		private static ItemTable INSTANCE = new ItemTable();
	}

	public static ItemTable getInstance() {
		return SingletonHolder.INSTANCE;
	}

	public static void reload() {
		SingletonHolder.INSTANCE = new ItemTable();
	}

	private ItemTable() {
		_etcitems = allEtcItem();
		_weapons = allWeapon();
		_armors = allArmor();
		_descCached = TCollections.synchronizedMap(new TIntObjectHashMap<List<L1Item>>(64));
		buildFastLookupTable();
	}

	private TIntObjectMap<L1EtcItem> allEtcItem() {
		TIntObjectMap<L1EtcItem> result = new TIntObjectHashMap<L1EtcItem>(3072);
		String sql = "SELECT * FROM etcitem";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1EtcItem item = new L1EtcItem();
				item.setItemId(rs.getInt("item_id"));
				item.setName(rs.getString("name"));
				item.setNameId(rs.getString("name_id"));
				item.setType(parseEtcType(rs.getString("item_type")));
				item.setUseType(parseUseType(rs.getString("use_type")));
				// PSS settings item uses the existing immediate self-use interaction.
				if (item.getItemId() == 900000901) item.setUseType(30);
				item.setType2(0);
				item.setMaterial(parseMaterial(rs.getString("material")));
				item.setWeight(rs.getInt("weight"));
				item.setGfxId(rs.getInt("invgfx"));
				item.setGroundGfxId(rs.getInt("grdgfx"));
				item.setItemDescId(rs.getInt("itemdesc_id"));
				item.setMinLevel(rs.getInt("min_lvl"));
				item.setMaxLevel(rs.getInt("max_lvl"));
				item.setBless(rs.getInt("bless"));
				item.setTradable(rs.getInt("trade") == 0);
				item.setCantDelete(rs.getInt("cant_delete") == 1);
				item.setHitModifier(rs.getInt("hit"));
				item.setDmgModifier(rs.getInt("dmg"));
				item.set_stackable(rs.getInt("stackable") == 1);
				item.setMaxChargeCount(rs.getInt("max_charge_count"));
				item.set_locx(rs.getInt("locx"));
				item.set_locy(rs.getInt("locy"));
				item.set_mapid(rs.getShort("mapid"));
				item.set_delayid(rs.getInt("delay_id"));
				item.set_delaytime(rs.getInt("delay_time"));
				item.set_delayEffect(rs.getInt("delay_effect"));
				item.setFoodVolume(rs.getInt("food_volume"));
				item.setToBeSavedAtOnce(rs.getInt("save_at_once") == 1);
				item.setEndedTimeMessage(rs.getBoolean("isEndedTimeMessage"));

				if (Config.IS_VALIDITEMID) {
					if (_weapons != null && _weapons.containsKey(item.getItemId()))
						System.out.println(String.format("ETCITEM %d(%s)이 무기와 겹칩니다.", item.getItemId(), item.getName()));
					if (_armors != null && _armors.containsKey(item.getItemId()))
						System.out.println(String.format("ETCITEM %d(%s)이 방어구와 겹칩니다.", item.getItemId(), item.getName()));
				}
				result.put(item.getItemId(), item);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ItemTable allEtcItem error", e);
		}
		return result;
	}

	private TIntObjectMap<L1Weapon> allWeapon() {
		TIntObjectMap<L1Weapon> result = new TIntObjectHashMap<L1Weapon>(512);
		String sql = "SELECT * FROM weapon";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1Weapon weapon = new L1Weapon();
				weapon.setItemId(rs.getInt("item_id"));
				weapon.setName(rs.getString("name"));
				weapon.setNameId(rs.getString("name_id"));
				weapon.setType(parseWeaponType(rs.getString("type")));
				weapon.setType1(parseWeaponId(rs.getString("type")));
				weapon.setType2(1);
				weapon.setUseType(1);
				weapon.setMaterial(parseMaterial(rs.getString("material")));
				weapon.setWeight(rs.getInt("weight"));
				weapon.setGfxId(rs.getInt("invgfx"));
				weapon.setGroundGfxId(rs.getInt("grdgfx"));
				weapon.setItemDescId(rs.getInt("itemdesc_id"));
				weapon.setDmgSmall(rs.getInt("dmg_small"));
				weapon.setDmgLarge(rs.getInt("dmg_large"));
				weapon.set_safeenchant(rs.getInt("safenchant"));
				weapon.setUseRoyal(rs.getInt("use_royal") != 0);
				weapon.setUseKnight(rs.getInt("use_knight") != 0);
				weapon.setUseElf(rs.getInt("use_elf") != 0);
				weapon.setUseMage(rs.getInt("use_mage") != 0);
				weapon.setUseDarkelf(rs.getInt("use_darkelf") != 0);
				weapon.setUseDragonKnight(rs.getInt("use_dragonknight") != 0);
				weapon.setUseBlackwizard(rs.getInt("use_blackwizard") != 0);
				weapon.setUse전사(rs.getInt("use_warrior") != 0);
				weapon.setHitModifier(rs.getInt("hitmodifier"));
				weapon.setDmgModifier(rs.getInt("dmgmodifier"));
				weapon.set_addstr(rs.getByte("add_str"));
				weapon.set_adddex(rs.getByte("add_dex"));
				weapon.set_addcon(rs.getByte("add_con"));
				weapon.set_addint(rs.getByte("add_int"));
				weapon.set_addwis(rs.getByte("add_wis"));
				weapon.set_addcha(rs.getByte("add_cha"));
				weapon.set_addhp(rs.getInt("add_hp"));
				weapon.set_addmp(rs.getInt("add_mp"));
				weapon.set_addhpr(rs.getInt("add_hpr"));
				weapon.set_addmpr(rs.getInt("add_mpr"));
				weapon.set_addsp(rs.getInt("add_sp"));
				weapon.set_mdef(rs.getInt("m_def"));
				weapon.setDoubleDmgChance(rs.getInt("double_dmg_chance"));
				weapon.setMagicDmgModifier(rs.getInt("magicdmgmodifier"));
				weapon.set_canbedmg(rs.getInt("canbedmg"));
				weapon.setMinLevel(rs.getInt("min_lvl"));
				weapon.setMaxLevel(rs.getInt("max_lvl"));
				weapon.setBless(rs.getInt("bless"));
				weapon.setTradable(rs.getInt("trade") == 0);
				weapon.setCantDelete(rs.getInt("cant_delete") == 1);
				weapon.setHasteItem(rs.getInt("haste_item") != 0);
				weapon.setMaxUseTime(rs.getInt("max_use_time"));
				weapon.setMagicName(rs.getString("Magic_name"));
				weapon.setEndedTimeMessage(rs.getBoolean("isEndedTimeMessage"));
				weapon.set_missile_critical_probability(rs.getInt("missile_critical_probability"));
				weapon.set_melee_critical_probability(rs.getInt("melee_critical_probability"));

				if (Config.IS_VALIDITEMID) {
					if (_etcitems != null && _etcitems.containsKey(weapon.getItemId()))
						System.out.println(String.format("무기 %d(%s)이 etcItem과 겹칩니다.", weapon.getItemId(), weapon.getName()));
					if (_armors != null && _armors.containsKey(weapon.getItemId()))
						System.out.println(String.format("무기 %d(%s)이 방어구와 겹칩니다.", weapon.getItemId(), weapon.getName()));
				}

				result.put(weapon.getItemId(), weapon);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ItemTable allWeapon error", e);
		}
		return result;
	}

	private TIntObjectMap<L1Armor> allArmor() {
		TIntObjectMap<L1Armor> result = new TIntObjectHashMap<L1Armor>(1024);
		String sql = "SELECT * FROM armor";

		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement pstm = con.prepareStatement(sql);
			 ResultSet rs = pstm.executeQuery()) {

			while (rs.next()) {
				L1Armor armor = new L1Armor();
				armor.setItemId(rs.getInt("item_id"));
				armor.setName(rs.getString("name"));
				armor.setNameId(rs.getString("name_id"));
				armor.setType(parseArmorType(rs.getString("type")));
				armor.setType2(2);
				armor.setUseType(parseUseType(rs.getString("type")));
				armor.set장신구처리(rs.getInt("acctype"));
				armor.setGrade(rs.getInt("grade"));
				armor.setMaterial(parseMaterial(rs.getString("material")));
				armor.setWeight(rs.getInt("weight"));
				armor.setGfxId(rs.getInt("invgfx"));
				armor.setGroundGfxId(rs.getInt("grdgfx"));
				armor.setItemDescId(rs.getInt("itemdesc_id"));
				armor.set_ac(rs.getInt("ac"));
				armor.set_safeenchant(rs.getInt("safenchant"));
				armor.setUseRoyal(rs.getInt("use_royal") != 0);
				armor.setUseKnight(rs.getInt("use_knight") != 0);
				armor.setUseElf(rs.getInt("use_elf") != 0);
				armor.setUseMage(rs.getInt("use_mage") != 0);
				armor.setUseDarkelf(rs.getInt("use_darkelf") != 0);
				armor.setUseDragonKnight(rs.getInt("use_dragonknight") != 0);
				armor.setUseBlackwizard(rs.getInt("use_blackwizard") != 0);
				armor.setUse전사(rs.getInt("use_warrior") != 0);
				armor.set_addstr(rs.getByte("add_str"));
				armor.set_addcon(rs.getByte("add_con"));
				armor.set_adddex(rs.getByte("add_dex"));
				armor.set_addint(rs.getByte("add_int"));
				armor.set_addwis(rs.getByte("add_wis"));
				armor.set_addcha(rs.getByte("add_cha"));
				armor.set_addhp(rs.getInt("add_hp"));
				armor.set_addmp(rs.getInt("add_mp"));
				armor.set_addhpr(rs.getInt("add_hpr"));
				armor.set_addmpr(rs.getInt("add_mpr"));
				armor.set_addsp(rs.getInt("add_sp"));
				armor.set_addeinhasadper(rs.getInt("add_einhasadper"));
				armor.setMinLevel(rs.getInt("min_lvl"));
				armor.setMaxLevel(rs.getInt("max_lvl"));
				armor.set_mdef(rs.getInt("m_def"));
				armor.setWeightReduction(rs.getInt("weight_reduction"));
				armor.set_damage_reduction(rs.getInt("damage_reduction"));
				armor.setHitRate(rs.getInt("hit_rate"));
				armor.setDmgRate(rs.getInt("dmg_rate"));
				armor.setBowHitRate(rs.getInt("bow_hit_rate"));
				armor.setBowDmgRate(rs.getInt("bow_dmg_rate"));
				armor.setHasteItem(rs.getInt("haste_item") != 0);
				armor.setBless(rs.getInt("bless"));
				armor.setTradable(rs.getInt("trade") == 0);
				armor.setCantDelete(rs.getInt("cant_delete") == 1);
				armor.set_defense_earth(rs.getInt("defense_earth"));
				armor.set_defense_water(rs.getInt("defense_water"));
				armor.set_defense_wind(rs.getInt("defense_wind"));
				armor.set_defense_fire(rs.getInt("defense_fire"));
				
                armor.getSpecialStats().putResist(eKind.ABILITY.value(), rs.getInt("ability_resis"));
                armor.getSpecialStats().putPierce(eKind.ABILITY.value(), rs.getInt("ability_pierce"));
                armor.getSpecialStats().putResist(eKind.SPIRIT.value(), rs.getInt("spirit_resis"));
                armor.getSpecialStats().putPierce(eKind.SPIRIT.value(), rs.getInt("spirit_pierce"));
                armor.getSpecialStats().putResist(eKind.DRAGON_SPELL.value(), rs.getInt("dragonS_resis"));
                armor.getSpecialStats().putPierce(eKind.DRAGON_SPELL.value(), rs.getInt("dragonS_pierce"));
                armor.getSpecialStats().putResist(eKind.FEAR.value(), rs.getInt("fear_resis"));
                armor.getSpecialStats().putPierce(eKind.FEAR.value(), rs.getInt("fear_pierce"));
                armor.getSpecialStats().putResist(eKind.ALL.value(), rs.getInt("all_resis"));
                armor.getSpecialStats().putPierce(eKind.ALL.value(), rs.getInt("all_pierce"));

				armor.set_regist_calcPcDefense(rs.getInt("PVPcalcPcDefense"));
				armor.set_regist_PVPweaponTotalDamage(rs.getInt("PVPweaponTotalDamage"));
				armor.setMaxUseTime(rs.getInt("max_use_time"));
				armor.setMagicName(rs.getString("Magic_name"));
				armor.setEndedTimeMessage(rs.getBoolean("isEndedTimeMessage"));
				armor.set_missile_critical_probability(rs.getInt("missile_critical_probability"));
				armor.set_melee_critical_probability(rs.getInt("melee_critical_probability"));
				armor.setMagicHitup(rs.getInt("magic_hit_up"));

				if (Config.IS_VALIDITEMID) {
					if (_etcitems != null && _etcitems.containsKey(armor.getItemId()))
						System.out.println(String.format("방어구 %d(%s)이 etcItem과 겹칩니다.", armor.getItemId(), armor.getName()));
					if (_weapons != null && _weapons.containsKey(armor.getItemId()))
						System.out.println(String.format("방어구 %d(%s)이 무기와 겹칩니다.", armor.getItemId(), armor.getName()));
				}

				result.put(armor.getItemId(), armor);
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, "ItemTable allArmor error", e);
		}
		return result;
	}

	public void initRace() {
		String[] queries = {
			"DELETE FROM character_items WHERE item_id > 8000000 AND item_id < 120000000",
			"DELETE FROM character_elf_warehouse WHERE item_id > 8000000 AND item_id < 120000000",
			"DELETE FROM character_warehouse WHERE item_id > 8000000 AND item_id < 120000000",
			"DELETE FROM clan_warehouse WHERE item_id > 8000000 AND item_id < 120000000",
			"DELETE FROM character_package_warehouse WHERE item_id > 8000000 AND item_id < 120000000"
		};

		try (Connection con = DatabaseFactory.getInstance().getConnection()) {
			for (String sql : queries) {
				try (PreparedStatement pstm = con.prepareStatement(sql)) {
					pstm.execute();
				}
			}
		} catch (Exception e) {
			_log.log(Level.SEVERE, "ItemTable initRace error", e);
		}
	}

	private void buildFastLookupTable() {
		_allTemplates = new TIntObjectHashMap<L1Item>(_etcitems.size() + _weapons.size() + _armors.size() + _race.size());
		_allTemplates.putAll(_etcitems);
		_allTemplates.putAll(_weapons);
		_allTemplates.putAll(_armors);
		_allTemplates.putAll(_race);
		
		_nameToItemsMap = new HashMap<>();
		_nameWithoutSpaceToItemsMap = new HashMap<>();
		
		int[] keys = _allTemplates.keys();
		for (int key : keys) {
			L1Item item = _allTemplates.get(key);
			if (item == null || item.getName() == null)
				continue;
			
			_nameToItemsMap.computeIfAbsent(item.getName(), k -> new ArrayList<>()).add(item);
			
			String noSpaceKey = item.getName().replace(" ", "").toLowerCase();
			_nameWithoutSpaceToItemsMap.computeIfAbsent(noSpaceKey, k -> new ArrayList<>()).add(item);
		}
	}

	public L1Item getTemplate(int id) {
		return _allTemplates.get(id);
	}

	public L1ItemInstance createItem(int itemId) {
		return createItem(itemId, true);
	}

	public L1ItemInstance createItem(int itemId, boolean identified) {
		L1Item temp = getTemplate(itemId);
		if (temp == null) {
			return null;
		}
		L1ItemInstance item = new L1ItemInstance();
		item.setId(IdFactory.getInstance().nextId());
		item.setItem(temp);
		item.setBless(temp.getBless());
		item.setIdentified(identified);

		L1World.getInstance().storeObject(item);
		return item;
	}

	public L1ItemInstance createItem(L1Item temp) {
		if (temp == null) return null;
		L1ItemInstance item = new L1ItemInstance();
		item.setId(IdFactory.getInstance().nextId());
		item.setItem(temp);
		item.setBless(temp.getBless());
		L1World.getInstance().storeObject(item);
		return item;
	}

	public ArrayList<L1Item> findItemByName(String name) {
		if (name == null || _nameToItemsMap == null)
			return new ArrayList<>(0);
		List<L1Item> list = _nameToItemsMap.get(name);
		return list != null ? new ArrayList<>(list) : new ArrayList<>(0);
	}

	public L1ItemInstance createItem(String itemName) {
		int itemId = findItemIdByName(itemName);
		L1Item temp = getTemplate(itemId);
		if (temp == null) {
			return null;
		}
		L1ItemInstance item = new L1ItemInstance();
		item.setId(IdFactory.getInstance().nextId());
		item.setItem(temp);
		item.setBless(temp.getBless());
		L1World.getInstance().storeObject(item);
		return item;
	}

	public void AddTicket(L1RaceTicket race) {
		_race.put(race.getItemId(), race);
		_allTemplates.put(race.getItemId(), race);
	}

	public int GetIssuedTicket() {
		return _race.size();
	}

	public L1Item create_dogfight_ticket(L1Item item, int new_item_id, String name) {
		L1EtcItem etc = new L1EtcItem();
		etc.setItemId(new_item_id);
		etc.setName(name);
		etc.setNameId(name);
		etc.setType(item.getType());
		etc.setUseType(item.getUseType());
		etc.setType2(0);
		etc.setMaterial(item.getMaterial());
		etc.setWeight(item.getWeight());
		etc.setGfxId(item.getGfxId());
		etc.setGroundGfxId(item.getGroundGfxId());
		etc.setItemDescId(item.getItemDescId());
		etc.setMinLevel(item.getMinLevel());
		etc.setMaxLevel(item.getMaxLevel());
		etc.setBless(item.getBless());
		etc.setTradable(item.isTradable());
		etc.setCantDelete(item.isCantDelete());
		etc.setDmgSmall(item.getDmgSmall());
		etc.setDmgLarge(item.getDmgLarge());
		etc.set_stackable(item.isStackable());
		etc.setMaxChargeCount(item.getMaxChargeCount());
		etc.set_locx(item.get_locx());
		etc.set_locy(item.get_locy());
		etc.set_mapid(item.get_mapid());
		etc.set_delayid(item.get_delayid());
		etc.set_delaytime(item.get_delaytime());
		etc.set_delayEffect(item.get_delayEffect());
		etc.setFoodVolume(item.getFoodVolume());
		etc.setToBeSavedAtOnce(item.isToBeSavedAtOnce());
		etc.set_missile_critical_probability(item.get_missile_critical_probability());
		etc.set_melee_critical_probability(item.get_melee_critical_probability());
		etc.setHitModifier(item.getHitModifier());
		etc.setDmgModifier(item.getDmgModifier());
		_allTemplates.put(new_item_id, etc);
		_etcitems.put(new_item_id, etc);
		return etc;
	}

	public boolean findItemId(int id) {
		return _allTemplates.containsKey(id);
	}

	public static int parseWeaponId(String s) {
		if (s.equalsIgnoreCase("sword")) return 4;
		else if (s.equalsIgnoreCase("dagger")) return 46;
		else if (s.equalsIgnoreCase("tohandsword")) return 50;
		else if (s.equalsIgnoreCase("bow")) return 20;
		else if (s.equalsIgnoreCase("spear")) return 24;
		else if (s.equalsIgnoreCase("blunt")) return 11;
		else if (s.equalsIgnoreCase("staff")) return 40;
		else if (s.equalsIgnoreCase("throwingknife")) return 2922;
		else if (s.equalsIgnoreCase("arrow")) return 66;
		else if (s.equalsIgnoreCase("gauntlet")) return 62;
		else if (s.equalsIgnoreCase("claw")) return 58;
		else if (s.equalsIgnoreCase("edoryu")) return 54;
		else if (s.equalsIgnoreCase("singlebow")) return 20;
		else if (s.equalsIgnoreCase("singlespear")) return 24;
		else if (s.equalsIgnoreCase("tohandblunt")) return 11;
		else if (s.equalsIgnoreCase("tohandstaff")) return 40;
		else if (s.equalsIgnoreCase("keyring")) return 58;
		else if (s.equalsIgnoreCase("chainsword")) return 83;
		System.out.println(String.format("invalid item weapon Id %s", s));
		return 0;
	}

	public static int parseWeaponType(String s) {
		if (s.equalsIgnoreCase("sword")) return 1;
		else if (s.equalsIgnoreCase("dagger")) return 2;
		else if (s.equalsIgnoreCase("tohandsword")) return 3;
		else if (s.equalsIgnoreCase("bow")) return 4;
		else if (s.equalsIgnoreCase("spear")) return 5;
		else if (s.equalsIgnoreCase("blunt")) return 6;
		else if (s.equalsIgnoreCase("staff")) return 7;
		else if (s.equalsIgnoreCase("throwingknife")) return 8;
		else if (s.equalsIgnoreCase("arrow")) return 9;
		else if (s.equalsIgnoreCase("gauntlet")) return 10;
		else if (s.equalsIgnoreCase("claw")) return 11;
		else if (s.equalsIgnoreCase("edoryu")) return 12;
		else if (s.equalsIgnoreCase("singlebow")) return 13;
		else if (s.equalsIgnoreCase("singlespear")) return 14;
		else if (s.equalsIgnoreCase("tohandblunt")) return 15;
		else if (s.equalsIgnoreCase("tohandstaff")) return 16;
		else if (s.equalsIgnoreCase("keyring")) return 17;
		else if (s.equalsIgnoreCase("chainsword")) return 18;
		System.out.println(String.format("invalid item weapon type %s", s));
		return 0;
	}

	public static int parseArmorType(String s) {
		if (s.equalsIgnoreCase("none")) return 0;
		else if (s.equalsIgnoreCase("helm")) return 1;
		else if (s.equalsIgnoreCase("armor")) return 2;
		else if (s.equalsIgnoreCase("T")) return 3;
		else if (s.equalsIgnoreCase("cloak")) return 4;
		else if (s.equalsIgnoreCase("glove")) return 5;
		else if (s.equalsIgnoreCase("boots")) return 6;
		else if (s.equalsIgnoreCase("shield")) return 7;
		else if (s.equalsIgnoreCase("amulet")) return 8;
		else if (s.equalsIgnoreCase("ring")) return 9;
		else if (s.equalsIgnoreCase("belt")) return 10;
		else if (s.equalsIgnoreCase("ring2")) return 11;
		else if (s.equalsIgnoreCase("earring")) return 12;
		else if (s.equalsIgnoreCase("garder")) return 13;
		else if (s.equalsIgnoreCase("ron")) return 14;
		else if (s.equalsIgnoreCase("pair")) return 15;
		else if (s.equalsIgnoreCase("ron2")) return 28;
		else if (s.equalsIgnoreCase("should")) return 29;
		else if (s.equalsIgnoreCase("badge")) return 30;
		System.out.println(String.format("invalid item armor type %s", s));
		return 0;
	}

	public static int parseEtcType(String s) {
		if (s.equalsIgnoreCase("arrow")) return 0;
		else if (s.equalsIgnoreCase("wand")) return 1;
		else if (s.equalsIgnoreCase("light")) return 2;
		else if (s.equalsIgnoreCase("gem")) return 3;
		else if (s.equalsIgnoreCase("totem")) return 4;
		else if (s.equalsIgnoreCase("firecracker")) return 5;
		else if (s.equalsIgnoreCase("potion")) return 6;
		else if (s.equalsIgnoreCase("food")) return 7;
		else if (s.equalsIgnoreCase("scroll")) return 8;
		else if (s.equalsIgnoreCase("questitem")) return 9;
		else if (s.equalsIgnoreCase("spellbook")) return 10;
		else if (s.equalsIgnoreCase("petitem")) return 11;
		else if (s.equalsIgnoreCase("other")) return 12;
		else if (s.equalsIgnoreCase("material")) return 13;
		else if (s.equalsIgnoreCase("event")) return 14;
		else if (s.equalsIgnoreCase("sting")) return 15;
		else if (s.equalsIgnoreCase("treasure_box")) return 16;
		else if (s.equalsIgnoreCase("teleport_scroll")) return 17;
		System.out.println(String.format("invalid item etc type %s", s));
		return -1;
	}

	public static int parseMaterial(String s) {
		if (s.equalsIgnoreCase("none")) return 0;
		else if (s.equalsIgnoreCase("liquid")) return 1;
		else if (s.equalsIgnoreCase("web")) return 2;
		else if (s.equalsIgnoreCase("vegetation")) return 3;
		else if (s.equalsIgnoreCase("animalmatter")) return 4;
		else if (s.equalsIgnoreCase("paper")) return 5;
		else if (s.equalsIgnoreCase("cloth")) return 6;
		else if (s.equalsIgnoreCase("leather")) return 7;
		else if (s.equalsIgnoreCase("wood")) return 8;
		else if (s.equalsIgnoreCase("bone")) return 9;
		else if (s.equalsIgnoreCase("dragonscale")) return 10;
		else if (s.equalsIgnoreCase("iron")) return 11;
		else if (s.equalsIgnoreCase("steel")) return 12;
		else if (s.equalsIgnoreCase("copper")) return 13;
		else if (s.equalsIgnoreCase("silver")) return 14;
		else if (s.equalsIgnoreCase("gold")) return 15;
		else if (s.equalsIgnoreCase("platinum")) return 16;
		else if (s.equalsIgnoreCase("mithril")) return 17;
		else if (s.equalsIgnoreCase("blackmithril")) return 18;
		else if (s.equalsIgnoreCase("glass")) return 19;
		else if (s.equalsIgnoreCase("gemstone")) return 20;
		else if (s.equalsIgnoreCase("mineral")) return 21;
		else if (s.equalsIgnoreCase("oriharukon")) return 22;

		System.out.println(String.format("invalid item material %s", s));
		return 0;
	}

	public static int parseUseType(String s) {
		if (s.equalsIgnoreCase("none")) return -1;
		else if (s.equalsIgnoreCase("normal")) return 0;
		else if (s.equalsIgnoreCase("weapon")) return 1;
		else if (s.equalsIgnoreCase("armor")) return 2;
		else if (s.equalsIgnoreCase("spell_long")) return 5;
		else if (s.equalsIgnoreCase("ntele")) return 6;
		else if (s.equalsIgnoreCase("identify")) return 7;
		else if (s.equalsIgnoreCase("res")) return 8;
		else if (s.equalsIgnoreCase("teleport")) return 9;
		else if (s.equalsIgnoreCase("letter")) return 12;
		else if (s.equalsIgnoreCase("letter_w")) return 13;
		else if (s.equalsIgnoreCase("choice")) return 14;
		else if (s.equalsIgnoreCase("instrument")) return 15;
		else if (s.equalsIgnoreCase("sosc")) return 16;
		else if (s.equalsIgnoreCase("spell_short")) return 17;
		else if (s.equalsIgnoreCase("T")) return 18;
		else if (s.equalsIgnoreCase("cloak")) return 19;
		else if (s.equalsIgnoreCase("glove")) return 20;
		else if (s.equalsIgnoreCase("boots")) return 21;
		else if (s.equalsIgnoreCase("helm")) return 22;
		else if (s.equalsIgnoreCase("ring")) return 23;
		else if (s.equalsIgnoreCase("amulet")) return 24;
		else if (s.equalsIgnoreCase("shield") || s.equalsIgnoreCase("garder")) return 25;
		else if (s.equalsIgnoreCase("dai")) return 26;
		else if (s.equalsIgnoreCase("zel")) return 27;
		else if (s.equalsIgnoreCase("blank")) return 28;
		else if (s.equalsIgnoreCase("btele")) return 29;
		else if (s.equalsIgnoreCase("spell_buff")) return 30;
		else if (s.equalsIgnoreCase("ccard")) return 31;
		else if (s.equalsIgnoreCase("ccard_w")) return 32;
		else if (s.equalsIgnoreCase("vcard")) return 33;
		else if (s.equalsIgnoreCase("vcard_w")) return 34;
		else if (s.equalsIgnoreCase("wcard")) return 35;
		else if (s.equalsIgnoreCase("wcard_w")) return 36;
		else if (s.equalsIgnoreCase("belt")) return 37;
		else if (s.equalsIgnoreCase("spell_long2")) return 39;
		else if (s.equalsIgnoreCase("earring")) return 40;
		else if (s.equalsIgnoreCase("fishing_rod")) return 42;
		else if (s.equalsIgnoreCase("ron")) return 44;
		else if (s.equalsIgnoreCase("acczel")) return 46;
		else if (s.equalsIgnoreCase("healing")) return 51;
		else if (s.equalsIgnoreCase("tam")) return 68;
		else if (s.equalsIgnoreCase("ron2")) return 74;
		else if (s.equalsIgnoreCase("pair")) return 70;
		else if (s.equalsIgnoreCase("magic_doll")) return 73;
		else if (s.equalsIgnoreCase("should")) return 75;
		else if (s.equalsIgnoreCase("petp")) return 77;
		else if (s.equalsIgnoreCase("badge")) return 78;
		else if (s.equalsIgnoreCase("JJ")) return 79;
		else return Integer.parseInt(s);
	}

	public List<L1Item> findItemId(TableFindItemFilter filter) {
		List<L1Item> list = new LinkedList<L1Item>();
		for (int key : _allTemplates.keys()) {
			L1Item item = _allTemplates.get(key);
			if (item == null)
				continue;

			if (!filter.isFilter(item))
				list.add(item);
		}
		return list;
	}

	public List<L1Item> findDescCachedItems(final int itemDescId) {
		List<L1Item> items = _descCached.get(itemDescId);
		if (items != null)
			return items;

		items = findItemId(new TableFindItemFilter() {
			@Override
			public boolean isFilter(L1Item item) {
				return item.getItemDescId() != itemDescId;
			}
		});

		if (items == null || items.size() <= 0)
			return null;

		_descCached.put(itemDescId, items);
		return items;
	}

	public int findItemIdByName(String name) {
		if (name == null || _nameToItemsMap == null)
			return 0;
		List<L1Item> list = _nameToItemsMap.get(name);
		return (list != null && !list.isEmpty()) ? list.get(0).getItemId() : 0;
	}

	public int findItemIdByNameWithoutSpace(String name) {
		if (name == null || _nameWithoutSpaceToItemsMap == null)
			return 0;
		List<L1Item> list = _nameWithoutSpaceToItemsMap.get(name.replace(" ", "").toLowerCase());
		return (list != null && !list.isEmpty()) ? list.get(0).getItemId() : 0;
	}

	public String findItemIdByName(int itemid) {
		L1Item item = _allTemplates.get(itemid);
		if (item != null)
			return item.getName();
		return null;
	}

	public L1Item findCraftCachedItem(final CraftOutputItem outputItem){
		List<L1Item> items = findDescCachedItems(outputItem.get_nameId());
		if(items == null)
			return null;
		
		int iconId = outputItem.get_iconId();
		int bless = outputItem.get_bless();
		for(L1Item item : items){
			if(item.getGfxId() != iconId)
				continue;
			
			if(item.getBless() != bless)
				continue;
			
			return item;
		}
		return null;
	}
}