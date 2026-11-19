package l1j.server.server.model.Warehouse;

public class SupplementaryServiceList extends WarehouseList {
	@Override
	protected SupplementaryService createWarehouse(String name) {
		return new SupplementaryService(name);
	}
}
