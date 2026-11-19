package l1j.server.server.model.item.function; import l1j.server.server.model.Instance.L1PcInstance;
public class TransformationItems {public static boolean usePolyScroll(L1PcInstance p,int id,String s){return PotionEffects.usePolyScroll(p,id,s);}
public static void usePolyPotion(L1PcInstance p,int id){PotionEffects.usePolyPotion(p,id);}
public static void useLevelPolyScroll(L1PcInstance p,int id){PotionEffects.useLevelPolyScroll(p,id);}}