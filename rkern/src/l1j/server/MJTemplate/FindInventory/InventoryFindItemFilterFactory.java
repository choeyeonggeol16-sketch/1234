package l1j.server.MJTemplate.FindInventory;

import l1j.server.server.model.Instance.L1ItemInstance;

public class InventoryFindItemFilterFactory {
	public static InventoryFindItemFilter createFishItemsFilter(){
		return new InventoryFindItemFilter(){
			@Override
			public boolean isFilter(L1ItemInstance item){
				switch(item.getItemId()){
				case 41297:
				case 41296:
				case 41301:
				case 41304:
				case 41303:
				case 600230:
				case 820018:
				case 49092:
				case 49093:
				case 49094:
				case 49095:
					return false;
					
				default:
					if(item.getItem().isEndedTimeMessage())
						return false;
						
				}
				return true;
			}
		};
	}
}
