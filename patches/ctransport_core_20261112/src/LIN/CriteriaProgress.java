package LIN;

import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;


/**
 * CriteriaProgress — 도감 기준 진행도 (구 generated 대체: 도메인 + 서브메시지 코덱)
 * wire: [1]=criteria_id uint32, [2]=quantity uint64
 * 사용처: SC_CriteriaUpdateNoti, SC_AddCriteriaProgressBatch, BQSCharacterCriteriaProgress(DB blob)
 */
public final class CriteriaProgress {
    public static LIN.CriteriaProgress newInstance(int criteriaId){
        LIN.CriteriaProgress p = new LIN.CriteriaProgress();
        p.set_criteria_id(criteriaId);
        p.set_quantity(0L);
        return p;
    }

    public static LIN.CriteriaProgress newInstance(){
        return new LIN.CriteriaProgress();
    }

    private int _criteriaId;
    private long _quantity;
    private boolean _hasCriteriaId;
    private boolean _hasQuantity;

    private CriteriaProgress(){}

    public int criteria_id(){ return _criteriaId; }
    public void set_criteria_id(int val){ _hasCriteriaId = true; _criteriaId = val; }
    public boolean has_criteria_id(){ return _hasCriteriaId; }

    public long quantity(){ return _quantity; }
    public void set_quantity(long val){ _hasQuantity = true; _quantity = val; }
    public boolean has_quantity(){ return _hasQuantity; }

    public long onUpdate(){
        _quantity = _quantity + 1L;
        _hasQuantity = true;
        return _quantity;
    }

    // ── 서브메시지 코덱 ──
    public void saveBody(ProtoWriter w){
        if(_hasCriteriaId)
            w.uint32(1, _criteriaId);
        if(_hasQuantity)
            w.uint64(2, _quantity);
    }

    /** 구로 저장된 DB blob도 그대로 읽힘 (protobuf 표준) */
    public static LIN.CriteriaProgress loadFrom(ProtoReader r){
        LIN.CriteriaProgress p = new LIN.CriteriaProgress();
        while(!r.isAtEnd()){
            int tag = r.tag();
            switch(tag){
            case (1 << 3) | 0: p.set_criteria_id(r.varint32()); break;
            case (2 << 3) | 0: p.set_quantity(r.varint64()); break;
            default: r.skip(tag & 7);
            }
        }
        return p;
    }

    public void dispose(){
        _criteriaId = 0;
        _quantity = 0;
        _hasCriteriaId = false;
        _hasQuantity = false;
    }
}