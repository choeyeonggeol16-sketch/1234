package l1j.server.server.model.network.core.session;
import l1j.server.server.model.network.core.connection.CUserSocket;
public final class CIOClient {public CUserSocket socket;public CIOClient(CUserSocket s){socket=s;}public boolean isConnected(){return socket!=null&&socket.connected;} public CUserSocket getConnection(){return socket;}}
