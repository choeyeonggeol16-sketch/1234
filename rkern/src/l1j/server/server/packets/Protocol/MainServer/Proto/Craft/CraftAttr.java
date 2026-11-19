package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 조건 속성
 * 1 desc / 2 min_level / 3 max_level / 4 required_gender / 5 min_align / 6 max_align
 * 7 min_karma / 8 max_karma / 9 max_count / 10 show(bool)
 */
public class CraftAttr {

    private int _desc;
    private int _minLevel;
    private int _maxLevel;
    private int _requiredGender;
    private int _minAlign;
    private int _maxAlign;
    private int _minKarma;
    private int _maxKarma;
    private int _maxCount;
    private boolean _show;

    // getter/setter
    public int get_desc(){ return _desc; }
    public void set_desc(int v){ _desc = v; }
    public int get_minLevel(){ return _minLevel; }
    public void set_minLevel(int v){ _minLevel = v; }
    public int get_maxLevel(){ return _maxLevel; }
    public void set_maxLevel(int v){ _maxLevel = v; }
    public int get_requiredGender(){ return _requiredGender; }
    public void set_requiredGender(int v){ _requiredGender = v; }
    public int get_minAlign(){ return _minAlign; }
    public void set_minAlign(int v){ _minAlign = v; }
    public int get_maxAlign(){ return _maxAlign; }
    public void set_maxAlign(int v){ _maxAlign = v; }
    public int get_minKarma(){ return _minKarma; }
    public void set_minKarma(int v){ _minKarma = v; }
    public int get_maxKarma(){ return _maxKarma; }
    public void set_maxKarma(int v){ _maxKarma = v; }
    public int get_maxCount(){ return _maxCount; }
    public void set_maxCount(int v){ _maxCount = v; }
    public boolean get_show(){ return _show; }
    public void set_show(boolean v){ _show = v; }

    public static CraftAttr parse(ProtoInputStream in) throws IOException {
        CraftAttr a = new CraftAttr();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: a._desc = in.readInt32(); break;
            case 2: a._minLevel = in.readInt32(); break;
            case 3: a._maxLevel = in.readInt32(); break;
            case 4: a._requiredGender = in.readInt32(); break;
            case 5: a._minAlign = in.readInt32(); break;
            case 6: a._maxAlign = in.readInt32(); break;
            case 7: a._minKarma = in.readInt32(); break;
            case 8: a._maxKarma = in.readInt32(); break;
            case 9: a._maxCount = in.readInt32(); break;
            case 10: a._show = in.readBool(); break;
            default: in.skipField(tag); break;
            }
        }
        return a;
    }

    public void writeTo(ProtoWriter w){
        w.int32(1, _desc);
        w.int32(2, _minLevel);
        w.int32(3, _maxLevel);
        w.int32(4, _requiredGender);
        w.int32(5, _minAlign);
        w.int32(6, _maxAlign);
        w.int32(7, _minKarma);
        w.int32(8, _maxKarma);
        w.int32(9, _maxCount);
        w.bool(10, _show);
    }
}