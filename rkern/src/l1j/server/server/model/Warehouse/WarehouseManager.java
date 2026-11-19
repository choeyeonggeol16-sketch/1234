package l1j.server.server.model.Warehouse;

public class WarehouseManager {	
	private static volatile WarehouseManager uniqueInstance = null;
	private final PrivateWarehouseList plist = new PrivateWarehouseList();
	private final ElfWarehouseList elist = new ElfWarehouseList();
	private final ClanWarehouseList clist = new ClanWarehouseList();
	private final SupplementaryServiceList supplist = new SupplementaryServiceList();
	private final SpecialWarehouseList speciallist = new SpecialWarehouseList();
	
	public static WarehouseManager getInstance() {
		if(uniqueInstance == null) {
			synchronized(ElfWarehouseList.class){
				if(uniqueInstance == null)
					uniqueInstance = new WarehouseManager();
			}
		}
		return uniqueInstance;
	}
	
	public PrivateWarehouse getPrivateWarehouse(String name) {
		return (PrivateWarehouse) plist.findWarehouse(name);
	}
	
	public ElfWarehouse getElfWarehouse(String name) {
		return (ElfWarehouse) elist.findWarehouse(name);
	}
	
	public ClanWarehouse getClanWarehouse(String name) {
		return (ClanWarehouse) clist.findWarehouse(name);
	}
	
	public SupplementaryService getSupplementaryService(String name) {
		return (SupplementaryService) supplist.findWarehouse(name);
	}
	
	public SpecialWarehouse getSpecialWarehouse(String name) {
		return (SpecialWarehouse) speciallist.findWarehouse(name);
	}
	
	
	/**아이템삭제 명령어*/
	public PrivateWarehouse getPrivateItems(String name) {
		return (PrivateWarehouse) plist.findWarehouse1(name);
	}
	
	public ElfWarehouse getElfItems(String name) {
		return (ElfWarehouse) elist.findWarehouse1(name);
	}
	
	public ClanWarehouse getClanItems(String name) {
		return (ClanWarehouse) clist.findWarehouse1(name);
	}
	
	public SupplementaryService getSupplementaryItems(String name) {
		return (SupplementaryService) supplist.findWarehouse1(name);
	}
	
	public void delPrivateWarehouse(String name) {
		plist.delWarehouse(name);
	}
	
	public void delElfWarehouse(String name) {
		elist.delWarehouse(name);
	}
	
	public void delClanWarehouse(String name) {
		clist.delWarehouse(name);
	}
	
	public void delSupplementaryService(String name) {
		supplist.delWarehouse(name);
	}
	
	public void delSpecialWarehouse(String name) {
		speciallist.delWarehouse(name);
	}
}
