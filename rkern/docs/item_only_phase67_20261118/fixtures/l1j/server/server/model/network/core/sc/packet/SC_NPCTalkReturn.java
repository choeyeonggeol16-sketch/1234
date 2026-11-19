package l1j.server.server.model.network.core.sc.packet;
import java.util.*;
import l1j.server.server.model.item.Card.Trace;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_NPCTalkReturn {
 public static boolean send(L1PcInstance pc,int id,String html) { Trace.add(html+":[]");return true; }
 public static boolean send(L1PcInstance pc,int id,String html,String[] a) { Trace.add(html+":"+Arrays.toString(a));return true; }
 public static boolean sendList(L1PcInstance pc,int id,String html,List<String> a){Trace.add(html+":"+a.toString());return true;}
}
