package l1j.server.server.model;
import l1j.server.server.model.Instance.L1PcInstance;
/** Environment fixture only: no house DB/map bonus in natural-regen tests. */
public final class L1HouseLocation {
 public static boolean isInHouse(int x,int y,short map){return false;}
 public static boolean isRegenLoc(L1PcInstance pc,int x,int y,short map){return false;}
}
