package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.inventory.CInventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.character.companion.instance.CompanionInstanceCache;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;

public final class CS_GiveItem {

    private static final String[] RECEIVABLE_IMPLS = {
        "L1Npc", "L1Monster", "L1Guardian", "L1Teleporter", "L1Guard"
    };

    private CS_GiveItem() {
    }

    public static void handle(
            CUserSocket client,
            int targetId,
            int itemObjectId,
            int requestedCount) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc = client.getActiveChar();

        if (pc == null || (pc.isGhost() && !pc.isGm())) {
            return;
        }

        final L1Object object = L1World.getInstance().findObject(targetId);

        if (!(object instanceof L1NpcInstance)) {
            return;
        }

        final L1NpcInstance target = (L1NpcInstance) object;
        final CInventory targetInv = target.getInventory();
        final CInventory inv = pc.getInventory();

        // 서버 좌표 기준: 같은 맵 + 인접 1칸 이내만 아이템 주기 허용.
        if (target.isDead()
                || target.getMapId() != pc.getMapId()
                || pc.getLocation().getTileLineDistance(target.getLocation()) > 2) {
            return;
        }

        L1ItemInstance item = inv.GetByServerID(itemObjectId);

        if (target.getNpcTemplate().get_npcId() != 45032) {
            if (pc.getLevel() > 0 && !pc.isGm()) {
                SC_SystemMessage.send(pc, "모든 아이템은 교환으로 가능합니다.");
                return;
            }
        }

        if (item == null) {
            return;
        }

        if (target.getNpcTemplate().get_npcId() == 45032) {
            if (item.getItemId() == 40308
                    || item.getItemId() == 600254
                    || item.getItemId() == 400254) {

                return;
            }
        }

        if (!isNpcItemReceivable(target.getNpcTemplate())) return;

        if (item.isEquipped()) {
            SC_ServerMessage.send(pc, 141);
            return;
        }

        if (item.getBless() >= 128) {
            SC_ServerMessage.send(pc, 141);
            return;
        }

        if (item.getItemId() == 80500) {
            return;
        }

        if (item.getItemId() == 22229
                || item.getItemId() == 22230
                || item.getItemId() == 22231
                || item.getItemId() == 22215
                || item.getItemId() == 22216
                || item.getItemId() == 22217
                || item.getItemId() == 22218
                || item.getItemId() == 22219
                || item.getItemId() == 22220
                || item.getItemId() == 22221
                || item.getItemId() == 22225
                || item.getItemId() == 22222
                || item.getItemId() == 22226
                || item.getItemId() == 22223
                || item.getItemId() == 22227
                || item.getItemId() == 22224
                || item.getItemId() == 22228) {

            return;
        }

        // 패킷 값이 서버 상태와 다르면 접속을 끊지 않고 해당 요청만 폐기한다.
        if (itemObjectId != item.getId()) {
            return;
        }

        final int count = requestedCount;

        if (count <= 0 || item.getCount() <= 0) {
            return;
        }

        if (!item.isStackable() && count != 1) {
            return;
        }

        // 실제 보유량보다 큰 요청을 서버가 임의로 보정하지 않는다.
        if (count > item.getCount()) {
            return;
        }

        if (!item.getItem().isTradable() || item.getItemId() == 40308) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return;
        }

        if (!CompanionInstanceCache.is_companion_oblivion(item.getId())) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return;
        }

        final var companion = pc.get_companion();
        if (companion != null && companion.get_control_object_id() == item.getId()) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return;
        }

        for (Object dollObject : pc.getDollList().values()) {
            if (dollObject instanceof L1DollInstance) {
                final L1DollInstance doll = (L1DollInstance) dollObject;

                if (item.getId() == doll.getItemObjId()) {
                    SC_ServerMessage.send(pc, 210, item.getItem().getName());
                    return;
                }
            }
        }

        if (!pc.isGm()
                && targetInv.CheckAddable(item, count) != CInventory.OK) {

            SC_ServerMessage.send(pc, 942);
            return;
        }

        // 실제 소유권 이전이 성공한 뒤에만 NPC 후처리를 실행한다.
        final L1ItemInstance moved = inv.tradeItem(item, count, targetInv);
        if (moved == null) {
            return;
        }

        item = moved;

        if (!pc.isGm()) {
            item.setGiveItem(true);
        }

        target.onGetItem(item);
        target.refreshLight();
        pc.refreshLight();


    }

    private static boolean isNpcItemReceivable(L1Npc npc) {
        for (String impl : RECEIVABLE_IMPLS) {
            if (npc.getImpl().equals(impl)) {
                return true;
            }
        }

        return false;
    }

}
