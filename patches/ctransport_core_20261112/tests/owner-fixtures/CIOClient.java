package l1j.server.server.model.network.core;
import l1j.server.server.Account;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.utill.ConnectionState;
// Game/account fixture only; the tested CUserSocket is the production class.
public final class CIOClient {
 public final CUserSocket connection;public volatile boolean ready=true,failCapture;
 public volatile StackTraceElement[] closeStack;
 public CIOClient(CUserSocket c){connection=c;}
 public CUserSocket getConnection(){return connection;}
 public boolean isConnected(){return connection.isOpen();}
 public boolean isGameSessionReady(){return ready;}
 public L1PcInstance getActiveChar(){if(failCapture)throw new IllegalStateException("injected session capture failure");return null;}
 public Account getAccount(){return null;}
 public ConnectionState getConnectionState(){return ConnectionState.CONNECTED;}
 public int incrementEmptyPacketCount(){return 1;}
 public void resetEmptyPacketCount(){}
 public boolean checkPacketRateLimit(long now){return false;}
 void onTransportClosing(){closeStack=Thread.currentThread().getStackTrace();ready=false;}
 public void disconnect(){connection.close();}
}
