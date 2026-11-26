package l1j.server.server.datatables;

import gnu.trove.TCollections;
import gnu.trove.map.TIntObjectMap;
import gnu.trove.map.hash.TIntObjectHashMap;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.IdFactory;
import l1j.server.server.model.inventory.CSetItem;
import l1j.server.server.model.Instance.L1AuctionBoardInstance;
import l1j.server.server.model.Instance.L1BoardInstance;
import l1j.server.server.model.Instance.L1CastleGuardInstance;
import l1j.server.server.model.Instance.L1CataInstance;
import l1j.server.server.model.Instance.L1CrownInstance;
import l1j.server.server.model.Instance.L1DoorInstance;
import l1j.server.server.model.Instance.L1DwarfInstance;
import l1j.server.server.model.Instance.L1EffectInstance;
import l1j.server.server.model.Instance.L1FieldObjectInstance;
import l1j.server.server.model.Instance.L1FurnitureInstance;
import l1j.server.server.model.Instance.L1GuardInstance;
import l1j.server.server.model.Instance.L1GuardianInstance;
import l1j.server.server.model.Instance.L1HousekeeperInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1MobTrapInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcCashShopInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1NpcShopInstance;
import l1j.server.server.model.Instance.L1PeopleInstance;
import l1j.server.server.model.Instance.L1QuestInstance;
import l1j.server.server.model.Instance.L1RaidDoorInstance;
import l1j.server.server.model.Instance.L1RaidPortalInstance;
import l1j.server.server.model.Instance.L1RequestInstance;
import l1j.server.server.model.Instance.L1ScarecrowInstance;
import l1j.server.server.model.Instance.L1SignboardInstance;
import l1j.server.server.model.Instance.L1TeleporterInstance;
import l1j.server.server.model.Instance.L1TowerFromLfcInstance;
import l1j.server.server.model.Instance.L1TowerInstance;
import l1j.server.server.model.Instance.MessengerInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.item.search.template.TableFindItemFilter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftOutputItem;
import l1j.server.server.templates.L1Armor;
import l1j.server.server.templates.L1ArmorSets;
import l1j.server.server.templates.L1EtcItem;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1RaceTicket;
import l1j.server.server.templates.L1Weapon;
import l1j.server.server.utils.eKind;

/**
 * Prototype and creation owner. Item IDs and NPC IDs are separate namespaces.
 * The existing SQL loaders are retained as the typed stores, not copied.
 * Live object identity and registration remain owned by CWorld/IdFactory.
 */
public final class CObjectDB {
    private static final Logger LOG = Logger.getLogger(CObjectDB.class.getName());

    private CObjectDB() {}

    // Independent lazy holders preserve the existing startup dependencies.
    private static final class Items {
        private static volatile ItemData current = new ItemData();
    }

    private static final class Npcs {
        private static volatile NpcData current = new NpcData();
    }

    private static final class Sets {
        private static volatile SetData current = new SetData();
    }

    public static ItemData LoadItem() {
        return Items.current;
    }

    public static NpcData LoadNPC() {
        return Npcs.current;
    }

    public static SetData LoadSetItem() {
        return Sets.current;
    }

    public static void PostLoad() {
        CSetItem.PostLoad();
    }

    // Publish one completed loader generation; do not mutate an older reader's
    // set list. SQL failure policy stays in the loaders.
    public static synchronized void ReloadItem() {
        Items.current = new ItemData();
    }

    public static synchronized void ReloadNPC() {
        Npcs.current = new NpcData();
    }

    public static synchronized void ReloadSetItem() {
        Sets.current = new SetData();
    }

    public static L1Item GetItem(int classId) {
        return LoadItem().getTemplate(classId);
    }

    public static L1Npc GetNPC(int classId) {
        return LoadNPC().getTemplate(classId);
    }

    public static L1ItemInstance CreateItem(int classId) {
        return CreateItem(classId, true);
    }

    public static L1ItemInstance CreateItem(int classId, boolean identified) {
        return CreateItem(GetItem(classId), identified);
    }

    public static L1ItemInstance CreateItem(L1Item template, boolean identified) {
        return CreateItem(template, true, identified);
    }

    public static L1ItemInstance CreateItem(L1Item template) {
        // Legacy template/name overloads leave the instance's default unchanged.
        return CreateItem(template, false, false);
    }

