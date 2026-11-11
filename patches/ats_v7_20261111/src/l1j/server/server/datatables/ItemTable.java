/*
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA.
 *
 * http://www.gnu.org/copyleft/gpl.html
 */
package l1j.server.server.datatables;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.L1DatabaseFactory;
import l1j.server.BugRace.L1RaceTicket;
import l1j.server.DogFightSystem.DogFightTicket;
import l1j.server.Templates.L1Armor;
import l1j.server.Templates.L1EtcItem;
import l1j.server.Templates.L1Item;
import l1j.server.Templates.L1Weapon;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.utils.SQLUtil;

public class ItemTable {
	private static Logger _log = Logger.getLogger(ItemTable.class.getName());

	private static final Map<String, Integer> _armorTypes = new HashMap<String, Integer>();

	private static final Map<String, Integer> _weaponTypes = new HashMap<String, Integer>();

	private static final Map<String, Integer> _weaponId = new HashMap<String, Integer>();

	private static final Map<String, Integer> _materialTypes = new HashMap<String, Integer>();

	private static final Map<String, Integer> _etcItemTypes = new HashMap<String, Integer>();

	private static final Map<String, Integer> _useTypes = new HashMap<String, Integer>();

	private static ItemTable _instance;

	private HashMap<Integer, L1Item> _allTemplates;
	private final Map<Integer, L1EtcItem> _etcitems;
	private final Map<Integer, L1Armor> _armors;
	private final Map<Integer, L1Weapon> _weapons;
	
	
	// 전체아이템중 티켓만 복사
	private void copyTicketItem(HashMap<Integer, L1Item> target) {
		Set<Integer> keys = _allTemplates.keySet();
		Iterator<Integer> it = keys.iterator();
		for (; it.hasNext(); ) {
			int itemId = it.next();
			L1Item item = _allTemplates.get(itemId);
			if (item instanceof L1RaceTicket || item instanceof DogFightTicket) {
				target.put(itemId, item);
			}
		}
	}


	public HashMap<Integer, L1Item> getAllTemplates(){
		return _allTemplates;
	}

