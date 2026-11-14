package l1j.server.server.packets.Protocol.Packet.MonsterBook;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.BookQuest.BQSInformation;
import l1j.server.server.model.BookQuest.BQSWQInformation;
import l1j.server.server.model.BookQuest.Loader.BQSInformationLoader;
import l1j.server.server.model.BookQuest.Loader.BQSLoadManager;
import l1j.server.server.model.BookQuest.Loader.BQSWQInformationLoader;
import l1j.server.server.model.BookQuest.Reward.BQSExpReward;
import l1j.server.server.model.BookQuest.Reward.BQSItemReward;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.packets.Protocol.ProtoReader;
import l1j.server.server.packets.Protocol.ProtoWriter;
import l1j.server.server.packets.Protocol.Packet.SC_MONSTER_BOOK_V2_REWARD_ACK;
import l1j.server.server.packets.Protocol.Packet.SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI;
import l1j.server.server.templates.L1Item;

/**
 * MonsterBookV2Info — 몬스터북 V2 스키마 트리 
 *
 * 역할 3개:
 *  1) 와이어: SC_MONSTER_BOOK_V2_INFO_NOTI의 info 서브메시지 (saveBody)
 *  2) DB 저장: BQSCharacterData character_data blob의 decks 필드 (DeckT.saveBody / DeckT.loadFrom)
 *  3) 도메인: 보상 지급(doReward), 덱 진행(onUpdateIsComplete), 덱 재생성(deepCopy)
 *
 * 와이어 스키마
 *  MonsterBookV2Info { SystemT system=1; repeated DeckT decks=2; }
 *  SystemT           { repeated RewardT rewards=2; repeated BonusRewardT bonus_rewards=3; uint32 min_level=4; }
 *  RewardT           { enum grade=1; int32 reward_exp=2; int32 item_name_id=3; repeated ItemRewardT item_rewards=4; uint32 high_level=5; uint32 high_reward_exp=6; }
 *  ItemRewardT       { int32 item_name_id=1; uint32 amount=2; }
 *  BonusRewardT      { enum grade=1; int32 reward_exp=2; int32 item_name_id=3; }
 *  DeckT             { uint32 index=1; enum state=3; repeated CardT cards=4; }  // field 2 없음 — 구 스키마 그대로
 *  CardT             { uint32 index=1; uint32 required_amount=2; uint32 achievement_id=3; uint32 criteria_id=4; uint32 amount=5; }
 */
public final class MonsterBookV2Info {
    public static MonsterBookV2Info newInstance(){
        return new MonsterBookV2Info();
    }
    private SystemT _system;
    private ArrayList<DeckT> _decks;
    private MonsterBookV2Info(){}

    public SystemT get_system(){
        return _system;
    }
    public void set_system(SystemT val){
        _system = val;
    }
    public boolean has_system(){
        return _system != null;
    }
    public ArrayList<DeckT> get_decks(){
        return _decks;
    }
    public void set_decks(ArrayList<DeckT> val){
        _decks = val;
    }
    public void add_decks(DeckT val){
        if(!has_decks())
            _decks = new ArrayList<DeckT>(8);
        _decks.add(val);
    }
    public boolean has_decks(){
        return _decks != null;
    }

    /** 서브메시지 직렬화 — INFO_NOTI에서 msg(1)로 감쌈 (구 writeTo 대응) */
    public void saveBody(ProtoWriter w){
        if(has_system()){
            w.msg(1);
            _system.saveBody(w);
            w.end();
        }
        if(has_decks()){
            for(DeckT val : _decks){
                w.msg(2);
                val.saveBody(w);
                w.end();
            }
        }
    }

    public void dispose(){
        if(_system != null){
            _system.dispose();
            _system = null;
        }
        if(_decks != null){
            for(DeckT val : _decks)
                val.dispose();
            _decks.clear();
            _decks = null;
        }
    }

    // ═══════════════════════════════════════════════════════════
    // SystemT — 보상 시스템 (DB 로더가 ResultSet으로 조립, 와이어로는 system=1로 감쌈)
    // ═══════════════════════════════════════════════════════════
    public static final class SystemT {
        public static SystemT newInstance(){
            return new SystemT();
        }
        private HashMap<Integer, RewardT> _rewards;
        private ArrayList<BonusRewardT> _bonus_rewards;
        private int _min_level;
        private boolean _has_min_level;
        private SystemT(){}

