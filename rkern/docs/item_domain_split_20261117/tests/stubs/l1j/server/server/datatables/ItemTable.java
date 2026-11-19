package l1j.server.server.datatables;
import l1j.server.server.templates.L1Item;
public class ItemTable {public static int missing=-1; public static boolean failAny=false; private static final ItemTable INSTANCE=new ItemTable();
 public static ItemTable getInstance(){return INSTANCE;}
 public L1Item getTemplate(int id){return (failAny||id==missing)?null:new L1Item(id);} }
