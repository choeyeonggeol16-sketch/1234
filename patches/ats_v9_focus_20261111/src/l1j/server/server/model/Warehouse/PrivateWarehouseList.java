package l1j.server.server.model.Warehouse;

public class PrivateWarehouseList extends WarehouseList {
	@Override
	protected PrivateWarehouse createWarehouse(String name) {
		PrivateWarehouse wh = new PrivateWarehouse(name);
		wh.loadItems();
		
		return wh;
	}
}
