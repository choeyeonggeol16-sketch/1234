#!/usr/bin/env python3
"""JDK21 tests of extracted production methods with deterministic typed doubles.
No live SQL engine, CacheD threads or game server. JDBC failures are injected;
transaction rollback is modeled by discarding the staged database copy.
"""
from pathlib import Path
import tempfile, subprocess
P=Path(__file__).resolve().parent; R=P.parents[1]/'src/l1j/server/server'
def extract(s,sig):
    a=s.index(sig); b=s.index('{',a); n=1; i=b+1
    while n:
        n+=(s[i]=='{')-(s[i]=='}');i+=1
    return s[a:i]
def run(code):
    with tempfile.TemporaryDirectory() as t:
        p=Path(t)/'Test.java';p.write_text(code)
        subprocess.run(['javac','--release','21','-encoding','UTF-8','-d',t,str(p)],check=True)
        subprocess.run(['java','-cp',t,'Test'],check=True)
s=(R/'model/item/tam/TamFruitUseService.java').read_text()
constants=s[s.index('    private static final long DAY_MS'):s.index('    private static final int[] TAM_SKILLS')]
hexes=s[s.index('    public static final int[] hextable'):s.index('    /**\n     * - "탐')]
methods='\n'.join(extract(s,x) for x in ['private static final class FruitResult','private static void applyFruit(','private static boolean isActive(','private static void updateEnd(','static Timestamp extendReservation(','private static void deleteReservation(','private static String byteWrite(','private static int timerMillis('])
run('''import java.sql.*;import java.lang.reflect.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
public class Test {
'''+constants+hexes+methods+'''
static final long NOW=1700000000000L;
record Char(int id,String acc,String name,Timestamp end){}
record Res(long id,int obj,int day,String enc){}
static class Db {
 TreeMap<Integer,Char> chars=new TreeMap<>();TreeMap<Long,Res> res=new TreeMap<>();int failAt, writes,queries;long next=10000;
 Db copy(){Db d=new Db();d.chars.putAll(chars);d.res.putAll(res);d.next=next;return d;}
 void add(int id,String acc,long end){chars.put(id,new Char(id,acc,"char"+id,end==0?null:new Timestamp(end)));}
 void r(long id,int obj,int day){res.put(id,new Res(id,obj,day,byteWrite(obj)));}
 @SuppressWarnings("unchecked") static <T>T proxy(Class<T> c, InvocationHandler h){return (T)Proxy.newProxyInstance(c.getClassLoader(),new Class[]{c},h);}
 Connection connection(){return proxy(Connection.class,(o,m,a)->{if(m.getName().equals("prepareStatement"))return ps((String)a[0]);if(m.getName().equals("close"))return null;throw new AssertionError(m);});}
 PreparedStatement ps(String sql){Map<Integer,Object> args=new HashMap<>();return proxy(PreparedStatement.class,(o,m,a)->{
  String n=m.getName();if(n.startsWith("set")){args.put((Integer)a[0],a[1]);return null;}if(n.equals("close"))return null;
  if(n.equals("executeQuery")){queries++;List<Map<Object,Object>> rows=new ArrayList<>();
    if(sql.equals(SQL_LOCK_ACCOUNT_CHARS)){for(Char c:chars.values())if(c.acc.equals(args.get(1)))rows.add(row("objid",c.id,"char_name",c.name,"TamEndTime",c.end));}
    else if(sql.startsWith("SELECT id, day FROM tam")){for(Res r:res.values())if(r.obj==(int)args.get(1)){rows.add(row(1,r.id,2,r.day));break;}}
    else if(sql.startsWith("SELECT objid FROM characters")){Char c=chars.get(args.get(1));if(c!=null&&c.acc.equals(args.get(2)))rows.add(row(1,c.id));}
    else if(sql.startsWith("SELECT id, objid, day FROM tam")){for(Res r:res.values())if(r.enc.equals(args.get(1))){rows.add(row(1,r.id,2,r.obj,3,r.day));break;}}
    else throw new AssertionError(sql);return rs(rows);
  }if(n.equals("executeUpdate")){if(++writes==failAt)throw new SQLException("injected");
    if(sql.equals(SQL_UPDATE_END)){Char c=chars.get(args.get(2));if(c==null||!c.acc.equals(args.get(3)))return 0;chars.put(c.id,new Char(c.id,c.acc,c.name,(Timestamp)args.get(1)));return 1;}
    if(sql.equals(SQL_INSERT_RESERVE)){long id=next++;res.put(id,new Res(id,(int)args.get(1),(int)args.get(3),(String)args.get(4)));return 1;}
    if(sql.startsWith("DELETE FROM tam WHERE id=")){Res r=res.get(args.get(1));if(r==null||r.obj!=(int)args.get(2))return 0;res.remove(r.id);return 1;}
    throw new AssertionError(sql);
  }throw new AssertionError(m);
 });}
 static Map<Object,Object> row(Object... a){Map<Object,Object> r=new HashMap<>();for(int i=0;i<a.length;i+=2)r.put(a[i],a[i+1]);return r;}
 ResultSet rs(List<Map<Object,Object>> rows){int[] i={-1};return proxy(ResultSet.class,(o,m,a)->{String n=m.getName();if(n.equals("next"))return ++i[0]<rows.size();if(n.equals("close"))return null;Object x=rows.get(i[0]).get(a[0]);return switch(n){case "getInt"->((Number)x).intValue();case "getLong"->((Number)x).longValue();case "getTimestamp","getString"->x;default->throw new AssertionError(m);};});}
}
static void ck(boolean b){if(!b)throw new AssertionError();}
public static void main(String[] args)throws Exception{
 int cases=0;
 for(int day:new int[]{3,7,30})for(int active=0;active<=5;active++)for(int status=0;status<4;status++)for(int foreign=0;foreign<2;foreign++)for(int pending=0;pending<2;pending++)for(int fail=0;fail<4;fail++){
  Db committed=new Db();committed.add(1,"A",0);long end=switch(status){case 1->NOW+999999;case 2->NOW-9000;case 3->NOW-1000;default->0;};committed.add(2,foreign==0?"A":"B",end);for(int k=0;k<active;k++)committed.add(3+k,"A",NOW+9999999);if(pending!=0)committed.r(10,2,7);Db staged=committed.copy();staged.failAt=fail;FruitResult f=new FruitResult();boolean ok=true;
  try{applyFruit(staged.connection(),"A",1,2,day,NOW,f);}catch(SQLException e){ok=false;}
  boolean renew=status>=2&&pending!=0;boolean isActive=status==1||renew;boolean reserve=isActive||status==3;boolean limit=!isActive&&active>=5;int writes=(renew?2:0)+1;
  boolean expected=foreign==0&&!limit&&(fail==0||fail>writes);ck(ok==expected);if(ok){ck(f.reserved==reserve);ck(f.stage==Math.min(active+(isActive||!reserve?1:0),5));ck(staged.res.size()==(pending!=0&&!renew?1:0)+(reserve?1:0));if(!reserve)ck(staged.chars.get(2).end.getTime()==NOW+DAY_MS*day+10000);committed=staged;}else{ck(committed.chars.get(2).end==null?end==0:committed.chars.get(2).end.getTime()==end);ck(committed.res.size()==pending);}
  cases++;
 }
 System.out.println("TAM_FRUIT_PASS "+cases+" SQL decision/failure cases");
 int cancel=0;for(int mask=0;mask<32;mask++){Db d=new Db();d.add(2,(mask&1)!=0?"B":"A",NOW);d.r(7,2,(mask&2)!=0?7:3);if((mask&4)!=0)d.r(6,2,3);if((mask&8)!=0)d.res.remove(7L);d.failAt=(mask&16)!=0?1:0;boolean ok=true;try{deleteReservation(d.connection(),"A",byteWrite(2),7,2,3);}catch(SQLException e){ok=false;}ck(ok==(mask==0));if(ok)ck(!d.res.containsKey(7L));else if((mask&8)==0)ck(d.res.containsKey(7L));cancel++;}
 System.out.println("TAM_REFUND_PASS "+cancel+" stale/head/owner/delete-failure cases");
 ck(timerMillis(3*86400)==259200000);ck(timerMillis(7*86400)==604800000);ck(timerMillis(30*86400)==Integer.MAX_VALUE);ck(timerMillis(0)==0);ck(timerMillis(-1)==0);System.out.println("TAM_TIMER_PASS 5 bounds");
}
}
''')
# 232+ replaces the old separate post-commit fishing reward path. Its full
# current regression (including the former 231 failure/stale scenarios) is
# executed by the companion test rather than an obsolete stub signature.
subprocess.run([__import__('sys').executable, str(P.parent / 'item_use_phase232_235_20261119/test_fishing_receipt.py')], check=True)
