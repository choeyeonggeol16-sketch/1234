package l1j.server.server.model.network.core.connection;
public class CUserSocket {public boolean isClientConnected(){return true;}public CIOFrame beginDirectFrame(int n){return new CIOFrame();}public boolean commitDirectFrame(CIOFrame f){return true;}public void abortDirectFrame(CIOFrame f){} }
