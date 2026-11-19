package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 재료 아이템
 * 1 name_id / 2 count / 3 slot / 4 enchant / 5 bless / 6 desc(string)
 * 7 iconId / 8 elemental_enchant_type / 9 elemental_enchant_value
 * 10 increase_prob / 11 all_enchants_allowed(bool)
 */
public class CraftInputItem {

    private int _nameId;
    private int _count;
    private int _slot;
    private int _enchant;
    private int _bless;
    private String _desc;
    private int _iconId;
    private int _elementalEnchantType;
    private int _elementalEnchantValue;
    private int _increaseProb;
    private boolean _allEnchantsAllowed;

    // getter/setter
    public int get_nameId(){ return _nameId; }
    public void set_nameId(int v){ _nameId = v; }
    public int get_count(){ return _count; }
    public void set_count(int v){ _count = v; }
    public int get_slot(){ return _slot; }
    public void set_slot(int v){ _slot = v; }
    public int get_enchant(){ return _enchant; }
    public void set_enchant(int v){ _enchant = v; }
    public int get_bless(){ return _bless; }
    public void set_bless(int v){ _bless = v; }
    public String get_desc(){ return _desc; }
    public void set_desc(String v){ _desc = v; }
    public int get_iconId(){ return _iconId; }
    public void set_iconId(int v){ _iconId = v; }
    public int get_elementalEnchantType(){ return _elementalEnchantType; }
    public void set_elementalEnchantType(int v){ _elementalEnchantType = v; }
    public int get_elementalEnchantValue(){ return _elementalEnchantValue; }
    public void set_elementalEnchantValue(int v){ _elementalEnchantValue = v; }
    public int get_increaseProb(){ return _increaseProb; }
    public void set_increaseProb(int v){ _increaseProb = v; }
    public boolean get_allEnchantsAllowed(){ return _allEnchantsAllowed; }
    public void set_allEnchantsAllowed(boolean v){ _allEnchantsAllowed = v; }

    public static CraftInputItem parse(ProtoInputStream in) throws IOException {
        CraftInputItem i = new CraftInputItem();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: i._nameId = in.readInt32(); break;
            case 2: i._count = in.readInt32(); break;
            case 3: i._slot = in.readInt32(); break;
            case 4: i._enchant = in.readInt32(); break;
            case 5: i._bless = in.readInt32(); break;
            case 6: i._desc = in.readString(); break;
            case 7: i._iconId = in.readInt32(); break;
            case 8: i._elementalEnchantType = in.readInt32(); break;
            case 9: i._elementalEnchantValue = in.readInt32(); break;
            case 10: i._increaseProb = in.readInt32(); break;
            case 11: i._allEnchantsAllowed = in.readBool(); break;
            default: in.skipField(tag); break;
            }
        }
        return i;
    }
    

    public void writeTo(ProtoWriter w){
        w.int32(1, _nameId);
        w.int32(2, _count);
        w.int32(3, _slot);
        w.int32(4, _enchant);
        w.int32(5, _bless);
        w.string(6, _desc);
        w.int32(7, _iconId);
        w.int32(8, _elementalEnchantType);
        w.int32(9, _elementalEnchantValue);
        w.int32(10, _increaseProb);
        w.bool(11, _allEnchantsAllowed);
    }
}