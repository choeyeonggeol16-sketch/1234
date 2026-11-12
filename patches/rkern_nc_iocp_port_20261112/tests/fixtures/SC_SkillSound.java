package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.L1Character;
/** Visual transport only; the real skill handler/Resistance/stop callback run. */
public final class SC_SkillSound {
 public static long pack(int id,int gfx){return ((long)id<<32)|(gfx&0xffffL);}
 public static void sendPacked(L1Character target,long value){}
 public static void broadcastFastPacked(L1Character target,long value){}
}
