package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;
import java.util.LinkedList;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 성공/실패 결과 리스트
 * 1 on_flag(msg) / 2 off_flag(msg) / 3 prob_count / 4 non_prob_count
 * 5 output_prob_items(repeated) / 6 output_items(repeated) / 7 events(repeated) / 8 event_count
 */
public class CraftOutputSFList {

    private long _onFlag1;
    private long _onFlag2;
    private long _offFlag1;
    private long _offFlag2;
    private int _probCount;
    private int _nonProbCount;
    private LinkedList<CraftOutputItem> _outputProbItems;
    private LinkedList<CraftOutputItem> _outputItems;
    private LinkedList<CraftEvent> _events;
    private int _eventCount;

    // getter/setter
    public long get_onFlag1(){ return _onFlag1; }
    public void set_onFlag1(long v){ _onFlag1 = v; }
    public long get_onFlag2(){ return _onFlag2; }
    public void set_onFlag2(long v){ _onFlag2 = v; }
    public long get_offFlag1(){ return _offFlag1; }
    public void set_offFlag1(long v){ _offFlag1 = v; }
    public long get_offFlag2(){ return _offFlag2; }
    public void set_offFlag2(long v){ _offFlag2 = v; }
    public int get_probCount(){ return _probCount; }
    public void set_probCount(int v){ _probCount = v; }
    public int get_nonProbCount(){ return _nonProbCount; }
    public void set_nonProbCount(int v){ _nonProbCount = v; }
    public LinkedList<CraftOutputItem> get_outputProbItems(){ return _outputProbItems; }
    public void add_outputProbItem(CraftOutputItem v){
        if(_outputProbItems == null) _outputProbItems = new LinkedList<CraftOutputItem>();
        _outputProbItems.add(v);
    }
    public LinkedList<CraftOutputItem> get_outputItems(){ return _outputItems; }
    public void add_outputItem(CraftOutputItem v){
        if(_outputItems == null) _outputItems = new LinkedList<CraftOutputItem>();
        _outputItems.add(v);
    }
    public LinkedList<CraftEvent> get_events(){ return _events; }
    public void add_event(CraftEvent v){
        if(_events == null) _events = new LinkedList<CraftEvent>();
        _events.add(v);
    }
    public int get_eventCount(){ return _eventCount; }
    public void set_eventCount(int v){ _eventCount = v; }

    // ── 역직렬화 (craftinfo.dat) ──
    public static CraftOutputSFList parse(ProtoInputStream in) throws IOException {
        CraftOutputSFList l = new CraftOutputSFList();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: parseQuestFlag(in, l, true); break;      // on_flag
            case 2: parseQuestFlag(in, l, false); break;     // off_flag
            case 3: l._probCount = in.readInt32(); break;
            case 4: l._nonProbCount = in.readInt32(); break;
            case 5: l.add_outputProbItem(CraftOutputItem.parse(in.readSlice())); break;
            case 6: l.add_outputItem(CraftOutputItem.parse(in.readSlice())); break;
            case 7: l.add_event(CraftEvent.parse(in.readSlice())); break;
            case 8: l._eventCount = in.readInt32(); break;
            default: in.skipField(tag); break;
            }
        }
        return l;
    }

    /** CraftQuestFlag(flag1/flag2) 서브메시지 파싱 — 별도 클래스 대신 인라인 */
    private static void parseQuestFlag(ProtoInputStream in, CraftOutputSFList target, boolean isOn) throws IOException {
        byte[] b = in.readBytes();
        ProtoInputStream sub = ProtoInputStream.newInstance(b == null ? new byte[0] : b);
        long f1 = 0, f2 = 0;
        while(!sub.isAtEnd()){
            int tag = sub.readTag();
            switch(tag >>> 3){
            case 1: f1 = sub.readInt64(); break;
            case 2: f2 = sub.readInt64(); break;
            default: sub.skipField(tag); break;
            }
        }
        if(isOn){
            target._onFlag1 = f1;
            target._onFlag2 = f2;
        }else{
            target._offFlag1 = f1;
            target._offFlag2 = f2;
        }
    }

    // ── 직렬화 ──
    public void writeTo(ProtoWriter w){
        w.msg(1);                                    // on_flag
        w.int64(1, _onFlag1);
        w.int64(2, _onFlag2);
        w.end();
        w.msg(2);                                    // off_flag
        w.int64(1, _offFlag1);
        w.int64(2, _offFlag2);
        w.end();
        w.int32(3, _probCount);
        w.int32(4, _nonProbCount);
        if(_outputProbItems != null){
            for(CraftOutputItem v : _outputProbItems){ w.msg(5); v.writeTo(w); w.end(); }
        }
        if(_outputItems != null){
            for(CraftOutputItem v : _outputItems){ w.msg(6); v.writeTo(w); w.end(); }
        }
        if(_events != null){
            for(CraftEvent v : _events){ w.msg(7); v.writeTo(w); w.end(); }
        }
        w.int32(8, _eventCount);
    }
}