    private static L1ItemInstance CreateItem(L1Item template, boolean setIdentified, boolean identified) {
        if (template == null) return null;
        L1ItemInstance item = new L1ItemInstance();
        item.setId(IdFactory.getInstance().nextId());
        item.setItem(template);
        item.setBless(template.getBless());
        if (setIdentified) item.setIdentified(identified);
        L1World.getInstance().storeObject(item);
        return item;
    }

    // Resolve once when the prototype's implementation is assigned. The
    // spawn path uses only the primitive discriminator, including clones.
    private static final int NPC_UNSET = 0;
    private static final int NPC_CONTEXT_REQUIRED = -1;
    private static final int NPC_L1MONSTER = 1;
    private static final int NPC_L1MERCHANT = 2;
    private static final int NPC_L1NPC = 3;
    private static final int NPC_L1TELEPORTER = 4;
    private static final int NPC_L1HOUSEKEEPER = 5;
    private static final int NPC_L1FIELDOBJECT = 6;
    private static final int NPC_L1GUARD = 7;
    private static final int NPC_L1EFFECT = 8;
    private static final int NPC_L1NPCSHOP = 9;
    private static final int NPC_L1DWARF = 10;
    private static final int NPC_L1CASTLEGUARD = 11;
    private static final int NPC_L1PEOPLE = 12;
    private static final int NPC_L1DOOR = 13;
    private static final int NPC_L1BOARD = 14;
    private static final int NPC_L1FURNITURE = 15;
    private static final int NPC_L1SIGNBOARD = 16;
    private static final int NPC_L1SCARECROW = 17;
    private static final int NPC_L1TOWER = 18;
    private static final int NPC_L1QUEST = 19;
    private static final int NPC_L1CATA = 20;
    private static final int NPC_L1GUARDIAN = 21;
    private static final int NPC_L1RAIDPORTAL = 22;
    private static final int NPC_L1RAIDDOOR = 23;
    private static final int NPC_L1CROWN = 24;
    private static final int NPC_L1AUCTIONBOARD = 25;
    private static final int NPC_L1TOWERFROMLFC = 26;
    private static final int NPC_L1REQUEST = 27;
    private static final int NPC_L1NPCCASHSHOP = 28;
    private static final int NPC_L1MOBTRAP = 29;
    private static final int NPC_MESSENGER = 30;

