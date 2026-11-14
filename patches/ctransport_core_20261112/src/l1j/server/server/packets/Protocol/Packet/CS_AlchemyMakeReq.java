package l1j.server.server.packets.Protocol.Packet;

import java.util.LinkedList;

import l1j.server.Config;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.datatables.AlchemyProbability;
import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.packet.ServerPacket;
import l1j.server.server.model.network.packet.WritablePacket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;
import l1j.server.server.storage.CacheD;

/**
 * CS_ALCHEMY_MAKE_REQ (0x007C) — 인형합성 실행
 *  1 alchemy_id int32 [required]
 *  2 inputs     repeated Input { 1 slot_no, 2 item_name_id, 3 item_id }
 */
public final class CS_AlchemyMakeReq {
    private CS_AlchemyMakeReq() {}

    /** 합성 요청 슬롯 정보 */
    public static final class Input {
        private L1ItemInstance _temporaryRemovedItems;
        private int _slotNo;
        private int _itemNameId;
        private int _itemId;

        public int get_slot_no(){ return _slotNo; }
        public int get_item_name_id(){ return _itemNameId; }
        public int get_item_id(){ return _itemId; }

        public boolean isInput(L1ItemInstance item){
            return item.getId() == _itemId && item.getItem().getItemDescId() == _itemNameId;
        }
        public boolean hasRemoved(){ return _temporaryRemovedItems != null; }
        public L1ItemInstance getTemporaryRemovedItems(){ return _temporaryRemovedItems; }
        public void setTemporaryRemovedItems(L1ItemInstance item){ _temporaryRemovedItems = item; }

        @Override
        public String toString(){
            StringBuilder sb = new StringBuilder(32);
            sb.append("[slot:").append(_slotNo);
            sb.append(", desc:").append(_itemNameId);
            sb.append(", objectId:").append(_itemId).append("]");
            return sb.toString();
        }
    }

    /** 합성 판정 결과 — 인스턴스 단위 반환 */
    private static final class MakeResult {
        final L1Item template;
        final SC_AlchemyMakeAck.ResultCode resultCode;
        final int resultAlchemyId;

        MakeResult(L1Item template, SC_AlchemyMakeAck.ResultCode resultCode, int resultAlchemyId){
            this.template = template;
            this.resultCode = resultCode;
            this.resultAlchemyId = resultAlchemyId;
        }
    }

