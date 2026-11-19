package l1j.server.server.model.item.function;


import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.network.core.sc.packet.SC_ActionUI2;
import l1j.server.server.utils.L1SpawnUtil;
import l1j.server.server.model.network.core.sc.packet.SC_OwnCharStatus2;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Random;
import java.util.logging.Logger;
import l1j.server.Config;
import l1j.server.server.storage.CacheD;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;

import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;

import l1j.server.server.model.network.core.sc.packet.SC_InventoryIcon;

import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;
import l1j.server.server.utils.MJCommons;


import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.cs.packet.CDB;

import static l1j.server.server.model.item.function.CreatureItemActions.*;

public final class BossSummonWandItems {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;

    // Immutable reward IDs: boss summon uses this table on every activation.
    private static final int[][] BOSS_SUMMON_TABLE = {
					{ 45573, 81201, 45609, 7320217, 7310015, 7310021, 7310028, 7310034, 7310041, 7310046, 7310051,
						7310056, 7310061, 7310066 },
					{ 45546, 45600, 45601, 7320072, 7320073, 7320074, 7320075, 7320076, 7320077, 7320078, 7320079,
							7320080, 7320081 },
					{ 5136, 5135, 5146, 45529, 7000093, 7310148, 7310154, 7310160, 45684 } };

    private BossSummonWandItems() {}

	public static void UseMobEventSpawnWand(L1PcInstance pc, L1ItemInstance item, Random random) {
		try {


			int category = 0;
			int rnd = random.nextInt(BOSS_SUMMON_TABLE[category].length);
			L1SpawnUtil.spawn(pc, BOSS_SUMMON_TABLE[category][rnd], 0, 0);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
