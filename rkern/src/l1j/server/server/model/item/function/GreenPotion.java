package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;
import l1j.server.server.templates.L1Item;

import l1j.server.server.model.network.core.sc.packet.SC_SkillHaste;
public class GreenPotion
  extends L1ItemInstance
{
  public GreenPotion(L1Item item)
  {
    super(item);
  }
  
  public static void checkConditon(L1PcInstance pc, L1ItemInstance item)
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
    ItemReceiptOperations.UseDurableConsume(pc, item, () -> useGreenPotion(pc, itemId));
  }
  
  private static void useGreenPotion(L1PcInstance pc, int itemId)
  {
    pc.cancelAbsoluteBarrier();
    pc.cancelMoebius();
    
    int time = 0;
    if (itemId == 40013) {
      time = 300;
    } else if (itemId == 140013 || itemId == 875640293) {
      time = 350;
    } else if ((itemId == 40018) || (itemId == 41342)) {
      time = 1800;
    } else if ((itemId == 140018) || (itemId == 41338)) {
      time = 2250;
    } else if (itemId == 40039) {
      time = 600;
    } else if (itemId == 40040) {
      time = 900;
    } else if ((itemId == 40030) || (itemId == 30067)) {
      time = 300;
    } else if ((itemId == 41261) || (itemId == 41262) || (itemId == 41268) || (itemId == 41269) || (itemId == 41271) || (itemId == 41272) || (itemId == 41273)) {
      time = 30;
    }
    SC_SkillSound.send(pc, pc.getId(), 191);
    SC_SkillSound.broadcastFast(pc, pc.getId(), 191);
    if (pc.getHasteItemEquipped() > 0) {
      return;
    }
    pc.setDrink(false);
    if (pc.hasSkillEffect(43))
    {
      pc.killSkillEffectTimer(43);
      SC_SkillHaste.send(pc, pc.getId(), 0, 0);
      SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
      pc.setMoveSpeed(0);
    }
    else if (pc.hasSkillEffect(54))
    {
      pc.killSkillEffectTimer(54);
      SC_SkillHaste.send(pc, pc.getId(), 0, 0);
      SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
      pc.setMoveSpeed(0);
    }
    if (pc.hasSkillEffect(29))
    {
      pc.killSkillEffectTimer(29);
      SC_SkillHaste.send(pc, pc.getId(), 0, 0);
      SC_SkillHaste.broadcastFast(pc, pc.getId(), 0, 0);
    }
    else
    {
      int skilltime = pc.getSkillEffectTimeSec(1001);
      skilltime += time;
      if (skilltime >= 7200) {
        skilltime = 7200;
      }
      SC_SkillHaste.send(pc, pc.getId(), 1, skilltime);
      SC_SkillHaste.broadcastFast(pc, pc.getId(), 1, 0);
      pc.setMoveSpeed(1);
      pc.setSkillEffect(1001, skilltime * 1000);
    }
    //if(pc.getBug_check() == 0){pc.sendPackets(new SC_SystemMessage("가속 효과: 이동 및 공격 속도 향상"));}
  }
}