    public static void handle(CIOClient client, ProtoReader r) {
        // ── 파싱 (readBytes + 서브 리더 — 정석 서브메시지 파싱) ──
        int alchemyId = 0;
        boolean hasAlchemyId = false;
        LinkedList<Input> inputs = new LinkedList<>();
        LinkedList<Integer> filledSlots = new LinkedList<>();

        try {
            while (!r.isAtEnd()) {
                int tag = r.tag();
                switch (tag) {
                case 0x08:
                    alchemyId = r.varint32();
                    hasAlchemyId = true;
                    break;
                case 0x12: {
                    byte[] sub = r.readBytes();          // Input 본문 통째로
                    Input input = parseInput(sub);
                    if (input != null) {
                        filledSlots.add(input._slotNo);
                        inputs.add(input);
                    }
                    break;
                }
                default:
                    r.skip(tag & 7);
                    break;
                }
            }
        } catch (Exception e) {
            System.out.println("[CS_AlchemyMakeReq] parse fail: " + e);
            return;
        }

        if (!hasAlchemyId) return;

        L1PcInstance pc = client.getActiveChar();
        if (pc == null) return;
        if (pc.isItemTransferBusy() || pc.getTradeID() != 0 || inputs.isEmpty()) return;

        // ── 특수 상태 합성 차단 (MJ IsChangeItem 체크 그대로) ──
        if (pc.getIsChangeItem1() || pc.getIsChangeItem2() || pc.getIsChangeItem3() || pc.getIsChangeItem4() || pc.getIsChangeItem5() ||
            pc.getIsChangeItem6() || pc.getIsChangeItem7() || pc.getIsChangeItem8() || pc.getIsChangeItem9() || pc.getIsChangeItem10() ||
            pc.getIsChangeItem11() || pc.getIsChangeItem12() || pc.getIsChangeItem13() || pc.getIsChangeItem14() || pc.getIsChangeItem15() ||
            pc.getIsChangeItem16() || pc.getIsChangeItem17() || pc.getIsChangeItem18() || pc.getIsChangeItem19() || pc.getIsChangeItem20()) {
            SC_ChatPacket.sendDirect(pc, pc, "\\f3현재 상태에서는 인형합성을 할 수 없는 상태입니다.");
            SC_ChatPacket.sendDirect(pc, pc, "\\f3리스 후에 가능합니다.");
            return;
        }

        try {
            int designId = alchemyId - 1;
            SC_AlchemyDesignAck.Alchemy alchemy = SC_AlchemyDesignAck.getAlchemy(designId);
            if (alchemy == null) {
                toInvalidAlchemyId(pc, alchemyId);
                return;
            }

            int userInputsSize = inputs.size();
            if (alchemyId == 5) {
                SC_AlchemyDesignAck.InputList in1 = SC_AlchemyDesignAck.getInputList(designId);
                if (in1 == null || countInInputs(in1, inputs) < 1)
                    { toInvalidInput(pc, alchemyId, userInputsSize); return; }
                SC_AlchemyDesignAck.InputList in2 = SC_AlchemyDesignAck.getInputList(designId - 1);
                if (in2 == null || countInInputs(in2, inputs) != userInputsSize - 1)
                    { toInvalidInput(pc, alchemyId, userInputsSize); return; }
            } else if (alchemyId == 6 || alchemyId == 7) {
                SC_AlchemyDesignAck.InputList in1 = SC_AlchemyDesignAck.getInputList(designId - 3);
                if (in1 == null || countInInputs(in1, inputs) < 1)
                    { toInvalidInput(pc, alchemyId, userInputsSize); return; }
                SC_AlchemyDesignAck.InputList in2 = SC_AlchemyDesignAck.getInputList(designId - 4);
                if (in2 == null || countInInputs(in2, inputs) != userInputsSize - 1)
                    { toInvalidInput(pc, alchemyId, userInputsSize); return; }
            } else {
                SC_AlchemyDesignAck.InputList in1 = SC_AlchemyDesignAck.getInputList(designId);
                if (in1 == null || countInInputs(in1, inputs) != userInputsSize)
                    { toInvalidInput(pc, alchemyId, userInputsSize); return; }
            }

            int fillCount = pc.getInventory().fillAlchemyInputItem(new LinkedList<>(inputs));
            if (fillCount != userInputsSize) {
                toInvalidInputForInventory(pc, alchemyId, inputs);
                return;
            }

            MakeResult result = selectResult(alchemy, pc, alchemyId, inputs, filledSlots);
            int addCode = pc.getInventory().checkAddItem(result.template, 1);
            if (addCode != L1Inventory.OK) {
                doNotAllowInventory(pc, addCode);
                return;
            }

            makeTransaction(pc, inputs, result);
        } catch (Exception e) {
            System.out.println(String.format("[(인형합성)예외 정보.]케릭터 이름 : %s", pc.getName()));
            SC_AlchemyMakeAck.ResultCode.RC_ERROR_CURRENTLY_CLOSED.sendCachedMessage(pc);
            e.printStackTrace();
        }
    }
    
	public static final int CRAFT_ITEM_SUCCESS_MILLIONS = 1000000;

    // ── 판정 —──

