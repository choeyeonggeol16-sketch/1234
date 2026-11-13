package l1j.server.server;
// Game/DB fixture only; production Swing manager and screen buffer are used.
public final class GameServer {public static GameServer getInstance(){return new GameServer();}public void shutdownWithCountdown(int s){throw new AssertionError("game shutdown in GUI test");}public static void disconnectChar(Object p){}}
