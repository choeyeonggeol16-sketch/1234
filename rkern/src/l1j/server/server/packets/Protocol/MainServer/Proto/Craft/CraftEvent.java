package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;
import java.util.LinkedList;

import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 이벤트 결과 그룹
 * 1 event_id / 2 prob_count / 3 non_prob_count
 * 4 output_prob_items(repeated msg) / 5 output_items(repeated msg)
 */
public class CraftEvent {

    private int _eventId;
    private int _probCount;
    private int _nonProbCount;
    private LinkedList<CraftOutputItem> _outputProbItems;
    private LinkedList<CraftOutputItem> _outputItems;

    // getter/setter
    public int get_eventId(){ return _eventId; }
    public void set_eventId(int v){ _eventId = v; }
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

    public static CraftEvent parse(ProtoInputStream in) throws IOException {
        CraftEvent e = new CraftEvent();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: e._eventId = in.readInt32(); break;
            case 2: e._probCount = in.readInt32(); break;
            case 3: e._nonProbCount = in.readInt32(); break;
            case 4: e.add_outputProbItem(CraftOutputItem.parse(in.readSlice())); break;
            case 5: e.add_outputItem(CraftOutputItem.parse(in.readSlice())); break;
            default: in.skipField(tag); break;
            }
        }
        return e;
    }

    public void writeTo(ProtoWriter w){
        w.int32(1, _eventId);
        w.int32(2, _probCount);
        w.int32(3, _nonProbCount);
        if(_outputProbItems != null){
            for(CraftOutputItem v : _outputProbItems){ w.msg(4); v.writeTo(w); w.end(); }
        }
        if(_outputItems != null){
            for(CraftOutputItem v : _outputItems){ w.msg(5); v.writeTo(w); w.end(); }
        }
    }
}