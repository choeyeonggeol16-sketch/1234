package l1j.server.server.model.network.core.session;
import l1j.server.server.model.network.core.connection.CUserSocket;
public final class CIOClient {
    private final CUserSocket socket;
    public CIOClient(CUserSocket socket) { this.socket = socket; }
    public boolean isConnected() { return true; }
    public CUserSocket getConnection() { return socket; }
}