	static {

		_etcItemTypes.put("arrow",           L1Item.TYPE_ETCITEM_ARROW);
		_etcItemTypes.put("wand",            L1Item.TYPE_ETCITEM_WAND);
		_etcItemTypes.put("light",           L1Item.TYPE_ETCITEM_LIGHT);
		_etcItemTypes.put("gem",             L1Item.TYPE_ETCITEM_GAM);
		_etcItemTypes.put("totem",           L1Item.TYPE_ETCITEM_TOTEM);
		_etcItemTypes.put("firecracker",     L1Item.TYPE_ETCITEM_FIRECRACKER);
		_etcItemTypes.put("potion",          L1Item.TYPE_ETCITEM_POTION);
		_etcItemTypes.put("food",            L1Item.TYPE_ETCITEM_FOOD);
		_etcItemTypes.put("scroll",          L1Item.TYPE_ETCITEM_SCROLL);
		_etcItemTypes.put("questitem",       L1Item.TYPE_ETCITEM_QUESTITEM);
		_etcItemTypes.put("spellbook",       L1Item.TYPE_ETCITEM_SPELLBOOK);
		_etcItemTypes.put("petitem",         L1Item.TYPE_ETCITEM_PETITEM);
		_etcItemTypes.put("other",           L1Item.TYPE_ETCITEM_OTHER);
		_etcItemTypes.put("material",        L1Item.TYPE_ETCITEM_MATERIAL);
		_etcItemTypes.put("event",           L1Item.TYPE_ETCITEM_EVENT);
		_etcItemTypes.put("sting",           L1Item.TYPE_ETCITEM_STING);
		_etcItemTypes.put("treasure_box",    L1Item.TYPE_ETCITEM_TREASURE_BOX);
		_etcItemTypes.put("spellscr",        L1Item.TYPE_ETCITEM_SPELLSCR);
		_etcItemTypes.put("spellscrv",       L1Item.TYPE_ETCITEM_SPELLSCRV);		
		_etcItemTypes.put("blessing",        L1Item.TYPE_ETCITEM_BLESSING);	
		_etcItemTypes.put("jewel",           L1Item.TYPE_ETCITEM_JEWEL);		 
		_etcItemTypes.put("controller",      L1Item.TYPE_ETCITEM_CONTROLLER);
		_etcItemTypes.put("healingpotion",   L1Item.TYPE_ETCITEM_HEALING_POTION);

		_useTypes.put("none",                L1Item.TYPE_USE_NONE); // 사용 불가능
		_useTypes.put("normal",              L1Item.TYPE_USE_NORMAL);
		_useTypes.put("weapon",              L1Item.TYPE_USE_WEAPON);
		_useTypes.put("armor",               L1Item.TYPE_USE_ARMOR);
		_useTypes.put("spell_long",          L1Item.TYPE_USE_SPELL_LONG); // 지면 / 오브젝트 선택(원거리)
		_useTypes.put("ntele",               L1Item.TYPE_USE_NTELE);
		_useTypes.put("identify",            L1Item.TYPE_USE_IDENTIFY);
		_useTypes.put("res",                 L1Item.TYPE_USE_RES);
		_useTypes.put("letter",              L1Item.TYPE_USE_LETTER);
		_useTypes.put("letter_w",            L1Item.TYPE_USE_LETTER_W);
		_useTypes.put("choice",              L1Item.TYPE_USE_CHOICE);
		_useTypes.put("instrument",          L1Item.TYPE_USE_INSTRUMENT);
		_useTypes.put("sosc",                L1Item.TYPE_USE_SOSC);
		_useTypes.put("spell_short",         L1Item.TYPE_USE_SPELL_SHORT); // 지면 / 오브젝트 선택(근거리)
		_useTypes.put("T",                   L1Item.TYPE_USE_T);
		_useTypes.put("cloak",               L1Item.TYPE_USE_CLOAK);
		_useTypes.put("glove",               L1Item.TYPE_USE_GLOVE);
		_useTypes.put("boots",               L1Item.TYPE_USE_BOOTS);
		_useTypes.put("helm",                L1Item.TYPE_USE_HELM);
		_useTypes.put("ring",                L1Item.TYPE_USE_RING);
		_useTypes.put("amulet",              L1Item.TYPE_USE_AMULET);
		_useTypes.put("shield",              L1Item.TYPE_USE_SHIELD);
		_useTypes.put("garder",              L1Item.TYPE_USE_GARDER);
		_useTypes.put("dai",                 L1Item.TYPE_USE_DAL);
		_useTypes.put("zel",                 L1Item.TYPE_USE_ZEL);
		_useTypes.put("blank",               L1Item.TYPE_USE_BLANK);
		_useTypes.put("btele",               L1Item.TYPE_USE_BTELE);
		_useTypes.put("spell_buff",          L1Item.TYPE_USE_SPELL_BUFF);
		_useTypes.put("ccard",               L1Item.TYPE_USE_CCARD);
		_useTypes.put("ccard_w",             L1Item.TYPE_USE_CCARD_W);
		_useTypes.put("vcard",               L1Item.TYPE_USE_VCARD);
		_useTypes.put("vcard_w",             L1Item.TYPE_USE_VCARD_W);
		_useTypes.put("wcard",               L1Item.TYPE_USE_WCARD);
		_useTypes.put("wcard_w",             L1Item.TYPE_USE_WCARD_W);
		_useTypes.put("belt",                L1Item.TYPE_USE_BELT);
		_useTypes.put("earring",             L1Item.TYPE_USE_EARRING);
		_useTypes.put("fishing_rod",         L1Item.TYPE_USE_FISHING_ROD); 
		_useTypes.put("ron",                 L1Item.TYPE_USE_RON);//룬1		
		_useTypes.put("acczel",              L1Item.TYPE_USE_ACCZEL);
		_useTypes.put("healing",             L1Item.TYPE_USE_HEALING);
		_useTypes.put("poly",                L1Item.TYPE_USE_POLY); 
		_useTypes.put("magicdoll",           L1Item.TYPE_USE_NAGIC_DOLL); 
		_useTypes.put("map",                 L1Item.TYPE_USE_MAP);
		_useTypes.put("Food",                L1Item.TYPE_USE_FOOD); 
		_useTypes.put("omanTelScroll",       L1Item.TYPE_USE_OMANTELL); 
		
		_armorTypes.put("none",              L1Item.TYPE_ARMOR_NONE);
		_armorTypes.put("helm",              L1Item.TYPE_ARMOR_HELM);
		_armorTypes.put("armor",             L1Item.TYPE_ARMOR_ARMOR);
		_armorTypes.put("T",                 L1Item.TYPE_ARMOR_T);
		_armorTypes.put("cloak",             L1Item.TYPE_ARMOR_CLOAK);
		_armorTypes.put("glove",             L1Item.TYPE_ARMOR_GLOVE);
		_armorTypes.put("boots",             L1Item.TYPE_ARMOR_BOOTS);
		_armorTypes.put("shield",            L1Item.TYPE_ARMOR_SHIELD);
		_armorTypes.put("amulet",            L1Item.TYPE_ARMOR_AMULET);
		_armorTypes.put("ring",              L1Item.TYPE_ARMOR_RING);
		_armorTypes.put("belt",              L1Item.TYPE_ARMOR_BELT);
		_armorTypes.put("ring2",             L1Item.TYPE_ARMOR_RING_TWO);
		_armorTypes.put("earring",           L1Item.TYPE_ARMOR_EARRING);
		_armorTypes.put("garder",            L1Item.TYPE_ARMOR_GARDER);
		_armorTypes.put("ron",               L1Item.TYPE_ARMOR_RON);//룬1

		_weaponTypes.put("sword",            L1Item.TYPE_WEAPON_SWORD);
		_weaponTypes.put("dagger",           L1Item.TYPE_WEAPON_DAGGER);
		_weaponTypes.put("tohandsword",      L1Item.TYPE_WEAPON_TOHANDSWORD);
		_weaponTypes.put("bow",              L1Item.TYPE_WEAPON_BOW);
		_weaponTypes.put("spear",            L1Item.TYPE_WEAPON_SPEAR);
		_weaponTypes.put("blunt",            L1Item.TYPE_WEAPON_BLUNT);
		_weaponTypes.put("staff",            L1Item.TYPE_WEAPON_STAFF);
		_weaponTypes.put("throwingknife",    L1Item.TYPE_WEAPON_TGROWINMGKNIFE);
		_weaponTypes.put("arrow",            L1Item.TYPE_WEAPON_ARROW);
		_weaponTypes.put("gauntlet",         L1Item.TYPE_WEAPON_GAUNTLET);
		_weaponTypes.put("claw",             L1Item.TYPE_WEAPON_CLAW);
		_weaponTypes.put("edoryu",           L1Item.TYPE_WEAPON_EDORYU);
		_weaponTypes.put("singlebow",        L1Item.TYPE_WEAPON_SINGLEBOW);
		_weaponTypes.put("singlespear",      L1Item.TYPE_WEAPON_SINGLESPEAR);
		_weaponTypes.put("tohandblunt",      L1Item.TYPE_WEAPON_TOHANDBLUNT);
		_weaponTypes.put("tohandstaff",      L1Item.TYPE_WEAPON_TOHANDSTAFF);

		_weaponId.put("sword",               L1Item.TYPE_WEAPONID_SWORD);
		_weaponId.put("dagger",              L1Item.TYPE_WEAPONID_DAGGER);
		_weaponId.put("tohandsword",         L1Item.TYPE_WEAPONID_TOHANDSWORD);
		_weaponId.put("bow",                 L1Item.TYPE_WEAPONID_BOW);
		_weaponId.put("blunt",               L1Item.TYPE_WEAPONID_BLUNT);
		_weaponId.put("spear",               L1Item.TYPE_WEAPONID_SPEAR);
		_weaponId.put("staff",               L1Item.TYPE_WEAPONID_STAFF);
		_weaponId.put("throwingknife",       L1Item.TYPE_WEAPONID_THROWINGKNIFE);
		_weaponId.put("arrow",               L1Item.TYPE_WEAPONID_ARROW);
		_weaponId.put("gauntlet",            L1Item.TYPE_WEAPONID_GAUNTLET);
		_weaponId.put("claw",                L1Item.TYPE_WEAPONID_CLAW);
		_weaponId.put("edoryu",              L1Item.TYPE_WEAPONID_EDORYU);
		_weaponId.put("singlebow",           L1Item.TYPE_WEAPONID_SINGLE_BOW);
		_weaponId.put("singlespear",         L1Item.TYPE_WEAPONID_SINGLE_SPEAR);
		_weaponId.put("tohandblunt",         L1Item.TYPE_WEAPONID_TOHAND_BLUNT);
		_weaponId.put("tohandstaff",         L1Item.TYPE_WEAPONID_TOHAND_STAFF);

		_materialTypes.put("none",           L1Item.TYPE_MATARIAL_NONE);
		_materialTypes.put("liquid",         L1Item.TYPE_MATARIAL_LIQUID);
		_materialTypes.put("web",            L1Item.TYPE_MATARIAL_WED);
		_materialTypes.put("vegetation",     L1Item.TYPE_MATARIAL_VEGETATION);
		_materialTypes.put("animalmatter",   L1Item.TYPE_MATARIAL_ANIMALMATTER);
		_materialTypes.put("paper",          L1Item.TYPE_MATARIAL_PAPER);
		_materialTypes.put("cloth",          L1Item.TYPE_MATARIAL_CLOTH);
		_materialTypes.put("leather",        L1Item.TYPE_MATARIAL_LEATHER);
		_materialTypes.put("wood",           L1Item.TYPE_MATARIAL_WOOD);
		_materialTypes.put("bone",           L1Item.TYPE_MATARIAL_BONE);
		_materialTypes.put("dragonscale",    L1Item.TYPE_MATARIAL_DRAGONSCALE);
		_materialTypes.put("iron",           L1Item.TYPE_MATARIAL_IRON);
		_materialTypes.put("steel",          L1Item.TYPE_MATARIAL_STEEL);
		_materialTypes.put("copper",         L1Item.TYPE_MATARIAL_COPPER);
		_materialTypes.put("silver",         L1Item.TYPE_MATARIAL_SILVER);
		_materialTypes.put("gold",           L1Item.TYPE_MATARIAL_GOLD);
		_materialTypes.put("platinum",       L1Item.TYPE_MATARIAL_PLATINUM);
		_materialTypes.put("mithril",        L1Item.TYPE_MATARIAL_MITHRIL);
		_materialTypes.put("blackmithril",   L1Item.TYPE_MATARIAL_BLACKMITHRIL);
		_materialTypes.put("glass",          L1Item.TYPE_MATARIAL_GLASS);
		_materialTypes.put("gemstone",       L1Item.TYPE_MATARIAL_GEMSTONE);
		_materialTypes.put("mineral",        L1Item.TYPE_MATARIAL_MINERAL);
		_materialTypes.put("oriharukon",     L1Item.TYPE_MATARIAL_ORIHARUKON);
	}

