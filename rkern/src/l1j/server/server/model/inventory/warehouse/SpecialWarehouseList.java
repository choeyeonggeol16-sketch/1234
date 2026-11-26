package l1j.server.server.model.inventory.warehouse;

public class SpecialWarehouseList extends WarehouseList {
	@Override
	protected SpecialWarehouse createWarehouse(String name) {
		return new SpecialWarehouse(name);
	}
}
