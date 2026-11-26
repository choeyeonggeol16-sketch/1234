package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.connection.CUserSocket;
public final class CS_SlaveControl {
    private CS_SlaveControl() {}
    /** Legacy pet-only packet. Companion commands use the companion protocol. */
    public static void handle(CUserSocket client, int petId, int targetId) {}
}