	public static ItemTable getInstance() {
		if (_instance == null) {
			_instance = new ItemTable();
		}
		return _instance;
	}
	private ItemTable() {
		_etcitems = allEtcItem();
		_weapons = allWeapon();
		_armors = allArmor();
		buildFastLookupTable();
	}
	
	public static void reload(){
		ItemTable oldInstance = _instance;
		_instance = new ItemTable();
		// 티켓 복사처리
		oldInstance.copyTicketItem(_instance._allTemplates);		 
		oldInstance._etcitems.clear();
		oldInstance._weapons.clear();
		oldInstance._armors.clear();
		oldInstance._allTemplates.clear(); 
	}



	private Map<Integer, L1EtcItem> allEtcItem() {
		Map<Integer, L1EtcItem> result = new HashMap<Integer, L1EtcItem>();

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1EtcItem item = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from item_etc");

			rs = pstm.executeQuery();
			while (rs.next()) {
				item = new L1EtcItem();
				item.setItemId(rs.getInt("item_id"));
				item.setName(rs.getString("name"));
				item.setNameId(rs.getString("name_id"));
				item.setType((_etcItemTypes.get(rs.getString("item_type"))).intValue());
				item.setUseType(_useTypes.get(rs.getString("use_type")).intValue()); 
				item.setType2(L1Item.CATEGORY_ETC);
				item.setMaterial((_materialTypes.get(rs.getString("material"))).intValue());
				item.setWeight(rs.getInt("weight"));
				item.setGfxId(rs.getInt("invgfx"));
				item.setGroundGfxId(rs.getInt("grdgfx"));
				item.setItemDescId(rs.getInt("itemdesc_id"));
				item.setMinLevel(rs.getInt("min_lvl"));
				item.setMaxLevel(rs.getInt("max_lvl"));
				item.setBless(rs.getInt("bless"));
				item.setTradable(rs.getInt("trade") == 0 ? true : false);
				item.setCantDelete(rs.getInt("cant_delete") == 1 ? true : false);
				item.setDmgSmall(rs.getInt("dmg_small"));
				item.setDmgLarge(rs.getInt("dmg_large"));
				item.set_stackable(rs.getInt("stackable") == 1 ? true : false);
				item.setMaxChargeCount(rs.getInt("max_charge_count"));
				item.set_locx(rs.getInt("locx"));
				item.set_locy(rs.getInt("locy"));
				item.set_mapid(rs.getShort("mapid"));
				item.set_delayid(rs.getInt("delay_id"));
				item.set_delaytime(rs.getInt("delay_time"));
				item.set_delayEffect(rs.getInt("delay_effect"));
				item.setFoodVolume(rs.getInt("food_volume"));
				item.setToBeSavedAtOnce((rs.getInt("save_at_once") == 1) ? true : false);

				result.put(Integer.valueOf(item.getItemId()), item);
			}
		} catch (NullPointerException e) {
			_log.log(Level.SEVERE, new StringBuilder().append(item.getName()).append("(" + item.getItemId() + ")")
					.append("의 읽어 들이기에 실패했습니다.").toString());
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return result;
	}

