package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.MovementSpeedEffects;
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
    final int seconds = durationSeconds(itemId);
    if (seconds == 0) return; // Reject unknown potion IDs before durable debit.
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
    ItemReceiptOperations.UseDurableConsume(pc, item, () -> useGreenPotion(pc, seconds));
  }
  
  private static int durationSeconds(int itemId) {
    switch (itemId) {
      case 40013: return 300;
      case 140013:
      case 875640293: return 350;
      case 40018:
      case 41342: return 1800;
      case 140018:
      case 41338: return 2250;
      case 40039: return 600;
      case 40040: return 900;
      case 40030:
      case 30067: return 300;
      case 41261:
      case 41262:
      case 41268:
      case 41269:
      case 41271:
      case 41272:
      case 41273: return 30;
      default: return 0;
    }
  }

  private static void useGreenPotion(L1PcInstance pc, int time)
  {
    pc.cancelAbsoluteBarrier();
    pc.cancelMoebius();
    SC_SkillSound.send(pc, pc.getId(), 191);
    SC_SkillSound.broadcastFast(pc, pc.getId(), 191);
    if (pc.getHasteItemEquipped() > 0) {
      return;
    }
    pc.setDrink(false);
    if (pc.hasSkillEffect(L1SkillId.HASTE))
    {
      pc.killSkillEffectTimer(L1SkillId.HASTE);
      MovementSpeedEffects.endHaste(pc, L1SkillId.HASTE);
    }
    else if (pc.hasSkillEffect(L1SkillId.GREATER_HASTE))
    {
      pc.killSkillEffectTimer(L1SkillId.GREATER_HASTE);
      MovementSpeedEffects.endHaste(pc, L1SkillId.GREATER_HASTE);
    }
    if (!MovementSpeedEffects.cancelSlowWithPotion(pc))
    {
      // No previous haste timer: absence returns -1, not zero seconds.
      int skilltime = pc.hasSkillEffect(L1SkillId.STATUS_HASTE)
          ? pc.getSkillEffectTimeSec(L1SkillId.STATUS_HASTE) : 0;
      skilltime += time;
      if (skilltime >= 7200) {
        skilltime = 7200;
      }
      SC_SkillHaste.send(pc, pc.getId(), 1, skilltime);
      SC_SkillHaste.broadcastFast(pc, pc.getId(), 1, 0);
      pc.setMoveSpeed(1);
      pc.setSkillEffect(L1SkillId.STATUS_HASTE, skilltime * 1000);
    }
    //if(pc.getBug_check() == 0){pc.sendPackets(new SC_SystemMessage("가속 효과: 이동 및 공격 속도 향상"));}
  }
}
