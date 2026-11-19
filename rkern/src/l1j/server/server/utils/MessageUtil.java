package l1j.server.server.utils;

import java.io.PrintStream;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.L1World;
import l1j.server.server.model.network.core.sc.packet.SC_ChatPacket;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.model.network.core.sc.packet.SC_TopMessage;

public class MessageUtil
{

	
	
  public static void toAllPlayers(String message)
  {
    SC_SystemMessage.broadcast(message);
  }
  

  public static void greenMessageToAllPlayers(String message)
  {
    SC_TopMessage.broadcastAll(message);
  }
  
  @SuppressWarnings("null")
 	public static void toPlayers(String message)
 	  {
 		 L1PcInstance pc = null;
 		 L1PcInstance pc1 = (L1PcInstance) pc;
 		 SC_SystemMessage.send(pc1, message);
 	  }
  
  public static void putCmd(String message)
  {
    System.out.println(message);
  }
}
