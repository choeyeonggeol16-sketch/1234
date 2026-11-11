package l1j.server.server.model;
import java.util.ArrayList;import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
public class L1World {
    private static final L1World INSTANCE=new L1World();
    public final List<L1PcInstance> players=new ArrayList<>();
    public static L1World getInstance(){return INSTANCE;}
    public List<L1PcInstance> getAllPlayers(){return players;}
}