        public SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode doReward(L1PcInstance pc, eMonsterBookV2RewardGrade grade){
            RewardT reward = get_rewards(grade.toInt());
            return reward == null ?
            		SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL :
                    reward.doReward(pc);
        }

        public HashMap<Integer, RewardT> get_rewards(){
            return _rewards;
        }
        public RewardT get_rewards(eMonsterBookV2RewardGrade grade){
            return get_rewards(grade.toInt());
        }
        public RewardT get_rewards(int grade){
            return has_rewards() ? _rewards.get(grade) : null;
        }
        public void add_rewards(RewardT val){
            if(!has_rewards())
                _rewards = new HashMap<Integer, RewardT>(4);
            _rewards.put(val.get_grade().toInt(), val);
        }
        public boolean has_rewards(){
            return _rewards != null;
        }
        public ArrayList<BonusRewardT> get_bonus_rewards(){
            return _bonus_rewards;
        }
        public void add_bonus_rewards(BonusRewardT val){
            if(!has_bonus_rewards())
                _bonus_rewards = new ArrayList<BonusRewardT>(3);
            _bonus_rewards.add(val);
        }
        public boolean has_bonus_rewards(){
            return _bonus_rewards != null;
        }
        public int get_min_level(){
            return _min_level;
        }
        public void set_min_level(int val){
            _has_min_level = true;
            _min_level = val;
        }
        public boolean has_min_level(){
            return _has_min_level;
        }

        public void saveBody(ProtoWriter w){
            if(has_rewards()){
                for(RewardT val : _rewards.values()){
                    w.msg(2);
                    val.saveBody(w);
                    w.end();
                }
            }
            if(has_bonus_rewards()){
                for(BonusRewardT val : _bonus_rewards){
                    w.msg(3);
                    val.saveBody(w);
                    w.end();
                }
            }
            if(_has_min_level){
                w.uint32(4, _min_level);
            }
        }

        public void dispose(){
            if(has_rewards()){
                for(RewardT val : _rewards.values())
                    val.dispose();
                _rewards.clear();
                _rewards = null;
            }
            if(has_bonus_rewards()){
                for(BonusRewardT val : _bonus_rewards)
                    val.dispose();
                _bonus_rewards.clear();
                _bonus_rewards = null;
            }
        }

        // ═══════════════════════════════════════════════════════
        // RewardT
        // ═══════════════════════════════════════════════════════
        public static final class RewardT {
            public static RewardT newInstance(ResultSet rs) throws SQLException{
                RewardT reward = RewardT.newInstance();
                reward.set_grade(eMonsterBookV2RewardGrade.fromInt(rs.getInt("reward_grade")));
                reward.set_reward_exp(rs.getInt("reward_exp_percent") * BQSLoadManager.BQS_WQ_STD_EXP);
                reward.set_item_id(rs.getInt("reward_need_item_id"));

                int reward_exp = reward.get_reward_exp();
                if(reward_exp > 0)
                    reward.set_bqsexp_reward(BQSExpReward.newInstance(reward.get_grade().toInt(), reward_exp));
                return reward;
            }

            public static RewardT newInstance(){
                return new RewardT();
            }
            private eMonsterBookV2RewardGrade _grade;
            private BQSExpReward _bqsexp_reward;
            private int _reward_exp;
            private int _item_id;
            private int _item_name_id;
            private ArrayList<ItemRewardT> _item_rewards;
            private int _high_level;
            private int _high_reward_exp;
            private int _bit;  
            private RewardT(){}

            public SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode doReward(L1PcInstance pc){
                if(_item_id > 0 && !pc.getInventory().consumeItem(_item_id, 1)){
                    return SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_FAIL_NOT_ENOUGH_ITEM;
                }
                if(_bqsexp_reward != null)
                    _bqsexp_reward.doReward(pc);

                if(has_item_rewards()){
                    for(ItemRewardT reward : _item_rewards)
                        reward.doReward(pc);
                }
                return SC_MONSTER_BOOK_V2_REWARD_ACK.eResultCode.REWARD_SUCCESS;
            }

