package l1j.server.server.datatables;

/** Legacy source-compatible type; storage and logic are owned by CObjectDB. */
public class NpcTable extends CObjectDB.NpcData {
    NpcTable() { super(); }

    public static NpcTable getInstance() {
        return CObjectDB.LoadNPC();
    }

    public static void reload() {
        CObjectDB.ReloadNPC();
    }
}
