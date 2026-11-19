package l1j.server.server.model.network.core.sc.packet;
import l1j.server.server.model.Instance.L1PcInstance;
public class SC_ServerMessage{public static void send(L1PcInstance pc,int opcode){System.out.print("MSG:"+opcode+" ");}
public static void send(L1PcInstance pc,int opcode,String arg){System.out.print("MSG:"+opcode+":"+arg+" ");}}
