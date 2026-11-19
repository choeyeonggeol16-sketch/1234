package l1j.server.server.command.executor;

import java.util.StringTokenizer;
import java.util.logging.Logger;

import l1j.server.server.datatables.NpcSpawnTable;
import l1j.server.server.datatables.SpawnTable;
import l1j.server.server.model.L1Object;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1MerchantInstance;
import l1j.server.server.model.Instance.L1MonsterInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;


public class L1NpcDown implements L1CommandExecutor{
  private static Logger _log = Logger.getLogger(L1NpcDown.class.getName());

  public static L1CommandExecutor getInstance()
  {
    return new L1NpcDown();
  }

  public void execute(L1PcInstance pc, String cmdName, String poby)
  {
    try {
      StringTokenizer token = new StringTokenizer(poby);
      String type = token.nextToken();
      String spawn = token.nextToken();

      if (type.equals("몬스터")) {
        if (spawn.equals("배치")) {
          SpawnTable.getInstance().reload();
 
          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 몬스터가 재배치 되었습니다.");
        } else if (spawn.equals("삭제")) {
        	 for (L1Object obj : L1World.getInstance().getAllObj()) {
        		 if ((obj != null) && ((obj instanceof L1MonsterInstance))) {
        			 L1MonsterInstance mon = (L1MonsterInstance) obj;
        			 mon.setRespawn(false);
        			 mon.re();   
        		 }
          }
          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 몬스터가 삭제 되었습니다.");
        } else {
          throw new Exception();
        }
      } else if (type.equals("엔피씨")) {
        if (spawn.equals("배치")) {
        	NpcSpawnTable.getInstance().reload();

          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 NPC가 재배치 되었습니다.");
        } else if (spawn.equals("삭제")) {
        	  for (L1Object npc : L1World.getInstance().getAllObj()) {
        		  if ((npc != null) && npc instanceof L1MerchantInstance) {
        			  L1MerchantInstance merchent = (L1MerchantInstance)npc;
	        			  merchent.setRespawn(false);
	        			  merchent.deleteMe();
                    }
                  }
         

          SC_PacketBox.broadcastGreen("[긴급공지] 월드전체 NPC가 삭제 되었습니다.");
        } else {
          throw new Exception();
        }
      }
      else throw new Exception(); 
    }
    catch (Exception e)
    {
    	SC_SystemMessage.send(pc, "." + cmdName + " <몬스터/엔피씨> <배치/삭제>");
    }
  }
}