package l1j.server.server.model.network.core.cs.packet;

import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_PetList;

import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import java.util.logging.Level;
import java.util.logging.Logger;

import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Pet;

final class NpcPetCommands {
    private NpcPetCommands() {}

    private static final Logger LOG = Logger.getLogger(NpcPetCommands.class.getName());

    static void petbuy(CUserSocket client, int npcId, int currencyId, int price) {
        final L1PcInstance pc = client.getActiveChar();
        if (pc == null || PetTypeTable.getInstance().get(npcId) == null) return;

        int charisma = pc.GetTotalStatCha();
        if (pc.isElf()) charisma += 12;
        else if (pc.isCrown() || pc.isWizard() || pc.isDarkelf()
                || pc.isDragonknight() || pc.isBlackwizard()) charisma += 6;
        var pets = pc.getPetList();
        // L1SmallIntMap's iteration and mutations use this same monitor.
        synchronized (pets) {
            for (L1NpcInstance pet : pets.values())
                if (pet instanceof L1PetInstance) charisma -= pet.getPetcost();
        }
        if (charisma < 6) {
            SC_ServerMessage.send(pc, 489);
            return;
        }

        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, npcId)) {
            if (!tx.debit(currencyId, price)) return;
            if (pc.getInventory().getSize() >= 180) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            L1NpcInstance target = CObjectDB.LoadNPC().newNpcInstance(npcId);
            if (!(target instanceof L1MonsterInstance) || target.isDead()) return;
            final L1ItemInstance amulet = tx.grant(40314, 1);
            if (amulet == null || amulet.isStackable()) return;
            final int amuletId = amulet.getId();
            final L1PetInstance pet = L1PetInstance.prepareNpcPurchase(target, pc, amuletId);
            final L1Pet record = PetTable.newPetRecord(target, pet.getId(), amuletId);
            PetTable.getInstance();
            tx.requireTables("pets");
            tx.sql(con -> PetTable.insertPet(con, record));
            tx.onDurableCommit(() -> PetTable.publishPurchasedPet(record));
            tx.onFailure(() -> SC_SystemMessage.send(pc, "펫 구매를 완료하지 못했습니다."));
            tx.commitAfterInventoryRelease(() -> {
                // The owner can summon from the committed amulet if live
                // world registration fails or the original connection closes.
                try { pet.enterPurchasedPetWorld(); }
                catch (RuntimeException failure) {
                    LOG.log(Level.WARNING, "Committed NPC pet could not enter the world: " + amuletId, failure);
                    SC_SystemMessage.send(pc, "아뮬렛으로 펫을 소환해 주세요.");
                }
                L1ItemInstance current = pc.getInventory().GetByServerID(amuletId);
                if (current != null) SC_ItemName.send(pc, current);
            });
        } catch (RuntimeException failure) {
            LOG.log(Level.WARNING, "NPC pet purchase failed: " + npcId, failure);
            SC_SystemMessage.send(pc, "펫 구매를 완료하지 못했습니다.");
        }
    }

    static void withdrawPets(L1PcInstance pc, int objid) {
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            final int id = item.getItem().getItemId();
            if (id == 40314 || id == 40316) {
                SC_PetList.send(pc, objid);
                return;
            }
        }
        SC_SystemMessage.send(pc, "보유중인 펫이 없습니다.");
    }
}
