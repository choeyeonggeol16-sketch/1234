package l1j.server.server.packets.Protocol.MainServer.Proto.Craft;

import java.io.IOException;

import l1j.server.Config;
import l1j.server.MJTemplate.MJRnd;
import l1j.server.server.datatables.CraftProbability;
import l1j.server.server.packets.Protocol.MainServer.Proto.ProtoWriter;
import l1j.server.server.packets.Protocol.MainServer.Proto.Stream.ProtoInputStream;

/**
 * 제작 결과 목록 (성공/실패 + 확률)
 * 1 success(msg: CraftOutputSFList) / 2 failure(msg: CraftOutputSFList) / 3 success_prob_by_million
 */
public class CraftOutputList {

    public static final int CRAFT_ITEM_SUCCESS_MILLIONS = 1000000;

    private CraftOutputSFList _success;
    private CraftOutputSFList _failure;
    private int _successProbByMillion;

    // getter/setter
    public CraftOutputSFList get_success(){ return _success; }
    public void set_success(CraftOutputSFList v){ _success = v; }
    public CraftOutputSFList get_failure(){ return _failure; }
    public void set_failure(CraftOutputSFList v){ _failure = v; }
    public int get_successProbByMillion(){ return _successProbByMillion; }
    public void set_successProbByMillion(int v){ _successProbByMillion = v; }

    // ── 역직렬화 ──
    public static CraftOutputList parse(ProtoInputStream in) throws IOException {
        CraftOutputList l = new CraftOutputList();
        while(!in.isAtEnd()){
            int tag = in.readTag();
            switch(tag >>> 3){
            case 1: l._success = CraftOutputSFList.parse(in.readSlice()); break;
            case 2: l._failure = CraftOutputSFList.parse(in.readSlice()); break;
            case 3: l._successProbByMillion = in.readInt32(); break;
            default: in.skipField(tag); break;
            }
        }
        return l;
    }
    

    public CraftOutputItemResult createOutputItem(int increase_prob, int craft_id){
        int prob = get_successProbByMillion() + increase_prob;
        if(prob >= CRAFT_ITEM_SUCCESS_MILLIONS)
            return new CraftOutputItemResult(extractOutputItem(get_success(), prob), true);

        int real_prob = (int)((double)prob * Config.CRAFT_INCREASE_PROB_BYMILLION);
        Integer db_prob = CraftProbability.getInstance().get(craft_id);
        if(db_prob != null && db_prob > 0)
            real_prob = db_prob;

        if(MJRnd.isWinning(CRAFT_ITEM_SUCCESS_MILLIONS, real_prob))
            return new CraftOutputItemResult(extractOutputItem(get_success(), real_prob), true);

        CraftOutputSFList fail = get_failure();
        if(fail.get_probCount() <= 0 && fail.get_nonProbCount() <= 0)
            return new CraftOutputItemResult(null, false);
        return new CraftOutputItemResult(extractOutputItem(get_failure(), real_prob), false);
    }

    private CraftOutputItem extractOutputItem(CraftOutputSFList sfList, int prob){
        int probCount = sfList.get_probCount();
        if(probCount > 0){
            java.util.List<CraftOutputItem> outputItems = sfList.get_outputProbItems();
            return outputItems.get((probCount == 1 || l1j.server.MJTemplate.MJRnd.isWinning(CRAFT_ITEM_SUCCESS_MILLIONS, prob) ? 0 : l1j.server.MJTemplate.MJRnd.next(outputItems.size() - 1) + 1));
        }
        return sfList.get_outputItems().get(0);
    }

    // ── 직렬화 ──
    public void writeTo(ProtoWriter w){
        if(_success != null){ w.msg(1); _success.writeTo(w); w.end(); }
        if(_failure != null){ w.msg(2); _failure.writeTo(w); w.end(); }
        w.int32(3, _successProbByMillion);
    }
}