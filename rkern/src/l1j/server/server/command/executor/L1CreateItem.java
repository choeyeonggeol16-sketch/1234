package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;
import l1j.server.server.templates.L1Item;

public class L1CreateItem implements L1CommandExecutor {

	private L1CreateItem() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1CreateItem();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
//			if (st.hasMoreTokens()) {  //추가
			String nameid = st.nextToken();
			int count = 1;
			if (st.hasMoreTokens()) {
				count = Integer.parseInt(st.nextToken());
			}
			int enchant = 0;
			if (st.hasMoreTokens()) {
				enchant = Integer.parseInt(st.nextToken());
			}
			 int attrenchant = 0;
			   if (st.hasMoreTokens()) {
			    attrenchant = Integer.parseInt(st.nextToken());
			   }
			   
			int isId = 0;
			if (st.hasMoreTokens()) {
				isId = Integer.parseInt(st.nextToken());
			}

			int itemid = 0;
			try {
				itemid = Integer.parseInt(nameid);
			} catch (NumberFormatException e) {
				itemid = ItemTable.getInstance(). findItemIdByNameWithoutSpace(
						nameid);
				if (itemid == 0) {
					SC_SystemMessage.send(pc, "해당 아이템이 발견되지 않습니다. ");
					return;
				}
			}
			L1Item temp = ItemTable.getInstance(). getTemplate(itemid);
			if (temp != null) {
				if (temp.isStackable()) {
					L1ItemInstance item = ItemTable.getInstance(). createItem(itemid);
					item.setEnchantLevel(0);
					item.setCount(count);
					if (isId == 1) {
						item.setIdentified(true);
					}
					if (pc.getInventory().checkAddItem(item, count) == L1Inventory.OK) {
						pc.getInventory().storeItem(item);
						SC_SystemMessage.send(pc, "\\fY아이템:[\\aA"+item.getLogName()+"\\fY] 아이템ID:[\\aA"+itemid+"\\fY] 인챈트:[\\aA"+enchant+"\\fY] \\aG생성");;
					}
				} else {
					L1ItemInstance item = null;
					int createCount;
					for (createCount = 0; createCount < count; createCount++) {
						item = ItemTable.getInstance(). createItem(itemid);
						item.setEnchantLevel(enchant);
						item.setAttrEnchantLevel(attrenchant);
						if (isId == 1) {
							item.setIdentified(true);
						}
						if (pc.getInventory(). checkAddItem(item, 1) == L1Inventory.OK) {
							pc.getInventory(). storeItem(item);
						} else {
							break;
						}
					}
					if (createCount > 0) {
						SC_SystemMessage.send(pc, "\\fY아이템:[\\aA"+item.getLogName()+"\\fY] 아이템ID:[\\aA"+itemid+"\\fY] 인챈트:[\\aA"+enchant+"\\fY] \\aG생성");;
					}
				}
			} else {
				SC_SystemMessage.send(pc, "지정 ID의 아이템은 존재하지 않습니다");
			}
//			}
		} catch (Exception e) {
		//	e.printStackTrace();
			SC_SystemMessage.send(pc, ".아이템 [이름] [갯수] [인챈] [속성1~20] [확인0~1] ");
		}
	}
}
