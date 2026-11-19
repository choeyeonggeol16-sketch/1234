package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.MJCompanion.Instance.MJCompanionInstanceCache;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1DollInstance;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.Instance.L1SummonInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1PetType;

public final class CS_GiveItem {

    private static final String[] RECEIVABLE_IMPLS = {
        "L1Npc", "L1Monster", "L1Guardian", "L1Teleporter", "L1Guard"
    };

    private CS_GiveItem() {
    }

    public static void handle(
            CIOClient client,
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
        final L1Inventory targetInv = target.getInventory();
        final L1Inventory inv = pc.getInventory();

        // 서버 좌표 기준: 같은 맵 + 인접 1칸 이내만 아이템 주기 허용.
        if (target.isDead()
                || target.getMapId() != pc.getMapId()
                || pc.getLocation().getTileLineDistance(target.getLocation()) > 2) {
            return;
        }

        L1ItemInstance item = inv.getItem(itemObjectId);

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

        if (!isNpcItemReceivable(target.getNpcTemplate())) {
            if (item.getItemId() != 40499 && item.getItemId() != 40507) {
                return;
            }
        }

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

        if (!MJCompanionInstanceCache.is_companion_oblivion(item.getId())) {
            SC_ServerMessage.send(pc, 210, item.getItem().getName());
            return;
        }

        for (Object petObject : pc.getPetList().values()) {
            if (petObject instanceof L1PetInstance) {
                final L1PetInstance pet = (L1PetInstance) petObject;

                if (pet != null && item.getId() == pet.getItemObjId()) {
                    SC_ServerMessage.send(pc, 210, item.getItem().getName());
                    return;
                }
            }
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
                && targetInv.checkAddItem(item, count) != L1Inventory.OK) {

            SC_ServerMessage.send(pc, 942);
            return;
        }

        final L1PetType petType =
                PetTypeTable.getInstance()
                        .get(target.getNpcTemplate().get_npcId());

        if (petType != null) {
            if ((petType.getBaseNpcId() == 46046
                    || petType.getItemIdForTaming() != 0)
                    && item.getItem().isUseHighPet()) {

                return;
            }
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
        target.getLight().turnOnOffLight();
        pc.getLight().turnOnOffLight();

        if (petType == null || target.isDead()) {
            return;
        }

        if (item.getItemId() == petType.getItemIdForTaming()
                && ((item.getItemId() == 490026
                        && target.getNpcTemplate().get_npcId() == 45711)
                    || (item.getItemId() == 490027
                        && target.getNpcTemplate().get_npcId() == 45313))) {

            if (item.getItemId() >= 490024
                    && item.getItemId() <= 490027) {

                int value =
                        ThreadLocalRandom.current().nextInt(100) + 1;

                value +=
                        (item.getItemId() == 490026
                                || item.getItemId() == 490027)
                                ? 20
                                : 0;

                if (value > 75) {
                    tamePet(pc, target);
                }
            }
        }

        if (item.getItemId() == petType.getItemIdForTaming()) {
            tamePet(pc, target);

        } else if (item.getItemId() == 40070
                && petType.getItemIdForTaming() != 0) {

            evolvePet(pc, target, item.getItemId());

        } else if (item.getItemId() == 41310
                && petType.canEvolve()
                && petType.getItemIdForTaming() == 0) {

            evolvePet(pc, target, item.getItemId());
        }
    }

    private static boolean isNpcItemReceivable(L1Npc npc) {
        for (String impl : RECEIVABLE_IMPLS) {
            if (npc.getImpl().equals(impl)) {
                return true;
            }
        }

        return false;
    }

    private static void tamePet(
            L1PcInstance pc,
            L1NpcInstance target) {

        if (target instanceof L1PetInstance
                || target instanceof L1SummonInstance) {

            return;
        }

        int petcost = 0;

        if (pc.getPetList() != null
                && !pc.getPetList().isEmpty()) {

            for (L1NpcInstance pet : pc.getPetList().values()) {
                if (pet != null) {
                    petcost += pet.getPetcost();
                }
            }
        }

        int charisma = pc.getAbility().getTotalCha();

        if (pc.isCrown()) {
            charisma += 6;
        } else if (pc.isElf()) {
            charisma += 12;
        } else if (pc.isWizard()
                || pc.isDarkelf()
                || pc.isDragonknight()
                || pc.isBlackwizard()) {

            charisma += 6;
        }

        charisma -= petcost;

        final L1PcInventory inv = pc.getInventory();
        final String npcname =
                target.getNpcTemplate().get_name();

        if (charisma >= 6 && inv.getSize() < 180) {
            if (isTamePet(target)) {
                final L1ItemInstance petamu =
                        inv.storeItem(40314, 1);

                if (petamu != null) {
                    new L1PetInstance(
                            target,
                            pc,
                            petamu.getId());

                    SC_ItemName.send(pc, petamu);
                    SC_SystemMessage.send(pc, npcname + "의 목걸이를 얻었습니다.");
                }

            } else {
                SC_ServerMessage.send(pc, 324);
            }
        }
    }

    private static void evolvePet(
            L1PcInstance pc,
            L1NpcInstance target,
            int itemId) {

        if (!(target instanceof L1PetInstance)) {
            return;
        }

        final L1PcInventory inv = pc.getInventory();
        final L1PetInstance pet = (L1PetInstance) target;
        final L1ItemInstance petamu =
                inv.getItem(pet.getItemObjId());

        final String npcname =
                target.getNpcTemplate().get_name();

        if (pet.getLevel() >= 30
                && pc == pet.getMaster()
                && petamu != null) {

            final L1ItemInstance highpetamu =
                    inv.storeItem(40316, 1);

            if (highpetamu != null) {
                pet.evolvePet(highpetamu.getId());
                SC_ItemName.send(pc, highpetamu);
                inv.removeItem(petamu, 1);
                SC_SystemMessage.send(pc, npcname + "의 진화에 성공 하였습니다.");
            }

        } else {
            SC_SystemMessage.send(pc, npcname + "의 진화조건이 충족되지 않았습니다.");
        }
    }

    private static boolean isTamePet(L1NpcInstance npc) {
        boolean isSuccess = false;

        final int npcId =
                npc.getNpcTemplate().get_npcId();

        if (npcId == 45313 || npcId == 45711) {
            isSuccess = true;
        } else if (npc.getMaxHp() / 2 > npc.getCurrentHp()) {
            isSuccess = true;
        }

        if (npcId == 45313
                || npcId == 45044
                || npcId == 45711) {

            if (npc.isResurrect()) {
                isSuccess = false;
            }
        }

        return isSuccess;
    }
}
