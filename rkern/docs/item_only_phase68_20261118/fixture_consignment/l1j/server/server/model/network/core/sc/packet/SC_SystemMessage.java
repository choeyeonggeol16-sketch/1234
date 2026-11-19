package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.*;
public class SC_SystemMessage{public static void send(L1PcInstance pc,String text){pc.log.add("message:"+text);}public static Object nativeWriter(String text){return text;}}
