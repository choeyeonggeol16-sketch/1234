package l1j.server.MJTemplate.Reward;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.Instance.L1PcInstance;
import l1j.server.server.templates.L1Item;

public class ItemReward extends AbstractReward{
	public static ItemReward newInstance(){
		return new ItemReward();
	}
	
	private ItemReward(){}
	
	@Override
	public void do_reward(L1PcInstance pc) {
		int item_id = get_reward_asset_id();
		int item_amount = get_reward_amount();
		L1Item item = ItemTable.getInstance().getTemplate(item_id);
		pc.sendPackets(String.format("아이템 %s을(를) %d개 보상받았습니다.", item.getName(), item_amount));
		if(!item.isStackable() && item_amount > 1){
			for(int i=item_amount - 1; i>=0; --i)
				pc.getInventory().storeItem(item_id, 1);
		}else{
			pc.getInventory().storeItem(item_id, item_amount);			
		}
	}
}
