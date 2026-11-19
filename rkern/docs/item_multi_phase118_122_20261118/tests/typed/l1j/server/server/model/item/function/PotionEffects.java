package l1j.server.server.model.item.function;
import l1j.server.server.model.Instance.L1PcInstance;
public class PotionEffects {
 public static int morphCount;
 public static boolean usePolyScroll(L1PcInstance p,int id,String s){return false;}
 public static void usePolyPotion(L1PcInstance p,int id){morphCount++;}
 public static void useLevelPolyScroll(L1PcInstance p,int id){morphCount++;}
 public static void useGreenPotion(L1PcInstance p,int id){MovementPotionEffects.useGreenPotion(p,id);}
 public static void useBravePotion(L1PcInstance p,int id){MovementPotionEffects.useBravePotion(p,id);}
 public static void useFruit(L1PcInstance p,int id){MovementPotionEffects.useFruit(p,id);}
 public static void useBluePotion(L1PcInstance p,int id){ManaPotionEffects.useBluePotion(p,id);}
 public static void useWisdomPotion(L1PcInstance p,int id){ManaPotionEffects.useWisdomPotion(p,id);}
 public static void useBlessOfEva(L1PcInstance p,int id){ProtectivePotionEffects.useBlessOfEva(p,id);}
 public static void useBlindPotion(L1PcInstance p){ProtectivePotionEffects.useBlindPotion(p);}
}
