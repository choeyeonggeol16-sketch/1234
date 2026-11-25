package l1j.server.server.command.executor;

import l1j.server.server.datatables.CObjectDB;
import java.util.StringTokenizer;

import l1j.server.server.model.Instance.L1ItemInstance;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.model.network.core.sc.packet.SC_SystemMessage;

public class L1DescIdWeapon implements L1CommandExecutor {
	private L1DescIdWeapon() {
	}

	public static L1CommandExecutor getInstance() {
		return new L1DescIdWeapon();
	}

	@Override
	public void execute(L1PcInstance pc, String cmdName, String arg) {
		try {
			StringTokenizer st = new StringTokenizer(arg);
			int descid = Integer.parseInt(st.nextToken(), 10);
			int count = Integer.parseInt(st.nextToken(), 10);
			int enchant = 0;
			int attrLevel = 0;
			if (st.hasMoreTokens()) {
				enchant = Integer.parseInt(st.nextToken());
			}
			
			if(st.hasMoreTokens()){
				attrLevel = Integer.parseInt(st.nextToken());
			}
			
			int countA = -1;
			L1ItemInstance item = null;
			for (int i = 0; i < count; i++) {
				if (pc.getInventory().getSize() > 255) {
					SC_SystemMessage.send(pc, "\\aG인벤보관 아이템의 최대 가짓수는 256개입니다.");
					break;
				}
				countA++;
				item = CObjectDB.LoadItem().createItem(87);
				item.getItem().setItemDescId(descid + i);
				item.getItem().setGfxId(1945);
				item.setCount(1);
				item.setEnchantLevel(enchant);
				item.setAttrEnchantLevel(attrLevel);
				item.getItem().setName(String.valueOf(descid + i));
				item.getItem().setNameId(String.valueOf(descid + i));
				item.setIdentified(true);
				pc.getInventory().CreateOrAddItem(item);
			}
			SC_SystemMessage.send(pc, "\\aH웨폰 " + descid + "~" + String.valueOf(descid + countA) + " 생성 되었습니다.");
		} catch (Exception exception) {
			int count = 0;
			for (L1ItemInstance item : pc.getInventory().getItems()) {
				if (item.getItemId() == 87) {
					pc.getInventory().DeleteItemAmount(item);
					count++;
				}
			}
			if (count > 0) {
				SC_SystemMessage.send(pc, "\\aA웨폰 확인용 아이템("
						+ count + ")이 삭제되었습니다.");
			} else {
				SC_SystemMessage.send(pc, cmdName + " [id] [출현시키는 수] [인챈]로 입력해 주세요. ");
			}
		}
	}
}
