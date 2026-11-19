package l1j.server;
import java.sql.*;
import java.lang.reflect.*;
import java.util.*;
public class DatabaseFactory {
 static final DatabaseFactory instance=new DatabaseFactory();
 public static final List<int[]> rows=new ArrayList<>();
 public static DatabaseFactory getInstance(){return instance;}
 static Object def(Class<?> t){ if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;return null;}
 @SuppressWarnings("unchecked")static <T> T proxy(Class<T> type,InvocationHandler h){return (T)Proxy.newProxyInstance(type.getClassLoader(),new Class[]{type},h);}
 public Connection getConnection(){return proxy(Connection.class,(self,m,a)->{
   if(m.getName().equals("prepareStatement"))return proxy(PreparedStatement.class,(q,n,b)->{
     if(n.getName().equals("executeQuery"))return result();
     return def(n.getReturnType());
   });
   return def(m.getReturnType());
 });}
 static ResultSet result(){final int[] at={-1};return proxy(ResultSet.class,(self,m,a)->{
  if(m.getName().equals("next")){at[0]++;return at[0]<rows.size();}
  if(m.getName().equals("getInt")){int[] row=rows.get(at[0]);return switch((String)a[0]){case "item_id"->row[0];case "count"->row[1];case "enchantlvl"->row[2];default->0;};}
  return def(m.getReturnType());
 });}
}