package l1j.server.server.model.Instance;
import l1j.server.server.model.network.core.session.CIOClient;
public final class L1PcInstance {
    private final CIOClient client;
    public L1PcInstance(CIOClient client) { this.client = client; }
    public CIOClient getNetConnection() { return client; }
}
