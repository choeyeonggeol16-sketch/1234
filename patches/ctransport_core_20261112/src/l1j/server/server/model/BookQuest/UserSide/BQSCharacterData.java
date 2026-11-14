package l1j.server.server.model.BookQuest.UserSide;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.server.model.L1World;
import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.model.BookQuest.Loader.BQSCharacterDataLoader;
import l1j.server.server.model.BookQuest.Loader.BQSInformationLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSWQDecksLoader;
import l1j.server.server.model.BookQuest.Loader.BQSWQRewardsLoader;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;
import LIN.SC_ACHIEVEMENT_COMPLETE_NOTI;
import LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH;
import LIN.SC_ADD_CRITERIA_PROGRESS_BATCH;
import LIN.SC_CRITERIA_UPDATE_NOTI;
import LIN.SC_MONSTER_BOOK_V2_INFO_NOTI;
import LIN.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI;
import LIN.CompletedAchievement;
import LIN.CriteriaProgress;
import LIN.MonsterBookV2Info;
import LIN.eMonsterBookV2DeckState;

/**
 * 캐릭터 도감 데이터 — 도메인 모델 + DB 저장 코덱 + 와이어 발신
 *
 * 저장 포맷 (DB character_data 컬럼, body-only — 구와 바이트 호환):
 *  [1]=object_id uint32, [2]=BQSCharacterCriteriaProgress repeated msg,
 *  [3]=CompletedAchievement repeated msg, [4]=MonsterBookV2Info.DeckT repeated msg,
 *  [5]=decks_version int64
 */
public class BQSCharacterData {
	
    public static BQSCharacterData newInstance(ResultSet rs) throws Exception{
        return load(rs.getBytes("character_data"));
    }
    
    public static BQSCharacterData newInstance(){
        return new BQSCharacterData();
    }

    /** DB blob → 도메인. protobuf 표준 인코딩이라 구로 저장된 기존 데이터도 그대로 읽힘 */
    public static BQSCharacterData load(byte[] blob){
        BQSCharacterData d = new BQSCharacterData();
        if(blob == null || blob.length == 0)
            return d;
        d.loadFrom(new ProtoReader(blob, 0, blob.length));
        return d;
    }

    private int _object_id;
    private HashMap<Integer, BQSCharacterCriteriaProgress> _progresses;
    private HashMap<Integer, LIN.CompletedAchievement> _achievements;
    private ArrayList<LIN.MonsterBookV2Info.DeckT> _decks;
    private long _decks_version;
    private int _bit;   // 도메인 내부 has_* 용도로만 유지 (realloc_decks에서 사용)

    private BQSCharacterData(){}

    // ─────────────────────────────────────────────
    // 저장 코덱 — 구 MJIProtoMessage 완전 제거, 우리식
    // ─────────────────────────────────────────────

    private void loadFrom(ProtoReader r){
        while(!r.isAtEnd()){
            int tag = r.tag();
            switch(tag){
            case (1 << 3) | 0:                       // object_id varint
                _object_id = r.varint32();
                _bit |= 0x01;
                break;
            case (2 << 3) | 2: {                     // progress submessage
                ProtoReader sub = r.subReader();
                add_progresses(BQSCharacterCriteriaProgress.loadFrom(sub));
                break;
            }
            case (3 << 3) | 2:                       // achievement submessage
                add_achievements(readAchievement(r.subReader()));
                break;
            case (4 << 3) | 2: {                     // deck submessage
                ProtoReader sub = r.subReader();
                add_decks(LIN.MonsterBookV2Info.DeckT.loadFrom(sub));
                break;
            }
            case (5 << 3) | 0:                       // decks_version varint
                set_decks_version(r.varint64());
                break;
            default:
                r.skip(tag & 7);
            }
        }
        if (_decks != null) {
            for (int i = 0, n = _decks.size(); i < n; i++) {
                LIN.MonsterBookV2Info.DeckT deck = _decks.get(i);
                if (deck != null) deck.alignWireIndices(i);
            }
        }
    }

    /** CompletedAchievement submessage 파싱 — [1]id uint32, [2]time uint64, [3]get_reward bool */
    private static LIN.CompletedAchievement readAchievement(ProtoReader r){
        LIN.CompletedAchievement a = LIN.CompletedAchievement.newInstance();
        while(!r.isAtEnd()){
            int tag = r.tag();
            switch(tag){
            case (1 << 3) | 0: a.set_achievement_id(r.varint32()); break;
            case (2 << 3) | 0: a.set_completed_time(r.varint64()); break;
            case (3 << 3) | 0: a.set_get_reward(r.varint32() != 0); break;
            default: r.skip(tag & 7);
            }
        }
        return a;
    }