            public void set_bqsexp_reward(BQSExpReward reward){
                _bqsexp_reward = reward;
            }

            public void set_item_id(int val){
                _item_id = val;
                if(_item_id != 0){
                    L1Item item = ItemTable.getInstance().getTemplate(_item_id);
                    if(item == null){
                        System.out.println(String.format("[도감]아이템 테이블에서 아이템을 찾을 수 없습니다. 아이템아이디 : %d", _item_id));
                        set_item_name_id(0);
                    }else{
                        set_item_name_id(item.getItemDescId());
                    }
                }else{
                    set_item_name_id(0);
                }
            }

            public int get_item_id(){
                return _item_id;
            }

            public eMonsterBookV2RewardGrade get_grade(){
                return _grade;
            }
            public void set_grade(eMonsterBookV2RewardGrade val){
                _bit |= 0x1;
                _grade = val;
            }
            public boolean has_grade(){
                return (_bit & 0x1) == 0x1;
            }
            public int get_reward_exp(){
                return _reward_exp;
            }
            public void set_reward_exp(int val){
                _bit |= 0x2;
                _reward_exp = val;
            }
            public boolean has_reward_exp(){
                return (_bit & 0x2) == 0x2;
            }
            public int get_item_name_id(){
                return _item_name_id;
            }
            public void set_item_name_id(int val){
                _bit |= 0x4;
                _item_name_id = val;
            }
            public boolean has_item_name_id(){
                return (_bit & 0x4) == 0x4;
            }
            public ArrayList<ItemRewardT> get_item_rewards(){
                return _item_rewards;
            }
            public void add_item_rewards(ItemRewardT val){
                if(!has_item_rewards())
                    _item_rewards = new ArrayList<ItemRewardT>(8);
                _item_rewards.add(val);
            }
            public boolean has_item_rewards(){
                return _item_rewards != null;
            }
            public int get_high_level(){
                return _high_level;
            }
            public void set_high_level(int val){
                _bit |= 0x10;
                _high_level = val;
            }
            public boolean has_high_level(){
                return (_bit & 0x10) == 0x10;
            }
            public int get_high_reward_exp(){
                return _high_reward_exp;
            }
            public void set_high_reward_exp(int val){
                _bit |= 0x20;
                _high_reward_exp = val;
            }
            public boolean has_high_reward_exp(){
                return (_bit & 0x20) == 0x20;
            }

            public void saveBody(ProtoWriter w){
                if(has_grade())
                    w.enumV(1, _grade.toInt());
                if(has_reward_exp())
                    w.int32(2, _reward_exp);
                if(has_item_name_id())
                    w.int32(3, _item_name_id);
                if(has_item_rewards()){
                    for(ItemRewardT val : _item_rewards){
                        w.msg(4);
                        val.saveBody(w);
                        w.end();
                    }
                }
                if(has_high_level())
                    w.uint32(5, _high_level);
                if(has_high_reward_exp())
                    w.uint32(6, _high_reward_exp);
            }

            public void dispose(){
                if(has_item_rewards()){
                    _item_rewards.clear();
                    _item_rewards = null;
                }
                _bit = 0;
            }

            // ═══════════════════════════════════════════════════
            // ItemRewardT
            // ═══════════════════════════════════════════════════
            public static final class ItemRewardT {
                public static ItemRewardT newInstance(ResultSet rs) throws SQLException{
                    ItemRewardT rewardT = newInstance();
                    rewardT.set_item_id(rs.getInt("reward_item_id"));
                    rewardT.set_amount(rs.getInt("reward_amount"));

                    int itemId = rewardT.get_item_id();
                    if(itemId > 0)
                        rewardT.set_bqsitem_reward(BQSItemReward.newInstance(1, itemId, rewardT.get_amount()));
                    return rewardT;
                }
                public static ItemRewardT newInstance(){
                    return new ItemRewardT();
                }
                private BQSItemReward _bqsitem_reward;
                private int _item_id;
                private int _item_name_id;
                private int _amount;
                private int _bit;
                private ItemRewardT(){}

                public void doReward(L1PcInstance pc){
                    if(_bqsitem_reward != null)
                        _bqsitem_reward.doReward(pc);
                }

                public void set_bqsitem_reward(BQSItemReward reward){
                    _bqsitem_reward = reward;
                }

