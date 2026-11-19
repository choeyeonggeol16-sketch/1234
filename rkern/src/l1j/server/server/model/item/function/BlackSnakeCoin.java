package l1j.server.server.model.item.function;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.character.skill.L1SkillId;
import l1j.server.server.model.character.skill.L1SkillUse;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.templates.L1Item;

public class BlackSnakeCoin
  extends L1ItemInstance
{
  public BlackSnakeCoin(L1Item item)
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
    ItemReceiptOperations.UseDurableConsume(pc, item, () -> 흑사(pc, itemId));
  }
  
  private static void 흑사(L1PcInstance pc, int item_id) {
    pc.cancelAbsoluteBarrier();
    pc.cancelMoebius();
    
    
    if (pc.hasSkillEffect(L1SkillId.God_buff)) {
    	pc.removeSkillEffect(L1SkillId.God_buff);
	}      
	L1SkillUse.cast(
			pc,
			L1SkillId.God_buff,
			pc.getId(), pc.getX(), pc.getY(),
			null,
			0,
			L1SkillUse.TYPE_GMBUFF);

  }
}
