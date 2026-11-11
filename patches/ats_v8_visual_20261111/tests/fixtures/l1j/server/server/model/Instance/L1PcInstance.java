package l1j.server.server.model.Instance;
import l1j.server.server.GameClient;
public class L1PcInstance {
    public final GameClient client; public boolean active=true; public int stops;
    public L1PcInstance(GameClient c){client=c;}
    public GameClient getNetConnection(){return client;}
    public boolean getAutoHunt(){return active;}
    public synchronized void EndAutoController(){active=false;stops++;}
    public int getLevel(){return 30;} public int getMapId(){return 4;}
    public int getId(){return 123;} public boolean isDead(){return false;}
    public void sendPackets(Object p){}
}
