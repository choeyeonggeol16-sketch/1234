package l1j.server.server.model.network.core.broadcast;

import java.util.ArrayList;
import java.util.List;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.session.CIOClient;
import l1j.server.server.model.network.core.transport.CIOServer;

/** The production optional whole-world broadcasters must isolate one bad recipient selector. */
public final class OptionalWorldFilter110 {
    private static void check(boolean pass,String why) { if(!pass)throw new AssertionError(why); }
    private static L1PcInstance pc(CIOServer owner,int id) {
        return new L1PcInstance(id,1,new CIOClient(new CUserSocket(owner)));
    }
    private static L1PcInstance selectorFailure(CIOServer owner,int id,String selector) {
        return new L1PcInstance(id,1,new CIOClient(new CUserSocket(owner))) {
            @Override public boolean isPrivateShop() {
                if(selector.equals("shop")) throw new IllegalStateException("injected private-shop check");
                return false;
            }
            @Override public boolean is_world() {
                if(selector.equals("world")) throw new IllegalStateException("injected world check");
                return true;
            }
            @Override public boolean isGm() {
                if(selector.equals("gm")) throw new IllegalStateException("injected GM check");
                return true;
            }
        };
    }
    public static void main(String[]args) {
        final CIOServer owner = new CIOServer();
        final L1World world = L1World.getInstance();
        final L1PcInstance good1 = pc(owner,101), good2 = pc(owner,102);
        final L1PcInstance badShop = selectorFailure(owner,103,"shop");
        final L1PcInstance badWorld = selectorFailure(owner,104,"world");
        final L1PcInstance badGm = selectorFailure(owner,105,"gm");
        good1.gm=true;good2.gm=true;
        // A failed eligibility check must not abort optional broadcast to any later viewer.
        world.all=new ArrayList<>(List.of(good1,badShop,badWorld,good2));
        CSharedBroadcast.sendWrittenAllPlayableBestEffort(b -> b.put((byte)0x51));
        check(good1.client.socket.frames.size()==1,"playable first healthy recipient missing");
        check(good2.client.socket.frames.size()==1,"playable after throwing selectors missing");
        check(good2.client.socket.frames.get(0)[2]==(byte)0x51,"playable FIFO/frame bytes changed");
        // Two independent predicate failures separated by healthy GM recipients.
        world.all=new ArrayList<>(List.of(good1,badGm,good2));
        CSharedBroadcast.sendWrittenAllGmsBestEffort(b -> b.put((byte)0x52));
        check(good1.client.socket.frames.size()==2,"GM first recipient missing");
        check(good2.client.socket.frames.size()==2,"GM after failed predicate missing");
        check(good2.client.socket.frames.get(1)[2]==(byte)0x52,"GM FIFO/frame bytes changed");
        for (L1PcInstance p : List.of(good1,good2,badShop,badWorld,badGm)) p.client.socket.drain();
        check(owner.releases==2,"every optional source must be released exactly once");
        // Healthy filters must retain the old shop/world/GM exclusions and duplicates.
        good2.shop=true;good1.gm=false;
        world.all=new ArrayList<>(List.of(good1,good2,good1));
        CSharedBroadcast.sendWrittenAllPlayableBestEffort(b -> b.put((byte)0x53));
        check(good1.client.socket.frames.size()==4 && good2.client.socket.frames.size()==2,
              "healthy playable filters or duplicate order changed");
        CSharedBroadcast.sendWrittenAllGmsBestEffort(b -> b.put((byte)0x54));
        check(good2.client.socket.frames.size()==3 && good1.client.socket.frames.size()==4,
              "healthy GM selection changed");
        good1.client.socket.drain(); good2.client.socket.drain();
        check(owner.releases==4,"all optional Native source references must be returned");
        System.out.println("110 real production optional world selector: isolated failures, FIFO, duplicates, Native release PASS");
    }
}
