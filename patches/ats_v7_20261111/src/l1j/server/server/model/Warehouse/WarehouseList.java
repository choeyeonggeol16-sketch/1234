package l1j.server.server.model.Warehouse;

import java.util.Vector;

public abstract class WarehouseList {
	private Vector<Warehouse> warehouseList; 
	
	public WarehouseList() {
		warehouseList = new Vector<Warehouse>();
	}
	
	protected abstract Warehouse createWarehouse(String name);

	public Warehouse findWarehouse(String name) {
		synchronized(this) {
			Warehouse warehouse = null;
			for(Warehouse wh : warehouseList) {			
				if(wh.getName() == name) {
					return wh;
				}
			}
			warehouse = createWarehouse(name);
			warehouseList.add(warehouse);
			return warehouse;
		}
	}
}