    private static MakeResult selectResult(SC_AlchemyDesignAck.Alchemy alchemy, L1PcInstance pc,
            int alchemyId, LinkedList<Input> inputs, LinkedList<Integer> filledSlots) {
        // 실패 기본값: 재료 반환
        L1Item failTemplate = inputs.get(MJRnd.next(inputs.size())).getTemporaryRemovedItems().getItem();
        MakeResult fail = new MakeResult(failTemplate, SC_AlchemyMakeAck.ResultCode.RC_FAIL, alchemyId);

        // DB 확률 체크
        if (alchemyId < 6 && !AlchemyProbability.getInstance().is_winning(alchemyId, inputs.size()))
            return fail;

        filledSlots.sort((o1, o2) -> o1 - o2);

        SC_AlchemyDesignAck.Alchemy.Output output = alchemy.findOutput(filledSlots);
        if (output == null) {
            System.out.println(String.format("[(인형합성)Output을 찾을 수 없습니다.] 캐릭터 이름 : %s, 합성아이디 : %d", pc.getName(), alchemyId));
            return fail;
        }

        LinkedList<SC_AlchemyDesignAck.Alchemy.Output> hyperOutputs = output.get_hyper_outputs();
        SC_AlchemyMakeAck.ResultCode resultCode = SC_AlchemyMakeAck.ResultCode.RC_SUCCESS;

        // 대성공 판정 — 특수합성(5) 교체 제외 
        if (alchemyId < 6 && alchemyId != 5) {
            if (hyperOutputs != null && hyperOutputs.size() > 0
                    && MJRnd.isWinning(CRAFT_ITEM_SUCCESS_MILLIONS,
                            alchemyId >= 3 ? Config.ALCHEMY_HYPERSUCCESS_PROB_BYMILLION / 2
                                    : Config.ALCHEMY_HYPERSUCCESS_PROB_BYMILLION)) {
                output = hyperOutputs.get(MJRnd.next(hyperOutputs.size()));
                resultCode = SC_AlchemyMakeAck.ResultCode.RC_HYPER_SUCCESS;
            }
        } else {
            if (MJRnd.isWinning(CRAFT_ITEM_SUCCESS_MILLIONS,
                    alchemyId >= 3 ? Config.ALCHEMY_HYPERSUCCESS_PROB_BYMILLION / 2
                            : Config.ALCHEMY_HYPERSUCCESS_PROB_BYMILLION)) {
                resultCode = SC_AlchemyMakeAck.ResultCode.RC_HYPER_SUCCESS;
            }
        }
        L1Item selectItem = ItemTable.getInstance().getTemplate(
                AlchemyProbability.getInstance().select_id(output.get_output_list_id()));
        if (selectItem == null)
            return fail;

        // 특수합성 금지인형 최종 검증
        if (alchemyId == 5 && is특수합성금지인형(selectItem.getItemId()))
            return fail;

        return new MakeResult(selectItem, resultCode, output.get_output_list_id());
    }

    // [특수합성 안전장치] 절대 금지: 일반5단계 인형 / 드래곤슬레이어
    // [2026-07-29] 축복5단계(950013~950019) 차단 해제
    // [2026-07-30] 용인형(4100007~4100010) 차단 해제 - 운영자 지시(1% 출현)
    //              드래곤슬레이어(4200060)는 계속 절대금지
    private static boolean is특수합성금지인형(int itemId) {
        switch (itemId) {
        case 745: case 746: case 755: case 756: case 757:
        case 3000088: case 3000352:
        case 4200060:
            return true;
        }
        return false;
    }

    private static int countInInputs(SC_AlchemyDesignAck.InputList list, LinkedList<Input> inputs) {
        int count = 0;
        for (Input input : inputs) {
            if (list.get_item().containsKey(input.get_item_name_id()))
                ++count;
        }
        return count;
    }

    // ── 오류 알림 — MJ 그대로 ──

    private static void toInvalidAlchemyId(L1PcInstance pc, int alchemyId) {
        SC_AlchemyMakeAck.ResultCode.RC_ERROR_BLOCKED_ALCHEMY_ID.sendCachedMessage(pc);
        System.out.println(String.format("[(인형합성)Alchemy Id를 찾을 수 없습니다.] 캐릭터 이름 : %s, 합성아이디 : %d", pc.getName(), alchemyId));
    }

