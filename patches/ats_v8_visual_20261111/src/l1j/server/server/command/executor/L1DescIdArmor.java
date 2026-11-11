package l1j.server.server.command.executor;

import java.util.StringTokenizer;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.serverpackets.S_SystemMessage;

public class L1DescIdArmor implements L1CommandExecutor {
	private L1DescIdArmor() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1DescIdArmor();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int descid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			int enchant = 0;
			if (st.hasMoreTokens()) {
				enchant = Integer.parseInt(st.nextToken());
			}
			int countA = -1;
			ItemInstance item = null;
			for (int i = 0; i < count; i++) {
				if (pc.getInventory().getSize() > 255) {
					pc.sendPackets(new S_SystemMessage("\\fY인벤보관 아이템의 최대 가짓수는 256개입니다."));
					break;
				}
				countA++;
				item = ItemTable.getInstance().createItem(20456);
				item.getItem().setItemDescId(descid + i);
				item.getItem().setGfxId(1945);
				item.setCount(1);
				item.setEnchantLevel(enchant);
				item.getItem().setName(String.valueOf(descid + i));
				item.getItem().setNameId(String.valueOf(descid + i));
				item.setIdentified(true);
				pc.getInventory().storeItem(item);
			}
			pc.sendPackets(new S_SystemMessage("\\aH아머 " + descid + "~" + String.valueOf(descid + countA) + " 생성 되었습니다."));
		} catch (Exception exception) {
			int count = 0;
			for (ItemInstance item : pc.getInventory().getItems()) {
				if (item.getItemId() == 20456) {
					pc.getInventory().deleteItem(item);
					count++;
				}
			}
			if (count > 0) {
				pc.sendPackets(new S_SystemMessage("\\aA아머 확인용 아이템("
						+ count + ")이 삭제되었습니다."));
			} else {
				pc.sendPackets(new S_SystemMessage(cmdName + " [id] [출현시키는 수] [인챈]로 입력해 주세요. "));
			}
		}
	}
}
