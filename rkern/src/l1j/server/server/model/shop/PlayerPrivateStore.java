package l1j.server.server.model.shop;

import java.util.ArrayList;

import l1j.server.server.model.shop.dynamic.DynamicShopItem;

/** One character-owned lifecycle for open and closed private stores. */
public final class PlayerPrivateStore {
    public record State(boolean open, boolean displayOnly, int generation,
            ArrayList<DynamicShopItem> sellings, ArrayList<DynamicShopItem> purchasings, byte[] chat) { }

    private static final State INITIAL = new State(false, false, 0, null, null, null);
    private volatile State state = INITIAL;
    private volatile State ready;

    public static State initialState() { return INITIAL; }
    public State snapshot() { return state; }

    /** The caller hands off fresh validated lists; their identity also fences settlement.
     * Admission only enqueues the durable work. It executes before publication. */
    public synchronized void open(ArrayList<DynamicShopItem> sells, ArrayList<DynamicShopItem> buys,
            byte[] chat, boolean displayOnly, Runnable beforePublish) {
        final State next = new State(true, displayOnly, state.generation() + 1, sells, buys, chat);
        if (beforePublish != null) beforePublish.run();
        ready = null;
        state = next;
    }

    public synchronized boolean close(Runnable beforePublish) { return close(state, beforePublish); }

    public synchronized boolean close(int generation, Runnable beforePublish) {
        final State current = state;
        return current.generation() == generation && close(current, beforePublish);
    }

    private boolean close(State current, Runnable beforePublish) {
        if (!current.open()) return false;
        final State next = new State(false, false, current.generation() + 1, null, null, null);
        if (!current.displayOnly() && beforePublish != null) beforePublish.run();
        ready = null;
        state = next;
        return true;
    }

    public boolean isReady() {
        final State current = state;
        return current.open() && ready == current;
    }

    public synchronized void setReady(boolean value) {
        setReady(state, value);
    }

    public synchronized boolean setReady(int generation, boolean value) {
        final State current = state;
        return current.generation() == generation && setReady(current, value);
    }

    private boolean setReady(State current, boolean value) {
        if (!current.open() || (ready == current) == value) return false;
        ready = value ? current : null;
        return true;
    }
}
