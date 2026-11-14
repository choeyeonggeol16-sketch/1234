package l1j.server.server.model.rank.business;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import l1j.server.server.model.L1InventoryTransaction;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.storage.CacheD;
import l1j.server.server.storage.mysql.MySqlCharacterStorage.CharacterSnapshot;
import l1j.server.server.datatables.ExpTable;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

import l1j.server.Config;
import l1j.server.server.ThreadPool;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.gametime.BaseTime;
import l1j.server.server.model.gametime.RealTimeClock;
import l1j.server.server.model.gametime.TimeListener;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.rank.loader.RankLoadManager;
import l1j.server.server.model.rank.loader.RankUserLoader;
import l1j.server.server.packets.Protocol.Proto;
import l1j.server.server.packets.Protocol.ProtoPacketWriter;
import l1j.server.server.packets.Protocol.Packet.SC_MyRankingAck;
import l1j.server.server.packets.Protocol.Packet.SC_TopRankerAck;
import l1j.server.server.packets.Protocol.Packet.SC_TopRankerNoti;

/**
 * 랭킹 비즈니스 로직.
 */
public class RankBusiness implements TimeListener{
    private static RankBusiness _instance;
    public static RankBusiness getInstance(){
        if(_instance == null)
            _instance = new RankBusiness();
        return _instance;
    }
    
    private int _accumulate_second;
    private volatile int _expendiant;
    private volatile long _version;
    private byte[] _nowNotServiceAck;
    private volatile byte[][][] _acks;
    private SC_TopRankerAck[][] _rankInfos;
    private final AtomicBoolean _refreshRunning = new AtomicBoolean();
    private volatile boolean _refreshPending;
    private volatile long _refreshGeneration;
    private volatile SC_TopRankerNoti _lastTopRanker;
    private volatile boolean _isrun;
    
    private RankBusiness(){
        _accumulate_second = 0;
        
        _nowNotServiceAck = new SC_TopRankerAck()
                .resultCodeOnly(SC_TopRankerAck.ResultCode.NOW_NOT_SERVICE.toInt())
                .encode();
        
        _acks = new byte[9][2][];
        _rankInfos = new SC_TopRankerAck[9][2];
        for(int i=8; i>=0; --i){
            _rankInfos[i][0] = SC_TopRankerAck.newInstance(i, 2, 1);
            _rankInfos[i][1] = SC_TopRankerAck.newInstance(i, 2, 2);
        }
    }
    
    /** One-off ranking refreshes: no permanently parked instant-pool worker. */
    public synchronized void run(){
        if (_isrun) {
            requestRefresh();
            return;
        }
        _accumulate_second = 0;
        _refreshGeneration++;
        _isrun = true;
        RealTimeClock.getInstance().addListener(this, Calendar.SECOND);
        requestRefresh();
    }

    public synchronized void dispose(){
        if (!_isrun) return;
        _isrun = false;
        _refreshPending = false;
        _refreshGeneration++;
        RealTimeClock.getInstance().removeListener(this, Calendar.SECOND);
    }

    @Override public void onMonthChanged(BaseTime time) { }
    @Override public void onDayChanged(BaseTime time) { }
    @Override public void onHourChanged(BaseTime time) { }
    @Override public void onMinuteChanged(BaseTime time) { }

    @Override
    public void onSecondChanged(BaseTime time) {
        if (!_isrun) return;
        if (++_accumulate_second >= RankLoadManager.MRK_SYS_UPDATE_CLOCK) {
            _accumulate_second = 0;
            requestRefresh();
            if (Config.멘트온오프) {
                L1World.getInstance().broadcastServerMessage(Config.랭킹멘트);
            }
        } else if (_refreshPending) {
            // A rejected admission is retried by the existing clock, not inline.
            trySubmitRefresh();
        }
    }

    public void on_update(){
        _accumulate_second = 0;
        requestRefresh();
    }

    private void requestRefresh() {
        if (!_isrun) return;
        _refreshPending = true;
        trySubmitRefresh();
    }

    private void trySubmitRefresh() {
        if (!_isrun || !_refreshPending || !_refreshRunning.compareAndSet(false, true)) return;
        final long generation = _refreshGeneration;
        try {
            ThreadPool.getInstance().execute(() -> {
                try {
                    if (!_isrun || _refreshGeneration != generation || !_refreshPending) return;
                    _refreshPending = false;
                    initializeRankerInfo();
                    doUpdateRank();
                    makeStream();
                } catch (Throwable failure) {
                    ITEM_LOG.log(Level.SEVERE, "Rank refresh failed", failure);
                } finally {
                    _refreshRunning.set(false);
                    // A refresh requested while one was executing stays pending
                    // and is admitted on the next clock tick. Never spin on rejection.
                }
            });
        } catch (RejectedExecutionException rejected) {
            _refreshRunning.set(false);
            // _refreshPending stays set. No ranking work runs on an IO/owner thread.
        }
    }

