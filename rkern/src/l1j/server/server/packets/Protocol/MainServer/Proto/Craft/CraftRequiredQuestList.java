package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 필요 퀘스트 조건
 * 1 and_or(bool) / 2 count / 3 flag(msg: flag1/flag2 int64)
 */
public class CraftRequiredQuestList {

    private boolean _andOr;
    private int _count;
    private long _flag1;
    private long _flag2;

    // getter/setter
    public boolean get_andOr(){ return _andOr; }
    public void set_andOr(boolean v){ _andOr = v; }
    public int get_count(){ return _count; }
    public void set_count(int v){ _count = v; }
    public long get_flag1(){ return _flag1; }
    public void set_flag1(long v){ _flag1 = v; }
    public long get_flag2(){ return _flag2; }
    public void set_flag2(long v){ _flag2 = v; }

    // ── 역직렬화 ──
    public static CraftRequiredQuestList parse(ProtoInputStream in) throws IOException {
        CraftRequiredQuestList q = new CraftRequiredQuestList();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: q._andOr = in.readBool(); break;
            case 2: q._count = in.readInt32(); break;
            case 3: parseFlag(in, q); break;
            default: in.skipField(tag); break;
            }
        }
        return q;
    }

    private static void parseFlag(ProtoInputStream in, CraftRequiredQuestList target) throws IOException {
        byte[] b = in.readBytes();
        ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
        while(!sub.isAtEnd()){
            int tag = sub.readTag();
            switch(tag >>> 3){
            case 1: target._flag1 = sub.readInt64(); break;
            case 2: target._flag2 = sub.readInt64(); break;
            default: sub.skipField(tag); break;
            }
        }
    }

    // ── 직렬화 ──
    public void writeTo(ProtoWriter w){
        w.bool(1, _andOr);
        w.int32(2, _count);
        w.msg(3);                       // flag 서브메시지
        w.int64(1, _flag1);
        w.int64(2, _flag2);
        w.end();
    }
}