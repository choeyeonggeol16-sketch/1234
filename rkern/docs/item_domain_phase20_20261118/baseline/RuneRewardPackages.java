package l1j.server.server.model.item.function;

import java.util.ArrayList;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1PcInstance;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageReward;
import static l1j.server.server.model.item.function.PackageRewardStaging.stageSealedReward;

/** Contents of sealed rune, rune pouch, and parchment packages; transaction ownership belongs to the caller. */
final class RuneRewardPackages {
    private RuneRewardPackages() {}

    static void stage(L1PcInstance pc, int itemId, L1InventoryTransaction tx, ArrayList<String> messages) {

        switch (itemId) {
            case 68088, 68089, 68090, 68091, 68092 -> SealedRuneLevel90Rewards.stage(pc, itemId, tx, messages);
            case 68083, 68084, 68085, 68086, 68087 -> SealedRuneLevel85Rewards.stage(pc, itemId, tx, messages);
            case 68078, 68079, 68080, 68081, 68082 -> SealedRuneLevel80Rewards.stage(pc, itemId, tx, messages);
            case 60041, 60042, 60043, 60044, 60045 -> SealedRuneLevel70Rewards.stage(pc, itemId, tx, messages);
            case 60036, 60037, 60038, 60039, 60040 -> SealedRuneLevel55Rewards.stage(pc, itemId, tx, messages);
            default -> { }
        }
    }
}
