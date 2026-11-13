package l1j.server.server.model.network.core;
/** Original IServerCallback allocation role, adapted to the L1 Java owner. */
@FunctionalInterface
public interface IServerCallback {
    CUserSocket CreateSocket(CSocket socket,CIOBufferPool pool,CUserSocket.Closed closed) throws Exception;
}
