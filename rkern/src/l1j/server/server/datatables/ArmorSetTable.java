package l1j.server.server.datatables;

/** Legacy source-compatible type; storage and logic are owned by CObjectDB. */
public class ArmorSetTable extends CObjectDB.SetData {
    ArmorSetTable() { super(); }

    public static ArmorSetTable getInstance() {
        return CObjectDB.LoadSetItem();
    }

    public static void reload() {
        CObjectDB.ReloadSetItem();
    }
}
