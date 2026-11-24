package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.item.L1ItemId;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;

public final class CS_Fix {

    private CS_Fix() {
    }

    public static void handle(
            CUserSocket client,
            int itemObjectId,
            int npcObjectId) {

        if (client == null) {
            return;
        }

        final L1PcInstance pc =
                client.getActiveChar();

        if (pc == null
                || pc.isGhost()) {

            return;
        }

        if (npcObjectId != 0) {

            repairItem(
                    pc,
                    itemObjectId,
                    npcObjectId);

            return;
        }

 
        withdrawPet(
                pc,
                itemObjectId);
    }

    private static void repairItem(
            L1PcInstance pc,
            int itemObjectId,
            int npcObjectId) {

        final L1Object object = L1World.getInstance().findObject(npcObjectId);
        if (!(object instanceof L1NpcInstance npc) || npc.getMapId() != pc.getMapId()
                || Math.abs(pc.getX() - npc.getX()) > 3 || Math.abs(pc.getY() - npc.getY()) > 3) return;

        final L1PcInventory inventory = pc.getInventory();
        if (!inventory.tryBeginEquipmentChange()) return;
        try {
            final L1ItemInstance item = inventory.getItem(itemObjectId);
            if (item == null || item.get_durability() <= 0) return;
            final int durability = item.get_durability();
            if (!inventory.consumeItem(L1ItemId.ADENA, durability * 200)) {
                SC_ServerMessage.send(pc, 189);
                return;
            }
            item.set_durability(0);
            inventory.updateDurability(item, durability);
        } finally {
            inventory.endEquipmentChange();
        }
    }

    private static void withdrawPet(
            L1PcInstance pc,
            int itemObjectId) {

        int petCost = 0;

        /*
         * 현재 소환되어 있는 펫들이 차지하는 CHA 비용 계산.
         */
        for (Object petObject :
                pc.getPetList().values()) {

            if (petObject
                    instanceof L1NpcInstance) {

                petCost +=
                        ((L1NpcInstance) petObject)
                                .getPetcost();
            }
        }

        int charisma =
                pc.getAbility()
                        .getTotalCha();

        if (pc.isElf()) {

            charisma += 12;

        } else {

            charisma += 6;
        }

        charisma -= petCost;

        final int petCount =
                charisma / 6;

        if (petCount <= 0) {

            SC_ServerMessage.send(pc, 489);

            return;
        }

        final L1Pet petTemplate =
                PetTable
                        .getInstance()
                        .getTemplate(
                                itemObjectId);

        if (petTemplate == null) {
            return;
        }

        final L1Npc npcTemplate =
                CObjectDB.GetNPC(
                                petTemplate.get_npcid());

        if (npcTemplate == null) {
            return;
        }

        final L1PetInstance pet =
                new L1PetInstance(
                        npcTemplate,
                        pc,
                        petTemplate);

        pet.setPetcost(6);
    }
}