                public int get_item_id(){
                    return _item_id;
                }
                public void set_item_id(int i){
                    _item_id = i;
                    L1Item item = ItemTable.getInstance().getTemplate(i);
                    if(item == null){
                        System.out.println(String.format("[도감]아이템 테이블에서 아이템을 찾을 수 없습니다. 아이템아이디 : %d", i));
                        set_item_name_id(0);
                        return;
                    }
                    set_item_name_id(item.getItemDescId());
                }
                public int get_item_name_id(){
                    return _item_name_id;
                }
                public void set_item_name_id(int val){
                    _bit |= 0x1;
                    _item_name_id = val;
                }
                public boolean has_item_name_id(){
                    return (_bit & 0x1) == 0x1;
                }
                public int get_amount(){
                    return _amount;
                }
                public void set_amount(int val){
                    _bit |= 0x2;
                    _amount = val;
                }
                public boolean has_amount(){
                    return (_bit & 0x2) == 0x2;
                }

                public void saveBody(ProtoWriter w){
                    if(has_item_name_id())
                        w.int32(1, _item_name_id);
                    if(has_amount())
                        w.uint32(2, _amount);
                }

                public void dispose(){
                    _bit = 0;
                }
            }
        }

        // ═══════════════════════════════════════════════════════
        // BonusRewardT
        // ═══════════════════════════════════════════════════════
        public static final class BonusRewardT {
            public static BonusRewardT newInstance(){
                return new BonusRewardT();
            }
            private eMonsterBookV2BonusRewardGrade _grade;
            private int _reward_exp;
            private int _item_name_id;
            private int _bit;
            private BonusRewardT(){}
            public eMonsterBookV2BonusRewardGrade get_grade(){
                return _grade;
            }
            public void set_grade(eMonsterBookV2BonusRewardGrade val){
                _bit |= 0x1;
                _grade = val;
            }
            public boolean has_grade(){
                return (_bit & 0x1) == 0x1;
            }
            public int get_reward_exp(){
                return _reward_exp;
            }
            public void set_reward_exp(int val){
                _bit |= 0x2;
                _reward_exp = val;
            }
            public boolean has_reward_exp(){
                return (_bit & 0x2) == 0x2;
            }
            public int get_item_name_id(){
                return _item_name_id;
            }
            public void set_item_name_id(int val){
                _bit |= 0x4;
                _item_name_id = val;
            }
            public boolean has_item_name_id(){
                return (_bit & 0x4) == 0x4;
            }

            public void saveBody(ProtoWriter w){
                if(has_grade())
                    w.enumV(1, _grade.toInt());
                if(has_reward_exp())
                    w.int32(2, _reward_exp);
                if(has_item_name_id())
                    w.int32(3, _item_name_id);
            }