    private static void toInvalidInput(L1PcInstance pc, int alchemyId, int size) {
        SC_AlchemyMakeAck.ResultCode.RC_ERROR_INVALID_INPUT.sendCachedMessage(pc);
        System.out.println(String.format("[(인형합성)Inputs를 찾을 수 없습니다.] 캐릭터 이름 : %s, 합성아이디 : %d, 캐릭터인풋사이즈 : %d",
                pc.getName(), alchemyId, size));
    }

    private static void toInvalidInputForInventory(L1PcInstance pc, int alchemyId, LinkedList<Input> inputs) {
        SC_AlchemyMakeAck.ResultCode.RC_ERROR_INVALID_INPUT.sendCachedMessage(pc);
        System.out.println(String.format("[(인형합성)유저가 보내온 인형정보를 인벤토리에서 찾을 수 없습니다.] 캐릭터 이름 : %s, 합성아이디 : %d", pc.getName(), alchemyId));
        StringBuilder sb = new StringBuilder(256);
        for (Input input : inputs)
            sb.append(input.toString()).append("\r\n");
        System.out.println("-보내온 정보들-");
        System.out.print(sb.toString());
    }

    private static void doNotAllowInventory(L1PcInstance pc, int addCode) {
        (addCode == L1Inventory.WEIGHT_OVER
                ? SC_AlchemyMakeAck.ResultCode.RC_ERROR_WEIGHT_OVER
                : SC_AlchemyMakeAck.ResultCode.RC_ERROR_INVEN_OVER).sendCachedMessage(pc);
    }

    private static void makeTransaction(L1PcInstance pc, LinkedList<Input> inputs, MakeResult result) {
        // The selected outcome is immutable for the entire receipt. No item is
        // visible in the world and no success is sent before durable replay.
        try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc,
                CacheD.accountOrderKey("alchemy:" + pc.getId()))) {
            for (Input input : inputs) {
                L1ItemInstance material = input.getTemporaryRemovedItems();
                if (material == null || material.isEquipped()
                        || !tx.debit(material, material.getCount()))
                    throw new IllegalStateException("Alchemy input changed during preparation");
            }
            final L1ItemInstance reward = tx.grant(result.template.getItemId(), 1);
            if (reward == null) throw new IllegalStateException("Alchemy result could not be prepared");
            tx.commit(() -> doMake(pc, reward, result));
        }
    }

    // Called only after the input/result receipt and CIO inventory replay.
    private static void doMake(L1PcInstance pc, final L1ItemInstance resultItemInstance, final MakeResult result) {
        final int resultAlchemyId = result.resultAlchemyId;
        final SC_AlchemyMakeAck.ResultCode resultCode = result.resultCode;
        if (resultAlchemyId >= Config.ALCHEMY_NOTIFY_LEVEL
                && !SC_AlchemyMakeAck.ResultCode.RC_FAIL.equals(resultCode)) {
            String message = String.format("누군가가 %s 합성에 성공 하였습니다.", resultItemInstance.getName());
            SC_SystemMessage.broadcast(message);
            SC_PacketBox.broadcastGreen(message);
        }
        SC_AlchemyMakeAck.sendSuccess(pc, resultCode, resultItemInstance);
    }

    // ── 파싱: Input 본문 → 서브 리더 (정석 서브메시지 파싱) ──
    private static Input parseInput(byte[] sub) {
        ProtoReader subR = new ProtoReader(sub, 0, sub.length);
        Input input = new Input();
        boolean hasSlot = false, hasName = false, hasId = false;
        while (!subR.isAtEnd()) {
            int tag = subR.tag();
            switch (tag) {
            case 0x08:
                input._slotNo = subR.varint32();
                hasSlot = true;
                break;
            case 0x10:
                input._itemNameId = subR.varint32();
                hasName = true;
                break;
            case 0x18:
                input._itemId = subR.varint32();
                hasId = true;
                break;
            default:
                subR.skip(tag & 7);
                break;
            }
        }
        if (!hasSlot || !hasName || !hasId)
            return null;   // 필수 필드 누락 슬롯은 버림
        return input;
    }
}
