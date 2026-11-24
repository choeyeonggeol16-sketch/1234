package l1j.server.server.datatables;

/** Legacy source-compatible type; storage and logic are owned by CObjectDB. */
public class ItemTable extends CObjectDB.ItemData {
    ItemTable() { super(); }

    public static ItemTable getInstance() {
        return CObjectDB.LoadItem();
    }

    public static void reload() {
        CObjectDB.ReloadItem();
    }
}
