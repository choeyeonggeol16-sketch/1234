package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;

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

    /** Select one server-defined material per group. Optional groups may be omitted. */
    public int validateSelection(List<CraftInputItemSlotInfo> selections) {
        if (selections == null || selections.isEmpty() || _arr_input_item.isEmpty()) return -1;
        final int offset = selections.getFirst().get_input_slot_no() == 0 ? 1 : 0;
        final IntOpenHashSet chosen = new IntOpenHashSet(selections.size());
        long probability = 0;
        for (CraftInputItemSlotInfo selection : selections) {
            final int slot = selection.get_input_slot_no() + offset;
            if (slot <= 0 || !chosen.add(slot) || !selection.validAttributes()) return -1;
            CraftInputItem match = null;
            boolean required = false;
            for (CraftInputItem definition : _arr_input_item) {
                if (definition.get_slot() != slot) continue;
                required = true;
                if (selection.validation(definition)) {
                    match = definition;
                    break;
                }
            }
            if (!required) {
                for (CraftInputItem definition : _arr_option_item) {
                    if (definition.get_slot() == slot && selection.validation(definition)) {
                        match = definition;
                        break;
                    }
                }
            }
            if (match == null || match.get_count() <= 0) return -1;
            selection.applyDefinition(match);
            probability += match.get_increaseProb();
        }
        for (CraftInputItem input : _arr_input_item) {
            if (!chosen.contains(input.get_slot())) return -1;
        }
        return (int) Math.clamp(probability, 0, CraftOutputList.CRAFT_ITEM_SUCCESS_MILLIONS);
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