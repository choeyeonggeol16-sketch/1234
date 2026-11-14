package l1j.server.server.packets.Protocol.Packet;

import java.util.LinkedList;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;

import l1j.server.MJTemplate.MJL1Type;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.storage.CacheD;
import l1j.server.server.IdFactory;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Packet.Craft.Craft;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftInputItemSlotInfo;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftInputList;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItem;
import l1j.server.server.packets.Protocol.Packet.Craft.CraftOutputItemResult;
import l1j.server.server.packets.Protocol.Packet.Craft.eCraftMakeReqResultType;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;

import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

public final class CS_CRAFT_MAKE_REQ {
    private CS_CRAFT_MAKE_REQ() {}

    private static final int MAX_COUNT = 1000;

    public static void handleRequest(CIOClient client, ProtoReader r) {
        L1PcInstance pc = null;
        try {
            int npcId = 0, craftId = 0, count = 0;
            boolean hasNpc = false, hasCraft = false, hasCount = false;
            boolean slotsValid = true;
            LinkedList<CraftInputItemSlotInfo> slots = null;

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
                        if (slots == null) slots = new LinkedList<>();
                        slots.add(s);
                    }
                    break;
                }
                case 0x28: r.varint32(); break;
                default: r.skip(tag & 7); break;
                }
            }

            pc = client.getActiveChar();
            if (pc == null) return;
            if (pc.isItemTransferBusy() || pc.getTradeID() != 0) return;
            if (!hasNpc || !hasCraft || !hasCount || !slotsValid
                    || slots == null || slots.isEmpty()) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_INPUT.sendCachedMessage(pc);
                return;
            }
            final boolean zeroBasedSlots = slots.getFirst().get_input_slot_no() == 0;
            for (int i = 0, n = slots.size(); i < n; i++) {
                final CraftInputItemSlotInfo slot = slots.get(i);
                if (slot.get_input_slot_no() != i + (zeroBasedSlots ? 0 : 1)) {
                    eCraftMakeReqResultType.RP_ERROR_INVALID_INPUT.sendCachedMessage(pc);
                    return;
                }
                if (zeroBasedSlots) slot.set_input_slot_no(i + 1);
            }

            if (pc.getIsChangeItem1() || pc.getIsChangeItem2() || pc.getIsChangeItem3() || pc.getIsChangeItem4() || pc.getIsChangeItem5()
                    || pc.getIsChangeItem6() || pc.getIsChangeItem7() || pc.getIsChangeItem8() || pc.getIsChangeItem9() || pc.getIsChangeItem10()
                    || pc.getIsChangeItem11() || pc.getIsChangeItem12() || pc.getIsChangeItem13() || pc.getIsChangeItem14() || pc.getIsChangeItem15()
                    || pc.getIsChangeItem16() || pc.getIsChangeItem17() || pc.getIsChangeItem18() || pc.getIsChangeItem19() || pc.getIsChangeItem20()) {
                SC_ChatPacket.sendDirect(pc, pc, "현재 상태에서는 제작할 수 없는 상태입니다.");
                return;
            }

            if (count <= 0) {
                System.out.println(String.format("[(제작)제작 수량이 비정상적입니다.] 캐릭터이름 : %s, 제작아이디 : %d, 수량 : %d", pc.getName(), craftId, count));
                return;
            }
            if (count > MAX_COUNT) {
                System.out.println(String.format("[(제작)제작 수량 상한 초과 - 차단.] 캐릭터이름 : %s, 제작아이디 : %d, 수량 : %d", pc.getName(), craftId, count));
                return;
            }

            final L1Object obj = L1World.getInstance().findObject(npcId);
            if (obj == null || !obj.instanceOf(MJL1Type.L1TYPE_NPC)) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_NPC.sendCachedMessage(pc);
                return;
            }
            
            if(pc.getMapId() != obj.getMapId()) {
                eCraftMakeReqResultType.RP_ERROR_NPC_OUT_OF_RANGE.sendCachedMessage(pc);
                return;
            }

            if (Math.abs(pc.getX() - obj.getX()) > 15 || Math.abs(pc.getY() - obj.getY()) > 15) {
                eCraftMakeReqResultType.RP_ERROR_NPC_OUT_OF_RANGE.sendCachedMessage(pc);
                return;
            }

            final Craft craft = Craft.getCraft(craftId);
            if (craft == null) {
                eCraftMakeReqResultType.RP_ERROR_CRAFT_DOES_NOT_EXIST.sendCachedMessage(pc);
                System.out.println(String.format("[(제작)제작 아이디를 찾을 수 없습니다.] 캐릭터이름 : %s, 제작아이디 : %d", pc.getName(), craftId));
                return;
            }

            final CraftInputList inputs = craft.get_inputs();
            if (!inputs.has_arr_input_item()) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_INPUT.sendCachedMessage(pc);
                System.out.println(String.format("[(제작)제작 재료 아이템 리스트를 찾을 수 없습니다.] 캐릭터이름 : %s, 제작아이디 : %d", pc.getName(), craftId));
                return;
            }

            final int[] calcSlotAndIncreaseProb = inputs.calculateSlotAndIncreaseProb(slots);
            if (calcSlotAndIncreaseProb[0] - 1 != slots.size()) {
                eCraftMakeReqResultType.RP_ERROR_INVALID_INPUT.sendCachedMessage(pc);
                System.out.println(String.format("[(제작)재료 슬롯 정보가 일치하지 않습니다.] 캐릭터이름 : %s, 제작아이디 : %d, 슬롯번호 : %d, 유저가 보내온 패킷 슬롯 수 : %d", pc.getName(), craftId, calcSlotAndIncreaseProb[0] - 1, slots.size()));
                return;
            }

            makeBatch(pc, craft, slots, craftId, count, calcSlotAndIncreaseProb[1]);
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
    private static void makeBatch(L1PcInstance pc, Craft craft,
            LinkedList<CraftInputItemSlotInfo> slots, int craftId, int count, int probability) {
        final ArrayList<CraftAttempt> attempts = new ArrayList<>(count);
        final Map<Integer, Integer> available = new HashMap<>();
        for (L1ItemInstance item : pc.getInventory().getItems()) {
            if (item != null) available.put(item.getId(), item.getCount());
        }
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("craft:" + pc.getId()))) {
            for (int attempt = 0; attempt < count; attempt++) {
                final CraftOutputItemResult output = craft.get_outputs().createOutputItem(probability, craftId);
                if (output == null || (output.isSuccess && output.outputItem == null))
                    throw new IllegalStateException("Craft output configuration is missing");
                if (!prepareInputs(pc, tx, slots, available)) {
                    eCraftMakeReqResultType.RP_ERROR_NO_REQUIRED_ITEM.sendCachedMessage(pc);
                    return; // no SQL, memory, success packet, or material debit
                }
                L1ItemInstance result = null;
                if (output.outputItem != null) {
                    CraftOutputItem outputItem = output.outputItem;
                    L1Item template = ItemTable.getInstance().findCraftCachedItem(outputItem);
                    if (template == null || outputItem.get_count() <= 0)
                        throw new IllegalStateException("Craft result template/count is invalid");
                    result = new L1ItemInstance();
                    result.setId(IdFactory.getInstance().nextId());
                    result.setItem(template);
                    result.setCount(outputItem.get_count());
                    result.setEnchantLevel(outputItem.get_enchant());
                    result.setBless(outputItem.get_bless());
                    result.setIdentified(true);
                    int inherit = outputItem.get_inheritElementalEnchantFrom();
                    for (CraftInputItemSlotInfo slot : slots) {
                        if ((inherit != 0 && inherit == slot.get_item_name_id())
                                || outputItem.get_nameId() == slot.get_item_name_id()) {
                            int type = slot.get_elemental_enchant_type();
                            int value = slot.get_elemental_enchant_value();
                            if (type != 0 && value != 0)
                                result.setAttrEnchantLevel(L1ItemInstance.calculateElementalEnchant(type, value));
                            if (inherit == slot.get_item_name_id()) inherit = 0;
                        }
                    }
                    result = tx.grant(result);
                    if (result == null) throw new IllegalStateException("Craft result capacity exceeded");
                }
                attempts.add(new CraftAttempt(output, result));
            }
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
    private static boolean prepareInputs(L1PcInstance pc, L1InventoryTransaction tx,
            LinkedList<CraftInputItemSlotInfo> slots, Map<Integer, Integer> available) {
        for (CraftInputItemSlotInfo slot : slots) {
            int needed = slot.get_count();
            if (needed <= 0) return false;
            for (L1ItemInstance item : pc.getInventory().getItems()) {
                if (needed == 0) break;
                if (item == null || item.isEquipped() || !slot.isInput(item)) continue;
                int remaining = available.getOrDefault(item.getId(), 0);
                if (remaining <= 0) continue;
                int take = item.isStackable() ? Math.min(needed, remaining) : 1;
                if (!tx.debit(item, take)) return false;
                available.put(item.getId(), remaining - take);
                needed -= take;
            }
            if (needed != 0) return false;
        }
        return true;
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