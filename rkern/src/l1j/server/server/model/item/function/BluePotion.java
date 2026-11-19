package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;

import l1j.server.server.model.network.core.sc.packet.SC_SkillIconGFX;
public class BluePotion
  extends L1ItemInstance
{
  public BluePotion(L1Item item)
  {
    super(item);
  }
  
  public static void checkCondition(L1PcInstance pc, L1ItemInstance item)
  {
    int itemId = item.getItemId();
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
    if (pc.hasSkillEffect(71))
    {
      SC_ServerMessage.send(pc, 698);
      return;
    }
    ItemReceiptOperations.UseDurableConsume(pc, item, () -> useBluePotion(pc, itemId));
  }
  
  private static void useBluePotion(L1PcInstance pc, int item_id)
  {
    pc.cancelAbsoluteBarrier();
    pc.cancelMoebius();
    
    int time = 0;
    int type = 0;
    switch (item_id)
    {
    case 30083: 
    case 40015: 
    case 40736: 
      type = 1002;
      time = 600;
      break;
    case 41142: 
      type = 20082;
      time = 300;
      break;
    case 140015: 
      type = 1002;
      time = 700;
      break;
    case 210114: 
      type = 1002;
      time = 1800;
    }
    if (pc.hasSkillEffect(1002)) {
      pc.removeSkillEffect(1002);
    }
    if (pc.hasSkillEffect(20082)) {
      pc.removeSkillEffect(20082);
    }
    SC_SkillIconGFX.send(pc, 34, time, true);
    SC_SkillSound.send(pc, pc.getId(), 190);
    SC_SkillSound.broadcast(pc, pc.getId(), 190);
    pc.setSkillEffect(type, time * 1000);
    SC_ServerMessage.send(pc, 1007);
  }
}
