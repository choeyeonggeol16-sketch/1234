package l1j.server.server.model.item.function;

import l1j.server.server.model.network.core.sc.packet.SC_Fishing;
import l1j.server.server.ActionCodes;
import l1j.server.server.Controller.FishingTimeController;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import static l1j.server.server.model.item.function.FishingUseRules.requiresCharge;
import static l1j.server.server.model.item.function.FishingUseRules.safeCharge;
import static l1j.server.server.model.item.function.FishingRodProfile.applyRodUiOnly;

final class FishingRodSession {
    private FishingRodSession() {}
    private static final int BAIT_ITEM_ID = 41295;

    static void doStartFishing(L1PcInstance pc, int fishX, int fishY, int itemObjectId) {
        final L1ItemInstance rod = pc.getInventory().GetByServerID(itemObjectId);
        if (rod == null) {
            SC_ServerMessage.send(pc, 1137);
            return;
        }

        final int rodId = rod.getItemId();

        if (requiresCharge(rodId) && safeCharge(rod) <= 0) {
            SC_ServerMessage.send(pc, 1137);
            return;
        }
        
         if (!isBaitlessRodFallback(rodId) && pc.getInventory().GetUnequippedItemAmount(BAIT_ITEM_ID) <= 0) {
             SC_ServerMessage.send(pc, 1137);
             return;
         }

        // Publish the rod only after charge and bait admission succeed.
        pc._fishingRod = rod;
        pc._fishingX = fishX;
        pc._fishingY = fishY;
        boolean started = false;
        try {
            SC_Fishing.send(pc, pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY);
            SC_Fishing.broadcast(pc, pc.getId(), ActionCodes.ACTION_Fishing, fishX, fishY);
            pc.setFishing(true);
            pc.setFishingReady(true);
            applyRodUiOnly(pc, rodId);
            started = FishingTimeController.getInstance().addMember(pc);
        } finally {
            // A failed UI write or rejected timer must not leave a rod or an
            // active fishing state attached to the character indefinitely.
            if (!started) FishingTimeController.getInstance().endFishing(pc);
        }
    }

    static boolean isBaitlessRodFallback(int rodId) {
        return rodId == 600229 || rodId == 87058 || rodId == 87059
                || rodId == 4100293 || rodId == 4200293;
    }
}
