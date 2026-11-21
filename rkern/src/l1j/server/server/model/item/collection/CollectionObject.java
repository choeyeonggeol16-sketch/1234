package l1j.server.server.model.item.collection;

import l1j.server.server.model.network.core.sc.packet.SC_CTPacket;
/** 
 * CollectionCharInfo
 * MJSoft Character TradeSystem - Object
 * made by mjsoft, 2016.
 **/
public class CollectionObject {
	public int			marbleId;
	public int			charId;
	public String		name;
	public volatile byte[] invPck;
	public volatile byte[] spPck;
	public volatile SC_CTPacket.Data infoPck;
    private boolean previewQueued;
    private long retryAfterNanos;
    private boolean retryDelayed;

    public synchronized boolean tryQueuePreview() {
        if (previewQueued || previewComplete()) return false;
        if (retryDelayed && System.nanoTime() - retryAfterNanos < 0) return false;
        previewQueued = true;
        return true;
    }

    public boolean previewComplete() {
        return invPck != null && spPck != null && infoPck != null;
    }

    public synchronized void finishPreview() {
        previewQueued = false;
        retryDelayed = !previewComplete();
        retryAfterNanos = System.nanoTime() + 1_000_000_000L;
    }

	public int value1;
	public int dex;
	public int con;
	public int wis;
	public int ints;
	public int cha;
	public int classn;
	public int ele;
	public int hp;
	public int mp;
	public int level;
	
	
	public String		clan;
	
	
	
	public void dispose(){
		
		
		/*
		 * if(infoPck != null) infoPck.clear();
		 */
	}
}