    public static int ResolveNPCType(String impl) {
        if (impl == null) return NPC_UNSET;
        // Legacy pet prototypes must not silently become monsters through the fallback.
        if ("L1Pet".equals(impl) || "L1PetInstance".equals(impl)) return NPC_CONTEXT_REQUIRED;
        return switch (impl) {
            case "L1Monster", "L1MonsterInstance" -> NPC_L1MONSTER;
            case "L1Merchant", "L1MerchantInstance" -> NPC_L1MERCHANT;
            case "L1Npc", "L1NpcInstance" -> NPC_L1NPC;
            case "L1Teleporter", "L1TeleporterInstance" -> NPC_L1TELEPORTER;
            case "L1Housekeeper", "L1HousekeeperInstance" -> NPC_L1HOUSEKEEPER;
            case "L1FieldObject", "L1FieldObjectInstance" -> NPC_L1FIELDOBJECT;
            case "L1Guard", "L1GuardInstance" -> NPC_L1GUARD;
            case "L1Effect", "L1EffectInstance" -> NPC_L1EFFECT;
            case "L1NpcShop", "L1NpcShopInstance" -> NPC_L1NPCSHOP;
            case "L1Dwarf", "L1DwarfInstance" -> NPC_L1DWARF;
            case "L1CastleGuard", "L1CastleGuardInstance" -> NPC_L1CASTLEGUARD;
            case "L1People", "L1PeopleInstance" -> NPC_L1PEOPLE;
            case "L1Door", "L1DoorInstance" -> NPC_L1DOOR;
            case "L1Board", "L1BoardInstance" -> NPC_L1BOARD;
            case "L1Furniture", "L1FurnitureInstance" -> NPC_L1FURNITURE;
            case "L1Signboard", "L1SignboardInstance" -> NPC_L1SIGNBOARD;
            case "L1Scarecrow", "L1ScarecrowInstance" -> NPC_L1SCARECROW;
            case "L1Tower", "L1TowerInstance" -> NPC_L1TOWER;
            case "L1Quest", "L1QuestInstance" -> NPC_L1QUEST;
            case "L1Cata", "L1CataInstance" -> NPC_L1CATA;
            case "L1Guardian", "L1GuardianInstance" -> NPC_L1GUARDIAN;
            case "L1RaidPortal", "L1RaidPortalInstance" -> NPC_L1RAIDPORTAL;
            case "L1RaidDoor", "L1RaidDoorInstance" -> NPC_L1RAIDDOOR;
            case "L1Crown", "L1CrownInstance" -> NPC_L1CROWN;
            case "L1AuctionBoard", "L1AuctionBoardInstance" -> NPC_L1AUCTIONBOARD;
            case "L1TowerFromLfc", "L1TowerFromLfcInstance" -> NPC_L1TOWERFROMLFC;
            case "L1Request", "L1RequestInstance" -> NPC_L1REQUEST;
            case "L1NpcCashShop", "L1NpcCashShopInstance" -> NPC_L1NPCCASHSHOP;
            case "L1MobTrap", "L1MobTrapInstance" -> NPC_L1MOBTRAP;
            case "Messenger", "MessengerInstance" -> NPC_MESSENGER;
            case "L1Doll", "L1DollInstance", "L1Support", "L1SupportInstance", "L1Summon", "L1SummonInstance", "L1Companion", "L1CompanionInstance", "L1Follower", "L1FollowerInstance", "L1ClanJoin", "L1ClanJoinInstance", "L1Manager", "L1ManagerInstance", "L1Trap", "L1TrapInstance", "L1Pc", "L1PcInstance", "L1Item", "L1ItemInstance" -> NPC_CONTEXT_REQUIRED;
            // Missing classes in the 2026-11-05 DB: preserve the old
            // ClassNotFoundException -> monster fallback, not guessed aliases.
            case "L1EventTower", "L1EventTowerInstance", "MJL1Tower", "MJL1TowerInstance", "MJMessenger", "MJMessengerInstance", "L1EventCrown", "L1EventCrownInstance", "L1EventTower1", "L1EventTower1Instance", "L1EventCrown1", "L1EventCrown1Instance", "L1MJRaidPoison", "L1MJRaidPoisonInstance", "L1MagicScarecrow", "L1MagicScarecrowInstance" -> NPC_L1MONSTER;
            default -> NPC_L1MONSTER;
        };
    }

    public static L1NpcInstance CreateNPC(int classId) {
        return CreateNPC(GetNPC(classId));
    }

    public static L1NpcInstance CreateNPC(L1Npc template) {
        if (template == null) return null;
        try {
            return switch (template.getCreationType()) {
                case NPC_L1MONSTER -> new L1MonsterInstance(template);
                case NPC_L1MERCHANT -> new L1MerchantInstance(template);
                case NPC_L1NPC -> new L1NpcInstance(template);
                case NPC_L1TELEPORTER -> new L1TeleporterInstance(template);
                case NPC_L1HOUSEKEEPER -> new L1HousekeeperInstance(template);
                case NPC_L1FIELDOBJECT -> new L1FieldObjectInstance(template);
                case NPC_L1GUARD -> new L1GuardInstance(template);
                case NPC_L1EFFECT -> new L1EffectInstance(template);
                case NPC_L1NPCSHOP -> new L1NpcShopInstance(template);
                case NPC_L1DWARF -> new L1DwarfInstance(template);
                case NPC_L1CASTLEGUARD -> new L1CastleGuardInstance(template);
                case NPC_L1PEOPLE -> new L1PeopleInstance(template);
                case NPC_L1DOOR -> new L1DoorInstance(template);
                case NPC_L1BOARD -> new L1BoardInstance(template);
                case NPC_L1FURNITURE -> new L1FurnitureInstance(template);
                case NPC_L1SIGNBOARD -> new L1SignboardInstance(template);
                case NPC_L1SCARECROW -> new L1ScarecrowInstance(template);
                case NPC_L1TOWER -> new L1TowerInstance(template);
                case NPC_L1QUEST -> new L1QuestInstance(template);
                case NPC_L1CATA -> new L1CataInstance(template);
                case NPC_L1GUARDIAN -> new L1GuardianInstance(template);
                case NPC_L1RAIDPORTAL -> new L1RaidPortalInstance(template);
                case NPC_L1RAIDDOOR -> new L1RaidDoorInstance(template);
                case NPC_L1CROWN -> new L1CrownInstance(template);
                case NPC_L1AUCTIONBOARD -> new L1AuctionBoardInstance(template);
                case NPC_L1TOWERFROMLFC -> new L1TowerFromLfcInstance(template);
                case NPC_L1REQUEST -> new L1RequestInstance(template);
                case NPC_L1NPCCASHSHOP -> new L1NpcCashShopInstance(template);
                case NPC_L1MOBTRAP -> new L1MobTrapInstance(template);
                case NPC_MESSENGER -> new MessengerInstance(template);
                case NPC_CONTEXT_REQUIRED, NPC_UNSET -> {
                    LOG.log(Level.WARNING, "CreateNPC requires a specialized creation path: npcId="
                            + template.get_npcId() + ", impl=" + template.getImpl());
                    yield null;
                }
                default -> throw new IllegalStateException("Invalid NPC creation type: " + template.getCreationType());
            };
        } catch (VirtualMachineError | ThreadDeath fatal) {
            throw fatal;
        } catch (Throwable failure) {
            LOG.log(Level.SEVERE, "CreateNPC failed - npcId: " + template.get_npcId(), failure);
            return null;
        }
    }