    /** 도메인 → DB body 직렬화. storeCharacterBqs가 이걸 저장 (body-only, 구와 동일 포맷) */
    public byte[] save(){
        ProtoWriter w = ProtoWriter.newInstance();
        if(has_object_id())
            w.uint32(1, _object_id);
        if(has_progresses()){
            for(BQSCharacterCriteriaProgress p : _progresses.values()){
                w.msg(2);
                p.saveBody(w);
                w.end();
            }
        }
        if(has_achievements()){
            for(LIN.CompletedAchievement a : _achievements.values()){
                w.msg(3);
                w.uint32(1, a.achievement_id())
                 .uint64(2, a.completed_time())
                 .bool(3, a.get_reward());
                w.end();
            }
        }
        if(has_decks()){
            for(LIN.MonsterBookV2Info.DeckT d : _decks){
                w.msg(4);
                d.saveBody(w);
                w.end();
            }
        }
        if(has_decks_version())
            w.int64(5, _decks_version);
        return w.bodyBytes();
    }

    // ─────────────────────────────────────────────
    // 도메인 로직 — 구와 100% 동일, 와이어 발신만 신버전
    // ─────────────────────────────────────────────

    public void onUpdate(BQSInformation bqsInfo){
        if(bqsInfo == null)
            return;
        onProgress(bqsInfo);
        onDeck(bqsInfo);
    }

  
    private void onDeck(BQSInformation bqsInfo){
        int difficulty = bqsInfo.getWeekDifficulty();
       // System.out.println("[T3] onDeck criteria=" + bqsInfo.getCriteriaId() + " weekDiff=" + difficulty);  
        if(difficulty < 0 || difficulty > 2)
            return;

        int criteria_id = bqsInfo.getCriteriaId();

        LIN.MonsterBookV2Info.DeckT deck = get_deck(difficulty);
        if(deck == null){
       //     System.out.println("[T5] deck NULL diff=" + difficulty);  
            return;
        }
        // Prefer the actual card criterion; legacy remapping only applies if
        // there is no exact card. Otherwise it updates the adjacent card.
        if (deck.findCard(criteria_id) == null) {
            criteria_id = remapForDeck(criteria_id);
        }
//        final StringBuilder cardList = new StringBuilder();
//        for(MonsterBookV2Info.DeckT.CardT c : deck.get_cards())
//            cardList.append(c.get_criteria_id()).append("(").append(c.get_amount()).append("/").append(c.get_required_amount()).append(") ");
//        System.out.println("[T6] deck=" + difficulty + " cards=[" + cardList + "]");
        
        L1PcInstance pc = (L1PcInstance)L1World.getInstance().findObject(_object_id);
        if(pc == null)
            return;

        if(deck.onUpdateIsComplete(pc, criteria_id)){
            deck.set_state(LIN.eMonsterBookV2DeckState.DS_COMPLETED);
            LIN.SC_MONSTER_BOOK_V2_UPDATE_STATE_NOTI.send(pc, deck);
            BQSCharacterDataLoader.storeCharacterBqs(pc.getBqs(), false);
        }
    }

    /**  주간퀘 카드 매칭 보정 —  */
    private static int remapForDeck(int criteria){
        switch(criteria){
        case 648: return 649;
        case 647: return 648;
        default:  return criteria;
        }
    }

