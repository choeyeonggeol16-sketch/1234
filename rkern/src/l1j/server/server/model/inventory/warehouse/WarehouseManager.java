package l1j.server.server.model.inventory.warehouse;

public class WarehouseManager {
    private static final java.util.concurrent.atomic.AtomicLong SESSION_LEASES =
            new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.logging.Logger LOG =
            java.util.logging.Logger.getLogger(WarehouseManager.class.getName());

    public static long nextSessionLease() {
        final long lease = SESSION_LEASES.incrementAndGet();
        if (lease <= 0L) throw new IllegalStateException("Warehouse session lease exhausted");
        return lease;
    }

    public PrivateWarehouse bindPrivateWarehouse(String name, long lease) {
        return (PrivateWarehouse) plist.bindWarehouse(name, lease);
    }

    public ElfWarehouse bindElfWarehouse(String name, long lease) {
        return (ElfWarehouse) elist.bindWarehouse(name, lease);
    }

    public SupplementaryService bindSupplementaryService(String name, long lease) {
        return (SupplementaryService) supplist.bindWarehouse(name, lease);
    }

    public SpecialWarehouse bindSpecialWarehouse(String name, long lease) {
        return (SpecialWarehouse) speciallist.bindWarehouse(name, lease);
    }

    public void releaseSessionWarehouses(String account, String character, long lease) {
        if (lease <= 0L) return;
        release(plist, account, lease);
        release(elist, account, lease);
        release(supplist, account, lease);
        release(speciallist, character, lease);
    }

    private static void release(WarehouseList list, String name, long lease) {
        try {
            list.releaseWarehouse(name, lease);
        } catch (RuntimeException failure) {
            // Keep the cache fail-closed if world removal or a fence fails;
            // do not abort other logout cleanup.
            LOG.log(java.util.logging.Level.SEVERE, "Warehouse retirement failed: " + name, failure);
        }
    }
	
	private static final WarehouseManager INSTANCE = new WarehouseManager();
	private final PrivateWarehouseList plist = new PrivateWarehouseList();
	private final ElfWarehouseList elist = new ElfWarehouseList();
	private final ClanWarehouseList clist = new ClanWarehouseList();
	private final SupplementaryServiceList supplist = new SupplementaryServiceList();
	private final SpecialWarehouseList speciallist = new SpecialWarehouseList();
	
	public static WarehouseManager getInstance() {
		return INSTANCE;
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
		return (PrivateWarehouse) plist.findWarehouse(name);
	}
	
	public ElfWarehouse getElfItems(String name) {
		return (ElfWarehouse) elist.findWarehouse(name);
	}
	
	public ClanWarehouse getClanItems(String name) {
		return (ClanWarehouse) clist.findWarehouse(name);
	}
	
	public SupplementaryService getSupplementaryItems(String name) {
		return (SupplementaryService) supplist.findWarehouse(name);
	}
	
	public void delPrivateWarehouse(String name) {
		plist.delWarehouse(name);
	}
	
	public void delElfWarehouse(String name) {
		elist.delWarehouse(name);
	}
	
    public void retireDisbandedClanWarehouse(String name, ClanWarehouse expected) {
        clist.retireEmptyClanWarehouseWhenIdle(name, expected);
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
