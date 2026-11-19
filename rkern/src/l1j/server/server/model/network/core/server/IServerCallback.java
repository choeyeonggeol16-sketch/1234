package l1j.server.server.model.network.core.server;

import l1j.server.server.model.network.core.connection.CIOBufferPool;
import l1j.server.server.model.network.core.connection.CUserSocket;
import l1j.server.server.model.network.core.transport.CSocket;
/** Original IServerCallback allocation role, adapted to the L1 Java owner. */
@FunctionalInterface
public interface IServerCallback {
    CUserSocket CreateSocket(CSocket socket,CIOBufferPool pool,CUserSocket.Closed closed) throws Exception;
}
