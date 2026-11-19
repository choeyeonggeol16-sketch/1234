package l1j.server.MJCompanion;

import java.sql.ResultSet;
import java.sql.SQLException;

import l1j.server.MJTemplate.MJSqlHelper.Property.MJIPropertyHandler;
import l1j.server.MJTemplate.MJSqlHelper.Property.MJSqlPropertyReader;

public class CompanionSettings {
	public static int FRIEND_SHIP_MAX_GUAGE = 100000;
	public static double FRIEND_SHIP_BY_EXP;
	public static int FRIEND_SHIP_TO_MARBLE;
	public static int EXP_SCALE;
	public static int COMPANION_MAX_LEVEL;
	public static double HIT_BY_LEVEL;
	public static double HIT_BY_LEVEL_DIFF;
	public static int MINIMUM_HIT;
	public static int MAXIMUM_HIT;
	public static double DMG_BY_LEVEL;
	public static double DMG_BY_LEVEL_DIFF;
	public static double REDUCTION_BY_MR;
	public static double REDUCTION_BY_ELEMENTAL;
	public static double MAGNIFICATION_BY_CRITICAL;
	public static int MINIMUM_DMG;
	public static int MAXIMUM_DMG;
	public static int REGENERATION_TICK_SECONDS;
	public static long COMBO_DURATION_MILLIS;
	public static double MAGNIFICATION_BY_COMBO;
	public static double COMBO_ATTACK_DELAY_REDUCE;
	public static double COMBO_MOVE_DELAY_REDUCE;
	public static int MAX_STAT;
	public static int EXP_SAFETY_ITEMID;
	public static double MAGNIFICATION_BY_PET_TO_PC;
	public static double MAGNIFICATION_BY_PC_TO_PET;
	public static int TRANING_INITIALIZE_HOUR;
	public static int TRANING_EXP_BY_LEVEL;
	public static double TRANING_PERCENT_BY_EXP;
	public static int TRANING_NEED_ITEM_ID;
	public static int TRANING_NEED_ITEM_COUNT;
	public static int TRANING_NEED_FRIEND_SHIP;
	public static int TRANING_MIN_LEVEL;
	public static int EXP_RESTORE_NEED_ADENA;
	public static int OBLIVION_NEED_ADENA;
	public static int STATS_INITIALIZED_ITEM_ID;
	public static int STATS_INITIALIZED_NEED_LEVEL;
	public static int WILD_BLOOD_ITEM_ID;
	public static int ELIXIR_MAX_USE_COUNT;
	public static int ELIXIR_MIN_USE_LEVEL;
	public static int ELIXIR_BY_LEVEL;
	public static int ELIXIR_STR_ITEM_ID;
	public static int ELIXIR_CON_ITEM_ID;
	public static int ELIXIR_INT_ITEM_ID;
	public static int NAME_CHANGED_ITEM_ID;
	public static double EXP_BY_MASTER_EXP;
	public static void do_load(){
		MJSqlPropertyReader.do_exec("companion_settings", "section", "val", new MJIPropertyHandler(){
			@Override
			public void on_load(String section, MJSqlPropertyReader reader, ResultSet rs) throws SQLException {
				switch(section){
				case "ExpScale":
					EXP_SCALE = reader.read_int(rs);
					break;
				case "FriendShipByExp":
					FRIEND_SHIP_BY_EXP = reader.read_double_by_percent(rs);
					break;
				case "FriendShipToMarble":
					FRIEND_SHIP_TO_MARBLE = reader.read_int(rs);
					break;
				case "CompanionMaxLevel":
					COMPANION_MAX_LEVEL = reader.read_int(rs);
					break;
				case "HitByLevel":
					HIT_BY_LEVEL = reader.read_double_by_percent(rs);
					break;
				case "HitByLevelDiff":
					HIT_BY_LEVEL_DIFF = reader.read_double_by_percent(rs);
					break;
				case "MinimumHit":
					MINIMUM_HIT = reader.read_int(rs);
					break;
				case "MaximumHit":
					MAXIMUM_HIT = reader.read_int(rs);
					break;
				case "DmgByLevel":
					DMG_BY_LEVEL = reader.read_double_by_percent(rs);
					break;
				case "DmgByLevelDiff":
					DMG_BY_LEVEL_DIFF = reader.read_double_by_percent(rs);
					break;
				case "ReductionByMR":
					REDUCTION_BY_MR = reader.read_double_by_percent(rs);
					break;
				case "ReductionByElemental":
					REDUCTION_BY_ELEMENTAL = reader.read_double_by_percent(rs);
					break;
				case "MagnificationByCritical":
					MAGNIFICATION_BY_CRITICAL = reader.read_double_by_percent(rs);
					break;
				case "MinumumDmg":
					MINIMUM_DMG = reader.read_int(rs);
					break;
				case "MaximumDmg":
					MAXIMUM_DMG = reader.read_int(rs);
					break;
				case "RegenerationTickSeconds":
					REGENERATION_TICK_SECONDS = reader.read_int(rs);
					break;
				case "ComboDuration":
					COMBO_DURATION_MILLIS = reader.read_int(rs) * 1000;
					break;
				case "MagnificationByCombo":
					MAGNIFICATION_BY_COMBO = reader.read_double_by_percent(rs);
					break;
				case "ComboAttackDelayReduce":
					COMBO_ATTACK_DELAY_REDUCE = reader.read_double(rs);
					break;
				case "ComboMoveDelayReduce":
					COMBO_MOVE_DELAY_REDUCE = reader.read_double(rs);
					break;
				case "MaxStat":
					MAX_STAT = reader.read_int(rs);
					break;
				case "ExpSafetyItemId":
					EXP_SAFETY_ITEMID = reader.read_int(rs);
					break;
				case "MagnificationByPetToPc":
					MAGNIFICATION_BY_PET_TO_PC = reader.read_double_by_percent(rs);
					break;
				case "MagnificationByPcToPet":
					MAGNIFICATION_BY_PC_TO_PET = reader.read_double_by_percent(rs);
					break;
				case "TraningInitializeHour":
					TRANING_INITIALIZE_HOUR = reader.read_int(rs);
					break;
				case "TraningExpByLevel":
					TRANING_EXP_BY_LEVEL = reader.read_int(rs);
					break;
				case "TraningPercentByExp":
					TRANING_PERCENT_BY_EXP = reader.read_double_by_percent(rs);
					break;
				case "TraningNeedItemId":
					TRANING_NEED_ITEM_ID = reader.read_int(rs);
					break;
				case "TraningNeedItemCount":
					TRANING_NEED_ITEM_COUNT = reader.read_int(rs);
					break;
				case "TraningNeedFriendShip":
					TRANING_NEED_ITEM_ID = reader.read_int(rs);
					break;
				case "TraningMinLevel":
					TRANING_MIN_LEVEL = reader.read_int(rs);
					break;
				case "ExpRestoreNeedAdena":
					EXP_RESTORE_NEED_ADENA = reader.read_int(rs);
					break;
				case "OblivionNeedAdena":
					OBLIVION_NEED_ADENA = reader.read_int(rs);
					break;
				case "StatsInitializedItemId":
					STATS_INITIALIZED_ITEM_ID = reader.read_int(rs);
					break;
				case "StatsInitializedNeedLevel":
					STATS_INITIALIZED_NEED_LEVEL = reader.read_int(rs);
					break;
				case "WildBloodItemId":
					WILD_BLOOD_ITEM_ID = reader.read_int(rs);
					break;
				case "ElixirMaxUseCount":
					ELIXIR_MAX_USE_COUNT = reader.read_int(rs);
					break;
				case "ElixirMinUseLevel":
					ELIXIR_MIN_USE_LEVEL = reader.read_int(rs);
					break;
				case "ElixirByLevel":
					ELIXIR_BY_LEVEL = reader.read_int(rs);
					break;
				case "ElixirStrItemId":
					ELIXIR_STR_ITEM_ID = reader.read_int(rs);
					break;
				case "ElixirConItemId":
					ELIXIR_CON_ITEM_ID = reader.read_int(rs);
					break;
				case "ElixirIntItemId":
					ELIXIR_INT_ITEM_ID = reader.read_int(rs);
					break;
				case "NameChangedTicketItemId":
					NAME_CHANGED_ITEM_ID = reader.read_int(rs);
					break;
				case "ExpByMasterExp":
					EXP_BY_MASTER_EXP = reader.read_double_by_percent(rs);
					break;
				}
			}
		});
	}
}
