package l1j.server.server.model.item.function;
import l1j.server.server.model.network.core.sc.packet.SC_SkillSound;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_ServerMessage;

import l1j.server.server.templates.L1Item;

public class CurePotion
  extends L1ItemInstance
{
  public CurePotion(L1Item item)
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
    ItemReceiptOperations.UseDurableConsume(pc, item, () -> useCurePotion(pc, itemId));
  }
  
  private static void useCurePotion(L1PcInstance pc, int item_id) {
    pc.cancelAbsoluteBarrier();
    pc.cancelMoebius();
    
    SC_SkillSound.send(pc, pc.getId(), 192);
    SC_SkillSound.broadcast(pc, pc.getId(), 192);
    pc.curePoison();
  }
}