    public BQSCharacterData send_decks_noti(L1PcInstance pc){
        LIN.MonsterBookV2Info v2Info = LIN.MonsterBookV2Info.newInstance();
        v2Info.set_system(BQSWQRewardsLoader.getInstance().get());
        v2Info.set_decks(_decks);
        LIN.SC_MONSTER_BOOK_V2_INFO_NOTI.send(pc, v2Info);
        return this;
    }

    
    private void onProgress(BQSInformation bqsInfo){
        int criteria_id = bqsInfo.getCriteriaId();
        BQSCharacterCriteriaProgress progress = get_progress(criteria_id);

        if(progress == null){
            progress = BQSCharacterCriteriaProgress.newInstance(bqsInfo);
            add_progresses(progress);
        }else if(progress.get_current_ahcievement_level() >= 3){
            return;
        }

        L1PcInstance pc = (L1PcInstance) L1World.getInstance().findObject(_object_id);

        // =========================================================
        // 몬스터 도감 단계 버그 확인 로그
        // =========================================================
        final int beforeLevel = progress.get_current_ahcievement_level();

//        System.out.println(
//            "[MONSTER_BOOK][BEFORE]"
//            + " criteria=" + criteria_id
//            + " baseAchievement=" + bqsInfo.getAchievementId()
//            + " level=" + beforeLevel
//        );

        boolean isComplete = progress.onUpdateIsComplete(bqsInfo);

        final int afterLevel = progress.get_current_ahcievement_level();

//        System.out.println(
//            "[MONSTER_BOOK][AFTER]"
//            + " criteria=" + criteria_id
//            + " baseAchievement=" + bqsInfo.getAchievementId()
//            + " level=" + beforeLevel + " -> " + afterLevel
//            + " complete=" + isComplete
//        );

        if(pc != null){
            LIN.SC_CRITERIA_UPDATE_NOTI.sendOf(pc, progress.get_progress());
        }

        if(isComplete){
        	
        	 System.out.println(
        		        "[MBOOK COMPLETE]"
        		        + " criteria=" + bqsInfo.getCriteriaId()
        		        + " base=" + bqsInfo.getAchievementId()
        		        + " currentLevel=" + progress.get_current_ahcievement_level()
        		        + " sendId="
        		        + (bqsInfo.getAchievementId()
        		           + progress.get_current_ahcievement_level())
        		    );
        	
        	
            final int achievementId =
                    bqsInfo.getAchievementId()
                    + progress.get_current_ahcievement_level();

//            System.out.println(
//                "[MONSTER_BOOK][COMPLETE]"
//                + " criteria=" + criteria_id
//                + " baseAchievement=" + bqsInfo.getAchievementId()
//                + " currentLevel=" + progress.get_current_ahcievement_level()
//                + " SEND achievementId=" + achievementId
//            );

            LIN.CompletedAchievement achievement =
                    LIN.CompletedAchievement.newInstance(achievementId);

            add_achievements(achievement);

            if(pc != null){
                LIN.SC_ACHIEVEMENT_COMPLETE_NOTI.sendOf(pc, achievement);
                BQSCharacterDataLoader.storeCharacterBqs(pc.getBqs(), false);
            }
        }
    }
    
 
    public BQSCharacterData send_bqs_batch(L1PcInstance pc){
        return send_bqs_batch(pc, true);
    }

    public BQSCharacterData send_bqs_batch(L1PcInstance pc, boolean persist){
        if (persist) BQSCharacterDataLoader.storeCharacterBqs(this, false);
    	  
        ArrayList<LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement> achievementBatch = new ArrayList<>();
        ArrayList<LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress> criteriaBatch = new ArrayList<>();

        if(has_progresses()){
            for(BQSCharacterCriteriaProgress val : get_progresses().values()){
                LIN.CriteriaProgress p = val.get_progress();

//                System.out.println(
//                    "[MONSTER_BOOK][BATCH_PROGRESS]"
//                    + " criteria=" + p.get_criteria_id()
//                    + " quantity=" + p.get_quantity()
//                    + " level=" + val.get_current_ahcievement_level()
//                );

                criteriaBatch.add(new LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.Progress(
                    p.criteria_id(), p.quantity()));
            }
        }

        if(has_achievements()){
            for(LIN.CompletedAchievement val : get_achievements().values()){

//                System.out.println(
//                    "[MONSTER_BOOK][BATCH_ACHIEVEMENT]"
//                    + " achievementId=" + val.get_achievement_id()
//                    + " reward=" + val.get_get_reward()
//                );

                achievementBatch.add(
                    LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.Achievement.of(val)
                );
            }
        }

        LIN.SC_ADD_COMPLETED_ACHIEVEMENT_BATCH.sendFields(pc, 0, 0, achievementBatch);
        LIN.SC_ADD_CRITERIA_PROGRESS_BATCH.sendFields(pc, 0, 0, criteriaBatch);

        return this;
    }

    // ─────────────────────────────────────────────
    // getter/setter — 순수 도메인 (has_* 유지)
    // ─────────────────────────────────────────────

