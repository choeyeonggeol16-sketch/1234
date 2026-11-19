package l1j.server.server.model;
import java.sql.*;
import java.util.*;
import java.lang.reflect.*;
import l1j.server.server.model.Instance.L1ItemInstance;
public final class L1InventoryTransaction implements AutoCloseable {
  private final Object player;
  private final Map<Integer,Integer> debit=new HashMap<>();
  private final Map<Integer,Integer> grants=new HashMap<>();
  private SqlAction sql;private Runnable failed;private boolean submitted;
  private L1InventoryTransaction(Object p){player=p;}
  public static L1InventoryTransaction begin(Object p,int resource){return new L1InventoryTransaction(p);}
  public interface SqlAction { void run(Connection c) throws SQLException; }
  public boolean debit(L1ItemInstance item,int count){return debit(item.getItemId(),count);}
  public boolean debit(int id,int count){
    Map<Integer,Integer> inv=ref("inv");
    int available=inv.getOrDefault(id,0)-debit.getOrDefault(id,0);
    if(available<count||count<=0)return false;
    debit.merge(id,count,Integer::sum);return true;
  }
  public L1ItemInstance grant(int id,int count){
    if(id<=0||count<=0)return null;
    grants.merge(id,count,Integer::sum);
    return new L1ItemInstance(id);
  }
  public void requireTables(String...names){if(names.length!=1||!"character_quests".equals(names[0]))throw new AssertionError("missing transactional quest table");}
  public void sql(SqlAction s){sql=s;}
  public void onFailure(Runnable r){failed=r;}
  @SuppressWarnings("unchecked") private <T>T ref(String field){try{Field f=player.getClass().getDeclaredField(field);f.setAccessible(true);return (T)f.get(player);}catch(Exception e){throw new RuntimeException(e);}}
  private boolean flag(String name){try{Field f=player.getClass().getDeclaredField(name);f.setAccessible(true);return f.getBoolean(player);}catch(Exception e){throw new RuntimeException(e);}}
  public void commitAfterInventoryRelease(Runnable after){
    submitted=true;
    Map<Integer,Integer> live=ref("inv");Map<Integer,Integer> db=ref("db");
    Map<Integer,Integer> nextdb=new HashMap<>(db);
    try {
      if(sql!=null)sql.run(mockConnection(nextdb));
      if(flag("failSql")||flag("failCommit")||flag("failCapacity"))throw new SQLException("injected rollback");
      for(var e:debit.entrySet()){
        int next=live.getOrDefault(e.getKey(),0)-e.getValue();
        if(next<0)throw new SQLException("missing material");
      }
      for(var e:debit.entrySet())live.merge(e.getKey(),-e.getValue(),Integer::sum);
      for(var e:grants.entrySet())live.merge(e.getKey(),e.getValue(),Integer::sum);
      db.clear();db.putAll(nextdb);
      after.run();
    } catch(SQLException e) {if(failed!=null)failed.run();}
  }
  private Connection mockConnection(Map<Integer,Integer> steps){
    return (Connection)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{Connection.class},(proxy,method,args)->{
      if("prepareStatement".equals(method.getName()))return statement((String)args[0],steps);
      if("close".equals(method.getName()))return null;
      throw new UnsupportedOperationException(method.getName());
    });
  }
  private PreparedStatement statement(String sql,Map<Integer,Integer> steps){
    Map<Integer,Integer> params=new HashMap<>();
    return (PreparedStatement)Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{PreparedStatement.class},(proxy,method,args)->{
      String n=method.getName();
      if("setInt".equals(n)){params.put((Integer)args[0],(Integer)args[1]);return null;}
      if("executeQuery".equals(n)){
        int questId=params.get(2);boolean exists=steps.containsKey(questId);int value=steps.getOrDefault(questId,0);
        return Proxy.newProxyInstance(getClass().getClassLoader(),new Class<?>[]{ResultSet.class},new InvocationHandler(){boolean called=false;
          public Object invoke(Object p,Method m,Object[] a){switch(m.getName()){
            case "next":if(!called){called=true;return exists;}return false;
            case "getInt":return value;case "close":return null;default:throw new UnsupportedOperationException(m.getName());
          }}
        });
      }
      if("executeUpdate".equals(n)){steps.put(params.get(2),params.get(3));return 1;}
      if("close".equals(n))return null;
      throw new UnsupportedOperationException(n);
    });
  }
  public void close() {}
}
