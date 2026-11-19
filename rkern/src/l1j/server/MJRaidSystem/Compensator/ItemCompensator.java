package l1j.server.MJRaidSystem.Compensator;

import java.util.concurrent.ThreadLocalRandom;

import l1j.server.server.datatables.ItemTable;
import l1j.server.server.model.L1Inventory;
import l1j.server.server.model.L1World;
import l1j.server.server.model.Instance.L1ItemInstance;

public class ItemCompensator implements CompensatorElement{
	
	private int	_itemid;
	private int	_count;
	private int _rate;
	
	public ItemCompensator(int itemid, int count, int rate){
		_itemid = itemid;
		_count	= count;
		_rate	= rate;
	}
	
	@Override
	public void compensate(Object obj) {
		if(_itemid < 0 || _count <= 0)
			return;
		
		if(ThreadLocalRandom.current().nextInt(100) + 1 > _rate)
			return;
		
		try{
			int[] arr 	= (int[])obj;
			int mid		= arr[0];
			int x		= arr[1];
			int y		= arr[2];
			int xi		= 1;
			int yi		= 1;
			if(ThreadLocalRandom.current().nextBoolean())
				xi = -1;
			if(ThreadLocalRandom.current().nextBoolean())
				yi = -1;
			
			L1Inventory inv = L1World.getInstance().getInventory(x + (ThreadLocalRandom.current().nextInt(3) * xi), y + (ThreadLocalRandom.current().nextInt(3) * yi), (short)mid);
			L1ItemInstance item = ItemTable.getInstance().createItem(_itemid);
			if(item == null){
				System.out.println(String.format("레이드 보상 : %d 아이템을 찾을 수 없습니다.", _itemid));
				return;
			}
			item.setCount(_count);
			inv.storeTradeItem(item);			
		}catch(Exception e){
			e.printStackTrace();
		}
	}
}