    public int get_object_id(){
        return _object_id;
    }
    public void set_object_id(int id){
        _bit |= 0x01;
        _object_id = id;
    }
    public boolean has_object_id(){
        return (_bit & 0x01) == 0x01;
    }
    public BQSCharacterCriteriaProgress get_progress(int criteria_id){
        return has_progresses() ? _progresses.get(criteria_id) : null;
    }
    public HashMap<Integer, BQSCharacterCriteriaProgress> get_progresses(){
        return _progresses;
    }
    public void add_progresses(BQSCharacterCriteriaProgress progress){
        if(!has_progresses()){
            _progresses = new HashMap<Integer, BQSCharacterCriteriaProgress>(256);
            _bit |= 0x02;
        }
        _progresses.put(progress.get_criteria_id(), progress);
    }
    public boolean has_progresses(){
        return (_bit & 0x02) == 0x02;
    }
    public HashMap<Integer, LIN.CompletedAchievement> get_achievements(){
        return _achievements;
    }
    public LIN.CompletedAchievement get_achievement(int achievement_id){
        return has_achievements() ? _achievements.get(achievement_id) : null;
    }
    public void add_achievements(LIN.CompletedAchievement achievement){
        if(!has_achievements()){
            _achievements = new HashMap<Integer, LIN.CompletedAchievement>(32);
            _bit |= 0x04;
        }
        _achievements.put(achievement.achievement_id(), achievement);
    }
    public boolean has_achievements(){
        return (_bit & 0x04) == 0x04;
    }
    public LIN.MonsterBookV2Info.DeckT get_deck(int difficulty){
        return has_decks() ? _decks.get(difficulty) : null;
    }
    public ArrayList<LIN.MonsterBookV2Info.DeckT> get_decks(){
        return _decks;
    }
    public BQSCharacterData realloc_decks(L1PcInstance pc){
        if(!has_decks_version() || Math.abs(BQSLoadManager.BQS_UPDATE_CALENDAR.getTimeInMillis() - get_decks_version()) > 1000L){
            drain_decks(BQSWQDecksLoader.getInstance().getDecks(), pc.getLevel());
            set_decks_version(BQSLoadManager.BQS_UPDATE_CALENDAR.getTimeInMillis());
            BQSCharacterDataLoader.storeCharacterBqs(this, false);
        }
        return this;
    }
    public void drain_decks(ArrayList<LIN.MonsterBookV2Info.DeckT> decks, int level){
        ArrayList<LIN.MonsterBookV2Info.DeckT> replace_decks = new ArrayList<LIN.MonsterBookV2Info.DeckT>(BQSLoadManager.BQS_WQ_HEIGHT);
        for(LIN.MonsterBookV2Info.DeckT deck : decks)
            replace_decks.add(deck.deepCopy(level));
        set_decks(replace_decks);
    }
    public void set_decks(ArrayList<LIN.MonsterBookV2Info.DeckT> decks){
        ArrayList<LIN.MonsterBookV2Info.DeckT> old = _decks;
        _bit |= 0x08;
        _decks = decks;
        if (_decks != null) {
            for (int i = 0, n = _decks.size(); i < n; i++) {
                LIN.MonsterBookV2Info.DeckT deck = _decks.get(i);
                if (deck != null) deck.alignWireIndices(i);
            }
        }
        if(old != null){
            for(LIN.MonsterBookV2Info.DeckT deck : old)
                deck.dispose();
            old.clear();
        }
    }
    public void add_decks(LIN.MonsterBookV2Info.DeckT deck){
        if(!has_decks()){
            _decks = new ArrayList<LIN.MonsterBookV2Info.DeckT>(BQSLoadManager.BQS_WQ_HEIGHT);
            _bit |= 0x08;
        }
        if (deck != null) deck.alignWireIndices(_decks.size());
        _decks.add(deck);
    }
    public boolean has_decks(){
        return (_bit & 0x08) == 0x08;
    }
    public long get_decks_version(){
        return _decks_version;
    }
    public BQSCharacterData set_decks_version(long val){
        _bit |= 0x10;
        _decks_version = val;
        return this;
    }
    public boolean has_decks_version(){
        return (_bit & 0x10) == 0x10;
    }

    /** 자원 정리 — 서브메시지들의 dispose는 각 클래스 변환 후 재정비 */
    public void dispose(){
        if(has_progresses()){
            for(BQSCharacterCriteriaProgress val : _progresses.values())
                val.dispose();
            _progresses.clear();
            _progresses = null;
        }
        if(has_achievements()){
            _achievements.clear();
            _achievements = null;
        }
        if(has_decks()){
            for(LIN.MonsterBookV2Info.DeckT val : _decks)
                val.dispose();
            _decks.clear();
            _decks = null;
        }
        _bit = 0;
    }
}