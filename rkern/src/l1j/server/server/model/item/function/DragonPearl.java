package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.templates.L1Item;

public class DragonPearl
  extends L1ItemInstance
{
  public DragonPearl(L1Item item)
  {
    super(item);
  }
  
  public static void checkCondition(L1PcInstance pc, L1ItemInstance item)
  {
    int itemId = item.getItemId();
    if (pc.hasSkillEffect(71))
    {
      SC_ServerMessage.send(pc, 698);
      return;
    }
    if ((pc.hasSkillEffect(157)) || 
      (pc.hasSkillEffect(33)) || 
      (pc.hasSkillEffect(10101)) || 
      (pc.hasSkillEffect(30007)) || 
      (pc.hasSkillEffect(30002)) || 
      (pc.hasSkillEffect(30006)) || 
      (pc.hasSkillEffect(40007)) || 
      (pc.hasSkillEffect(30081)) || 
      (pc.hasSkillEffect(22055)) || 
      (pc.hasSkillEffect(22025)) || 
      (pc.hasSkillEffect(22026)) || 
      (pc.hasSkillEffect(22027)) || 
      (pc.hasSkillEffect(30004)) || 
      (pc.hasSkillEffect(87)) || 
      (pc.hasSkillEffect(208)) || 
      (pc.hasSkillEffect(103)) || 
      (pc.hasSkillEffect(212)) || 
      (pc.hasSkillEffect(123))) {
      return;
    }
    // The effect implementation below is disabled. Never debit an item
    // for an inactive effect (also applies to any future direct caller).
    return;
  }
  private static void useDragonPearl(L1PcInstance pc, int item_id)
  {
	// 드래곤의 진주
	  
	  /*
			if (pc.hasSkillEffect(DECAY_POTION) == true) { // 디케이포션 상태
				SC_ServerMessage.send(pc, 698);
				return;
			}
			pc.cancelAbsoluteBarrier();
			int time = (10 * 60 * 1000) + 1000;
			if (pc.hasSkillEffect(STATUS_DRAGON_PEARL)) {
				pc.killSkillEffectTimer(STATUS_DRAGON_PEARL);
				RPacketBox.send(pc, RPacketBox.DRAGON_PEARL, 8, 0);
				SC_Liquor.send(pc, pc.getId(), 0);
				pc.setPearl(0);
			}
			SC_ServerMessage.send(pc, 1065);
			pc.sendPackets(new S_SkillSound(pc.getId(), 197));
			pc.broadcastPacket(new S_SkillSound(pc.getId(), 197));

			pc.sendPackets(new S_SkillSound(pc.getId(), 15355));
			pc.broadcastPacket(new S_SkillSound(pc.getId(), 15355));
			pc.setSkillEffect(STATUS_DRAGON_PEARL, time);
			SC_Liquor.send(pc, pc.getId(), 8);
			SC_Liquor.broadcast(pc, pc.getId(), 8);
			RPacketBox.send(pc, RPacketBox.DRAGON_PEARL, 8, 151);
			pc.setPearl(1);
		*/
  }
}
//  private static void useDragonPearl(L1PcInstance pc, int item_id)
//  {
//    pc.cancelAbsoluteBarrier();
//    pc.cancelMoebius();
//    
//    int time = 601000;
//    if (pc.hasSkillEffect(22017))
//    {
//      pc.killSkillEffectTimer(22017);
//      SC_Liquor.send(pc, pc.getId(), 0);
//      pc.sendPackets(new RPacketBox(60, 0, 0));
//      pc.setPearl(0);
//    }
//    pc.sendPackets(new SC_ServerMessage(1065));
//    pc.sendPackets(new S_SkillSound(pc.getId(), 7976));
//    pc.broadcastPacket(new S_SkillSound(pc.getId(), 7976));
//    pc.setSkillEffect(22017, time);
//    SC_Liquor.send(pc, pc.getId(), 8);
//    SC_Liquor.broadcast(pc, pc.getId(), 8);
//    pc.sendPackets(new RPacketBox(60, 8, 601));
//    pc.setPearl(1);
//  }
//}
