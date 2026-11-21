package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.util.Random;
import java.util.logging.Logger;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.utils.L1SpawnUtil;

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
