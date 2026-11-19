package l1j.server.server.model.item.function.Change;


import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.Config;
import l1j.server.server.model.L1PcInventory;
import l1j.server.server.model.network.core.sc.packet.SC_PacketBox;
import l1j.server.server.model.network.core.sc.packet.SC_PremiumShopSellList;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.utils.TYUtil;

public class ChangeItem1_4
{
  public static void ClickToItem(L1PcInstance pc, L1ItemInstance item)
  {
	  
    int itemid = item.getItemId();
    int type = -1;
    if (pc.getInventory().getSize() >= 180)
    {
      SC_SystemMessage.send(pc, "아이템 사용 실패 : 인벤토리 갯수 줄인 후 사용가능");
      return;
    }
    if (item.getEndTime() != null)
    {
      SC_SystemMessage.send(pc, "\\fW시간제 아이템은 불가능합니다.");
      return;
    }
    int[] bossIds = TYUtil.GetIntTokenizeIntArray(Config.ChangeItemList4);
    for (int i = 0; i < bossIds.length; i++) {
      if (itemid == bossIds[i]) {
        type = 0;
      }
    }
    if (type == -1)
    {
      SC_SystemMessage.send(pc, "교환할 수 없는 아이템입니다");
    }
    else
    {
    	SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3변경창을 취소 할 경우, 원활한 상점이용을 위해 리스한번 해주시길 바랍니다.");
    	SC_PacketBox.send(pc, SC_PacketBox.GREEN_MESSAGE, "\\f3리스를 안 할 경우, 발생한 불이익은 책임지지 않습니다.");
      SC_PremiumShopSellList.send(pc, 88004);
      pc.setIsChangeItem3(true);
      pc.setDefaultItem3(item);
    }
  }
}
