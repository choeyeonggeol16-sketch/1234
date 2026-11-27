package l1j.server.server.packets.Protocol.MainServer.Proto;

import java.util.ArrayList;
import java.util.List;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import l1j.server.server.IdFactory;
import l1j.server.server.datatables.CObjectDB;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.Instance.L1NpcInstance;
import l1j.server.server.model.CWorld;
import l1j.server.server.model.inventory.CUserInventory;
import l1j.server.server.model.inventory.L1InventoryTransaction;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.Craft;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftInputItemSlotInfo;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftInputList;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftOutputItem;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.CraftOutputItemResult;
import l1j.server.server.packets.Protocol.MainServer.Proto.Craft.eCraftMakeReqResultType;
import l1j.server.server.storage.CacheD;
import l1j.server.server.templates.L1Item;

public final class CS_CRAFT_MAKE_REQ {
    private CS_CRAFT_MAKE_REQ() {}

    private static final int MAX_SLOTS = 255;

    public static void handleRequest(CUserSocket client, ProtoReader r) {
        L1PcInstance pc = null;
        try {
            int npcId = 0, craftId = 0, count = 0;
            boolean hasNpc = false, hasCraft = false, hasCount = false;
            boolean slotsValid = true;
            List<CraftInputItemSlotInfo> slots = null;

            while (!r.isAtEnd()) {
                final int tag = r.tag();
                switch (tag) {
                case 0x08: npcId = r.varint32(); hasNpc = true; break;
                case 0x10: craftId = r.varint32(); hasCraft = true; break;
                case 0x18: count = r.varint32(); hasCount = true; break;
                case 0x22: {
                    final CraftInputItemSlotInfo s = parseSlot(r.subReader());
                    if (s == null) {
                        slotsValid = false;
                    } else {
                        if (slots == null) slots = new ArrayList<>();
                        if (slots.size() == MAX_SLOTS) slotsValid = false;
                        else slots.add(s);
                    }
                    break;
                }
                case 0x28: r.varint32(); break;
                default: r.skip(tag & 7); break;
                }
            }

            pc = client.getActiveChar();
            if (!CraftRequestAccess.active(client, pc)) return;
            if (pc.isItemTransferBusy() || pc.getTradeID() != 0) return;
            if (!hasNpc || !hasCraft || !hasCount || !slotsValid
                    || slots == null || slots.isEmpty()) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_INPUT.sendCachedMessage(pc);
                return;
            }
            if (l1j.server.server.model.shop.CEquipmentExchange.isOpen(pc) || pc.getIsChangeItem1() || pc.getIsChangeItem2() || pc.getIsChangeItem3() || pc.getIsChangeItem4() || pc.getIsChangeItem5()
                    || pc.getIsChangeItem6() || pc.getIsChangeItem7() || pc.getIsChangeItem8() || pc.getIsChangeItem9() || pc.getIsChangeItem10()
                    || pc.getIsChangeItem11() || pc.getIsChangeItem12() || pc.getIsChangeItem13() || pc.getIsChangeItem14() || pc.getIsChangeItem15()
                    || pc.getIsChangeItem16() || pc.getIsChangeItem17() || pc.getIsChangeItem18() || pc.getIsChangeItem19() || pc.getIsChangeItem20()) {
                SC_ChatPacket.sendDirect(pc, pc, "현재 상태에서는 제작할 수 없는 상태입니다.");
                return;
            }

            if (count <= 0 || count > CraftRequestAccess.MAX_COUNT) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_INPUT.sendCachedMessage(pc);
                return;
            }
            final L1NpcInstance npc = CraftRequestAccess.findNpc(npcId);
            if (npc == null) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_NPC.sendCachedMessage(pc);
                return;
            }
            if (!CraftRequestAccess.inRange(pc, npc)) {
                eCraftMakeReqResultType.RP_ERROR_NPC_OUT_OF_RANGE.sendCachedMessage(pc);
                return;
            }
            final Craft craft = CraftRequestAccess.recipe(npc, craftId);
            if (craft == null || craft.get_outputs() == null) {
                eCraftMakeReqResultType.RP_ERROR_CRAFT_DOES_NOT_EXIST.sendCachedMessage(pc);
                return;
            }
            final CraftInputList inputs = craft.get_inputs();
            final int probability = inputs == null ? -1 : inputs.validateSelection(slots);
            if (probability < 0) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_INPUT.sendCachedMessage(pc);
                return;
            }
            makeBatch(client, pc, npc, craft, slots, craftId, count, probability);
        } catch (Exception e) {
            e.printStackTrace();
            if (pc != null) {
                System.out.println(String.format("케릭터 이름 : %s", pc.getName()));
                eCraftMakeReqResultType.RP_ERROR_TOO_MANY_MAKE_REQ.sendCachedMessage(pc);
            }
        }
    }

    private static final class CraftAttempt {
        final CraftOutputItemResult output;
        final L1ItemInstance reward;
        CraftAttempt(CraftOutputItemResult output, L1ItemInstance reward) {
            this.output = output;
            this.reward = reward;
        }
    }

    /** A multi-count request is one durable input/output batch, never a series
     * of half-committed material removals. Only pre-existing inputs are used.
     */
    private static void makeBatch(CUserSocket session, L1PcInstance pc, L1NpcInstance npc, Craft craft,
            List<CraftInputItemSlotInfo> slots, int craftId, int count, int probability) {
        final int stamp = pc.getSessionStamp();
        final long npcIdentity = CWorld.getInstance().smartIdOf(npc);
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("craft:", pc.getId()))) {
            if (!CraftRequestAccess.stillAllowed(session, pc, stamp, npc, npcIdentity, craft)) return;
            final ArrayList<CraftAttempt> attempts = new ArrayList<>(count);
            final CUserInventory inventory = pc.getInventory();
            final Int2ObjectOpenHashMap<L1ItemInstance[]> candidates = new Int2ObjectOpenHashMap<>(slots.size());
            final Int2IntOpenHashMap available = new Int2IntOpenHashMap();
            for (CraftInputItemSlotInfo slot : slots) {
                final int descId = slot.get_item_name_id();
                if (candidates.containsKey(descId)) continue;
                final L1ItemInstance[] rows = inventory.GetItemListByDescID(descId);
                candidates.put(descId, rows);
                for (L1ItemInstance item : rows) available.put(item.getId(), item.getCount());
            }
            for (int attempt = 0; attempt < count; attempt++) {
                if (!prepareInputs(tx, slots, candidates, available)) {
                    eCraftMakeReqResultType.RP_ERROR_NO_REQUIRED_ITEM.sendCachedMessage(pc);
                    return;
                }
                final CraftOutputItemResult output = craft.get_outputs().createOutputItem(probability, craftId);
                if (output == null || (output.isSuccess && output.outputItem == null))
                    throw new IllegalStateException("Craft output configuration is missing");
                L1ItemInstance result = null;
                if (output.outputItem != null) {
                    CraftOutputItem outputItem = output.outputItem;
                    L1Item template = CObjectDB.LoadItem().findCraftCachedItem(outputItem);
                    if (template == null || outputItem.get_count() <= 0)
                        throw new IllegalStateException("Craft result template/count is invalid");
                    result = new L1ItemInstance();
                    result.setId(IdFactory.getInstance().nextId());
                    result.setItem(template);
                    result.setCount(outputItem.get_count());
                    result.setEnchantLevel(outputItem.get_enchant());
                    result.setBless(outputItem.get_bless());
                    result.setIdentified(true);
                    applyInheritance(result, outputItem, slots);
                    result = tx.grant(result);
                    if (result == null) throw new IllegalStateException("Craft result capacity exceeded");
                }
                attempts.add(new CraftAttempt(output, result));
            }
            if (!CraftRequestAccess.stillAllowed(session, pc, stamp, npc, npcIdentity, craft)) return;
            tx.onFailure(() -> eCraftMakeReqResultType.RP_ERROR_TOO_MANY_MAKE_REQ.sendCachedMessage(pc));
            tx.commit(() -> {
                for (CraftAttempt attempt : attempts) {
                    final eCraftMakeReqResultType code = attempt.output.isSuccess
                            ? eCraftMakeReqResultType.RP_SUCCESS : eCraftMakeReqResultType.RP_FAILURE;
                    if (attempt.output.isSuccess && attempt.reward != null
                            && ((craftId >= 10001 && craftId <= 10008)
                                || (craftId >= 10101 && craftId <= 10908))) {
                        String message = String.format("%s 님께서 (%s) \\f2제작에 성공하였습니다.",
                                pc.getName(), attempt.reward.getLogName());
                        SC_SystemMessage.broadcast(message);
                        SC_PacketBox.broadcastGreen(message);
                    }
                    SC_CRAFT_MAKE_ACK.send(pc, code,
                            attempt.output.isSuccess ? attempt.output.outputItem : null);
                    if (attempt.output.isSuccess) SC_SkillSound.send(pc, pc.getId(), 2029);
                }
            });
        }
    }

    /** Count exact matching instances across all slots/attempts. No selected
     * row can be spent twice, even when two recipe slots match the same item.
     */
    private static boolean prepareInputs(L1InventoryTransaction tx,
            List<CraftInputItemSlotInfo> slots,
            Int2ObjectOpenHashMap<L1ItemInstance[]> candidates, Int2IntOpenHashMap available) {
        for (CraftInputItemSlotInfo slot : slots) {
            slot.resetConsumedItem();
            int needed = slot.get_count();
            if (needed <= 0) return false;
            for (L1ItemInstance item : candidates.get(slot.get_item_name_id())) {
                if (needed == 0) break;
                if (item.isEquipped() || !slot.isInput(item)) continue;
                int remaining = available.get(item.getId());
                if (remaining <= 0) continue;
                int take = item.isStackable() ? Math.min(needed, remaining) : 1;
                if (!tx.debit(item, take)) return false;
                slot.recordConsumption(item);
                available.put(item.getId(), remaining - take);
                needed -= take;
            }
            if (needed != 0) return false;
        }
        return true;
    }

    private static void applyInheritance(L1ItemInstance reward, CraftOutputItem output,
            List<CraftInputItemSlotInfo> slots) {
        final int enchantSource = output.get_inheritEnchantFrom();
        final int elementSource = output.get_inheritElementalEnchantFrom();
        L1ItemInstance elementalItem = null;
        boolean hasExplicitElementSource = false;
        boolean hasEnchantSource = false;
        for (CraftInputItemSlotInfo slot : slots) {
            final L1ItemInstance consumed = slot.consumedItem();
            if (consumed == null) continue;
            final int desc = slot.get_item_name_id();
            if (!hasEnchantSource && enchantSource != 0 && desc == enchantSource) {
                reward.setEnchantLevel(consumed.getEnchantLevel());
                hasEnchantSource = true;
            }
            if (!hasExplicitElementSource && elementSource != 0 && desc == elementSource) {
                elementalItem = consumed;
                hasExplicitElementSource = true;
            } else if (!hasExplicitElementSource && output.get_nameId() == desc
                    && slot.get_elemental_enchant_type() != 0) {
                elementalItem = consumed;
            }
        }
        if (elementalItem != null) reward.setAttrEnchantLevel(elementalItem.getAttrEnchantLevel());
    }

    private static CraftInputItemSlotInfo parseSlot(ProtoReader r) {
        final CraftInputItemSlotInfo s = new CraftInputItemSlotInfo();
        while (!r.isAtEnd()) {
            final int tag = r.tag();
            switch (tag) {
            case 0x08: s.set_input_slot_no(r.varint32()); break;
            case 0x10: s.set_item_name_id(r.varint32()); break;
            case 0x18: s.set_count(r.varint32()); break;
            case 0x20: s.set_enchant(r.varint32()); break;
            case 0x28: s.set_elemental_enchant_type(r.varint32()); break;
            case 0x30: s.set_elemental_enchant_value(r.varint32()); break;
            case 0x38: s.set_bless(r.varint32()); break;
            default: r.skip(tag & 7); break;
            }
        }
        if (s.get_item_name_id() <= 0 || s.get_input_slot_no() < 0) return null;
        return s;
    }
}
