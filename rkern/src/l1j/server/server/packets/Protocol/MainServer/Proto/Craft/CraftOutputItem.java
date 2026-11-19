package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 결과물
 * 1 name_id / 2 count / 3 slot / 4 enchant / 5 bless / 6 elemental_type / 7 elemental_level
 * 8 desc(string) / 9 system_desc / 10 broadcast_desc / 11 iconId / 12 url(string)
 * 13 extra_desc(bytes) / 14 inherit_enchant_from / 15 inherit_elemental_enchant_from / 16 event_id
 */
public class CraftOutputItem {

    private int _nameId;
    private int _count;
    private int _slot;
    private int _enchant;
    private int _bless;
    private int _elementalType;
    private int _elementalLevel;
    private String _desc;
    private int _systemDesc;
    private int _broadcastDesc;
    private int _iconId;
    private String _url;
    private byte[] _extraDesc;
    private int _inheritEnchantFrom;
    private int _inheritElementalEnchantFrom;
    private int _eventId;

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
    public int get_elementalType(){ return _elementalType; }
    public void set_elementalType(int v){ _elementalType = v; }
    public int get_elementalLevel(){ return _elementalLevel; }
    public void set_elementalLevel(int v){ _elementalLevel = v; }
    public String get_desc(){ return _desc; }
    public void set_desc(String v){ _desc = v; }
    public int get_systemDesc(){ return _systemDesc; }
    public void set_systemDesc(int v){ _systemDesc = v; }
    public int get_broadcastDesc(){ return _broadcastDesc; }
    public void set_broadcastDesc(int v){ _broadcastDesc = v; }
    public int get_iconId(){ return _iconId; }
    public void set_iconId(int v){ _iconId = v; }
    public String get_url(){ return _url; }
    public void set_url(String v){ _url = v; }
    public byte[] get_extraDesc(){ return _extraDesc; }
    public void set_extraDesc(byte[] v){ _extraDesc = v; }
    public int get_inheritEnchantFrom(){ return _inheritEnchantFrom; }
    public void set_inheritEnchantFrom(int v){ _inheritEnchantFrom = v; }
    public int get_inheritElementalEnchantFrom(){ return _inheritElementalEnchantFrom; }
    public void set_inheritElementalEnchantFrom(int v){ _inheritElementalEnchantFrom = v; }
    public int get_eventId(){ return _eventId; }
    public void set_eventId(int v){ _eventId = v; }

    public static CraftOutputItem parse(ProtoInputStream in) throws IOException {
        CraftOutputItem o = new CraftOutputItem();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: o._nameId = in.readInt32(); break;
            case 2: o._count = in.readInt32(); break;
            case 3: o._slot = in.readInt32(); break;
            case 4: o._enchant = in.readInt32(); break;
            case 5: o._bless = in.readInt32(); break;
            case 6: o._elementalType = in.readInt32(); break;
            case 7: o._elementalLevel = in.readInt32(); break;
            case 8: o._desc = in.readString(); break;
            case 9: o._systemDesc = in.readInt32(); break;
            case 10: o._broadcastDesc = in.readInt32(); break;
            case 11: o._iconId = in.readInt32(); break;
            case 12: o._url = in.readString(); break;
            case 13: o._extraDesc = in.readBytes(); break;
            case 14: o._inheritEnchantFrom = in.readInt32(); break;
            case 15: o._inheritElementalEnchantFrom = in.readInt32(); break;
            case 16: o._eventId = in.readInt32(); break;
            default: in.skipField(tag); break;
            }
        }
        return o;
    }

    public void writeTo(ProtoWriter w){
        w.int32(1, _nameId);
        w.int32(2, _count);
        w.int32(3, _slot);
        w.int32(4, _enchant);
        w.int32(5, _bless);
        w.int32(6, _elementalType);
        w.int32(7, _elementalLevel);
        w.string(8, _desc);
        w.int32(9, _systemDesc);
        w.int32(10, _broadcastDesc);
        w.int32(11, _iconId);
        w.string(12, _url);
        w.bytes(13, _extraDesc);
        w.int32(14, _inheritEnchantFrom);
        w.int32(15, _inheritElementalEnchantFrom);
        w.int32(16, _eventId);
    }
}