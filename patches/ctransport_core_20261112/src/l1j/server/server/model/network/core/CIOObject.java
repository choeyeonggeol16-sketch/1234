package l1j.server.server.model.network.core;

/** An admitted native completion executes or cancels exactly once. */
public interface CIOObject extends Runnable {
    void cancel();
}
