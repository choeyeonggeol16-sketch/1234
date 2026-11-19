package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public final class SC_InventoryIcon {public static void icoNew(L1PcInstance pc,int id,int gfx,int duration,boolean display){
 L1PcInstance.events.append("ICON:").append(id).append(':').append(gfx).append(':').append(duration).append(':').append(display).append('|');}}
