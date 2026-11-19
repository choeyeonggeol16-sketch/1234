package l1j.server.server.command.executor;

import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1AttackLog
  implements L1CommandExecutor
{
  public static L1CommandExecutor getInstance()
  {
    return new L1AttackLog();
  }

  public void execute(L1PcInstance pc, String cmdName, String poby)
  {
//    if (poby.equals("켬")) {
//      pc.setAttackLog(true);
//      pc.sendPackets(new SC_SystemMessage( "\\aH공격로그가 활성화되었습니다."));
//    } else if (poby.equals("끔")) {
//      pc.setAttackLog(false);
//      pc.sendPackets(new SC_SystemMessage( "\\aH공격로그가 비활성화되었습니다."));
//    } else {
//      pc.sendPackets(new SC_SystemMessage( "\\aH." + cmdName + " <켬/끔>"));
//    }
  }
}