package l1j.server.server.model.Warehouse;

public class WarehouseManager {	
	private static WarehouseManager uniqueInstance = null;
	private PrivateWarehouseList plist = new PrivateWarehouseList();
	private ClanWarehouseList clist = new ClanWarehouseList();
	private PresentWarehouseList prlist = new PresentWarehouseList();
	
	public static WarehouseManager getInstance() {
		if (uniqueInstance == null) {
			if (uniqueInstance == null)
				uniqueInstance = new WarehouseManager();
		}
		return uniqueInstance;
	}

	public PrivateWarehouse getPrivateWarehouse(String name) {
		return (PrivateWarehouse) plist.findWarehouse(name);
	}
		
	public ClanWarehouse getClanWarehouse(String name) {
		return (ClanWarehouse) clist.findWarehouse(name);
	}
	
	public PresentWarehouse getPresentWarehouse(String name) {
		return (PresentWarehouse) prlist.findWarehouse(name);
	}
}