	private Map<Integer, L1Weapon> allWeapon() {
		Map<Integer, L1Weapon> result = new HashMap<Integer, L1Weapon>();

		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1Weapon weapon = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from item_weapon");

			rs = pstm.executeQuery();
			while (rs.next()) {
				weapon = new L1Weapon();
				weapon.setItemId(rs.getInt("item_id"));
				weapon.setName(rs.getString("name"));
				weapon.setNameId(rs.getString("name_id"));
				weapon.setType((_weaponTypes.get(rs.getString("type"))).intValue());
				weapon.setType1((_weaponId.get(rs.getString("type"))).intValue());
				weapon.setType2(L1Item.CATEGORY_WEAPON);
				weapon.setUseType(1);
				weapon.setMaterial((_materialTypes.get(rs.getString("material"))).intValue());
				weapon.setWeight(rs.getInt("weight"));
				weapon.setGfxId(rs.getInt("invgfx"));
				weapon.setGroundGfxId(rs.getInt("grdgfx"));
				weapon.setItemDescId(rs.getInt("itemdesc_id"));
				weapon.setDmgSmall(rs.getInt("dmg_small"));
				weapon.setDmgLarge(rs.getInt("dmg_large"));
				weapon.set_safeenchant(rs.getInt("safenchant"));
				weapon.setUseRoyal(rs.getInt("use_royal") == 0 ? false : true);
				weapon.setUseKnight(rs.getInt("use_knight") == 0 ? false : true);
				weapon.setUseElf(rs.getInt("use_elf") == 0 ? false : true);
				weapon.setUseMage(rs.getInt("use_mage") == 0 ? false : true);
				weapon.setUseDarkelf(rs.getInt("use_darkelf") == 0 ? false : true);
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
				weapon.set_canbedmg(rs.getInt("canbedmg"));
				weapon.setMinLevel(rs.getInt("min_lvl"));
				weapon.setMaxLevel(rs.getInt("max_lvl"));
				weapon.setBless(rs.getInt("bless"));
				weapon.setMagicName(rs.getString("Magic_name"));
				weapon.setTradable(rs.getInt("trade") == 0 ? true : false);
				weapon.setCantDelete(rs.getInt("cant_delete") == 1 ? true : false);
				weapon.setMaxUseTime(rs.getInt("max_use_time")); 
				weapon.setHpDrain(rs.getInt("hp_Drain") == 0 ? false : true);
				weapon.setMpDrain(rs.getInt("mp_Drain") == 0 ? false : true);
				weapon.setHasteItem(rs.getInt("haste_item") == 0 ? false : true);
				weapon.setregist_technology(rs.getInt("regist_technology"));
				weapon.settechnique_hit(rs.getInt("technique_hit"));
				weapon.setregist_spirit(rs.getInt("regist_spirit"));
				weapon.setspirit_hit(rs.getInt("spirit_hit"));
				weapon.setmagic_hit(rs.getInt("magic_hit"));
				weapon.setPvpReduction(rs.getInt("Weapon_Pvp_Drop"));
				weapon.setPvpDmg(rs.getInt("Weapon_Pvp_Up"));
				weapon.setDmgReductionCancel(rs.getInt("add_reduction_cancel"));

				result.put(Integer.valueOf(weapon.getItemId()), weapon);
			}
		} catch (NullPointerException e) {
			_log.log(Level.SEVERE, new StringBuilder().append(weapon.getName()).append("(" + weapon.getItemId() + ")")
					.append("의 읽어 들이기에 실패했습니다.").toString());
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);

		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return result;
	}

	private Map<Integer, L1Armor> allArmor() {
		Map<Integer, L1Armor> result = new HashMap<Integer, L1Armor>();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		L1Armor armor = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from item_armor");

			rs = pstm.executeQuery();
			while (rs.next()) {
				armor = new L1Armor();
				armor.setItemId(rs.getInt("item_id"));
				armor.setName(rs.getString("name"));
				armor.setNameId(rs.getString("name_id"));
				armor.setType((_armorTypes.get(rs.getString("type"))).intValue()); 
				armor.setType2(L1Item.CATEGORY_ARMOR);
				armor.setUseType((_useTypes.get(rs.getString("type"))).intValue());
				armor.setMaterial((_materialTypes.get(rs.getString("material"))).intValue());
				armor.setWeight(rs.getInt("weight"));
				armor.setGfxId(rs.getInt("invgfx"));
				armor.setGroundGfxId(rs.getInt("grdgfx"));
				armor.setItemDescId(rs.getInt("itemdesc_id"));
				armor.set_ac(rs.getInt("ac"));
				armor.set_safeenchant(rs.getInt("safenchant"));
				armor.setUseRoyal(rs.getInt("use_royal") == 0 ? false : true);
				armor.setUseKnight(rs.getInt("use_knight") == 0 ? false : true);
				armor.setUseElf(rs.getInt("use_elf") == 0 ? false : true);
				armor.setUseMage(rs.getInt("use_mage") == 0 ? false : true);
				armor.setUseDarkelf(rs.getInt("use_darkelf") == 0 ? false : true);
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
				armor.setMinLevel(rs.getInt("min_lvl"));
				armor.setMaxLevel(rs.getInt("max_lvl"));
				armor.set_mdef(rs.getInt("m_def"));
				armor.setDamageReduction(rs.getInt("damage_reduction"));
				armor.setWeightReduction(rs.getInt("weight_reduction"));
				armor.setHitRate(rs.getInt("hit_rate"));
				armor.setDmgRate(rs.getInt("dmg_rate"));
				armor.setBowHitRate(rs.getInt("bow_hit_rate"));
				armor.setBowDmgRate(rs.getInt("bow_dmg_rate"));
				armor.setHasteItem(rs.getInt("haste_item") == 0 ? false : true);
				armor.setBless(rs.getInt("bless"));
				armor.setTradable(rs.getInt("trade") == 0 ? true : false);
				armor.setCantDelete(rs.getInt("cant_delete") == 1 ? true : false);
				armor.setMaxUseTime(rs.getInt("max_use_time"));
				armor.set_defense_earth(rs.getInt("defense_earth"));
				armor.set_defense_water(rs.getInt("defense_water"));
				armor.set_defense_wind(rs.getInt("defense_wind"));
				armor.set_defense_fire(rs.getInt("defense_fire"));
				armor.setMagicName(rs.getString("Magic_name"));
				armor.setregist_technology(rs.getInt("regist_technology"));
				armor.settechnique_hit(rs.getInt("technique_hit"));
				armor.setregist_spirit(rs.getInt("regist_spirit"));
				armor.setspirit_hit(rs.getInt("spirit_hit"));
				armor.setmagic_hit(rs.getInt("magic_hit"));		
				armor.setPvpReduction(rs.getInt("Armor_Pvp_Drop"));
				armor.setPvpDmg(rs.getInt("Armor_Pvp_Up"));
				armor.setDmgReductionCancel(rs.getInt("reduction_cancel"));

				result.put(Integer.valueOf(armor.getItemId()), armor);
			}
		} catch (NullPointerException e) {
			_log.log(Level.SEVERE, new StringBuilder().append(armor.getName()).append("(" + armor.getItemId() + ")")
					.append("의 읽어 들이기에 실패했습니다.").toString());
			System.out.println(e);
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return result;
	}

	private void buildFastLookupTable() {
		_allTemplates = new HashMap<Integer, L1Item>(_etcitems.size() + _weapons.size() + _armors.size());
		_allTemplates.putAll(_etcitems);
		_allTemplates.putAll(_weapons);
		_allTemplates.putAll(_armors);
	}

	public L1Item getTemplate(int id) {
		return _allTemplates.get(id);
	}

	public ItemInstance createItem(int itemId) {
		L1Item temp = getTemplate(itemId);
		if (temp == null) {
			return null;
		}
		ItemInstance item = new ItemInstance();
		item.setId(IdFactory.getInstance().nextId());
		item.setItem(temp);
		item.setBless(temp.getBless());
		L1World.getInstance().storeObject(item);
		return item;
	}
	 
	
	
	public ItemInstance createItem(String itemName) { //2
		int itemId = findItemIdByName(itemName);
		L1Item temp = getTemplate(itemId);
		if (temp == null) {
			return null;
		}
		ItemInstance item = new ItemInstance();
		item.setId(IdFactory.getInstance().nextId());
		item.setItem(temp);
		L1World.getInstance().storeObject(item);
		return item;
	}
	
	public ItemInstance createItem(L1Item temp) { //2
		if (temp == null) {
			return null;
		}
		ItemInstance item = new ItemInstance();
		item.setId(IdFactory.getInstance().nextId());
		item.setItem(temp);
		L1World.getInstance().storeObject(item);
		return item;
	}

	public int findItemIdByName(String name) {
		int nl		= name.length();
		for (L1Item item : _allTemplates.values()) {
			if(item == null) {
				continue;
			}
			String s = item.getName();
			if(s.length() != nl) {
				continue;
			}
			if(s.equals(name)) {
				return item.getItemId();
			}
		}
		return 0;
	}

	public String findItemIdByName(int itemid) {
		L1Item item = _allTemplates.get(itemid);
		if (item != null) {
			return item.getName();
		}
		return null;
	}
	public int findItemIdByNameWithoutSpace(String name) {
		for (L1Item item : _allTemplates.values()) {
			if(item == null) {
				continue;
			}
			String s = item.getName();
			String new_name = name.replace(" ", "");
			if(s.replace(" ", "").equalsIgnoreCase(new_name)) {
				return item.getItemId();
			}
		}
		return 0;
	}
	
	public void initDogfight() {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("delete from character_items where item_id > 100000000 and item_id < 200000000");
				pstm.execute();
				pstm.close();
				pstm = con.prepareStatement("delete from character_warehouse where item_id > 100000000 and item_id < 200000000");
				pstm.execute();
				pstm.close();
				pstm = con.prepareStatement("delete from clan_warehouse where item_id > 100000000 and item_id < 200000000");
				pstm.execute();
				pstm.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
	
	public void initRace() {
		Connection con = null;
		PreparedStatement pstm = null;
		try {
			con = L1DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("delete from character_items where item_id > 8000000 and item_id < 12000000");
				pstm.execute();
				pstm.close();
				pstm = con.prepareStatement("delete from character_warehouse where item_id > 8000000 and item_id < 12000000");
				pstm.execute();
				pstm.close();
				pstm = con.prepareStatement("delete from clan_warehouse where item_id > 8000000 and item_id < 12000000");
				pstm.execute();
				pstm.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}
}
