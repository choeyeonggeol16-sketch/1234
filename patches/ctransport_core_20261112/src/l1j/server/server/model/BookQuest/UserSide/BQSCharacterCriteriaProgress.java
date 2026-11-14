package l1j.server.server.model.BookQuest.UserSide;

import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;
import LIN.CriteriaProgress;

/**
 * 캐릭터 도감 진행도 — 도메인 + DB 저장 코덱 (구 MJIProtoMessage 대체)
 * 저장 스키마 (BQSCharacterData blob field 2 서브메시지):
 *  [1]=current_achievement_level uint32, [2]=progress CriteriaProgress 서브메시지
 */
public class BQSCharacterCriteriaProgress {
    public static BQSCharacterCriteriaProgress newInstance(BQSInformation bqsInfo){
        BQSCharacterCriteriaProgress progress = new BQSCharacterCriteriaProgress();
        progress.set_current_ahcievement_level(0);
        progress.set_progress(LIN.CriteriaProgress.newInstance(bqsInfo.getCriteriaId()));
        return progress;
    }

    public static BQSCharacterCriteriaProgress newInstance(){
        return new BQSCharacterCriteriaProgress();
    }

    private int _current_ahcievement_level; 
    private LIN.CriteriaProgress _progress;
    private boolean _hasLevel;
    private boolean _hasProgress;

    private BQSCharacterCriteriaProgress(){}

    // ── DB 저장 코덱 (BQSCharacterData blob field 2) ──
    public void saveBody(ProtoWriter w){
        if(_hasLevel)
            w.uint32(1, _current_ahcievement_level);
        if(_hasProgress){
            w.msg(2);
            _progress.saveBody(w);
            w.end();
        }
    }

    /** DB blob → 도메인. 구로 저장된 기존 캐릭터 데이터도 그대로 읽힘 */
    public static BQSCharacterCriteriaProgress loadFrom(ProtoReader r){
        BQSCharacterCriteriaProgress p = new BQSCharacterCriteriaProgress();
        while(!r.isAtEnd()){
            int tag = r.tag();
            switch(tag){
            case (1 << 3) | 0:
                p.set_current_ahcievement_level(r.varint32());
                break;
            case (2 << 3) | 2:
                p.set_progress(LIN.CriteriaProgress.loadFrom(r.subReader()));
                break;
            default:
                r.skip(tag & 7);
            }
        }
        return p;
    }

    public int get_criteria_id(){
        return _progress.criteria_id();
    }

    public boolean onUpdateIsComplete(BQSInformation bqsInfo){
        long thisAchievementQuantity = bqsInfo.getBookStep(_current_ahcievement_level);
        long currentQuantity = _progress.quantity();
        if(currentQuantity >= thisAchievementQuantity)
            return false;

        return _progress.onUpdate() == thisAchievementQuantity;
    }

    public long getCurrentQuantity(){
        return _progress.quantity();
    }

    public void increase_current_achievement_level(){
        ++_current_ahcievement_level;
    }

    public int get_current_ahcievement_level(){
        return _current_ahcievement_level;
    }
    public void set_current_ahcievement_level(int current_ahcievement_level){
        _hasLevel = true;
        _current_ahcievement_level = current_ahcievement_level;
    }
    public boolean has_current_ahcievement_level(){
        return _hasLevel;
    }
    public LIN.CriteriaProgress get_progress(){
        return _progress;
    }
    public void set_progress(LIN.CriteriaProgress progress){
        _hasProgress = true;
        _progress = progress;
    }
    public boolean has_progress(){
        return _hasProgress;
    }

    public void dispose(){
        if(_progress != null){
            _progress.dispose();
            _progress = null;
        }
        _hasLevel = false;
        _hasProgress = false;
    }
}