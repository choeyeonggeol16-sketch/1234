package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;
import java.util.ArrayList;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 필요 아이템 조건
 * 1 and_or(bool) / 2 count / 3 items(repeated msg: CraftRequiredItem)
 *
 *   (enchant/bless 등 추가 필드 있으면 아래 FIELD_* 상수와 parse/write에 추가)
 */
public class CraftRequiredItemList {

    private static final int ITEM_FIELD_NAME_ID	= 1;
    private static final int ITEM_FIELD_COUNT	= 2;

    private boolean _andOr;
    private int _count;
    private ArrayList<int[]> _items = new ArrayList<int[]>();	// [0]=nameId, [1]=count

    // getter/setter
    public boolean get_andOr(){ return _andOr; }
    public void set_andOr(boolean v){ _andOr = v; }
    public int get_count(){ return _count; }
    public void set_count(int v){ _count = v; }
    public ArrayList<int[]> get_items(){ return _items; }
    public void add_item(int nameId, int itemCount){
        _items.add(new int[]{ nameId, itemCount });
    }

    // ── 역직렬화 ──
    public static CraftRequiredItemList parse(ProtoInputStream in) throws IOException {
        CraftRequiredItemList l = new CraftRequiredItemList();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: l._andOr = in.readBool(); break;
            case 2: l._count = in.readInt32(); break;
            case 3: parseItem(in, l); break;
            default: in.skipField(tag); break;
            }
        }
        return l;
    }

    private static void parseItem(ProtoInputStream in, CraftRequiredItemList target) throws IOException {
        byte[] b = in.readBytes();
        ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
        int nameId = 0, itemCount = 0;
        while(!sub.isAtEnd()){
            int tag = sub.readTag();
            switch(tag >>> 3){
            case ITEM_FIELD_NAME_ID: nameId = sub.readInt32(); break;
            case ITEM_FIELD_COUNT: itemCount = sub.readInt32(); break;
            default: sub.skipField(tag); break;
            }
        }
        target.add_item(nameId, itemCount);
    }

    // ── 직렬화 ──
    public void writeTo(ProtoWriter w){
        w.bool(1, _andOr);
        w.int32(2, _count);
        for(int[] item : _items){
            w.msg(3);
            w.int32(ITEM_FIELD_NAME_ID, item[0]);
            w.int32(ITEM_FIELD_COUNT, item[1]);
            w.end();
        }
    }
}