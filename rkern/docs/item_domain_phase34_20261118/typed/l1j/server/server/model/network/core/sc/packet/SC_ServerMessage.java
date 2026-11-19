package l1j.server.server.model.network.core.sc.packet;
import java.util.*;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ServerMessage {
 public static final List<String> events=new ArrayList<>();
 public static void send(L1PcInstance pc,int code,String content){events.add(code+":"+content);}
}