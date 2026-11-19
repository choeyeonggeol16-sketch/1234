package l1j.server.server.model.network.core.sc.packet;
import java.util.ArrayList;import java.util.List;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ServerMessage {
 public static final List<String> events=new ArrayList<>();
 public static void send(L1PcInstance pc,int code){events.add("M"+code);}
 public static void send(L1PcInstance pc,int code,String message){events.add("M"+code+":"+message);}
}