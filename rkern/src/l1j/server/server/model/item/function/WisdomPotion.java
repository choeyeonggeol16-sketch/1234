package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.templates.L1Item;
import l1j.server.server.model.network.core.sc.packet.SC_SkillIconWisdomPotion;
import l1j.server.server.model.character.skill.L1SkillId;

public class WisdomPotion
  extends L1ItemInstance
{
  public WisdomPotion(L1Item item)
  {
    super(item);
  }
  
  public static void checkCondition(L1PcInstance pc, L1ItemInstance item)
  {
    int itemId = item.getItemId();
    final int seconds = durationSeconds(itemId);
    if (seconds == 0) return; // An unknown item must not be debited or grant a permanent buff.
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
    if (!pc.isWizard() && !pc.isBlackwizard()) {
      SC_ServerMessage.send(pc, 79);
      return;
    }
    ItemReceiptOperations.UseDurableConsume(pc, item, () -> useWisdomPotion(pc, seconds));
  }
  
  private static int durationSeconds(int itemId) {
    return switch (itemId) {
      case 40016, 30089 -> 300;
      case 140016 -> 360;
      case 210113 -> 1000;
      default -> 0;
    };
  }

  private static void useWisdomPotion(L1PcInstance pc, int time)
  {
    pc.cancelAbsoluteBarrier();
    pc.cancelMoebius();
    // A single managed skill source owns both SP +2 and MPR +2.
    pc.applySkillModifierFromOwner(L1SkillId.STATUS_WISDOM_POTION, 0L);
    pc.setSkillEffect(L1SkillId.STATUS_WISDOM_POTION, time * 1000);
    SC_SkillIconWisdomPotion.send(pc, time);
    SC_SkillSound.send(pc, pc.getId(), 750);
    SC_SkillSound.broadcast(pc, pc.getId(), 750);
  }
}
