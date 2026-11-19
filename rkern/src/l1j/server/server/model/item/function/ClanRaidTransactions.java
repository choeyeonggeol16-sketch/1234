package l1j.server.server.model.item.function;

import java.sql.Timestamp;
import java.util.concurrent.ThreadLocalRandom;
import l1j.server.Config;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1ItemDelay;
import l1j.server.server.datatables.NpcTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.L1SpawnUtil;
import static l1j.server.server.model.item.function.SpecialConsumableRouter.nowTimestamp;
import l1j.server.server.model.item.function.SpecialConsumableRouter.Result;

final class ClanRaidTransactions {
    private ClanRaidTransactions() {}
    private static final int RAID_PORTAL_LIFETIME_MS = 600_000;

    static Result useRaidItem(
            L1PcInstance pc,
            L1ItemInstance item,
            int requiredMapId,
            int teleportX,
            int teleportY,
            short teleportMapId,
            int portalNpcId,
            int bossX,
            int bossY,
            short bossMapId) {

        final int rnd = ThreadLocalRandom.current().nextInt(1000);
        if (!(rnd > 900 && pc.getMapId() == requiredMapId)) {
            pc.sendPackets("\\aH이곳이 아닌것 같다. 다른곳을 찾아보자");
            item.setLastUsed(nowTimestamp());
            return Result.RETURN;
        }

        final int[] bossIds = ClanRaidBossSelection.configuredBossIds(Config.clanraidmobboss);
        if (bossIds.length == 0) {
            SC_SystemMessage.send(pc, "혈맹 레이드 보스 설정이 비어 있습니다.");
            return Result.RETURN;
        }

        // Preserve the original boss selection and the entrance position.
        // The latter must not be read after a possibly deferred teleport.
        final int bossId = bossIds[ThreadLocalRandom.current().nextInt(100) % bossIds.length];
        if (NpcTable.getInstance().getTemplate(portalNpcId) == null
                || NpcTable.getInstance().getTemplate(bossId) == null) {
            SC_SystemMessage.send(pc, "혈맹 레이드 NPC 설정을 확인해 주세요.");
            return Result.RETURN;
        }
        final int portalX = pc.getX();
        final int portalY = pc.getY();
        final short portalMapId = (short) pc.getMapId();

        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, item.getId())) {
            if (!tx.debit(item, 1)) return Result.RETURN;
            // A stack retains its original item identity. Update its delay
            // within the same durable receipt, never on a failed use.
            if (item.getCount() > 1) {
                final Timestamp used = nowTimestamp();
                tx.change(item, image -> image.setLastUsed(used));
            }
            tx.onFailure(() -> SC_SystemMessage.send(pc, "레이드 아이템 사용이 확정되지 않았습니다."));
            tx.commitAfterInventoryRelease(() -> {
                pc.start_teleport(teleportX, teleportY, teleportMapId,
                        pc.getHeading(), 169, true, false);
                L1SpawnUtil.spawn2(portalX, portalY, portalMapId,
                        portalNpcId, 0, RAID_PORTAL_LIFETIME_MS, 0);
                ClanRaidNpcSpawn.spawn(bossX, bossY, bossMapId, 0, bossId, 0, 1);
                L1ItemDelay.onItemUse(pc, item);
                SC_SystemMessage.send(pc, "개방한 균열은 10분간 유지됩니다.");
            });
        } catch (IllegalStateException failure) {
            SC_SystemMessage.send(pc, "아이템 처리가 진행 중입니다. 다시 시도해 주세요.");
        }
        return Result.RETURN;
    }
}
