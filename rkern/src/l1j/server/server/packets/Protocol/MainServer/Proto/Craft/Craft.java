package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;
import java.util.Map;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 항목 본체 — craftinfo.dat 로드, 클라 전송은 ProtoWriter.
 * 와이어: 1 id / 2 craft_attr / 3 required_classes / 4 required_quests
 *        5 required_sprites / 6 required_items / 7 inputs / 8 outputs / 9 batch_delay_sec
 */
public class Craft {

    private int _id;
    private CraftAttr _attr;
    private int _requiredClasses;
    private CraftRequiredQuestList _requiredQuests;
    private CraftRequiredSpriteList _requiredSprites;
    private CraftRequiredItemList _requiredItems;
    private CraftInputList _inputs;
    private CraftOutputList _outputs;
    private int _batchDelaySec;

    private static volatile Map<Integer, Craft> crafts = Map.of();

    static void publish(Map<Integer, Craft> loaded) { crafts = java.util.Collections.unmodifiableMap(loaded); }
    public static Map<Integer, Craft> all() { return crafts; }

    /** LEN 필드 본문을 바이트로 뽑아서 서브 파서에 넘김 */
    static byte[] readSub(ProtoInputStream in) throws IOException {
        byte[] b = in.readBytes();
        return b == null ? new byte[0] : b;
    }

    // ── getter/setter ──
    public int get_id(){ return _id; }
    public void set_id(int v){ _id = v; }
    public CraftAttr get_attr(){ return _attr; }
    public void set_attr(CraftAttr v){ _attr = v; }
    public int get_requiredClasses(){ return _requiredClasses; }
    public void set_requiredClasses(int v){ _requiredClasses = v; }
    public CraftRequiredQuestList get_requiredQuests(){ return _requiredQuests; }
    public void set_requiredQuests(CraftRequiredQuestList v){ _requiredQuests = v; }
    public CraftRequiredSpriteList get_requiredSprites(){ return _requiredSprites; }
    public void set_requiredSprites(CraftRequiredSpriteList v){ _requiredSprites = v; }
    public CraftRequiredItemList get_requiredItems(){ return _requiredItems; }
    public void set_requiredItems(CraftRequiredItemList v){ _requiredItems = v; }
    public CraftInputList get_inputs(){ return _inputs; }
    public void set_inputs(CraftInputList v){ _inputs = v; }
    public CraftOutputList get_outputs(){ return _outputs; }
    public void set_outputs(CraftOutputList v){ _outputs = v; }
    public int get_batchDelaySec(){ return _batchDelaySec; }
    public void set_batchDelaySec(int v){ _batchDelaySec = v; }

    // ── 역직렬화 (craftinfo.dat) ──
    public static Craft parse(ProtoInputStream in) throws IOException {
        Craft c = new Craft();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: c._id = in.readInt32(); break;
            case 2: c._attr = CraftAttr.parse(in.readSlice()); break;
            case 3: c._requiredClasses = in.readInt32(); break;
            case 4: c._requiredQuests = CraftRequiredQuestList.parse(in.readSlice()); break;
            case 5: c._requiredSprites = CraftRequiredSpriteList.parse(in.readSlice()); break;
            case 6: c._requiredItems = CraftRequiredItemList.parse(in.readSlice()); break;
            case 7: c._inputs = CraftInputList.parse(in.readSlice()); break;
            case 8: c._outputs = CraftOutputList.parse(in.readSlice()); break;
            case 9: c._batchDelaySec = in.readInt32(); break;
            default: in.skipField(tag); break;
            }
        }
        return c;
    }
    
    public static Craft getCraft(int craftId){
        return all().get(craftId);
    }

    // ── 직렬화 ──
    public void writeTo(ProtoWriter w){
        w.int32(1, _id);
        if(_attr != null){ w.msg(2); _attr.writeTo(w); w.end(); }
        w.int32(3, _requiredClasses);
        if(_requiredQuests != null){ w.msg(4); _requiredQuests.writeTo(w); w.end(); }
        if(_requiredSprites != null){ w.msg(5); _requiredSprites.writeTo(w); w.end(); }
        if(_requiredItems != null){ w.msg(6); _requiredItems.writeTo(w); w.end(); }
        if(_inputs != null){ w.msg(7); _inputs.writeTo(w); w.end(); }
        if(_outputs != null){ w.msg(8); _outputs.writeTo(w); w.end(); }
        w.int32(9, _batchDelaySec);
    }
}