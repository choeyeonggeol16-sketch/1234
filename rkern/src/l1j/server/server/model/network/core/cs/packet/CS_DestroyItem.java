package l1j.server.server.model.network.core.cs.packet;

import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.monitor.Logger.ItemActionType;
import l1j.server.server.monitor.LoggerInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

public final class CS_DestroyItem {

    private CS_DestroyItem() {}


    public static void handle(CIOClient client, int length, int objectId, int count, int[] pairs) {
        if (client == null || length <= 0 || length > 100) return;

        final L1PcInstance pc = client.getActiveChar();
        if (pc == null || pc.isGhost()) return;

        if (length == 1) {
            if (count < 0) return;
            destroyOne(pc, objectId, count);
            return;
        }

        if (pairs == null || pairs.length != (length << 1)) return;

        // 여러 아이템 처리 도중 잘못된 count를 발견해 부분 삭제되는 것을 방지.
        for (int i = 1, end = pairs.length; i < end; i += 2) {
            if (pairs[i] < 0) return;
        }

        for (int i = 0, end = pairs.length; i < end; i += 2) {
            if (!destroyOne(pc, pairs[i], pairs[i + 1])) return;
        }
    }

    /** false: 금지된 아이템 발견. 원본처럼 나머지 항목 삭제를 즉시 중단. */
    private static boolean destroyOne(L1PcInstance pc, int itemObjectId, int requestedCount) {
        final L1ItemInstance item = pc.getInventory().getItem(itemObjectId);
        if (item == null) return true;

        if (!pc.isGm() && item.getItem().isCantDelete()) {
            SC_ServerMessage.send(pc, 125);
            return false;
        }

        if (!pc.isGm() && item.getBless() >= 128) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return false;
        }

        if (!MJCompanionInstanceCache.is_companion_oblivion(item.getId())) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return false;
        }

        for (Object petObject : pc.getPetList().values()) {
            if (petObject instanceof L1PetInstance) {
                final L1PetInstance pet = (L1PetInstance) petObject;
                if (item.getId() == pet.getItemObjId()) {
                    SC_ServerMessage.send(pc, 210, item.getItem().getName());
                    return false;
                }
            }
        }

        for (Object dollObject : pc.getDollList().values()) {
            if (dollObject instanceof L1DollInstance) {
                final L1DollInstance doll = (L1DollInstance) dollObject;
                if (item.getId() == doll.getItemObjId()) {
                    SC_ServerMessage.send(pc, 210, item.getItem().getName());
                    return false;
                }
            }
        }

        if (item.isEquipped()) {
            SC_ServerMessage.send(pc, 125);
            return false;
        }

        LoggerInstance.getInstance().addItemAction(ItemActionType.Delete, pc, item, requestedCount);

        final int removeCount = requestedCount == 0 ? item.getCount() : requestedCount;
        pc.getInventory().removeItem(item, removeCount);
        pc.getLight().turnOnOffLight();
        return true;
    }
}
