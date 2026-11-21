package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/** 제작 재료 목록 — field1=input(필수), field2=option(선택). server 원본 확인 완료 */
public class CraftInputList {

    private static final int FIELD_ARR_INPUT_ITEM  = 1;
    private static final int FIELD_ARR_OPTION_ITEM = 2;

    private ArrayList<CraftInputItem> _arr_input_item  = new ArrayList<CraftInputItem>();
    private ArrayList<CraftInputItem> _arr_option_item = new ArrayList<CraftInputItem>();

    public ArrayList<CraftInputItem> get_arr_input_item()  { return _arr_input_item; }
    public void add_arr_input_item(CraftInputItem v)       { _arr_input_item.add(v); }
    public boolean has_arr_input_item()                    { return !_arr_input_item.isEmpty(); }

    public ArrayList<CraftInputItem> get_arr_option_item() { return _arr_option_item; }
    public void add_arr_option_item(CraftInputItem v)      { _arr_option_item.add(v); }

    /** 유저 슬롯 검증 + 성공확률 증가치 계산 */
    public int[] calculateSlotAndIncreaseProb(LinkedList<CraftInputItemSlotInfo> slotInfo){
        int currentSlotNumber = 1;
        int increase_prob = 0;
        if (slotInfo == null || slotInfo.isEmpty()) return new int[]{0, 0};

        for (CraftInputItem input : _arr_input_item) {
            int slot = input.get_slot();
            if (currentSlotNumber < slot) {
                break;
            } else if (currentSlotNumber > slot)
                continue;

            if (currentSlotNumber > slotInfo.size()) break;
            CraftInputItemSlotInfo sInfo = slotInfo.get(currentSlotNumber - 1);
            if (sInfo.validation(input)) {
                increase_prob += input.get_increaseProb();
                sInfo.set_count(input.get_count());
                ++currentSlotNumber;
            }
        }
        return new int[]{currentSlotNumber, increase_prob};
    }

    // ── 역직렬화 ──
    public static CraftInputList parse(ProtoInputStream in) throws IOException {
        CraftInputList l = new CraftInputList();
        while (!in.isAtEnd()) {
            int tag = in.readTag();
            switch (tag >>> 3) {
            case FIELD_ARR_INPUT_ITEM:  l.add_arr_input_item(CraftInputItem.parse(in.readSlice())); break;
            case FIELD_ARR_OPTION_ITEM: l.add_arr_option_item(CraftInputItem.parse(in.readSlice())); break;
            default: in.skipField(tag); break;
            }
        }
        return l;
    }

    // ── 직렬화 ──
    public void writeTo(ProtoWriter w) {
        for (CraftInputItem v : _arr_input_item) {
            w.msg(FIELD_ARR_INPUT_ITEM);
            v.writeTo(w);
            w.end();
        }
        for (CraftInputItem v : _arr_option_item) {
            w.msg(FIELD_ARR_OPTION_ITEM);
            v.writeTo(w);
            w.end();
        }
    }
}