    public static final class ItemData {
        private static final Logger _log = Logger.getLogger(ItemData.class.getName());

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

    	ItemData() {
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
                _log.log(Level.SEVERE, "ItemData allEtcItem error", e);
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
                _log.log(Level.SEVERE, "ItemData allWeapon error", e);
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
                _log.log(Level.SEVERE, "ItemData allArmor error", e);
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
                _log.log(Level.SEVERE, "ItemData initRace error", e);
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
    		return CObjectDB.CreateItem(getTemplate(itemId), identified);
    	}

    	public L1ItemInstance createItem(L1Item temp) {
    		return CObjectDB.CreateItem(temp);
    	}

    	public ArrayList<L1Item> findItemByName(String name) {
    		if (name == null || _nameToItemsMap == null)
    			return new ArrayList<>(0);
    		List<L1Item> list = _nameToItemsMap.get(name);
    		return list != null ? new ArrayList<>(list) : new ArrayList<>(0);
    	}

    	public L1ItemInstance createItem(String itemName) {
    		return createItem(getTemplate(findItemIdByName(itemName)));
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

    public static final class NpcData {
        private static final Logger _log = Logger.getLogger(NpcData.class.getName());

    	private final boolean _initialized;

    	private final TIntObjectMap<L1Npc> _npcs = new TIntObjectHashMap<L1Npc>(4096);

    	private static final Map<String, Integer> _familyTypes = buildFamily();

    	public boolean isInitialized() {
    		return _initialized;
    	}

    	NpcData() {
    		loadNpcData();
    		_initialized = true;
    	}

    	private void loadNpcBorn() {
    		String sql = "SELECT * FROM npc_born";

    		try (Connection con = DatabaseFactory.getInstance().getConnection();
    			 PreparedStatement pstm = con.prepareStatement(sql);
    			 ResultSet rs = pstm.executeQuery()) {

    			while (rs.next()) {
    				int npcid = rs.getInt("npcid");
    				L1Npc npc = getTemplate(npcid);
    				if (npc == null) {
    					_log.warning(String.format("[npc_born load fail] 엔피씨 아이디를 찾을 수 없습니다: %d", npcid));
    					continue;
    				}
    				npc.createBorn(rs);
    			}
    		} catch (Exception e) {
                _log.log(Level.SEVERE, "NpcData loadNpcBorn error", e);
    		}
    	}

    	private void loadNpcData() {
    		String sql = "SELECT * FROM npc";

    		try (Connection con = DatabaseFactory.getInstance().getConnection();
    			 PreparedStatement pstm = con.prepareStatement(sql);
    			 ResultSet rs = pstm.executeQuery()) {

                ResultSetMetaData columns = rs.getMetaData();
                int classIdColumn = 0;
                for (int i = 1; i <= columns.getColumnCount(); i++) {
                    if ("class_id".equalsIgnoreCase(columns.getColumnLabel(i))) {
                        classIdColumn = i;
                        break;
                    }
                }
                if (classIdColumn == 0) {
                    _log.warning("[NPC class_id] npc.class_id column missing; apply APPLY_Monsterbook_ClassIds.sql");
                }
    			while (rs.next()) {
    				L1Npc npc = new L1Npc();
    				int npcId = rs.getInt("npcid");
    				npc.set_npcId(npcId);
                    npc.set_class_id(classIdColumn == 0 ? 0 : rs.getInt(classIdColumn));
     
    				npc.set_name(rs.getString("name"));
    				npc.set_nameid(rs.getString("nameid"));

    				String impl = rs.getString("impl");
    				npc.setImpl(impl);

    				npc.set_gfxid(rs.getInt("gfxid"));
    				npc.set_level(rs.getInt("lvl"));
    				npc.set_hp(rs.getInt("hp"));
    				npc.set_mp(rs.getInt("mp"));
    				npc.set_ac(rs.getInt("ac"));
    				npc.set_str(rs.getByte("str"));
    				npc.set_con(rs.getByte("con"));
    				npc.set_dex(rs.getByte("dex"));
    				npc.set_wis(rs.getByte("wis"));
    				npc.set_int(rs.getByte("intel"));
    				npc.set_mr(rs.getInt("mr"));
    				npc.set_exp(rs.getInt("exp"));
    				npc.set_lawful(rs.getInt("lawful"));
    				npc.set_size(rs.getString("size"));
    				npc.set_weakAttr(rs.getInt("weakAttr"));
    				npc.set_ranged(rs.getInt("ranged"));
    				npc.setTamable(rs.getBoolean("tamable"));
    				npc.setUseAction(rs.getString("use_action"));
    				npc.set_undead(rs.getInt("undead"));
    				npc.set_poisonatk(rs.getInt("poison_atk"));
    				npc.set_paralysisatk(rs.getInt("paralysis_atk"));
    				npc.set_agro(rs.getBoolean("agro"));
    				npc.set_agrososc(rs.getBoolean("agrososc"));
    				npc.set_agrocoi(rs.getBoolean("agrocoi"));

    				Integer family = _familyTypes.get(rs.getString("family"));
    				npc.set_family(family == null ? 0 : family.intValue());

    				int agrofamily = rs.getInt("agrofamily");
    				if (npc.get_family() == 0 && agrofamily == 1) {
    					npc.set_agrofamily(0);
    				} else {
    					npc.set_agrofamily(agrofamily);
    				}

    				npc.set_agrogfxid1(rs.getInt("agrogfxid1"));
    				npc.set_agrogfxid2(rs.getInt("agrogfxid2"));
    				npc.set_picupitem(rs.getBoolean("picupitem"));
    				npc.set_digestitem(rs.getInt("digestitem"));
    				npc.set_hprinterval(rs.getInt("hprinterval"));
    				npc.set_hpr(rs.getInt("hpr"));
    				npc.set_mprinterval(rs.getInt("mprinterval"));
    				npc.set_mpr(rs.getInt("mpr"));
    				npc.set_teleport(rs.getBoolean("teleport"));
    				npc.set_randomlevel(rs.getInt("randomlevel"));
    				npc.set_randomhp(rs.getInt("randomhp"));
    				npc.set_randommp(rs.getInt("randommp"));
    				npc.set_randomac(rs.getInt("randomac"));
    				npc.set_randomexp(rs.getInt("randomexp"));
    				npc.set_randomlawful(rs.getInt("randomlawful"));
    				npc.set_damagereduction(rs.getInt("damage_reduction"));
    				npc.set_hard(rs.getBoolean("hard"));
    				npc.set_doppel(rs.getBoolean("doppel"));
    				npc.set_IsTU(rs.getBoolean("IsTU"));
    				npc.set_IsErase(rs.getBoolean("IsErase"));
    				npc.setBowActId(rs.getInt("bowActId"));
    				npc.setKarma(rs.getInt("karma"));
    				npc.setTransformId(rs.getInt("transform_id"));
    				npc.setTransformGfxId(rs.getInt("transform_gfxid"));
    				npc.setLightSize(rs.getInt("light_size"));
    				npc.setAmountFixed(rs.getBoolean("amount_fixed"));
    				npc.setChangeHead(rs.getBoolean("change_head"));
    				npc.setDoor(rs.getInt("spawnlist_door"));
    				npc.setCountId(rs.getInt("count_map"));
    				npc.setCantResurrect(rs.getBoolean("cant_resurrect"));
    				npc.setShapeChange(rs.getBoolean("shape_change"));


    				_npcs.put(npcId, npc);
    			}
    		} catch (SQLException e) {
                _log.log(Level.SEVERE, "NpcData loadNpcData error", e);
    		} finally {
    			loadNpcBorn();
    		}
    	}

    	public L1Npc getTemplate(int id) {
    		return _npcs.get(id);
    	}

    	public L1NpcInstance newNpcInstance(int id) {
    		L1Npc npcTemp = getTemplate(id);
    		if (npcTemp == null) {
    			return null;
    		}
    		return newNpcInstance(npcTemp);
    	}

    	public L1NpcInstance newNpcInstance(L1Npc npcTemp) {
    		return CObjectDB.CreateNPC(npcTemp);
    	}

    	public static Map<String, Integer> buildFamily() {
    		Map<String, Integer> result = new HashMap<String, Integer>(256);
    		String sql = "SELECT DISTINCT(family) AS family FROM npc WHERE NOT trim(family) = ''";

    		try (Connection con = DatabaseFactory.getInstance().getConnection();
    			 PreparedStatement pstm = con.prepareStatement(sql);
    			 ResultSet rs = pstm.executeQuery()) {

    			int id = 1;
    			while (rs.next()) {
    				String family = rs.getString("family");
    				result.put(family, id++);
    			}
    		} catch (SQLException e) {
                _log.log(Level.SEVERE, "NpcData buildFamily error", e);
    		}
    		return result;
    	}

    	public int findNpcIdByName(String name) {
    		int nl = name.length();
    		for (L1Npc npc : _npcs.valueCollection()) {
    			String s = npc.get_name();
    			if (s.length() != nl)
    				continue;

    			if (s.equals(name))
    				return npc.get_npcId();
    		}
    		return 0;
    	}

    	public int findNpcIdByNameWithoutSpace(String name) {
    		for (L1Npc npc : _npcs.valueCollection()) {
    			if (npc.get_name().replace(" ", "").equals(name)) {
    				return npc.get_npcId();
    			}
    		}
    		return 0;
    	}
    }

    public static final class SetData {
    	
        private static final Logger _log = Logger.getLogger(SetData.class.getName());

    	private final List<L1ArmorSets> _armorSetList = new ArrayList<L1ArmorSets>();
    	
    	SetData() {
    		load();
    	}

    	private void load() {
    		_armorSetList.clear();
    		String sql = "SELECT * FROM armor_set";

    		try (Connection con = DatabaseFactory.getInstance().getConnection();
    			 PreparedStatement pstm = con.prepareStatement(sql);
    			 ResultSet rs = pstm.executeQuery()) {

    			fillTable(rs);
    			_log.config("갑옷 세트(ArmorSet) 정보 " + _armorSetList.size() + "건 로드 완료");

    		} catch (SQLException e) {
    			_log.log(Level.SEVERE, "error while creating armor_set table", e);
    		}
    	}

    	private void fillTable(ResultSet rs) throws SQLException {
    		while (rs.next()) {
    			L1ArmorSets as = new L1ArmorSets();
    			as.setId(rs.getInt("id"));
    			as.setSets(rs.getString("sets"));
    			as.set_main_id(rs.getInt("main_id"));
    			as.setPolyId(rs.getInt("polyid"));
    			as.setAc(rs.getInt("ac"));
    			as.setHp(rs.getInt("hp"));
    			as.setMp(rs.getInt("mp"));
    			as.setHpr(rs.getInt("hpr"));
    			as.setMpr(rs.getInt("mpr"));
    			as.setMr(rs.getInt("mr"));
    			as.setStr(rs.getInt("str"));
    			as.setDex(rs.getInt("dex"));
    			as.setCon(rs.getInt("con"));
    			as.setWis(rs.getInt("wis"));
    			as.setCha(rs.getInt("cha"));
    			as.setIntl(rs.getInt("intl"));
    			as.set_defense_water(rs.getInt("defense_water"));
    			as.set_defense_earth(rs.getInt("defense_earth"));
    			as.set_defense_wind(rs.getInt("defense_wind"));
    			as.set_defense_fire(rs.getInt("defense_fire"));
    			
    			_armorSetList.add(as);
    		}
    	}

    	public L1ArmorSets[] getAllList() {
    		return _armorSetList.toArray(new L1ArmorSets[_armorSetList.size()]);
    	}
    }
}