    private void initializeRankerInfo(){
        _version = System.currentTimeMillis();
        for(int i=8; i>=0; --i){
            _rankInfos[i][0].clearRankers();
            _rankInfos[i][0].set_version(_version);
            _rankInfos[i][1].clearRankers();
            _rankInfos[i][1].set_version(_version);
        }
    }
    
    private void makeStream(){
        if(!RankLoadManager.MRK_SYS_ISON)
            return;
        
        byte[][][] acks = new byte[9][2][];
        for(int i=8; i>=0; --i){
            int totalPage = 1;
            if(_rankInfos[i][1].hasRankers()){
                totalPage = 2;
                _rankInfos[i][1].set_current_page(2);
                _rankInfos[i][1].set_total_page(2);
                acks[i][1] = _rankInfos[i][1].encode();
            }
            if(_rankInfos[i][0].hasRankers()){
                _rankInfos[i][0].set_current_page(1);
                _rankInfos[i][0].set_total_page(totalPage);
                acks[i][0] = _rankInfos[i][0].encode();
            }
        }
        _acks = acks;
    }
    
    private void doUpdateRank(){
        ArrayList<SC_TopRankerNoti> rankers = RankUserLoader.getInstance().createSortSnapshot();
    //    System.out.println("[Rank] doUpdateRank size=" + (rankers==null?-1:rankers.size()));
        if(rankers == null)
            return;
        
        int[] currentPlace = new int[]{1,1,1,1,1,1,1,1,1};
        SC_TopRankerNoti[] prevRankers = new SC_TopRankerNoti[9];
        
        try{
            int size = rankers.size();
            for(int i=0; i<size; ++i){
                SC_TopRankerNoti rnk = rankers.get(i);
                int class_id = rnk.get_class();
                int current_class_place = currentPlace[class_id];
                int current_total_place = currentPlace[8];
                if(!rnk.isInRank()){
                    rnk.updatePlace();
                    rnk.updateAlmost();
                }else{
                    rnk.updatePlace(current_class_place, current_total_place);
                    rnk.updateRating(current_class_place, current_total_place);
                    SC_TopRankerNoti prev_class = prevRankers[class_id];
                    SC_TopRankerNoti prev_total = prevRankers[8];
                    rnk.updateAlmost(prev_class, prev_total);
                    if(current_class_place <= RankLoadManager.MRK_SYS_CLASS_RANGE){
                        _rankInfos[class_id][current_class_place > 100 ? 1 : 0].add_rankers(rnk.get_class_ranker());
                        prevRankers[class_id] = rnk;
                        currentPlace[class_id] = ++current_class_place;
                    }
                    
                    if(current_total_place <= RankLoadManager.MRK_SYS_TOTAL_RANGE){
                        if(current_total_place == 1)
                            _lastTopRanker = rnk;
                        
                        _rankInfos[8][current_total_place > 100 ? 1 : 0].add_rankers(rnk.get_total_ranker());
                        prevRankers[8] = rnk;
                        
                        if(current_total_place == RankLoadManager.MRK_SYS_RANK_POTION - 1) 
                            _expendiant = rnk.get_exp();
                        currentPlace[8] = ++current_total_place;
                    }
                }
                rnk.buff();
            }
            rankers.clear();
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    
    public int getExpendiant(){
        return _expendiant;
    }

    private static final Logger ITEM_LOG = Logger.getLogger(RankBusiness.class.getName());

    /** The rank reward and its consumable must survive or roll back together. */
    public void onExpendiantItem(L1PcInstance pc, L1ItemInstance input) {
        if (pc == null || input == null || input.getItemId() != 5990
                || pc.getInventory().getItem(input.getId()) != input) return;
        if (_expendiant <= 0 || !RankLoadManager.MRK_SYS_ISON) {
            pc.sendPackets("운영자에 의해 랭킹 시스템이 중단되어 있는 상태입니다.");
            return;
        }
        if (pc.isGm()) {
            pc.sendPackets("GM은 해당 아이템을 사용할 수 없습니다.");
            return;
        }
        SC_TopRankerNoti noti = RankUserLoader.getInstance().get(pc.getId());
        if (noti == null) noti = RankUserLoader.getInstance().create_user_information(pc);
        else if (noti.get_total_ranker().get_rank() <= RankLoadManager.MRK_SYS_RANK_POTION) {
            pc.sendPackets("진입하려는 랭킹보다 현재 랭킹이 높습니다.");
            return;
        }
        final int beforeExp = pc.getExp();
        final int level = pc.getLevel();
        final int rankExp = _expendiant;
        final int afterExp = (int) Math.min((long) ExpTable.MAX_EXP, (long) rankExp + 5L);
        if (afterExp <= beforeExp || level <= 0 || level >= ExpTable.MAX_LEVEL) return;
        try {
            // Serialize the character save ahead of the item+EXP receipt.
            CacheD.getInstance().saveCharacter(new CharacterSnapshot(pc));
            try (L1InventoryTransaction tx = L1InventoryTransaction.begin(pc, input.getId())) {
                if (!tx.debit(input, 1)) return;
                tx.requireTables("characters");
                tx.sql(con -> {
                    try (PreparedStatement update = con.prepareStatement(
                            "UPDATE characters SET Exp=? WHERE objid=? AND level=? AND Exp=?")) {
                        update.setInt(1, afterExp);
                        update.setInt(2, pc.getId());
                        update.setInt(3, level);
                        update.setInt(4, beforeExp);
                        if (update.executeUpdate() != 1)
                            throw new SQLException("Rank EXP preimage changed: " + pc.getId());
                    }
                });
                tx.onFailure(() -> SC_SystemMessage.send(pc, "랭킹 경험치 물약 저장에 실패했습니다."));
                tx.commitAfterInventoryRelease(() -> {
                    if (pc.getExp() != beforeExp || pc.getLevel() != level)
                        throw new IllegalStateException("Rank EXP live preimage changed");
                    pc.setExp(afterExp);
                    pc.sendPackets(String.format("%s님의 경험치가 랭킹 %d위의 경험치로 재조정 됩니다.",
                            pc.getName(), RankLoadManager.MRK_SYS_RANK_POTION));
                });
            }
        } catch (RuntimeException failure) {
            ITEM_LOG.log(Level.WARNING, "Rank potion prepare failed: " + pc.getId(), failure);
            SC_SystemMessage.send(pc, "랭킹 경험치 물약을 처리하지 못했습니다.");
        }
    }

    public boolean onExpendiant(L1PcInstance pc){
        try{
            if(pc == null)
                return false;
            
            if(_expendiant <= 0 || !RankLoadManager.MRK_SYS_ISON){
                pc.sendPackets("운영자에 의해 랭킹 시스템이 중단되어 있는 상태입니다.");
                return false;
            }
            
            if(pc.isGm()){
                pc.sendPackets("GM은 해당 아이템을 사용할 수 없습니다.");
                return false;
            }
            
            SC_TopRankerNoti noti = RankUserLoader.getInstance().get(pc.getId());
            if(noti == null){
                noti = RankUserLoader.getInstance().create_user_information(pc);
            }else{
                if(noti.get_total_ranker().get_rank() <= RankLoadManager.MRK_SYS_RANK_POTION){
                    pc.sendPackets("진입하려는 랭킹보다 현재 랭킹이 높습니다.");
                    return false;
                }
            }
            
            pc.setExp((int)(_expendiant + 5));
            pc.sendPackets(String.format("%s님의 경험치가 랭킹 %d위의 경험치로 재조정 됩니다.", pc.getName(), RankLoadManager.MRK_SYS_RANK_POTION));
            return true;
        }catch(Exception e){
            e.printStackTrace();
            pc.sendPackets("운영자에 의해 랭킹 시스템이 중단되어 있는 상태입니다.");
            return false;
        }
    }
    
    public void noti(CIOClient clnt, int objid){
        SC_TopRankerNoti noti = RankUserLoader.getInstance().get(objid);
        if(noti != null) {
            clnt.sendPacket(noti);
        }
    }
    
    public void ack_private_version(L1PcInstance pc, long version){
        if(!RankLoadManager.MRK_SYS_ISON){
            SC_MyRankingAck.send_not_service(pc);
        }else if(_version == version){
            SC_MyRankingAck.send(pc);
        }else{
            SC_TopRankerNoti noti = RankUserLoader.getInstance().getRankNoti(pc);
            if(noti != null){
                SC_MyRankingAck.send(pc, noti, _version);
            }
        }
    }
    
    public void ack(L1PcInstance pc, int classId, long version){
    	//  System.out.println("[RankACK] classId=" + classId + " clientVer=" + version + " serverVer=" + _version + " ison=" + RankLoadManager.MRK_SYS_ISON);
        if(!RankLoadManager.MRK_SYS_ISON){
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _nowNotServiceAck);
            return;
        }
        if(version == _version){
            SC_TopRankerAck.sendNoChange(pc, version);
            return;
        }
        if(classId < 0 || classId >= _acks.length){
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _nowNotServiceAck);
            return;
        }
        byte[][] streams = _acks[classId];
        int sending = 0;
        for(int i=0; i<streams.length; ++i){
            byte[] data = streams[i];
            if(data != null){
                l1j.server.server.packets.Protocol.Proto.sendRaw(pc, data);
                ++sending;
            }
        }
        if(sending == 0) {
            l1j.server.server.packets.Protocol.Proto.sendRaw(pc, _nowNotServiceAck);
        }
    }
    
    public SC_TopRankerNoti getLastTopRanker(){
        return _lastTopRanker;
    }
}