package l1j.server.server.model.item.Card;
import java.util.*;
public class Trace {
 public static long seed=1;
 public static final List<String> messages=new ArrayList<>();
 public static void add(String s){messages.add(s);}
 public static String take(){String s=messages.toString(); messages.clear();return s;}
}
