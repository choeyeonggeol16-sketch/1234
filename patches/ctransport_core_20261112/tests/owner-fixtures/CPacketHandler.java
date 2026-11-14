package l1j.server.server.model.network.packet;
import l1j.server.server.model.network.buffer.ReadBuffer;
import l1j.server.server.model.network.core.session.CIOClient;
public final class CPacketHandler {
 public interface PacketHandler { boolean execute(ReadBuffer buffer,CIOClient client); }
 public static final PacketHandler[] HANDLERS=new PacketHandler[256];
 public static boolean isFastOpcode(int opcode){return true;}
 public static boolean isDirectOpcode(int opcode){return true;}
 public static boolean isSessionOpcode(int opcode){return false;}
}
