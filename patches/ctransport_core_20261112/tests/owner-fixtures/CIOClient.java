package l1j.server.server.model.network.core;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.utill.ConnectionState;
// Game/account fixture only; the tested CIOUserSocket is the production class.
public final class CIOClient {
 public final CIOUserSocket connection;public volatile boolean ready=true,failCapture;
 public CIOClient(CIOUserSocket c){connection=c;}
 public CIOUserSocket getConnection(){return connection;}
 public boolean isConnected(){return connection.isOpen();}
 public boolean isGameSessionReady(){return ready;}
 public L1PcInstance getActiveChar(){if(failCapture)throw new IllegalStateException("injected session capture failure");return null;}
 public Account getAccount(){return null;}
 public ConnectionState getConnectionState(){return ConnectionState.CONNECTED;}
 public int incrementEmptyPacketCount(){return 1;}
 public void resetEmptyPacketCount(){}
 public boolean checkPacketRateLimit(long now){return false;}
 void onTransportClosing(){ready=false;}
 public void disconnect(){connection.close();}
}
