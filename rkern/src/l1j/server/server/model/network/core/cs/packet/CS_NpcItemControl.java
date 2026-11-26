package l1j.server.server.model.network.core.cs.packet;
import l1j.server.server.model.network.core.connection.CUserSocket;
public final class CS_NpcItemControl {
    private CS_NpcItemControl() {}
    /** Reject retired legacy pet equipment/inventory requests. */
    public static void handle(CUserSocket client, int data, int petId, int listNo) {}
}
