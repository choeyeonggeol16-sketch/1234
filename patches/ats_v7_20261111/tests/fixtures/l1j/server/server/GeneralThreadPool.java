package l1j.server.server;
// Synchronous test boundary only; the real server executes outside the Netty owner loop.
public class GeneralThreadPool {
    private static final GeneralThreadPool INSTANCE=new GeneralThreadPool();
    public static GeneralThreadPool getInstance(){return INSTANCE;}
    public void execute(Runnable r){r.run();}
}
