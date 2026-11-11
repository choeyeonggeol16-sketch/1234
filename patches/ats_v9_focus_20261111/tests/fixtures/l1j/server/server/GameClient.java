package l1j.server.server;
public class GameClient {
    private final String ip; private final int port;
    public GameClient(String ip,int port){this.ip=ip;this.port=port;}
    public String getIp(){return ip;} public int getRemotePort(){return port;}
}
