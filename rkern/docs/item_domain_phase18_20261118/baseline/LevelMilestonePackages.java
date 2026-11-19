package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;

/** Original achievement item entrypoint. Receipt ownership remains with the caller. */
final class LevelMilestonePackages {
    private LevelMilestonePackages() {}
    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {
        switch (itemId) {
            case 714 -> Level82AchievementReward.stage(pc, tx, messages);
            case 715 -> Level84AchievementReward.stage(pc, tx, messages);
            case 716 -> Level86AchievementReward.stage(pc, tx, messages);
            case 717 -> Level88AchievementReward.stage(pc, tx, messages);
            case 721 -> Level89AchievementReward.stage(pc, tx, messages);
            case 722 -> Level90AchievementReward.stage(pc, tx, messages);
            default -> { }
        }
    }
}
