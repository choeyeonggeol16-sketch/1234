package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.connection.CUserSocket;
public final class CS_CheckInventory {
    private CS_CheckInventory() {}
    /** Reject retired legacy pet equipment/inventory requests. */
    public static void handle(CUserSocket client, int petId) {}
}