            public void dispose(){
                _bit = 0;
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // DeckT — 주간퀘스트 덱 (DB blob 저장/로드 + 와이어 + 도메인)
    // ═══════════════════════════════════════════════════════════
    public static final class DeckT {
        public static DeckT newInstance(int i) {
            DeckT current_deck = newInstance();
            current_deck.set_index(i);
            current_deck.set_state(eMonsterBookV2DeckState.DS_IN_PROGRESS);
            ArrayList<BQSInformation> weeks = BQSInformationLoader.getInstance().createShuffleWeeks(i);
            int size = weeks.size();
            if(size < BQSLoadManager.BQS_WQ_WIDTH){
                System.out.println(String.format("[도감] 주간퀘스트 난이도 %d의 할당 갯수가 모자랍니다. %d개", i, size));
            }
            for(int index = 0; index < BQSLoadManager.BQS_WQ_WIDTH; ++index){
                if(index >= size){
                    current_deck.add_cards(CardT.nullInstance(index));
                }else{
                    BQSInformation bqs = weeks.get(index);
                    current_deck.add_cards(CardT.newInstance(bqs, index));
                }
            }
            return current_deck;
        }

        public static DeckT newInstance(ResultSet rs) throws SQLException{
            DeckT current_deck = newInstance();
            int difficulty = rs.getInt("difficulty");
            current_deck.set_index(difficulty);
            current_deck.set_state(eMonsterBookV2DeckState.DS_IN_PROGRESS);
            BQSInformationLoader loader = BQSInformationLoader.getInstance();

            for(int index = 0; index < BQSLoadManager.BQS_WQ_WIDTH; ++index){
                BQSInformation bqs = loader.getInformation(rs.getInt(String.format("col%d", index + 1)));
                current_deck.add_cards(CardT.newInstance(bqs, index));
            }
            return current_deck;
        }

        public static DeckT newInstance(){
            return new DeckT();
        }
        private int _index;
        private eMonsterBookV2DeckState _state;
        private ArrayList<CardT> _cards;
        private int _bit;
        private DeckT(){}

        public CardT findCard(int criteria_id){
            for(CardT card : _cards){
                if(card.get_criteria_id() == criteria_id)
                    return card;
            }
            return null;
        }

        public boolean onUpdateIsComplete(L1PcInstance pc, int criteria_id){
            if(!_state.equals(eMonsterBookV2DeckState.DS_IN_PROGRESS))
                return false;

            int completed = 0;
            for(CardT card : _cards){
                if(card.get_criteria_id() == criteria_id){
                    if(card.isCompleted())
                        return false;

                    boolean isComplete = card.onUpdateIsComplete();
                    SC_MONSTER_BOOK_V2_UPDATE_AMOUNT_NOTI.send(pc, _index, card.get_index(), card.get_amount());
                    if(isComplete)
                        ++completed;
                }else{
                    if(card.isCompleted())
                        ++completed;
                }
            }
            return completed == _cards.size();
        }

        public int get_index(){
            return _index;
        }
        public void set_index(int val){
            _bit |= 0x1;
            _index = val;
        }
        // The deck's wire index and each card's wire index must match their
        // positions used by BQSCharacterData.get_deck/get_card. Old DB blobs
        // may carry a shifted index without a shifted list position.
        public void alignWireIndices(int deckPosition){
            if (_index != deckPosition) set_index(deckPosition);
            if (_cards != null) {
                for (int i = 0, n = _cards.size(); i < n; i++) {
                    CardT card = _cards.get(i);
                    if (card != null && card.get_index() != i) card.set_index(i);
                }
            }
        }
        public boolean has_index(){
            return (_bit & 0x1) == 0x1;
        }
        public eMonsterBookV2DeckState get_state(){
            return _state;
        }
        public void set_state(eMonsterBookV2DeckState val){
            _bit |= 0x4;
            _state = val;
        }
        public boolean has_state(){
            return (_bit & 0x4) == 0x4;
        }
        public CardT get_card(int card_index){
            return _cards.get(card_index);
        }
        public ArrayList<CardT> get_cards(){
            return _cards;
        }
        public void add_cards(CardT val){
            if(!has_cards())
                _cards = new ArrayList<CardT>(BQSLoadManager.BQS_WQ_WIDTH);
            _cards.add(val);
        }
        public boolean has_cards(){
            return _cards != null;
        }

        // ── DB 저장 코덱 (BQSCharacterData blob field 4) ──
        public void saveBody(ProtoWriter w){
            if(has_index())
                w.uint32(1, _index);
            if(has_state())
                w.enumV(3, _state.toInt());       // field 2 없음 — 구 스키마 그대로
            if(has_cards()){
                for(CardT val : _cards){
                    w.msg(4);
                    val.saveBody(w);
                    w.end();
                }
            }
        }

        /** DB blob → 도메인 (BQSCharacterData.loadFrom에서 호출). 구 generated와 바이트 호환 */
        public static DeckT loadFrom(ProtoReader r){
            DeckT d = new DeckT();
            while(!r.isAtEnd()){
                int tag = r.tag();
                switch(tag){
                case (1 << 3) | 0:
                    d.set_index(r.varint32());
                    break;
                case (3 << 3) | 0:
                    d.set_state(eMonsterBookV2DeckState.fromInt(r.varint32()));
                    break;
                case (4 << 3) | 2:
                    d.add_cards(CardT.loadFrom(r.subReader()));
                    break;
                default:
                    r.skip(tag & 7);
                }
            }
            return d;
        }

        public void dispose(){
            if(has_cards()){
                for(CardT val : _cards)
                    val.dispose();
                _cards.clear();
                _cards = null;
            }
            _bit = 0;
        }

        public DeckT deepCopy(int level){
            DeckT deck = newInstance();
            deck.set_index(_index);
            deck.set_state(_state);
            for(CardT val : _cards)
                deck.add_cards(val.deepCopy(level));
            return deck;
        }

        // ═══════════════════════════════════════════════════
        // CardT
        // ═══════════════════════════════════════════════════
        public static final class CardT {
            public static CardT newInstance(BQSInformation bqs, int index){
                CardT card = CardT.newInstance();
                card.set_achievement_id(bqs.getAchievementId());
                card.set_amount(0);
                card.set_criteria_id(bqs.getCriteriaId());
                card.set_index(index);
                card.set_required_amount(bqs.getWeekCompletedCount());
                return card;
            }

            public static CardT nullInstance(int index){
                CardT card = CardT.newInstance();
                card.set_achievement_id(0);
                card.set_amount(0);
                card.set_criteria_id(1);
                card.set_index(index);
                card.set_required_amount(10000000);
                return card;
            }

            public static CardT newInstance(){
                return new CardT();
            }
            private int _index;
            private int _required_amount;
            private int _achievement_id;
            private int _criteria_id;
            private int _amount;
            private int _bit;
            private CardT(){}

            public boolean isCompleted(){
                return _amount >= _required_amount;
            }

            public boolean onUpdateIsComplete(){
                return ++_amount >= _required_amount;
            }

            public int get_index(){
                return _index;
            }
            public void set_index(int val){
                _bit |= 0x1;
                _index = val;
            }
            public boolean has_index(){
                return (_bit & 0x1) == 0x1;
            }
            public int get_required_amount(){
                return _required_amount;
            }
            public void set_required_amount(int val){
                _bit |= 0x2;
                _required_amount = val;
            }
            public boolean has_required_amount(){
                return (_bit & 0x2) == 0x2;
            }
            public int get_achievement_id(){
                return _achievement_id;
            }
            public void set_achievement_id(int val){
                _bit |= 0x4;
                _achievement_id = val;
            }
            public boolean has_achievement_id(){
                return (_bit & 0x4) == 0x4;
            }
            public int get_criteria_id(){
                return _criteria_id;
            }
            public void set_criteria_id(int val){
                _bit |= 0x8;
                _criteria_id = val;
            }
            public boolean has_criteria_id(){
                return (_bit & 0x8) == 0x8;
            }
            public int get_amount(){
                return _amount;
            }
            public void set_amount(int val){
                _bit |= 0x10;
                _amount = val;
            }
            public boolean has_amount(){
                return (_bit & 0x10) == 0x10;
            }

            // ── DB 저장 코덱 (DeckT blob 내부) ──
            public void saveBody(ProtoWriter w){
                if(has_index())
                    w.uint32(1, _index);
                if(has_required_amount())
                    w.uint32(2, _required_amount);
                if(has_achievement_id())
                    w.uint32(3, _achievement_id);
                if(has_criteria_id())
                    w.uint32(4, _criteria_id);
                if(has_amount())
                    w.uint32(5, _amount);
            }

            public static CardT loadFrom(ProtoReader r){
                CardT c = new CardT();
                while(!r.isAtEnd()){
                    int tag = r.tag();
                    switch(tag){
                    case (1 << 3) | 0: c.set_index(r.varint32()); break;
                    case (2 << 3) | 0: c.set_required_amount(r.varint32()); break;
                    case (3 << 3) | 0: c.set_achievement_id(r.varint32()); break;
                    case (4 << 3) | 0: c.set_criteria_id(r.varint32()); break;
                    case (5 << 3) | 0: c.set_amount(r.varint32()); break;
                    default:
                        r.skip(tag & 7);
                    }
                }
                return c;
            }

            public void dispose(){
                _bit = 0;
            }

            public CardT deepCopy(int level){
                CardT card = CardT.newInstance();
                card.set_achievement_id(_achievement_id);
                BQSWQInformation wqInfo = BQSWQInformationLoader.getInstance().get(level);
                card.set_required_amount(wqInfo == null ? _required_amount : wqInfo.calc_amount(_required_amount));
                card.set_amount(_amount);
                card.set_criteria_id(_criteria_id);
                card.set_index(_index);
                return card;
            }
        }
    }
}