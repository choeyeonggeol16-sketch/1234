package l1j.server.server.model.item.function;


import static l1j.server.server.model.item.function.CreatureItemActions.*;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.logging.Logger;

import l1j.server.server.IdFactory;
import l1j.server.server.datatables.PetTable;
import l1j.server.server.datatables.PetTypeTable;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1PetInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.cs.packet.CDB;
import l1j.server.server.model.network.core.cs.packet.User;
import l1j.server.server.model.network.core.sc.packet.SC_ItemName;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Npc;
import l1j.server.server.templates.L1Pet;

public final class PetVoucherTransactions {
    private static final Logger ITEM_USE2_LOG = CreatureItemActions.ITEM_USE2_LOG;
    private PetVoucherTransactions() {}

    public static void UsePetAmulet(CUserSocket client, L1ItemInstance input, int npcId) {
        final L1PcInstance pc = client == null ? null : client.getActiveChar();
        if (pc == null || input == null
                || pc.getInventory().GetByServerID(input.getId()) != input) return;
        final l1j.server.server.templates.L1Npc template =
                l1j.server.server.datatables.CObjectDB.LoadNPC().getTemplate(npcId);
        if (template == null || PetTypeTable.getInstance().get(npcId) == null) return;

        int charisma = pc.getAbility().getTotalCha();
        int petCost = 0;
        for (L1NpcInstance pet : pc.getPetList().values()) petCost += pet.getPetcost();
        if (pc.isElf()) charisma += 12;
        else if (pc.isCrown() || pc.isWizard() || pc.isDarkelf()
                || pc.isDragonknight() || pc.isBlackwizard()) charisma += 6;
        if ((charisma - petCost) / 6 <= 0) {
            SC_ServerMessage.send(pc, 489);
            return;
        }
        if (pc.getInventory().getSize() >= 180) {
            SC_ServerMessage.send(pc, 82);
            return;
        }

        try (l1j.server.server.model.L1InventoryTransaction tx =
                User.CheckAndSetPendingUseETCItem(pc,
                        CacheD.accountOrderKey("item-use:", pc.getId()))) {
            if (!tx.debit(input, 1)) return;
            final L1ItemInstance amulet = tx.grant(40314, 1);
            if (amulet == null) {
                SC_ServerMessage.send(pc, 82);
                return;
            }
            final l1j.server.server.templates.L1Pet pet = new l1j.server.server.templates.L1Pet();
            pet.set_itemobjid(amulet.getId());
            pet.set_objid(IdFactory.getInstance().nextId());
            pet.set_npcid(npcId);
            pet.set_name(template.get_nameid());
            pet.set_level(template.get_level());
            pet.set_hp(template.get_hp());
            pet.set_mp(template.get_mp());
            pet.set_exp(750);
            pet.set_lawful(0);

            tx.requireTables("pets");
            tx.sql(con -> {
                try (PreparedStatement insert = con.prepareStatement(
                        "INSERT INTO pets(item_obj_id,objid,npcid,name,lvl,hp,mp,exp,lawful) "
                        + "VALUES(?,?,?,?,?,?,?,?,?)")) {
                    insert.setInt(1, pet.get_itemobjid());
                    insert.setInt(2, pet.get_objid());
                    insert.setInt(3, pet.get_npcid());
                    insert.setString(4, pet.get_name());
                    insert.setInt(5, pet.get_level());
                    insert.setInt(6, pet.get_hp());
                    insert.setInt(7, pet.get_mp());
                    insert.setInt(8, pet.get_exp());
                    insert.setInt(9, pet.get_lawful());
                    if (insert.executeUpdate() != 1)
                        throw new SQLException("Pet voucher row insert failed");
                }
            });
            tx.onFailure(() -> SC_SystemMessage.send(pc, "펫 소환권 저장에 실패했습니다."));
            CDB.RequestUseItem(pc, tx, () -> {
                // Offline/relogin can recover the committed amulet and pets row
                // if the world pet could not be created after COMMIT.
                PetTable.getInstance().registerCommittedPet(pet);
                final L1PetInstance spawned = new L1PetInstance(template, pc, pet);
                spawned.setPetcost(6);
                final L1ItemInstance current = pc.getInventory().GetByServerID(amulet.getId());
                if (current != null) {
                    try {
                        SC_ItemName.send(pc, current);
                    } catch (RuntimeException notificationFailure) {
                        ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                                "Pet amulet item-name packet failed after commit: " + pc.getId(),
                                notificationFailure);
                    }
                }
            });
        } catch (RuntimeException failure) {
            ITEM_USE2_LOG.log(java.util.logging.Level.WARNING,
                    "Pet voucher prepare failed: " + pc.getId() + " / " + npcId, failure);
            SC_SystemMessage.send(pc, "펫 소환권을 처리하지 못했습니다.");
        }
    }
}
